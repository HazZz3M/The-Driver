import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../data/models/driver_settings.dart';
import '../../data/models/hud_settings.dart';
import '../../engine/fare_calculator_engine.dart';
import '../../providers/assistant_provider.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
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
          style: TextStyle(color: AppColors.textTertiary, fontSize: 12),
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

        const SectionLabel("المزايدة في شاشة الطلب"),
        SettingsGroup(children: [
          ChoiceTile<String>(
            title: "دوائر على أزرار المزايدة",
            subtitle: "دايرة فوق كل سعر مقترح من إن درايف فيها صافي ربحك. أفضل عرض بيتلوّن وعليه نجمة: "
                "أقل سعر بيحقق هدفك، مش لازم الأعلى",
            icon: Icons.radio_button_checked,
            value: hud.bidChipsMode,
            options: const {
              "OFF": "متوقفة",
              "HIGHLIGHT": "عرض الدوائر وتمييز الأفضل",
              "AUTO_TAP": "تمييز الأفضل والضغط عليه تلقائياً",
            },
            onChanged: (v) async {
              if (v == "AUTO_TAP" &&
                  !await confirmAction(
                    context,
                    title: "تحذير: اختيار المزايدة تلقائياً",
                    message: "التطبيق هيضغط بنفسه على زر أفضل مزايدة في شاشة الطلب، مرة واحدة لكل طلب، "
                        "بعد نفس وقت الانتظار بتاع القبول التلقائي.\n\n"
                        "• الضغط على زر المزايدة في إن درايف ممكن يبعت العرض للراكب على طول.\n"
                        "• بيعيد قراءة الشاشة قبل الضغط، ومش بيضغط لو الطلب اتغيّر.\n"
                        "• الأتمتة ممكن تخالف شروط إن درايف وتعرّض حسابك للإيقاف. انت المسؤول عن استخدامها.",
                    confirmLabel: "أفهم المخاطر، فعّل",
                    destructive: false,
                  )) {
                return;
              }
              if (!context.mounted) return;
              change((h) => h.copyWith(bidChipsMode: v));
            },
          ),
        ]),

        const SectionLabel("القراءة"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.document_scanner_outlined,
            iconColor: AppColors.accentCyan,
            title: "قراءة طول المشوار من الخريطة",
            subtitle: "عند فتح الطلب يقرأ التطبيق الرقم المرسوم على الخريطة (مثل 2,7 km) داخل الهاتف دون حفظ أي صورة. يتطلب أندرويد 11 أو أحدث",
            value: hud.readMapDistance,
            onChanged: (v) => change((h) => h.copyWith(readMapDistance: v)),
          ),
          SwitchTile(
            icon: Icons.history,
            iconColor: AppColors.accentCyan,
            title: "تذكّر المسافات من رحلات سابقة",
            subtitle: "لما تفتح طلب ويتقري طوله، بيتحفظ لنفس مكان التحرك والوصول. "
                "ولما نفس المشوار يظهر تاني في القائمة يتعرض طوله على طول. كله محفوظ على الموبايل بس",
            value: hud.tripFromHistory,
            onChanged: (v) => change((h) => h.copyWith(tripFromHistory: v)),
          ),
          SwitchTile(
            icon: Icons.place_outlined,
            iconColor: AppColors.accentCyan,
            title: "تقدير المسافة من العناوين",
            subtitle: "بيدوّر على العنوانين في خرائط جوجل ويحسب مسافة تقريبية (≈). "
                "نص العنوان بيتبعت لجوجل، ومحتاج إنترنت",
            value: hud.tripFromAddresses,
            onChanged: (v) async {
              if (v &&
                  !await confirmAction(
                    context,
                    title: "تقدير المسافة من العناوين",
                    message: "عشان يحسب المسافة، التطبيق هيبعت نص عنوان التحرك والوصول لخدمة العناوين بتاعة جوجل "
                        "(مش اسم الراكب ولا أي بيانات تانية).\n\n"
                        "• المسافة تقريبية (خط مستقيم × 1.3) وممكن تفرق 10–25% عن الطريق الحقيقي.\n"
                        "• بتظهر بعلامة ≈، والقبول التلقائي مش هيقبل طلب بمسافة تقريبية.",
                    confirmLabel: "موافق، فعّل",
                    destructive: false,
                  )) {
                return;
              }
              if (!context.mounted) return;
              change((h) => h.copyWith(tripFromAddresses: v));
            },
          ),
          SwitchTile(
            icon: Icons.touch_app_outlined,
            iconColor: AppColors.warningYellow,
            title: "فتح الطلبات تلقائياً لقراءة المسافة",
            subtitle: "لو طول المشوار مش معروف، التطبيق يفتح الطلب بنفسه ويقرا الخريطة ويرجع للقائمة. "
                "يحتاج \"قراءة طول المشوار من الخريطة\"",
            value: hud.tripAutoOpen,
            onChanged: (v) async {
              if (v &&
                  !await confirmAction(
                    context,
                    title: "تحذير: فتح الطلبات تلقائياً",
                    message: "التطبيق هيضغط على كارت الطلب في إن درايف عشان يفتحه، يستنى الخريطة، وبعدين يضغط رجوع.\n\n"
                        "• مش بيضغط أبداً على \"قبول\" أو \"اقترح\" أو \"تخطي\".\n"
                        "• طلب واحد كل 8 ثواني على الأكتر، وبيبطّل لو انت فتحت حاجة تانية.\n"
                        "• الشاشة هتتنقل قدامك، وممكن ده يلخبطك وانت بتختار.\n"
                        "• الأتمتة ممكن تخالف شروط إن درايف وتعرّض حسابك للإيقاف. انت المسؤول عن استخدامها.",
                    confirmLabel: "أفهم المخاطر، فعّل",
                    destructive: false,
                  )) {
                return;
              }
              if (!context.mounted) return;
              change((h) => h.copyWith(tripAutoOpen: v, readMapDistance: v ? true : null));
            },
          ),
          if (hud.tripAutoOpen) ...[
            SwitchTile(
              icon: Icons.keyboard_return,
              iconColor: AppColors.warningYellow,
              title: "ارجع أول ما المسافة تتقري",
              subtitle: "لو مقفول: يفضل الطلب مفتوح لحد نهاية مدة الفتح",
              value: hud.autoOpenBackWhenRead,
              onChanged: (v) => change((h) => h.copyWith(autoOpenBackWhenRead: v)),
            ),
            NumberStepperTile(
              icon: Icons.timer_outlined,
              iconColor: AppColors.warningYellow,
              title: "مدة فتح الطلب",
              subtitle: "أقصى وقت قبل الرجوع للقائمة",
              unit: "ثانية",
              value: hud.autoOpenStaySeconds,
              step: 1,
              min: 1,
              max: 30,
              onChanged: (v) => change((h) => h.copyWith(autoOpenStaySeconds: v)),
            ),
            NumberStepperTile(
              icon: Icons.hourglass_bottom,
              iconColor: AppColors.warningYellow,
              title: "الانتظار بين طلب والتاني",
              subtitle: "من لحظة قفل الطلب لحد فتح اللي بعده",
              unit: "ثانية",
              value: hud.autoOpenGapSeconds,
              step: 1,
              min: 1,
              max: 120,
              onChanged: (v) => change((h) => h.copyWith(autoOpenGapSeconds: v)),
            ),
          ],
        ]),

        const SectionLabel("التنبيهات"),
        SettingsGroup(children: [
          NavTile(
            icon: Icons.notifications_active_outlined,
            title: "الاهتزاز والصوت",
            subtitle: AlertsPage.summary(hud),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const AlertsPage())),
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
// ALERTS (vibration & voice)
// =============================================================================

class AlertsPage extends StatelessWidget {
  const AlertsPage({super.key});

  /// One line for the Settings tile, e.g. "اهتزاز: كل الطلبات • صوت: متوقف".
  static String summary(HudSettings h) {
    String part(bool on, String levels) {
      if (!on) return "متوقف";
      final n = levels.split(',').where(HudSettings.levelLabels.containsKey).length;
      if (n == 0) return "متوقف";
      return n == HudSettings.levelLabels.length ? "كل الطلبات" : "$n من ${HudSettings.levelLabels.length}";
    }

    return "اهتزاز: ${part(h.vibrate, h.vibrateLevels)} • صوت: ${part(h.speakVerdict, h.speakLevels)}";
  }

  @override
  Widget build(BuildContext context) {
    final hud = context.watch<AssistantProvider>().hud;
    void change(HudSettings Function(HudSettings h) change) => _updateHud(context, change);

    Future<void> test(String level, {required bool voice}) async {
      final ok = await NativeBridgeService.testAlert(level, voice: voice);
      if (!ok && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("شغّل المساعد العائم الأول عشان تجرب")),
        );
      }
    }

    return _SubPage(
      title: "الاهتزاز والصوت",
      children: [
        const SectionLabel("الاهتزاز"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.vibration,
            title: "اهتزاز عند ظهور طلب جديد",
            subtitle: "هزة قصيرة = رابح • هزتين = هامش ضعيف • هزة طويلة = خسارة",
            value: hud.vibrate,
            onChanged: (v) => change((h) => h.copyWith(vibrate: v)),
          ),
          if (hud.vibrate) ...[
            _LevelPicker(
              title: "يهتز لـ",
              levels: hud.vibrateLevels,
              onChanged: (v) => change((h) => h.copyWith(vibrateLevels: v)),
              onTest: (level) => test(level, voice: false),
            ),
            ChoiceTile<String>(
              icon: Icons.graphic_eq,
              title: "قوة الاهتزاز",
              value: hud.vibrationStrength,
              options: HudSettings.strengthLabels,
              onChanged: (v) => change((h) => h.copyWith(vibrationStrength: v)),
            ),
          ],
        ]),

        const SectionLabel("نطق الحكم بالصوت"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.record_voice_over_outlined,
            title: "نطق الحكم بالصوت",
            subtitle: "يقول مثلاً «رابح، صافي 42» من غير ما تبص على الشاشة. محتاج صوت عربي في إعدادات تحويل النص لكلام",
            value: hud.speakVerdict,
            onChanged: (v) => change((h) => h.copyWith(speakVerdict: v)),
          ),
          if (hud.speakVerdict) ...[
            _LevelPicker(
              title: "ينطق لـ",
              levels: hud.speakLevels,
              onChanged: (v) => change((h) => h.copyWith(speakLevels: v)),
              onTest: (level) => test(level, voice: true),
            ),
            ChoiceTile<String>(
              icon: Icons.short_text,
              title: "يقول إيه",
              value: hud.speakDetail,
              options: HudSettings.speakDetailLabels,
              onChanged: (v) => change((h) => h.copyWith(speakDetail: v)),
            ),
            NumberStepperTile(
              icon: Icons.speed,
              title: "سرعة الكلام",
              unit: "×",
              value: hud.speechRate,
              step: 0.25,
              min: 0.5,
              max: 2.0,
              onChanged: (v) => change((h) => h.copyWith(speechRate: v)),
            ),
            SwitchTile(
              icon: Icons.voice_over_off_outlined,
              title: "اسكت لما الطلب يختفي",
              subtitle: "لو الطلب اتقفل أو اتشال من القائمة وهو بيتكلم، الكلام يقف على طول",
              value: hud.speakStopWhenGone,
              onChanged: (v) => change((h) => h.copyWith(speakStopWhenGone: v)),
            ),
          ],
        ]),

        const SectionLabel("إمتى ينبّه"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.looks_one_outlined,
            title: "مرة واحدة بس لكل طلب",
            subtitle: "لو فتحت الطلب أو رجعت للقائمة ولقيته تاني، مش هينبّه تاني",
            value: hud.alertOncePerRequest,
            onChanged: (v) => change((h) => h.copyWith(alertOncePerRequest: v)),
          ),
          SwitchTile(
            icon: Icons.smart_toy_outlined,
            iconColor: AppColors.warningYellow,
            title: "سكوت أثناء الأتمتة",
            subtitle: "مفيش اهتزاز ولا صوت للطلبات اللي التطبيق بيفتحها بنفسه عشان يقرا المسافة",
            value: hud.muteDuringAutomation,
            onChanged: (v) => change((h) => h.copyWith(muteDuringAutomation: v)),
          ),
        ]),
      ],
    );
  }
}

