package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import com.geek.lock.factory.LockProviderFactory;
import com.geek.lock.model.LockOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@RequiredArgsConstructor
public class LockTemplate {

    private final LockProviderFactory providerFactory;

    public LockInfo lock(String key, long leaseTime, long waitTime) {
        return lock(key, leaseTime, waitTime, null);
    }

    public LockInfo lock(String key, long leaseTime, long waitTime,
                         Class<? extends LockProvider> providerClass) {
        LockRequest request = LockRequest.builder()
                .key(key)
                .leaseTime(leaseTime)
                .waitTime(waitTime)
                .provider(providerClass)
                .build();
        return lock(request);
    }

    public LockInfo lock(LockRequest request) {
        if (isNull(request) || isNull(request.getKey())) {
            throw new IllegalArgumentException("Lock key cannot be null");
        }

        LockProvider provider = resolveProvider(request.getProvider());
        if (isNull(provider)) {
            throw new IllegalStateException("No LockProvider available");
        }

        LockKey lockKey = buildLockKey(request);
        LockOptions options = buildLockOptions(request);

        boolean acquired = provider.tryLock(lockKey, options);
        
        return new LockInfo(lockKey, provider, acquired);
    }

    public void releaseLock(LockInfo lockInfo) {
        if (isNull(lockInfo) || !lockInfo.isReleasable()) {
            return;
        }

        try {
            lockInfo.getProvider().unlock(lockInfo.getLockKey());
        } catch (Exception e) {
            log.warn("Failed to release lock: {}", lockInfo.getLockKey().getKey(), e);
        }
    }

    private LockProvider resolveProvider(Class<? extends LockProvider> providerClass) {
        if (nonNull(providerClass) && providerClass != LockProvider.class) {
            return providerFactory.getProvider(providerClass);
        }

        if (providerFactory.hasPrimaryProvider()) {
            return providerFactory.getPrimaryProvider();
        }

        return providerFactory.getFirstProvider();
    }

    private LockKey buildLockKey(LockRequest request) {
        return LockKey.builder()
                .key(request.getKey())
                .lockType(request.getLockType())
                .waitTime(request.getWaitTime())
                .leaseTime(request.getLeaseTime())
                .timeUnit(request.getTimeUnit())
                .build();
    }

    private LockOptions buildLockOptions(LockRequest request) {
        LockOptions options = new LockOptions();
        options.setWaitTime(request.getWaitTime());
        options.setLeaseTime(request.getLeaseTime());
        options.setTimeUnit(request.getTimeUnit());
        return options;
    }
}