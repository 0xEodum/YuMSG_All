package com.example.yumsg.core.data;

public class Progress {
    private String taskId;
    private int percentage;
    private String status;
    private long bytesTransferred;
    private long totalBytes;

    public Progress(String taskId, int percentage, String status) {
        this.taskId = taskId;
        this.percentage = percentage;
        this.status = status;
    }

    public String getTaskId() { return taskId; }
    public int getPercentage() { return percentage; }
    public String getStatus() { return status; }
    public long getBytesTransferred() { return bytesTransferred; }
    public long getTotalBytes() { return totalBytes; }
    public void setBytesTransferred(long bytes) { this.bytesTransferred = bytes; }
    public void setTotalBytes(long bytes) { this.totalBytes = bytes; }
}
