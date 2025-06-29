package com.example.yumsg.core.data;

public class TransferResult {
    private boolean success;
    private String transferId;
    private String message;

    public TransferResult(boolean success, String transferId, String message) {
        this.success = success;
        this.transferId = transferId;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getTransferId() { return transferId; }
    public String getMessage() { return message; }
}
