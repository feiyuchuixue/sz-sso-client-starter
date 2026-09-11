package com.sz.ssoclient.spi.advanced;

import com.sz.ssocore.SsoMessageResult;

/** 普通 Client 可选的高级自定义消息处理 SPI；仅允许注册全局唯一的 CUSTOM_* 类型。 */
public interface SsoClientMessageHandler {

    /** 返回本 Handler 唯一处理的 CUSTOM_* 消息类型。 */
    String messageType();

    /** 使用中立、不可变上下文处理消息。 */
    SsoMessageResult<?> handle(SsoClientMessageContext context);
}
