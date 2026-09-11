package com.sz.ssoclient.internal.firstparty;

import com.sz.ssocore.SsoMessageResult;

/**
 * 首方 Platform 的受控 typed Handler 贡献点。
 * <p>该 internal friend contract 不属于普通 Client 的兼容承诺。</p>
 */
public interface SsoFirstPartyMessageContribution {

    /** 返回唯一贡献的 Platform 保留消息类型。 */
    String messageType();

    /** 使用中立上下文处理首方消息。 */
    SsoMessageResult<?> handle(SsoFirstPartyMessageContext context);
}
