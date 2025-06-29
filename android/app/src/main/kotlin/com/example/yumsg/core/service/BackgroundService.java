package com.yumsg.core.service;

import android.content.Context;
import android.util.Log;

import com.yumsg.core.data.*;
import com.yumsg.core.enums.*;
import com.yumsg.core.storage.SharedPreferencesManager;
import com.yumsg.core.storage.DatabaseManager;
import com.yumsg.core.crypto.CryptoManager;
import com.yumsg.core.state.StateManager;
import com.yumsg.core.session.SessionManager;
import com.yumsg.core.network.NetworkManager;
import com.yumsg.core.network.ServerNetworkManager;
import com.yumsg.core.network.LocalNetworkManager;
import com.yumsg.core.ui.UIBridge;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BackgroundService - Complete Coordination Implementation
 * 
 * Central coordinator for all YuMSG components and business logic.
 * Manages component lifecycle, handles cross-component coordination,
 * and implements all business logic flows from interaction chains.
 * 
 * Key Responsibilities:
 * - Component initialization and lifecycle management
 * - NetworkManager selection and configuration
 * - Message processing coordination (WebSocket/P2P)
 * - Crypto protocol handling (chat initialization)
 * - Connection restoration and recovery
 * - UI event processing and response coordination
 * - Background task management
 * - Error handling and recovery coordination
 */
public class BackgroundService {
    private static final String TAG = "BackgroundService";
    
    // Singleton instance
    private static volatile BackgroundService instance;
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    
    // Dependencies
    private final Context context;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduler;
    
    // Core components
    private SharedPreferencesManager preferencesManager;
    private DatabaseManager databaseManager;
    private CryptoManager cryptoManager;
    private StateManager stateManager;
    private SessionManager sessionManager;
    private NetworkManager networkManager;
    private UIBridge uiBridge;
    
    // Message handlers
    private ServerMessageHandler serverMessageHandler;
    private P2PMessageHandler p2pMessageHandler;
    
    // Background tasks
    private final Map<String, CompletableFuture<?>> activeTasks = new ConcurrentHashMap<>();
    private final Queue<PendingOperation> pendingOperations = new ConcurrentLinkedQueue<>();
    // Temporary storage for KEM secrets during chat initialization
    private final Map<String, byte[]> pendingSecrets = new ConcurrentHashMap<>();
    
    /**
     * Private constructor for singleton pattern
     */
    private BackgroundService(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newCachedThreadPool();
        this.scheduler = Executors.newScheduledThreadPool(3);
        
        Log.d(TAG, "BackgroundService instance created");
    }
    
