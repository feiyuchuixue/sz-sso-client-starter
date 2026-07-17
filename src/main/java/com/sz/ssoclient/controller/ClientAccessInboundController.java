package com.sz.ssoclient.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.sz.ssoclient.clientaccess.inbound.ClientAccessInboundException;
import com.sz.ssoclient.clientaccess.inbound.ClientAccessInboundService;
import com.sz.ssoclient.clientaccess.inbound.ClientAccessInboundVerifier;
import com.sz.ssoclient.clientaccess.json.ClientAccessJsonCodec;
import com.sz.ssocore.clientaccess.v1.ClientAccessHeaders;
import com.sz.ssocore.clientaccess.v1.ClientAccessOperation;
import com.sz.ssocore.clientaccess.v1.dto.ClientAccessResponse;
import com.sz.ssocore.clientaccess.v1.dto.ClientMessageEnvelope;
import com.sz.ssocore.clientaccess.v1.dto.ClientMessageResult;
import com.sz.ssocore.clientaccess.v1.dto.SloCallbackRequest;
import com.sz.ssocore.clientaccess.v1.dto.SloCallbackResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Signed Server-to-Client CAP V1 endpoints; raw bytes are verified before JSON parsing. */
@SaIgnore
@RestController
@RequestMapping("/sso/v1")
public class ClientAccessInboundController {

    private final ClientAccessInboundVerifier verifier;
    private final ClientAccessInboundService service;
    private final ClientAccessJsonCodec jsonCodec;

    public ClientAccessInboundController(ClientAccessInboundVerifier verifier,
            ClientAccessInboundService service, ClientAccessJsonCodec jsonCodec) {
        this.verifier = verifier;
        this.service = service;
        this.jsonCodec = jsonCodec;
    }

    @PostMapping(value = "/callbacks/logout", consumes = "application/json")
    public ClientAccessResponse<SloCallbackResult> logoutCallback(
            @RequestHeader Map<String, String> headers, @RequestBody byte[] rawBody) {
        verifier.verify(ClientAccessOperation.SLO_CALLBACK, "POST",
                ClientAccessOperation.SLO_CALLBACK.logicalPath(), headers, rawBody);
        SloCallbackRequest request = deserialize(rawBody, SloCallbackRequest.class);
        return success(headers, service.handleSlo(request));
    }

    @PostMapping(value = "/messages", consumes = "application/json")
    public ClientAccessResponse<ClientMessageResult> receiveMessage(
            @RequestHeader Map<String, String> headers, @RequestBody byte[] rawBody) {
        verifier.verify(ClientAccessOperation.CLIENT_MESSAGE_RECEIVE, "POST",
                ClientAccessOperation.CLIENT_MESSAGE_RECEIVE.logicalPath(), headers, rawBody);
        ClientMessageEnvelope envelope = deserialize(rawBody, ClientMessageEnvelope.class);
        return success(headers, service.handleMessage(envelope));
    }

    private <T> T deserialize(byte[] rawBody, Class<T> type) {
        try {
            return jsonCodec.readValue(rawBody, type);
        } catch (Exception exception) {
            throw new ClientAccessInboundException("CAP-1001", 400, "CAP callback body is invalid JSON");
        }
    }

    private static <T> ClientAccessResponse<T> success(Map<String, String> headers, T data) {
        return new ClientAccessResponse<>(true, "CAP-0000", "success",
                header(headers, ClientAccessHeaders.REQUEST_ID), Instant.now(), data, List.of());
    }

    private static String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("unavailable");
    }
}
