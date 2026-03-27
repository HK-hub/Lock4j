package com.geek.lock.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultLockEventPublisher implements LockEventPublisher {

    private final List<LockEventListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void publish(LockEvent event) {
        for (LockEventListener listener : listeners) {
            if (listener.supports(event.getType())) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void addListener(LockEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(LockEventListener listener) {
        listeners.remove(listener);
    }
}