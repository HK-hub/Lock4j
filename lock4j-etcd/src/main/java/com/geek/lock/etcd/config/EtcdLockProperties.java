package com.geek.lock.etcd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "lock4j.etcd")
public class EtcdLockProperties {

    private boolean enabled = false;
    private List<String> endpoints = List.of("http://localhost:2379");
    private Long connectTimeout = 5000L;
    private String user;
    private String password;
}