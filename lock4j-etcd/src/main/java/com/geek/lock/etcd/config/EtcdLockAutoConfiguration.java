package com.geek.lock.etcd.config;

import com.geek.lock.core.LockProvider;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.etcd.provider.EtcdLockProvider;
import io.etcd.jetcd.Client;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Client.class)
@ConditionalOnProperty(prefix = "lock4j.etcd", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EtcdLockProperties.class)
public class EtcdLockAutoConfiguration {

    @Bean("etcdLockProvider")
    @ConditionalOnMissingBean(name = "etcdLockProvider")
    public LockProvider etcdLockProvider(Client etcdClient,
                                          LockEventPublisher eventPublisher) {
        return new EtcdLockProvider(etcdClient, eventPublisher);
    }
}