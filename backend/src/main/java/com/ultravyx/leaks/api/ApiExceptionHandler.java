package com.ultravyx.leaks.api;

import com.ultravyx.leaks.api.ApiDtos.ApiError;
import com.ultravyx.leaks.api.ApiDtos.RowError;
import com.ultravyx.leaks.service.BadRequestException;
import com.ultravyx.leaks.service.ConflictException;
import com.ultravyx.leaks.service.NotFoundException;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;
    public ApiExceptionHandler(Clock clock) { this.clock = clock; }
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> bad(BadRequestException ex) { return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), List.of()); }
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> missing(NotFoundException ex) { return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), List.of()); }
    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> conflict(Exception ex) { return error(HttpStatus.CONFLICT, "CONFLICT", "External lead ID already exists or data conflicts with a constraint", List.of()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        List<RowError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new RowError(0, f.getField(), f.getDefaultMessage())).toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please correct the highlighted fields", details);
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> invalidValue(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_VALUE", "Invalid parameter or request body value", List.of());
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.BAD_REQUEST, "UPLOAD_TOO_LARGE", "CSV exceeds the 10 MB upload limit", List.of());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", List.of());
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, List<RowError> details) {
        return ResponseEntity.status(status).body(new ApiError(clock.instant(), status.value(), code, message, details));
    }
}
