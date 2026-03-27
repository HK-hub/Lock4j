package com.geek.lock.core;

import com.geek.lock.annotation.Lock;
import org.aspectj.lang.ProceedingJoinPoint;

public interface LockExecutor {

    Object execute(ProceedingJoinPoint joinPoint, Lock annotation) throws Throwable;
}