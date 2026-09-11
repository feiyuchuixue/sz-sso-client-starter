package com.sz.ssoclient.internal.login;

import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoClientLoginContext;
import com.sz.ssoclient.spi.SsoClientLoginResult;
import com.sz.ssoclient.spi.SsoClientSessionHandle;
import com.sz.ssoclient.spi.SsoDefaultAccessInitializer;
import com.sz.ssoclient.spi.SsoDefaultAccessStatus;
import com.sz.ssoclient.spi.SsoSuperAdminAuthorityAdapter;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.signout.SsoLocalOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoClientLoginOrchestratorTest {

    @Test
    void executesFrozenOrderAndSuperAdminBeforeDefaultAccess() {
        Fixture fixture = fixture(true);
        when(fixture.defaultAccess.initialize("local-42")).thenReturn(SsoDefaultAccessStatus.APPLIED);

        SsoClientLoginResult result = fixture.orchestrator().login("ticket-1");

        assertEquals("token-1", result.accessToken());
        InOrder order = inOrder(
                fixture.ticket,
                fixture.identityResolver,
                fixture.gateway,
                fixture.authority,
                fixture.defaultAccess,
                fixture.loginAdapter);
        order.verify(fixture.ticket).authenticate("ticket-1");
        order.verify(fixture.identityResolver).resolve(42L);
        order.verify(fixture.gateway).send(
                SsoMessageTypes.QUERY_CLIENT_SUPER_ADMIN_STATUS,
                Map.of("ssoUserId", 42L),
                Boolean.class);
        order.verify(fixture.authority).apply("local-42", true);
        order.verify(fixture.defaultAccess).initialize("local-42");
        order.verify(fixture.loginAdapter).loadLoginUser("local-42");
        order.verify(fixture.loginAdapter).establishSession(
                "user",
                new SsoClientLoginContext(42L, "local-42", "device-a", true));
    }

    @Test
    void missingSuperAdminAdapterFailsClosedBeforeDefaultAccessAndLogin() {
        Fixture fixture = fixture(true);
        SsoClientLoginOrchestrator<String> orchestrator = fixture.orchestrator(null, fixture.defaultAccess);

        assertThrows(IllegalStateException.class, () -> orchestrator.login("ticket-1"));
        verify(fixture.defaultAccess, never()).initialize(any());
        verify(fixture.loginAdapter, never()).loadLoginUser(any());
    }

    @Test
    void defaultAccessFailureBlocksOrdinaryUserButNotAppliedSuperAdmin() {
        Fixture ordinary = fixture(false);
        when(ordinary.defaultAccess.initialize("local-42")).thenThrow(new IllegalStateException("role missing"));
        assertThrows(IllegalStateException.class, () -> ordinary.orchestrator().login("ticket-1"));
        verify(ordinary.loginAdapter, never()).establishSession(any(), any());

        Fixture superAdmin = fixture(true);
        when(superAdmin.defaultAccess.initialize("local-42")).thenThrow(new IllegalStateException("role missing"));
        SsoClientLoginResult result = superAdmin.orchestrator().login("ticket-1");
        assertEquals("token-1", result.accessToken());
        verify(superAdmin.identity, never()).completeDefaultAccessInitialization(any());
    }

    @Test
    void missingDefaultAccessInitializerTerminatesMarkerAsDisabled() {
        Fixture fixture = fixture(false);

        SsoClientLoginResult result = fixture.orchestrator(fixture.authority, null)
                .login("ticket-1");

        assertEquals("token-1", result.accessToken());
        verify(fixture.identity).completeDefaultAccessInitialization("local-42");
    }

    @Test
    void postSessionFailureRevokesOnlyExactNewHandleAndKeepsPrimaryFailure() {
        Fixture fixture = fixture(false);
        when(fixture.defaultAccess.initialize("local-42")).thenReturn(SsoDefaultAccessStatus.ALREADY_COMPLETED);
        RuntimeException primary = new IllegalStateException("transaction consume failed");
        when(fixture.localSessions.revokeExact(new SsoClientSessionHandle("session-1")))
                .thenThrow(new IllegalStateException("compensation failed"));

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> fixture.orchestrator().login("ticket-1", ignored -> {
                    throw primary;
                }));

        assertSame(primary, actual);
        verify(fixture.localSessions).revokeExact(new SsoClientSessionHandle("session-1"));
        verify(fixture.localSessions, never()).revokeDevice(any(), any());
        verify(fixture.localSessions, never()).revokeAccount(any());
    }

    @Test
    void ticketAuthenticatorAttemptsTicketExactlyOnce() {
        SsoTicketAuthenticator.TicketChecker checker = mock(SsoTicketAuthenticator.TicketChecker.class);
        when(checker.check("ticket-1")).thenReturn(
                new SsoTicketAuthenticator.CheckedTicket("42", "device-a"));

        assertEquals(42L, new SsoTicketAuthenticator(checker)
                .authenticate("ticket-1").ssoUserId());
        verify(checker).check("ticket-1");
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(boolean superAdmin) {
        SsoTicketAuthenticator ticket = mock(SsoTicketAuthenticator.class);
        SsoClientIdentityResolver identityResolver = mock(SsoClientIdentityResolver.class);
        SsoClientMessageGateway gateway = mock(SsoClientMessageGateway.class);
        SsoClientIdentityAdapter identity = mock(SsoClientIdentityAdapter.class);
        SsoClientLoginAdapter<String> loginAdapter = mock(SsoClientLoginAdapter.class);
        SsoDefaultAccessInitializer defaultAccess = mock(SsoDefaultAccessInitializer.class);
        SsoSuperAdminAuthorityAdapter authority = mock(SsoSuperAdminAuthorityAdapter.class);
        SsoClientLocalSessionAccessor localSessions = mock(SsoClientLocalSessionAccessor.class);
        when(ticket.authenticate("ticket-1")).thenReturn(
                new SsoTicketAuthenticator.AuthenticatedTicket(42L, "device-a"));
        when(identityResolver.resolve(42L)).thenReturn("local-42");
        when(gateway.send(
                eq(SsoMessageTypes.QUERY_CLIENT_SUPER_ADMIN_STATUS),
                eq(Map.of("ssoUserId", 42L)),
                eq(Boolean.class)))
                .thenReturn(new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "ok", superAdmin));
        when(loginAdapter.loadLoginUser("local-42")).thenReturn("user");
        when(loginAdapter.establishSession(eq("user"), any(SsoClientLoginContext.class)))
                .thenReturn(new SsoClientLoginResult("token-1", new SsoClientSessionHandle("session-1")));
        when(localSessions.revokeExact(any())).thenReturn(SsoLocalOutcome.REVOKED);
        return new Fixture(
                ticket,
                identityResolver,
                gateway,
                identity,
                loginAdapter,
                defaultAccess,
                authority,
                localSessions);
    }

    private record Fixture(
            SsoTicketAuthenticator ticket,
            SsoClientIdentityResolver identityResolver,
            SsoClientMessageGateway gateway,
            SsoClientIdentityAdapter identity,
            SsoClientLoginAdapter<String> loginAdapter,
            SsoDefaultAccessInitializer defaultAccess,
            SsoSuperAdminAuthorityAdapter authority,
            SsoClientLocalSessionAccessor localSessions) {

        SsoClientLoginOrchestrator<String> orchestrator() {
            return orchestrator(authority, defaultAccess);
        }

        SsoClientLoginOrchestrator<String> orchestrator(
                SsoSuperAdminAuthorityAdapter chosenAuthority,
                SsoDefaultAccessInitializer chosenDefaultAccess) {
            return new SsoClientLoginOrchestrator<>(
                    ticket,
                    identityResolver,
                    gateway,
                    identity,
                    loginAdapter,
                    chosenDefaultAccess,
                    chosenAuthority,
                    localSessions);
        }
    }
}
