package com.example.yumsg.core.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.example.yumsg.core.data.*;
import com.example.yumsg.core.enums.*;
import com.example.yumsg.core.service.BackgroundService;
import com.example.yumsg.core.state.StateManager;
import com.example.yumsg.core.session.SessionManager;
import com.example.yumsg.core.storage.SharedPreferencesManager;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * UIBridge - Complete Flutter-Android Communication Bridge
 * 
 * Provides comprehensive two-way communication between Flutter UI and Android native layer.
 * Implements both MethodChannel for Flutter->Android calls and EventChannel for Android->Flutter events.
 * 
 * Architecture:
 * - MethodChannel: Handles synchronous calls from Flutter (user actions, data requests)
 * - EventChannel: Streams asynchronous events to Flutter (messages, state changes, notifications)
 * - Thread-safe operations with proper error handling
 * - Structured event system with typed data transfer
 * - Integration with BackgroundService for business logic coordination
 * 
 * Key Features:
 * - Comprehensive method handling for all app operations
 * - Real-time event streaming to Flutter
 * - Proper lifecycle management
 * - Structured error handling and response formatting
 * - Support for complex data types serialization
 * - Thread-safe concurrent operations
 */
public class UIBridge implements MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private static final String TAG = "UIBridge";
    
    // Channel names
    private static final String METHOD_CHANNEL = "com.example.yumsg/methods";
    private static final String EVENT_CHANNEL = "com.example.yumsg/events";
    
    // Method names - App State & Mode Management
    private static final String METHOD_SELECT_MODE = "selectMode";
    private static final String METHOD_GET_CURRENT_STATE = "getCurrentState";
    private static final String METHOD_GET_APP_MODE = "getAppMode";
    
    // Method names - Server Connection & Authentication
    private static final String METHOD_CONNECT_TO_SERVER = "connectToServer";
    private static final String METHOD_AUTHENTICATE_USER = "authenticateUser";
    private static final String METHOD_REGISTER_USER = "registerUser";
    private static final String METHOD_SET_LOCAL_USER = "setLocalUser";
    private static final String METHOD_LOGOUT = "logout";
    
    // Method names - Chat Operations
    private static final String METHOD_INITIALIZE_CHAT = "initializeChat";
    private static final String METHOD_SEND_MESSAGE = "sendMessage";
    private static final String METHOD_SEND_FILE = "sendFile";
    private static final String METHOD_GET_CHAT_LIST = "getChatList";
    private static final String METHOD_GET_CHAT_INFO = "getChatInfo";
    private static final String METHOD_DELETE_CHAT = "deleteChat";
    private static final String METHOD_CLEAR_CHAT_HISTORY = "clearChatHistory";
    
    // Method names - User Management
    private static final String METHOD_SEARCH_USERS = "searchUsers";
    private static final String METHOD_GET_USER_PROFILE = "getUserProfile";
    private static final String METHOD_UPDATE_USER_PROFILE = "updateUserProfile";
    private static final String METHOD_GET_USER_STATUS = "getUserStatus";
    private static final String METHOD_SET_USER_STATUS = "setUserStatus";
    
    // Method names - Settings & Configuration
    private static final String METHOD_UPDATE_CRYPTO_ALGORITHMS = "updateCryptoAlgorithms";
    private static final String METHOD_GET_CRYPTO_ALGORITHMS = "getCryptoAlgorithms";
    private static final String METHOD_CHANGE_THEME = "changeTheme";
    private static final String METHOD_GET_SETTINGS = "getSettings";
    private static final String METHOD_UPDATE_SETTINGS = "updateSettings";
    
    // Method names - File Operations
    private static final String METHOD_UPLOAD_FILE = "uploadFile";
    private static final String METHOD_DOWNLOAD_FILE = "downloadFile";
    private static final String METHOD_GET_FILE_INFO = "getFileInfo";
    
    // Method names - Contacts
    private static final String METHOD_GET_CONTACTS = "getContacts";
    private static final String METHOD_ADD_CONTACT = "addContact";
    private static final String METHOD_DELETE_CONTACT = "deleteContact";
    
    // Method names - Organization
    private static final String METHOD_GET_ORGANIZATION_INFO = "getOrganizationInfo";
    private static final String METHOD_GET_SERVER_ALGORITHMS = "getServerAlgorithms";
    
    // Method names - Session Management
    private static final String METHOD_GET_SESSION_INFO = "getSessionInfo";
    private static final String METHOD_REFRESH_SESSION = "refreshSession";
    private static final String METHOD_IS_SESSION_VALID = "isSessionValid";
    
    // Method names - Utility
    private static final String METHOD_GET_APP_VERSION = "getAppVersion";
    private static final String METHOD_GET_CONNECTION_STATUS = "getConnectionStatus";
    private static final String METHOD_GET_CONNECTION_METRICS = "getConnectionMetrics";
    
    // Event types - State Changes
    private static final String EVENT_APP_STATE_CHANGED = "AppStateChanged";
    private static final String EVENT_CONNECTION_STATE_CHANGED = "ConnectionStateChanged";
    private static final String EVENT_USER_STATUS_CHANGED = "UserStatusChanged";
    
    // Event types - Messages & Chats
    private static final String EVENT_MESSAGE_RECEIVED = "MessageReceived";
    private static final String EVENT_MESSAGE_STATUS_UPDATED = "MessageStatusUpdated";
    private static final String EVENT_CHAT_ESTABLISHED = "ChatEstablished";
    private static final String EVENT_CHAT_DELETED = "ChatDeleted";
    private static final String EVENT_TYPING_STATUS = "TypingStatus";
    
    // Event types - Notifications & UI
    private static final String EVENT_NOTIFICATION = "Notification";
    private static final String EVENT_ERROR_OCCURRED = "ErrorOccurred";
    private static final String EVENT_PROGRESS_UPDATE = "ProgressUpdate";
    private static final String EVENT_SESSION_RESTORED = "SessionRestored";
    private static final String EVENT_SESSION_RESTORATION_FAILED = "SessionRestorationFailed";
    private static final String EVENT_P2P_RESTORED = "P2PRestored";
    
    // Event types - Connection & Network
    private static final String EVENT_CONNECTION_HEALTH = "ConnectionHealth";
    private static final String EVENT_PEER_CONNECTED = "PeerConnected";
    private static final String EVENT_PEER_DISCONNECTED = "PeerDisconnected";
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Dependencies
    private final Context context;
    private final Handler mainHandler;
    private final Gson gson;
    
    // Flutter components
    private FlutterEngine flutterEngine;
    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private EventChannel.EventSink eventSink;
    
    // Core components
    private BackgroundService backgroundService;
    private StateManager stateManager;
    private SessionManager sessionManager;
    private SharedPreferencesManager preferencesManager;
    
    // State
    private volatile boolean isInitialized = false;
    private volatile boolean isEventStreamActive = false;
    
    /**
     * Constructor
     */
    public UIBridge(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .serializeNulls()
            .create();
        
        Log.d(TAG, "UIBridge instance created");
    }
    
    // ===========================
    // LIFECYCLE MANAGEMENT
    // ===========================
    
    /**
     * Initialize UIBridge with Flutter engine
     */
    public void initialize(FlutterEngine flutterEngine) {
        lock.writeLock().lock();
        try {
            if (isInitialized) {
                Log.w(TAG, "UIBridge already initialized");
                return;
            }
            
            Log.d(TAG, "Initializing UIBridge with Flutter engine");
            
            this.flutterEngine = flutterEngine;
            
            // Initialize core components
            initializeCoreComponents();
            
            // Setup Flutter channels
            setupFlutterChannels();
            
            // Register with BackgroundService
            backgroundService.setUIBridge(this);
            
            isInitialized = true;
            Log.i(TAG, "UIBridge initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize UIBridge", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Cleanup resources
     */
    public void dispose() {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Disposing UIBridge");
            
            isInitialized = false;
            isEventStreamActive = false;
            
            // Clear event sink
            if (eventSink != null) {
                eventSink.endOfStream();
                eventSink = null;
            }
            
            // Clear channel references
            if (methodChannel != null) {
                methodChannel.setMethodCallHandler(null);
                methodChannel = null;
            }
            
            if (eventChannel != null) {
                eventChannel.setStreamHandler(null);
                eventChannel = null;
            }
            
            flutterEngine = null;
            
            Log.i(TAG, "UIBridge disposed");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Initialize core components
     */
    private void initializeCoreComponents() {
        backgroundService = BackgroundService.getInstance();
        stateManager = StateManager.getInstance();
        sessionManager = SessionManager.getInstance();
        preferencesManager = SharedPreferencesManager.getInstance();
        
        Log.d(TAG, "Core components initialized");
    }
    
    /**
     * Setup Flutter communication channels
     */
    private void setupFlutterChannels() {
        // Setup method channel
        methodChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), METHOD_CHANNEL);
        methodChannel.setMethodCallHandler(this);
        
        // Setup event channel
        eventChannel = new EventChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), EVENT_CHANNEL);
        eventChannel.setStreamHandler(this);
        
        Log.d(TAG, "Flutter channels configured");
    }
    
    // ===========================
    // METHODCHANNEL IMPLEMENTATION
    // ===========================
    
    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        try {
            Log.d(TAG, "Method call received: " + call.method);
            
            if (!isInitialized) {
                result.error("NOT_INITIALIZED", "UIBridge not initialized", null);
                return;
            }
            
            // Route method calls
            switch (call.method) {
                // App State & Mode Management
                case METHOD_SELECT_MODE:
                    handleSelectMode(call, result);
                    break;
                case METHOD_GET_CURRENT_STATE:
                    handleGetCurrentState(call, result);
                    break;
                case METHOD_GET_APP_MODE:
                    handleGetAppMode(call, result);
                    break;
                    
                // Server Connection & Authentication
                case METHOD_CONNECT_TO_SERVER:
                    handleConnectToServer(call, result);
                    break;
                case METHOD_AUTHENTICATE_USER:
                    handleAuthenticateUser(call, result);
                    break;
                case METHOD_REGISTER_USER:
                    handleRegisterUser(call, result);
                    break;
                case METHOD_SET_LOCAL_USER:
                    handleSetLocalUser(call, result);
                    break;
                case METHOD_LOGOUT:
                    handleLogout(call, result);
                    break;
                    
                // Chat Operations
                case METHOD_INITIALIZE_CHAT:
                    handleInitializeChat(call, result);
                    break;
                case METHOD_SEND_MESSAGE:
                    handleSendMessage(call, result);
                    break;
                case METHOD_SEND_FILE:
                    handleSendFile(call, result);
                    break;
                case METHOD_GET_CHAT_LIST:
                    handleGetChatList(call, result);
                    break;
                case METHOD_GET_CHAT_INFO:
                    handleGetChatInfo(call, result);
                    break;
                case METHOD_DELETE_CHAT:
                    handleDeleteChat(call, result);
                    break;
                case METHOD_CLEAR_CHAT_HISTORY:
                    handleClearChatHistory(call, result);
                    break;
                    
                // User Management
                case METHOD_SEARCH_USERS:
                    handleSearchUsers(call, result);
                    break;
                case METHOD_GET_USER_PROFILE:
                    handleGetUserProfile(call, result);
                    break;
                case METHOD_UPDATE_USER_PROFILE:
                    handleUpdateUserProfile(call, result);
                    break;
                case METHOD_GET_USER_STATUS:
                    handleGetUserStatus(call, result);
                    break;
                case METHOD_SET_USER_STATUS:
                    handleSetUserStatus(call, result);
                    break;
                    
                // Settings & Configuration
                case METHOD_UPDATE_CRYPTO_ALGORITHMS:
                    handleUpdateCryptoAlgorithms(call, result);
                    break;
                case METHOD_GET_CRYPTO_ALGORITHMS:
                    handleGetCryptoAlgorithms(call, result);
                    break;
                case METHOD_CHANGE_THEME:
                    handleChangeTheme(call, result);
                    break;
                case METHOD_GET_SETTINGS:
                    handleGetSettings(call, result);
                    break;
                case METHOD_UPDATE_SETTINGS:
                    handleUpdateSettings(call, result);
                    break;
                    
                // File Operations
                case METHOD_UPLOAD_FILE:
                    handleUploadFile(call, result);
                    break;
                case METHOD_DOWNLOAD_FILE:
                    handleDownloadFile(call, result);
                    break;
                case METHOD_GET_FILE_INFO:
                    handleGetFileInfo(call, result);
                    break;
                    
                // Contacts
                case METHOD_GET_CONTACTS:
                    handleGetContacts(call, result);
                    break;
                case METHOD_ADD_CONTACT:
                    handleAddContact(call, result);
                    break;
                case METHOD_DELETE_CONTACT:
                    handleDeleteContact(call, result);
                    break;
                    
                // Organization
                case METHOD_GET_ORGANIZATION_INFO:
                    handleGetOrganizationInfo(call, result);
                    break;
                case METHOD_GET_SERVER_ALGORITHMS:
                    handleGetServerAlgorithms(call, result);
                    break;
                    
                // Session Management
                case METHOD_GET_SESSION_INFO:
                    handleGetSessionInfo(call, result);
                    break;
                case METHOD_REFRESH_SESSION:
                    handleRefreshSession(call, result);
                    break;
                case METHOD_IS_SESSION_VALID:
                    handleIsSessionValid(call, result);
                    break;
                    
                // Utility
                case METHOD_GET_APP_VERSION:
                    handleGetAppVersion(call, result);
                    break;
                case METHOD_GET_CONNECTION_STATUS:
                    handleGetConnectionStatus(call, result);
                    break;
                case METHOD_GET_CONNECTION_METRICS:
                    handleGetConnectionMetrics(call, result);
                    break;
                    
                default:
                    result.notImplemented();
                    break;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling method call: " + call.method, e);
            result.error("INTERNAL_ERROR", "Internal error: " + e.getMessage(), getStackTrace(e));
        }
    }
    
    // ===========================
    // METHOD HANDLERS - App State & Mode Management
    // ===========================
    
    private void handleSelectMode(MethodCall call, MethodChannel.Result result) {
        String modeStr = call.argument("mode");
        if (modeStr == null) {
            result.error("INVALID_ARGUMENTS", "Mode parameter is required", null);
            return;
        }
        
        try {
            AppMode mode = AppMode.valueOf(modeStr.toUpperCase());
            
            backgroundService.selectAppMode(mode)
                .thenAccept(success -> {
                    mainHandler.post(() -> {
                        if (success) {
                            result.success(createSuccessResponse("Mode selected successfully"));
                        } else {
                            result.error("MODE_SELECTION_FAILED", "Failed to select mode", null);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    mainHandler.post(() -> result.error("MODE_SELECTION_ERROR", throwable.getMessage(), null));
                    return null;
                });
                
        } catch (IllegalArgumentException e) {
            result.error("INVALID_MODE", "Invalid mode: " + modeStr, null);
        }
    }
    
    private void handleGetCurrentState(MethodCall call, MethodChannel.Result result) {
        try {
            AppState currentState = backgroundService.getCurrentState();
            Map<String, Object> response = createSuccessResponse("Current state retrieved");
            response.put("appState", currentState.name());
            response.put("connectionState", stateManager.getConnectionState().name());
            result.success(response);
        } catch (Exception e) {
            result.error("STATE_ERROR", "Failed to get current state", null);
        }
    }
    
    private void handleGetAppMode(MethodCall call, MethodChannel.Result result) {
        try {
            AppMode appMode = backgroundService.getAppMode();
            Map<String, Object> response = createSuccessResponse("App mode retrieved");
            response.put("mode", appMode != null ? appMode.name() : null);
            result.success(response);
        } catch (Exception e) {
            result.error("MODE_ERROR", "Failed to get app mode", null);
        }
    }
    
    // ===========================
    // METHOD HANDLERS - Server Connection & Authentication
    // ===========================
    
    private void handleConnectToServer(MethodCall call, MethodChannel.Result result) {
        String host = call.argument("host");
        Integer port = call.argument("port");

        if (host == null || port == null) {
            result.error("INVALID_ARGUMENTS", "Host and port are required", null);
            return;
        }

        backgroundService.connectToServer(host, port)
            .thenAccept(connectionResult -> {
                mainHandler.post(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", connectionResult.isSuccess());
                    response.put("message", connectionResult.getMessage());
                    result.success(response);
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("CONNECTION_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleAuthenticateUser(MethodCall call, MethodChannel.Result result) {
        String username = call.argument("username");
        String password = call.argument("password");
        String email = call.argument("email");
        
        if (username == null || password == null) {
            result.error("INVALID_ARGUMENTS", "Username and password are required", null);
            return;
        }
        
        backgroundService.authenticateUser(username, password, email)
            .thenAccept(authResult -> {
                mainHandler.post(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", authResult.isSuccess());
                    response.put("message", authResult.getMessage());
                    response.put("token", authResult.getToken());
                    result.success(response);
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("AUTH_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleRegisterUser(MethodCall call, MethodChannel.Result result) {
        String username = call.argument("username");
        String password = call.argument("password");
        String email = call.argument("email");
        String displayName = call.argument("displayName");
        
        if (username == null || password == null || email == null) {
            result.error("INVALID_ARGUMENTS", "Username, password, and email are required", null);
            return;
        }
        
        // Create UserRegistrationInfo
        UserRegistrationInfo regInfo = new UserRegistrationInfo(username, email, password, displayName);
        
        // Convert to UserProfile for background service
        UserProfile userProfile = new UserProfile();
        userProfile.setUsername(username);
        userProfile.setEmail(email);
        userProfile.setDisplayName(displayName != null ? displayName : username);
        
        backgroundService.getNetworkManager().registerUser(userProfile)
            .thenAccept(authResult -> {
                mainHandler.post(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", authResult.isSuccess());
                    response.put("message", authResult.getMessage());
                    result.success(response);
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("REGISTRATION_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleSetLocalUser(MethodCall call, MethodChannel.Result result) {
        String username = call.argument("username");
        
        if (username == null) {
            result.error("INVALID_ARGUMENTS", "Username is required", null);
            return;
        }
        
        backgroundService.setLocalUser(username)
            .thenAccept(success -> {
                mainHandler.post(() -> {
                    if (success) {
                        result.success(createSuccessResponse("Local user set successfully"));
                    } else {
                        result.error("LOCAL_USER_ERROR", "Failed to set local user", null);
                    }
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("LOCAL_USER_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleLogout(MethodCall call, MethodChannel.Result result) {
        backgroundService.logout()
            .thenAccept(success -> {
                mainHandler.post(() -> {
                    if (success) {
                        result.success(createSuccessResponse("Logout successful"));
                    } else {
                        result.error("LOGOUT_ERROR", "Failed to logout", null);
                    }
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("LOGOUT_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    // ===========================
    // METHOD HANDLERS - Chat Operations
    // ===========================
    
    private void handleInitializeChat(MethodCall call, MethodChannel.Result result) {
        String recipientId = call.argument("recipientId");
        
        if (recipientId == null) {
            result.error("INVALID_ARGUMENTS", "Recipient ID is required", null);
            return;
        }
        
        backgroundService.initializeChat(recipientId)
            .thenAccept(success -> {
                mainHandler.post(() -> {
                    if (success) {
                        result.success(createSuccessResponse("Chat initialized successfully"));
                    } else {
                        result.error("CHAT_INIT_ERROR", "Failed to initialize chat", null);
                    }
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("CHAT_INIT_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleSendMessage(MethodCall call, MethodChannel.Result result) {
        String chatId = call.argument("chatId");
        String messageText = call.argument("messageText");
        
        if (chatId == null || messageText == null) {
            result.error("INVALID_ARGUMENTS", "Chat ID and message text are required", null);
            return;
        }
        
        backgroundService.sendMessage(chatId, messageText)
            .thenAccept(success -> {
                mainHandler.post(() -> {
                    if (success) {
                        result.success(createSuccessResponse("Message sent successfully"));
                    } else {
                        result.error("SEND_MESSAGE_ERROR", "Failed to send message", null);
                    }
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("SEND_MESSAGE_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleSendFile(MethodCall call, MethodChannel.Result result) {
        String chatId = call.argument("chatId");
        String filePath = call.argument("filePath");
        
        if (chatId == null || filePath == null) {
            result.error("INVALID_ARGUMENTS", "Chat ID and file path are required", null);
            return;
        }
        
        backgroundService.sendFile(chatId, filePath)
            .thenAccept(success -> {
                mainHandler.post(() -> {
                    if (success) {
                        result.success(createSuccessResponse("File sent successfully"));
                    } else {
                        result.error("SEND_FILE_ERROR", "Failed to send file", null);
                    }
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("SEND_FILE_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleGetChatList(MethodCall call, MethodChannel.Result result) {
        try {
            List<Chat> chats = backgroundService.getDatabaseManager().getAllChats();
            List<Map<String, Object>> chatList = new ArrayList<>();
            
            for (Chat chat : chats) {
                Map<String, Object> chatMap = new HashMap<>();
                chatMap.put("id", chat.getId());
                chatMap.put("name", chat.getName());
                chatMap.put("lastActivity", chat.getLastActivity());
                chatMap.put("keyEstablishmentStatus", chat.getKeyEstablishmentStatus());
                chatMap.put("fingerprint", chat.getFingerprint());
                chatMap.put("isReadyForMessaging", chat.isReadyForMessaging());
                chatList.add(chatMap);
            }
            
            Map<String, Object> response = createSuccessResponse("Chat list retrieved");
            response.put("chats", chatList);
            result.success(response);
            
        } catch (Exception e) {
            result.error("CHAT_LIST_ERROR", "Failed to get chat list", null);
        }
    }
    
    private void handleGetChatInfo(MethodCall call, MethodChannel.Result result) {
        String chatId = call.argument("chatId");
        
        if (chatId == null) {
            result.error("INVALID_ARGUMENTS", "Chat ID is required", null);
            return;
        }
        
        try {
            Chat chat = backgroundService.getDatabaseManager().getChat(chatId);
            if (chat != null) {
                Map<String, Object> chatMap = new HashMap<>();
                chatMap.put("id", chat.getId());
                chatMap.put("name", chat.getName());
                chatMap.put("lastActivity", chat.getLastActivity());
                chatMap.put("createdAt", chat.getCreatedAt());
                chatMap.put("keyEstablishmentStatus", chat.getKeyEstablishmentStatus());
                chatMap.put("fingerprint", chat.getFingerprint());
                chatMap.put("isReadyForMessaging", chat.isReadyForMessaging());
                
                Map<String, Object> response = createSuccessResponse("Chat info retrieved");
                response.put("chat", chatMap);
                result.success(response);
            } else {
                result.error("CHAT_NOT_FOUND", "Chat not found", null);
            }
        } catch (Exception e) {
            result.error("CHAT_INFO_ERROR", "Failed to get chat info", null);
        }
    }
    
    private void handleDeleteChat(MethodCall call, MethodChannel.Result result) {
        String chatId = call.argument("chatId");
        
        if (chatId == null) {
            result.error("INVALID_ARGUMENTS", "Chat ID is required", null);
            return;
        }
        
        try {
            boolean success = backgroundService.getDatabaseManager().deleteChat(chatId);
            if (success) {
                result.success(createSuccessResponse("Chat deleted successfully"));
            } else {
                result.error("DELETE_CHAT_ERROR", "Failed to delete chat", null);
            }
        } catch (Exception e) {
            result.error("DELETE_CHAT_ERROR", "Failed to delete chat", null);
        }
    }
    
    private void handleClearChatHistory(MethodCall call, MethodChannel.Result result) {
        String chatId = call.argument("chatId");
        
        if (chatId == null) {
            result.error("INVALID_ARGUMENTS", "Chat ID is required", null);
            return;
        }
        
        try {
            boolean success = backgroundService.getDatabaseManager().clearChatMessages(chatId);
            if (success) {
                result.success(createSuccessResponse("Chat history cleared successfully"));
            } else {
                result.error("CLEAR_CHAT_ERROR", "Failed to clear chat history", null);
            }
        } catch (Exception e) {
            result.error("CLEAR_CHAT_ERROR", "Failed to clear chat history", null);
        }
    }
    
    // ===========================
    // METHOD HANDLERS - User Management
    // ===========================
    
    private void handleSearchUsers(MethodCall call, MethodChannel.Result result) {
        String query = call.argument("query");
        
        if (query == null) {
            result.error("INVALID_ARGUMENTS", "Search query is required", null);
            return;
        }
        
        backgroundService.searchUsers(query)
            .thenAccept(users -> {
                mainHandler.post(() -> {
                    List<Map<String, Object>> userList = new ArrayList<>();
                    
                    for (User user : users) {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", user.getId());
                        userMap.put("username", user.getUsername());
                        userMap.put("status", user.getStatus() != null ? user.getStatus().name() : "OFFLINE");
                        userList.add(userMap);
                    }
                    
                    Map<String, Object> response = createSuccessResponse("Users found");
                    response.put("users", userList);
                    result.success(response);
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("SEARCH_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleGetUserProfile(MethodCall call, MethodChannel.Result result) {
        try {
            UserProfile profile = sessionManager.getUserProfile();
            if (profile != null) {
                Map<String, Object> profileMap = new HashMap<>();
                profileMap.put("id", profile.getId());
                profileMap.put("username", profile.getUsername());
                profileMap.put("email", profile.getEmail());
                profileMap.put("displayName", profile.getDisplayName());
                
                Map<String, Object> response = createSuccessResponse("User profile retrieved");
                response.put("profile", profileMap);
                result.success(response);
            } else {
                result.error("PROFILE_NOT_FOUND", "User profile not found", null);
            }
        } catch (Exception e) {
            result.error("PROFILE_ERROR", "Failed to get user profile", null);
        }
    }
    
    private void handleUpdateUserProfile(MethodCall call, MethodChannel.Result result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> profileData = call.argument("profile");
        
        if (profileData == null) {
            result.error("INVALID_ARGUMENTS", "Profile data is required", null);
            return;
        }
        
        try {
            UserProfile profile = new UserProfile();
            profile.setId((String) profileData.get("id"));
            profile.setUsername((String) profileData.get("username"));
            profile.setEmail((String) profileData.get("email"));
            profile.setDisplayName((String) profileData.get("displayName"));
            
            boolean success = sessionManager.updateUserProfile(profile);
            if (success) {
                result.success(createSuccessResponse("Profile updated successfully"));
            } else {
                result.error("UPDATE_PROFILE_ERROR", "Failed to update profile", null);
            }
        } catch (Exception e) {
            result.error("UPDATE_PROFILE_ERROR", "Failed to update profile", null);
        }
    }
    
    private void handleGetUserStatus(MethodCall call, MethodChannel.Result result) {
        String userId = call.argument("userId");
        
        if (userId == null) {
            result.error("INVALID_ARGUMENTS", "User ID is required", null);
            return;
        }
        
        backgroundService.getNetworkManager().getUserStatus(userId)
            .thenAccept(status -> {
                mainHandler.post(() -> {
                    Map<String, Object> response = createSuccessResponse("User status retrieved");
                    response.put("status", status.name());
                    result.success(response);
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("STATUS_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleSetUserStatus(MethodCall call, MethodChannel.Result result) {
        String statusStr = call.argument("status");
        
        if (statusStr == null) {
            result.error("INVALID_ARGUMENTS", "Status is required", null);
            return;
        }
        
        try {
            UserStatus status = UserStatus.valueOf(statusStr.toUpperCase());
            
            backgroundService.getNetworkManager().setUserStatus(status)
                .thenAccept(v -> {
                    mainHandler.post(() -> result.success(createSuccessResponse("Status updated successfully")));
                })
                .exceptionally(throwable -> {
                    mainHandler.post(() -> result.error("STATUS_ERROR", throwable.getMessage(), null));
                    return null;
                });
        } catch (IllegalArgumentException e) {
            result.error("INVALID_STATUS", "Invalid status: " + statusStr, null);
        }
    }
    
    // ===========================
    // METHOD HANDLERS - Settings & Configuration
    // ===========================
    
    private void handleUpdateCryptoAlgorithms(MethodCall call, MethodChannel.Result result) {
        String kemAlg = call.argument("kemAlgorithm");
        String symAlg = call.argument("symmetricAlgorithm");
        String sigAlg = call.argument("signatureAlgorithm");
        
        if (kemAlg == null || symAlg == null || sigAlg == null) {
            result.error("INVALID_ARGUMENTS", "All algorithm parameters are required", null);
            return;
        }
        
        backgroundService.updateCryptoAlgorithms(kemAlg, symAlg, sigAlg)
            .thenAccept(success -> {
                mainHandler.post(() -> {
                    if (success) {
                        result.success(createSuccessResponse("Crypto algorithms updated successfully"));
                    } else {
                        result.error("CRYPTO_UPDATE_ERROR", "Failed to update crypto algorithms", null);
                    }
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("CRYPTO_UPDATE_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleGetCryptoAlgorithms(MethodCall call, MethodChannel.Result result) {
        try {
            CryptoAlgorithms algorithms = preferencesManager.getCryptoAlgorithms();
            
            Map<String, Object> algMap = new HashMap<>();
            algMap.put("kemAlgorithm", algorithms.getKemAlgorithm());
            algMap.put("symmetricAlgorithm", algorithms.getSymmetricAlgorithm());
            algMap.put("signatureAlgorithm", algorithms.getSignatureAlgorithm());
            
            Map<String, Object> response = createSuccessResponse("Crypto algorithms retrieved");
            response.put("algorithms", algMap);
            result.success(response);
            
        } catch (Exception e) {
            result.error("CRYPTO_ERROR", "Failed to get crypto algorithms", null);
        }
    }
    
    private void handleChangeTheme(MethodCall call, MethodChannel.Result result) {
        String themeName = call.argument("theme");
        
        if (themeName == null) {
            result.error("INVALID_ARGUMENTS", "Theme name is required", null);
            return;
        }
        
        backgroundService.changeTheme(themeName)
            .thenAccept(success -> {
                mainHandler.post(() -> {
                    if (success) {
                        result.success(createSuccessResponse("Theme changed successfully"));
                    } else {
                        result.error("THEME_ERROR", "Failed to change theme", null);
                    }
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("THEME_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleGetSettings(MethodCall call, MethodChannel.Result result) {
        try {
            Map<String, Object> settings = new HashMap<>();
            
            // Get theme mode
            ThemeMode themeMode = preferencesManager.getThemeMode();
            settings.put("themeMode", themeMode.name());
            
            // Get font size
            FontSize fontSize = preferencesManager.getFontSize();
            settings.put("fontSize", fontSize.name());
            
            // Get notifications enabled
            boolean notificationsEnabled = preferencesManager.isNotificationsEnabled();
            settings.put("notificationsEnabled", notificationsEnabled);
            
            // Get first setup completed
            boolean firstSetupCompleted = preferencesManager.isFirstSetupCompleted();
            settings.put("firstSetupCompleted", firstSetupCompleted);
            
            Map<String, Object> response = createSuccessResponse("Settings retrieved");
            response.put("settings", settings);
            result.success(response);
            
        } catch (Exception e) {
            result.error("SETTINGS_ERROR", "Failed to get settings", null);
        }
    }
    
    private void handleUpdateSettings(MethodCall call, MethodChannel.Result result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = call.argument("settings");
        
        if (settings == null) {
            result.error("INVALID_ARGUMENTS", "Settings data is required", null);
            return;
        }
        
        try {
            // Update theme mode if present
            if (settings.containsKey("themeMode")) {
                String themeModeStr = (String) settings.get("themeMode");
                ThemeMode themeMode = ThemeMode.valueOf(themeModeStr.toUpperCase());
                preferencesManager.setThemeMode(themeMode);
            }
            
            // Update font size if present
            if (settings.containsKey("fontSize")) {
                String fontSizeStr = (String) settings.get("fontSize");
                FontSize fontSize = FontSize.valueOf(fontSizeStr.toUpperCase());
                preferencesManager.setFontSize(fontSize);
            }
            
            // Update notifications if present
            if (settings.containsKey("notificationsEnabled")) {
                Boolean notificationsEnabled = (Boolean) settings.get("notificationsEnabled");
                preferencesManager.setNotificationsEnabled(notificationsEnabled);
            }
            
            result.success(createSuccessResponse("Settings updated successfully"));
            
        } catch (Exception e) {
            result.error("SETTINGS_ERROR", "Failed to update settings", null);
        }
    }
    
    // ===========================
    // METHOD HANDLERS - File Operations, Contacts, Organization, Session, Utility
    // ===========================
    
    private void handleUploadFile(MethodCall call, MethodChannel.Result result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fileData = call.argument("file");
        
        if (fileData == null) {
            result.error("INVALID_ARGUMENTS", "File data is required", null);
            return;
        }
        
        try {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setId((String) fileData.get("id"));
            fileInfo.setName((String) fileData.get("name"));
            fileInfo.setPath((String) fileData.get("path"));
            fileInfo.setSize(((Number) fileData.get("size")).longValue());
            fileInfo.setMimeType((String) fileData.get("mimeType"));
            
            backgroundService.getNetworkManager().uploadFile(fileInfo)
                .thenAccept(uploadResult -> {
                    mainHandler.post(() -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", uploadResult.isSuccess());
                        response.put("message", uploadResult.getMessage());
                        response.put("fileId", uploadResult.getFileId());
                        response.put("url", uploadResult.getUrl());
                        result.success(response);
                    });
                })
                .exceptionally(throwable -> {
                    mainHandler.post(() -> result.error("UPLOAD_ERROR", throwable.getMessage(), null));
                    return null;
                });
                
        } catch (Exception e) {
            result.error("UPLOAD_ERROR", "Failed to upload file", null);
        }
    }
    
    private void handleDownloadFile(MethodCall call, MethodChannel.Result result) {
        String fileId = call.argument("fileId");
        
        if (fileId == null) {
            result.error("INVALID_ARGUMENTS", "File ID is required", null);
            return;
        }
        
        backgroundService.getNetworkManager().downloadFile(fileId)
            .thenAccept(downloadResult -> {
                mainHandler.post(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", downloadResult.isSuccess());
                    response.put("message", downloadResult.getMessage());
                    response.put("localPath", downloadResult.getLocalPath());
                    
                    if (downloadResult.getFileInfo() != null) {
                        Map<String, Object> fileMap = new HashMap<>();
                        fileMap.put("id", downloadResult.getFileInfo().getId());
                        fileMap.put("name", downloadResult.getFileInfo().getName());
                        fileMap.put("size", downloadResult.getFileInfo().getSize());
                        fileMap.put("mimeType", downloadResult.getFileInfo().getMimeType());
                        response.put("fileInfo", fileMap);
                    }
                    
                    result.success(response);
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("DOWNLOAD_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleGetFileInfo(MethodCall call, MethodChannel.Result result) {
        String fileId = call.argument("fileId");
        
        if (fileId == null) {
            result.error("INVALID_ARGUMENTS", "File ID is required", null);
            return;
        }
        
        try {
            FileInfo fileInfo = backgroundService.getDatabaseManager().getFile(fileId);
            if (fileInfo != null) {
                Map<String, Object> fileMap = new HashMap<>();
                fileMap.put("id", fileInfo.getId());
                fileMap.put("name", fileInfo.getName());
                fileMap.put("size", fileInfo.getSize());
                fileMap.put("mimeType", fileInfo.getMimeType());
                fileMap.put("path", fileInfo.getPath());
                
                Map<String, Object> response = createSuccessResponse("File info retrieved");
                response.put("fileInfo", fileMap);
                result.success(response);
            } else {
                result.error("FILE_NOT_FOUND", "File not found", null);
            }
        } catch (Exception e) {
            result.error("FILE_ERROR", "Failed to get file info", null);
        }
    }
    
    private void handleGetContacts(MethodCall call, MethodChannel.Result result) {
        try {
            List<Contact> contacts = backgroundService.getDatabaseManager().getAllContacts();
            List<Map<String, Object>> contactList = new ArrayList<>();
            
            for (Contact contact : contacts) {
                Map<String, Object> contactMap = new HashMap<>();
                contactMap.put("id", contact.getId());
                contactMap.put("name", contact.getName());
                contactMap.put("publicKey", contact.getPublicKey());
                contactList.add(contactMap);
            }
            
            Map<String, Object> response = createSuccessResponse("Contacts retrieved");
            response.put("contacts", contactList);
            result.success(response);
            
        } catch (Exception e) {
            result.error("CONTACTS_ERROR", "Failed to get contacts", null);
        }
    }
    
    private void handleAddContact(MethodCall call, MethodChannel.Result result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> contactData = call.argument("contact");
        
        if (contactData == null) {
            result.error("INVALID_ARGUMENTS", "Contact data is required", null);
            return;
        }
        
        try {
            Contact contact = new Contact();
            contact.setId((String) contactData.get("id"));
            contact.setName((String) contactData.get("name"));
            contact.setPublicKey((String) contactData.get("publicKey"));
            
            String contactId = backgroundService.getDatabaseManager().saveContact(contact);
            if (contactId != null) {
                result.success(createSuccessResponse("Contact added successfully"));
            } else {
                result.error("ADD_CONTACT_ERROR", "Failed to add contact", null);
            }
        } catch (Exception e) {
            result.error("ADD_CONTACT_ERROR", "Failed to add contact", null);
        }
    }
    
    private void handleDeleteContact(MethodCall call, MethodChannel.Result result) {
        String contactId = call.argument("contactId");
        
        if (contactId == null) {
            result.error("INVALID_ARGUMENTS", "Contact ID is required", null);
            return;
        }
        
        try {
            boolean success = backgroundService.getDatabaseManager().deleteContact(contactId);
            if (success) {
                result.success(createSuccessResponse("Contact deleted successfully"));
            } else {
                result.error("DELETE_CONTACT_ERROR", "Failed to delete contact", null);
            }
        } catch (Exception e) {
            result.error("DELETE_CONTACT_ERROR", "Failed to delete contact", null);
        }
    }
    
    private void handleGetOrganizationInfo(MethodCall call, MethodChannel.Result result) {
        backgroundService.getNetworkManager().getOrganizationInfo()
            .thenAccept(orgInfo -> {
                mainHandler.post(() -> {
                    if (orgInfo != null) {
                        Map<String, Object> orgMap = new HashMap<>();
                        orgMap.put("name", orgInfo.getName());
                        orgMap.put("id", orgInfo.getId());
                        orgMap.put("serverVersion", orgInfo.getServerVersion());
                        
                        if (orgInfo.getSupportedAlgorithms() != null) {
                            Map<String, Object> algMap = new HashMap<>();
                            algMap.put("kemAlgorithm", orgInfo.getSupportedAlgorithms().getKemAlgorithm());
                            algMap.put("symmetricAlgorithm", orgInfo.getSupportedAlgorithms().getSymmetricAlgorithm());
                            algMap.put("signatureAlgorithm", orgInfo.getSupportedAlgorithms().getSignatureAlgorithm());
                            orgMap.put("supportedAlgorithms", algMap);
                        }
                        
                        Map<String, Object> response = createSuccessResponse("Organization info retrieved");
                        response.put("organization", orgMap);
                        result.success(response);
                    } else {
                        result.error("ORG_NOT_FOUND", "Organization info not available", null);
                    }
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("ORG_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleGetServerAlgorithms(MethodCall call, MethodChannel.Result result) {
        backgroundService.getNetworkManager().getServerAlgorithms()
            .thenAccept(algorithms -> {
                mainHandler.post(() -> {
                    Map<String, Object> algMap = new HashMap<>();
                    algMap.put("kemAlgorithm", algorithms.getKemAlgorithm());
                    algMap.put("symmetricAlgorithm", algorithms.getSymmetricAlgorithm());
                    algMap.put("signatureAlgorithm", algorithms.getSignatureAlgorithm());
                    
                    Map<String, Object> response = createSuccessResponse("Server algorithms retrieved");
                    response.put("algorithms", algMap);
                    result.success(response);
                });
            })
            .exceptionally(throwable -> {
                mainHandler.post(() -> result.error("ALGORITHM_ERROR", throwable.getMessage(), null));
                return null;
            });
    }
    
    private void handleGetSessionInfo(MethodCall call, MethodChannel.Result result) {
        try {
            String sessionSummary = sessionManager.getSessionSummary();
            boolean isValid = sessionManager.isSessionValid();
            long loginTimestamp = sessionManager.getLoginTimestamp();
            String userId = sessionManager.getUserId();
            String organizationId = sessionManager.getOrganizationId();
            
            Map<String, Object> sessionMap = new HashMap<>();
            sessionMap.put("summary", sessionSummary);
            sessionMap.put("isValid", isValid);
            sessionMap.put("loginTimestamp", loginTimestamp);
            sessionMap.put("userId", userId);
            sessionMap.put("organizationId", organizationId);
            
            Map<String, Object> response = createSuccessResponse("Session info retrieved");
            response.put("session", sessionMap);
            result.success(response);
            
        } catch (Exception e) {
            result.error("SESSION_ERROR", "Failed to get session info", null);
        }
    }
    
    private void handleRefreshSession(MethodCall call, MethodChannel.Result result) {
        try {
            boolean success = sessionManager.refreshSession();
            if (success) {
                result.success(createSuccessResponse("Session refreshed successfully"));
            } else {
                result.error("REFRESH_ERROR", "Failed to refresh session", null);
            }
        } catch (Exception e) {
            result.error("REFRESH_ERROR", "Failed to refresh session", null);
        }
    }
    
    private void handleIsSessionValid(MethodCall call, MethodChannel.Result result) {
        try {
            boolean isValid = sessionManager.isSessionValid();
            Map<String, Object> response = createSuccessResponse("Session validity checked");
            response.put("isValid", isValid);
            result.success(response);
        } catch (Exception e) {
            result.error("SESSION_ERROR", "Failed to check session validity", null);
        }
    }
    
    private void handleGetAppVersion(MethodCall call, MethodChannel.Result result) {
        try {
            // Get version from package manager
            String versionName = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName;
            int versionCode = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionCode;
            
            Map<String, Object> versionMap = new HashMap<>();
            versionMap.put("versionName", versionName);
            versionMap.put("versionCode", versionCode);
            
            Map<String, Object> response = createSuccessResponse("App version retrieved");
            response.put("version", versionMap);
            result.success(response);
            
        } catch (Exception e) {
            result.error("VERSION_ERROR", "Failed to get app version", null);
        }
    }
    
    private void handleGetConnectionStatus(MethodCall call, MethodChannel.Result result) {
        try {
            ConnectionState connectionState = stateManager.getConnectionState();
            boolean isConnected = backgroundService.getNetworkManager() != null && 
                                 backgroundService.getNetworkManager().isConnected();
            
            Map<String, Object> statusMap = new HashMap<>();
            statusMap.put("state", connectionState.name());
            statusMap.put("isConnected", isConnected);
            
            Map<String, Object> response = createSuccessResponse("Connection status retrieved");
            response.put("status", statusMap);
            result.success(response);
            
        } catch (Exception e) {
            result.error("CONNECTION_ERROR", "Failed to get connection status", null);
        }
    }
    
    private void handleGetConnectionMetrics(MethodCall call, MethodChannel.Result result) {
        try {
            if (backgroundService.getNetworkManager() != null) {
                ConnectionMetrics metrics = backgroundService.getNetworkManager().getConnectionMetrics();
                
                Map<String, Object> metricsMap = new HashMap<>();
                metricsMap.put("latency", metrics.getLatency());
                metricsMap.put("bandwidth", metrics.getBandwidth());
                metricsMap.put("packetLoss", metrics.getPacketLoss());
                metricsMap.put("connectionType", metrics.getConnectionType());
                metricsMap.put("connectedTime", metrics.getConnectedTime());
                
                Map<String, Object> response = createSuccessResponse("Connection metrics retrieved");
                response.put("metrics", metricsMap);
                result.success(response);
            } else {
                result.error("NO_CONNECTION", "No active network connection", null);
            }
        } catch (Exception e) {
            result.error("METRICS_ERROR", "Failed to get connection metrics", null);
        }
    }
    
    // ===========================
    // EVENT CHANNEL IMPLEMENTATION
    // ===========================
    
    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Event stream started");
            this.eventSink = events;
            this.isEventStreamActive = true;
            
            // Send initial state
            sendInitialState();
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void onCancel(Object arguments) {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Event stream cancelled");
            this.eventSink = null;
            this.isEventStreamActive = false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Send initial state to Flutter when event stream starts
     */
    private void sendInitialState() {
        try {
            // Send current app state
            Map<String, Object> stateEvent = new HashMap<>();
            stateEvent.put("type", EVENT_APP_STATE_CHANGED);
            stateEvent.put("appState", backgroundService.getCurrentState().name());
            stateEvent.put("timestamp", System.currentTimeMillis());
            sendEventToUI(stateEvent);
            
            // Send connection state
            Map<String, Object> connectionEvent = new HashMap<>();
            connectionEvent.put("type", EVENT_CONNECTION_STATE_CHANGED);
            connectionEvent.put("connectionState", stateManager.getConnectionState().name());
            connectionEvent.put("timestamp", System.currentTimeMillis());
            sendEventToUI(connectionEvent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending initial state", e);
        }
    }
    
    // ===========================
    // PUBLIC EVENT METHODS (called by BackgroundService)
    // ===========================
    
    /**
     * Called when app state changes
     */
    public void onAppStateChanged(AppState newState) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", EVENT_APP_STATE_CHANGED);
        event.put("appState", newState.name());
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Called when connection state changes
     */
    public void onConnectionStateChanged(ConnectionState newState) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", EVENT_CONNECTION_STATE_CHANGED);
        event.put("connectionState", newState.name());
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Called when user status changes
     */
    public void onUserStatusChanged(String userId, UserStatus newStatus) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", EVENT_USER_STATUS_CHANGED);
        event.put("userId", userId);
        event.put("status", newStatus.name());
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Called when a message is received
     */
    public void onMessageReceived(Message message) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", message.getId());
        messageMap.put("chatId", message.getChatId());
        messageMap.put("content", message.getContent());
        messageMap.put("status", message.getStatus().name());
        messageMap.put("timestamp", message.getTimestamp());
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", EVENT_MESSAGE_RECEIVED);
        event.put("message", messageMap);
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Called when message status is updated
     */
    public void onMessageStatusUpdated(long messageId, MessageStatus newStatus) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", EVENT_MESSAGE_STATUS_UPDATED);
        event.put("messageId", messageId);
        event.put("status", newStatus.name());
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Show notification to user
     */
    public void showNotification(NotificationData notification) {
        Map<String, Object> notificationMap = new HashMap<>();
        notificationMap.put("id", notification.getId());
        notificationMap.put("title", notification.getTitle());
        notificationMap.put("message", notification.getMessage());
        notificationMap.put("type", notification.getType());
        notificationMap.put("extras", notification.getExtras());
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", EVENT_NOTIFICATION);
        event.put("notification", notificationMap);
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Send error event to UI
     */
    public void onErrorOccurred(AppError error) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("code", error.getCode());
        errorMap.put("message", error.getMessage());
        errorMap.put("details", error.getDetails());
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", EVENT_ERROR_OCCURRED);
        event.put("error", errorMap);
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Send progress update to UI
     */
    public void onProgressUpdate(Progress progress) {
        Map<String, Object> progressMap = new HashMap<>();
        progressMap.put("taskId", progress.getTaskId());
        progressMap.put("percentage", progress.getPercentage());
        progressMap.put("status", progress.getStatus());
        progressMap.put("bytesTransferred", progress.getBytesTransferred());
        progressMap.put("totalBytes", progress.getTotalBytes());
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", EVENT_PROGRESS_UPDATE);
        event.put("progress", progressMap);
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Generic method to send events to UI
     */
    public void sendEventToUI(String eventType, Map<String, Object> eventData) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("data", eventData);
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Send UIEvent to Flutter
     */
    public void sendEventToUI(UIEvent uiEvent) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", uiEvent.getType());
        event.put("data", uiEvent.getData());
        event.put("timestamp", System.currentTimeMillis());
        sendEventToUI(event);
    }
    
    /**
     * Send raw event map to UI
     */
    public void sendEventToUI(Map<String, Object> event) {
        if (!isEventStreamActive || eventSink == null) {
            Log.w(TAG, "Event stream not active, event dropped: " + event.get("type"));
            return;
        }
        
        mainHandler.post(() -> {
            try {
                if (eventSink != null) {
                    eventSink.success(event);
                    Log.d(TAG, "Event sent to UI: " + event.get("type"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending event to UI", e);
            }
        });
    }
    
    // ===========================
    // HELPER METHODS
    // ===========================
    
    /**
     * Create standard success response
     */
    private Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    /**
     * Create standard error response
     */
    private Map<String, Object> createErrorResponse(String message, String code) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("errorCode", code);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTrace(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Get reference to BackgroundService (for external access)
     */
    public BackgroundService getBackgroundService() {
        return backgroundService;
    }
    
    /**
     * Get reference to NetworkManager (for external access)
     */
    public NetworkManager getNetworkManager() {
        return backgroundService != null ? backgroundService.getNetworkManager() : null;
    }
    
    /**
     * Check if UIBridge is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Check if event stream is active
     */
    public boolean isEventStreamActive() {
        return isEventStreamActive;
    }
}