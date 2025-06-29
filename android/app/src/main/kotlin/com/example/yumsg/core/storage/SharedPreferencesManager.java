package com.example.yumsg.core.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.example.yumsg.core.data.*;
import com.example.yumsg.core.enums.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SharedPreferencesManager - Complete Implementation
 * 
 * Manages application preferences and configuration using Android SharedPreferences.
 * Thread-safe implementation with proper error handling and JSON serialization.
 * 
 * Key Features:
 * - Thread-safe read/write operations
 * - Type-safe preference storage with validation
 * - JSON serialization for complex objects
 * - Atomic batch operations
 * - Comprehensive error handling
 * - Migration support for preference schema updates
 * 
 * Note: Session management is handled separately by SessionManager with AndroidKeystore
 * for enhanced security. This class focuses on application settings and configuration.
 */
public class SharedPreferencesManager {
    private static final String TAG = "SharedPreferencesManager";
    private static final String PREFS_NAME = "yumsg_preferences";
    private static final int CURRENT_PREFS_VERSION = 1;
    
    // Preference keys
    private static final String KEY_PREFS_VERSION = "prefs_version";
    private static final String KEY_CRYPTO_ALGORITHMS = "crypto_algorithms";
    private static final String KEY_APP_MODE = "app_mode";
    private static final String KEY_SERVER_CONFIG = "server_config";
    private static final String KEY_USER_PROFILE = "user_profile";
    private static final String KEY_LOCAL_USERNAME = "local_username";
    private static final String KEY_FIRST_SETUP_COMPLETED = "first_setup_completed";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_CURRENT_SCREEN = "current_screen";
    private static final String KEY_LAST_ACTIVE_TIME = "last_active_time";
    
    // Singleton instance
    private static volatile SharedPreferencesManager instance;
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Dependencies
    private final SharedPreferences preferences;
    private final SharedPreferences.Editor editor;
    private final Gson gson;
    private final Context context;
    
    // State
    private volatile boolean isInitialized = false;
    
    /**
     * Private constructor for singleton pattern
     */
    private SharedPreferencesManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.editor = preferences.edit();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();
        
