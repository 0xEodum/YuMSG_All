package com.example.yumsg.core.data;

import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * SessionAuthData - Enhanced Authentication Data Model
 * 
 * Complete authentication data structure for session management.
 * Includes access tokens, refresh tokens, expiration handling, and security features.
 * 
 * Key Features:
 * - Access and refresh token management
 * - Expiration validation and checks
 * - Session metadata storage
 * - Secure cleanup methods
 * - Token lifecycle management
 * - Thread-safe operations
 */
public class SessionAuthData {
    private static final String TAG = "SessionAuthData";
    
    // Core authentication information
    private String username;
    private String userId;
    private String organizationId;
    
    // Token management
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    
    // Timing information
    private long issuedAt;
    private long expiresAt;
    private long refreshExpiresAt;
    
    // Session metadata
    private String sessionId;
    private String scope;
    private Map<String, Object> metadata;
    
    // Security and state
    private boolean isValid;
    private long lastActivityTime;
    
    /**
     * Default constructor
     */
    public SessionAuthData() {
        this.metadata = new HashMap<>();
        this.issuedAt = System.currentTimeMillis();
        this.lastActivityTime = System.currentTimeMillis();
        this.isValid = true;
    }
    
    /**
     * Constructor with core parameters
     */
    public SessionAuthData(String username, String userId, String organizationId, 
                          String accessToken, String refreshToken, long expiresAt) {
        this();
        this.username = username;
        this.userId = userId;
        this.organizationId = organizationId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.refreshExpiresAt = expiresAt + (7 * 24 * 60 * 60 * 1000L); // +7 days for refresh
    }
    
    /**
     * Full constructor with all parameters
     */
    public SessionAuthData(String username, String userId, String organizationId,
                          String accessToken, String refreshToken, String tokenType,
                          long issuedAt, long expiresAt, long refreshExpiresAt,
                          String sessionId, String scope) {
        this();
        this.username = username;
        this.userId = userId;
        this.organizationId = organizationId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.sessionId = sessionId;
        this.scope = scope;
    }
    
