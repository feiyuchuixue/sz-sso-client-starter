package com.sz.ssoclient.internal.web;

import cn.dev33.satoken.annotation.SaIgnore;
import com.sz.ssoclient.api.browser.SsoLocalLogoutResponse;
import com.sz.ssoclient.api.browser.SsoLoginCallbackRequest;
import com.sz.ssoclient.api.browser.SsoLoginCallbackResponse;
import com.sz.ssoclient.api.browser.SsoLoginTransactionRequest;
import com.sz.ssoclient.api.browser.SsoLoginTransactionResponse;
import com.sz.ssoclient.api.browser.SsoPortalEntryRequest;
import com.sz.ssoclient.api.browser.SsoPortalEntryResponse;
import com.sz.ssoclient.api.browser.SsoSignoutResponse;
import com.sz.ssoclient.api.browser.SsoWebResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** Starter 唯一正式 Browser HTTP 控制器。 */
@SaIgnore
@RestController
@RequestMapping("/sso/v1")
public class SsoClientWebController {

    private static final Set<String> LOGIN_TRANSACTION_FIELDS = Set.of("back", "theme");
    private static final Set<String> LOGIN_CALLBACK_FIELDS = Set.of("ticket", "state");
    private static final Set<String> PORTAL_ENTRY_FIELDS = Set.of("target");

    private final SsoClientWebService webService;
    private final SsoClientBrowserBinding browserBinding;
    private final SsoSameOriginGuard sameOriginGuard;
    private final SsoStrictJsonGuard strictJsonGuard;

    public SsoClientWebController(
            SsoClientWebService webService,
            SsoClientBrowserBinding browserBinding,
            SsoSameOriginGuard sameOriginGuard,
            SsoStrictJsonGuard strictJsonGuard) {
        this.webService = java.util.Objects.requireNonNull(webService, "webService");
        this.browserBinding = java.util.Objects.requireNonNull(browserBinding, "browserBinding");
        this.sameOriginGuard = java.util.Objects.requireNonNull(sameOriginGuard, "sameOriginGuard");
        this.strictJsonGuard = java.util.Objects.requireNonNull(strictJsonGuard, "strictJsonGuard");
    }

    @PostMapping("/login/transactions")
    public ResponseEntity<SsoWebResult<SsoLoginTransactionResponse>> createLoginTransaction(
            @RequestBody String body,
            HttpServletRequest request,
            HttpServletResponse response) {
        sameOriginGuard.verify(request);
        SsoLoginTransactionRequest command = strictJsonGuard.bind(
                body, SsoLoginTransactionRequest.class, LOGIN_TRANSACTION_FIELDS);
        String browserId = browserBinding.resolveOrCreate(request, response);
        return success(webService.createLoginTransaction(browserId, command));
    }

    @PostMapping("/login/callback")
    public ResponseEntity<SsoWebResult<SsoLoginCallbackResponse>> loginCallback(
            @RequestBody String body,
            HttpServletRequest request) {
        sameOriginGuard.verify(request);
        SsoLoginCallbackRequest command = strictJsonGuard.bind(
                body, SsoLoginCallbackRequest.class, LOGIN_CALLBACK_FIELDS);
        return success(webService.completeLogin(browserBinding.require(request), command));
    }

    @PostMapping("/session/logout")
    public ResponseEntity<SsoWebResult<SsoLocalLogoutResponse>> logoutLocalSession(
            @RequestBody(required = false) String body,
            HttpServletRequest request,
            HttpServletResponse response) {
        sameOriginGuard.verify(request);
        strictJsonGuard.requireEmptyObject(body);
        try {
            return success(webService.logoutLocalSession());
        } finally {
            browserBinding.clear(request, response);
        }
    }

    @PostMapping("/signouts/device")
    public ResponseEntity<SsoWebResult<SsoSignoutResponse>> signoutCurrentDevice(
            @RequestBody(required = false) String body,
            HttpServletRequest request,
            HttpServletResponse response) {
        sameOriginGuard.verify(request);
        strictJsonGuard.requireEmptyObject(body);
        try {
            return success(webService.signoutCurrentDevice());
        } finally {
            browserBinding.clear(request, response);
        }
    }

    @PostMapping("/signouts/account")
    public ResponseEntity<SsoWebResult<SsoSignoutResponse>> signoutAccount(
            @RequestBody(required = false) String body,
            HttpServletRequest request,
            HttpServletResponse response) {
        sameOriginGuard.verify(request);
        strictJsonGuard.requireEmptyObject(body);
        try {
            return success(webService.signoutAccount());
        } finally {
            browserBinding.clear(request, response);
        }
    }

    @PostMapping("/portal/entries")
    public ResponseEntity<SsoWebResult<SsoPortalEntryResponse>> createPortalEntry(
            @RequestBody String body,
            HttpServletRequest request) {
        sameOriginGuard.verify(request);
        SsoPortalEntryRequest command = strictJsonGuard.bind(
                body, SsoPortalEntryRequest.class, PORTAL_ENTRY_FIELDS);
        return success(webService.createPortalEntry(command));
    }

    static HttpHeaders sensitiveHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.set("Referrer-Policy", "no-referrer");
        return headers;
    }

    private static <T> ResponseEntity<SsoWebResult<T>> success(T data) {
        return ResponseEntity.ok()
                .headers(sensitiveHeaders())
                .body(SsoWebResult.success(data));
    }
}
