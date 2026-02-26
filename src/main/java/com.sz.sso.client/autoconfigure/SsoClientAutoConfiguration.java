package com.sz.sso.client.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.sso.client.DefaultSsoSessionCreator;
import com.sz.sso.client.SsoLoginHandler;
import com.sz.sso.client.SsoSessionCreator;
import com.sz.sso.client.SsoUserMappingService;
import com.sz.sso.client.controller.SsoClientController;
import com.sz.sso.client.service.SsoClientService;
import com.sz.sso.client.service.impl.SsoClientServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static com.sz.sso.client.SsoCoreConstant.MESSAGE_REGISTER;

/**
 * SSO Client 自动配置.
 * <p>
 * 当 classpath 中存在 {@link SaSsoClientTemplate} 且业务方提供了
 * {@link SsoUserMappingService} 和 {@link SsoLoginHandler} 的实现 Bean 时自动激活。
 * {@link SsoSessionCreator} 有内置默认实现 {@link DefaultSsoSessionCreator}，
 * 业务方可注册自己的 Bean 覆盖默认行为。
 * </p>
 * <p>
 * 自动完成以下配置：
 * <ul>
 *   <li>注册 {@link SaSsoClientTemplate} 的 centerId/loginId 转换策略</li>
 *   <li>注册消息处理器（REGISTER 消息 → 用户同步）</li>
 *   <li>注册 {@link SsoClientService} Bean</li>
 *   <li>导入 {@link SsoClientController} 提供标准 SSO 端点</li>
 * </ul>
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SaSsoClientTemplate.class)
@ConditionalOnBean({SsoUserMappingService.class, SsoLoginHandler.class})
@Import(SsoClientController.class)
public class SsoClientAutoConfiguration {

    /**
     * 默认 SsoSessionCreator：基于 Sa-Token 原生 API 建立会话.
     * <p>
     * 若业务方已注册自己的 {@link SsoSessionCreator} Bean，本 Bean 不会创建。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(SsoSessionCreator.class)
    public SsoSessionCreator<?> defaultSsoSessionCreator() {
        log.info("SSO Client 自动配置: 注册默认 DefaultSsoSessionCreator（基于 Sa-Token 原生 API）");
        return new DefaultSsoSessionCreator();
    }

    /**
     * 配置 SaSsoClientTemplate 的策略函数和消息处理器.
     */
    @Autowired
    public void configSsoTemplate(SaSsoClientTemplate ssoClientTemplate,
                                   SsoUserMappingService ssoUserMappingService) {
        // 将 centerId 转换为 loginId 的函数
        ssoClientTemplate.strategy.convertCenterIdToLoginId = (centerId) -> {
            log.info("convertCenterIdToLoginId: 开始转换, centerId={}", centerId);
            Object clientUserId = ssoUserMappingService.toClientUserId(centerId);
            log.info("convertCenterIdToLoginId: 转换完成, centerId={}, clientUserId={}", centerId, clientUserId);
            return clientUserId;
        };
        // 将 loginId 转换为 centerId 的函数
        ssoClientTemplate.strategy.convertLoginIdToCenterId = (loginId) -> {
            log.debug("convertLoginIdToCenterId: loginId={}", loginId);
            return ssoUserMappingService.toServerUserId(loginId);
        };

        // 注册消息处理器
        ssoClientTemplate.messageHolder.addHandle(MESSAGE_REGISTER, (ssoTemplate, message) -> {
            Object ssoUserId = message.get("ssoUserId");
            log.info("MESSAGE_REGISTER: 收到注册消息, ssoUserId={}", ssoUserId);
            ssoUserMappingService.syncSsoRegisterUser(message);
            return SaResult.ok();
        });

        log.info("SSO Client 自动配置完成: 策略函数和消息处理器已注册");
    }

    /**
     * 注册 SsoClientService Bean.
     */
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SsoClientService ssoClientService(SsoLoginHandler<?> ssoLoginHandler,
                                              SsoSessionCreator<?> ssoSessionCreator) {
        log.info("SSO Client 自动配置: 注册 SsoClientService");
        return new SsoClientServiceImpl(ssoLoginHandler, ssoSessionCreator);
    }

}
