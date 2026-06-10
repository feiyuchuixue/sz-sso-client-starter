package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.controller.SsoClientController;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoUserMappingService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

/**
 * SSO Client 对外端点自动配置入口.
 */
@AutoConfiguration(after = SsoClientLoginAutoConfiguration.class)
@ConditionalOnClass(SaSsoClientTemplate.class)
@ConditionalOnBean({SsoUserMappingService.class, SsoClientLoginAdapter.class})
@Import(SsoClientController.class)
public class SsoClientAutoConfiguration {
}

