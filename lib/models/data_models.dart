/// Data models for Flutter-Native communication

// ===========================
// ENUMERATIONS
// ===========================

/// App mode enum
enum AppMode {
  local('LOCAL'),
  server('SERVER');
  
  final String value;
  const AppMode(this.value);
  
  static AppMode fromString(String value) {
    return AppMode.values.firstWhere(
      (mode) => mode.value == value.toUpperCase(),
      orElse: () => AppMode.local,
    );
  }
}

/// App state enum
enum AppState {
  initializing('INITIALIZING'),
  modeSelection('MODE_SELECTION'),
  serverConnection('SERVER_CONNECTION'),
  authentication('AUTHENTICATION'),
  chatList('CHAT_LIST'),
  chatActive('CHAT_ACTIVE'),
  modeSelected('MODE_SELECTED'),
  authenticated('AUTHENTICATED'),
  restored('RESTORED');
  
  final String value;
  const AppState(this.value);
  
  static AppState fromString(String value) {
    return AppState.values.firstWhere(
      (state) => state.value == value.toUpperCase(),
      orElse: () => AppState.initializing,
    );
  }
}

/// Connection state enum
enum ConnectionState {
  disconnected('DISCONNECTED'),
  connecting('CONNECTING'),
  connected('CONNECTED'),
  authenticated('AUTHENTICATED'),
  error('ERROR'),
  reconnecting('RECONNECTING');
  
  final String value;
  const ConnectionState(this.value);
  
  static ConnectionState fromString(String value) {
    return ConnectionState.values.firstWhere(
      (state) => state.value == value.toUpperCase(),
      orElse: () => ConnectionState.disconnected,
    );
  }
}

/// Message status enum
enum MessageStatus {
  pending('PENDING'),
  sent('SENT'),
  delivered('DELIVERED'),
  read('READ'),
  failed('FAILED');
  
  final String value;
  const MessageStatus(this.value);
  
  static MessageStatus fromString(String value) {
    return MessageStatus.values.firstWhere(
      (status) => status.value == value.toUpperCase(),
      orElse: () => MessageStatus.pending,
    );
  }
}

/// Message type enum
enum MessageType {
  text('TEXT'),
  file('FILE'),
  image('IMAGE'),
  system('SYSTEM');
  
  final String value;
  const MessageType(this.value);
  
  static MessageType fromString(String value) {
    return MessageType.values.firstWhere(
      (type) => type.value == value.toUpperCase(),
      orElse: () => MessageType.text,
    );
  }
}

/// User presence enum
enum UserPresence {
  online('ONLINE'),
  offline('OFFLINE'),
  away('AWAY'),
  busy('BUSY');
  
  final String value;
  const UserPresence(this.value);
  
  static UserPresence fromString(String value) {
    return UserPresence.values.firstWhere(
      (presence) => presence.value == value.toUpperCase(),
      orElse: () => UserPresence.offline,
    );
  }
}

// ===========================
// DATA MODELS
// ===========================

/// User model
class User {
  final String id;
  final String username;
  final String? displayName;
  final String? email;
  final String? avatarUrl;
  final String? status;
  final UserPresence presence;
  final DateTime? lastSeen;
  final bool isOnline;
  final Map<String, dynamic>? metadata;
  
  User({
    required this.id,
    required this.username,
    this.displayName,
    this.email,
    this.avatarUrl,
    this.status,
    this.presence = UserPresence.offline,
    this.lastSeen,
    this.isOnline = false,
    this.metadata,
  });
  
  factory User.fromMap(Map<String, dynamic> map) {
    return User(
      id: map['id'] ?? '',
      username: map['username'] ?? '',
      displayName: map['displayName'],
      email: map['email'],
      avatarUrl: map['avatarUrl'],
      status: map['status'],
      presence: map['presence'] != null 
          ? UserPresence.fromString(map['presence']) 
          : UserPresence.offline,
      lastSeen: map['lastSeen'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['lastSeen']) 
          : null,
      isOnline: map['isOnline'] ?? false,
      metadata: map['metadata'] != null 
          ? Map<String, dynamic>.from(map['metadata']) 
          : null,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'username': username,
      if (displayName != null) 'displayName': displayName,
      if (email != null) 'email': email,
      if (avatarUrl != null) 'avatarUrl': avatarUrl,
      if (status != null) 'status': status,
      'presence': presence.value,
      if (lastSeen != null) 'lastSeen': lastSeen.millisecondsSinceEpoch,
      'isOnline': isOnline,
      if (metadata != null) 'metadata': metadata,
    };
  }
  
