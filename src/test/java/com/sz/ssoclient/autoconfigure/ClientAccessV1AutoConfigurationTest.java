package com.sz.ssoclient.autoconfigure;

import com.sz.ssoclient.clientaccess.http.ClientAccessV1Client;
import com.sz.ssoclient.clientaccess.state.ClientAccessStateRepository;
import com.sz.ssoclient.clientaccess.state.InMemoryClientAccessStateRepository;
import com.sz.ssoclient.clientaccess.state.RedisClientAccessStateRepository;
import com.sz.ssoclient.clientaccess.web.ClientBrowserBinding;
import com.sz.ssoclient.controller.ClientAccessInboundController;
import com.sz.ssoclient.message.SsoServerMessageDispatcher;
import com.sz.ssoclient.service.SsoClientService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClientAccessV1AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClientAccessV1AutoConfiguration.class))
            .withBean(SsoUserMappingService.class, () -> mock(SsoUserMappingService.class))
            .withBean(SsoClientService.class, () -> mock(SsoClientService.class))
            .withBean(SsoServerMessageDispatcher.class,
                    () -> new SsoServerMessageDispatcher(List.of(), List.of()));

    @Test
    void capStartsWithoutHostJackson2ObjectMapperOrAdditionalConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ClientAccessV1Client.class);
            assertThat(context).hasSingleBean(ClientAccessInboundController.class);
            assertThat(context).hasSingleBean(ClientBrowserBinding.class);
            assertThat(context).getBean(ClientAccessStateRepository.class)
                    .isInstanceOf(InMemoryClientAccessStateRepository.class);
        });
    }

    @Test
    void productionProfileFailsWithoutSharedStateRepository() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void productionProfileUsesHostRedisAsSharedStateRepository() {
        contextRunner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(ClientAccessStateRepository.class)
                            .isInstanceOf(RedisClientAccessStateRepository.class);
                });
    }
}
