package com.geek.lock.model;

import com.geek.lock.annotation.Lock;
import lombok.Data;
import lombok.experimental.Accessors;

import java.lang.reflect.Method;

@Data
@Accessors(chain = true)
public class LockFailureContext {

    private String[] lockKeys;
    private Lock annotation;
    private Object[] args;
    private Method method;
    private Object target;
}