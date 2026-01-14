package com.example.mainapp.repository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository {

    void addPermission(long fileId, long userId, String permissionType);

    void removePermission(long fileId, long userId);

    Optional<String> findPermissionForUserAndFile(long userId, long fileId);

    List<Long> findFileIdsSharedWithUser(long userId);

    void deleteByFileId(long fileId);
}
