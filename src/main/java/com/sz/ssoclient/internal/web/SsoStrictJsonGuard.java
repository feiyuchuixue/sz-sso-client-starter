package com.sz.ssoclient.internal.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.ssoclient.api.browser.SsoWebCodes;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/** 仅对六条 SSO Browser 路由执行严格字段白名单绑定。 */
public final class SsoStrictJsonGuard {

    private final ObjectMapper objectMapper;

    public SsoStrictJsonGuard(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public <T> T bind(String body, Class<T> targetType, Set<String> allowedFields) {
        Objects.requireNonNull(targetType, "targetType");
        Set<String> allowed = Set.copyOf(Objects.requireNonNull(allowedFields, "allowedFields"));
        JsonNode json = parse(body);
        if (json == null || !json.isObject()) {
            throw invalid("请求体必须是 JSON 对象");
        }
        Set<String> unknown = new HashSet<>();
        Iterator<String> fields = json.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                unknown.add(field);
            }
        }
        if (!unknown.isEmpty()) {
            throw invalid("请求包含不允许的字段");
        }
        try {
            return objectMapper.treeToValue(json, targetType);
        } catch (JsonProcessingException exception) {
            throw invalid("请求字段格式非法");
        }
    }

    public void requireEmptyObject(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        JsonNode json = parse(body);
        if (json == null || !json.isObject() || json.size() != 0) {
            throw invalid("该路由不接受请求字段");
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw invalid("请求字段格式非法");
        }
    }

    private static SsoClientWebException invalid(String message) {
        return new SsoClientWebException(SsoWebCodes.REQUEST_INVALID, 400, message);
    }
}
