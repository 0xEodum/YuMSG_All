package com.yumsg.core.data;

public class UserSession {
    private String username;
    private String token;
    private String organizationId;
    private long timestamp;

    public UserSession(String username, String token, String organizationId, long timestamp) {
        this.username = username;
        this.token = token;
        this.organizationId = organizationId;
        this.timestamp = timestamp;
    }

    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getOrganizationId() { return organizationId; }
    public long getTimestamp() { return timestamp; }
}
