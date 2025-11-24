package com.punith.chat.session;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RedisSessionService {

    private final StringRedisTemplate redisTemplate;

    public RedisSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String userSessionsKey(Long userId) {
        return "user_sessions:" + userId;
    }

    private String sessionKey(String sessionId) {
        return "session:" + sessionId;
    }

    public void registerSession(Long userId, String sessionId, String nodeId) {
        long now = Instant.now().toEpochMilli();


        redisTemplate.opsForSet().add(userSessionsKey(userId), sessionId);


        String key = sessionKey(sessionId);
        redisTemplate.opsForHash().put(key, "userId", userId.toString());
        redisTemplate.opsForHash().put(key, "nodeId", nodeId);
        redisTemplate.opsForHash().put(key, "connectedAt", Long.toString(now));
    }

    public void unregisterSession(Long userId, String sessionId) {

        redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
        redisTemplate.delete(sessionKey(sessionId));
    }
}
