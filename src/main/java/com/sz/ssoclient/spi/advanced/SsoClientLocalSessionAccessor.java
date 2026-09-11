package com.sz.ssoclient.spi.advanced;

import com.sz.ssoclient.spi.SsoClientSessionHandle;
import com.sz.ssocore.signout.SsoLocalOutcome;

/** Starter 读取和精确撤销可信 Client 本地会话的高级 SPI。 */
public interface SsoClientLocalSessionAccessor {

    /** 返回当前可信本地会话快照。 */
    SsoClientLocalSession current();

    /** 仅撤销指定精确 Session 句柄。 */
    SsoLocalOutcome revokeExact(SsoClientSessionHandle handle);

    /** 撤销不透明本地用户在同一可信 deviceId 下的会话。 */
    SsoLocalOutcome revokeDevice(String localUserId, String deviceId);

    /** 撤销不透明本地用户在当前 Client 中的全部 SSO 会话。 */
    SsoLocalOutcome revokeAccount(String localUserId);
}
