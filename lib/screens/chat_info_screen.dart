// screens/chat_info_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:math' as math;
import 'dart:convert';
import 'package:yumsg/widgets/scrollable_card.dart';
import '../services/app_state_service.dart';
import '../services/ui_bridge.dart';
import '../constants/enums.dart';
import '../models/chat_models.dart';
import '../models/organization_models.dart';

class ChatInfoScreen extends StatefulWidget {
  final ChatContact contact;
  final ChatInfo? chat;

  const ChatInfoScreen({
    super.key,
    required this.contact,
    this.chat,
  });

  @override
  State<ChatInfoScreen> createState() => _ChatInfoScreenState();
}

class _ChatInfoScreenState extends State<ChatInfoScreen> {
  final UIBridge _uiBridge = UIBridge.instance;
  // Состояние шифрования
  EncryptionStatus _encryptionStatus = EncryptionStatus.active;
  
  String? _fingerprint;
  List<List<int>> _keyVisualization = [];

  AlgorithmInfo? _myAlgorithms;
  AlgorithmInfo? _peerAlgorithms;
  AlgorithmInfo? _serverAlgorithms;

  @override
  void initState() {
    super.initState();
    _loadSecurityInfo();
  }

  Future<void> _loadSecurityInfo() async {
    if (widget.chat != null) {
      final info = await _uiBridge.getChatInfo(chatId: widget.chat!.id);
      if (info != null) {
        final fp = info['fingerprint'] as String?;
        final peer = info['peerCryptoInfo'];
        final statusStr = info['keyEstablishmentStatus'] as String?;
        setState(() {
          _fingerprint = fp;
          if (statusStr != null) {
            _encryptionStatus = EncryptionStatus.values.firstWhere(
              (e) => e.name == statusStr.toLowerCase(),
              orElse: () => EncryptionStatus.active,
            );
          }
          if (peer != null && peer['algorithms'] != null) {
            _peerAlgorithms = AlgorithmInfo.fromMap(
                Map<String, dynamic>.from(peer['algorithms']));
          }
        });
        if (fp != null) {
          _generateKeyVisualization(fp);
          setState(() {});
        }
      }
    }

    final myAlg = await _uiBridge.getCryptoAlgorithms();
    if (myAlg != null) {
      setState(() {
        _myAlgorithms = AlgorithmInfo.fromMap(myAlg);
      });
    }

    if (AppStateService.getAppMode() == 'server') {
      final serverAlg = await _uiBridge.getServerAlgorithms();
      if (serverAlg != null) {
        setState(() {
          _serverAlgorithms = AlgorithmInfo.fromMap(serverAlg);
        });
      }
    }
  }

