# Lock4j

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

[English](README_EN.md) | 中文

基于 Spring Boot 的分布式锁框架，通过 `@Lock` 注解实现本地锁和分布式锁的一键加锁。

## 特性

- 🚀 **简单易用** - 一个 `@Lock` 注解搞定分布式锁
- 🔧 **多引擎支持** - Redisson、RedisTemplate、Zookeeper、Etcd、本地锁
- 📝 **派生注解** - `@RedissonLock`、`@RedisTemplateLock`、`@ZookeeperLock`、`@EtcdLock`、`@LocalLock`
- 🔑 **SpEL 表达式** - 灵活的 Key 构建，支持方法参数和对象属性
- 🏗️ **自定义 KeyBuilder** - 复杂场景可扩展
- 🔒 **多种锁类型** - 可重入锁、公平锁、读写锁
- ⏱️ **Watchdog 自动续期** - 防止业务未执行完锁过期
- 🎯 **失败处理** - 可自定义加锁失败处理策略
- 🔌 **拦截器机制** - 流程钩子，支持监控、日志、链路追踪
- 📡 **事件监听** - 锁生命周期事件发布
- 🔄 **多 Key 加锁** - 支持同时对多个 Key 加锁
- 🏗️ **Spring Boot 集成** - 自动配置，开箱即用

## 模块说明

```
lock4j
├── lock4j-core                    # 核心模块，定义接口和抽象类
├── lock4j-redisson                # Redisson 实现 (Redis)
├── lock4j-redis-template          # RedisTemplate 实现 (Redis)
├── lock4j-zookeeper               # Zookeeper 实现 (Curator 5.x)
├── lock4j-etcd                    # Etcd 实现 (jetcd 0.7.x)
├── lock4j-local                   # 本地锁实现
├── lock4j-spring-boot-starter     # Spring Boot 自动配置
└── lock4j-examples                # 使用示例
```

## 快速开始

### 1. 添加依赖

**Maven:**

```xml
<dependency>
    <groupId>com.geek.lock</groupId>
    <artifactId>lock4j-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- 根据需要添加具体 Provider 依赖 -->
<dependency>
    <groupId>com.geek.lock</groupId>
    <artifactId>lock4j-redisson</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'com.geek.lock:lock4j-spring-boot-starter:1.0.0-SNAPSHOT'
implementation 'com.geek.lock:lock4j-redisson:1.0.0-SNAPSHOT'
```

### 2. 配置 Provider

```yaml
lock4j:
  enabled: true
  primary-provider: redissonLockProvider
  
  redisson:
    enabled: true
    address: localhost:6379
```

### 3. 使用注解

```java
@Service
public class OrderService {

    @Lock(keys = "#orderId")
    public void processOrder(String orderId) {
        // 业务逻辑
    }
}
```

## 支持的 LockProvider

| Provider | 锁类型 | 实现技术 | 说明 |
|----------|--------|----------|------|
| **Redisson** | 可重入、公平、读写 | Redisson 3.x | 功能最全，推荐使用 |
| **RedisTemplate** | 可重入 | Lua 脚本 | 轻量级，依赖少 |
| **Zookeeper** | 可重入、读写 | Curator 5.x | CP 一致性 |
| **Etcd** | 可重入、公平 | jetcd 0.7.x | 云原生场景 |
| **Local** | 可重入、公平、读写 | JDK ReentrantLock | 单进程使用 |

## 使用示例

### 基本使用

#### SpEL 表达式

```java
@Service
public class OrderService {

    // 方法参数
    @Lock(keys = "#orderId")
    public void processOrder(String orderId) {
        // 业务逻辑
    }

    // 对象属性
    @Lock(keys = "#order.id")
    public void processOrder(Order order) {
        // 业务逻辑
    }

    // 嵌套属性
    @Lock(keys = "#order.user.id")
    public void processOrderByUser(Order order) {
        // 业务逻辑
    }

    // 多个 Key
    @Lock(keys = {"#productId", "#warehouseId"})
    public void deductStock(String productId, String warehouseId) {
        // 业务逻辑
    }
}
```

