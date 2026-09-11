package com.sz.ssoclient.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SsoStarterPublicSurfaceTest {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([^;]+);");
    private static final Set<String> STABLE_PACKAGES = Set.of(
            "com.sz.ssoclient.api",
            "com.sz.ssoclient.api.browser",
            "com.sz.ssoclient.spi",
            "com.sz.ssoclient.spi.advanced");
    private static final List<String> INTERNAL_PREFIXES = List.of(
            "com.sz.ssoclient.internal.bootstrap",
            "com.sz.ssoclient.internal.firstparty",
            "com.sz.ssoclient.internal.messaging",
            "com.sz.ssoclient.internal.login",
            "com.sz.ssoclient.internal.web");

    @Test
    void productionSourcesStayInsideFrozenStableOrInternalPackages() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            String packageName = packageName(text);
            boolean allowed = STABLE_PACKAGES.contains(packageName)
                    || INTERNAL_PREFIXES.stream().anyMatch(prefix -> packageName.equals(prefix)
                    || packageName.startsWith(prefix + "."))
                    || source.getFileName().toString().equals("SsoClientAutoConfiguration.java");
            if (!allowed) {
                violations.add(sourceRoot().relativize(source) + " -> " + packageName);
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void stableApiAndSpiDoNotReferenceFrameworkOrInternalTypes() throws Exception {
        List<String> forbidden = List.of(
                "com.sz.ssoclient.internal",
                "cn.dev33.satoken.util.SaResult",
                "cn.dev33.satoken.sso.message.SaSsoMessage",
                "cn.dev33.satoken.sso.template",
                "cn.dev33.satoken.stp.parameter.SaLoginParameter",
                "com.fasterxml.jackson",
                "org.springframework");
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (!STABLE_PACKAGES.contains(packageName(text))) {
                continue;
            }
            forbidden.stream()
                    .filter(text::contains)
                    .forEach(hit -> violations.add(
                            sourceRoot().relativize(source) + " -> " + hit));
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void hasOnlyOneAutoConfigurationClass() throws Exception {
        List<Path> autoConfigurations = new ArrayList<>();
        for (Path source : javaSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (text.contains("@AutoConfiguration")) {
                autoConfigurations.add(sourceRoot().relativize(source));
            }
        }
        assertThat(autoConfigurations).containsExactly(
                Path.of("com", "sz", "ssoclient", "autoconfigure",
                        "SsoClientAutoConfiguration.java"));
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

    private static String packageName(String source) {
        Matcher matcher = PACKAGE.matcher(source);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Java source has no package declaration");
        }
        return matcher.group(1);
    }
}
