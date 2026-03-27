package com.geek.lock.redisson.config;

import com.geek.lock.core.LockProvider;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.redisson.provider.RedissonLockProvider;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Redisson.class)
@ConditionalOnProperty(prefix = "lock4j.redisson", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonLockProperties.class)
public class RedissonLockAutoConfiguration {

    private static final String REDIS_PREFIX = "redis://";

    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedissonLockProperties properties) {
        Config config = new Config();

        RedissonLockProperties.Cluster cluster = properties.getCluster();
        RedissonLockProperties.Sentinel sentinel = properties.getSentinel();

        if (cluster != null && cluster.getNodeAddresses() != null && cluster.getNodeAddresses().length > 0) {
            configureCluster(config, properties, cluster);
        } else if (sentinel != null && sentinel.getSentinelAddresses() != null 
                && sentinel.getSentinelAddresses().length > 0 && sentinel.getMasterName() != null) {
            configureSentinel(config, properties, sentinel);
        } else {
            configureSingleServer(config, properties);
        }

        return Redisson.create(config);
    }

    private void configureSingleServer(Config config, RedissonLockProperties properties) {
        SingleServerConfig singleConfig = config.useSingleServer();
        singleConfig.setAddress(normalizeAddress(properties.getAddress()));
        
        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            singleConfig.setPassword(properties.getPassword());
        }
        
        singleConfig.setDatabase(properties.getDatabase());
        singleConfig.setConnectionMinimumIdleSize(properties.getConnectionMinimumIdleSize());
        singleConfig.setConnectionPoolSize(properties.getConnectionPoolSize());
        singleConfig.setIdleConnectionTimeout((int) properties.getIdleConnectionTimeout().toMillis());
        singleConfig.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        singleConfig.setTimeout((int) properties.getTimeout().toMillis());
        singleConfig.setRetryAttempts(properties.getRetryAttempts());
        singleConfig.setRetryInterval((int) properties.getRetryInterval().toMillis());
    }

    private void configureCluster(Config config, RedissonLockProperties properties, RedissonLockProperties.Cluster cluster) {
        ClusterServersConfig clusterConfig = config.useClusterServers();
        
        String[] addresses = cluster.getNodeAddresses();
        String[] normalizedAddresses = new String[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            normalizedAddresses[i] = normalizeAddress(addresses[i]);
        }
        clusterConfig.addNodeAddress(normalizedAddresses);
        
        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            clusterConfig.setPassword(properties.getPassword());
        }
        
        clusterConfig.setIdleConnectionTimeout((int) properties.getIdleConnectionTimeout().toMillis());
        clusterConfig.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        clusterConfig.setTimeout((int) properties.getTimeout().toMillis());
        clusterConfig.setRetryAttempts(properties.getRetryAttempts());
        clusterConfig.setRetryInterval((int) properties.getRetryInterval().toMillis());
    }

    private void configureSentinel(Config config, RedissonLockProperties properties, RedissonLockProperties.Sentinel sentinel) {
        SentinelServersConfig sentinelConfig = config.useSentinelServers();
        sentinelConfig.setMasterName(sentinel.getMasterName());
        
        String[] addresses = sentinel.getSentinelAddresses();
        String[] normalizedAddresses = new String[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            normalizedAddresses[i] = normalizeAddress(addresses[i]);
        }
        sentinelConfig.addSentinelAddress(normalizedAddresses);
        
        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            sentinelConfig.setPassword(properties.getPassword());
        }
        
        sentinelConfig.setDatabase(properties.getDatabase());
        sentinelConfig.setIdleConnectionTimeout((int) properties.getIdleConnectionTimeout().toMillis());
        sentinelConfig.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        sentinelConfig.setTimeout((int) properties.getTimeout().toMillis());
    }

    private String normalizeAddress(String address) {
        if (address == null || address.isEmpty()) {
            return REDIS_PREFIX + "localhost:6379";
        }
        if (address.startsWith(REDIS_PREFIX) || address.startsWith("rediss://")) {
            return address;
        }
        return REDIS_PREFIX + address;
    }

    @Bean("redissonLockProvider")
    @ConditionalOnMissingBean(name = "redissonLockProvider")
    public LockProvider redissonLockProvider(ObjectProvider<RedissonClient> redissonClientProvider,
                                              LockEventPublisher eventPublisher) {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            throw new IllegalStateException("RedissonClient is not available. Please check Redis configuration.");
        }
        return new RedissonLockProvider(redissonClient, eventPublisher);
    }
}