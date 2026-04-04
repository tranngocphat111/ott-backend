package iuh.fit.userservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1001, "You do not have permission", HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(1002, "Unauthenticated", HttpStatus.UNAUTHORIZED),

    USER_NOT_FOUND(1100, "User not found", HttpStatus.NOT_FOUND),
    USER_NOT_ACTIVE(1101, "User account is not active", HttpStatus.FORBIDDEN),
    USER_BLOCKED(1102, "User account is temporarily blocked", HttpStatus.FORBIDDEN),
    ACCOUNT_DELETED(1103, "Account has been deleted", HttpStatus.GONE),
    ACCOUNT_CAN_BE_RESTORED(6099, "Account can be restored", HttpStatus.BAD_REQUEST),

    INVALID_CREDENTIALS(1200, "Invalid phone or password", HttpStatus.UNAUTHORIZED),
    PASSWORD_NOT_SET(1201, "Password has not been set", HttpStatus.BAD_REQUEST),
    PASSWORD_ALREADY_SET(1202, "Password is already set", HttpStatus.BAD_REQUEST),
    INCORRECT_PASSWORD(1203, "Incorrect password", HttpStatus.UNAUTHORIZED),
    NEW_PASSWORD_SAME_AS_OLD(1204, "New password must be different", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(1205, "Password is required", HttpStatus.BAD_REQUEST),

    INVALID_PHONE_FORMAT(1300, "Invalid phone number format", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(1301, "Invalid email format", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD_FORMAT(1302, "Password must be at least 8 chars", HttpStatus.BAD_REQUEST),
    INVALID_FULL_NAME(1303, "Full name must be 1-100 characters", HttpStatus.BAD_REQUEST),
    INVALID_DATE_OF_BIRTH(1304, "Invalid date of birth", HttpStatus.BAD_REQUEST),
    INVALID_AVATAR_URL(1305, "Invalid avatar URL", HttpStatus.BAD_REQUEST),
    INVALID_COVER_URL(1306, "Invalid cover URL", HttpStatus.BAD_REQUEST),
    BIO_TOO_LONG(1307, "Bio must not exceed 500 characters", HttpStatus.BAD_REQUEST),
    FULL_NAME_REQUIRED(1308, "Full name is required", HttpStatus.BAD_REQUEST),

    PHONE_ALREADY_EXISTS(1400, "Phone number already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(1401, "Email already exists", HttpStatus.CONFLICT),
    PHONE_ALREADY_LINKED(1402, "Phone number already linked", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_LINKED(1403, "Email already linked", HttpStatus.BAD_REQUEST),
    EMAIL_MISMATCH(1404, "Email does not match", HttpStatus.BAD_REQUEST),
    PHONE_AND_EMAIL_REQUIRED(1070, "Both phone and email are required", HttpStatus.BAD_REQUEST),
    PHONE_MISMATCH(1071, "Phone number does not match", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED(1406, "Email is required", HttpStatus.BAD_REQUEST),

    OTP_NOT_FOUND(1500, "OTP not found or expired", HttpStatus.NOT_FOUND),
    OTP_EXPIRED(1501, "OTP has expired", HttpStatus.BAD_REQUEST),
    OTP_ALREADY_USED(1502, "OTP has already been used", HttpStatus.BAD_REQUEST),
    INVALID_OTP_CODE(1503, "Invalid OTP code", HttpStatus.BAD_REQUEST),
    OTP_MAX_ATTEMPTS_EXCEEDED(1504, "Max OTP attempts exceeded", HttpStatus.TOO_MANY_REQUESTS),
    // OTP_RATE_LIMIT_EXCEEDED(1505, "Too many OTP requests", HttpStatus.TOO_MANY_REQUESTS),
    OTP_BLOCKED(1506, "OTP verification is blocked", HttpStatus.TOO_MANY_REQUESTS),

    TWO_FACTOR_AUTH_ALREADY_ENABLED(1601, "2FA already enabled", HttpStatus.BAD_REQUEST),
    TWO_FACTOR_AUTH_NOT_ENABLED(1602, "2FA is not enabled", HttpStatus.BAD_REQUEST),

    SESSION_NOT_FOUND(1800, "Session not found", HttpStatus.NOT_FOUND),

    SAME_EMAIL(2100, "New email must differ from current", HttpStatus.BAD_REQUEST),
    SAME_PHONE(2101, "New phone must differ from current", HttpStatus.BAD_REQUEST),

    VALIDATION_FAILED(1111, "Validation failed", HttpStatus.BAD_REQUEST),

    OTP_RATE_LIMIT_EXCEEDED(1234, "otp rate limit exceeded", HttpStatus.BAD_REQUEST)
    ;



    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}