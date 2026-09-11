package com.sz.ssoclient.api.browser;

import com.sz.ssocore.signout.SsoLocalOutcome;

import java.util.Objects;

/** 只退出当前 Client Session 的真实结果。 */
public record SsoLocalLogoutResponse(SsoLocalOutcome localOutcome) {

    public SsoLocalLogoutResponse {
        Objects.requireNonNull(localOutcome, "localOutcome");
    }
}
