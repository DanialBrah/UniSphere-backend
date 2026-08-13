package com.unisphere.backend.commerce.events.service;

import com.unisphere.backend.commerce.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Ages every ended PUBLISHED event to COMPLETED. Mirrors {@code LostFoundExpiryScheduler}: a single
 * bulk UPDATE guarded by {@code status = PUBLISHED}, so every Render replica can run it with no
 * distributed lock — a second instance ticking the same minute matches zero rows the second time.
 *
 * <p>{@code zone = "UTC"} governs when the cron fires, while {@code endDatetime} is a naive DATETIME
 * read against the JVM's zone — Render runs UTC, so the two agree.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventCompletionScheduler {

    private final EventRepository eventRepository;

    @Scheduled(cron = "${events.completion-cron:0 */15 * * * *}", zone = "UTC")
    @Transactional
    public void completeEndedEvents() {
        try {
            int completed = eventRepository.completeEndedEvents(LocalDateTime.now());
            if (completed > 0) {
                log.info("Marked {} event(s) COMPLETED", completed);
            }
        } catch (Exception ex) {
            // Spring logs an uncaught @Scheduled failure and keeps ticking; catching it keeps the
            // log a single meaningful line rather than a stack trace on every run.
            log.warn("Scheduled event completion sweep failed: {}", ex.getMessage());
        }
    }
}
