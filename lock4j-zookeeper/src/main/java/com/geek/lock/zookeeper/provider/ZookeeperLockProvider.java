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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ZookeeperLockProvider extends AbstractLockProvider {

    private static final String LOCK_PATH_PREFIX = "/lock4j/";
    private static final long DEFAULT_WAIT_TIMEOUT = 30000L;
    private static final long WATCHDOG_INTERVAL = 10000L;

    private final CuratorFramework curatorFramework;
    private final ConcurrentHashMap<String, LockHolder> lockCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InterProcessReadWriteLock> readWriteLockCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdogScheduler;

    public ZookeeperLockProvider(CuratorFramework curatorFramework, LockEventPublisher eventPublisher) {
        super(eventPublisher);
        this.curatorFramework = curatorFramework;
        this.watchdogScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "lock4j-zookeeper-watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        String path = LOCK_PATH_PREFIX + lockKey.getKey().replace(":", "/");
        LockType lockType = lockKey.getLockType();

        long waitTime = options.getWaitTime() <= 0 ? DEFAULT_WAIT_TIMEOUT : convertToMillis(options.getWaitTime(), options.getTimeUnit());
        long leaseTime = convertToMillis(options.getLeaseTime(), options.getTimeUnit());

        LockHolder holder = lockCache.computeIfAbsent(path, k -> new LockHolder(getOrCreateLock(path, lockType)));

        InterProcessMutex lock = holder.getLock();
        String lockOwner = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();

        if (holder.isHeldByCurrentThread(lockOwner)) {
            holder.incrementHoldCount(lockOwner);
            return true;
        }

        boolean acquired;
        if (options.isEnableWatchdog()) {
            acquired = lock.acquire(waitTime, TimeUnit.MILLISECONDS);
            if (acquired) {
                holder.setOwner(lockOwner);
                holder.incrementHoldCount(lockOwner);
                startWatchdog(path, holder, leaseTime > 0 ? leaseTime : WATCHDOG_INTERVAL * 3);
            }
        } else {
            acquired = lock.acquire(waitTime, TimeUnit.MILLISECONDS);
            if (acquired) {
                holder.setOwner(lockOwner);
                holder.incrementHoldCount(lockOwner);
                if (leaseTime > 0) {
                    startWatchdog(path, holder, leaseTime);
                }
            }
        }

        return acquired;
    }

    @Override
    protected void doUnlock(LockKey lockKey) {
        String path = LOCK_PATH_PREFIX + lockKey.getKey().replace(":", "/");
        String lockOwner = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();

        LockHolder holder = lockCache.get(path);
        if (holder == null) {
            log.warn("No lock holder found for path: {}", path);
            return;
        }

        if (!holder.isHeldByCurrentThread(lockOwner)) {
            throw new IllegalStateException("Lock not held by current thread: " + path);
        }

        int holdCount = holder.decrementHoldCount(lockOwner);
        if (holdCount > 0) {
            return;
        }

        stopWatchdog(path);

        try {
            InterProcessMutex lock = holder.getLock();
            if (lock.isAcquiredInThisProcess()) {
                lock.release();
            }
            holder.setOwner(null);
        } catch (Exception e) {
            log.warn("Failed to release zookeeper lock: {}", path, e);
        }
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT
                || lockType == LockType.READ
                || lockType == LockType.WRITE;
    }

    private InterProcessMutex getOrCreateLock(String path, LockType lockType) {
        return switch (lockType) {
            case READ -> {
                InterProcessReadWriteLock rwLock = readWriteLockCache.computeIfAbsent(path, 
                        p -> new InterProcessReadWriteLock(curatorFramework, p));
                yield rwLock.readLock();
            }
            case WRITE -> {
                InterProcessReadWriteLock rwLock = readWriteLockCache.computeIfAbsent(path, 
                        p -> new InterProcessReadWriteLock(curatorFramework, p));
                yield rwLock.writeLock();
            }
            default -> new InterProcessMutex(curatorFramework, path);
        };
    }

    private void startWatchdog(String path, LockHolder holder, long leaseTime) {
        ScheduledFuture<?> future = watchdogScheduler.scheduleAtFixedRate(() -> {
            try {
                InterProcessMutex lock = holder.getLock();
                if (!lock.isAcquiredInThisProcess()) {
                    log.warn("Lock not held, stopping watchdog: {}", path);
                    stopWatchdog(path);
                    return;
                }
            } catch (Exception e) {
                log.error("Error checking lock status: {}", path, e);
                stopWatchdog(path);
            }
        }, leaseTime / 3, leaseTime / 3, TimeUnit.MILLISECONDS);

        holder.setWatchdogFuture(future);
    }

    private void stopWatchdog(String path) {
        LockHolder holder = lockCache.get(path);
        if (holder != null && holder.getWatchdogFuture() != null) {
            holder.getWatchdogFuture().cancel(false);
            holder.setWatchdogFuture(null);
        }
    }

    public void shutdown() {
        watchdogScheduler.shutdown();
        try {
            if (!watchdogScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                watchdogScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            watchdogScheduler.shutdownNow();
        }
    }

    private static class LockHolder {
        private final InterProcessMutex lock;
        private volatile String owner;
        private final ConcurrentHashMap<String, AtomicInteger> holdCounts = new ConcurrentHashMap<>();
        private volatile ScheduledFuture<?> watchdogFuture;

        public LockHolder(InterProcessMutex lock) {
            this.lock = lock;
        }

        public InterProcessMutex getLock() {
            return lock;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public boolean isHeldByCurrentThread(String threadId) {
            return threadId.equals(owner);
        }

        public void incrementHoldCount(String threadId) {
            holdCounts.computeIfAbsent(threadId, k -> new AtomicInteger(0)).incrementAndGet();
        }

        public int decrementHoldCount(String threadId) {
            AtomicInteger count = holdCounts.get(threadId);
            if (count != null) {
                int newCount = count.decrementAndGet();
                if (newCount <= 0) {
                    holdCounts.remove(threadId);
                }
                return newCount;
            }
            return 0;
        }

        public ScheduledFuture<?> getWatchdogFuture() {
            return watchdogFuture;
        }

        public void setWatchdogFuture(ScheduledFuture<?> watchdogFuture) {
            this.watchdogFuture = watchdogFuture;
        }
    }
}