  User copyWith({
    String? displayName,
    String? email,
    String? avatarUrl,
    String? status,
    UserPresence? presence,
    DateTime? lastSeen,
    bool? isOnline,
    Map<String, dynamic>? metadata,
  }) {
    return User(
      id: id,
      username: username,
      displayName: displayName ?? this.displayName,
      email: email ?? this.email,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      status: status ?? this.status,
      presence: presence ?? this.presence,
      lastSeen: lastSeen ?? this.lastSeen,
      isOnline: isOnline ?? this.isOnline,
      metadata: metadata ?? this.metadata,
    );
  }
}

/// Message model
class Message {
  final String id;
  final String chatId;
  final String senderId;
  final String? recipientId;
  final String content;
  final MessageType type;
  final MessageStatus status;
  final DateTime timestamp;
  final DateTime? editedAt;
  final DateTime? deletedAt;
  final String? replyToId;
  final Map<String, dynamic>? attachmentData;
  final Map<String, dynamic>? metadata;
  final bool isEncrypted;
  final bool isEdited;
  final bool isDeleted;
  
  Message({
    required this.id,
    required this.chatId,
    required this.senderId,
    this.recipientId,
    required this.content,
    this.type = MessageType.text,
    this.status = MessageStatus.pending,
    required this.timestamp,
    this.editedAt,
    this.deletedAt,
    this.replyToId,
    this.attachmentData,
    this.metadata,
    this.isEncrypted = false,
    this.isEdited = false,
    this.isDeleted = false,
  });
  
  factory Message.fromMap(Map<String, dynamic> map) {
    return Message(
      id: map['id'] ?? '',
      chatId: map['chatId'] ?? '',
      senderId: map['senderId'] ?? '',
      recipientId: map['recipientId'],
      content: map['content'] ?? '',
      type: map['type'] != null 
          ? MessageType.fromString(map['type']) 
          : MessageType.text,
      status: map['status'] != null 
          ? MessageStatus.fromString(map['status']) 
          : MessageStatus.pending,
      timestamp: map['timestamp'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['timestamp']) 
          : DateTime.now(),
      editedAt: map['editedAt'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['editedAt']) 
          : null,
      deletedAt: map['deletedAt'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['deletedAt']) 
          : null,
      replyToId: map['replyToId'],
      attachmentData: map['attachmentData'] != null 
          ? Map<String, dynamic>.from(map['attachmentData']) 
          : null,
      metadata: map['metadata'] != null 
          ? Map<String, dynamic>.from(map['metadata']) 
          : null,
      isEncrypted: map['isEncrypted'] ?? false,
      isEdited: map['isEdited'] ?? false,
      isDeleted: map['isDeleted'] ?? false,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'chatId': chatId,
      'senderId': senderId,
      if (recipientId != null) 'recipientId': recipientId,
      'content': content,
      'type': type.value,
      'status': status.value,
      'timestamp': timestamp.millisecondsSinceEpoch,
      if (editedAt != null) 'editedAt': editedAt!.millisecondsSinceEpoch,
      if (deletedAt != null) 'deletedAt': deletedAt!.millisecondsSinceEpoch,
      if (replyToId != null) 'replyToId': replyToId,
      if (attachmentData != null) 'attachmentData': attachmentData,
      if (metadata != null) 'metadata': metadata,
      'isEncrypted': isEncrypted,
      'isEdited': isEdited,
      'isDeleted': isDeleted,
    };
  }
  
