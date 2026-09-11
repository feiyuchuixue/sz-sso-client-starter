package com.sz.ssoclient.spi;

import com.sz.ssocore.SsoUserMeta;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;

import java.util.Collection;

/** 宿主可选的批量身份 readiness 与显式 preparation 适配器。 */
public interface SsoClientIdentityPreparationService {

    /** 纯只读检查所选中心用户，不创建账户、不补 mapping、不修改授权。 */
    SsoClientUserReadinessBatchResult checkUsers(
            Collection<SsoUserMeta> users,
            SsoClientGrantPurpose purpose);

    /** 仅为显式选择的中心用户逐项执行幂等身份准备，保留部分成功与失败明细。 */
    SsoClientUserPreparationBatchResult prepareUsers(
            Collection<SsoUserMeta> users,
            SsoClientGrantPurpose purpose);
}
