package com.unisphere.backend.identity.repository;

import com.unisphere.backend.identity.entity.UserRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {

    Optional<UserRefreshToken> findByTokenHash(String tokenHash);

    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM UserRefreshToken t WHERE t.tokenHash = :tokenHash")
    int deleteByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM UserRefreshToken t WHERE t.userId = :userId")
    int deleteByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM UserRefreshToken t WHERE t.expiresAt < :now")
    int deleteAllExpiredBefore(Instant now);
}
