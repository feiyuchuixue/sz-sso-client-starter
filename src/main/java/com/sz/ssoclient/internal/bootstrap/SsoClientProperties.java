package com.sz.ssoclient.internal.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Starter 自有的最小启动、安全和基础设施配置。 */
@ConfigurationProperties(prefix = "sz.sso-client")
public class SsoClientProperties {

    private boolean enabled = true;
    private URI externalOrigin;
    private Boolean allowMissingBrowserSource;
    private Boolean secureCookie;
    private int messageConnectTimeoutMillis = 3000;
    private int messageReadTimeoutMillis = 5000;
    private String portalLoginPath = "/portal-login";
    private List<String> allowedAbsoluteBackUrls = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getExternalOrigin() {
        return externalOrigin;
    }

    public void setExternalOrigin(URI externalOrigin) {
        this.externalOrigin = externalOrigin;
    }

    public Boolean getAllowMissingBrowserSource() {
        return allowMissingBrowserSource;
    }

    public void setAllowMissingBrowserSource(Boolean allowMissingBrowserSource) {
        this.allowMissingBrowserSource = allowMissingBrowserSource;
    }

    public Boolean getSecureCookie() {
        return secureCookie;
    }

    public void setSecureCookie(Boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public int getMessageConnectTimeoutMillis() {
        return messageConnectTimeoutMillis;
    }

    public void setMessageConnectTimeoutMillis(int messageConnectTimeoutMillis) {
        this.messageConnectTimeoutMillis = messageConnectTimeoutMillis;
    }

    public int getMessageReadTimeoutMillis() {
        return messageReadTimeoutMillis;
    }

    public void setMessageReadTimeoutMillis(int messageReadTimeoutMillis) {
        this.messageReadTimeoutMillis = messageReadTimeoutMillis;
    }

    public String getPortalLoginPath() {
        return portalLoginPath;
    }

    public void setPortalLoginPath(String portalLoginPath) {
        this.portalLoginPath = portalLoginPath;
    }

    public List<String> getAllowedAbsoluteBackUrls() {
        return List.copyOf(allowedAbsoluteBackUrls);
    }

    public void setAllowedAbsoluteBackUrls(List<String> allowedAbsoluteBackUrls) {
        this.allowedAbsoluteBackUrls = allowedAbsoluteBackUrls == null
                ? new ArrayList<>()
                : new ArrayList<>(allowedAbsoluteBackUrls);
    }
}
