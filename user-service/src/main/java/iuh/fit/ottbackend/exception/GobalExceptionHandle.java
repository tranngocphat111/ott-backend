package iuh.fit.ottbackend.exception;

import iuh.fit.ottbackend.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GobalExceptionHandle {

    // app
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(
            AppException ex,
            HttpServletRequest request) {

        ErrorCode errorCode = ex.getErrorCode();

        log.error("AppException: {} - {} at {}",
                errorCode.getCode(),
                ex.getMessage(),
                request.getRequestURI());

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage())
                .build();

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    // validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.error("Validation error: {}", errors);

        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
                .code(ErrorCode.VALIDATION_FAILED.getCode())
                .message("Validation failed")
                .result(errors)
                .build();

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    // access denied
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex) {

        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        log.error("Access denied: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    // bad credential
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            BadCredentialsException ex) {

        ErrorCode errorCode = ErrorCode.INVALID_CREDENTIALS;

        log.error("Bad credentials: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    // runtime
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;

        log.error("Runtime exception at {}: ", request.getRequestURI(), ex);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity
                .internalServerError()
                .body(response);
    }

    // chung
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(
            Exception ex,
            HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;

        log.error("Unexpected exception at {}: ", request.getRequestURI(), ex);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity
                .internalServerError()
                .body(response);
    }
}
