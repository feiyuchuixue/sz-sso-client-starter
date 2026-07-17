package com.sz.ssoclient.login;

/**
 * CAP 登录成功后建立 Client 本地会话所需的最小命令.
 *
 * @param centerUserId 中心用户标识，仅用于审计和映射追踪
 * @param localUserId Client 本地用户标识
 * @param deviceId Client 本地会话设备标识
 * @param sessionTimeoutSeconds Client 本地会话超时秒数
 * @param superAdmin Server 在当前 Client 范围内裁决的超管状态
 */
public record SsoLoginCommand(
        Object centerUserId,
        Long localUserId,
        String deviceId,
        long sessionTimeoutSeconds,
        Boolean superAdmin) {

    public SsoLoginCommand {
        if (localUserId == null) {
            throw new IllegalArgumentException("localUserId must not be null");
        }
        if (sessionTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("sessionTimeoutSeconds must be positive");
        }
    }
}
