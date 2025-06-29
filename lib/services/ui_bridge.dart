import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';

/// UIBridge - Flutter-side implementation for native communication
/// 
/// Provides comprehensive interface for Flutter-Android communication through:
/// - MethodChannel for Flutter->Android synchronous calls
/// - EventChannel for Android->Flutter event streaming
/// 
/// Thread-safe singleton implementation with proper lifecycle management
class UIBridge {
  static const String _methodChannel = 'com.example.yumsg/methods';
  static const String _eventChannel = 'com.example.yumsg/events';
  
  // Singleton instance
  static UIBridge? _instance;
  static UIBridge get instance {
    _instance ??= UIBridge._internal();
    return _instance!;
  }
  
  // Channels
  late final MethodChannel _methodChannelInstance;
  late final EventChannel _eventChannelInstance;
  
  // Event stream
  Stream<UIEvent>? _eventStream;
  StreamSubscription? _eventSubscription;
  final StreamController<UIEvent> _eventController = StreamController<UIEvent>.broadcast();
  
  // State
  bool _isInitialized = false;
  bool get isInitialized => _isInitialized;
  
  // Event listeners
  final Map<String, List<Function(Map<String, dynamic>)>> _eventListeners = {};
  
  UIBridge._internal() {
    _methodChannelInstance = const MethodChannel(_methodChannel);
    _eventChannelInstance = const EventChannel(_eventChannel);
  }
  
  /// Initialize UIBridge and start event listening
  Future<void> initialize() async {
    if (_isInitialized) {
      print('UIBridge already initialized');
      return;
    }
    
    try {
      // Start listening to events from native
      _startEventListening();
      
      _isInitialized = true;
      print('UIBridge initialized successfully');
    } catch (e) {
      print('Failed to initialize UIBridge: $e');
      rethrow;
    }
  }
  
  /// Dispose resources
  void dispose() {
    _eventSubscription?.cancel();
    _eventController.close();
    _eventListeners.clear();
    _isInitialized = false;
  }
  
  // ===========================
  // EVENT HANDLING
  // ===========================
  
  /// Start listening to native events
  void _startEventListening() {
    _eventStream ??= _eventChannelInstance.receiveBroadcastStream().map((event) {
      final Map<String, dynamic> eventMap = Map<String, dynamic>.from(event);
      return UIEvent.fromMap(eventMap);
    });
    
    _eventSubscription = _eventStream!.listen(
      (event) {
        print('Received event: ${event.type}');
        _handleEvent(event);
        _eventController.add(event);
      },
      onError: (error) {
        print('Error in event stream: $error');
      },
    );
  }
  
  /// Handle incoming event
  void _handleEvent(UIEvent event) {
    final listeners = _eventListeners[event.type];
    if (listeners != null) {
      for (final listener in listeners) {
        try {
          listener(event.data);
        } catch (e) {
          print('Error in event listener for ${event.type}: $e');
        }
      }
    }
  }
  
  /// Register event listener
  void addEventListener(String eventType, Function(Map<String, dynamic>) listener) {
    _eventListeners[eventType] ??= [];
    _eventListeners[eventType]!.add(listener);
  }
  
  /// Remove event listener
  void removeEventListener(String eventType, Function(Map<String, dynamic>) listener) {
    _eventListeners[eventType]?.remove(listener);
  }
  
  /// Get event stream
  Stream<UIEvent> get eventStream => _eventController.stream;
  
  // ===========================
  // APP STATE & MODE METHODS
  // ===========================
  
  /// Select app mode (LOCAL or SERVER)
  Future<UIResponse> selectMode(String mode) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('selectMode', {
        'mode': mode,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to select mode: $e');
    }
  }
  
  /// Get current app state
  Future<String?> getCurrentState() async {
    try {
      final result = await _methodChannelInstance.invokeMethod('getCurrentState');
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      return response.success ? response.data['state'] : null;
    } catch (e) {
      print('Failed to get current state: $e');
      return null;
    }
  }
  
  /// Get app mode
  Future<String?> getAppMode() async {
    try {
      final result = await _methodChannelInstance.invokeMethod('getAppMode');
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      return response.success ? response.data['mode'] : null;
    } catch (e) {
      print('Failed to get app mode: $e');
      return null;
    }
  }
  
  // ===========================
  // SERVER CONNECTION & AUTH
  // ===========================
  
  /// Connect to server
  Future<UIResponse> connectToServer({
    required String host,
    required int port,
  }) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('connectToServer', {
        'host': host,
        'port': port,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to connect to server: $e');
    }
  }
  
