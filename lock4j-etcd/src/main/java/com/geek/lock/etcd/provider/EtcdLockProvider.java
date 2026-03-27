package com.geek.lock.etcd.provider;

import com.geek.lock.core.AbstractLockProvider;
import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.lock.LockResponse;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class EtcdLockProvider extends AbstractLockProvider {

    private final Client etcdClient;

    public EtcdLockProvider(Client etcdClient, LockEventPublisher eventPublisher) {
        super(eventPublisher);
        this.etcdClient = etcdClient;
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        var lockClient = etcdClient.getLockClient();
        ByteSequence lockKeyBytes = ByteSequence.from(lockKey.getKey(), StandardCharsets.UTF_8);
        long leaseTime = convertToMillis(options.getLeaseTime(), options.getTimeUnit());

        CompletableFuture<LockResponse> future = lockClient.lock(lockKeyBytes, leaseTime);

        try {
            LockResponse response = future.get(options.getWaitTime() <= 0 ? leaseTime : options.getWaitTime(), TimeUnit.MILLISECONDS);
            return response != null;
        } catch (Exception e) {
            future.cancel(true);
            return false;
        }
    }

    @Override
    protected void doUnlock(LockKey lockKey) {
        try {
            var lockClient = etcdClient.getLockClient();
            ByteSequence lockKeyBytes = ByteSequence.from(lockKey.getKey(), StandardCharsets.UTF_8);
            lockClient.unlock(lockKeyBytes).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to release etcd lock: {}", lockKey.getKey(), e);
        }
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT || lockType == LockType.FAIR;
    }
}