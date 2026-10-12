import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../data/models/driver_settings.dart';
import '../../data/models/hud_settings.dart';
import '../../providers/assistant_provider.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';
import '../../utils/update_manager.dart';
import '../components/dialogs.dart';
import '../components/hud_preview.dart';
import '../components/ui_kit.dart';

/// Scaffold shared by all settings sub-pages.
class _SubPage extends StatelessWidget {
  final String title;
  final List<Widget> children;

  const _SubPage({required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
          children: children,
        ),
      ),
    );
  }
}

void _updateSettings(BuildContext context, DriverSettings Function(DriverSettings s) change) {
  final p = context.read<AssistantProvider>();
  p.updateSettings(change(p.driverSettings));
}

void _updateHud(BuildContext context, HudSettings Function(HudSettings h) change) {
  final p = context.read<AssistantProvider>();
  p.updateHud(change(p.hud));
}

// =============================================================================
// FLOATING CARD (HUD)
// =============================================================================
class HudSettingsPage extends StatelessWidget {
  const HudSettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final hud = context.watch<AssistantProvider>().hud;
    void change(HudSettings Function(HudSettings h) change) => _updateHud(context, change);

    return _SubPage(
      title: "العلامة العائمة",
      children: [
        const SizedBox(height: Insets.sm),
        HudPlacementPreview(hud: hud),
        const SizedBox(height: Insets.sm),
        const Text(
          "التغييرات تُطبَّق فوراً على المساعد أثناء تشغيله.",
          textAlign: TextAlign.center,
          style: TextStyle(color: AppColors.textTertiary, fontSize: 11.5),
        ),

        const SectionLabel("ماذا تعرض العلامة"),
        SettingsGroup(children: [
          ChoiceTile<String>(
            title: "الرقم الظاهر",
            icon: Icons.numbers,
            value: hud.primaryValue,
            options: HudSettings.primaryValueLabels,
            onChanged: (v) => change((h) => h.copyWith(primaryValue: v)),
          ),
          SwitchTile(
            icon: Icons.visibility_off_outlined,
            iconColor: AppColors.lossRed,
            title: "إظهار العلامة على العروض الخاسرة",
            subtitle: "عند الإيقاف تختفي العلامة من الطلبات الخاسرة وغير المقروءة",
            value: hud.showLossBadges,
            onChanged: (v) => change((h) => h.copyWith(showLossBadges: v)),
          ),
        ]),

        const SectionLabel("الشكل والموضع"),
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text("النمط", style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: Insets.md),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: HudSettings.styleLabels.entries.map((e) {
                  final selected = hud.style == e.key;
                  return InkWell(
                    borderRadius: BorderRadius.circular(Insets.radiusMd),
                    onTap: () => change((h) => h.copyWith(style: e.key)),
                    child: Container(
                      width: 96,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      decoration: BoxDecoration(
                        color: selected ? AppColors.emeraldPrimary.withValues(alpha: 0.12) : AppColors.darkSurfaceVariant,
                        borderRadius: BorderRadius.circular(Insets.radiusMd),
                        border: Border.all(
                          color: selected ? AppColors.emeraldPrimary : AppColors.darkCardBorder,
                          width: selected ? 1.6 : 1,
                        ),
                      ),
                      child: Column(
                        children: [
                          SizedBox(
                            height: 56,
                            child: Center(child: HudBadgePreview(hud: hud.copyWith(style: e.key, size: "SMALL", opacity: 1.0))),
                          ),
                          const SizedBox(height: 6),
                          Text(e.value, style: TextStyle(fontSize: 12, color: selected ? AppColors.emeraldLight : AppColors.textSecondary)),
                        ],
                      ),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: Insets.lg),
              const Text("الحجم", style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: Insets.sm),
              SizedBox(
                width: double.infinity,
                child: SegmentedButton<String>(
                  showSelectedIcon: false,
                  segments: HudSettings.sizeLabels.entries
                      .map((e) => ButtonSegment<String>(value: e.key, label: Text(e.value)))
                      .toList(),
                  selected: {hud.size},
                  onSelectionChanged: (s) => change((h) => h.copyWith(size: s.first)),
                ),
              ),
              const SizedBox(height: Insets.lg),
              const Text("جهة الشاشة", style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: Insets.sm),
              SizedBox(
                width: double.infinity,
                child: SegmentedButton<String>(
                  showSelectedIcon: false,
                  segments: const [
                    ButtonSegment(value: "LEFT", label: Text("يسار"), icon: Icon(Icons.align_horizontal_left)),
                    ButtonSegment(value: "RIGHT", label: Text("يمين"), icon: Icon(Icons.align_horizontal_right)),
                  ],
                  selected: {hud.side},
                  onSelectionChanged: (s) => change((h) => h.copyWith(side: s.first)),
                ),
              ),
              const SizedBox(height: Insets.lg),
              Row(
                children: [
                  const Expanded(child: Text("الشفافية", style: TextStyle(fontWeight: FontWeight.w600))),
                  Text("${(hud.opacity * 100).round()}%", style: const TextStyle(color: AppColors.emeraldLight, fontWeight: FontWeight.w700)),
                ],
              ),
              Slider(
                value: hud.opacity,
                min: 0.4,
                max: 1.0,
                divisions: 6,
                onChanged: (v) => change((h) => h.copyWith(opacity: v)),
              ),
            ],
          ),
        ),

        const SectionLabel("البطاقة المفصّلة (عند الضغط على العلامة)"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.compare_arrows,
            title: "مقارنة العرض بالمزايدة المقترحة",
            value: hud.showFareComparison,
            onChanged: (v) => change((h) => h.copyWith(showFareComparison: v)),
          ),
          SwitchTile(
            icon: Icons.account_balance_wallet_outlined,
            title: "صافي الربح والربح لكل كم",
            value: hud.showProfit,
            onChanged: (v) => change((h) => h.copyWith(showProfit: v)),
          ),
          SwitchTile(
            icon: Icons.route_outlined,
            title: "المسافات (وصول + مشوار)",
            value: hud.showDistance,
            onChanged: (v) => change((h) => h.copyWith(showDistance: v)),
          ),
          SwitchTile(
            icon: Icons.receipt_long_outlined,
            title: "تفصيل التكاليف",
            subtitle: "بنزين + عمولة + استهلاك",
            value: hud.showCostBreakdown,
            onChanged: (v) => change((h) => h.copyWith(showCostBreakdown: v)),
          ),
          SwitchTile(
            icon: Icons.place_outlined,
            title: "أزرار المناطق (مفضلة / محظورة)",
            value: hud.showZones,
            onChanged: (v) => change((h) => h.copyWith(showZones: v)),
          ),
          ChoiceTile<int>(
            title: "إغلاق تلقائي للبطاقة",
            icon: Icons.timer_outlined,
            value: hud.autoCollapseSeconds,
            options: const {0: "أبداً", 5: "بعد 5 ث", 10: "بعد 10 ث", 20: "بعد 20 ث"},
            onChanged: (v) => change((h) => h.copyWith(autoCollapseSeconds: v)),
          ),
        ]),

