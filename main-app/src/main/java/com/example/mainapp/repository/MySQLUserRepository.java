package com.example.mainapp.repository;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.model.User;
import com.example.mainapp.util.PasswordHasher;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class MySQLUserRepository implements UserRepository {

    private final RemoteMySQLDataSource dataSource;

    public MySQLUserRepository(RemoteMySQLDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<User> findByUsernameAndPassword(String username, String plainPassword) {
        String sql = "SELECT id, username, password_hash, role, created_at FROM users WHERE username = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    String uname = rs.getString("username");
                    String hash = rs.getString("password_hash");
                    String roleStr = rs.getString("role");
                    LocalDateTime createdAt = rs.getObject("created_at", LocalDateTime.class);

                    User.Role role = User.Role.valueOf(roleStr);

                    if (PasswordHasher.verify(plainPassword, hash)) {
                        return Optional.of(new User(id, uname, hash, role, createdAt));
                    }
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            System.err.println("MySQLUserRepository error: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public User saveUser(String username, String passwordHash) {
        return saveUserWithRole(username, passwordHash, User.Role.STANDARD);
    }
    @Override
    public User saveUserWithRole(String username, String passwordHash, User.Role role) {
        String sql = "INSERT INTO users (username, password_hash, role, created_at) VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, role.toString());
            ps.setObject(4, LocalDateTime.now());

            int rowsInserted = ps.executeUpdate();
            if (rowsInserted > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        return new User(id, username, passwordHash, role, LocalDateTime.now());
                    }
                }
            }

            throw new SQLException("Failed to insert user, no rows affected.");

        } catch (SQLException e) {
            System.err.println("MySQLUserRepository.saveUser error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("MySQLUserRepository.existsByUsername error: " + e.getMessage());
            return false;
        }
    }
    
    public void deleteByUsername(String username) {
        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("MySQLUserRepository.deleteByUsername error: " + e.getMessage());
        }
    }

    @Override
    public Optional<User> findById(long id) {
        String sql = "SELECT id, username, password_hash, role, created_at FROM users WHERE id = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long userId = rs.getLong("id");
                    String username = rs.getString("username");
                    String passwordHash = rs.getString("password_hash");
                    String roleStr = rs.getString("role");
                    java.time.LocalDateTime createdAt = rs.getObject("created_at", java.time.LocalDateTime.class);
                    
                    User.Role role = User.Role.valueOf(roleStr);
                    return Optional.of(new User(userId, username, passwordHash, role, createdAt));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            System.err.println("MySQLUserRepository.findById error: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT id, username, password_hash, role, created_at FROM users ORDER BY created_at DESC";
        List<User> users = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String username = rs.getString("username");
                String passwordHash = rs.getString("password_hash");
                String roleStr = rs.getString("role");
                java.time.LocalDateTime createdAt = rs.getObject("created_at", java.time.LocalDateTime.class);
                
                User.Role role = User.Role.valueOf(roleStr);
                users.add(new User(id, username, passwordHash, role, createdAt));
            }

        } catch (SQLException e) {
            System.err.println("MySQLUserRepository.findAll error: " + e.getMessage());
        }

        return users;
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("MySQLUserRepository.deleteById error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean updatePassword(String username, String newHashedPassword) {
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
        
        try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, newHashedPassword);
            ps.setString(2, username);
            
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("MySQLUserRepository updatePassword error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void promoteDemotebyId(long id){

        String sql = "UPDATE users SET role = CASE " +
                    "WHEN role = 'STANDARD' THEN 'ADMIN' " +
                    "WHEN role = 'ADMIN' THEN 'STANDARD' " +
                    "END WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, id);
            ps.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("MySQLUserRepository.promoteDemotebyId error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
