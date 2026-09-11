package com.sz.ssoclient.internal.login;

import cn.dev33.satoken.sso.exception.SaSsoException;
import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.name.ParamName;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoTicketAuthenticatorTest {

    @Test
    void exchangesTicketWithoutRunningCenterToLocalIdConversion() {
        SaSsoClientTemplate template = mock(SaSsoClientTemplate.class);
        template.paramName = new ParamName();
        SaSsoMessage message = new SaSsoMessage("checkTicket");
        when(template.buildCheckTicketMessage("ticket-1", null)).thenReturn(message);
        when(template.pushMessageAsSaResult(message))
                .thenReturn(SaResult.ok()
                        .set(template.paramName.loginId, "42")
                        .set(template.paramName.deviceId, "device-a"));

        SsoTicketAuthenticator.AuthenticatedTicket authenticated =
                new SsoTicketAuthenticator(template).authenticate("ticket-1");
        assertEquals(42L, authenticated.ssoUserId());
        assertEquals("device-a", authenticated.deviceId());

        verify(template).buildCheckTicketMessage("ticket-1", null);
        verify(template).pushMessageAsSaResult(message);
    }

    @Test
    void rejectsUntrustedTicketExchangeResult() {
        SaSsoClientTemplate template = mock(SaSsoClientTemplate.class);
        template.paramName = new ParamName();
        SaSsoMessage message = new SaSsoMessage("checkTicket");
        when(template.buildCheckTicketMessage("ticket-1", null)).thenReturn(message);
        when(template.pushMessageAsSaResult(message)).thenReturn(SaResult.error("invalid"));

        assertThrows(SaSsoException.class,
                () -> new SsoTicketAuthenticator(template).authenticate("ticket-1"));
    }

    @Test
    void rejectsTicketExchangeWithoutTrustedDeviceId() {
        SaSsoClientTemplate template = mock(SaSsoClientTemplate.class);
        template.paramName = new ParamName();
        SaSsoMessage message = new SaSsoMessage("checkTicket");
        when(template.buildCheckTicketMessage("ticket-1", null)).thenReturn(message);
        when(template.pushMessageAsSaResult(message))
                .thenReturn(SaResult.ok().set(template.paramName.loginId, "42"));

        assertThrows(IllegalStateException.class,
                () -> new SsoTicketAuthenticator(template).authenticate("ticket-1"));
    }
}
