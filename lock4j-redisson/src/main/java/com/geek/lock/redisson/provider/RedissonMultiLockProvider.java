package com.geek.lock.redisson.provider;

import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedissonMultiLockProvider extends RedissonLockProvider {

    private final RedissonClient redissonClient;

    public RedissonMultiLockProvider(RedissonClient redissonClient, LockEventPublisher eventPublisher) {
        super(redissonClient, eventPublisher);
        this.redissonClient = redissonClient;
    }

    public boolean tryMultiLock(LockKey[] lockKeys, LockOptions options) throws Exception {
        List<RLock> locks = new ArrayList<>();
        for (LockKey lockKey : lockKeys) {
            if (lockKey.getLockType() == LockType.READ || lockKey.getLockType() == LockType.WRITE) {
                log.warn("MultiLock does not support READ/WRITE lock types, using REENTRANT instead");
            }
            locks.add(redissonClient.getLock(lockKey.getKey()));
        }

        RLock multiLock = redissonClient.getMultiLock(locks.toArray(new RLock[0]));
        
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());
        long leaseTime = convertToMillis(options.getLeaseTime(), options.getTimeUnit());

        if (options.isEnableWatchdog()) {
            return multiLock.tryLock(waitTime, TimeUnit.MILLISECONDS);
        } else {
            return multiLock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
        }
    }

    public void unlockMulti(LockKey[] lockKeys) {
        List<RLock> locks = new ArrayList<>();
        for (LockKey lockKey : lockKeys) {
            locks.add(redissonClient.getLock(lockKey.getKey()));
        }

        RLock multiLock = redissonClient.getMultiLock(locks.toArray(new RLock[0]));
        if (multiLock.isHeldByCurrentThread()) {
            multiLock.unlock();
        }
    }

    public boolean tryRedLock(LockKey[] lockKeys, LockOptions options) throws Exception {
        List<RLock> locks = new ArrayList<>();
        for (LockKey lockKey : lockKeys) {
            locks.add(redissonClient.getLock(lockKey.getKey()));
        }

        RLock redLock = redissonClient.getRedLock(locks.toArray(new RLock[0]));
        
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());
        long leaseTime = convertToMillis(options.getLeaseTime(), options.getTimeUnit());

        if (options.isEnableWatchdog()) {
            return redLock.tryLock(waitTime, TimeUnit.MILLISECONDS);
        } else {
            return redLock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
        }
    }

    public void unlockRedLock(LockKey[] lockKeys) {
        List<RLock> locks = new ArrayList<>();
        for (LockKey lockKey : lockKeys) {
            locks.add(redissonClient.getLock(lockKey.getKey()));
        }

        RLock redLock = redissonClient.getRedLock(locks.toArray(new RLock[0]));
        if (redLock.isHeldByCurrentThread()) {
            redLock.unlock();
        }
    }
}