#### 指定锁参数

```java
@Service
public class PaymentService {

    // 自定义等待时间和过期时间
    @Lock(keys = "#orderId", waitTime = 5000, leaseTime = 60000)
    public void processPayment(String orderId) {
        // 业务逻辑
    }

    // 指定时间单位
    @Lock(keys = "#orderId", waitTime = 5, leaseTime = 60, timeUnit = TimeUnit.SECONDS)
    public void processPaymentWithSeconds(String orderId) {
        // 业务逻辑
    }

    // 添加 Key 前缀
    @Lock(keys = "#orderId", prefix = "order:lock:")
    public void processOrderWithPrefix(String orderId) {
        // 业务逻辑
    }
}
```

### 锁类型

```java
@Service
public class ResourceService {

    // 可重入锁（默认）
    @Lock(keys = "#resourceId", lockType = LockType.REENTRANT)
    public void processResource(String resourceId) {
        // 业务逻辑
    }

    // 公平锁
    @Lock(keys = "#resourceId", lockType = LockType.FAIR)
    public void processWithFairLock(String resourceId) {
        // 业务逻辑
    }

    // 读锁（共享锁）
    @Lock(keys = "#resourceId", lockType = LockType.READ)
    public String readResource(String resourceId) {
        // 读取逻辑
        return "data";
    }

    // 写锁（排他锁）
    @Lock(keys = "#resourceId", lockType = LockType.WRITE)
    public void writeResource(String resourceId, String data) {
        // 写入逻辑
    }
}
```

### 派生注解

每个 LockProvider 都有对应的派生注解，自动指定 Provider：

```java
@Service
public class LockService {

    // Redisson 锁
    @RedissonLock(keys = "#id", lockType = LockType.FAIR)
    public void processWithRedisson(String id) {
        // 业务逻辑
    }

    // RedisTemplate 锁
    @RedisTemplateLock(keys = "#id", waitTime = 3000, leaseTime = 30000)
    public void processWithRedisTemplate(String id) {
        // 业务逻辑
    }

    // Zookeeper 锁
    @ZookeeperLock(keys = "#id", lockType = LockType.READ)
    public String readWithZookeeper(String id) {
        return "data";
    }

    // Etcd 锁
    @EtcdLock(keys = "#id", lockType = LockType.FAIR)
    public void processWithEtcd(String id) {
        // 业务逻辑
    }

    // 本地锁
    @LocalLock(keys = "#id")
    public void processWithLocal(String id) {
        // 业务逻辑
    }
}
```

### 自定义 KeyBuilder

当 SpEL 表达式无法满足复杂场景时，可自定义 KeyBuilder：

```java
// 1. 实现自定义 KeyBuilder
@Component
public class OrderKeyBuilder extends AbstractKeyBuilder {
    
    @Override
    protected String[] doBuild(Method method, String[] parameterNames, 
                               Object[] args, Lock annotation) {
        Order order = (Order) args[0];
        String userId = (String) args[1];
        // 根据业务逻辑构建 Key
        return new String[]{
            "order:" + order.getId(),
            "user:" + userId
        };
    }
}

// 2. 使用自定义 KeyBuilder
@Service
public class OrderService {

    @Lock(keyBuilder = OrderKeyBuilder.class)
    public void processOrder(Order order, String userId) {
        // 业务逻辑
    }
}
```

### 失败处理

#### 内置失败处理器

```java
@Service
public class OrderService {

    // 默认：抛出 LockFailureException
    @Lock(keys = "#orderId")
    public void processOrder(String orderId) {
        // 业务逻辑
    }

    // 抛出自定义异常
    @Lock(keys = "#orderId", failFast = OrderLockException.class)
    public void processOrderWithCustomException(String orderId) {
        // 业务逻辑
    }
}
```

#### 自定义失败处理器

