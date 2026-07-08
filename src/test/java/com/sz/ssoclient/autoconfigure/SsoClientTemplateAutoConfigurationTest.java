package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.dtflys.forest.http.ForestRequest;
import com.sun.net.httpserver.HttpServer;
import com.sz.ssoclient.spi.SsoUserMappingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SsoClientTemplateAutoConfiguration 单元测试")
class SsoClientTemplateAutoConfigurationTest {

    @Test
    @DisplayName("configSsoTemplate() 应将登录链路绑定到 resolveOrProvisionClientUser")
    void configSsoTemplateShouldUseResolveOrProvisionClientUser() {
        SaSsoClientTemplate template = new SaSsoClientTemplate();
        RecordingMappingService mappingService = new RecordingMappingService();

        new SsoClientTemplateAutoConfiguration().configSsoTemplate(template, mappingService, new SsoClientMessageHttpProperties());

        Object clientUserId = template.strategy.convertCenterIdToLoginId.run(431L);

        assertThat(clientUserId).isEqualTo(3001L);
        assertThat(mappingService.resolveOrProvisionCalled).isTrue();
        assertThat(mappingService.toClientCalled).isFalse();
    }

    @Test
    @DisplayName("configSsoTemplate() 应将消息请求策略绑定到配置的超时")
    void configSsoTemplateShouldBindMessageRequestTimeoutStrategy() throws IOException {
        SaSsoClientTemplate template = new SaSsoClientTemplate();
        SsoClientMessageHttpProperties properties = new SsoClientMessageHttpProperties();
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofMillis(100));
        HttpServer server = delayedServer(Duration.ofMillis(250));
        server.start();
        try {
            new SsoClientTemplateAutoConfiguration().configSsoTemplate(template, new RecordingMappingService(), properties);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

            assertThatThrownBy(() -> template.strategy.sendRequest.apply(url))
                    .hasCauseInstanceOf(SocketTimeoutException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("buildSsoMessageRequest() 应覆盖 Forest 默认 3 秒超时")
    void buildSsoMessageRequestShouldOverrideForestDefaultTimeout() {
        SsoClientMessageHttpProperties properties = new SsoClientMessageHttpProperties();
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(30));

        ForestRequest<?> request = SsoClientTemplateAutoConfiguration.buildSsoMessageRequest("http://127.0.0.1:1", properties);

        assertThat(request.getConnectTimeout()).isEqualTo(5000);
        assertThat(request.getReadTimeout()).isEqualTo(30000);
        assertThat(request.getTimeout()).isEqualTo(30000);
    }

    @Test
    @DisplayName("buildSsoMessageRequest() 应拒绝小于 1ms 的无效超时")
    void buildSsoMessageRequestShouldFallbackWhenTimeoutRoundsToZeroMillis() {
        SsoClientMessageHttpProperties properties = new SsoClientMessageHttpProperties();
        properties.setConnectTimeout(Duration.ofNanos(1));
        properties.setReadTimeout(Duration.ofNanos(1));

        ForestRequest<?> request = SsoClientTemplateAutoConfiguration.buildSsoMessageRequest("http://127.0.0.1:1", properties);

        assertThat(request.getConnectTimeout()).isEqualTo(5000);
        assertThat(request.getReadTimeout()).isEqualTo(30000);
        assertThat(request.getTimeout()).isEqualTo(30000);
    }

    private static HttpServer delayedServer(Duration delay) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(delay.toMillis());
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        return server;
    }

    private static final class RecordingMappingService implements SsoUserMappingService {

        private boolean toClientCalled;
        private boolean resolveOrProvisionCalled;

        @Override
        public Object toServerUserId(Object clientUserId) {
            return null;
        }

        @Override
        public Object toClientUserId(Object serverUserId) {
            toClientCalled = true;
            return 9999L;
        }

        @Override
        public Object resolveOrProvisionClientUser(Object serverUserId) {
            resolveOrProvisionCalled = true;
            return 3001L;
        }

        @Override
        public void syncSsoRegisterUser(SaSsoMessage message, String client) {
        }
    }
}