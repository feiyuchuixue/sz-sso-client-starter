package com.sz.ssoclient.login.step;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.login.LoginContext;
import com.sz.ssoclient.login.LoginStep;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.SsoMessageTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 查询平台认定的 Client 超管状态.
 */
@Slf4j
@RequiredArgsConstructor
public class QuerySuperAdminStep<U> implements LoginStep<U> {

    private final SsoUserMappingService userMappingService;

    @Override
    public void execute(LoginContext<U> context) {
        try {
            Object centerId = userMappingService.toServerUserId(context.getLocalUserId());
            String clientId = SaSsoClientUtil.getSsoTemplate().getClient();
            SaSsoMessage message = new SaSsoMessage();
            message.setType(SsoMessageTypes.QUERY_USER_ROLES);
            message.set("centerId", centerId);
            message.set("clientId", clientId);
            SaResult result = SaSsoClientUtil.pushMessageAsSaResult(message);
            boolean isSuperAdmin = result != null && result.getCode() == 200 && Boolean.TRUE.equals(result.getData());
            if (result == null || result.getCode() != 200 || result.getData() == null) {
                log.warn("[SSO] 查询超管状态失败，降级为 false: localUserId={}, result={}", context.getLocalUserId(), result);
            }
            context.setSuperAdmin(isSuperAdmin);
        } catch (Exception e) {
            log.warn("[SSO] 查询超管状态异常，降级为 false: localUserId={}, error={}", context.getLocalUserId(), e.getMessage(), e);
            context.setSuperAdmin(false);
        }
    }
}

