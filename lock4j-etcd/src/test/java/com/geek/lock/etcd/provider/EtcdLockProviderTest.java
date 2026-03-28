package com.geek.lock.etcd.provider;

import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.lock.LockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EtcdLockProvider 单元测试")
class EtcdLockProviderTest {

    @Mock
    private Client etcdClient;

    @Mock
    private Lock lockClient;

    @Mock
    private Lease leaseClient;

    @Mock
    private LockEventPublisher eventPublisher;

    @Mock
    private LockResponse lockResponse;

    private EtcdLockProvider provider;

    @BeforeEach
    void setUp() {
        provider = new EtcdLockProvider(etcdClient, eventPublisher);
        when(etcdClient.getLockClient()).thenReturn(lockClient);
        when(etcdClient.getLeaseClient()).thenReturn(leaseClient);
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
        @DisplayName("不支持 READ 类型")
        void testNotSupportsRead() {
            assertFalse(provider.supports(LockType.READ));
        }

        @Test
        @DisplayName("不支持 WRITE 类型")
        void testNotSupportsWrite() {
            assertFalse(provider.supports(LockType.WRITE));
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

            CompletableFuture<LockResponse> future = CompletableFuture.completedFuture(lockResponse);
            when(lockClient.lock(any(), anyLong())).thenReturn(future);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
        }

        @Test
        @DisplayName("获取锁失败")
        void testTryLockFailure() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").build();
            LockOptions options = new LockOptions().setWaitTime(100).setEnableWatchdog(false);

            CompletableFuture<LockResponse> future = new CompletableFuture<>();
            future.cancel(true);
            when(lockClient.lock(any(), anyLong())).thenReturn(future);

            boolean result = provider.tryLock(lockKey, options);

            assertFalse(result);
        }

        @Test
        @DisplayName("Watchdog 模式获取锁")
        void testTryLockWithWatchdog() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").build();
            LockOptions options = new LockOptions().setEnableWatchdog(true).setWaitTime(1000);

            CompletableFuture<LockResponse> future = CompletableFuture.completedFuture(lockResponse);
            when(lockClient.lock(any(), eq(0L))).thenReturn(future);

            boolean result = provider.tryLock(lockKey, options);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("解锁测试")
    class UnlockTests {

        @Test
        @DisplayName("成功解锁")
        void testUnlockSuccess() throws Exception {
            LockKey lockKey = LockKey.builder().key("test-lock").build();
            LockOptions options = new LockOptions().setEnableWatchdog(false);

            CompletableFuture<LockResponse> lockFuture = CompletableFuture.completedFuture(lockResponse);
            when(lockClient.lock(any(), anyLong())).thenReturn(lockFuture);
            CompletableFuture<Void> unlockFuture = CompletableFuture.completedFuture(null);
            when(lockClient.unlock(any())).thenReturn(unlockFuture);

            provider.tryLock(lockKey, options);
            provider.unlock(lockKey);

            verify(lockClient).unlock(any());
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