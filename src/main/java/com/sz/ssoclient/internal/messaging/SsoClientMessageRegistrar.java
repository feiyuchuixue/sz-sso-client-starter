package com.sz.ssoclient.internal.messaging;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.sso.util.SaSsoConsts;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.internal.firstparty.SsoFirstPartyMessageContext;
import com.sz.ssoclient.internal.firstparty.SsoFirstPartyMessageContribution;
import com.sz.ssoclient.spi.advanced.SsoClientMessageContext;
import com.sz.ssoclient.spi.advanced.SsoClientMessageHandler;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一次性聚合固定、首方和 CUSTOM Handler 的唯一 Sa-Token Registrar。 */
public class SsoClientMessageRegistrar {

    public interface FixedHandler {
        String messageType();

        SsoMessageResult<?> handle(Map<String, Object> payload);
    }

    private static final Set<String> CORE_TYPES = Set.of(
            SsoMessageTypes.USER_CHECK,
            SsoMessageTypes.USER_CHECK_BATCH,
            SsoMessageTypes.QUERY_CLIENT_SUPER_ADMIN_STATUS,
            SsoMessageTypes.SYNC_SUPER_ADMIN,
            SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN,
            SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN_BATCH,
            SsoMessageTypes.CREATE_PORTAL_TICKET,
            SsoMessageTypes.CHECK_CLIENT_USER_READINESS_BATCH,
            SsoMessageTypes.PREPARE_CLIENT_USERS_BATCH,
            SsoMessageTypes.REQUEST_SLO,
            SsoMessageTypes.REVOKE_SSO_SESSIONS,
            SsoMessageTypes.QUERY_CLIENT_SUPER_ADMIN_SNAPSHOT);

    private static final Set<String> SERVER_TO_CLIENT_CORE_TYPES = Set.of(
            SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN,
            SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN_BATCH,
            SsoMessageTypes.CHECK_CLIENT_USER_READINESS_BATCH,
            SsoMessageTypes.PREPARE_CLIENT_USERS_BATCH,
            SsoMessageTypes.REVOKE_SSO_SESSIONS,
            SsoMessageTypes.QUERY_CLIENT_SUPER_ADMIN_SNAPSHOT);

    private static final Set<String> PLATFORM_TYPES = Set.of(
            "CONFIG_QUERY",
            "RESTFUL_SSO_CLIENT",
            "RESTFUL_SSO_USER",
            "RESTFUL_SSO_DOMAIN");

    private static final Set<String> SA_TOKEN_TYPES = Set.of(
            SaSsoConsts.MESSAGE_CHECK_TICKET,
            SaSsoConsts.MESSAGE_SIGNOUT,
            SaSsoConsts.MESSAGE_LOGOUT_CALL);

    private final SaSsoClientTemplate clientTemplate;
    private final SsoMessageCodec codec;
    private final List<FixedHandler> fixedHandlers;
    private final List<SsoClientMessageHandler> customHandlers;
    private final List<SsoFirstPartyMessageContribution> firstPartyContributions;
    private final AtomicBoolean registered = new AtomicBoolean();

    public SsoClientMessageRegistrar(
            SaSsoClientTemplate clientTemplate,
            SsoMessageCodec codec,
            List<FixedHandler> fixedHandlers,
            List<SsoClientMessageHandler> customHandlers,
            List<SsoFirstPartyMessageContribution> firstPartyContributions) {
        this.clientTemplate = Objects.requireNonNull(clientTemplate, "clientTemplate");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.fixedHandlers = List.copyOf(fixedHandlers == null ? List.of() : fixedHandlers);
        this.customHandlers = List.copyOf(customHandlers == null ? List.of() : customHandlers);
        this.firstPartyContributions = List.copyOf(
                firstPartyContributions == null ? List.of() : firstPartyContributions);
    }

