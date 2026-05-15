package com.videoinsight.backend.ratelimit;

/**
 * 桶里没令牌时抛出。被 {@code GlobalExceptionHandler} 捕获后返 HTTP 429 +
 * ApiResponse(code=429, message)。
 */
public class RateLimitExceededException extends RuntimeException {

    private final String bucketKey;

    public RateLimitExceededException(String bucketKey, String message) {
        super(message);
        this.bucketKey = bucketKey;
    }

    public String getBucketKey() {
        return bucketKey;
    }
}
