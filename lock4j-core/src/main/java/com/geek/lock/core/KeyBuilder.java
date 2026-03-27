package com.geek.lock.core;

import com.geek.lock.annotation.Lock;

import java.lang.reflect.Method;

public interface KeyBuilder {

    String[] build(Method method, String[] parameterNames, Object[] args, Lock annotation);

    default int getOrder() {
        return 0;
    }
}