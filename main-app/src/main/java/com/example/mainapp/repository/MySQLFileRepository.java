// src/main/java/com/example/mainapp/repository/MySQLFileRepository.java
package com.example.mainapp.repository;

// CHANGE 1: Remove FileChunker import from aggregator (no longer needed)
// OLD: import com.example.mainapp.aggregator.FileChunker;
// NEW: Import our new metadata model instead
import com.example.mainapp.model.FileChunkMetadata;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.model.File;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public class MySQLFileRepository implements FileRepository {

    private final RemoteMySQLDataSource dataSource;

    public MySQLFileRepository(RemoteMySQLDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public File saveFile(long ownerId, String filename, long sizeInBytes) {
        // No changes needed here - this method is already clean
        String sql = "INSERT INTO files (owner_id, filename, size_bytes, created_at, last_modified) " +
                     "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            LocalDateTime now = LocalDateTime.now();
            ps.setLong(1, ownerId);
            ps.setString(2, filename);
            ps.setLong(3, sizeInBytes);
            ps.setObject(4, now);
            ps.setObject(5, now);

            int rowsInserted = ps.executeUpdate();
            if (rowsInserted > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        return new File(id, ownerId, filename, sizeInBytes, now, now);
                    }
                }
            }
            throw new SQLException("Failed to insert file, no rows affected.");

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.saveFile error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // CHANGE 2: Update saveChunk signature to include volumeGroup parameter
    // WHY: We need to track which volume group (1-4) each chunk belongs to
    // This matches the updated FileRepository interface
    @Override
    public void saveChunk(long fileId, int chunkOrder, String checksum, String storageLocation, int volumeGroup) {
        // CHANGE 3: Update SQL to include volumegroup column
        // WHY: Load balancer needs to know volume groups when routing requests
        String sql = "INSERT INTO file_chunks (file_id, chunk_order, crc32_checksum, storage_location, volume_group) " +
                     "VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
        
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            ps.setInt(2, chunkOrder);         // 1, 2, 3, or 4
            ps.setString(3, checksum);         // CRC32 checksum from aggregator
            ps.setString(4, storageLocation);  // FS container name (e.g., "fs-1")
            ps.setInt(5, volumeGroup);         // Volume group: 1, 2, 3, or 4

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Failed to save chunk metadata");
            }

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.saveChunk error: " + e.getMessage());
            throw new RuntimeException("Failed to save chunk: " + e.getMessage(), e);
        }
    }

    @Override
    public void replaceChunks(long fileId, Collection<FileChunkMetadata> chunks) {
        String deleteSql = "DELETE FROM file_chunks WHERE file_id = ?";
        String insertSql = "INSERT INTO file_chunks "
                + "(file_id, chunk_order, crc32_checksum, storage_location, volume_group) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delete = conn.prepareStatement(deleteSql);
                 PreparedStatement insert = conn.prepareStatement(insertSql)) {
                delete.setLong(1, fileId);
                delete.executeUpdate();
                for (FileChunkMetadata chunk : chunks) {
                    insert.setLong(1, fileId);
                    insert.setInt(2, chunk.getChunkOrder());
                    insert.setString(3, chunk.getCrc32Checksum());
                    insert.setString(4, chunk.getStorageLocation());
                    insert.setInt(5, chunk.getVolumeGroup());
                    insert.addBatch();
                }
                insert.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to replace chunk metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public void replaceFileAndChunks(File file, Collection<FileChunkMetadata> chunks) {
        String deleteSql = "DELETE FROM file_chunks WHERE file_id = ?";
        String insertSql = "INSERT INTO file_chunks "
                + "(file_id, chunk_order, crc32_checksum, storage_location, volume_group) "
                + "VALUES (?, ?, ?, ?, ?)";
        String updateSql = "UPDATE files SET size_bytes = ?, last_modified = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delete = conn.prepareStatement(deleteSql);
                 PreparedStatement insert = conn.prepareStatement(insertSql);
                 PreparedStatement update = conn.prepareStatement(updateSql)) {
                delete.setLong(1, file.getId());
                delete.executeUpdate();
                for (FileChunkMetadata chunk : chunks) {
                    insert.setLong(1, file.getId());
                    insert.setInt(2, chunk.getChunkOrder());
                    insert.setString(3, chunk.getCrc32Checksum());
                    insert.setString(4, chunk.getStorageLocation());
                    insert.setInt(5, chunk.getVolumeGroup());
                    insert.addBatch();
                }
                insert.executeBatch();
                update.setLong(1, file.getSizeInBytes());
                update.setObject(2, file.getLastModified());
                update.setLong(3, file.getId());
                if (update.executeUpdate() != 1) {
                    throw new SQLException("File metadata row was not updated");
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to replace file metadata: " + e.getMessage(), e);
        }
    }

    // CHANGE 4: Update return type from FileChunker.Chunk to FileChunkMetadata
    // WHY: Main app should not depend on Aggregator's FileChunker class
    // FileChunkMetadata is a lightweight model that only stores metadata (no chunk bytes)
    @Override
    public List<FileChunkMetadata> getChunksForFile(long fileId) {
        List<FileChunkMetadata> chunks = new ArrayList<>();
        
        // CHANGE 5: Add volume_group to SELECT query
        // WHY: We need volume group info when downloading files
        String sql = "SELECT id, file_id, chunk_order, crc32_checksum, storage_location, volume_group " +
                     "FROM file_chunks WHERE file_id = ? ORDER BY chunk_order ASC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // CHANGE 6: Extract all fields including id and volume_group
                    long id = rs.getLong("id");                    // Primary key
                    long chunkFileId = rs.getLong("file_id");      // Foreign key to files table
                    int chunkOrder = rs.getInt("chunk_order");     // 1-4
                    String checksum = rs.getString("crc32_checksum");
                    String storageLocation = rs.getString("storage_location");  // FS container name
                    int volumeGroup = rs.getInt("volume_group");   // 1-4

                    // CHANGE 7: Create FileChunkMetadata instead of FileChunker.Chunk
                    // WHY: This is our own model, no dependency on aggregator app
                    chunks.add(new FileChunkMetadata(
                        id, 
                        chunkFileId, 
                        chunkOrder, 
                        storageLocation, 
                        checksum, 
                        volumeGroup
                    ));
                }
            }

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.getChunksForFile error: " + e.getMessage());
            throw new RuntimeException("Failed to load chunks: " + e.getMessage(), e);
        }

        return chunks;
    }

    @Override
    public Optional<File> findById(long fileId) {
        // No changes needed - this is already clean
        String sql = "SELECT id, owner_id, filename, size_bytes, created_at, last_modified " +
                     "FROM files WHERE id = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToFile(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.findById error: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<File> findByOwnerId(long ownerId) {
        // No changes needed
        String sql = "SELECT id, owner_id, filename, size_bytes, created_at, last_modified " +
                     "FROM files WHERE owner_id = ? ORDER BY created_at DESC";
        List<File> files = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, ownerId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    files.add(mapResultSetToFile(rs));
                }
            }

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.findByOwnerId error: " + e.getMessage());
        }

        return files;
    }

    @Override
    public List<File> findAll() {
        // No changes needed
        String sql = "SELECT id, owner_id, filename, size_bytes, created_at, last_modified " +
                     "FROM files ORDER BY created_at DESC";
        List<File> files = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                files.add(mapResultSetToFile(rs));
            }

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.findAll error: " + e.getMessage());
        }

        return files;
    }

    @Override
    public void updateFile(File file) {
        // No changes needed
        String sql = "UPDATE files SET filename = ?, last_modified = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, file.getFilename());
            ps.setObject(2, file.getLastModified());
            ps.setLong(3, file.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.updateFile error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteById(long fileId) {
        // No changes needed
        String sql = "DELETE FROM files WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.deleteById error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteChunksByFileId(long fileId) {
        // No changes needed
        String sql = "DELETE FROM file_chunks WHERE file_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.deleteChunksByFileId error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean existsById(long fileId) {
        // No changes needed
        String sql = "SELECT 1 FROM files WHERE id = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("MySQLFileRepository.existsById error: " + e.getMessage());
            return false;
        }
    }

    // CHANGE 8: Add updateFileStatus method (from updated interface)
    // WHY: Track upload progress: "UPLOADING" -> "READY" or "FAILED"
    // public void updateFileStatus(long fileId, String status) {
    //     String sql = "UPDATE files SET status = ?, last_modified = ? WHERE id = ?";
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //         ps.setString(1, status);
    //         ps.setObject(2, LocalDateTime.now());
    //         ps.setLong(3, fileId);
    //         ps.executeUpdate();
    //     } catch (SQLException e) {
    //         System.err.println("MySQLFileRepository.updateFileStatus error: " + e.getMessage());
    //         throw new RuntimeException(e);
    //     }
    // }

    private File mapResultSetToFile(ResultSet rs) throws SQLException {
        // No changes needed
        return new File(
            rs.getLong("id"),
            rs.getLong("owner_id"),
            rs.getString("filename"),
            rs.getLong("size_bytes"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("last_modified", LocalDateTime.class)
        );
    }
}
