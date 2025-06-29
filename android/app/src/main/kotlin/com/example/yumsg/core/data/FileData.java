package com.yumsg.core.data;

public class FileData {
    private String path;
    private String name;
    private String mimeType;
    private long size;
    private byte[] data;

    public FileData(String path, String name, String mimeType, long size) {
        this.path = path;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
    }

    public String getPath() { return path; }
    public String getName() { return name; }
    public String getMimeType() { return mimeType; }
    public long getSize() { return size; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
}
