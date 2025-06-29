package com.example.yumsg.core.data;

import java.util.HashMap;
import java.util.Map;

public class AuthData {
    private String username;
    private String token;
    private String organizationId;
    private String userId;
    private long expiresAt;
    private Map<String, Object> metadata;

    public AuthData(String username, String token, String organizationId, String userId, long expiresAt) {
        this.username = username;
        this.token = token;
        this.organizationId = organizationId;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.metadata = new HashMap<>();
    }

    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getOrganizationId() { return organizationId; }
    public String getUserId() { return userId; }
    public long getExpiresAt() { return expiresAt; }
    public Map<String, Object> getMetadata() { return metadata; }
}