    public void register() {
        if (registered.get()) {
            throw new IllegalStateException("SSO Client 消息 Registrar 不允许重复注册");
        }
        Map<String, DispatchTarget> targets = collectAndValidate();
        String expectedClient = clientTemplate.getClient();
        if (expectedClient == null || expectedClient.isBlank()) {
            throw new IllegalStateException("Sa-Token SSO Client 标识不能为空");
        }
        List<String> collisions = targets.keySet().stream()
                .filter(clientTemplate.messageHolder::hasHandle)
                .toList();
        if (!collisions.isEmpty()) {
            throw new IllegalStateException("SSO 消息类型已被其他注册路径占用: " + collisions);
        }
        if (!registered.compareAndSet(false, true)) {
            throw new IllegalStateException("SSO Client 消息 Registrar 不允许并发重复注册");
        }
        targets.forEach((messageType, target) -> clientTemplate.messageHolder.addHandle(
                messageType,
                (template, message) -> dispatch(expectedClient, target, message)));
    }

    Map<String, DispatchTarget> collectAndValidate() {
        Map<String, DispatchTarget> targets = new LinkedHashMap<>();
        for (FixedHandler handler : fixedHandlers) {
            Objects.requireNonNull(handler, "fixed handler");
            String messageType = handler.messageType();
            if (!SERVER_TO_CLIENT_CORE_TYPES.contains(messageType)) {
                throw new IllegalStateException("固定 Handler 声明了错误方向或非 Core 类型: " + messageType);
            }
            addUnique(targets, messageType, payload -> handler.handle(payload));
        }
        for (SsoFirstPartyMessageContribution contribution : firstPartyContributions) {
            Objects.requireNonNull(contribution, "first-party contribution");
            String messageType = contribution.messageType();
            if (!PLATFORM_TYPES.contains(messageType)) {
                throw new IllegalStateException("首方 contribution 只能声明 Platform 保留消息类型: " + messageType);
            }
            addUnique(targets, messageType, payload -> contribution.handle(
                    new SsoFirstPartyMessageContext(messageType, payload)));
        }
        for (SsoClientMessageHandler handler : customHandlers) {
            Objects.requireNonNull(handler, "custom handler");
            String messageType = handler.messageType();
            requireCustomType(messageType);
            addUnique(targets, messageType, payload -> handler.handle(
                    new SsoClientMessageContext(messageType, payload)));
        }
        return Map.copyOf(targets);
    }

    static void requireCustomType(String messageType) {
        if (messageType == null || !messageType.startsWith("CUSTOM_") || messageType.length() == "CUSTOM_".length()) {
            throw new IllegalArgumentException("自定义消息类型必须使用非空 CUSTOM_* 命名空间: " + messageType);
        }
        if (reservedTypes().contains(messageType)) {
            throw new IllegalArgumentException("自定义消息类型不得覆盖 Core、Platform 或 Sa-Token 保留类型: " + messageType);
        }
    }

    static void requireFirstPartyOutboundType(String messageType) {
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("首方消息类型不能为空");
        }
        if (SA_TOKEN_TYPES.contains(messageType) || SERVER_TO_CLIENT_CORE_TYPES.contains(messageType)) {
            throw new IllegalArgumentException("首方出站消息不允许发送 Sa-Token 类型或错误方向的 Core 类型: " + messageType);
        }
        if (!CORE_TYPES.contains(messageType) && !PLATFORM_TYPES.contains(messageType)) {
            throw new IllegalArgumentException("首方出站消息类型不在冻结保留集合中: " + messageType);
        }
    }

    static Set<String> reservedTypes() {
        List<String> types = new ArrayList<>(CORE_TYPES);
        types.addAll(PLATFORM_TYPES);
        types.addAll(SA_TOKEN_TYPES);
        return Set.copyOf(types);
    }

    private Object dispatch(String expectedClient, DispatchTarget target, SaSsoMessage message) {
        String actualClient = message.get(SsoProtocolFields.CLIENT) == null
                ? null
                : message.get(SsoProtocolFields.CLIENT).toString();
        if (!expectedClient.equals(actualClient)) {
            throw new IllegalArgumentException("SSO pushC 消息的受签名 Client 身份不匹配");
        }
        return SaResult.data(target.handle(codec.businessPayload(message)));
    }

    private static void addUnique(
            Map<String, DispatchTarget> targets,
            String messageType,
            DispatchTarget target) {
        if (targets.putIfAbsent(messageType, target) != null) {
            throw new IllegalStateException("SSO 消息类型重复，拒绝部分注册: " + messageType);
        }
    }

    @FunctionalInterface
    interface DispatchTarget {
        SsoMessageResult<?> handle(Map<String, Object> payload);
    }
}
