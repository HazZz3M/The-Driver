import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../data/models/hud_settings.dart';
import '../../providers/assistant_provider.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';
import '../components/dialogs.dart';
import '../components/hud_preview.dart';
import '../components/ui_kit.dart';
import 'settings_pages.dart';

class OverlayControlScreen extends StatefulWidget {
  const OverlayControlScreen({super.key});

  @override
  State<OverlayControlScreen> createState() => _OverlayControlScreenState();
}

class _OverlayControlScreenState extends State<OverlayControlScreen> with WidgetsBindingObserver {
  bool _hasOverlayPermission = false;
  bool _hasAccessibilityPermission = false;
  bool _isServiceRunning = false;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshPermissions();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // The driver comes back from Android settings after granting a permission.
    if (state == AppLifecycleState.resumed) _refreshPermissions();
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

  /// Google Play's "prominent disclosure" for the Accessibility API, shown before Settings opens.
  Future<void> _openAccessibilityWithDisclosure() async {
    final ok = await confirmAction(
      context,
      title: "قبل تفعيل خدمة قراءة الشاشة",
      message: "يستخدم التطبيق خدمة إمكانية الوصول لقراءة نص طلبات الرحلات (السعر، المسافة، العناوين) "
          "من شاشة إن درايف فقط، لحساب الربح وإظهار العلامة العائمة. "
          "وعند فتح طلب يلتقط الشاشة مرة واحدة لقراءة طول المشوار المرسوم على الخريطة.\n\n"
          "• المعالجة تتم داخل هاتفك، ولا تُحفظ أي صورة، ولا يُرسل أي شيء للخارج.\n"
          "• لا تُقرأ أي تطبيقات أخرى.\n"
          "• الضغط التلقائي لا يعمل إلا إذا فعّلت \"القبول التلقائي\" بنفسك.\n\n"
          "في الشاشة التالية اختر \"The Driver\" ثم فعّل الخدمة.",
      confirmLabel: "موافق، متابعة",
      destructive: false,
    );
    if (ok) await NativeBridgeService.openAccessibilitySettings();
  }

  /// Turns the accessibility service off so apps that refuse to run next to one (banking apps
  /// such as InstaPay) can be opened. Android lets only the driver turn it back on.
  Future<void> _pauseScreenReading() async {
    final messenger = ScaffoldMessenger.of(context);
    final paused = await NativeBridgeService.pauseScreenReading();
    await Future.delayed(const Duration(milliseconds: 300));
    await _refreshPermissions();
    messenger.showSnackBar(SnackBar(
      duration: const Duration(seconds: 6),
      content: Text(paused
          ? "تم إيقاف قراءة الشاشة. افتح تطبيق البنك الآن، وشغّلها بعدها من الإشعار أو من الإعدادات السريعة"
          : "قراءة الشاشة متوقفة بالفعل"),
    ));
  }

