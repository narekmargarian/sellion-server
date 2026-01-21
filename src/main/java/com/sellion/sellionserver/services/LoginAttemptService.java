package com.sellion.sellionserver.services;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {
    private final int MAX_ATTEMPT = 5;
    private final int BLOCK_DURATION_MIN = 5;
    private final Map<String, Integer> attemptsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> blockCache = new ConcurrentHashMap<>();

    public void loginSucceeded(String key) {
        attemptsCache.remove(key);
        blockCache.remove(key);
    }

    public void loginFailed(String key) {
        int attempts = attemptsCache.getOrDefault(key, 0);
        attempts++;
        attemptsCache.put(key, attempts);
        if (attempts >= MAX_ATTEMPT) {
            blockCache.put(key, System.currentTimeMillis() + (BLOCK_DURATION_MIN * 60 * 1000));
        }
    }

    public boolean isBlocked(String key) {
        if (!blockCache.containsKey(key)) return false;
        if (System.currentTimeMillis() > blockCache.get(key)) {
            blockCache.remove(key);
            attemptsCache.remove(key);
            return false;
        }
        return true;
    }

    public long getBlockTimeRemaining(String key) {
        return Math.max(0, (blockCache.getOrDefault(key, 0L) - System.currentTimeMillis()) / 1000);
    }

    public int getAttempts(String key) {
        return attemptsCache.getOrDefault(key, 0);
    }
}

