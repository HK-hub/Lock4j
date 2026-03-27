package com.geek.lock.template;

import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.LockType;
import lombok.Builder;
import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
@Builder
public class LockRequest {

    private String key;
    
    @Builder.Default
    private long leaseTime = 30000;
    
    @Builder.Default
    private long waitTime = 3000;
    
    @Builder.Default
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    
    @Builder.Default
    private LockType lockType = LockType.REENTRANT;
    
    private Class<? extends LockProvider> provider;
}