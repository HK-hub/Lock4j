package com.geek.lock.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "lock4j")
public class LockProperties {

    private boolean enabled = true;
    private String defaultProvider;
    private long defaultWaitTime = 3000;
    private long defaultLeaseTime = 30000;

    private final Redisson redisson = new Redisson();
    private final Zookeeper zookeeper = new Zookeeper();
    private final Etcd etcd = new Etcd();
    private final Local local = new Local();

    @Data
    public static class Redisson {
        private boolean enabled = true;
    }

    @Data
    public static class Zookeeper {
        private boolean enabled = false;
        private String connectString = "localhost:2181";
        private int sessionTimeout = 60000;
        private int connectionTimeout = 15000;
    }

    @Data
    public static class Etcd {
        private boolean enabled = false;
        private List<String> endpoints = List.of("http://localhost:2379");
    }

    @Data
    public static class Local {
        private boolean enabled = true;
    }
}