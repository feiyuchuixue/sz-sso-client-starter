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

}
