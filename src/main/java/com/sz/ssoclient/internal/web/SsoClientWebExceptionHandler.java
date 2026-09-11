package com.sz.ssoclient.internal.web;

import com.sz.ssoclient.api.browser.SsoWebCodes;
import com.sz.ssoclient.api.browser.SsoWebResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将六条 Browser 路由异常收口为稳定、安全的公开错误。 */
@Slf4j
@RestControllerAdvice(assignableTypes = SsoClientWebController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SsoClientWebExceptionHandler {

    @ExceptionHandler(SsoClientWebException.class)
    public ResponseEntity<SsoWebResult<Void>> stable(SsoClientWebException exception) {
        return failure(exception.httpStatus(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<SsoWebResult<Void>> invalidRequest(Exception exception) {
        return failure(400, SsoWebCodes.REQUEST_INVALID, "请求 JSON 或 Content-Type 非法");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<SsoWebResult<Void>> internal(RuntimeException exception) {
        log.error("[SSO] Browser HTTP 操作未形成可信结果", exception);
        return failure(500, SsoWebCodes.SERVER_FAILURE, "SSO 操作未形成可信结果");
    }

    private static ResponseEntity<SsoWebResult<Void>> failure(
            int status, String code, String message) {
        return ResponseEntity.status(status)
                .headers(SsoClientWebController.sensitiveHeaders())
                .body(SsoWebResult.failure(code, message));
    }
}
