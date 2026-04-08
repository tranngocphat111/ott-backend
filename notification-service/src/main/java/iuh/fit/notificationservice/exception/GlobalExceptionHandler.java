package iuh.fit.notificationservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(
            AppException ex, HttpServletRequest request) {

        ErrorCode errorCode = ex.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(Map.of(
                        "code", errorCode.getCode(),
                        "message", errorCode.getMessage(),
                        "result", Map.of(), // đồng bộ format
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage()); // KEY
        });

        return ResponseEntity.badRequest().body(Map.of(
                "code", 1111,
                "message", "VALIDATION_FAILED",
                "result", errors,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception ex, HttpServletRequest request) {

        return ResponseEntity.internalServerError().body(Map.of(
                "code", 9999,
                "message", "UNCATEGORIZED_EXCEPTION",
                "result", Map.of(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}