package com.sz.ssoclient.autoconfigure;

import com.sz.ssoclient.internal.bootstrap.SsoClientContractValidator;
import com.sz.ssoclient.internal.bootstrap.SsoClientCoreConfiguration;
import com.sz.ssoclient.internal.bootstrap.SsoClientEndpointConfiguration;
import com.sz.ssoclient.internal.bootstrap.SsoClientInfrastructureConfiguration;
import com.sz.ssoclient.internal.bootstrap.SsoClientSpiConfiguration;
import com.sz.ssoclient.internal.bootstrap.SsoClientConfigurationException;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoDefaultAccessInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Import;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SsoClientAutoConfigurationContractTest {

    @Test
    void exposesExactlyOnePublicAutoConfigurationEntry() throws Exception {
        String resource = "META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        Enumeration<URL> resources = getClass().getClassLoader().getResources(resource);
        List<List<String>> starterEntries = new ArrayList<>();
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                List<String> entries = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))
                        .lines()
                        .filter(line -> !line.isBlank())
                        .toList();
                if (entries.contains(SsoClientAutoConfiguration.class.getName())) {
                    starterEntries.add(entries);
                }
            }
        }
        assertThat(starterEntries).containsExactly(
                List.of(SsoClientAutoConfiguration.class.getName()));
    }

    @Test
    void importsExactlyFourOrdinaryInternalBoundaries() {
        Import imported = SsoClientAutoConfiguration.class.getAnnotation(Import.class);
        assertThat(imported.value()).containsExactlyInAnyOrder(
                SsoClientCoreConfiguration.class,
                SsoClientSpiConfiguration.class,
                SsoClientInfrastructureConfiguration.class,
                SsoClientEndpointConfiguration.class);
        assertThat(List.of(
                SsoClientCoreConfiguration.class,
                SsoClientSpiConfiguration.class,
                SsoClientInfrastructureConfiguration.class,
                SsoClientEndpointConfiguration.class))
                .allSatisfy(type -> assertThat(type.isAnnotationPresent(AutoConfiguration.class))
                        .isFalse());
    }

    @Test
    void validatorRequiresExactlyOneRequiredSpiAndAtMostOneOptionalSpi() {
        DefaultListableBeanFactory valid = new DefaultListableBeanFactory();
        valid.registerSingleton("identity", mock(SsoClientIdentityAdapter.class));
        valid.registerSingleton("login", mock(SsoClientLoginAdapter.class));
        assertThatCode(() -> new SsoClientContractValidator(valid).validate())
                .doesNotThrowAnyException();

        DefaultListableBeanFactory missing = new DefaultListableBeanFactory();
        missing.registerSingleton("identity", mock(SsoClientIdentityAdapter.class));
        assertThatThrownBy(() -> new SsoClientContractValidator(missing).validate())
                .isInstanceOf(SsoClientConfigurationException.class)
                .hasMessageContaining(SsoClientLoginAdapter.class.getName())
                .hasMessageContaining("恰好 1 个");

        DefaultListableBeanFactory duplicateOptional = new DefaultListableBeanFactory();
        duplicateOptional.registerSingleton("identity", mock(SsoClientIdentityAdapter.class));
        duplicateOptional.registerSingleton("login", mock(SsoClientLoginAdapter.class));
        duplicateOptional.registerSingleton("defaultAccess1", mock(SsoDefaultAccessInitializer.class));
        duplicateOptional.registerSingleton("defaultAccess2", mock(SsoDefaultAccessInitializer.class));
        assertThatThrownBy(() -> new SsoClientContractValidator(duplicateOptional).validate())
                .isInstanceOf(SsoClientConfigurationException.class)
                .hasMessageContaining(SsoDefaultAccessInitializer.class.getName())
                .hasMessageContaining("最多 1 个");
    }
}
