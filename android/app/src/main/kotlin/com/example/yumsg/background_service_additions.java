// Add these methods to BackgroundService.java for UIBridge integration

    // ===========================
    // UIBRIDGE INTEGRATION METHODS
    // ===========================
    
    /**
     * Get NetworkManager instance for UIBridge
     */
    public NetworkManager getNetworkManager() {
        lock.readLock().lock();
        try {
            return networkManager;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get DatabaseManager instance for UIBridge
     */
    public DatabaseManager getDatabaseManager() {
        lock.readLock().lock();
        try {
            return databaseManager;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get CryptoManager instance for UIBridge
     */
    public CryptoManager getCryptoManager() {
        lock.readLock().lock();
        try {
            return cryptoManager;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get StateManager instance for UIBridge
     */
    public StateManager getStateManager() {
        lock.readLock().lock();
        try {
            return stateManager;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get SessionManager instance for UIBridge
     */
    public SessionManager getSessionManager() {
        lock.readLock().lock();
        try {
            return sessionManager;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get SharedPreferencesManager instance for UIBridge
     */
    public SharedPreferencesManager getPreferencesManager() {
        lock.readLock().lock();
        try {
            return preferencesManager;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // FLUTTER COMMUNICATION HELPERS
    // ===========================
    
    /**
     * Handle UI method call from Flutter
     * This method routes Flutter method calls to appropriate BackgroundService methods
     */
    public CompletableFuture<Map<String, Object>> handleUIMethod(String method, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Handling UI method: " + method);
                
                switch (method) {
                    case "selectMode":
                        String modeStr = (String) arguments.get("mode");
                        AppMode mode = AppMode.valueOf(modeStr.toUpperCase());
                        return handleSelectModeUI(mode);
                        
                    case "connectToServer":
                        String host = (String) arguments.get("host");
                        Integer port = (Integer) arguments.get("port");
                        String orgName = (String) arguments.get("organizationName");
                        return handleConnectToServerUI(host, port, orgName);
                        
                    case "authenticateUser":
                        String username = (String) arguments.get("username");
                        String password = (String) arguments.get("password");
                        String email = (String) arguments.get("email");
                        return handleAuthenticateUserUI(username, password, email);
                        
                    case "setLocalUser":
                        String localUsername = (String) arguments.get("username");
                        return handleSetLocalUserUI(localUsername);
                        
                    case "initializeChat":
                        String recipientId = (String) arguments.get("recipientId");
                        return handleInitializeChatUI(recipientId);
                        
                    case "sendMessage":
                        String chatId = (String) arguments.get("chatId");
                        String messageText = (String) arguments.get("messageText");
                        return handleSendMessageUI(chatId, messageText);
                        
                    case "sendFile":
                        String fileChatId = (String) arguments.get("chatId");
                        String filePath = (String) arguments.get("filePath");
                        return handleSendFileUI(fileChatId, filePath);
                        
                    case "searchUsers":
                        String query = (String) arguments.get("query");
                        return handleSearchUsersUI(query);
                        
                    case "updateCryptoAlgorithms":
                        String kemAlg = (String) arguments.get("kemAlgorithm");
                        String symAlg = (String) arguments.get("symmetricAlgorithm");
                        String sigAlg = (String) arguments.get("signatureAlgorithm");
                        return handleUpdateCryptoAlgorithmsUI(kemAlg, symAlg, sigAlg);
                        
                    case "changeTheme":
                        String themeName = (String) arguments.get("theme");
                        return handleChangeThemeUI(themeName);
                        
                    case "logout":
                        return handleLogoutUI();
                        
                    default:
                        throw new IllegalArgumentException("Unknown UI method: " + method);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error handling UI method: " + method, e);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", e.getMessage());
                return errorResponse;
            }
        }, executorService);
    }
    
    // ===========================
    // UI METHOD HANDLERS
    // ===========================
    
    private Map<String, Object> handleSelectModeUI(AppMode mode) {
        try {
            boolean success = selectAppMode(mode).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Mode selected successfully" : "Failed to select mode");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleConnectToServerUI(String host, int port, String organizationName) {
        try {
            ConnectionResult result = connectToServer(host, port, organizationName).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleAuthenticateUserUI(String username, String password, String email) {
        try {
            AuthResult result = authenticateUser(username, password, email).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("token", result.getToken());
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleSetLocalUserUI(String username) {
        try {
            boolean success = setLocalUser(username).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Local user set successfully" : "Failed to set local user");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleInitializeChatUI(String recipientId) {
        try {
            boolean success = initializeChat(recipientId).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Chat initialized successfully" : "Failed to initialize chat");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleSendMessageUI(String chatId, String messageText) {
        try {
            boolean success = sendMessage(chatId, messageText).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Message sent successfully" : "Failed to send message");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleSendFileUI(String chatId, String filePath) {
        try {
            boolean success = sendFile(chatId, filePath).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "File sent successfully" : "Failed to send file");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleSearchUsersUI(String query) {
        try {
            List<User> users = searchUsers(query).get();
            
            List<Map<String, Object>> userList = new ArrayList<>();
            for (User user : users) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("status", user.getStatus() != null ? user.getStatus().name() : "OFFLINE");
                userList.add(userMap);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("users", userList);
            response.put("message", "Found " + users.size() + " users");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleUpdateCryptoAlgorithmsUI(String kemAlg, String symAlg, String sigAlg) {
        try {
            boolean success = updateCryptoAlgorithms(kemAlg, symAlg, sigAlg).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Crypto algorithms updated successfully" : "Failed to update crypto algorithms");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleChangeThemeUI(String themeName) {
        try {
            boolean success = changeTheme(themeName).get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Theme changed successfully" : "Failed to change theme");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    private Map<String, Object> handleLogoutUI() {
        try {
            boolean success = logout().get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Logout successful" : "Failed to logout");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    // ===========================
    // UI EVENT BROADCASTING
    // ===========================
    
    /**
     * Broadcast event to UI
     */
    public void broadcastUIEvent(String eventType, Map<String, Object> eventData) {
        if (uiBridge != null) {
            uiBridge.sendEventToUI(eventType, eventData);
        }
    }
    
    /**
     * Broadcast error to UI
     */
    public void broadcastError(String errorCode, String errorMessage, String errorDetails) {
        if (uiBridge != null) {
            AppError error = new AppError(errorCode, errorMessage, errorDetails, null);
            uiBridge.onErrorOccurred(error);
        }
    }
    
    /**
     * Broadcast progress update to UI
     */
    public void broadcastProgress(String taskId, int percentage, String status) {
        if (uiBridge != null) {
            Progress progress = new Progress(taskId, percentage, status);
            uiBridge.onProgressUpdate(progress);
        }
    }
    
    // ===========================
    // COMPONENT STATUS METHODS
    // ===========================
    
    /**
     * Get comprehensive system status for UI
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // App state
        status.put("appState", getCurrentState().name());
        status.put("appMode", getAppMode() != null ? getAppMode().name() : null);
        
        // Connection state
        status.put("connectionState", stateManager.getConnectionState().name());
        status.put("isConnected", networkManager != null && networkManager.isConnected());
        
        // Session state
        status.put("isSessionValid", sessionManager.isSessionValid());
        status.put("userId", sessionManager.getUserId());
        status.put("organizationId", sessionManager.getOrganizationId());
        
        // Component initialization status
        status.put("isBackgroundServiceInitialized", isInitialized.get());
        status.put("isDatabaseInitialized", databaseManager.isInitialized());
        status.put("isCryptoInitialized", cryptoManager.isInitialized());
        status.put("isPreferencesInitialized", preferencesManager.isInitialized());
        
        // Timestamps
        status.put("lastActiveTime", stateManager.getLastActiveTime());
        status.put("timestamp", System.currentTimeMillis());
        
        return status;
    }
    
    /**
     * Get detailed connection metrics for UI
     */
    public Map<String, Object> getDetailedConnectionMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        if (networkManager != null) {
            ConnectionMetrics connMetrics = networkManager.getConnectionMetrics();
            metrics.put("latency", connMetrics.getLatency());
            metrics.put("bandwidth", connMetrics.getBandwidth());
            metrics.put("packetLoss", connMetrics.getPacketLoss());
            metrics.put("connectionType", connMetrics.getConnectionType());
            metrics.put("connectedTime", connMetrics.getConnectedTime());
            metrics.put("isConnected", networkManager.isConnected());
        }
        
        metrics.put("connectionState", stateManager.getConnectionState().name());
        metrics.put("isRestoringConnection", stateManager.isRestoringConnection());
        metrics.put("timestamp", System.currentTimeMillis());
        
        return metrics;
    }
    
    /**
     * Validate component integrity
     */
    public Map<String, Object> validateComponentIntegrity() {
        Map<String, Object> validation = new HashMap<>();
        
        // Check all components
        validation.put("backgroundService", isInitialized.get());
        validation.put("databaseManager", databaseManager != null && databaseManager.isInitialized());
        validation.put("cryptoManager", cryptoManager != null && cryptoManager.isInitialized());
        validation.put("stateManager", stateManager != null);
        validation.put("sessionManager", sessionManager != null);
        validation.put("preferencesManager", preferencesManager != null && preferencesManager.isInitialized());
        validation.put("networkManager", networkManager != null);
        validation.put("uiBridge", uiBridge != null);
        
        // Overall integrity
        boolean allValid = validation.values().stream()
            .allMatch(value -> Boolean.TRUE.equals(value));
        validation.put("overallIntegrity", allValid);
        validation.put("timestamp", System.currentTimeMillis());
        
        return validation;
    }
    
    // ===========================
    // DEBUGGING AND MONITORING
    // ===========================
    
    /**
     * Get debug information for troubleshooting
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> debug = new HashMap<>();
        
        // System status
        debug.put("systemStatus", getSystemStatus());
        debug.put("componentIntegrity", validateComponentIntegrity());
        debug.put("connectionMetrics", getDetailedConnectionMetrics());
        
        // State information
        debug.put("stateSummary", stateManager.getStateSummary());
        debug.put("sessionSummary", sessionManager.getSessionSummary());
        
        // Active tasks and operations
        debug.put("activeTasksCount", activeTasks.size());
        debug.put("pendingOperationsCount", pendingOperations.size());
        
        // Memory and performance
        Runtime runtime = Runtime.getRuntime();
        debug.put("memoryUsed", runtime.totalMemory() - runtime.freeMemory());
        debug.put("memoryTotal", runtime.totalMemory());
        debug.put("memoryMax", runtime.maxMemory());
        
        debug.put("timestamp", System.currentTimeMillis());
        
        return debug;
    }
    
    /**
     * Perform system health check
     */
    public Map<String, Object> performHealthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Check component health
            boolean isHealthy = true;
            List<String> issues = new ArrayList<>();
            
            // Check BackgroundService
            if (!isInitialized.get()) {
                isHealthy = false;
                issues.add("BackgroundService not initialized");
            }
            
            // Check database connection
            if (databaseManager == null || !databaseManager.isInitialized()) {
                isHealthy = false;
                issues.add("DatabaseManager not available");
            }
            
            // Check crypto manager
            if (cryptoManager == null || !cryptoManager.isInitialized()) {
                isHealthy = false;
                issues.add("CryptoManager not available");
            }
            
            // Check network connectivity if needed
            if (preferencesManager.getAppMode() == AppMode.SERVER) {
                if (networkManager == null || !networkManager.isConnected()) {
                    issues.add("Network connection not available");
                }
            }
            
            // Check session validity if needed
            if (sessionManager != null && !sessionManager.isSessionValid()) {
                issues.add("Session not valid");
            }
            
            health.put("isHealthy", isHealthy);
            health.put("issues", issues);
            health.put("checkTime", System.currentTimeMillis());
            
        } catch (Exception e) {
            health.put("isHealthy", false);
            health.put("error", e.getMessage());
            health.put("checkTime", System.currentTimeMillis());
        }
        
        return health;
    }