package com.sz.sso.client.pojo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoginStatus} 单元测试.
 * <p>
 * 验证 Lombok {@code @Data} 生成的 getter/setter 正确性，以及布尔字段的默认值。
 * </p>
 */
@DisplayName("LoginStatus 单元测试")
class LoginStatusTest {

    @Test
    @DisplayName("默认 hasLogin 应为 false")
    void defaultHasLogin_shouldBeFalse() {
        LoginStatus status = new LoginStatus();
        assertThat(status.isHasLogin()).isFalse();
    }

    @Test
    @DisplayName("默认 loginId 应为 null")
    void defaultLoginId_shouldBeNull() {
        LoginStatus status = new LoginStatus();
        assertThat(status.getLoginId()).isNull();
    }

    @Test
    @DisplayName("setHasLogin(true) 后 isHasLogin() 应返回 true")
    void setHasLoginTrue_shouldReturnTrue() {
        LoginStatus status = new LoginStatus();
        status.setHasLogin(true);
        assertThat(status.isHasLogin()).isTrue();
    }

    @Test
    @DisplayName("setLoginId 后 getLoginId() 应返回设置的值")
    void setLoginId_shouldBeRetrievable() {
        LoginStatus status = new LoginStatus();
        status.setLoginId(42L);
        assertThat(status.getLoginId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("loginId 可以是 String 类型（框架无关）")
    void loginId_canBeString() {
        LoginStatus status = new LoginStatus();
        status.setLoginId("user-001");
        assertThat(status.getLoginId()).isEqualTo("user-001");
    }

    @Test
    @DisplayName("两个字段可以独立设置")
    void twoFields_shouldBeIndependent() {
        LoginStatus status = new LoginStatus();
        status.setHasLogin(true);
        status.setLoginId(99L);

        assertThat(status.isHasLogin()).isTrue();
        assertThat(status.getLoginId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Lombok @Data equals：字段相同的两个实例应相等")
    void equals_sameFields_shouldBeEqual() {
        LoginStatus a = new LoginStatus();
        a.setHasLogin(true);
        a.setLoginId(1L);

        LoginStatus b = new LoginStatus();
        b.setHasLogin(true);
        b.setLoginId(1L);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Lombok @Data equals：字段不同的两个实例应不相等")
    void equals_differentFields_shouldNotBeEqual() {
        LoginStatus a = new LoginStatus();
        a.setHasLogin(true);

        LoginStatus b = new LoginStatus();
        b.setHasLogin(false);

        assertThat(a).isNotEqualTo(b);
    }

}