```java
// 1. 实现自定义失败处理器
@Component
public class RetryFailureHandler implements FailureHandler {
    
    private static final int MAX_RETRY = 3;
    
    @Override
    public Object handle(LockFailureContext context) {
        String[] keys = context.getLockKeys();
        Method method = context.getMethod();
        Object[] args = context.getArgs();
        
        log.warn("Lock failed for keys: {}, method: {}", 
                 Arrays.toString(keys), method.getName());
        
        // 返回默认值
        return getDefaultValue(method.getReturnType());
    }
    
    private Object getDefaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == String.class) return "LOCK_FAILED";
        return null;
    }
}

// 2. 使用自定义失败处理器
@Service
public class OrderService {

    @Lock(keys = "#orderId", failureHandler = RetryFailureHandler.class)
    public Order getOrder(String orderId) {
        // 业务逻辑
        return orderRepository.findById(orderId);
    }
    
    // 失败时返回 null 而不是抛异常
    @Lock(keys = "#orderId", failureHandler = RetryFailureHandler.class)
    public String processOrder(String orderId) {
        // 业务逻辑
        return "SUCCESS";
    }
}
```

### 拦截器

拦截器提供锁执行流程各阶段的钩子方法，可用于监控、日志、链路追踪等。

#### 定义拦截器

```java
@Component
public class LoggingLockInterceptor implements LockInterceptor {
    
    @Override
    public void beforeKeyBuild(Method method, Object[] args, Lock annotation) {
        log.info("[Lock] 准备构建 Key, 方法: {}, 参数: {}", 
                 method.getName(), Arrays.toString(args));
    }
    
    @Override
    public void afterKeyBuild(List<String> keys) {
        log.info("[Lock] Key 构建完成: {}", keys);
    }
    
    @Override
    public void beforeLock(List<String> keys, LockOptions options) {
        log.info("[Lock] 尝试加锁: {}, 等待时间: {}ms, 租期: {}ms", 
                 keys, options.getWaitTime(), options.getLeaseTime());
    }
    
    @Override
    public void afterLock(List<String> keys) {
        log.debug("[Lock] 单个 Key 加锁操作完成: {}", keys);
    }
    
    @Override
    public void onLockSuccess(List<String> keys, LockKey lockKey) {
        log.info("[Lock] 加锁成功: {}", lockKey.getKey());
    }
    
    @Override
    public void onLockFailure(List<String> keys) {
        log.warn("[Lock] 加锁失败: {}", keys);
    }
    
    @Override
    public void onException(List<String> keys, Throwable exception) {
        log.error("[Lock] 执行异常: {}, keys: {}", 
                  exception.getMessage(), keys, exception);
    }
}
```

#### 使用拦截器

```java
@Service
public class OrderService {

    @Lock(keys = "#orderId", interceptor = LoggingLockInterceptor.class)
    public void processOrder(String orderId) {
        // 业务逻辑
    }
    
    // 结合其他属性
    @Lock(keys = "#orderId", 
          interceptor = LoggingLockInterceptor.class,
          waitTime = 5000,
          leaseTime = 60000)
    public Order processOrderWithLog(String orderId) {
        // 业务逻辑
        return order;
    }
}
```

#### 拦截器执行流程

```
beforeKeyBuild(method, args, annotation)
        ↓
    解析 Key
        ↓
afterKeyBuild(keys)
        ↓
beforeLock(keys, options)
        ↓
    ┌─── 循环每个 Key ───┐
    │                    │
    │  afterLock(key)    │
    │        ↓           │
    │   尝试加锁          │
    │        ↓           │
    │  成功 → onLockSuccess(key, lockKey)
    │  失败 → onLockFailure(keys) → 失败处理
    │                    │
    └────────────────────┘
        ↓
   执行业务方法
        ↓
    成功 → 返回结果
    异常 → onException(keys, exception)
```

### 事件监听

通过 Spring 事件机制监听锁的生命周期事件：

