package com.sz.ssoclient.spi;

import cn.dev33.satoken.sso.message.SaSsoMessage;

/**
 * SSO 用户 ID 映射 SPI.
 */
public interface SsoUserMappingService {

    Object toServerUserId(Object clientUserId);

    Object toClientUserId(Object serverUserId);

    void syncSsoRegisterUser(SaSsoMessage message, String client);
}

