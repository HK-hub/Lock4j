package com.geek.lock.metrics;

import com.geek.lock.core.LockKey;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultLockMetricsCollector implements LockMetricsCollector {

    private final ConcurrentHashMap<String, LockMetrics> metricsMap = new ConcurrentHashMap<>();

    @Override
    public void recordLockAcquisition(LockKey lockKey, long waitTime, TimeUnit unit, boolean success) {
        String key = lockKey.getKey();
        LockMetrics metrics = metricsMap.computeIfAbsent(key, k -> new LockMetrics());
        
        metrics.totalAcquisitions.incrementAndGet();
        if (success) {
            metrics.successfulAcquisitions.incrementAndGet();
            metrics.totalWaitTimeMillis.addAndGet(unit.toMillis(waitTime));
        } else {
            metrics.failedAcquisitions.incrementAndGet();
        }
    }

    @Override
    public void recordLockRelease(LockKey lockKey, long holdTime, TimeUnit unit) {
        String key = lockKey.getKey();
        LockMetrics metrics = metricsMap.get(key);
        if (metrics != null) {
            long holdTimeMillis = unit.toMillis(holdTime);
            metrics.totalHoldTimeMillis.addAndGet(holdTimeMillis);
            metrics.maxHoldTimeMillis.accumulateAndGet(holdTimeMillis, Math::max);
        }
    }

    @Override
    public void recordLockRenewal(LockKey lockKey, boolean success) {
        String key = lockKey.getKey();
        LockMetrics metrics = metricsMap.get(key);
        if (metrics != null) {
            if (success) {
                metrics.renewalCount.incrementAndGet();
            } else {
                metrics.failedRenewalCount.incrementAndGet();
            }
        }
    }

    @Override
    public void recordLockFailure(LockKey lockKey, Throwable cause) {
        String key = lockKey.getKey();
        LockMetrics metrics = metricsMap.computeIfAbsent(key, k -> new LockMetrics());
        metrics.totalAcquisitions.incrementAndGet();
        metrics.failedAcquisitions.incrementAndGet();
    }

    @Override
    public LockStatistics getStatistics(String lockKey) {
        LockMetrics metrics = metricsMap.get(lockKey);
        if (metrics == null) {
            return null;
        }
        
        long total = metrics.totalAcquisitions.get();
        long successful = metrics.successfulAcquisitions.get();
        long failed = metrics.failedAcquisitions.get();
        long totalHold = metrics.totalHoldTimeMillis.get();
        long avgHold = successful > 0 ? totalHold / successful : 0;
        long maxHold = metrics.maxHoldTimeMillis.get();
        
        return new LockStatistics(lockKey, total, successful, failed, totalHold, avgHold, maxHold,
                metrics.renewalCount.get(), metrics.failedRenewalCount.get());
    }

    @Override
    public void reset() {
        metricsMap.clear();
    }

    private static class LockMetrics {
        final AtomicLong totalAcquisitions = new AtomicLong();
        final AtomicLong successfulAcquisitions = new AtomicLong();
        final AtomicLong failedAcquisitions = new AtomicLong();
        final AtomicLong totalWaitTimeMillis = new AtomicLong();
        final AtomicLong totalHoldTimeMillis = new AtomicLong();
        final AtomicLong maxHoldTimeMillis = new AtomicLong();
        final AtomicLong renewalCount = new AtomicLong();
        final AtomicLong failedRenewalCount = new AtomicLong();
    }
}