// screens/chat_screen.dart
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'chat_info_screen.dart';
import '../constants/file_types.dart';
import '../models/chat_models.dart';
import '../services/ui_bridge.dart';
import '../services/app_state_service.dart';

class ChatScreen extends StatefulWidget {
  final ChatContact contact;
  final ChatInfo? chat;

  const ChatScreen({super.key, required this.contact, this.chat});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> with TickerProviderStateMixin {
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final FocusNode _messageFocusNode = FocusNode();

  final UIBridge _uiBridge = UIBridge.instance;
  late final String _currentUserId;

  bool _isTyping = false;

  List<ChatMessage> _messages = [];

  late final Function(Map<String, dynamic>) _messageHandler;
  late final Function(Map<String, dynamic>) _statusHandler;
  late final Function(Map<String, dynamic>) _typingHandler;

  @override
  void initState() {
    super.initState();
    final session = AppStateService.getUserSession();
    _currentUserId = session['username'] ?? AppStateService.getLocalUser() ?? '';
    _loadMessages();
    _listenEvents();
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    _messageFocusNode.dispose();
    _uiBridge.removeEventListener('MessageReceived', _messageHandler);
    _uiBridge.removeEventListener('MessageStatusUpdated', _statusHandler);
    _uiBridge.removeEventListener('TypingStatus', _typingHandler);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final mediaQuery = MediaQuery.of(context);
    final statusBarHeight = mediaQuery.padding.top;

    return Scaffold(
      body: Column(
        children: [
          // Верхняя панель чата
          _buildChatHeader(isDark, statusBarHeight),
          
          // Область сообщений
          Expanded(
            child: _buildMessagesArea(isDark),
          ),
          
          // Область ввода сообщения
          _buildInputArea(isDark),
        ],
      ),
    );
  }

  Widget _buildChatHeader(bool isDark, double statusBarHeight) {
    return Container(
      padding: EdgeInsets.only(
        top: statusBarHeight + 8,
        left: 16,
        right: 16,
        bottom: 12,
      ),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1F2937) : Colors.white,
        border: Border(
          bottom: BorderSide(
            color: isDark ? const Color(0xFF374151) : const Color(0xFFE5E7EB),
          ),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          // Кнопка назад
          IconButton(
            onPressed: () => Navigator.pop(context),
            icon: const Icon(Icons.arrow_back),
            style: IconButton.styleFrom(
              backgroundColor: isDark ? const Color(0xFF374151) : const Color(0xFFF3F4F6),
              padding: const EdgeInsets.all(8),
            ),
          ),
          const SizedBox(width: 12),
          
          // Аватар собеседника
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  Theme.of(context).colorScheme.primary,
                  Theme.of(context).colorScheme.secondary,
                ],
              ),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Center(
              child: Text(
                widget.contact.avatar,
                style: const TextStyle(fontSize: 18),
              ),
            ),
          ),
          const SizedBox(width: 12),
          
