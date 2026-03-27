package com.geek.lock.handler;

import com.geek.lock.core.FailureHandler;
import com.geek.lock.exception.LockFailureException;
import com.geek.lock.model.LockFailureContext;
import org.apache.commons.lang3.ArrayUtils;

public class ThrowExceptionFailureHandler implements FailureHandler {

    @Override
    public Object handle(LockFailureContext context) {
        String[] lockKeys = context.getLockKeys();
        String lockKey = ArrayUtils.isNotEmpty(lockKeys) ? lockKeys[0] : "unknown";
        Class<? extends RuntimeException> exceptionClass = context.getAnnotation().failFast();

        if (exceptionClass == LockFailureException.class) {
            throw new LockFailureException(lockKey);
        }

        throw createException(exceptionClass, lockKey);
    }

    private RuntimeException createException(Class<? extends RuntimeException> exceptionClass, String lockKey) {
        try {
            return exceptionClass.getConstructor(String.class).newInstance(lockKey);
        } catch (Exception e) {
            return new LockFailureException(lockKey);
        }
    }
}