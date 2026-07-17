package com.sz.ssoclient.clientaccess.web.dto;

/** Client-local login result returned to the Web SDK. */
public record LoginCallbackWebResponse(
        String accessToken,
        Long expireInSeconds,
        String tokenType,
        String back,
        Object user) {
}
