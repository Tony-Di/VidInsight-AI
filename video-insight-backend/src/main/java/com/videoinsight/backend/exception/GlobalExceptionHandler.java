package com.videoinsight.backend.exception;

import com.videoinsight.backend.common.ApiResponse;
import com.videoinsight.backend.ratelimit.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "invalid request" : fieldError.getDefaultMessage();
        return ApiResponse.fail(400, message);
    }

    /**
     * 限流唯一一处 break 了"所有业务错误走 HTTP 200 + body code"的约定——
     * 返真正的 HTTP 429,因为 (1) 是 REST 标准,(2) 反向代理 / 监控 / curl --fail
     * 能直接识别为限流而不是泛业务错误,(3) 客户端可以基于 status code 直接做退避。
     */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(RateLimitExceededException.class)
    public ApiResponse<Void> handleRateLimit(RateLimitExceededException exception) {
        return ApiResponse.fail(429, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException() {
        return ApiResponse.fail(400, "request body is invalid");
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ApiResponse.fail(400, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        return ApiResponse.fail(500, exception.getMessage());
    }
}
