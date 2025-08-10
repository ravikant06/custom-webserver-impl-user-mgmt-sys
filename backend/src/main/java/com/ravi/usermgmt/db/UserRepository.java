package com.ravi.usermgmt.db;

import com.ravi.usermgmt.models.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * User repository for database operations
 */
public class UserRepository {
    private static final Logger logger = Logger.getLogger(UserRepository.class.getName());
    private final DatabaseManager dbManager;

    public UserRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    // Create operations
    public User create(User user) throws SQLException {
        String sql = """
            INSERT INTO users (username, email, password_hash, first_name, last_name, 
                             phone, status, role, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::user_status, ?::user_role, ?, ?)
            """;

        // Hash password before storing
        String hashedPassword = BCrypt.hashpw(user.getPasswordHash(), BCrypt.gensalt());
        
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        Long id = dbManager.executeInsert(sql,
            user.getUsername(),
            user.getEmail(),
            hashedPassword,
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getStatus().name(),
            user.getRole().name(),
            Timestamp.valueOf(user.getCreatedAt()),
            Timestamp.valueOf(user.getUpdatedAt())
        );

        user.setId(id);
        user.setPasswordHash(hashedPassword);
        logger.info("Created user: " + user.getUsername());
        return user;
    }

    // Read operations
    public Optional<User> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        
        return Optional.ofNullable(dbManager.executeQuery(sql, this::mapResultSetToUser, id));
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        
        return Optional.ofNullable(dbManager.executeQuery(sql, this::mapResultSetToUser, username));
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ?";
        
