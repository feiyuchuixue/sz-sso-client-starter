package com.sz.sso.client.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SSO 登录结果 DTO.
 * <p>
 * 框架无关的登录响应对象，由 {@link com.sz.sso.client.SsoSessionCreator} 实现方填充并返回。
 * 替代原 sz 框架专属的 {@code LoginVO}，使 starter 不依赖任何业务框架的具体类型。
 * </p>
 *
 * @author sz
 * @version 2.0
 * @since 2025/6/20
 */
@Data
@Schema(description = "SSO 登录结果")
public class SsoLoginResult {

    @Schema(description = "访问令牌 access_token")
    private String accessToken;

    @Schema(description = "令牌有效期（秒）")
    private Long expireIn;

    @Schema(description = "用户信息")
    private Object userInfo;

    /**
     * 快速构建 SsoLoginResult.
     *
     * @param accessToken 访问令牌
     * @param expireIn    有效期（秒）
     * @param userInfo    用户信息对象
     * @return SsoLoginResult
     */
    public static SsoLoginResult of(String accessToken, Long expireIn, Object userInfo) {
        SsoLoginResult result = new SsoLoginResult();
        result.setAccessToken(accessToken);
        result.setExpireIn(expireIn);
        result.setUserInfo(userInfo);
        return result;
    }

}
