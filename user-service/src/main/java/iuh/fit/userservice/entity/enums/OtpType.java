package iuh.fit.userservice.entity.enums;

import lombok.Getter;

@Getter
public enum OtpType {
    REGISTER("register"),
    EMAIL_VERIFICATION("email_verification"),
    LOGIN_OTP_EMAIL("login_otp_email"),
    TWO_FACTOR_AUTH("two_factor_auth"),
    RESET_PASSWORD("reset_password"),
    CHANGE_PASSWORD("change_password"),
    CHANGE_EMAIL("change_email"),
    CHANGE_PHONE("change_phone"),
    LINK_GOOGLE_ACCOUNT("link_google_account"),
    LINK_PHONE("link_phone"),
    LINK_EMAIL("link_email"),
    DELETE_ACCOUNT("delete_account"),
    ENABLE_TWO_FACTOR("enable_two_factor"),
    DISABLE_TWO_FACTOR("disable_two_factor");

    private final String value;
    OtpType(String value) { this.value = value; }
}