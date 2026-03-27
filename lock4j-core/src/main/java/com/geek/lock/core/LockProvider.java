package com.geek.lock.core;

import com.geek.lock.enums.LockType;
import com.geek.lock.model.LockOptions;
import lombok.NonNull;

import java.util.concurrent.TimeUnit;

public interface LockProvider extends java.util.concurrent.locks.Lock {

    boolean tryLock(LockKey lockKey, LockOptions options);

    void unlock(LockKey lockKey);

    boolean supports(LockType lockType);

    @Override
    default void lock() {
        throw new UnsupportedOperationException("Use tryLock(LockKey, LockOptions) instead");
    }

    @Override
    default void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException("Use tryLock(LockKey, LockOptions) instead");
    }

    @Override
    default boolean tryLock() {
        throw new UnsupportedOperationException("Use tryLock(LockKey, LockOptions) instead");
    }

    @Override
    default boolean tryLock(long time, @NonNull TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException("Use tryLock(LockKey, LockOptions) instead");
    }

    @Override
    default void unlock() {
        throw new UnsupportedOperationException("Use unlock(LockKey) instead");
    }

    @Override
    default java.util.concurrent.locks.Condition newCondition() {
        throw new UnsupportedOperationException("Condition is not supported");
    }
}