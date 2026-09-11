package com.sz.ssoclient.api.browser;

/** Browser HTTP 稳定错误码。 */
public final class SsoWebCodes {

    public static final String SUCCESS = "0000";
    public static final String REQUEST_INVALID = "SSO-1001";
    public static final String REQUEST_ORIGIN_INVALID = "SSO-1002";
    public static final String CLIENT_DISABLED = "SSO-2002";
    public static final String REDIRECT_INVALID = "SSO-3001";
    public static final String LOGIN_TRANSACTION_INVALID = "SSO-3002";
    public static final String LOGIN_TICKET_INVALID = "SSO-3003";
    public static final String LOGIN_TICKET_EXPIRED = "SSO-3004";
    public static final String LOGIN_SECURITY_FAILURE = "SSO-3005";
    public static final String LOGIN_BROWSER_BINDING_FAILURE = "SSO-3006";
    public static final String SESSION_REQUIRED = "SSO-4001";
    public static final String CLIENT_FORBIDDEN = "SSO-4002";
    public static final String PORTAL_TARGET_INVALID = "SSO-4003";
    public static final String SESSION_INVALID = "SSO-4004";
    public static final String SERVER_UNAVAILABLE = "SSO-5001";
    public static final String SERVER_FAILURE = "SSO-5002";

    private SsoWebCodes() {
        throw new IllegalStateException("Constant class");
    }
}
