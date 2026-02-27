package com.sz.sso.client;

/**
 * SSO 核心常量定义.
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 */
public class SsoCoreConstant {

    private SsoCoreConstant() {
        throw new IllegalStateException("Constant class");
    }

    /**
     * 消息类型：用户注册同步
     */
    public static final String MESSAGE_REGISTER = "REGISTER";

    /**
     * 消息类型：用户信息查询（降级机制）
     */
    public static final String MESSAGE_USER_CHECK = "USER_CHECK";

    /**
     * 消息类型：查询用户在指定 Client 的角色（是否超管）
     */
    public static final String MESSAGE_QUERY_USER_ROLES = "QUERY_USER_ROLES";

    /**
     * TokenSession key：存储平台下发的用户上下文
     */
    public static final String SESSION_KEY_SSO_CONTEXT = "ssoContext";

}