        const SectionLabel("التنبيهات"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.vibration,
            title: "اهتزاز عند ظهور طلب جديد",
            value: hud.vibrate,
            onChanged: (v) => change((h) => h.copyWith(vibrate: v)),
          ),
          if (hud.vibrate)
            SwitchTile(
              icon: Icons.filter_alt_outlined,
              title: "للطلبات الرابحة فقط",
              subtitle: "لا اهتزاز للخاسرة أو ذات المسافة التقريبية",
              value: hud.vibrateProfitableOnly,
              onChanged: (v) => change((h) => h.copyWith(vibrateProfitableOnly: v)),
            ),
        ]),

        const SizedBox(height: Insets.lg),
        Center(
          child: TextButton.icon(
            icon: const Icon(Icons.restart_alt),
            label: const Text("استعادة الإعدادات الافتراضية"),
            onPressed: () => context.read<AssistantProvider>().updateHud(const HudSettings()),
          ),
        ),
      ],
    );
  }
}

// =============================================================================
// AUTO-ACCEPT
// =============================================================================
class AutoAcceptPage extends StatelessWidget {
  const AutoAcceptPage({super.key});

  Future<void> _setEnabled(BuildContext context, bool enable) async {
    final provider = context.read<AssistantProvider>();
    if (enable && !provider.autoAcceptConsentGiven) {
      final ok = await confirmAction(
        context,
        title: "تحذير قبل تفعيل القبول التلقائي",
        message: "سيضغط التطبيق زر \"قبول\" في إن درايف نيابة عنك عندما يطابق العرض شروطك.\n\n"
            "• الأتمتة قد تخالف شروط استخدام إن درايف وقد تعرّض حسابك للإيقاف. أنت المسؤول عن استخدامها.\n"
            "• لا يتم القبول إذا كانت المسافة غير مقروءة أو تقريبية.\n"
            "• يعيد التطبيق فحص الطلب قبل الضغط مباشرة، ويلغي العملية إذا تغيّر.\n"
            "• يمكنك الإيقاف في أي وقت من هنا أو من إشعار المساعد.",
        confirmLabel: "أفهم المخاطر، فعّل",
        destructive: false,
      );
      if (!ok) return;
      await provider.recordAutoAcceptConsent();
    }
    if (context.mounted) _updateSettings(context, (s) => s.copyWith(autoAcceptEnabled: enable));
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<AssistantProvider>().driverSettings;
    void change(DriverSettings Function(DriverSettings s) c) => _updateSettings(context, c);

    return _SubPage(
      title: "القبول التلقائي",
      children: [
        const SizedBox(height: Insets.sm),
        AppCard(
          borderColor: s.autoAcceptEnabled ? AppColors.warningYellow.withValues(alpha: 0.6) : null,
          padding: EdgeInsets.zero,
          child: SwitchTile(
            icon: Icons.bolt,
            iconColor: AppColors.warningYellow,
            title: "تفعيل القبول التلقائي",
            subtitle: "يقبل فقط العروض المطابقة لكل الشروط أدناه، بعد إعادة التحقق",
            value: s.autoAcceptEnabled,
            onChanged: (v) => _setEnabled(context, v),
          ),
        ),
        const SectionLabel("شروط القبول"),
        SettingsGroup(children: [
          NumberStepperTile(
            title: "أقل صافي ربح",
            unit: s.currency,
            value: s.autoAcceptMinNetProfit,
            step: 5,
            min: 0,
            max: 1000,
            onChanged: (v) => change((x) => x.copyWith(autoAcceptMinNetProfit: v)),
          ),
          NumberStepperTile(
            title: "أقل ربح لكل كم",
            unit: "${s.currency}/كم",
            value: s.autoAcceptMinProfitPerKm,
            step: 0.5,
            min: 0,
            max: 100,
            onChanged: (v) => change((x) => x.copyWith(autoAcceptMinProfitPerKm: v)),
          ),
          NumberStepperTile(
            title: "أقصى مسافة وصول",
            unit: "كم",
            value: s.autoAcceptMaxPickupKm,
            step: 0.5,
            min: 0.5,
            max: 30,
            onChanged: (v) => change((x) => x.copyWith(autoAcceptMaxPickupKm: v)),
          ),
          NumberStepperTile(
            title: "الانتظار قبل القبول",
            subtitle: "وقت لتراجع يدوي",
            unit: "ثانية",
            value: s.autoAcceptDelaySeconds,
            step: 0.5,
            min: 0.5,
            max: 8,
            onChanged: (v) => change((x) => x.copyWith(autoAcceptDelaySeconds: v)),
          ),
        ]),
        const SectionLabel("المناطق"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.star_outline,
            iconColor: AppColors.accentAmber,
            title: "المناطق المفضلة فقط",
            value: s.autoAcceptOnlyFavoriteZones,
            onChanged: (v) => change((x) => x.copyWith(autoAcceptOnlyFavoriteZones: v)),
          ),
          SwitchTile(
            icon: Icons.block,
            iconColor: AppColors.lossRed,
            title: "رفض المناطق المحظورة دائماً",
            subtitle: "موصى به",
            value: s.autoAcceptRejectBlacklisted,
            onChanged: (v) => change((x) => x.copyWith(autoAcceptRejectBlacklisted: v)),
          ),
        ]),
      ],
    );
  }
}

