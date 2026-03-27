# Lock4j

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
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

    // Basic usage - SpEL expression
    @Lock(keys = "#orderId")
    public void processOrder(String orderId) {
        // business logic
    }

    // Specify lock parameters
    @Lock(keys = "#userId", waitTime = 5000, leaseTime = 60000)
    public void deductBalance(String userId) {
        // business logic
    }

    // Use derived annotation
    @RedissonLock(keys = "#orderId", lockType = LockType.FAIR)
    public void processOrderWithFairLock(String orderId) {
        // business logic
    }

    // Multi-key locking
    @Lock(keys = {"#productId", "#warehouseId"})
    public void deductStock(String productId, String warehouseId) {
        // business logic
    }

    // Use object property
    @Lock(keys = "#order.id")
    public void processOrder(Order order) {
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

## @Lock Annotation Attributes

| Attribute | Description | Default |
|-----------|-------------|---------|
| `keys` | Lock key array, supports SpEL | {} |
| `keyBuilder` | Custom KeyBuilder type | - |
| `keyAbsentPolicy` | Key absent policy | USE_METHOD_PATH |
| `prefix` | Lock key prefix | "" |
| `waitTime` | Time to wait for lock (ms) | 3000 |
| `leaseTime` | Lock expiration time (ms) | 30000 |
| `timeUnit` | Time unit | MILLISECONDS |
| `lockType` | Lock type | REENTRANT |
| `failureHandler` | Lock failure handler | ThrowException |
| `failFast` | Failure exception type | LockFailureException |
| `provider` | Specify LockProvider | - |
| `interceptor` | Interceptor | - |

## Advanced Usage

### SpEL Expression

```java
// Method parameter
@Lock(keys = "#orderId")
public void process(String orderId) {}

// Object property
@Lock(keys = "#order.user.id")
public void process(Order order) {}

// Multiple keys
@Lock(keys = {"#userId", "#orderId"})
public void process(String userId, String orderId) {}
```

### Custom KeyBuilder

```java
@Component
public class OrderKeyBuilder extends AbstractKeyBuilder {
    @Override
    protected String[] doBuild(Method method, String[] paramNames, 
                               Object[] args, Lock annotation) {
        return new String[]{"order:" + args[0]};
    }
}

@Lock(keyBuilder = OrderKeyBuilder.class)
public void process(String orderId) {}
```

### Failure Handling

```java
@Component
public class RetryFailureHandler implements FailureHandler {
    @Override
    public Object handle(LockFailureContext ctx) {
        // Custom handling logic
        return null;
    }
}

@Lock(keys = "#id", failureHandler = RetryFailureHandler.class)
public void process(String id) {}
```

### Interceptor

Interceptors provide hooks at various stages of the lock execution flow for monitoring, logging, tracing, etc.

```java
@Component
public class LoggingLockInterceptor implements LockInterceptor {
    
    @Override
    public void beforeKeyBuild(Method method, Object[] args, Lock annotation) {
        log.info("Preparing to build lock key, method: {}", method.getName());
    }
    
    @Override
    public void afterKeyBuild(List<String> keys) {
        log.info("Lock keys built: {}", keys);
    }
    
    @Override
    public void beforeLock(List<String> keys, LockOptions options) {
        log.info("Attempting to lock: {}", keys);
    }
    
    @Override
    public void onLockSuccess(List<String> keys, LockKey lockKey) {
        log.info("Lock acquired: {}", lockKey.getKey());
    }
    
    @Override
    public void onLockFailure(List<String> keys) {
        log.warn("Lock failed: {}", keys);
    }
    
    @Override
    public void onException(List<String> keys, Throwable exception) {
        log.error("Exception occurred: {}, keys: {}", exception.getMessage(), keys);
    }
}

// Use interceptor
@Lock(keys = "#orderId", interceptor = LoggingLockInterceptor.class)
public void processOrder(String orderId) {}
```

**Interceptor Hook Execution Order:**

```
beforeKeyBuild → afterKeyBuild → beforeLock → afterLock → onLockSuccess/onLockFailure
                                                              ↓
                                                         onException (on error)
```

### Event Listening

```java
@Component
public class LockEventListener {
    
    @EventListener
    public void onLockEvent(LockEvent event) {
        switch (event.getType()) {
            case BEFORE_LOCK -> log.info("Lock started: {}", event.getKeys());
            case AFTER_LOCK -> log.info("Lock acquired: {}", event.getKeys());
            case LOCK_FAILED -> log.warn("Lock failed: {}", event.getKeys());
        }
    }
}
```

### Derived Annotations

```java
// Redisson lock
@RedissonLock(keys = "#id", lockType = LockType.FAIR)
public void process(String id) {}

// RedisTemplate lock
@RedisTemplateLock(keys = "#id")
public void process(String id) {}

// Zookeeper lock
@ZookeeperLock(keys = "#id", lockType = LockType.READ)
public void read(String id) {}

// Etcd lock
@EtcdLock(keys = "#id")
public void process(String id) {}

// Local lock
@LocalLock(keys = "#id")
public void process(String id) {}
```

## Configuration

```yaml
lock4j:
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
    # Cluster config
    cluster:
      node-addresses:
        - redis://127.0.0.1:7000
        - redis://127.0.0.1:7001
    # Sentinel config
    sentinel:
      master-name: mymaster
      sentinel-addresses:
        - redis://127.0.0.1:26379

  # RedisTemplate config
  redis:
    enabled: true
    host: localhost
    port: 6379
    password:
    database: 0

  # Zookeeper config
  zookeeper:
    enabled: false
    connect-string: localhost:2181
    session-timeout: 30000
    connection-timeout: 10000

  # Etcd config
  etcd:
    enabled: false
    endpoints:
      - http://localhost:2379

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

Apache License 2.0