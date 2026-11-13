package com.tablebite.service;

/** A business error that maps to a specific HTTP status and error code. */
public class ServiceException extends RuntimeException {
    private final int status;
    private final String code;

    public ServiceException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ServiceException notFound(String what) {
        return new ServiceException(404, "NOT_FOUND", what + " was not found");
    }

    public static ServiceException unauthorized(String message) {
        return new ServiceException(401, "AUTH_REQUIRED", message);
    }

    public static ServiceException forbidden(String message) {
        return new ServiceException(403, "FORBIDDEN", message);
    }

    public static ServiceException conflict(String message) {
        return new ServiceException(409, "CONFLICT", message);
    }

    public static ServiceException tooManyRequests(String message) {
        return new ServiceException(429, "TOO_MANY_REQUESTS", message);
    }
}
