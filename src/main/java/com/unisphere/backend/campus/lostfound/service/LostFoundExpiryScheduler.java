package com.unisphere.backend.campus.lostfound.service;

import com.unisphere.backend.campus.lostfound.repository.LostFoundItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Ages stale OPEN reports off the board so it does not fill with items nobody is looking for any
 * more. Mirrors {@code campus.news.service.NewsPublishScheduler}.
 *
 * <p>Daily rather than the news sweep's every-minute tick: expiry has day granularity, so a
 * per-minute schedule would be 1,440 pointless queries a day.
 *
 * <p>{@code zone = "UTC"} governs when the cron <em>fires</em>, while {@code createdAt} is a naive
 * DATETIME read against the JVM's zone — keep the container on UTC (Render already is) so the two
 * agree.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LostFoundExpiryScheduler {

    private final LostFoundItemRepository lostFoundItemRepository;

    @Value("${lost-found.expiry-days:60}")
    private int expiryDays;

    /**
     * The sweep is a single bulk UPDATE guarded by {@code status = OPEN}, so every replica can run
     * it without a distributed lock — a second instance ticking the same second simply matches zero
     * rows. Raising the interval only widens the window before a stale item drops out of the feed.
     */
    @Scheduled(cron = "${lost-found.expiry-cron:0 15 3 * * *}", zone = "UTC")
    @Transactional
    public void expireStaleItems() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int expired = lostFoundItemRepository.expireStaleItems(now.minusDays(expiryDays), now);
            if (expired > 0) {
                log.info("Expired {} stale lost-and-found item(s)", expired);
            }
        } catch (Exception ex) {
            // Spring logs an uncaught @Scheduled failure and keeps ticking; catching it keeps the
            // log a single meaningful line rather than a stack trace on every run.
            log.warn("Scheduled lost-and-found expiry sweep failed: {}", ex.getMessage());
        }
    }
}
