package com.example.mainapp.service;

import com.example.mainapp.model.User;
import com.example.mainapp.repository.SyncRepository;
import com.example.mainapp.util.PasswordHasher;

import java.time.LocalDateTime;
import java.util.Optional;

public class SyncAwareUserService {

    private final ConnectivityService connectivity;
    private final AuthService authService;
    private final RegistrationService registrationService;
    private final UserManagementService userManagementService;
    private final PermissionService permissionService;
    private final SyncRepository syncRepository;

    public SyncAwareUserService() {
        this(
            new ConnectivityService(),
            new AuthService(),
            new RegistrationService(),
            new UserManagementService(),
            new PermissionService(),
            new SyncRepository()
        );
    }

    public SyncAwareUserService(ConnectivityService connectivity,
                                AuthService authService,
                                RegistrationService registrationService,
                                UserManagementService userManagementService,
                                PermissionService permissionService,
                                SyncRepository syncRepository) {
        this.connectivity = connectivity;
        this.authService = authService;
        this.registrationService = registrationService;
        this.userManagementService = userManagementService;
        this.permissionService = permissionService;
        this.syncRepository = syncRepository;
    }

    // =========================================================
    // LOGIN (online + offline)
    // =========================================================

    public Optional<User> login(String username, String plainPassword) {
        if (connectivity.isOnline()) {
            Optional<User> userOpt = authService.login(username, plainPassword);
            userOpt.ifPresent(this::cacheUserAsSynced);
            return userOpt;
        } else {
            return offlineLogin(username, plainPassword);
        }
    }

    private Optional<User> offlineLogin(String username, String plainPassword) {
        return syncRepository.findCachedUserByUsername(username)
            .filter(u -> PasswordHasher.verify(plainPassword, u.getPasswordHash()));
    }

    private void cacheUserAsSynced(User u) {
        syncRepository.upsertCachedUser(
            u.getId(),
            u.getUsername(),
            u.getPasswordHash(),
            u.getRole().name(),
            u.getCreatedAt(),
            "synced"
        );
    }

    // =========================================================
    // REGISTRATION (online + offline queued)
    // =========================================================

    public User register(String username, String plainPassword, User.Role role, Long actorUserIdOrNull) {
        if (connectivity.isOnline()) {
            User created = registrationService.register(username, plainPassword, role);
            cacheUserAsSynced(created);
            return created;
        } else {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Username cannot be empty.");
            }
            String hash = PasswordHasher.hash(plainPassword);

            syncRepository.upsertCachedUser(
                0L,
                username,
                hash,
                role.name(),
                LocalDateTime.now(),
                "new"
            );

            String payload = String.format(
                "{ \"username\": \"%s\", \"passwordHash\": \"%s\", \"role\": \"%s\", \"actorId\": %s }",
                username,
                hash,
                role.name(),
                actorUserIdOrNull == null ? "null" : actorUserIdOrNull.toString()
            );

            syncRepository.insertPendingChange("users", 0L, "USER_REGISTER", payload);

            return new User(
                0L,
                username,
                hash,
                role,
                LocalDateTime.now()
            );
        }
    }

    // =========================================================
    // PASSWORD CHANGE
    // =========================================================

    public boolean updatePassword(String username, String newPlainPassword) {
        String hash = PasswordHasher.hash(newPlainPassword);

        if (connectivity.isOnline()) {
            boolean ok = authService.updatePassword(username, hash);
            if (ok) {
                syncRepository.updateCachedUserPassword(username, hash);
            }
            return ok;
        } else {
            syncRepository.updateCachedUserPassword(username, hash);

            String payload = String.format(
                "{ \"username\": \"%s\", \"passwordHash\": \"%s\" }",
                username,
                hash
            );

            syncRepository.insertPendingChange("users", 0L, "USER_PASSWORD_UPDATE", payload);
            return true;
        }
    }

    // =========================================================
    // USER MANAGEMENT (delete, promote/demote)
    // =========================================================

    public void deleteUser(long userId) {
        if (connectivity.isOnline()) {
            userManagementService.deleteUser(userId);
            syncRepository.markCachedUserDeleted(userId);
        } else {
            syncRepository.markCachedUserDeleted(userId);
            String payload = String.format(
                "{ \"userId\": %d }",
                userId
            );
            syncRepository.insertPendingChange("users", userId, "USER_DELETE", payload);
        }
    }

    public void promoteDemoteUser(long userId) {
        if (connectivity.isOnline()) {
            userManagementService.promoteDemoteUser(userId);
            syncRepository.toggleCachedUserRole(userId);
        } else {
            syncRepository.toggleCachedUserRole(userId);
            String payload = String.format(
                "{ \"userId\": %d }",
                userId
            );
            syncRepository.insertPendingChange("users", userId, "USER_ROLE_TOGGLE", payload);
        }
    }

    // =========================================================
    // PERMISSIONS (share/revoke + checks)
    // =========================================================

    public void shareFile(long fileId, long targetUserId, String permissionType) {
        if (connectivity.isOnline()) {
            permissionService.shareFile(fileId, targetUserId, permissionType);
            syncRepository.upsertCachedPermission(
                fileId,
                targetUserId,
                permissionType.toUpperCase(),
                LocalDateTime.now(),
                "synced"
            );
        } else {
            permissionType = permissionType.toUpperCase();
            syncRepository.upsertCachedPermission(
                fileId,
                targetUserId,
                permissionType,
                LocalDateTime.now(),
                "new"
            );
            String payload = String.format(
                "{ \"fileId\": %d, \"userId\": %d, \"permissionType\": \"%s\" }",
                fileId, targetUserId, permissionType
            );
            syncRepository.insertPendingChange("filepermissions", fileId, "PERMISSION_SHARE", payload);
        }
    }

    public void revokeShare(long fileId, long targetUserId) {
        if (connectivity.isOnline()) {
            permissionService.revokeShare(fileId, targetUserId);
            syncRepository.deleteCachedPermission(fileId, targetUserId);
        } else {
            syncRepository.deleteCachedPermission(fileId, targetUserId);
            String payload = String.format(
                "{ \"fileId\": %d, \"userId\": %d }",
                fileId, targetUserId
            );
            syncRepository.insertPendingChange("filepermissions", fileId, "PERMISSION_REVOKE", payload);
        }
    }

    public boolean hasReadAccess(long userId, long fileId, boolean isAdmin, long ownerId) {
        if (isAdmin || userId == ownerId) {
            return true;
        }

        if (connectivity.isOnline()) {
            return permissionService.hasReadAccess(userId, fileId, isAdmin, ownerId);
        } else {
            return syncRepository
                .findCachedPermissionForUserAndFile(userId, fileId)
                .map(p -> p.equalsIgnoreCase("READ") || p.equalsIgnoreCase("READ_WRITE"))
                .orElse(false);
        }
    }

    public boolean hasWriteAccess(long userId, long fileId, boolean isAdmin, long ownerId) {
        if (isAdmin || userId == ownerId) {
            return true;
        }

        if (connectivity.isOnline()) {
            return permissionService.hasWriteAccess(userId, fileId, isAdmin, ownerId);
        } else {
            return syncRepository
                .findCachedPermissionForUserAndFile(userId, fileId)
                .map(p -> p.equalsIgnoreCase("READ_WRITE"))
                .orElse(false);
        }
    }

    public java.util.List<Long> findFileIdsSharedWithUser(long userId) {
        if (connectivity.isOnline()) {
            return permissionService.findFileIdsSharedWithUser(userId);
        } else {
            return syncRepository.findCachedFileIdsSharedWithUser(userId);
        }
    }
}
