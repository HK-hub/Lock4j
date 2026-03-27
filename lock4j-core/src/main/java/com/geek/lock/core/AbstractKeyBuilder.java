package com.geek.lock.core;

import com.geek.lock.annotation.Lock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Method;

/**
 * Key 构建器抽象类
 * 
 * <p>提供了 Key 构建的基础流程，当指定了自定义 KeyBuilder 时，
 * 会检查注解中的 keys 属性是否为空，如果不为空则输出警告日志。</p>
 * 
 * <p>设计目的：
 * <ul>
 *     <li>避免用户误用：同时配置 keys 和 keyBuilder 时提醒用户</li>
 *     <li>简化子类实现：子类只需关注实际的 Key 构建逻辑</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractKeyBuilder implements KeyBuilder {

    /**
     * 构建 Key
     * 
     * <p>这是 final 方法，定义了 Key 构建的基础流程：
     * <ol>
     *     <li>检查注解中的 keys 属性是否为空</li>
     *     <li>如果不为空，输出警告日志（keys 将被忽略）</li>
     *     <li>调用子类的 doBuild 方法执行实际构建</li>
     * </ol>
     *
     * @param method         被拦截的方法
     * @param parameterNames 参数名数组
     * @param args           参数值数组
     * @param annotation     @Lock 注解配置
     * @return 构建的 Key 数组
     */
    @Override
    public final String[] build(Method method, String[] parameterNames, Object[] args, Lock annotation) {
        // 获取注解中的 keys 属性
        String[] keys = annotation.keys();
        
        // 检查是否同时配置了 keys 和 keyBuilder
        if (ArrayUtils.isNotEmpty(keys)) {
            log.warn("keys {} will be ignored when keyBuilder {} is specified",
                    keys, this.getClass().getSimpleName());
        }
        
        // 调用子类实现执行实际构建
        return doBuild(method, parameterNames, args, annotation);
    }

    /**
     * 执行实际的 Key 构建
     * 
     * <p>子类必须实现此方法，提供具体的 Key 构建逻辑。
     * 可以基于方法信息、参数名、参数值等构建自定义的 Key。</p>
     *
     * @param method         被拦截的方法
     * @param parameterNames 参数名数组
     * @param args           参数值数组
     * @param annotation     @Lock 注解配置
     * @return 构建的 Key 数组
     */
    protected abstract String[] doBuild(Method method, String[] parameterNames, Object[] args, Lock annotation);
}