package com.sz.ssoclient.internal.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Browser HTTP 边界不依赖宿主全局 Jackson 宽松策略的基础配置。 */
@Configuration(proxyBeanMethods = false)
public class SsoWebConfiguration {

    @Bean
    SsoStrictJsonGuard ssoStrictJsonGuard() {
        return new SsoStrictJsonGuard(new ObjectMapper().findAndRegisterModules());
    }

    @Bean
    SsoClientWebExceptionHandler ssoClientWebExceptionHandler() {
        return new SsoClientWebExceptionHandler();
    }
}