  /// Authenticate user
  Future<UIResponse> authenticateUser({
    required String username,
    required String password,
    String? email,
  }) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('authenticateUser', {
        'username': username,
        'password': password,
        if (email != null) 'email': email,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to authenticate: $e');
    }
  }
  
  /// Register new user
  Future<UIResponse> registerUser({
    required String username,
    required String password,
    required String email,
  }) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('registerUser', {
        'username': username,
        'password': password,
        'email': email,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to register user: $e');
    }
  }

  /// Set local user (for LOCAL mode)
  Future<UIResponse> setLocalUser({required String username}) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('setLocalUser', {
        'username': username,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to set local user: $e');
    }
  }

  /// Logout user
  Future<UIResponse> logout() async {
    try {
      final result = await _methodChannelInstance.invokeMethod('logout');
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to logout: $e');
    }
  }

  /// Get organization information from server
  Future<Map<String, dynamic>?> getOrganizationInfo() async {
    try {
      final result =
          await _methodChannelInstance.invokeMethod('getOrganizationInfo');
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      return response.success ? response.data['organization'] : null;
    } catch (e) {
      print('Failed to get organization info: $e');
      return null;
    }
  }

  /// Get supported algorithms from server
  Future<Map<String, dynamic>?> getServerAlgorithms() async {
    try {
      final result =
          await _methodChannelInstance.invokeMethod('getServerAlgorithms');
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      return response.success ? response.data['algorithms'] : null;
    } catch (e) {
      print('Failed to get server algorithms: $e');
      return null;
    }
  }

  /// Generate and save organization signature keys
  Future<UIResponse> generateOrganizationKeys() async {
    try {
      final result =
          await _methodChannelInstance.invokeMethod('generateOrganizationKeys');
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to generate organization keys: $e');
    }
  }

  // ===========================
  // CHAT OPERATIONS
  // ===========================
  
  /// Initialize chat with recipient
  Future<UIResponse> initializeChat({required String recipientId}) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('initializeChat', {
        'recipientId': recipientId,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to initialize chat: $e');
    }
  }
  
  /// Send message
  Future<UIResponse> sendMessage({
    required String chatId,
    required String content,
    String? replyToId,
  }) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('sendMessage', {
        'chatId': chatId,
        'content': content,
        if (replyToId != null) 'replyToId': replyToId,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to send message: $e');
    }
  }
  
  /// Send file
  Future<UIResponse> sendFile({
    required String chatId,
    required String filePath,
    required String fileName,
    String? caption,
  }) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('sendFile', {
        'chatId': chatId,
        'filePath': filePath,
        'fileName': fileName,
        if (caption != null) 'caption': caption,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to send file: $e');
    }
  }
  
  /// Get chat list
  Future<List<Map<String, dynamic>>> getChatList() async {
    try {
      final result = await _methodChannelInstance.invokeMethod('getChatList');
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      if (response.success) {
        final chats = response.data['chats'] as List;
        return chats.map((chat) => Map<String, dynamic>.from(chat)).toList();
      }
      return [];
    } catch (e) {
      print('Failed to get chat list: $e');
      return [];
    }
  }
  
