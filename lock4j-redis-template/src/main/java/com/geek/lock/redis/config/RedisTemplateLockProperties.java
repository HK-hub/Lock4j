package com.geek.lock.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "lock4j.redis")
public class RedisTemplateLockProperties {

    private boolean enabled = true;

    private String host = "localhost";

    private int port = 6379;

    private String password;

    private int database = 0;

    private Duration connectTimeout = Duration.ofMillis(10000);

    private Duration readTimeout = Duration.ofMillis(3000);

    private int maxIdle = 8;

    private int maxActive = 16;

    private Duration maxWait = Duration.ofMillis(-1);
}