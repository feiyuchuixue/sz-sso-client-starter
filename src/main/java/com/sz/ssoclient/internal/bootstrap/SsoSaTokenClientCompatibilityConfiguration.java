package com.sz.ssoclient.internal.bootstrap;

import cn.dev33.satoken.sso.SaSsoManager;
import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 统一补齐 Sa-Token SSO Client 的 Spring 配置绑定与默认模板。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
        SsoSaTokenClientCompatibilityConfiguration.SaTokenSsoClientProperties.class)
public class SsoSaTokenClientCompatibilityConfiguration {

    @Bean
    public static BeanPostProcessor ssoSaTokenClientConfigBridge(
            SaTokenSsoClientProperties properties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {
                if (bean instanceof SaSsoClientConfig config) {
                    properties.applyTo(config);
                    SaSsoManager.setClientConfig(config);
                } else if (bean instanceof SaSsoClientTemplate template) {
                    SaSsoClientProcessor.instance.ssoClientTemplate = template;
                    SaSsoClientConfig config = SaSsoManager.getClientConfig();
                    properties.applyTo(config);
                    SaSsoManager.setClientConfig(config);
                }
                return bean;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(SaSsoClientTemplate.class)
    public SaSsoClientTemplate ssoStarterSaSsoClientTemplate() {
        return new SaSsoClientTemplate();
    }

    /**
     * Sa-Token 1.45.0 的大部分 setter 返回配置对象。Boot 4 不将这类 fluent
     * setter 识别为 JavaBean 写方法，因此使用标准写方法接收同名配置后再同步。
     */
    @ConfigurationProperties(prefix = "sa-token.sso-client")
    static final class SaTokenSsoClientProperties {
        private String mode;
        private String client;
        private String serverUrl;
        private String authUrl;
        private String signoutUrl;
        private String pushUrl;
        private String getDataUrl;
        private String currSsoLogin;
        private String currSsoLogoutCall;
        private Boolean isHttp;
        private Boolean isSlo;
        private Boolean regLogoutCall;
        private String secretKey;
        private Boolean isCheckSign;

        void applyTo(SaSsoClientConfig target) {
            if (mode != null) target.setMode(mode);
            if (client != null) target.setClient(client);
            if (serverUrl != null) target.setServerUrl(serverUrl);
            if (authUrl != null) target.setAuthUrl(authUrl);
            if (signoutUrl != null) target.setSignoutUrl(signoutUrl);
            if (pushUrl != null) target.setPushUrl(pushUrl);
            if (getDataUrl != null) target.setGetDataUrl(getDataUrl);
            if (currSsoLogin != null) target.setCurrSsoLogin(currSsoLogin);
            if (currSsoLogoutCall != null) target.setCurrSsoLogoutCall(currSsoLogoutCall);
            if (isHttp != null) target.setIsHttp(isHttp);
            if (isSlo != null) target.setIsSlo(isSlo);
            if (regLogoutCall != null) target.setRegLogoutCall(regLogoutCall);
            if (secretKey != null) target.setSecretKey(secretKey);
            if (isCheckSign != null) target.setIsCheckSign(isCheckSign);
        }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getClient() { return client; }
        public void setClient(String client) { this.client = client; }
        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
        public String getAuthUrl() { return authUrl; }
        public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }
        public String getSignoutUrl() { return signoutUrl; }
        public void setSignoutUrl(String signoutUrl) { this.signoutUrl = signoutUrl; }
        public String getPushUrl() { return pushUrl; }
        public void setPushUrl(String pushUrl) { this.pushUrl = pushUrl; }
        public String getGetDataUrl() { return getDataUrl; }
        public void setGetDataUrl(String getDataUrl) { this.getDataUrl = getDataUrl; }
        public String getCurrSsoLogin() { return currSsoLogin; }
        public void setCurrSsoLogin(String currSsoLogin) { this.currSsoLogin = currSsoLogin; }
        public String getCurrSsoLogoutCall() { return currSsoLogoutCall; }
        public void setCurrSsoLogoutCall(String value) { this.currSsoLogoutCall = value; }
        public Boolean getIsHttp() { return isHttp; }
        public void setIsHttp(Boolean isHttp) { this.isHttp = isHttp; }
        public Boolean getIsSlo() { return isSlo; }
        public void setIsSlo(Boolean isSlo) { this.isSlo = isSlo; }
        public Boolean getRegLogoutCall() { return regLogoutCall; }
        public void setRegLogoutCall(Boolean value) { this.regLogoutCall = value; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public Boolean getIsCheckSign() { return isCheckSign; }
        public void setIsCheckSign(Boolean value) { this.isCheckSign = value; }
    }
}