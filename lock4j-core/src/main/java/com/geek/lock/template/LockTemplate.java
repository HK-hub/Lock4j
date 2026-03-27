package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import com.geek.lock.exception.LockFailureException;
import com.geek.lock.factory.LockProviderFactory;
import com.geek.lock.model.LockOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * 编程式锁操作模板类
 *
 * <p>提供手动加锁和解锁的能力，允许用户精确控制锁的获取和释放时机。
 * 参考 JDK {@link java.util.concurrent.locks.Lock} 接口设计，提供两种风格的加锁方法：</p>
 *
 * <p>方法分类：
 * <table border="1">
 *   <tr><th>方法</th><th>获取失败时行为</th><th>适用场景</th></tr>
 *   <tr><td>{@link #lock(String, long, long)}</td><td>抛出 {@link LockFailureException}</td><td>简单场景，失败直接抛异常</td></tr>
 *   <tr><td>{@link #tryLock(String, long, long)}</td><td>返回 isAcquired=false 的 LockInfo</td><td>需要自行处理失败场景</td></tr>
 * </table>
 *
 * <p>使用示例：
 * <pre>
 * &#64;Service
 * public class ProgrammaticService {
 *
 *     &#64;Autowired
 *     private LockTemplate lockTemplate;
 *
 *     // 方式1：lock() - 失败时抛异常
 *     public void doSomethingSimple(String userId) {
 *         LockInfo lockInfo = lockTemplate.lock(userId, 30000, 5000);
 *         try {
 *             processBusiness(userId);
 *         } finally {
 *             lockTemplate.releaseLock(lockInfo);
 *         }
 *     }
 *
 *     // 方式2：tryLock() - 失败时不抛异常，自行处理
 *     public void doSomethingWithFallback(String userId) {
 *         LockInfo lockInfo = lockTemplate.tryLock(userId, 30000, 5000);
 *         if (!lockInfo.isAcquired()) {
 *             // 自行处理获取失败的情况
 *             log.warn("获取锁失败: {}", userId);
 *             return;
 *         }
 *         try {
 *             processBusiness(userId);
 *         } finally {
 *             lockTemplate.releaseLock(lockInfo);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>注意事项：
 * <ul>
 *     <li>务必在 finally 块中释放锁，避免锁泄漏</li>
 *     <li>lock() 方法失败时抛出 LockFailureException，需要捕获或向上抛出</li>
 *     <li>tryLock() 方法失败时返回 isAcquired=false 的 LockInfo，需要自行判断</li>
 *     <li>释放锁时会自动检查锁是否可释放，无需手动判断</li>
 * </ul>
 *
 * @see LockInfo
 * @see LockRequest
 * @see LockProviderFactory
 * @see LockProvider
 * @see LockFailureException
 */
@Slf4j
@RequiredArgsConstructor
public class LockTemplate {

    /**
     * 锁提供者工厂
     *
     * <p>用于获取具体的 LockProvider 实例。
     * 支持按类型获取 Provider，或获取默认的 Primary Provider。</p>
     */
    private final LockProviderFactory providerFactory;

    // ==================== lock() 系列方法 - 获取失败时抛异常 ====================

    /**
     * 获取锁，获取失败时抛出异常
     *
     * <p>使用配置的 Primary Provider 或第一个可用的 Provider 获取锁。
     * 如果在等待时间内未能获取锁，将抛出 {@link LockFailureException}。</p>
     *
     * <p>此方法适用于简单场景，当获取锁失败时直接抛异常，由上层统一处理。
     * 参考 JDK Lock.lock() 的设计思想。</p>
     *
     * @param key       锁的唯一标识
     * @param leaseTime 锁的持有时间（毫秒）
     * @param waitTime  获取锁的等待时间（毫秒）
     * @return LockInfo 对象，包含锁的信息
     * @throws IllegalArgumentException 如果 key 为 null
     * @throws IllegalStateException    如果没有可用的 LockProvider
     * @throws LockFailureException    如果获取锁失败
     */
    public LockInfo lock(String key, long leaseTime, long waitTime) {
        return lock(key, leaseTime, waitTime, null);
    }

    /**
     * 使用指定Provider获取锁，获取失败时抛出异常
     *
     * <p>显式指定使用的 LockProvider 类型获取锁。
     * 如果在等待时间内未能获取锁，将抛出 {@link LockFailureException}。</p>
     *
     * @param key           锁的唯一标识
     * @param leaseTime     锁的持有时间（毫秒）
     * @param waitTime      获取锁的等待时间（毫秒）
     * @param providerClass 锁提供者类型，为 null 时使用默认 Provider
     * @return LockInfo 对象，包含锁的信息
     * @throws IllegalArgumentException 如果 key 为 null
     * @throws IllegalStateException    如果没有可用的 LockProvider
     * @throws LockFailureException    如果获取锁失败
     */
    public LockInfo lock(String key, long leaseTime, long waitTime,
                         Class<? extends LockProvider> providerClass) {
        LockInfo lockInfo = tryLock(key, leaseTime, waitTime, providerClass);
        if (!lockInfo.isAcquired()) {
            throw new LockFailureException("Failed to acquire lock: " + key);
        }
        return lockInfo;
    }

    /**
     * 使用LockRequest获取锁，获取失败时抛出异常
     *
     * <p>通过 LockRequest 对象配置加锁参数，支持更复杂的锁配置场景。
     * 如果获取锁失败，将抛出 {@link LockFailureException}。</p>
     *
     * @param request 加锁请求参数对象
     * @return LockInfo 对象，包含锁的信息
     * @throws IllegalArgumentException 如果 request 为 null 或 key 为 null
     * @throws IllegalStateException    如果没有可用的 LockProvider
     * @throws LockFailureException    如果获取锁失败
     */
    public LockInfo lock(LockRequest request) {
        LockInfo lockInfo = tryLock(request);
        if (!lockInfo.isAcquired()) {
            throw new LockFailureException("Failed to acquire lock: " + request.getKey());
        }
        return lockInfo;
    }

    // ==================== tryLock() 系列方法 - 获取失败时不抛异常 ====================

    /**
     * 尝试获取锁，获取失败时不抛异常
     *
     * <p>使用配置的 Primary Provider 或第一个可用的 Provider 尝试获取锁。
     * 如果在等待时间内未能获取锁，返回 isAcquired=false 的 LockInfo，不抛出异常。</p>
     *
     * <p>此方法适用于需要自行处理获取失败场景的情况。
     * 参考 JDK Lock.tryLock() 的设计思想。</p>
     *
     * @param key       锁的唯一标识
     * @param leaseTime 锁的持有时间（毫秒）
     * @param waitTime  获取锁的等待时间（毫秒）
     * @return LockInfo 对象，包含锁的信息和获取状态
     * @throws IllegalArgumentException 如果 key 为 null
     * @throws IllegalStateException    如果没有可用的 LockProvider
     */
    public LockInfo tryLock(String key, long leaseTime, long waitTime) {
        return tryLock(key, leaseTime, waitTime, null);
    }

    /**
     * 使用指定Provider尝试获取锁，获取失败时不抛异常
     *
     * <p>显式指定使用的 LockProvider 类型尝试获取锁。
     * 如果在等待时间内未能获取锁，返回 isAcquired=false 的 LockInfo，不抛出异常。</p>
     *
     * @param key           锁的唯一标识
     * @param leaseTime     锁的持有时间（毫秒）
     * @param waitTime      获取锁的等待时间（毫秒）
     * @param providerClass 锁提供者类型，为 null 时使用默认 Provider
     * @return LockInfo 对象，包含锁的信息和获取状态
     * @throws IllegalArgumentException 如果 key 为 null
     * @throws IllegalStateException    如果没有可用的 LockProvider
     */
    public LockInfo tryLock(String key, long leaseTime, long waitTime,
                            Class<? extends LockProvider> providerClass) {
        LockRequest request = LockRequest.builder()
                .key(key)
                .leaseTime(leaseTime)
                .waitTime(waitTime)
                .provider(providerClass)
                .build();
        return tryLock(request);
    }

    /**
     * 使用LockRequest尝试获取锁，获取失败时不抛异常
     *
     * <p>通过 LockRequest 对象配置加锁参数，尝试获取锁。
     * 如果获取锁失败，返回 isAcquired=false 的 LockInfo，不抛出异常。</p>
     *
     * <p>执行流程：
     * <ol>
     *     <li>验证请求参数是否有效</li>
     *     <li>解析并获取 LockProvider</li>
     *     <li>构建 LockKey 和 LockOptions</li>
     *     <li>调用 Provider 执行加锁</li>
     *     <li>返回 LockInfo 封装结果</li>
     * </ol>
     *
     * @param request 加锁请求参数对象
     * @return LockInfo 对象，包含锁的信息和获取状态
     * @throws IllegalArgumentException 如果 request 为 null 或 key 为 null
     * @throws IllegalStateException    如果没有可用的 LockProvider
     */
    public LockInfo tryLock(LockRequest request) {
        if (isNull(request) || isNull(request.getKey())) {
            throw new IllegalArgumentException("Lock key cannot be null");
        }

        LockProvider provider = resolveProvider(request.getProvider());
        if (isNull(provider)) {
            throw new IllegalStateException("No LockProvider available");
        }

        LockKey lockKey = buildLockKey(request);
        LockOptions options = buildLockOptions(request);

        boolean acquired = provider.tryLock(lockKey, options);

        return new LockInfo(lockKey, provider, acquired);
    }

    // ==================== 释放锁 ====================

    /**
     * 释放锁
     *
     * <p>释放通过 {@link #lock} 或 {@link #tryLock} 方法获取的锁。
     * 此方法会自动检查锁是否可释放，无需用户手动判断。
     * 释放失败时会记录警告日志，不会抛出异常。</p>
     *
     * <p>安全检查：
     * <ul>
     *     <li>如果 lockInfo 为 null，直接返回</li>
     *     <li>如果锁未成功获取（isAcquired 为 false），直接返回</li>
     *     <li>如果 lockKey 或 provider 为 null，直接返回</li>
     * </ul>
     *
     * <p>使用建议：务必在 finally 块中调用此方法，确保锁一定会被释放。
     * <pre>
     * try {
     *     // 业务逻辑
     * } finally {
     *     lockTemplate.releaseLock(lockInfo);
     * }
     * </pre>
     *
     * @param lockInfo 锁信息对象，包含需要释放的锁信息
     */
    public void releaseLock(LockInfo lockInfo) {
        if (isNull(lockInfo) || !lockInfo.isReleasable()) {
            return;
        }

        try {
            lockInfo.getProvider().unlock(lockInfo.getLockKey());
        } catch (Exception e) {
            log.warn("Failed to release lock: {}", lockInfo.getLockKey().getKey(), e);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 解析LockProvider
     *
     * <p>根据配置解析获取 LockProvider 实例。
     * 解析优先级：指定 Provider > Primary Provider > First Provider。</p>
     *
     * <p>解析逻辑：
     * <ol>
     *     <li>如果指定了具体的 Provider 类型，从工厂获取该类型的 Provider</li>
     *     <li>如果未指定，检查是否有 Primary Provider</li>
     *     <li>如果没有 Primary Provider，获取第一个可用的 Provider</li>
     *     <li>如果没有任何 Provider，返回 null</li>
     * </ol>
     *
     * @param providerClass 锁提供者类型，可能为 null
     * @return LockProvider 实例，如果没有可用的 Provider 则返回 null
     */
    private LockProvider resolveProvider(Class<? extends LockProvider> providerClass) {
        if (nonNull(providerClass) && providerClass != LockProvider.class) {
            return providerFactory.getProvider(providerClass);
        }

        if (providerFactory.hasPrimaryProvider()) {
            return providerFactory.getPrimaryProvider();
        }

        return providerFactory.getFirstProvider();
    }

    /**
     * 构建LockKey对象
     *
     * <p>从 LockRequest 中提取参数，构建 LockKey 对象。
     * LockKey 包含锁的唯一标识和配置信息。</p>
     *
     * @param request 加锁请求参数对象
     * @return LockKey 对象
     */
    private LockKey buildLockKey(LockRequest request) {
        return LockKey.builder()
                .key(request.getKey())
                .lockType(request.getLockType())
                .waitTime(request.getWaitTime())
                .leaseTime(request.getLeaseTime())
                .timeUnit(request.getTimeUnit())
                .build();
    }

    /**
     * 构建LockOptions对象
     *
     * <p>从 LockRequest 中提取时间相关参数，构建 LockOptions 对象。
     * LockOptions 用于传递给 Provider 的加锁方法。</p>
     *
     * @param request 加锁请求参数对象
     * @return LockOptions 对象
     */
    private LockOptions buildLockOptions(LockRequest request) {
        LockOptions options = new LockOptions();
        options.setWaitTime(request.getWaitTime());
        options.setLeaseTime(request.getLeaseTime());
        options.setTimeUnit(request.getTimeUnit());
        return options;
    }
}