package com.sz.ssoclient.spi.advanced;

import java.time.Duration;

/** 登录事务和幂等安全状态使用的高级原子存储 SPI。 */
public interface SsoClientStateRepository {

    /** 读取当前值。 */
    String get(String key);

    /** 键不存在时按 TTL 原子写入。 */
    boolean putIfAbsent(String key, String value, Duration ttl);

    /** 当前值匹配时按 TTL 原子替换。 */
    boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl);

    /** 当前值匹配时原子删除。 */
    boolean compareAndDelete(String key, String expectedValue);

    /** 是否为多实例共享存储。 */
    boolean shared();

    /** 返回不含凭据的存储说明，供启动诊断使用。 */
    String description();
}
