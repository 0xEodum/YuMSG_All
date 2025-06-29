package com.example.yumsg.core.data;

public class ConnectionResult {
    private boolean success;
    private String message;

    public ConnectionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
