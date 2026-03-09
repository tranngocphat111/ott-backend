package iuh.fit.userservice.exception;

import iuh.fit.userservice.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex, HttpServletRequest req) {
        ErrorCode ec = ex.getErrorCode();
        log.error("AppException: {} - {} at {}", ec.getCode(), ex.getMessage(), req.getRequestURI());
        return ResponseEntity.status(ec.getStatusCode())
                .body(ApiResponse.<Void>builder()
                        .code(ec.getCode())
                        .message(ex.getMessage() != null ? ex.getMessage() : ec.getMessage())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(e ->
                errors.put(((FieldError) e).getField(), e.getDefaultMessage()));
        log.error("Validation error: {}", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, String>>builder()
                        .code(ErrorCode.VALIDATION_FAILED.getCode())
                        .message("Validation failed")
                        .result(errors)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error at {}: ", req.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.<Void>builder()
                        .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                        .message("An unexpected error occurred")
                        .build());
    }
}