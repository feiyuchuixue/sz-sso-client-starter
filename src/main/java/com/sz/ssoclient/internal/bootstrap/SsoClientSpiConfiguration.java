package com.sz.ssoclient.internal.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 宿主 SPI 数量合同的内部配置边界。 */
@Configuration(proxyBeanMethods = false)
public class SsoClientSpiConfiguration {

    @Bean
    public static SsoClientContractValidator ssoClientContractValidator() {
        return new SsoClientContractValidator();
    }
}
