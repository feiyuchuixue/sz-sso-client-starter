package com.sz.ssoclient.message;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;

/**
 * SSO Server 推送消息处理器 SPI.
 */
public interface SsoServerMessageHandler {

    String messageType();

    SaResult handle(SaSsoTemplate template, SaSsoMessage message);
}

