import 'package:flutter/material.dart';
import '../services/app_state_service.dart';
import '../services/ui_bridge.dart';
import '../models/data_models.dart';

class ModeSelectionScreen extends StatefulWidget {
  const ModeSelectionScreen({super.key});

  @override
  State<ModeSelectionScreen> createState() => _ModeSelectionScreenState();
}

class _ModeSelectionScreenState extends State<ModeSelectionScreen> {
  final UIBridge _uiBridge = UIBridge.instance;
  
  String? selectedMode;
  bool _isLoading = false;
  String? _error;
  
  @override
  void initState() {
    super.initState();
    _initializeUIBridge();
    _listenToEvents();
  }
  
  /// Initialize UIBridge
  Future<void> _initializeUIBridge() async {
    try {
      await _uiBridge.initialize();
      print('UIBridge initialized in ModeSelectionScreen');
      
      // Get current app mode if any
      final currentMode = await _uiBridge.getAppMode();
      if (currentMode != null && mounted) {
        setState(() {
          selectedMode = currentMode.toLowerCase();
        });
      }
    } catch (e) {
      print('Failed to initialize UIBridge: $e');
      if (mounted) {
        setState(() {
          _error = 'Ошибка инициализации приложения';
        });
      }
    }
  }
  
  /// Listen to UIBridge events
  void _listenToEvents() {
    // Listen for app state changes
    _uiBridge.addEventListener('APP_STATE_CHANGED', (data) {
      final state = data['state'] as String?;
      if (state != null) {
        print('App state changed to: $state');
        _handleAppStateChange(AppState.fromString(state));
      }
    });
    
    // Listen for mode selection confirmation
    _uiBridge.addEventListener('MODE_SELECTED', (data) {
      final mode = data['mode'] as String?;
      if (mode != null && mounted) {
        print('Mode selected confirmed: $mode');
        _navigateToNextScreen(mode);
      }
    });
    
    // Listen for errors
    _uiBridge.addEventListener('ERROR_OCCURRED', (data) {
      final message = data['message'] as String?;
      if (message != null && mounted) {
        setState(() {
          _error = message;
          _isLoading = false;
        });
      }
    });
  }
  
  /// Handle app state changes
  void _handleAppStateChange(AppState state) {
    switch (state) {
      case AppState.serverConnection:
        if (mounted) {
          Navigator.pushReplacementNamed(context, '/server-connection');
        }
        break;
      case AppState.authentication:
        if (mounted) {
          Navigator.pushReplacementNamed(context, '/auth');
        }
        break;
      case AppState.chatList:
        if (mounted) {
          Navigator.pushReplacementNamed(context, '/chat-list');
        }
        break;
      default:
        // Stay on current screen
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Логотип и название
                  _buildHeader(isDark),
                  const SizedBox(height: 32),
                  
                  // Основная карточка
                  _buildMainCard(isDark),
                  const SizedBox(height: 16),
                  
                  // Информационный блок
                  _buildVersionInfo(isDark),
                  const SizedBox(height: 8),
                
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(bool isDark) {
    return Column(
      children: [
        Text(
          'YuMSG',
          style: TextStyle(
            fontSize: 48,
            fontWeight: FontWeight.bold,
            color: isDark ? const Color(0xFF8B5CF6) : const Color(0xFF7C3AED),
          ),
        ),
        const SizedBox(height: 8),
        Container(
          height: 4,
          width: 64,
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF8B5CF6) : const Color(0xFF8B5CF6),
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ],
    );
  }

  Widget _buildMainCard(bool isDark) {
    return Card(
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Заголовок
            Text(
              'Выберите режим работы',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : const Color(0xFF1F2937),
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            
            // Режим сервера
            _buildModeOption(
              mode: 'server',
              icon: Icons.cloud_outlined,
              title: 'Серверный режим',
              description: 'Подключение к корпоративному серверу для обмена сообщениями',
              isDark: isDark,
            ),
            const SizedBox(height: 12),
            
            // Локальный режим
            _buildModeOption(
              mode: 'local',
              icon: Icons.wifi_tethering,
              title: 'Локальный режим',
              description: 'Прямое подключение к устройствам в локальной сети',
              isDark: isDark,
            ),
            
            // Ошибка
            if (_error != null) ...[
              const SizedBox(height: 16),
              _buildErrorMessage(_error!, isDark),
            ],
            
            const SizedBox(height: 24),
            
            // Кнопка продолжения
            _buildContinueButton(isDark),
          ],
        ),
      ),
    );
  }