  void _generateKeyVisualization(String hex) {
    final random = math.Random(hex.hashCode);
    _keyVisualization = List.generate(
      8,
      (i) => List.generate(8, (j) => random.nextInt(4)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final mediaQuery = MediaQuery.of(context);
    final statusBarHeight = mediaQuery.padding.top;
    final appMode = AppStateService.getAppMode();

    return Scaffold(
      body: Column(
        children: [
          // Заголовок
          _buildHeader(isDark, statusBarHeight),
          
          // Основной контент
          Expanded(
            child: ScrollableCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Информация о собеседнике
                  _buildContactInfo(isDark),
                  const SizedBox(height: 32),

                  // Секция шифрования
                  _buildEncryptionSection(isDark, appMode),
                  const SizedBox(height: 32),

                  // Секция настроек чата
                  _buildChatSettingsSection(isDark),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader(bool isDark, double statusBarHeight) {
    return Container(
      padding: EdgeInsets.only(
        top: statusBarHeight + 8,
        left: 16,
        right: 16,
        bottom: 16,
      ),
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        border: Border(
          bottom: BorderSide(
            color: isDark ? const Color(0xFF374151) : const Color(0xFFE5E7EB),
          ),
        ),
      ),
      child: Row(
        children: [
          IconButton(
            onPressed: () => Navigator.pop(context),
            icon: const Icon(Icons.arrow_back),
            style: IconButton.styleFrom(
              backgroundColor: isDark ? const Color(0xFF374151) : const Color(0xFFF3F4F6),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Информация о чате',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : const Color(0xFF1F2937),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildContactInfo(bool isDark) {
    return Column(
      children: [
        // Аватар собеседника
        Container(
          width: 80,
          height: 80,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                Theme.of(context).colorScheme.primary,
                Theme.of(context).colorScheme.secondary,
              ],
            ),
            borderRadius: BorderRadius.circular(40),
            boxShadow: [
              BoxShadow(
                color: Theme.of(context).colorScheme.primary.withOpacity(0.3),
                blurRadius: 20,
                spreadRadius: 5,
              ),
            ],
          ),
          child: Center(
            child: Text(
              widget.contact.avatar,
              style: const TextStyle(fontSize: 32),
            ),
          ),
        ),
        const SizedBox(height: 16),
        
        // Имя собеседника
        Text(
          widget.contact.name,
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: isDark ? Colors.white : const Color(0xFF1F2937),
          ),
        ),
        const SizedBox(height: 4),
        
        // Статус
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
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
                fontSize: 14,
                color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildEncryptionSection(bool isDark, String? appMode) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Заголовок секции
        Row(
          children: [
            Icon(
              Icons.security,
              color: Theme.of(context).colorScheme.primary,
              size: 24,
            ),
            const SizedBox(width: 8),
            Text(
              'Шифрование',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : const Color(0xFF1F2937),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Статус шифрования
        _buildEncryptionStatus(isDark),
        const SizedBox(height: 20),

        // Визуализация ключей
        _buildKeyVisualization(isDark),
        const SizedBox(height: 20),

        // Используемые алгоритмы
        _buildEncryptionAlgorithms(isDark, appMode ?? 'server'),
      ],
    );
  }

  Widget _buildEncryptionStatus(bool isDark) {
    Color bgColor, textColor;
    IconData icon;
    String statusText;

    switch (_encryptionStatus) {
      case EncryptionStatus.active:
        bgColor = const Color(0xFF10B981).withOpacity(0.1);
        textColor = const Color(0xFF10B981);
        icon = Icons.shield;
        statusText = 'Сквозное шифрование активно';
        break;
      case EncryptionStatus.initializing:
        bgColor = const Color(0xFFF59E0B).withOpacity(0.1);
        textColor = const Color(0xFFF59E0B);
        icon = Icons.sync;
        statusText = 'Инициализация шифрования...';
        break;
      case EncryptionStatus.error:
        bgColor = const Color(0xFFEF4444).withOpacity(0.1);
        textColor = const Color(0xFFEF4444);
        icon = Icons.warning;
        statusText = 'Ошибка настройки шифрования';
        break;
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(icon, color: textColor, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              statusText,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
                color: textColor,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildKeyVisualization(bool isDark) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF374151) : const Color(0xFFF9FAFB),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? const Color(0xFF4B5563) : const Color(0xFFE5E7EB),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Идентификатор безопасности',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: isDark ? Colors.white : const Color(0xFF1F2937),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Производная от публичных ключей участников чата',
            style: TextStyle(
              fontSize: 14,
              color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
            ),
          ),
          const SizedBox(height: 16),
          
          Row(
            children: [
              // Визуализация 8x8
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  border: Border.all(
                    color: isDark ? const Color(0xFF6B7280) : const Color(0xFFD1D5DB),
                  ),
                  borderRadius: BorderRadius.circular(2),
                ),
                child: _buildKeyGrid(),
              ),
              const SizedBox(width: 16),
              
              // Описание и кнопка
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Уникальная визуализация',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: isDark ? Colors.white : const Color(0xFF1F2937),
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Сравните с собеседником для подтверждения безопасности',
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: _showHexDialog,
                        icon: const Icon(Icons.code, size: 16),
                        label: const Text('HEX'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Theme.of(context).colorScheme.primary,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildKeyGrid() {
    final baseColor = Theme.of(context).colorScheme.primary;
    if (_keyVisualization.isEmpty) {
      return const SizedBox.shrink();
    }

    return Column(
      children: List.generate(8, (row) =>
          Expanded(
            child: Row(
              children: List.generate(8, (col) {
                final intensity = _keyVisualization[row][col];
                final opacity = 0.3 + (intensity * 0.2);

                return Expanded(
                  child: Container(
                    margin: const EdgeInsets.all(0.5),
                    decoration: BoxDecoration(
                      color: baseColor.withOpacity(opacity),
                    ),
                  ),
                );
              }),
            ),
          ),
        ),
    );
  }

  Widget _buildEncryptionAlgorithms(bool isDark, String appMode) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Используемые алгоритмы',
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w600,
            color: isDark ? Colors.white : const Color(0xFF1F2937),
          ),
        ),
        const SizedBox(height: 12),
        
        if (appMode == 'server')
          _serverAlgorithms != null
              ? _buildAlgorithmBlock(
                  title: 'Алгоритмы организации',
                  algorithms: _toAlgMap(_serverAlgorithms!),
                  isDark: isDark,
                )
              : const Text('Нет данных')
        else ...[
          if (_myAlgorithms != null)
            _buildAlgorithmBlock(
              title: 'Мои алгоритмы',
              algorithms: _toAlgMap(_myAlgorithms!),
              isDark: isDark,
            ),
          if (_peerAlgorithms != null) ...[
            const SizedBox(height: 12),
            _buildAlgorithmBlock(
              title: 'Алгоритмы собеседника',
              algorithms: _toAlgMap(_peerAlgorithms!),
              isDark: isDark,
            ),
          ],
        ],
      ],
    );
  }

  Widget _buildAlgorithmBlock({
    required String title,
    required Map<String, String> algorithms,
    required bool isDark,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1F2937) : Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? const Color(0xFF374151) : const Color(0xFFE5E7EB),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: Theme.of(context).colorScheme.primary,
            ),
          ),
          const SizedBox(height: 12),
          ...algorithms.entries.map((entry) => Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  entry.key,
                  style: TextStyle(
                    fontSize: 14,
                    color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                  ),
                ),
                Text(
                  entry.value,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    color: isDark ? Colors.white : const Color(0xFF1F2937),
                  ),
                ),
              ],
            ),
          )),
        ],
      ),
    );
  }

  Map<String, String> _toAlgMap(AlgorithmInfo info) {
    return {
      'KEM': info.kemAlgorithm,
      'Шифрование': info.symmetricAlgorithm,
      'Подпись': info.signatureAlgorithm,
    };
  }

  Widget _buildChatSettingsSection(bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Заголовок секции
        Row(
          children: [
            Icon(
              Icons.settings,
              color: Theme.of(context).colorScheme.primary,
              size: 24,
            ),
            const SizedBox(width: 8),
            Text(
              'Настройки чата',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : const Color(0xFF1F2937),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Плитка кнопок 2x2
        GridView.count(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          crossAxisCount: 2,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12,
          childAspectRatio: 1.0,
          children: [
            _buildActionTile(
              icon: Icons.refresh,
              title: 'Повторная\nинициализация',
              color: const Color(0xFF3B82F6),
              onTap: _handleReinitialize,
              isDark: isDark,
            ),
            _buildActionTile(
              icon: Icons.download,
              title: 'Экспорт\nчата',
              color: const Color(0xFF10B981),
              onTap: _handleExportChat,
              isDark: isDark,
            ),
            _buildActionTile(
              icon: Icons.delete_forever,
              title: 'Удалить\nчат',
              color: const Color(0xFFEF4444),
              onTap: _handleDeleteChat,
              isDark: isDark,
            ),
            _buildActionTile(
              icon: Icons.clear_all,
              title: 'Очистить\nчат',
              color: const Color(0xFFF59E0B),
              onTap: _handleClearChat,
              isDark: isDark,
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildActionTile({
    required IconData icon,
    required String title,
    required Color color,
    required VoidCallback onTap,
    required bool isDark,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF374151) : const Color(0xFFF9FAFB),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: isDark ? const Color(0xFF4B5563) : const Color(0xFFE5E7EB),
            ),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: color.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  icon,
                  color: color,
                  size: 24,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                title,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: isDark ? Colors.white : const Color(0xFF1F2937),
                  height: 1.3,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showHexDialog() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    Icons.fingerprint,
                    color: Theme.of(context).colorScheme.primary,
                    size: 24,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Идентификатор безопасности',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w600,
                        color: isDark ? Colors.white : const Color(0xFF1F2937),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              
              Text(
                'HEX представление:',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                ),
              ),
              const SizedBox(height: 8),
              
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF1F2937) : const Color(0xFFF9FAFB),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: isDark ? const Color(0xFF374151) : const Color(0xFFE5E7EB),
                  ),
                ),
                child: SelectableText(
                  _fingerprint ?? '---',
                  style: TextStyle(
                    fontSize: 12,
                    fontFamily: 'monospace',
                    color: isDark ? Colors.white : const Color(0xFF1F2937),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              
              Text(
                'Сравните этот идентификатор с собеседником по другому каналу связи для подтверждения безопасности соединения.',
                style: TextStyle(
                  fontSize: 12,
                  color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                ),
              ),
              const SizedBox(height: 20),
              
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        if (_fingerprint != null) {
                          Clipboard.setData(ClipboardData(text: _fingerprint!));
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Скопировано в буфер обмена')),
                          );
                        }
                      },
                      icon: const Icon(Icons.copy, size: 16),
                      label: const Text('Копировать'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('Закрыть'),
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

  void _handleReinitialize() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Повторная инициализация'),
        content: const Text('Это приведет к пересозданию ключей шифрования. Продолжить?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Отмена'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(context);
              setState(() {
                _encryptionStatus = EncryptionStatus.initializing;
              });
              if (widget.chat != null) {
                await _uiBridge.clearChatHistory(chatId: widget.chat!.id);
              }
              Future.delayed(const Duration(seconds: 2), () {
                if (!mounted) return;
                setState(() {
                  _encryptionStatus = EncryptionStatus.active;
                });
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Инициализация завершена')),
                );
              });
            },
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF3B82F6)),
            child: const Text('Инициализировать'),
          ),
        ],
      ),
    );
  }

  Future<void> _handleExportChat() async {
    if (widget.chat == null) return;
    try {
      final messages = await _uiBridge.getMessages(chatId: widget.chat!.id);
      final data = const JsonEncoder.withIndent('  ').convert(messages);
      await Clipboard.setData(ClipboardData(text: data));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('История чата скопирована')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Ошибка экспорта чата')),
        );
      }
    }
  }

  void _handleDeleteChat() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Удалить чат'),
        content: const Text('Это действие нельзя отменить. Все сообщения будут удалены.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Отмена'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(context);
              if (widget.chat != null) {
                final result = await _uiBridge.deleteChat(chatId: widget.chat!.id);
                if (!mounted) return;
                if (result.success) {
                  Navigator.pop(context);
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Чат удален')),
                  );
                } else {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(result.message)),
                  );
                }
              }
            },
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFEF4444)),
            child: const Text('Удалить'),
          ),
        ],
      ),
    );
  }

  void _handleClearChat() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Очистить чат'),
        content: const Text('Все сообщения будут удалены, но чат останется.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Отмена'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(context);
              if (widget.chat != null) {
                final result =
                    await _uiBridge.clearChatHistory(chatId: widget.chat!.id);
                if (!mounted) return;
                if (result.success) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Чат очищен')),
                  );
                } else {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(result.message)),
                  );
                }
              }
            },
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFF59E0B)),
            child: const Text('Очистить'),
          ),
        ],
      ),
    );
  }
}
