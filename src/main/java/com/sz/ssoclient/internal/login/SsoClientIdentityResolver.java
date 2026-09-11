package com.sz.ssoclient.internal.login;

import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import com.sz.ssocore.SsoUserMeta;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 固定执行“只读 mapping → 缺失时 USER_CHECK → 宿主幂等 JIT”的身份解析器。 */
public class SsoClientIdentityResolver {

    private final SsoClientIdentityAdapter identityAdapter;
    private final SsoClientMessageGateway messageGateway;

    public SsoClientIdentityResolver(
            SsoClientIdentityAdapter identityAdapter,
            SsoClientMessageGateway messageGateway) {
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.messageGateway = Objects.requireNonNull(messageGateway, "messageGateway");
    }

    public String resolve(long ssoUserId) {
        Optional<String> existing = identityAdapter.findLocalUserId(ssoUserId);
        if (existing != null && existing.isPresent()) {
            return requireText(existing.get(), "existing localUserId");
        }

        SsoMessageResult<SsoUserMeta> result = messageGateway.send(
                SsoMessageTypes.USER_CHECK,
                Map.of(SsoProtocolFields.SSO_USER_ID, ssoUserId),
                SsoUserMeta.class);
        if (result == null || !result.successful() || result.data() == null) {
            throw new IllegalStateException(result == null || result.message() == null
                    ? "SSO Server 用户身份查询失败"
                    : result.message());
        }
        if (result.data().getSsoUserId() == null || result.data().getSsoUserId() != ssoUserId) {
            throw new IllegalStateException("SSO Server 用户身份查询结果与 Ticket 不匹配");
        }
        return requireText(identityAdapter.resolveOrProvision(result.data()), "resolved localUserId");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " 不能为空");
        }
        return value;
    }
}