```java
@Component
public class LockEventListener {

    @EventListener
    public void onLockEvent(LockEvent event) {
        LockEventType type = event.getType();
        List<String> keys = event.getKeys();
        
        switch (type) {
            case BEFORE_LOCK:
                log.info("加锁开始: {}", keys);
                metrics.increment("lock.attempt");
                break;
                
            case AFTER_LOCK:
                log.info("加锁成功: {}", keys);
                metrics.increment("lock.success");
                break;
                
            case LOCK_FAILED:
                log.warn("加锁失败: {}", keys);
                metrics.increment("lock.failed");
                break;
                
            case LOCK_ERROR:
                log.error("加锁错误: {}", keys);
                metrics.increment("lock.error");
                break;
                
            case BEFORE_UNLOCK:
                log.debug("准备解锁: {}", keys);
                break;
                
            case AFTER_UNLOCK:
                log.debug("解锁完成: {}", keys);
                break;
        }
    }
}
```

### Key 缺失策略

当 `keys` 和 `keyBuilder` 都为空时的处理策略：

```java
@Service
public class DefaultKeyService {

    // 使用方法全限定名作为 Key（默认）
    @Lock
    public void processWithDefaultKey() {
        // Key = "com.example.DefaultKeyService.processWithDefaultKey"
    }

    // 使用方法全限定名作为 Key，自定义超时
    @Lock(leaseTime = 60000)
    public void processWithLongLease() {
        // 业务逻辑
    }

    // Key 为空时抛出异常
    @Lock(keyAbsentPolicy = KeyAbsentPolicy.THROW_EXCEPTION)
    public void processWithStrictKey() {
        // 如果未指定 keys，会抛出 IllegalArgumentException
    }
}
```

### 指定 Provider

当存在多个 LockProvider 时，可以指定使用哪个：

```java
@Service
public class MultiProviderService {

    // 使用默认 Provider（primary-provider 配置）
    @Lock(keys = "#id")
    public void processWithDefault(String id) {
        // 业务逻辑
    }

    // 指定使用 Redisson
    @Lock(keys = "#id", provider = RedissonLockProvider.class)
    public void processWithRedisson(String id) {
        // 业务逻辑
    }

    // 指定使用 RedisTemplate
    @Lock(keys = "#id", provider = RedisTemplateLockProvider.class)
    public void processWithRedisTemplate(String id) {
        // 业务逻辑
    }

    // 指定使用 Zookeeper
    @Lock(keys = "#id", provider = ZookeeperLockProvider.class)
    public void processWithZookeeper(String id) {
        // 业务逻辑
    }

    // 指定使用本地锁
    @Lock(keys = "#id", provider = LocalLockProvider.class)
    public void processWithLocal(String id) {
        // 业务逻辑
    }
}
```

### 综合示例

```java
@Service
public class ComprehensiveOrderService {

    private final OrderRepository orderRepository;
    
    /**
     * 完整的锁使用示例
     * - 使用 SpEL 表达式构建 Key
     * - 添加前缀
     * - 自定义等待时间和租期
     * - 使用公平锁
     * - 添加拦截器
     * - 自定义失败处理
     */
    @Lock(
        keys = "#order.id",
        prefix = "order:process:",
        waitTime = 5000,
        leaseTime = 60000,
        lockType = LockType.FAIR,
        interceptor = LoggingLockInterceptor.class,
        failureHandler = RetryFailureHandler.class
    )
    public Order processOrder(Order order) {
        // 业务逻辑
        return orderRepository.save(order);
    }
    
    /**
     * 多 Key 加锁示例
     * 同时锁定用户和订单
     */
    @Lock(
        keys = {"#userId", "#orderId"},
        prefix = "deduct:",
        interceptor = LoggingLockInterceptor.class
    )
    public void deductBalance(String userId, String orderId, BigDecimal amount) {
        // 扣减余额逻辑
    }
    
    /**
     * 读写锁示例
     */
    @Lock(
        keys = "#productId",
        lockType = LockType.READ,
        provider = RedissonLockProvider.class
    )
    public Product getProduct(String productId) {
        return orderRepository.findProductById(productId);
    }
    
    @Lock(
        keys = "#product.id",
        lockType = LockType.WRITE,
        provider = RedissonLockProvider.class
    )
    public Product updateProduct(Product product) {
        return orderRepository.saveProduct(product);
    }
}
```

