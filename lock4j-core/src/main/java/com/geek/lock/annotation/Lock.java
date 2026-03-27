package com.geek.lock.annotation;

import com.geek.lock.core.FailureHandler;
import com.geek.lock.core.KeyBuilder;
import com.geek.lock.enums.KeyAbsentPolicy;
import com.geek.lock.enums.LockType;
import com.geek.lock.exception.LockFailureException;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Lock 注解
 * 
 * <p>用于标记需要加锁的方法。支持多种配置选项，
 * 包括 Key 构建、锁类型、等待时间、失败处理等。</p>
 * 
 * <p>使用示例：
 * <pre>
 * // 使用 SpEL 表达式
 * {@literal @}Lock(keys = "#orderId")
 * public void processOrder(String orderId) { }
 * 
 * // 使用自定义 KeyBuilder
 * {@literal @}Lock(keyBuilder = OrderKeyBuilder.class)
 * public void processOrder(Order order) { }
 * 
 * // 多个 Key
 * {@literal @}Lock(keys = {"#productId", "#warehouseId"})
 * public void deductStock(String productId, String warehouseId) { }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {

    /**
     * 锁的 Key 数组，支持 SpEL 表达式
     */
    String[] keys() default {};

    /**
     * 自定义 KeyBuilder 类型
     */
    Class<? extends KeyBuilder> keyBuilder() default KeyBuilder.class;

    /**
     * Key 缺失策略
     * 
     * <p>当 keys 和 keyBuilder 都为空时的处理策略：
     * <ul>
     *     <li>USE_METHOD_PATH: 使用方法全限定名作为 Key</li>
     *     <li>THROW_EXCEPTION: 抛出异常</li>
     * </ul>
     */
    KeyAbsentPolicy keyAbsentPolicy() default KeyAbsentPolicy.USE_METHOD_PATH;

    /**
     * 锁前缀
     */
    String prefix() default "";

    /**
     * 等待获取锁的时间（毫秒）
     */
    long waitTime() default 3000;

    /**
     * 锁过期时间（毫秒）
     */
    long leaseTime() default 30000;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 锁类型
     */
    LockType lockType() default LockType.REENTRANT;

    /**
     * 加锁失败处理器
     */
    Class<? extends FailureHandler> failureHandler() default FailureHandler.Default.class;

    /**
     * 加锁失败时抛出的异常类型
     */
    Class<? extends RuntimeException> failFast() default LockFailureException.class;

    /**
     * 指定 LockProvider 类型
     */
    Class<? extends com.geek.lock.core.LockProvider> provider() default com.geek.lock.core.LockProvider.class;
}