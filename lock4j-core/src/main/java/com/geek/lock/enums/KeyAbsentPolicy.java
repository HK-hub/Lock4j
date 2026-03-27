package com.geek.lock.enums;

/**
 * Key 缺失策略枚举
 * 
 * <p>定义当 keys 和 keyBuilder 都为空时的处理策略。</p>
 */
public enum KeyAbsentPolicy {

    /**
     * 使用方法全限定名作为 Key
     * 
     * <p>当 keys 和 keyBuilder 都为空时，自动使用方法的全限定名作为锁 Key。
     * 格式为：类全限定名#方法名(参数类型列表)</p>
     */
    USE_METHOD_PATH,

    /**
     * 抛出异常
     * 
     * <p>当 keys 和 keyBuilder 都为空时，抛出 IllegalArgumentException。</p>
     */
    THROW_EXCEPTION
}