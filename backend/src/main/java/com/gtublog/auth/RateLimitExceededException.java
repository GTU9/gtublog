package com.gtublog.auth;

public class RateLimitExceededException extends IllegalArgumentException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
