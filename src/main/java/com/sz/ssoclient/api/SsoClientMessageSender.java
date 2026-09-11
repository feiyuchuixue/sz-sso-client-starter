package com.sz.ssoclient.api;

import com.sz.ssocore.SsoMessageResult;

import java.util.Map;

/** 宿主调用的受类型约束自定义 SSO 消息发送入口。 */
public interface SsoClientMessageSender {

    /**
     * 发送 Core 消息表中的自定义消息，并按中立类型读取响应。
     *
     * @param messageType Core 定义的消息类型
     * @param payload 不含可信 Client 身份和签名字段的业务载荷
     * @param responseType 中立响应数据类型
     * @param <T> 响应数据类型
     * @return 中立消息结果
     */
    <T> SsoMessageResult<T> sendCustom(
            String messageType,
            Map<String, ?> payload,
            Class<T> responseType);
}