// =============================================================================
// ZONES
// =============================================================================
class ZonesPage extends StatelessWidget {
  const ZonesPage({super.key});

  Future<void> _addZone(BuildContext context, String title, Future<bool> Function(String) onAdd) async {
    final ctrl = TextEditingController();
    String? error;
    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) => AlertDialog(
          title: Text(title),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            decoration: InputDecoration(hintText: "مثلاً: التجمع الخامس", errorText: error),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("إلغاء")),
            FilledButton(
              onPressed: () async {
                final text = ctrl.text.trim();
                if (text.isEmpty) {
                  setDlg(() => error = "اكتب اسم المنطقة");
                  return;
                }
                if (!await onAdd(text)) {
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

  Widget _section(
    BuildContext context, {
    required String title,
    required String hint,
    required IconData icon,
    required Color color,
    required List<String> zones,
    required Future<bool> Function(String) onAdd,
    required Future<bool> Function(String) onRemove,
  }) {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              TileIcon(icon, color: color),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14.5)),
                    Text(hint, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)),
                  ],
                ),
              ),
              IconButton.filledTonal(
                tooltip: "إضافة",
                icon: const Icon(Icons.add),
                onPressed: () => _addZone(context, "إضافة إلى $title", onAdd),
              ),
            ],
          ),
          const SizedBox(height: Insets.md),
          if (zones.isEmpty)
            const Text("لا توجد مناطق بعد", style: TextStyle(color: AppColors.textTertiary, fontSize: 12.5))
          else
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: zones
                  .map((z) => InputChip(
                        label: Text(z),
                        backgroundColor: color.withValues(alpha: 0.12),
                        side: BorderSide(color: color.withValues(alpha: 0.35)),
                        deleteIconColor: color,
                        onDeleted: () async {
                          await onRemove(z);
                          if (context.mounted) {
                            showUndoSnackBar(context, "تمت إزالة '$z'", () => onAdd(z));
                          }
                        },
                      ))
                  .toList(),
            ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final s = provider.driverSettings;

    return _SubPage(
      title: "المناطق",
      children: [
        const SizedBox(height: Insets.sm),
        _section(
          context,
          title: "المناطق المفضلة",
          hint: "تظهر بنجمة ★ على العلامة",
          icon: Icons.star_rounded,
          color: AppColors.accentAmber,
          zones: s.favoriteZones,
          onAdd: provider.addFavoriteZone,
          onRemove: provider.removeFavoriteZone,
        ),
        const SizedBox(height: Insets.md),
        _section(
          context,
          title: "المناطق المحظورة",
          hint: "تُحسب خاسرة إلا إذا كان السعر مرتفعاً جداً",
          icon: Icons.block,
          color: AppColors.lossRed,
          zones: s.blacklistedZones,
          onAdd: provider.addBlacklistZone,
          onRemove: provider.removeBlacklistZone,
        ),
        const SizedBox(height: Insets.md),
        const Text(
          "تُطابَق المنطقة إذا ظهر اسمها داخل عنوان التحرك أو الوصول، مع تجاهل الهمزات والتاء المربوطة. "
          "يمكنك أيضاً إضافة المناطق مباشرة من البطاقة العائمة فوق إن درايف.",
          style: TextStyle(color: AppColors.textSecondary, fontSize: 12, height: 1.5),
        ),
      ],
    );
  }
}

