package com.geek.lock.executor;

import com.geek.lock.annotation.Lock;
import com.geek.lock.core.AbstractLockExecutor;
import com.geek.lock.core.FailureHandler;
import com.geek.lock.core.KeyBuilder;
import com.geek.lock.core.LockInterceptor;
import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.KeyAbsentPolicy;
import com.geek.lock.exception.NoSuchProviderException;
import com.geek.lock.factory.LockProviderFactory;
import com.geek.lock.handler.ThrowExceptionFailureHandler;
import com.geek.lock.model.LockFailureContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * 默认锁执行器实现
 * 
 * <p>提供了锁 Key 解析、Provider 获取、失败处理的具体实现。
 * 支持两种 Key 解析方式：
 * <ul>
 *     <li>SpEL 表达式解析（默认）</li>
 *     <li>自定义 KeyBuilder（通过 Spring 容器获取）</li>
 * </ul>
 */
@Slf4j
public class DefaultLockExecutor extends AbstractLockExecutor implements ApplicationContextAware {

    /**
     * SpEL 表达式解析器（线程安全，可全局共享）
     */
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /**
     * 锁拦截器
     */
    private static final LockInterceptor EMPTY_INTERCEPTOR = new LockInterceptor() {};

    /**
     * LockProvider 工厂，用于获取锁提供者
     */
    private final LockProviderFactory providerFactory;

    /**
     * SpEL 表达式缓存，避免重复解析相同的表达式
     */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 失败处理器缓存，避免重复创建相同的处理器
     */
    private final Map<Class<? extends FailureHandler>, FailureHandler> handlerCache = new ConcurrentHashMap<>();

    /**
     * Spring 应用上下文，用于获取 KeyBuilder Bean
     */
    private ApplicationContext applicationContext;

