package com.geek.lock.redis.provider;

import com.geek.lock.core.AbstractLockProvider;
import com.geek.lock.core.LockKey;
import com.geek.lock.enums.LockType;
import com.geek.lock.event.LockEventPublisher;
import com.geek.lock.model.LockOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class RedisTemplateLockProvider extends AbstractLockProvider {

    private static final String LOCK_PREFIX = "lock4j:";
    private static final long DEFAULT_LEASE_TIME = 30000L;
    private static final long DEFAULT_WAIT_TIME = 3000L;

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

    private static final String FAIR_LOCK_SCRIPT = """
            local current_time = redis.call('time')[1] * 1000 + redis.call('time')[2] / 1000;
            local lock_key = KEYS[1];
            local queue_key = KEYS[2];
            local lease_time = tonumber(ARGV[1]);
            local requester = ARGV[2];
            local wait_time = tonumber(ARGV[3]);
            
            redis.call('zadd', queue_key, current_time, requester);
            
            local first = redis.call('zrange', queue_key, 0, 0, 'WITHSCORES');
            if first[1] == requester then
                local oldest = tonumber(first[2]);
                if current_time - oldest > wait_time then
                    redis.call('zrem', queue_key, requester);
                    return -1;
                end;
                redis.call('hset', lock_key, requester, current_time + lease_time);
                redis.call('pexpire', lock_key, lease_time);
                return nil;
            end;
            
            local expire_time = redis.call('hget', lock_key, requester);
            if expire_time and tonumber(expire_time) > current_time then
                return nil;
            end;
            
            return redis.call('zrank', queue_key, requester);
            """;

    private static final String FAIR_UNLOCK_SCRIPT = """
            local lock_key = KEYS[1];
            local queue_key = KEYS[2];
            local requester = ARGV[1];
            
            redis.call('zrem', queue_key, requester);
            redis.call('hdel', lock_key, requester);
            
            local first = redis.call('zrange', queue_key, 0, 0);
            if #first > 0 then
                local current_time = redis.call('time')[1] * 1000 + redis.call('time')[2] / 1000;
                redis.call('hset', lock_key, first[1], current_time + tonumber(ARGV[2]));
            else
                redis.call('del', lock_key);
            end;
            return 1;
            """;

    private static final String READ_LOCK_SCRIPT = """
            local lock_key = KEYS[1];
            local read_count_key = KEYS[2];
            local lease_time = tonumber(ARGV[1]);
            local requester = ARGV[2];
            
            local write_owner = redis.call('hget', lock_key, 'write_owner');
            if write_owner then
                return redis.call('pttl', lock_key);
            end;
            
            local read_count = redis.call('hincrby', read_count_key, requester, 1);
            redis.call('hset', lock_key, 'read:' .. requester, requester);
            redis.call('pexpire', lock_key, lease_time);
            redis.call('pexpire', read_count_key, lease_time);
            return nil;
            """;

    private static final String READ_UNLOCK_SCRIPT = """
            local lock_key = KEYS[1];
            local read_count_key = KEYS[2];
            local requester = ARGV[1];
            
            local count = redis.call('hincrby', read_count_key, requester, -1);
            if count <= 0 then
                redis.call('hdel', read_count_key, requester);
                redis.call('hdel', lock_key, 'read:' .. requester);
            end;
            
            local has_writes = redis.call('hexists', lock_key, 'write_owner');
            local has_reads = redis.call('hlen', read_count_key);
            if not has_writes and has_reads == 0 then
                redis.call('del', lock_key);
                redis.call('del', read_count_key);
            end;
            return 1;
            """;

    private static final String WRITE_LOCK_SCRIPT = """
            local lock_key = KEYS[1];
            local read_count_key = KEYS[2];
            local lease_time = tonumber(ARGV[1]);
            local requester = ARGV[2];
            
            local write_owner = redis.call('hget', lock_key, 'write_owner');
            if write_owner and write_owner ~= requester then
                return redis.call('pttl', lock_key);
            end;
            
            local read_count = redis.call('hlen', read_count_key);
            if read_count > 0 then
                local is_reader = redis.call('hexists', read_count_key, requester);
                if not is_reader then
                    return -1;
                end;
            end;
            
            redis.call('hset', lock_key, 'write_owner', requester);
            redis.call('pexpire', lock_key, lease_time);
            return nil;
            """;

    private static final String WRITE_UNLOCK_SCRIPT = """
            local lock_key = KEYS[1];
            local read_count_key = KEYS[2];
            local requester = ARGV[1];
            
            redis.call('hdel', lock_key, 'write_owner');
            
            local has_reads = redis.call('hlen', read_count_key);
            if has_reads == 0 then
                redis.call('del', lock_key);
                redis.call('del', read_count_key);
            end;
            return 1;
            """;

    private final StringRedisTemplate redisTemplate;
    private final String nodeId;
    private final ScheduledExecutorService watchdogScheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> watchdogTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> leaseTimes = new ConcurrentHashMap<>();

    private final DefaultRedisScript<Long> lockScript;
    private final DefaultRedisScript<Long> unlockScript;
    private final DefaultRedisScript<Long> renewScript;
    private final DefaultRedisScript<Long> fairLockScript;
    private final DefaultRedisScript<Long> fairUnlockScript;
    private final DefaultRedisScript<Long> readLockScript;
    private final DefaultRedisScript<Long> readUnlockScript;
    private final DefaultRedisScript<Long> writeLockScript;
    private final DefaultRedisScript<Long> writeUnlockScript;

    public RedisTemplateLockProvider(StringRedisTemplate redisTemplate, LockEventPublisher eventPublisher) {
        super(eventPublisher);
        this.redisTemplate = redisTemplate;
        this.nodeId = UUID.randomUUID().toString().replace("-", "");
        
        this.watchdogScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "lock4j-redis-watchdog");
            t.setDaemon(true);
            return t;
        });

        this.lockScript = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        this.renewScript = new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);
        this.fairLockScript = new DefaultRedisScript<>(FAIR_LOCK_SCRIPT, Long.class);
        this.fairUnlockScript = new DefaultRedisScript<>(FAIR_UNLOCK_SCRIPT, Long.class);
        this.readLockScript = new DefaultRedisScript<>(READ_LOCK_SCRIPT, Long.class);
        this.readUnlockScript = new DefaultRedisScript<>(READ_UNLOCK_SCRIPT, Long.class);
        this.writeLockScript = new DefaultRedisScript<>(WRITE_LOCK_SCRIPT, Long.class);
        this.writeUnlockScript = new DefaultRedisScript<>(WRITE_UNLOCK_SCRIPT, Long.class);
    }

    @Override
    protected boolean doTryLock(LockKey lockKey, LockOptions options) throws Exception {
        LockType lockType = lockKey.getLockType();
        
        return switch (lockType) {
            case FAIR -> tryFairLock(lockKey, options);
            case READ -> tryReadLock(lockKey, options);
            case WRITE -> tryWriteLock(lockKey, options);
            default -> tryReentrantLock(lockKey, options);
        };
    }

    private boolean tryReentrantLock(LockKey lockKey, LockOptions options) {
        String key = LOCK_PREFIX + lockKey.getKey();
        String lockName = getLockName(Thread.currentThread().getId());
        long leaseTime = options.isEnableWatchdog() ? DEFAULT_LEASE_TIME : convertToMillis(options.getLeaseTime(), options.getTimeUnit());
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());

        long remainingTime = waitTime;
        long currentTime = System.currentTimeMillis();

        Long ttl = tryAcquire(key, leaseTime, lockName);
        if (ttl == null) {
            if (options.isEnableWatchdog()) {
                scheduleExpirationRenewal(key, lockName, leaseTime);
            } else {
                leaseTimes.put(key, leaseTime);
            }
            return true;
        }

        remainingTime -= System.currentTimeMillis() - currentTime;
        if (remainingTime <= 0) {
            return false;
        }

        while (true) {
            currentTime = System.currentTimeMillis();
            ttl = tryAcquire(key, leaseTime, lockName);
            if (ttl == null) {
                if (options.isEnableWatchdog()) {
                    scheduleExpirationRenewal(key, lockName, leaseTime);
                } else {
                    leaseTimes.put(key, leaseTime);
                }
                return true;
            }

            remainingTime -= System.currentTimeMillis() - currentTime;
            if (remainingTime <= 0) {
                return false;
            }

            currentTime = System.currentTimeMillis();
            long sleepTime = Math.min(ttl, remainingTime);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            remainingTime -= System.currentTimeMillis() - currentTime;
            if (remainingTime <= 0) {
                return false;
            }
        }
    }

    private boolean tryFairLock(LockKey lockKey, LockOptions options) throws Exception {
        String lockKeyStr = LOCK_PREFIX + lockKey.getKey();
        String queueKey = lockKeyStr + ":queue";
        String requester = getLockName(Thread.currentThread().getId());
        long leaseTime = options.isEnableWatchdog() ? DEFAULT_LEASE_TIME : convertToMillis(options.getLeaseTime(), options.getTimeUnit());
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());

        Long result = redisTemplate.execute(
                fairLockScript,
                Arrays.asList(lockKeyStr, queueKey),
                String.valueOf(leaseTime),
                requester,
                String.valueOf(waitTime)
        );

        if (result == null) {
            if (options.isEnableWatchdog()) {
                scheduleExpirationRenewal(lockKeyStr, requester, leaseTime);
            } else {
                leaseTimes.put(lockKeyStr, leaseTime);
            }
            return true;
        }

        if (result < 0) {
            redisTemplate.opsForZSet().remove(queueKey, requester);
            return false;
        }

        try {
            Thread.sleep(Math.min(result * 50, waitTime));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private boolean tryReadLock(LockKey lockKey, LockOptions options) throws Exception {
        String lockKeyStr = LOCK_PREFIX + lockKey.getKey();
        String readCountKey = lockKeyStr + ":read_count";
        String requester = getLockName(Thread.currentThread().getId());
        long leaseTime = options.isEnableWatchdog() ? DEFAULT_LEASE_TIME : convertToMillis(options.getLeaseTime(), options.getTimeUnit());
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());

        long remainingTime = waitTime;
        while (remainingTime > 0) {
            Long result = redisTemplate.execute(
                    readLockScript,
                    Arrays.asList(lockKeyStr, readCountKey),
                    String.valueOf(leaseTime),
                    requester
            );

            if (result == null) {
                if (options.isEnableWatchdog()) {
                    scheduleExpirationRenewal(lockKeyStr, requester, leaseTime);
                }
                return true;
            }

            long sleepTime = Math.min(result, remainingTime);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            remainingTime -= sleepTime;
        }

        return false;
    }

    private boolean tryWriteLock(LockKey lockKey, LockOptions options) throws Exception {
        String lockKeyStr = LOCK_PREFIX + lockKey.getKey();
        String readCountKey = lockKeyStr + ":read_count";
        String requester = getLockName(Thread.currentThread().getId());
        long leaseTime = options.isEnableWatchdog() ? DEFAULT_LEASE_TIME : convertToMillis(options.getLeaseTime(), options.getTimeUnit());
        long waitTime = convertToMillis(options.getWaitTime(), options.getTimeUnit());

        long remainingTime = waitTime;
        while (remainingTime > 0) {
            Long result = redisTemplate.execute(
                    writeLockScript,
                    Arrays.asList(lockKeyStr, readCountKey),
                    String.valueOf(leaseTime),
                    requester
            );

            if (result == null) {
                if (options.isEnableWatchdog()) {
                    scheduleExpirationRenewal(lockKeyStr, requester, leaseTime);
                }
                return true;
            }

            if (result < 0) {
                return false;
            }

            long sleepTime = Math.min(result, remainingTime);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            remainingTime -= sleepTime;
        }

        return false;
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
        LockType lockType = lockKey.getLockType();
        String key = LOCK_PREFIX + lockKey.getKey();
        String lockName = getLockName(Thread.currentThread().getId());

        cancelExpirationRenewal(key, lockName);

        long leaseTime = leaseTimes.getOrDefault(key, DEFAULT_LEASE_TIME);

        switch (lockType) {
            case FAIR -> unlockFair(key, lockName, leaseTime);
            case READ -> unlockRead(key, lockName);
            case WRITE -> unlockWrite(key, lockName);
            default -> unlockReentrant(key, lockName);
        }
    }

    private void unlockReentrant(String key, String lockName) {
        Long leaseTime = leaseTimes.getOrDefault(key, DEFAULT_LEASE_TIME);
        
        Long result = redisTemplate.execute(
                unlockScript,
                Collections.singletonList(key),
                String.valueOf(leaseTime),
                lockName
        );

        if (result == null) {
            throw new IllegalStateException("Attempt to unlock lock not held by current thread: " + key);
        }
        
        leaseTimes.remove(key);
    }

    private void unlockFair(String key, String requester, long leaseTime) {
        String queueKey = key + ":queue";
        redisTemplate.execute(
                fairUnlockScript,
                Arrays.asList(key, queueKey),
                requester,
                String.valueOf(leaseTime)
        );
        cancelExpirationRenewal(key, requester);
    }

    private void unlockRead(String key, String requester) {
        String readCountKey = key + ":read_count";
        redisTemplate.execute(
                readUnlockScript,
                Arrays.asList(key, readCountKey),
                requester
        );
        cancelExpirationRenewal(key, requester);
    }

    private void unlockWrite(String key, String requester) {
        String readCountKey = key + ":read_count";
        redisTemplate.execute(
                writeUnlockScript,
                Arrays.asList(key, readCountKey),
                requester
        );
        cancelExpirationRenewal(key, requester);
    }

    @Override
    public boolean supports(LockType lockType) {
        return lockType == LockType.REENTRANT
                || lockType == LockType.FAIR
                || lockType == LockType.READ
                || lockType == LockType.WRITE;
    }

    private String getLockName(long threadId) {
        return nodeId + ":" + threadId;
    }

    private void scheduleExpirationRenewal(String key, String lockName, long leaseTime) {
        String entryKey = key + ":" + lockName;
        
        if (watchdogTasks.containsKey(entryKey)) {
            return;
        }

        long renewalInterval = leaseTime / 3;
        ScheduledFuture<?> future = watchdogScheduler.scheduleAtFixedRate(() -> {
            try {
                Long result = redisTemplate.execute(
                        renewScript,
                        Collections.singletonList(key),
                        String.valueOf(leaseTime),
                        lockName
                );
                if (result == null || result == 0) {
                    log.warn("Failed to renew lock expiration: {}", key);
                    cancelExpirationRenewal(key, lockName);
                }
            } catch (Exception e) {
                log.error("Error renewing lock expiration: {}", key, e);
                cancelExpirationRenewal(key, lockName);
            }
        }, renewalInterval, renewalInterval, TimeUnit.MILLISECONDS);

        watchdogTasks.put(entryKey, future);
    }

    private void cancelExpirationRenewal(String key, String lockName) {
        String entryKey = key + ":" + lockName;
        ScheduledFuture<?> future = watchdogTasks.remove(entryKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void shutdown() {
        watchdogScheduler.shutdown();
        try {
            if (!watchdogScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                watchdogScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            watchdogScheduler.shutdownNow();
        }
    }
}