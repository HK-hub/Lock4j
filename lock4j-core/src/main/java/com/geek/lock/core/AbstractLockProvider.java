package com.geek.lock.core;

import com.geek.lock.event.DefaultLockEventPublisher;
import com.geek.lock.event.LockEvent;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.exception.LockException;
import com.geek.lock.model.LockOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.nonNull;

/**
 * 锁提供者抽象类
 * 
 * <p>提供了锁获取和释放的核心流程模板，定义了加锁前事件发布、
 * 加锁、加锁后事件发布、解锁等完整流程。</p>
 * 
 * <p>加锁流程：
 * <pre>
 * 1. 验证锁 Key 是否有效
 * 2. 发布加锁开始事件
 * 3. 执行加锁（子类实现）
 * 4. 根据加锁结果发布相应事件
 *    - 成功：发布加锁成功事件，启动看门狗（可选）
 *    - 失败：发布加锁失败事件
 * 5. 异常时发布错误事件
 * </pre>
 * 
 * <p>解锁流程：
 * <pre>
 * 1. 发布解锁开始事件
 * 2. 执行解锁（子类实现）
 * 3. 发布解锁完成事件
 * </pre>
 */
@Slf4j
public abstract class AbstractLockProvider implements LockProvider {

    /**
     * 锁事件发布器，用于发布锁相关事件
     */
    protected final LockEventPublisher eventPublisher;

    /**
     * 构造函数
     *
     * @param eventPublisher 锁事件发布器，如果为 null 则使用默认实现
     */
    protected AbstractLockProvider(LockEventPublisher eventPublisher) {
        this.eventPublisher = nonNull(eventPublisher) ? eventPublisher : new DefaultLockEventPublisher();
    }

    /**
     * 尝试获取锁
     * 
     * <p>这是 final 方法，定义了加锁的完整流程模板。
     * 子类通过实现 doTryLock 方法提供具体的加锁逻辑。</p>
     * 
     * <p>执行流程：
     * <ol>
     *     <li>验证锁 Key 是否有效</li>
     *     <li>发布加锁开始事件</li>
     *     <li>调用子类的 doTryLock 方法执行实际加锁</li>
     *     <li>根据结果发布相应事件并启动看门狗（如果需要）</li>
     *     <li>异常时发布错误事件</li>
     * </ol>
     *
     * @param lockKey 锁 Key 对象
     * @param options 锁选项
     * @return 是否成功获取锁
     */
    @Override
    public final boolean tryLock(LockKey lockKey, LockOptions options) {
        // 步骤1：验证锁 Key 是否有效
        validateLockKey(lockKey);

        // 步骤2：发布加锁开始事件
        if (nonNull(eventPublisher)) {
            eventPublisher.publish(LockEvent.beforeLock(lockKey));
        }

        try {
            // 步骤3：调用子类实现执行实际加锁
            boolean acquired = doTryLock(lockKey, options);

            // 步骤4：根据加锁结果发布相应事件
            if (acquired) {
                // 加锁成功，发布成功事件
                if (nonNull(eventPublisher)) {
                    eventPublisher.publish(LockEvent.afterLock(lockKey));
                }
                // 启动看门狗（子类可覆盖）
                startWatchdogIfNeeded(lockKey, options);
            } else {
                // 加锁失败，发布失败事件
                if (nonNull(eventPublisher)) {
                    eventPublisher.publish(LockEvent.lockFailed(lockKey));
                }
            }
            
            return acquired;
        } catch (Exception e) {
            // 步骤5：异常时发布错误事件
            if (nonNull(eventPublisher)) {
                eventPublisher.publish(LockEvent.lockError(lockKey, e));
            }
            throw new LockException("Lock failed: " + lockKey.getKey(), e);
        }
    }

    /**
     * 释放锁
     * 
     * <p>这是 final 方法，定义了解锁的完整流程模板。
     * 子类通过实现 doUnlock 方法提供具体的解锁逻辑。</p>
     * 
     * <p>执行流程：
     * <ol>
     *     <li>发布解锁开始事件</li>
     *     <li>调用子类的 doUnlock 方法执行实际解锁</li>
     *     <li>发布解锁完成事件</li>
     * </ol>
     *
     * @param lockKey 锁 Key 对象
     */
    @Override
    public final void unlock(LockKey lockKey) {
        // 步骤1：发布解锁开始事件
        if (nonNull(eventPublisher)) {
            eventPublisher.publish(LockEvent.beforeUnlock(lockKey));
        }

        try {
            // 步骤2：调用子类实现执行实际解锁
            doUnlock(lockKey);

            // 步骤3：发布解锁完成事件
            if (nonNull(eventPublisher)) {
                eventPublisher.publish(LockEvent.afterUnlock(lockKey));
            }
        } catch (Exception e) {
            log.warn("Unlock failed: {}", lockKey.getKey(), e);
        }
    }

    /**
     * 执行实际加锁操作
     * 
     * <p>子类必须实现此方法，提供具体的加锁逻辑。</p>
     *
     * @param lockKey 锁 Key 对象
     * @param options 锁选项
     * @return 是否成功获取锁
     * @throws Exception 加锁过程中可能抛出的异常
     */
    protected abstract boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception;

    /**
     * 执行实际解锁操作
     * 
     * <p>子类必须实现此方法，提供具体的解锁逻辑。</p>
     *
     * @param lockKey 锁 Key 对象
     */
    protected abstract void doUnlock(LockKey lockKey);

    /**
     * 验证锁 Key 是否有效
     * 
     * <p>检查锁 Key 是否为 null 或空字符串。</p>
     *
     * @param lockKey 锁 Key 对象
     * @throws IllegalArgumentException 如果锁 Key 无效
     */
    protected void validateLockKey(LockKey lockKey) {
        if (nonNull(lockKey) && StringUtils.isNotEmpty(lockKey.getKey())) {
            return;
        }
        throw new IllegalArgumentException("Lock key cannot be null or empty");
    }

    /**
     * 启动看门狗（如果需要）
     * 
     * <p>默认空实现，子类可覆盖此方法实现自动续期功能。
     * 当启用看门狗时，会自动延长锁的过期时间，防止业务未执行完锁就过期。</p>
     *
     * @param lockKey 锁 Key 对象
     * @param options 锁选项
     */
    protected void startWatchdogIfNeeded(LockKey lockKey, LockOptions options) {
        // default no-op, subclass can override
    }

    /**
     * 将时间转换为毫秒
     * 
     * <p>根据给定的时间单位，将时间值转换为毫秒。</p>
     *
     * @param time 时间值
     * @param unit 时间单位
     * @return 转换后的毫秒数
     */
    protected long convertToMillis(long time, TimeUnit unit) {
        return unit.toMillis(time);
    }
}