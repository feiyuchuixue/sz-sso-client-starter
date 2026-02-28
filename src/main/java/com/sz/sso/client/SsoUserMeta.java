package com.sz.sso.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SSO 用户元信息.
 * <p>
 * 用于 SSO Server 与 Client 之间传输用户基本信息。
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoUserMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * SSO 用户 ID
     */
    private Long ssoUserId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像 URL
     */
    private String avatarUrl;

    /**
     * 注册/创建时间
     */
    private LocalDateTime createTime;

}
