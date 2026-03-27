package com.geek.lock.handler;

import com.geek.lock.core.FailureHandler;
import com.geek.lock.model.LockFailureContext;

public class ReturnDefaultFailureHandler implements FailureHandler {

    private final Object defaultValue;

    public ReturnDefaultFailureHandler() {
        this.defaultValue = null;
    }

    public ReturnDefaultFailureHandler(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public Object handle(LockFailureContext context) {
        return defaultValue;
    }
}