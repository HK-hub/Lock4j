package com.geek.lock.zookeeper.config;

import com.geek.lock.core.LockProvider;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.zookeeper.provider.ZookeeperLockProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.ObjectProvider;
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

    @Bean
    @ConditionalOnMissingBean(CuratorFramework.class)
    public CuratorFramework curatorFramework(ZookeeperLockProperties properties) {
        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(properties.getConnectString())
                .sessionTimeoutMs(properties.getSessionTimeout())
                .connectionTimeoutMs(properties.getConnectionTimeout())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        curatorFramework.start();
        return curatorFramework;
    }

    @Bean("zookeeperLockProvider")
    @ConditionalOnMissingBean(name = "zookeeperLockProvider")
    public LockProvider zookeeperLockProvider(ObjectProvider<CuratorFramework> curatorFrameworkProvider,
                                               LockEventPublisher eventPublisher) {
        CuratorFramework curatorFramework = curatorFrameworkProvider.getIfAvailable();
        if (curatorFramework == null) {
            throw new IllegalStateException("CuratorFramework is not available. Please check Zookeeper configuration.");
        }
        return new ZookeeperLockProvider(curatorFramework, eventPublisher);
    }
}