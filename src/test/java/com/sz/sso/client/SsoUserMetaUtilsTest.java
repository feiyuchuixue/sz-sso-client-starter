package com.sz.sso.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SsoUserMetaUtils} 单元测试.
 * <p>
 * 覆盖 toMap / fromMap / fromEntries 往返无损、null 容忍、
 * createTime 格式化与解析、parseLong 多类型容忍等核心逻辑。
 * </p>
 */
@DisplayName("SsoUserMetaUtils 单元测试")
class SsoUserMetaUtilsTest {

    // ---------------------------------------------------------------
    // 完整对象往返（toMap → fromMap）
    // ---------------------------------------------------------------

    @Test
    @DisplayName("完整对象 toMap 后 fromMap 应往返无损")
    void toMap_thenFromMap_shouldBeIdempotent() {
        LocalDateTime now = LocalDateTime.of(2025, 6, 20, 10, 30, 0);
        SsoUserMeta original = SsoUserMeta.builder()
                .ssoUserId(100L)
                .username("admin")
                .nickname("管理员")
                .email("admin@sz.com")
                .phone("13800138000")
                .avatarUrl("https://cdn.sz.com/avatar/1.png")
                .createTime(now)
                .build();

        Map<String, Object> map = SsoUserMetaUtils.toMap(original);
        SsoUserMeta restored = SsoUserMetaUtils.fromMap(map);

        assertThat(restored.getSsoUserId()).isEqualTo(100L);
        assertThat(restored.getUsername()).isEqualTo("admin");
        assertThat(restored.getNickname()).isEqualTo("管理员");
        assertThat(restored.getEmail()).isEqualTo("admin@sz.com");
        assertThat(restored.getPhone()).isEqualTo("13800138000");
        assertThat(restored.getAvatarUrl()).isEqualTo("https://cdn.sz.com/avatar/1.png");
        assertThat(restored.getCreateTime()).isEqualTo(now);
    }

