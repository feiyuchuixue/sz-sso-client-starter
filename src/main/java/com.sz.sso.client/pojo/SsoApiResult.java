package com.sz.sso.client.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * SSO Starter 统一响应包装.
 * <p>
 * 字段结构与 sz-common-core 的 {@code ApiResult} 完全一致（{@code code}、{@code message}、
 * {@code data}、{@code param}），确保前端 axios 拦截器对响应格式的假定成立。
 * </p>
 * <p>
 * 本类刻意不依赖 {@code sz-common-core}，以保持 starter 框架无关性。
 * 若未来项目将 ApiResult 拆为独立公共 JAR 发布，可直接替换本类。
 * </p>
 *
 * @param <T> 响应数据类型
 * @author sz
 * @version 1.0
 * @since 2025/6/23
 */
@Data
public class SsoApiResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "自定义响应码", example = "0000")
    public String code = "0000";

    @Schema(description = "响应信息", example = "SUCCESS")
    public String message = "SUCCESS";

    @Schema(description = "响应数据")
    public T data;

    @Schema(description = "额外参数")
    private Object param = new Object();

    /**
     * 构建成功响应（无数据）.
     */
    public static <T> SsoApiResult<T> success() {
        SsoApiResult<T> result = new SsoApiResult<>();
        result.data = null;
        return result;
    }

    /**
     * 构建成功响应（含数据）.
     *
     * @param data 响应数据
     */
    public static <T> SsoApiResult<T> success(T data) {
        SsoApiResult<T> result = new SsoApiResult<>();
        result.data = data;
        return result;
    }

    /**
     * 构建成功响应（含数据和额外参数）.
     *
     * @param data  响应数据
     * @param param 额外参数
     */
    public static <T> SsoApiResult<T> success(T data, Object param) {
        SsoApiResult<T> result = new SsoApiResult<>();
        result.data = data;
        if (param != null) {
            result.param = param;
        }
        return result;
    }

    /**
     * 构建失败响应.
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public static <T> SsoApiResult<T> error(String code, String message) {
        SsoApiResult<T> result = new SsoApiResult<>();
        result.code = code;
        result.message = message;
        result.data = null;
        return result;
    }

}
