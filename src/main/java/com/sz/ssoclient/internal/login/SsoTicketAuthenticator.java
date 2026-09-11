package com.sz.ssoclient.internal.login;

import cn.dev33.satoken.sso.exception.SaSsoException;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaResult;

import java.util.Objects;

/** 将 Sa-Token 单次 Ticket 校验结果收口为可信 SSO 用户与中心设备身份。 */
public class SsoTicketAuthenticator {

    private final TicketChecker ticketChecker;

    public SsoTicketAuthenticator(SaSsoClientTemplate template) {
        this(ticket -> exchangeTicket(template, ticket));
    }

    SsoTicketAuthenticator(TicketChecker ticketChecker) {
        this.ticketChecker = Objects.requireNonNull(ticketChecker, "ticketChecker");
    }

    public AuthenticatedTicket authenticate(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("ticket 不能为空");
        }
        CheckedTicket checked = ticketChecker.check(ticket);
        Object centerId = checked == null ? null : checked.centerId();
        if (centerId == null) {
            throw new IllegalStateException("SSO Server 未返回可信 Ticket 用户身份");
        }
        String deviceId = checked.deviceId();
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalStateException("SSO Server 未返回可信 Ticket 设备身份");
        }
        try {
            return new AuthenticatedTicket(
                    Long.parseLong(centerId.toString()),
                    deviceId);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("SSO Server 返回了非法 Ticket 用户身份", exception);
        }
    }

    private static CheckedTicket exchangeTicket(
            SaSsoClientTemplate template,
            String ticket) {
        SaResult result = template.pushMessageAsSaResult(
                template.buildCheckTicketMessage(ticket, null));
        if (result == null || result.getCode() == null || result.getCode() != SaResult.CODE_SUCCESS) {
            throw new SaSsoException("SSO Ticket 校验失败").setCode(30005);
        }
        Object deviceId = result.get(template.paramName.deviceId);
        return new CheckedTicket(
                result.get(template.paramName.loginId),
                deviceId == null ? null : deviceId.toString());
    }

    public record AuthenticatedTicket(long ssoUserId, String deviceId) {

        public AuthenticatedTicket {
            if (deviceId == null || deviceId.isBlank()) {
                throw new IllegalArgumentException("deviceId 不能为空");
            }
        }
    }

    record CheckedTicket(Object centerId, String deviceId) {
    }

    @FunctionalInterface
    interface TicketChecker {
        CheckedTicket check(String ticket);
    }
}
