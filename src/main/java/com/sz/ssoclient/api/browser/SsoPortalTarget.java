package com.sz.ssoclient.api.browser;

/** 允许进入认证中心的版本化业务目标。 */
public enum SsoPortalTarget {

    APPLICATIONS("/ucenter/applications"),
    PROFILE("/ucenter/profile"),
    ACCOUNT_SECURITY("/ucenter/password"),
    LOGIN_LOG("/ucenter/login-log");

    private final String targetPath;

    SsoPortalTarget(String targetPath) {
        this.targetPath = targetPath;
    }

    public String targetPath() {
        return targetPath;
    }
}
