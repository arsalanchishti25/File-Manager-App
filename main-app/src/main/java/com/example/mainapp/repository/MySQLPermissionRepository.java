package com.example.mainapp.repository;

import com.example.mainapp.db.RemoteMySQLDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MySQLPermissionRepository implements PermissionRepository {

    private final RemoteMySQLDataSource dataSource;

    public MySQLPermissionRepository(RemoteMySQLDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void addPermission(long fileId, long userId, String permissionType) {
        String sql = "INSERT INTO file_permissions (file_id, user_id, permission_type) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            ps.setLong(2, userId);
            ps.setString(3, permissionType);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("MySQLPermissionRepository.addPermission error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removePermission(long fileId, long userId) {
        String sql = "DELETE FROM file_permissions WHERE file_id = ? AND user_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            ps.setLong(2, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("MySQLPermissionRepository.removePermission error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<String> findPermissionForUserAndFile(long userId, long fileId) {
        String sql = "SELECT permission_type FROM file_permissions WHERE user_id = ? AND file_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, fileId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("permission_type"));
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            System.err.println("MySQLPermissionRepository.findPermissionForUserAndFile error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Long> findFileIdsSharedWithUser(long userId) {
        String sql = "SELECT file_id FROM file_permissions WHERE user_id = ?";
        List<Long> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("file_id"));
                }
            }
            return result;

        } catch (SQLException e) {
            System.err.println("MySQLPermissionRepository.findFileIdsSharedWithUser error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void deleteByFileId(long fileId) {
        String sql = "DELETE FROM file_permissions WHERE file_id = ?";
        try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fileId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("MySQLPermissionRepository.deleteByFileId error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
