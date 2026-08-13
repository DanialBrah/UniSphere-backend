package com.unisphere.backend.commerce.jobs.service;

import com.unisphere.backend.commerce.jobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ages every OPEN job whose application deadline has passed to CLOSED. Mirrors
 * {@code EventCompletionScheduler}: a single bulk UPDATE guarded by {@code status = OPEN}, so every
 * Render replica can run it with no distributed lock — a second instance ticking the same minute
 * matches zero rows the second time.
 *
 * <p>Deliberately no notification on this automatic path — same asymmetry as
 * {@code EventCompletionScheduler}: only a manual employer-initiated {@code changeStatus} call
 * notifies in-pipeline applicants.
 *
 * <p>{@code zone = "UTC"} governs when the cron fires, while {@code applicationDeadline} is a naive
 * DATE read against the JVM's zone — Render runs UTC, so the two agree. Daily cadence, not
 * per-minute: a deadline has day granularity, same reasoning as {@code lost-found.expiry-cron}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobDeadlineScheduler {

    private final JobRepository jobRepository;

    @Scheduled(cron = "${jobs.deadline-cron:0 0 1 * * *}", zone = "UTC")
    @Transactional
    public void closeExpiredJobs() {
        try {
            int closed = jobRepository.closeExpiredJobs(LocalDateTime.now(), LocalDate.now());
            if (closed > 0) {
                log.info("Marked {} job(s) CLOSED (deadline passed)", closed);
            }
        } catch (Exception ex) {
            // Spring logs an uncaught @Scheduled failure and keeps ticking; catching it keeps the
            // log a single meaningful line rather than a stack trace on every run.
            log.warn("Scheduled job deadline sweep failed: {}", ex.getMessage());
        }
    }
}
