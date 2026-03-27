# Lock4j

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

基于 Spring Boot 的分布式锁框架，通过 `@Lock` 注解实现本地锁和分布式锁的一键加锁。

## 特性

- 🚀 **简单易用**：只需一个 `@Lock` 注解即可实现加锁
- 🔧 **多种 Lock Provider**：支持 Redisson、Zookeeper、Etcd、本地锁
- 📝 **派生注解**：`@RedissonLock`、`@ZookeeperLock`、`@EtcdLock`、`@LocalLock`
- 🔑 **灵活的 Key 构建**：支持 SpEL 表达式和自定义 KeyBuilder
- 🔒 **多种锁类型**：可重入锁、公平锁、读写锁
- ⏱️ **自动续期**：Watchdog 自动续期机制
- 🎯 **失败处理**：支持自定义加锁失败处理器
- 🔄 **多 Key 加锁**：支持同时对多个 Key 加锁
- 🏗️ **Spring Boot 集成**：自动配置，开箱即用

## 模块说明

```
lock4j
├── lock4j-core                    # 核心模块，定义接口和抽象类
├── lock4j-redisson                # Redisson 实现
├── lock4j-zookeeper               # Zookeeper 实现 (Curator 5.x)
├── lock4j-etcd                    # Etcd 实现 (jetcd)
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
  default-provider: redisson
  
  redisson:
    enabled: true
```

### 3. 使用注解

```java
@Service
public class OrderService {

    // 基本使用 - SpEL 表达式
    @Lock(keys = "#orderId")
    public void processOrder(String orderId) {
        // 业务逻辑
    }

    // 指定锁参数
    @Lock(keys = "#userId", waitTime = 5000, leaseTime = 60000)
    public void deductBalance(String userId) {
        // 业务逻辑
    }

    // 使用派生注解
    @RedissonLock(keys = "#orderId", lockType = LockType.FAIR)
    public void processOrderWithFairLock(String orderId) {
        // 业务逻辑
    }

    // 多 Key 加锁
    @Lock(keys = {"#productId", "#warehouseId"})
    public void deductStock(String productId, String warehouseId) {
        // 业务逻辑
    }

    // 使用对象属性
    @Lock(keys = "#order.id")
    public void processOrder(Order order) {
        // 业务逻辑
    }
}
```

## 注解说明

### @Lock 核心属性

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| keys | 锁的 Key 数组，支持 SpEL 表达式 | String[] | {} |
| keyBuilder | 自定义 KeyBuilder 类型 | Class<? extends KeyBuilder> | KeyBuilder.class |
| keyAbsentPolicy | Key 缺失策略 | KeyAbsentPolicy | USE_METHOD_PATH |
| prefix | 锁前缀 | String | "" |
| waitTime | 等待获取锁的时间（毫秒） | long | 3000 |
| leaseTime | 锁过期时间（毫秒） | long | 30000 |
| timeUnit | 时间单位 | TimeUnit | MILLISECONDS |
| lockType | 锁类型 | LockType | REENTRANT |
| failureHandler | 加锁失败处理器 | Class<? extends FailureHandler> | FailureHandler.Default.class |
| failFast | 加锁失败时抛出的异常类型 | Class<? extends RuntimeException> | LockFailureException.class |
| provider | 指定 LockProvider 类型 | Class<? extends LockProvider> | LockProvider.class |

### 派生注解

派生注解自动指定对应的 LockProvider：

- `@RedissonLock` → RedissonLockProvider
- `@ZookeeperLock` → ZookeeperLockProvider  
- `@EtcdLock` → EtcdLockProvider
- `@LocalLock` → LocalLockProvider

### Key 缺失策略 (keyAbsentPolicy)

当 `keys` 和 `keyBuilder` 都为空时的处理策略：

| 策略 | 说明 |
|------|------|
| USE_METHOD_PATH | 使用方法全限定名作为 Key（默认） |
| THROW_EXCEPTION | 抛出 IllegalArgumentException |

