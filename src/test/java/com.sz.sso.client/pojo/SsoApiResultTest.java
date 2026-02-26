package com.sz.sso.client.pojo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SsoApiResult} 单元测试.
 * <p>
 * 覆盖全部静态工厂方法、字段默认值、以及前端契约要求的 code/message 值。
 * </p>
 */
@DisplayName("SsoApiResult 单元测试")
class SsoApiResultTest {

    // ---------------------------------------------------------------
    // 默认值测试
    // ---------------------------------------------------------------

    @Test
    @DisplayName("默认 code 应为 '0000'")
    void defaultCode_shouldBe0000() {
        SsoApiResult<String> result = new SsoApiResult<>();
        assertThat(result.getCode()).isEqualTo("0000");
    }

    @Test
    @DisplayName("默认 message 应为 'SUCCESS'")
    void defaultMessage_shouldBeSuccess() {
        SsoApiResult<String> result = new SsoApiResult<>();
        assertThat(result.getMessage()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("默认 data 应为 null")
    void defaultData_shouldBeNull() {
        SsoApiResult<String> result = new SsoApiResult<>();
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("默认 param 不为 null（初始化为 new Object()）")
    void defaultParam_shouldNotBeNull() {
        SsoApiResult<String> result = new SsoApiResult<>();
        assertThat(result.getParam()).isNotNull();
    }

    // ---------------------------------------------------------------
    // success() 无参
    // ---------------------------------------------------------------

    @Test
    @DisplayName("success() 的 code 应为 '0000'")
    void successNoArg_codeShouldBe0000() {
        SsoApiResult<Void> result = SsoApiResult.success();
        assertThat(result.getCode()).isEqualTo("0000");
    }

    @Test
    @DisplayName("success() 的 message 应为 'SUCCESS'")
    void successNoArg_messageShouldBeSuccess() {
        SsoApiResult<Void> result = SsoApiResult.success();
        assertThat(result.getMessage()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("success() 的 data 应为 null")
    void successNoArg_dataShouldBeNull() {
        SsoApiResult<Void> result = SsoApiResult.success();
        assertThat(result.getData()).isNull();
    }

    // ---------------------------------------------------------------
    // success(T data)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("success(data) 应正确携带 data")
    void successWithData_shouldCarryData() {
        String payload = "hello";
        SsoApiResult<String> result = SsoApiResult.success(payload);
        assertThat(result.getData()).isEqualTo(payload);
    }

    @Test
    @DisplayName("success(data) 的 code 应为 '0000'")
    void successWithData_codeShouldBe0000() {
        SsoApiResult<Integer> result = SsoApiResult.success(42);
        assertThat(result.getCode()).isEqualTo("0000");
    }

    @Test
    @DisplayName("success(null) data 应为 null")
    void successWithNullData_dataShouldBeNull() {
        SsoApiResult<String> result = SsoApiResult.success(null);
        assertThat(result.getData()).isNull();
    }

    // ---------------------------------------------------------------
    // success(T data, Object param)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("success(data, param) 应同时携带 data 和 param")
    void successWithDataAndParam_shouldCarryBoth() {
        String data = "value";
        String param = "extra";
        SsoApiResult<String> result = SsoApiResult.success(data, param);
        assertThat(result.getData()).isEqualTo(data);
        assertThat(result.getParam()).isEqualTo(param);
    }

    @Test
    @DisplayName("success(data, null) param 应保留默认 Object（不被 null 覆盖）")
    void successWithNullParam_shouldKeepDefaultParam() {
        SsoApiResult<String> result = SsoApiResult.success("data", null);
        // null 时不覆盖 param，param 保持初始 new Object()
        assertThat(result.getParam()).isNotNull();
    }

    // ---------------------------------------------------------------
    // error(String code, String message)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("error() 应携带指定 code")
    void error_shouldCarryCode() {
        SsoApiResult<Void> result = SsoApiResult.error("5000", "Internal Error");
        assertThat(result.getCode()).isEqualTo("5000");
    }

    @Test
    @DisplayName("error() 应携带指定 message")
    void error_shouldCarryMessage() {
        SsoApiResult<Void> result = SsoApiResult.error("4001", "Unauthorized");
        assertThat(result.getMessage()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("error() 的 data 应为 null")
    void error_dataShouldBeNull() {
        SsoApiResult<String> result = SsoApiResult.error("4004", "Not Found");
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("error() 不应将 code 设为 '0000'（与 success 区分）")
    void error_codeShouldNotBeDefaultSuccess() {
        SsoApiResult<Void> result = SsoApiResult.error("9999", "Error");
        assertThat(result.getCode()).isNotEqualTo("0000");
    }

}
