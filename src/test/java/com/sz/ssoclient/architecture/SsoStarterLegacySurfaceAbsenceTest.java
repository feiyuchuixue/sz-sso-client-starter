package com.sz.ssoclient.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SsoStarterLegacySurfaceAbsenceTest {

    private static final List<String> LEGACY_TYPES = List.of(
            "com.sz.ssoclient.message.handler.SsoRegisterMessageHandler",
            "com.sz.ssoclient.spi.SsoClientRoleProvider",
            "com.sz.ssoclient.spi.SsoRoleBindingService",
            "com.sz.ssoclient.spi.SsoUserMappingService",
            "com.sz.ssoclient.spi.SsoClientUserProvisioningService",
            "com.sz.ssoclient.SsoClientUtil",
            "com.sz.ssoclient.pojo.SsoLoginResult");
    private static final Pattern LEGACY_SYMBOLS = Pattern.compile(
            "\\b(SsoRegisterMessageHandler|SsoClientRoleProvider|SsoRoleBindingService|"
                    + "SsoUserMappingService|SsoClientUserProvisioningService|SsoClientUtil|"
                    + "SsoLoginResult|logoutCall|COMPLETED|resetToCreated|REGISTER)\\b");

    @Test
    void legacyTypesHaveNoProductionSource() {
        List<Path> existing = LEGACY_TYPES.stream()
                .map(type -> sourceRoot().resolve(type.replace('.', '/') + ".java"))
                .filter(Files::exists)
                .map(sourceRoot()::relativize)
                .toList();
        assertThat(existing).isEmpty();
    }

    @Test
    void legacySymbolsHaveNoProductionHits() throws Exception {
        List<String> hits = new ArrayList<>();
        for (Path source : javaSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (LEGACY_SYMBOLS.matcher(text).find()) {
                hits.add(sourceRoot().relativize(source).toString());
            }
        }
        assertThat(hits).isEmpty();
    }

    private static List<Path> javaSources() throws IOException {
        try (var stream = Files.walk(sourceRoot())) {
            return stream.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static Path sourceRoot() {
        String configured = System.getProperty("starter.source.root");
        Path root = configured == null
                ? Path.of("src", "main", "java")
                : Path.of(configured);
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Starter source root does not exist: " + root);
        }
        return root.toAbsolutePath().normalize();
    }
}