// =============================================================================
// PRICING
// =============================================================================
class PricingPage extends StatelessWidget {
  const PricingPage({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final s = provider.driverSettings;
    final c = s.currency;
    void change(DriverSettings Function(DriverSettings s) change) => _updateSettings(context, change);

    return _SubPage(
      title: "التسعير والتكاليف",
      children: [
        SectionLabel("المركبة: ${provider.activeProfile?.name ?? ''}"),
        SettingsGroup(children: [
          NumberStepperTile(
            icon: Icons.local_gas_station_outlined,
            title: "استهلاك الوقود",
            unit: "ل/100كم",
            value: s.fuelConsumptionLPer100Km,
            step: 0.5,
            min: 1,
            max: 40,
            onChanged: (v) => change((x) => x.copyWith(fuelConsumptionLPer100Km: v)),
          ),
          NumberStepperTile(
            icon: Icons.attach_money,
            title: "سعر لتر الوقود",
            unit: c,
            value: s.fuelPricePerLiter,
            step: 0.25,
            min: 0.25,
            max: 500,
            onChanged: (v) => change((x) => x.copyWith(fuelPricePerLiter: v)),
          ),
          NumberStepperTile(
            icon: Icons.build_outlined,
            title: "الاستهلاك والصيانة",
            unit: "$c/كم",
            value: s.wearAndTearPerKm,
            step: 0.1,
            min: 0,
            max: 50,
            onChanged: (v) => change((x) => x.copyWith(wearAndTearPerKm: v)),
          ),
        ]),
        const SectionLabel("التطبيق والأهداف"),
        SettingsGroup(children: [
          NumberStepperTile(
            icon: Icons.percent,
            iconColor: AppColors.accentCyan,
            title: "عمولة إن درايف",
            unit: "%",
            value: s.inDriveCommissionPercent,
            step: 0.5,
            min: 0,
            max: 50,
            onChanged: (v) => change((x) => x.copyWith(inDriveCommissionPercent: v)),
          ),
          NumberStepperTile(
            icon: Icons.flag_outlined,
            iconColor: AppColors.accentCyan,
            title: "هدف الربح لكل كم",
            subtitle: "أساس الألوان والمزايدة المقترحة",
            unit: "$c/كم",
            value: s.minProfitPerKm,
            step: 0.5,
            min: 0,
            max: 100,
            onChanged: (v) => change((x) => x.copyWith(minProfitPerKm: v)),
          ),
          NumberStepperTile(
            icon: Icons.route_outlined,
            iconColor: AppColors.accentCyan,
            title: "مسافة المشوار المفترضة",
            subtitle: "إن درايف لا يعرض طول المشوار كنص، لذا يُستخدم هذا الرقم للحساب (≈). اضبطه على متوسط مشاويرك",
            unit: "كم",
            value: s.assumedTripKm,
            step: 0.5,
            min: 0.5,
            max: 50,
            onChanged: (v) => change((x) => x.copyWith(assumedTripKm: v)),
          ),
          NumberStepperTile(
            icon: Icons.emoji_events_outlined,
            iconColor: AppColors.accentCyan,
            title: "الهدف اليومي",
            unit: c,
            value: s.targetDailyEarnings,
            step: 100,
            min: 0,
            max: 100000,
            onChanged: (v) => change((x) => x.copyWith(targetDailyEarnings: v)),
          ),
        ]),
      ],
    );
  }
}

// =============================================================================
// UPDATES & ABOUT
// =============================================================================
class UpdatesPage extends StatefulWidget {
  const UpdatesPage({super.key});

