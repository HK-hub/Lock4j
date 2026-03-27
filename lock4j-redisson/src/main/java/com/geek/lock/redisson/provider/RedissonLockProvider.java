package com.geek.lock.redisson.provider;

import com.geek.lock.core.AbstractLockProvider;
import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

@Slf4j
public class RedissonLockProvider extends AbstractLockProvider {

    private final RedissonClient redissonClient;

    public RedissonLockProvider(RedissonClient redissonClient, LockEventPublisher eventPublisher) {
        super(eventPublisher);
        this.redissonClient = redissonClient;
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        RLock lock = getLock(lockKey);
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());
        long leaseTime = convertToMillis(options.getLeaseTime(), options.getTimeUnit());

        if (options.isEnableWatchdog()) {
            return lock.tryLock(waitTime, TimeUnit.MILLISECONDS);
        } else {
            return lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void doUnlock(LockKey lockKey) {
        RLock lock = getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT
                || lockType == LockType.FAIR
                || lockType == LockType.READ
                || lockType == LockType.WRITE;
    }

    private RLock getLock(LockKey lockKey) {
        String key = lockKey.getKey();
        LockType lockType = lockKey.getLockType();

        return switch (lockType) {
            case FAIR -> redissonClient.getFairLock(key);
            case READ -> redissonClient.getReadWriteLock(key).readLock();
            case WRITE -> redissonClient.getReadWriteLock(key).writeLock();
            default -> redissonClient.getLock(key);
        };
    }
}