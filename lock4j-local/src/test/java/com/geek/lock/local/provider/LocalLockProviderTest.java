package com.geek.lock.local.provider;

import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.model.LockOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalLockProvider 集成测试")
class LocalLockProviderTest {

    private LocalLockProvider provider;
    private LockOptions options;

    @BeforeEach
    void setUp() {
        provider = new LocalLockProvider(null);
        options = new LockOptions()
                .setWaitTime(1000)
                .setLeaseTime(10000)
                .setTimeUnit(TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("基本加锁解锁测试")
    void testBasicLockAndUnlock() {
        LockKey lockKey = LockKey.builder()
                .key("test-lock-1")
                .lockType(LockType.REENTRANT)
                .build();

        boolean acquired = provider.tryLock(lockKey, options);
        assertTrue(acquired, "应该成功获取锁");

        provider.unlock(lockKey);
    }

    @Test
    @DisplayName("可重入锁测试")
    void testReentrantLock() {
        LockKey lockKey = LockKey.builder()
                .key("test-reentrant")
                .lockType(LockType.REENTRANT)
                .build();

        assertTrue(provider.tryLock(lockKey, options));
        assertTrue(provider.tryLock(lockKey, options));

        provider.unlock(lockKey);
        provider.unlock(lockKey);
    }

    @Test
    @DisplayName("公平锁测试")
    void testFairLock() {
        LockKey lockKey = LockKey.builder()
                .key("test-fair")
                .lockType(LockType.FAIR)
                .build();

        assertTrue(provider.tryLock(lockKey, options));
        provider.unlock(lockKey);
    }

    @Test
    @DisplayName("读写锁 - 读读共享测试")
    void testReadWriteLockReadReadShared() throws InterruptedException {
        LockKey readKey1 = LockKey.builder()
                .key("test-rw")
                .lockType(LockType.READ)
                .build();
        LockKey readKey2 = LockKey.builder()
                .key("test-rw")
                .lockType(LockType.READ)
                .build();

        assertTrue(provider.tryLock(readKey1, options));
        assertTrue(provider.tryLock(readKey2, options));

        provider.unlock(readKey1);
        provider.unlock(readKey2);
    }

    @Test
    @DisplayName("读写锁 - 写写互斥测试")
    void testReadWriteLockWriteWriteExclusive() throws InterruptedException {
        LockKey writeKey1 = LockKey.builder()
                .key("test-rw-exclusive")
                .lockType(LockType.WRITE)
                .build();

        assertTrue(provider.tryLock(writeKey1, options));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);

        executor.submit(() -> {
            LockKey writeKey2 = LockKey.builder()
                    .key("test-rw-exclusive")
                    .lockType(LockType.WRITE)
                    .build();
            LockOptions shortOptions = new LockOptions()
                    .setWaitTime(100)
                    .setTimeUnit(TimeUnit.MILLISECONDS);
            
            boolean acquired = provider.tryLock(writeKey2, shortOptions);
            result.set(acquired ? 1 : 0);
            latch.countDown();
        });

        latch.await(2, TimeUnit.SECONDS);
        assertEquals(0, result.get(), "第二个写锁不应该获取成功");

        provider.unlock(writeKey1);
        executor.shutdown();
    }

    @Test
    @DisplayName("并发加锁测试")
    void testConcurrentLock() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger counter = new AtomicInteger(0);

        LockKey lockKey = LockKey.builder()
                .key("concurrent-test")
                .lockType(LockType.REENTRANT)
                .build();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    LockOptions waitOptions = new LockOptions()
                            .setWaitTime(5000)
                            .setTimeUnit(TimeUnit.MILLISECONDS);
                    
                    if (provider.tryLock(lockKey, waitOptions)) {
                        successCount.incrementAndGet();
                        int value = counter.incrementAndGet();
                        Thread.sleep(10);
                        assertEquals(value, counter.get());
                        provider.unlock(lockKey);
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS), "所有线程应该完成");
        executor.shutdown();

        assertTrue(successCount.get() > 0, "应该有线程成功获取锁");
        assertEquals(threadCount, counter.get(), "计数器应该等于线程数");
    }

    @Test
    @DisplayName("锁超时测试")
    void testLockTimeout() throws InterruptedException {
        LockKey lockKey = LockKey.builder()
                .key("timeout-test")
                .lockType(LockType.REENTRANT)
                .build();

        assertTrue(provider.tryLock(lockKey, options));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);

        executor.submit(() -> {
            LockOptions timeoutOptions = new LockOptions()
                    .setWaitTime(100)
                    .setTimeUnit(TimeUnit.MILLISECONDS);
            
            boolean acquired = provider.tryLock(lockKey, timeoutOptions);
            result.set(acquired ? 1 : 0);
            latch.countDown();
        });

        latch.await(2, TimeUnit.SECONDS);
        assertEquals(0, result.get(), "超时后不应该获取到锁");

        provider.unlock(lockKey);
        executor.shutdown();
    }

    @Test
    @DisplayName("supports 方法测试")
    void testSupports() {
        assertTrue(provider.supports(LockType.REENTRANT));
        assertTrue(provider.supports(LockType.FAIR));
        assertTrue(provider.supports(LockType.READ));
        assertTrue(provider.supports(LockType.WRITE));
    }
}