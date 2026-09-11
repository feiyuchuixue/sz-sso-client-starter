package com.sz.ssoclient.internal.bootstrap;

import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientIdentityPreparationService;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoDefaultAccessInitializer;
import com.sz.ssoclient.spi.SsoSuperAdminAuthorityAdapter;
import com.sz.ssoclient.spi.SsoSuperAdminSnapshotProvider;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssoclient.spi.advanced.SsoClientStateRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import java.util.ArrayList;
import java.util.List;

/** 在固定内核 Bean 实例化前一次性校验宿主 SPI 数量合同。 */
public final class SsoClientContractValidator
        implements BeanFactoryPostProcessor, PriorityOrdered {

    private final ListableBeanFactory testBeanFactory;

    public SsoClientContractValidator() {
        this.testBeanFactory = null;
    }

    public SsoClientContractValidator(ListableBeanFactory beanFactory) {
        this.testBeanFactory = java.util.Objects.requireNonNull(beanFactory, "beanFactory");
    }

    public void validate() {
        if (testBeanFactory == null) {
            throw new IllegalStateException("validate() 仅用于显式提供 BeanFactory 的验证场景");
        }
        validate(testBeanFactory);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        validate(beanFactory);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static void validate(ListableBeanFactory beanFactory) {
        List<SsoClientConfigurationException.Violation> violations = new ArrayList<>();
        requireExactlyOne(beanFactory, SsoClientIdentityAdapter.class, violations);
        requireExactlyOne(beanFactory, SsoClientLoginAdapter.class, violations);

        requireAtMostOne(beanFactory, SsoDefaultAccessInitializer.class, violations);
        requireAtMostOne(beanFactory, SsoSuperAdminAuthorityAdapter.class, violations);
        requireAtMostOne(beanFactory, SsoClientIdentityPreparationService.class, violations);
        requireAtMostOne(beanFactory, SsoSuperAdminSnapshotProvider.class, violations);
        requireAtMostOne(beanFactory, SsoClientLocalSessionAccessor.class, violations);
        requireAtMostOne(beanFactory, SsoClientStateRepository.class, violations);

        if (!violations.isEmpty()) {
            throw new SsoClientConfigurationException(violations);
        }
    }

    private static void requireExactlyOne(
            ListableBeanFactory beanFactory,
            Class<?> type,
            List<SsoClientConfigurationException.Violation> violations) {
        int count = beanFactory.getBeanNamesForType(type).length;
        if (count != 1) {
            violations.add(new SsoClientConfigurationException.Violation(
                    type.getName(), "恰好 1 个 Bean", count));
        }
    }

    private static void requireAtMostOne(
            ListableBeanFactory beanFactory,
            Class<?> type,
            List<SsoClientConfigurationException.Violation> violations) {
        int count = beanFactory.getBeanNamesForType(type).length;
        if (count > 1) {
            violations.add(new SsoClientConfigurationException.Violation(
                    type.getName(), "最多 1 个 Bean", count));
        }
    }
}
