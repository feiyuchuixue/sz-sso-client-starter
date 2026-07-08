package com.sz.ssoclient.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SSO message HTTP request timeout settings.
 */
@ConfigurationProperties(prefix = "sz.sso-client.message-http")
public class SsoClientMessageHttpProperties {

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    int connectTimeoutMillis() {
        return toPositiveMillis(connectTimeout, Duration.ofSeconds(5));
    }

    int readTimeoutMillis() {
        return toPositiveMillis(readTimeout, Duration.ofSeconds(30));
    }

    private static int toPositiveMillis(Duration value, Duration defaultValue) {
        Duration resolved = value == null || value.isZero() || value.isNegative() ? defaultValue : value;
        long millis = resolved.toMillis();
        if (millis <= 0) {
            millis = defaultValue.toMillis();
        }
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}