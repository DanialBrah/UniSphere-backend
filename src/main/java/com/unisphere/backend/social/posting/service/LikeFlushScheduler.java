package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.social.posting.repository.CommentRepository;
import com.unisphere.backend.social.posting.repository.PostRepository;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeFlushScheduler {

    private final RedisTemplate<String, Long> redisTemplate;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Scheduled(fixedRate = 60_000)
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
        for (String key : scanKeys("post:likes:*")) {
            try {
                Long delta = redisTemplate.opsForValue().get(key);
                if (delta == null || delta == 0) continue;
                Long postId = Long.parseLong(key.substring("post:likes:".length()));
                postRepository.incrementLikesCount(postId, delta);
                redisTemplate.delete(key);
            } catch (Exception ex) {
                log.warn("Failed to flush post likes for key {}: {}", key, ex.getMessage());
            }
        }
    }

    private void flushPostViews() {
        for (String key : scanKeys("post:views:*")) {
            try {
                Long delta = redisTemplate.opsForValue().get(key);
                if (delta == null || delta == 0) continue;
                Long postId = Long.parseLong(key.substring("post:views:".length()));
                postRepository.incrementViewsCount(postId, delta);
                redisTemplate.delete(key);
            } catch (Exception ex) {
                log.warn("Failed to flush post views for key {}: {}", key, ex.getMessage());
            }
        }
    }

    private void flushCommentLikes() {
        for (String key : scanKeys("comment:likes:*")) {
            try {
                Long delta = redisTemplate.opsForValue().get(key);
                if (delta == null || delta == 0) continue;
                Long commentId = Long.parseLong(key.substring("comment:likes:".length()));
                commentRepository.incrementLikesCount(commentId, delta);
                redisTemplate.delete(key);
            } catch (Exception ex) {
                log.warn("Failed to flush comment likes for key {}: {}", key, ex.getMessage());
            }
        }
    }

    private List<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<List<String>>) connection -> {
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
    }
}
