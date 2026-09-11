package fixture.boot4;




import com.sz.ssoclient.internal.bootstrap.InMemorySsoClientStateRepository;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoClientLoginContext;
import com.sz.ssoclient.spi.SsoClientLoginResult;
import com.sz.ssoclient.spi.SsoClientSessionHandle;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSession;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssoclient.spi.advanced.SsoClientStateRepository;
import com.sz.ssocore.SsoUserMeta;
import com.sz.ssocore.signout.SsoLocalOutcome;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@TestConfiguration(proxyBeanMethods = false)
public class RequiredSpiFixtureConfiguration {

    static final String CLIENT_FLAG = "boot4-fixture";
    static final String CLIENT_ORIGIN = "https://client.example.com";
    static final String SSO_ORIGIN = "https://sso.example.com";


    @Bean
    SsoClientIdentityAdapter ssoClientIdentityAdapter() {
        return identityAdapter();
    }

    @Bean
    SsoClientLoginAdapter<String> ssoClientLoginAdapter() {
        return loginAdapter();
    }

    @Bean
    SsoClientLocalSessionAccessor ssoClientLocalSessionAccessor() {
        return localSessionAccessor();
    }
    @Bean
    SsoClientStateRepository ssoClientStateRepository() {
        return new InMemorySsoClientStateRepository(Clock.systemUTC());
    }


    static SsoClientIdentityAdapter identityAdapter() {
        return new SsoClientIdentityAdapter() {
            @Override
            public Optional<Long> findSsoUserId(String localUserId) {
                return Optional.empty();
            }

            @Override
            public Map<String, Long> findSsoUserIds(Collection<String> localUserIds) {
                return Map.of();
            }

            @Override
            public Optional<String> findLocalUserId(long ssoUserId) {
                return ssoUserId == 42L ? Optional.of("local-42") : Optional.empty();
            }

            @Override
            public String resolveOrProvision(SsoUserMeta userMeta) {
                return "local-" + userMeta.getSsoUserId();
            }

            @Override
            public void completeDefaultAccessInitialization(String localUserId) {
                // Fixture only proves binary integration; the host owns this idempotent marker.
            }
        };
    }

    static SsoClientLoginAdapter<String> loginAdapter() {
        return new SsoClientLoginAdapter<>() {
            @Override
            public String loadLoginUser(String localUserId) {
                return localUserId;
            }

            @Override
            public SsoClientLoginResult establishSession(
                    String user, SsoClientLoginContext context) {
                return new SsoClientLoginResult(
                        "fixture-access-token",
                        new SsoClientSessionHandle("fixture-session"));
            }
        };
    }

    static SsoClientLocalSessionAccessor localSessionAccessor() {
        return new SsoClientLocalSessionAccessor() {
            private final SsoClientLocalSession current = new SsoClientLocalSession(
                    true,
                    "local-42",
                    "fixture-device",
                    new SsoClientSessionHandle("fixture-session"));

            @Override
            public SsoClientLocalSession current() {
                return current;
            }

            @Override
            public SsoLocalOutcome revokeExact(SsoClientSessionHandle handle) {
                return SsoLocalOutcome.REVOKED;
            }

            @Override
            public SsoLocalOutcome revokeDevice(String localUserId, String deviceId) {
                return SsoLocalOutcome.REVOKED;
            }

            @Override
            public SsoLocalOutcome revokeAccount(String localUserId) {
                return SsoLocalOutcome.REVOKED;
            }
        };
    }
}
