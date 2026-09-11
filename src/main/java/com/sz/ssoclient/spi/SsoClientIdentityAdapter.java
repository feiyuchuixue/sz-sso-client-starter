package com.sz.ssoclient.spi;

import com.sz.ssocore.SsoUserMeta;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 宿主身份、SSO mapping 与幂等 JIT 的必需适配器。 */
public interface SsoClientIdentityAdapter {

    /** 按不透明本地用户 ID 查询中心 SSO 用户 ID。 */
    Optional<Long> findSsoUserId(String localUserId);

    /** 批量把不透明本地用户 ID 转为中心 SSO 用户 ID，避免快照查询产生 N+1。 */
    Map<String, Long> findSsoUserIds(Collection<String> localUserIds);

    /** 按中心 SSO 用户 ID 只读查询不透明本地用户 ID。 */
    Optional<String> findLocalUserId(long ssoUserId);

    /** 使用完整中立用户快照幂等匹配、绑定或创建用户，成功时返回不透明本地用户 ID。 */
    String resolveOrProvision(SsoUserMeta userMeta);

    /** 终结该 mapping 的一次性默认访问初始化标记；失败时抛异常。 */
    void completeDefaultAccessInitialization(String localUserId);
}
