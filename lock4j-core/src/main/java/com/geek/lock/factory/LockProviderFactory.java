package com.geek.lock.factory;

import com.geek.lock.core.LockProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface LockProviderFactory {

    LockProvider getProvider(Class<? extends LockProvider> providerClass);

    LockProvider getDefaultProvider();

    void registerProvider(String name, LockProvider provider);

    void setDefaultProvider(String name);
}