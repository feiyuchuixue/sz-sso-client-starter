package com.sz.sso.client;

import cn.dev33.satoken.stp.StpUtil;
import com.sz.sso.client.pojo.SsoUserContext;

/**
 * SSO Client 工具类.
 * <p>
 * 提供从当前登录用户的 TokenSession 中读取平台下发上下文的便捷方法。
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/23
 */
public class SsoClientUtil {

    private SsoClientUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 从当前登录用户的 TokenSession 读取平台下发的 {@link SsoUserContext}.
     * <p>
     * 若当前用户未登录、或登录时未触发角色下发流程，则返回 {@code null}。
     * </p>
     *
     * @return SsoUserContext，可能为 null
     */
    public static SsoUserContext getSsoContext() {
        Object val = StpUtil.getTokenSession().get(SsoCoreConstant.SESSION_KEY_SSO_CONTEXT);
        if (val instanceof SsoUserContext ssoUserContext) {
            return ssoUserContext;
        }
        return null;
    }

}
