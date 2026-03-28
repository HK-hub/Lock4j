package com.geek.lock.redis.provider;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisTemplateLockProvider 单元测试")
class RedisTemplateLockProviderTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private LockEventPublisher eventPublisher;

    private RedisTemplateLockProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RedisTemplateLockProvider(redisTemplate, eventPublisher);
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

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        }

        @Test
        @DisplayName("获取锁失败 - 返回 TTL")
        void testTryLockFailure() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").lockType(LockType.REENTRANT).build();
            LockOptions options = new LockOptions().setWaitTime(100).setEnableWatchdog(false);

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(10000L);

            boolean result = provider.tryLock(lockKey, options);

            assertFalse(result);
        }

        @Test
        @DisplayName("Watchdog 模式获取锁")
        void testTryLockWithWatchdog() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").lockType(LockType.REENTRANT).build();
            LockOptions options = new LockOptions().setEnableWatchdog(true).setWaitTime(1000);

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("解锁测试")
    class UnlockTests {

        @Test
        @DisplayName("成功解锁")
        void testUnlockSuccess() {
            LockKey lockKey = LockKey.builder().key("test-lock").lockType(LockType.REENTRANT).build();

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

            provider.unlock(lockKey);

            verify(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        }

        @Test
        @DisplayName("解锁失败 - 非持有者")
        void testUnlockNotHeld() {
            LockKey lockKey = LockKey.builder().key("test-lock").lockType(LockType.REENTRANT).build();

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

            assertThrows(IllegalStateException.class, () -> provider.unlock(lockKey));
        }
    }

    @Nested
    @DisplayName("Provider 生命周期测试")
    class LifecycleTests {

        @Test
        @DisplayName("关闭 Provider")
        void testShutdown() {
            provider.shutdown();
        }
    }
}