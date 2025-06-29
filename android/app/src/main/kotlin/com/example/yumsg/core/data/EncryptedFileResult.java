package com.example.yumsg.core.data;

public class EncryptedFileResult {
    private String encryptedFilePath;
    private byte[] hash;
    private long size;

    public EncryptedFileResult(String encryptedFilePath, byte[] hash, long size) {
        this.encryptedFilePath = encryptedFilePath;
        this.hash = hash;
        this.size = size;
    }

    public String getEncryptedFilePath() { return encryptedFilePath; }
    public byte[] getHash() { return hash; }
    public long getSize() { return size; }
}
