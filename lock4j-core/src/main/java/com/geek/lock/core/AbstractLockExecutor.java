package com.geek.lock.core;

import com.geek.lock.annotation.Lock;
import com.geek.lock.model.LockOptions;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.nonNull;

/**
 * 锁执行器抽象类
 * 
 * <p>提供了锁执行的核心流程模板，定义了加锁、执行业务、释放锁的完整流程。
 * 子类只需实现具体的解析逻辑即可。</p>
 * 
 * <p>执行流程：
 * <pre>
 * 1. 解析锁 Key
 * 2. 获取 LockProvider
 * 3. 依次对每个 Key 尝试加锁
 *    - 加锁成功：记录已获取的锁
 *    - 加锁失败：释放已获取的锁，执行失败处理
 * 4. 执行业务方法
 * 5. 释放所有已获取的锁
 * </pre>
 */
@Slf4j
public abstract class AbstractLockExecutor implements LockExecutor {

    /**
     * 在锁保护下执行方法
     * 
     * <p>这是核心模板方法，定义了完整的锁执行流程。
     * 使用 try-finally 确保锁一定会被释放。</p>
     *
     * @param joinPoint  AOP 切点，包含被拦截方法的信息
     * @param annotation @Lock 注解配置
     * @return 方法执行结果
     * @throws Throwable 方法执行过程中可能抛出的异常
     */
    @Override
    public Object execute(ProceedingJoinPoint joinPoint, Lock annotation) throws Throwable {
        // 步骤1：解析锁 Key
        // 根据注解配置解析出需要加锁的 Key 数组
        String[] lockKeys = resolveLockKeys(joinPoint, annotation);
        
        // 如果没有锁 Key，直接执行业务方法
        if (lockKeys.length == 0) {
            return joinPoint.proceed();
        }

        // 步骤2：获取 LockProvider
        // 根据注解配置获取对应的锁提供者
        LockProvider provider = resolveProvider(annotation);
        
        // 用于记录已成功获取的锁，便于后续释放
        List<LockKey> acquiredKeys = new ArrayList<>();

        try {
            // 步骤3：依次对每个 Key 尝试加锁
            // 采用全部加锁策略，即必须获取所有锁才算成功
            for (String lockKey : lockKeys) {
                // 构建 LockKey 对象，包含锁的所有属性
                LockKey key = buildLockKey(lockKey, annotation);
                // 构建锁选项，包含等待时间、租期等配置
                LockOptions options = buildLockOptions(annotation);

                // 尝试获取锁
                boolean acquired = doTryLock(provider, key, options);
                
                // 加锁失败处理
                if (!acquired) {
                    // 释放已获取的锁
                    releaseLocks(provider, acquiredKeys);
                    // 执行失败回调
                    return handleLockFailure(joinPoint, annotation, lockKeys);
                }
                
                // 记录成功获取的锁
                acquiredKeys.add(key);
            }

            // 步骤4：所有锁获取成功，执行业务方法
            return doExecute(joinPoint, annotation);
        } finally {
            // 步骤5：释放所有已获取的锁
            // 无论执行成功还是失败，都要释放锁
            releaseLocks(provider, acquiredKeys);
        }
    }

    /**
     * 解析锁 Key
     * 
     * <p>根据注解配置解析出需要加锁的 Key 数组。
     * 可能通过 SpEL 表达式解析，也可能通过自定义 KeyBuilder 解析。</p>
     *
     * @param joinPoint  AOP 切点
     * @param annotation @Lock 注解配置
     * @return 锁 Key 数组
     * @throws Exception 解析过程中可能抛出的异常
     */
    protected abstract String[] resolveLockKeys(ProceedingJoinPoint joinPoint, Lock annotation) throws Exception;

    /**
     * 解析 LockProvider
     * 
     * <p>根据注解配置获取对应的锁提供者。
     * 可以指定特定的 Provider，也可以使用默认的 Provider。</p>
     *
     * @param annotation @Lock 注解配置
     * @return LockProvider 实例
     * @throws Exception 获取过程中可能抛出的异常
     */
    protected abstract LockProvider resolveProvider(Lock annotation) throws Exception;

    /**
     * 处理加锁失败
     * 
     * <p>当加锁失败时执行的处理逻辑。
     * 可能抛出异常，也可能执行自定义的失败处理器。</p>
     *
     * @param joinPoint AOP 切点
     * @param annotation @Lock 注解配置
     * @param lockKeys   需要加锁的 Key 数组
     * @return 失败处理结果
     * @throws Exception 处理过程中可能抛出的异常
     */
    protected abstract Object handleLockFailure(ProceedingJoinPoint joinPoint, Lock annotation, String[] lockKeys) throws Exception;

    /**
     * 构建 LockKey 对象
     * 
     * <p>将字符串形式的 key 和注解配置封装为 LockKey 对象。</p>
     *
     * @param key        锁 Key 字符串
     * @param annotation @Lock 注解配置
     * @return LockKey 对象
     */
    protected LockKey buildLockKey(String key, Lock annotation) {
        return LockKey.builder()
                .key(key)
                .lockType(annotation.lockType())
                .waitTime(annotation.waitTime())
                .leaseTime(annotation.leaseTime())
                .timeUnit(annotation.timeUnit())
                .build();
    }

    /**
     * 构建 LockOptions 对象
     * 
     * <p>从注解配置中提取锁选项，如等待时间、租期等。</p>
     *
     * @param annotation @Lock 注解配置
     * @return LockOptions 对象
     */
    protected LockOptions buildLockOptions(Lock annotation) {
        LockOptions options = new LockOptions();
        options.setWaitTime(annotation.waitTime());
        options.setLeaseTime(annotation.leaseTime());
        options.setTimeUnit(annotation.timeUnit());
        return options;
    }

    /**
     * 尝试获取锁
     * 
     * <p>调用 LockProvider 进行加锁操作。
     * 子类可覆盖此方法添加额外的处理逻辑。</p>
     *
     * @param provider LockProvider 实例
     * @param key      LockKey 对象
     * @param options  锁选项
     * @return 是否成功获取锁
     */
    protected boolean doTryLock(LockProvider provider, LockKey key, LockOptions options) {
        return provider.tryLock(key, options);
    }

    /**
     * 执行业务方法
     * 
     * <p>在锁保护下执行被拦截的方法。
     * 子类可覆盖此方法添加额外的处理逻辑。</p>
     *
     * @param joinPoint AOP 切点
     * @param annotation @Lock 注解配置
     * @return 方法执行结果
     * @throws Throwable 方法执行过程中可能抛出的异常
     */
    protected Object doExecute(ProceedingJoinPoint joinPoint, Lock annotation) throws Throwable {
        return joinPoint.proceed();
    }

    /**
     * 释放所有已获取的锁
     * 
     * <p>遍历已获取的锁列表，依次释放。
     * 每个锁的释放操作都在 try-catch 中执行，避免单个锁释放失败影响其他锁的释放。</p>
     *
     * @param provider LockProvider 实例
     * @param keys     已获取的锁列表
     */
    protected void releaseLocks(LockProvider provider, List<LockKey> keys) {
        for (LockKey key : keys) {
            try {
                provider.unlock(key);
            } catch (Exception e) {
                log.warn("Failed to unlock: {}", key.getKey(), e);
            }
        }
    }
}