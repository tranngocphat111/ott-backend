package iuh.fit.notificationservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {

    // ========== GENERAL ==========
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1001, "You do not have permission", HttpStatus.FORBIDDEN),

    // ========== VALIDATION ==========
    INVALID_EMAIL_FORMAT(1301, "Invalid email format", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(1111, "Validation failed", HttpStatus.BAD_REQUEST),

    // ========== OTP ==========
    OTP_RATE_LIMIT_EXCEEDED(1505, "Too many OTP requests. Please try again later", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_OTP_CODE(1503, "Invalid OTP code", HttpStatus.BAD_REQUEST),

    // ========== EMAIL ==========
    EMAIL_SEND_FAILED(2000, "Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR);

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}