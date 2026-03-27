package com.geek.lock.core;

import com.geek.lock.enums.LockType;
import com.geek.lock.model.DefaultLockKey;

import java.util.concurrent.TimeUnit;

public interface LockKey {

    String getKey();

    LockType getLockType();

    long getWaitTime();

    long getLeaseTime();

    TimeUnit getTimeUnit();

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private String key;
        private LockType lockType = LockType.REENTRANT;
        private long waitTime = 3000;
        private long leaseTime = 30000;
        private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder lockType(LockType lockType) {
            this.lockType = lockType;
            return this;
        }

        public Builder waitTime(long waitTime) {
            this.waitTime = waitTime;
            return this;
        }

        public Builder leaseTime(long leaseTime) {
            this.leaseTime = leaseTime;
            return this;
        }

        public Builder timeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
            return this;
        }

        public LockKey build() {
            DefaultLockKey lockKey = new DefaultLockKey();
            lockKey.setKey(key);
            lockKey.setLockType(lockType);
            lockKey.setWaitTime(waitTime);
            lockKey.setLeaseTime(leaseTime);
            lockKey.setTimeUnit(timeUnit);
            return lockKey;
        }
    }
}