```java
// 使用方法全限定名作为 Key
@Lock(keyAbsentPolicy = KeyAbsentPolicy.USE_METHOD_PATH)
public void process() { }

// 抛出异常
@Lock(keyAbsentPolicy = KeyAbsentPolicy.THROW_EXCEPTION)
public void strictProcess() { }
```

### 锁类型 (LockType)

| 类型 | 说明 | 支持的 Provider |
|------|------|-----------------|
| REENTRANT | 可重入锁（默认） | 全部 |
| FAIR | 公平锁 | Redisson, Zookeeper, Local |
| READ | 读锁 | Redisson, Zookeeper, Local |
| WRITE | 写锁 | Redisson, Zookeeper, Local |

## 自定义扩展

### 自定义 FailureHandler

```java
@Component
public class CustomLockFailureHandler implements FailureHandler {
    @Override
    public Object handle(LockFailureContext context) {
        String[] keys = context.getLockKeys();
        log.warn("Lock failed for keys: {}", Arrays.toString(keys));
        return null; // 返回默认值
    }
}

// 使用
@Lock(keys = "#orderId", failureHandler = CustomLockFailureHandler.class)
public Order processOrder(String orderId) { ... }
```

### 自定义 KeyBuilder

```java
@Component
public class OrderKeyBuilder implements KeyBuilder {
    @Override
    public String[] build(Method method, String[] parameterNames, Object[] args, Lock annotation) {
        Order order = (Order) args[0];
        return new String[]{"order:" + order.getId()};
    }
}

// 使用
@Lock(keyBuilder = OrderKeyBuilder.class)
public void processOrder(Order order) { ... }
```

### 自定义 LockProvider

```java
@Component
public class MyCustomLockProvider extends AbstractLockProvider {

    public MyCustomLockProvider(LockEventPublisher eventPublisher) {
        super(eventPublisher);
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        // 实现加锁逻辑
        return true;
    }

    @Override
    protected void doUnlock(LockKey lockKey) {
        // 实现解锁逻辑
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT;
    }

    @Override
    public String getName() {
        return "myCustom";
    }
}
```

## 配置说明

```yaml
lock4j:
  enabled: true
  default-provider: redisson
  default-wait-time: 3000
  default-lease-time: 30000

  redisson:
    enabled: true
    
  zookeeper:
    enabled: false
    connect-string: localhost:2181
    session-timeout: 30000
    connection-timeout: 10000
    
  etcd:
    enabled: false
    endpoints:
      - http://localhost:2379
    
  local:
    enabled: true
```

## 注意事项

1. **锁 Key 设计**：建议使用业务相关的唯一标识作为 Key，避免 Key 冲突
2. **锁超时时间**：`leaseTime` 应该大于业务执行时间，避免锁过期导致并发问题
3. **等待时间**：`waitTime` 设置过长会影响系统响应速度，设置过短可能导致频繁获取锁失败
4. **多 Key 加锁**：多 Key 加锁时采用全部加锁策略，必须获取所有锁才算成功
5. **异常处理**：业务方法抛出异常时，锁会自动释放
6. **锁续期**：启用 Watchdog 时，会自动续期，防止业务未执行完锁就过期

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      @Lock Annotation                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        LockAspect                           │
│  (AOP 切面，拦截 @Lock 注解方法)                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    LockExecutor                             │
│  (锁执行器，负责加锁、执行、解锁)                              │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ SpEL Parser │  │ KeyBuilder  │  │FailureHandler│         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   LockProviderFactory                       │
│  (Provider 工厂，获取 LockProvider)                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    LockProvider                             │
│  (锁提供者接口，继承 java.util.concurrent.locks.Lock)        │
│                                                             │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐   │
│  │ Redisson  │ │ Zookeeper │ │   Etcd    │ │   Local   │   │
│  │  Provider │ │  Provider │ │  Provider │ │  Provider │   │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 技术栈

- JDK 17+
- Spring Boot 3.x
- Spring AOP
- Redisson 3.x (Redis)
- Curator 5.x (Zookeeper)
- jetcd 0.7.x (Etcd)
- Lombok

## License

Apache License 2.0