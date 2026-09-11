package com.sz.ssoclient.spi;

/** 宿主本地 Session 的精确撤销句柄；值由宿主解释。 */
public record SsoClientSessionHandle(String value) {

    public SsoClientSessionHandle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
