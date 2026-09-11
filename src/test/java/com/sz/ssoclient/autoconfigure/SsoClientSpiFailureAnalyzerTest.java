package com.sz.ssoclient.autoconfigure;

import com.sz.ssoclient.internal.bootstrap.SsoClientConfigurationException;
import com.sz.ssoclient.internal.bootstrap.SsoClientFailureAnalyzer;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SsoClientSpiFailureAnalyzerTest {

    @Test
    void reportsChineseTypeCountAndConcreteHostAction() {
        SsoClientConfigurationException cause = new SsoClientConfigurationException(
                List.of(new SsoClientConfigurationException.Violation(
                        SsoClientIdentityAdapter.class.getName(),
                        "恰好 1 个 Bean",
                        0)));

        FailureAnalysis analysis = new ExposedAnalyzer().analyzeCause(cause);

        assertThat(analysis.getDescription())
                .contains("SSO Client 启动合同校验失败")
                .contains(SsoClientIdentityAdapter.class.getName())
                .contains("恰好 1 个 Bean")
                .contains("实际 0 个");
        assertThat(analysis.getAction())
                .contains("必需 SPI")
                .contains("只注册一个实现")
                .contains("Redis");
    }

    private static final class ExposedAnalyzer extends SsoClientFailureAnalyzer {
        private FailureAnalysis analyzeCause(SsoClientConfigurationException cause) {
            return super.analyze(cause, cause);
        }
    }
}