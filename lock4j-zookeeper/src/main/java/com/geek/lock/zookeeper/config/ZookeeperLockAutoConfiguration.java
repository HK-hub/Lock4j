package com.geek.lock.zookeeper.config;

import com.geek.lock.core.LockProvider;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.zookeeper.provider.ZookeeperLockProvider;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(CuratorFramework.class)
@ConditionalOnProperty(prefix = "lock4j.zookeeper", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ZookeeperLockProperties.class)
public class ZookeeperLockAutoConfiguration {

    @Bean("zookeeperLockProvider")
    @ConditionalOnMissingBean(name = "zookeeperLockProvider")
    public LockProvider zookeeperLockProvider(CuratorFramework curatorFramework,
                                               LockEventPublisher eventPublisher) {
        return new ZookeeperLockProvider(curatorFramework, eventPublisher);
    }
}