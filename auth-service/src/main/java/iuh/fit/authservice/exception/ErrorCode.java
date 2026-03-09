package iuh.fit.authservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // Common
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Invalid key", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1006, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_REQUEST(1008, "Invalid request", HttpStatus.BAD_REQUEST),

    // User
    USER_NOT_EXISTED(1002, "User not existed", HttpStatus.NOT_FOUND),
    USER_NOT_ACTIVE(1003, "User is not active", HttpStatus.FORBIDDEN),
    USER_BLOCKED(1004, "User is blocked", HttpStatus.FORBIDDEN),
    ACCOUNT_DELETED(1005, "Account has been deleted", HttpStatus.GONE),
    ACCOUNT_CAN_BE_RESTORED(1009, "Account can be restored", HttpStatus.CONFLICT),

    // Auth
    INCORRECT_PASSWORD(2001, "Incorrect password", HttpStatus.UNAUTHORIZED),
    PASSWORD_ALREADY_SET(2002, "Password already set", HttpStatus.CONFLICT),
    INVALID_PASSWORD_FORMAT(2003, "Password must be at least 8 chars with uppercase, lowercase, number and special char", HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_SAME_AS_OLD(2004, "New password must be different from old password", HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(2005, "Token is invalid", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(2006, "Token has expired", HttpStatus.UNAUTHORIZED),

    // Registration / Duplicate
    PHONE_ALREADY_EXISTS(3001, "Phone number already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(3002, "Email already exists", HttpStatus.CONFLICT),
    GOOGLE_ACCOUNT_ALREADY_LINKED(3003, "Google account already linked to another user", HttpStatus.CONFLICT),

    // OTP
    OTP_NOT_FOUND(4001, "OTP not found or expired", HttpStatus.NOT_FOUND),
    OTP_ALREADY_USED(4002, "OTP has already been used", HttpStatus.CONFLICT),
    OTP_EXPIRED(4003, "OTP has expired", HttpStatus.GONE),
    OTP_MAX_ATTEMPTS(4004, "Maximum OTP attempts exceeded", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RATE_LIMIT(4005, "Too many OTP requests. Please try again later", HttpStatus.TOO_MANY_REQUESTS),
    OTP_INVALID(4006, "Invalid OTP code", HttpStatus.BAD_REQUEST),

    // Validation
    INVALID_PHONE_FORMAT(5001, "Invalid phone number format", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(5002, "Invalid email format", HttpStatus.BAD_REQUEST),
    PHONE_AND_EMAIL_REQUIRED(5003, "Both phone and email are required", HttpStatus.BAD_REQUEST),
    FULL_NAME_REQUIRED(5004, "Full name is required", HttpStatus.BAD_REQUEST),
    INVALID_FULL_NAME(5005, "Full name is too long", HttpStatus.BAD_REQUEST),

    // Google
    GOOGLE_AUTH_FAILED(6001, "Google authentication failed", HttpStatus.BAD_REQUEST),
    GOOGLE_TOKEN_INVALID(6002, "Invalid Google token", HttpStatus.BAD_REQUEST),

    // Session
    SESSION_NOT_FOUND(7001, "Session not found", HttpStatus.NOT_FOUND),

    // QR
    QR_CODE_NOT_FOUND(8001, "QR code not found", HttpStatus.NOT_FOUND),
    QR_CODE_EXPIRED(8002, "QR code has expired", HttpStatus.GONE),
    INVALID_QR_CODE(8003, "Invalid QR code", HttpStatus.BAD_REQUEST),
    QR_CODE_ALREADY_USED(8004, "QR code has already been used", HttpStatus.CONFLICT),
    INVALID_QR_STATUS(8005, "Invalid QR code status", HttpStatus.BAD_REQUEST),
    INVALID_DEVICE_ID(8006, "Device ID is required", HttpStatus.BAD_REQUEST),
    TOO_MANY_PENDING_QR_LOGINS(8007, "Too many pending QR login requests", HttpStatus.TOO_MANY_REQUESTS),

    // Email
    EMAIL_SEND_FAILED(9001, "Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR),

    // Internal
    INTERNAL_SERVICE_ERROR(9998, "Internal service communication error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}