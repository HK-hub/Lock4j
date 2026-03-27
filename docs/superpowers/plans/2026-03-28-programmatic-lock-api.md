# 编程式锁API实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Lock4j 添加编程式加锁能力，提供 LockTemplate 类供用户手动控制加锁和解锁。

**Architecture:** 新增 LockTemplate、LockInfo、LockRequest 三个类，复用现有 LockProviderFactory 和 LockProvider 接口，在 Spring Boot Starter 中自动配置。

**Tech Stack:** Java 17, Spring Boot 3.x, Lombok

---

## 文件结构

| 文件 | 操作 | 职责 |
|-----|-----|-----|
| `lock4j-core/.../template/LockInfo.java` | 创建 | 锁信息封装，持有LockKey和Provider |
| `lock4j-core/.../template/LockRequest.java` | 创建 | 加锁请求参数，Builder模式 |
| `lock4j-core/.../template/LockTemplate.java` | 创建 | 编程式锁模板类，提供lock/releaseLock |
| `lock4j-spring-boot-starter/.../LockAutoConfiguration.java` | 修改 | 添加LockTemplate Bean配置 |
| `lock4j-core/.../template/LockTemplateTest.java` | 创建 | LockTemplate单元测试 |

---

### Task 1: 创建 LockInfo 类

**Files:**
- Create: `lock4j-core/src/main/java/com/geek/lock/template/LockInfo.java`

- [ ] **Step 1: 创建 LockInfo 类**

```java
package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import lombok.Getter;

import static java.util.Objects.nonNull;

@Getter
public class LockInfo {

    private final LockKey lockKey;
    private final LockProvider provider;
    private final boolean acquired;

    public LockInfo(LockKey lockKey, LockProvider provider, boolean acquired) {
        this.lockKey = lockKey;
        this.provider = provider;
        this.acquired = acquired;
    }

    public boolean isAcquired() {
        return acquired;
    }

    public boolean isReleasable() {
        return nonNull(lockKey) && nonNull(provider) && acquired;
    }
}
```

- [ ] **Step 2: 验证编译**

运行: `mvn compile -pl lock4j-core -q`
预期: 编译成功

- [ ] **Step 3: Commit**

```bash
git add lock4j-core/src/main/java/com/geek/lock/template/LockInfo.java
git commit -m "feat: add LockInfo class for programmatic lock"
```

---

### Task 2: 创建 LockRequest 类

**Files:**
- Create: `lock4j-core/src/main/java/com/geek/lock/template/LockRequest.java`

- [ ] **Step 1: 创建 LockRequest 类**

```java
package com.geek.lock.template;

import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.LockType;
import lombok.Builder;
import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
@Builder
public class LockRequest {

    private String key;
    
    @Builder.Default
    private long leaseTime = 30000;
    
    @Builder.Default
    private long waitTime = 3000;
    
    @Builder.Default
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    
    @Builder.Default
    private LockType lockType = LockType.REENTRANT;
    
    private Class<? extends LockProvider> provider;
}
```

- [ ] **Step 2: 验证编译**

运行: `mvn compile -pl lock4j-core -q`
预期: 编译成功

- [ ] **Step 3: Commit**

```bash
git add lock4j-core/src/main/java/com/geek/lock/template/LockRequest.java
git commit -m "feat: add LockRequest class for programmatic lock"
```

---

### Task 3: 创建 LockTemplate 类

**Files:**
- Create: `lock4j-core/src/main/java/com/geek/lock/template/LockTemplate.java`

- [ ] **Step 1: 创建 LockTemplate 类**

