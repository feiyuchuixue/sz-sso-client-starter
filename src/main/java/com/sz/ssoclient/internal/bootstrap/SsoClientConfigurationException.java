package com.sz.ssoclient.internal.bootstrap;

import java.util.List;

/** Starter 启动合同不满足时的统一可分析异常。 */
public class SsoClientConfigurationException extends RuntimeException {

    private final List<Violation> violations;

    public SsoClientConfigurationException(List<Violation> violations) {
        super(message(violations));
        this.violations = List.copyOf(violations);
        if (this.violations.isEmpty()) {
            throw new IllegalArgumentException("violations must not be empty");
        }
    }

    public SsoClientConfigurationException(String message) {
        super(message);
        this.violations = List.of();
    }

    public List<Violation> violations() {
        return violations;
    }

    private static String message(List<Violation> violations) {
        if (violations == null || violations.isEmpty()) {
            return "SSO Client 启动合同不满足";
        }
        return violations.stream()
                .map(violation -> violation.typeName() + " 需要"
                        + violation.expectation() + "，实际 " + violation.actualCount() + " 个")
                .collect(java.util.stream.Collectors.joining("；"));
    }

    public record Violation(String typeName, String expectation, int actualCount) {

        public Violation {
            if (typeName == null || typeName.isBlank()) {
                throw new IllegalArgumentException("typeName must not be blank");
            }
            if (expectation == null || expectation.isBlank()) {
                throw new IllegalArgumentException("expectation must not be blank");
            }
            if (actualCount < 0) {
                throw new IllegalArgumentException("actualCount must not be negative");
            }
        }
    }
}
