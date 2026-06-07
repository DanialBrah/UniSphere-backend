package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.social.posting.repository.CommentRepository;
import com.unisphere.backend.social.posting.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

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
        Set<String> keys = redisTemplate.keys("post:likes:*");
        if (keys == null || keys.isEmpty()) return;

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
        Set<String> keys = redisTemplate.keys("post:views:*");
        if (keys == null || keys.isEmpty()) return;

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
        Set<String> keys = redisTemplate.keys("comment:likes:*");
        if (keys == null || keys.isEmpty()) return;

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
}
