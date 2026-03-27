package com.geek.lock.event;

import com.geek.lock.enums.LockEventType;

public interface LockEventListener {

    void onEvent(LockEvent event);

    default boolean supports(LockEventType type) {
        return true;
    }

    default int getOrder() {
        return 0;
    }
}