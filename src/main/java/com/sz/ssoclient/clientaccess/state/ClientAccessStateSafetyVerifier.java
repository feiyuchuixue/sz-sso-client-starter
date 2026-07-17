package com.sz.ssoclient.clientaccess.state;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Prevents production-like deployments from silently using local CAP state. */
public final class ClientAccessStateSafetyVerifier implements SmartInitializingSingleton {

    private static final Set<String> STRICT_PROFILES = Set.of("prod", "production", "preview");

    private final ClientAccessStateRepository repository;
    private final Environment environment;

    public ClientAccessStateSafetyVerifier(ClientAccessStateRepository repository, Environment environment) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public void verify() {
        String strictProfile = Arrays.stream(environment.getActiveProfiles())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(STRICT_PROFILES::contains)
                .findFirst()
                .orElse(null);
        if (strictProfile != null && !repository.shared()) {
            throw new IllegalStateException("CAP V1 requires a shared ClientAccessStateRepository for profile '"
                    + strictProfile + "'; current backend is " + repository.description());
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        verify();
    }
}
