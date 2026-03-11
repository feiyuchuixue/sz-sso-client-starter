package com.sz.sso.client;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;

/**
 * SSO Server 消息处理器接口（SPI）.
 * <p>
 * 业务方实现此接口并注册为 Spring Bean 后，starter 会在自动配置阶段将其注册到
 * {@code SaSsoTemplate#messageHolder}，当 SSO Server 向本 Client 推送对应类型
 * 的消息时，框架会自动路由并调用对应实现类的 {@link #handle} 方法。
 * </p>
 *
 * <p><b>使用方式：</b>实现此接口并添加 {@code @Component} 注解，无需任何额外配置，
 * starter 的自动装配会自动发现并注册所有实现类。</p>
 *
 * <p><b>消息类型冲突：</b>同一 {@link #messageType()} 只允许注册一个处理器，
 * 若注册重复类型，sa-token 框架会以后注册的覆盖先注册的，请确保消息类型唯一。</p>
 *
 * <p><b>注意：</b>{@code REGISTER} 消息已由 starter 内部占用（用于同步新用户注册），
 * 业务方不应再实现同一类型的处理器。</p>
 *
 * <p>接入示例：</p>
 * <pre>{@code
 * // 接收 Server 推送的用户状态变更通知
 * @Component
 * public class UserStatusChangedHandler implements SsoServerMessageHandler {
 *
 *     @Override
 *     public String messageType() {
 *         return "SERVER_USER_STATUS_CHANGED";
 *     }
 *
 *     @Override
 *     public SaResult handle(SaSsoTemplate template, SaSsoMessage message) {
 *         Long ssoUserId = message.getLong("ssoUserId");
 *         String status = message.getString("status");
 *         if ("banned".equals(status)) {
 *             // 根据 ssoUserId 找到本地用户并踢出登录
 *         }
 *         return SaResult.ok();
 *     }
 * }
 * }</pre>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 * @see SsoMessageSender
 */
public interface SsoServerMessageHandler {

    /**
     * 返回该处理器负责的消息类型.
     * <p>
     * 对应 {@link SaSsoMessage#getType()} 的值，大小写敏感。
     * </p>
     *
     * @return 消息类型字符串，如 {@code "SERVER_USER_STATUS_CHANGED"}
     */
    String messageType();

    /**
     * 处理 SSO Server 推送的消息.
     *
     * @param template sa-token SSO 模板（一般无需直接使用）
     * @param message  Server 推送的消息对象，通过 {@code message.get(key)} 获取参数
     * @return 处理结果，返回 {@link SaResult#ok()} 表示成功；
     *         返回 {@link SaResult#error(String)} 会将错误信息传回 Server
     */
    SaResult handle(SaSsoTemplate template, SaSsoMessage message);

}
