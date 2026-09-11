package com.sz.ssoclient.internal.firstparty;

import com.sz.ssocore.SsoMessageResult;

import java.util.Map;

/**
 * 首方 Platform 复用 Starter 唯一消息传输的 typed 入口。
 * <p>该 internal friend contract 不属于普通 Client 的兼容承诺。</p>
 */
public interface SsoFirstPartyMessageTransport {

    /** 发送首方 typed 消息到 Server。 */
    <T> SsoMessageResult<T> sendToServer(
            String messageType,
            Map<String, ?> payload,
            Class<T> responseType);
}
