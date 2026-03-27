package com.geek.lock.autoconfigure;

import com.geek.lock.aspect.LockAspect;
import com.geek.lock.core.FailureHandler;
import com.geek.lock.core.LockExecutor;
import com.geek.lock.core.LockProvider;
import com.geek.lock.event.DefaultLockEventPublisher;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.executor.DefaultLockExecutor;
import com.geek.lock.factory.DefaultLockProviderFactory;
import com.geek.lock.factory.LockProviderFactory;
import com.geek.lock.handler.ThrowExceptionFailureHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(LockAspect.class)
@EnableConfigurationProperties(LockProperties.class)
@RequiredArgsConstructor
public class LockAutoConfiguration {

    private final LockProperties properties;

    @Bean
    @ConditionalOnMissingBean
    public LockEventPublisher lockEventPublisher() {
        return new DefaultLockEventPublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    public LockProviderFactory lockProviderFactory(ObjectProvider<LockProvider> providers) {
        DefaultLockProviderFactory factory = new DefaultLockProviderFactory();
        providers.forEach(provider -> factory.registerProvider(provider.getName(), provider));

        String defaultProvider = properties.getDefaultProvider();
        if (defaultProvider != null && !defaultProvider.isEmpty()) {
            factory.setDefaultProvider(defaultProvider);
        }

        return factory;
    }

    @Bean
    @ConditionalOnMissingBean(FailureHandler.class)
    public FailureHandler defaultFailureHandler() {
        return new ThrowExceptionFailureHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public LockExecutor lockExecutor(LockProviderFactory providerFactory) {
        return new DefaultLockExecutor(providerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(LockExecutor lockExecutor) {
        return new LockAspect(lockExecutor);
    }
}