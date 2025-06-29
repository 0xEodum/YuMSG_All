package com.yumsg.core.state;

import android.util.Log;

import com.yumsg.core.data.*;
import com.yumsg.core.enums.*;
import com.yumsg.core.storage.SharedPreferencesManager;
import com.yumsg.core.session.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * StateManager - Complete Implementation
 * 
 * Manages application state, connection state, and screen navigation logic.
 * Provides observer pattern for state changes and thread-safe operations.
 * 
 * Key Features:
 * - Thread-safe state management
 * - Observer pattern for state notifications
 * - Smart screen determination logic
 * - Connection restoration coordination
 * - State persistence and restoration
 * - Comprehensive logging and error handling
 */
public class StateManager {
    private static final String TAG = "StateManager";
    
    // Singleton instance
    private static volatile StateManager instance;
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // State variables
    private volatile AppState currentAppState;
    private volatile ConnectionState connectionState;
    private volatile UserStatus userPresence;
    private volatile String activeChatId;
    private volatile long lastActiveTime;
    private volatile boolean appInForeground;
    private volatile boolean restoringConnection;
    
    // Observer pattern
    private final List<StateObserver> observers;
    
    // Dependencies
    private SharedPreferencesManager preferencesManager;
    
    // State tracking
    private AppState previousAppState;
    private ConnectionState previousConnectionState;
    
    /**
     * Private constructor for singleton pattern
     */
    private StateManager() {
        this.observers = new CopyOnWriteArrayList<>();
        this.currentAppState = AppState.INITIALIZING;
        this.connectionState = ConnectionState.DISCONNECTED;
        this.userPresence = UserStatus.OFFLINE;
        this.lastActiveTime = System.currentTimeMillis();
        this.appInForeground = true;
        this.restoringConnection = false;
        
        Log.d(TAG, "StateManager instance created");
    }
    
    /**
     * Get singleton instance
     */
    public static StateManager getInstance() {
        if (instance == null) {
            synchronized (StateManager.class) {
                if (instance == null) {
                    instance = new StateManager();
                }
            }
        }
        return instance;
    }
    
    // ===========================
    // INITIALIZATION
    // ===========================
    
