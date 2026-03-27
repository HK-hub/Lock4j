package com.geek.lock.template;

import com.geek.lock.core.LockProvider;
import com.geek.lock.enums.LockType;
import lombok.Builder;
import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * 加锁请求参数封装类
 *
 * <p>用于封装编程式加锁所需的各项参数，支持通过 Builder 模式构建。
 * 当需要更复杂的锁配置时，可以使用此类代替简单的参数传递方式。</p>
 *
 * <p>使用示例：
 * <pre>
 * LockRequest request = LockRequest.builder()
 *     .key("user:123")
 *     .leaseTime(30000)
 *     .waitTime(5000)
 *     .lockType(LockType.FAIR)
 *     .provider(RedissonLockProvider.class)
 *     .build();
 * LockInfo lockInfo = lockTemplate.lock(request);
 * </pre>
 *
 * <p>参数说明：
 * <ul>
 *     <li>key: 锁的唯一标识，必填</li>
 *     <li>leaseTime: 锁的持有时间，默认 30000 毫秒</li>
 *     <li>waitTime: 获取锁的等待时间，默认 3000 毫秒</li>
 *     <li>timeUnit: 时间单位，默认毫秒</li>
 *     <li>lockType: 锁类型，默认可重入锁</li>
 *     <li>provider: 锁提供者类型，可选</li>
 * </ul>
 *
 * @see LockTemplate
 * @see LockType
 * @see LockProvider
 */
@Data
@Builder
public class LockRequest {

    /**
     * 锁的唯一标识Key
     *
     * <p>必填参数，用于标识需要加锁的资源。
     * 建议使用有业务意义的命名，如 "user:123"、"order:456" 等。</p>
     */
    private String key;

    /**
     * 锁的持有时间（租期）
     *
     * <p>锁成功获取后的最大持有时间，超过此时间锁会自动释放。
     * 默认值为 30000 毫秒（30秒）。</p>
     *
     * <p>注意：设置过短可能导致业务未执行完锁就过期，
     * 设置过长可能导致其他线程等待时间过长。</p>
     */
    @Builder.Default
    private long leaseTime = 30000;

    /**
     * 获取锁的等待时间
     *
     * <p>当锁被其他线程持有时，当前线程会等待此时间。
     * 如果在此时间内未能获取锁，则返回获取失败。
     * 默认值为 3000 毫秒（3秒）。</p>
     *
     * <p>设置为 0 表示立即返回，不等待。</p>
     */
    @Builder.Default
    private long waitTime = 3000;

    /**
     * 时间单位
     *
     * <p>leaseTime 和 waitTime 的时间单位。
     * 默认值为 {@link TimeUnit#MILLISECONDS}（毫秒）。</p>
     */
    @Builder.Default
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    /**
     * 锁类型
     *
     * <p>支持的锁类型包括：
     * <ul>
     *     <li>{@link LockType#REENTRANT}: 可重入锁（默认）</li>
     *     <li>{@link LockType#FAIR}: 公平锁</li>
     *     <li>{@link LockType#READ}: 读锁（用于读写锁场景）</li>
     *     <li>{@link LockType#WRITE}: 写锁（用于读写锁场景）</li>
     * </ul>
     */
    @Builder.Default
    private LockType lockType = LockType.REENTRANT;

    /**
     * 锁提供者类型
     *
     * <p>可选参数，用于指定使用哪个 LockProvider。
     * 如果不指定，将使用配置的 Primary Provider 或第一个可用的 Provider。</p>
     *
     * <p>支持的提供者包括：
     * <ul>
     *     <li>RedissonLockProvider: 基于 Redisson 的分布式锁</li>
     *     <li>RedisTemplateLockProvider: 基于 RedisTemplate 的分布式锁</li>
     *     <li>ZookeeperLockProvider: 基于 Zookeeper 的分布式锁</li>
     *     <li>EtcdLockProvider: 基于 Etcd 的分布式锁</li>
     *     <li>LocalLockProvider: 本地锁</li>
     * </ul>
     */
    private Class<? extends LockProvider> provider;
}