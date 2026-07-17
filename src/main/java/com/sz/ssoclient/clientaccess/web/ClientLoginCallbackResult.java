package com.sz.ssoclient.clientaccess.web;

import com.sz.ssoclient.pojo.SsoLoginResult;

/** Client-local login result plus the server-side validated navigation target. */
public record ClientLoginCallbackResult(SsoLoginResult loginResult, String back) {
}
