package com.sz.sso.client;

import cn.dev33.satoken.sso.message.SaSsoMessage;

/**
 * SSO 用户 ID 映射服务接口（SPI）.
 * <p>
 * 业务方需实现此接口，完成 SSO Server 用户 ID 与本地用户 ID 之间的转换，
 * 以及处理 SSO Server 推送的用户注册消息。
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 */
public interface SsoUserMappingService {

    /**
     * 将本地用户 ID 转换为 SSO Server 用户 ID（centerId）.
     *
     * @param clientUserId 本地用户 ID
     * @return SSO Server 用户 ID
     */
    Object toServerUserId(Object clientUserId);

    /**
     * 将 SSO Server 用户 ID（centerId）转换为本地用户 ID.
     * <p>
     * 如果本地不存在该用户，实现类应负责创建（降级机制）。
     * </p>
     *
     * @param serverUserId SSO Server 用户 ID
     * @return 本地用户 ID
     */
    Object toClientUserId(Object serverUserId);

    /**
     * 处理 SSO Server 推送的用户注册消息.
     * <p>
     * 当 SSO Server 有新用户注册时，会通过消息推送通知客户端，
     * 实现类应在此方法中完成本地用户的创建。
     * </p>
     *
     * @param message SSO 消息
     */
    void syncSsoRegisterUser(SaSsoMessage message, String client);

}