## @Lock 注解属性

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `keys` | 锁 Key 数组，支持 SpEL | String[] | {} |
| `keyBuilder` | 自定义 KeyBuilder 类型 | Class<? extends KeyBuilder> | KeyBuilder.class |
| `keyAbsentPolicy` | Key 缺失策略 | KeyAbsentPolicy | USE_METHOD_PATH |
| `prefix` | 锁 Key 前缀 | String | "" |
| `waitTime` | 等待获取锁时间(ms) | long | 3000 |
| `leaseTime` | 锁过期时间(ms) | long | 30000 |
| `timeUnit` | 时间单位 | TimeUnit | MILLISECONDS |
| `lockType` | 锁类型 | LockType | REENTRANT |
| `failureHandler` | 加锁失败处理器 | Class<? extends FailureHandler> | FailureHandler.Default.class |
| `failFast` | 失败异常类型 | Class<? extends RuntimeException> | LockFailureException.class |
| `provider` | 指定 LockProvider | Class<? extends LockProvider> | LockProvider.class |
| `interceptor` | 拦截器 | Class<? extends LockInterceptor> | LockInterceptor.class |

## 配置说明

### 完整配置示例

```yaml
lock4j:
  # 全局配置
  enabled: true
  primary-provider: redissonLockProvider
  default-wait-time: 3000
  default-lease-time: 30000

  # Redisson 配置
  redisson:
    enabled: true
    address: localhost:6379
    password: 
    database: 0
    connection-pool-size: 64
    connection-minimum-idle-size: 10
    timeout: 3000ms
    # 单机模式
    # address: localhost:6379
    # 集群模式
    cluster:
      node-addresses:
        - redis://127.0.0.1:7000
        - redis://127.0.0.1:7001
        - redis://127.0.0.1:7002
    # 哨兵模式
    sentinel:
      master-name: mymaster
      sentinel-addresses:
        - redis://127.0.0.1:26379
        - redis://127.0.0.1:26380

  # RedisTemplate 配置
  redis:
    enabled: true
    host: localhost
    port: 6379
    password:
    database: 0
    connect-timeout: 3000ms

  # Zookeeper 配置
  zookeeper:
    enabled: false
    connect-string: localhost:2181
    session-timeout: 30000
    connection-timeout: 10000
    base-path: /lock4j

  # Etcd 配置
  etcd:
    enabled: false
    endpoints:
      - http://localhost:2379
    user:
    password:
    connect-timeout: 5000

  # 本地锁配置
  local:
    enabled: true
```

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      @Lock Annotation                        │
│   (@RedissonLock, @RedisTemplateLock, @ZookeeperLock, ...)  │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                        LockAspect                           │
│                   (AOP 切面拦截)                             │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    LockExecutor                             │
│    (SpEL解析 / KeyBuilder / Interceptor / 失败处理)         │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ SpEL Parser │  │ KeyBuilder  │  │FailureHandler│         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              LockInterceptor (流程钩子)               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                  LockProviderFactory                        │
└─────────────────────────────────────────────────────────────┘
                               │
          ┌────────────┬───────┴───────┬────────────┐
          ▼            ▼               ▼            ▼
    ┌──────────┐ ┌──────────┐   ┌──────────┐ ┌──────────┐
    │ Redisson │ │  Redis   │   │Zookeeper │ │   Etcd   │
    │ Provider │ │Template  │   │ Provider │ │ Provider │
    └──────────┘ └──────────┘   └──────────┘ └──────────┘
```

## 技术栈

- JDK 17+
- Spring Boot 3.x
- Spring AOP
- Redisson 3.25.2
- Curator 5.5.0
- jetcd 0.7.6
- Lombok

## 注意事项

1. **锁 Key 设计** - 建议使用业务相关的唯一标识作为 Key
2. **锁超时时间** - `leaseTime` 应大于业务执行时间
3. **等待时间** - `waitTime` 需根据业务场景权衡
4. **多 Key 加锁** - 采用全部加锁策略，必须获取所有锁才算成功
5. **异常处理** - 业务方法抛出异常时，锁会自动释放
6. **Watchdog** - 启用后自动续期，防止业务未执行完锁过期

## License

Apache License 2.0