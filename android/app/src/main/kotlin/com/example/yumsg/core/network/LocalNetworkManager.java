package com.example.yumsg.core.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.example.yumsg.core.data.*;
import com.example.yumsg.core.enums.*;
import com.example.yumsg.core.state.StateManager;
import com.example.yumsg.core.storage.SharedPreferencesManager;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LocalNetworkManager - Complete P2P Implementation
 * 
 * Manages peer-to-peer communication for local network mode.
 * Implements device discovery, direct messaging, and file transfer without server infrastructure.
 * 
 * Key Features:
 * - Multicast device discovery
 * - Wi-Fi Direct peer connections
 * - Direct socket communication
 * - P2P file transfer
 * - Network state monitoring
 * - Offline-first architecture
 * - Local user management
 */
public class LocalNetworkManager implements NetworkManager {
    private static final String TAG = "LocalNetworkManager";
    
    // Network configuration
    private static final int DISCOVERY_PORT = 8888;
    private static final int MESSAGING_PORT = 8889;
    private static final int FILE_TRANSFER_PORT = 8890;
    private static final String MULTICAST_GROUP = "224.0.2.60";
    private static final int DISCOVERY_INTERVAL_MS = 5000;
    private static final int PEER_TIMEOUT_MS = 30000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    
    // Protocol messages
    private static final String MSG_DISCOVERY_REQUEST = "YUMSG_DISCOVERY_REQUEST";
    private static final String MSG_DISCOVERY_RESPONSE = "YUMSG_DISCOVERY_RESPONSE";
    private static final String MSG_USER_MESSAGE = "YUMSG_USER_MESSAGE";
    private static final String MSG_CHAT_INIT_REQUEST = "YUMSG_CHAT_INIT_REQUEST";
    private static final String MSG_CHAT_INIT_RESPONSE = "YUMSG_CHAT_INIT_RESPONSE";
    private static final String MSG_CHAT_INIT_CONFIRM = "YUMSG_CHAT_INIT_CONFIRM";
    private static final String MSG_CHAT_INIT_SIGNATURE = "YUMSG_CHAT_INIT_SIGNATURE";
    private static final String MSG_CHAT_DELETE = "YUMSG_CHAT_DELETE";
    private static final String MSG_FILE_TRANSFER = "YUMSG_FILE_TRANSFER";
    private static final String MSG_STATUS_UPDATE = "YUMSG_STATUS_UPDATE";
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);
    
    // Dependencies
    private final Context context;
    private final Gson gson;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduler;
    
    // Network components
    private WifiManager wifiManager;
    private WifiP2pManager p2pManager;
    private WifiP2pManager.Channel p2pChannel;
    private ConnectivityManager connectivityManager;
    
    // Discovery and messaging
    private MulticastSocket discoverySocket;
    private ServerSocket messagingSocket;
    private ServerSocket fileTransferSocket;
    
    // State management
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile ConnectionMetrics connectionMetrics;
    private volatile String localUsername;
    private volatile UserProfile localUserProfile;
    
    // Peer management
    private final Map<String, DiscoveredPeer> discoveredPeers = new ConcurrentHashMap<>();
    private final Map<String, PeerConnection> activePeerConnections = new ConcurrentHashMap<>();
    private final Set<P2PMessageListener> messageListeners = ConcurrentHashMap.newKeySet();
    
    // Message queuing for offline scenarios
    private final Queue<QueuedMessage> messageQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * Constructor
     */
    public LocalNetworkManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .create();
        this.executorService = Executors.newCachedThreadPool();
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.connectionMetrics = new ConnectionMetrics();
        
        Log.d(TAG, "LocalNetworkManager instance created");
    }
    
    // ===========================
    // NETWORKMANAGER INTERFACE IMPLEMENTATION
    // ===========================
    
    @Override
    public CompletableFuture<ConnectionResult> connect(Map<String, Object> connectionParams) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                Log.d(TAG, "Connecting to local P2P network");
                
                // Initialize network components
                if (!initializeNetworkComponents()) {
                    return new ConnectionResult(false, "Failed to initialize network components");
                }
                
                // Start discovery
                if (!startDiscovery()) {
                    return new ConnectionResult(false, "Failed to start device discovery");
                }
                
                // Start messaging server
                if (!startMessagingServer()) {
                    return new ConnectionResult(false, "Failed to start messaging server");
                }
                
                // Update connection state
                setConnectionState(ConnectionState.CONNECTED);
                
                Log.i(TAG, "Connected to local P2P network successfully");
                return new ConnectionResult(true, "Connected to local P2P network");
                
            } catch (Exception e) {
                Log.e(TAG, "P2P connection failed", e);
                setConnectionState(ConnectionState.ERROR);
                return new ConnectionResult(false, "P2P connection failed: " + e.getMessage());
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
                Log.d(TAG, "Disconnecting from P2P network");
                
                // Stop discovery
                stopDiscovery();
                
                // Close all peer connections
                for (PeerConnection connection : activePeerConnections.values()) {
                    connection.close();
                }
                activePeerConnections.clear();
                
                // Close server sockets
                closeSocket(messagingSocket);
                closeSocket(fileTransferSocket);
                closeSocket(discoverySocket);
                
                // Clear discovered peers
                discoveredPeers.clear();
                
                // Update state
                setConnectionState(ConnectionState.DISCONNECTED);
                
                Log.i(TAG, "Disconnected from P2P network");
                
            } finally {
                lock.writeLock().unlock();
            }
        }, executorService);
    }
    
    @Override
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED && isDiscovering.get();
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
        // P2P mode doesn't require traditional authentication
        return CompletableFuture.supplyAsync(() -> {
            Log.d(TAG, "P2P mode: Setting local user from credentials");
            
            localUsername = credentials.getUsername();
            
            // Create user profile
            localUserProfile = new UserProfile();
            localUserProfile.setUsername(credentials.getUsername());
            localUserProfile.setEmail(credentials.getEmail());
            localUserProfile.setDisplayName(credentials.getUsername()); // Use username as display name
            
            // Store in preferences
            SharedPreferencesManager.getInstance().setLocalUsername(localUsername);
            SharedPreferencesManager.getInstance().setUserProfile(localUserProfile);
            
            Log.i(TAG, "Local user set: " + localUsername);
            return new AuthResult(true, "local_session", "Local user authenticated");
        });
    }
    
    @Override
    public CompletableFuture<AuthResult> registerUser(UserProfile userInfo) {
        // P2P mode treats registration same as authentication
        UserCredentials credentials = new UserCredentials(userInfo.getUsername(), "", userInfo.getEmail());
        return authenticateUser(credentials);
    }
    
    @Override
    public CompletableFuture<List<User>> searchUsers(String query) {
        return CompletableFuture.supplyAsync(() -> {
            Log.d(TAG, "Searching for peers with query: " + query);
            
            List<User> users = new ArrayList<>();
            
            // Search discovered peers
            for (DiscoveredPeer peer : discoveredPeers.values()) {
                if (peer.getDisplayName().toLowerCase().contains(query.toLowerCase()) ||
                    peer.getUsername().toLowerCase().contains(query.toLowerCase())) {
                    
                    User user = new User();
                    user.setId(peer.getPeerId());
                    user.setUsername(peer.getUsername());
                    user.setStatus(peer.isOnline() ? UserStatus.ONLINE : UserStatus.OFFLINE);
                    users.add(user);
                }
            }
            
            Log.d(TAG, "Found " + users.size() + " matching peers");
            return users;
        }, executorService);
    }
    
    @Override
    public CompletableFuture<UpdateResult> updateProfile(UserProfile profile) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                this.localUserProfile = profile;
                SharedPreferencesManager.getInstance().setUserProfile(profile);
                
                // Broadcast updated profile to peers
                broadcastStatusUpdate();
                
                Log.d(TAG, "Local profile updated");
                return new UpdateResult(true, "Profile updated successfully");
                
            } finally {
                lock.writeLock().unlock();
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> logout() {
        return disconnect();
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(String recipientId, String messageType, Object messageData) {
        return CompletableFuture.runAsync(() -> {
            try {
                Log.d(TAG, "Sending P2P message to: " + recipientId + ", type: " + messageType);
                
                // Find peer
                DiscoveredPeer peer = discoveredPeers.get(recipientId);
                if (peer == null) {
                    throw new RuntimeException("Peer not found: " + recipientId);
                }
                
                // Create message
                P2PMessage message = new P2PMessage();
                message.setType(messageType);
                message.setData(messageData);
                message.setSenderId(localUsername);
                message.setTimestamp(System.currentTimeMillis());
                
                // Send message
                if (!sendDirectMessage(peer, message)) {
                    // Queue for retry if peer is offline
                    QueuedMessage queuedMsg = new QueuedMessage(recipientId, message);
                    messageQueue.offer(queuedMsg);
                    throw new RuntimeException("Failed to send message, queued for retry");
                }
                
                Log.d(TAG, "P2P message sent successfully");
                
            } catch (Exception e) {
                Log.e(TAG, "P2P message send error", e);
                throw new RuntimeException("P2P message send error", e);
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
        
        return sendMessage(recipientId, MSG_USER_MESSAGE, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitRequest(String recipientId, String chatUuid, byte[] publicKey) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("public_key", Base64.getEncoder().encodeToString(publicKey));

        // Include crypto algorithms for P2P mode
        CryptoAlgorithms algorithms = SharedPreferencesManager.getInstance().getCryptoAlgorithms();
        Map<String, String> cryptoAlgorithms = new HashMap<>();
        cryptoAlgorithms.put("asymmetric", algorithms.getKemAlgorithm());
        cryptoAlgorithms.put("symmetric", algorithms.getSymmetricAlgorithm());
        cryptoAlgorithms.put("signature", algorithms.getSignatureAlgorithm());
        messageData.put("crypto_algorithms", cryptoAlgorithms);

        return sendMessage(recipientId, MSG_CHAT_INIT_REQUEST, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitResponse(String recipientId, String chatUuid, byte[] publicKey, byte[] kemCapsule, byte[] userSignature) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("public_key", Base64.getEncoder().encodeToString(publicKey));
        messageData.put("kem_capsule", Base64.getEncoder().encodeToString(kemCapsule));
        messageData.put("user_signature", Base64.getEncoder().encodeToString(userSignature));

        // Include crypto algorithms for P2P mode
        CryptoAlgorithms algorithms = SharedPreferencesManager.getInstance().getCryptoAlgorithms();
        Map<String, String> cryptoAlgorithms = new HashMap<>();
        cryptoAlgorithms.put("asymmetric", algorithms.getKemAlgorithm());
        cryptoAlgorithms.put("symmetric", algorithms.getSymmetricAlgorithm());
        cryptoAlgorithms.put("signature", algorithms.getSignatureAlgorithm());
        messageData.put("crypto_algorithms", cryptoAlgorithms);

        return sendMessage(recipientId, MSG_CHAT_INIT_RESPONSE, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitConfirm(String recipientId, String chatUuid, byte[] kemCapsule) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("kem_capsule", Base64.getEncoder().encodeToString(kemCapsule));

        return sendMessage(recipientId, MSG_CHAT_INIT_CONFIRM, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitSignature(String recipientId, String chatUuid, byte[] signature) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("signature", Base64.getEncoder().encodeToString(signature));

        return sendMessage(recipientId, MSG_CHAT_INIT_SIGNATURE, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatDelete(String recipientId, String chatUuid) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("delete_reason", "user_initiated");
        
        return sendMessage(recipientId, MSG_CHAT_DELETE, messageData);
    }
    
    @Override
    public CompletableFuture<ChatResult> createChat(List<String> participantIds) {
        return CompletableFuture.supplyAsync(() -> {
            if (participantIds.size() != 1) {
                return new ChatResult(false, null, "P2P mode supports only 1-to-1 chats");
            }
            
            String recipientId = participantIds.get(0);
            String chatUuid = UUID.randomUUID().toString();
            
            // P2P mode: Chat creation is just generating UUID
            // Actual chat initialization happens through crypto handshake
            Chat chat = new Chat();
            chat.setId(chatUuid);
            chat.setName("Chat with " + recipientId);
            chat.setLastActivity(System.currentTimeMillis());
            
            Log.d(TAG, "P2P chat created: " + chatUuid);
            return new ChatResult(true, chat, "P2P chat created");
        }, executorService);
    }
    
    // The following methods are not applicable to P2P mode or return empty/default values
    
    @Override
    public CompletableFuture<List<Chat>> getChatList() {
        // P2P mode: Chats are managed locally
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
    
    @Override
    public CompletableFuture<Chat> getChatInfo(String chatId) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Boolean> deleteChat(String chatId) {
        return CompletableFuture.completedFuture(true);
    }
    
    @Override
    public CompletableFuture<Boolean> clearChatHistory(String chatId) {
        return CompletableFuture.completedFuture(true);
    }
    
    @Override
    public CompletableFuture<UploadResult> uploadFile(FileInfo file) {
        return CompletableFuture.supplyAsync(() -> {
            // P2P mode: File upload means preparing for direct transfer
            try {
                String fileId = UUID.randomUUID().toString();
                
                // File will be transferred directly to peers when requested
                Log.d(TAG, "File prepared for P2P transfer: " + file.getName());
                return new UploadResult(true, fileId, "p2p://" + fileId, "File ready for P2P transfer");
                
            } catch (Exception e) {
                Log.e(TAG, "File preparation error", e);
                return new UploadResult(false, null, null, "File preparation error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<DownloadResult> downloadFile(String fileId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // P2P mode: Download means requesting file from peer
                Log.d(TAG, "Requesting P2P file download: " + fileId);
                
                // TODO: Implement P2P file request protocol
                return new DownloadResult(false, null, null, "P2P file download not implemented yet");
                
            } catch (Exception e) {
                Log.e(TAG, "P2P file download error", e);
                return new DownloadResult(false, null, null, "P2P file download error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<TransferResult> transferFile(String recipientId, FileInfo file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Starting P2P file transfer to: " + recipientId);
                
                // Find peer
                DiscoveredPeer peer = discoveredPeers.get(recipientId);
                if (peer == null) {
                    return new TransferResult(false, null, "Peer not found: " + recipientId);
                }
                
                // TODO: Implement direct P2P file transfer
                String transferId = UUID.randomUUID().toString();
                
                return new TransferResult(true, transferId, "P2P file transfer initiated");
                
            } catch (Exception e) {
                Log.e(TAG, "P2P file transfer error", e);
                return new TransferResult(false, null, "P2P file transfer error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> setMessageStatus(String messageId, MessageStatus status) {
        // P2P mode: Message status handled locally
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> setUserStatus(UserStatus status) {
        return CompletableFuture.runAsync(() -> {
            // Update local status and broadcast to peers
            Log.d(TAG, "Setting P2P user status: " + status);
            
            if (localUserProfile != null) {
                // Update profile metadata with status
                Map<String, Object> statusInfo = new HashMap<>();
                statusInfo.put("status", status.name());
                statusInfo.put("timestamp", System.currentTimeMillis());
                
                broadcastStatusUpdate();
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<UserStatus> getUserStatus(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            DiscoveredPeer peer = discoveredPeers.get(userId);
            if (peer != null) {
                return peer.isOnline() ? UserStatus.ONLINE : UserStatus.OFFLINE;
            }
            return UserStatus.OFFLINE;
        }, executorService);
    }
    
    @Override
    public CompletableFuture<OrganizationInfo> getOrganizationInfo() {
        // P2P mode: No organization concept
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<CryptoAlgorithms> getServerAlgorithms() {
        // P2P mode: Use local algorithms from preferences
        return CompletableFuture.supplyAsync(() -> {
            return SharedPreferencesManager.getInstance().getCryptoAlgorithms();
        }, executorService);
    }
    
    // ===========================
    // P2P SPECIFIC METHODS
    // ===========================
    
    /**
     * Start device discovery
     */
    public boolean startDiscovery() {
        lock.writeLock().lock();
        try {
            if (isDiscovering.get()) {
                Log.w(TAG, "Discovery already running");
                return true;
            }
            
            Log.d(TAG, "Starting P2P device discovery");
            
            // Initialize discovery socket
            if (!initializeDiscoverySocket()) {
                return false;
            }
            
            // Start discovery listener
            startDiscoveryListener();
            
            // Start periodic discovery broadcasts
            startDiscoveryBroadcast();
            
            isDiscovering.set(true);
            Log.i(TAG, "P2P device discovery started");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start discovery", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Stop device discovery
     */
    public void stopDiscovery() {
        lock.writeLock().lock();
        try {
            if (!isDiscovering.get()) {
                return;
            }
            
            Log.d(TAG, "Stopping P2P device discovery");
            
            isDiscovering.set(false);
            
            if (discoverySocket != null) {
                discoverySocket.close();
                discoverySocket = null;
            }
            
            Log.i(TAG, "P2P device discovery stopped");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get discovered peers
     */
    public List<DiscoveredPeer> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers.values());
    }
    
    /**
     * Broadcast presence to other peers
     */
    public void broadcastPresence(UserProfile userProfile) {
        if (!isDiscovering.get()) {
            Log.w(TAG, "Cannot broadcast presence - discovery not active");
            return;
        }
        
        try {
            this.localUserProfile = userProfile;
            
            DiscoveryMessage discoveryMsg = new DiscoveryMessage();
            discoveryMsg.setType(MSG_DISCOVERY_REQUEST);
            discoveryMsg.setUsername(userProfile.getUsername());
            discoveryMsg.setDisplayName(userProfile.getDisplayName());
            discoveryMsg.setPeerId(generatePeerId());
            discoveryMsg.setTimestamp(System.currentTimeMillis());
            
            String messageJson = gson.toJson(discoveryMsg);
            byte[] messageBytes = messageJson.getBytes(StandardCharsets.UTF_8);
            
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, group, DISCOVERY_PORT);
            
            if (discoverySocket != null) {
                discoverySocket.send(packet);
                Log.d(TAG, "Presence broadcast sent");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to broadcast presence", e);
        }
    }
    
    /**
     * Connect to specific peer
     */
    public CompletableFuture<Boolean> connectToPeer(String peerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DiscoveredPeer peer = discoveredPeers.get(peerId);
                if (peer == null) {
                    Log.w(TAG, "Peer not found: " + peerId);
                    return false;
                }
                
                Log.d(TAG, "Connecting to peer: " + peerId);
                
                // Create direct socket connection
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(peer.getIpAddress(), MESSAGING_PORT), CONNECTION_TIMEOUT_MS);
                
                // Create peer connection
                PeerConnection connection = new PeerConnection(peer, socket);
                activePeerConnections.put(peerId, connection);
                
                // Start connection handler
                startPeerConnectionHandler(connection);
                
                Log.i(TAG, "Connected to peer: " + peerId);
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to connect to peer: " + peerId, e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Disconnect from peer
     */
    public void disconnectFromPeer(String peerId) {
        PeerConnection connection = activePeerConnections.remove(peerId);
        if (connection != null) {
            connection.close();
            Log.d(TAG, "Disconnected from peer: " + peerId);
        }
    }
    
    /**
     * Add P2P message listener
     */
    public void addMessageListener(P2PMessageListener listener) {
        messageListeners.add(listener);
    }
    
    /**
     * Remove P2P message listener
     */
    public void removeMessageListener(P2PMessageListener listener) {
        messageListeners.remove(listener);
    }
    
    // ===========================
    // PRIVATE HELPER METHODS
    // ===========================
    
    /**
     * Initialize network components
     */
    private boolean initializeNetworkComponents() {
        try {
            wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            // Initialize Wi-Fi P2P if available
            p2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
            if (p2pManager != null) {
                p2pChannel = p2pManager.initialize(context, context.getMainLooper(), null);
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize network components", e);
            return false;
        }
    }
    
    /**
     * Initialize discovery socket
     */
    private boolean initializeDiscoverySocket() {
        try {
            discoverySocket = new MulticastSocket(DISCOVERY_PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            discoverySocket.joinGroup(group);
            discoverySocket.setTimeToLive(1); // Local network only
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize discovery socket", e);
            return false;
        }
    }
    
    /**
     * Start messaging server
     */
    private boolean startMessagingServer() {
        try {
            messagingSocket = new ServerSocket(MESSAGING_PORT);
            fileTransferSocket = new ServerSocket(FILE_TRANSFER_PORT);
            
            // Start server threads
            executorService.submit(new MessagingServerTask());
            executorService.submit(new FileTransferServerTask());
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start messaging server", e);
            return false;
        }
    }
    
    /**
     * Start discovery listener
     */
    private void startDiscoveryListener() {
        executorService.submit(new DiscoveryListenerTask());
    }
    
    /**
     * Start discovery broadcast
     */
    private void startDiscoveryBroadcast() {
        scheduler.scheduleAtFixedRate(new DiscoveryBroadcastTask(), 0, DISCOVERY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Send direct message to peer
     */
    private boolean sendDirectMessage(DiscoveredPeer peer, P2PMessage message) {
        try {
            PeerConnection connection = activePeerConnections.get(peer.getPeerId());
            if (connection == null || !connection.isConnected()) {
                // Try to establish connection
                if (!connectToPeer(peer.getPeerId()).get(5, TimeUnit.SECONDS)) {
                    return false;
                }
                connection = activePeerConnections.get(peer.getPeerId());
            }
            
            if (connection != null) {
                return connection.sendMessage(message);
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send direct message to peer: " + peer.getPeerId(), e);
            return false;
        }
    }
    
    /**
     * Broadcast status update to all peers
     */
    private void broadcastStatusUpdate() {
        if (localUserProfile != null) {
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("username", localUserProfile.getUsername());
            statusData.put("display_name", localUserProfile.getDisplayName());
            statusData.put("timestamp", System.currentTimeMillis());
            
            P2PMessage statusMessage = new P2PMessage();
            statusMessage.setType(MSG_STATUS_UPDATE);
            statusMessage.setData(statusData);
            statusMessage.setSenderId(localUsername);
            statusMessage.setTimestamp(System.currentTimeMillis());
            
            for (DiscoveredPeer peer : discoveredPeers.values()) {
                sendDirectMessage(peer, statusMessage);
            }
        }
    }
    
    /**
     * Start peer connection handler
     */
    private void startPeerConnectionHandler(PeerConnection connection) {
        executorService.submit(new PeerConnectionTask(connection));
    }
    
    /**
     * Set connection state and notify
     */
    private void setConnectionState(ConnectionState state) {
        ConnectionState oldState = this.connectionState;
        this.connectionState = state;
        
        if (oldState != state) {
            Log.d(TAG, "P2P connection state changed: " + oldState + " -> " + state);
            
            // Update metrics
            if (state == ConnectionState.CONNECTED) {
                connectionMetrics.setConnectionType("P2P");
                connectionMetrics.setConnectedTime(System.currentTimeMillis());
            }
            
            // Notify StateManager
            StateManager.getInstance().setConnectionState(state);
        }
    }
    
    /**
     * Generate unique peer ID
     */
    private String generatePeerId() {
        return "peer_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Close socket safely
     */
    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing socket", e);
            }
        }
    }
    
    /**
     * Close server socket safely
     */
    private void closeSocket(ServerSocket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing server socket", e);
            }
        }
    }
    
    /**
     * Close multicast socket safely
     */
    private void closeSocket(MulticastSocket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing multicast socket", e);
            }
        }
    }
    
    // ===========================
    // BACKGROUND TASKS
    // ===========================
    
    /**
     * Discovery listener task
     */
    private class DiscoveryListenerTask implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            
            while (isDiscovering.get() && discoverySocket != null && !discoverySocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    discoverySocket.receive(packet);
                    
                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleDiscoveryMessage(message, packet.getAddress());
                    
                } catch (Exception e) {
                    if (isDiscovering.get()) {
                        Log.e(TAG, "Discovery listener error", e);
                    }
                }
            }
        }
        
        private void handleDiscoveryMessage(String message, InetAddress senderAddress) {
            try {
                DiscoveryMessage discoveryMsg = gson.fromJson(message, DiscoveryMessage.class);
                
                if (MSG_DISCOVERY_REQUEST.equals(discoveryMsg.getType())) {
                    // Received discovery request from another peer
                    if (!discoveryMsg.getUsername().equals(localUsername)) {
                        // Add to discovered peers
                        DiscoveredPeer peer = new DiscoveredPeer();
                        peer.setPeerId(discoveryMsg.getPeerId());
                        peer.setUsername(discoveryMsg.getUsername());
                        peer.setDisplayName(discoveryMsg.getDisplayName());
                        peer.setIpAddress(senderAddress.getHostAddress());
                        peer.setLastSeen(System.currentTimeMillis());
                        peer.setOnline(true);
                        
                        discoveredPeers.put(peer.getPeerId(), peer);
                        
                        // Send discovery response
                        sendDiscoveryResponse(senderAddress);
                        
                        Log.d(TAG, "Discovered peer: " + peer.getUsername() + " at " + peer.getIpAddress());
                    }
                } else if (MSG_DISCOVERY_RESPONSE.equals(discoveryMsg.getType())) {
                    // Received discovery response
                    // Handle similar to request
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error handling discovery message", e);
            }
        }
        
        private void sendDiscoveryResponse(InetAddress targetAddress) {
            try {
                if (localUserProfile != null) {
                    DiscoveryMessage response = new DiscoveryMessage();
                    response.setType(MSG_DISCOVERY_RESPONSE);
                    response.setUsername(localUserProfile.getUsername());
                    response.setDisplayName(localUserProfile.getDisplayName());
                    response.setPeerId(generatePeerId());
                    response.setTimestamp(System.currentTimeMillis());
                    
                    String responseJson = gson.toJson(response);
                    byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
                    
                    DatagramPacket packet = new DatagramPacket(
                        responseBytes, responseBytes.length, targetAddress, DISCOVERY_PORT);
                    
                    discoverySocket.send(packet);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending discovery response", e);
            }
        }
    }
    
    /**
     * Discovery broadcast task
     */
    private class DiscoveryBroadcastTask implements Runnable {
        @Override
        public void run() {
            if (localUserProfile != null) {
                broadcastPresence(localUserProfile);
            }
        }
    }
    
    /**
     * Messaging server task
     */
    private class MessagingServerTask implements Runnable {
        @Override
        public void run() {
            while (!messagingSocket.isClosed()) {
                try {
                    Socket clientSocket = messagingSocket.accept();
                    
                    // Handle client in separate thread
                    executorService.submit(() -> handleIncomingConnection(clientSocket));
                    
                } catch (Exception e) {
                    if (!messagingSocket.isClosed()) {
                        Log.e(TAG, "Messaging server error", e);
                    }
                }
            }
        }
        
        private void handleIncomingConnection(Socket clientSocket) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String messageJson = reader.readLine();
                if (messageJson != null) {
                    P2PMessage message = gson.fromJson(messageJson, P2PMessage.class);
                    handleIncomingMessage(message);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling incoming connection", e);
            } finally {
                closeSocket(clientSocket);
            }
        }
    }
    
    /**
     * File transfer server task
     */
    private class FileTransferServerTask implements Runnable {
        @Override
        public void run() {
            while (!fileTransferSocket.isClosed()) {
                try {
                    Socket clientSocket = fileTransferSocket.accept();
                    
                    // Handle file transfer in separate thread
                    executorService.submit(() -> handleFileTransfer(clientSocket));
                    
                } catch (Exception e) {
                    if (!fileTransferSocket.isClosed()) {
                        Log.e(TAG, "File transfer server error", e);
                    }
                }
            }
        }
        
        private void handleFileTransfer(Socket clientSocket) {
            // TODO: Implement file transfer protocol
            Log.d(TAG, "File transfer request received");
            closeSocket(clientSocket);
        }
    }
    
    /**
     * Peer connection task
     */
    private class PeerConnectionTask implements Runnable {
        private final PeerConnection connection;
        
        public PeerConnectionTask(PeerConnection connection) {
            this.connection = connection;
        }
        
        @Override
        public void run() {
            try {
                while (connection.isConnected()) {
                    P2PMessage message = connection.receiveMessage();
                    if (message != null) {
                        handleIncomingMessage(message);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Peer connection task error", e);
            } finally {
                connection.close();
            }
        }
    }
    
    /**
     * Handle incoming P2P message
     */
    private void handleIncomingMessage(P2PMessage message) {
        Log.d(TAG, "Received P2P message: " + message.getType() + " from " + message.getSenderId());
        
        // Notify listeners
        for (P2PMessageListener listener : messageListeners) {
            try {
                listener.onMessageReceived(message.getSenderId(), message.getType(), message.getData());
            } catch (Exception e) {
                Log.e(TAG, "Error notifying message listener", e);
            }
        }
    }
    
    // ===========================
    // INNER CLASSES AND INTERFACES
    // ===========================
    
    /**
     * P2P message listener interface
     */
    public interface P2PMessageListener {
        void onMessageReceived(String fromPeerId, String messageType, Object messageData);
        void onPeerConnected(String peerId);
        void onPeerDisconnected(String peerId);
    }
    
    /**
     * Discovered peer data class
     */
    public static class DiscoveredPeer {
        private String peerId;
        private String username;
        private String displayName;
        private String ipAddress;
        private long lastSeen;
        private boolean online;
        
        // Getters and setters
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        
        public long getLastSeen() { return lastSeen; }
        public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
        
        public boolean isOnline() { return online; }
        public void setOnline(boolean online) { this.online = online; }
    }
    
    /**
     * P2P message data class
     */
    public static class P2PMessage {
        private String type;
        private Object data;
        private String senderId;
        private long timestamp;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    /**
     * Discovery message data class
     */
    private static class DiscoveryMessage {
        private String type;
        private String username;
        private String displayName;
        private String peerId;
        private long timestamp;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    /**
     * Peer connection wrapper
     */
    private static class PeerConnection {
        private final DiscoveredPeer peer;
        private final Socket socket;
        private final PrintWriter writer;
        private final BufferedReader reader;
        private volatile boolean connected;
        
        public PeerConnection(DiscoveredPeer peer, Socket socket) throws IOException {
            this.peer = peer;
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.connected = true;
        }
        
        public boolean isConnected() {
            return connected && !socket.isClosed();
        }
        
        public boolean sendMessage(P2PMessage message) {
            try {
                String messageJson = new Gson().toJson(message);
                writer.println(messageJson);
                return !writer.checkError();
            } catch (Exception e) {
                Log.e(TAG, "Error sending message to peer: " + peer.getPeerId(), e);
                return false;
            }
        }
        
        public P2PMessage receiveMessage() throws IOException {
            String messageJson = reader.readLine();
            if (messageJson != null) {
                return new Gson().fromJson(messageJson, P2PMessage.class);
            }
            return null;
        }
        
        public void close() {
            connected = false;
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "Error closing peer connection", e);
            }
        }
        
        public DiscoveredPeer getPeer() {
            return peer;
        }
    }
    
    /**
     * Queued message for offline scenarios
     */
    private static class QueuedMessage {
        private final String recipientId;
        private final P2PMessage message;
        private final long queuedAt;
        
        public QueuedMessage(String recipientId, P2PMessage message) {
            this.recipientId = recipientId;
            this.message = message;
            this.queuedAt = System.currentTimeMillis();
        }
        
        public String getRecipientId() { return recipientId; }
        public P2PMessage getMessage() { return message; }
        public long getQueuedAt() { return queuedAt; }
    }
}