// models/chat_models.dart

enum MessageStatus { sending, sent, delivered, read }
enum SignatureStatus { verified, invalid }

class ChatContact {
  final String name;
  final String avatar;
  final bool isOnline;

  ChatContact({
    required this.name,
    required this.avatar,
    required this.isOnline,
  });
}

class ChatMessage {
  final String id;
  final String text;
  final bool isOwn;
  final DateTime timestamp;
  final MessageStatus status;
  final FileAttachment? file;

  ChatMessage({
    required this.id,
    required this.text,
    required this.isOwn,
    required this.timestamp,
    required this.status,
    this.file,
  });

  factory ChatMessage.fromMap(
    Map<String, dynamic> map, {
    required String currentUserId,
  }) {
    final attachment = map['attachmentData'];
    return ChatMessage(
      id: map['id']?.toString() ?? '',
      text: map['content'] ?? '',
      isOwn: map['senderId']?.toString() == currentUserId,
      timestamp: map['timestamp'] != null
          ? DateTime.fromMillisecondsSinceEpoch(map['timestamp'])
          : DateTime.now(),
      status: _statusFromString(map['status']),
      file: attachment is Map<String, dynamic>
          ? FileAttachment.fromMap(attachment)
          : null,
    );
  }

  static MessageStatus _statusFromString(dynamic status) {
    switch (status?.toString().toLowerCase()) {
      case 'sent':
        return MessageStatus.sent;
      case 'delivered':
        return MessageStatus.delivered;
      case 'read':
        return MessageStatus.read;
      default:
        return MessageStatus.sending;
    }
  }
}

class FileAttachment {
  final String name;
  final String type;
  final String size;
  final SignatureStatus signatureStatus;

  FileAttachment({
    required this.name,
    required this.type,
    required this.size,
    required this.signatureStatus,
  });

  factory FileAttachment.fromMap(Map<String, dynamic> map) {
    final sizeBytes = map['fileSize'] ?? 0;
    final sizeKb = (sizeBytes is num) ? sizeBytes / 1024 : 0;
    final extension = (map['mimeType'] as String?)?.split('/')?.last ?? '';
    return FileAttachment(
      name: map['fileName'] ?? '',
      type: extension,
      size: sizeKb >= 1024
          ? '${(sizeKb / 1024).toStringAsFixed(1)} MB'
          : '${sizeKb.toStringAsFixed(0)} KB',
      signatureStatus: SignatureStatus.verified,
    );
  }
}

class ChatInfo {
  final String id;
  final String name;
  final String lastMessage;
  final String time;
  final int unread;
  final String avatar;
  final bool isOnline;
  final bool isGroup;

  ChatInfo({
    required this.id,
    required this.name,
    this.lastMessage = '',
    this.time = '',
    this.unread = 0,
    required this.avatar,
    this.isOnline = false,
    this.isGroup = false,
  });

  factory ChatInfo.fromMap(Map<String, dynamic> map) {
    final name = map['name'] ?? 'Chat';
    final avatar = name.isNotEmpty ? name[0].toUpperCase() : '?';
    String time = '';
    if (map['lastActivity'] != null) {
      final dt = DateTime.fromMillisecondsSinceEpoch(map['lastActivity']);
      final h = dt.hour.toString().padLeft(2, '0');
      final m = dt.minute.toString().padLeft(2, '0');
      time = '$h:$m';
    }

    return ChatInfo(
      id: map['id'] ?? '',
      name: name,
      lastMessage: map['lastMessage'] ?? '',
      time: time,
      unread: map['unread'] ?? 0,
      avatar: avatar,
      isOnline: map['isOnline'] ?? false,
      isGroup: map['isGroup'] ?? false,
    );
  }
}