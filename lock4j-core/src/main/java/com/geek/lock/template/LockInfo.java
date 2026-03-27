package com.geek.lock.template;

import com.geek.lock.core.LockKey;
import com.geek.lock.core.LockProvider;
import lombok.Getter;

import static java.util.Objects.nonNull;

/**
 * 锁信息封装类
 *
 * <p>持有锁的所有必要信息，用于后续释放锁操作。
 * 当用户通过 {@link LockTemplate} 获取锁后，会返回此对象，
 * 用户需要在 finally 块中调用 {@link LockTemplate#releaseLock(LockInfo)} 释放锁。</p>
 *
 * <p>使用示例：
 * <pre>
 * LockInfo lockInfo = lockTemplate.lock("user:123", 30000, 5000);
 * if (lockInfo == null || !lockInfo.isAcquired()) {
 *     throw new RuntimeException("业务处理中,请稍后再试");
 * }
 * try {
 *     // 执行业务逻辑
 * } finally {
 *     lockTemplate.releaseLock(lockInfo);
 * }
 * </pre>
 *
 * @see LockTemplate
 * @see LockKey
 * @see LockProvider
 */
@Getter
public class LockInfo {

    /**
     * 锁的Key对象，包含锁的唯一标识和配置信息
     */
    private final LockKey lockKey;

    /**
     * 锁提供者实例，用于执行实际的解锁操作
     */
    private final LockProvider provider;

    /**
     * 锁是否成功获取的标志
     */
    private final boolean acquired;

    /**
     * 构造函数
     *
     * <p>创建锁信息对象，记录锁的Key、提供者和获取状态。</p>
     *
     * @param lockKey   锁的Key对象
     * @param provider  锁提供者实例
     * @param acquired  锁是否成功获取
     */
    public LockInfo(LockKey lockKey, LockProvider provider, boolean acquired) {
        this.lockKey = lockKey;
        this.provider = provider;
        this.acquired = acquired;
    }

    /**
     * 判断锁是否成功获取
     *
     * <p>当返回 true 时，表示锁已成功获取，业务代码可以在锁的保护下执行。
     * 当返回 false 时，表示获取锁失败，可能是因为锁已被其他线程持有。</p>
     *
     * @return 如果锁成功获取返回 true，否则返回 false
     */
    public boolean isAcquired() {
        return acquired;
    }

    /**
     * 判断锁是否可以释放
     *
     * <p>只有当 lockKey、provider 都不为 null，且 acquired 为 true 时，
     * 锁才能被释放。此方法用于在释放锁前进行安全检查。</p>
     *
     * <p>检查条件：
     * <ul>
     *     <li>lockKey 不为 null</li>
     *     <li>provider 不为 null</li>
     *     <li>acquired 为 true</li>
     * </ul>
     *
     * @return 如果锁可以释放返回 true，否则返回 false
     */
    public boolean isReleasable() {
        return nonNull(lockKey) && nonNull(provider) && acquired;
    }
}