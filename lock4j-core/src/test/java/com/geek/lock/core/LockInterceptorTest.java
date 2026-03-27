package com.geek.lock.core;

import com.geek.lock.annotation.Lock;
import com.geek.lock.enums.LockType;
import com.geek.lock.factory.LockProviderFactory;
import com.geek.lock.model.LockOptions;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LockInterceptor 单元测试")
class LockInterceptorTest {

    @Mock
    private LockProviderFactory providerFactory;

    @Mock
    private LockProvider lockProvider;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private TestLockExecutor executor;

    private List<String> callbackSequence;

    @BeforeEach
    void setUp() {
        callbackSequence = new ArrayList<>();
        executor = new TestLockExecutor(providerFactory, callbackSequence);
        executor.setApplicationContext(applicationContext);
    }

    @Nested
    @DisplayName("拦截器生命周期测试")
    class LifecycleTests {

        @Test
        @DisplayName("验证完整执行流程的钩子顺序")
        void testFullLifecycleSequence() throws Throwable {
            setupSuccessfulLockExecution();

            Lock annotation = createMockLockAnnotation(TestInterceptor.class);
            when(applicationContext.getBean(TestInterceptor.class))
                .thenReturn(new TestInterceptor(callbackSequence));

            executor.execute(joinPoint, annotation);

            assertEquals(Arrays.asList(
                "beforeKeyBuild",
                "afterKeyBuild",
                "beforeLock",
                "afterLock",
                "onLockSuccess"
            ), callbackSequence);
        }

        @Test
        @DisplayName("验证加锁失败时的钩子调用")
        void testLockFailureCallbacks() throws Throwable {
            setupLockFailureExecution();

            Lock annotation = createMockLockAnnotation(TestInterceptor.class);
            when(applicationContext.getBean(TestInterceptor.class))
                .thenReturn(new TestInterceptor(callbackSequence));

            assertThrows(com.geek.lock.exception.LockFailureException.class, 
                () -> executor.execute(joinPoint, annotation));

            assertTrue(callbackSequence.contains("beforeKeyBuild"));
            assertTrue(callbackSequence.contains("afterKeyBuild"));
            assertTrue(callbackSequence.contains("beforeLock"));
            assertTrue(callbackSequence.contains("afterLock"));
            assertTrue(callbackSequence.contains("onLockFailure"));
        }

        @Test
        @DisplayName("验证异常时的onException调用")
        void testExceptionCallback() throws Throwable {
            setupExceptionExecution();

            Lock annotation = createMockLockAnnotation(TestInterceptor.class);
            when(applicationContext.getBean(TestInterceptor.class))
                .thenReturn(new TestInterceptor(callbackSequence));

            assertThrows(RuntimeException.class, () -> executor.execute(joinPoint, annotation));
            assertTrue(callbackSequence.contains("onException"));
        }
    }

    @Nested
    @DisplayName("拦截器实例获取测试")
    class InterceptorResolutionTests {

        @Test
        @DisplayName("默认拦截器返回空实现")
        void testDefaultInterceptor() {
            Lock annotation = createMockLockAnnotation(LockInterceptor.class);

            LockInterceptor interceptor = executor.resolveInterceptor(annotation);
            LockInterceptor interceptor2 = executor.resolveInterceptor(annotation);

            assertNotNull(interceptor);
            assertSame(interceptor, interceptor2);
        }

        @Test
        @DisplayName("从Spring容器获取拦截器Bean")
        void testGetInterceptorFromSpringContext() {
            TestInterceptor springInterceptor = new TestInterceptor(callbackSequence);
            when(applicationContext.getBean(TestInterceptor.class)).thenReturn(springInterceptor);

            Lock annotation = createMockLockAnnotation(TestInterceptor.class);

            LockInterceptor result = executor.resolveInterceptor(annotation);

            assertSame(springInterceptor, result);
        }
    }

    private void setupSuccessfulLockExecution() throws Throwable {
        Method method = TestService.class.getMethod("processOrder", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"orderId"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORDER-123"});
        when(joinPoint.proceed()).thenReturn("success");

        when(providerFactory.hasPrimaryProvider()).thenReturn(true);
        when(providerFactory.getPrimaryProvider()).thenReturn(lockProvider);
        when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class))).thenReturn(true);
    }

    private void setupLockFailureExecution() throws Throwable {
        Method method = TestService.class.getMethod("processOrder", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"orderId"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORDER-123"});

        when(providerFactory.hasPrimaryProvider()).thenReturn(true);
        when(providerFactory.getPrimaryProvider()).thenReturn(lockProvider);
        when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class))).thenReturn(false);
    }

    private void setupExceptionExecution() throws Throwable {
        Method method = TestService.class.getMethod("processOrder", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"orderId"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORDER-123"});
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Business error"));

        when(providerFactory.hasPrimaryProvider()).thenReturn(true);
        when(providerFactory.getPrimaryProvider()).thenReturn(lockProvider);
        when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class))).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private Lock createMockLockAnnotation(Class<? extends LockInterceptor> interceptorClass) {
        Lock annotation = mock(Lock.class);
        doReturn(new String[]{"#orderId"}).when(annotation).keys();
        doReturn("").when(annotation).prefix();
        doReturn(KeyBuilder.class).when(annotation).keyBuilder();
        doReturn(3000L).when(annotation).waitTime();
        doReturn(30000L).when(annotation).leaseTime();
        doReturn(TimeUnit.MILLISECONDS).when(annotation).timeUnit();
        doReturn(LockType.REENTRANT).when(annotation).lockType();
        doReturn(interceptorClass).when(annotation).interceptor();
        doReturn(LockProvider.class).when(annotation).provider();
        doReturn(com.geek.lock.core.FailureHandler.Default.class).when(annotation).failureHandler();
        return annotation;
    }

    static class TestInterceptor implements LockInterceptor {
        private final List<String> callbacks;

        TestInterceptor(List<String> callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public void beforeKeyBuild(Method method, Object[] args, Lock annotation) {
            callbacks.add("beforeKeyBuild");
        }

        @Override
        public void afterKeyBuild(List<String> keys) {
            callbacks.add("afterKeyBuild");
        }

        @Override
        public void beforeLock(List<String> keys, LockOptions options) {
            callbacks.add("beforeLock");
        }

        @Override
        public void afterLock(List<String> keys) {
            callbacks.add("afterLock");
        }

        @Override
        public void onLockSuccess(List<String> keys, LockKey lockKey) {
            callbacks.add("onLockSuccess");
        }

        @Override
        public void onLockFailure(List<String> keys) {
            callbacks.add("onLockFailure");
        }

        @Override
        public void onException(List<String> keys, Throwable exception) {
            callbacks.add("onException");
        }
    }

    static class TestLockExecutor extends com.geek.lock.executor.DefaultLockExecutor {
        private final List<String> callbacks;

        TestLockExecutor(LockProviderFactory factory, List<String> callbacks) {
            super(factory);
            this.callbacks = callbacks;
        }

        @Override
        public LockInterceptor resolveInterceptor(Lock annotation) {
            return super.resolveInterceptor(annotation);
        }
    }

    static class TestService {
        @Lock(keys = "#orderId")
        public String processOrder(String orderId) {
            return "success";
        }
    }
}