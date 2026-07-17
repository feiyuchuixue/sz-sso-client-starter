package com.sz.ssoclient.controller;

import com.sz.ssoclient.clientaccess.web.ClientAccessWebService;
import com.sz.ssoclient.clientaccess.web.ClientBrowserBinding;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionCreated;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionService;
import com.sz.ssoclient.clientaccess.web.dto.LoginTransactionWebRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientAccessV1ControllerContractTest {

    @Test
    void sessionEndpointUsesOnlyTheDocumentedBrowserResponseShape() throws Exception {
        Method method = ClientAccessV1Controller.class.getDeclaredMethod("session");
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("ClientSessionWebResponse");

        Class<?> responseType = Class.forName(
                "com.sz.ssoclient.clientaccess.web.dto.ClientSessionWebResponse");
        assertThat(responseType.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("authenticated", "user", "expireAt");
    }

    @Test
    void loginTransactionUsesStarterBrowserBindingWithoutCreatingHttpSession() {
        ClientLoginTransactionService transactions = mock(ClientLoginTransactionService.class);
        ClientAccessWebService webService = mock(ClientAccessWebService.class);
        ClientBrowserBinding browserBinding = mock(ClientBrowserBinding.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        LoginTransactionWebRequest body = new LoginTransactionWebRequest("/workspace", "default", "light");
        when(browserBinding.resolveOrCreate(request, response)).thenReturn("A".repeat(43));
        when(transactions.create("A".repeat(43), "/workspace", "default", "light"))
                .thenReturn(new ClientLoginTransactionCreated("https://auth.example.com", Instant.now()));
        ClientAccessV1Controller controller = new ClientAccessV1Controller(transactions, webService, browserBinding);

        controller.createLoginTransaction(body, request, response);

        verify(transactions).create("A".repeat(43), "/workspace", "default", "light");
        verify(request, never()).getSession();
        verify(request, never()).getSession(true);
    }
}
