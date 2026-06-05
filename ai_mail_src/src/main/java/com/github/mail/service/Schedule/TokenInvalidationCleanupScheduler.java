package com.github.mail.service.Schedule;

import com.github.mail.model.config.Properties.AccountAuthProperties;
import com.github.mail.service.User.ITokenInvalidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenInvalidationCleanupScheduler {

    private final ITokenInvalidationService tokenInvalidationService;
    private final AccountAuthProperties accountAuthProperties;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpired() {
        if (!accountAuthProperties.isServerLogoutEnabled()) {
            return;
        }
        int deleted = tokenInvalidationService.cleanupExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("清理过期 token 失效记录: {}", deleted);
        }
    }
}
