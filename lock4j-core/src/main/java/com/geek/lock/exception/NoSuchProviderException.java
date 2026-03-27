package com.geek.lock.exception;

public class NoSuchProviderException extends LockException {

    public NoSuchProviderException(String message) {
        super(message);
    }

    public NoSuchProviderException(Class<?> providerClass) {
        super("No LockProvider found for type: " + providerClass.getName());
    }
}