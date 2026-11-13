package com.tablebite.database;

import com.tablebite.model.User;
import com.tablebite.model.UserRole;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Users and their login sessions. Only token hashes are stored, never raw tokens. */
public class UserRepository {

    public record Credentials(User user, String passwordHash) {
    }

    private static final String USER_COLUMNS =
            "u.user_id, u.full_name, u.email, u.phone, u.role, u.active, u.created_at, u.last_login_at";

    public User create(String fullName, String email, String phone, String passwordHash,
                       UserRole role) throws SQLException {
        String sql = """
                INSERT INTO users (full_name, email, phone, password_hash, role)
                VALUES (?, ?, ?, ?, ?)
                RETURNING user_id
                """;
        long id = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, fullName);
                statement.setString(2, email);
                statement.setString(3, phone);
                statement.setString(4, passwordHash);
                statement.setString(5, role.name());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
        return findById(id);
    }

    public User findById(long id) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + USER_COLUMNS + " FROM users u WHERE u.user_id = ?")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? map(result) : null;
                }
            }
        });
    }

    public Credentials findCredentialsByEmail(String email) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + USER_COLUMNS + ", u.password_hash FROM users u "
                            + "WHERE LOWER(u.email) = LOWER(?)")) {
                statement.setString(1, email);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next()
                            ? new Credentials(map(result), result.getString("password_hash"))
                            : null;
                }
            }
        });
    }

    public String findPasswordHash(long userId) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT password_hash FROM users WHERE user_id = ?")) {
                statement.setLong(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getString(1) : null;
                }
            }
        });
    }

    public boolean emailExists(String email, Long excludingUserId) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?) AND user_id <> ?")) {
                statement.setString(1, email);
                statement.setLong(2, excludingUserId == null ? -1 : excludingUserId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    public List<User> findAll(UserRole role) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + " FROM users u"
                + (role == null ? "" : " WHERE u.role = ?")
                + " ORDER BY u.role, u.full_name";
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (role != null) {
                    statement.setString(1, role.name());
                }
                List<User> users = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        users.add(map(result));
                    }
                }
                return users;
            }
        });
    }

    public int countByRole(UserRole role) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM users WHERE role = ? AND active")) {
                statement.setString(1, role.name());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            }
        });
    }

    public User updateProfile(long userId, String fullName, String email, String phone)
            throws SQLException {
        Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE users SET full_name = ?, email = ?, phone = ?, updated_at = NOW()
                    WHERE user_id = ?""")) {
                statement.setString(1, fullName);
                statement.setString(2, email);
                statement.setString(3, phone);
                statement.setLong(4, userId);
                return statement.executeUpdate();
            }
        });
        return findById(userId);
    }

    public User updateAccess(long userId, UserRole role, boolean active) throws SQLException {
        Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE users SET role = ?, active = ?, updated_at = NOW()
                    WHERE user_id = ?""")) {
                statement.setString(1, role.name());
                statement.setBoolean(2, active);
                statement.setLong(3, userId);
                return statement.executeUpdate();
            }
        });
        if (!active) {
            deleteSessionsForUser(userId);
        }
        return findById(userId);
    }

    public void updatePassword(long userId, String passwordHash) throws SQLException {
        Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE user_id = ?")) {
                statement.setString(1, passwordHash);
                statement.setLong(2, userId);
                return statement.executeUpdate();
            }
        });
    }

    public void touchLogin(long userId) throws SQLException {
        Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET last_login_at = NOW() WHERE user_id = ?")) {
                statement.setLong(1, userId);
                return statement.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------ sessions

    public void createSession(long userId, String tokenHash, Instant expiresAt, String userAgent)
            throws SQLException {
        Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO user_sessions (user_id, token_hash, expires_at, user_agent)
                    VALUES (?, ?, ?, ?)""")) {
                statement.setLong(1, userId);
                statement.setString(2, tokenHash);
                statement.setTimestamp(3, Timestamp.from(expiresAt));
                statement.setString(4, userAgent == null ? null
                        : userAgent.substring(0, Math.min(255, userAgent.length())));
                return statement.executeUpdate();
            }
        });
    }

    public User findUserBySessionHash(String tokenHash) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + USER_COLUMNS + " FROM user_sessions s "
                            + "JOIN users u ON u.user_id = s.user_id "
                            + "WHERE s.token_hash = ? AND s.expires_at > NOW() AND u.active")) {
                statement.setString(1, tokenHash);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? map(result) : null;
                }
            }
        });
    }

    public void deleteSession(String tokenHash) throws SQLException {
        Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM user_sessions WHERE token_hash = ?")) {
                statement.setString(1, tokenHash);
                return statement.executeUpdate();
            }
        });
    }

    public void deleteSessionsForUser(long userId) throws SQLException {
        Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM user_sessions WHERE user_id = ?")) {
                statement.setLong(1, userId);
                return statement.executeUpdate();
            }
        });
    }

    public int deleteExpiredSessions() throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM user_sessions WHERE expires_at <= NOW()")) {
                return statement.executeUpdate();
            }
        });
    }

    static User map(ResultSet result) throws SQLException {
        return new User(
                result.getLong("user_id"),
                result.getString("full_name"),
                result.getString("email"),
                result.getString("phone"),
                UserRole.valueOf(result.getString("role")),
                result.getBoolean("active"),
                Jdbc.iso(result, "created_at"),
                Jdbc.iso(result, "last_login_at"));
    }
}
