package com.sz.sso.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * {@link SsoCoreConstant} 单元测试.
 * <p>
 * 验证常量值的正确性（防止误改），以及工具类构造器的防实例化保护。
 * </p>
 */
@DisplayName("SsoCoreConstant 单元测试")
class SsoCoreConstantTest {

    @Test
    @DisplayName("MESSAGE_REGISTER 常量值应为 'REGISTER'")
    void messageRegister_shouldBeRegister() {
        assertThat(SsoCoreConstant.MESSAGE_REGISTER).isEqualTo("REGISTER");
    }

    @Test
    @DisplayName("MESSAGE_USER_CHECK 常量值应为 'USER_CHECK'")
    void messageUserCheck_shouldBeUserCheck() {
        assertThat(SsoCoreConstant.MESSAGE_USER_CHECK).isEqualTo("USER_CHECK");
    }

    @Test
    @DisplayName("两个常量值不应相同（避免消息路由混淆）")
    void twoConstants_shouldBeDifferent() {
        assertThat(SsoCoreConstant.MESSAGE_REGISTER)
                .isNotEqualTo(SsoCoreConstant.MESSAGE_USER_CHECK);
    }

    @Test
    @DisplayName("工具类构造器应抛出 IllegalStateException，禁止实例化")
    void constructor_shouldThrowIllegalStateException() throws Exception {
        Constructor<SsoCoreConstant> constructor = SsoCoreConstant.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Constant class");
    }

}
