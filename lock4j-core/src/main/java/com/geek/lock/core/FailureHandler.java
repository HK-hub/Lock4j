package com.geek.lock.core;

import com.geek.lock.model.LockFailureContext;

public interface FailureHandler {

    Object handle(LockFailureContext context);

    class Default implements FailureHandler {
        @Override
        public Object handle(LockFailureContext context) {
            return null;
        }
    }
}