/// Chips for the verdicts an alert fires for, with a "try" button for each.
class _LevelPicker extends StatelessWidget {
  final String title;
  final String levels;
  final ValueChanged<String> onChanged;
  final ValueChanged<String> onTest;

  const _LevelPicker({required this.title, required this.levels, required this.onChanged, required this.onTest});

  @override
  Widget build(BuildContext context) {
    final selected = levels.split(',').toSet();
    return Padding(
      padding: const EdgeInsets.all(Insets.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: Insets.sm),
          for (final e in HudSettings.levelLabels.entries)
            Row(
              children: [
                Expanded(
                  child: CheckboxListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    controlAffinity: ListTileControlAffinity.leading,
                    title: Text(e.value),
                    value: selected.contains(e.key),
                    onChanged: (on) {
                      final next = HudSettings.levelLabels.keys
                          .where((k) => k == e.key ? on == true : selected.contains(k))
                          .join(',');
                      onChanged(next);
                    },
                  ),
                ),
                IconButton(
                  tooltip: "جرّب",
                  icon: const Icon(Icons.play_circle_outline),
                  onPressed: () => onTest(e.key),
                ),
              ],
            ),
        ],
      ),
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

  /// Turning auto-hide on (from all off) asks first: it swipes cards inside inDrive.
  Future<void> _setDismiss(BuildContext context, {bool? loss, bool? blocked}) async {
    final provider = context.read<AssistantProvider>();
    final s = provider.driverSettings;
    final turningOn = (loss ?? false) || (blocked ?? false);
    if (turningOn && !s.autoDismissLoss && !s.autoDismissBlocked) {
      final ok = await confirmAction(
        context,
        title: "تحذير: الإخفاء التلقائي",
        message: "التطبيق هيسحب كارت الطلب في إن درايف بنفسه عشان يخفيه، أو يضغط \"تخطي\" لو الطلب مفتوح.\n\n"
            "• بيستنى الوقت اللي تختاره، ويعيد قراءة الشاشة قبل السحب، ومش بيخفي طلب اتغيّر.\n"
            "• الخسارة بتتحسب بس لما مسافتي الوصول والمشوار مقروءين.\n"
            "• جرّب اتجاه السحب الأول: لو السحب في إن درايف بيعمل حاجة تانية غير الإخفاء، غيّر الاتجاه أو اقفل الخاصية.\n"
            "• الأتمتة ممكن تخالف شروط إن درايف وتعرّض حسابك للإيقاف. انت المسؤول عن استخدامها.",
        confirmLabel: "أفهم المخاطر، فعّل",
        destructive: false,
      );
      if (!ok) return;
    }
    if (context.mounted) {
      _updateSettings(context, (x) => x.copyWith(autoDismissLoss: loss, autoDismissBlocked: blocked));
    }
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
        const SectionLabel("الإخفاء التلقائي"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.money_off,
            iconColor: AppColors.lossRed,
            title: "إخفاء الطلبات الخاسرة",
            subtitle: "صافي الربح أقل من صفر، وبس لما المسافتين مقروءين",
            value: s.autoDismissLoss,
            onChanged: (v) => _setDismiss(context, loss: v),
          ),
          SwitchTile(
            icon: Icons.block,
            iconColor: AppColors.lossRed,
            title: "إخفاء المناطق والعملاء المحظورين",
            subtitle: "إلا لو السعر عالي بشكل استثنائي في منطقة محظورة",
            value: s.autoDismissBlocked,
            onChanged: (v) => _setDismiss(context, blocked: v),
          ),
          if (s.autoDismissLoss || s.autoDismissBlocked) ...[
            ChoiceTile<String>(
              title: "اتجاه سحب الكارت",
              icon: Icons.swipe,
              iconColor: AppColors.lossRed,
              value: s.autoDismissDirection,
              options: const {"RIGHT": "لليمين", "LEFT": "للشمال"},
              onChanged: (v) => change((x) => x.copyWith(autoDismissDirection: v)),
            ),
            NumberStepperTile(
              icon: Icons.timer_outlined,
              iconColor: AppColors.lossRed,
              title: "الانتظار قبل الإخفاء",
              subtitle: "وقت تشوف فيه الطلب قبل ما يختفي",
              unit: "ثانية",
              value: s.autoDismissDelaySeconds,
              step: 0.5,
              min: 0.5,
              max: 10,
              onChanged: (v) => change((x) => x.copyWith(autoDismissDelaySeconds: v)),
            ),
          ],
        ]),
        const SectionLabel("أولوية القبول"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.leaderboard_outlined,
            iconColor: AppColors.warningYellow,
            title: "اقبل الأعلى ربحاً أولاً",
            subtitle: "لو أكتر من طلب مطابق ظاهر في نفس الوقت، يقبل الأفضل بدل أول واحد في القائمة",
            value: s.autoAcceptPickBest,
            onChanged: (v) => change((x) => x.copyWith(autoAcceptPickBest: v)),
          ),
          if (s.autoAcceptPickBest)
            ChoiceTile<String>(
              title: "الأفضل حسب",
              icon: Icons.sort,
              iconColor: AppColors.warningYellow,
              value: s.autoAcceptRankBy,
              options: const {
                "PROFIT_PER_KM": "الربح لكل كم",
                "NET_PROFIT": "صافي الربح",
                "MARGIN": "نسبة الهامش %",
              },
              onChanged: (v) => change((x) => x.copyWith(autoAcceptRankBy: v)),
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
    List<String> suggestions = const [],
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
          if (suggestions.isNotEmpty) ...[
            const SizedBox(height: Insets.md),
            const Text("اقتراحات (اضغط للإضافة)", style: TextStyle(color: AppColors.textSecondary, fontSize: 12)),
            const SizedBox(height: Insets.sm),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: suggestions
                  .map((z) => ActionChip(
                        avatar: Icon(Icons.add, size: 16, color: color),
                        label: Text(z),
                        onPressed: () => onAdd(z),
                      ))
                  .toList(),
            ),
          ],
        ],
      ),
    );
  }

  /// Common areas offered as one-tap favourites. Nothing is preselected (see DriverSettings).
  static const List<String> _suggestedZones = [
    "المعادي", "التجمع", "مصر الجديدة", "مدينة نصر", "الدقي", "المهندسين", "الشيخ زايد", "6 أكتوبر",
  ];

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
          suggestions: _suggestedZones
              .where((z) => !s.favoriteZones.contains(z) && !s.blacklistedZones.contains(z))
              .toList(),
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
// PASSENGERS (favourite / blocked)
// =============================================================================
class PassengersPage extends StatelessWidget {
  const PassengersPage({super.key});

