package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.LockType;
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
    @DisplayName("lock method tests")
    class LockMethodTests {

        @Test
        @DisplayName("should return LockInfo when lock acquired successfully")
        void shouldReturnLockInfoWhenLockAcquired() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            assertEquals("test-key", lockInfo.getLockKey().getKey());
            verify(lockProvider).tryLock(any(LockKey.class), any(LockOptions.class));
        }

        @Test
        @DisplayName("should return LockInfo with acquired=false when lock failed")
        void shouldReturnLockInfoWhenLockFailed() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(false);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            assertNotNull(lockInfo);
            assertFalse(lockInfo.isAcquired());
        }

        @Test
        @DisplayName("should throw exception when key is null")
        void shouldThrowExceptionWhenKeyIsNull() {
            assertThrows(IllegalArgumentException.class, () ->
                    lockTemplate.lock(null, 30000, 5000));
        }

        @Test
        @DisplayName("should use specified provider")
        void shouldUseSpecifiedProvider() {
            when(providerFactory.getProvider(TestLockProvider.class))
                    .thenReturn(lockProvider);
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000, TestLockProvider.class);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            verify(providerFactory).getProvider(TestLockProvider.class);
            verify(lockProvider).tryLock(any(LockKey.class), any(LockOptions.class));
        }

        @Test
        @DisplayName("should use LockRequest builder")
        void shouldUseLockRequestBuilder() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockRequest request = LockRequest.builder()
                    .key("test-key")
                    .leaseTime(30000)
                    .waitTime(5000)
                    .lockType(LockType.FAIR)
                    .build();

            LockInfo lockInfo = lockTemplate.lock(request);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            assertEquals(LockType.FAIR, lockInfo.getLockKey().getLockType());
        }
    }

    @Nested
    @DisplayName("releaseLock method tests")
    class ReleaseLockMethodTests {

        @Test
        @DisplayName("should release lock successfully")
        void shouldReleaseLockSuccessfully() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            lockTemplate.releaseLock(lockInfo);

            verify(lockProvider).unlock(any(LockKey.class));
        }

        @Test
        @DisplayName("should not throw exception when lockInfo is null")
        void shouldNotThrowWhenLockInfoIsNull() {
            lockTemplate.releaseLock(null);
            verify(lockProvider, never()).unlock(any());
        }

        @Test
        @DisplayName("should not unlock when lock not acquired")
        void shouldNotUnlockWhenLockNotAcquired() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(false);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            lockTemplate.releaseLock(lockInfo);

            verify(lockProvider, never()).unlock(any());
        }
    }

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