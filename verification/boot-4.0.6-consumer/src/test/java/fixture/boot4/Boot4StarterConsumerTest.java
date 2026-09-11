package fixture.boot4;

import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sz.ssoclient.api.browser.SsoLoginTransactionResponse;
import com.sz.ssoclient.autoconfigure.SsoClientAutoConfiguration;
import com.sz.ssoclient.internal.bootstrap.InMemorySsoClientStateRepository;
import com.sz.ssoclient.internal.bootstrap.SsoClientConfigurationException;
import com.sz.ssoclient.internal.login.SsoClientLoginOrchestrator;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransaction;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransactionCodec;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransactionStatus;
import com.sz.ssoclient.internal.login.transaction.SsoStateHasher;
import com.sz.ssoclient.internal.messaging.SaTokenSsoMessageTransport;
import com.sz.ssoclient.internal.messaging.SsoClientMessageRegistrar;
import com.sz.ssoclient.internal.messaging.SsoClientPushController;
import com.sz.ssoclient.internal.web.SsoClientBrowserBinding;
import com.sz.ssoclient.internal.web.SsoClientWebController;
import com.sz.ssoclient.internal.web.SsoClientWebService;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientIdentityPreparationService;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoDefaultAccessInitializer;
import com.sz.ssoclient.spi.SsoSuperAdminAuthorityAdapter;
import com.sz.ssoclient.spi.SsoSuperAdminSnapshotProvider;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssoclient.spi.advanced.SsoClientMessageContext;
import com.sz.ssoclient.spi.advanced.SsoClientMessageHandler;
import com.sz.ssoclient.spi.advanced.SsoClientStateRepository;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoUserMeta;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(
        classes = Boot4ConsumerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RequiredSpiFixtureConfiguration.class)
class Boot4StarterConsumerTest {

    private static final String ORIGIN = RequiredSpiFixtureConfiguration.CLIENT_ORIGIN;
    private static final Cookie BROWSER_COOKIE = new Cookie(
            SsoClientBrowserBinding.COOKIE_NAME, "B".repeat(43));

