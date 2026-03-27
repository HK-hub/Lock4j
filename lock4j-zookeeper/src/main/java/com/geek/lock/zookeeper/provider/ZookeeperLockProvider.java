package com.geek.lock.zookeeper.provider;

import com.geek.lock.core.AbstractLockProvider;
import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ZookeeperLockProvider extends AbstractLockProvider {

    private static final String LOCK_PATH_PREFIX = "/lock4j/";

    private final CuratorFramework curatorFramework;

    public ZookeeperLockProvider(CuratorFramework curatorFramework, LockEventPublisher eventPublisher) {
        super(eventPublisher);
        this.curatorFramework = curatorFramework;
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        InterProcessMutex lock = getLock(lockKey);
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());

        if (waitTime <= 0) {
            lock.acquire();
            return true;
        }

        return lock.acquire(waitTime, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doUnlock(LockKey lockKey) {
        try {
            InterProcessMutex lock = getLock(lockKey);
            lock.release();
        } catch (Exception e) {
            log.warn("Failed to release zookeeper lock: {}", lockKey.getKey(), e);
        }
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT
                || lockType == LockType.READ
                || lockType == LockType.WRITE;
    }

    private InterProcessMutex getLock(LockKey lockKey) {
        String path = LOCK_PATH_PREFIX + lockKey.getKey().replace(":", "/");
        LockType lockType = lockKey.getLockType();

        return switch (lockType) {
            case READ -> new InterProcessReadWriteLock(curatorFramework, path).readLock();
            case WRITE -> new InterProcessReadWriteLock(curatorFramework, path).writeLock();
            default -> new InterProcessMutex(curatorFramework, path);
        };
    }
}