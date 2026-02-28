package com.sz.sso.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * SSO 用户元信息工具类.
 * <p>
 * 提供 {@link SsoUserMeta} 与 {@link Map} 之间的转换方法，
 * 用于 SSO 消息的序列化/反序列化。
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 */
public class SsoUserMetaUtils {

    private SsoUserMetaUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static final String SSO_USER_ID = "ssoUserId";
    private static final String USERNAME = "username";
    private static final String NICKNAME = "nickname";
    private static final String EMAIL = "email";
    private static final String PHONE = "phone";
    private static final String AVATAR_URL = "avatarUrl";
    private static final String CREATE_TIME = "createTime";

    private static final DateTimeFormatter CREATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 将 SsoUserMeta 转换为 Map.
     *
     * @param meta 用户元信息
     * @return Map 表示
     */
    public static Map<String, Object> toMap(SsoUserMeta meta) {
        Map<String, Object> map = new HashMap<>();
        if (meta == null) {
            return map;
        }
        map.put(SSO_USER_ID, meta.getSsoUserId() != null ? String.valueOf(meta.getSsoUserId()) : null);
        map.put(USERNAME, meta.getUsername());
        map.put(NICKNAME, meta.getNickname());
        map.put(EMAIL, meta.getEmail());
        map.put(PHONE, meta.getPhone());
        map.put(AVATAR_URL, meta.getAvatarUrl());
        map.put(CREATE_TIME, formatLocalDateTime(meta.getCreateTime()));
        return map;
    }

    /**
     * 从 Map 构建 SsoUserMeta.
     *
     * @param map Map 数据
     * @return SsoUserMeta
     */
    public static SsoUserMeta fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return SsoUserMeta.builder().build();
        }
        return SsoUserMeta.builder()
                .ssoUserId(parseLong(map.get(SSO_USER_ID)))
                .username(asString(map.get(USERNAME)))
                .nickname(asString(map.get(NICKNAME)))
                .email(asString(map.get(EMAIL)))
                .phone(asString(map.get(PHONE)))
                .avatarUrl(asString(map.get(AVATAR_URL)))
                .createTime(parseLocalDateTime(map.get(CREATE_TIME)))
                .build();
    }

    /**
     * 从 Map.Entry 集合构建 SsoUserMeta.
     *
     * @param entries Map.Entry 集合
     * @return SsoUserMeta
     */
    public static SsoUserMeta fromEntries(Set<Map.Entry<String, Object>> entries) {
        if (entries == null || entries.isEmpty()) {
            return SsoUserMeta.builder().build();
        }
        // 注意：不能用 Collectors.toMap，它不允许 null value（会在 HashMap.merge 中抛 NPE）
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return fromMap(map);
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatLocalDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(CREATE_TIME_FORMATTER);
    }

    private static LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value), CREATE_TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

}
