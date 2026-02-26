package com.sz.sso.client.service;

import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import com.sz.sso.client.pojo.SsoLoginResult;

/**
 * SSO Client 登录服务接口.
 *
 * @author sz
 * @version 2.0
 * @since 2025/6/20
 */
public interface SsoClientService {

    /**
     * 处理 SSO ticket 登录.
     *
     * @param ctr ticket 验证结果
     * @return 登录结果（框架无关）
     */
    SsoLoginResult login(SaCheckTicketResult ctr);

}
