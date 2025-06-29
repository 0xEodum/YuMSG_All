package com.yumsg.core.data;

public class UpdateResult {
    private boolean success;
    private String message;

    public UpdateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
