package com.sz.sso.client.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录状态信息 DTO.
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 */
@Data
@Schema(description = "登录状态信息")
public class LoginStatus {

    @Schema(description = "当前是否登录")
    private boolean hasLogin;

    @Schema(description = "登录ID")
    private Object loginId;

}
