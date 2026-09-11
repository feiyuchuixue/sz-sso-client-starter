package com.sz.ssoclient.api;

import com.sz.ssocore.SsoMessageResult;

/** 宿主调用的本地 SSO 超管状态同步操作。 */
public interface SsoClientSuperAdminSyncOperations {

    /**
     * 将本地用户的 SSO 超管变化同步到 Server；失败结果必须原样返回，不能伪造成功。
     *
     * @param localUserId 宿主不透明本地用户 ID
     * @param superAdmin 是否为 SSO 超管
     * @return Server 的中立消息结果
     */
    SsoMessageResult<Void> syncSuperAdmin(String localUserId, boolean superAdmin);
}
