package com.geek.lock.example;

import com.geek.lock.core.FailureHandler;
import com.geek.lock.model.LockFailureContext;
import org.springframework.stereotype.Component;

@Component
public class CustomLockFailureHandler implements FailureHandler {

    @Override
    public Object handle(LockFailureContext context) {
        String lockKey = context.getLockKey();
        System.out.println("Lock acquisition failed for key: " + lockKey);
        return "Lock failed: " + lockKey;
    }
}