  Message copyWith({
    MessageStatus? status,
    DateTime? editedAt,
    DateTime? deletedAt,
    String? content,
    bool? isEdited,
    bool? isDeleted,
  }) {
    return Message(
      id: id,
      chatId: chatId,
      senderId: senderId,
      recipientId: recipientId,
      content: content ?? this.content,
      type: type,
      status: status ?? this.status,
      timestamp: timestamp,
      editedAt: editedAt ?? this.editedAt,
      deletedAt: deletedAt ?? this.deletedAt,
      replyToId: replyToId,
      attachmentData: attachmentData,
      metadata: metadata,
      isEncrypted: isEncrypted,
      isEdited: isEdited ?? this.isEdited,
      isDeleted: isDeleted ?? this.isDeleted,
    );
  }
}

/// Chat model
class Chat {
  final String id;
  final String? recipientId;
  final String? recipientUsername;
  final String? recipientDisplayName;
  final String? recipientAvatarUrl;
  final UserPresence recipientPresence;
  final DateTime? lastMessageTime;
  final String? lastMessageText;
  final String? lastMessageSenderId;
  final int unreadCount;
  final bool isPinned;
  final bool isMuted;
  final bool isArchived;
  final DateTime createdAt;
  final DateTime? updatedAt;
  final Map<String, dynamic>? metadata;
  
  Chat({
    required this.id,
    this.recipientId,
    this.recipientUsername,
    this.recipientDisplayName,
    this.recipientAvatarUrl,
    this.recipientPresence = UserPresence.offline,
    this.lastMessageTime,
    this.lastMessageText,
    this.lastMessageSenderId,
    this.unreadCount = 0,
    this.isPinned = false,
    this.isMuted = false,
    this.isArchived = false,
    required this.createdAt,
    this.updatedAt,
    this.metadata,
  });
  
  factory Chat.fromMap(Map<String, dynamic> map) {
    return Chat(
      id: map['id'] ?? '',
      recipientId: map['recipientId'],
      recipientUsername: map['recipientUsername'],
      recipientDisplayName: map['recipientDisplayName'],
      recipientAvatarUrl: map['recipientAvatarUrl'],
      recipientPresence: map['recipientPresence'] != null 
          ? UserPresence.fromString(map['recipientPresence']) 
          : UserPresence.offline,
      lastMessageTime: map['lastMessageTime'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['lastMessageTime']) 
          : null,
      lastMessageText: map['lastMessageText'],
      lastMessageSenderId: map['lastMessageSenderId'],
      unreadCount: map['unreadCount'] ?? 0,
      isPinned: map['isPinned'] ?? false,
      isMuted: map['isMuted'] ?? false,
      isArchived: map['isArchived'] ?? false,
      createdAt: map['createdAt'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['createdAt']) 
          : DateTime.now(),
      updatedAt: map['updatedAt'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['updatedAt']) 
          : null,
      metadata: map['metadata'] != null 
          ? Map<String, dynamic>.from(map['metadata']) 
          : null,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      if (recipientId != null) 'recipientId': recipientId,
      if (recipientUsername != null) 'recipientUsername': recipientUsername,
      if (recipientDisplayName != null) 'recipientDisplayName': recipientDisplayName,
      if (recipientAvatarUrl != null) 'recipientAvatarUrl': recipientAvatarUrl,
      'recipientPresence': recipientPresence.value,
      if (lastMessageTime != null) 'lastMessageTime': lastMessageTime!.millisecondsSinceEpoch,
      if (lastMessageText != null) 'lastMessageText': lastMessageText,
      if (lastMessageSenderId != null) 'lastMessageSenderId': lastMessageSenderId,
      'unreadCount': unreadCount,
      'isPinned': isPinned,
      'isMuted': isMuted,
      'isArchived': isArchived,
      'createdAt': createdAt.millisecondsSinceEpoch,
      if (updatedAt != null) 'updatedAt': updatedAt!.millisecondsSinceEpoch,
      if (metadata != null) 'metadata': metadata,
    };
  }
  
