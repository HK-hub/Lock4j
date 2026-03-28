# Lock4j 架构设计文档

## 1. 概述

Lock4j 是一个基于 Spring Boot 的分布式锁框架，提供了统一的锁抽象和多种实现。

## 2. 架构设计

### 2.1 模块结构

```
lock4j/
├── lock4j-core                    # 核心模块 - 接口定义、抽象实现
├── lock4j-redisson                # Redisson 实现
├── lock4j-redis-template          # RedisTemplate 实现
├── lock4j-zookeeper               # Zookeeper 实现
├── lock4j-etcd                    # Etcd 实现
├── lock4j-local                   # 本地锁实现
└── lock4j-spring-boot-starter     # Spring Boot 自动配置
```

### 2.2 核心接口

#### LockProvider
```java
public interface LockProvider extends Lock {
    boolean tryLock(LockKey lockKey, LockOptions options);
    void unlock(LockKey lockKey);
    boolean supports(LockType lockType);
}
```

#### LockKey
```java
public interface LockKey {
    String getKey();
    LockType getLockType();
    long getWaitTime();
    long getLeaseTime();
    TimeUnit getTimeUnit();
}
```

### 2.3 设计模式

1. **模板方法模式** - `AbstractLockProvider` 定义锁操作流程
2. **策略模式** - 不同 `LockProvider` 实现不同锁策略
3. **建造者模式** - `LockKey.builder()` 和 `LockTemplate` API

## 3. 锁类型

| 类型 | 说明 | Redisson | RedisTemplate | Zookeeper | Etcd | Local |
|------|------|----------|---------------|-----------|------|-------|
| REENTRANT | 可重入锁 | ✅ | ✅ | ✅ | ✅ | ✅ |
| FAIR | 公平锁 | ✅ | ✅ | ❌ | ✅ | ✅ |
| READ | 读锁 | ✅ | ✅ | ✅ | ❌ | ✅ |
| WRITE | 写锁 | ✅ | ✅ | ✅ | ❌ | ✅ |

## 4. 核心流程

### 4.1 加锁流程

```
1. 验证 LockKey
2. 发布 beforeLock 事件
3. 执行 doTryLock (子类实现)
4. 成功: 发布 afterLock 事件, 启动 Watchdog
5. 失败: 发布 lockFailed 事件
```

### 4.2 解锁流程

```
1. 发布 beforeUnlock 事件
2. 执行 doUnlock (子类实现)
3. 停止 Watchdog
4. 发布 afterUnlock 事件
```

## 5. 扩展点

### 5.1 自定义 LockProvider

```java
public class MyLockProvider extends AbstractLockProvider {
    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) {
        // 实现加锁逻辑
    }
    
    @Override
    protected void doUnlock(LockKey lockKey) {
        // 实现解锁逻辑
    }
    
    @Override
    public boolean supports(LockType lockType) {
        // 返回支持的锁类型
    }
}
```

### 5.2 自定义 KeyBuilder

```java
public class MyKeyBuilder implements KeyBuilder {
    @Override
    public String[] build(Method method, String[] paramNames, Object[] args, Lock annotation) {
        // 自定义 key 生成逻辑
    }
}
```

### 5.3 自定义 FailureHandler

```java
public class MyFailureHandler implements FailureHandler {
    @Override
    public Object handle(ProceedingJoinPoint joinPoint, Lock annotation, String[] keys) {
        // 自定义失败处理逻辑
    }
}
```

## 6. Watchdog 机制

### 6.1 工作原理

1. 加锁成功后启动定时任务
2. 定期（默认 leaseTime/3）续期
3. 解锁时停止续期

### 6.2 实现对比

| 实现 | 方式 |
|------|------|
| Redisson | 内置 Watchdog |
| RedisTemplate | 共享线程池续期 |
| Zookeeper | 检查锁状态 |
| Etcd | Lease Keep-Alive |

## 7. 事件系统

```java
public enum LockEventType {
    BEFORE_LOCK,    // 加锁前
    AFTER_LOCK,     // 加锁后
    LOCK_FAILED,    // 加锁失败
    LOCK_ERROR,     // 加锁异常
    BEFORE_UNLOCK,  // 解锁前
    AFTER_UNLOCK    // 解锁后
}
```

## 8. 配置属性

```yaml
lock4j:
  enabled: true
  primary-provider: RedissonLockProvider
  default-wait-time: 3000
  default-lease-time: 30000
  redisson:
    address: redis://localhost:6379
  zookeeper:
    connect-string: localhost:2181
  etcd:
    endpoints: http://localhost:2379
```

## 9. 性能考量

### 9.1 RedisTemplate 锁优化

- 共享线程池处理 Watchdog
- Lua 脚本保证原子性
- 支持公平锁和读写锁

### 9.2 连接复用

- Redisson: 复用 RedissonClient
- RedisTemplate: 复用 StringRedisTemplate
- Zookeeper: 复用 CuratorFramework
- Etcd: 复用 Client

## 10. 最佳实践

1. **合理设置超时**: waitTime 和 leaseTime 根据业务场景设置
2. **启用 Watchdog**: 长时间任务防止锁过期
3. **选择合适实现**: 高并发选 Redisson，强一致选 Zookeeper
4. **监控锁指标**: 使用 LockMetricsCollector 收集统计