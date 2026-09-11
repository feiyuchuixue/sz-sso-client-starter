package com.sz.ssoclient.api.browser;

import com.sz.ssocore.signout.SsoLocalOutcome;
import com.sz.ssocore.signout.SsoPropagationOutcome;
import com.sz.ssocore.signout.SsoPropagationReason;
import com.sz.ssocore.signout.SsoSignoutReceipt;

import java.util.Objects;

/** 设备或账号退出的本地与传播双轴真实结果。 */
public record SsoSignoutResponse(
        SsoLocalOutcome localOutcome,
        SsoPropagationOutcome propagationOutcome,
        SsoPropagationReason propagationReason,
        SsoSignoutReceipt receipt) {

    public SsoSignoutResponse {
        Objects.requireNonNull(localOutcome, "localOutcome");
        Objects.requireNonNull(propagationOutcome, "propagationOutcome");
        if (propagationOutcome == SsoPropagationOutcome.NOT_REQUESTED
                && propagationReason == null) {
            throw new IllegalArgumentException("NOT_REQUESTED requires propagationReason");
        }
        if (propagationOutcome != SsoPropagationOutcome.NOT_REQUESTED
                && propagationReason != null) {
            throw new IllegalArgumentException("propagationReason is only valid for NOT_REQUESTED");
        }
        if (propagationOutcome == SsoPropagationOutcome.ACCEPTED && receipt == null) {
            throw new IllegalArgumentException("ACCEPTED requires receipt");
        }
        if (propagationOutcome != SsoPropagationOutcome.ACCEPTED && receipt != null) {
            throw new IllegalArgumentException("receipt is only valid for ACCEPTED");
        }
    }
}
