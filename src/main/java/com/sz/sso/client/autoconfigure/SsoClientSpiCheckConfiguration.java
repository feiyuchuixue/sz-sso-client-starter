package com.sz.sso.client.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.sso.client.SsoClientLoginAdapter;
import com.sz.sso.client.SsoUserMappingService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * SSO Client SPI 缺失检测配置.
 * <p>
 * 在 {@link SsoClientAutoConfiguration} 之前加载。通过注册
 * {@link BeanDefinitionRegistryPostProcessor}，在所有 Bean 定义注册完成后、
 * 任何 Bean 实例化之前检测 SPI 是否存在。
 * 若缺失，则抛出 {@link SsoClientSpiMissingException}，
 * 由 {@link SsoClientSpiFailureAnalyzer} 转换为友好的启动失败提示。
 * </p>
 *
 * <p>使用 {@link BeanDefinitionRegistryPostProcessor} 而非 {@link org.springframework.beans.factory.InitializingBean}，
 * 确保检测发生在任何 Bean 依赖注入之前，避免被 Spring 原生的
 * {@code UnsatisfiedDependencyException} 抢先报出。
 * </p>
 */
@AutoConfiguration(before = SsoClientAutoConfiguration.class)
@ConditionalOnClass(SaSsoClientTemplate.class)
public class SsoClientSpiCheckConfiguration {

    @Bean
    public static BeanDefinitionRegistryPostProcessor ssoClientSpiChecker() {
        return new BeanDefinitionRegistryPostProcessor() {

            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
                // BeanDefinition 注册阶段：仅检查 BeanDefinition 是否存在
                // 此时 BeanFactory 尚未完全初始化，使用 registry 判断
                boolean hasMappingService = false;
                boolean hasLoginAdapter = false;

                for (String beanName : registry.getBeanDefinitionNames()) {
                    org.springframework.beans.factory.config.BeanDefinition bd = registry.getBeanDefinition(beanName);
                    String beanClassName = bd.getBeanClassName();
                    if (beanClassName == null) {
                        continue;
                    }
                    try {
                        Class<?> beanClass = Class.forName(beanClassName);
                        if (SsoUserMappingService.class.isAssignableFrom(beanClass)) {
                            hasMappingService = true;
                        }
                        if (SsoClientLoginAdapter.class.isAssignableFrom(beanClass)) {
                            hasLoginAdapter = true;
                        }
                    } catch (ClassNotFoundException ignored) {
                        // 类不在当前 classloader，跳过
                    }
                }

                List<String> missing = new ArrayList<>();
                if (!hasMappingService) {
                    missing.add(SsoUserMappingService.class.getName());
                }
                if (!hasLoginAdapter) {
                    missing.add(SsoClientLoginAdapter.class.getName());
                }
                if (!missing.isEmpty()) {
                    throw new SsoClientSpiMissingException(missing);
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                // 无需处理
            }
        };
    }
}
