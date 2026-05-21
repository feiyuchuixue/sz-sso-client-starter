package com.sz.sso.client.autoconfigure;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * SSO Client SPI 缺失的友好启动失败分析器.
 * <p>
 * 捕获 {@link SsoClientSpiMissingException}，输出清晰的 Description 和 Action，
 * 引导用户实现必要的 SPI 接口。
 * </p>
 *
 * <p>通过 {@code META-INF/spring.factories} 注册：
 * <pre>
 * org.springframework.boot.diagnostics.FailureAnalyzer=\
 *   com.sz.sso.client.autoconfigure.SsoClientSpiFailureAnalyzer
 * </pre>
 * </p>
 */
public class SsoClientSpiFailureAnalyzer extends AbstractFailureAnalyzer<SsoClientSpiMissingException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SsoClientSpiMissingException cause) {
        String description = buildDescription(cause);
        String action = buildAction(cause);
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(SsoClientSpiMissingException cause) {
        StringBuilder sb = new StringBuilder();
        sb.append("SSO Client 自动配置未激活：classpath 中已检测到 Sa-Token SSO Client，\n");
        sb.append("但容器中缺少以下必要的 SPI 实现 Bean：\n\n");
        for (String spi : cause.getMissingSpiInterfaces()) {
            sb.append("  - ").append(spi).append("\n");
        }
        return sb.toString();
    }

    private String buildAction(SsoClientSpiMissingException cause) {
        StringBuilder sb = new StringBuilder();
        sb.append("请在您的项目中实现以上接口并注册为 Spring Bean，例如：\n\n");

        boolean needsMapping = cause.getMissingSpiInterfaces().stream()
                .anyMatch(s -> s.contains("SsoUserMappingService"));
        boolean needsLogin = cause.getMissingSpiInterfaces().stream()
                .anyMatch(s -> s.contains("SsoClientLoginAdapter"));

        if (needsMapping) {
            sb.append("  @Component\n");
            sb.append("  public class SsoUserMappingServiceImpl implements SsoUserMappingService {\n");
            sb.append("      @Override\n");
            sb.append("      public Object toServerUserId(Object clientUserId) { ... }\n");
            sb.append("      @Override\n");
            sb.append("      public Object toClientUserId(Object serverUserId) { ... }\n");
            sb.append("      @Override\n");
            sb.append("      public void syncSsoRegisterUser(SaSsoMessage message, String client) { ... }\n");
            sb.append("  }\n\n");
        }

        if (needsLogin) {
            sb.append("  @Component\n");
            sb.append("  public class SsoClientLoginAdapterImpl implements SsoClientLoginAdapter<LoginUser> {\n");
            sb.append("      @Override\n");
            sb.append("      public LoginUser buildLoginUser(Long userId) { ... }\n");
            sb.append("      @Override\n");
            sb.append("      public SsoLoginResult createLoginResult(LoginUser user, SaLoginParameter parameter, Object loginId) { ... }\n");
            sb.append("  }\n");
        }

        return sb.toString();
    }
}