  Future<void> _toggleService() async {
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _busy = true);
    try {
      if (_isServiceRunning) {
        await NativeBridgeService.stopFloatingService();
      } else {
        if (!_hasOverlayPermission) {
          messenger.showSnackBar(const SnackBar(content: Text("امنح صلاحية العرض فوق التطبيقات أولاً (الخطوة 2)")));
          return;
        }
        final error = await NativeBridgeService.startFloatingService();
        if (error != null) {
          messenger.showSnackBar(SnackBar(content: Text(error)));
        } else {
          HapticFeedback.mediumImpact();
          if (!_hasAccessibilityPermission) {
            messenger.showSnackBar(const SnackBar(
              content: Text("المساعد يعمل، لكن لن تظهر العلامات حتى تفعّل قراءة الشاشة (الخطوة 1)"),
            ));
          }
        }
      }
      await Future.delayed(const Duration(milliseconds: 300));
      await _refreshPermissions();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _armCapture() async {
    final messenger = ScaffoldMessenger.of(context);
    if (!_hasAccessibilityPermission) {
      messenger.showSnackBar(const SnackBar(content: Text("فعّل خدمة قراءة الشاشة أولاً")));
      return;
    }
    await NativeBridgeService.requestScreenCapture();
    messenger.showSnackBar(const SnackBar(
      duration: Duration(seconds: 6),
      content: Text("افتح إن درايف على شاشة الطلبات والكروت ظاهرة، سيتم الالتقاط تلقائياً. ثم ارجع واضغط \"نسخ اللقطة\""),
    ));
  }

  Future<void> _copyDump() async {
    final messenger = ScaffoldMessenger.of(context);
    final dump = await NativeBridgeService.getLastScreenDump();
    if (dump == null || dump.isEmpty) {
      messenger.showSnackBar(const SnackBar(content: Text("لا توجد لقطة بعد. اضغط \"التقاط\" أولاً")));
      return;
    }
    await Clipboard.setData(ClipboardData(text: dump));
    messenger.showSnackBar(const SnackBar(content: Text("تم نسخ اللقطة. الصقها في رسالة للمطور")));
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final hud = provider.hud;
    final ready = _hasOverlayPermission && _hasAccessibilityPermission;
    final statusColor = _isServiceRunning ? AppColors.emeraldPrimary : AppColors.textTertiary;

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _refreshPermissions,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
            children: [
              const PageHeader(title: "المساعد العائم", subtitle: "علامة ربح فوق كل طلب في إن درايف"),

              // Status hero
              AppCard(
                borderColor: _isServiceRunning ? AppColors.emeraldPrimary.withValues(alpha: 0.5) : null,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        StatusPill(
                          label: _isServiceRunning ? "يعمل الآن" : "متوقف",
                          color: statusColor,
                          icon: _isServiceRunning ? Icons.radio_button_checked : Icons.radio_button_off,
                        ),
                        const Spacer(),
                        if (provider.driverSettings.autoAcceptEnabled)
                          const StatusPill(label: "القبول التلقائي مفعّل", color: AppColors.warningYellow, icon: Icons.bolt),
                      ],
                    ),
                    const SizedBox(height: Insets.md),
                    HudPlacementPreview(hud: hud),
                    const SizedBox(height: Insets.md),
                    SizedBox(
                      width: double.infinity,
                      child: _isServiceRunning
                          ? OutlinedButton.icon(
                              style: OutlinedButton.styleFrom(foregroundColor: AppColors.lossRed),
                              icon: const Icon(Icons.stop_circle_outlined),
                              label: const Text("إيقاف المساعد"),
                              onPressed: _busy ? null : _toggleService,
                            )
                          : FilledButton.icon(
                              icon: const Icon(Icons.play_arrow_rounded),
                              label: Text(ready ? "تشغيل المساعد" : "تشغيل المساعد (أكمل الإعداد أولاً)"),
                              onPressed: _busy ? null : _toggleService,
                            ),
                    ),
                  ],
                ),
              ),

              // Setup checklist
              SectionLabel(
                "خطوات الإعداد",
                action: IconButton(
                  tooltip: "تحديث",
                  visualDensity: VisualDensity.compact,
                  icon: const Icon(Icons.refresh, size: 20, color: AppColors.textSecondary),
                  onPressed: _refreshPermissions,
                ),
              ),
              SettingsGroup(children: [
                _StepTile(
                  step: 1,
                  title: "قراءة شاشة إن درايف",
                  subtitle: "لقراءة السعر والمسافة والعناوين من الكروت",
                  done: _hasAccessibilityPermission,
                  actionLabel: "تفعيل",
                  onAction: _openAccessibilityWithDisclosure,
                ),
                _StepTile(
                  step: 2,
                  title: "العرض فوق التطبيقات",
                  subtitle: "لإظهار العلامة فوق كروت الطلبات",
                  done: _hasOverlayPermission,
                  actionLabel: "منح",
                  onAction: NativeBridgeService.requestOverlayPermission,
                ),
                _StepTile(
                  step: 3,
                  title: "تشغيل المساعد",
                  subtitle: "يعمل في الخلفية ويمكن إيقافه من الإشعار",
                  done: _isServiceRunning,
                  actionLabel: "تشغيل",
                  onAction: ready ? _toggleService : null,
                ),
              ]),

              // Banking apps (e.g. InstaPay) refuse to open while an accessibility service is on.
              const SectionLabel("تطبيقات البنوك"),
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "بعض تطبيقات البنوك (زي إنستاباي) مش بتفتح طول ما قراءة الشاشة شغالة. "
                      "أوقفها قبل ما تفتح البنك، وشغّلها تاني لما ترجع لإن درايف.",
                      style: TextStyle(color: AppColors.textSecondary, fontSize: 12.5, height: 1.5),
                    ),
                    const SizedBox(height: Insets.md),
                    SizedBox(
                      width: double.infinity,
                      child: _hasAccessibilityPermission
                          ? OutlinedButton.icon(
                              icon: const Icon(Icons.pause_circle_outline),
                              label: const Text("إيقاف قراءة الشاشة الآن"),
                              onPressed: _pauseScreenReading,
                            )
                          : FilledButton.tonalIcon(
                              icon: const Icon(Icons.play_circle_outline),
                              label: const Text("تشغيل قراءة الشاشة"),
                              onPressed: _openAccessibilityWithDisclosure,
                            ),
                    ),
                    const SizedBox(height: Insets.md),
                    const Text("أسرع طريقة للتشغيل والإيقاف:", style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
                    const SizedBox(height: 4),
                    const Text(
                      "• زر \"قراءة الشاشة\" في الإعدادات السريعة: اسحب شريط الإشعارات لتحت، اضغط على القلم (تعديل)، واسحب الزر للقائمة.\n"
                      "• زر \"إيقاف / تشغيل قراءة الشاشة\" في إشعار المساعد.\n"
                      "• اختصار أزرار الصوت: من إعدادات الهاتف ← إمكانية الوصول ← The Driver ← الاختصار، "
                      "وبعدها اضغط على زرّي الصوت معاً 3 ثوانٍ.",
                      style: TextStyle(color: AppColors.textSecondary, fontSize: 12.5, height: 1.6),
                    ),
                  ],
                ),
              ),

              // Quick customisation
              const SectionLabel("التخصيص"),
              SettingsGroup(children: [
                NavTile(
                  icon: Icons.palette_outlined,
                  title: "شكل ومحتوى العلامة",
                  subtitle: "${HudSettings.styleLabels[hud.style]} • يعرض ${HudSettings.primaryValueLabels[hud.primaryValue]}",
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const HudSettingsPage())),
                ),
                NavTile(
                  icon: Icons.bolt_outlined,
                  color: AppColors.warningYellow,
                  title: "القبول التلقائي",
                  trailingText: provider.driverSettings.autoAcceptEnabled ? "مفعّل" : "متوقف",
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const AutoAcceptPage())),
                ),
                NavTile(
                  icon: Icons.place_outlined,
                  color: AppColors.accentAmber,
                  title: "المناطق المفضلة والمحظورة",
                  trailingText:
                      "${provider.driverSettings.favoriteZones.length} / ${provider.driverSettings.blacklistedZones.length}",
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ZonesPage())),
                ),
              ]),

              // Legend
              const SectionLabel("معنى الألوان"),
              AppCard(
                child: Column(
                  children: const [
                    _LegendRow(color: AppColors.emeraldPrimary, title: "أخضر", text: "عرض رابح ويحقق هدفك لكل كم"),
                    _LegendRow(color: AppColors.warningYellow, title: "أصفر", text: "رابح لكن أقل من هدفك - اقترح زيادة"),
                    _LegendRow(color: AppColors.lossRed, title: "أحمر", text: "خسارة أو منطقة محظورة"),
                    _LegendRow(color: Color(0xFF64748B), title: "رمادي ؟", text: "لم تُقرأ المسافة من الكارت"),
                    _LegendRow(color: Color(0xFFFFB800), title: "★ / ≈", text: "منطقة مفضلة / ربح تقريبي (مسافة مفترضة)"),
                  ],
                ),
              ),

              // Diagnostics
              const SectionLabel("تشخيص قراءة الكروت"),
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "إذا ظهرت المسافة أو العنوان بشكل خاطئ: التقط شاشة الطلبات وأرسل النص للمطور. "
                      "اللقطة تحتوي نصوص الشاشة (أسماء وعناوين) ولا تُرسل لأي جهة تلقائياً.",
                      style: TextStyle(color: AppColors.textSecondary, fontSize: 12.5, height: 1.5),
                    ),
                    const SizedBox(height: Insets.md),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            icon: const Icon(Icons.camera_alt_outlined, size: 18),
                            label: const Text("التقاط"),
                            onPressed: _armCapture,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: OutlinedButton.icon(
                            icon: const Icon(Icons.copy_rounded, size: 18),
                            label: const Text("نسخ اللقطة"),
                            onPressed: _copyDump,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StepTile extends StatelessWidget {
  final int step;
  final String title;
  final String subtitle;
  final bool done;
  final String actionLabel;
  final VoidCallback? onAction;

  const _StepTile({
    required this.step,
    required this.title,
    required this.subtitle,
    required this.done,
    required this.actionLabel,
    required this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Container(
        width: 34,
        height: 34,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: done ? AppColors.emeraldPrimary.withValues(alpha: 0.16) : AppColors.darkSurfaceVariant,
          shape: BoxShape.circle,
          border: Border.all(color: done ? AppColors.emeraldPrimary : AppColors.darkCardBorder),
        ),
        child: done
            ? const Icon(Icons.check, size: 18, color: AppColors.emeraldLight)
            : Text("$step", style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.textSecondary)),
      ),
      title: Text(title, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600)),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
      trailing: done
          ? const Text("تم", style: TextStyle(color: AppColors.emeraldLight, fontWeight: FontWeight.w700))
          : FilledButton.tonal(
              style: FilledButton.styleFrom(minimumSize: const Size(64, 36), padding: const EdgeInsets.symmetric(horizontal: 14)),
              onPressed: onAction,
              child: Text(actionLabel),
            ),
    );
  }
}

class _LegendRow extends StatelessWidget {
  final Color color;
  final String title;
  final String text;

  const _LegendRow({required this.color, required this.title, required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Container(
            width: 14,
            height: 14,
            decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: color, width: 3)),
          ),
          const SizedBox(width: 10),
          SizedBox(width: 64, child: Text(title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13))),
          Expanded(child: Text(text, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5))),
        ],
      ),
    );
  }
}
