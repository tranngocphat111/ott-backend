package iuh.fit.ottbackend.entity.enums;

import lombok.Getter;

@Getter
public enum EmailType {
    WELCOME("welcome"),
    OTP_VERIFICATION("otp_verification"),
    PASSWORD_RESET("password_reset"),
    PASSWORD_CHANGED("password_changed"),
    EMAIL_CHANGED("email_changed"),
    PHONE_CHANGED("phone_changed"),
    TWO_FACTOR_ENABLED("two_factor_enabled"),
    TWO_FACTOR_DISABLED("two_factor_disabled"),
    NEW_LOGIN_ALERT("new_login_alert"),
    ACCOUNT_DELETED("account_deleted"),
    SECURITY_ALERT("security_alert");

    private final String value;

    EmailType(String value) {
        this.value = value;
    }
}