  Chat copyWith({
    UserPresence? recipientPresence,
    DateTime? lastMessageTime,
    String? lastMessageText,
    String? lastMessageSenderId,
    int? unreadCount,
    bool? isPinned,
    bool? isMuted,
    bool? isArchived,
    DateTime? updatedAt,
  }) {
    return Chat(
      id: id,
      recipientId: recipientId,
      recipientUsername: recipientUsername,
      recipientDisplayName: recipientDisplayName,
      recipientAvatarUrl: recipientAvatarUrl,
      recipientPresence: recipientPresence ?? this.recipientPresence,
      lastMessageTime: lastMessageTime ?? this.lastMessageTime,
      lastMessageText: lastMessageText ?? this.lastMessageText,
      lastMessageSenderId: lastMessageSenderId ?? this.lastMessageSenderId,
      unreadCount: unreadCount ?? this.unreadCount,
      isPinned: isPinned ?? this.isPinned,
      isMuted: isMuted ?? this.isMuted,
      isArchived: isArchived ?? this.isArchived,
      createdAt: createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      metadata: metadata,
    );
  }
  
  String get displayName => recipientDisplayName ?? recipientUsername ?? 'Unknown';
  
  bool get hasUnread => unreadCount > 0;
}

/// Server info model
class ServerInfo {
  final String host;
  final int port;
  final String organizationName;
  final String? organizationId;
  final String? serverVersion;
  final bool isSecure;
  final Map<String, dynamic>? capabilities;
  final DateTime? connectedAt;
  
  ServerInfo({
    required this.host,
    required this.port,
    required this.organizationName,
    this.organizationId,
    this.serverVersion,
    this.isSecure = false,
    this.capabilities,
    this.connectedAt,
  });
  
  factory ServerInfo.fromMap(Map<String, dynamic> map) {
    return ServerInfo(
      host: map['host'] ?? '',
      port: map['port'] ?? 8080,
      organizationName: map['organizationName'] ?? '',
      organizationId: map['organizationId'],
      serverVersion: map['serverVersion'],
      isSecure: map['isSecure'] ?? false,
      capabilities: map['capabilities'] != null 
          ? Map<String, dynamic>.from(map['capabilities']) 
          : null,
      connectedAt: map['connectedAt'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['connectedAt']) 
          : null,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'host': host,
      'port': port,
      'organizationName': organizationName,
      if (organizationId != null) 'organizationId': organizationId,
      if (serverVersion != null) 'serverVersion': serverVersion,
      'isSecure': isSecure,
      if (capabilities != null) 'capabilities': capabilities,
      if (connectedAt != null) 'connectedAt': connectedAt!.millisecondsSinceEpoch,
    };
  }
}

/// Connection metrics model
class ConnectionMetrics {
  final double latency;
  final double bandwidth;
  final double packetLoss;
  final String connectionType;
  final int connectedTime;
  final int messagesSent;
  final int messagesReceived;
  final int reconnectCount;
  
  ConnectionMetrics({
    this.latency = 0.0,
    this.bandwidth = 0.0,
    this.packetLoss = 0.0,
    this.connectionType = 'unknown',
    this.connectedTime = 0,
    this.messagesSent = 0,
    this.messagesReceived = 0,
    this.reconnectCount = 0,
  });
  
  factory ConnectionMetrics.fromMap(Map<String, dynamic> map) {
    return ConnectionMetrics(
      latency: (map['latency'] ?? 0.0).toDouble(),
      bandwidth: (map['bandwidth'] ?? 0.0).toDouble(),
      packetLoss: (map['packetLoss'] ?? 0.0).toDouble(),
      connectionType: map['connectionType'] ?? 'unknown',
      connectedTime: map['connectedTime'] ?? 0,
      messagesSent: map['messagesSent'] ?? 0,
      messagesReceived: map['messagesReceived'] ?? 0,
      reconnectCount: map['reconnectCount'] ?? 0,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'latency': latency,
      'bandwidth': bandwidth,
      'packetLoss': packetLoss,
      'connectionType': connectionType,
      'connectedTime': connectedTime,
      'messagesSent': messagesSent,
      'messagesReceived': messagesReceived,
      'reconnectCount': reconnectCount,
    };
  }
}

