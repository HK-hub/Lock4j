package com.geek.lock.zookeeper.annotation;

import com.geek.lock.annotation.Lock;
import com.geek.lock.core.FailureHandler;
import com.geek.lock.core.KeyBuilder;
import com.geek.lock.core.LockInterceptor;
import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.KeyAbsentPolicy;
import com.geek.lock.enums.LockType;
import com.geek.lock.exception.LockFailureException;
import com.geek.lock.zookeeper.provider.ZookeeperLockProvider;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Zookeeper Lock 注解
 * 
 * <p>使用 Zookeeper 作为 LockProvider 的快捷注解。</p>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Lock
public @interface ZookeeperLock {

    @AliasFor(annotation = Lock.class, attribute = "keys")
    String[] keys() default {};

    @AliasFor(annotation = Lock.class, attribute = "keyBuilder")
    Class<? extends KeyBuilder> keyBuilder() default KeyBuilder.class;

    @AliasFor(annotation = Lock.class, attribute = "keyAbsentPolicy")
    KeyAbsentPolicy keyAbsentPolicy() default KeyAbsentPolicy.USE_METHOD_PATH;

    @AliasFor(annotation = Lock.class, attribute = "prefix")
    String prefix() default "";

    @AliasFor(annotation = Lock.class, attribute = "waitTime")
    long waitTime() default 3000;

    @AliasFor(annotation = Lock.class, attribute = "leaseTime")
    long leaseTime() default 30000;

    @AliasFor(annotation = Lock.class, attribute = "timeUnit")
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    @AliasFor(annotation = Lock.class, attribute = "lockType")
    LockType lockType() default LockType.REENTRANT;

    @AliasFor(annotation = Lock.class, attribute = "failureHandler")
    Class<? extends FailureHandler> failureHandler() default FailureHandler.Default.class;

    @AliasFor(annotation = Lock.class, attribute = "failFast")
    Class<? extends RuntimeException> failFast() default LockFailureException.class;

    @AliasFor(annotation = Lock.class, attribute = "provider")
    Class<? extends LockProvider> provider() default ZookeeperLockProvider.class;

    @AliasFor(annotation = Lock.class, attribute = "interceptor")
    Class<? extends LockInterceptor> interceptor() default LockInterceptor.class;
}