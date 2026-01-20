package iuh.fit.ottbackend.entity.enums;

import lombok.Getter;

@Getter
public enum QrLoginSessionStatus {
    WAITING("waiting"),
    AUTHORIZED("authorized"),
    REJECTED("rejected"),
    EXPIRED("expired");

    private final String value;

    QrLoginSessionStatus(String value) {
        this.value = value;
    }
}