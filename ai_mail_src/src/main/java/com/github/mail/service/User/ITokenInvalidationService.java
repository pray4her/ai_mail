package com.github.mail.service.User;

import java.time.LocalDateTime;

public interface ITokenInvalidationService {

    void invalidate(String jti, LocalDateTime expiresAt);

    boolean isInvalidated(String jti);

    int cleanupExpired(LocalDateTime now);
}
