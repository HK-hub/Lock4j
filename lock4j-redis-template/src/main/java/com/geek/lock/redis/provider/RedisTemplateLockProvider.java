package com.geek.lock.redis.provider;

import com.geek.lock.core.AbstractLockProvider;
import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisTemplateLockProvider extends AbstractLockProvider {

    private static final String LOCK_PREFIX = "lock4j:";
    private static final long LOCK_WATCHDOG_TIMEOUT = 30000L;

    private static final String LOCK_SCRIPT = """
            if (redis.call('exists', KEYS[1]) == 0) then
                redis.call('hincrby', KEYS[1], ARGV[2], 1);
                redis.call('pexpire', KEYS[1], ARGV[1]);
                return nil;
            end;
            if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
                redis.call('hincrby', KEYS[1], ARGV[2], 1);
                redis.call('pexpire', KEYS[1], ARGV[1]);
                return nil;
            end;
            return redis.call('pttl', KEYS[1]);
            """;

    private static final String UNLOCK_SCRIPT = """
            if (redis.call('hexists', KEYS[1], ARGV[2]) == 0) then
                return nil;
            end;
            local counter = redis.call('hincrby', KEYS[1], ARGV[2], -1);
            if (counter > 0) then
                redis.call('pexpire', KEYS[1], ARGV[1]);
                return 0;
            else
                redis.call('del', KEYS[1]);
                return 1;
            end;
            """;

    private static final String RENEW_SCRIPT = """
            if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
                redis.call('pexpire', KEYS[1], ARGV[1]);
                return 1;
            end;
            return 0;
            """;

    private final StringRedisTemplate redisTemplate;
    private final String id;
    private final ConcurrentHashMap<String, ScheduledExecutorService> expirationRenewalMap = new ConcurrentHashMap<>();

    private final DefaultRedisScript<Long> lockScript;
    private final DefaultRedisScript<Long> unlockScript;
    private final DefaultRedisScript<Long> renewScript;

    public RedisTemplateLockProvider(StringRedisTemplate redisTemplate, LockEventPublisher eventPublisher) {
        super(eventPublisher);
        this.redisTemplate = redisTemplate;
        this.id = UUID.randomUUID().toString().replace("-", "");

        this.lockScript = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        this.renewScript = new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        String key = LOCK_PREFIX + lockKey.getKey();
        String lockName = getLockName(Thread.currentThread().getId());
        long leaseTime = options.isEnableWatchdog() ? LOCK_WATCHDOG_TIMEOUT : convertToMillis(options.getLeaseTime(), options.getTimeUnit());
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());

        long time = waitTime;
        long current = System.currentTimeMillis();

        Long ttl = tryAcquire(key, leaseTime, lockName);
        if (ttl == null) {
            if (options.isEnableWatchdog()) {
                scheduleExpirationRenewal(lockKey.getKey(), lockName);
            }
            return true;
        }

        time -= System.currentTimeMillis() - current;
        if (time <= 0) {
            return false;
        }

        while (true) {
            long currentTime = System.currentTimeMillis();
            ttl = tryAcquire(key, leaseTime, lockName);
            if (ttl == null) {
                if (options.isEnableWatchdog()) {
                    scheduleExpirationRenewal(lockKey.getKey(), lockName);
                }
                return true;
            }

            time -= System.currentTimeMillis() - currentTime;
            if (time <= 0) {
                return false;
            }

            currentTime = System.currentTimeMillis();
            long sleepTime = Math.min(ttl, time);
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }

            time -= System.currentTimeMillis() - currentTime;
            if (time <= 0) {
                return false;
            }
        }
    }

    private Long tryAcquire(String key, long leaseTime, String lockName) {
        return redisTemplate.execute(
                lockScript,
                Collections.singletonList(key),
                String.valueOf(leaseTime),
                lockName
        );
    }

    @Override
    protected void doUnlock(LockKey lockKey) {
        String key = LOCK_PREFIX + lockKey.getKey();
        String lockName = getLockName(Thread.currentThread().getId());

        cancelExpirationRenewal(lockKey.getKey());

        Long result = redisTemplate.execute(
                unlockScript,
                Collections.singletonList(key),
                String.valueOf(LOCK_WATCHDOG_TIMEOUT),
                lockName
        );

        if (result == null) {
            throw new IllegalStateException("Attempt to unlock lock, not locked by current thread by node id: " + id);
        }
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT;
    }

    private String getLockName(long threadId) {
        return id + ":" + threadId;
    }

    private void scheduleExpirationRenewal(String lockKey, String lockName) {
        String entryKey = lockKey + ":" + lockName;
        
        expirationRenewalMap.computeIfAbsent(entryKey, k -> {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "lock4j-watchdog-" + lockKey);
                thread.setDaemon(true);
                return thread;
            });

            long renewalInterval = LOCK_WATCHDOG_TIMEOUT / 3;
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    String key = LOCK_PREFIX + lockKey;
                    Long result = redisTemplate.execute(
                            renewScript,
                            Collections.singletonList(key),
                            String.valueOf(LOCK_WATCHDOG_TIMEOUT),
                            lockName
                    );
                    if (result == null || result == 0) {
                        log.warn("Failed to renew lock expiration: {}", lockKey);
                        cancelExpirationRenewal(lockKey);
                    }
                } catch (Exception e) {
                    log.error("Error renewing lock expiration: {}", lockKey, e);
                    cancelExpirationRenewal(lockKey);
                }
            }, renewalInterval, renewalInterval, TimeUnit.MILLISECONDS);

            return scheduler;
        });
    }

    private void cancelExpirationRenewal(String lockKey) {
        String threadId = getLockName(Thread.currentThread().getId());
        String entryKey = lockKey + ":" + threadId;
        
        ScheduledExecutorService scheduler = expirationRenewalMap.remove(entryKey);
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}