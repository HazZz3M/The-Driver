import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../data/models/driver_settings.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../../services/native_bridge_service.dart';
import '../../utils/update_manager.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isCheckingUpdate = false;
  bool _isDownloadingApk = false;
  double _downloadProgress = 0.0;
  UpdateInfo? _updateInfo;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 5, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  const Icon(Icons.tune, color: AppColors.neonGreen, size: 24),
                  const SizedBox(width: 10),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: const [
                      Text(
                        "الإعدادات التشغيلية والتخصيص",
                        style: TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 17,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        "القبول والمزايدة التلقائية، ستايل الـ HUD، والمناطق",
                        style: TextStyle(color: AppColors.textSecondary, fontSize: 11),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            // Tab Bar
            Container(
              color: AppColors.darkSurface,
              child: TabBar(
                controller: _tabController,
                isScrollable: true,
                indicatorColor: AppColors.neonGreen,
                labelColor: AppColors.neonGreen,
                unselectedLabelColor: AppColors.textSecondary,
                labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                tabs: const [
                  Tab(icon: Icon(Icons.bolt, size: 18), text: "القبول التلقائي"),
                  Tab(icon: Icon(Icons.palette, size: 18), text: "شكل العلامة"),
                  Tab(icon: Icon(Icons.map, size: 18), text: "المناطق"),
                  Tab(icon: Icon(Icons.attach_money, size: 18), text: "التسعير"),
                  Tab(icon: Icon(Icons.settings, size: 18), text: "التطبيق"),
                ],
              ),
            ),

            // Tab Bar View
            Expanded(
              child: TabBarView(
                controller: _tabController,
                children: [
                  _buildAutoAcceptTab(),
                  _buildOverlayStyleTab(),
                  _buildZonesTab(),
                  _buildPricingTab(),
                  _buildAppSettingsTab(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ==========================================
  // TAB 1: AUTO-ACCEPT & AUTOMATION
  // ==========================================
  Widget _buildAutoAcceptTab() {
    final provider = context.watch<AssistantProvider>();
    final settings = provider.driverSettings;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        SwitchListTile(
          title: const Text("تفعيل القبول التلقائي للطلبات (Auto-Accept)", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13.5)),
          subtitle: const Text("قبول العروض المتوافقة مع معايير الربحية الخاصة بك فوراً", style: TextStyle(fontSize: 11, color: AppColors.textSecondary)),
          value: settings.autoAcceptEnabled,
          activeColor: AppColors.neonGreen,
          contentPadding: EdgeInsets.zero,
          onChanged: (v) {
            provider.updateSettings(settings.copyWith(autoAcceptEnabled: v));
          },
        ),

        const Divider(color: AppColors.darkCardBorder),

        const SizedBox(height: 10),

        _buildNumberSettingTile(
          title: "الحد الأدنى لصافي ربح الرحلة (EGP)",
          value: settings.autoAcceptMinNetProfit,
          step: 5.0,
          onChanged: (v) => provider.updateSettings(settings.copyWith(autoAcceptMinNetProfit: v)),
        ),

        const SizedBox(height: 10),

        _buildNumberSettingTile(
          title: "الحد الأدنى لصافي الربح لكل كيلومتر (ج/كم)",
          value: settings.autoAcceptMinProfitPerKm,
          step: 1.0,
          onChanged: (v) => provider.updateSettings(settings.copyWith(autoAcceptMinProfitPerKm: v)),
        ),

        const SizedBox(height: 10),

        _buildNumberSettingTile(
          title: "أقصى مسافة وصول للراكب (كم)",
          value: settings.autoAcceptMaxPickupKm,
          step: 0.5,
          onChanged: (v) => provider.updateSettings(settings.copyWith(autoAcceptMaxPickupKm: v)),
        ),

        const SizedBox(height: 10),

        _buildNumberSettingTile(
          title: "مهلة التأخير قبل الضغط التلقائي (بالثواني)",
          value: settings.autoAcceptDelaySeconds,
          step: 0.5,
          onChanged: (v) => provider.updateSettings(settings.copyWith(autoAcceptDelaySeconds: v)),
        ),

        const SizedBox(height: 14),

        SwitchListTile(
          title: const Text("قبول الطلبات المتجهة للمناطق المفضلة فقط", style: TextStyle(fontSize: 13)),
          value: settings.autoAcceptOnlyFavoriteZones,
          activeColor: AppColors.accentAmber,
          contentPadding: EdgeInsets.zero,
          onChanged: (v) {
            provider.updateSettings(settings.copyWith(autoAcceptOnlyFavoriteZones: v));
          },
        ),

        SwitchListTile(
          title: const Text("تجاهل ورفض طلبات المناطق المحظورة تماماً", style: TextStyle(fontSize: 13)),
          value: !settings.autoAcceptIgnoreBlacklist,
          activeColor: AppColors.lossRed,
          contentPadding: EdgeInsets.zero,
          onChanged: (v) {
            provider.updateSettings(settings.copyWith(autoAcceptIgnoreBlacklist: !v));
          },
        ),
      ],
    );
  }

  // ==========================================
  // TAB 2: OVERLAY STYLE & SIZING
  // ==========================================
  Widget _buildOverlayStyleTab() {
    final provider = context.watch<AssistantProvider>();
    final currentStyle = provider.overlayStyle;
    final currentSize = provider.overlaySize;

    final styles = [
      {"id": "COMPACT_CIRCLE", "title": "قرص دائري مصغر", "desc": "أيقونة دائرية أنيقة تعرض السعر العادل"},
      {"id": "SLIM_PILL", "title": "كبسولة أفقية رفيعة", "desc": "شريط أفقي كبسولي مدمج ومريح للعين"},
      {"id": "SPEEDO_GAUGE", "title": "عداد سرعة رقمي", "desc": "تصميم عداد سيارة رقمي أنيق"},
      {"id": "MINI_TAG", "title": "شارة تسعير مدمجة", "desc": "تاج مدمج يركز على قيمة السعر"},
      {"id": "CORNER_RIBBON", "title": "شريط زاوية أنيق", "desc": "شريط جانبي لا يشغل مساحة"},
      {"id": "GLOW_DOT", "title": "نقطة إشعاع ذكية", "desc": "نقطة توهج نيون عالية التباين"},
    ];

    final sizes = [
      {"id": "SMALL", "label": "صغير (85%)"},
      {"id": "MEDIUM", "label": "متوسط (100%)"},
      {"id": "LARGE", "label": "كبير (120%)"},
    ];

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text(
          "اختر نمط وشكل العلامة العائمة فوق إن درايف:",
          style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 14),
        ),
        const SizedBox(height: 12),

        ...styles.map((s) {
          final isSelected = currentStyle == s["id"];
          return Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: InkWell(
              onTap: () => provider.setOverlayStyle(s["id"]!),
              borderRadius: BorderRadius.circular(12),
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: isSelected ? AppColors.neonGreen.withOpacity(0.1) : AppColors.darkSurface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: isSelected ? AppColors.neonGreen : AppColors.darkCardBorder,
                    width: isSelected ? 1.5 : 1,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      isSelected ? Icons.radio_button_checked : Icons.radio_button_off,
                      color: isSelected ? AppColors.neonGreen : AppColors.textSecondary,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            s["title"]!,
                            style: TextStyle(
                              color: isSelected ? AppColors.neonGreen : AppColors.textPrimary,
                              fontWeight: FontWeight.bold,
                              fontSize: 13,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(s["desc"]!, style: const TextStyle(color: AppColors.textSecondary, fontSize: 11)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        }),

        const SizedBox(height: 16),

        const Text(
          "حجم العلامة على الشاشة:",
          style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 14),
        ),

        const SizedBox(height: 8),

        Row(
          children: sizes.map((sz) {
            final isSelected = currentSize == sz["id"];
            return Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: ChoiceChip(
                  label: Text(sz["label"]!),
                  selected: isSelected,
                  selectedColor: AppColors.neonGreen,
                  backgroundColor: AppColors.darkSurface,
                  labelStyle: TextStyle(
                    color: isSelected ? AppColors.darkBackground : AppColors.textPrimary,
                    fontSize: 11,
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  ),
                  onSelected: (_) => provider.setOverlaySize(sz["id"]!),
                ),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }

  // ==========================================
  // TAB 3: ZONES MANAGEMENT
  // ==========================================
  Widget _buildZonesTab() {
    final provider = context.watch<AssistantProvider>();
    final settings = provider.driverSettings;
    final favCtrl = TextEditingController();
    final blackCtrl = TextEditingController();

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Favorite Zones Section
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Row(
              children: [
                Icon(Icons.star, color: AppColors.accentAmber, size: 20),
                SizedBox(width: 6),
                Text(
                  "المناطق المفضلة (عالية الربحية)",
                  style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 14),
                ),
              ],
            ),
            IconButton(
              icon: const Icon(Icons.add_circle, color: AppColors.accentAmber),
              onPressed: () {
                _showAddZoneDialog("إضافة منطقة مفضلة", (val) => provider.addFavoriteZone(val));
              },
            ),
          ],
        ),

        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: settings.favoriteZones.map((z) {
            return Chip(
              label: Text(z, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
              backgroundColor: AppColors.accentAmber.withOpacity(0.18),
              labelStyle: const TextStyle(color: AppColors.accentAmber),
              deleteIcon: const Icon(Icons.close, size: 16, color: AppColors.accentAmber),
              onDeleted: () => provider.removeFavoriteZone(z),
            );
          }).toList(),
        ),

        const SizedBox(height: 20),
        const Divider(color: AppColors.darkCardBorder),
        const SizedBox(height: 10),

        // Blacklisted Zones Section
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Row(
              children: [
                Icon(Icons.block, color: AppColors.lossRed, size: 20),
                SizedBox(width: 6),
                Text(
                  "المناطق المحظورة / غير المرغوبة",
                  style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 14),
                ),
              ],
            ),
            IconButton(
              icon: const Icon(Icons.add_circle, color: AppColors.lossRed),
              onPressed: () {
                _showAddZoneDialog("إضافة منطقة محظورة", (val) => provider.addBlacklistZone(val));
              },
            ),
          ],
        ),

        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: settings.blacklistedZones.map((z) {
            return Chip(
              label: Text(z, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
              backgroundColor: AppColors.lossRed.withOpacity(0.18),
              labelStyle: const TextStyle(color: AppColors.lossRed),
              deleteIcon: const Icon(Icons.close, size: 16, color: AppColors.lossRed),
              onDeleted: () => provider.removeBlacklistZone(z),
            );
          }).toList(),
        ),
      ],
    );
  }

  void _showAddZoneDialog(String title, Function(String) onAdd) {
    final ctrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
        content: TextField(
          controller: ctrl,
          decoration: const InputDecoration(hintText: "اكتب اسم المنطقة هنا..."),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("إلغاء")),
          ElevatedButton(
            onPressed: () {
              final text = ctrl.text.trim();
              if (text.isNotEmpty) {
                onAdd(text);
                Navigator.pop(ctx);
              }
            },
            child: const Text("إضافة"),
          ),
        ],
      ),
    );
  }

  // ==========================================
  // TAB 4: PRICING & OPERATIONAL CONSTANTS
  // ==========================================
  Widget _buildPricingTab() {
    final provider = context.watch<AssistantProvider>();
    final settings = provider.driverSettings;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _buildNumberSettingTile(
          title: "معدل استهلاك البنزين (لتر/100 كم)",
          value: settings.fuelConsumptionLPer100Km,
          step: 0.5,
          onChanged: (v) => provider.updateSettings(settings.copyWith(fuelConsumptionLPer100Km: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "سعر لتر الوقود الحالي (ج.م)",
          value: settings.fuelPricePerLiter,
          step: 0.25,
          onChanged: (v) => provider.updateSettings(settings.copyWith(fuelPricePerLiter: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "نسبة عمولة إن درايف (%)",
          value: settings.inDriveCommissionPercent,
          step: 0.5,
          onChanged: (v) => provider.updateSettings(settings.copyWith(inDriveCommissionPercent: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "معدل استهلاك وصيانة السيارة (ج/كم)",
          value: settings.wearAndTearPerKm,
          step: 0.1,
          onChanged: (v) => provider.updateSettings(settings.copyWith(wearAndTearPerKm: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "الحد الأدنى للربح المطلوب (ج/كم)",
          value: settings.minProfitPerKm,
          step: 0.5,
          onChanged: (v) => provider.updateSettings(settings.copyWith(minProfitPerKm: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "الهدف المالي اليومي المستهدف (EGP)",
          value: settings.targetDailyEarnings,
          step: 100.0,
          onChanged: (v) => provider.updateSettings(settings.copyWith(targetDailyEarnings: v)),
        ),
      ],
    );
  }

  // ==========================================
  // TAB 5: APP SETTINGS & IN-APP UPDATES
  // ==========================================
  Widget _buildAppSettingsTab() {
    final provider = context.watch<AssistantProvider>();

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Theme switch
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.darkSurface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.darkCardBorder),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Row(
                children: [
                  Icon(Icons.dark_mode, color: AppColors.neonGreen, size: 20),
                  SizedBox(width: 8),
                  Text("المظهر الداكن (Dark Cockpit)", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                ],
              ),
              Switch(
                value: provider.themeMode == "DARK",
                activeColor: AppColors.neonGreen,
                onChanged: (isDark) => provider.setThemeMode(isDark ? "DARK" : "LIGHT"),
              ),
            ],
          ),
        ),

        const SizedBox(height: 16),

        // In-App Updates Card
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.darkSurface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.neonGreen.withOpacity(0.5)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(Icons.system_update, color: AppColors.neonGreen, size: 20),
                  SizedBox(width: 8),
                  Text(
                    "تحديثات التطبيق (The Driver Updater)",
                    style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 14),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              const Text(
                "الإصدار الحالي: ${UpdateManager.currentAppVersion} • كود أصلي Flutter",
                style: TextStyle(color: AppColors.textSecondary, fontSize: 12),
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.neonGreen,
                  foregroundColor: AppColors.darkBackground,
                ),
                icon: _isCheckingUpdate
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.darkBackground),
                      )
                    : const Icon(Icons.cloud_download, size: 18),
                label: const Text("فحص وجود تحديث جديد", style: TextStyle(fontWeight: FontWeight.bold)),
                onPressed: (_isCheckingUpdate || _isDownloadingApk)
                    ? null
                    : () async {
                        setState(() => _isCheckingUpdate = true);
                        final info = await UpdateManager.checkForUpdate();
                        setState(() {
                          _isCheckingUpdate = false;
                          _updateInfo = info;
                        });

                        if (!mounted) return;
                        if (info != null && info.isAvailable) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text("يتوفر تحديث جديد: ${info.versionName}"),
                              backgroundColor: AppColors.neonGreenDark,
                            ),
                          );
                        } else {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text("أنت تستخدم أحدث إصدار بالفعل!")),
                          );
                        }
                      },
              ),
              if (_updateInfo != null && _updateInfo!.isAvailable) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.darkSurfaceVariant,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppColors.neonGreen.withOpacity(0.4)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.new_releases, color: AppColors.neonGreen, size: 18),
                          const SizedBox(width: 8),
                          Text(
                            "إصدار جديد متوفر: ${_updateInfo!.versionName}",
                            style: const TextStyle(color: AppColors.neonGreen, fontWeight: FontWeight.bold, fontSize: 13),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _updateInfo!.releaseNotes,
                        style: const TextStyle(color: AppColors.textSecondary, fontSize: 11.5),
                      ),
                      const SizedBox(height: 12),
                      if (_isDownloadingApk) ...[
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                const Text("جاري تحميل ملف التحديث...", style: TextStyle(color: AppColors.textPrimary, fontSize: 12)),
                                Text("${(_downloadProgress * 100).toInt()}%", style: const TextStyle(color: AppColors.neonGreen, fontWeight: FontWeight.bold, fontSize: 12)),
                              ],
                            ),
                            const SizedBox(height: 6),
                            ClipRRect(
                              borderRadius: BorderRadius.circular(4),
                              child: LinearProgressIndicator(
                                value: _downloadProgress > 0 ? _downloadProgress : null,
                                backgroundColor: AppColors.darkBackground,
                                color: AppColors.neonGreen,
                                minHeight: 6,
                              ),
                            ),
                          ],
                        ),
                      ] else ...[
                        Row(
                          children: [
                            Expanded(
                              child: ElevatedButton.icon(
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: AppColors.neonGreen,
                                  foregroundColor: AppColors.darkBackground,
                                  padding: const EdgeInsets.symmetric(vertical: 10),
                                ),
                                icon: const Icon(Icons.install_mobile, size: 18),
                                label: const Text("تثبيت التحديث مباشرة (APK)", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                                onPressed: () async {
                                  setState(() {
                                    _isDownloadingApk = true;
                                    _downloadProgress = 0.0;
                                  });

                                  final file = await UpdateManager.downloadApk(
                                    _updateInfo!.downloadUrl,
                                    onProgress: (p, rec, tot) {
                                      if (mounted) setState(() => _downloadProgress = p);
                                    },
                                  );

                                  setState(() => _isDownloadingApk = false);

                                  if (!mounted) return;
                                  if (file != null && await file.exists()) {
                                    final success = await NativeBridgeService.installApk(file.path);
                                    if (!success && mounted) {
                                      ScaffoldMessenger.of(context).showSnackBar(
                                        const SnackBar(content: Text("يرجى السماح بتثبيت التطبيقات من مصادر غير معروفة لتثبيت التحديث")),
                                      );
                                    }
                                  } else {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(content: Text("فشل تحميل التحديث، يمكنك فتحه عبر المتصفح")),
                                    );
                                  }
                                },
                              ),
                            ),
                            const SizedBox(width: 8),
                            IconButton(
                              tooltip: "فتح رابط التحميل في المتصفح",
                              style: IconButton.styleFrom(
                                backgroundColor: AppColors.darkSurface,
                                foregroundColor: AppColors.textPrimary,
                              ),
                              icon: const Icon(Icons.open_in_browser),
                              onPressed: () async {
                                final uri = Uri.parse(_updateInfo!.downloadUrl);
                                try {
                                  await launchUrl(uri, mode: LaunchMode.externalApplication);
                                } catch (_) {
                                  try {
                                    await launchUrl(uri, mode: LaunchMode.platformDefault);
                                  } catch (e) {
                                    if (mounted) {
                                      ScaffoldMessenger.of(context).showSnackBar(
                                        SnackBar(content: Text("تعذر فتح الرابط: $e")),
                                      );
                                    }
                                  }
                                }
                              },
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildNumberSettingTile({
    required String title,
    required double value,
    required double step,
    required Function(double) onChanged,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.darkSurface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.darkCardBorder),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              title,
              style: const TextStyle(color: AppColors.textPrimary, fontSize: 12.5),
            ),
          ),
          Row(
            children: [
              IconButton(
                icon: const Icon(Icons.remove, size: 18),
                color: AppColors.textSecondary,
                onPressed: () {
                  final next = (value - step).clamp(0.0, 50000.0);
                  onChanged(next);
                },
              ),
              Text(
                value % 1 == 0 ? value.toInt().toString() : value.toStringAsFixed(1),
                style: const TextStyle(
                  color: AppColors.neonGreen,
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                ),
              ),
              IconButton(
                icon: const Icon(Icons.add, size: 18),
                color: AppColors.neonGreen,
                onPressed: () {
                  final next = value + step;
                  onChanged(next);
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}
