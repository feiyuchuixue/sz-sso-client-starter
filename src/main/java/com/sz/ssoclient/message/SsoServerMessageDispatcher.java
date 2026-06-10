package com.sz.ssoclient.message;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSO Server 消息分发器，统一承载 type -> handler 映射与拦截器链.
 */
@Slf4j
public class SsoServerMessageDispatcher {

    private final Map<String, SsoServerMessageHandler> handlers;

    private final List<SsoMessageInterceptor> interceptors;

    public SsoServerMessageDispatcher(List<SsoServerMessageHandler> handlers,
                                      List<SsoMessageInterceptor> interceptors) {
        Map<String, SsoServerMessageHandler> handlerMap = new LinkedHashMap<>();
        for (SsoServerMessageHandler handler : handlers) {
            SsoServerMessageHandler previous = handlerMap.putIfAbsent(handler.messageType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate SSO message handler type: " + handler.messageType());
            }
        }
        this.handlers = Collections.unmodifiableMap(handlerMap);
        this.interceptors = List.copyOf(interceptors);
    }

    public Map<String, SsoServerMessageHandler> handlers() {
        return handlers;
    }

    public SaResult dispatch(SaSsoTemplate template, SaSsoMessage message) {
        SsoServerMessageHandler handler = handlers.get(message.getType());
        if (handler == null) {
            log.warn("[SSO] 未找到消息处理器: type={}", message.getType());
            return SaResult.error("unsupported message type: " + message.getType());
        }
        try {
            for (SsoMessageInterceptor interceptor : interceptors) {
                interceptor.preHandle(template, message);
            }
            SaResult result = handler.handle(template, message);
            for (SsoMessageInterceptor interceptor : interceptors) {
                interceptor.postHandle(template, message, result);
            }
            return result;
        } catch (Exception e) {
            for (SsoMessageInterceptor interceptor : interceptors) {
                interceptor.afterThrowing(template, message, e);
            }
            throw e;
        }
    }
}

