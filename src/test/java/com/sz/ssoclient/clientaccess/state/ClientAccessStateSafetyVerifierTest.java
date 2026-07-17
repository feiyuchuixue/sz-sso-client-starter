package com.sz.ssoclient.clientaccess.state;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientAccessStateSafetyVerifierTest {

    @Test
    void productionProfilesRejectLocalStateRepository() {
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(Clock.systemUTC());
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ClientAccessStateSafetyVerifier(repository, environment).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared ClientAccessStateRepository")
                .hasMessageContaining("prod");
    }

    @Test
    void localProfileAllowsExplicitLocalRepository() {
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(Clock.systemUTC());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatCode(() -> new ClientAccessStateSafetyVerifier(repository, environment).verify())
                .doesNotThrowAnyException();
    }

    @Test
    void productionProfileAcceptsSharedRepository() {
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(Clock.systemUTC()) {
            @Override
            public boolean shared() {
                return true;
            }

            @Override
            public String description() {
                return "shared-test-store";
            }
        };
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        assertThatCode(() -> new ClientAccessStateSafetyVerifier(repository, environment).verify())
                .doesNotThrowAnyException();
    }
}
