package com.example.yumsg.core.data;

public class FileInfo {
    private String id;
    private String name;
    private long size;
    private String mimeType;
    private String path;

    public FileInfo() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
