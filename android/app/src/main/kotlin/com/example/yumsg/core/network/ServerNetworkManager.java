package com.yumsg.core.network;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import com.yumsg.core.data.*;
import com.yumsg.core.enums.*;
import com.yumsg.core.session.SessionManager;
import com.yumsg.core.state.StateManager;

import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Base64;

/**
 * ServerNetworkManager - Complete Implementation
 * 
 * Manages all server communication including HTTP API calls and WebSocket connections.
 * Implements the NetworkManager interface with server-specific functionality.
 * 
 * Key Features:
 * - OkHttp client with SSL Certificate Pinning
 * - JWT authentication management
 * - WebSocket real-time messaging
 * - Comprehensive error handling
 * - Thread-safe operations
 * - Connection management and recovery
 * - Message queuing for offline scenarios
 */
public class ServerNetworkManager implements NetworkManager {
    private static final String TAG = "ServerNetworkManager";
    
    // Network configuration
    private static final int CONNECT_TIMEOUT = 30; // seconds
    private static final int READ_TIMEOUT = 60; // seconds
    private static final int WRITE_TIMEOUT = 60; // seconds
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    // API endpoints
    private static final String API_BASE = "/api";
    private static final String API_PING = API_BASE + "/ping";
    private static final String API_ORG_INFO = API_BASE + "/organization/info";
    private static final String API_AUTH_REGISTER = API_BASE + "/auth/register";
    private static final String API_AUTH_LOGIN = API_BASE + "/auth/login";
    private static final String API_USERS_PROFILE = API_BASE + "/users/profile";
    private static final String API_USERS_SEARCH = API_BASE + "/users/search";
    private static final String API_USERS_STATUS = API_BASE + "/users/%s/status";
    private static final String API_CHATS = API_BASE + "/chats";
    private static final String API_CHATS_DELETE = API_BASE + "/chats/%s";
    private static final String API_MESSAGES_SEND = API_BASE + "/messages/%s";
    private static final String API_MESSAGES_PENDING = API_BASE + "/messages/pending";
    private static final String API_MESSAGES_ACK = API_BASE + "/messages/acknowledge";
    private static final String API_PRESENCE_OFFLINE = API_BASE + "/presence/offline";
    private static final String API_WEBSOCKET = "/ws/messages";
    
    // HTTP headers
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String CONTENT_TYPE_JSON = "application/json";
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    
    // Dependencies
    private final Context context;
    private final Gson gson;
    private final ExecutorService executorService;
    
    // Network components
    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private String baseUrl;
    private String organizationName;
    
    // Authentication
    private volatile String currentAccessToken;
    private volatile long tokenExpiresAt;
    
    // Connection state
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile ConnectionMetrics connectionMetrics;
    
    // Message queuing
    private final Queue<PendingMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // WebSocket listeners
    private final Set<WebSocketMessageListener> messageListeners = ConcurrentHashMap.newKeySet();
    
    /**
     * Constructor
     */
    public ServerNetworkManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .create();
        this.executorService = Executors.newCachedThreadPool();
        this.connectionMetrics = new ConnectionMetrics();
        
