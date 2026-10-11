import 'package:flutter/material.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';

class OverlayControlScreen extends StatefulWidget {
  const OverlayControlScreen({super.key});

  @override
  State<OverlayControlScreen> createState() => _OverlayControlScreenState();
}

class _OverlayControlScreenState extends State<OverlayControlScreen> {
  bool _hasOverlayPermission = false;
  bool _hasAccessibilityPermission = false;
  bool _isServiceRunning = false;

  @override
  void initState() {
    super.initState();
    _refreshPermissions();
  }

  Future<void> _refreshPermissions() async {
    final overlay = await NativeBridgeService.checkOverlayPermission();
    final access = await NativeBridgeService.checkAccessibilityPermission();
    final running = await NativeBridgeService.isFloatingServiceRunning();

    if (mounted) {
      setState(() {
        _hasOverlayPermission = overlay;
        _hasAccessibilityPermission = access;
        _isServiceRunning = running;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Screen Header
            const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  "المساعد العائم والتسعير التلقائي",
                  style: TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Text(
                  "نظام تحليل عروض إن درايف واقتراح السعر العادل في الزمن الفعلي",
                  style: TextStyle(color: AppColors.textSecondary, fontSize: 12),
                ),
              ],
            ),

            const SizedBox(height: 18),

            // 1. Accessibility Service Card
            _buildPermissionCard(
              title: _hasAccessibilityPermission
                  ? "خدمة قراءة الشاشة مفعلة وجاهزة"
                  : "مطلوب تفعيل خدمة قراءة الشاشة التلقائية",
              description:
                  "تتيح هذه الصلاحية للمساعد قراءة مسافة العميل والأجرة المعروضة فور ظهورها، واحتساب التكاليف التشغيلية وعرض السعر العادل فوراً.",
              isGranted: _hasAccessibilityPermission,
              actionLabel: "الانتقال لإعدادات إمكانية الوصول",
              icon: Icons.accessibility_new,
              onAction: () async {
                await NativeBridgeService.openAccessibilitySettings();
                _refreshPermissions();
              },
            ),

            const SizedBox(height: 16),

            // 2. Overlay Permission Card
            _buildPermissionCard(
              title: _hasOverlayPermission
                  ? "صلاحية العرض فوق التطبيقات مفعلة"
                  : "صلاحية العرض فوق التطبيقات مطلوبة",
              description:
                  "تسمح هذه الصلاحية لـ Popup Hints بالظهور الشفاف بجوار طلبات الرحلات في تطبيق إن درايف أثناء القيادة.",
              isGranted: _hasOverlayPermission,
              actionLabel: "منح صلاحية العرض فوق التطبيقات",
              icon: Icons.layers,
              onAction: () async {
                await NativeBridgeService.requestOverlayPermission();
                _refreshPermissions();
              },
            ),

            const SizedBox(height: 18),

            // 3. Service Controller Card
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: AppColors.darkSurface,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(
                  color: _isServiceRunning ? AppColors.neonGreen : AppColors.darkCardBorder,
                  width: _isServiceRunning ? 1.5 : 1,
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Icon(
                            _isServiceRunning ? Icons.check_circle : Icons.power_settings_new,
                            color: _isServiceRunning ? AppColors.neonGreen : AppColors.textSecondary,
                            size: 22,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            _isServiceRunning ? "المساعد العائم يعمل الآن" : "المساعد العائم متوقف",
                            style: TextStyle(
                              color: _isServiceRunning ? AppColors.neonGreen : AppColors.textPrimary,
                              fontWeight: FontWeight.bold,
                              fontSize: 15,
                            ),
                          ),
                        ],
                      ),
                      IconButton(
                        icon: const Icon(Icons.refresh, size: 20),
                        color: AppColors.textSecondary,
                        onPressed: _refreshPermissions,
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    "عند تشغيل المساعد، ستظهر علامات التسعير العادل تلقائياً بجانب كل طلب يظهر على شاشة إن درايف فوراً.",
                    style: TextStyle(color: AppColors.textSecondary, fontSize: 12.5),
                  ),
                  const SizedBox(height: 14),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: _isServiceRunning ? AppColors.lossRed : AppColors.neonGreen,
                        foregroundColor: _isServiceRunning ? Colors.white : AppColors.darkBackground,
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      icon: Icon(_isServiceRunning ? Icons.stop : Icons.play_arrow, size: 20),
                      label: Text(
                        _isServiceRunning ? "إيقاف المساعد العائم" : "تشغيل المساعد العائم",
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                      ),
                      onPressed: () async {
                        if (_isServiceRunning) {
                          await NativeBridgeService.stopFloatingService();
                        } else {
                          await NativeBridgeService.startFloatingService();
                        }
                        _refreshPermissions();
                      },
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            // Quick Tips
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.darkSurfaceVariant.withOpacity(0.5),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.darkCardBorder),
              ),
              child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.info_outline, color: AppColors.accentCyan, size: 18),
                      SizedBox(width: 8),
                      Text(
                        "تلميحات كابتن إن درايف:",
                        style: TextStyle(color: AppColors.accentCyan, fontWeight: FontWeight.bold, fontSize: 13),
                      ),
                    ],
                  ),
                  SizedBox(height: 8),
                  Text(
                    "• يمكنك سحب العلامة العائمة في أي مكان على الشاشة لضبط موضعها بحرية.\n"
                    "• اضغط على العلامة العائمة لتكبير تفاصيل الرحلة وصافي الربح وإدارة المناطق بلمسة واحدة.\n"
                    "• يمكنك تغيير شكل وحجم العلامة من تبويب 'الإعدادات'.",
                    style: TextStyle(color: AppColors.textSecondary, fontSize: 12, height: 1.5),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionCard({
    required String title,
    required String description,
    required bool isGranted,
    required String actionLabel,
    required IconData icon,
    required VoidCallback onAction,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.darkSurface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isGranted ? AppColors.neonGreen.withOpacity(0.5) : AppColors.warningYellow,
          width: 1.2,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                isGranted ? Icons.check_circle : Icons.warning_amber_rounded,
                color: isGranted ? AppColors.neonGreen : AppColors.warningYellow,
                size: 22,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            description,
            style: const TextStyle(color: AppColors.textSecondary, fontSize: 12, height: 1.4),
          ),
          if (!isGranted) ...[
            const SizedBox(height: 12),
            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.warningYellow,
                foregroundColor: AppColors.darkBackground,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              ),
              icon: Icon(icon, size: 18),
              label: Text(actionLabel, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12.5)),
              onPressed: onAction,
            ),
          ],
        ],
      ),
    );
  }
}
