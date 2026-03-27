package com.geek.lock.event;

import java.util.List;

public interface LockEventPublisher {

    void publish(LockEvent event);

    void addListener(LockEventListener listener);

    void removeListener(LockEventListener listener);
}