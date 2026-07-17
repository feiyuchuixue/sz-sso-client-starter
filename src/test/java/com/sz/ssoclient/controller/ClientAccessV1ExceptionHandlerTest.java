package com.sz.ssoclient.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAccessV1ExceptionHandlerTest {

    @Test
    void protocolAdviceRunsBeforeHostCatchAllAdvice() {
        Order order = AnnotationUtils.findAnnotation(ClientAccessV1ExceptionHandler.class, Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
