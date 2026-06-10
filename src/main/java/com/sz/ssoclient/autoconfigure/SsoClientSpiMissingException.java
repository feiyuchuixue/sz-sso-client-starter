package com.sz.ssoclient.autoconfigure;

import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoUserMappingService;

import java.util.List;

/**
 * SSO Client SPI 接口缺失异常.
 * <p>
 * 当 classpath 中存在 Sa-Token SSO Client 依赖，但容器中缺少
 * {@link SsoUserMappingService} 或
 * {@link SsoClientLoginAdapter} 的实现 Bean 时抛出。
 * </p>
 *
 * @see SsoClientSpiFailureAnalyzer
 */
public class SsoClientSpiMissingException extends RuntimeException {

    private final List<String> missingSpiInterfaces;

    public SsoClientSpiMissingException(List<String> missingSpiInterfaces) {
        super("SSO Client SPI 接口未实现: " + missingSpiInterfaces);
        this.missingSpiInterfaces = missingSpiInterfaces;
    }

    public List<String> getMissingSpiInterfaces() {
        return missingSpiInterfaces;
    }
}


