package com.sz.ssoclient.spi;

/** 宿主可选的一次性默认访问初始化适配器。 */
public interface SsoDefaultAccessInitializer {

    /** 按不透明本地用户 ID 幂等初始化默认访问。 */
    SsoDefaultAccessStatus initialize(String localUserId);
}
