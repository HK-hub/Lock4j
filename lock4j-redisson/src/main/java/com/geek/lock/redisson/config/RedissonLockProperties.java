package com.geek.lock.redisson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "lock4j.redisson")
public class RedissonLockProperties {

    private boolean enabled = true;

    private String address = "localhost:6379";

    private String password;

    private int database = 0;

    private int connectionMinimumIdleSize = 1;

    private int connectionPoolSize = 64;

    private Duration idleConnectionTimeout = Duration.ofMillis(10000);

    private Duration connectTimeout = Duration.ofMillis(10000);

    private Duration timeout = Duration.ofMillis(3000);

    private int retryAttempts = 3;

    private Duration retryInterval = Duration.ofMillis(1500);

    private Cluster cluster;

    private Sentinel sentinel;

    @Data
    public static class Cluster {
        private String[] nodeAddresses;
    }

    @Data
    public static class Sentinel {
        private String masterName;
        private String[] sentinelAddresses;
    }
}