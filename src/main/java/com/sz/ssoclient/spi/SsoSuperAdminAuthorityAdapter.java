package com.sz.ssoclient.spi;

/** 宿主可选的本地 SSO 超管权限适配器。 */
public interface SsoSuperAdminAuthorityAdapter {

    /** 幂等赋予或撤销本地 SSO 超管；失败时抛异常，禁止返回假成功。 */
    void apply(String localUserId, boolean superAdmin);
}
