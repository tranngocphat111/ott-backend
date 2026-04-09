package iuh.fit.authservice.entity.enums;

import lombok.Getter;

@Getter
public enum QrCodeStatus {
    PENDING("pending"), SCANNED("scanned"), CONFIRMED("confirmed"),
    EXPIRED("expired"), CANCELLED("cancelled");
    private final String value;

    QrCodeStatus(String value) {
        this.value = value;
    }
}