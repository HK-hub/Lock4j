package com.geek.lock.factory;

import com.geek.lock.core.LockProvider;

public interface LockProviderFactory {

    LockProvider getProvider(Class<? extends LockProvider> providerClass);

    LockProvider getPrimaryProvider();

    LockProvider getFirstProvider();

    boolean hasPrimaryProvider();

    void registerProvider(LockProvider provider);

    void setPrimaryProvider(Class<? extends LockProvider> providerClass);
}