    /**
     * Initialize the state manager
     */
    public void initialize() {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Initializing StateManager");
            
            // Initialize dependencies
            this.preferencesManager = SharedPreferencesManager.getInstance();
            
            // Restore state from preferences if available
            restoreStateFromPreferences();
            
            // Update last active time
            updateLastActiveTime();
            
            Log.i(TAG, "StateManager initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize StateManager", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // APP STATE MANAGEMENT
    // ===========================
    
    /**
     * Get current application state
     */
    public AppState getCurrentAppState() {
        lock.readLock().lock();
        try {
            return currentAppState;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set application state
     */
    public void setAppState(AppState state) {
        if (state == null) {
            Log.w(TAG, "Attempted to set null app state");
            return;
        }
        
        lock.writeLock().lock();
        try {
            AppState oldState = this.currentAppState;
            this.previousAppState = oldState;
            this.currentAppState = state;
            
            Log.d(TAG, "App state changed: " + oldState + " -> " + state);
            
            // Update preferences
            if (preferencesManager != null) {
                preferencesManager.setCurrentScreen(appStateToScreen(state));
            }
            
            // Update last active time
            updateLastActiveTime();
            
            // Notify observers
            notifyStateChange(new StateChange("APP_STATE_CHANGED", oldState, state));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get previous application state
     */
    public AppState getPreviousAppState() {
        lock.readLock().lock();
        try {
            return previousAppState;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // CONNECTION STATE MANAGEMENT
    // ===========================
    
    /**
     * Get current connection state
     */
    public ConnectionState getConnectionState() {
        lock.readLock().lock();
        try {
            return connectionState;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set connection state
     */
    public void setConnectionState(ConnectionState state) {
        if (state == null) {
            Log.w(TAG, "Attempted to set null connection state");
            return;
        }
        
        lock.writeLock().lock();
        try {
            ConnectionState oldState = this.connectionState;
            this.previousConnectionState = oldState;
            this.connectionState = state;
            
            Log.d(TAG, "Connection state changed: " + oldState + " -> " + state);
            
            // Update restoration flag
            if (state == ConnectionState.CONNECTING) {
                this.restoringConnection = true;
            } else if (state == ConnectionState.CONNECTED || state == ConnectionState.ERROR) {
                this.restoringConnection = false;
            }
            
            // Notify observers
            notifyStateChange(new StateChange("CONNECTION_STATE_CHANGED", oldState, state));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get previous connection state
     */
    public ConnectionState getPreviousConnectionState() {
        lock.readLock().lock();
        try {
            return previousConnectionState;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // USER PRESENCE MANAGEMENT
    // ===========================
    
    /**
     * Get user presence status
     */
    public UserStatus getUserPresence() {
        lock.readLock().lock();
        try {
            return userPresence;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set user presence status
     */
    public void setUserPresence(UserStatus presence) {
        if (presence == null) {
            Log.w(TAG, "Attempted to set null user presence");
            return;
        }
        
        lock.writeLock().lock();
        try {
            UserStatus oldPresence = this.userPresence;
            this.userPresence = presence;
            
            Log.d(TAG, "User presence changed: " + oldPresence + " -> " + presence);
            
            // Update last active time if coming online
            if (presence == UserStatus.ONLINE) {
                updateLastActiveTime();
            }
            
            // Notify observers
            notifyStateChange(new StateChange("USER_PRESENCE_CHANGED", oldPresence, presence));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // ACTIVE CHAT MANAGEMENT
    // ===========================
    
    /**
     * Get list of active chats (simplified - returns single active chat)
     */
    public List<String> getActiveChats() {
        List<String> activeChats = new ArrayList<>();
        
        lock.readLock().lock();
        try {
            if (activeChatId != null) {
                activeChats.add(activeChatId);
            }
        } finally {
            lock.readLock().unlock();
        }
        
        return activeChats;
    }
    
    /**
     * Set active chat ID
     */
    public void setActiveChatId(String chatId) {
        lock.writeLock().lock();
        try {
            String oldChatId = this.activeChatId;
            this.activeChatId = chatId;
            
            if (chatId != null) {
                Log.d(TAG, "Active chat changed: " + oldChatId + " -> " + chatId);
                updateLastActiveTime();
            } else {
                Log.d(TAG, "Active chat cleared");
            }
            
            // Notify observers
            notifyStateChange(new StateChange("ACTIVE_CHAT_CHANGED", oldChatId, chatId));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get active chat ID
     */
    public String getActiveChatId() {
        lock.readLock().lock();
        try {
            return activeChatId;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // SCREEN DETERMINATION LOGIC
    // ===========================
    
    /**
     * Determine which screen to show on app start
     * This is the main navigation logic for the application
     */
    public AppScreen determineStartScreen() {
        lock.readLock().lock();
        try {
            Log.d(TAG, "Determining start screen...");
            
            // Ensure preferences manager is available
            if (preferencesManager == null) {
                Log.w(TAG, "PreferencesManager not available, defaulting to MODE_SELECTION");
                return AppScreen.MODE_SELECTION;
            }
            
            // 1. Check first setup completion
            boolean firstSetupCompleted = preferencesManager.isFirstSetupCompleted();
            if (!firstSetupCompleted) {
                Log.d(TAG, "First setup not completed -> MODE_SELECTION");
                return AppScreen.MODE_SELECTION;
            }
            
            // 2. Get application mode
            AppMode appMode = preferencesManager.getAppMode();
            if (appMode == null) {
                Log.d(TAG, "No app mode set -> MODE_SELECTION");
                return AppScreen.MODE_SELECTION;
            }
            
            // 3. Mode-specific logic
            if (appMode == AppMode.SERVER) {
                AppScreen serverScreen = determineServerModeStartScreen();
                Log.d(TAG, "Server mode start screen: " + serverScreen);
                return serverScreen;
            } else if (appMode == AppMode.LOCAL) {
                AppScreen localScreen = determineLocalModeStartScreen();
                Log.d(TAG, "Local mode start screen: " + localScreen);
                return localScreen;
            }
            
            // 4. Fallback
            Log.d(TAG, "Unknown app mode, defaulting to MODE_SELECTION");
            return AppScreen.MODE_SELECTION;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Determine start screen for server mode
     */
    private AppScreen determineServerModeStartScreen() {
        // Check server configuration
        ServerConfig serverConfig = preferencesManager.getServerConfig();
        if (serverConfig == null) {
            Log.d(TAG, "No server config -> SERVER_CONNECTION");
            return AppScreen.SERVER_CONNECTION;
        }
        
        // Check user session through SessionManager
        SessionManager sessionManager = SessionManager.getInstance();
        if (!sessionManager.isUserAuthorized()) {
            Log.d(TAG, "No valid session -> AUTHENTICATION");
            return AppScreen.AUTHENTICATION;
        }
        
        // Check session validity (detailed validation in SessionManager)
        if (!sessionManager.isSessionValid()) {
            Log.d(TAG, "Session expired -> AUTHENTICATION");
            return AppScreen.AUTHENTICATION;
        }
        
        // Valid session - schedule background restoration and go to chats
        Log.d(TAG, "Valid session found -> CHAT_LIST (with background restoration)");
        scheduleConnectionRestoration();
        return AppScreen.CHAT_LIST;
    }
    
    /**
     * Determine start screen for local mode
     */
    private AppScreen determineLocalModeStartScreen() {
        // Check local username
        String localUsername = preferencesManager.getLocalUsername();
        if (localUsername == null || localUsername.trim().isEmpty()) {
            Log.d(TAG, "No local username -> AUTHENTICATION");
            return AppScreen.AUTHENTICATION;
        }
        
        // Username exists - go to chats
        Log.d(TAG, "Local username found -> CHAT_LIST");
        return AppScreen.CHAT_LIST;
    }
    
    // ===========================
    // CONNECTION RESTORATION
    // ===========================
    
    /**
     * Schedule connection restoration for automatic session recovery
     */
    public void scheduleConnectionRestoration() {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Scheduling connection restoration");
            
            AppMode appMode = preferencesManager.getAppMode();
            
            if (appMode == AppMode.SERVER) {
                scheduleServerConnectionRestoration();
            } else if (appMode == AppMode.LOCAL) {
                scheduleLocalNetworkInitialization();
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Schedule server connection restoration
     */
    private void scheduleServerConnectionRestoration() {
        Log.d(TAG, "Scheduling server connection restoration");
        setConnectionState(ConnectionState.CONNECTING);
        
        // Note: Actual connection restoration will be handled by BackgroundService
        // This method just sets the appropriate flags and states
        restoringConnection = true;
    }
    
    /**
     * Schedule local network initialization
     */
    private void scheduleLocalNetworkInitialization() {
        Log.d(TAG, "Scheduling local network initialization");
        setConnectionState(ConnectionState.CONNECTING);
        
        // Note: Actual P2P initialization will be handled by BackgroundService
        // This method just sets the appropriate flags and states
        restoringConnection = true;
    }
    
    /**
     * Check if connection progress should be shown
     */
    public boolean shouldShowConnectionProgress() {
        lock.readLock().lock();
        try {
            return restoringConnection && 
                   (connectionState == ConnectionState.CONNECTING || 
                    connectionState == ConnectionState.DISCONNECTED);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check if connection restoration is in progress
     */
    public boolean isRestoringConnection() {
        lock.readLock().lock();
        try {
            return restoringConnection;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // OBSERVER PATTERN
    // ===========================
    
    /**
     * Add state observer
     */
    public void addStateObserver(StateObserver observer) {
        if (observer == null) {
            Log.w(TAG, "Attempted to add null observer");
            return;
        }
        
        if (!observers.contains(observer)) {
            observers.add(observer);
            Log.d(TAG, "State observer added: " + observer.getClass().getSimpleName());
        }
    }
    
    /**
     * Remove state observer
     */
    public void removeStateObserver(StateObserver observer) {
        if (observer == null) {
            Log.w(TAG, "Attempted to remove null observer");
            return;
        }
        
        if (observers.remove(observer)) {
            Log.d(TAG, "State observer removed: " + observer.getClass().getSimpleName());
        }
    }
    
    /**
     * Notify all observers of state change
     */
    public void notifyStateChange(StateChange change) {
        if (change == null) {
            Log.w(TAG, "Attempted to notify with null state change");
            return;
        }
        
        Log.d(TAG, "Notifying " + observers.size() + " observers of state change: " + change.getType());
        
        // Notify observers on a separate thread to avoid blocking
        for (StateObserver observer : observers) {
            try {
                observer.onStateChanged(currentAppState);
                observer.onConnectionStateChanged(connectionState);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying observer: " + observer.getClass().getSimpleName(), e);
            }
        }
    }
    
    // ===========================
    // STATE PERSISTENCE
    // ===========================
    
    /**
     * Save current state to preferences
     */
    public void saveState() {
        lock.readLock().lock();
        try {
            if (preferencesManager == null) {
                Log.w(TAG, "Cannot save state - preferences manager not available");
                return;
            }
            
            Log.d(TAG, "Saving state to preferences");
            
            // Save current screen
            AppScreen currentScreen = appStateToScreen(currentAppState);
            if (currentScreen != null) {
                preferencesManager.setCurrentScreen(currentScreen);
            }
            
            // Save last active time
            preferencesManager.setLastActiveTime(lastActiveTime);
            
            Log.d(TAG, "State saved successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save state", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Restore state from preferences
     */
    public void restoreState() {
        lock.writeLock().lock();
        try {
            if (preferencesManager == null) {
                Log.w(TAG, "Cannot restore state - preferences manager not available");
                return;
            }
            
            Log.d(TAG, "Restoring state from preferences");
            
            restoreStateFromPreferences();
            
            Log.d(TAG, "State restored successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore state", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Reset state to initial values
     */
    public void resetState() {
        lock.writeLock().lock();
        try {
            Log.i(TAG, "Resetting state to defaults");
            
            AppState oldAppState = this.currentAppState;
            ConnectionState oldConnectionState = this.connectionState;
            
            this.currentAppState = AppState.INITIALIZING;
            this.connectionState = ConnectionState.DISCONNECTED;
            this.userPresence = UserStatus.OFFLINE;
            this.activeChatId = null;
            this.lastActiveTime = System.currentTimeMillis();
            this.restoringConnection = false;
            
            // Notify observers
            notifyStateChange(new StateChange("STATE_RESET", 
                Map.of("appState", oldAppState, "connectionState", oldConnectionState), 
                Map.of("appState", currentAppState, "connectionState", connectionState)));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // ACTIVITY TRACKING
    // ===========================
    
    /**
     * Get last active time
     */
    public long getLastActiveTime() {
        lock.readLock().lock();
        try {
            return lastActiveTime;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Update last active time to current time
     */
    public void updateLastActiveTime() {
        lock.writeLock().lock();
        try {
            this.lastActiveTime = System.currentTimeMillis();
            
            // Update presence to online if app is in foreground
            if (appInForeground && userPresence != UserStatus.ONLINE) {
                setUserPresence(UserStatus.ONLINE);
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if app is in foreground
     */
    public boolean isAppInForeground() {
        lock.readLock().lock();
        try {
            return appInForeground;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Set app foreground state
     */
    public void setAppInForeground(boolean inForeground) {
        lock.writeLock().lock();
        try {
            boolean oldState = this.appInForeground;
            this.appInForeground = inForeground;
            
            Log.d(TAG, "App foreground state changed: " + oldState + " -> " + inForeground);
            
            if (inForeground) {
                // App came to foreground
                updateLastActiveTime();
                if (userPresence == UserStatus.AWAY) {
                    setUserPresence(UserStatus.ONLINE);
                }
            } else {
                // App went to background
                if (userPresence == UserStatus.ONLINE) {
                    setUserPresence(UserStatus.AWAY);
                }
            }
            
            // Notify observers
            notifyStateChange(new StateChange("APP_FOREGROUND_CHANGED", oldState, inForeground));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // PRIVATE HELPER METHODS
    // ===========================
    
    /**
     * Restore state from preferences
     */
    private void restoreStateFromPreferences() {
        try {
            // Restore last active time
            long savedLastActiveTime = preferencesManager.getLastActiveTime();
            if (savedLastActiveTime > 0) {
                this.lastActiveTime = savedLastActiveTime;
            }
            
            // Restore current screen and convert to app state
            AppScreen savedScreen = preferencesManager.getCurrentScreen();
            if (savedScreen != null) {
                AppState restoredState = screenToAppState(savedScreen);
                if (restoredState != null) {
                    this.currentAppState = restoredState;
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore state from preferences", e);
        }
    }
    
    /**
     * Convert AppState to AppScreen
     */
    private AppScreen appStateToScreen(AppState appState) {
        if (appState == null) {
            return null;
        }
        
        switch (appState) {
            case MODE_SELECTION:
                return AppScreen.MODE_SELECTION;
            case SERVER_CONNECTION:
                return AppScreen.SERVER_CONNECTION;
            case AUTHENTICATION:
                return AppScreen.AUTHENTICATION;
            case CHAT_LIST:
            case AUTHENTICATED:
            case RESTORED:
                return AppScreen.CHAT_LIST;
            case CHAT_ACTIVE:
                return AppScreen.CHAT_ACTIVE;
            default:
                return AppScreen.MODE_SELECTION;
        }
    }
    
    /**
     * Convert AppScreen to AppState
     */
    private AppState screenToAppState(AppScreen screen) {
        if (screen == null) {
            return null;
        }
        
        switch (screen) {
            case MODE_SELECTION:
                return AppState.MODE_SELECTION;
            case SERVER_CONNECTION:
                return AppState.SERVER_CONNECTION;
            case AUTHENTICATION:
                return AppState.AUTHENTICATION;
            case CHAT_LIST:
                return AppState.CHAT_LIST;
            case CHAT_ACTIVE:
                return AppState.CHAT_ACTIVE;
            default:
                return AppState.INITIALIZING;
        }
    }
    
    /**
     * Get current state summary for debugging
     */
    public String getStateSummary() {
        lock.readLock().lock();
        try {
            return String.format(
                "StateManager Summary: AppState=%s, ConnectionState=%s, UserPresence=%s, " +
                "ActiveChat=%s, InForeground=%s, RestoringConnection=%s",
                currentAppState, connectionState, userPresence, 
                activeChatId, appInForeground, restoringConnection
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}