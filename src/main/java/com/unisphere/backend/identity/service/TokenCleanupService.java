package com.unisphere.backend.identity.service;

import com.unisphere.backend.identity.repository.PasswordResetTokenRepository;
import com.unisphere.backend.identity.repository.UserRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final UserRefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        int refreshDeleted = refreshTokenRepository.deleteAllExpiredBefore(now);
        int resetDeleted = passwordResetTokenRepository.deleteAllExpiredOrUsed(now);
        log.info("Token cleanup: removed {} expired refresh tokens, {} expired/used reset tokens",
                refreshDeleted, resetDeleted);
    }
}
