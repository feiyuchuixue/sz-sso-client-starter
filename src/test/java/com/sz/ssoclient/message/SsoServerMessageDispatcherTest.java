package com.sz.ssoclient.message;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.util.SaResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SsoServerMessageDispatcher 单元测试")
class SsoServerMessageDispatcherTest {

    @Test
    @DisplayName("dispatch() 应按 message type 路由到对应 handler")
    void dispatch_shouldRouteByMessageType() {
        SaSsoMessage message = new SaSsoMessage();
        message.setType("PING");
        SsoServerMessageDispatcher dispatcher = new SsoServerMessageDispatcher(List.of(new TestHandler("PING")), List.of());

        SaResult result = dispatcher.dispatch(null, message);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMsg()).isEqualTo("pong:PING");
    }

    @Test
    @DisplayName("dispatch() 应执行 pre/post 拦截器")
    void dispatch_shouldRunInterceptorChain() {
        List<String> events = new ArrayList<>();
        SaSsoMessage message = new SaSsoMessage();
        message.setType("PING");
        SsoMessageInterceptor interceptor = new SsoMessageInterceptor() {
            @Override
            public void preHandle(cn.dev33.satoken.sso.template.SaSsoTemplate template, SaSsoMessage message) {
                events.add("pre:" + message.getType());
            }

            @Override
            public void postHandle(cn.dev33.satoken.sso.template.SaSsoTemplate template, SaSsoMessage message, SaResult result) {
                events.add("post:" + result.getMsg());
            }
        };
        SsoServerMessageDispatcher dispatcher = new SsoServerMessageDispatcher(List.of(new TestHandler("PING")), List.of(interceptor));

        dispatcher.dispatch(null, message);

        assertThat(events).containsExactly("pre:PING", "post:pong:PING");
    }

    @Test
    @DisplayName("构造器应拒绝重复 message type")
    void constructor_shouldRejectDuplicateMessageType() {
        assertThatThrownBy(() -> new SsoServerMessageDispatcher(
                List.of(new TestHandler("PING"), new TestHandler("PING")), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate SSO message handler type");
    }

    private record TestHandler(String messageType) implements SsoServerMessageHandler {
        @Override
        public SaResult handle(cn.dev33.satoken.sso.template.SaSsoTemplate template, SaSsoMessage message) {
            return SaResult.ok("pong:" + message.getType());
        }
    }
}

