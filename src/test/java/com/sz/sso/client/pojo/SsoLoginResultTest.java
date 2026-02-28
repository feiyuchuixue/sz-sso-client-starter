package com.sz.sso.client.pojo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SsoLoginResult} 单元测试.
 * <p>
 * 验证静态工厂方法 {@code of()} 的字段赋值以及 Lombok {@code @Data} 的 getter/setter 正确性。
 * </p>
 */
@DisplayName("SsoLoginResult 单元测试")
class SsoLoginResultTest {

    @Test
    @DisplayName("of() 应正确设置 accessToken")
    void of_shouldSetAccessToken() {
        SsoLoginResult result = SsoLoginResult.of("my-token", 3600L, "userInfo");
        assertThat(result.getAccessToken()).isEqualTo("my-token");
    }

    @Test
    @DisplayName("of() 应正确设置 expireIn")
    void of_shouldSetExpireIn() {
        SsoLoginResult result = SsoLoginResult.of("token", 7200L, null);
        assertThat(result.getExpireIn()).isEqualTo(7200L);
    }

    @Test
    @DisplayName("of() 应正确设置 userInfo")
    void of_shouldSetUserInfo() {
        Object userInfo = new Object();
        SsoLoginResult result = SsoLoginResult.of("token", 3600L, userInfo);
        assertThat(result.getUserInfo()).isSameAs(userInfo);
    }

    @Test
    @DisplayName("of() userInfo 为 null 时不应抛异常")
    void of_withNullUserInfo_shouldNotThrow() {
        SsoLoginResult result = SsoLoginResult.of("token", 0L, null);
        assertThat(result.getUserInfo()).isNull();
    }

    @Test
    @DisplayName("of() accessToken 为 null 时不应抛异常")
    void of_withNullToken_shouldNotThrow() {
        SsoLoginResult result = SsoLoginResult.of(null, 0L, null);
        assertThat(result.getAccessToken()).isNull();
    }

    @Test
    @DisplayName("Lombok @Data setter 应正常工作")
    void setters_shouldWork() {
        SsoLoginResult result = new SsoLoginResult();
        result.setAccessToken("new-token");
        result.setExpireIn(1000L);
        result.setUserInfo("user");

        assertThat(result.getAccessToken()).isEqualTo("new-token");
        assertThat(result.getExpireIn()).isEqualTo(1000L);
        assertThat(result.getUserInfo()).isEqualTo("user");
    }

    @Test
    @DisplayName("expireIn 为 0 时不应抛异常（边界值）")
    void of_withZeroExpireIn_shouldNotThrow() {
        SsoLoginResult result = SsoLoginResult.of("token", 0L, null);
        assertThat(result.getExpireIn()).isEqualTo(0L);
    }

    @Test
    @DisplayName("expireIn 为 -1 时不应抛异常（Sa-Token 永不过期约定）")
    void of_withMinusOneExpireIn_shouldNotThrow() {
        SsoLoginResult result = SsoLoginResult.of("token", -1L, null);
        assertThat(result.getExpireIn()).isEqualTo(-1L);
    }

}
