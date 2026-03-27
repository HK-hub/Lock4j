package com.geek.lock.core;

import com.geek.lock.annotation.Lock;
import com.geek.lock.model.LockOptions;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Lock拦截器接口
 *
 * <p>提供锁执行流程各阶段的钩子方法，允许用户在关键节点插入自定义逻辑。
 * 所有方法返回void，通过异常控制流程中断。</p>
 *
 * <p>执行流程钩子顺序：
 * <pre>
 * 1. beforeKeyBuild - Key构建前
 * 2. afterKeyBuild - Key构建后
 * 3. beforeLock - 加锁前
 * 4. afterLock - 每个Key加锁后（循环内）
 * 5. onLockSuccess/onLockFailure - 单个Key加锁结果
 * 6. onException - 执行过程中抛出异常
 * </pre>
 */
public interface LockInterceptor {

    /**
     * Key构建前回调
     *
     * @param method 被拦截的方法
     * @param args 方法参数
     * @param annotation @Lock注解配置
     */
    default void beforeKeyBuild(Method method, Object[] args, Lock annotation) {}

    /**
     * Key构建后回调
     *
     * @param keys 解析出的锁Key列表
     */
    default void afterKeyBuild(List<String> keys) {}

    /**
     * 加锁前回调
     *
     * @param keys 需要加锁的Key列表
     * @param options 锁选项配置
     */
    default void beforeLock(List<String> keys, LockOptions options) {}

    /**
     * 每个Key加锁后回调（无论成功或失败）
     *
     * @param keys 当前处理的Key列表（单个Key）
     */
    default void afterLock(List<String> keys) {}

    /**
     * 单个Key加锁成功回调
     *
     * @param keys 当前成功加锁的Key列表（单个Key）
     * @param lockKey 加锁信息对象
     */
    default void onLockSuccess(List<String> keys, LockKey lockKey) {}

    /**
     * 加锁失败回调
     *
     * <p>当某个Key获取锁失败时触发，会先释放已获取的锁。</p>
     *
     * @param keys 需要加锁的Key列表
     */
    default void onLockFailure(List<String> keys) {}

    /**
     * 执行过程中抛出异常回调
     *
     * <p>业务方法执行或锁操作过程中抛出异常时触发。</p>
     *
     * @param keys 需要加锁的Key列表
     * @param exception 抛出的异常
     */
    default void onException(List<String> keys, Throwable exception) {}
}