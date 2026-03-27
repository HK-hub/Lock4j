# Lock4j

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

中文 | [English](README_EN.md)

A Spring Boot-based distributed lock framework that enables one-click locking via the `@Lock` annotation.

## Features

- 🚀 **Easy to Use** - One `@Lock` annotation handles distributed locking
- 🔧 **Multiple Engines** - Redisson, RedisTemplate, Zookeeper, Etcd, Local lock
- 📝 **Derived Annotations** - `@RedissonLock`, `@RedisTemplateLock`, `@ZookeeperLock`, `@EtcdLock`, `@LocalLock`
- 🔑 **SpEL Expression** - Flexible key building with method parameters and object properties
- 🏗️ **Custom KeyBuilder** - Extensible for complex scenarios
- 🔒 **Multiple Lock Types** - Reentrant, Fair, Read/Write locks
- ⏱️ **Watchdog Auto-Renewal** - Prevents lock expiration during long operations
- 🎯 **Failure Handling** - Customizable lock failure strategies
- 🔌 **Interceptor Mechanism** - Lifecycle hooks for monitoring, logging, tracing
- 📡 **Event Listening** - Lock lifecycle event publishing
- 🔄 **Multi-Key Locking** - Lock multiple keys simultaneously
- 🏗️ **Spring Boot Integration** - Auto-configuration, ready to use

## Modules

```
lock4j
├── lock4j-core                    # Core module, interfaces and abstract classes
├── lock4j-redisson                # Redisson implementation (Redis)
├── lock4j-redis-template          # RedisTemplate implementation (Redis)
├── lock4j-zookeeper               # Zookeeper implementation (Curator 5.x)
├── lock4j-etcd                    # Etcd implementation (jetcd 0.7.x)
├── lock4j-local                   # Local lock implementation
├── lock4j-spring-boot-starter     # Spring Boot auto-configuration
└── lock4j-examples                # Usage examples
```

## Quick Start

### 1. Add Dependency

**Maven:**

```xml
<dependency>
    <groupId>com.geek.lock</groupId>
    <artifactId>lock4j-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Add a LockProvider dependency -->
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

### 2. Configure Provider

```yaml
lock4j:
  enabled: true
  primary-provider: redissonLockProvider
  
  redisson:
    enabled: true
    address: localhost:6379
```

### 3. Use Annotation

```java
@Service
public class OrderService {

    @Lock(keys = "#orderId")
    public void processOrder(String orderId) {
        // business logic
    }
}
```

## Supported LockProviders

| Provider | Lock Types | Implementation | Description |
|----------|------------|----------------|-------------|
| **Redisson** | Reentrant, Fair, Read/Write | Redisson 3.x | Full-featured, recommended |
| **RedisTemplate** | Reentrant | Lua script | Lightweight, minimal dependencies |
| **Zookeeper** | Reentrant, Read/Write | Curator 5.x | CP consistency |
| **Etcd** | Reentrant, Fair | jetcd 0.7.x | Cloud-native scenarios |
| **Local** | Reentrant, Fair, Read/Write | JDK ReentrantLock | Single process |

## Usage Examples

### Basic Usage

#### SpEL Expression

```java
@Service
public class OrderService {

    // Method parameter
    @Lock(keys = "#orderId")
    public void processOrder(String orderId) {
        // business logic
    }

    // Object property
    @Lock(keys = "#order.id")
    public void processOrder(Order order) {
        // business logic
    }

    // Nested property
    @Lock(keys = "#order.user.id")
    public void processOrderByUser(Order order) {
        // business logic
    }

    // Multiple keys
    @Lock(keys = {"#productId", "#warehouseId"})
    public void deductStock(String productId, String warehouseId) {
        // business logic
    }
}
```

#### Specify Lock Parameters

```java
@Service
public class PaymentService {

    // Custom wait time and lease time
    @Lock(keys = "#orderId", waitTime = 5000, leaseTime = 60000)
    public void processPayment(String orderId) {
        // business logic
    }

    // Specify time unit
    @Lock(keys = "#orderId", waitTime = 5, leaseTime = 60, timeUnit = TimeUnit.SECONDS)
    public void processPaymentWithSeconds(String orderId) {
        // business logic
    }

    // Add key prefix
    @Lock(keys = "#orderId", prefix = "order:lock:")
    public void processOrderWithPrefix(String orderId) {
        // business logic
    }
}
```

### Lock Types

```java
@Service
public class ResourceService {

