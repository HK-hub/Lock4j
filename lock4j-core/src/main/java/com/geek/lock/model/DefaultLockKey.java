package com.geek.lock.model;

import com.geek.lock.enums.LockType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.concurrent.TimeUnit;

@Data
@Accessors(chain = true)
public class DefaultLockKey implements com.geek.lock.core.LockKey {

    private String key;
    private LockType lockType = LockType.REENTRANT;
    private long waitTime = 3000;
    private long leaseTime = 30000;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
}