  Widget _buildModeOption({
    required String mode,
    required IconData icon,
    required String title,
    required String description,
    required bool isDark,
  }) {
    final isSelected = selectedMode == mode;
    
    return InkWell(
      onTap: _isLoading ? null : () {
        setState(() {
          selectedMode = mode;
          _error = null;
        });
      },
      borderRadius: BorderRadius.circular(12),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        decoration: BoxDecoration(
          border: Border.all(
            color: isSelected
                ? (isDark ? const Color(0xFF8B5CF6) : const Color(0xFF7C3AED))
                : (isDark ? const Color(0xFF374151) : const Color(0xFFE5E7EB)),
            width: isSelected ? 2 : 1,
          ),
          borderRadius: BorderRadius.circular(12),
          color: isSelected
              ? (isDark
                  ? const Color(0xFF8B5CF6).withOpacity(0.1)
                  : const Color(0xFF7C3AED).withOpacity(0.05))
              : null,
        ),
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(
              icon,
              size: 40,
              color: isSelected
                  ? (isDark ? const Color(0xFF8B5CF6) : const Color(0xFF7C3AED))
                  : (isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280)),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: isDark ? Colors.white : const Color(0xFF1F2937),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    description,
                    style: TextStyle(
                      fontSize: 12,
                      color: isDark ? const Color(0xFF9CA3AF) : const Color(0xFF6B7280),
                    ),
                  ),
                ],
              ),
            ),
            Radio<String>(
              value: mode,
              groupValue: selectedMode,
              onChanged: _isLoading ? null : (value) {
                setState(() {
                  selectedMode = value;
                  _error = null;
                });
              },
              activeColor: isDark ? const Color(0xFF8B5CF6) : const Color(0xFF7C3AED),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContinueButton(bool isDark) {
    return SizedBox(
      height: 48,
      child: ElevatedButton(
        onPressed: selectedMode == null || _isLoading ? null : _handleContinue,
        style: ElevatedButton.styleFrom(
          backgroundColor: selectedMode != null
              ? (isDark ? const Color(0xFF8B5CF6) : const Color(0xFF7C3AED))
              : (isDark
                  ? const Color(0xFF6B7280)
                  : const Color(0xFF9CA3AF)),
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
        ),
        child: _isLoading
            ? const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  ),
                  SizedBox(width: 12),
                  Text(
                    'Подождите...',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              )
            : const Text(
                'Продолжить',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w500,
                ),
              ),
      ),
    );
  }

  Widget _buildErrorMessage(String message, bool isDark) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark 
            ? const Color(0xFF7F1D1D).withOpacity(0.2) 
            : const Color(0xFFFEF2F2),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(
            Icons.error,
            color: isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626),
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              message,
              style: TextStyle(
                fontSize: 14,
                color: isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildVersionInfo(bool isDark) {
    return Text(
      'YuMSG Версия 1.0',
      style: TextStyle(
        fontSize: 12,
        color: isDark ? const Color(0xFF8B5CF6) : const Color(0xFF8B5CF6),
      ),
      textAlign: TextAlign.center,
    );
  }

  /// Handle continue button press
  void _handleContinue() async {
    if (selectedMode == null) return;
    
    setState(() {
      _isLoading = true;
      _error = null;
    });
    
    try {
      // Call native method to select mode
      final response = await _uiBridge.selectMode(selectedMode!);
      
      if (response.success) {
        // Save mode locally for Flutter
        await AppStateService.saveAppMode(selectedMode!);
        
        // Navigation will be handled by event listener
        // based on APP_STATE_CHANGED event from native
      } else {
        setState(() {
          _error = response.message;
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _error = 'Ошибка при выборе режима: $e';
        _isLoading = false;
      });
    }
  }
  
  /// Navigate to next screen based on mode
  void _navigateToNextScreen(String mode) {
    if (!mounted) return;
    
    // Navigation logic based on selected mode
    if (mode.toUpperCase() == 'SERVER') {
      Navigator.pushReplacementNamed(context, '/server-connection');
    } else {
      // For local mode, go directly to authentication
      Navigator.pushReplacementNamed(context, '/auth');
    }
  }
  
  @override
  void dispose() {
    // Remove event listeners
    _uiBridge.removeEventListener('APP_STATE_CHANGED', (_) {});
    _uiBridge.removeEventListener('MODE_SELECTED', (_) {});
    _uiBridge.removeEventListener('ERROR_OCCURRED', (_) {});
    super.dispose();
  }
}