/// File attachment model
class FileAttachment {
  final String id;
  final String fileName;
  final String filePath;
  final String mimeType;
  final int fileSize;
  final String? thumbnailPath;
  final Map<String, dynamic>? metadata;
  
  FileAttachment({
    required this.id,
    required this.fileName,
    required this.filePath,
    required this.mimeType,
    required this.fileSize,
    this.thumbnailPath,
    this.metadata,
  });
  
  factory FileAttachment.fromMap(Map<String, dynamic> map) {
    return FileAttachment(
      id: map['id'] ?? '',
      fileName: map['fileName'] ?? '',
      filePath: map['filePath'] ?? '',
      mimeType: map['mimeType'] ?? 'application/octet-stream',
      fileSize: map['fileSize'] ?? 0,
      thumbnailPath: map['thumbnailPath'],
      metadata: map['metadata'] != null 
          ? Map<String, dynamic>.from(map['metadata']) 
          : null,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'fileName': fileName,
      'filePath': filePath,
      'mimeType': mimeType,
      'fileSize': fileSize,
      if (thumbnailPath != null) 'thumbnailPath': thumbnailPath,
      if (metadata != null) 'metadata': metadata,
    };
  }
  
  bool get isImage => mimeType.startsWith('image/');
  bool get isVideo => mimeType.startsWith('video/');
  bool get isAudio => mimeType.startsWith('audio/');
  bool get isDocument => !isImage && !isVideo && !isAudio;
}

/// Session auth data model
class SessionAuthData {
  final String username;
  final String userId;
  final String? organizationId;
  final String accessToken;
  final String? refreshToken;
  final DateTime expiresAt;
  final DateTime? refreshExpiresAt;
  final String? sessionId;
  final String? scope;
  final Map<String, dynamic>? metadata;
  
  SessionAuthData({
    required this.username,
    required this.userId,
    this.organizationId,
    required this.accessToken,
    this.refreshToken,
    required this.expiresAt,
    this.refreshExpiresAt,
    this.sessionId,
    this.scope,
    this.metadata,
  });
  
  factory SessionAuthData.fromMap(Map<String, dynamic> map) {
    return SessionAuthData(
      username: map['username'] ?? '',
      userId: map['userId'] ?? '',
      organizationId: map['organizationId'],
      accessToken: map['accessToken'] ?? '',
      refreshToken: map['refreshToken'],
      expiresAt: map['expiresAt'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['expiresAt']) 
          : DateTime.now().add(const Duration(hours: 1)),
      refreshExpiresAt: map['refreshExpiresAt'] != null 
          ? DateTime.fromMillisecondsSinceEpoch(map['refreshExpiresAt']) 
          : null,
      sessionId: map['sessionId'],
      scope: map['scope'],
      metadata: map['metadata'] != null 
          ? Map<String, dynamic>.from(map['metadata']) 
          : null,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'username': username,
      'userId': userId,
      if (organizationId != null) 'organizationId': organizationId,
      'accessToken': accessToken,
      if (refreshToken != null) 'refreshToken': refreshToken,
      'expiresAt': expiresAt.millisecondsSinceEpoch,
      if (refreshExpiresAt != null) 'refreshExpiresAt': refreshExpiresAt!.millisecondsSinceEpoch,
      if (sessionId != null) 'sessionId': sessionId,
      if (scope != null) 'scope': scope,
      if (metadata != null) 'metadata': metadata,
    };
  }
  
  bool get isExpired => DateTime.now().isAfter(expiresAt);
  bool get needsRefresh => DateTime.now().isAfter(expiresAt.subtract(const Duration(minutes: 5)));
}

/// App error model
class AppError {
  final String code;
  final String message;
  final String? details;
  final dynamic cause;
  
  AppError({
    required this.code,
    required this.message,
    this.details,
    this.cause,
  });
  
  factory AppError.fromMap(Map<String, dynamic> map) {
    return AppError(
      code: map['code'] ?? 'UNKNOWN',
      message: map['message'] ?? 'Unknown error',
      details: map['details'],
      cause: map['cause'],
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'code': code,
      'message': message,
      if (details != null) 'details': details,
      if (cause != null) 'cause': cause,
    };
  }
}