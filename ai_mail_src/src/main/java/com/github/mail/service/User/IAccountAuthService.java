package com.github.mail.service.User;

import com.github.mail.repo.User.domain.Users;

public interface IAccountAuthService {

    Users register(String username, String password);

    String login(String username, String password);

    Users getCurrentUser(String username);

    void changePassword(String username, String oldPassword, String newPassword);

    void logout(String jti, java.time.LocalDateTime expiresAt);
}
