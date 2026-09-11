package com.sz.ssoclient.internal.messaging;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.sign.template.SaSignTemplate;
import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.internal.firstparty.SsoFirstPartyMessageContribution;
import com.sz.ssoclient.spi.advanced.SsoClientMessageHandler;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SsoClientPushSecurityBoundaryTest {

    @Test
    void pushControllerExposesOnlyPushCAndDelegatesToNativeProcessor() throws Exception {
        CountingProcessor processor = new CountingProcessor();
        SsoClientPushController controller = new SsoClientPushController(processor);

        assertEquals("native", controller.receivePush());
        assertEquals(1, processor.calls.get());
        RequestMapping classMapping = SsoClientPushController.class.getAnnotation(RequestMapping.class);
        Method method = SsoClientPushController.class.getMethod("receivePush");
        assertEquals(List.of("/sso"), List.of(classMapping.value()));
        assertEquals(List.of("/pushC"), List.of(method.getAnnotation(RequestMapping.class).value()));
        assertFalse(List.of(SsoClientPushController.class.getDeclaredMethods()).stream()
                .anyMatch(candidate -> candidate.getName().toLowerCase().contains("logout")));
    }

    @Test
    void nativeSignatureTimestampOrNonceFailureRunsNoFixedCustomOrFirstPartyHandler() {
        AtomicInteger fixedCalls = new AtomicInteger();
        AtomicInteger customCalls = new AtomicInteger();
        AtomicInteger firstPartyCalls = new AtomicInteger();
        TestTemplate template = new TestTemplate("client-a");
        doThrow(new IllegalStateException("native signature/timestamp/nonce rejected"))
                .when(template.signTemplate).checkParamMap(any());
        registerCounters(template, fixedCalls, customCalls, firstPartyCalls);

        for (String type : List.of(
                SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN,
                "CUSTOM_AUDIT",
                "CONFIG_QUERY")) {
            SaSsoClientProcessor processor = processor(template);
            Map<String, String> params = signedParams(type, "client-a");
            SaRequest request = request(params);
            try (MockedStatic<SaHolder> holder = mockStatic(SaHolder.class)) {
                holder.when(SaHolder::getRequest).thenReturn(request);
                assertThrows(IllegalStateException.class, processor::ssoPushC);
            }
        }

        assertEquals(0, fixedCalls.get());
        assertEquals(0, customCalls.get());
        assertEquals(0, firstPartyCalls.get());
    }

    @Test
    void signedIdentityMismatchAndWrongDirectionFailBeforeBusinessHandler() {
        AtomicInteger fixedCalls = new AtomicInteger();
        TestTemplate template = new TestTemplate("client-a");
        registerCounters(template, fixedCalls, new AtomicInteger(), new AtomicInteger());

        invokeAndExpectFailure(template, signedParams(SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN, "client-b"));
        invokeAndExpectFailure(template, signedParams(SsoMessageTypes.USER_CHECK, "client-a"));

        assertEquals(0, fixedCalls.get());
    }

    private static void registerCounters(
            TestTemplate template,
            AtomicInteger fixedCalls,
            AtomicInteger customCalls,
            AtomicInteger firstPartyCalls) {
        SsoClientMessageRegistrar.FixedHandler fixed = new SsoClientMessageRegistrar.FixedHandler() {
            @Override
            public String messageType() {
                return SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN;
            }

            @Override
            public SsoMessageResult<?> handle(Map<String, Object> payload) {
                fixedCalls.incrementAndGet();
                return success();
            }
        };
        SsoClientMessageHandler custom = new SsoClientMessageHandler() {
            @Override
            public String messageType() {
                return "CUSTOM_AUDIT";
            }

            @Override
            public SsoMessageResult<?> handle(com.sz.ssoclient.spi.advanced.SsoClientMessageContext context) {
                customCalls.incrementAndGet();
                return success();
            }
        };
        SsoFirstPartyMessageContribution firstParty = new SsoFirstPartyMessageContribution() {
            @Override
            public String messageType() {
                return "CONFIG_QUERY";
            }

            @Override
            public SsoMessageResult<?> handle(
                    com.sz.ssoclient.internal.firstparty.SsoFirstPartyMessageContext context) {
                firstPartyCalls.incrementAndGet();
                return success();
            }
        };
        new SsoClientMessageRegistrar(
                template,
                new SsoMessageCodec(),
                List.of(fixed),
                List.of(custom),
                List.of(firstParty)).register();
    }

    private static void invokeAndExpectFailure(TestTemplate template, Map<String, String> params) {
        SaRequest request = request(params);
        try (MockedStatic<SaHolder> holder = mockStatic(SaHolder.class)) {
            holder.when(SaHolder::getRequest).thenReturn(request);
            assertThrows(RuntimeException.class, () -> processor(template).ssoPushC());
        }
    }

    private static SaRequest request(Map<String, String> params) {
        SaRequest request = mock(SaRequest.class);
        when(request.getParamMap()).thenReturn(params);
        return request;
    }

    private static Map<String, String> signedParams(String type, String client) {
        return Map.of(
                "msgType", type,
                "client", client,
                "timestamp", "1785800000000",
                "nonce", "nonce-1",
                "sign", "native-sign");
    }

    private static SaSsoClientProcessor processor(SaSsoClientTemplate template) {
        SaSsoClientProcessor processor = new SaSsoClientProcessor();
        processor.ssoClientTemplate = template;
        return processor;
    }

    private static SsoMessageResult<Void> success() {
        return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "ok", null);
    }

    private static final class TestTemplate extends SaSsoClientTemplate {
        private final SaSsoClientConfig config = new SaSsoClientConfig();
        private final SaSignTemplate signTemplate = mock(SaSignTemplate.class);

        private TestTemplate(String client) {
            config.setClient(client);
            config.setIsCheckSign(true);
        }

        @Override
        public SaSsoClientConfig getClientConfig() {
            return config;
        }

        @Override
        public SaSignTemplate getSignTemplate() {
            return signTemplate;
        }
    }

    private static final class CountingProcessor extends SaSsoClientProcessor {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Object ssoPushC() {
            calls.incrementAndGet();
            return "native";
        }
    }
}
