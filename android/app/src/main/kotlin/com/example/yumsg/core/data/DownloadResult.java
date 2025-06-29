package com.yumsg.core.data;

public class DownloadResult {
    private boolean success;
    private FileInfo fileInfo;
    private String localPath;
    private String message;

    public DownloadResult(boolean success, FileInfo fileInfo, String localPath, String message) {
        this.success = success;
        this.fileInfo = fileInfo;
        this.localPath = localPath;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public FileInfo getFileInfo() { return fileInfo; }
    public String getLocalPath() { return localPath; }
    public String getMessage() { return message; }
}
