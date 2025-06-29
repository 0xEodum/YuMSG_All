package com.yumsg;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yumsg.core.service.BackgroundService;
import com.yumsg.core.ui.UIBridge;
import com.yumsg.core.data.StateObserver;
import com.yumsg.core.enums.AppState;
import com.yumsg.core.enums.ConnectionState;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.dart.DartExecutor;

/**
 * MainActivity - Flutter Activity with UIBridge Integration
 * 
 * Integrates Flutter engine with native Android components through UIBridge.
 * Manages application lifecycle and coordinates between Flutter UI and native services.
 * 
 * Key Features:
 * - FlutterEngine management and caching
 * - UIBridge initialization and lifecycle management
 * - BackgroundService coordination
 * - Proper cleanup on activity destruction
 * - State observation and UI synchronization
 */
public class MainActivity extends FlutterActivity implements StateObserver {
    private static final String TAG = "MainActivity";
    private static final String ENGINE_ID = "yumsg_engine";
    
    // Components
    private UIBridge uiBridge;
    private BackgroundService backgroundService;
    private FlutterEngine flutterEngine;
    
    // State
    private boolean isInitialized = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainActivity onCreate");
        
        // Initialize components in proper order
        initializeComponents();
    }
    
    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        Log.d(TAG, "Configuring FlutterEngine");
        
        this.flutterEngine = flutterEngine;
        
        // Initialize UIBridge with Flutter engine
        if (uiBridge != null) {
            uiBridge.initialize(flutterEngine);
            Log.i(TAG, "UIBridge initialized with FlutterEngine");
        } else {
            Log.e(TAG, "UIBridge is null during FlutterEngine configuration");
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MainActivity onResume");
        
        // Update app foreground state
        if (backgroundService != null) {
            backgroundService.getStateManager().setAppInForeground(true);
        }
        
        // Keep background service alive
        if (backgroundService != null) {
            backgroundService.keepAlive();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "MainActivity onPause");
        
        // Update app foreground state
        if (backgroundService != null) {
            backgroundService.getStateManager().setAppInForeground(false);
        }
    }
    
    @Override
    protected void onDestroy() {
        Log.d(TAG, "MainActivity onDestroy");
        
        // Cleanup components
        cleanup();
        
        super.onDestroy();
    }
    
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "MainActivity onNewIntent");
        
        // Handle deep links or push notifications
        handleIntent(intent);
    }
    
    // ===========================
    // INITIALIZATION
    // ===========================
    
    /**
     * Initialize all components in proper order
     */
    private void initializeComponents() {
        try {
            Log.d(TAG, "Initializing components");
            
            // Initialize BackgroundService
            backgroundService = BackgroundService.getInstance(this);
            if (!backgroundService.initialize()) {
                Log.e(TAG, "Failed to initialize BackgroundService");
                return;
            }
            
            // Initialize UIBridge
            uiBridge = new UIBridge(this);
            
            // Register as state observer
            backgroundService.registerStateObserver(this);
            
            // Setup Flutter engine (if not using cached engine)
            setupFlutterEngine();
            
            isInitialized = true;
            Log.i(TAG, "All components initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize components", e);
        }
    }
    
    /**
     * Setup Flutter engine for caching and reuse
     */
    private void setupFlutterEngine() {
        // Check if we have a cached engine
        FlutterEngine cachedEngine = FlutterEngineCache.getInstance().get(ENGINE_ID);
        
        if (cachedEngine == null) {
            Log.d(TAG, "Creating new FlutterEngine");
            
            // Create new engine
            FlutterEngine newEngine = new FlutterEngine(this);
            
            // Start Dart execution
            newEngine.getDartExecutor().executeDartEntrypoint(
                DartExecutor.DartEntrypoint.createDefault()
            );
            
            // Cache the engine
            FlutterEngineCache.getInstance().put(ENGINE_ID, newEngine);
            
            this.flutterEngine = newEngine;
        } else {
            Log.d(TAG, "Using cached FlutterEngine");
            this.flutterEngine = cachedEngine;
        }
    }
    
    // ===========================
    // FLUTTER ENGINE MANAGEMENT
    // ===========================
    
    @Nullable
    @Override
    public String getCachedEngineId() {
        return ENGINE_ID;
    }
    
    @Override
    public boolean shouldDestroyEngineWithHost() {
        // Don't destroy engine with host to enable caching
        return false;
    }
    
    // ===========================
    // STATE OBSERVER IMPLEMENTATION
    // ===========================
    
    @Override
    public void onStateChanged(AppState newState) {
        Log.d(TAG, "App state changed to: " + newState);
        
        // Forward state change to UIBridge
        if (uiBridge != null && uiBridge.isInitialized()) {
            uiBridge.onAppStateChanged(newState);
        }
        
        // Handle specific state changes
        switch (newState) {
            case CHAT_LIST:
                // Prepare for chat list screen
                handleChatListState();
                break;
            case CHAT_ACTIVE:
                // Handle active chat state
                handleActiveChatState();
                break;
            case AUTHENTICATION:
                // Handle authentication required
                handleAuthenticationState();
                break;
        }
    }
    
    @Override
    public void onConnectionStateChanged(ConnectionState newState) {
        Log.d(TAG, "Connection state changed to: " + newState);
        
        // Forward state change to UIBridge
        if (uiBridge != null && uiBridge.isInitialized()) {
            uiBridge.onConnectionStateChanged(newState);
        }
        
        // Handle specific connection states
        switch (newState) {
            case CONNECTED:
                handleConnectedState();
                break;
            case DISCONNECTED:
                handleDisconnectedState();
                break;
            case ERROR:
                handleConnectionError();
                break;
        }
    }
    
    // ===========================
    // STATE HANDLERS
    // ===========================
    
    private void handleChatListState() {
        // Perform any necessary setup for chat list
        Log.d(TAG, "Preparing chat list state");
    }
    
    private void handleActiveChatState() {
        // Handle active chat state
        Log.d(TAG, "Entered active chat state");
    }
    
    private void handleAuthenticationState() {
        // Handle authentication required state
        Log.d(TAG, "Authentication required");
    }
    
    private void handleConnectedState() {
        // Handle successful connection
        Log.d(TAG, "Successfully connected");
    }
    
    private void handleDisconnectedState() {
        // Handle disconnection
        Log.d(TAG, "Disconnected from service");
    }
    
    private void handleConnectionError() {
        // Handle connection error
        Log.w(TAG, "Connection error occurred");
    }
    
    // ===========================
    // INTENT HANDLING
    // ===========================
    
    /**
     * Handle incoming intents (deep links, notifications)
     */
    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "Handling intent with action: " + action);
        
        // Handle different intent actions
        if (Intent.ACTION_VIEW.equals(action)) {
            // Handle deep link
            handleDeepLink(intent);
        } else if ("com.yumsg.MESSAGE_NOTIFICATION".equals(action)) {
            // Handle message notification
            handleMessageNotification(intent);
        }
    }
    
    /**
     * Handle deep link navigation
     */
    private void handleDeepLink(Intent intent) {
        android.net.Uri uri = intent.getData();
        if (uri != null) {
            Log.d(TAG, "Deep link received: " + uri.toString());
            
            // Parse deep link and navigate accordingly
            String path = uri.getPath();
            if (path != null) {
                if (path.startsWith("/chat/")) {
                    String chatId = path.substring(6);
                    handleChatDeepLink(chatId);
                } else if (path.startsWith("/user/")) {
                    String userId = path.substring(6);
                    handleUserDeepLink(userId);
                }
            }
        }
    }
    
    /**
     * Handle chat deep link
     */
    private void handleChatDeepLink(String chatId) {
        Log.d(TAG, "Navigating to chat: " + chatId);
        
        if (uiBridge != null && uiBridge.isInitialized()) {
            // Send navigation event to Flutter
            java.util.Map<String, Object> eventData = new java.util.HashMap<>();
            eventData.put("chatId", chatId);
            uiBridge.sendEventToUI("NAVIGATE_TO_CHAT", eventData);
        }
    }
    
    /**
     * Handle user deep link
     */
    private void handleUserDeepLink(String userId) {
        Log.d(TAG, "Navigating to user: " + userId);
        
        if (uiBridge != null && uiBridge.isInitialized()) {
            // Send navigation event to Flutter
            java.util.Map<String, Object> eventData = new java.util.HashMap<>();
            eventData.put("userId", userId);
            uiBridge.sendEventToUI("NAVIGATE_TO_USER", eventData);
        }
    }
    
    /**
     * Handle message notification tap
     */
    private void handleMessageNotification(Intent intent) {
        String chatId = intent.getStringExtra("chatId");
        String messageId = intent.getStringExtra("messageId");
        
        Log.d(TAG, "Message notification tapped - Chat: " + chatId + ", Message: " + messageId);
        
        if (chatId != null && uiBridge != null && uiBridge.isInitialized()) {
            // Send notification tap event to Flutter
            java.util.Map<String, Object> eventData = new java.util.HashMap<>();
            eventData.put("chatId", chatId);
            eventData.put("messageId", messageId);
            uiBridge.sendEventToUI("NOTIFICATION_TAPPED", eventData);
        }
    }
    
    // ===========================
    // LIFECYCLE MANAGEMENT
    // ===========================
    
    /**
     * Cleanup resources
     */
    private void cleanup() {
        try {
            Log.d(TAG, "Cleaning up MainActivity resources");
            
            // Unregister state observer
            if (backgroundService != null) {
                backgroundService.unregisterStateObserver(this);
            }
            
            // Cleanup UIBridge
            if (uiBridge != null) {
                uiBridge.dispose();
                uiBridge = null;
            }
            
            isInitialized = false;
            Log.i(TAG, "MainActivity cleanup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }
    
    // ===========================
    // PUBLIC GETTERS
    // ===========================
    
    /**
     * Get UIBridge instance
     */
    public UIBridge getUIBridge() {
        return uiBridge;
    }
    
    /**
     * Get BackgroundService instance
     */
    public BackgroundService getBackgroundService() {
        return backgroundService;
    }
    
    /**
     * Check if MainActivity is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
}