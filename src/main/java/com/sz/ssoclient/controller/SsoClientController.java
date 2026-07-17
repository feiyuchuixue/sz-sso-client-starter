package com.sz.ssoclient.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.sso.error.SaSsoErrorCode;
import cn.dev33.satoken.sso.exception.SaSsoException;
import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLogoutParameter;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.message.SsoMessageSender;
import com.sz.ssoclient.pojo.LoginStatus;
import com.sz.ssoclient.pojo.SsoLoginResult;
import com.sz.ssoclient.service.SsoClientService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.dto.SsoApiResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * SSO 客户端 Controller.
 * <p>
 * 提供 SSO 客户端的标准端点：登录状态检查、获取认证 URL、ticket 登录、
 * 本地会话退出、单点注销、注销回调、消息推送接收。
 * </p>
 * <p>
 * 所有有效载荷接口统一用 {@link SsoApiResult} 包装，与前端 axios 拦截器
 * 期望的 {@code {code, message, data}} 格式保持一致。
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 */
@Tag(name = "SSO客户端", description = "SSO客户端相关接口")
@Slf4j
@RestController
@RequestMapping("/sso")
@SaIgnore
@RequiredArgsConstructor
public class SsoClientController {

    private static final String DEFAULT_PORTAL_TARGET_PATH = "/ucenter/applications";

    private final SsoClientService ssoClientService;

    private final SsoUserMappingService userMappingService;

    private final SsoMessageSender ssoMessageSender;

    // 当前是否登录
    @GetMapping("/isLogin")
    public SsoApiResult<LoginStatus> isLogin() {
        log.debug("isLogin: 检查登录状态");
        LoginStatus status = new LoginStatus();
        status.setHasLogin(StpUtil.isLogin());
        status.setLoginId(StpUtil.getLoginIdDefaultNull());
        log.debug("isLogin: hasLogin={}, loginId={}", status.isHasLogin(), status.getLoginId());
        return SsoApiResult.success(status);
    }

    // 返回SSO认证中心登录地址
    @GetMapping("/getSsoAuthUrl")
    public SsoApiResult<String> getSsoAuthUrl(String clientLoginUrl) {
        log.info("getSsoAuthUrl: clientLoginUrl={}", clientLoginUrl);
        String serverAuthUrl = SaSsoClientUtil.buildServerAuthUrl(clientLoginUrl, "");
        log.info("getSsoAuthUrl: serverAuthUrl={}", serverAuthUrl);
        return SsoApiResult.success(serverAuthUrl);
    }

    // 获取认证中心个人门户入口地址
    @GetMapping("/getSsoPortalUrl")
    public SsoApiResult<String> getSsoPortalUrl(String targetPath) {
        log.info("getSsoPortalUrl: targetPath={}", targetPath);
        if (!StpUtil.isLogin()) {
            return SsoApiResult.error("401", "当前 Client 未登录，请先登录");
        }

        Object localUserId = StpUtil.getLoginId();
        Object centerId = userMappingService.toServerUserId(localUserId);
        if (centerId == null) {
            return SsoApiResult.error("400", "当前用户未绑定 SSO 用户，无法进入认证中心");
        }

        String safeTargetPath = targetPath == null || targetPath.isBlank() ? DEFAULT_PORTAL_TARGET_PATH : targetPath;
        SaResult result = ssoMessageSender.sendToServer("CREATE_PORTAL_TICKET", Map.of(
                "centerId", centerId,
                "targetPath", safeTargetPath
        ));
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            String message = result == null ? "认证中心未返回 portal ticket" : result.getMsg();
            return SsoApiResult.error("403", message == null || message.isBlank() ? "无法进入认证中心" : message);
        }

        String ticket = String.valueOf(result.getData());
        String portalUrl = buildPortalLoginUrl(ticket, safeTargetPath);

