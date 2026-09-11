package com.sz.ssoclient.internal.messaging;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.internal.firstparty.SsoFirstPartyMessageContribution;
import com.sz.ssoclient.spi.advanced.SsoClientMessageHandler;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoClientMessageRegistrarTest {

    @Test
    void registersFixedFirstPartyAndCustomHandlersIntoOneHolder() {
        SaSsoClientTemplate template = template("client-a");
        SsoClientMessageRegistrar registrar = new SsoClientMessageRegistrar(
                template,
                new SsoMessageCodec(),
                List.of(fixed(SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN)),
                List.of(custom("CUSTOM_AUDIT")),
                List.of(firstParty("CONFIG_QUERY")));

        registrar.register();

        assertTrue(template.messageHolder.hasHandle(SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN));
        assertTrue(template.messageHolder.hasHandle("CONFIG_QUERY"));
        assertTrue(template.messageHolder.hasHandle("CUSTOM_AUDIT"));
        assertThrows(IllegalStateException.class, registrar::register);
    }

    @Test
    void registeredHandlerMustKeepSaTokenTransportEnvelopeOutsideBusinessResult() {
        SaSsoClientTemplate template = template("client-a");
        SsoClientMessageRegistrar registrar = new SsoClientMessageRegistrar(
                template,
                new SsoMessageCodec(),
                List.of(fixed(SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN)),
                List.of(),
                List.of());
        registrar.register();
        SaSsoMessage message = new SaSsoMessage(SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN);
        message.set(SsoProtocolFields.CLIENT, "client-a");

        Object response = template.messageHolder.handleMessage(template, message);

        assertTrue(response instanceof SaResult);
        SaResult transport = (SaResult) response;
        assertEquals(SaResult.CODE_SUCCESS, transport.getCode());
        assertTrue(transport.getData() instanceof SsoMessageResult<?>);
        assertEquals(SsoMessageResult.SUCCESS_CODE,
                ((SsoMessageResult<?>) transport.getData()).code());
    }

    @Test
    void validatesAllTypesBeforeMutatingSaTokenHolder() {
        SaSsoClientTemplate template = template("client-a");
        SsoClientMessageRegistrar registrar = new SsoClientMessageRegistrar(
                template,
                new SsoMessageCodec(),
                List.of(fixed(SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN)),
                List.of(custom("CUSTOM_DUPLICATE"), custom("CUSTOM_DUPLICATE")),
                List.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, registrar::register);

        assertTrue(exception.getMessage().contains("重复"));
        assertFalse(template.messageHolder.hasHandle(SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN));
        assertFalse(template.messageHolder.hasHandle("CUSTOM_DUPLICATE"));
    }

    @Test
    void rejectsWrongNamespacesAndAnyExistingRegistrationCollision() {
        SaSsoClientTemplate template = template("client-a");
        assertThrows(IllegalArgumentException.class, () -> SsoClientMessageRegistrar.requireCustomType("USER_CHECK"));
        assertThrows(IllegalArgumentException.class, () -> SsoClientMessageRegistrar.requireCustomType("CUSTOM_"));

        template.messageHolder.addHandle("CUSTOM_EXISTING", (ignored, message) -> null);
        SsoClientMessageRegistrar registrar = new SsoClientMessageRegistrar(
                template,
                new SsoMessageCodec(),
                List.of(),
                List.of(custom("CUSTOM_EXISTING")),
                List.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, registrar::register);
        assertTrue(exception.getMessage().contains("其他注册路径"));
        assertEquals(1, template.messageHolder.messageHandleMap.entrySet().stream()
                .filter(entry -> entry.getKey().equals("CUSTOM_EXISTING"))
                .count());
    }

    private static SaSsoClientTemplate template(String client) {
        return new SaSsoClientTemplate() {
            @Override
            public String getClient() {
                return client;
            }
        };
    }

    private static SsoClientMessageRegistrar.FixedHandler fixed(String type) {
        return new SsoClientMessageRegistrar.FixedHandler() {
            @Override
            public String messageType() {
                return type;
            }

            @Override
            public SsoMessageResult<?> handle(Map<String, Object> payload) {
                return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "ok", payload);
            }
        };
    }

    private static SsoClientMessageHandler custom(String type) {
        return new SsoClientMessageHandler() {
            @Override
            public String messageType() {
                return type;
            }

            @Override
            public SsoMessageResult<?> handle(com.sz.ssoclient.spi.advanced.SsoClientMessageContext context) {
                return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "ok", context.payload());
            }
        };
    }

    private static SsoFirstPartyMessageContribution firstParty(String type) {
        return new SsoFirstPartyMessageContribution() {
            @Override
            public String messageType() {
                return type;
            }

            @Override
            public SsoMessageResult<?> handle(
                    com.sz.ssoclient.internal.firstparty.SsoFirstPartyMessageContext context) {
                return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "ok", context.payload());
            }
        };
    }
}