  @override
  State<UpdatesPage> createState() => _UpdatesPageState();
}

class _UpdatesPageState extends State<UpdatesPage> {
  bool _checking = false;
  bool _downloading = false;
  double _progress = 0;
  UpdateInfo? _info;
  String _installed = "";

  @override
  void initState() {
    super.initState();
    UpdateManager.currentVersion().then((v) {
      if (mounted) setState(() => _installed = v);
    });
  }

  void _snack(String msg) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _check() async {
    setState(() => _checking = true);
    final result = await UpdateManager.checkForUpdate();
    if (!mounted) return;
    setState(() {
      _checking = false;
      _info = result.info;
    });
    if (result.error != null) {
      _snack(result.error!);
    } else if (!result.info!.isAvailable) {
      _snack("أنت تستخدم أحدث إصدار");
    }
  }

  Future<void> _install() async {
    final info = _info!;
    setState(() {
      _downloading = true;
      _progress = 0;
    });
    final sha = await UpdateManager.fetchExpectedSha256(info.sha256Url);
    final file = await UpdateManager.downloadApk(info.downloadUrl, onProgress: (p) {
      if (mounted) setState(() => _progress = p);
    });
    if (!mounted) return;
    setState(() => _downloading = false);
    if (file == null) {
      _snack("فشل التحميل. جرّب فتح الرابط من المتصفح");
      return;
    }

    switch (await NativeBridgeService.installApk(file.path, expectedSha256: sha)) {
      case InstallApkResult.started:
        break;
      case InstallApkResult.needsPermission:
        _snack("اسمح بتثبيت التطبيقات من هذا المصدر، ثم اضغط \"تثبيت\" مرة أخرى");
      case InstallApkResult.checksumMismatch:
        _snack("ملف التحديث غير موثوق (البصمة غير مطابقة) وتم حذفه");
      case InstallApkResult.fileNotFound:
      case InstallApkResult.error:
        _snack("تعذر بدء التثبيت. جرّب فتح الرابط من المتصفح");
    }
  }

