package com.example.yumsg.core.data;

public class AppError {
    private String code;
    private String message;
    private String details;
    private Throwable cause;

    public AppError(String code, String message, String details, Throwable cause) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.cause = cause;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getDetails() { return details; }
    public Throwable getCause() { return cause; }
}
