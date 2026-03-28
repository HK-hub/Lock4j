package com.geek.lock.zookeeper.provider;

import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ZookeeperLockProvider 单元测试")
class ZookeeperLockProviderTest {

    @Mock
    private CuratorFramework curatorFramework;

    @Mock
    private LockEventPublisher eventPublisher;

    @Mock
    private InterProcessMutex lock;

    @Mock
    private InterProcessReadWriteLock readWriteLock;

    private ZookeeperLockProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ZookeeperLockProvider(curatorFramework, eventPublisher);
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
        @DisplayName("支持 READ 类型")
        void testSupportsRead() {
            assertTrue(provider.supports(LockType.READ));
        }

        @Test
        @DisplayName("支持 WRITE 类型")
        void testSupportsWrite() {
            assertTrue(provider.supports(LockType.WRITE));
        }

        @Test
        @DisplayName("不支持 FAIR 类型")
        void testNotSupportsFair() {
            assertFalse(provider.supports(LockType.FAIR));
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

            whenNew(InterProcessMutex.class).withArguments(any(CuratorFramework.class), anyString()).thenReturn(lock);
            when(lock.acquire(anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
        }

        @Test
        @DisplayName("获取锁失败")
        void testTryLockFailure() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").build();
            LockOptions options = new LockOptions().setWaitTime(1000).setEnableWatchdog(false);

            whenNew(InterProcessMutex.class).withArguments(any(CuratorFramework.class), anyString()).thenReturn(lock);
            when(lock.acquire(anyLong(), any(TimeUnit.class))).thenReturn(false);

            boolean result = provider.tryLock(lockKey, options);

            assertFalse(result);
        }

        @Test
        @DisplayName("默认超时保护 - waitTime <= 0")
        void testDefaultTimeoutProtection() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").build();
            LockOptions options = new LockOptions().setWaitTime(0).setEnableWatchdog(false);

            whenNew(InterProcessMutex.class).withArguments(any(CuratorFramework.class), anyString()).thenReturn(lock);
            when(lock.acquire(anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(lock).acquire(anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    @DisplayName("读写锁测试")
    class ReadWriteLockTests {

        @Test
        @DisplayName("获取读锁")
        void testGetReadLock() throws Exception {
            LockKey lockKey = LockKey.builder().key("rw-lock").lockType(LockType.READ).build();
            LockOptions options = new LockOptions().setEnableWatchdog(false);

            whenNew(InterProcessReadWriteLock.class).withArguments(any(CuratorFramework.class), anyString()).thenReturn(readWriteLock);
            when(readWriteLock.readLock()).thenReturn(lock);
            when(lock.acquire(anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(readWriteLock).readLock();
        }

        @Test
        @DisplayName("获取写锁")
        void testGetWriteLock() throws Exception {
            LockKey lockKey = LockKey.builder().key("rw-lock").lockType(LockType.WRITE).build();
            LockOptions options = new LockOptions().setEnableWatchdog(false);

            whenNew(InterProcessReadWriteLock.class).withArguments(any(CuratorFramework.class), anyString()).thenReturn(readWriteLock);
            when(readWriteLock.writeLock()).thenReturn(lock);
            when(lock.acquire(anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
            verify(readWriteLock).writeLock();
        }
    }

    @Nested
    @DisplayName("解锁测试")
    class UnlockTests {

        @Test
        @DisplayName("成功解锁")
        void testUnlockSuccess() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").build();

            whenNew(InterProcessMutex.class).withArguments(any(CuratorFramework.class), anyString()).thenReturn(lock);
            when(lock.isAcquiredInThisProcess()).thenReturn(true);

            provider.unlock(lockKey);

            verify(lock).release();
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