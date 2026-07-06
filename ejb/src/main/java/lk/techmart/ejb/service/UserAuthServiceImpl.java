package lk.techmart.ejb.service;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import lk.techmart.core.dto.AuthenticatedUser;
import lk.techmart.core.entity.Role;
import lk.techmart.core.entity.User;
import lk.techmart.core.exception.DuplicateUserException;
import lk.techmart.core.service.UserAuthService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Stateless
public class UserAuthServiceImpl implements UserAuthService {

    private static final int DEFAULT_ROLE_ID = 1;

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    @Override
    public AuthenticatedUser register(String username, String email, String rawPassword) {
        username = normalizeRequired(username, "Username");
        email = normalizeRequired(email, "Email");
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        boolean exists = em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.username = :u OR u.email = :e", Long.class)
                .setParameter("u", username)
                .setParameter("e", email)
                .getSingleResult() > 0;

        if (exists) {
            throw new DuplicateUserException("Username or email already registered");
        }

        Role defaultRole = em.find(Role.class, DEFAULT_ROLE_ID);
        if (defaultRole == null) {
            throw new IllegalStateException("Default role id " + DEFAULT_ROLE_ID + " is missing from the roles table");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash.generate(rawPassword.toCharArray()));
        user.setRole(defaultRole);
        em.persist(user);
        em.flush();

        log.info("Registered new user: {}", username);
        return toDto(user);
    }

    @Override
    public AuthenticatedUser authenticate(String usernameOrEmail, String rawPassword) {
        usernameOrEmail = normalize(usernameOrEmail);
        if (usernameOrEmail.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return null;
        }

        User user = em.createQuery(
                        "SELECT u FROM User u JOIN FETCH u.role WHERE u.username = :v OR u.email = :v", User.class)
                .setParameter("v", usernameOrEmail)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (user == null) {
            return null;
        }

        if (!passwordHash.verify(rawPassword.toCharArray(), user.getPasswordHash())) {
            return null;
        }

        return toDto(user);
    }

    private AuthenticatedUser toDto(User user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getEmail(), user.getRole().getRoleName());
    }

    private String normalizeRequired(String value, String label) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