        return Optional.ofNullable(dbManager.executeQuery(sql, this::mapResultSetToUser, email));
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        
        return dbManager.executeQuery(sql, this::mapResultSetToUserList);
    }

    public List<User> findAll(int page, int pageSize) throws SQLException {
        String sql = "SELECT * FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?";
        int offset = page * pageSize;
        
        return dbManager.executeQuery(sql, this::mapResultSetToUserList, pageSize, offset);
    }

    public List<User> findByStatus(User.UserStatus status) throws SQLException {
        String sql = "SELECT * FROM users WHERE status = ? ORDER BY created_at DESC";
        
        return dbManager.executeQuery(sql, this::mapResultSetToUserList, status.name());
    }

    public List<User> searchUsers(String searchTerm, int page, int pageSize) throws SQLException {
        String sql = """
            SELECT * FROM users 
            WHERE username ILIKE ? OR email ILIKE ? OR first_name ILIKE ? OR last_name ILIKE ?
            ORDER BY created_at DESC 
            LIMIT ? OFFSET ?
            """;
        
        String searchPattern = "%" + searchTerm + "%";
        int offset = page * pageSize;
        
        return dbManager.executeQuery(sql, this::mapResultSetToUserList,
            searchPattern, searchPattern, searchPattern, searchPattern, pageSize, offset);
    }

    public long countUsers() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        });
    }

    public long countUsersByStatus(User.UserStatus status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE status = ?";
        
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        }, status.name());
    }

    public long countSearchResults(String searchTerm) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM users 
            WHERE username ILIKE ? OR email ILIKE ? OR first_name ILIKE ? OR last_name ILIKE ?
            """;
        
        String searchPattern = "%" + searchTerm + "%";
        
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        }, searchPattern, searchPattern, searchPattern, searchPattern);
    }

    // Update operations
    public User update(User user) throws SQLException {
        String sql = """
            UPDATE users 
            SET username = ?, email = ?, first_name = ?, last_name = ?, 
                phone = ?, status = ?::user_status, role = ?::user_role, updated_at = ?
            WHERE id = ?
            """;

        user.markAsUpdated();
        
        int affectedRows = dbManager.executeUpdate(sql,
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getStatus().name(),
            user.getRole().name(),
            Timestamp.valueOf(user.getUpdatedAt()),
            user.getId()
        );

        if (affectedRows == 0) {
            throw new SQLException("User not found for update: " + user.getId());
        }

        logger.info("Updated user: " + user.getUsername());
        return user;
    }

    public void updatePassword(Long userId, String newPassword) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?";
        
        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        LocalDateTime now = LocalDateTime.now();
        
        int affectedRows = dbManager.executeUpdate(sql, hashedPassword, Timestamp.valueOf(now), userId);
        
        if (affectedRows == 0) {
            throw new SQLException("User not found for password update: " + userId);
        }

        logger.info("Updated password for user ID: " + userId);
    }

    public void updateStatus(Long userId, User.UserStatus status) throws SQLException {
        String sql = "UPDATE users SET status = ?::user_status, updated_at = ? WHERE id = ?";
        
        LocalDateTime now = LocalDateTime.now();
        
        int affectedRows = dbManager.executeUpdate(sql, status.name(), Timestamp.valueOf(now), userId);
        
        if (affectedRows == 0) {
            throw new SQLException("User not found for status update: " + userId);
        }

        logger.info("Updated status for user ID: " + userId + " to " + status);
    }

    public void updateLastLogin(Long userId) throws SQLException {
        String sql = "UPDATE users SET last_login = ?, updated_at = ? WHERE id = ?";
        
        LocalDateTime now = LocalDateTime.now();
        
        int affectedRows = dbManager.executeUpdate(sql, Timestamp.valueOf(now), Timestamp.valueOf(now), userId);
        
        if (affectedRows == 0) {
            throw new SQLException("User not found for last login update: " + userId);
        }

        logger.info("Updated last login for user ID: " + userId);
    }

    // Delete operations
    public boolean delete(Long id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        
        int affectedRows = dbManager.executeUpdate(sql, id);
        
        if (affectedRows > 0) {
            logger.info("Deleted user ID: " + id);
            return true;
        }
        
        return false;
    }

    public void deleteByUsername(String username) throws SQLException {
        String sql = "DELETE FROM users WHERE username = ?";
        
        int affectedRows = dbManager.executeUpdate(sql, username);
        
        if (affectedRows == 0) {
            throw new SQLException("User not found for deletion: " + username);
        }

        logger.info("Deleted user: " + username);
    }

    // Authentication operations
    public Optional<User> authenticate(String username, String password) throws SQLException {
        Optional<User> userOpt = findByUsername(username);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // Verify password
            if (BCrypt.checkpw(password, user.getPasswordHash()) && user.isActive()) {
                // Update last login
                updateLastLogin(user.getId());
                user.updateLastLogin();
                return Optional.of(user);
            }
        }
        
        return Optional.empty();
    }

    public boolean existsByUsername(String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        
        long count = dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        }, username);
        
        return count > 0;
    }

    public boolean existsByEmail(String email) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        
        long count = dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        }, email);
        
        return count > 0;
    }

    // Utility methods
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return null;
        }
        
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setPhone(rs.getString("phone"));
        
        // Handle enums
        String statusStr = rs.getString("status");
        if (statusStr != null) {
            user.setStatus(User.UserStatus.valueOf(statusStr));
        }
        
        String roleStr = rs.getString("role");
        if (roleStr != null) {
            user.setRole(User.UserRole.valueOf(roleStr));
        }
        
        // Handle timestamps
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            user.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        Timestamp lastLogin = rs.getTimestamp("last_login");
        if (lastLogin != null) {
            user.setLastLogin(lastLogin.toLocalDateTime());
        }
        
        return user;
    }

    private List<User> mapResultSetToUserList(ResultSet rs) throws SQLException {
        List<User> users = new ArrayList<>();
        
        while (rs.next()) {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setFirstName(rs.getString("first_name"));
            user.setLastName(rs.getString("last_name"));
            user.setPhone(rs.getString("phone"));
            
            // Handle enums
            String statusStr = rs.getString("status");
            if (statusStr != null) {
                user.setStatus(User.UserStatus.valueOf(statusStr));
            }
            
            String roleStr = rs.getString("role");
            if (roleStr != null) {
                user.setRole(User.UserRole.valueOf(roleStr));
            }
            
            // Handle timestamps
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                user.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                user.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            Timestamp lastLogin = rs.getTimestamp("last_login");
            if (lastLogin != null) {
                user.setLastLogin(lastLogin.toLocalDateTime());
            }
            
            users.add(user);
        }
        
        return users;
    }
}