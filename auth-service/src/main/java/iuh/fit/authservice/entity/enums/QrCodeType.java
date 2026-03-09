package iuh.fit.authservice.entity.enums;

import lombok.Getter;

@Getter
public enum QrCodeType {
    LOGIN("login"), PAYMENT("payment"), ADD_FRIEND("add_friend");
    private final String value;

    QrCodeType(String value) {
        this.value = value;
    }
}