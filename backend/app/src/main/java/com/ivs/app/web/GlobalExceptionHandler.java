package com.ivs.app.web;

import com.ivs.shared.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 统一错误响应：把可预期业务异常与常见框架异常转换为稳定错误码。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException exception) {
        HttpStatus status;
        if ("NOT_FOUND".equals(exception.code())) {
            status = HttpStatus.NOT_FOUND;
        } else if (exception.code().startsWith("AUTH_")) {
            status = HttpStatus.UNAUTHORIZED;
        } else {
            status = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), exception.code(), exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), "VALIDATION_ERROR", "请求参数校验失败", details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), "INVALID_PARAMETER", "参数格式错误: " + exception.getName(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), "MALFORMED_REQUEST", "请求内容无法解析", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(Instant.now(), "DATA_CONFLICT", "数据冲突，请刷新后重试", null));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(Instant.now(), "CONCURRENT_MODIFICATION", "数据已被其他操作修改，请刷新后重试", null));
    }
}