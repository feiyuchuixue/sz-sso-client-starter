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
     * 消息类型：Client 通知 Server 同步超管状态变更
     * <p>
     * Client 内部超管角色发生变更（赋予或撤销）时，通过此消息通知 Server
     * 更新 sso_user_client_role 表，确保 Server 作为权威来源保持最新状态。
     * </p>
     */
    public static final String MESSAGE_SYNC_SUPER_ADMIN = "SYNC_SUPER_ADMIN";

    /**
     * 消息类型：Server 通知 Client 同步超管状态变更
     * <p>
     * SSO Server 管理端授予或撤销某用户在指定 Client 的超管身份时，通过此消息通知目标 Client
     * 更新本地用户标签和角色。Client 侧由 starter 内置处理器接收，并委托
     * {@link SsoRoleBindingService#applySuperAdmin(Object, boolean)} 执行业务落库。
     * </p>
     */
    public static final String MESSAGE_SYNC_CLIENT_SUPER_ADMIN = "SYNC_CLIENT_SUPER_ADMIN";

    /**
     * TokenSession key：存储平台认定的超管状态（Boolean）
     * <p>
     * 每次 SSO 登录时由 QUERY_USER_ROLES 响应写入，表示本次 Session 内
     * 平台是否认定当前用户为该 Client 的超管。
     * </p>
     */
    public static final String SESSION_KEY_IS_SUPER_ADMIN = "isSuperAdmin";

}