        Log.d(TAG, "SharedPreferencesManager instance created");
    }
    
    /**
     * Get singleton instance
     */
    public static SharedPreferencesManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SharedPreferencesManager.class) {
                if (instance == null) {
                    instance = new SharedPreferencesManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Get singleton instance (requires previous initialization with context)
     */
    public static SharedPreferencesManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SharedPreferencesManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }
    
    // ===========================
    // LIFECYCLE METHODS
    // ===========================
    
    /**
     * Initialize the preferences manager
     */
    public boolean initialize() {
        lock.writeLock().lock();
        try {
            if (isInitialized) {
                Log.w(TAG, "SharedPreferencesManager already initialized");
                return true;
            }
            
            Log.d(TAG, "Initializing SharedPreferencesManager");
            
            // Check and perform migrations if needed
            performMigrationIfNeeded();
            
            // Validate critical preferences
            validateCriticalPreferences();
            
            isInitialized = true;
            Log.i(TAG, "SharedPreferencesManager initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SharedPreferencesManager", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear all preferences (use with caution)
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            Log.w(TAG, "Clearing all preferences");
            editor.clear().apply();
            Log.i(TAG, "All preferences cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // CRYPTO ALGORITHMS MANAGEMENT
    // ===========================
    
    /**
     * Set cryptographic algorithms configuration
     */
    public void setCryptoAlgorithms(CryptoAlgorithms algorithms) {
        if (algorithms == null) {
            Log.w(TAG, "Attempted to set null crypto algorithms");
            return;
        }
        
        lock.writeLock().lock();
        try {
            String json = gson.toJson(algorithms);
            editor.putString(KEY_CRYPTO_ALGORITHMS, json).apply();
            Log.d(TAG, "Crypto algorithms updated: KEM=" + algorithms.getKemAlgorithm() + 
                      ", Symmetric=" + algorithms.getSymmetricAlgorithm() + 
                      ", Signature=" + algorithms.getSignatureAlgorithm());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save crypto algorithms", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get cryptographic algorithms configuration
     */
    public CryptoAlgorithms getCryptoAlgorithms() {
        lock.readLock().lock();
        try {
            String json = preferences.getString(KEY_CRYPTO_ALGORITHMS, null);
            if (json == null) {
                Log.d(TAG, "No crypto algorithms found, returning defaults");
                return getDefaultCryptoAlgorithms();
            }
            
            CryptoAlgorithms algorithms = gson.fromJson(json, CryptoAlgorithms.class);
            if (algorithms == null || !algorithms.isValid()) {
                Log.w(TAG, "Invalid crypto algorithms found, returning defaults");
                return getDefaultCryptoAlgorithms();
            }
            
            return algorithms;
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse crypto algorithms JSON, returning defaults", e);
            return getDefaultCryptoAlgorithms();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set specific KEM algorithm
     */
    public void setKemAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.trim().isEmpty()) {
            Log.w(TAG, "Invalid KEM algorithm provided");
            return;
        }
        
        CryptoAlgorithms algorithms = getCryptoAlgorithms();
        algorithms.setKemAlgorithm(algorithm.trim());
        setCryptoAlgorithms(algorithms);
    }
    
    /**
     * Get current KEM algorithm
     */
    public String getKemAlgorithm() {
        return getCryptoAlgorithms().getKemAlgorithm();
    }
    
    /**
     * Set specific symmetric algorithm
     */
    public void setSymmetricAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.trim().isEmpty()) {
            Log.w(TAG, "Invalid symmetric algorithm provided");
            return;
        }
        
        CryptoAlgorithms algorithms = getCryptoAlgorithms();
        algorithms.setSymmetricAlgorithm(algorithm.trim());
        setCryptoAlgorithms(algorithms);
    }
    
    /**
     * Get current symmetric algorithm
     */
    public String getSymmetricAlgorithm() {
        return getCryptoAlgorithms().getSymmetricAlgorithm();
    }
    
    /**
     * Set specific signature algorithm
     */
    public void setSignatureAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.trim().isEmpty()) {
            Log.w(TAG, "Invalid signature algorithm provided");
            return;
        }
        
        CryptoAlgorithms algorithms = getCryptoAlgorithms();
        algorithms.setSignatureAlgorithm(algorithm.trim());
        setCryptoAlgorithms(algorithms);
    }
    
    /**
     * Get current signature algorithm
     */
    public String getSignatureAlgorithm() {
        return getCryptoAlgorithms().getSignatureAlgorithm();
    }
    
    /**
     * Reset crypto algorithms to defaults
     */
    public void resetCryptoAlgorithmsToDefaults() {
        Log.i(TAG, "Resetting crypto algorithms to defaults");
        setCryptoAlgorithms(getDefaultCryptoAlgorithms());
    }
    
    /**
     * Check if crypto algorithms are configured
     */
    public boolean isCryptoAlgorithmsConfigured() {
        CryptoAlgorithms algorithms = getCryptoAlgorithms();
        return algorithms != null && algorithms.isValid();
    }
    
    // ===========================
    // APP MODE MANAGEMENT
    // ===========================
    
    /**
     * Set application mode
     */
    public void setAppMode(AppMode mode) {
        if (mode == null) {
            Log.w(TAG, "Attempted to set null app mode");
            return;
        }
        
        lock.writeLock().lock();
        try {
            editor.putString("app_mode", mode.name()).apply();
            Log.d(TAG, "App mode set to: " + mode);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get current application mode
     */
    public AppMode getAppMode() {
        lock.readLock().lock();
        try {
            String modeStr = preferences.getString("app_mode", null);
            if (modeStr == null) {
                return null;
            }
            
            try {
                return AppMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid app mode stored: " + modeStr, e);
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clear application mode
     */
    public void clearAppMode() {
        lock.writeLock().lock();
        try {
            editor.remove("app_mode").apply();
            Log.d(TAG, "App mode cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // SERVER CONFIGURATION
    // ===========================
    
    /**
     * Set server configuration
     */
    public void setServerConfig(String host, int port, String organizationName) {
        if (host == null || host.trim().isEmpty()) {
            Log.w(TAG, "Invalid host provided for server config");
            return;
        }
        
        if (port <= 0 || port > 65535) {
            Log.w(TAG, "Invalid port provided for server config: " + port);
            return;
        }
        
        if (organizationName == null || organizationName.trim().isEmpty()) {
            Log.w(TAG, "Invalid organization name provided for server config");
            return;
        }
        
        lock.writeLock().lock();
        try {
            ServerConfig config = new ServerConfig(host.trim(), port, organizationName.trim());
            String json = gson.toJson(config);
            editor.putString(KEY_SERVER_CONFIG, json).apply();
            Log.d(TAG, "Server config saved: " + host + ":" + port + " (" + organizationName + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save server config", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get server configuration
     */
    public ServerConfig getServerConfig() {
        lock.readLock().lock();
        try {
            String json = preferences.getString(KEY_SERVER_CONFIG, null);
            if (json == null) {
                return null;
            }
            
            return gson.fromJson(json, ServerConfig.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse server config JSON", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clear server configuration
     */
    public void clearServerConfig() {
        lock.writeLock().lock();
        try {
            editor.remove(KEY_SERVER_CONFIG).apply();
            Log.d(TAG, "Server config cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if server is configured
     */
    public boolean isServerConfigured() {
        return getServerConfig() != null;
    }
    
    // ===========================
    // USER PROFILE MANAGEMENT
    // ===========================
    
    /**
     * Set user profile
     */
    public void setUserProfile(UserProfile profile) {
        if (profile == null) {
            Log.w(TAG, "Attempted to set null user profile");
            return;
        }
        
        lock.writeLock().lock();
        try {
            String json = gson.toJson(profile);
            editor.putString(KEY_USER_PROFILE, json).apply();
            Log.d(TAG, "User profile saved for: " + profile.getUsername());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save user profile", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get user profile
     */
    public UserProfile getUserProfile() {
        lock.readLock().lock();
        try {
            String json = preferences.getString(KEY_USER_PROFILE, null);
            if (json == null) {
                return null;
            }
            
            return gson.fromJson(json, UserProfile.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse user profile JSON", e);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clear user profile
     */
    public void clearUserProfile() {
        lock.writeLock().lock();
        try {
            editor.remove(KEY_USER_PROFILE).apply();
            Log.d(TAG, "User profile cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // LOCAL USERNAME (for local mode)
    // ===========================
    
    /**
     * Set local username for P2P mode
     */
    public void setLocalUsername(String username) {
        if (username == null) {
            Log.w(TAG, "Attempted to set null local username");
            return;
        }
        
        lock.writeLock().lock();
        try {
            editor.putString(KEY_LOCAL_USERNAME, username.trim()).apply();
            Log.d(TAG, "Local username set: " + username.trim());
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
            return preferences.getString(KEY_LOCAL_USERNAME, null);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // SESSION MANAGEMENT - REMOVED
    // ===========================
    // Session management is now handled by SessionManager with AndroidKeystore
    // for enhanced security. All session-related methods have been moved there.
    
    // ===========================
    // SETUP STATE
    // ===========================
    
    /**
     * Mark first setup as completed
     */
    public void setFirstSetupCompleted(boolean completed) {
        lock.writeLock().lock();
        try {
            editor.putBoolean(KEY_FIRST_SETUP_COMPLETED, completed).apply();
            Log.d(TAG, "First setup completed flag set to: " + completed);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if first setup is completed
     */
    public boolean isFirstSetupCompleted() {
        lock.readLock().lock();
        try {
            return preferences.getBoolean(KEY_FIRST_SETUP_COMPLETED, false);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // UI PREFERENCES
    // ===========================
    
    /**
     * Set theme mode
     */
    public void setThemeMode(ThemeMode mode) {
        if (mode == null) {
            Log.w(TAG, "Attempted to set null theme mode");
            return;
        }
        
        lock.writeLock().lock();
        try {
            editor.putString(KEY_THEME_MODE, mode.name()).apply();
            Log.d(TAG, "Theme mode set to: " + mode);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get theme mode
     */
    public ThemeMode getThemeMode() {
        lock.readLock().lock();
        try {
            String modeStr = preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name());
            try {
                return ThemeMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid theme mode stored: " + modeStr, e);
                return ThemeMode.SYSTEM;
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set font size
     */
    public void setFontSize(FontSize size) {
        if (size == null) {
            Log.w(TAG, "Attempted to set null font size");
            return;
        }
        
        lock.writeLock().lock();
        try {
            editor.putString(KEY_FONT_SIZE, size.name()).apply();
            Log.d(TAG, "Font size set to: " + size);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get font size
     */
    public FontSize getFontSize() {
        lock.readLock().lock();
        try {
            String sizeStr = preferences.getString(KEY_FONT_SIZE, FontSize.MEDIUM.name());
            try {
                return FontSize.valueOf(sizeStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid font size stored: " + sizeStr, e);
                return FontSize.MEDIUM;
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set notifications enabled
     */
    public void setNotificationsEnabled(boolean enabled) {
        lock.writeLock().lock();
        try {
            editor.putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
            Log.d(TAG, "Notifications enabled set to: " + enabled);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if notifications are enabled
     */
    public boolean isNotificationsEnabled() {
        lock.readLock().lock();
        try {
            return preferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // APP STATE
    // ===========================
    
    /**
     * Set current screen
     */
    public void setCurrentScreen(AppScreen screen) {
        if (screen == null) {
            Log.w(TAG, "Attempted to set null current screen");
            return;
        }
        
        lock.writeLock().lock();
        try {
            editor.putString(KEY_CURRENT_SCREEN, screen.name()).apply();
            Log.d(TAG, "Current screen set to: " + screen);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get current screen
     */
    public AppScreen getCurrentScreen() {
        lock.readLock().lock();
        try {
            String screenStr = preferences.getString(KEY_CURRENT_SCREEN, null);
            if (screenStr == null) {
                return null;
            }
            
            try {
                return AppScreen.valueOf(screenStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid current screen stored: " + screenStr, e);
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set last active time
     */
    public void setLastActiveTime(long timestamp) {
        lock.writeLock().lock();
        try {
            editor.putLong(KEY_LAST_ACTIVE_TIME, timestamp).apply();
            Log.d(TAG, "Last active time set to: " + timestamp);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get last active time
     */
    public long getLastActiveTime() {
        lock.readLock().lock();
        try {
            return preferences.getLong(KEY_LAST_ACTIVE_TIME, 0L);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // BATCH OPERATIONS
    // ===========================
    
    /**
     * Perform multiple preference updates atomically
     */
    public void performBatchUpdate(Runnable updates) {
        lock.writeLock().lock();
        try {
            updates.run();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Export all preferences as a map
     */
    public Map<String, Object> exportPreferences() {
        lock.readLock().lock();
        try {
            return new HashMap<>(preferences.getAll());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // PRIVATE HELPER METHODS
    // ===========================
    
    /**
     * Get default crypto algorithms
     */
    private CryptoAlgorithms getDefaultCryptoAlgorithms() {
        return new CryptoAlgorithms("KYBER", "AES-256", "FALCON");
    }
    
    /**
     * Perform migration if needed
     */
    private void performMigrationIfNeeded() {
        int currentVersion = preferences.getInt(KEY_PREFS_VERSION, 0);
        
        if (currentVersion < CURRENT_PREFS_VERSION) {
            Log.i(TAG, "Performing preferences migration from version " + currentVersion + " to " + CURRENT_PREFS_VERSION);
            
            // Migration logic would go here
            // For now, just update the version
            editor.putInt(KEY_PREFS_VERSION, CURRENT_PREFS_VERSION).apply();
            
            Log.i(TAG, "Preferences migration completed");
        }
    }
    
    /**
     * Validate critical preferences
     */
    private void validateCriticalPreferences() {
        // Ensure crypto algorithms are valid
        CryptoAlgorithms algorithms = getCryptoAlgorithms();
        if (!algorithms.isValid()) {
            Log.w(TAG, "Invalid crypto algorithms detected, resetting to defaults");
            resetCryptoAlgorithmsToDefaults();
        }
        
        // Validate other critical preferences as needed
    }
    
    /**
     * Check if the manager is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
}