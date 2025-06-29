package com.example.yumsg.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.example.yumsg.core.data.*;
import com.example.yumsg.core.enums.*;
import com.example.yumsg.core.network.NetworkManager;
import com.example.yumsg.core.network.ServerNetworkManager;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SessionManager - Complete Implementation with AndroidKeystore
 * 
 * Manages user sessions with hardware-backed security using AndroidKeystore.
 * Provides secure token storage, session validation, and lifecycle management.
 * 
 * Key Features:
 * - AndroidKeystore integration for secure token storage
 * - Hardware-backed encryption of sensitive session data
 * - Thread-safe session management
 * - Session validation and expiration handling
 * - Automatic session cleanup and security
 * - Mode switching support
 * - Token refresh coordination via NetworkManager
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    
    // AndroidKeystore configuration
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "YuMSG_SessionKey";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    
    // SharedPreferences for encrypted session storage
    private static final String SESSION_PREFS = "yumsg_session_prefs";
    private static final String KEY_ENCRYPTED_SESSION = "encrypted_session_data";
    private static final String KEY_SESSION_IV = "session_iv";
    private static final String KEY_SESSION_EXISTS = "session_exists";
    
    // Session validation constants
    private static final long DEFAULT_SESSION_TIMEOUT = 24 * 60 * 60 * 1000L; // 24 hours
    private static final long REFRESH_THRESHOLD = 5 * 60 * 1000L; // 5 minutes
    
    // Singleton instance
    private static volatile SessionManager instance;
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Dependencies
    private final Context context;
    private final SharedPreferences sessionPrefs;
    private final Gson gson;
    // Network manager reference for token refresh
    private volatile NetworkManager networkManager;
    
    // State
    private volatile boolean isInitialized = false;
    private volatile SessionAuthData currentSession;
    private KeyStore keyStore;
    private SecretKey sessionKey;
    
    /**
     * Private constructor for singleton pattern
     */
    private SessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionPrefs = this.context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE);
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();
        
        Log.d(TAG, "SessionManager instance created");
    }
    
    /**
     * Get singleton instance
     */
    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Get singleton instance (requires previous initialization)
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SessionManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }
    
    // ===========================
    // LIFECYCLE METHODS
    // ===========================
    
    /**
     * Initialize session manager
     */
    public void initialize() {
        lock.writeLock().lock();
        try {
            if (isInitialized) {
                Log.w(TAG, "SessionManager already initialized");
                return;
            }
            
            Log.d(TAG, "Initializing SessionManager");
            
            // Initialize AndroidKeystore
            if (!initializeKeystore()) {
                Log.e(TAG, "Failed to initialize AndroidKeystore");
                return;
            }
            
            // Load existing session if available
            loadSessionFromStorage();
            
            isInitialized = true;
            Log.i(TAG, "SessionManager initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SessionManager", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Set network manager for token refresh operations
     */
    public void setNetworkManager(NetworkManager networkManager) {
        lock.writeLock().lock();
        try {
            this.networkManager = networkManager;
            Log.d(TAG, "NetworkManager set for session refresh operations");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ===========================
    // SESSION MANAGEMENT
    // ===========================
    
    /**
     * Create new session from authentication data
     */
    public UserSession createSession(SessionAuthData authData) {
        checkInitialized();
        
        if (authData == null) {
            throw new IllegalArgumentException("AuthData cannot be null");
        }
        
        if (!authData.isComplete()) {
            throw new IllegalArgumentException("AuthData is incomplete");
        }
        
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Creating new session for user: " + authData.getUsername());
            
            // Clear any existing session
            clearCurrentSession();
            
            // Store the session data
            this.currentSession = authData.copy();
            
            // Save to secure storage
            saveSessionToStorage();
            
            // Create UserSession for compatibility with existing code
            UserSession userSession = new UserSession(
                authData.getUsername(),
                authData.getAccessToken(),
                authData.getOrganizationId(),
                authData.getIssuedAt()
            );
            
            Log.i(TAG, "Session created successfully for: " + authData.getUsername());
            return userSession;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create session", e);
            throw new RuntimeException("Failed to create session", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get current session
     */
    public UserSession getCurrentSession() {
        lock.readLock().lock();
        try {
            if (currentSession == null) {
                return null;
            }
            
            // Return UserSession for compatibility
            return new UserSession(
                currentSession.getUsername(),
                currentSession.getAccessToken(),
                currentSession.getOrganizationId(),
                currentSession.getIssuedAt()
            );
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Update existing session
     */
    public boolean updateSession(UserSession session) {
        checkInitialized();
        
        if (session == null) {
            Log.w(TAG, "Cannot update with null session");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            if (currentSession == null) {
                Log.w(TAG, "No current session to update");
                return false;
            }
            
            Log.d(TAG, "Updating session for: " + session.getUsername());
            
            // Update current session with new data
            currentSession.setUsername(session.getUsername());
            currentSession.setAccessToken(session.getToken());
            currentSession.setOrganizationId(session.getOrganizationId());
            currentSession.updateLastActivity();
            
            // Save updated session
            saveSessionToStorage();
            
            Log.d(TAG, "Session updated successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update session", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Destroy current session
     */
    public void destroySession() {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Destroying current session");
            
            clearCurrentSession();
            clearSessionFromStorage();
            
            Log.i(TAG, "Session destroyed successfully");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // SESSION VALIDATION
    // ===========================
    
    /**
     * Check if current session is valid
     */
    public boolean isSessionValid() {
        lock.readLock().lock();
        try {
            if (currentSession == null) {
                return false;
            }
            
            boolean valid = currentSession.isSessionValid();
            
            if (!valid) {
                Log.d(TAG, "Session validation failed - session will be cleared");
                // Clear invalid session in background
                CompletableFuture.runAsync(this::destroySession);
            }
            
            return valid;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Refresh session token using NetworkManager if available
     */
    public boolean refreshSession() {
        checkInitialized();

        lock.writeLock().lock();
        try {
            if (currentSession == null) {
                Log.w(TAG, "No session to refresh");
                return false;
            }

            if (currentSession.isRefreshTokenExpired()) {
                Log.w(TAG, "Refresh token expired - cannot refresh session");
                destroySession();
                return false;
            }

            Log.d(TAG, "Session refresh requested for: " + currentSession.getUsername());

            if (networkManager instanceof ServerNetworkManager) {
                ServerNetworkManager serverManager = (ServerNetworkManager) networkManager;
                try {
                    CompletableFuture<SessionAuthData> refreshFuture =
                        serverManager.refreshSessionToken(currentSession.getRefreshToken());

                    SessionAuthData refreshedSession = refreshFuture.get(10, TimeUnit.SECONDS);

                    if (refreshedSession != null) {
                        currentSession.updateTokens(
                            refreshedSession.getAccessToken(),
                            refreshedSession.getRefreshToken(),
                            refreshedSession.getExpiresAt());
                        saveSessionToStorage();

                        Log.i(TAG, "Session refreshed successfully");
                        return true;
                    } else {
                        Log.w(TAG, "Session refresh failed - received null response");
                        return false;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Session refresh failed", e);
                    return false;
                }
            } else {
                Log.w(TAG, "No ServerNetworkManager available for session refresh");

                if (currentSession.needsRefresh()) {
                    long newExpiresAt = System.currentTimeMillis() + DEFAULT_SESSION_TIMEOUT;
                    currentSession.updateAccessToken(currentSession.getAccessToken(), newExpiresAt);
                    saveSessionToStorage();

                    Log.d(TAG, "Session extended locally (fallback mode)");
                    return true;
                }
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if session refresh is available
     */
    public boolean canRefreshSession() {
        lock.readLock().lock();
        try {
            return currentSession != null &&
                   !currentSession.isRefreshTokenExpired() &&
                   networkManager instanceof ServerNetworkManager;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get current access token for network requests
     */
    public String getCurrentAccessToken() {
        lock.readLock().lock();
        try {
            return currentSession != null ? currentSession.getAccessToken() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if access token needs refresh
     */
    public boolean needsTokenRefresh() {
        lock.readLock().lock();
        try {
            return currentSession != null && currentSession.needsRefresh();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Handle token expiration notification from network layer
     */
    public void onTokenExpired() {
        Log.w(TAG, "Token expired notification received");

        if (canRefreshSession()) {
            Log.d(TAG, "Attempting automatic token refresh");
            if (!refreshSession()) {
                Log.w(TAG, "Automatic token refresh failed, session will be destroyed");
                destroySession();
            }
        } else {
            Log.w(TAG, "Cannot refresh token, destroying session");
            destroySession();
        }
    }

    // ===========================
    // USER CREDENTIALS (NOT STORED)
    // ===========================
    
    /**
     * Save user credentials (NOT IMPLEMENTED - security policy)
     */
    public boolean saveUserCredentials(UserCredentials credentials) {
        Log.w(TAG, "User credentials are not stored for security reasons");
        // This method intentionally does nothing - we never store passwords
        return false;
    }
    
    /**
     * Get user credentials (NOT IMPLEMENTED - security policy) 
     */
    public UserCredentials getUserCredentials() {
        Log.w(TAG, "User credentials are not stored - returning null");
        // This method intentionally returns null - we never store passwords
        return null;
    }
    
    /**
     * Clear user credentials (NOT APPLICABLE)
     */
    public void clearUserCredentials() {
        Log.d(TAG, "No user credentials to clear - they are never stored");
        // This method intentionally does nothing - we never store passwords
    }
    
    // ===========================
    // USER PROFILE MANAGEMENT
    // ===========================
    
    /**
     * Set user profile in session metadata
     */
    public boolean setUserProfile(UserProfile profile) {
        lock.writeLock().lock();
        try {
            if (currentSession == null) {
                Log.w(TAG, "No active session to set profile");
                return false;
            }
            
            if (profile != null) {
                currentSession.addMetadata("userProfile", profile);
                saveSessionToStorage();
                Log.d(TAG, "User profile set in session");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting user profile", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get user profile from session metadata
     */
    public UserProfile getUserProfile() {
        lock.readLock().lock();
        try {
            if (currentSession == null) {
                return null;
            }
            
            Object profileObj = currentSession.getMetadata("userProfile");
            if (profileObj instanceof UserProfile) {
                return (UserProfile) profileObj;
            }
            
            return null;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Update user profile in session
     */
    public boolean updateUserProfile(UserProfile profile) {
        return setUserProfile(profile);
    }
    
    // ===========================
    // LOCAL MODE SUPPORT
    // ===========================
    
    /**
     * Set local username for P2P mode
     */
    public boolean setLocalUsername(String username) {
        checkInitialized();
        
        if (username == null || username.trim().isEmpty()) {
            Log.w(TAG, "Invalid local username");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Setting local username: " + username);
            
            // Create minimal session data for local mode
            SessionAuthData localSession = new SessionAuthData();
            localSession.setUsername(username.trim());
            localSession.setUserId(username.trim()); // Use username as userId in local mode
            localSession.setTokenType("Local");
            localSession.setAccessToken("local_token_" + System.currentTimeMillis());
            localSession.setExpiresAt(Long.MAX_VALUE); // Never expires in local mode
            localSession.setRefreshExpiresAt(Long.MAX_VALUE);
            localSession.addMetadata("mode", "LOCAL");
            
            this.currentSession = localSession;
            saveSessionToStorage();
            
            Log.i(TAG, "Local session created for: " + username);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting local username", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get local username
     */
    public String getLocalUsername() {
        lock.readLock().lock();
        try {
            if (currentSession == null) {
                return null;
            }
            
            Object mode = currentSession.getMetadata("mode");
            if ("LOCAL".equals(mode)) {
                return currentSession.getUsername();
            }
            
            return null;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // SETUP STATE MANAGEMENT
    // ===========================
    
    /**
     * Check if first setup is completed
     */
    public boolean isFirstSetupCompleted() {
        // Delegate to a simple SharedPreferences check
        return context.getSharedPreferences("app_setup", Context.MODE_PRIVATE)
                     .getBoolean("first_setup_completed", false);
    }
    
    /**
     * Mark first setup as completed
     */
    public void markFirstSetupCompleted() {
        context.getSharedPreferences("app_setup", Context.MODE_PRIVATE)
               .edit()
               .putBoolean("first_setup_completed", true)
               .apply();
        
        Log.d(TAG, "First setup marked as completed");
    }
    
    // ===========================
    // SESSION INFO GETTERS
    // ===========================
    
    /**
     * Get login timestamp
     */
    public long getLoginTimestamp() {
        lock.readLock().lock();
        try {
            return currentSession != null ? currentSession.getIssuedAt() : 0L;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set login timestamp
     */
    public void setLoginTimestamp(long timestamp) {
        lock.writeLock().lock();
        try {
            if (currentSession != null) {
                currentSession.setIssuedAt(timestamp);
                saveSessionToStorage();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting login timestamp", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // AUTHORIZATION CHECKS
    // ===========================
    
    /**
     * Check if user is authorized
     */
    public boolean isUserAuthorized() {
        return isSessionValid();
    }
    
    /**
     * Get user ID from current session
     */
    public String getUserId() {
        lock.readLock().lock();
        try {
            return currentSession != null ? currentSession.getUserId() : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get organization ID from current session
     */
    public String getOrganizationId() {
        lock.readLock().lock();
        try {
            return currentSession != null ? currentSession.getOrganizationId() : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // LOGOUT AND MODE SWITCHING
    // ===========================
    
    /**
     * Logout current user
     */
    public void logout() {
        Log.i(TAG, "User logout requested");
        destroySession();
    }
    
    /**
     * Switch app mode (clears current session)
     */
    public boolean switchMode(AppMode mode) {
        checkInitialized();
        
        Log.i(TAG, "Switching to mode: " + mode);
        
        // Clear current session when switching modes
        destroySession();
        
        Log.d(TAG, "Mode switch completed, session cleared");
        return true;
    }
    
    // ===========================
    // SESSION PERSISTENCE
    // ===========================
    
    /**
     * Save user session (compatibility method)
     */
    public void saveUserSession(String username, String token, String orgId, long expiresAt) {
        checkInitialized();
        
        if (username == null || token == null) {
            Log.w(TAG, "Cannot save session with null username or token");
            return;
        }
        
        // Create SessionAuthData from parameters
        SessionAuthData authData = new SessionAuthData();
        authData.setUsername(username);
        authData.setUserId(username); // Use username as userId if not provided
        authData.setOrganizationId(orgId);
        authData.setAccessToken(token);
        authData.setRefreshToken(token); // Use same token for refresh if not provided
        authData.setExpiresAt(expiresAt);
        authData.setRefreshExpiresAt(expiresAt + (7 * 24 * 60 * 60 * 1000L)); // +7 days
        
        createSession(authData);
    }
    
    // ===========================
    // ANDROID KEYSTORE METHODS
    // ===========================
    
    /**
     * Initialize AndroidKeystore
     */
    private boolean initializeKeystore() {
        try {
            Log.d(TAG, "Initializing AndroidKeystore");
            
            // Load AndroidKeystore
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            
            // Check if key already exists
            if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.d(TAG, "Loading existing session key from keystore");
                sessionKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            } else {
                Log.d(TAG, "Generating new session key in keystore");
                generateSessionKey();
            }
            
            Log.i(TAG, "AndroidKeystore initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AndroidKeystore", e);
            return false;
        }
    }
    
    /**
     * Generate new session key in AndroidKeystore
     */
    private void generateSessionKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);
        
        // Add user authentication requirement if supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setUserAuthenticationRequired(false); // Set to true for biometric protection
        }
        
        keyGenerator.init(builder.build());
        sessionKey = keyGenerator.generateKey();
        
        Log.d(TAG, "Session key generated in hardware keystore");
    }
    
    /**
     * Encrypt data using AndroidKeystore key
     */
    private EncryptedData encryptData(String data) throws Exception {
        if (sessionKey == null) {
            throw new IllegalStateException("Session key not initialized");
        }
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey);
        
        byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
        byte[] iv = cipher.getIV();
        
        return new EncryptedData(
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP)
        );
    }
    
    /**
     * Decrypt data using AndroidKeystore key
     */
    private String decryptData(EncryptedData encryptedData) throws Exception {
        if (sessionKey == null) {
            throw new IllegalStateException("Session key not initialized");
        }
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        IvParameterSpec ivSpec = new IvParameterSpec(Base64.decode(encryptedData.iv, Base64.NO_WRAP));
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, ivSpec);
        
        byte[] decryptedBytes = cipher.doFinal(Base64.decode(encryptedData.encryptedData, Base64.NO_WRAP));
        return new String(decryptedBytes, "UTF-8");
    }
    
    // ===========================
    // SESSION STORAGE METHODS
    // ===========================
    
    /**
     * Save session to encrypted storage
     */
    private void saveSessionToStorage() {
        try {
            if (currentSession == null) {
                Log.w(TAG, "No session to save");
                return;
            }
            
            Log.d(TAG, "Saving session to secure storage");
            
            // Serialize session to JSON
            String sessionJson = gson.toJson(currentSession);
            
            // Encrypt session data
            EncryptedData encryptedSession = encryptData(sessionJson);
            
            // Save encrypted data to SharedPreferences
            sessionPrefs.edit()
                .putString(KEY_ENCRYPTED_SESSION, encryptedSession.encryptedData)
                .putString(KEY_SESSION_IV, encryptedSession.iv)
                .putBoolean(KEY_SESSION_EXISTS, true)
                .apply();
            
            Log.d(TAG, "Session saved to secure storage successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save session to storage", e);
        }
    }
    
    /**
     * Load session from encrypted storage
     */
    private void loadSessionFromStorage() {
        try {
            boolean sessionExists = sessionPrefs.getBoolean(KEY_SESSION_EXISTS, false);
            if (!sessionExists) {
                Log.d(TAG, "No saved session found");
                return;
            }
            
            Log.d(TAG, "Loading session from secure storage");
            
            String encryptedData = sessionPrefs.getString(KEY_ENCRYPTED_SESSION, null);
            String iv = sessionPrefs.getString(KEY_SESSION_IV, null);
            
            if (encryptedData == null || iv == null) {
                Log.w(TAG, "Incomplete session data in storage");
                clearSessionFromStorage();
                return;
            }
            
            // Decrypt session data
            EncryptedData encrypted = new EncryptedData(encryptedData, iv);
            String sessionJson = decryptData(encrypted);
            
            // Deserialize session from JSON
            SessionAuthData loadedSession = gson.fromJson(sessionJson, SessionAuthData.class);
            
            if (loadedSession != null && loadedSession.isSessionValid()) {
                this.currentSession = loadedSession;
                Log.i(TAG, "Session loaded successfully: " + loadedSession.getUsername());
            } else {
                Log.w(TAG, "Loaded session is invalid, clearing storage");
                clearSessionFromStorage();
            }
            
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse saved session JSON", e);
            clearSessionFromStorage();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load session from storage", e);
            clearSessionFromStorage();
        }
    }
    
    /**
     * Clear session from storage
     */
    private void clearSessionFromStorage() {
        sessionPrefs.edit()
            .remove(KEY_ENCRYPTED_SESSION)
            .remove(KEY_SESSION_IV)
            .remove(KEY_SESSION_EXISTS)
            .apply();
        
        Log.d(TAG, "Session cleared from storage");
    }
    
    /**
     * Clear current session from memory
     */
    private void clearCurrentSession() {
        if (currentSession != null) {
            currentSession.secureWipe();
            currentSession = null;
        }
    }
    
    // ===========================
    // UTILITY METHODS
    // ===========================
    
    /**
     * Check if session manager is initialized
     */
    private void checkInitialized() {
        if (!isInitialized) {
            throw new IllegalStateException("SessionManager not initialized");
        }
    }
    
    /**
     * Get session summary for debugging
     */
    public String getSessionSummary() {
        lock.readLock().lock();
        try {
            if (currentSession == null) {
                return "No active session";
            }
            
            return currentSession.getSessionSummary();
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // INNER CLASSES
    // ===========================
    
    /**
     * Container for encrypted data with IV
     */
    private static class EncryptedData {
        final String encryptedData;
        final String iv;
        
        EncryptedData(String encryptedData, String iv) {
            this.encryptedData = encryptedData;
            this.iv = iv;
        }
    }
}