          // Информация о собеседнике
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.contact.name,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: isDark ? Colors.white : const Color(0xFF1F2937),
                  ),
                ),
                const SizedBox(height: 2),
                Row(
                  children: [
                    Container(
                      width: 8,
                      height: 8,
                      decoration: BoxDecoration(
                        color: widget.contact.isOnline ? Colors.green : Colors.grey,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      widget.contact.isOnline ? 'В сети' : 'Не в сети',
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                      ),
                    ),
                    if (_isTyping) ...[
                      const SizedBox(width: 8),
                      Text(
                        '• печатает...',
                        style: TextStyle(
                          fontSize: 12,
                          color: Theme.of(context).colorScheme.primary,
                          fontStyle: FontStyle.italic,
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
          
          // Кнопка меню чата
          IconButton(
            onPressed: _showChatInfo,
            icon: const Icon(Icons.more_vert),
            style: IconButton.styleFrom(
              backgroundColor: isDark ? const Color(0xFF374151) : const Color(0xFFF3F4F6),
              padding: const EdgeInsets.all(8),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessagesArea(bool isDark) {
    return Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF111827) : const Color(0xFFF9FAFB),
      ),
      child: ListView.builder(
        controller: _scrollController,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        itemCount: _messages.length,
        itemBuilder: (context, index) {
          final message = _messages[index];
          final showTimestamp = index == 0 || 
              _messages[index - 1].timestamp.difference(message.timestamp).inHours > 1;
          
          return Column(
            children: [
              if (showTimestamp) _buildTimestamp(message.timestamp, isDark),
              _buildMessageBubble(message, isDark),
              const SizedBox(height: 8),
            ],
          );
        },
      ),
    );
  }

  Widget _buildTimestamp(DateTime timestamp, bool isDark) {
    final now = DateTime.now();
    final difference = now.difference(timestamp);
    
    String timeText;
    if (difference.inDays > 0) {
      timeText = '${timestamp.day}.${timestamp.month}.${timestamp.year}';
    } else {
      timeText = '${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}';
    }
    
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Center(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF374151) : const Color(0xFFE5E7EB),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Text(
            timeText,
            style: TextStyle(
              fontSize: 12,
              color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildMessageBubble(ChatMessage message, bool isDark) {
    return Align(
      alignment: message.isOwn ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.75,
        ),
        child: Column(
          crossAxisAlignment: message.isOwn ? CrossAxisAlignment.end : CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: message.isOwn 
                    ? Theme.of(context).colorScheme.primary
                    : (isDark ? const Color(0xFF374151) : Colors.white),
                borderRadius: BorderRadius.circular(16).copyWith(
                  bottomRight: message.isOwn ? const Radius.circular(4) : null,
                  bottomLeft: message.isOwn ? null : const Radius.circular(4),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.1),
                    blurRadius: 4,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Файловый компонент (если есть)
                  if (message.file != null) ...[
                    _buildFileComponent(message.file!, isDark, message.isOwn),
                    const SizedBox(height: 8),
                  ],
                  
                  // Текстовое сообщение
                  Text(
                    message.text,
                    style: TextStyle(
                      fontSize: 16,
                      color: message.isOwn 
                          ? Colors.white 
                          : (isDark ? Colors.white : const Color(0xFF1F2937)),
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
            
            // Время и статус сообщения
            const SizedBox(height: 4),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '${message.timestamp.hour.toString().padLeft(2, '0')}:${message.timestamp.minute.toString().padLeft(2, '0')}',
                  style: TextStyle(
                    fontSize: 11,
                    color: isDark ? const Color(0xFF6B7280) : const Color(0xFF9CA3AF),
                  ),
                ),
                if (message.isOwn) ...[
                  const SizedBox(width: 4),
                  _buildMessageStatusIcon(message.status),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFileComponent(FileAttachment file, bool isDark, bool isOwnMessage) {
    final typeInfo = FileTypes.getFileTypeInfo(file.type);

    return GestureDetector(
      onTap: () => _showFileDialog(file),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: isOwnMessage 
              ? Colors.white.withOpacity(0.2)
              : (isDark ? const Color(0xFF1F2937) : const Color(0xFFF9FAFB)),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isOwnMessage 
                ? Colors.white.withOpacity(0.3)
                : (isDark ? const Color(0xFF4B5563) : const Color(0xFFE5E7EB)),
          ),
        ),
        child: Column(
          children: [
            Row(
              children: [
                // Иконка файла
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: typeInfo.color.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(
                    typeInfo.icon,
                    color: typeInfo.color,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),
                
                // Информация о файле
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        file.name,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                          color: isOwnMessage 
                              ? Colors.white 
                              : (isDark ? Colors.white : const Color(0xFF1F2937)),
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${file.size} • ${typeInfo.extension}',
                        style: TextStyle(
                          fontSize: 12,
                          color: isOwnMessage 
                              ? Colors.white.withOpacity(0.8)
                              : (isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280)),
                        ),
                      ),
                    ],
                  ),
                ),
                
                // Кнопка скачивания
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: typeInfo.color.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(
                    Icons.download,
                    color: typeInfo.color,
                    size: 16,
                  ),
                ),
              ],
            ),
            
            // Статус подписи
            const SizedBox(height: 8),
            _buildSignatureStatus(file.signatureStatus, isOwnMessage, isDark),
          ],
        ),
      ),
    );
  }

  Widget _buildSignatureStatus(SignatureStatus status, bool isOwnMessage, bool isDark) {
    IconData icon;
    Color color;
    String text;
    Color bgColor;

    switch (status) {
      case SignatureStatus.verified:
        icon = Icons.shield_outlined;
        color = const Color(0xFF059669);
        text = 'Подпись верна';
        bgColor = const Color(0xFF059669).withOpacity(0.1);
        break;
      case SignatureStatus.invalid:
        icon = Icons.warning_amber_outlined;
        color = const Color(0xFFD97706);
        text = 'Подпись неверна';
        bgColor = const Color(0xFFD97706).withOpacity(0.1);
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: color),
          const SizedBox(width: 4),
          Text(
            text,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w500,
              color: color,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessageStatusIcon(MessageStatus status) {
    IconData icon;
    Color color;

    switch (status) {
      case MessageStatus.sending:
        icon = Icons.access_time;
        color = Colors.grey;
        break;
      case MessageStatus.sent:
        icon = Icons.check;
        color = Colors.grey;
        break;
      case MessageStatus.delivered:
        icon = Icons.done_all;
        color = Colors.grey;
        break;
      case MessageStatus.read:
        icon = Icons.done_all;
        color = Theme.of(context).colorScheme.primary;
        break;
    }

    return Icon(icon, size: 14, color: color);
  }

  Widget _buildInputArea(bool isDark) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1F2937) : Colors.white,
        border: Border(
          top: BorderSide(
            color: isDark ? const Color(0xFF374151) : const Color(0xFFE5E7EB),
          ),
        ),
      ),
      child: Row(
        children: [
          // Кнопка прикрепления файла
          IconButton(
            onPressed: _pickFile,
            icon: const Icon(Icons.attach_file),
            style: IconButton.styleFrom(
              backgroundColor: isDark ? const Color(0xFF374151) : const Color(0xFFF3F4F6),
            ),
          ),
          const SizedBox(width: 8),
          
          // Поле ввода сообщения
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF374151) : const Color(0xFFF9FAFB),
                borderRadius: BorderRadius.circular(24),
                border: Border.all(
                  color: isDark ? const Color(0xFF4B5563) : const Color(0xFFE5E7EB),
                ),
              ),
              child: TextField(
                controller: _messageController,
                focusNode: _messageFocusNode,
                maxLines: null,
                textInputAction: TextInputAction.newline,
                decoration: InputDecoration(
                  hintText: 'Напишите сообщение...',
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  hintStyle: TextStyle(
                    color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                  ),
                ),
                style: TextStyle(
                  color: isDark ? Colors.white : const Color(0xFF1F2937),
                ),
                onChanged: (text) {
                  if (widget.chat != null) {
                    _uiBridge.sendTypingStatus(
                      chatId: widget.chat!.id,
                      isTyping: text.isNotEmpty,
                    );
                  }
                },
              ),
            ),
          ),
          const SizedBox(width: 8),
          
          // Кнопка отправки
          IconButton(
            onPressed: _sendMessage,
            icon: const Icon(Icons.send),
            style: IconButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.primary,
              foregroundColor: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  void _showFileDialog(FileAttachment file) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final typeInfo = FileTypes.getFileTypeInfo(file.type);

    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Иконка файла
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: typeInfo.color.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Icon(
                  typeInfo.icon,
                  color: typeInfo.color,
                  size: 32,
                ),
              ),
              const SizedBox(height: 16),
              
              // Название файла
              Text(
                file.name,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: isDark ? Colors.white : const Color(0xFF1F2937),
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              
              // Информация о файле
              Text(
                '${file.size} • ${typeInfo.extension}',
                style: TextStyle(
                  fontSize: 14,
                  color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                ),
              ),
              const SizedBox(height: 16),
              
              // Статус подписи
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: file.signatureStatus == SignatureStatus.verified
                      ? const Color(0xFF059669).withOpacity(0.1)
                      : const Color(0xFFD97706).withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    Icon(
                      file.signatureStatus == SignatureStatus.verified
                          ? Icons.shield_outlined
                          : Icons.warning_amber_outlined,
                      color: file.signatureStatus == SignatureStatus.verified
                          ? const Color(0xFF059669)
                          : const Color(0xFFD97706),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            file.signatureStatus == SignatureStatus.verified
                                ? 'Цифровая подпись верна'
                                : 'Цифровая подпись неверна',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                              color: file.signatureStatus == SignatureStatus.verified
                                  ? const Color(0xFF059669)
                                  : const Color(0xFFD97706),
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            file.signatureStatus == SignatureStatus.verified
                                ? 'Файл подлинный и безопасен для открытия'
                                : 'Файл был изменен или поврежден',
                            style: TextStyle(
                              fontSize: 12,
                              color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              
              // Кнопки действий
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('Закрыть'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: file.signatureStatus == SignatureStatus.verified
                          ? () {
                              Navigator.pop(context);
                              _openFile(file);
                            }
                          : null,
                      icon: const Icon(Icons.open_in_new, size: 14),
                      label: const Text('Открыть'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showChatInfo() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => ChatInfoScreen(
          contact: widget.contact,
          chat: widget.chat,
        ),
      ),
    );
  }

  void _pickFile() async {
    try {
      if (widget.chat == null) return;
      FilePickerResult? result = await FilePicker.platform.pickFiles();
      if (result != null && result.files.single.path != null) {
        await _uiBridge.sendFile(
          chatId: widget.chat!.id,
          filePath: result.files.single.path!,
          fileName: result.files.single.name,
        );
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Ошибка при выборе файла')),
      );
    }
  }

  void _sendMessage() {
    final text = _messageController.text.trim();
    if (text.isEmpty || widget.chat == null) return;

    _uiBridge
        .sendMessage(chatId: widget.chat!.id, content: text)
        .then((_) {});

    _messageController.clear();
    setState(() {
      _isTyping = false;
    });
  }

  void _openFile(FileAttachment file) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Открытие файла: ${file.name}')),
    );
  }

  Future<void> _loadMessages() async {
    if (widget.chat == null) return;
    final data = await _uiBridge.getMessages(chatId: widget.chat!.id);
    setState(() {
      _messages = data
          .map((e) => ChatMessage.fromMap(e, currentUserId: _currentUserId))
          .toList();
    });
    _scrollToBottom();
  }

  void _listenEvents() {
    _messageHandler = (data) {
      final msg = data['message'] as Map<String, dynamic>?;
      if (msg != null && msg['chatId'] == widget.chat?.id) {
        setState(() {
          _messages.add(
            ChatMessage.fromMap(msg, currentUserId: _currentUserId),
          );
        });
        _scrollToBottom();
      }
    };

    _statusHandler = (data) {
      final id = data['messageId']?.toString();
      final status = data['status'];
      final index = _messages.indexWhere((m) => m.id == id);
      if (index >= 0) {
        setState(() {
          _messages[index] = ChatMessage(
            id: _messages[index].id,
            text: _messages[index].text,
            isOwn: _messages[index].isOwn,
            timestamp: _messages[index].timestamp,
            status: _parseStatus(status),
            file: _messages[index].file,
          );
        });
      }
    };

    _typingHandler = (data) {
      if (data['chatId'] == widget.chat?.id) {
        setState(() {
          _isTyping = data['isTyping'] ?? false;
        });
      }
    };

    _uiBridge.addEventListener('MessageReceived', _messageHandler);
    _uiBridge.addEventListener('MessageStatusUpdated', _statusHandler);
    _uiBridge.addEventListener('TypingStatus', _typingHandler);
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
      );
    });
  }

  MessageStatus _parseStatus(dynamic status) {
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