  @override
  Widget build(BuildContext context) {
    final info = _info;
    return _SubPage(
      title: "التحديثات وحول التطبيق",
      children: [
        const SizedBox(height: Insets.sm),
        AppCard(
          child: Column(
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: AppColors.emeraldPrimary.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: const Icon(Icons.local_taxi_rounded, color: AppColors.emeraldLight, size: 34),
              ),
              const SizedBox(height: Insets.md),
              const Text("The Driver", style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
              const SizedBox(height: 2),
              Text("الإصدار ${_installed.isEmpty ? '...' : _installed}",
                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5)),
              const SizedBox(height: Insets.lg),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  icon: _checking
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.system_update_outlined),
                  label: const Text("البحث عن تحديث"),
                  onPressed: (_checking || _downloading) ? null : _check,
                ),
              ),
            ],
          ),
        ),
        if (info != null && info.isAvailable) ...[
          const SectionLabel("تحديث جديد"),
          AppCard(
            borderColor: AppColors.emeraldPrimary.withValues(alpha: 0.5),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text("الإصدار ${info.versionName}",
                    style: const TextStyle(color: AppColors.emeraldLight, fontWeight: FontWeight.w800, fontSize: 15)),
                if (info.releaseNotes.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(info.releaseNotes, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5, height: 1.5)),
                ],
                const SizedBox(height: Insets.md),
                if (_downloading) ...[
                  LinearProgressIndicator(value: _progress > 0 ? _progress : null, minHeight: 6),
                  const SizedBox(height: 6),
                  Text("${(_progress * 100).toInt()}%", style: const TextStyle(color: AppColors.textSecondary)),
                ] else
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton.icon(
                          icon: const Icon(Icons.install_mobile),
                          label: const Text("تحميل وتثبيت"),
                          onPressed: _install,
                        ),
                      ),
                      const SizedBox(width: 8),
                      IconButton.outlined(
                        tooltip: "فتح في المتصفح",
                        icon: const Icon(Icons.open_in_browser),
                        onPressed: () async {
                          final ok = await launchUrl(Uri.parse(info.downloadUrl), mode: LaunchMode.externalApplication);
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
    );
  }
}
