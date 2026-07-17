package com.sz.ssoclient.clientaccess.inbound;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.clientaccess.web.ClientLocalSessionAccessor;
import com.sz.ssoclient.message.SsoServerMessageDispatcher;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.clientaccess.v1.ClientAccessMessageTypes;
import com.sz.ssocore.clientaccess.v1.dto.ClientMessageEnvelope;
import com.sz.ssocore.clientaccess.v1.dto.ClientMessageResult;
import com.sz.ssocore.clientaccess.v1.dto.ClientMessageStatus;
import com.sz.ssocore.clientaccess.v1.dto.SloCallbackRequest;
import com.sz.ssocore.clientaccess.v1.dto.SloCallbackResult;
import com.sz.ssocore.clientaccess.v1.dto.SloCallbackStatus;
import com.sz.ssocore.clientaccess.v1.dto.SignoutScope;
import com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserPreparationItem;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserReadinessItem;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Idempotent adapter from CAP callbacks to existing Client session and message SPIs. */
public class ClientAccessInboundService {

    private static final String SLO_NAMESPACE = "slo";
    private static final String MESSAGE_NAMESPACE = "message";

    private final ClientLocalSessionAccessor sessions;
    private final SsoUserMappingService mappings;
    private final SsoServerMessageDispatcher dispatcher;
    private final Supplier<SaSsoTemplate> templateSupplier;
    private final ClientInboundEventStore eventStore;
    private final Clock clock;
    private final int eventTtlSeconds;

