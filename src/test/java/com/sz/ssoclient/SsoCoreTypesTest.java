package com.sz.ssoclient;

import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSO core 常量单元测试.
 * <p>
 * 验证消息类型与协议字段值的正确性（防止误改），以及工具类构造器的防实例化保护。
 * </p>
 */
@DisplayName("SSO core 常量单元测试")
class SsoCoreTypesTest {

    @Test
    @DisplayName("REGISTER 消息类型应为 'REGISTER'")
    void messageRegister_shouldBeRegister() {
        assertThat(SsoMessageTypes.REGISTER).isEqualTo("REGISTER");
    }

    @Test
    @DisplayName("USER_CHECK 消息类型应为 'USER_CHECK'")
    void messageUserCheck_shouldBeUserCheck() {
        assertThat(SsoMessageTypes.USER_CHECK).isEqualTo("USER_CHECK");
    }

    @Test
    @DisplayName("TokenSession 超管字段应为 'isSuperAdmin'")
    void superAdminSessionField_shouldBeIsSuperAdmin() {
        assertThat(SsoProtocolFields.IS_SUPER_ADMIN).isEqualTo("isSuperAdmin");
    }

    @Test
    @DisplayName("两个消息类型值不应相同（避免消息路由混淆）")
    void twoConstants_shouldBeDifferent() {
        assertThat(SsoMessageTypes.REGISTER).isNotEqualTo(SsoMessageTypes.USER_CHECK);
    }

    @Test
    @DisplayName("消息类型工具类构造器应抛出 IllegalStateException，禁止实例化")
    void messageTypesConstructor_shouldThrowIllegalStateException() throws Exception {
        Constructor<SsoMessageTypes> constructor = SsoMessageTypes.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Constant class");
    }

}