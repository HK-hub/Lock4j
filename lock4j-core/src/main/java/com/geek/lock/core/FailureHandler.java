package com.geek.lock.core;

import com.geek.lock.annotation.Lock;
import com.geek.lock.model.LockFailureContext;

import java.util.concurrent.TimeUnit;

public interface FailureHandler {

    Object handle(LockFailureContext context);

    class Default implements FailureHandler {
        @Override
        public Object handle(LockFailureContext context) {
            return null;
        }
    }
}