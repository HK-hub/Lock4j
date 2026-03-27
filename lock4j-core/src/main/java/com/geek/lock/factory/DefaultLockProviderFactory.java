package com.geek.lock.factory;

import com.geek.lock.core.LockProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * LockProvider 工厂默认实现
 * 
 * <p>管理所有注册的 {@link LockProvider} 实例，支持按类型获取 Provider。</p>
 * 
 * <p>工厂职责：
 * <ul>
 *     <li>注册 LockProvider 实例</li>
 *     <li>按类型获取 LockProvider</li>
 *     <li>获取 Primary Provider</li>
 *     <li>设置 Primary Provider</li>
 * </ul>
 * 
 * @see LockProvider
 */
public class DefaultLockProviderFactory implements LockProviderFactory {

    private final Map<Class<? extends LockProvider>, LockProvider> providers = new LinkedHashMap<>();

    private volatile Class<? extends LockProvider> primaryProviderClass;

    @Override
    public LockProvider getProvider(Class<? extends LockProvider> providerClass) {
        if (isNull(providerClass) || providerClass == LockProvider.class) {
            return null;
        }

        LockProvider provider = providers.get(providerClass);
        if (nonNull(provider)) {
            return provider;
        }

        for (Map.Entry<Class<? extends LockProvider>, LockProvider> entry : providers.entrySet()) {
            if (providerClass.isAssignableFrom(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public LockProvider getPrimaryProvider() {
        if (nonNull(primaryProviderClass) && providers.containsKey(primaryProviderClass)) {
            return providers.get(primaryProviderClass);
        }
        return null;
    }

    @Override
    public LockProvider getFirstProvider() {
        return providers.values().stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean hasPrimaryProvider() {
        return nonNull(primaryProviderClass);
    }

    @Override
    public void registerProvider(LockProvider provider) {
        if (isNull(provider)) {
            return;
        }
        
        Class<? extends LockProvider> providerClass = provider.getClass();
        providers.put(providerClass, provider);
    }

    @Override
    public void setPrimaryProvider(Class<? extends LockProvider> providerClass) {
        if (isNull(providerClass)) {
            return;
        }

        for (Class<? extends LockProvider> registeredClass : providers.keySet()) {
            if (providerClass.isAssignableFrom(registeredClass)) {
                this.primaryProviderClass = registeredClass;
                return;
            }
        }
        
        this.primaryProviderClass = providerClass;
    }
}