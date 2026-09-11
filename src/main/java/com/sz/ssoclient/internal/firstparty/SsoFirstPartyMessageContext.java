package com.sz.ssoclient.internal.firstparty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首方 Platform Handler 可见的中立、递归不可变上下文。
 * <p>该 internal friend contract 不属于普通 Client 的兼容承诺。</p>
 */
public record SsoFirstPartyMessageContext(
        String messageType,
        Map<String, Object> payload) {

    public SsoFirstPartyMessageContext {
        requireText(messageType, "messageType");
        payload = immutablePayload(payload);
    }

    private static Map<String, Object> immutablePayload(Map<String, Object> source) {
        if (source == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            requireText(key, "payload key");
            copy.put(key, normalize(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(normalize(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("payload map keys must be strings");
                }
                requireText(stringKey, "payload key");
                copy.put(stringKey, normalize(item));
            });
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("unsupported payload value type: " + value.getClass().getName());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
