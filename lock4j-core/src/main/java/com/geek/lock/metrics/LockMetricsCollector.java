package com.geek.lock.metrics;

import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;

import java.util.concurrent.TimeUnit;

public interface LockMetricsCollector {

    void recordLockAcquisition(LockKey lockKey, long waitTime, TimeUnit unit, boolean success);

    void recordLockRelease(LockKey lockKey, long holdTime, TimeUnit unit);

    void recordLockRenewal(LockKey lockKey, boolean success);

    void recordLockFailure(LockKey lockKey, Throwable cause);

    LockStatistics getStatistics(String lockKey);

    void reset();

    class LockStatistics {
        private final String lockKey;
        private final long totalAcquisitions;
        private final long successfulAcquisitions;
        private final long failedAcquisitions;
        private final long totalHoldTimeMillis;
        private final long averageHoldTimeMillis;
        private final long maxHoldTimeMillis;
        private final long renewalCount;
        private final long failedRenewalCount;

        public LockStatistics(String lockKey, long totalAcquisitions, long successfulAcquisitions,
                              long failedAcquisitions, long totalHoldTimeMillis, long averageHoldTimeMillis,
                              long maxHoldTimeMillis, long renewalCount, long failedRenewalCount) {
            this.lockKey = lockKey;
            this.totalAcquisitions = totalAcquisitions;
            this.successfulAcquisitions = successfulAcquisitions;
            this.failedAcquisitions = failedAcquisitions;
            this.totalHoldTimeMillis = totalHoldTimeMillis;
            this.averageHoldTimeMillis = averageHoldTimeMillis;
            this.maxHoldTimeMillis = maxHoldTimeMillis;
            this.renewalCount = renewalCount;
            this.failedRenewalCount = failedRenewalCount;
        }

        public String getLockKey() { return lockKey; }
        public long getTotalAcquisitions() { return totalAcquisitions; }
        public long getSuccessfulAcquisitions() { return successfulAcquisitions; }
        public long getFailedAcquisitions() { return failedAcquisitions; }
        public long getTotalHoldTimeMillis() { return totalHoldTimeMillis; }
        public long getAverageHoldTimeMillis() { return averageHoldTimeMillis; }
        public long getMaxHoldTimeMillis() { return maxHoldTimeMillis; }
        public long getRenewalCount() { return renewalCount; }
        public long getFailedRenewalCount() { return failedRenewalCount; }
        public double getSuccessRate() {
            return totalAcquisitions > 0 ? (double) successfulAcquisitions / totalAcquisitions : 0.0;
        }
    }
}