package com.sz.ssoclient.internal.messaging;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.api.SsoClientMessageSender;
import com.sz.ssoclient.internal.firstparty.SsoFirstPartyMessageTransport;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoProtocolFields;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Starter 中唯一直接调用 Sa-Token Client 消息传输的生产类。 */
public class SaTokenSsoMessageTransport
        implements SsoClientMessageSender, SsoFirstPartyMessageTransport {

    private final SaSsoClientTemplate clientTemplate;
    private final SsoMessageCodec codec;

    public SaTokenSsoMessageTransport(SaSsoClientTemplate clientTemplate, SsoMessageCodec codec) {
        this.clientTemplate = Objects.requireNonNull(clientTemplate, "clientTemplate");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public <T> SsoMessageResult<T> sendCustom(
            String messageType,
            Map<String, ?> payload,
            Class<T> responseType) {
        SsoClientMessageRegistrar.requireCustomType(messageType);
        return sendTrusted(messageType, payload, responseType);
    }

    @Override
    public <T> SsoMessageResult<T> sendToServer(
            String messageType,
            Map<String, ?> payload,
            Class<T> responseType) {
        SsoClientMessageRegistrar.requireFirstPartyOutboundType(messageType);
        return sendTrusted(messageType, payload, responseType);
    }

    <T> SsoMessageResult<T> sendTrusted(
            String messageType,
            Map<String, ?> payload,
            Class<T> responseType) {
        requireText(messageType, "messageType");
        Objects.requireNonNull(responseType, "responseType");
        Map<String, ?> safePayload = payload == null ? Map.of() : payload;
        rejectEnvelopeFields(safePayload);

        String client = clientTemplate.getClient();
        requireText(client, "Sa-Token client");
        SaSsoMessage message = new SaSsoMessage(messageType);
        message.set(SsoProtocolFields.CLIENT, client);
        safePayload.forEach(message::set);
        SaResult result = clientTemplate.pushMessageAsSaResult(message);
        return codec.fromSaResult(result, responseType);
    }

    static void rejectEnvelopeFields(Map<String, ?> payload) {
        Map<String, Object> rejected = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("自定义消息 payload 字段名不能为空");
            }
            if (SsoMessageCodec.envelopeFields().contains(key)) {
                rejected.put(key, value);
            }
        });
        if (!rejected.isEmpty()) {
            throw new IllegalArgumentException(
                    "自定义消息 payload 不得包含 Sa-Token 保留信封字段: " + rejected.keySet());
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