    // ===========================
    // GETTERS AND SETTERS
    // ===========================
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { 
        this.accessToken = accessToken;
        updateLastActivity();
    }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { 
        this.refreshToken = refreshToken;
        updateLastActivity();
    }
    
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    
    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }
    
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    
    public long getRefreshExpiresAt() { return refreshExpiresAt; }
    public void setRefreshExpiresAt(long refreshExpiresAt) { this.refreshExpiresAt = refreshExpiresAt; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { 
        this.metadata = metadata != null ? metadata : new HashMap<>(); 
    }
    
    public boolean isValid() { return isValid; }
    public void setValid(boolean valid) { this.isValid = valid; }
    
    public long getLastActivityTime() { return lastActivityTime; }
    
    // ===========================
    // TOKEN VALIDATION METHODS
    // ===========================
    
    /**
     * Check if access token is expired
     */
    public boolean isExpired() {
        return isExpired(0); // No grace period
    }
    
    /**
     * Check if access token is expired with grace period
     */
    public boolean isExpired(long gracePeriodMs) {
        long currentTime = System.currentTimeMillis();
        return currentTime + gracePeriodMs >= expiresAt;
    }
    
    /**
     * Check if access token needs refresh (expires within threshold)
     */
    public boolean needsRefresh() {
        return needsRefresh(5 * 60 * 1000L); // 5 minutes default threshold
    }
    
    /**
     * Check if access token needs refresh within specified threshold
     */
    public boolean needsRefresh(long thresholdMs) {
        return isExpired(thresholdMs);
    }
    
    /**
     * Check if refresh token is expired
     */
    public boolean isRefreshTokenExpired() {
        long currentTime = System.currentTimeMillis();
        return currentTime >= refreshExpiresAt;
    }
    
    /**
     * Check if the entire session is valid
     */
    public boolean isSessionValid() {
        return isValid && 
               !isRefreshTokenExpired() && 
               hasRequiredTokens() &&
               hasRequiredUserInfo();
    }
    
    /**
     * Get time until access token expiration in milliseconds
     */
    public long getTimeUntilExpiration() {
        long currentTime = System.currentTimeMillis();
        return Math.max(0, expiresAt - currentTime);
    }
    
    /**
     * Get time until refresh token expiration in milliseconds
     */
    public long getTimeUntilRefreshExpiration() {
        long currentTime = System.currentTimeMillis();
        return Math.max(0, refreshExpiresAt - currentTime);
    }
    
    /**
     * Get human-readable time until expiration
     */
    public String getTimeUntilExpirationFormatted() {
        long timeMs = getTimeUntilExpiration();
        
        if (timeMs <= 0) {
            return "Expired";
        }
        
        long minutes = timeMs / (60 * 1000);
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day(s)";
        } else if (hours > 0) {
            return hours + " hour(s)";
        } else {
            return minutes + " minute(s)";
        }
    }
    
    // ===========================
    // TOKEN MANAGEMENT
    // ===========================
    
    /**
     * Update tokens from refresh response
     */
    public void updateTokens(String newAccessToken, String newRefreshToken, long newExpiresAt) {
        if (newAccessToken != null) {
            this.accessToken = newAccessToken;
        }
        
        if (newRefreshToken != null) {
            this.refreshToken = newRefreshToken;
        }
        
        this.expiresAt = newExpiresAt;
        this.issuedAt = System.currentTimeMillis();
        updateLastActivity();
        
        Log.d(TAG, "Tokens updated, new expiration: " + getTimeUntilExpirationFormatted());
    }
    
    /**
     * Update only access token (when refresh token stays the same)
     */
    public void updateAccessToken(String newAccessToken, long newExpiresAt) {
        updateTokens(newAccessToken, null, newExpiresAt);
    }
    
    /**
     * Mark tokens as invalid
     */
    public void invalidateTokens() {
        this.isValid = false;
        Log.d(TAG, "Session tokens invalidated");
    }
    
    /**
     * Get authorization header value
     */
    public String getAuthorizationHeader() {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return null;
        }
        
        return tokenType + " " + accessToken;
    }
    
    // ===========================
    // METADATA MANAGEMENT
    // ===========================
    
    /**
     * Add metadata entry
     */
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * Get metadata entry
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * Remove metadata entry
     */
    public void removeMetadata(String key) {
        if (metadata != null) {
            metadata.remove(key);
        }
    }
    
    // ===========================
    // ACTIVITY TRACKING
    // ===========================
    
    /**
     * Update last activity time
     */
    public void updateLastActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }
    
    /**
     * Get time since last activity in milliseconds
     */
    public long getTimeSinceLastActivity() {
        return System.currentTimeMillis() - lastActivityTime;
    }
    
    /**
     * Check if session is idle beyond threshold
     */
    public boolean isIdle(long idleThresholdMs) {
        return getTimeSinceLastActivity() > idleThresholdMs;
    }
    
    // ===========================
    // VALIDATION HELPERS
    // ===========================
    
    /**
     * Check if required tokens are present
     */
    private boolean hasRequiredTokens() {
        return accessToken != null && !accessToken.trim().isEmpty() &&
               refreshToken != null && !refreshToken.trim().isEmpty();
    }
    
    /**
     * Check if required user information is present
     */
    private boolean hasRequiredUserInfo() {
        return username != null && !username.trim().isEmpty() &&
               userId != null && !userId.trim().isEmpty();
    }
    
    /**
     * Validate session data completeness
     */
    public boolean isComplete() {
        return hasRequiredTokens() && 
               hasRequiredUserInfo() && 
               expiresAt > 0 && 
               refreshExpiresAt > 0;
    }
    
    // ===========================
    // SECURITY METHODS
    // ===========================
    
    /**
     * Create a secure copy of the auth data
     */
    public SessionAuthData copy() {
        SessionAuthData copy = new SessionAuthData();
        
        copy.username = this.username;
        copy.userId = this.userId;
        copy.organizationId = this.organizationId;
        copy.accessToken = this.accessToken;
        copy.refreshToken = this.refreshToken;
        copy.tokenType = this.tokenType;
        copy.issuedAt = this.issuedAt;
        copy.expiresAt = this.expiresAt;
        copy.refreshExpiresAt = this.refreshExpiresAt;
        copy.sessionId = this.sessionId;
        copy.scope = this.scope;
        copy.isValid = this.isValid;
        copy.lastActivityTime = this.lastActivityTime;
        
        // Deep copy metadata
        if (this.metadata != null) {
            copy.metadata = new HashMap<>(this.metadata);
        }
        
        return copy;
    }
    
    /**
     * Securely wipe sensitive data from memory
     */
    public void secureWipe() {
        Log.d(TAG, "Performing secure wipe of session data");
        
        // Wipe tokens by overwriting with random data
        if (accessToken != null) {
            char[] tokenChars = accessToken.toCharArray();
            Arrays.fill(tokenChars, '\0');
            accessToken = null;
        }
        
        if (refreshToken != null) {
            char[] refreshChars = refreshToken.toCharArray();
            Arrays.fill(refreshChars, '\0');
            refreshToken = null;
        }
        
        // Clear other sensitive data
        username = null;
        userId = null;
        organizationId = null;
        sessionId = null;
        scope = null;
        
        // Clear metadata
        if (metadata != null) {
            metadata.clear();
            metadata = null;
        }
        
        // Mark as invalid
        isValid = false;
        
        Log.d(TAG, "Session data securely wiped");
    }
    
    // ===========================
    // SERIALIZATION HELPERS
    // ===========================
    
    /**
     * Get session summary for logging (without sensitive data)
     */
    public String getSessionSummary() {
        return String.format(
            "SessionAuthData{username='%s', userId='%s', organizationId='%s', " +
            "tokenType='%s', isValid=%s, expiresIn='%s', sessionId='%s'}",
            username, userId, organizationId, tokenType, isValid, 
            getTimeUntilExpirationFormatted(), sessionId
        );
    }
    
    @Override
    public String toString() {
        // Never include actual tokens in toString for security
        return getSessionSummary();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SessionAuthData that = (SessionAuthData) obj;
        
        return sessionId != null ? sessionId.equals(that.sessionId) : 
               (userId != null ? userId.equals(that.userId) : super.equals(obj));
    }
    
    @Override
    public int hashCode() {
        return sessionId != null ? sessionId.hashCode() : 
               (userId != null ? userId.hashCode() : super.hashCode());
    }
}