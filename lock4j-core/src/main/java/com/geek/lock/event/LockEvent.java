package com.geek.lock.event;

import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockEventType;
import lombok.Data;

@Data
public class LockEvent {

    private final LockKey lockKey;
    private final LockEventType type;
    private final Throwable error;
    private final long timestamp = System.currentTimeMillis();

    public LockEvent(LockKey lockKey, LockEventType type, Throwable error) {
        this.lockKey = lockKey;
        this.type = type;
        this.error = error;
    }

    public static LockEvent beforeLock(LockKey lockKey) {
        return new LockEvent(lockKey, LockEventType.BEFORE_LOCK, null);
    }

    public static LockEvent afterLock(LockKey lockKey) {
        return new LockEvent(lockKey, LockEventType.AFTER_LOCK, null);
    }

    public static LockEvent lockFailed(LockKey lockKey) {
        return new LockEvent(lockKey, LockEventType.LOCK_FAILED, null);
    }

    public static LockEvent lockError(LockKey lockKey, Throwable error) {
        return new LockEvent(lockKey, LockEventType.LOCK_ERROR, error);
    }

    public static LockEvent beforeUnlock(LockKey lockKey) {
        return new LockEvent(lockKey, LockEventType.BEFORE_UNLOCK, null);
    }

    public static LockEvent afterUnlock(LockKey lockKey) {
        return new LockEvent(lockKey, LockEventType.AFTER_UNLOCK, null);
    }
}