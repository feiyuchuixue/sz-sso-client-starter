package com.sz.ssoclient.internal.messaging;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.util.SaResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoProtocolFields;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sa-Token 消息对象与 Starter 中立合同之间的唯一内部转换器。 */
public final class SsoMessageCodec {

    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            SsoProtocolFields.CLIENT,
            "clientId",
            "type",
            SsoProtocolFields.MSG_TYPE,
            SsoProtocolFields.SIGN,
            SsoProtocolFields.TIMESTAMP,
            SsoProtocolFields.NONCE);

    private final ObjectMapper objectMapper;

    public SsoMessageCodec() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    SsoMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Map<String, Object> businessPayload(SaSsoMessage message) {
        java.util.Objects.requireNonNull(message, "message");
        Map<String, Object> payload = new LinkedHashMap<>();
        message.forEach((key, value) -> {
            if (!ENVELOPE_FIELDS.contains(key)) {
                payload.put(key, normalize(value));
            }
        });
        return Collections.unmodifiableMap(payload);
    }

    public <T> SsoMessageResult<T> fromSaResult(SaResult result, Class<T> responseType) {
        if (result == null) {
            throw new IllegalStateException("SSO Server 未返回消息结果");
        }
        String code = SaResult.CODE_SUCCESS == result.getCode()
                ? SsoMessageResult.SUCCESS_CODE
                : String.valueOf(result.getCode());
        T data = convert(result.getData(), responseType);
        return new SsoMessageResult<>(code, result.getMsg(), data);
    }

    public <T> T convert(Object value, Class<T> targetType) {
        java.util.Objects.requireNonNull(targetType, "targetType");
        if (value == null || targetType == Void.class) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        return objectMapper.convertValue(value, targetType);
    }

    public static String requiredText(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("缺少必要消息字段: " + field);
        }
        return value.toString().trim();
    }

    public static long requiredLong(Map<String, Object> payload, String field) {
        String value = requiredText(payload, field);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("消息字段必须为整数: " + field, exception);
        }
    }

    public static boolean requiredBoolean(Map<String, Object> payload, String field) {
        String value = requiredText(payload, field);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("消息字段必须为布尔值: " + field);
    }

    public static List<Long> requiredLongs(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        List<Object> rawValues = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            rawValues.addAll(collection);
        } else if (value != null && value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                rawValues.add(Array.get(value, index));
            }
        } else if (value != null) {
            String text = value.toString().trim();
            if (text.startsWith("[") && text.endsWith("]")) {
                text = text.substring(1, text.length() - 1);
            }
            if (!text.isBlank()) {
                for (String item : text.split(",")) {
                    rawValues.add(item.trim());
                }
            }
        }
        if (rawValues.isEmpty()) {
            throw new IllegalArgumentException("缺少必要消息字段: " + field);
        }
        List<Long> values = new ArrayList<>(rawValues.size());
        for (Object rawValue : rawValues) {
            if (rawValue == null || rawValue.toString().isBlank()) {
                throw new IllegalArgumentException("消息字段包含空用户 ID: " + field);
            }
            try {
                values.add(Long.valueOf(rawValue.toString().trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("消息字段包含非法用户 ID: " + field, exception);
            }
        }
        return List.copyOf(values);
    }

    static Set<String> envelopeFields() {
        return ENVELOPE_FIELDS;
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            collection.forEach(item -> copy.add(normalize(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray()) {
            List<Object> copy = new ArrayList<>(Array.getLength(value));
            for (int index = 0; index < Array.getLength(value); index++) {
                copy.add(normalize(Array.get(value, index)));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String stringKey) || stringKey.isBlank()) {
                    throw new IllegalArgumentException("消息 payload 的 Map key 必须是非空字符串");
                }
                copy.put(stringKey, normalize(item));
            });
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("消息 payload 包含非中立类型: " + value.getClass().getName());
    }
}
