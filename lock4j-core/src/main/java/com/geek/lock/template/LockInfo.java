package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import lombok.Getter;

import static java.util.Objects.nonNull;

@Getter
public class LockInfo {

    private final LockKey lockKey;
    private final LockProvider provider;
    private final boolean acquired;

    public LockInfo(LockKey lockKey, LockProvider provider, boolean acquired) {
        this.lockKey = lockKey;
        this.provider = provider;
        this.acquired = acquired;
    }

    public boolean isAcquired() {
        return acquired;
    }

    public boolean isReleasable() {
        return nonNull(lockKey) && nonNull(provider) && acquired;
    }
}