    public ClientAccessInboundService(ClientLocalSessionAccessor sessions,
            SsoUserMappingService mappings,
            SsoServerMessageDispatcher dispatcher,
            Supplier<SaSsoTemplate> templateSupplier,
            ClientInboundEventStore eventStore,
            Clock clock,
            int eventTtlSeconds) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.mappings = Objects.requireNonNull(mappings, "mappings");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.templateSupplier = Objects.requireNonNull(templateSupplier, "templateSupplier");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (eventTtlSeconds <= 0) {
            throw new IllegalArgumentException("eventTtlSeconds must be positive");
        }
        this.eventTtlSeconds = eventTtlSeconds;
    }

    public SloCallbackResult handleSlo(SloCallbackRequest request) {
        Objects.requireNonNull(request, "request");
        Instant expiresAt = clock.instant().plusSeconds(eventTtlSeconds);
        ClientInboundEventStatus state = eventStore.begin(SLO_NAMESPACE, request.eventId(), expiresAt);
        if (state == ClientInboundEventStatus.COMPLETED) {
            return new SloCallbackResult(request.eventId(), SloCallbackStatus.ALREADY_APPLIED, 0);
        }
        if (state == ClientInboundEventStatus.IN_PROGRESS) {
            throw new ClientAccessInboundException("CAP-5001", 503, "SLO callback is already in progress");
        }
        try {
            Object localUserId = mappings.toClientUserId(request.ssoUserId());
            if (localUserId == null) {
                eventStore.complete(SLO_NAMESPACE, request.eventId(), expiresAt);
                return new SloCallbackResult(request.eventId(), SloCallbackStatus.NO_LOCAL_SESSION, 0);
            }
            if (request.scope() == SignoutScope.CURRENT_DEVICE) {
                sessions.logoutCurrentDevice(localUserId, request.deviceId());
            } else if (request.scope() == SignoutScope.ACCOUNT_GLOBAL) {
                sessions.logoutAccount(localUserId);
            } else {
                throw new ClientAccessInboundException("CAP-1001", 400, "Unsupported SLO scope");
            }
            eventStore.complete(SLO_NAMESPACE, request.eventId(), expiresAt);
            return new SloCallbackResult(request.eventId(), SloCallbackStatus.APPLIED, 1);
        } catch (RuntimeException exception) {
            eventStore.release(SLO_NAMESPACE, request.eventId());
            throw exception;
        }
    }

    public ClientMessageResult handleMessage(ClientMessageEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        Instant now = clock.instant();
        if (envelope.expiresAt() == null || !envelope.expiresAt().isAfter(now)) {
            throw new ClientAccessInboundException("CAP-1001", 400, "Client message is expired");
        }
        Instant expiresAt = now.plusSeconds(eventTtlSeconds);
        ClientInboundEventStatus state = eventStore.begin(MESSAGE_NAMESPACE, envelope.messageId(), expiresAt);
        if (state == ClientInboundEventStatus.COMPLETED) {
            return new ClientMessageResult(envelope.messageId(), ClientMessageStatus.ALREADY_APPLIED, List.of());
        }
        if (state == ClientInboundEventStatus.IN_PROGRESS) {
            throw new ClientAccessInboundException("CAP-5001", 503, "Client message is already in progress");
        }
        try {
            SaSsoMessage message = new SaSsoMessage()
                    .setType(ClientAccessMessageTypes.toInternal(envelope.messageType()));
            envelope.data().forEach(message::set);
            SaResult result = dispatcher.dispatch(templateSupplier.get(), message);
            ClientMessageResult messageResult = toMessageResult(envelope.messageId(), result);
            eventStore.complete(MESSAGE_NAMESPACE, envelope.messageId(), expiresAt);
            return messageResult;
        } catch (RuntimeException exception) {
            eventStore.release(MESSAGE_NAMESPACE, envelope.messageId());
            throw exception;
        }
    }

    private static ClientMessageResult toMessageResult(String messageId, SaResult result) {
        List<Map<String, Object>> items = extractResultItems(result == null ? null : result.getData());
        if (result == null || result.getCode() == null || result.getCode() != SaResult.CODE_SUCCESS) {
            return new ClientMessageResult(messageId, ClientMessageStatus.REJECTED, items);
        }
        int failed = counter(result.getData(), "failed", 0);
        int success = counter(result.getData(), "success", failed == 0 ? 1 : 0);
        ClientMessageStatus status = failed == 0
                ? ClientMessageStatus.APPLIED
                : success > 0 ? ClientMessageStatus.PARTIALLY_APPLIED : ClientMessageStatus.REJECTED;
        return new ClientMessageResult(messageId, status, items);
    }

    private static List<Map<String, Object>> extractResultItems(Object data) {
        if (data instanceof SsoClientUserReadinessBatchResult readiness) {
            return readiness.getItems() == null ? List.of() : readiness.getItems().stream()
                    .filter(Objects::nonNull)
                    .map(ClientAccessInboundService::readinessItem)
                    .toList();
        }
        if (data instanceof SsoClientUserPreparationBatchResult preparation) {
            return preparation.getItems() == null ? List.of() : preparation.getItems().stream()
                    .filter(Objects::nonNull)
                    .map(ClientAccessInboundService::preparationItem)
                    .toList();
        }
        if (!(data instanceof Map<?, ?> dataMap)
                || !(dataMap.get("failItems") instanceof Collection<?> rawItems)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null) {
                    item.put(key.toString(), value);
                }
            });
            items.add(Collections.unmodifiableMap(item));
        }
        return List.copyOf(items);
    }

    private static Map<String, Object> readinessItem(SsoClientUserReadinessItem source) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ssoUserId", source.getSsoUserId());
        item.put("username", source.getUsername());
        item.put("nickname", source.getNickname());
        item.put("localUserId", source.getLocalUserId());
        item.put("status", source.getStatus() == null ? null : source.getStatus().name());
        item.put("preparable", source.isPreparable());
        item.put("reasonCode", source.getReasonCode());
        item.put("reason", source.getReason());
        return Collections.unmodifiableMap(item);
    }

    private static Map<String, Object> preparationItem(SsoClientUserPreparationItem source) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ssoUserId", source.getSsoUserId());
        item.put("localUserId", source.getLocalUserId());
        item.put("status", source.getStatus() == null ? null : source.getStatus().name());
        item.put("reasonCode", source.getReasonCode());
        item.put("reason", source.getReason());
        return Collections.unmodifiableMap(item);
    }

    private static int counter(Object data, String key, int defaultValue) {
        if (data instanceof Map<?, ?> dataMap && dataMap.get(key) instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
}
