package iuh.fit.ottbackend.entity.enums;

import lombok.Getter;

@Getter
public enum LoginMethod {
    LOCAL("local"),
    OTP("otp"),
    QR_CODE("qr_code"),
    GOOGLE("google");

    private final String value;

    LoginMethod(String value) {
        this.value = value;
    }
}
