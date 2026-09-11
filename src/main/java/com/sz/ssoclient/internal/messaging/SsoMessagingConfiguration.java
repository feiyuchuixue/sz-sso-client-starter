package com.sz.ssoclient.internal.messaging;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.internal.firstparty.SsoFirstPartyMessageContribution;
import com.sz.ssoclient.internal.messaging.handler.SsoClientSessionRevocationHandler;
import com.sz.ssoclient.internal.messaging.handler.SsoClientSuperAdminBatchSyncHandler;
import com.sz.ssoclient.internal.messaging.handler.SsoClientSuperAdminSnapshotHandler;
import com.sz.ssoclient.internal.messaging.handler.SsoClientSuperAdminSyncHandler;
import com.sz.ssoclient.internal.messaging.handler.SsoClientUserPreparationBatchHandler;
import com.sz.ssoclient.internal.messaging.handler.SsoClientUserReadinessBatchHandler;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientIdentityPreparationService;
import com.sz.ssoclient.spi.SsoSuperAdminAuthorityAdapter;
import com.sz.ssoclient.spi.SsoSuperAdminSnapshotProvider;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssoclient.spi.advanced.SsoClientMessageHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Starter 唯一消息传输、注册和 pushC 入口的内部配置边界。 */
@Configuration(proxyBeanMethods = false)
public class SsoMessagingConfiguration {

    @Bean
    public SsoMessageCodec ssoMessageCodec() {
        return new SsoMessageCodec();
    }

    @Bean
    public SaTokenSsoMessageTransport saTokenSsoMessageTransport(
            SaSsoClientTemplate clientTemplate,
            SsoMessageCodec codec) {
        return new SaTokenSsoMessageTransport(clientTemplate, codec);
    }

    @Bean
    public SsoClientMessageGateway ssoClientMessageGateway(
            SaTokenSsoMessageTransport transport,
            SsoClientIdentityAdapter identityAdapter) {
        return new SsoClientMessageGateway(transport, identityAdapter);
    }

    @Bean
    public SsoClientUserReadinessBatchHandler ssoClientUserReadinessBatchHandler(
            SsoClientIdentityAdapter identityAdapter,
            ObjectProvider<SsoClientIdentityPreparationService> preparationService,
            SsoClientMessageGateway messageGateway) {
        return new SsoClientUserReadinessBatchHandler(
                identityAdapter, preparationService.getIfAvailable(), messageGateway);
    }

    @Bean
    public SsoClientUserPreparationBatchHandler ssoClientUserPreparationBatchHandler(
            ObjectProvider<SsoClientIdentityPreparationService> preparationService,
            SsoClientMessageGateway messageGateway) {
        return new SsoClientUserPreparationBatchHandler(
                preparationService.getIfAvailable(), messageGateway);
    }

    @Bean
    public SsoClientSuperAdminSyncHandler ssoClientSuperAdminSyncHandler(
            SsoClientIdentityAdapter identityAdapter,
            ObjectProvider<SsoSuperAdminAuthorityAdapter> authorityAdapter) {
        return new SsoClientSuperAdminSyncHandler(identityAdapter, authorityAdapter.getIfAvailable());
    }

    @Bean
    public SsoClientSuperAdminBatchSyncHandler ssoClientSuperAdminBatchSyncHandler(
            SsoClientIdentityAdapter identityAdapter,
            ObjectProvider<SsoSuperAdminAuthorityAdapter> authorityAdapter) {
        return new SsoClientSuperAdminBatchSyncHandler(identityAdapter, authorityAdapter.getIfAvailable());
    }

    @Bean
    public SsoClientSuperAdminSnapshotHandler ssoClientSuperAdminSnapshotHandler(
            SsoClientIdentityAdapter identityAdapter,
            ObjectProvider<SsoSuperAdminSnapshotProvider> snapshotProvider) {
        return new SsoClientSuperAdminSnapshotHandler(identityAdapter, snapshotProvider.getIfAvailable());
    }

    @Bean
    public SsoClientSessionRevocationHandler ssoClientSessionRevocationHandler(
            SsoClientIdentityAdapter identityAdapter,
            SsoClientLocalSessionAccessor localSessionAccessor) {
        return new SsoClientSessionRevocationHandler(identityAdapter, localSessionAccessor);
    }

    @Bean
    public SsoClientMessageRegistrar ssoClientMessageRegistrar(
            SaSsoClientTemplate clientTemplate,
            SsoMessageCodec codec,
            SsoClientUserReadinessBatchHandler readinessHandler,
            SsoClientUserPreparationBatchHandler preparationHandler,
            SsoClientSuperAdminSyncHandler superAdminHandler,
            SsoClientSuperAdminBatchSyncHandler superAdminBatchHandler,
            SsoClientSuperAdminSnapshotHandler snapshotHandler,
            SsoClientSessionRevocationHandler sessionRevocationHandler,
            ObjectProvider<SsoClientMessageHandler> customHandlers,
            ObjectProvider<SsoFirstPartyMessageContribution> firstPartyContributions) {
        List<SsoClientMessageRegistrar.FixedHandler> fixedHandlers = List.of(
                readinessHandler,
                preparationHandler,
                superAdminHandler,
                superAdminBatchHandler,
                snapshotHandler,
                sessionRevocationHandler);
        return new SsoClientMessageRegistrar(
                clientTemplate,
                codec,
                fixedHandlers,
                customHandlers.orderedStream().toList(),
                firstPartyContributions.orderedStream().toList());
    }

    @Bean
    public SmartInitializingSingleton ssoClientMessageRegistrarInitializer(
            SsoClientMessageRegistrar registrar) {
        return registrar::register;
    }

    @Bean
    public SsoClientPushController ssoClientPushController(SaSsoClientTemplate clientTemplate) {
        return new SsoClientPushController(clientTemplate);
    }
}