```java
package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import com.geek.lock.factory.LockProviderFactory;
import com.geek.lock.model.LockOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@RequiredArgsConstructor
public class LockTemplate {

    private final LockProviderFactory providerFactory;

    public LockInfo lock(String key, long leaseTime, long waitTime) {
        return lock(key, leaseTime, waitTime, null);
    }

    public LockInfo lock(String key, long leaseTime, long waitTime,
                         Class<? extends LockProvider> providerClass) {
        LockRequest request = LockRequest.builder()
                .key(key)
                .leaseTime(leaseTime)
                .waitTime(waitTime)
                .provider(providerClass)
                .build();
        return lock(request);
    }

    public LockInfo lock(LockRequest request) {
        if (isNull(request) || isNull(request.getKey())) {
            throw new IllegalArgumentException("Lock key cannot be null");
        }

        LockProvider provider = resolveProvider(request.getProvider());
        if (isNull(provider)) {
            throw new IllegalStateException("No LockProvider available");
        }

        LockKey lockKey = buildLockKey(request);
        LockOptions options = buildLockOptions(request);

        boolean acquired = provider.tryLock(lockKey, options);
        
        return new LockInfo(lockKey, provider, acquired);
    }

    public void releaseLock(LockInfo lockInfo) {
        if (isNull(lockInfo) || !lockInfo.isReleasable()) {
            return;
        }

        try {
            lockInfo.getProvider().unlock(lockInfo.getLockKey());
        } catch (Exception e) {
            log.warn("Failed to release lock: {}", lockInfo.getLockKey().getKey(), e);
        }
    }

    private LockProvider resolveProvider(Class<? extends LockProvider> providerClass) {
        if (nonNull(providerClass) && providerClass != LockProvider.class) {
            return providerFactory.getProvider(providerClass);
        }

        if (providerFactory.hasPrimaryProvider()) {
            return providerFactory.getPrimaryProvider();
        }

        return providerFactory.getFirstProvider();
    }

    private LockKey buildLockKey(LockRequest request) {
        return LockKey.builder()
                .key(request.getKey())
                .lockType(request.getLockType())
                .waitTime(request.getWaitTime())
                .leaseTime(request.getLeaseTime())
                .timeUnit(request.getTimeUnit())
                .build();
    }

    private LockOptions buildLockOptions(LockRequest request) {
        LockOptions options = new LockOptions();
        options.setWaitTime(request.getWaitTime());
        options.setLeaseTime(request.getLeaseTime());
        options.setTimeUnit(request.getTimeUnit());
        return options;
    }
}
```

- [ ] **Step 2: 验证编译**

运行: `mvn compile -pl lock4j-core -q`
预期: 编译成功

- [ ] **Step 3: Commit**

```bash
git add lock4j-core/src/main/java/com/geek/lock/template/LockTemplate.java
git commit -m "feat: add LockTemplate class for programmatic lock"
```

---

### Task 4: 更新 LockAutoConfiguration

**Files:**
- Modify: `lock4j-spring-boot-starter/src/main/java/com/geek/lock/autoconfigure/LockAutoConfiguration.java`

- [ ] **Step 1: 添加 LockTemplate Bean**

在第9行后添加import:
```java
import com.geek.lock.template.LockTemplate;
```

在第65行后添加Bean定义:
```java
    @Bean
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate(LockProviderFactory providerFactory) {
        return new LockTemplate(providerFactory);
    }
```

- [ ] **Step 2: 验证编译**

运行: `mvn compile -pl lock4j-spring-boot-starter -q`
预期: 编译成功

- [ ] **Step 3: Commit**

```bash
git add lock4j-spring-boot-starter/src/main/java/com/geek/lock/autoconfigure/LockAutoConfiguration.java
git commit -m "feat: add LockTemplate bean configuration"
```

---

### Task 5: 创建 LockTemplate 单元测试

**Files:**
- Create: `lock4j-core/src/test/java/com/geek/lock/template/LockTemplateTest.java`

- [ ] **Step 1: 创建测试类**

