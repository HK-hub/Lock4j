package com.geek.lock.etcd.config;

import com.geek.lock.core.LockProvider;
import com.geek.lock.etcd.provider.EtcdLockProvider;
import com.geek.lock.event.LockEventPublisher;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@ConditionalOnClass(Client.class)
@ConditionalOnProperty(prefix = "lock4j.etcd", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EtcdLockProperties.class)
public class EtcdLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Client.class)
    public Client etcdClient(EtcdLockProperties properties) {
        ClientBuilder builder = Client.builder()
                .endpoints(properties.getEndpoints().toArray(new String[0]));

        if (properties.getConnectTimeout() != null) {
            builder.connectTimeout(Duration.ofMillis(properties.getConnectTimeout()));
        }
        if (properties.getUser() != null && properties.getPassword() != null) {
            builder.user(ByteSequence.from(properties.getUser(), StandardCharsets.UTF_8));
            builder.password(ByteSequence.from(properties.getPassword(), StandardCharsets.UTF_8));
        }

        return builder.build();
    }

    @Bean("etcdLockProvider")
    @ConditionalOnMissingBean(name = "etcdLockProvider")
    public LockProvider etcdLockProvider(ObjectProvider<Client> etcdClientProvider,
                                          LockEventPublisher eventPublisher) {
        Client etcdClient = etcdClientProvider.getIfAvailable();
        if (etcdClient == null) {
            throw new IllegalStateException("Etcd Client is not available. Please check Etcd configuration.");
        }
        return new EtcdLockProvider(etcdClient, eventPublisher);
    }
}