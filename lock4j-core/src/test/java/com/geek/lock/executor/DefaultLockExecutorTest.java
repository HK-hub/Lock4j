package com.geek.lock.executor;

import com.geek.lock.annotation.Lock;
import com.geek.lock.core.KeyBuilder;
import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.KeyAbsentPolicy;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultLockExecutor 单元测试")
class DefaultLockExecutorTest {

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

    private DefaultLockExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DefaultLockExecutor(providerFactory);
        executor.setApplicationContext(applicationContext);
    }

    @Nested
    @DisplayName("SpEL 表达式解析测试")
    class SpelExpressionTests {

        @Test
        @DisplayName("解析单个 SpEL 表达式")
        void testParseSingleSpelExpression() throws Exception {
            Method method = TestService.class.getMethod("processOrder", String.class);
            String[] keys = setupAndResolveKeys(method, new Object[]{"ORDER-123"}, new String[]{"orderId"});

            assertEquals(1, keys.length);
            assertEquals("ORDER-123", keys[0]);
        }

        @Test
        @DisplayName("解析多个 SpEL 表达式")
        void testParseMultipleSpelExpressions() throws Exception {
            Method method = TestService.class.getMethod("deductStock", String.class, String.class);
            String[] keys = setupAndResolveKeys(method, new Object[]{"PROD-001", "WH-001"}, new String[]{"productId", "warehouseId"});

            assertEquals(2, keys.length);
            assertEquals("PROD-001", keys[0]);
            assertEquals("WH-001", keys[1]);
        }

        @Test
        @DisplayName("解析对象属性 SpEL 表达式")
        void testParseObjectPropertySpelExpression() throws Exception {
            Method method = TestService.class.getMethod("processUser", User.class);
            User user = new User("USER-001", "张三");
            String[] keys = setupAndResolveKeys(method, new Object[]{user}, new String[]{"user"});

            assertEquals(1, keys.length);
            assertEquals("USER-001", keys[0]);
        }

        @Test
        @DisplayName("解析嵌套属性 SpEL 表达式")
        void testParseNestedPropertySpelExpression() throws Exception {
            Method method = TestService.class.getMethod("processOrderWithNested", Order.class);
            Order order = new Order("ORDER-001", new User("USER-001", "张三"));
            String[] keys = setupAndResolveKeys(method, new Object[]{order}, new String[]{"order"});

            assertEquals(1, keys.length);
            assertEquals("ORDER-001", keys[0]);
        }
    }

    @Nested
    @DisplayName("KeyAbsentPolicy 测试")
    class KeyAbsentPolicyTests {

        @Test
        @DisplayName("USE_METHOD_PATH - 使用方法全限定名作为 Key")
        void testUseMethodPath() throws Exception {
            Method method = TestService.class.getMethod("noKeyMethod");
            String[] keys = resolveKeysWithPolicy(method, new Object[]{}, new String[]{}, KeyAbsentPolicy.USE_METHOD_PATH);

            assertEquals(1, keys.length);
            assertTrue(keys[0].contains("noKeyMethod"));
        }

        @Test
        @DisplayName("THROW_EXCEPTION - 抛出异常")
        void testThrowException() throws Exception {
            Method method = TestService.class.getMethod("noKeyMethod");
            
            assertThrows(IllegalArgumentException.class, () -> {
                resolveKeysWithPolicy(method, new Object[]{}, new String[]{}, KeyAbsentPolicy.THROW_EXCEPTION);
            });
        }
    }

    @Nested
    @DisplayName("前缀处理测试")
    class PrefixTests {

        @Test
        @DisplayName("添加前缀")
        void testAddPrefix() throws Exception {
            Method method = TestService.class.getMethod("processOrder", String.class);
            String[] keys = setupAndResolveKeysWithPrefix(method, new Object[]{"ORDER-123"}, new String[]{"orderId"}, "lock:");

            assertEquals(1, keys.length);
            assertEquals("lock:ORDER-123", keys[0]);
        }

        @Test
        @DisplayName("空前缀不添加")
        void testEmptyPrefix() throws Exception {
            Method method = TestService.class.getMethod("processOrder", String.class);
            String[] keys = setupAndResolveKeysWithPrefix(method, new Object[]{"ORDER-123"}, new String[]{"orderId"}, "");

            assertEquals(1, keys.length);
            assertEquals("ORDER-123", keys[0]);
        }
    }

    @Nested
    @DisplayName("自定义 KeyBuilder 测试")
    class KeyBuilderTests {

        @Test
        @DisplayName("使用自定义 KeyBuilder")
        void testCustomKeyBuilder() throws Exception {
            Method method = TestService.class.getMethod("processWithBuilder", String.class);
            
            TestKeyBuilder keyBuilder = new TestKeyBuilder();
            when(applicationContext.getBean(TestKeyBuilder.class)).thenReturn(keyBuilder);

            Lock annotation = createLockAnnotationWithKeyBuilder(TestKeyBuilder.class);
            String[] keys = resolveKeys(method, new Object[]{"test"}, new String[]{"orderId"}, annotation);

            assertEquals(1, keys.length);
            assertEquals("CUSTOM-KEY", keys[0]);
        }
    }

    @Nested
    @DisplayName("Provider 解析测试")
    class ProviderTests {

        @Test
        @DisplayName("获取指定 Provider")
        void testResolveSpecificProvider() {
            doReturn(lockProvider).when(providerFactory).getProvider(TestLockProvider.class);

            Lock annotation = mock(Lock.class);
            doReturn(TestLockProvider.class).when(annotation).provider();

            LockProvider result = executor.resolveProvider(annotation);

            assertNotNull(result);
            verify(providerFactory).getProvider(TestLockProvider.class);
        }

        @Test
        @DisplayName("获取默认 Provider")
        void testResolveDefaultProvider() {
            doReturn(null).when(providerFactory).getProvider(TestLockProvider.class);
            doReturn(lockProvider).when(providerFactory).getDefaultProvider();

            Lock annotation = mock(Lock.class);
            doReturn(TestLockProvider.class).when(annotation).provider();

            LockProvider result = executor.resolveProvider(annotation);

            assertNotNull(result);
            verify(providerFactory).getDefaultProvider();
        }

        @Test
        @DisplayName("无可用 Provider 抛出异常")
        void testNoProviderAvailable() {
            doReturn(null).when(providerFactory).getProvider(TestLockProvider.class);
            doReturn(null).when(providerFactory).getDefaultProvider();

            Lock annotation = mock(Lock.class);
            doReturn(TestLockProvider.class).when(annotation).provider();

            assertThrows(IllegalStateException.class, () -> executor.resolveProvider(annotation));
        }
    }

    private String[] setupAndResolveKeys(Method method, Object[] args, String[] paramNames) throws Exception {
        Lock annotation = createLockAnnotation(method);
        return resolveKeys(method, args, paramNames, annotation);
    }

    private String[] setupAndResolveKeysWithPrefix(Method method, Object[] args, String[] paramNames, String prefix) throws Exception {
        Lock annotation = createLockAnnotationWithPrefix(prefix);
        return resolveKeys(method, args, paramNames, annotation);
    }

    private String[] resolveKeysWithPolicy(Method method, Object[] args, String[] paramNames, KeyAbsentPolicy policy) throws Exception {
        Lock annotation = createLockAnnotationWithPolicy(policy);
        return resolveKeys(method, args, paramNames, annotation);
    }

    private String[] resolveKeys(Method method, Object[] args, String[] paramNames, Lock annotation) throws Exception {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(paramNames);
        when(joinPoint.getArgs()).thenReturn(args);

        return executor.resolveLockKeys(joinPoint, annotation);
    }

    private Lock createLockAnnotation(Method method) {
        Lock annotation = method.getAnnotation(Lock.class);
        if (annotation != null) {
            return annotation;
        }
        return createMockLockAnnotation();
    }

    @SuppressWarnings("unchecked")
    private Lock createLockAnnotationWithPrefix(String prefix) {
        Lock annotation = mock(Lock.class);
        doReturn(new String[]{"#orderId"}).when(annotation).keys();
        doReturn(prefix).when(annotation).prefix();
        doReturn(KeyBuilder.class).when(annotation).keyBuilder();
        doReturn(KeyAbsentPolicy.USE_METHOD_PATH).when(annotation).keyAbsentPolicy();
        return annotation;
    }

    @SuppressWarnings("unchecked")
    private Lock createLockAnnotationWithPolicy(KeyAbsentPolicy policy) {
        Lock annotation = mock(Lock.class);
        doReturn(new String[]{}).when(annotation).keys();
        doReturn("").when(annotation).prefix();
        doReturn(KeyBuilder.class).when(annotation).keyBuilder();
        doReturn(policy).when(annotation).keyAbsentPolicy();
        return annotation;
    }

    @SuppressWarnings("unchecked")
    private Lock createLockAnnotationWithKeyBuilder(Class<? extends KeyBuilder> keyBuilderClass) {
        Lock annotation = mock(Lock.class);
        doReturn(new String[]{}).when(annotation).keys();
        doReturn("").when(annotation).prefix();
        doReturn(keyBuilderClass).when(annotation).keyBuilder();
        doReturn(KeyAbsentPolicy.USE_METHOD_PATH).when(annotation).keyAbsentPolicy();
        return annotation;
    }

    @SuppressWarnings("unchecked")
    private Lock createMockLockAnnotation() {
        Lock annotation = mock(Lock.class);
        doReturn(new String[]{}).when(annotation).keys();
        doReturn("").when(annotation).prefix();
        doReturn(KeyBuilder.class).when(annotation).keyBuilder();
        doReturn(KeyAbsentPolicy.USE_METHOD_PATH).when(annotation).keyAbsentPolicy();
        doReturn(3000L).when(annotation).waitTime();
        doReturn(30000L).when(annotation).leaseTime();
        doReturn(TimeUnit.MILLISECONDS).when(annotation).timeUnit();
        doReturn(LockType.REENTRANT).when(annotation).lockType();
        return annotation;
    }

    static class TestService {
        @Lock(keys = "#orderId")
        public void processOrder(String orderId) {}

        @Lock(keys = {"#productId", "#warehouseId"})
        public void deductStock(String productId, String warehouseId) {}

        @Lock(keys = "#user.id")
        public void processUser(User user) {}

        @Lock(keys = "#order.id")
        public void processOrderWithNested(Order order) {}

        @Lock
        public void noKeyMethod() {}

        @Lock(keyBuilder = TestKeyBuilder.class)
        public void processWithBuilder(String orderId) {}
    }

    static class TestKeyBuilder implements KeyBuilder {
        @Override
        public String[] build(Method method, String[] parameterNames, Object[] args, Lock annotation) {
            return new String[]{"CUSTOM-KEY"};
        }
    }

    static class TestLockProvider implements LockProvider {
        @Override
        public boolean tryLock(LockKey lockKey, LockOptions options) {
            return true;
        }

        @Override
        public void unlock(LockKey lockKey) {}

        @Override
        public boolean supports(LockType lockType) {
            return true;
        }

        @Override
        public String getName() {
            return "test";
        }
    }

    static class User {
        private final String id;
        private final String name;

        public User(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    static class Order {
        private final String id;
        private final User user;

        public Order(String id, User user) {
            this.id = id;
            this.user = user;
        }

        public String getId() {
            return id;
        }

        public User getUser() {
            return user;
        }
    }
}