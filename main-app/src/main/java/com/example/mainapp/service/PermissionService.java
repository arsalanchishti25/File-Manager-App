package com.example.mainapp.service;


import com.example.mainapp.repository.PermissionRepository;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.repository.MySQLPermissionRepository;

import java.util.List;

public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService() {
        this.permissionRepository =
            new MySQLPermissionRepository(new RemoteMySQLDataSource());
    }

    // For testing / DI
    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public void shareFile(long fileId, long targetUserId, String permissionType) {
        if (!"READ".equalsIgnoreCase(permissionType) &&
            !"READ_WRITE".equalsIgnoreCase(permissionType)) {
            throw new IllegalArgumentException("Invalid permission type: " + permissionType);
        }
        permissionRepository.addPermission(fileId, targetUserId, permissionType.toUpperCase());
    }

    public void revokeShare(long fileId, long targetUserId) {
        permissionRepository.removePermission(fileId, targetUserId);
    }

    public boolean hasReadAccess(long userId, long fileId, boolean isAdmin, long ownerId) {
        if (isAdmin || userId == ownerId) {
            return true;
        }
        return permissionRepository
                .findPermissionForUserAndFile(userId, fileId)
                .map(p -> p.equalsIgnoreCase("READ") || p.equalsIgnoreCase("READ_WRITE"))
                .orElse(false);
    }

    public boolean hasWriteAccess(long userId, long fileId, boolean isAdmin, long ownerId) {
        if (isAdmin || userId == ownerId) {
            return true;
        }
        return permissionRepository
                .findPermissionForUserAndFile(userId, fileId)
                .map(p -> p.equalsIgnoreCase("READ_WRITE"))
                .orElse(false);
    }

    public List<Long> findFileIdsSharedWithUser(long userId) {
        return permissionRepository.findFileIdsSharedWithUser(userId);
    }

    public void deletePermissionsForFile(long fileId) {
        permissionRepository.deleteByFileId(fileId);
    }
}
