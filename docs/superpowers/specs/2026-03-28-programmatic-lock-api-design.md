# 编程式锁API设计文档

## 概述

为 Lock4j 添加编程式加锁能力，允许用户手动控制加锁和解锁时机，而非仅通过注解方式。

## 目标

- 提供简洁的手动加锁/解锁API
- 兼容现有架构，最小化改动
- 支持多种 LockProvider（Redisson/Redis/Zookeeper等）
- 用户可选择显式指定Provider或使用默认Provider

## 核心组件

### 1. LockInfo

锁信息封装，持有锁的所有必要信息用于后续释放。

```java
public class LockInfo {
    private LockKey lockKey;
    private LockProvider provider;
    private boolean acquired;
    
    public boolean isAcquired() { return acquired; }
}
```

### 2. LockRequest

加锁请求参数封装（可选，用于复杂配置场景）。

```java
public class LockRequest {
    private String key;
    private long leaseTime = 30000;
    private long waitTime = 3000;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private LockType lockType = LockType.REENTRANT;
    private Class<? extends LockProvider> provider;
    
    // Builder模式
}
```

### 3. LockTemplate

编程式锁操作模板类。

```java
public class LockTemplate {
    private final LockProviderFactory providerFactory;
    
    // 简洁API
    public LockInfo lock(String key, long leaseTime, long waitTime);
    public LockInfo lock(String key, long leaseTime, long waitTime, 
                         Class<? extends LockProvider> providerClass);
    
    // Builder方式
    public LockInfo lock(LockRequest request);
    
    // 释放锁
    public void releaseLock(LockInfo lockInfo);
}
```

## 使用示例

```java
@Service
public class ProgrammaticService {
    @Autowired
    private LockTemplate lockTemplate;
    
    public void doSomething(String userId) {
        // 查询操作，不上锁
        User user = userDao.findById(userId);
        
        // 获取锁
        LockInfo lockInfo = lockTemplate.lock(userId, 30000, 5000);
        if (lockInfo == null || !lockInfo.isAcquired()) {
            throw new RuntimeException("业务处理中,请稍后再试");
        }
        
        try {
            // 执行业务逻辑
            processBusiness(user);
        } finally {
            // 释放锁
            lockTemplate.releaseLock(lockInfo);
        }
    }
    
    // 使用Builder方式
    public void doSomethingComplex(String userId) {
        LockInfo lockInfo = lockTemplate.lock(LockRequest.builder()
            .key(userId)
            .leaseTime(30000)
            .waitTime(5000)
            .lockType(LockType.FAIR)
            .provider(RedissonLockProvider.class)
            .build());
        
        if (lockInfo == null || !lockInfo.isAcquired()) {
            return;
        }
        
        try {
            processBusiness();
        } finally {
            lockTemplate.releaseLock(lockInfo);
        }
    }
}
```

## 文件结构

新增文件位于 `lock4j-core` 模块：

```
lock4j-core/src/main/java/com/geek/lock/
├── template/
│   ├── LockTemplate.java          # 编程式锁模板类
│   ├── LockInfo.java              # 锁信息封装
│   └── LockRequest.java           # 加锁请求参数封装
└── spring-boot-starter模块更新自动配置
```

## 自动配置

在 `lock4j-spring-boot-starter` 的 `LockAutoConfiguration` 中自动注入 `LockTemplate`：

```java
@Bean
@ConditionalOnBean(LockProviderFactory.class)
public LockTemplate lockTemplate(LockProviderFactory providerFactory) {
    return new LockTemplate(providerFactory);
}
```

## 实现要点

1. **复用现有组件**：直接使用 `LockProviderFactory`、`LockProvider`、`LockKey`、`LockOptions`
2. **Provider选择逻辑**：
   - 用户显式指定Provider类型 → 使用指定Provider
   - 用户未指定 → 使用Primary Provider（优先）或First Provider
3. **锁释放**：调用 `LockProvider.unlock(LockKey)`
4. **null返回**：加锁失败返回null，而非抛异常（与注解方式区分）

## 不涉及的内容

- 不修改现有注解方式的任何逻辑
- 不引入新的LockProvider
- 不添加自动续期（看门狗）的编程式控制（当前由Provider内部处理）

## 验证方案

- 单元测试：测试 `LockTemplate` 的加锁/解锁逻辑
- 集成测试：在示例项目中添加编程式锁使用示例