    // ---------------------------------------------------------------
    // toMap：null 容忍
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toMap(null) 应返回空 Map，不抛异常")
    void toMap_withNull_shouldReturnEmptyMap() {
        Map<String, Object> map = SsoUserMetaUtils.toMap(null);
        assertThat(map).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("toMap：ssoUserId 为 null 时 map 中对应值为 null")
    void toMap_nullSsoUserId_shouldPutNull() {
        SsoUserMeta meta = SsoUserMeta.builder().username("test").build();
        Map<String, Object> map = SsoUserMetaUtils.toMap(meta);
        assertThat(map).containsKey("ssoUserId");
        assertThat(map.get("ssoUserId")).isNull();
    }

    @Test
    @DisplayName("toMap：createTime 为 null 时 map 中对应值为 null")
    void toMap_nullCreateTime_shouldPutNull() {
        SsoUserMeta meta = SsoUserMeta.builder().ssoUserId(1L).build();
        Map<String, Object> map = SsoUserMetaUtils.toMap(meta);
        assertThat(map.get("createTime")).isNull();
    }

    // ---------------------------------------------------------------
    // toMap：ssoUserId 被序列化为 String
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toMap 中 ssoUserId 应被序列化为 String 类型")
    void toMap_ssoUserId_shouldBeSerializedAsString() {
        SsoUserMeta meta = SsoUserMeta.builder().ssoUserId(999L).build();
        Map<String, Object> map = SsoUserMetaUtils.toMap(meta);
        assertThat(map.get("ssoUserId")).isInstanceOf(String.class).isEqualTo("999");
    }

    // ---------------------------------------------------------------
    // toMap：createTime 格式化为 ISO_LOCAL_DATE_TIME 字符串
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toMap 中 createTime 应被格式化为 ISO_LOCAL_DATE_TIME 字符串")
    void toMap_createTime_shouldBeFormattedAsIso() {
        LocalDateTime time = LocalDateTime.of(2024, 1, 15, 8, 0, 0);
        SsoUserMeta meta = SsoUserMeta.builder().createTime(time).build();
        Map<String, Object> map = SsoUserMetaUtils.toMap(meta);
        String expected = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        assertThat(map.get("createTime")).isEqualTo(expected);
    }

    // ---------------------------------------------------------------
    // fromMap：null / 空 Map 容忍
    // ---------------------------------------------------------------

    @Test
    @DisplayName("fromMap(null) 应返回空 SsoUserMeta，不抛异常")
    void fromMap_withNull_shouldReturnEmptyMeta() {
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(null);
        assertThat(meta).isNotNull();
        assertThat(meta.getSsoUserId()).isNull();
        assertThat(meta.getUsername()).isNull();
    }

    @Test
    @DisplayName("fromMap(空 Map) 应返回空 SsoUserMeta，不抛异常")
    void fromMap_withEmptyMap_shouldReturnEmptyMeta() {
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(new HashMap<>());
        assertThat(meta).isNotNull();
        assertThat(meta.getSsoUserId()).isNull();
    }

    // ---------------------------------------------------------------
    // fromMap：parseLong 多类型容忍
    // ---------------------------------------------------------------

    @Test
    @DisplayName("fromMap：ssoUserId 为 Long 类型时应正确解析")
    void fromMap_ssoUserIdAsLong_shouldParse() {
        Map<String, Object> map = new HashMap<>();
        map.put("ssoUserId", 42L);
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(map);
        assertThat(meta.getSsoUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("fromMap：ssoUserId 为 Integer 类型时应自动转换为 Long")
    void fromMap_ssoUserIdAsInteger_shouldConvertToLong() {
        Map<String, Object> map = new HashMap<>();
        map.put("ssoUserId", 7);   // Integer
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(map);
        assertThat(meta.getSsoUserId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("fromMap：ssoUserId 为数字字符串时应解析为 Long")
    void fromMap_ssoUserIdAsString_shouldParseLong() {
        Map<String, Object> map = new HashMap<>();
        map.put("ssoUserId", "123");
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(map);
        assertThat(meta.getSsoUserId()).isEqualTo(123L);
    }

    @Test
    @DisplayName("fromMap：ssoUserId 为非法字符串时 ssoUserId 应为 null（不抛异常）")
    void fromMap_ssoUserIdAsInvalidString_shouldReturnNull() {
        Map<String, Object> map = new HashMap<>();
        map.put("ssoUserId", "not-a-number");
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(map);
        assertThat(meta.getSsoUserId()).isNull();
    }

    // ---------------------------------------------------------------
    // fromMap：parseLocalDateTime 容忍
    // ---------------------------------------------------------------

    @Test
    @DisplayName("fromMap：createTime 为 LocalDateTime 对象时直接返回")
    void fromMap_createTimeAsLocalDateTime_shouldReturnDirectly() {
        LocalDateTime time = LocalDateTime.of(2025, 3, 1, 12, 0, 0);
        Map<String, Object> map = new HashMap<>();
        map.put("createTime", time);
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(map);
        assertThat(meta.getCreateTime()).isEqualTo(time);
    }

    @Test
    @DisplayName("fromMap：createTime 为 ISO 字符串时应解析为 LocalDateTime")
    void fromMap_createTimeAsIsoString_shouldParse() {
        LocalDateTime time = LocalDateTime.of(2025, 3, 1, 12, 0, 0);
        String isoStr = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Map<String, Object> map = new HashMap<>();
        map.put("createTime", isoStr);
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(map);
        assertThat(meta.getCreateTime()).isEqualTo(time);
    }

    @Test
    @DisplayName("fromMap：createTime 为非法字符串时 createTime 应为 null（不抛异常）")
    void fromMap_createTimeAsInvalidString_shouldReturnNull() {
        Map<String, Object> map = new HashMap<>();
        map.put("createTime", "not-a-date");
        SsoUserMeta meta = SsoUserMetaUtils.fromMap(map);
        assertThat(meta.getCreateTime()).isNull();
    }

    // ---------------------------------------------------------------
    // fromEntries：与 fromMap 等价
    // ---------------------------------------------------------------

    @Test
    @DisplayName("fromEntries 结果应与 fromMap 完全等价")
    void fromEntries_shouldBeEquivalentToFromMap() {
        LocalDateTime now = LocalDateTime.of(2025, 6, 20, 10, 0, 0);
        SsoUserMeta original = SsoUserMeta.builder()
                .ssoUserId(55L)
                .username("test-user")
                .createTime(now)
                .build();

        Map<String, Object> map = SsoUserMetaUtils.toMap(original);

        SsoUserMeta fromMap     = SsoUserMetaUtils.fromMap(map);
        SsoUserMeta fromEntries = SsoUserMetaUtils.fromEntries(map.entrySet());

        assertThat(fromEntries.getSsoUserId()).isEqualTo(fromMap.getSsoUserId());
        assertThat(fromEntries.getUsername()).isEqualTo(fromMap.getUsername());
        assertThat(fromEntries.getCreateTime()).isEqualTo(fromMap.getCreateTime());
    }

    @Test
    @DisplayName("fromEntries(null) 应返回空 SsoUserMeta，不抛异常")
    void fromEntries_withNull_shouldReturnEmptyMeta() {
        SsoUserMeta meta = SsoUserMetaUtils.fromEntries(null);
        assertThat(meta).isNotNull();
        assertThat(meta.getSsoUserId()).isNull();
    }

    @Test
    @DisplayName("fromEntries(空集合) 应返回空 SsoUserMeta，不抛异常")
    void fromEntries_withEmptySet_shouldReturnEmptyMeta() {
        SsoUserMeta meta = SsoUserMetaUtils.fromEntries(new java.util.HashSet<>());
        assertThat(meta).isNotNull();
        assertThat(meta.getSsoUserId()).isNull();
    }

}
