package com.sz.ssoclient.message;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.Map;

/**
 * SSO 消息发送工具.
 */
@Slf4j
public class SsoMessageSender {

    public SaResult sendToServer(String type, Map<String, Object> params) {
        SaSsoMessage message = buildMessage(type, params);
        log.info("[SSO] 发送消息到 Server: type={}, params={}", type, params);
        SaResult result = SaSsoClientUtil.pushMessageAsSaResult(message);
        log.info("[SSO] Server 响应: type={}, code={}, msg={}", type, result.getCode(), result.getMsg());
        return result;
    }

    @Async
    public void sendToServerAsync(String type, Map<String, Object> params) {
        try {
            SaSsoMessage message = buildMessage(type, params);
            log.info("[SSO] 异步发送消息到 Server: type={}, params={}", type, params);
            SaSsoClientUtil.pushMessage(message);
            log.info("[SSO] 异步消息发送完成: type={}", type);
        } catch (Exception e) {
            log.warn("[SSO] 异步消息发送失败: type={}, error={}", type, e.getMessage(), e);
        }
    }

    private SaSsoMessage buildMessage(String type, Map<String, Object> params) {
        SaSsoMessage message = new SaSsoMessage();
        message.setType(type);
        message.set("clientId", SaSsoClientUtil.getSsoTemplate().getClient());
        if (params != null) {
            params.forEach(message::set);
        }
        return message;
    }
}

