package iuh.fit.ottbackend.entity.enums;

import lombok.Getter;

@Getter
public enum EmailStatus {
    PENDING("pending"),
    SENT("sent"),
    FAILED("failed"),
    BOUNCED("bounced");

    private final String value;

    EmailStatus(String value) {
        this.value = value;
    }
}