    // Reentrant lock (default)
    @Lock(keys = "#resourceId", lockType = LockType.REENTRANT)
    public void processResource(String resourceId) {
        // business logic
    }

    // Fair lock
    @Lock(keys = "#resourceId", lockType = LockType.FAIR)
    public void processWithFairLock(String resourceId) {
        // business logic
    }

    // Read lock (shared lock)
    @Lock(keys = "#resourceId", lockType = LockType.READ)
    public String readResource(String resourceId) {
        // read logic
        return "data";
    }

    // Write lock (exclusive lock)
    @Lock(keys = "#resourceId", lockType = LockType.WRITE)
    public void writeResource(String resourceId, String data) {
        // write logic
    }
}
```

### Derived Annotations

Each LockProvider has a corresponding derived annotation:

```java
@Service
public class LockService {

    // Redisson lock
    @RedissonLock(keys = "#id", lockType = LockType.FAIR)
    public void processWithRedisson(String id) {
        // business logic
    }

    // RedisTemplate lock
    @RedisTemplateLock(keys = "#id", waitTime = 3000, leaseTime = 30000)
    public void processWithRedisTemplate(String id) {
        // business logic
    }

    // Zookeeper lock
    @ZookeeperLock(keys = "#id", lockType = LockType.READ)
    public String readWithZookeeper(String id) {
        return "data";
    }

    // Etcd lock
    @EtcdLock(keys = "#id", lockType = LockType.FAIR)
    public void processWithEtcd(String id) {
        // business logic
    }

    // Local lock
    @LocalLock(keys = "#id")
    public void processWithLocal(String id) {
        // business logic
    }
}
```

### Custom KeyBuilder

When SpEL expressions cannot handle complex scenarios, implement a custom KeyBuilder:

```java
// 1. Implement custom KeyBuilder
@Component
public class OrderKeyBuilder extends AbstractKeyBuilder {
    
    @Override
    protected String[] doBuild(Method method, String[] parameterNames, 
                               Object[] args, Lock annotation) {
        Order order = (Order) args[0];
        String userId = (String) args[1];
        // Build keys based on business logic
        return new String[]{
            "order:" + order.getId(),
            "user:" + userId
        };
    }
}

// 2. Use custom KeyBuilder
@Service
public class OrderService {

    @Lock(keyBuilder = OrderKeyBuilder.class)
    public void processOrder(Order order, String userId) {
        // business logic
    }
}
```

### Failure Handling

#### Built-in Failure Handlers

```java
@Service
public class OrderService {

    // Default: throws LockFailureException
    @Lock(keys = "#orderId")
    public void processOrder(String orderId) {
        // business logic
    }

    // Throw custom exception
    @Lock(keys = "#orderId", failFast = OrderLockException.class)
    public void processOrderWithCustomException(String orderId) {
        // business logic
    }
}
```

#### Custom Failure Handler

```java
// 1. Implement custom failure handler
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
        
        // Return default value
        return getDefaultValue(method.getReturnType());
    }
    
    private Object getDefaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == String.class) return "LOCK_FAILED";
        return null;
    }
}

// 2. Use custom failure handler
@Service
public class OrderService {

    @Lock(keys = "#orderId", failureHandler = RetryFailureHandler.class)
    public Order getOrder(String orderId) {
        // business logic
        return orderRepository.findById(orderId);
    }
    
    // Return null instead of throwing exception on failure
    @Lock(keys = "#orderId", failureHandler = RetryFailureHandler.class)
    public String processOrder(String orderId) {
        // business logic
        return "SUCCESS";
    }
}
```

### Interceptor

Interceptors provide hooks at various stages of the lock execution flow for monitoring, logging, tracing, etc.

#### Define Interceptor

```java
@Component
public class LoggingLockInterceptor implements LockInterceptor {
    
    @Override
    public void beforeKeyBuild(Method method, Object[] args, Lock annotation) {
        log.info("[Lock] Preparing to build key, method: {}, args: {}", 
                 method.getName(), Arrays.toString(args));
    }
    
    @Override
    public void afterKeyBuild(List<String> keys) {
        log.info("[Lock] Key build complete: {}", keys);
    }
    
    @Override
    public void beforeLock(List<String> keys, LockOptions options) {
        log.info("[Lock] Attempting to lock: {}, waitTime: {}ms, leaseTime: {}ms", 
                 keys, options.getWaitTime(), options.getLeaseTime());
    }
    
