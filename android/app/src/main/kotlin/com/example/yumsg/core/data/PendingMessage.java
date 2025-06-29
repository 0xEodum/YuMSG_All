package com.yumsg.core.data;

import java.util.Map;

/**
 * PendingMessage - Data class for offline messages from server
 * 
 * Represents a message that was sent to the user while they were offline
 * and is now being delivered when they come back online.
 */
public class PendingMessage {
    private String id;
    private String fromUserId;
    private String fromUserName;
    private String messageType;
    private Map<String, Object> messageData;
    private String receivedAt;
    private String expiresAt;
    
    /**
     * Default constructor
     */
    public PendingMessage() {}
    
    /**
     * Constructor with parameters
     */
    public PendingMessage(String id, String fromUserId, String fromUserName, String messageType,
                         Map<String, Object> messageData, String receivedAt, String expiresAt) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.fromUserName = fromUserName;
        this.messageType = messageType;
        this.messageData = messageData;
        this.receivedAt = receivedAt;
        this.expiresAt = expiresAt;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
    
    public String getFromUserName() { return fromUserName; }
    public void setFromUserName(String fromUserName) { this.fromUserName = fromUserName; }
    
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    
    public Map<String, Object> getMessageData() { return messageData; }
    public void setMessageData(Map<String, Object> messageData) { this.messageData = messageData; }
    
    public String getReceivedAt() { return receivedAt; }
    public void setReceivedAt(String receivedAt) { this.receivedAt = receivedAt; }
    
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    
    /**
     * Check if message is expired
     */
    public boolean isExpired() {
        try {
            long expiresAtTime = java.time.Instant.parse(expiresAt).toEpochMilli();
            return System.currentTimeMillis() > expiresAtTime;
        } catch (Exception e) {
            return false; // If we can't parse, assume not expired
        }
    }
    
    /**
     * Get message age in milliseconds
     */
    public long getMessageAge() {
        try {
            long receivedAtTime = java.time.Instant.parse(receivedAt).toEpochMilli();
            return System.currentTimeMillis() - receivedAtTime;
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    public String toString() {
        return "PendingMessage{" +
                "id='" + id + '\'' +
                ", fromUserId='" + fromUserId + '\'' +
                ", fromUserName='" + fromUserName + '\'' +
                ", messageType='" + messageType + '\'' +
                ", receivedAt='" + receivedAt + '\'' +
                ", expiresAt='" + expiresAt + '\'' +
                '}';
    }
}