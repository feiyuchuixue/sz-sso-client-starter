package com.sz.ssoclient.api.browser;

/** 创建 Browser 登录事务的唯一公开请求。 */
public record SsoLoginTransactionRequest(String back, String theme) {
}