  /// Get chat info
  Future<Map<String, dynamic>?> getChatInfo({required String chatId}) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('getChatInfo', {
        'chatId': chatId,
      });
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      return response.success ? response.data['chat'] : null;
    } catch (e) {
      print('Failed to get chat info: $e');
      return null;
    }
  }
  
  /// Delete chat
  Future<UIResponse> deleteChat({required String chatId}) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('deleteChat', {
        'chatId': chatId,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to delete chat: $e');
    }
  }
  
  /// Clear chat history
  Future<UIResponse> clearChatHistory({required String chatId}) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('clearChatHistory', {
        'chatId': chatId,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to clear chat history: $e');
    }
  }
  
  // ===========================
  // USER MANAGEMENT
  // ===========================
  
  /// Search users
  Future<List<Map<String, dynamic>>> searchUsers({required String query}) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('searchUsers', {
        'query': query,
      });
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      if (response.success) {
        final users = response.data['users'] as List;
        return users.map((user) => Map<String, dynamic>.from(user)).toList();
      }
      return [];
    } catch (e) {
      print('Failed to search users: $e');
      return [];
    }
  }
  
  /// Get user info
  Future<Map<String, dynamic>?> getUserInfo({required String userId}) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('getUserInfo', {
        'userId': userId,
      });
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      return response.success ? response.data['user'] : null;
    } catch (e) {
      print('Failed to get user info: $e');
      return null;
    }
  }
  
  /// Update user profile
  Future<UIResponse> updateUserProfile({
    String? displayName,
    String? status,
    String? avatarPath,
  }) async {
    try {
      final params = <String, dynamic>{};
      if (displayName != null) params['displayName'] = displayName;
      if (status != null) params['status'] = status;
      if (avatarPath != null) params['avatarPath'] = avatarPath;
      
      final result = await _methodChannelInstance.invokeMethod('updateUserProfile', params);
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to update profile: $e');
    }
  }
  
  // ===========================
  // MESSAGES
  // ===========================
  
  /// Get messages
  Future<List<Map<String, dynamic>>> getMessages({
    required String chatId,
    int limit = 50,
    String? beforeId,
  }) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('getMessages', {
        'chatId': chatId,
        'limit': limit,
        if (beforeId != null) 'beforeId': beforeId,
      });
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      if (response.success) {
        final messages = response.data['messages'] as List;
        return messages.map((msg) => Map<String, dynamic>.from(msg)).toList();
      }
      return [];
    } catch (e) {
      print('Failed to get messages: $e');
      return [];
    }
  }
  
  /// Mark message as read
  Future<UIResponse> markMessageAsRead({
    required String chatId,
    required String messageId,
  }) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('markMessageAsRead', {
        'chatId': chatId,
        'messageId': messageId,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to mark message as read: $e');
    }
  }
  
  /// Send typing status
  Future<UIResponse> sendTypingStatus({
    required String chatId,
    required bool isTyping,
  }) async {
    try {
      final result = await _methodChannelInstance.invokeMethod('sendTypingStatus', {
        'chatId': chatId,
        'isTyping': isTyping,
      });
      return UIResponse.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      return UIResponse.error('Failed to send typing status: $e');
    }
  }
  
  // ===========================
  // CONNECTION STATUS
  // ===========================
  
  /// Get connection status
  Future<Map<String, dynamic>?> getConnectionStatus() async {
    try {
      final result = await _methodChannelInstance.invokeMethod('getConnectionStatus');
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      return response.success ? response.data['status'] : null;
    } catch (e) {
      print('Failed to get connection status: $e');
      return null;
    }
  }
  
  /// Get connection metrics
  Future<Map<String, dynamic>?> getConnectionMetrics() async {
    try {
      final result = await _methodChannelInstance.invokeMethod('getConnectionMetrics');
      final response = UIResponse.fromMap(Map<String, dynamic>.from(result));
      return response.success ? response.data['metrics'] : null;
    } catch (e) {
      print('Failed to get connection metrics: $e');
      return null;
    }
  }
}

/// UIEvent - Event from native to Flutter
class UIEvent {
  final String type;
  final Map<String, dynamic> data;
  final int timestamp;
  
  UIEvent({
    required this.type,
    required this.data,
    required this.timestamp,
  });
  
  factory UIEvent.fromMap(Map<String, dynamic> map) {
    return UIEvent(
      type: map['type'] ?? '',
      data: Map<String, dynamic>.from(map['data'] ?? {}),
      timestamp: map['timestamp'] ?? DateTime.now().millisecondsSinceEpoch,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'type': type,
      'data': data,
      'timestamp': timestamp,
    };
  }
}

/// UIResponse - Standard response from native methods
class UIResponse {
  final bool success;
  final String message;
  final Map<String, dynamic> data;
  final String? errorCode;
  final int timestamp;
  
  UIResponse({
    required this.success,
    required this.message,
    this.data = const {},
    this.errorCode,
    required this.timestamp,
  });
  
  factory UIResponse.fromMap(Map<String, dynamic> map) {
    return UIResponse(
      success: map['success'] ?? false,
      message: map['message'] ?? '',
      data: Map<String, dynamic>.from(map['data'] ?? {}),
      errorCode: map['errorCode'],
      timestamp: map['timestamp'] ?? DateTime.now().millisecondsSinceEpoch,
    );
  }
  
  factory UIResponse.error(String message, {String? errorCode}) {
    return UIResponse(
      success: false,
      message: message,
      errorCode: errorCode,
      timestamp: DateTime.now().millisecondsSinceEpoch,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'success': success,
      'message': message,
      'data': data,
      if (errorCode != null) 'errorCode': errorCode,
      'timestamp': timestamp,
    };
  }
}