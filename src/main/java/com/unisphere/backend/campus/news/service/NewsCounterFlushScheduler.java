package com.unisphere.backend.campus.news.service;

import com.unisphere.backend.campus.news.repository.NewsArticleRepository;
import com.unisphere.backend.campus.news.repository.NewsCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Drains the Redis view/like deltas into MySQL once a minute.
 *
 * <p>A near-copy of {@code social.posting.service.LikeFlushScheduler} rather than an extension of
 * it: that class lives in social/posting and would need campus.news.repository imports, making it
 * a social -> campus dependency — the worse direction of the two. The right consolidation is a
 * shared common/counter flusher with a registry of (key prefix -> increment callback); worth doing
 * once a third module needs counters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCounterFlushScheduler {

    private final RedisTemplate<String, Long> redisTemplate;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsCommentRepository newsCommentRepository;

    @Scheduled(fixedRate = 60_000)
    public void flushCounters() {
        try {
            flush(NewsService.REDIS_NEWS_LIKES, newsArticleRepository::incrementLikesCount);
            flush(NewsService.REDIS_NEWS_VIEWS, newsArticleRepository::incrementViewsCount);
            flush(NewsCommentService.REDIS_NEWS_COMMENT_LIKES, newsCommentRepository::incrementLikesCount);
        } catch (Exception ex) {
            log.warn("NewsCounterFlushScheduler skipped — Redis unavailable: {}", ex.getMessage());
        }
    }

    private void flush(String prefix, BiConsumer<Long, Long> increment) {
        for (String key : scanKeys(prefix + "*")) {
            Long delta = null;
            try {
                delta = redisTemplate.opsForValue().getAndSet(key, 0L);
                if (delta == null || delta == 0) { redisTemplate.delete(key); continue; }
                Long id = Long.parseLong(key.substring(prefix.length()));
                increment.accept(id, delta);
                redisTemplate.delete(key);
            } catch (Exception ex) {
                log.warn("Failed to flush {} for key {}: {}", prefix, key, ex.getMessage());
                restoreDelta(key, delta);
            }
        }
    }

    private void restoreDelta(String key, Long delta) {
        if (delta == null || delta == 0) return;
        try {
            redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception ex) {
            log.error("Delta {} for key {} lost — Redis restore failed: {}", delta, key, ex.getMessage());
        }
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = redisTemplate.execute((RedisCallback<List<String>>) connection -> {
            List<String> result = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            } catch (Exception ex) {
                log.warn("Redis SCAN failed for pattern {}: {}", pattern, ex.getMessage());
            }
            return result;
        });
        return keys != null ? keys : List.of();
    }
}