    @Override
    public void afterLock(List<String> keys) {
        log.debug("[Lock] Single key lock operation complete: {}", keys);
    }
    
    @Override
    public void onLockSuccess(List<String> keys, LockKey lockKey) {
        log.info("[Lock] Lock acquired: {}", lockKey.getKey());
    }
    
    @Override
    public void onLockFailure(List<String> keys) {
        log.warn("[Lock] Lock failed: {}", keys);
    }
    
    @Override
    public void onException(List<String> keys, Throwable exception) {
        log.error("[Lock] Exception occurred: {}, keys: {}", 
                  exception.getMessage(), keys, exception);
    }
}
```

#### Use Interceptor

```java
@Service
public class OrderService {

    @Lock(keys = "#orderId", interceptor = LoggingLockInterceptor.class)
    public void processOrder(String orderId) {
        // business logic
    }
    
    // Combined with other attributes
    @Lock(keys = "#orderId", 
          interceptor = LoggingLockInterceptor.class,
          waitTime = 5000,
          leaseTime = 60000)
    public Order processOrderWithLog(String orderId) {
        // business logic
        return order;
    }
}
```

#### Interceptor Execution Flow

```
beforeKeyBuild(method, args, annotation)
        ↓
    Parse Keys
        ↓
afterKeyBuild(keys)
        ↓
beforeLock(keys, options)
        ↓
    ┌─── Loop Each Key ───┐
    │                     │
    │  afterLock(key)     │
    │        ↓            │
    │  Try Lock           │
    │        ↓            │
    │  Success → onLockSuccess(key, lockKey)
    │  Failure → onLockFailure(keys) → Failure Handler
    │                     │
    └─────────────────────┘
        ↓
 Execute Business Method
        ↓
    Success → Return Result
    Exception → onException(keys, exception)
```

### Event Listening

Listen to lock lifecycle events via Spring event mechanism:

```java
@Component
public class LockEventListener {

    @EventListener
    public void onLockEvent(LockEvent event) {
        LockEventType type = event.getType();
        List<String> keys = event.getKeys();
        
        switch (type) {
            case BEFORE_LOCK:
                log.info("Lock starting: {}", keys);
                metrics.increment("lock.attempt");
                break;
                
            case AFTER_LOCK:
                log.info("Lock acquired: {}", keys);
                metrics.increment("lock.success");
                break;
                
            case LOCK_FAILED:
                log.warn("Lock failed: {}", keys);
                metrics.increment("lock.failed");
                break;
                
            case LOCK_ERROR:
                log.error("Lock error: {}", keys);
                metrics.increment("lock.error");
                break;
                
            case BEFORE_UNLOCK:
                log.debug("Preparing to unlock: {}", keys);
                break;
                
            case AFTER_UNLOCK:
                log.debug("Unlock complete: {}", keys);
                break;
        }
    }
}
```

### Key Absent Policy

Handling strategy when both `keys` and `keyBuilder` are empty:

```java
@Service
public class DefaultKeyService {

    // Use method fully qualified name as key (default)
    @Lock
    public void processWithDefaultKey() {
        // Key = "com.example.DefaultKeyService.processWithDefaultKey"
    }

    // Use method fully qualified name as key with custom timeout
    @Lock(leaseTime = 60000)
    public void processWithLongLease() {
        // business logic
    }

    // Throw exception when key is absent
    @Lock(keyAbsentPolicy = KeyAbsentPolicy.THROW_EXCEPTION)
    public void processWithStrictKey() {
        // Throws IllegalArgumentException if keys not specified
    }
}
```

### Specify Provider

When multiple LockProviders exist, specify which one to use:

```java
@Service
public class MultiProviderService {

    // Use default provider (primary-provider config)
    @Lock(keys = "#id")
    public void processWithDefault(String id) {
        // business logic
    }

    // Specify Redisson
    @Lock(keys = "#id", provider = RedissonLockProvider.class)
    public void processWithRedisson(String id) {
        // business logic
    }

    // Specify RedisTemplate
    @Lock(keys = "#id", provider = RedisTemplateLockProvider.class)
    public void processWithRedisTemplate(String id) {
        // business logic
    }

    // Specify Zookeeper
    @Lock(keys = "#id", provider = ZookeeperLockProvider.class)
    public void processWithZookeeper(String id) {
        // business logic
    }

