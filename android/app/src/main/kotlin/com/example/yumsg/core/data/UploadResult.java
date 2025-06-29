package com.example.yumsg.core.data;

public class UploadResult {
    private boolean success;
    private String fileId;
    private String url;
    private String message;

    public UploadResult(boolean success, String fileId, String url, String message) {
        this.success = success;
        this.fileId = fileId;
        this.url = url;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getFileId() { return fileId; }
    public String getUrl() { return url; }
    public String getMessage() { return message; }
}