  Future<void> _add(BuildContext context, {required bool favorite}) async {
    final ctrl = TextEditingController();
    String? error;
    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) => AlertDialog(
          title: Text(favorite ? "إضافة عميل مميز" : "إضافة عميل محظور"),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            decoration: InputDecoration(hintText: "الاسم زي ما بيظهر في إن درايف", errorText: error),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("إلغاء")),
            FilledButton(
              onPressed: () async {
                final name = ctrl.text.trim();
                if (name.isEmpty) {
                  setDlg(() => error = "اكتب اسم العميل");
                  return;
                }
                await context.read<AssistantProvider>().setPassengerList(name, favorite);
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
    required List<String> names,
    required bool favorite,
  }) {
    final provider = context.read<AssistantProvider>();
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
                onPressed: () => _add(context, favorite: favorite),
              ),
            ],
          ),
          const SizedBox(height: Insets.md),
          if (names.isEmpty)
            const Text("لا يوجد عملاء بعد", style: TextStyle(color: AppColors.textTertiary, fontSize: 12.5))
          else
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: names
                  .map((n) => InputChip(
                        label: Text(n),
                        backgroundColor: color.withValues(alpha: 0.12),
                        side: BorderSide(color: color.withValues(alpha: 0.35)),
                        deleteIconColor: color,
                        onDeleted: () async {
                          await provider.setPassengerList(n, null);
                          if (context.mounted) {
                            showUndoSnackBar(context, "تمت إزالة '$n'", () => provider.setPassengerList(n, favorite));
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
    final s = context.watch<AssistantProvider>().driverSettings;
    return _SubPage(
      title: "العملاء",
      children: [
        const SizedBox(height: Insets.sm),
        _section(
          context,
          title: "عملاء مميزين",
          hint: "بيظهروا بنجمة ★ على العلامة",
          icon: Icons.star_rounded,
          color: AppColors.accentAmber,
          names: s.favoritePassengers,
          favorite: true,
        ),
        const SizedBox(height: Insets.md),
        _section(
          context,
          title: "عملاء محظورين",
          hint: "طلباتهم بتظهر حمرا، والقبول التلقائي مش هيقبلها أبداً",
          icon: Icons.block,
          color: AppColors.lossRed,
          names: s.blacklistedPassengers,
          favorite: false,
        ),
        const SizedBox(height: Insets.md),
        const Text(
          "التطابق بيكون على الاسم اللي بيظهر في الكارت بالظبط (مع تجاهل الهمزات). "
          "إن درايف بيعرض الاسم الأول بس، فحظر اسم شائع زي \"أحمد\" هيحظر كل اللي اسمهم كده. "
          "تقدر كمان تضيف العميل من البطاقة العائمة فوق إن درايف.",
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
        ]),
        const SectionLabel("تسعير مسافة الوصول"),
        SettingsGroup(children: [
          SwitchTile(
            icon: Icons.near_me_outlined,
            iconColor: AppColors.accentCyan,
            title: "تسعير منفصل لمسافة الوصول",
            subtitle: "كيلو الوصول للراكب بيتحسب أرخص من كيلو المشوار: وقود + استهلاك + جزء من العمولة + هامش ربح خاص بيه",
            value: s.pickupPricingEnabled,
            onChanged: (v) => change((x) => x.copyWith(pickupPricingEnabled: v)),
          ),
          if (s.pickupPricingEnabled) ...[
            NumberStepperTile(
              icon: Icons.trending_up,
              iconColor: AppColors.accentCyan,
              title: "هامش الربح لكيلو الوصول",
              subtitle: "كيلو المشوار: ${formatNumber(s.minProfitPerKm)} $c",
              unit: "$c/كم",
              value: s.pickupProfitPerKm,
              step: 0.5,
              min: 0,
              max: 100,
              onChanged: (v) => change((x) => x.copyWith(pickupProfitPerKm: v)),
            ),
            NumberStepperTile(
              icon: Icons.percent,
              iconColor: AppColors.accentCyan,
              title: "نسبة العمولة على كيلو الوصول",
              subtitle: "من عمولة إن درايف (${formatNumber(s.inDriveCommissionPercent)}%)",
              unit: "%",
              value: s.pickupCommissionSharePercent,
              step: 10,
              min: 0,
              max: 100,
              onChanged: (v) => change((x) => x.copyWith(pickupCommissionSharePercent: v)),
            ),
            SwitchTile(
              icon: Icons.compare_arrows,
              iconColor: AppColors.accentCyan,
              title: "بس لما الوصول أطول من المشوار",
              value: s.pickupRuleLongerThanTrip,
              onChanged: (v) => change((x) => x.copyWith(pickupRuleLongerThanTrip: v)),
            ),
            SwitchTile(
              icon: Icons.straighten,
              iconColor: AppColors.accentCyan,
              title: "بس لما الوصول أطول من عدد كيلومترات ثابت",
              value: s.pickupRuleOverKm,
              onChanged: (v) => change((x) => x.copyWith(pickupRuleOverKm: v)),
            ),
            if (s.pickupRuleOverKm)
              NumberStepperTile(
                icon: Icons.flag_outlined,
                iconColor: AppColors.accentCyan,
                title: "الحد الثابت للوصول",
                unit: "كم",
                value: s.pickupRuleThresholdKm,
                step: 0.5,
                min: 0.5,
                max: 30,
                onChanged: (v) => change((x) => x.copyWith(pickupRuleThresholdKm: v)),
              ),
            if (s.pickupRuleLongerThanTrip && s.pickupRuleOverKm)
              ChoiceTile<bool>(
                title: "لما الشرطين مفعّلين",
                icon: Icons.rule,
                iconColor: AppColors.accentCyan,
                value: s.pickupRuleMatchAll,
                options: const {false: "يكفي أي شرط", true: "لازم الاتنين"},
                onChanged: (v) => change((x) => x.copyWith(pickupRuleMatchAll: v)),
              ),
            if (s.pickupRuleLongerThanTrip || s.pickupRuleOverKm)
              ChoiceTile<String>(
                title: "الوصول القصير يتحسب",
                subtitle: "لما الشرط مش متحقق",
                icon: Icons.near_me_disabled_outlined,
                iconColor: AppColors.accentCyan,
                value: s.pickupShortMode,
                options: const {
                  "COST_ONLY": "تكلفة بس (من غير ربح)",
                  "FREE": "مجاناً (عليك)",
                  "AS_TRIP": "زي كيلو المشوار",
                },
                onChanged: (v) => change((x) => x.copyWith(pickupShortMode: v)),
              ),
            _PickupPricingExample(settings: s),
          ],
        ]),
        const SectionLabel("المسافات والهدف اليومي"),
        SettingsGroup(children: [
          NumberStepperTile(
            icon: Icons.route_outlined,
            iconColor: AppColors.accentCyan,
            title: "مسافة المشوار المفترضة",
            subtitle: "يُستخدم في كروت القائمة قبل فتح الطلب (≈). عند فتح الطلب يُقرأ الطول الحقيقي من الخريطة",
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

/// Worked example of the fair price with and without separate pickup pricing.
class _PickupPricingExample extends StatelessWidget {
  final DriverSettings settings;

  const _PickupPricingExample({required this.settings});

  @override
  Widget build(BuildContext context) {
    final c = settings.currency;
    String line(double pickupKm, double tripKm) {
      final withPickup = FareCalculatorEngine.roundToSmartStep(
          FareCalculatorEngine.fairPrice(pickupDistanceKm: pickupKm, tripDistanceKm: tripKm, settings: settings));
      final flat = FareCalculatorEngine.roundToSmartStep(FareCalculatorEngine.fairPrice(
          pickupDistanceKm: pickupKm, tripDistanceKm: tripKm, settings: settings.copyWith(pickupPricingEnabled: false)));
      final applies = FareCalculatorEngine.pickupRuleApplies(pickupKm, tripKm, settings);
      return "• ${formatNumber(pickupKm)} كم وصول + ${formatNumber(tripKm)} كم مشوار ← ${withPickup.toInt()} $c "
          "(${applies ? "تسعير الوصول" : "وصول قصير"}؛ بدل ${flat.toInt()} $c لو اتحسب زي المشوار)";
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(Insets.lg, 4, Insets.lg, Insets.md),
      child: Text(
        "أمثلة على السعر العادل:\n${line(1.0, 10.0)}\n${line(6.0, 4.0)}",
        style: const TextStyle(color: AppColors.textSecondary, fontSize: 12, height: 1.6),
      ),
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

class _UpdatesPageState extends State<UpdatesPage> with WidgetsBindingObserver {
  bool _checking = false;
  bool _downloading = false;
  double _progress = 0;
  UpdateInfo? _info;
  String _installed = "";

  /// A verified download waiting for "install unknown apps" permission. Kept so the driver
  /// doesn't download again: installing resumes when they come back from Android settings.
  String? _pendingApkPath;
  String? _pendingSha;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    UpdateManager.currentVersion().then((v) {
      if (mounted) setState(() => _installed = v);
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _pendingApkPath != null) _installDownloaded();
  }

  /// Hands the already-downloaded APK to the installer again (after the permission was granted).
  Future<void> _installDownloaded() async {
    final path = _pendingApkPath;
    if (path == null) return;
    if (!await NativeBridgeService.canInstallPackages()) {
      // Still not allowed (came back without enabling it): keep the file; don't reopen Settings.
      _snack("لسه إذن التثبيت مش مفعّل. اضغط \"تثبيت\" لما تفعّله");
      return;
    }
    final result = await NativeBridgeService.installApk(path, expectedSha256: _pendingSha);
    if (!mounted) return;
    setState(() {
      _pendingApkPath = null;
      _pendingSha = null;
    });
    _reportInstall(result);
  }

  void _reportInstall(InstallApkResult result) {
    switch (result) {
      case InstallApkResult.started:
      case InstallApkResult.needsPermission:
        break;
      case InstallApkResult.checksumMismatch:
        _snack("ملف التحديث غير موثوق (البصمة غير مطابقة) وتم حذفه");
      case InstallApkResult.fileNotFound:
      case InstallApkResult.error:
        _snack("تعذر بدء التثبيت. جرّب فتح الرابط من المتصفح");
    }
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
    if (_pendingApkPath != null) {
      // Already downloaded and verified: only the installer step is left.
      final granted = await NativeBridgeService.canInstallPackages();
      if (!granted) {
        await NativeBridgeService.installApk(_pendingApkPath!, expectedSha256: _pendingSha); // opens the permission screen
        return;
      }
      await _installDownloaded();
      return;
    }
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

    final result = await NativeBridgeService.installApk(file.path, expectedSha256: sha);
    if (!mounted) return;
    if (result == InstallApkResult.needsPermission) {
      // Android opened its "install unknown apps" screen; finish when the driver comes back.
      setState(() {
        _pendingApkPath = file.path;
        _pendingSha = sha;
      });
      _snack("اسمح بتثبيت التطبيقات من هذا المصدر، ثم ارجع للتطبيق: التثبيت هيكمل لوحده");
      return;
    }
    _reportInstall(result);
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
                          label: Text(_pendingApkPath != null ? "تثبيت" : "تحميل وتثبيت"),
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
