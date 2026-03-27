package com.geek.lock.local.provider;

import com.geek.lock.core.AbstractLockProvider;
import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class LocalLockProvider extends AbstractLockProvider {

    private static final String NAME = "local";

    private final ConcurrentHashMap<String, Lock> lockMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> readWriteLockMap = new ConcurrentHashMap<>();

    public LocalLockProvider(LockEventPublisher eventPublisher) {
        super(eventPublisher);
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        Lock lock = getOrCreateLock(lockKey);
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());

        if (waitTime <= 0) {
            lock.lock();
            return true;
        }

        return lock.tryLock(waitTime, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doUnlock(LockKey lockKey) {
        Lock lock = getLock(lockKey);
        if (lock != null) {
            try {
                lock.unlock();
            } catch (Exception e) {
                log.warn("Failed to unlock: {}", lockKey.getKey(), e);
            }
        }
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT
                || lockType == LockType.FAIR
                || lockType == LockType.READ
                || lockType == LockType.WRITE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    private Lock getOrCreateLock(LockKey lockKey) {
        String key = lockKey.getKey();
        LockType lockType = lockKey.getLockType();

        return switch (lockType) {
            case FAIR -> lockMap.computeIfAbsent(key, k -> new ReentrantLock(true));
            case READ -> {
                ReentrantReadWriteLock rwLock = readWriteLockMap.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
                yield rwLock.readLock();
            }
            case WRITE -> {
                ReentrantReadWriteLock rwLock = readWriteLockMap.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
                yield rwLock.writeLock();
            }
            default -> lockMap.computeIfAbsent(key, k -> new ReentrantLock());
        };
    }

    private Lock getLock(LockKey lockKey) {
        String key = lockKey.getKey();
        LockType lockType = lockKey.getLockType();

        return switch (lockType) {
            case READ, WRITE -> {
                ReentrantReadWriteLock rwLock = readWriteLockMap.get(key);
                yield rwLock != null
                        ? (lockType == LockType.READ ? rwLock.readLock() : rwLock.writeLock())
                        : null;
            }
            default -> lockMap.get(key);
        };
    }
}