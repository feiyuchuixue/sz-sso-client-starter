package com.sz.ssoclient.internal.bootstrap;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransactionCodec;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransactionRepository;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransactionService;
import com.sz.ssoclient.internal.login.transaction.SsoSafeBackValidator;
import com.sz.ssoclient.internal.web.SaTokenSsoClientLocalSessionAccessor;
import com.sz.ssoclient.internal.web.SsoClientBrowserBinding;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssoclient.spi.advanced.SsoClientStateRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

/** 共享状态、登录事务和默认高级 SPI 的内部配置边界。 */
@Configuration(proxyBeanMethods = false)
public class SsoClientInfrastructureConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoClientStateRepository.class)
    public SsoClientStateRepository ssoClientStateRepository(
            ObjectProvider<StringRedisTemplate> redisProvider,
            Environment environment) {
        return ssoClientStateRepository(redisProvider, environment, Clock.systemUTC());
    }

    public SsoClientStateRepository ssoClientStateRepository(
            ObjectProvider<StringRedisTemplate> redisProvider,
            Environment environment,
            Clock clock) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            return new RedisSsoClientStateRepository(redis);
        }
        if (SsoClientUrlSupport.localLike(environment)) {
            return new InMemorySsoClientStateRepository(clock);
        }
        String profiles = environment.getActiveProfiles().length == 0
                ? "<default>"
                : String.join(",", environment.getActiveProfiles());
        throw new SsoClientConfigurationException(
                "当前 profiles=" + profiles
                        + " 未提供 Redis；生产状态必须使用 Spring Data Redis"
                        + " 或唯一自定义共享 SsoClientStateRepository");
    }

    @Bean
    @ConditionalOnMissingBean(SsoClientLocalSessionAccessor.class)
    public SsoClientLocalSessionAccessor ssoClientLocalSessionAccessor() {
        return new SaTokenSsoClientLocalSessionAccessor();
    }

    @Bean
    public SsoLoginTransactionCodec ssoLoginTransactionCodec() {
        return new SsoLoginTransactionCodec();
    }

    @Bean
    public SsoLoginTransactionRepository ssoLoginTransactionRepository(
            SsoClientStateRepository stateRepository,
            SsoLoginTransactionCodec codec,
            SaSsoClientTemplate template) {
        return new SsoLoginTransactionRepository(
                stateRepository,
                codec,
                Clock.systemUTC(),
                SsoClientUrlSupport.clientFlag(template));
    }

    @Bean
    public SsoSafeBackValidator ssoSafeBackValidator(SsoClientProperties properties) {
        return new SsoSafeBackValidator(properties.getAllowedAbsoluteBackUrls());
    }

    @Bean
    public SsoLoginTransactionService ssoLoginTransactionService(
            SsoLoginTransactionRepository repository,
            SsoSafeBackValidator backValidator) {
        return new SsoLoginTransactionService(repository, backValidator, Clock.systemUTC());
    }

    @Bean
    public SsoClientBrowserBinding ssoClientBrowserBinding(
            SsoClientProperties properties,
            SaSsoClientTemplate template,
            Environment environment) {
        SecureRandom random = new SecureRandom();
        boolean secureCookie = SsoClientUrlSupport.secureCookie(
                properties,
                SsoClientUrlSupport.externalOrigin(properties, template),
                environment);
        return new SsoClientBrowserBinding(
                () -> randomToken(random),
                secureCookie);
    }

    private static String randomToken(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
