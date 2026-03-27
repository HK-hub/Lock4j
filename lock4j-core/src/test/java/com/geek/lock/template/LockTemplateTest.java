package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.LockType;
import com.geek.lock.exception.LockFailureException;
import com.geek.lock.factory.LockProviderFactory;
import com.geek.lock.model.LockOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LockTemplate 单元测试类
 *
 * <p>测试 LockTemplate 的加锁、解锁功能，包括：</p>
 * <ul>
 *     <li>lock() 方法 - 获取失败时抛异常</li>
 *     <li>tryLock() 方法 - 获取失败时不抛异常</li>
 *     <li>releaseLock() 方法 - 释放锁</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LockTemplateTest {

    @Mock
    private LockProviderFactory providerFactory;

    @Mock
    private LockProvider lockProvider;

    private LockTemplate lockTemplate;

    @BeforeEach
    void setUp() {
        lenient().when(providerFactory.getFirstProvider()).thenReturn(lockProvider);
        lenient().when(providerFactory.hasPrimaryProvider()).thenReturn(false);
        lockTemplate = new LockTemplate(providerFactory);
    }

    @Nested
    @DisplayName("tryLock() 方法测试 - 获取失败时不抛异常")
    class TryLockMethodTests {

        @Test
        @DisplayName("成功获取锁时返回 isAcquired=true 的 LockInfo")
        void shouldReturnLockInfoWhenLockAcquired() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.tryLock("test-key", 30000, 5000);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            assertEquals("test-key", lockInfo.getLockKey().getKey());
            verify(lockProvider).tryLock(any(LockKey.class), any(LockOptions.class));
        }

        @Test
        @DisplayName("获取锁失败时返回 isAcquired=false 的 LockInfo")
        void shouldReturnLockInfoWhenLockFailed() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(false);

            LockInfo lockInfo = lockTemplate.tryLock("test-key", 30000, 5000);

            assertNotNull(lockInfo);
            assertFalse(lockInfo.isAcquired());
        }

        @Test
        @DisplayName("key 为 null 时抛出 IllegalArgumentException")
        void shouldThrowExceptionWhenKeyIsNull() {
            assertThrows(IllegalArgumentException.class, () ->
                    lockTemplate.tryLock(null, 30000, 5000));
        }

        @Test
        @DisplayName("指定 Provider 时使用指定的 Provider")
        void shouldUseSpecifiedProvider() {
            when(providerFactory.getProvider(TestLockProvider.class))
                    .thenReturn(lockProvider);
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.tryLock("test-key", 30000, 5000, TestLockProvider.class);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            verify(providerFactory).getProvider(TestLockProvider.class);
            verify(lockProvider).tryLock(any(LockKey.class), any(LockOptions.class));
        }

        @Test
        @DisplayName("使用 LockRequest Builder 方式加锁")
        void shouldUseLockRequestBuilder() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockRequest request = LockRequest.builder()
                    .key("test-key")
                    .leaseTime(30000)
                    .waitTime(5000)
                    .lockType(LockType.FAIR)
                    .build();

            LockInfo lockInfo = lockTemplate.tryLock(request);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            assertEquals(LockType.FAIR, lockInfo.getLockKey().getLockType());
        }
    }

    @Nested
    @DisplayName("lock() 方法测试 - 获取失败时抛异常")
    class LockMethodTests {

        @Test
        @DisplayName("成功获取锁时返回 LockInfo")
        void shouldReturnLockInfoWhenLockAcquired() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
        }

        @Test
        @DisplayName("获取锁失败时抛出 LockFailureException")
        void shouldThrowLockFailureExceptionWhenLockFailed() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(false);

            assertThrows(LockFailureException.class, () ->
                    lockTemplate.lock("test-key", 30000, 5000));
        }

        @Test
        @DisplayName("使用 LockRequest 时获取失败抛出 LockFailureException")
        void shouldThrowLockFailureExceptionWhenUsingRequest() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(false);

            LockRequest request = LockRequest.builder()
                    .key("test-key")
                    .leaseTime(30000)
                    .waitTime(5000)
                    .build();

            assertThrows(LockFailureException.class, () ->
                    lockTemplate.lock(request));
        }
    }

    @Nested
    @DisplayName("releaseLock() 方法测试")
    class ReleaseLockMethodTests {

        @Test
        @DisplayName("成功释放锁")
        void shouldReleaseLockSuccessfully() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.tryLock("test-key", 30000, 5000);

            lockTemplate.releaseLock(lockInfo);

            verify(lockProvider).unlock(any(LockKey.class));
        }

        @Test
        @DisplayName("lockInfo 为 null 时不抛异常")
        void shouldNotThrowWhenLockInfoIsNull() {
            lockTemplate.releaseLock(null);
            verify(lockProvider, never()).unlock(any());
        }

        @Test
        @DisplayName("锁未成功获取时不调用 unlock")
        void shouldNotUnlockWhenLockNotAcquired() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(false);

            LockInfo lockInfo = lockTemplate.tryLock("test-key", 30000, 5000);

            lockTemplate.releaseLock(lockInfo);

            verify(lockProvider, never()).unlock(any());
        }
    }

    /**
     * 测试用 LockProvider 实现
     */
    private static class TestLockProvider implements LockProvider {
        @Override
        public boolean tryLock(LockKey lockKey, LockOptions options) {
            return false;
        }

        @Override
        public void unlock(LockKey lockKey) {
        }

        @Override
        public boolean supports(LockType lockType) {
            return true;
        }
    }
}