    private final ApplicationContext context;
    private final RequestMappingHandlerMapping handlerMapping;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    Boot4StarterConsumerTest(
            ApplicationContext context,
            RequestMappingHandlerMapping handlerMapping,
            MockMvc mockMvc,
            ObjectMapper objectMapper) {
        this.context = context;
        this.handlerMapping = handlerMapping;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void consumesOnlyInstalledCoreAndStarterJars() throws Exception {
        Path repository = Path.of(requiredSystemProperty("m4.repo.path"))
                .toAbsolutePath()
                .normalize();
        assertJarSource(
                SsoMessageResult.class,
                repository.resolve("com/sz-dev/sz-sso-core/1.0.0-SNAPSHOT/"
                        + "sz-sso-core-1.0.0-SNAPSHOT.jar"));
        assertJarSource(
                SsoLoginTransactionResponse.class,
                repository.resolve("com/sz-dev/sz-sso-client-starter/1.0.0-SNAPSHOT/"
                        + "sz-sso-client-starter-1.0.0-SNAPSHOT.jar"));

        String classPath = System.getProperty("java.class.path", "")
                .replace('\\', '/');
        assertThat(classPath)
                .doesNotContain("/sz-sso-core/target/classes")
                .doesNotContain("/sz-sso-client-starter/target/classes")
                .doesNotContain("/sz-sso-core/src/main")
                .doesNotContain("/sz-sso-client-starter/src/main");
    }

    @Test
    void startsFullContextWithOneAutoConfigurationRegistrarAndTransport() {
        assertThat(context.getBeansOfType(SsoClientAutoConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(SsoClientMessageRegistrar.class)).hasSize(1);
        assertThat(context.getBeansOfType(SaTokenSsoMessageTransport.class)).hasSize(1);
        assertThat(context.getBeansOfType(SsoClientPushController.class)).hasSize(1);
        assertThat(context.getBeansOfType(SsoClientWebController.class)).hasSize(1);
        assertThat(context.getBeansOfType(SsoClientWebService.class)).hasSize(1);
        assertThat(context.getBeansOfType(SsoClientLoginOrchestrator.class)).hasSize(1);
        assertThat(BeanFactoryUtils.beansOfTypeIncludingAncestors(
                context, SsoClientStateRepository.class)).hasSize(1);

        for (String legacy : List.of(
                "com.sz.ssoclient.autoconfigure.SsoClientLoginAutoConfiguration",
                "com.sz.ssoclient.autoconfigure.SsoClientMessageAutoConfiguration",
                "com.sz.ssoclient.autoconfigure.SsoClientWebAutoConfiguration",
                "com.sz.ssoclient.autoconfigure.SsoClientTemplateAutoConfiguration")) {
            assertThatThrownBy(() -> Class.forName(legacy))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void bindsStandardSaTokenClientPropertiesWithoutProgrammaticTemplate() {
        SaSsoClientTemplate template = context.getBean(SaSsoClientTemplate.class);
        SaSsoClientConfig config = template.getClientConfig();

        assertThat(config.getClient()).isEqualTo("boot4-fixture");
        assertThat(config.getServerUrl()).isEqualTo("https://sso.example.com");
        assertThat(config.getAuthUrl()).isEqualTo("https://sso.example.com/sso/auth");
        assertThat(config.getCurrSsoLogin())
                .isEqualTo("https://client.example.com/sso/v1/login/callback");
    }

    @Test
    void exposesOnlySixBrowserPostsAndExecutesTheirRealHttpJsonContracts()
            throws Exception {
        Set<String> routes = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType() == SsoClientWebController.class)
                .flatMap(entry -> entry.getKey().getPatternValues().stream()
                        .flatMap(pattern -> entry.getKey().getMethodsCondition().getMethods().stream()
                                .map(method -> method.name() + " " + pattern)))
                .collect(Collectors.toSet());
        assertThat(routes).containsExactlyInAnyOrder(
                "POST /sso/v1/login/transactions",
                "POST /sso/v1/login/callback",
                "POST /sso/v1/session/logout",
                "POST /sso/v1/signouts/device",
                "POST /sso/v1/signouts/account",
                "POST /sso/v1/portal/entries");
        assertThat(routes).allMatch(route -> route.startsWith(RequestMethod.POST.name()));

        MvcResult transaction = perform("/sso/v1/login/transactions",
                "{\"back\":\"/dashboard\",\"theme\":\"light\"}", false);
        assertThat(transaction.getResponse().getStatus()).isEqualTo(200);
        assertSensitiveHeaders(transaction);
        JsonNode transactionJson = json(transaction);
        assertExactFields(transactionJson, "code", "message", "data");
        assertThat(transactionJson.path("code").asText()).isEqualTo("0000");
        assertThat(transactionJson.path("message").asText()).isEqualTo("success");
        assertExactFields(transactionJson.path("data"), "authorizationUrl");
        String authorizationUrl = transactionJson.path("data").path("authorizationUrl").asText();
        assertThat(authorizationUrl)
                .startsWith(RequiredSpiFixtureConfiguration.SSO_ORIGIN + "/sso/auth")
                .contains("state=")
                .contains("theme=light");
        assertThat(transaction.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains(SsoClientBrowserBinding.COOKIE_NAME + "=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");

        MvcResult callback = perform("/sso/v1/login/callback",
                "{\"ticket\":\"ticket\",\"state\":\""
                        + "A".repeat(43) + "\",\"unexpected\":true}", true);
        assertJson(callback, 400,
                "{\"code\":\"SSO-1001\",\"message\":\"请求包含不允许的字段\",\"data\":null}");

        MvcResult logout = perform("/sso/v1/session/logout", "{}", true);
        assertJson(logout, 200,
                "{\"code\":\"0000\",\"message\":\"success\","
                        + "\"data\":{\"localOutcome\":\"REVOKED\"}}");

        String notPropagated = "{\"code\":\"0000\",\"message\":\"success\","
                + "\"data\":{\"localOutcome\":\"REVOKED\","
                + "\"propagationOutcome\":\"NOT_REQUESTED\","
                + "\"propagationReason\":\"NO_MAPPING\",\"receipt\":null}}";
        assertJson(perform("/sso/v1/signouts/device", "{}", true), 200, notPropagated);
        assertJson(perform("/sso/v1/signouts/account", "{}", true), 200, notPropagated);

        MvcResult portal = perform("/sso/v1/portal/entries",
                "{\"target\":\"PROFILE\"}", true);
        assertJson(portal, 403,
                "{\"code\":\"SSO-4002\","
                        + "\"message\":\"当前用户不存在可信 SSO 映射\",\"data\":null}");

        assertThat(mockMvc.perform(post("/sso/logoutCall")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(post("/client-api/v1/login")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void roundTripsDateBearingNeutralJsonWithoutDerivedEnvelopeFields()
            throws Exception {
        LocalDateTime createdAt = LocalDateTime.parse("2026-08-04T12:34:56");
        SsoUserMeta user = new SsoUserMeta(
                42L,
                "alice",
                "Alice",
                "alice@example.com",
                "13800000000",
                "https://cdn.example.com/avatar.png",
                createdAt);
        SsoMessageResult<SsoUserMeta> result = new SsoMessageResult<>(
                SsoMessageResult.SUCCESS_CODE, "success", user);

        String encoded = objectMapper.writeValueAsString(result);
        JsonNode tree = objectMapper.readTree(encoded);
        assertExactFields(tree, "code", "message", "data");
        assertThat(tree.has("successful")).isFalse();
        assertThat(tree.path("data").path("createTime").asText())
                .isEqualTo("2026-08-04T12:34:56");

        SsoMessageResult<SsoUserMeta> decoded = objectMapper.readValue(
                encoded,
                new TypeReference<>() { });
        assertThat(decoded.code()).isEqualTo("0000");
        assertThat(decoded.message()).isEqualTo("success");
        assertThat(decoded.data().getSsoUserId()).isEqualTo(42L);
        assertThat(decoded.data().getCreateTime()).isEqualTo(createdAt);
    }

    @Test
    void roundTripsDeterministicLoginTransactionAndAtomicSecurityState()
            throws Exception {
        String rawState = "state-secret-" + "S".repeat(32);
        String rawBrowser = "browser-secret-" + "B".repeat(32);
        Instant createdAt = Instant.parse("2026-08-04T00:00:00Z");
        SsoLoginTransaction transaction = new SsoLoginTransaction(
                SsoStateHasher.sha256(rawState),
                SsoStateHasher.sha256(rawBrowser),
                "/dashboard?tab=profile",
                createdAt,
                createdAt.plusSeconds(60),
                SsoLoginTransactionStatus.CREATED);
        SsoLoginTransactionCodec codec = context.getBean(SsoLoginTransactionCodec.class);

        String encoded = codec.encode(transaction);
        SsoLoginTransaction decoded = codec.decode(encoded);
        assertThat(decoded).isEqualTo(transaction);
        assertThat(codec.encode(decoded)).isEqualTo(encoded);
        assertThat(encoded).doesNotContain(rawState, rawBrowser, "ticket", "secretKey");
        assertThat(decoded.expiresAt()).isEqualTo(decoded.createdAt().plusSeconds(60));
        assertThat(decoded.exchanging().status()).isEqualTo(SsoLoginTransactionStatus.EXCHANGING);
        assertThatThrownBy(() -> decoded.exchanging().exchanging())
                .isInstanceOf(IllegalStateException.class);
        assertThat(Arrays.stream(SsoLoginTransactionStatus.values())
                .map(Enum::name)).containsExactly("CREATED", "EXCHANGING");

        MutableClock clock = new MutableClock(createdAt);
        SsoClientStateRepository state = new InMemorySsoClientStateRepository(clock);
        assertThat(state.shared()).isFalse();
        assertThat(state.putIfAbsent("security-key", encoded, Duration.ofSeconds(60))).isTrue();
        assertThat(state.putIfAbsent("security-key", "other", Duration.ofSeconds(60))).isFalse();
        String exchanging = codec.encode(decoded.exchanging());
        assertThat(state.compareAndSet(
                "security-key", encoded, exchanging, Duration.ofSeconds(60))).isTrue();
        assertThat(state.compareAndDelete("security-key", encoded)).isFalse();
        assertThat(state.get("security-key")).isEqualTo(exchanging);
        clock.advance(Duration.ofSeconds(61));
        assertThat(state.get("security-key")).isNull();
    }

    @Test
    void enforcesFrozenSpiCardinalityAndProductionStateFailClosed() {
        List<Class<?>> ordinarySpis = List.of(
                SsoClientIdentityAdapter.class,
                SsoClientLoginAdapter.class,
                SsoDefaultAccessInitializer.class,
                SsoSuperAdminAuthorityAdapter.class,
                SsoClientIdentityPreparationService.class,
                SsoSuperAdminSnapshotProvider.class);
        List<Class<?>> advancedSpis = List.of(
                SsoClientMessageHandler.class,
                SsoClientStateRepository.class,
                SsoClientLocalSessionAccessor.class);
        assertThat(ordinarySpis).hasSize(6);
        assertThat(advancedSpis).hasSize(3);

        withRequired(baseRunner("test")).run(started -> {
            assertThat(started.getStartupFailure()).isNull();
            assertThat(started.getBeansOfType(SsoClientIdentityAdapter.class)).hasSize(1);
            assertThat(started.getBeansOfType(SsoClientLoginAdapter.class)).hasSize(1);
        });

        baseRunner("test")
                .withBean("onlyLogin", SsoClientLoginAdapter.class,
                        RequiredSpiFixtureConfiguration::loginAdapter)
                .run(failed -> assertFailureContains(
                        failed.getStartupFailure(), SsoClientIdentityAdapter.class.getName()));
        baseRunner("test")
                .withBean("onlyIdentity", SsoClientIdentityAdapter.class,
                        RequiredSpiFixtureConfiguration::identityAdapter)
                .run(failed -> assertFailureContains(
                        failed.getStartupFailure(), SsoClientLoginAdapter.class.getName()));

        for (Class<?> singleton : ordinarySpis) {
            assertDuplicateRejected(singleton, singleton == SsoClientIdentityAdapter.class
                    || singleton == SsoClientLoginAdapter.class);
        }
        assertDuplicateRejected(SsoClientStateRepository.class, false);
        assertDuplicateRejected(SsoClientLocalSessionAccessor.class, false);

        withRequired(baseRunner("test"))
                .withBean("customOne", SsoClientMessageHandler.class,
                        () -> customHandler("CUSTOM_BOOT4_ONE"))
                .withBean("customTwo", SsoClientMessageHandler.class,
                        () -> customHandler("CUSTOM_BOOT4_TWO"))
                .run(started -> {
                    assertThat(started.getStartupFailure()).isNull();
                    assertThat(started.getBeansOfType(SsoClientMessageHandler.class)).hasSize(2);
                });

        withRequired(baseRunner("prod")).run(failed -> {
            assertFailureContains(failed.getStartupFailure(), "Redis");
            assertFailureContains(failed.getStartupFailure(), "prod");
        });
    }

    private MvcResult perform(String path, String json, boolean withBrowserCookie)
            throws Exception {
        var request = post(path)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (withBrowserCookie) {
            request.cookie(BROWSER_COOKIE);
        }
        return mockMvc.perform(request).andReturn();
    }

    private void assertJson(MvcResult result, int status, String expectedJson)
            throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertSensitiveHeaders(result);
        assertThat(json(result)).isEqualTo(objectMapper.readTree(expectedJson));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static void assertSensitiveHeaders(MvcResult result) {
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("no-store");
        assertThat(result.getResponse().getHeader("Referrer-Policy"))
                .isEqualTo("no-referrer");
    }

    private static void assertExactFields(JsonNode object, String... fields) {
        assertThat(object.isObject()).isTrue();
        assertThat(object.propertyNames())
                .containsExactlyInAnyOrder(fields);
    }

    private static void assertJarSource(Class<?> type, Path expectedJar) throws Exception {
        URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path actual = Path.of(location).toAbsolutePath().normalize();
        assertThat(actual.toString()).endsWith(".jar");
        assertThat(actual).isEqualTo(expectedJar.toAbsolutePath().normalize());
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("Missing system property: " + name);
        }
        return value;
    }

    private static ApplicationContextRunner baseRunner(String profile) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SsoClientAutoConfiguration.class))
                .withPropertyValues(
                        "spring.profiles.active=" + profile,
                        "sz.sso-client.enabled=true",
                        "sz.sso-client.external-origin=" + ORIGIN,
                        "sz.sso-client.allow-missing-browser-source=false",
                        "sz.sso-client.secure-cookie=true",
                        "sa-token.sso-client.client=boot4-fixture",
                        "sa-token.sso-client.server-url=https://sso.example.com",
                        "sa-token.sso-client.auth-url=https://sso.example.com/sso/auth",
                        "sa-token.sso-client.curr-sso-login=https://client.example.com/sso/v1/login/callback",
                        "sa-token.sso-client.secret-key=boot4-fixture-secret-not-production");
    }

    private static ApplicationContextRunner withRequired(ApplicationContextRunner runner) {
        return runner
                .withBean("fixtureIdentity", SsoClientIdentityAdapter.class,
                        RequiredSpiFixtureConfiguration::identityAdapter)
                .withBean("fixtureLogin", SsoClientLoginAdapter.class,
                        RequiredSpiFixtureConfiguration::loginAdapter);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertDuplicateRejected(Class<?> type, boolean alreadyPresent) {
        ApplicationContextRunner runner = withRequired(baseRunner("test"));
        int additions = alreadyPresent ? 1 : 2;
        for (int index = 0; index < additions; index++) {
            String name = "duplicate" + type.getSimpleName() + index;
            Class rawType = type;
            Supplier supplier = () -> mock(type);
            runner = runner.withBean(name, rawType, supplier);
        }
        runner.run(failed -> assertFailureContains(
                failed.getStartupFailure(), type.getName()));
    }

    private static void assertFailureContains(Throwable failure, String expected) {
        assertThat(failure).isNotNull();
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.append(current.getClass().getName())
                    .append(':')
                    .append(current.getMessage())
                    .append('\n');
        }
        assertThat(messages.toString()).contains(expected);
    }

    private static SsoClientMessageHandler customHandler(String messageType) {
        return new SsoClientMessageHandler() {
            @Override
            public String messageType() {
                return messageType;
            }

            @Override
            public SsoMessageResult<?> handle(SsoClientMessageContext context) {
                return new SsoMessageResult<>("0000", "success", Map.of());
            }
        };
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
