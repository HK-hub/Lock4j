package com.geek.lock.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LockDistributedContext {

    private static final ThreadLocal<LockDistributedContext> CURRENT = new ThreadLocal<>();
    
    private final Map<String, Object> context = new ConcurrentHashMap<>();
    private final String traceId;
    private final String spanId;
    private final long startTimeMillis;

    private LockDistributedContext(String traceId, String spanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.startTimeMillis = System.currentTimeMillis();
    }

    public static LockDistributedContext create(String traceId, String spanId) {
        LockDistributedContext ctx = new LockDistributedContext(traceId, spanId);
        CURRENT.set(ctx);
        return ctx;
    }

    public static LockDistributedContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getElapsedTimeMillis() {
        return System.currentTimeMillis() - startTimeMillis;
    }

    public LockDistributedContext put(String key, Object value) {
        context.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) context.get(key);
    }

    public <T> T get(String key, T defaultValue) {
        T value = get(key);
        return value != null ? value : defaultValue;
    }

    public Map<String, Object> getAll() {
        return Map.copyOf(context);
    }

    public LockDistributedContext putAll(Map<String, Object> values) {
        context.putAll(values);
        return this;
    }

    public boolean contains(String key) {
        return context.containsKey(key);
    }

    public LockDistributedContext remove(String key) {
        context.remove(key);
        return this;
    }
}