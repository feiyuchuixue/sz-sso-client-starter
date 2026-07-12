package com.sz.ssoclient.spi;

import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;

import java.util.Collection;

/**
 * 目标 Client 的用户准备 SPI。
 * <p>readiness 检查必须只读、无副作用；prepare 可按 Client 自身规则创建账号或补建映射，并应支持幂等调用。</p>
 */
public interface SsoClientUserProvisioningService {

    /**
     * 只读检查 SSO 用户在当前 Client 的就绪状态，不得创建本地账号或映射。
     *
     * @param centerIds SSO 用户 ID 集合
     * @param purpose 授权用途，仅用于审计和 Client 策略
     * @return 对应每个输入用户的就绪检查结果
     */
    SsoClientUserReadinessBatchResult checkUsers(Collection<?> centerIds, SsoClientGrantPurpose purpose);

    /**
     * 按 Client 自身规则准备本地账号或映射；单项失败不得中断整批，重复请求应保持幂等。
     *
     * @param centerIds SSO 用户 ID 集合
     * @param purpose 授权用途，仅用于审计和 Client 策略
     * @return 对应每个输入用户的准备结果
     */
    SsoClientUserPreparationBatchResult prepareUsers(Collection<?> centerIds, SsoClientGrantPurpose purpose);
}
