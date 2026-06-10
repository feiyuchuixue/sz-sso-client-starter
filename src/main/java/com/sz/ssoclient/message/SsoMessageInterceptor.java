package com.sz.ssoclient.message;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;

/**
 * SSO 消息拦截器.
 */
public interface SsoMessageInterceptor {

    default void preHandle(SaSsoTemplate template, SaSsoMessage message) {
    }

    default void postHandle(SaSsoTemplate template, SaSsoMessage message, SaResult result) {
    }

    default void afterThrowing(SaSsoTemplate template, SaSsoMessage message, Exception exception) {
    }
}

