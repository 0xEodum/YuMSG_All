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
  final int id;
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