package com.geek.lock.factory;

import com.geek.lock.core.LockProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * LockProvider 工厂默认实现
 * 
 * <p>管理所有注册的 {@link LockProvider} 实例，支持按名称或类型获取 Provider。</p>
 * 
 * <p>工厂职责：
 * <ul>
 *     <li>注册 LockProvider 实例</li>
 *     <li>按类型获取 LockProvider</li>
 *     <li>获取默认 LockProvider</li>
 *     <li>设置默认 LockProvider</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>
 * LockProviderFactory factory = new DefaultLockProviderFactory();
 * factory.registerProvider("redisson", redissonProvider);
 * factory.registerProvider("zookeeper", zookeeperProvider);
 * factory.setDefaultProvider("redisson");
 * 
 * LockProvider provider = factory.getDefaultProvider();
 * </pre>
 * 
 * @see LockProvider
 */
public class DefaultLockProviderFactory implements LockProviderFactory {

    /**
     * Provider 注册表
     * 
     * <p>Key: Provider 名称
     * <br>Value: Provider 实例</p>
     */
    private final Map<String, LockProvider> providers = new ConcurrentHashMap<>();

    /**
     * 默认 Provider 名称
     * 
     * <p>当未指定 Provider 时使用此默认值</p>
     */
    private volatile String defaultProviderName;

    /**
     * 根据类型获取 LockProvider
     * 
     * <p>获取流程：
     * <ol>
     *     <li>如果类型为 null 或 LockProvider 接口本身，返回默认 Provider</li>
     *     <li>从注册表中查找匹配类型的 Provider</li>
     *     <li>如果未找到，返回默认 Provider</li>
     * </ol>
     *
     * @param providerClass LockProvider 类型
     * @return 匹配的 LockProvider 实例，可能为 null
     */
    @Override
    public LockProvider getProvider(Class<? extends LockProvider> providerClass) {
        // 如果类型为 null 或接口本身，返回默认 Provider
        if (isNull(providerClass) || providerClass == LockProvider.class) {
            return getDefaultProvider();
        }

        // 从注册表中查找匹配类型的 Provider
        return providers.values().stream()
                .filter(p -> providerClass.isAssignableFrom(p.getClass()))
                .findFirst()
                .orElse(getDefaultProvider());
    }

    /**
     * 获取默认 LockProvider
     * 
     * <p>获取流程：
     * <ol>
     *     <li>如果设置了默认 Provider 名称且存在对应 Provider，返回该 Provider</li>
     *     <li>否则返回注册表中的第一个 Provider</li>
     *     <li>如果注册表为空，返回 null</li>
     * </ol>
     *
     * @return 默认 LockProvider 实例，可能为 null
     */
    @Override
    public LockProvider getDefaultProvider() {
        // 如果设置了默认名称且存在对应 Provider
        if (nonNull(defaultProviderName) && providers.containsKey(defaultProviderName)) {
            return providers.get(defaultProviderName);
        }
        
        // 返回注册表中的第一个 Provider
        return providers.values().stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 注册 LockProvider
     * 
     * <p>将 Provider 实例注册到工厂中，使用 Provider 的 getName() 作为名称。</p>
     * 
     * <p>注册逻辑：
     * <ul>
     *     <li>将 Provider 添加到注册表</li>
     *     <li>如果这是第一个注册的 Provider，自动设为默认 Provider</li>
     * </ul>
     *
     * @param name     Provider 名称
     * @param provider LockProvider 实例
     */
    @Override
    public void registerProvider(String name, LockProvider provider) {
        // 注册 Provider
        providers.put(name, provider);
        
        // 如果是第一个注册的 Provider，自动设为默认
        if (isNull(defaultProviderName)) {
            defaultProviderName = name;
        }
    }

    /**
     * 设置默认 LockProvider
     * 
     * <p>只有当指定名称的 Provider 已注册时才会设置成功。</p>
     *
     * @param name Provider 名称
     */
    @Override
    public void setDefaultProvider(String name) {
        // 只有注册表中存在该名称的 Provider 时才设置
        if (providers.containsKey(name)) {
            this.defaultProviderName = name;
        }
    }
}