package com.sz.ssoclient.spi;

/**
 * Client 本地默认角色 key 提供者 SPI.
 */
public interface SsoClientRoleProvider {

    String getDefaultRoleKey();
}

