package com.geek.lock.autoconfigure;

import com.geek.lock.core.LockProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lock4j")
public class LockProperties {

    private boolean enabled = true;
    private Class<? extends LockProvider> primaryProvider;
    private long defaultWaitTime = 3000;
    private long defaultLeaseTime = 30000;
}