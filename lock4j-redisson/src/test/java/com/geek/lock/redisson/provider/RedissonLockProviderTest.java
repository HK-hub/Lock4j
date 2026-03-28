package com.geek.lock.redisson.provider;

import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedissonLockProvider 单元测试")
class RedissonLockProviderTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private LockEventPublisher eventPublisher;

    @Mock
    private RLock lock;

    private RedissonLockProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RedissonLockProvider(redissonClient, eventPublisher);
    }

    @Nested
    @DisplayName("锁类型支持测试")
    class LockTypeSupportTests {

        @Test
        @DisplayName("支持 REENTRANT 类型")
        void testSupportsReentrant() {
            assertTrue(provider.supports(LockType.REENTRANT));
        }

        @Test
        @DisplayName("支持 FAIR 类型")
        void testSupportsFair() {
            assertTrue(provider.supports(LockType.FAIR));
        }

        @Test
        @DisplayName("支持 READ 类型")
        void testSupportsRead() {
            assertTrue(provider.supports(LockType.READ));
        }

        @Test
        @DisplayName("支持 WRITE 类型")
        void testSupportsWrite() {
            assertTrue(provider.supports(LockType.WRITE));
        }
    }

    @Nested
    @DisplayName("可重入锁测试")
    class ReentrantLockTests {

        @Test
        @DisplayName("成功获取锁")
        void testTryLockSuccess() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").lockType(LockType.REENTRANT).build();
            LockOptions options = new LockOptions().setWaitTime(1000).setLeaseTime(30000).setEnableWatchdog(false);

            when(redissonClient.getLock("test-lock")).thenReturn(lock);
            when(lock.tryLock(1000L, 30000L, TimeUnit.MILLISECONDS)).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(redissonClient).getLock("test-lock");
            verify(lock).tryLock(1000L, 30000L, TimeUnit.MILLISECONDS);
        }

        @Test
        @DisplayName("获取锁失败")
        void testTryLockFailure() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").build();
            LockOptions options = new LockOptions().setWaitTime(1000).setEnableWatchdog(false);

            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

            boolean result = provider.tryLock(lockKey, options);

            assertFalse(result);
        }

        @Test
        @DisplayName("Watchdog 模式获取锁")
        void testTryLockWithWatchdog() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").build();
            LockOptions options = new LockOptions().setEnableWatchdog(true).setWaitTime(1000);

            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(lock).tryLock(1000L, TimeUnit.MILLISECONDS);
        }
    }

    @Nested
    @DisplayName("公平锁测试")
    class FairLockTests {

        @Test
        @DisplayName("获取公平锁")
        void testGetFairLock() throws Exception {
            LockKey lockKey = LockKey.builder().key("fair-lock").lockType(LockType.FAIR).build();
            LockOptions options = new LockOptions().setEnableWatchdog(false);

            when(redissonClient.getFairLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(redissonClient).getFairLock("fair-lock");
        }
    }

    @Nested
    @DisplayName("读写锁测试")
    class ReadWriteLockTests {

        @Mock
        private org.redisson.api.RReadWriteLock readWriteLock;

        @Test
        @DisplayName("获取读锁")
        void testGetReadLock() throws Exception {
            LockKey lockKey = LockKey.builder().key("rw-lock").lockType(LockType.READ).build();
            LockOptions options = new LockOptions().setEnableWatchdog(false);

            when(redissonClient.getReadWriteLock(anyString())).thenReturn(readWriteLock);
            when(readWriteLock.readLock()).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(redissonClient).getReadWriteLock("rw-lock");
            verify(readWriteLock).readLock();
        }

        @Test
        @DisplayName("获取写锁")
        void testGetWriteLock() throws Exception {
            LockKey lockKey = LockKey.builder().key("rw-lock").lockType(LockType.WRITE).build();
            LockOptions options = new LockOptions().setEnableWatchdog(false);

            when(redissonClient.getReadWriteLock(anyString())).thenReturn(readWriteLock);
            when(readWriteLock.writeLock()).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(redissonClient).getReadWriteLock("rw-lock");
            verify(readWriteLock).writeLock();
        }
    }

    @Nested
    @DisplayName("解锁测试")
    class UnlockTests {

        @Test
        @DisplayName("成功解锁")
        void testUnlockSuccess() {
            LockKey lockKey = LockKey.builder().key("test-lock").lockType(LockType.REENTRANT).build();

            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.isHeldByCurrentThread()).thenReturn(true);

            provider.unlock(lockKey);

            verify(lock).unlock();
        }

        @Test
        @DisplayName("非持有线程不解锁")
        void testUnlockNotHeldByCurrentThread() {
            LockKey lockKey = LockKey.builder().key("test-lock").build();

            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.isHeldByCurrentThread()).thenReturn(false);

            provider.unlock(lockKey);

            verify(lock, never()).unlock();
        }
    }
}