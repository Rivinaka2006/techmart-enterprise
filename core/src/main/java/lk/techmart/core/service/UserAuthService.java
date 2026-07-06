package lk.techmart.core.service;

import lk.techmart.core.dto.AuthenticatedUser;

public interface UserAuthService {
    AuthenticatedUser register(String username, String email, String rawPassword);
    AuthenticatedUser authenticate(String usernameOrEmail, String rawPassword);
}
