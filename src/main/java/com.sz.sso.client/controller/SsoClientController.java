package com.sz.sso.client.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.sz.sso.client.pojo.LoginStatus;
import com.sz.sso.client.pojo.SsoApiResult;
import com.sz.sso.client.pojo.SsoLoginResult;
import com.sz.sso.client.service.SsoClientService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 客户端 Controller.
 * <p>
 * 提供 SSO 客户端的标准端点：登录状态检查、获取认证 URL、ticket 登录、
 * 单点注销、注销回调、消息推送接收。
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

    private final SsoClientService ssoClientService;

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

    // 根据ticket进行登录
    @GetMapping("/doLoginByTicket")
    public SsoApiResult<SsoLoginResult> doLoginByTicket(String ticket) {
        log.info("doLoginByTicket: 开始 ticket 登录, ticketPrefix={}...", ticket.substring(0, Math.min(8, ticket.length())));
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

    // SSO-Client：单点注销地址
    @RequestMapping("/logout")
    public SsoApiResult<Void> ssoLogout() {
        log.info("ssoLogout: 执行单点注销");
        SaSsoClientProcessor.instance.ssoLogout();
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

}
