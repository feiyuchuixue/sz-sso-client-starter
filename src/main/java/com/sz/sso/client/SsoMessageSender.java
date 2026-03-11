package com.sz.sso.client;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.Map;

/**
 * SSO 消息发送工具（Spring Bean）.
 * <p>
 * 由 starter 自动注册为 Bean，Client 注入后即可向 SSO Server 发送自定义消息。
 * 封装了 {@code clientId} 的自动填充（从 sa-token 配置读取），调用方只需关注
 * 消息类型和业务参数。
 * </p>
 *
 * <p><b>同步发送 vs 异步发送：</b></p>
 * <ul>
 *   <li>{@link #sendToServer(String, Map)} — 同步，等待 Server 响应并返回结果，
 *       适合需要获取 Server 返回数据的场景（如跨库查询）</li>
 *   <li>{@link #sendToServerAsync(String, Map)} — 异步，不关心响应（fire-and-forget），
 *       失败时只记录 warn 日志，不抛出异常，适合通知类消息</li>
 * </ul>
 *
 * <p>接入示例：</p>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class SomePlatformService {
 *
 *     private final SsoMessageSender ssoMessageSender;
 *
 *     // 同步查询：从 Server 获取用户信息
 *     public SsoUserMeta queryUserFromServer(Long ssoUserId) {
 *         SaResult result = ssoMessageSender.sendToServer(
 *             "PLATFORM_QUERY_USER",
 *             Map.of("ssoUserId", ssoUserId)
 *         );
 *         Map<String, Object> data = (Map<String, Object>) result.getData();
 *         return SsoUserMetaUtils.fromMap(data);
 *     }
 *
 *     // 异步通知：不关心结果
 *     public void notifyServer(Long ssoUserId) {
 *         ssoMessageSender.sendToServerAsync(
 *             "PLATFORM_NOTIFY",
 *             Map.of("ssoUserId", ssoUserId)
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 * @see SsoServerMessageHandler
 * @see SsoSyncHelper
 */
@Slf4j
public class SsoMessageSender {

    /**
     * 同步发送消息到 SSO Server，阻塞等待响应.
     * <p>
     * {@code clientId} 会从 sa-token SSO 配置中自动读取并填充，调用方无需手动传入。
     * 若消息发送失败或 Server 返回异常，会直接抛出异常。
     * </p>
     *
     * @param type   消息类型，对应 Server 端注册的消息处理器类型
     * @param params 业务参数，{@code null} 时只发送 type 和 clientId
     * @return Server 端消息处理器返回的 {@link SaResult}
     * @throws RuntimeException 当网络异常或 Server 端处理失败时抛出
     */
    public SaResult sendToServer(String type, Map<String, Object> params) {
        SaSsoMessage message = buildMessage(type, params);
        log.info("[SSO] 发送消息到 Server: type={}, params={}", type, params);
        SaResult result = SaSsoClientUtil.pushMessageAsSaResult(message);
        log.info("[SSO] Server 响应: type={}, code={}, msg={}", type, result.getCode(), result.getMsg());
        return result;
    }

    /**
     * 异步发送消息到 SSO Server，不等待响应（fire-and-forget）.
     * <p>
     * 失败时只记录 warn 日志，不抛出异常，不影响调用方的业务逻辑。
     * 适合通知类消息，如数据变更通知等不需要响应数据的场景。
     * </p>
     *
     * @param type   消息类型
     * @param params 业务参数，{@code null} 时只发送 type 和 clientId
     */
    @Async
    public void sendToServerAsync(String type, Map<String, Object> params) {
        try {
            SaSsoMessage message = buildMessage(type, params);
            log.info("[SSO] 异步发送消息到 Server: type={}, params={}", type, params);
            SaSsoClientUtil.pushMessage(message);
            log.info("[SSO] 异步消息发送完成: type={}", type);
        } catch (Exception e) {
            log.warn("[SSO] 异步消息发送失败: type={}, error={}", type, e.getMessage(), e);
        }
    }

    /**
     * 构建 SSO 消息对象，自动填充 clientId.
     */
    private SaSsoMessage buildMessage(String type, Map<String, Object> params) {
        SaSsoMessage message = new SaSsoMessage();
        message.setType(type);
        // 自动填充 clientId，无需调用方手动传入
        message.set("clientId", SaSsoClientUtil.getSsoTemplate().getClient());
        if (params != null) {
            params.forEach(message::set);
        }
        return message;
    }

}