    /**
     * Get singleton instance
     */
    public static BackgroundService getInstance(Context context) {
        if (instance == null) {
            synchronized (BackgroundService.class) {
                if (instance == null) {
                    instance = new BackgroundService(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Get singleton instance (requires previous initialization)
     */
    public static BackgroundService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BackgroundService not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }
    
    // ===========================
    // LIFECYCLE MANAGEMENT
    // ===========================
    
    /**
     * Initialize all components in proper order
     * Implementation of initialization chain from updated_interaction_chains.md
     */
    public boolean initialize() {
        lock.writeLock().lock();
        try {
            if (isInitialized.get()) {
                Log.w(TAG, "BackgroundService already initialized");
                return true;
            }
            
            Log.i(TAG, "Initializing BackgroundService and all components");
            
            // Phase 1: Initialize foundation components
            if (!initializeFoundationComponents()) {
                Log.e(TAG, "Failed to initialize foundation components");
                return false;
            }
            
            // Phase 2: Initialize business logic components  
            if (!initializeBusinessLogicComponents()) {
                Log.e(TAG, "Failed to initialize business logic components");
                return false;
            }
            
            // Phase 3: Initialize network layer
            if (!initializeNetworkLayer()) {
                Log.e(TAG, "Failed to initialize network layer");
                return false;
            }
            
            // Phase 4: Setup cross-component coordination
            if (!setupComponentCoordination()) {
                Log.e(TAG, "Failed to setup component coordination");
                return false;
            }
            
            // Phase 5: Start background tasks
            startBackgroundTasks();
            
            isInitialized.set(true);
            Log.i(TAG, "BackgroundService initialization completed successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "BackgroundService initialization failed", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Initialize foundation components (Phase 1)
     */
    private boolean initializeFoundationComponents() {
        Log.d(TAG, "Phase 1: Initializing foundation components");
        
        try {
            // SharedPreferencesManager - Priority #1
            preferencesManager = SharedPreferencesManager.getInstance(context);
            if (!preferencesManager.initialize()) {
                Log.e(TAG, "Failed to initialize SharedPreferencesManager");
                return false;
            }
            
            // DatabaseManager - Priority #2
            String dbPath = context.getDatabasePath("yumsg.db").getAbsolutePath();
            databaseManager = DatabaseManager.getInstance(context, dbPath);
            if (!databaseManager.initialize(dbPath)) {
                Log.e(TAG, "Failed to initialize DatabaseManager");
                return false;
            }
            
            Log.d(TAG, "Foundation components initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Foundation components initialization failed", e);
            return false;
        }
    }
    
    /**
     * Initialize business logic components (Phase 2)
     */
    private boolean initializeBusinessLogicComponents() {
        Log.d(TAG, "Phase 2: Initializing business logic components");
        
        try {
            // CryptoManager - Critical path
            cryptoManager = CryptoManager.getInstance();
            if (!cryptoManager.initialize()) {
                Log.e(TAG, "Failed to initialize CryptoManager");
                return false;
            }
            
            // StateManager
            stateManager = StateManager.getInstance();
            stateManager.initialize();
            
            // SessionManager
            sessionManager = SessionManager.getInstance(context);
            sessionManager.initialize();
            
            Log.d(TAG, "Business logic components initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Business logic components initialization failed", e);
            return false;
        }
    }
    
    /**
     * Initialize network layer (Phase 3)
     */
    private boolean initializeNetworkLayer() {
        Log.d(TAG, "Phase 3: Initializing network layer");
        
        try {
            // Create appropriate NetworkManager based on app mode
            networkManager = createNetworkManager();
            
            if (networkManager == null) {
                Log.e(TAG, "Failed to create NetworkManager");
                return false;
            }
            
            // Setup message handlers
            setupMessageHandlers();
            
            Log.d(TAG, "Network layer initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Network layer initialization failed", e);
            return false;
        }
    }
    
    /**
     * Setup cross-component coordination (Phase 4)
     */
    private boolean setupComponentCoordination() {
        Log.d(TAG, "Phase 4: Setting up component coordination");
        
        try {
            // Connect SessionManager to NetworkManager for token refresh
            sessionManager.setNetworkManager(networkManager);
            
            // Setup state change observers
            stateManager.addStateObserver(new StateChangeHandler());
            
            // Initialize UIBridge (will be set later)
            // UIBridge initialization happens externally with Flutter engine
            
            Log.d(TAG, "Component coordination setup successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Component coordination setup failed", e);
            return false;
        }
    }
    
    /**
     * Create appropriate NetworkManager based on app mode
     */
    private NetworkManager createNetworkManager() {
        AppMode appMode = preferencesManager.getAppMode();
        
        if (appMode == null) {
            Log.d(TAG, "No app mode set, returning null NetworkManager");
            return null;
        }
        
        switch (appMode) {
            case SERVER:
                Log.d(TAG, "Creating ServerNetworkManager");
                return new ServerNetworkManager(context);
                
            case LOCAL:
                Log.d(TAG, "Creating LocalNetworkManager");
                return new LocalNetworkManager(context);
                
            default:
                Log.e(TAG, "Unknown app mode: " + appMode);
                return null;
        }
    }
    
    /**
     * Setup message handlers for incoming messages
     */
    private void setupMessageHandlers() {
        if (networkManager instanceof ServerNetworkManager) {
            serverMessageHandler = new ServerMessageHandler();
            ((ServerNetworkManager) networkManager).addMessageListener(serverMessageHandler);
            Log.d(TAG, "Server message handler configured");
            
        } else if (networkManager instanceof LocalNetworkManager) {
            p2pMessageHandler = new P2PMessageHandler();
            ((LocalNetworkManager) networkManager).addMessageListener(p2pMessageHandler);
            Log.d(TAG, "P2P message handler configured");
        }
    }
    
    /**
     * Start background tasks
     */
    private void startBackgroundTasks() {
        Log.d(TAG, "Starting background tasks");
        
        // Start connection health monitoring
        scheduler.scheduleAtFixedRate(new ConnectionHealthTask(), 30, 30, TimeUnit.SECONDS);
        
        // Start pending operations processor
        scheduler.scheduleAtFixedRate(new PendingOperationsTask(), 5, 5, TimeUnit.SECONDS);
        
        // Start session validation
        scheduler.scheduleAtFixedRate(new SessionValidationTask(), 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * Shutdown service and cleanup resources
     */
    public void shutdown() {
        lock.writeLock().lock();
        try {
            Log.i(TAG, "Shutting down BackgroundService");
            
            // Cancel all active tasks
            for (CompletableFuture<?> task : activeTasks.values()) {
                task.cancel(true);
            }
            activeTasks.clear();
            
            // Shutdown schedulers
            scheduler.shutdown();
            executorService.shutdown();
            
            // Cleanup components
            if (networkManager != null) {
                networkManager.disconnect();
            }
            
            if (cryptoManager != null) {
                cryptoManager.cleanup();
            }
            
            if (databaseManager != null) {
                databaseManager.close();
            }
            
            isInitialized.set(false);
            Log.i(TAG, "BackgroundService shutdown completed");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ===========================
    // BUSINESS LOGIC METHODS (from interaction chains)
    // ===========================
    
    /**
     * Select application mode
     * Chain: UIBridge.handleUIMethod("selectMode") -> BackgroundService.selectAppMode()
     */
    public CompletableFuture<Boolean> selectAppMode(AppMode mode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Selecting app mode: " + mode);
                
                // Validate mode
                if (mode == null) {
                    throw new IllegalArgumentException("App mode cannot be null");
                }
                
                // Store mode in preferences
                preferencesManager.setAppMode(mode);
                
                // Update state
                stateManager.setAppState(AppState.MODE_SELECTED);
                
                // Set default crypto algorithms
                CryptoAlgorithms defaultAlgorithms = cryptoManager.getDefaultAlgorithms();
                preferencesManager.setCryptoAlgorithms(defaultAlgorithms);
                
                // Recreate NetworkManager for new mode
                if (isInitialized.get()) {
                    recreateNetworkManager();
                }
                
                Log.i(TAG, "App mode selected successfully: " + mode);
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to select app mode: " + mode, e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Connect to server
     * Chain: UIBridge -> BackgroundService.connectToServer() -> ServerNetworkManager.connect()
     */
    public CompletableFuture<ConnectionResult> connectToServer(String host, int port, String organizationName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Connecting to server: " + host + ":" + port);
                
                // Validate parameters
                if (host == null || host.trim().isEmpty()) {
                    throw new IllegalArgumentException("Host cannot be empty");
                }
                if (port <= 0 || port > 65535) {
                    throw new IllegalArgumentException("Invalid port: " + port);
                }
                if (organizationName == null || organizationName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Organization name cannot be empty");
                }
                
                // Store server configuration
                preferencesManager.setServerConfig(host, port, organizationName);
                
                // Ensure we have ServerNetworkManager
                if (!(networkManager instanceof ServerNetworkManager)) {
                    preferencesManager.setAppMode(AppMode.SERVER);
                    recreateNetworkManager();
                }
                
                // Connect to server
                Map<String, Object> connectionParams = new HashMap<>();
                connectionParams.put("host", host);
                connectionParams.put("port", port);
                connectionParams.put("organizationName", organizationName);
                
                return networkManager.connect(connectionParams).get();
                
            } catch (Exception e) {
                Log.e(TAG, "Server connection failed", e);
                return new ConnectionResult(false, "Connection failed: " + e.getMessage());
            }
        }, executorService).thenCompose(connectionResult -> {
            if (connectionResult.isSuccess()) {
                // Get organization info and server algorithms
                return networkManager.getOrganizationInfo()
                    .thenCompose(orgInfo -> networkManager.getServerAlgorithms())
                    .thenApply(serverAlgorithms -> {
                        // Store server algorithms
                        preferencesManager.setCryptoAlgorithms(serverAlgorithms);
                        stateManager.setConnectionState(ConnectionState.CONNECTED);
                        
                        Log.i(TAG, "Server connection established successfully");
                        return connectionResult;
                    });
            } else {
                return CompletableFuture.completedFuture(connectionResult);
            }
        });
    }
    
    /**
     * Authenticate user
     * Chain: UIBridge -> BackgroundService.authenticateUser() -> NetworkManager.authenticateUser()
     */
    public CompletableFuture<AuthResult> authenticateUser(String username, String password, String email) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Authenticating user: " + username);
                
                // Validate parameters
                if (username == null || username.trim().isEmpty()) {
                    throw new IllegalArgumentException("Username cannot be empty");
                }
                if (password == null || password.trim().isEmpty()) {
                    throw new IllegalArgumentException("Password cannot be empty");
                }
                
                // Create credentials
                UserCredentials credentials = new UserCredentials(username, password, email);
                
                return networkManager.authenticateUser(credentials).get();
                
            } catch (Exception e) {
                Log.e(TAG, "Authentication failed", e);
                return new AuthResult(false, null, "Authentication failed: " + e.getMessage());
            }
        }, executorService).thenCompose(authResult -> {
            if (authResult.isSuccess()) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        // Create session data
                        SessionAuthData authData = new SessionAuthData(
                            username,
                            username, // Use username as userId for now
                            preferencesManager.getServerConfig() != null ? 
                                preferencesManager.getServerConfig().getOrganizationName() : "local",
                            authResult.getToken(),
                            authResult.getToken(), // Use same token for refresh for now
                            System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 24h expiry
                        );
                        
                        // Create session
                        sessionManager.createSession(authData);
                        
                        // Update state
                        stateManager.setAppState(AppState.AUTHENTICATED);
                        
                        Log.i(TAG, "User authenticated successfully: " + username);
                        return authResult;
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to create session after authentication", e);
                        return new AuthResult(false, null, "Session creation failed: " + e.getMessage());
                    }
                }, executorService);
            } else {
                return CompletableFuture.completedFuture(authResult);
            }
        });
    }
    
    /**
     * Set local user for P2P mode
     * Chain: UIBridge -> BackgroundService.setLocalUser() -> LocalNetworkManager setup
     */
    public CompletableFuture<Boolean> setLocalUser(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Setting local user: " + username);
                
                // Validate username
                if (username == null || username.trim().isEmpty()) {
                    throw new IllegalArgumentException("Username cannot be empty");
                }
                
                // Store local username
                preferencesManager.setLocalUsername(username);
                
                // Update state
                stateManager.setAppState(AppState.AUTHENTICATED);
                
                // Ensure we have LocalNetworkManager
                if (!(networkManager instanceof LocalNetworkManager)) {
                    preferencesManager.setAppMode(AppMode.LOCAL);
                    recreateNetworkManager();
                }
                
                LocalNetworkManager localManager = (LocalNetworkManager) networkManager;
                
                // Start discovery
                localManager.startDiscovery();
                
                // Broadcast presence
                UserProfile userProfile = new UserProfile();
                userProfile.setUsername(username);
                userProfile.setDisplayName(username);
                preferencesManager.setUserProfile(userProfile);
                
                localManager.broadcastPresence(userProfile);
                
                Log.i(TAG, "Local user set successfully: " + username);
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to set local user", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Initialize chat with another user
     * Chain: UIBridge -> BackgroundService.initializeChat() -> CryptoManager + NetworkManager
     */
    public CompletableFuture<Boolean> initializeChat(String recipientId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Initializing chat with: " + recipientId);
                
                // Validate recipient
                if (recipientId == null || recipientId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Recipient ID cannot be empty");
                }
                
                // Get crypto algorithms from preferences
                CryptoAlgorithms algorithms = preferencesManager.getCryptoAlgorithms();
                
                // Initialize chat keys
                ChatKeys chatKeys = cryptoManager.initializeChatKeysFromPreferences(preferencesManager);
                
                // Create chat
                String chatUuid = UUID.randomUUID().toString();
                Chat chat = new Chat(chatUuid, "Chat with " + recipientId);
                chat.setKeys(chatKeys);
                chat.setLastActivity(System.currentTimeMillis());
                
                // Save chat to database
                String chatId = databaseManager.saveChat(chat);
                if (chatId == null) {
                    throw new RuntimeException("Failed to save chat to database");
                }
                
                // Set as active chat
                stateManager.setActiveChatId(chatId);
                
                Log.d(TAG, "Chat initialized, sending init request");
                return chatUuid;
                
            } catch (Exception e) {
                Log.e(TAG, "Chat initialization failed", e);
                throw new RuntimeException("Chat initialization failed", e);
            }
        }, executorService).thenCompose(chatUuid -> {
            // Send chat initialization request
            String chatId = stateManager.getActiveChatId();
            Chat chat = databaseManager.getChat(chatId);
            
            return networkManager.sendChatInitRequest(
                recipientId, 
                chatUuid, 
                chat.getKeys().getPublicKeySelf()
            ).thenApply(v -> {
                Log.i(TAG, "Chat init request sent successfully");
                return true;
            });
        });
    }
    
    /**
     * Send message to another user
     * Chain: UIBridge -> BackgroundService.sendMessage() -> CryptoManager + NetworkManager
     */
    public CompletableFuture<Boolean> sendMessage(String chatId, String messageText) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Sending message in chat: " + chatId);
                
                // Validate parameters
                if (chatId == null || chatId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Chat ID cannot be empty");
                }
                if (messageText == null || messageText.trim().isEmpty()) {
                    throw new IllegalArgumentException("Message text cannot be empty");
                }
                
                // Get chat from database
                Chat chat = databaseManager.getChat(chatId);
                if (chat == null) {
                    throw new RuntimeException("Chat not found: " + chatId);
                }
                
                ChatKeys chatKeys = chat.getKeys();
                if (chatKeys == null || !chatKeys.isComplete()) {
                    throw new RuntimeException("Chat keys not initialized for chat: " + chatId);
                }
                
                // Encrypt message
                byte[] encryptedContent = cryptoManager.encryptMessage(messageText, chatKeys.getSymmetricKey());
                
                // Create and save message
                Message message = new Message();
                message.setChatId(chatId);
                message.setContent(Base64.getEncoder().encodeToString(encryptedContent));
                message.setTimestamp(System.currentTimeMillis());
                message.setStatus(MessageStatus.SENDING);
                
                long messageId = databaseManager.saveMessage(message);
                if (messageId == -1) {
                    throw new RuntimeException("Failed to save message to database");
                }
                
                message.setId(messageId);
                
                Log.d(TAG, "Message encrypted and saved, sending to network");
                return new MessageContext(message, chat, encryptedContent);
                
            } catch (Exception e) {
                Log.e(TAG, "Message preparation failed", e);
                throw new RuntimeException("Message preparation failed", e);
            }
        }, executorService).thenCompose(context -> {
            // Send message through network
            stateManager.updateLastActiveTime();
            
            // Extract recipient ID from chat name or use a proper field
            String recipientId = extractRecipientId(context.chat);
            
            return networkManager.sendUserMessage(
                recipientId, 
                context.chat.getId(), 
                context.encryptedContent
            ).thenApply(v -> {
                // Update message status to sent
                databaseManager.updateMessageStatus(context.message.getId(), MessageStatus.SENT);
                Log.i(TAG, "Message sent successfully");
                return true;
            });
        });
    }
    
    /**
     * Send file to another user
     * Chain: UIBridge -> BackgroundService.sendFile() -> CryptoManager + NetworkManager
     */
    public CompletableFuture<Boolean> sendFile(String chatId, String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Sending file in chat: " + chatId + ", file: " + filePath);
                
                // Validate parameters
                if (chatId == null || chatId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Chat ID cannot be empty");
                }
                if (filePath == null || filePath.trim().isEmpty()) {
                    throw new IllegalArgumentException("File path cannot be empty");
                }
                
                // Create file info
                java.io.File file = new java.io.File(filePath);
                if (!file.exists()) {
                    throw new RuntimeException("File not found: " + filePath);
                }
                
                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(UUID.randomUUID().toString());
                fileInfo.setPath(filePath);
                fileInfo.setName(file.getName());
                fileInfo.setSize(file.length());
                
                // Save file info to database
                String fileId = databaseManager.saveFile(fileInfo);
                if (fileId == null) {
                    throw new RuntimeException("Failed to save file info to database");
                }
                
                // Get chat and crypto keys
                Chat chat = databaseManager.getChat(chatId);
                if (chat == null) {
                    throw new RuntimeException("Chat not found: " + chatId);
                }
                
                ChatKeys chatKeys = chat.getKeys();
                if (chatKeys == null || !chatKeys.isComplete()) {
                    throw new RuntimeException("Chat keys not initialized");
                }
                
                // Get crypto algorithms
                CryptoAlgorithms algorithms = preferencesManager.getCryptoAlgorithms();
                
                // Encrypt file
                EncryptedFileResult encryptedFileResult = cryptoManager.encryptFile(
                    filePath, 
                    chatKeys.getSymmetricKey(), 
                    algorithms.getSymmetricAlgorithm()
                );
                
                Log.d(TAG, "File encrypted, uploading to network");
                return new FileContext(fileInfo, chat, encryptedFileResult);
                
            } catch (Exception e) {
                Log.e(TAG, "File preparation failed", e);
                throw new RuntimeException("File preparation failed", e);
            }
        }, executorService).thenCompose(context -> {
            // Upload encrypted file
            FileInfo encryptedFileInfo = new FileInfo();
            encryptedFileInfo.setPath(context.encryptedResult.getEncryptedFilePath());
            encryptedFileInfo.setSize(context.encryptedResult.getSize());
            encryptedFileInfo.setName(context.fileInfo.getName() + ".enc");
            
            return networkManager.uploadFile(encryptedFileInfo)
                .thenCompose(uploadResult -> {
                    if (uploadResult.isSuccess()) {
                        // Create file message
                        Message fileMessage = new Message();
                        fileMessage.setChatId(context.chat.getId());
                        fileMessage.setContent("FILE:" + uploadResult.getFileId());
                        fileMessage.setTimestamp(System.currentTimeMillis());
                        fileMessage.setStatus(MessageStatus.SENDING);
                        
                        databaseManager.saveMessage(fileMessage);
                        
                        // Send file message
                        String recipientId = extractRecipientId(context.chat);
                        return networkManager.sendUserMessage(
                            recipientId, 
                            context.chat.getId(), 
                            ("FILE:" + uploadResult.getFileId()).getBytes()
                        ).thenApply(v -> {
                            Log.i(TAG, "File sent successfully");
                            return true;
                        });
                    } else {
                        throw new RuntimeException("File upload failed: " + uploadResult.getMessage());
                    }
                });
        });
    }
    
    /**
     * Search for users
     * Chain: UIBridge -> BackgroundService.searchUsers() -> NetworkManager.searchUsers()
     */
    public CompletableFuture<List<User>> searchUsers(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Searching users with query: " + query);
                
                // Validate query
                if (query == null || query.trim().length() < 2) {
                    throw new IllegalArgumentException("Search query must be at least 2 characters");
                }
                
                return networkManager.searchUsers(query.trim()).get();
                
            } catch (Exception e) {
                Log.e(TAG, "User search failed", e);
                return new ArrayList<>();
            }
        }, executorService).thenApply(searchResults -> {
            // Merge with local contacts if needed
            List<Contact> localContacts = databaseManager.getAllContacts();
            
            // Convert contacts to users and add to results
            for (Contact contact : localContacts) {
                if (contact.getName().toLowerCase().contains(query.toLowerCase())) {
                    User user = new User();
                    user.setId(contact.getId());
                    user.setUsername(contact.getName());
                    user.setStatus(UserStatus.OFFLINE); // Local contacts default to offline
                    
                    // Add if not already in results
                    boolean alreadyExists = searchResults.stream()
                        .anyMatch(u -> u.getId().equals(contact.getId()));
                    if (!alreadyExists) {
                        searchResults.add(user);
                    }
                }
            }
            
            Log.d(TAG, "User search completed, found " + searchResults.size() + " users");
            return searchResults;
        });
    }
    
    /**
     * Update crypto algorithms
     * Chain: UIBridge -> BackgroundService.updateCryptoAlgorithms() -> CryptoManager + SharedPreferences
     */
    public CompletableFuture<Boolean> updateCryptoAlgorithms(String kemAlg, String symAlg, String sigAlg) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Updating crypto algorithms: KEM=" + kemAlg + ", Sym=" + symAlg + ", Sig=" + sigAlg);
                
                // Create new algorithms configuration
                CryptoAlgorithms newAlgorithms = new CryptoAlgorithms(kemAlg, symAlg, sigAlg);
                
                // Validate algorithms
                cryptoManager.validateCryptoAlgorithms(newAlgorithms);
                
                // Get old algorithms for state change notification
                CryptoAlgorithms oldAlgorithms = preferencesManager.getCryptoAlgorithms();
                
                // Save new algorithms
                preferencesManager.setCryptoAlgorithms(newAlgorithms);
                
                // Reset crypto statistics
                cryptoManager.resetStatistics();
                
                // Notify state change
                stateManager.notifyStateChange(
                    new StateChange("CRYPTO_ALGORITHMS_CHANGED", oldAlgorithms, newAlgorithms));
                
                Log.i(TAG, "Crypto algorithms updated successfully");
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to update crypto algorithms", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Change theme
     * Chain: UIBridge -> BackgroundService.changeTheme() -> SharedPreferencesManager + StateManager
     */
    public CompletableFuture<Boolean> changeTheme(String themeName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Changing theme to: " + themeName);
                
                // Validate theme name
                ThemeMode newTheme;
                try {
                    newTheme = ThemeMode.valueOf(themeName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid theme name: " + themeName);
                }
                
                // Get old theme
                ThemeMode oldTheme = preferencesManager.getThemeMode();
                
                // Save new theme
                preferencesManager.setThemeMode(newTheme);
                
                // Notify state change
                stateManager.notifyStateChange(
                    new StateChange("THEME_CHANGED", oldTheme, newTheme));
                
                Log.i(TAG, "Theme changed successfully to: " + themeName);
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to change theme", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Logout user
     * Chain: UIBridge -> BackgroundService.logout() -> SessionManager + NetworkManager + cleanup
     */
    public CompletableFuture<Boolean> logout() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.i(TAG, "User logout initiated");
                
                // Clear session
                sessionManager.logout();
                
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Logout preparation failed", e);
                return false;
            }
        }, executorService).thenCompose(success -> {
            // Disconnect from network and cleanup
            List<Chat> allChats = databaseManager.getAllChats();
            
            return networkManager.logout().thenRun(() -> {
                // Cleanup crypto data
                cryptoManager.cleanup();
                
                // Reset state
                stateManager.resetState();
                
                Log.i(TAG, "User logout completed successfully");
            }).thenApply(v -> success);
        }).exceptionally(throwable -> {
            // Even if network logout fails, complete local logout
            Log.w(TAG, "Network logout failed, but completing local logout", throwable);
            return true;
        });
    }
    
    // ===========================
    // MESSAGE HANDLING (incoming from network)
    // ===========================
    
    /**
     * Server message handler for WebSocket messages
     */
    private class ServerMessageHandler implements ServerNetworkManager.WebSocketMessageListener {
        @Override
        public void onMessageReceived(String fromUserId, String messageType, Map<String, Object> messageData) {
            handleIncomingMessage(fromUserId, messageType, messageData, MessageSource.SERVER);
        }
        
        @Override
        public void onStatusUpdate(String userId, String status, String lastSeen) {
            Log.d(TAG, "Status update from server: " + userId + " -> " + status);
            
            try {
                UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
                if (uiBridge != null) {
                    uiBridge.onUserStatusChanged(userId, userStatus);
                }
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid user status received: " + status);
            }
        }
        
        @Override
        public void onConnectionStatus(String connectionId, String status) {
            Log.d(TAG, "Connection status update: " + connectionId + " -> " + status);
            
            if ("connected".equals(status)) {
                stateManager.setConnectionState(ConnectionState.CONNECTED);
            } else if ("disconnected".equals(status)) {
                stateManager.setConnectionState(ConnectionState.DISCONNECTED);
            }
        }
    }
    
    /**
     * P2P message handler for local network messages
     */
    private class P2PMessageHandler implements LocalNetworkManager.P2PMessageListener {
        @Override
        public void onMessageReceived(String fromPeerId, String messageType, Object messageData) {
            // Convert messageData to Map for consistent handling
            Map<String, Object> dataMap = new HashMap<>();
            if (messageData instanceof Map) {
                dataMap = (Map<String, Object>) messageData;
            } else {
                dataMap.put("data", messageData);
            }
            
            handleIncomingMessage(fromPeerId, messageType, dataMap, MessageSource.P2P);
        }
        
        @Override
        public void onPeerConnected(String peerId) {
            Log.d(TAG, "Peer connected: " + peerId);
            if (uiBridge != null) {
                uiBridge.onUserStatusChanged(peerId, UserStatus.ONLINE);
            }
        }
        
        @Override
        public void onPeerDisconnected(String peerId) {
            Log.d(TAG, "Peer disconnected: " + peerId);
            if (uiBridge != null) {
                uiBridge.onUserStatusChanged(peerId, UserStatus.OFFLINE);
            }
        }
    }
    
    /**
     * Handle incoming message from either server or P2P
     */
    private void handleIncomingMessage(String fromUserId, String messageType, Map<String, Object> messageData, MessageSource source) {
        executorService.submit(() -> {
            try {
                Log.d(TAG, "Handling incoming message: type=" + messageType + ", from=" + fromUserId + ", source=" + source);
                
                switch (messageType) {
                    case "USER_MESSAGE":
                    case "YUMSG_USER_MESSAGE":
                        handleUserMessage(fromUserId, messageData);
                        break;
                        
                    case "CHAT_INIT_REQUEST":
                    case "YUMSG_CHAT_INIT_REQUEST":
                        handleChatInitRequest(fromUserId, messageData);
                        break;
                    case "CHAT_INIT_RESPONSE":
                    case "YUMSG_CHAT_INIT_RESPONSE":
                        handleChatInitResponse(fromUserId, messageData);
                        break;
                    case "CHAT_INIT_CONFIRM":
                    case "YUMSG_CHAT_INIT_CONFIRM":
                        handleChatInitConfirm(fromUserId, messageData);
                        break;
                    case "CHAT_INIT_SIGNATURE":
                    case "YUMSG_CHAT_INIT_SIGNATURE":
                        handleChatInitSignature(fromUserId, messageData);
                        break;
                    case "YUMSG_CHAT_INIT":
                        handleChatInitMessage(fromUserId, messageData);
                        break;

                    case "CHAT_DELETE":
                    case "YUMSG_CHAT_DELETE":
                        handleChatDeleteMessage(fromUserId, messageData);
                        break;
                        
                    case "TYPING_STATUS":
                        handleTypingStatusMessage(fromUserId, messageData);
                        break;
                        
                    default:
                        Log.w(TAG, "Unknown message type: " + messageType);
                        break;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error handling incoming message", e);
            }
        });
    }
    
    /**
     * Handle incoming user message
     */
    private void handleUserMessage(String fromUserId, Map<String, Object> messageData) {
        try {
            String chatUuid = (String) messageData.get("chat_uuid");
            String encryptedContent = (String) messageData.get("encrypted_content");
            String contentType = (String) messageData.get("content_type");
            
            if (chatUuid == null || encryptedContent == null) {
                Log.w(TAG, "Invalid user message data");
                return;
            }
            
            // Find chat by UUID
            Chat chat = findChatByUuid(chatUuid);
            if (chat == null) {
                Log.w(TAG, "Chat not found for UUID: " + chatUuid);
                return;
            }
            
            // Check if chat has keys
            ChatKeys chatKeys = chat.getKeys();
            if (chatKeys == null || !chatKeys.isComplete()) {
                Log.w(TAG, "Chat keys not available for decryption");
                return;
            }
            
            // Decrypt message
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedContent);
            String decryptedMessage = cryptoManager.decryptMessage(encryptedBytes, chatKeys.getSymmetricKey());
            
            // Create and save message
            Message message = new Message();
            message.setChatId(chat.getId());
            message.setContent(decryptedMessage);
            message.setTimestamp(System.currentTimeMillis());
            message.setStatus(MessageStatus.DELIVERED);
            
            long messageId = databaseManager.saveMessage(message);
            message.setId(messageId);
            
            // Update chat activity
            chat.setLastActivity(System.currentTimeMillis());
            databaseManager.saveChat(chat);
            
            // Notify UI
            if (uiBridge != null) {
                uiBridge.onMessageReceived(message);
                
                // Show notification if not in active chat
                if (!chat.getId().equals(stateManager.getActiveChatId())) {
                    NotificationData notification = new NotificationData(
                        "msg_" + messageId,
                        "New message from " + fromUserId,
                        decryptedMessage,
                        "MESSAGE"
                    );
                    uiBridge.showNotification(notification);
                }
            }
            
            Log.d(TAG, "User message processed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling user message", e);
        }
    }
    
    /**
     * Handle chat initialization messages
     */
    private void handleChatInitMessage(String fromUserId, Map<String, Object> messageData) {
        try {
            String chatUuid = (String) messageData.get("chat_uuid");
            String step = (String) messageData.get("step");
            
            if (step == null) {
                step = "request"; // Default for backward compatibility
            }
            
            Log.d(TAG, "Handling chat init message: step=" + step + ", chat=" + chatUuid);
            
            switch (step) {
                case "request":
                    handleChatInitRequest(fromUserId, messageData);
                    break;
                case "response":
                    handleChatInitResponse(fromUserId, messageData);
                    break;
                case "confirm":
                    handleChatInitConfirm(fromUserId, messageData);
                    break;
                case "signature":
                    handleChatInitSignature(fromUserId, messageData);
                    break;
                default:
                    Log.w(TAG, "Unknown chat init step: " + step);
                    break;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling chat init message", e);
        }
    }
    
    /**
     * Handle chat initialization request
     */
    private void handleChatInitRequest(String fromUserId, Map<String, Object> messageData) {
        try {
            String chatUuid = (String) messageData.get("chat_uuid");
            String peerPublicKeyB64 = (String) messageData.get("public_key");
            @SuppressWarnings("unchecked")
            Map<String, String> algMap = (Map<String, String>) messageData.get("crypto_algorithms");

            if (chatUuid == null || peerPublicKeyB64 == null) {
                Log.w(TAG, "Invalid chat init request data");
                return;
            }

            CryptoAlgorithms algorithms;
            if (algMap != null) {
                algorithms = new CryptoAlgorithms(
                        algMap.get("asymmetric"),
                        algMap.get("symmetric"),
                        algMap.get("signature"));
            } else {
                algorithms = preferencesManager.getCryptoAlgorithms();
            }

            byte[] peerPublicKeyBytes = Base64.getDecoder().decode(peerPublicKeyB64);
            AsymmetricKeyParameter peerKeyParam = cryptoManager.bytesToKemPublicKey(
                peerPublicKeyBytes, algorithms.getKemAlgorithm());
            byte[] peerPublicKey = cryptoManager.getPublicKeyBytes(
                peerKeyParam, algorithms.getKemAlgorithm());

            Chat chat = findOrCreateChat(chatUuid, fromUserId);
            if (chat.getKeyEstablishmentStatus() == null || chat.getKeyEstablishmentStatus().isEmpty()) {
                chat.setKeyEstablishmentStatus("INITIALIZING");
            }

            ChatKeys chatKeys = cryptoManager.createChatKeys(algorithms.getKemAlgorithm());
            chatKeys.setPublicKeyPeer(peerPublicKey);

            PeerCryptoInfo peerInfo = chat.getPeerCryptoInfo();
            if (peerInfo == null) {
                peerInfo = new PeerCryptoInfo();
                peerInfo.setPeerId(fromUserId);
            }
            peerInfo.setPeerAlgorithms(algorithms);
            chat.setPeerCryptoInfo(peerInfo);

            SecretWithEncapsulation kemResult = cryptoManager.encapsulateSecret(
                peerPublicKey, algorithms.getKemAlgorithm());
            pendingSecrets.put(chatUuid, kemResult.getSecret());

            chat.setKeys(chatKeys);
            databaseManager.saveChat(chat);

            networkManager.sendChatInitResponse(fromUserId, chatUuid,
                chatKeys.getPublicKeySelf(), kemResult.getEncapsulation(), null);

            Log.i(TAG, "Chat init request processed, response sent. Chat: " + chatUuid);

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid cryptographic parameters in chat init request", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in handleChatInitRequest", e);
        }
    }
    
    /**
     * Handle other chat init steps (simplified for brevity)
     */
    private void handleChatInitResponse(String fromUserId, Map<String, Object> messageData) {
        try {
            String chatUuid = (String) messageData.get("chat_uuid");
            String peerPublicKeyB64 = (String) messageData.get("public_key");
            String capsuleB64 = (String) messageData.get("kem_capsule");
            @SuppressWarnings("unchecked")
            Map<String, String> algMap = (Map<String, String>) messageData.get("crypto_algorithms");

            if (chatUuid == null || peerPublicKeyB64 == null || capsuleB64 == null) {
                Log.w(TAG, "Invalid chat init response data");
                return;
            }

            Chat chat = databaseManager.getChat(chatUuid);
            if (chat == null) {
                Log.w(TAG, "Chat not found for init response: " + chatUuid);
                return;
            }

            CryptoAlgorithms algorithms;
            if (algMap != null) {
                algorithms = new CryptoAlgorithms(
                        algMap.get("asymmetric"),
                        algMap.get("symmetric"),
                        algMap.get("signature"));
            } else {
                algorithms = preferencesManager.getCryptoAlgorithms();
            }

            ChatKeys chatKeys = chat.getKeys();
            if (chatKeys == null) {
                Log.w(TAG, "Chat keys missing for chat: " + chatUuid);
                return;
            }

            byte[] peerKeyBytes = Base64.getDecoder().decode(peerPublicKeyB64);
            AsymmetricKeyParameter peerKeyParam = cryptoManager.bytesToKemPublicKey(
                peerKeyBytes, algorithms.getKemAlgorithm());
            byte[] peerPublicKey = cryptoManager.getPublicKeyBytes(
                peerKeyParam, algorithms.getKemAlgorithm());
            chatKeys.setPublicKeyPeer(peerPublicKey);

            byte[] capsuleBytes = Base64.getDecoder().decode(capsuleB64);
            byte[] secretB = cryptoManager.extractSecret(
                capsuleBytes,
                chatKeys.getPrivateKeySelf(),
                algorithms.getKemAlgorithm()
            );

            SecretWithEncapsulation kemResult = cryptoManager.encapsulateSecret(
                peerPublicKey, algorithms.getKemAlgorithm());
            byte[] secretA = kemResult.getSecret();
            byte[] capsuleA = kemResult.getEncapsulation();

            ChatKeys updatedKeys = cryptoManager.completeChatInitialization(
                chatKeys, secretA, secretB);

            String fingerprint = cryptoManager.generateChatFingerprintFromKeys(updatedKeys);

            ChatKeys cleanedKeys = new ChatKeys(updatedKeys.getAlgorithm());
            cleanedKeys.setSymmetricKey(updatedKeys.getSymmetricKey());
            chat.setKeys(cleanedKeys);

            databaseManager.updateChatKeyEstablishment(chat.getId(), fingerprint, "ESTABLISHED");
            databaseManager.saveChat(chat);

            cryptoManager.secureClearChatKeys(updatedKeys);

            networkManager.sendChatInitConfirm(fromUserId, chatUuid, capsuleA);

            if (uiBridge != null) {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("chatId", chat.getId());
                eventData.put("fingerprint", fingerprint);
                eventData.put("status", "ESTABLISHED");
                uiBridge.sendEventToUI("ChatEstablished", eventData);
            }

            Log.i(TAG, "Chat initialization completed successfully. Chat: " + chatUuid +
                   ", Fingerprint: " + fingerprint);

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid cryptographic parameters in chat init response", e);
        } catch (SecurityException e) {
            Log.e(TAG, "Security error during chat initialization", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in handleChatInitResponse", e);
        }
    }
    
    private void handleChatInitConfirm(String fromUserId, Map<String, Object> messageData) {
        try {
            String chatUuid = (String) messageData.get("chat_uuid");
            String capsuleB64 = (String) messageData.get("kem_capsule");

            if (chatUuid == null || capsuleB64 == null) {
                Log.w(TAG, "Invalid chat init confirm data");
                return;
            }

            Chat chat = databaseManager.getChat(chatUuid);
            if (chat == null) {
                Log.w(TAG, "Chat not found for init confirm: " + chatUuid);
                return;
            }

            ChatKeys chatKeys = chat.getKeys();
            if (chatKeys == null) {
                Log.w(TAG, "Chat keys missing for chat: " + chatUuid);
                return;
            }

            CryptoAlgorithms algorithms = preferencesManager.getCryptoAlgorithms();

            byte[] capsuleBytes = Base64.getDecoder().decode(capsuleB64);
            byte[] secretA = cryptoManager.extractSecret(
                capsuleBytes,
                chatKeys.getPrivateKeySelf(),
                algorithms.getKemAlgorithm()
            );

            byte[] secretB = pendingSecrets.remove(chatUuid);
            if (secretB == null) {
                Log.w(TAG, "No pending secret for chat: " + chatUuid);
                return;
            }

            ChatKeys updatedKeys = cryptoManager.completeChatInitialization(
                chatKeys, secretA, secretB);

            String fingerprint = cryptoManager.generateChatFingerprintFromKeys(updatedKeys);

            ChatKeys cleanedKeys = new ChatKeys(updatedKeys.getAlgorithm());
            cleanedKeys.setSymmetricKey(updatedKeys.getSymmetricKey());
            chat.setKeys(cleanedKeys);

            databaseManager.updateChatKeyEstablishment(chat.getId(), fingerprint, "ESTABLISHED");
            databaseManager.saveChat(chat);

            cryptoManager.secureClearChatKeys(updatedKeys);

            if (uiBridge != null) {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("chatId", chat.getId());
                eventData.put("fingerprint", fingerprint);
                eventData.put("status", "ESTABLISHED");
                uiBridge.sendEventToUI("ChatEstablished", eventData);
            }

            networkManager.sendChatInitSignature(fromUserId, chatUuid, new byte[0]);

            Log.i(TAG, "Chat initialization confirmed successfully. Chat: " + chatUuid +
                   ", Fingerprint: " + fingerprint);

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid cryptographic parameters in chat init confirm", e);
        } catch (SecurityException e) {
            Log.e(TAG, "Security error during chat initialization confirm", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in handleChatInitConfirm", e);
        }
    }
    
    private void handleChatInitSignature(String fromUserId, Map<String, Object> messageData) {
        String chatUuid = (String) messageData.get("chat_uuid");
        String signatureB64 = (String) messageData.get("signature");

        if (chatUuid == null || signatureB64 == null) {
            Log.w(TAG, "Invalid chat init signature data");
            return;
        }

        Chat chat = findChatByUuid(chatUuid);
        if (chat == null) {
            Log.w(TAG, "Chat not found for init signature: " + chatUuid);
            return;
        }

        PeerCryptoInfo peerInfo = chat.getPeerCryptoInfo();
        if (peerInfo == null) {
            peerInfo = new PeerCryptoInfo();
            peerInfo.setPeerId(fromUserId);
        }

        byte[] signatureKey = Base64.getDecoder().decode(signatureB64);
        peerInfo.setPeerSignaturePublicKey(signatureKey);

        if (peerInfo.getPeerSignatureAlgorithm() == null || peerInfo.getPeerSignatureAlgorithm().trim().isEmpty()) {
            CryptoAlgorithms algorithms = peerInfo.getPeerAlgorithms();
            if (algorithms == null) {
                algorithms = preferencesManager.getCryptoAlgorithms();
            }
            peerInfo.setPeerSignatureAlgorithm(algorithms.getSignatureAlgorithm());
        }

        chat.setPeerCryptoInfo(peerInfo);
        databaseManager.updateChatPeerCrypto(chat.getId(), peerInfo);

        Log.d(TAG, "Chat init signature processed and peer key stored");
    }
    
    /**
     * Handle chat delete message
     */
    private void handleChatDeleteMessage(String fromUserId, Map<String, Object> messageData) {
        String chatUuid = (String) messageData.get("chat_uuid");
        String deleteReason = (String) messageData.get("delete_reason");
        
        if (chatUuid == null) {
            Log.w(TAG, "Invalid chat delete message data");
            return;
        }
        
        // Find and delete chat
        Chat chat = findChatByUuid(chatUuid);
        if (chat != null) {
            databaseManager.deleteChat(chat.getId());
            Log.d(TAG, "Chat deleted: " + chatUuid + ", reason: " + deleteReason);
            
            // Notify UI
            if (uiBridge != null) {
                uiBridge.sendEventToUI(new UIEvent("CHAT_DELETED", 
                    Map.of("chatId", chat.getId(), "reason", deleteReason)));
            }
        }
    }
    
    /**
     * Handle typing status message
     */
    private void handleTypingStatusMessage(String fromUserId, Map<String, Object> messageData) {
        String chatUuid = (String) messageData.get("chat_uuid");
        Boolean isTyping = (Boolean) messageData.get("is_typing");
        
        if (chatUuid != null && isTyping != null && uiBridge != null) {
            uiBridge.sendEventToUI(new UIEvent("TYPING_STATUS", 
                Map.of("userId", fromUserId, "chatUuid", chatUuid, "isTyping", isTyping)));
        }
    }
    
    // ===========================
    // CONNECTION RESTORATION
    // ===========================
    
    /**
     * Schedule connection restoration for automatic session recovery
     */
    public void scheduleConnectionRestoration() {
        scheduler.submit(() -> {
            try {
                AppMode appMode = preferencesManager.getAppMode();
                
                if (appMode == AppMode.SERVER) {
                    scheduleServerConnectionRestoration();
                } else if (appMode == AppMode.LOCAL) {
                    scheduleLocalNetworkInitialization();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Connection restoration failed", e);
            }
        });
    }
    
    /**
     * Schedule server connection restoration
     */
    private void scheduleServerConnectionRestoration() {
        Log.d(TAG, "Scheduling server connection restoration");
        
        ServerConfig serverConfig = preferencesManager.getServerConfig();
        if (serverConfig == null) {
            Log.w(TAG, "No server config for restoration");
            return;
        }
        
        connectToServer(serverConfig.getHost(), serverConfig.getPort(), serverConfig.getOrganizationName())
            .thenCompose(connectionResult -> {
                if (connectionResult.isSuccess()) {
                    stateManager.setConnectionState(ConnectionState.CONNECTED);
                    
                    // Attempt session refresh if needed
                    return sessionManager.refreshSession() ? 
                        CompletableFuture.completedFuture(true) :
                        CompletableFuture.failedFuture(new RuntimeException("Token refresh failed"));
                } else {
                    throw new RuntimeException(connectionResult.getMessage());
                }
            })
            .thenRun(() -> {
                stateManager.setAppState(AppState.AUTHENTICATED);
                if (uiBridge != null) {
                    uiBridge.onConnectionStateChanged(ConnectionState.CONNECTED);
                    uiBridge.sendEventToUI(new UIEvent("SESSION_RESTORED", 
                        Map.of("success", true, "message", "Автоматический вход выполнен")));
                }
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "Server connection restoration failed", throwable);
                stateManager.setConnectionState(ConnectionState.ERROR);
                preferencesManager.clearUserSession();
                
                if (uiBridge != null) {
                    uiBridge.sendEventToUI(new UIEvent("SESSION_RESTORATION_FAILED", 
                        Map.of("error", throwable.getMessage(), "action", "NAVIGATE_TO_AUTH")));
                }
                return null;
            });
    }
    
    /**
     * Schedule local network initialization
     */
    private void scheduleLocalNetworkInitialization() {
        Log.d(TAG, "Scheduling local network initialization");
        
        String localUsername = preferencesManager.getLocalUsername();
        if (localUsername == null) {
            Log.w(TAG, "No local username for P2P restoration");
            return;
        }
        
        setLocalUser(localUsername)
            .thenRun(() -> {
                stateManager.setAppState(AppState.AUTHENTICATED);
                if (uiBridge != null) {
                    uiBridge.sendEventToUI(new UIEvent("P2P_RESTORED", 
                        Map.of("success", true, "username", localUsername)));
                }
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "P2P restoration failed", throwable);
                return null;
            });
    }
    
    // ===========================
    // BACKGROUND TASKS
    // ===========================
    
    /**
     * Connection health monitoring task
     */
    private class ConnectionHealthTask implements Runnable {
        @Override
        public void run() {
            try {
                if (networkManager != null && networkManager.isConnected()) {
                    // Check connection health
                    ConnectionMetrics metrics = networkManager.getConnectionMetrics();
                    
                    // Update UI with connection info
                    if (uiBridge != null) {
                        uiBridge.sendEventToUI(new UIEvent("CONNECTION_HEALTH", 
                            Map.of("metrics", metrics)));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection health check failed", e);
            }
        }
    }
    
    /**
     * Pending operations processor task
     */
    private class PendingOperationsTask implements Runnable {
        @Override
        public void run() {
            try {
                PendingOperation operation;
                while ((operation = pendingOperations.poll()) != null) {
                    try {
                        operation.execute();
                    } catch (Exception e) {
                        Log.e(TAG, "Pending operation failed", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Pending operations processor failed", e);
            }
        }
    }
    
    /**
     * Session validation task
     */
    private class SessionValidationTask implements Runnable {
        @Override
        public void run() {
            try {
                if (sessionManager.needsTokenRefresh()) {
                    Log.d(TAG, "Session needs refresh, attempting automatic refresh");
                    if (!sessionManager.refreshSession()) {
                        Log.w(TAG, "Session refresh failed, may need re-authentication");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Session validation failed", e);
            }
        }
    }
    
    /**
     * State change observer
     */
    private class StateChangeHandler implements StateObserver {
        @Override
        public void onStateChanged(AppState newState) {
            Log.d(TAG, "App state changed to: " + newState);
            
            // Handle state-specific logic
            switch (newState) {
                case AUTHENTICATED:
                    // Load pending messages or perform other authenticated state setup
                    loadPendingMessages();
                    break;
                case CHAT_LIST:
                    // Prepare chat list data
                    prepareChatListData();
                    break;
            }
        }
        
        @Override
        public void onConnectionStateChanged(ConnectionState newState) {
            Log.d(TAG, "Connection state changed to: " + newState);
            
            // Handle connection state changes
            if (newState == ConnectionState.DISCONNECTED) {
                // Queue operations for when connection is restored
                stateManager.setAppState(AppState.INITIALIZING);
            }
        }
    }
    
    // ===========================
    // HELPER METHODS
    // ===========================
    
    /**
     * Recreate network manager when app mode changes
     */
    private void recreateNetworkManager() {
        try {
            // Disconnect old network manager
            if (networkManager != null) {
                networkManager.disconnect();
            }
            
            // Create new network manager
            networkManager = createNetworkManager();
            
            // Setup message handlers
            if (networkManager != null) {
                setupMessageHandlers();
                
                // Update session manager reference
                sessionManager.setNetworkManager(networkManager);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to recreate network manager", e);
        }
    }
    
    /**
     * Find chat by UUID
     */
    private Chat findChatByUuid(String chatUuid) {
        List<Chat> allChats = databaseManager.getAllChats();
        for (Chat chat : allChats) {
            if (chatUuid.equals(chat.getId())) {
                return chat;
            }
        }
        return null;
    }
    
    /**
     * Find or create chat
     */
    private Chat findOrCreateChat(String chatUuid, String peerUserId) {
        // Use efficient DB lookup
        Chat chat = databaseManager.getChat(chatUuid);

        if (chat == null) {
            chat = new Chat(chatUuid, "Chat with " + peerUserId);
            chat.setLastActivity(System.currentTimeMillis());
            chat.setKeyEstablishmentStatus("INITIALIZING");
            databaseManager.saveChat(chat);
            Log.d(TAG, "Created new chat: " + chatUuid + " with status INITIALIZING");
        } else {
            chat.setLastActivity(System.currentTimeMillis());
            Log.d(TAG, "Found existing chat: " + chatUuid +
                   " with status: " + chat.getKeyEstablishmentStatus());
        }

        return chat;
    }
    
    /**
     * Extract recipient ID from chat (simplified implementation)
     */
    private String extractRecipientId(Chat chat) {
        // This is a simplified implementation
        // In reality, you'd have proper participant management
        String chatName = chat.getName();
        if (chatName != null && chatName.startsWith("Chat with ")) {
            return chatName.substring("Chat with ".length());
        }
        return "unknown";
    }
    
    /**
     * Load pending messages from server
     */
    private void loadPendingMessages() {
        if (networkManager instanceof ServerNetworkManager) {
            ServerNetworkManager serverManager = (ServerNetworkManager) networkManager;
            serverManager.getPendingMessages()
                .thenAccept(pendingMessages -> {
                    for (PendingMessage pendingMsg : pendingMessages) {
                        // Process each pending message
                        handleIncomingMessage(
                            pendingMsg.getFromUserId(),
                            pendingMsg.getMessageType(),
                            pendingMsg.getMessageData(),
                            MessageSource.SERVER
                        );
                    }
                    
                    // Acknowledge received messages
                    List<String> messageIds = pendingMessages.stream()
                        .map(PendingMessage::getId)
                        .collect(java.util.stream.Collectors.toList());
                    
                    if (!messageIds.isEmpty()) {
                        serverManager.acknowledgeMessages(messageIds);
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to load pending messages", throwable);
                    return null;
                });
        }
    }
    
    /**
     * Prepare chat list data
     */
    private void prepareChatListData() {
        // Load all chats and update UI
        List<Chat> allChats = databaseManager.getAllChats();
        if (uiBridge != null) {
            uiBridge.sendEventToUI(new UIEvent("CHAT_LIST_READY", 
                Map.of("chats", allChats)));
        }
    }
    
    /**
     * Set UI bridge reference
     */
    public void setUIBridge(UIBridge uiBridge) {
        this.uiBridge = uiBridge;
        Log.d(TAG, "UIBridge reference set");
    }
    
    /**
     * Get current app mode
     */
    public AppMode getAppMode() {
        return preferencesManager.getAppMode();
    }
    
    /**
     * Get current app state  
     */
    public AppState getCurrentState() {
        return stateManager.getCurrentAppState();
    }
    
    /**
     * Register state observer
     */
    public void registerStateObserver(StateObserver observer) {
        stateManager.addStateObserver(observer);
    }
    
    /**
     * Unregister state observer
     */
    public void unregisterStateObserver(StateObserver observer) {
        stateManager.removeStateObserver(observer);
    }
    
    /**
     * Keep alive for background service
     */
    public void keepAlive() {
        // Update last active time
        stateManager.updateLastActiveTime();
    }
    
    // ===========================
    // INNER CLASSES
    // ===========================
    
    /**
     * Message source enum
     */
    private enum MessageSource {
        SERVER, P2P
    }
    
    /**
     * Message context for async processing
     */
    private static class MessageContext {
        final Message message;
        final Chat chat;
        final byte[] encryptedContent;
        
        MessageContext(Message message, Chat chat, byte[] encryptedContent) {
            this.message = message;
            this.chat = chat;
            this.encryptedContent = encryptedContent;
        }
    }
    
    /**
     * File context for async processing
     */
    private static class FileContext {
        final FileInfo fileInfo;
        final Chat chat;
        final EncryptedFileResult encryptedResult;
        
        FileContext(FileInfo fileInfo, Chat chat, EncryptedFileResult encryptedResult) {
            this.fileInfo = fileInfo;
            this.chat = chat;
            this.encryptedResult = encryptedResult;
        }
    }
    
    /**
     * Pending operation interface
     */
    private interface PendingOperation {
        void execute() throws Exception;
    }
}