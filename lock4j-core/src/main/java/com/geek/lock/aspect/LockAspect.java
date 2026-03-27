package com.geek.lock.aspect;

import com.geek.lock.annotation.Lock;
import com.geek.lock.core.LockExecutor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Lock 切面
 * 
 * <p>这是 Lock4j 框架的入口点，负责拦截所有带有 @Lock 注解的方法，
 * 并委托给 {@link LockExecutor} 执行实际的锁逻辑。</p>
 * 
 * <p>切面执行流程：
 * <pre>
 * 1. 匹配所有标注了 @Lock 注解的方法
 * 2. 从方法或类上获取 @Lock 注解
 * 3. 委托给 LockExecutor 执行
 * </pre>
 * 
 * <p>支持两种注解使用方式：
 * <ul>
 *     <li>方法级别：@Lock 直接标注在方法上</li>
 *     <li>类级别：@Lock 标注在类上，对该类所有方法生效</li>
 * </ul>
 * 
 * @see Lock
 * @see LockExecutor
 */
@Aspect
public class LockAspect {

    /**
     * 锁执行器，负责实际的加锁、执行、解锁逻辑
     */
    private final LockExecutor lockExecutor;

    /**
     * 构造函数
     *
     * @param lockExecutor 锁执行器实例
     */
    public LockAspect(LockExecutor lockExecutor) {
        this.lockExecutor = lockExecutor;
    }

    /**
     * 定义切点
     * 
     * <p>匹配所有标注了 @Lock 注解的方法（包括注解标注在类上的情况）。</p>
     */
    @Pointcut("@annotation(com.geek.lock.annotation.Lock)")
    public void lockPointcut() {
        // 切点定义，无需实现
    }

    /**
     * 环绕通知
     * 
     * <p>拦截带有 @Lock 注解的方法调用，在锁的保护下执行方法。</p>
     * 
     * <p>执行流程：
     * <ol>
     *     <li>获取 @Lock 注解配置</li>
     *     <li>如果未找到注解，直接执行原方法</li>
     *     <li>如果找到注解，委托给 LockExecutor 执行</li>
     * </ol>
     *
     * @param joinPoint AOP 切点
     * @return 方法执行结果
     * @throws Throwable 方法执行或加锁过程中可能抛出的异常
     */
    @Around("lockPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取 @Lock 注解配置
        Lock annotation = getLockAnnotation(joinPoint);
        
        // 如果未找到注解，直接执行原方法
        if (isNull(annotation)) {
            return joinPoint.proceed();
        }
        
        // 委托给 LockExecutor 执行
        return lockExecutor.execute(joinPoint, annotation);
    }

    /**
     * 获取 @Lock 注解
     * 
     * <p>优先从方法上获取，如果方法上没有则从类上获取。</p>
     * 
     * <p>查找顺序：
     * <ol>
     *     <li>方法级别注解</li>
     *     <li>类级别注解</li>
     * </ol>
     *
     * @param joinPoint AOP 切点
     * @return @Lock 注解实例，如果未找到返回 null
     */
    private Lock getLockAnnotation(ProceedingJoinPoint joinPoint) {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 优先从方法上获取注解
        Lock annotation = method.getAnnotation(Lock.class);
        
        // 如果方法上没有，尝试从类上获取
        if (isNull(annotation)) {
            annotation = method.getDeclaringClass().getAnnotation(Lock.class);
        }
        
        return annotation;
    }
}