package com.geek.lock.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.concurrent.TimeUnit;

@Data
@Accessors(chain = true)
public class LockOptions {

    private long waitTime = 3000;
    private long leaseTime = 30000;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private boolean enableWatchdog = true;
    private long watchdogTimeout = 10000;
}