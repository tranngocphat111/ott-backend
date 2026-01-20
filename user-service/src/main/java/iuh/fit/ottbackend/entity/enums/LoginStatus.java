package iuh.fit.ottbackend.entity.enums;

import lombok.Getter;

@Getter
public enum LoginStatus {
    SUCCESS("success"),
    FAILED("failed"),
    BLOCKED("blocked"),
    REQUIRES_2FA("requires_2fa");

    private final String value;

    LoginStatus(String value) {
        this.value = value;
    }
}