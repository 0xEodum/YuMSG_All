package com.yumsg.core.data;

public class CryptoStatistics {
    private long keyGenerationsCount;
    private long encryptionsCount;
    private long decryptionsCount;
    private long signaturesCount;
    private long verificationsCount;
    private long kemOperationsCount;
    private long totalOperationTime;
    private long createdAt;

    public CryptoStatistics() { this.createdAt = System.currentTimeMillis(); }

    public long getKeyGenerationsCount() { return keyGenerationsCount; }
    public long getEncryptionsCount() { return encryptionsCount; }
    public long getDecryptionsCount() { return decryptionsCount; }
    public long getSignaturesCount() { return signaturesCount; }
    public long getVerificationsCount() { return verificationsCount; }
    public long getKemOperationsCount() { return kemOperationsCount; }
    public long getTotalOperationTime() { return totalOperationTime; }
    public long getCreatedAt() { return createdAt; }
    public void reset() { /* reset all counters */ }
    public CryptoStatistics copy() { /* create copy */ return new CryptoStatistics(); }
}
