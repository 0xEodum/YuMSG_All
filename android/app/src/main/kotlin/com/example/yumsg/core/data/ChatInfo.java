package com.example.yumsg.core.data;

import java.util.ArrayList;
import java.util.List;

import com.example.yumsg.core.enums.EncryptionStatus;

public class ChatInfo {
    private String id;
    private String name;
    private List<String> participants;
    private EncryptionStatus encryptionStatus;
    private long createdAt;
    private long lastActivity;

    public ChatInfo(String id, String name) {
        this.id = id;
        this.name = name;
        this.participants = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getParticipants() { return participants; }
    public EncryptionStatus getEncryptionStatus() { return encryptionStatus; }
    public void setEncryptionStatus(EncryptionStatus status) { this.encryptionStatus = status; }
    public long getCreatedAt() { return createdAt; }
    public long getLastActivity() { return lastActivity; }
    public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }
}
