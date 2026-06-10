package com.sz.ssoclient.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AutoConfiguration.imports 单元测试")
class SsoClientAutoConfigurationImportsTest {

    @Test
    @DisplayName("AutoConfiguration.imports 应声明拆分后的职责配置")
    void imports_shouldContainSplitConfigurations() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).isNotNull();
            String imports = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports)
                    .contains("SsoClientSyncAutoConfiguration")
                    .contains("SsoClientTemplateAutoConfiguration")
                    .contains("SsoClientMessageAutoConfiguration")
                    .contains("SsoClientLoginAutoConfiguration")
                    .contains("SsoClientAutoConfiguration");
        }
    }
}

