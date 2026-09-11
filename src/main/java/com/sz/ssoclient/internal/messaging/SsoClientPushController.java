package com.sz.ssoclient.internal.messaging;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 仅承载 Sa-Token 原生 Server → Client 签名消息的内部端点。 */
@RestController
@RequestMapping("/sso")
@SaIgnore
public class SsoClientPushController {

    private final SaSsoClientProcessor processor;

    public SsoClientPushController(SaSsoClientTemplate clientTemplate) {
        this(newProcessor(clientTemplate));
    }

    SsoClientPushController(SaSsoClientProcessor processor) {
        this.processor = Objects.requireNonNull(processor, "processor");
    }

    @RequestMapping("/pushC")
    public Object receivePush() {
        return processor.ssoPushC();
    }

    private static SaSsoClientProcessor newProcessor(SaSsoClientTemplate clientTemplate) {
        SaSsoClientProcessor processor = new SaSsoClientProcessor();
        processor.ssoClientTemplate = Objects.requireNonNull(clientTemplate, "clientTemplate");
        return processor;
    }
}