```java
package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.LockType;
import com.geek.lock.factory.LockProviderFactory;
import com.geek.lock.model.LockOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockTemplateTest {

    @Mock
    private LockProviderFactory providerFactory;

    @Mock
    private LockProvider lockProvider;

    private LockTemplate lockTemplate;

    @BeforeEach
    void setUp() {
        when(providerFactory.getFirstProvider()).thenReturn(lockProvider);
        when(providerFactory.hasPrimaryProvider()).thenReturn(false);
        lockTemplate = new LockTemplate(providerFactory);
    }

    @Nested
    @DisplayName("lock method tests")
    class LockMethodTests {

        @Test
        @DisplayName("should return LockInfo when lock acquired successfully")
        void shouldReturnLockInfoWhenLockAcquired() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            assertEquals("test-key", lockInfo.getLockKey().getKey());
            verify(lockProvider).tryLock(any(LockKey.class), any(LockOptions.class));
        }

        @Test
        @DisplayName("should return LockInfo with acquired=false when lock failed")
        void shouldReturnLockInfoWhenLockFailed() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(false);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            assertNotNull(lockInfo);
            assertFalse(lockInfo.isAcquired());
        }

        @Test
        @DisplayName("should throw exception when key is null")
        void shouldThrowExceptionWhenKeyIsNull() {
            assertThrows(IllegalArgumentException.class, () ->
                    lockTemplate.lock(null, 30000, 5000));
        }

        @Test
        @DisplayName("should use specified provider")
        void shouldUseSpecifiedProvider() {
            when(providerFactory.getProvider(TestLockProvider.class))
                    .thenReturn(lockProvider);
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000, TestLockProvider.class);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            verify(providerFactory).getProvider(TestLockProvider.class);
            verify(lockProvider).tryLock(any(LockKey.class), any(LockOptions.class));
        }

        @Test
        @DisplayName("should use LockRequest builder")
        void shouldUseLockRequestBuilder() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockRequest request = LockRequest.builder()
                    .key("test-key")
                    .leaseTime(30000)
                    .waitTime(5000)
                    .lockType(LockType.FAIR)
                    .build();

            LockInfo lockInfo = lockTemplate.lock(request);

            assertNotNull(lockInfo);
            assertTrue(lockInfo.isAcquired());
            assertEquals(LockType.FAIR, lockInfo.getLockKey().getLockType());
        }
    }

    @Nested
    @DisplayName("releaseLock method tests")
    class ReleaseLockMethodTests {

        @Test
        @DisplayName("should release lock successfully")
        void shouldReleaseLockSuccessfully() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(true);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            lockTemplate.releaseLock(lockInfo);

            verify(lockProvider).unlock(any(LockKey.class));
        }

        @Test
        @DisplayName("should not throw exception when lockInfo is null")
        void shouldNotThrowWhenLockInfoIsNull() {
            lockTemplate.releaseLock(null);
            verify(lockProvider, never()).unlock(any());
        }

        @Test
        @DisplayName("should not unlock when lock not acquired")
        void shouldNotUnlockWhenLockNotAcquired() {
            when(lockProvider.tryLock(any(LockKey.class), any(LockOptions.class)))
                    .thenReturn(false);

            LockInfo lockInfo = lockTemplate.lock("test-key", 30000, 5000);

            lockTemplate.releaseLock(lockInfo);

            verify(lockProvider, never()).unlock(any());
        }
    }

    private static class TestLockProvider implements LockProvider {
        @Override
        public boolean tryLock(LockKey lockKey, LockOptions options) {
            return false;
        }

        @Override
        public void unlock(LockKey lockKey) {
        }

        @Override
        public boolean supports(LockType lockType) {
            return true;
        }
    }
}
```

- [ ] **Step 2: 运行测试**

运行: `mvn test -pl lock4j-core -Dtest=LockTemplateTest -q`
预期: 所有测试通过

- [ ] **Step 3: Commit**

```bash
git add lock4j-core/src/test/java/com/geek/lock/template/LockTemplateTest.java
git commit -m "test: add LockTemplate unit tests"
```

---

### Task 6: 验证整体编译和测试

- [ ] **Step 1: 编译整个项目**

运行: `mvn compile -q`
预期: 编译成功

- [ ] **Step 2: 运行所有测试**

运行: `mvn test -pl lock4j-core -q`
预期: 所有测试通过

- [ ] **Step 3: 最终Commit（如有遗漏）**

```bash
git status
git add -A
git commit -m "feat: complete programmatic lock API implementation"
```

---

## 自检清单

- [x] Spec覆盖: 设计文档中的所有组件都有对应任务
- [x] 无占位符: 所有代码块完整，无TBD/TODO
- [x] 类型一致: LockKey/LockOptions/LockProvider引用与现有类一致