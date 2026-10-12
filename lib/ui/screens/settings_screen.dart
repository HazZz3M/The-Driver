import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../data/models/driver_settings.dart';
import '../../providers/assistant_provider.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import '../../utils/update_manager.dart';
import '../components/dialogs.dart';

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
  String _installedVersion = "";

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 5, vsync: this);
    UpdateManager.currentVersion().then((v) {
      if (mounted) setState(() => _installedVersion = v);
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  /// Applies [change] to the provider's *current* settings at the moment of the tap, so quick
  /// repeated taps build on each other instead of on a stale snapshot.
  void _update(DriverSettings Function(DriverSettings s) change) {
    final provider = context.read<AssistantProvider>();
    provider.updateSettings(change(provider.driverSettings));
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  Icon(Icons.tune, color: AppColors.neonGreen, size: 24),
                  SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          "الإعدادات التشغيلية والتخصيص",
                          style: TextStyle(color: AppColors.textPrimary, fontSize: 17, fontWeight: FontWeight.bold),
                        ),
                        Text(
                          "القبول التلقائي، شكل العلامة، المناطق، والتسعير",
                          style: TextStyle(color: AppColors.textSecondary, fontSize: 11.5),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            Container(
              color: AppColors.darkSurface,
              child: TabBar(
                controller: _tabController,
                isScrollable: true,
                indicatorColor: AppColors.neonGreen,
                labelColor: AppColors.neonGreen,
                unselectedLabelColor: AppColors.textSecondary,
                labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12.5),
                tabs: const [
                  Tab(icon: Icon(Icons.bolt, size: 18), text: "القبول التلقائي"),
                  Tab(icon: Icon(Icons.palette, size: 18), text: "شكل العلامة"),
                  Tab(icon: Icon(Icons.map, size: 18), text: "المناطق"),
                  Tab(icon: Icon(Icons.attach_money, size: 18), text: "التسعير"),
                  Tab(icon: Icon(Icons.settings, size: 18), text: "التطبيق"),
                ],
              ),
            ),
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
  // TAB 1: AUTO-ACCEPT
  // ==========================================
  Future<void> _setAutoAccept(bool enable) async {
    final provider = context.read<AssistantProvider>();
    if (enable && !provider.autoAcceptConsentGiven) {
      final ok = await confirmAction(
        context,
        title: "تحذير قبل تفعيل القبول التلقائي",
        message: "سيضغط التطبيق زر \"قبول\" في إن درايف نيابة عنك عندما يطابق العرض شروطك.\n\n"
            "• الأتمتة قد تخالف شروط استخدام إن درايف وقد تعرّض حسابك للإيقاف. أنت المسؤول عن استخدامها.\n"
            "• لا يتم القبول إذا لم تُقرأ المسافة بدقة.\n"
            "• يعيد التطبيق فحص الطلب قبل الضغط مباشرة، ويلغي العملية إذا تغيّر.\n"
            "• يمكنك الإيقاف في أي وقت من هنا أو بإيقاف المساعد العائم من الإشعار.",
        confirmLabel: "أفهم المخاطر، فعّل",
        destructive: false,
      );
      if (!ok) return;
      await provider.recordAutoAcceptConsent();
    }
    _update((s) => s.copyWith(autoAcceptEnabled: enable));
  }

  Widget _buildAutoAcceptTab() {
    final settings = context.watch<AssistantProvider>().driverSettings;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        SwitchListTile(
          title: const Text("تفعيل القبول التلقائي للطلبات", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
          subtitle: const Text(
            "يقبل العروض المطابقة لشروطك فقط، بعد إعادة التحقق منها",
            style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary),
          ),
          value: settings.autoAcceptEnabled,
          activeColor: AppColors.neonGreen,
          contentPadding: EdgeInsets.zero,
          onChanged: _setAutoAccept,
        ),
        const Divider(color: AppColors.darkCardBorder),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "الحد الأدنى لصافي ربح الرحلة",
          unit: settings.currency,
          value: settings.autoAcceptMinNetProfit,
          step: 5.0,
          min: 0,
          max: 1000,
          onChanged: (v) => _update((s) => s.copyWith(autoAcceptMinNetProfit: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "الحد الأدنى لصافي الربح لكل كيلومتر",
          unit: "${settings.currency}/كم",
          value: settings.autoAcceptMinProfitPerKm,
          step: 0.5,
          min: 0,
          max: 100,
          onChanged: (v) => _update((s) => s.copyWith(autoAcceptMinProfitPerKm: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "أقصى مسافة وصول للراكب",
          unit: "كم",
          value: settings.autoAcceptMaxPickupKm,
          step: 0.5,
          min: 0.5,
          max: 30,
          onChanged: (v) => _update((s) => s.copyWith(autoAcceptMaxPickupKm: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "مهلة الانتظار قبل القبول",
          unit: "ثانية",
          value: settings.autoAcceptDelaySeconds,
          step: 0.5,
          min: 0.5,
          max: 8,
          onChanged: (v) => _update((s) => s.copyWith(autoAcceptDelaySeconds: v)),
        ),
        const SizedBox(height: 14),
        SwitchListTile(
          title: const Text("قبول الطلبات المرتبطة بالمناطق المفضلة فقط", style: TextStyle(fontSize: 13.5)),
          value: settings.autoAcceptOnlyFavoriteZones,
          activeColor: AppColors.accentAmber,
          contentPadding: EdgeInsets.zero,
          onChanged: (v) => _update((s) => s.copyWith(autoAcceptOnlyFavoriteZones: v)),
        ),
        SwitchListTile(
          title: const Text("عدم قبول طلبات المناطق المحظورة أبداً", style: TextStyle(fontSize: 13.5)),
          subtitle: const Text("موصى به", style: TextStyle(fontSize: 11, color: AppColors.textSecondary)),
          value: settings.autoAcceptRejectBlacklisted,
          activeColor: AppColors.lossRed,
          contentPadding: EdgeInsets.zero,
          onChanged: (v) => _update((s) => s.copyWith(autoAcceptRejectBlacklisted: v)),
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

    const styles = [
      {"id": "COMPACT_CIRCLE", "title": "قرص دائري", "desc": "دائرة تعرض صافي الربح"},
      {"id": "SLIM_PILL", "title": "كبسولة أفقية", "desc": "شريط أفقي مدمج ومريح للعين"},
      {"id": "SPEEDO_GAUGE", "title": "عداد رقمي", "desc": "تصميم عداد سيارة"},
      {"id": "MINI_TAG", "title": "شارة مدمجة", "desc": "شارة ملونة بالكامل حسب الربحية"},
      {"id": "CORNER_RIBBON", "title": "شريط جانبي", "desc": "شريط صغير لا يشغل مساحة"},
      {"id": "GLOW_DOT", "title": "نقطة مضيئة", "desc": "نقطة عالية التباين"},
    ];

    const sizes = [
      {"id": "SMALL", "label": "صغير"},
      {"id": "MEDIUM", "label": "متوسط"},
      {"id": "LARGE", "label": "كبير"},
    ];

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text(
          "شكل العلامة العائمة فوق إن درايف:",
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
                              fontSize: 13.5,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(s["desc"]!, style: const TextStyle(color: AppColors.textSecondary, fontSize: 11.5)),
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
                  label: Center(child: Text(sz["label"]!)),
                  selected: isSelected,
                  selectedColor: AppColors.neonGreen,
                  backgroundColor: AppColors.darkSurface,
                  labelStyle: TextStyle(
                    color: isSelected ? AppColors.darkBackground : AppColors.textPrimary,
                    fontSize: 12,
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
  // TAB 3: ZONES
  // ==========================================
  Widget _buildZonesTab() {
    final provider = context.watch<AssistantProvider>();
    final settings = provider.driverSettings;

    Widget section({
      required IconData icon,
      required Color color,
      required String title,
      required List<String> zones,
      required Future<bool> Function(String) onAdd,
      required Future<bool> Function(String) onRemove,
    }) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: color, size: 20),
              const SizedBox(width: 6),
              Expanded(
                child: Text(title, style: const TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 14)),
              ),
              IconButton(
                tooltip: "إضافة",
                icon: Icon(Icons.add_circle, color: color),
                onPressed: () => _showAddZoneDialog(title, onAdd),
              ),
            ],
          ),
          if (zones.isEmpty)
            const Text("لا توجد مناطق", style: TextStyle(color: AppColors.textSecondary, fontSize: 12)),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: zones.map((z) {
              return Chip(
                label: Text(z, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600)),
                backgroundColor: color.withOpacity(0.18),
                labelStyle: TextStyle(color: color),
                deleteIcon: Icon(Icons.close, size: 16, color: color),
                onDeleted: () async {
                  final ok = await confirmAction(context, title: "إزالة '$z'؟", message: "سيتم حذف المنطقة من هذه القائمة.");
                  if (ok) await onRemove(z);
                },
              );
            }).toList(),
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        section(
          icon: Icons.star,
          color: AppColors.accentAmber,
          title: "المناطق المفضلة",
          zones: settings.favoriteZones,
          onAdd: provider.addFavoriteZone,
          onRemove: provider.removeFavoriteZone,
        ),
        const SizedBox(height: 20),
        const Divider(color: AppColors.darkCardBorder),
        const SizedBox(height: 10),
        section(
          icon: Icons.block,
          color: AppColors.lossRed,
          title: "المناطق المحظورة / غير المرغوبة",
          zones: settings.blacklistedZones,
          onAdd: provider.addBlacklistZone,
          onRemove: provider.removeBlacklistZone,
        ),
        const SizedBox(height: 16),
        const Text(
          "تُطابَق المنطقة إذا ظهر اسمها داخل عنوان التحرك أو الوصول (بتجاهل الهمزات والتاء المربوطة).",
          style: TextStyle(color: AppColors.textSecondary, fontSize: 11.5, height: 1.4),
        ),
      ],
    );
  }

  void _showAddZoneDialog(String title, Future<bool> Function(String) onAdd) {
    final ctrl = TextEditingController();
    String? error;
    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) => AlertDialog(
          title: Text("إضافة إلى: $title", style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            decoration: InputDecoration(hintText: "اكتب اسم المنطقة هنا...", errorText: error),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("إلغاء")),
            ElevatedButton(
              onPressed: () async {
                final text = ctrl.text.trim();
                if (text.isEmpty) {
                  setDlg(() => error = "اكتب اسم المنطقة");
                  return;
                }
                final added = await onAdd(text);
                if (!added) {
                  setDlg(() => error = "المنطقة موجودة بالفعل");
                  return;
                }
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text("إضافة"),
            ),
          ],
        ),
      ),
    );
  }

  // ==========================================
  // TAB 4: PRICING
  // ==========================================
  Widget _buildPricingTab() {
    final settings = context.watch<AssistantProvider>().driverSettings;
    final c = settings.currency;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text(
          "قيم البنزين والاستهلاك تُحفظ على المركبة النشطة تلقائياً.",
          style: TextStyle(color: AppColors.textSecondary, fontSize: 11.5),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "استهلاك الوقود",
          unit: "لتر/100كم",
          value: settings.fuelConsumptionLPer100Km,
          step: 0.5,
          min: 1,
          max: 40,
          onChanged: (v) => _update((s) => s.copyWith(fuelConsumptionLPer100Km: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "سعر لتر الوقود",
          unit: c,
          value: settings.fuelPricePerLiter,
          step: 0.25,
          min: 0.25,
          max: 500,
          onChanged: (v) => _update((s) => s.copyWith(fuelPricePerLiter: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "عمولة إن درايف",
          unit: "%",
          value: settings.inDriveCommissionPercent,
          step: 0.5,
          min: 0,
          max: 50,
          onChanged: (v) => _update((s) => s.copyWith(inDriveCommissionPercent: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "الاستهلاك والصيانة",
          unit: "$c/كم",
          value: settings.wearAndTearPerKm,
          step: 0.1,
          min: 0,
          max: 50,
          onChanged: (v) => _update((s) => s.copyWith(wearAndTearPerKm: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "الحد الأدنى للربح المطلوب",
          unit: "$c/كم",
          value: settings.minProfitPerKm,
          step: 0.5,
          min: 0,
          max: 100,
          onChanged: (v) => _update((s) => s.copyWith(minProfitPerKm: v)),
        ),
        const SizedBox(height: 10),
        _buildNumberSettingTile(
          title: "الهدف اليومي",
          unit: c,
          value: settings.targetDailyEarnings,
          step: 100.0,
          min: 0,
          max: 100000,
          onChanged: (v) => _update((s) => s.copyWith(targetDailyEarnings: v)),
        ),
      ],
    );
  }

  // ==========================================
  // TAB 5: APP & UPDATES
  // ==========================================
  Future<void> _checkForUpdate() async {
    setState(() => _isCheckingUpdate = true);
    final result = await UpdateManager.checkForUpdate();
    if (!mounted) return;
    setState(() {
      _isCheckingUpdate = false;
      _updateInfo = result.info;
    });

    if (result.error != null) {
      _snack(result.error!);
    } else if (result.info!.isAvailable) {
      _snack("يتوفر تحديث جديد: ${result.info!.versionName}");
    } else {
      _snack("أنت تستخدم أحدث إصدار (${result.info!.currentVersion})");
    }
  }

  Future<void> _downloadAndInstall() async {
    final info = _updateInfo!;
    setState(() {
      _isDownloadingApk = true;
      _downloadProgress = 0.0;
    });

    final expectedSha = await UpdateManager.fetchExpectedSha256(info.sha256Url);
    final file = await UpdateManager.downloadApk(
      info.downloadUrl,
      onProgress: (p) {
        if (mounted) setState(() => _downloadProgress = p);
      },
    );

    if (!mounted) return;
    setState(() => _isDownloadingApk = false);

    if (file == null) {
      _snack("فشل تحميل التحديث. جرّب فتحه من المتصفح");
      return;
    }

    switch (await NativeBridgeService.installApk(file.path, expectedSha256: expectedSha)) {
      case InstallApkResult.started:
        break;
      case InstallApkResult.needsPermission:
        _snack("اسمح بتثبيت التطبيقات من هذا المصدر، ثم اضغط \"تثبيت\" مرة أخرى");
        break;
      case InstallApkResult.checksumMismatch:
        _snack("ملف التحديث تالف أو غير موثوق (البصمة غير مطابقة)، تم حذفه");
        break;
      case InstallApkResult.fileNotFound:
      case InstallApkResult.error:
        _snack("تعذر بدء التثبيت. جرّب فتح الرابط من المتصفح");
        break;
    }
  }

  Widget _buildAppSettingsTab() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
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
                  Expanded(
                    child: Text(
                      "تحديثات التطبيق",
                      style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                "الإصدار المثبت: ${_installedVersion.isEmpty ? '...' : _installedVersion}",
                style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5),
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                icon: _isCheckingUpdate
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.darkBackground),
                      )
                    : const Icon(Icons.cloud_download, size: 18),
                label: const Text("فحص وجود تحديث", style: TextStyle(fontWeight: FontWeight.bold)),
                onPressed: (_isCheckingUpdate || _isDownloadingApk) ? null : _checkForUpdate,
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
                      Text(
                        "إصدار جديد متوفر: ${_updateInfo!.versionName}",
                        style: const TextStyle(color: AppColors.neonGreen, fontWeight: FontWeight.bold, fontSize: 13.5),
                      ),
                      if (_updateInfo!.releaseNotes.isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Text(_updateInfo!.releaseNotes, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)),
                      ],
                      const SizedBox(height: 12),
                      if (_isDownloadingApk)
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Expanded(
                                  child: Text("جاري تحميل التحديث...", style: TextStyle(color: AppColors.textPrimary, fontSize: 12.5)),
                                ),
                                Text(
                                  "${(_downloadProgress * 100).toInt()}%",
                                  style: const TextStyle(color: AppColors.neonGreen, fontWeight: FontWeight.bold, fontSize: 12.5),
                                ),
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
                        )
                      else
                        Row(
                          children: [
                            Expanded(
                              child: ElevatedButton.icon(
                                style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 10)),
                                icon: const Icon(Icons.install_mobile, size: 18),
                                label: const Text("تحميل وتثبيت", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12.5)),
                                onPressed: _downloadAndInstall,
                              ),
                            ),
                            const SizedBox(width: 8),
                            IconButton(
                              tooltip: "فتح رابط التحميل في المتصفح",
                              icon: const Icon(Icons.open_in_browser, color: AppColors.textPrimary),
                              onPressed: () async {
                                final uri = Uri.parse(_updateInfo!.downloadUrl);
                                final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
                                if (!ok) _snack("تعذر فتح الرابط");
                              },
                            ),
                          ],
                        ),
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
    required String unit,
    required double value,
    required double step,
    required double min,
    required double max,
    required void Function(double) onChanged,
  }) {
    double clampRound(double v) => (v.clamp(min, max) * 100).round() / 100.0;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.darkSurface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.darkCardBorder),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(title, style: const TextStyle(color: AppColors.textPrimary, fontSize: 13)),
          ),
          IconButton(
            tooltip: "إنقاص",
            icon: const Icon(Icons.remove, size: 18),
            color: AppColors.textSecondary,
            onPressed: value <= min ? null : () => onChanged(clampRound(value - step)),
          ),
          InkWell(
            borderRadius: BorderRadius.circular(8),
            onTap: () async {
              final v = await promptNumber(context, title: title, initial: value, min: min, max: max, unit: unit);
              if (v != null) onChanged(clampRound(v));
            },
            child: Container(
              constraints: const BoxConstraints(minWidth: 64, minHeight: 40),
              alignment: Alignment.center,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              decoration: BoxDecoration(
                border: Border.all(color: AppColors.neonGreen.withOpacity(0.4)),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    formatNumber(value),
                    style: const TextStyle(color: AppColors.neonGreen, fontWeight: FontWeight.bold, fontSize: 14),
                  ),
                  Text(unit, style: const TextStyle(color: AppColors.textSecondary, fontSize: 9.5)),
                ],
              ),
            ),
          ),
          IconButton(
            tooltip: "زيادة",
            icon: const Icon(Icons.add, size: 18),
            color: AppColors.neonGreen,
            onPressed: value >= max ? null : () => onChanged(clampRound(value + step)),
          ),
        ],
      ),
    );
  }
}
