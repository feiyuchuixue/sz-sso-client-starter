package com.sz.sso.client;

import cn.dev33.satoken.stp.StpUtil;

/**
 * SSO Client 工具类.
 * <p>
 * 提供从当前登录用户的 TokenSession 中读取平台下发状态的便捷方法。
 * </p>
 *
 * @author sz
 * @version 2.0
 * @since 2025/6/23
 */
public class SsoClientUtil {

    private SsoClientUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 判断当前登录用户是否为本 Client 的平台超管.
     * <p>
     * 该值在每次 SSO 登录时由 {@code QUERY_USER_ROLES} 消息从 Server 取回并写入 TokenSession，
     * 表示本次 Session 内平台对该用户超管身份的认定。
     * </p>
     * <p>
     * 若当前用户未登录、或 SSO 查询失败（Server 不可达时降级），则返回 {@code false}。
     * </p>
     *
     * @return {@code true} 表示平台认定为超管；{@code false} 表示非超管或无法确定
     */
    public static boolean isSuperAdmin() {
        if (!StpUtil.isLogin()) {
            return false;
        }
        Object val = StpUtil.getTokenSession().get(SsoCoreConstant.SESSION_KEY_IS_SUPER_ADMIN);
        return Boolean.TRUE.equals(val);
    }

}
