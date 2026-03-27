package com.geek.lock.redisson.config;

import com.geek.lock.core.LockProvider;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.redisson.provider.RedissonLockProvider;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "lock4j.redisson", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonLockProperties.class)
public class RedissonLockAutoConfiguration {

    @Bean("redissonLockProvider")
    @ConditionalOnMissingBean(name = "redissonLockProvider")
    public LockProvider redissonLockProvider(RedissonClient redissonClient,
                                              LockEventPublisher eventPublisher) {
        return new RedissonLockProvider(redissonClient, eventPublisher);
    }
}