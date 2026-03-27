package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
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
 * 与注解方式不同，编程式锁让用户可以在业务代码中灵活地控制锁的范围。</p>
 *
 * <p>主要功能：
 * <ul>
 *     <li>获取锁：通过 {@link #lock(String, long, long)} 方法获取锁</li>
 *     <li>释放锁：通过 {@link #releaseLock(LockInfo)} 方法释放锁</li>
 *     <li>支持多种参数形式：简单参数、指定Provider、Builder方式</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * &#64;Service
 * public class ProgrammaticService {
 *
 *     &#64;Autowired
 *     private LockTemplate lockTemplate;
 *
 *     public void doSomething(String userId) {
 *         // 查询操作，不上锁
 *         User user = userDao.findById(userId);
 *
 *         // 获取锁
 *         LockInfo lockInfo = lockTemplate.lock(userId, 30000, 5000);
 *         if (lockInfo == null || !lockInfo.isAcquired()) {
 *             throw new RuntimeException("业务处理中,请稍后再试");
 *         }
 *
 *         try {
 *             // 执行业务逻辑
 *             processBusiness(user);
 *         } finally {
 *             // 释放锁
 *             lockTemplate.releaseLock(lockInfo);
 *         }
 *     }
 *
 *     // 使用指定Provider
 *     public void doSomethingWithProvider(String userId) {
 *         LockInfo lockInfo = lockTemplate.lock(userId, 30000, 5000,
 *             RedissonLockProvider.class);
 *         // ...
 *     }
 *
 *     // 使用Builder方式
 *     public void doSomethingWithBuilder(String userId) {
 *         LockInfo lockInfo = lockTemplate.lock(LockRequest.builder()
 *             .key(userId)
 *             .leaseTime(30000)
 *             .waitTime(5000)
 *             .lockType(LockType.FAIR)
 *             .build());
 *         // ...
 *     }
 * }
 * </pre>
 *
 * <p>注意事项：
 * <ul>
 *     <li>务必在 finally 块中释放锁，避免锁泄漏</li>
 *     <li>获取锁失败时返回的 LockInfo 的 isAcquired() 为 false</li>
 *     <li>释放锁时会自动检查锁是否可释放，无需手动判断</li>
 * </ul>
 *
 * @see LockInfo
 * @see LockRequest
 * @see LockProviderFactory
 * @see LockProvider
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

    /**
     * 使用默认Provider获取锁
     *
     * <p>使用配置的 Primary Provider 或第一个可用的 Provider 获取锁。
     * 这是最简单的加锁方式，适用于大多数场景。</p>
     *
     * <p>参数说明：
     * <ul>
     *     <li>key: 锁的唯一标识，建议使用有业务意义的命名</li>
     *     <li>leaseTime: 锁的持有时间（毫秒），默认建议 30秒</li>
     *     <li>waitTime: 获取锁的等待时间（毫秒），默认建议 3秒</li>
     * </ul>
     *
     * @param key       锁的唯一标识
     * @param leaseTime 锁的持有时间（毫秒）
     * @param waitTime  获取锁的等待时间（毫秒）
     * @return LockInfo 对象，包含锁的信息和获取状态；获取失败时返回 isAcquired() 为 false 的对象
     * @throws IllegalArgumentException 如果 key 为 null
     * @throws IllegalStateException    如果没有可用的 LockProvider
     */
    public LockInfo lock(String key, long leaseTime, long waitTime) {
        return lock(key, leaseTime, waitTime, null);
    }

    /**
     * 使用指定Provider获取锁
     *
     * <p>显式指定使用的 LockProvider 类型，适用于需要特定锁实现的场景。
     * 例如在有多个分布式锁实现时，可以指定使用 Redisson 或 Zookeeper。</p>
     *
     * <p>Provider 选择逻辑：
     * <ul>
     *     <li>如果指定了 providerClass，使用指定的 Provider</li>
     *     <li>如果 providerClass 为 null 或 LockProvider.class，使用默认 Provider</li>
     * </ul>
     *
     * @param key          锁的唯一标识
     * @param leaseTime    锁的持有时间（毫秒）
     * @param waitTime     获取锁的等待时间（毫秒）
     * @param providerClass 锁提供者类型，为 null 时使用默认 Provider
     * @return LockInfo 对象，包含锁的信息和获取状态
     * @throws IllegalArgumentException 如果 key 为 null
     * @throws IllegalStateException    如果没有可用的 LockProvider 或指定的 Provider 不存在
     */
    public LockInfo lock(String key, long leaseTime, long waitTime,
                         Class<? extends LockProvider> providerClass) {
        LockRequest request = LockRequest.builder()
                .key(key)
                .leaseTime(leaseTime)
                .waitTime(waitTime)
                .provider(providerClass)
                .build();
        return lock(request);
    }

    /**
     * 使用LockRequest获取锁
     *
     * <p>通过 LockRequest 对象配置加锁参数，支持更复杂的锁配置场景。
     * 可以配置锁类型、时间单位、指定 Provider 等。</p>
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
    public LockInfo lock(LockRequest request) {
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

    /**
     * 释放锁
     *
     * <p>释放通过 {@link #lock} 方法获取的锁。此方法会自动检查锁是否可释放，
     * 无需用户手动判断。释放失败时会记录警告日志，不会抛出异常。</p>
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