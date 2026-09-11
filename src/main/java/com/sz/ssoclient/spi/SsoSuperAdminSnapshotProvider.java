package com.sz.ssoclient.spi;

import java.util.Collection;

/** 宿主可选的本地 SSO 超管完整快照提供者。 */
public interface SsoSuperAdminSnapshotProvider {

    /** 返回全部本地 SSO 超管的不透明 localUserId；不得返回分页或部分结果。 */
    Collection<String> listAllSsoSuperAdminUserIds();
}
