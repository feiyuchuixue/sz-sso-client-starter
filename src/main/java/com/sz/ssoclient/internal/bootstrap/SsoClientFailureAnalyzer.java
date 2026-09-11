package com.sz.ssoclient.internal.bootstrap;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** 将 Starter 启动合同错误转换为可执行的中文诊断。 */
public class SsoClientFailureAnalyzer
        extends AbstractFailureAnalyzer<SsoClientConfigurationException> {

    @Override
    protected FailureAnalysis analyze(
            Throwable rootFailure, SsoClientConfigurationException cause) {
        StringBuilder description = new StringBuilder("SSO Client 启动合同校验失败：\n");
        if (cause.violations().isEmpty()) {
            description.append("  - ").append(cause.getMessage()).append('\n');
        } else {
            cause.violations().forEach(violation -> description
                    .append("  - ")
                    .append(violation.typeName())
                    .append("：期望 ")
                    .append(violation.expectation())
                    .append("，实际 ")
                    .append(violation.actualCount())
                    .append(" 个\n"));
        }
        String action = "请为每个必需 SPI 注册且只注册一个实现；"
                + "可选 SPI 和可替换基础设施至多注册一个。"
                + "生产环境还必须提供 Spring Data Redis 或显式自定义共享 SsoClientStateRepository。";
        return new FailureAnalysis(description.toString(), action, cause);
    }
}
