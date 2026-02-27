package com.sz.sso.client;

import com.sz.sso.client.pojo.SsoUserContext;
import lombok.extern.slf4j.Slf4j;

/**
 * SsoRoleBindingService 的内置默认实现.
 * <p>
 * 当业务方未自定义 {@link SsoRoleBindingService} Bean 时自动生效。
 * 默认行为：若用户本地尚无角色记录，则打印 warn 日志提醒业务方实现该接口，
 * 不做任何实际的角色写入操作。
 * {@code ssoContext} 已存入 TokenSession，业务方可通过
 * {@link SsoClientUtil#getSsoContext()} 自行读取。
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/23
 * @see SsoRoleBindingService
 */
@Slf4j
public class DefaultSsoRoleBindingService implements SsoRoleBindingService {

    @Override
    public void applyRoles(Long localUserId, SsoUserContext ssoContext, Object loginUser) {
        log.warn("[SSO] 用户 {} 首次登录，平台提议角色 key={}（isSuperAdmin={}），" +
                 "请实现 SsoRoleBindingService 完成本地权限初始化",
                 localUserId, ssoContext.getSsoRoleKey(), ssoContext.getIsSuperAdmin());
        // 默认不做任何操作，ssoContext 已存入 Session，业务方可自行读取
    }

}