    // Specify Local lock
    @Lock(keys = "#id", provider = LocalLockProvider.class)
    public void processWithLocal(String id) {
        // business logic
    }
}
```

### Comprehensive Example

```java
@Service
public class ComprehensiveOrderService {

    private final OrderRepository orderRepository;
    
    /**
     * Complete lock usage example
     * - SpEL expression for key building
     * - Key prefix
     * - Custom wait time and lease time
     * - Fair lock
     * - Interceptor
     * - Custom failure handler
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
        // business logic
        return orderRepository.save(order);
    }
    
    /**
     * Multi-key lock example
     * Lock both user and order simultaneously
     */
    @Lock(
        keys = {"#userId", "#orderId"},
        prefix = "deduct:",
        interceptor = LoggingLockInterceptor.class
    )
    public void deductBalance(String userId, String orderId, BigDecimal amount) {
        // Balance deduction logic
    }
    
    /**
     * Read/Write lock example
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

## @Lock Annotation Attributes

| Attribute | Description | Type | Default |
|-----------|-------------|------|---------|
| `keys` | Lock key array, supports SpEL | String[] | {} |
| `keyBuilder` | Custom KeyBuilder type | Class<? extends KeyBuilder> | KeyBuilder.class |
| `keyAbsentPolicy` | Key absent policy | KeyAbsentPolicy | USE_METHOD_PATH |
| `prefix` | Lock key prefix | String | "" |
| `waitTime` | Time to wait for lock (ms) | long | 3000 |
| `leaseTime` | Lock expiration time (ms) | long | 30000 |
| `timeUnit` | Time unit | TimeUnit | MILLISECONDS |
| `lockType` | Lock type | LockType | REENTRANT |
| `failureHandler` | Lock failure handler | Class<? extends FailureHandler> | FailureHandler.Default.class |
| `failFast` | Failure exception type | Class<? extends RuntimeException> | LockFailureException.class |
| `provider` | Specify LockProvider | Class<? extends LockProvider> | LockProvider.class |
| `interceptor` | Interceptor | Class<? extends LockInterceptor> | LockInterceptor.class |

## Configuration

### Full Configuration Example

```yaml
lock4j:
  # Global config
  enabled: true
  primary-provider: redissonLockProvider
  default-wait-time: 3000
  default-lease-time: 30000

  # Redisson config
  redisson:
    enabled: true
    address: localhost:6379
    password: 
    database: 0
    connection-pool-size: 64
    connection-minimum-idle-size: 10
    timeout: 3000ms
    # Standalone mode
    # address: localhost:6379
    # Cluster mode
    cluster:
      node-addresses:
        - redis://127.0.0.1:7000
        - redis://127.0.0.1:7001
        - redis://127.0.0.1:7002
    # Sentinel mode
    sentinel:
      master-name: mymaster
      sentinel-addresses:
        - redis://127.0.0.1:26379
        - redis://127.0.0.1:26380

  # RedisTemplate config
  redis:
    enabled: true
    host: localhost
    port: 6379
    password:
    database: 0
    connect-timeout: 3000ms

  # Zookeeper config
  zookeeper:
    enabled: false
    connect-string: localhost:2181
    session-timeout: 30000
    connection-timeout: 10000
    base-path: /lock4j

  # Etcd config
  etcd:
    enabled: false
    endpoints:
      - http://localhost:2379
    user:
    password:
    connect-timeout: 5000

  # Local lock config
  local:
    enabled: true
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      @Lock Annotation                        │
│   (@RedissonLock, @RedisTemplateLock, @ZookeeperLock, ...)  │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                        LockAspect                           │
│                   (AOP interception)                        │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    LockExecutor                             │
│    (SpEL parsing / KeyBuilder / Interceptor / FailHandler)  │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ SpEL Parser │  │ KeyBuilder  │  │FailureHandler│         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              LockInterceptor (lifecycle hooks)       │   │
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

## Tech Stack

- JDK 17+
- Spring Boot 3.x
- Spring AOP
- Redisson 3.25.2
- Curator 5.5.0
- jetcd 0.7.6
- Lombok

## Notes

1. **Lock Key Design** - Use business-related unique identifiers as keys
2. **Lock Timeout** - `leaseTime` should be greater than business execution time
3. **Wait Time** - Balance `waitTime` based on business requirements
4. **Multi-Key Locking** - Uses all-or-nothing strategy
5. **Exception Handling** - Lock is automatically released on exception
6. **Watchdog** - Auto-renews lock to prevent premature expiration

## License

MIT License