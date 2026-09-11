package com.sz.ssoclient.internal.login.transaction;

/** 60 秒登录事务只允许单向进入交换态，消费尝试结束后直接删除。 */
public enum SsoLoginTransactionStatus {
    CREATED,
    EXCHANGING
}
