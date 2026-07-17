package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import com.sz.ssoclient.clientaccess.http.ClientAccessHttpTransport;
import com.sz.ssoclient.clientaccess.http.ClientAccessRegistration;
import com.sz.ssoclient.clientaccess.http.ClientAccessRegistrationProvider;
import com.sz.ssoclient.clientaccess.http.ClientAccessV1Client;
import com.sz.ssoclient.clientaccess.http.JdkClientAccessHttpTransport;
import com.sz.ssoclient.clientaccess.inbound.ClientAccessInboundService;
import com.sz.ssoclient.clientaccess.inbound.ClientAccessInboundVerifier;
import com.sz.ssoclient.clientaccess.inbound.ClientInboundEventStore;
import com.sz.ssoclient.clientaccess.inbound.RepositoryClientAccessNonceStore;
import com.sz.ssoclient.clientaccess.inbound.RepositoryClientInboundEventStore;
import com.sz.ssoclient.clientaccess.json.ClientAccessJsonCodec;
import com.sz.ssoclient.clientaccess.state.ClientAccessStateRepository;
import com.sz.ssoclient.clientaccess.state.ClientAccessStateSafetyVerifier;
import com.sz.ssoclient.clientaccess.state.InMemoryClientAccessStateRepository;
import com.sz.ssoclient.clientaccess.state.RedisClientAccessStateRepository;
import com.sz.ssoclient.clientaccess.web.ClientAccessWebService;
import com.sz.ssoclient.clientaccess.web.ClientBrowserBinding;
import com.sz.ssoclient.clientaccess.web.ClientLocalSessionAccessor;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionService;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionStore;
import com.sz.ssoclient.clientaccess.web.RepositoryClientLoginTransactionStore;
import com.sz.ssoclient.clientaccess.web.SaTokenClientLocalSessionAccessor;
import com.sz.ssoclient.controller.ClientAccessInboundController;
import com.sz.ssoclient.controller.ClientAccessV1Controller;
import com.sz.ssoclient.controller.ClientAccessV1ExceptionHandler;
import com.sz.ssoclient.message.SsoServerMessageDispatcher;
import com.sz.ssoclient.service.SsoClientService;
import com.sz.ssocore.clientaccess.v1.ClientAccessNonceStore;
import com.sz.ssoclient.spi.SsoUserMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Supplier;

/** CAP V1 HTTP client and browser transaction auto-configuration. */
@Slf4j
@AutoConfiguration(after = SsoClientLoginAutoConfiguration.class)
@Import({ClientAccessV1Controller.class, ClientAccessInboundController.class, ClientAccessV1ExceptionHandler.class})
public class ClientAccessV1AutoConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 2;
    private static final int TRANSACTION_TTL_SECONDS = 600;
    private static final int MAX_PENDING_TRANSACTIONS_PER_BROWSER = 5;
    private static final long LOCAL_SESSION_TIMEOUT_SECONDS = 7200;
    private static final int MAX_CLOCK_SKEW_SECONDS = 300;
    private static final int NONCE_TTL_SECONDS = 600;
    private static final int INBOUND_EVENT_TTL_SECONDS = 3600;

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessJsonCodec clientAccessJsonCodec() {
        return new ClientAccessJsonCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessRegistrationProvider clientAccessRegistrationProvider() {
        return () -> {
            SaSsoClientConfig config = SaSsoClientUtil.getSsoTemplate().getClientConfig();
            return new ClientAccessRegistration(URI.create(config.getServerUrl()), config.getClient(),
                    config.getSecretKey().getBytes(StandardCharsets.UTF_8));
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessHttpTransport clientAccessHttpTransport() {
        return new JdkClientAccessHttpTransport(CONNECT_TIMEOUT, REQUEST_TIMEOUT);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessV1Client clientAccessV1Client(ClientAccessJsonCodec jsonCodec,
            ClientAccessRegistrationProvider registrationProvider,
            ClientAccessHttpTransport transport) {
        SecureRandom random = new SecureRandom();
        Supplier<String> nonce = () -> randomToken(random);
        return new ClientAccessV1Client(jsonCodec, registrationProvider, transport, Clock.systemUTC(),
                () -> UUID.randomUUID().toString().replace("-", ""), nonce, MAX_ATTEMPTS);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessStateRepository clientAccessStateRepository(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            log.info("[SSO] CAP 状态使用 Spring Data Redis 共享原子存储");
            return new RedisClientAccessStateRepository(redisTemplate);
        }
        log.warn("[SSO] CAP 状态使用单进程内存存储；仅允许 local/dev/test，生产环境将拒绝启动");
        return new InMemoryClientAccessStateRepository(Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessStateSafetyVerifier clientAccessStateSafetyVerifier(
            ClientAccessStateRepository repository, Environment environment) {
        return new ClientAccessStateSafetyVerifier(repository, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientLoginTransactionStore clientLoginTransactionStore(ClientAccessStateRepository repository,
            ClientAccessJsonCodec jsonCodec, ClientAccessRegistrationProvider registrationProvider) {
        return new RepositoryClientLoginTransactionStore(repository, jsonCodec, Clock.systemUTC(),
                () -> registrationProvider.current().clientFlag());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientLocalSessionAccessor clientLocalSessionAccessor() {
        return new SaTokenClientLocalSessionAccessor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessWebService clientAccessWebService(ClientAccessV1Client capClient,
            ClientLocalSessionAccessor sessions, SsoUserMappingService mappings) {
        return new ClientAccessWebService(capClient, sessions, mappings,
                () -> UUID.randomUUID().toString().replace("-", ""));
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientBrowserBinding clientBrowserBinding() {
        SecureRandom random = new SecureRandom();
        return new ClientBrowserBinding(() -> randomToken(random));
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessNonceStore clientAccessNonceStore(ClientAccessStateRepository repository) {
        return new RepositoryClientAccessNonceStore(repository, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessInboundVerifier clientAccessInboundVerifier(
            ClientAccessRegistrationProvider registrationProvider, ClientAccessNonceStore nonceStore) {
        return new ClientAccessInboundVerifier(registrationProvider, nonceStore, Clock.systemUTC(),
                MAX_CLOCK_SKEW_SECONDS, NONCE_TTL_SECONDS);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientInboundEventStore clientInboundEventStore(ClientAccessStateRepository repository,
            ClientAccessRegistrationProvider registrationProvider) {
        return new RepositoryClientInboundEventStore(repository,
                () -> registrationProvider.current().clientFlag(), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAccessInboundService clientAccessInboundService(ClientLocalSessionAccessor sessions,
            SsoUserMappingService mappings, SsoServerMessageDispatcher dispatcher,
            ClientInboundEventStore eventStore) {
        return new ClientAccessInboundService(sessions, mappings, dispatcher, SaSsoClientUtil::getSsoTemplate,
                eventStore, Clock.systemUTC(), INBOUND_EVENT_TTL_SECONDS);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientLoginTransactionService clientLoginTransactionService(ClientAccessV1Client capClient,
            ClientAccessRegistrationProvider registrationProvider,
            SsoUserMappingService mappingService,
            SsoClientService loginService,
            ClientLoginTransactionStore store) {
        SecureRandom random = new SecureRandom();
        return new ClientLoginTransactionService(capClient, registrationProvider, mappingService, loginService,
                store, Clock.systemUTC(), () -> randomToken(random), TRANSACTION_TTL_SECONDS,
                MAX_PENDING_TRANSACTIONS_PER_BROWSER, LOCAL_SESSION_TIMEOUT_SECONDS);
    }

    private static String randomToken(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
