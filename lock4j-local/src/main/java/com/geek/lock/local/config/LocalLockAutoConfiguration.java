package com.geek.lock.local.config;

import com.geek.lock.core.LockProvider;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.local.provider.LocalLockProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "lock4j.local", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LocalLockProperties.class)
public class LocalLockAutoConfiguration {

    @Bean("localLockProvider")
    @ConditionalOnMissingBean(name = "localLockProvider")
    public LockProvider localLockProvider(LockEventPublisher eventPublisher) {
        return new LocalLockProvider(eventPublisher);
    }
}