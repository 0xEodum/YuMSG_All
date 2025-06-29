package com.example.yumsg.core.data;

public class AuthResult {
    private boolean success;
    private String token;
    private String message;

    public AuthResult(boolean success, String token, String message) {
        this.success = success;
        this.token = token;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getToken() { return token; }
    public String getMessage() { return message; }
}