    /**
     * 构造函数
     *
     * @param providerFactory LockProvider 工厂
     */
    public DefaultLockExecutor(LockProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    /**
     * 设置 Spring 应用上下文
     * 
     * <p>通过 ApplicationContextAware 接口注入，
     * 用于后续获取自定义 KeyBuilder Bean。</p>
     *
     * @param applicationContext Spring 应用上下文
     * @throws BeansException 异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 解析锁 Key
     *
     * <p>解析流程：
     * <pre>
     * 1. 判断是否指定了自定义 KeyBuilder
     *    - 是：从 Spring 容器获取 KeyBuilder，调用其 build 方法
     * 2. 如果未指定 KeyBuilder，判断 keys 是否为空
     *    - keys 不为空：使用 SpEL 解析
     *    - keys 为空：根据 keyAbsentPolicy 处理
     *      • USE_METHOD_PATH: 使用方法全限定名作为 Key
     *      • THROW_EXCEPTION: 抛出异常
     * 3. 如果配置了前缀，为每个 Key 添加前缀
     * </pre>
     *
     * @param joinPoint  AOP 切点
     * @param annotation @Lock 注解配置
     * @return 锁 Key 数组
     */
    @Override
    protected String[] resolveLockKeys(ProceedingJoinPoint joinPoint, Lock annotation) throws Exception {
        String prefix = annotation.prefix();
        String[] keys;

        // 获取方法签名信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        
        // 获取注解中配置的 KeyBuilder 类型
        Class<? extends KeyBuilder> keyBuilderClass = annotation.keyBuilder();
        // 获取注解中配置的 keys
        String[] keysFromAnnotation = annotation.keys();
        
        // 判断是否指定了自定义 KeyBuilder
        if (nonNull(keyBuilderClass) && keyBuilderClass != KeyBuilder.class) {
            // 使用自定义 KeyBuilder 解析 Key
            keys = buildKeysByKeyBuilder(keyBuilderClass, signature.getMethod(), 
                    signature.getParameterNames(), joinPoint.getArgs(), annotation);
        } else if (ArrayUtils.isNotEmpty(keysFromAnnotation)) {
            // 使用 SpEL 解析 keys 属性中的表达式
            keys = parseKeysBySpel(joinPoint, keysFromAnnotation);
        } else {
            // keyBuilder 为空且 keys 也为空，根据 keyAbsentPolicy 处理
            KeyAbsentPolicy policy = annotation.keyAbsentPolicy();
            
            if (policy == KeyAbsentPolicy.USE_METHOD_PATH) {
                // 使用方法全限定名作为 Key
                keys = new String[]{signature.getMethod().toGenericString()};
            } else {
                // 抛出异常
                throw new IllegalArgumentException(
                    "Lock key is empty. Please specify 'keys' or 'keyBuilder' in @Lock annotation, " +
                    "or set keyAbsentPolicy to USE_METHOD_PATH");
            }
        }

        // 如果配置了前缀，为每个 Key 添加前缀
        if (StringUtils.isNotEmpty(prefix)) {
            for (int i = 0; i < keys.length; i++) {
                keys[i] = prefix + keys[i];
            }
        }

        return keys;
    }

    /**
     * 使用 SpEL 解析 Key 表达式
     * 
     * <p>解析流程：
     * <pre>
     * 1. 检查表达式数组是否为空
     * 2. 创建 SpEL 求值上下文，绑定方法参数
     * 3. 遍历每个表达式，解析并求值
     * 4. 将解析结果收集到列表中返回
     * </pre>
     *
     * @param joinPoint      AOP 切点
     * @param keyExpressions SpEL 表达式数组
     * @return 解析后的 Key 数组
     */
    private String[] parseKeysBySpel(ProceedingJoinPoint joinPoint, String[] keyExpressions) {
        // 检查表达式数组是否为空
        if (ArrayUtils.isEmpty(keyExpressions)) {
            return new String[0];
        }

        // 获取方法签名信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        // 创建 SpEL 求值上下文
        EvaluationContext context = createEvaluationContext(parameterNames, args);

        // 收集解析结果
        List<String> keys = new ArrayList<>();
        
        // 遍历每个表达式进行解析
        for (String keyExpression : keyExpressions) {
            // 跳过空表达式
            if (StringUtils.isEmpty(keyExpression)) {
                continue;
            }
            
            try {
                // 从缓存获取或解析表达式
                Expression expression = expressionCache.computeIfAbsent(keyExpression, SPEL_PARSER::parseExpression);
                // 在上下文中求值
                Object value = expression.getValue(context);
                // 将结果添加到列表
                if (nonNull(value)) {
                    keys.add(value.toString());
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse SpEL expression: " + keyExpression, e);
            }
        }

        return keys.toArray(new String[0]);
    }

    /**
     * 创建 SpEL 求值上下文
     * 
     * <p>将方法参数名和参数值绑定到上下文中，
     * 使得 SpEL 表达式可以通过参数名引用参数值。</p>
     *
     * @param parameterNames 参数名数组
     * @param args           参数值数组
     * @return SpEL 求值上下文
     */
    private EvaluationContext createEvaluationContext(String[] parameterNames, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 将参数名和参数值绑定到上下文
        if (ArrayUtils.isNotEmpty(parameterNames) && ArrayUtils.isNotEmpty(args)) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        
        return context;
    }

    /**
     * 使用自定义 KeyBuilder 构建 Key
     * 
     * <p>从 Spring 容器获取指定类型的 KeyBuilder Bean，
     * 然后调用其 build 方法构建 Key。</p>
     *
     * @param keyBuilderClass KeyBuilder 类型
     * @param method          被拦截的方法
     * @param parameterNames  参数名数组
     * @param args            参数值数组
     * @param annotation      @Lock 注解配置
     * @return 构建的 Key 数组
     */
    private String[] buildKeysByKeyBuilder(Class<? extends KeyBuilder> keyBuilderClass,
                                             Method method, String[] parameterNames, Object[] args, Lock annotation) {
        // 检查应用上下文是否可用
        if (isNull(applicationContext)) {
            throw new IllegalStateException("ApplicationContext is not available");
        }
        
        // 从 Spring 容器获取 KeyBuilder Bean
        KeyBuilder builder = applicationContext.getBean(keyBuilderClass);
        
        // 调用 build 方法构建 Key
        return builder.build(method, parameterNames, args, annotation);
    }

    /**
     * 解析 LockProvider
     * 
     * <p>解析流程：
     * <pre>
     * 1. 如果注解指定了具体 Provider Class：
     *    - 使用指定 Provider
     *    - 如果找不到，抛出 NoSuchProviderException
     * 2. 如果注解未指定 Provider（provider == LockProvider.class）：
     *    - 优先使用 Primary Provider（如果存在）
     *    - 如果 Primary Provider 不存在或未设置，使用第一个可用的 Provider
     * 3. 如果没有任何可用的 Provider，抛出异常
     * </pre>
     *
     * @param annotation @Lock 注解配置
     * @return LockProvider 实例
     */
    @Override
    protected LockProvider resolveProvider(Lock annotation) {
        Class<? extends LockProvider> providerClass = annotation.provider();
        
        if (providerClass != LockProvider.class) {
            LockProvider provider = providerFactory.getProvider(providerClass);
            if (nonNull(provider)) {
                return provider;
            }
            throw new NoSuchProviderException(providerClass);
        }
        
        if (providerFactory.hasPrimaryProvider()) {
            LockProvider primaryProvider = providerFactory.getPrimaryProvider();
            if (nonNull(primaryProvider)) {
                return primaryProvider;
            }
        }
        
        LockProvider firstProvider = providerFactory.getFirstProvider();
        if (nonNull(firstProvider)) {
            return firstProvider;
        }
        
        throw new IllegalStateException("No LockProvider available. Please configure at least one LockProvider.");
    }

    /**
     * 处理加锁失败
     * 
     * <p>处理流程：
     * <pre>
     * 1. 获取注解配置的失败处理器类型
     * 2. 尝试从缓存获取失败处理器
     * 3. 如果缓存中没有，创建新的失败处理器
     *    - 优先从 Spring 容器获取
     *    - 如果获取失败，尝试通过反射创建
     * 4. 创建失败上下文，包含锁 Key、注解、方法等信息
     * 5. 调用失败处理器的 handle 方法
     * </pre>
     *
     * @param joinPoint AOP 切点
     * @param annotation @Lock 注解配置
     * @param lockKeys   需要加锁的 Key 数组
     * @return 失败处理结果
     */
    @Override
    protected Object handleLockFailure(ProceedingJoinPoint joinPoint, Lock annotation, String[] lockKeys) {
        // 获取失败处理器类型
        Class<? extends FailureHandler> handlerClass = annotation.failureHandler();

        // 从缓存获取或创建失败处理器
        FailureHandler handler = handlerCache.computeIfAbsent(handlerClass, clazz -> {
            // 如果是默认处理器，直接创建
            if (clazz == FailureHandler.Default.class) {
                return new ThrowExceptionFailureHandler();
            }
            
            // 尝试从 Spring 容器获取
            if (nonNull(applicationContext)) {
                try {
                    return applicationContext.getBean(clazz);
                } catch (Exception e) {
                    // 容器获取失败，尝试通过反射创建
                    try {
                        return clazz.getDeclaredConstructor().newInstance();
                    } catch (Exception ex) {
                        return new ThrowExceptionFailureHandler();
                    }
                }
            }
            
            // 没有应用上下文，尝试通过反射创建
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return new ThrowExceptionFailureHandler();
            }
        });

        // 获取方法签名信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        
        // 构建失败上下文
        LockFailureContext context = new LockFailureContext()
                .setLockKeys(lockKeys)
                .setAnnotation(annotation)
                .setArgs(joinPoint.getArgs())
                .setMethod(signature.getMethod())
                .setTarget(joinPoint.getTarget());

        // 调用失败处理器
        return handler.handle(context);
    }

    /**
     * 解析拦截器
     *
     * <p>解析流程：
     * <pre>
     * 1. 如果拦截器类型为 LockInterceptor.class（默认值），返回空实现
     * 2. 优先从 Spring 容器获取 Bean
     * 3. 如果容器中无 Bean，通过反射创建实例
     * </pre>
     *
     * @param annotation @Lock 注解配置
     * @return LockInterceptor 实例
     */
    @Override
    protected LockInterceptor resolveInterceptor(Lock annotation) {
        Class<? extends LockInterceptor> interceptorClass = annotation.interceptor();

        if (interceptorClass == LockInterceptor.class) {
            return EMPTY_INTERCEPTOR;
        }

        if (nonNull(applicationContext)) {
            try {
                return applicationContext.getBean(interceptorClass);
            } catch (Exception e) {
                log.debug("Interceptor bean not found in Spring container, trying reflection: {}",
                    interceptorClass.getName());
            }
        }

        try {
            return interceptorClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to instantiate interceptor: " + interceptorClass.getName(), e);
        }
    }
}