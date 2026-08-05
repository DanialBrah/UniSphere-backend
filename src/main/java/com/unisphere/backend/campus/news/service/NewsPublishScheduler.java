package com.unisphere.backend.campus.news.service;

import com.unisphere.backend.campus.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Flips scheduled articles live once their {@code scheduledAt} comes due.
 *
 * <p>Timezone note: {@code zone = "UTC"} governs when the cron <em>fires</em>, while
 * {@code LocalDateTime.now()} uses the JVM default zone and {@code scheduled_at} is a naive
 * DATETIME. On Render the container runs UTC so the two agree; on a developer machine in another
 * zone they do not, and {@code scheduledAt} is effectively server-local.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsPublishScheduler {

    private final NewsArticleRepository newsArticleRepository;

    /**
     * One bulk UPDATE rather than select-then-save. The WHERE clause carries status = DRAFT, so a
     * second replica running the same tick matches zero rows instead of double-publishing — the
     * job is safe on every instance with no distributed lock and no SELECT ... FOR UPDATE.
     *
     * <p>The trade-off: nothing here can fan out a per-article notification, since that would need
     * the ids up front and therefore a lock. Deliberately out of scope.
     */
    @Scheduled(cron = "${news.publish-cron:0 * * * * *}", zone = "UTC")
    @Transactional
    public void publishDueArticles() {
        try {
            int published = newsArticleRepository.publishDueArticles(LocalDateTime.now());
            if (published > 0) {
                log.info("Published {} scheduled news article(s)", published);
            }
        } catch (Exception ex) {
            // Spring logs an uncaught @Scheduled failure but the next tick still runs; catching
            // here keeps the log line meaningful rather than a stack trace every minute.
            log.warn("Scheduled news publish sweep failed: {}", ex.getMessage());
        }
    }
}