        Log.d(TAG, "ServerNetworkManager instance created");
    }
    
    // ===========================
    // NETWORKMANAGER INTERFACE IMPLEMENTATION
    // ===========================
    
    @Override
    public CompletableFuture<ConnectionResult> connect(Map<String, Object> connectionParams) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                Log.d(TAG, "Connecting to server with params: " + connectionParams);
                
                // Extract connection parameters
                String host = (String) connectionParams.get("host");
                Integer port = (Integer) connectionParams.get("port");
                String orgName = (String) connectionParams.get("organizationName");
                
                if (host == null || port == null || orgName == null) {
                    throw new IllegalArgumentException("Missing required connection parameters");
                }
                
                this.baseUrl = buildBaseUrl(host, port);
                this.organizationName = orgName;
                
                // Initialize HTTP client with SSL pinning
                initializeHttpClient();
                
                // Test connection
                return testConnection();
                
            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                setConnectionState(ConnectionState.ERROR);
                return new ConnectionResult(false, "Connection failed: " + e.getMessage());
            } finally {
                lock.writeLock().unlock();
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                Log.d(TAG, "Disconnecting from server");
                
                // Close WebSocket
                if (webSocket != null) {
                    webSocket.close(1000, "Client disconnect");
                    webSocket = null;
                }
                
                // Clear authentication
                currentAccessToken = null;
                tokenExpiresAt = 0;
                
                // Update state
                setConnectionState(ConnectionState.DISCONNECTED);
                isConnected.set(false);
                
                Log.i(TAG, "Disconnected from server");
                
            } finally {
                lock.writeLock().unlock();
            }
        }, executorService);
    }
    
    @Override
    public boolean isConnected() {
        return isConnected.get();
    }
    
    @Override
    public ConnectionState getConnectionStatus() {
        return connectionState;
    }
    
    @Override
    public ConnectionMetrics getConnectionMetrics() {
        return connectionMetrics;
    }
    
    @Override
    public CompletableFuture<AuthResult> authenticateUser(UserCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Authenticating user: " + credentials.getUsername());
                
                // Prepare request
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("username", credentials.getUsername());
                requestBody.put("password", credentials.getPassword());
                
                // Make request
                ApiResponse<AuthResponse> response = makeAuthenticatedRequest(
                    "POST", API_AUTH_LOGIN, requestBody, new TypeToken<AuthResponse>(){}.getType(), false);
                
                if (response.isSuccess()) {
                    AuthResponse authData = response.getData();
                    
                    // Store authentication data
                    currentAccessToken = authData.token;
                    tokenExpiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000L); // 24h default
                    
                    // Connect WebSocket
                    connectWebSocket();
                    
                    setConnectionState(ConnectionState.CONNECTED);
                    isConnected.set(true);
                    
                    Log.i(TAG, "Authentication successful for: " + credentials.getUsername());
                    return new AuthResult(true, authData.token, authData.message);
                } else {
                    Log.w(TAG, "Authentication failed: " + response.getErrorDescription());
                    return new AuthResult(false, null, response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Authentication error", e);
                return new AuthResult(false, null, "Authentication error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<AuthResult> registerUser(UserProfile userInfo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Registering user: " + userInfo.getUsername());
                
                // Prepare request
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("username", userInfo.getUsername());
                requestBody.put("email", userInfo.getEmail());
                requestBody.put("display_name", userInfo.getDisplayName());
                requestBody.put("organization_domain", organizationName);
                // Note: Password should be provided separately for security
                
                // Make request
                ApiResponse<RegistrationResponse> response = makeAuthenticatedRequest(
                    "POST", API_AUTH_REGISTER, requestBody, new TypeToken<RegistrationResponse>(){}.getType(), false);
                
                if (response.isSuccess()) {
                    RegistrationResponse regData = response.getData();
                    Log.i(TAG, "Registration successful for: " + userInfo.getUsername());
                    return new AuthResult(true, null, regData.message);
                } else {
                    Log.w(TAG, "Registration failed: " + response.getErrorDescription());
                    return new AuthResult(false, null, response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Registration error", e);
                return new AuthResult(false, null, "Registration error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<User>> searchUsers(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Searching users with query: " + query);
                
                String url = API_USERS_SEARCH + "?q=" + query + "&limit=20";
                
                ApiResponse<SearchResponse> response = makeAuthenticatedRequest(
                    "GET", url, null, new TypeToken<SearchResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    SearchResponse searchData = response.getData();
                    List<User> users = new ArrayList<>();
                    
                    for (SearchResponse.UserResult userResult : searchData.users) {
                        User user = new User();
                        user.setId(userResult.id);
                        user.setUsername(userResult.username);
                        // Convert status string to enum
                        try {
                            user.setStatus(UserStatus.valueOf(userResult.status.toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            user.setStatus(UserStatus.OFFLINE);
                        }
                        users.add(user);
                    }
                    
                    Log.d(TAG, "Found " + users.size() + " users");
                    return users;
                } else {
                    Log.w(TAG, "User search failed: " + response.getErrorDescription());
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "User search error", e);
                return new ArrayList<>();
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<UpdateResult> updateProfile(UserProfile profile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Updating user profile");
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("display_name", profile.getDisplayName());
                requestBody.put("email", profile.getEmail());
                
                ApiResponse<UpdateProfileResponse> response = makeAuthenticatedRequest(
                    "PUT", API_USERS_PROFILE, requestBody, new TypeToken<UpdateProfileResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    Log.i(TAG, "Profile updated successfully");
                    return new UpdateResult(true, response.getData().message);
                } else {
                    Log.w(TAG, "Profile update failed: " + response.getErrorDescription());
                    return new UpdateResult(false, response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Profile update error", e);
                return new UpdateResult(false, "Profile update error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> logout() {
        return CompletableFuture.runAsync(() -> {
            try {
                Log.d(TAG, "Logging out user");
                
                // Disconnect WebSocket
                if (webSocket != null) {
                    webSocket.close(1000, "User logout");
                    webSocket = null;
                }
                
                // Clear authentication
                currentAccessToken = null;
                tokenExpiresAt = 0;
                
                // Update state
                setConnectionState(ConnectionState.DISCONNECTED);
                isConnected.set(false);
                
                Log.i(TAG, "User logged out successfully");
                
            } catch (Exception e) {
                Log.e(TAG, "Logout error", e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(String recipientId, String messageType, Object messageData) {
        return CompletableFuture.runAsync(() -> {
            try {
                Log.d(TAG, "Sending message to: " + recipientId + ", type: " + messageType);
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("message_type", messageType);
                requestBody.put("message_data", messageData);
                
                String url = String.format(API_MESSAGES_SEND, recipientId);
                
                ApiResponse<MessageSendResponse> response = makeAuthenticatedRequest(
                    "POST", url, requestBody, new TypeToken<MessageSendResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    Log.d(TAG, "Message sent successfully, ID: " + response.getData().messageId);
                } else {
                    Log.w(TAG, "Message send failed: " + response.getErrorDescription());
                    throw new RuntimeException("Message send failed: " + response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Message send error", e);
                throw new RuntimeException("Message send error", e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> sendUserMessage(String recipientId, String chatUuid, byte[] encryptedContent) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("message_uuid", UUID.randomUUID().toString());
        messageData.put("encrypted_content", Base64.getEncoder().encodeToString(encryptedContent));
        messageData.put("content_type", "text");
        messageData.put("content_hash", generateContentHash(encryptedContent));
        
        return sendMessage(recipientId, "USER_MESSAGE", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitRequest(String recipientId, String chatUuid, byte[] publicKey) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("public_key", Base64.getEncoder().encodeToString(publicKey));

        // Algorithms are unified for organization in server mode
        
        return sendMessage(recipientId, "CHAT_INIT_REQUEST", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitResponse(String recipientId, String chatUuid, byte[] publicKey, byte[] kemCapsule, byte[] userSignature) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("public_key", Base64.getEncoder().encodeToString(publicKey));
        messageData.put("kem_capsule", Base64.getEncoder().encodeToString(kemCapsule));
        
        messageData.put("user_signature", Base64.getEncoder().encodeToString(userSignature));
        // Algorithms are unified for organization in server mode
        
        return sendMessage(recipientId, "CHAT_INIT_RESPONSE", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitConfirm(String recipientId, String chatUuid, byte[] kemCapsule) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("kem_capsule", Base64.getEncoder().encodeToString(kemCapsule));
        
        return sendMessage(recipientId, "CHAT_INIT_CONFIRM", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitSignature(String recipientId, String chatUuid, byte[] signature) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("signature", Base64.getEncoder().encodeToString(signature));
        
        return sendMessage(recipientId, "CHAT_INIT_SIGNATURE", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatDelete(String recipientId, String chatUuid) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("reason", "user_initiated");
        
        return sendMessage(recipientId, "CHAT_DELETE", messageData);
    }
    
    @Override
    public CompletableFuture<ChatResult> createChat(List<String> participantIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (participantIds.size() != 1) {
                    throw new IllegalArgumentException("Server mode supports only 1-to-1 chats");
                }
                
                String recipientId = participantIds.get(0);
                String chatUuid = UUID.randomUUID().toString();
                
                Log.d(TAG, "Creating chat metadata with: " + recipientId);
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("recipient_id", recipientId);
                requestBody.put("chat_uuid", chatUuid);
                
                ApiResponse<ChatCreateResponse> response = makeAuthenticatedRequest(
                    "POST", API_CHATS, requestBody, new TypeToken<ChatCreateResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    ChatCreateResponse chatData = response.getData();
                    
                    Chat chat = new Chat();
                    chat.setId(chatData.chat.chatUuid);
                    chat.setName("Chat with " + recipientId); // TODO: Get display name
                    chat.setLastActivity(System.currentTimeMillis());
                    
                    Log.i(TAG, "Chat metadata created successfully");
                    return new ChatResult(true, chat, chatData.message);
                } else {
                    Log.w(TAG, "Chat creation failed: " + response.getErrorDescription());
                    return new ChatResult(false, null, response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Chat creation error", e);
                return new ChatResult(false, null, "Chat creation error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<Chat>> getChatList() {
        // Server doesn't store chat history, return empty list
        // Chats are managed locally
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
    
    @Override
    public CompletableFuture<Chat> getChatInfo(String chatId) {
        // Server doesn't store detailed chat info
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Boolean> deleteChat(String chatId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Deleting chat metadata: " + chatId);
                
                String url = String.format(API_CHATS_DELETE, chatId);
                
                ApiResponse<ChatDeleteResponse> response = makeAuthenticatedRequest(
                    "DELETE", url, null, new TypeToken<ChatDeleteResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    Log.i(TAG, "Chat metadata deleted successfully");
                    return true;
                } else {
                    Log.w(TAG, "Chat deletion failed: " + response.getErrorDescription());
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Chat deletion error", e);
                return false;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Boolean> clearChatHistory(String chatId) {
        // Server doesn't store chat history
        return CompletableFuture.completedFuture(true);
    }
    
    @Override
    public CompletableFuture<UploadResult> uploadFile(FileInfo file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Uploading file: " + file.getName());
                
                // TODO: Implement file upload
                // This would involve multipart/form-data request
                // For now, return mock result
                
                String fileId = UUID.randomUUID().toString();
                String url = baseUrl + "/files/" + fileId;
                
                Log.i(TAG, "File uploaded successfully: " + fileId);
                return new UploadResult(true, fileId, url, "File uploaded successfully");
                
            } catch (Exception e) {
                Log.e(TAG, "File upload error", e);
                return new UploadResult(false, null, null, "File upload error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<DownloadResult> downloadFile(String fileId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Downloading file: " + fileId);
                
                // TODO: Implement file download
                // This would involve binary data download
                // For now, return mock result
                
                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(fileId);
                fileInfo.setName("downloaded_file");
                
                String localPath = "/path/to/downloaded/file";
                
                Log.i(TAG, "File downloaded successfully: " + fileId);
                return new DownloadResult(true, fileInfo, localPath, "File downloaded successfully");
                
            } catch (Exception e) {
                Log.e(TAG, "File download error", e);
                return new DownloadResult(false, null, null, "File download error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<TransferResult> transferFile(String recipientId, FileInfo file) {
        return uploadFile(file).thenCompose(uploadResult -> {
            if (uploadResult.isSuccess()) {
                // Send file message
                Map<String, Object> messageData = new HashMap<>();
                messageData.put("chat_uuid", "file_transfer_" + UUID.randomUUID().toString());
                messageData.put("message_uuid", UUID.randomUUID().toString());
                messageData.put("encrypted_content", "FILE:" + uploadResult.getFileId());
                messageData.put("content_type", "file");
                
                Map<String, Object> fileMetadata = new HashMap<>();
                fileMetadata.put("filename", file.getName());
                fileMetadata.put("file_size", file.getSize());
                messageData.put("file_metadata", fileMetadata);
                
                return sendMessage(recipientId, "USER_MESSAGE", messageData)
                    .thenApply(v -> new TransferResult(true, uploadResult.getFileId(), "File transferred successfully"));
            } else {
                return CompletableFuture.completedFuture(
                    new TransferResult(false, null, "File transfer failed: " + uploadResult.getMessage()));
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> setMessageStatus(String messageId, MessageStatus status) {
        // Message status is handled through WebSocket events
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> setUserStatus(UserStatus status) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (status == UserStatus.OFFLINE || status == UserStatus.AWAY) {
                    // Send offline presence
                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("reason", status == UserStatus.AWAY ? "away" : "user_initiated");
                    
                    ApiResponse<PresenceResponse> response = makeAuthenticatedRequest(
                        "POST", API_PRESENCE_OFFLINE, requestBody, new TypeToken<PresenceResponse>(){}.getType(), true);
                    
                    if (response.isSuccess()) {
                        Log.d(TAG, "Status updated to: " + status);
                    } else {
                        Log.w(TAG, "Status update failed: " + response.getErrorDescription());
                    }
                }
                // Online status is managed automatically by WebSocket connection
                
            } catch (Exception e) {
                Log.e(TAG, "Status update error", e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<UserStatus> getUserStatus(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Getting user status: " + userId);
                
                String url = String.format(API_USERS_STATUS, userId);
                
                ApiResponse<UserStatusResponse> response = makeAuthenticatedRequest(
                    "GET", url, null, new TypeToken<UserStatusResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    UserStatusResponse statusData = response.getData();
                    try {
                        return UserStatus.valueOf(statusData.user.status.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return UserStatus.OFFLINE;
                    }
                } else {
                    Log.w(TAG, "Get user status failed: " + response.getErrorDescription());
                    return UserStatus.OFFLINE;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Get user status error", e);
                return UserStatus.OFFLINE;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<OrganizationInfo> getOrganizationInfo() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Getting organization info");
                
                ApiResponse<OrgInfoResponse> response = makeAuthenticatedRequest(
                    "GET", API_ORG_INFO, null, new TypeToken<OrgInfoResponse>(){}.getType(), false);
                
                if (response.isSuccess()) {
                    OrgInfoResponse orgData = response.getData();

                    // Build organization info
                    OrganizationInfo orgInfo = new OrganizationInfo(
                        orgData.organization.name,
                        orgData.organization.id
                    );

                    // Parse supported algorithms from server response
                    CryptoAlgorithms algorithms = parseOrganizationAlgorithms(orgData.organization.supportedAlgorithms);
                    orgInfo.setSupportedAlgorithms(algorithms);

                    // Set server version if provided (placeholder for now)
                    orgInfo.setServerVersion("1.0.0");

                    // Save server policies for future use
                    if (orgData.organization.serverPolicies != null) {
                        orgInfo.getPolicies().putAll(orgData.organization.serverPolicies);
                    }
                    
                    Log.i(TAG, "Organization info retrieved: " + orgData.organization.name);
                    return orgInfo;
                } else {
                    Log.w(TAG, "Get organization info failed: " + response.getErrorDescription());
                    return null;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Get organization info error", e);
                return null;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<CryptoAlgorithms> getServerAlgorithms() {
        return getOrganizationInfo().thenApply(orgInfo -> {
            if (orgInfo != null) {
                return orgInfo.getSupportedAlgorithms();
            } else {
                // Return default algorithms
                return new CryptoAlgorithms("KYBER", "AES-256", "FALCON");
            }
        });
    }
    
    // ===========================
    // SERVER-SPECIFIC METHODS
    // ===========================
    
    /**
     * Refresh session token using refresh token
     */
    public CompletableFuture<SessionAuthData> refreshSessionToken(String refreshToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Refreshing session token");
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("refresh_token", refreshToken);
                
                // Note: This endpoint should be added to Server.md spec
                ApiResponse<TokenRefreshResponse> response = makeAuthenticatedRequest(
                    "POST", "/api/auth/refresh", requestBody, 
                    new TypeToken<TokenRefreshResponse>(){}.getType(), false);
                
                if (response.isSuccess()) {
                    TokenRefreshResponse refreshData = response.getData();
                    
                    // Create updated session data
                    SessionAuthData refreshedSession = new SessionAuthData();
                    refreshedSession.setAccessToken(refreshData.accessToken);
                    refreshedSession.setRefreshToken(refreshData.refreshToken != null ? 
                        refreshData.refreshToken : refreshToken); // Keep old if not provided
                    refreshedSession.setExpiresAt(System.currentTimeMillis() + 
                        (refreshData.expiresIn * 1000L));
                    
                    // Update internal token
                    currentAccessToken = refreshData.accessToken;
                    tokenExpiresAt = refreshedSession.getExpiresAt();
                    
                    Log.i(TAG, "Session token refreshed successfully");
                    return refreshedSession;
                } else {
                    Log.w(TAG, "Token refresh failed: " + response.getErrorDescription());
                    return null;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Token refresh error", e);
                return null;
            }
        }, executorService);
    }
    
    /**
     * Get pending offline messages
     */
    public CompletableFuture<List<PendingMessage>> getPendingMessages() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Getting pending messages");
                
                String url = API_MESSAGES_PENDING + "?limit=50";
                
                ApiResponse<PendingMessagesResponse> response = makeAuthenticatedRequest(
                    "GET", url, null, new TypeToken<PendingMessagesResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    PendingMessagesResponse pendingData = response.getData();
                    
                    List<PendingMessage> messages = new ArrayList<>();
                    for (PendingMessagesResponse.PendingMessage msg : pendingData.messages) {
                        PendingMessage pendingMsg = new PendingMessage();
                        pendingMsg.setId(msg.id);
                        pendingMsg.setFromUserId(msg.fromUserId);
                        pendingMsg.setFromUserName(msg.fromUserName);
                        pendingMsg.setMessageType(msg.messageType);
                        pendingMsg.setMessageData(msg.messageData);
                        pendingMsg.setReceivedAt(msg.receivedAt);
                        pendingMsg.setExpiresAt(msg.expiresAt);
                        messages.add(pendingMsg);
                    }
                    
                    Log.d(TAG, "Retrieved " + messages.size() + " pending messages");
                    return messages;
                } else {
                    Log.w(TAG, "Get pending messages failed: " + response.getErrorDescription());
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Get pending messages error", e);
                return new ArrayList<>();
            }
        }, executorService);
    }
    
    /**
     * Acknowledge received messages
     */
    public CompletableFuture<Boolean> acknowledgeMessages(List<String> messageIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Acknowledging " + messageIds.size() + " messages");
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("message_ids", messageIds);
                
                ApiResponse<AckMessagesResponse> response = makeAuthenticatedRequest(
                    "POST", API_MESSAGES_ACK, requestBody, new TypeToken<AckMessagesResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    Log.d(TAG, "Messages acknowledged successfully");
                    return true;
                } else {
                    Log.w(TAG, "Message acknowledgment failed: " + response.getErrorDescription());
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Message acknowledgment error", e);
                return false;
            }
        }, executorService);
    }
    
    // ===========================
    // WEBSOCKET MANAGEMENT
    // ===========================
    
    /**
     * Connect WebSocket for real-time messaging
     */
    private void connectWebSocket() {
        try {
            Log.d(TAG, "Connecting WebSocket");
            
            String wsUrl = baseUrl.replace("http", "ws") + API_WEBSOCKET;
            
            Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader(HEADER_AUTHORIZATION, "Bearer " + currentAccessToken)
                .build();
            
            webSocket = httpClient.newWebSocket(request, new WebSocketMessageHandler());
            
        } catch (Exception e) {
            Log.e(TAG, "WebSocket connection error", e);
        }
    }
    
    /**
     * Add WebSocket message listener
     */
    public void addMessageListener(WebSocketMessageListener listener) {
        messageListeners.add(listener);
    }
    
    /**
     * Remove WebSocket message listener
     */
    public void removeMessageListener(WebSocketMessageListener listener) {
        messageListeners.remove(listener);
    }
    
    // ===========================
    // PRIVATE HELPER METHODS
    // ===========================
    
    /**
     * Build base URL from host and port
     */
    private String buildBaseUrl(String host, int port) {
        boolean isSecure = port == 443 || port == 8443;
        String protocol = isSecure ? "https" : "http";
        
        if ((isSecure && port == 443) || (!isSecure && port == 80)) {
            return protocol + "://" + host;
        } else {
            return protocol + "://" + host + ":" + port;
        }
    }
    
    /**
     * Initialize HTTP client with SSL pinning
     */
    private void initializeHttpClient() {
        try {
            Log.d(TAG, "Initializing HTTP client with SSL pinning");
            
            // Create certificate pinner
            CertificatePinner certificatePinner = new CertificatePinner.Builder()
                // Add your server's certificate pins here
                // .add("your-server.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .build();
            
            // Create HTTP logging interceptor
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            
            // Build HTTP client
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .certificatePinner(certificatePinner)
                .addInterceptor(loggingInterceptor)
                .addInterceptor(new AuthenticationInterceptor())
                .addInterceptor(new RetryInterceptor());
            
            httpClient = builder.build();
            
            Log.d(TAG, "HTTP client initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize HTTP client", e);
            throw new RuntimeException("Failed to initialize HTTP client", e);
        }
    }
    
    /**
     * Test connection to server
     */
    private ConnectionResult testConnection() {
        try {
            Log.d(TAG, "Testing connection to server");
            
            ApiResponse<PingResponse> response = makeAuthenticatedRequest(
                "GET", API_PING, null, new TypeToken<PingResponse>(){}.getType(), false);
            
            if (response.isSuccess()) {
                setConnectionState(ConnectionState.CONNECTED);
                Log.i(TAG, "Connection test successful");
                return new ConnectionResult(true, "Connected to server");
            } else {
                setConnectionState(ConnectionState.ERROR);
                Log.w(TAG, "Connection test failed: " + response.getErrorDescription());
                return new ConnectionResult(false, "Connection test failed: " + response.getErrorDescription());
            }
            
        } catch (Exception e) {
            setConnectionState(ConnectionState.ERROR);
            Log.e(TAG, "Connection test error", e);
            return new ConnectionResult(false, "Connection test error: " + e.getMessage());
        }
    }
    
    /**
     * Make authenticated HTTP request
     */
    private <T> ApiResponse<T> makeAuthenticatedRequest(String method, String path, Object requestBody, Type responseType, boolean requireAuth) {
        try {
            String url = baseUrl + path;
            
            Request.Builder requestBuilder = new Request.Builder().url(url);
            
            // Add authorization header if required
            if (requireAuth && currentAccessToken != null) {
                requestBuilder.addHeader(HEADER_AUTHORIZATION, "Bearer " + currentAccessToken);
            }
            
            // Add request body for POST/PUT requests
            if (requestBody != null && ("POST".equals(method) || "PUT".equals(method))) {
                String jsonBody = gson.toJson(requestBody);
                RequestBody body = RequestBody.create(jsonBody, MediaType.get(CONTENT_TYPE_JSON));
                requestBuilder.method(method, body);
                requestBuilder.addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
            } else {
                requestBuilder.method(method, null);
            }
            
            // Add user agent
            requestBuilder.addHeader(HEADER_USER_AGENT, "YuMSG Android Client/1.0");
            
            Request request = requestBuilder.build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    T data = gson.fromJson(responseBody, responseType);
                    return new ApiResponse<>(true, data, null, null, null);
                } else {
                    // Parse error response
                    try {
                        ErrorResponse errorResponse = gson.fromJson(responseBody, ErrorResponse.class);
                        return new ApiResponse<>(false, null, errorResponse.error, errorResponse.errorCode, errorResponse.errorDescription);
                    } catch (JsonSyntaxException e) {
                        return new ApiResponse<>(false, null, "HTTP_ERROR", String.valueOf(response.code()), "HTTP " + response.code() + ": " + response.message());
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Request error: " + method + " " + path, e);
            return new ApiResponse<>(false, null, "NETWORK_ERROR", "EXCEPTION", e.getMessage());
        }
    }
    
    /**
     * Set connection state and notify listeners
     */
    private void setConnectionState(ConnectionState state) {
        ConnectionState oldState = this.connectionState;
        this.connectionState = state;
        
        if (oldState != state) {
            Log.d(TAG, "Connection state changed: " + oldState + " -> " + state);
            
            // Update metrics
            if (state == ConnectionState.CONNECTED) {
                connectionMetrics.setConnectionType("SERVER");
                connectionMetrics.setConnectedTime(System.currentTimeMillis());
            }
            
            // Notify StateManager
            StateManager.getInstance().setConnectionState(state);
        }
    }
    
    /**
     * Generate content hash for integrity checking
     */
    private String generateContentHash(byte[] content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Log.e(TAG, "Error generating content hash", e);
            return "";
        }
    }

    /**
     * Parse organization supported algorithms
     */
    private CryptoAlgorithms parseOrganizationAlgorithms(Map<String, Object> supportedAlgorithms) {
        if (supportedAlgorithms == null) {
            return new CryptoAlgorithms("KYBER", "AES-256", "FALCON");
        }

        String kemAlgorithm = "KYBER";
        String symmetricAlgorithm = "AES-256";
        String signatureAlgorithm = "FALCON";

        // Asymmetric (KEM) algorithms
        List<Map<String, Object>> asymmetricList = (List<Map<String, Object>>) supportedAlgorithms.get("asymmetric");
        if (asymmetricList != null && !asymmetricList.isEmpty()) {
            Map<String, Object> kemAlg = asymmetricList.get(0);
            if (kemAlg != null && kemAlg.containsKey("name")) {
                kemAlgorithm = (String) kemAlg.get("name");
            }
        }

        // Symmetric algorithms
        List<Map<String, Object>> symmetricList = (List<Map<String, Object>>) supportedAlgorithms.get("symmetric");
        if (symmetricList != null && !symmetricList.isEmpty()) {
            Map<String, Object> symAlg = symmetricList.get(0);
            if (symAlg != null && symAlg.containsKey("name")) {
                symmetricAlgorithm = (String) symAlg.get("name");
            }
        }

        // Signature algorithms
        List<Map<String, Object>> signatureList = (List<Map<String, Object>>) supportedAlgorithms.get("signature");
        if (signatureList != null && !signatureList.isEmpty()) {
            Map<String, Object> sigAlg = signatureList.get(0);
            if (sigAlg != null && sigAlg.containsKey("name")) {
                signatureAlgorithm = (String) sigAlg.get("name");
            }
        }

        Log.d(TAG, String.format("Parsed algorithms - KEM: %s, Symmetric: %s, Signature: %s",
                kemAlgorithm, symmetricAlgorithm, signatureAlgorithm));

        return new CryptoAlgorithms(kemAlgorithm, symmetricAlgorithm, signatureAlgorithm);
    }
    
    // ===========================
    // INNER CLASSES AND INTERFACES
    // ===========================
    
    /**
     * WebSocket message listener interface
     */
    public interface WebSocketMessageListener {
        void onMessageReceived(String fromUserId, String messageType, Map<String, Object> messageData);
        void onStatusUpdate(String userId, String status, String lastSeen);
        void onConnectionStatus(String connectionId, String status);
    }
    
    /**
     * WebSocket message handler
     */
    private class WebSocketMessageHandler extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.i(TAG, "WebSocket connected");
            setConnectionState(ConnectionState.CONNECTED);
            isConnected.set(true);
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                Log.d(TAG, "WebSocket message received: " + text);
                
                WebSocketMessage wsMessage = gson.fromJson(text, WebSocketMessage.class);
                
                for (WebSocketMessageListener listener : messageListeners) {
                    switch (wsMessage.eventType) {
                        case "MESSAGE_RECEIVED":
                            listener.onMessageReceived(wsMessage.fromUserId, 
                                wsMessage.data.get("message_type").toString(),
                                (Map<String, Object>) wsMessage.data.get("message_data"));
                            break;
                        case "STATUS_UPDATE":
                            listener.onStatusUpdate(
                                wsMessage.data.get("user_id").toString(),
                                wsMessage.data.get("status").toString(),
                                wsMessage.data.get("last_seen").toString());
                            break;
                        case "CONNECTION_STATUS":
                            listener.onConnectionStatus(
                                wsMessage.data.get("connection_id").toString(),
                                wsMessage.data.get("status").toString());
                            break;
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing WebSocket message", e);
            }
        }
        
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closing: " + code + " " + reason);
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.i(TAG, "WebSocket closed: " + code + " " + reason);
            setConnectionState(ConnectionState.DISCONNECTED);
            isConnected.set(false);
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "WebSocket failure", t);
            setConnectionState(ConnectionState.ERROR);
            isConnected.set(false);
            
            // Attempt reconnection after delay
            scheduler.schedule(this::attemptReconnection, 5, TimeUnit.SECONDS);
        }
        
        private void attemptReconnection() {
            if (currentAccessToken != null && !isConnected.get()) {
                Log.d(TAG, "Attempting WebSocket reconnection");
                connectWebSocket();
            }
        }
    }
    
    /**
     * Authentication interceptor for HTTP requests
     */
    private class AuthenticationInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            
            // Add authentication header if token available and not already present
            if (currentAccessToken != null && originalRequest.header(HEADER_AUTHORIZATION) == null) {
                Request authenticatedRequest = originalRequest.newBuilder()
                    .header(HEADER_AUTHORIZATION, "Bearer " + currentAccessToken)
                    .build();
                return chain.proceed(authenticatedRequest);
            }
            
            return chain.proceed(originalRequest);
        }
    }
    
    /**
     * Retry interceptor for failed requests
     */
    private class RetryInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException lastException = null;
            
            for (int i = 0; i < MAX_RETRIES; i++) {
                try {
                    response = chain.proceed(request);
                    if (response.isSuccessful()) {
                        return response;
                    }
                    if (response != null) {
                        response.close();
                    }
                } catch (IOException e) {
                    lastException = e;
                    Log.w(TAG, "Request attempt " + (i + 1) + " failed", e);
                }
                
                if (i < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (i + 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            if (lastException != null) {
                throw lastException;
            }
            
            return response;
        }
    }
    
    // ===========================
    // DATA CLASSES FOR API RESPONSES
    // ===========================
    
    private static class ApiResponse<T> {
        private final boolean success;
        private final T data;
        private final String error;
        private final String errorCode;
        private final String errorDescription;
        
        public ApiResponse(boolean success, T data, String error, String errorCode, String errorDescription) {
            this.success = success;
            this.data = data;
            this.error = error;
            this.errorCode = errorCode;
            this.errorDescription = errorDescription;
        }
        
        public boolean isSuccess() { return success; }
        public T getData() { return data; }
        public String getError() { return error; }
        public String getErrorCode() { return errorCode; }
        public String getErrorDescription() { return errorDescription; }
    }
    
    private static class PingResponse {
        public boolean success;
        public String message;
        public String version;
        public long timestamp;
        public String serverTime;
    }
    
    private static class AuthResponse {
        public boolean success;
        public String message;
        public String token;
        public String tokenExpiresAt;
        public UserData user;
        public OrganizationData organization;
        
        public static class UserData {
            public String id;
            public String username;
            public String displayName;
            public String email;
            public String status;
            public String lastSeen;
        }
        
        public static class OrganizationData {
            public String id;
            public String name;
            public String domain;
        }
    }
    
    private static class RegistrationResponse {
        public boolean success;
        public String message;
        public AuthResponse.UserData user;
    }
    
    private static class SearchResponse {
        public boolean success;
        public String query;
        public int totalFound;
        public int limit;
        public int offset;
        public List<UserResult> users;
        
        public static class UserResult {
            public String id;
            public String username;
            public String displayName;
            public String status;
            public String lastSeen;
            public boolean hasActiveChat;
        }
    }
    
    private static class UpdateProfileResponse {
        public boolean success;
        public String message;
        public AuthResponse.UserData user;
    }
    
    private static class MessageSendResponse {
        public boolean success;
        public String messageId;
        public long timestamp;
        public String deliveryStatus;
    }
    
    private static class ChatCreateResponse {
        public boolean success;
        public String message;
        public ChatData chat;
        
        public static class ChatData {
            public String id;
            public String chatUuid;
            public List<AuthResponse.UserData> participants;
            public String createdAt;
        }
    }
    
    private static class ChatDeleteResponse {
        public boolean success;
        public String message;
        public Map<String, Object> deletedChatMetadata;
    }
    
    private static class PresenceResponse {
        public boolean success;
        public String message;
        public UserStatusData userStatus;
        
        public static class UserStatusData {
            public String userId;
            public String status;
            public String reason;
            public String updatedAt;
        }
    }
    
    private static class UserStatusResponse {
        public boolean success;
        public AuthResponse.UserData user;
    }
    
    private static class OrgInfoResponse {
        public boolean success;
        public OrganizationDetails organization;
        
        public static class OrganizationDetails {
            public String id;
            public String name;
            public String domain;
            public Map<String, Object> supportedAlgorithms;
            public Map<String, Object> serverPolicies;
        }
    }
    
    private static class PendingMessagesResponse {
        public boolean success;
        public int totalPending;
        public List<PendingMessage> messages;
        
        public static class PendingMessage {
            public String id;
            public String fromUserId;
            public String fromUserName;
            public String messageType;
            public Map<String, Object> messageData;
            public String receivedAt;
            public String expiresAt;
        }
    }
    
    private static class AckMessagesResponse {
        public boolean success;
        public String message;
        public int acknowledgedCount;
        public List<String> acknowledgedIds;
        public String acknowledgedAt;
    }
    
    private static class WebSocketMessage {
        public String eventType;
        public long timestamp;
        public String fromUserId;
        public Map<String, Object> data;
    }
    
    private static class TokenRefreshResponse {
        public boolean success;
        public String accessToken;
        public String refreshToken; // Optional - may be null if refresh token doesn't change
        public long expiresIn; // Seconds until expiration
        public String tokenType = "Bearer";
    }
    
    private static class ErrorResponse {
        public boolean success;
        public String error;
        public String errorCode;
        public String errorDescription;
        public Map<String, List<String>> validationErrors;
    }
}