package com.sz.ssoclient.spi;

/** 一次性默认访问初始化的真实业务结果。 */
public enum SsoDefaultAccessStatus {

    /** 本次已成功应用默认访问。 */
    APPLIED,

    /** 用户已有其他有效访问权限，不需要新增默认访问。 */
    EXISTING_ACCESS,

    /** 该 mapping 已完成过一次性处理，保持当前角色状态。 */
    ALREADY_COMPLETED,

    /** 宿主明确关闭默认访问能力，本次只终结一次性处理标记。 */
    DISABLED
}
