package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.social.posting.repository.CommentRepository;
import com.unisphere.backend.social.posting.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeFlushScheduler {

    private final RedisTemplate<String, Long> redisTemplate;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void flushCounters() {
        try {
            flushPostLikes();
            flushPostViews();
            flushCommentLikes();
        } catch (Exception ex) {
            log.warn("LikeFlushScheduler skipped — Redis unavailable: {}", ex.getMessage());
        }
    }

    private void flushPostLikes() {
        List<String> keys = scanKeys("post:likes:*");
        for (String key : keys) {
            try {
                Long delta = redisTemplate.opsForValue().getAndDelete(key);
                if (delta == null || delta == 0) continue;
                Long postId = Long.parseLong(key.substring("post:likes:".length()));
                postRepository.incrementLikesCount(postId, delta);
            } catch (Exception ex) {
                log.warn("Failed to flush post likes for key {}: {}", key, ex.getMessage());
            }
        }
    }

    private void flushPostViews() {
        List<String> keys = scanKeys("post:views:*");
        for (String key : keys) {
            try {
                Long delta = redisTemplate.opsForValue().getAndDelete(key);
                if (delta == null || delta == 0) continue;
                Long postId = Long.parseLong(key.substring("post:views:".length()));
                postRepository.incrementViewsCount(postId, delta);
            } catch (Exception ex) {
                log.warn("Failed to flush post views for key {}: {}", key, ex.getMessage());
            }
        }
    }

    private void flushCommentLikes() {
        List<String> keys = scanKeys("comment:likes:*");
        for (String key : keys) {
            try {
                Long delta = redisTemplate.opsForValue().getAndDelete(key);
                if (delta == null || delta == 0) continue;
                Long commentId = Long.parseLong(key.substring("comment:likes:".length()));
                commentRepository.incrementLikesCount(commentId, delta);
            } catch (Exception ex) {
                log.warn("Failed to flush comment likes for key {}: {}", key, ex.getMessage());
            }
        }
    }

    /**
     * Uses SCAN instead of KEYS to avoid blocking Redis on large keyspaces.
     */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        } catch (Exception ex) {
            log.warn("Failed to scan Redis keys for pattern {}: {}", pattern, ex.getMessage());
        }
        return keys;
    }
}
