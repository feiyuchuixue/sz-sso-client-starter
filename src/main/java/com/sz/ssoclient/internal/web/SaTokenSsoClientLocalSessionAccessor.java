package com.sz.ssoclient.internal.web;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLogoutParameter;
import com.sz.ssoclient.spi.SsoClientSessionHandle;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSession;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssocore.signout.SsoLocalOutcome;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/** Sa-Token 本地 Session 的默认高级 SPI 实现。 */
@Slf4j
public class SaTokenSsoClientLocalSessionAccessor
        implements SsoClientLocalSessionAccessor {

    @Override
    public SsoClientLocalSession current() {
        StpLogic logic = logic();
        if (!logic.isLogin()) {
            return new SsoClientLocalSession(false, null, null, null);
        }
        return new SsoClientLocalSession(
                true,
                logic.getLoginIdAsString(),
                logic.getLoginDeviceId(),
                new SsoClientSessionHandle(logic.getTokenValueNotNull()));
    }

    @Override
    public SsoLocalOutcome revokeExact(SsoClientSessionHandle handle) {
        if (handle == null) {
            return SsoLocalOutcome.FAILED;
        }
        try {
            StpLogic logic = logic();
            if (logic.getLoginIdByToken(handle.value()) == null) {
                return SsoLocalOutcome.ALREADY_REVOKED;
            }
            logic.logoutByTokenValue(handle.value());
            return SsoLocalOutcome.REVOKED;
        } catch (RuntimeException exception) {
            log.warn("[SSO] Sa-Token 精确 Session 撤销失败");
            return SsoLocalOutcome.FAILED;
        }
    }

    @Override
    public SsoLocalOutcome revokeDevice(String localUserId, String deviceId) {
        if (localUserId == null || localUserId.isBlank()
                || deviceId == null || deviceId.isBlank()) {
            return SsoLocalOutcome.FAILED;
        }
        try {
            StpLogic logic = logic();
            for (Object loginId : loginIdCandidates(localUserId)) {
                List<SaTerminalInfo> terminals = logic.getTerminalListByLoginId(loginId);
                if (hasDeviceTerminal(terminals, deviceId)) {
                    logic.logout(loginId, new SaLogoutParameter().setDeviceId(deviceId));
                    return SsoLocalOutcome.REVOKED;
                }
            }
            return SsoLocalOutcome.ALREADY_REVOKED;
        } catch (RuntimeException exception) {
            log.warn("[SSO] Sa-Token 当前设备 Session 撤销失败");
            return SsoLocalOutcome.FAILED;
        }
    }

    @Override
    public SsoLocalOutcome revokeAccount(String localUserId) {
        if (localUserId == null || localUserId.isBlank()) {
            return SsoLocalOutcome.FAILED;
        }
        try {
            StpLogic logic = logic();
            for (Object loginId : loginIdCandidates(localUserId)) {
                List<String> tokens = logic.getTokenValueListByLoginId(loginId);
                if (tokens != null && !tokens.isEmpty()) {
                    logic.logout(loginId);
                    return SsoLocalOutcome.REVOKED;
                }
            }
            return SsoLocalOutcome.ALREADY_REVOKED;
        } catch (RuntimeException exception) {
            log.warn("[SSO] Sa-Token 账号 Session 撤销失败");
            return SsoLocalOutcome.FAILED;
        }
    }

    static List<Object> loginIdCandidates(String localUserId) {
        List<Object> candidates = new ArrayList<>(2);
        candidates.add(localUserId);
        if (localUserId != null && localUserId.matches("-?(?:0|[1-9][0-9]*)")) {
            try {
                candidates.add(Long.valueOf(localUserId));
            } catch (NumberFormatException ignored) {
                // Keep the original opaque String when it is outside the Long range.
            }
        }
        return List.copyOf(candidates);
    }

    static boolean hasDeviceTerminal(
            List<SaTerminalInfo> terminals,
            String deviceId) {
        return terminals != null && terminals.stream()
                .anyMatch(terminal -> terminal != null
                        && deviceId.equals(terminal.getDeviceId()));
    }

    private static StpLogic logic() {
        return SaSsoClientProcessor.instance.ssoClientTemplate.getStpLogicOrGlobal();
    }
}
