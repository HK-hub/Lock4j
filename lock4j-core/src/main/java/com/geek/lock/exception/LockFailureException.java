package com.geek.lock.exception;

import lombok.Getter;

@Getter
public class LockFailureException extends RuntimeException {

    private final String lockKey;
    private final long waitTime;

    public LockFailureException(String lockKey) {
        super(String.format("Failed to acquire lock: %s", lockKey));
        this.lockKey = lockKey;
        this.waitTime = -1;
    }

    public LockFailureException(String lockKey, long waitTime) {
        super(String.format("Failed to acquire lock '%s' after waiting %dms", lockKey, waitTime));
        this.lockKey = lockKey;
        this.waitTime = waitTime;
    }
}