        return SsoApiResult.success(portalUrl);
    }

    // 根据ticket进行登录
    @GetMapping("/doLoginByTicket")
    public SsoApiResult<SsoLoginResult> doLoginByTicket(String ticket) {
        log.info("doLoginByTicket: 开始 ticket 登录");
        try {
            SaCheckTicketResult ctr = SaSsoClientProcessor.instance.checkTicket(ticket);
            log.info("doLoginByTicket: ticket 验证成功, centerId={}", ctr.centerId);
            SsoLoginResult result = ssoClientService.login(ctr);
            return SsoApiResult.success(result);
        } catch (Exception e) {
            log.error("doLoginByTicket: ticket 登录失败, error={}", e.getMessage(), e);
            throw e;
        }
    }

    // 当前 Client 本地会话退出，不通知 SSO 认证中心，不影响其它应用。
    @PostMapping("/session/logout")
    public SsoApiResult<Void> sessionLogout() {
        log.info("sessionLogout: 执行当前 Client 本地会话退出");
        StpLogic stpLogic = SaSsoClientProcessor.instance.ssoClientTemplate.getStpLogicOrGlobal();
        if (stpLogic.isLogin()) {
            stpLogic.getTokenSession().logout();
            stpLogic.logout();
        }
        return SsoApiResult.success();
    }

    // 全端单点注销：通知 SSO 认证中心注销当前账号所有 Client 会话。
    @PostMapping("/signout")
    public SsoApiResult<Void> ssoSignout() {
        log.info("ssoSignout: 执行全端单点注销");
        pushSignout(false);
        return SsoApiResult.success();
    }

    // 当前设备单点注销：仅注销同一账号同一 deviceId 分组下的 Client 会话。
    @PostMapping("/signout/device")
    public SsoApiResult<Void> ssoDeviceSignout() {
        log.info("ssoDeviceSignout: 执行当前设备单点注销");
        pushSignout(true);
        return SsoApiResult.success();
    }

    // SSO-Client：单点注销回调
    @RequestMapping("/logoutCall")
    public Object ssoLogoutCall() {
        log.info("ssoLogoutCall: 收到单点注销回调");
        return SaSsoClientProcessor.instance.ssoLogoutCall();
    }

    // SSO-Client：接收消息推送地址
    @RequestMapping("/pushC")
    public Object ssoPushC() {
        Object o = SaSsoClientProcessor.instance.ssoPushC();
        log.debug("ssoPushC: 消息推送处理完成");
        return o;
    }

    private void pushSignout(boolean currentDeviceOnly) {
        StpLogic stpLogic = SaSsoClientProcessor.instance.ssoClientTemplate.getStpLogicOrGlobal();
        if (!stpLogic.isLogin()) {
            return;
        }

        SaLogoutParameter logoutParameter = stpLogic.createSaLogoutParameter();
        if (currentDeviceOnly) {
            logoutParameter.setDeviceId(stpLogic.getLoginDeviceId());
        }
        Object loginId = stpLogic.getLoginId();
        Object centerId = SaSsoClientProcessor.instance.ssoClientTemplate.strategy.convertLoginIdToCenterId.run(loginId);
        SaSsoMessage message = SaSsoClientProcessor.instance.ssoClientTemplate.buildSignoutMessage(centerId, logoutParameter);
        SaResult result = SaSsoClientProcessor.instance.ssoClientTemplate.pushMessageAsSaResult(message);
        if (result == null || result.getCode() == null || SaResult.CODE_SUCCESS != result.getCode()) {
            String messageText = result == null ? "SSO Server 未返回单点注销结果" : result.getMsg();
            throw new SaSsoException(messageText).setCode(SaSsoErrorCode.CODE_30006);
        }
        if (stpLogic.isLogin()) {
            stpLogic.logout(loginId, logoutParameter);
        }
    }

    private String buildPortalLoginUrl(String ticket, String targetPath) {
        String authUrl = SaSsoClientUtil.getSsoTemplate().getClientConfig().getAuthUrl();
        URI authUri = URI.create(authUrl);
        String baseUrl = authUri.isAbsolute()
                ? authUri.getScheme() + "://" + authUri.getAuthority()
                : "";
        return baseUrl + "/portal-login?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8)
                + "&_back=" + URLEncoder.encode(targetPath, StandardCharsets.UTF_8);
    }
}