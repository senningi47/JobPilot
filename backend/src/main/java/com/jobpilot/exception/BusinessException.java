package com.jobpilot.exception;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BusinessException unauthorized() {
        return new BusinessException(1001, "Unauthorized");
    }

    public static BusinessException tokenExpired() {
        return new BusinessException(1002, "Token expired");
    }

    public static BusinessException userExists() {
        return new BusinessException(1003, "User already exists");
    }

    public static BusinessException notFound(int code, String msg) {
        return new BusinessException(code, msg);
    }

    public static BusinessException aiServiceUnavailable() {
        return new BusinessException(5002, "AI service unavailable");
    }

    public static BusinessException aiError(String message) {
        return new BusinessException(5003, message);
    }
}
