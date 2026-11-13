package com.tablebite.service;

import com.tablebite.database.UserRepository;
import com.tablebite.model.User;
import com.tablebite.model.UserRole;
import com.tablebite.util.LoggerUtil;
import com.tablebite.util.SecurityUtil;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registration, login, sessions and profile management.
 *
 * Passwords are hashed with PBKDF2 (see {@link SecurityUtil}); session tokens are
 * 256-bit random values returned to the client once, and only their SHA-256 hash is
 * stored in the database.
 */
public class AuthenticationService {
    private static final Logger LOG = LoggerUtil.get(AuthenticationService.class);
    private static final int MAX_FAILURES = 8;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);

    public record Session(String token, String expiresAt, User user) {
    }

    private final UserRepository users;
    private final Duration sessionTtl;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public AuthenticationService() {
        this(new UserRepository());
    }

    public AuthenticationService(UserRepository users) {
        this.users = users;
        long hours = 168;
        try {
            hours = Long.parseLong(System.getenv().getOrDefault("SESSION_TTL_HOURS", "168"));
        } catch (NumberFormatException ignored) {
            // keep the default
        }
        this.sessionTtl = Duration.ofHours(Math.max(1, hours));
    }

    // ------------------------------------------------------------------ auth

    public Session register(String fullName, String email, String phone, String password,
                            String userAgent) throws SQLException {
        String name = Validation.requireText(fullName, "Full name", 2, 120);
        String normalizedEmail = Validation.email(email);
        String normalizedPhone = Validation.optionalPhone(phone);
        Validation.password(password);
        if (users.emailExists(normalizedEmail, null)) {
            throw ServiceException.conflict("An account with this email already exists");
        }
        User user = users.create(name, normalizedEmail, normalizedPhone,
                SecurityUtil.hashPassword(password), UserRole.CUSTOMER);
        LOG.info("New customer registered: user #" + user.id());
        return openSession(user, userAgent);
    }

    public Session login(String email, String password, String userAgent, String clientKey)
            throws SQLException {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Email and password are required");
        }
        String limiterKey = email.trim().toLowerCase() + "|" + clientKey;
        checkRateLimit(limiterKey);
        UserRepository.Credentials credentials = users.findCredentialsByEmail(email.trim());
        boolean valid = credentials != null
                && SecurityUtil.verifyPassword(password, credentials.passwordHash());
        if (!valid) {
            recordFailure(limiterKey);
            throw ServiceException.unauthorized("Incorrect email or password");
        }
        if (!credentials.user().active()) {
            throw ServiceException.forbidden("This account has been disabled");
        }
        failures.remove(limiterKey);
        users.touchLogin(credentials.user().id());
        return openSession(users.findById(credentials.user().id()), userAgent);
    }

    public void logout(String token) throws SQLException {
        if (token != null && !token.isBlank()) {
            users.deleteSession(SecurityUtil.sha256Hex(token));
        }
    }

    /** Resolves a bearer token to its user, or null when missing/expired/invalid. */
    public User authenticate(String token) throws SQLException {
        if (token == null || token.isBlank() || token.length() > 200) {
            return null;
        }
        return users.findUserBySessionHash(SecurityUtil.sha256Hex(token));
    }

    /** Development fallback used only when no kitchen code is configured. */
    public static final String DEFAULT_KITCHEN_CODE = "kitchen123";

    /**
     * Kitchen access code (KITCHEN_ACCESS_CODE, or the older STAFF_ACCESS_CODE).
     * The kitchen screen sends it in the X-Staff-Access-Code header; it grants
     * kitchen rights only (accept / prepare / ready / reject, mark dishes sold out).
     */
    public static String kitchenCode() {
        String code = System.getenv("KITCHEN_ACCESS_CODE");
        if (code == null || code.isBlank()) {
            code = System.getenv("STAFF_ACCESS_CODE");
        }
        return code == null || code.isBlank() ? DEFAULT_KITCHEN_CODE : code;
    }

    /** Checks a kitchen code from an HTTP client, with the same lock-out as logins. */
    public User kitchenUser(String accessCode, String clientKey) {
        String key = "kitchen-code|" + clientKey;
        checkRateLimit(key);
        User user = legacyStaff(accessCode);
        if (user == null) {
            recordFailure(key);
            throw ServiceException.unauthorized("That kitchen code is not correct");
        }
        return user;
    }

    /** Returns the kitchen pseudo-user when the code matches, otherwise null. */
    public User legacyStaff(String accessCode) {
        if (accessCode == null || accessCode.isBlank()
                || !SecurityUtil.constantTimeEquals(kitchenCode(), accessCode)) {
            return null;
        }
        return new User(0, "Kitchen", null, null, UserRole.KITCHEN, true, null, null);
    }

    private Session openSession(User user, String userAgent) throws SQLException {
        String token = SecurityUtil.randomToken(32);
        Instant expires = Instant.now().plus(sessionTtl);
        users.createSession(user.id(), SecurityUtil.sha256Hex(token), expires, userAgent);
        return new Session(token, expires.toString(), user);
    }

    private void checkRateLimit(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            Instant cutoff = Instant.now().minus(FAILURE_WINDOW);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= MAX_FAILURES) {
                throw ServiceException.tooManyRequests(
                        "Too many failed sign-in attempts. Please try again in a few minutes.");
            }
        }
    }

    private void recordFailure(String key) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            attempts.addLast(Instant.now());
        }
    }

    // ------------------------------------------------------------------ profile

    public User updateProfile(User current, String fullName, String email, String phone)
            throws SQLException {
        String name = Validation.requireText(fullName, "Full name", 2, 120);
        String normalizedEmail = Validation.email(email);
        String normalizedPhone = Validation.optionalPhone(phone);
        if (users.emailExists(normalizedEmail, current.id())) {
            throw ServiceException.conflict("Another account already uses this email");
        }
        return users.updateProfile(current.id(), name, normalizedEmail, normalizedPhone);
    }

    public void changePassword(User current, String currentPassword, String newPassword)
            throws SQLException {
        String stored = users.findPasswordHash(current.id());
        if (!SecurityUtil.verifyPassword(currentPassword, stored)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        Validation.password(newPassword);
        users.updatePassword(current.id(), SecurityUtil.hashPassword(newPassword));
    }

    // ------------------------------------------------------------------ staff admin

    public List<User> listUsers(String role) throws SQLException {
        return users.findAll(role == null || role.isBlank() ? null : UserRole.parse(role));
    }

    public User createUser(String fullName, String email, String phone, String password,
                           String role) throws SQLException {
        String name = Validation.requireText(fullName, "Full name", 2, 120);
        String normalizedEmail = Validation.email(email);
        Validation.password(password);
        UserRole parsedRole = UserRole.parse(role);
        if (users.emailExists(normalizedEmail, null)) {
            throw ServiceException.conflict("An account with this email already exists");
        }
        return users.create(name, normalizedEmail, Validation.optionalPhone(phone),
                SecurityUtil.hashPassword(password), parsedRole);
    }

    public User updateAccess(User admin, long userId, String role, boolean active)
            throws SQLException {
        User target = users.findById(userId);
        if (target == null) {
            throw ServiceException.notFound("User");
        }
        UserRole parsedRole = UserRole.parse(role);
        if (target.id() == admin.id() && (parsedRole != UserRole.ADMIN || !active)) {
            throw new IllegalArgumentException("You cannot remove your own admin access");
        }
        return users.updateAccess(userId, parsedRole, active);
    }

    // ------------------------------------------------------------------ bootstrap

    /**
     * Ensures there is at least one administrator. Credentials come from ADMIN_EMAIL /
     * ADMIN_PASSWORD; when they are not set, development defaults are created and a
     * warning is logged. Demo kitchen/waiter/customer accounts are created unless
     * SEED_DEMO_ACCOUNTS=false.
     */
    public void bootstrapAccounts() throws SQLException {
        if (users.countByRole(UserRole.ADMIN) == 0) {
            String email = System.getenv("ADMIN_EMAIL");
            String password = System.getenv("ADMIN_PASSWORD");
            boolean defaults = email == null || email.isBlank() || password == null || password.isBlank();
            if (defaults) {
                email = "admin@tablebite.local";
                password = "Admin@12345";
            }
            createIfMissing("Restaurant Admin", email, password, UserRole.ADMIN);
            if (defaults) {
                LOG.warning("Created development admin " + email + " / " + password
                        + " - set ADMIN_EMAIL and ADMIN_PASSWORD in production and change this password.");
            } else {
                LOG.info("Created administrator account " + email);
            }
        }
        if (!"false".equalsIgnoreCase(System.getenv("SEED_DEMO_ACCOUNTS"))) {
            createIfMissing("Kitchen Team", "kitchen@tablebite.local", "Kitchen@12345", UserRole.KITCHEN);
            createIfMissing("Floor Staff", "waiter@tablebite.local", "Waiter@12345", UserRole.WAITER);
            createIfMissing("Demo Customer", "customer@tablebite.local", "Customer@12345", UserRole.CUSTOMER);
        }
        if (DEFAULT_KITCHEN_CODE.equals(kitchenCode())) {
            LOG.warning("Kitchen access code is the development default '" + DEFAULT_KITCHEN_CODE
                    + "' - set KITCHEN_ACCESS_CODE to your own code.");
        }
        int removed = users.deleteExpiredSessions();
        if (removed > 0) {
            LOG.info("Removed " + removed + " expired sessions");
        }
    }

    private void createIfMissing(String name, String email, String password, UserRole role)
            throws SQLException {
        if (!users.emailExists(email, null)) {
            users.create(name, email.toLowerCase(), null, SecurityUtil.hashPassword(password), role);
        }
    }
}
