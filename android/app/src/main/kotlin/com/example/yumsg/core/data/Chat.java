package com.yumsg.core.data;

public class Chat {
    private String id;
    private String name;
    private ChatKeys keys;
    private long lastActivity;
    private long createdAt;
    private long updatedAt;
    private PeerCryptoInfo peerCryptoInfo;

    // New fields for key establishment tracking
    private String fingerprint;
    private String keyEstablishmentStatus = "INITIALIZING";
    private long keyEstablishmentCompletedAt;

    public Chat() {}

    public Chat(String id, String name) {
        this.id = id;
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.keyEstablishmentStatus = "INITIALIZING";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ChatKeys getKeys() { return keys; }
    public void setKeys(ChatKeys keys) { this.keys = keys; }

    public long getLastActivity() { return lastActivity; }
    public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public PeerCryptoInfo getPeerCryptoInfo() { return peerCryptoInfo; }
    public void setPeerCryptoInfo(PeerCryptoInfo peerCryptoInfo) { this.peerCryptoInfo = peerCryptoInfo; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public String getKeyEstablishmentStatus() { return keyEstablishmentStatus; }
    public void setKeyEstablishmentStatus(String status) { this.keyEstablishmentStatus = status; }

    public long getKeyEstablishmentCompletedAt() { return keyEstablishmentCompletedAt; }
    public void setKeyEstablishmentCompletedAt(long completedAt) { this.keyEstablishmentCompletedAt = completedAt; }

    // Helper status checks
    public boolean isKeyEstablishmentComplete() {
        return "ESTABLISHED".equals(keyEstablishmentStatus) &&
               keys != null &&
               keys.isComplete();
    }

    public boolean isKeyEstablishmentFailed() {
        return "FAILED".equals(keyEstablishmentStatus);
    }

    public boolean isKeyEstablishmentInProgress() {
        return "INITIALIZING".equals(keyEstablishmentStatus);
    }

    public boolean isReadyForMessaging() {
        return isKeyEstablishmentComplete() &&
               keys != null &&
               keys.getSymmetricKey() != null;
    }

    @Override
    public String toString() {
        return "Chat{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", keyEstablishmentStatus='" + keyEstablishmentStatus + '\'' +
                ", fingerprint='" + (fingerprint != null ? fingerprint : "null") + '\'' +
                ", lastActivity=" + lastActivity +
                '}';
    }
}
