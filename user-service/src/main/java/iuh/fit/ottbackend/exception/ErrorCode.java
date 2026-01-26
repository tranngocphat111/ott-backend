package iuh.fit.ottbackend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    // ========== GENERAL ==========
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1001, "You do not have permission", HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(1002, "Unauthenticated", HttpStatus.UNAUTHORIZED),

    // ========== USER ERRORS ==========
    USER_NOT_EXISTED(1100, "User not existed", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(1100, "User not found", HttpStatus.NOT_FOUND),
    USER_NOT_ACTIVE(1101, "User account is not active", HttpStatus.FORBIDDEN),
    USER_BLOCKED(1102, "User account is temporarily blocked", HttpStatus.FORBIDDEN),
    ACCOUNT_DELETED(1103, "Account has been deleted", HttpStatus.GONE),
    ACCOUNT_ALREADY_DELETED(1104, "Account is already deleted", HttpStatus.BAD_REQUEST),

    // ========== AUTHENTICATION ==========
    INVALID_CREDENTIALS(1200, "Invalid phone or password", HttpStatus.UNAUTHORIZED),
    PASSWORD_NOT_SET(1201, "Password has not been set for this account", HttpStatus.BAD_REQUEST),
    PASSWORD_ALREADY_SET(1202, "Password is already set", HttpStatus.BAD_REQUEST),
    INCORRECT_PASSWORD(1203, "Incorrect password", HttpStatus.UNAUTHORIZED),
    NEW_PASSWORD_SAME_AS_OLD(1204, "New password must be different from old password", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(1205, "Password is required", HttpStatus.BAD_REQUEST),

    // ========== VALIDATION ==========
    INVALID_PHONE_FORMAT(1300, "Invalid phone number format", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(1301, "Invalid email format", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD_FORMAT(1302, "Password must be at least 8 characters with uppercase, lowercase, number and special character", HttpStatus.BAD_REQUEST),
    INVALID_FULL_NAME(1303, "Full name must be between 1 and 100 characters", HttpStatus.BAD_REQUEST),
    INVALID_DATE_OF_BIRTH(1304, "Invalid date of birth", HttpStatus.BAD_REQUEST),
    INVALID_AVATAR_URL(1305, "Invalid avatar URL", HttpStatus.BAD_REQUEST),
    INVALID_COVER_URL(1306, "Invalid cover URL", HttpStatus.BAD_REQUEST),
    BIO_TOO_LONG(1307, "Bio must not exceed 500 characters", HttpStatus.BAD_REQUEST),
    FULL_NAME_REQUIRED(1308, "Full name is required", HttpStatus.BAD_REQUEST),
    INVALID_DEVICE_ID(1309, "Invalid device ID", HttpStatus.BAD_REQUEST),

    // ========== REGISTRATION ==========
    PHONE_ALREADY_EXISTS(1400, "Phone number already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(1401, "Email already exists", HttpStatus.CONFLICT),
    PHONE_ALREADY_LINKED(1402, "Phone number is already linked to this account", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_LINKED(1403, "Email is already linked to this account", HttpStatus.BAD_REQUEST),
    EMAIL_MISMATCH(1404, "Email does not match user's registered email", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED_FOR_PASSWORD_RESET(1405, "Email is required for password reset", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED(1406, "Email is required", HttpStatus.BAD_REQUEST),

    // ========== OTP ==========
    OTP_NOT_FOUND(1500, "OTP not found or expired", HttpStatus.NOT_FOUND),
    OTP_EXPIRED(1501, "OTP has expired", HttpStatus.BAD_REQUEST),
    OTP_ALREADY_USED(1502, "OTP has already been used", HttpStatus.BAD_REQUEST),
    INVALID_OTP_CODE(1503, "Invalid OTP code", HttpStatus.BAD_REQUEST),
    OTP_MAX_ATTEMPTS_EXCEEDED(1504, "Maximum OTP verification attempts exceeded", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RATE_LIMIT_EXCEEDED(1505, "Too many OTP requests. Please try again later", HttpStatus.TOO_MANY_REQUESTS),
    OTP_BLOCKED(1506, "OTP verification is temporarily blocked", HttpStatus.TOO_MANY_REQUESTS),

    // ========== 2FA ==========
    TWO_FACTOR_AUTH_REQUIRED(1600, "Two-factor authentication is required", HttpStatus.UNAUTHORIZED),
    TWO_FACTOR_AUTH_ALREADY_ENABLED(1601, "Two-factor authentication is already enabled", HttpStatus.BAD_REQUEST),
    TWO_FACTOR_AUTH_NOT_ENABLED(1602, "Two-factor authentication is not enabled", HttpStatus.BAD_REQUEST),
    INVALID_2FA_CODE(1603, "Invalid two-factor authentication code", HttpStatus.UNAUTHORIZED),

    // ========== GOOGLE AUTH ==========
    GOOGLE_AUTH_FAILED(1700, "Google authentication failed", HttpStatus.UNAUTHORIZED),
    INVALID_GOOGLE_EMAIL(1701, "Invalid Google email", HttpStatus.BAD_REQUEST),
    GOOGLE_ACCOUNT_NOT_LINKED(1702, "Google account is not linked", HttpStatus.BAD_REQUEST),

    // ========== SESSION ==========
    SESSION_NOT_FOUND(1800, "Session not found", HttpStatus.NOT_FOUND),
    SESSION_EXPIRED(1801, "Session has expired", HttpStatus.UNAUTHORIZED),
    SESSION_REVOKED(1802, "Session has been revoked", HttpStatus.UNAUTHORIZED),
    INVALID_SESSION(1803, "Invalid session", HttpStatus.UNAUTHORIZED),

    // ========== QR CODE ==========
    QR_CODE_NOT_FOUND(1900, "QR code not found", HttpStatus.NOT_FOUND),
    QR_CODE_EXPIRED(1901, "QR code has expired", HttpStatus.BAD_REQUEST),
    QR_CODE_ALREADY_USED(1902, "QR code has already been used", HttpStatus.BAD_REQUEST),
    INVALID_QR_CODE(1903, "Invalid QR code", HttpStatus.BAD_REQUEST),
    INVALID_QR_STATUS(1904, "Invalid QR code status", HttpStatus.BAD_REQUEST),
    TOO_MANY_PENDING_QR_LOGINS(1905, "Too many pending QR login requests", HttpStatus.TOO_MANY_REQUESTS),

    // ========== EMAIL ==========
    EMAIL_SEND_FAILED(2000, "Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR),

    // ========== CHANGE SENSITIVE INFO ==========
    SAME_EMAIL(2100, "New email must be different from current email", HttpStatus.BAD_REQUEST),
    SAME_PHONE(2101, "New phone must be different from current phone", HttpStatus.BAD_REQUEST),
    EMAIL_CHANGE_FAILED(2102, "Failed to change email", HttpStatus.INTERNAL_SERVER_ERROR),
    PHONE_CHANGE_FAILED(2103, "Failed to change phone", HttpStatus.INTERNAL_SERVER_ERROR),

    INVALID_TOKEN(1023, "Invalid token", HttpStatus.UNAUTHORIZED),

    VALIDATION_FAILED(1111, "Validation failed", HttpStatus.BAD_REQUEST),


    PHONE_AND_EMAIL_REQUIRED(1070, "Both phone number and email are required", HttpStatus.BAD_REQUEST),
    PHONE_MISMATCH(1071, "Phone number does not match the account", HttpStatus.BAD_REQUEST),

    PASSWORD_REQUIRED_FOR_2FA(2322, "dsfs", HttpStatus.BAD_REQUEST)

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