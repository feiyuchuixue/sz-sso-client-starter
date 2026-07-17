package com.sz.ssoclient.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.sz.ssoclient.clientaccess.web.ClientAccessWebService;
import com.sz.ssoclient.clientaccess.web.ClientBrowserBinding;
import com.sz.ssoclient.clientaccess.web.ClientLocalSession;
import com.sz.ssoclient.clientaccess.web.ClientLoginCallbackResult;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionCreated;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionService;
import com.sz.ssoclient.clientaccess.web.dto.ClientSessionWebResponse;
import com.sz.ssoclient.clientaccess.web.dto.LoginCallbackWebRequest;
import com.sz.ssoclient.clientaccess.web.dto.LoginCallbackWebResponse;
import com.sz.ssoclient.clientaccess.web.dto.LoginTransactionWebRequest;
import com.sz.ssoclient.clientaccess.web.dto.PortalEntryWebRequest;
import com.sz.ssoclient.pojo.SsoLoginResult;
import com.sz.ssocore.clientaccess.v1.dto.ClientAccessResponse;
import com.sz.ssocore.clientaccess.v1.dto.PortalTicketCreateResponse;
import com.sz.ssocore.clientaccess.v1.dto.SignoutCreateResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Official browser-to-Client-backend Web Contract for CAP V1. */
@SaIgnore
@RestController
@RequestMapping("/sso/v1")
public class ClientAccessV1Controller {

    private final ClientLoginTransactionService transactions;
    private final ClientAccessWebService webService;
    private final ClientBrowserBinding browserBinding;

    public ClientAccessV1Controller(ClientLoginTransactionService transactions, ClientAccessWebService webService,
            ClientBrowserBinding browserBinding) {
        this.transactions = transactions;
        this.webService = webService;
        this.browserBinding = browserBinding;
    }

    @PostMapping("/login/transactions")
    public ClientAccessResponse<ClientLoginTransactionCreated> createLoginTransaction(
            @RequestBody LoginTransactionWebRequest body, HttpServletRequest request, HttpServletResponse response) {
        ClientLoginTransactionCreated created = transactions.create(
                browserBinding.resolveOrCreate(request, response), body.back(), body.mode(), body.theme());
        return success(created);
    }

    @PostMapping("/login/callback")
    public ResponseEntity<ClientAccessResponse<LoginCallbackWebResponse>> loginCallback(
            @RequestBody LoginCallbackWebRequest body, HttpServletRequest request) {
        ClientLoginCallbackResult completed = transactions.complete(
                browserBinding.require(request), body.state(), body.ticket());
        SsoLoginResult login = completed.loginResult();
        LoginCallbackWebResponse data = new LoginCallbackWebResponse(login.getAccessToken(), login.getExpireIn(),
                "Bearer", completed.back(), login.getUserInfo());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(success(data));
    }

    @GetMapping("/session")
    public ClientAccessResponse<ClientSessionWebResponse> session() {
        ClientLocalSession current = webService.currentSession();
        Object user = current.authenticated() && current.localUserId() != null
                ? Map.of("id", current.localUserId()) : null;
        return success(new ClientSessionWebResponse(current.authenticated(), user, current.expireAt()));
    }

    @PostMapping("/session/logout")
    public ClientAccessResponse<Void> logoutLocalSession() {
        webService.logoutLocalSession();
        return success(null);
    }

    @PostMapping("/signouts/device")
    public ClientAccessResponse<SignoutCreateResponse> signoutDevice() {
        return success(webService.signoutCurrentDevice());
    }

    @PostMapping("/signouts/account")
    public ClientAccessResponse<SignoutCreateResponse> signoutAccount() {
        return success(webService.signoutAccount());
    }

    @PostMapping("/portal/entries")
    public ClientAccessResponse<PortalTicketCreateResponse> createPortalEntry(@RequestBody PortalEntryWebRequest body) {
        return success(webService.createPortalEntry(body.targetPath()));
    }

    private static <T> ClientAccessResponse<T> success(T data) {
        return new ClientAccessResponse<>(true, "CAP-0000", "success", requestId(),
                Instant.now(), data, List.of());
    }

    private static String requestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
