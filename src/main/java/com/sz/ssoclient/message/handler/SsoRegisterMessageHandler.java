package com.sz.ssoclient.message.handler;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.message.SsoServerMessageHandler;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.SsoMessageTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Server 注册用户同步消息处理器.
 */
@Slf4j
@RequiredArgsConstructor
public class SsoRegisterMessageHandler implements SsoServerMessageHandler {

    private final SsoUserMappingService ssoUserMappingService;

    @Override
    public String messageType() {
        return SsoMessageTypes.REGISTER;
    }

    @Override
    public SaResult handle(SaSsoTemplate template, SaSsoMessage message) {
        Object ssoUserId = message.get("ssoUserId");
        String client = String.valueOf(message.get("client"));
        log.info("[SSO] 收到 REGISTER 消息, ssoUserId={}", ssoUserId);
        ssoUserMappingService.syncSsoRegisterUser(message, client);
        return SaResult.ok();
    }
}

