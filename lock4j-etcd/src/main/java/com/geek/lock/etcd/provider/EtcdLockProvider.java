package com.geek.lock.etcd.provider;

import com.geek.lock.core.AbstractLockProvider;
import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.lock.LockResponse;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class EtcdLockProvider extends AbstractLockProvider {

    private static final String LOCK_PREFIX = "/lock4j/";
    private static final long DEFAULT_WAIT_TIMEOUT = 30000L;
    private static final long WATCHDOG_INTERVAL = 10000L;

    private final Client etcdClient;
    private final ConcurrentHashMap<String, LockEntry> lockEntries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdogScheduler;
    private final String nodeId;

    public EtcdLockProvider(Client etcdClient, LockEventPublisher eventPublisher) {
        super(eventPublisher);
        this.etcdClient = etcdClient;
        this.nodeId = UUID.randomUUID().toString().replace("-", "");
        this.watchdogScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "lock4j-etcd-watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    private String getLockOwner() {
        return nodeId + ":" + Thread.currentThread().getId();
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        String key = LOCK_PREFIX + lockKey.getKey();
        String lockOwner = getLockOwner();
        
        LockEntry existingEntry = lockEntries.get(key);
        if (existingEntry != null && existingEntry.isOwner(lockOwner)) {
            existingEntry.incrementHoldCount();
            return true;
        }

        var lockClient = etcdClient.getLockClient();
        ByteSequence keyBytes = ByteSequence.from(key, StandardCharsets.UTF_8);
        
        long leaseTime = convertToMillis(options.getLeaseTime(), options.getTimeUnit());
        long waitTime = options.getWaitTime() <= 0 ? DEFAULT_WAIT_TIMEOUT : convertToMillis(options.getWaitTime(), options.getTimeUnit());

        long leaseId = 0;
        if (!options.isEnableWatchdog() && leaseTime > 0) {
            var leaseClient = etcdClient.getLeaseClient();
            CompletableFuture<LeaseGrantResponse> leaseFuture = leaseClient.grant(leaseTime / 1000);
            LeaseGrantResponse leaseResponse = leaseFuture.get(waitTime, TimeUnit.MILLISECONDS);
            leaseId = leaseResponse.getID();
        }

        CompletableFuture<LockResponse> lockFuture = lockClient.lock(keyBytes, leaseId);

        try {
            LockResponse response = lockFuture.get(waitTime, TimeUnit.MILLISECONDS);
            if (response != null) {
                ByteSequence acquiredKey = response.getKey();
                long acquiredLeaseId = leaseId > 0 ? leaseId : extractLeaseId(response);
                
                LockEntry entry = new LockEntry(acquiredKey, acquiredLeaseId, lockOwner);
                lockEntries.put(key, entry);
                
                if (options.isEnableWatchdog() && acquiredLeaseId > 0) {
                    startWatchdog(key, acquiredLeaseId, leaseTime > 0 ? leaseTime : WATCHDOG_INTERVAL * 3);
                }
                
                return true;
            }
            return false;
        } catch (TimeoutException e) {
            lockFuture.cancel(true);
            if (leaseId > 0) {
                try {
                    etcdClient.getLeaseClient().revoke(leaseId).get(5, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    log.warn("Failed to revoke lease {}", leaseId, ex);
                }
            }
            return false;
        } catch (Exception e) {
            lockFuture.cancel(true);
            throw e;
        }
    }

    private long extractLeaseId(LockResponse response) {
        try {
            var kvClient = etcdClient.getKVClient();
            ByteSequence key = response.getKey();
            CompletableFuture<GetResponse> getFuture = kvClient.get(key);
            GetResponse getResponse = getFuture.get(5, TimeUnit.SECONDS);
            if (!getResponse.getKvs().isEmpty()) {
                return getResponse.getKvs().get(0).getLease();
            }
        } catch (Exception e) {
            log.warn("Failed to extract lease id", e);
        }
        return 0;
    }

    @Override
    protected void doUnlock(LockKey lockKey) {
        String key = LOCK_PREFIX + lockKey.getKey();
        String lockOwner = getLockOwner();
        
        LockEntry entry = lockEntries.get(key);
        if (entry == null) {
            log.warn("No lock entry found for key: {}", key);
            return;
        }

        if (!entry.isOwner(lockOwner)) {
            throw new IllegalStateException("Lock not held by current thread: " + key);
        }

        if (entry.decrementHoldCount() > 0) {
            return;
        }

        try {
            stopWatchdog(key);
            
            var lockClient = etcdClient.getLockClient();
            lockClient.unlock(entry.getAcquiredKey()).get(5, TimeUnit.SECONDS);
            
            if (entry.getLeaseId() > 0) {
                try {
                    etcdClient.getLeaseClient().revoke(entry.getLeaseId()).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Failed to revoke lease {}", entry.getLeaseId(), e);
                }
            }
            
            lockEntries.remove(key);
        } catch (Exception e) {
            log.warn("Failed to release etcd lock: {}", key, e);
        }
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT || lockType == LockType.FAIR;
    }

    private void startWatchdog(String key, long leaseId, long leaseTime) {
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        
        ScheduledFuture<?> future = watchdogScheduler.scheduleAtFixedRate(() -> {
            try {
                var leaseClient = etcdClient.getLeaseClient();
                long ttl = leaseTime / 1000 + 1;
                CompletableFuture<LeaseKeepAliveResponse> keepAliveFuture = leaseClient.keepAliveOnce(leaseId);
                LeaseKeepAliveResponse response = keepAliveFuture.get(5, TimeUnit.SECONDS);
                if (response == null || response.getTTL() <= 0) {
                    log.warn("Lease keep alive failed for key: {}", key);
                    stopWatchdog(key);
                }
            } catch (Exception e) {
                log.error("Error keeping lease alive for key: {}", key, e);
                stopWatchdog(key);
            }
        }, WATCHDOG_INTERVAL / 3, WATCHDOG_INTERVAL / 3, TimeUnit.MILLISECONDS);
        
        futureRef.set(future);
        
        LockEntry entry = lockEntries.get(key);
        if (entry != null) {
            entry.setWatchdogFuture(future);
        }
    }

    private void stopWatchdog(String key) {
        LockEntry entry = lockEntries.get(key);
        if (entry != null && entry.getWatchdogFuture() != null) {
            entry.getWatchdogFuture().cancel(false);
            entry.setWatchdogFuture(null);
        }
    }

    private static class LockEntry {
        private final ByteSequence acquiredKey;
        private final long leaseId;
        private final String owner;
        private final AtomicInteger holdCount = new AtomicInteger(1);
        private volatile ScheduledFuture<?> watchdogFuture;

        public LockEntry(ByteSequence acquiredKey, long leaseId, String owner) {
            this.acquiredKey = acquiredKey;
            this.leaseId = leaseId;
            this.owner = owner;
        }

        public boolean isOwner(String checkOwner) {
            return this.owner.equals(checkOwner);
        }

        public void incrementHoldCount() {
            holdCount.incrementAndGet();
        }

        public int decrementHoldCount() {
            return holdCount.decrementAndGet();
        }

        public ByteSequence getAcquiredKey() {
            return acquiredKey;
        }

        public long getLeaseId() {
            return leaseId;
        }

        public ScheduledFuture<?> getWatchdogFuture() {
            return watchdogFuture;
        }

        public void setWatchdogFuture(ScheduledFuture<?> watchdogFuture) {
            this.watchdogFuture = watchdogFuture;
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
}