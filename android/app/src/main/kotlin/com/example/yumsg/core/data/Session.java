package com.yumsg.core.data;

public class Session {
    private String id;
    private String userId;
    private String token;
    private long createdAt;
    private long expiresAt;
    private boolean isValid;

    public Session(String id, String userId, String token, long expiresAt) {
        this.id = id;
        this.userId = userId;
        this.token = token;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = expiresAt;
        this.isValid = true;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getToken() { return token; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isValid() { return isValid && System.currentTimeMillis() < expiresAt; }
    public void invalidate() { this.isValid = false; }
}
