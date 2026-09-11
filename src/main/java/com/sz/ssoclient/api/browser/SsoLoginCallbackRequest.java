package com.sz.ssoclient.api.browser;

/** Browser 登录回调的单次交换请求。 */
public record SsoLoginCallbackRequest(String ticket, String state) {
}
