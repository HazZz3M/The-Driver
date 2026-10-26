import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../providers/assistant_provider.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';
import '../components/dialogs.dart';
import '../components/ui_kit.dart';
import 'settings_pages.dart';

/// Every automated action in one place: a master switch (also in the notification), what is
/// switched on, and what the automation actually did today, with the reason.
class AutomationScreen extends StatefulWidget {
  const AutomationScreen({super.key});

  @override
  State<AutomationScreen> createState() => _AutomationScreenState();
}

class _AutomationScreenState extends State<AutomationScreen> with WidgetsBindingObserver {
  bool _paused = false;
  List<Map<String, dynamic>> _log = const [];

  static const _actionLabels = {"ACCEPT": "قبول", "HIDE": "إخفاء", "OPEN": "فتح", "BID": "مزايدة"};
  static const _actionIcons = {
    "ACCEPT": Icons.check_circle_outline,
    "HIDE": Icons.visibility_off_outlined,
    "OPEN": Icons.open_in_new,
    "BID": Icons.radio_button_checked,
  };

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // The notification can pause automation while the app is in the background.
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    final paused = await NativeBridgeService.getAutomationPaused();
    final log = await NativeBridgeService.getAutomationLog();
    if (mounted) setState(() {
      _paused = paused;
      _log = log.reversed.toList();
    });
  }

  Future<void> _setPaused(bool paused) async {
    await NativeBridgeService.setAutomationPaused(paused);
    await _load();
  }

  Future<void> _clear() async {
    final ok = await confirmAction(context, title: "مسح سجل الأتمتة؟", message: "هيتمسح سجل اللي عملته الأتمتة. إعداداتك مش هتتغير.", confirmLabel: "مسح");
    if (!ok) return;
    await NativeBridgeService.clearAutomationLog();
    await _load();
  }

  void _open(Widget page) => Navigator.push(context, MaterialPageRoute(builder: (_) => page)).then((_) => _load());

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final s = provider.driverSettings;
    final hud = provider.hud;
    final c = s.currency;
    final today = DateTime.now();
    final todays = _log.where((e) {
      final t = DateTime.fromMillisecondsSinceEpoch((e['t'] as num?)?.toInt() ?? 0);
      return DateUtils.isSameDay(t, today);
    }).toList();
    int count(String a) => todays.where((e) => e['action'] == a).length;

    final hideLabel = s.autoDismissLoss && s.autoDismissBlocked
        ? "الخاسرة والمحظورة"
        : s.autoDismissLoss
            ? "الخاسرة"
            : s.autoDismissBlocked
                ? "المحظورة"
                : "متوقف";

    return Scaffold(
      appBar: AppBar(title: const Text("الأتمتة")),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _load,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(Insets.lg, Insets.sm, Insets.lg, Insets.xl),
            children: [
              AppCard(
                borderColor: _paused ? AppColors.lossRed.withValues(alpha: 0.5) : AppColors.emeraldPrimary.withValues(alpha: 0.5),
                padding: EdgeInsets.zero,
                child: SwitchTile(
                  icon: _paused ? Icons.pause_circle_outline : Icons.play_circle_outline,
                  iconColor: _paused ? AppColors.lossRed : AppColors.emeraldPrimary,
                  title: _paused ? "الأتمتة متوقفة" : "الأتمتة شغالة",
                  subtitle: "مفتاح واحد يوقف كل ضغط وسحب تلقائي في إن درايف. موجود كمان في إشعار المساعد",
                  value: !_paused,
                  onChanged: (on) => _setPaused(!on),
                ),
              ),

              const SectionLabel("اللي مفعّل"),
              SettingsGroup(children: [
                NavTile(
                  icon: Icons.bolt_outlined,
                  color: AppColors.warningYellow,
                  title: "القبول التلقائي",
                  trailingText: s.autoAcceptEnabled ? "مفعّل" : "متوقف",
                  onTap: () => _open(const AutoAcceptPage()),
                ),
                NavTile(
                  icon: Icons.visibility_off_outlined,
                  color: AppColors.lossRed,
                  title: "الإخفاء التلقائي",
                  trailingText: hideLabel,
                  onTap: () => _open(const AutoAcceptPage()),
                ),
                NavTile(
                  icon: Icons.touch_app_outlined,
                  color: AppColors.accentCyan,
                  title: "فتح الطلبات لقراءة المسافة",
                  trailingText: hud.tripAutoOpen ? "مفعّل" : "متوقف",
                  onTap: () => _open(const HudSettingsPage()),
                ),
                NavTile(
                  icon: Icons.radio_button_checked,
                  color: AppColors.accentPurple,
                  title: "الضغط على أفضل مزايدة",
                  trailingText: hud.bidChipsMode == "AUTO_TAP" ? "مفعّل" : "متوقف",
                  onTap: () => _open(const HudSettingsPage()),
                ),
              ]),

              const SectionLabel("اللي عملته الأتمتة النهارده"),
              Row(
                children: [
                  for (final a in const ["ACCEPT", "HIDE", "OPEN", "BID"])
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsetsDirectional.only(end: 6),
                        child: AppCard(
                          padding: const EdgeInsets.symmetric(vertical: Insets.md),
                          child: Column(
                            children: [
                              Text("${count(a)}", style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
                              Text(_actionLabels[a]!, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)),
                            ],
                          ),
                        ),
                      ),
                    ),
                ],
              ),
              SectionLabel(
                "السجل",
                action: _log.isEmpty ? null : TextButton(onPressed: _clear, child: const Text("مسح")),
              ),
              if (_log.isEmpty)
                const EmptyState(
                  icon: Icons.history,
                  title: "الأتمتة معملتش حاجة لسه",
                  message: "كل قبول أو إخفاء أو فتح تلقائي بيظهر هنا بالسبب.",
                )
              else
                AppCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: [
                      for (final e in _log.take(100))
                        ListTile(
                          leading: Icon(_actionIcons[e['action']] ?? Icons.bolt, color: AppColors.textSecondary),
                          title: Text(
                            "${_actionLabels[e['action']] ?? e['action']} • ${((e['fare'] as num?) ?? 0).toInt()} $c",
                            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
                          ),
                          subtitle: Text(
                            "${DateFormat('d MMM • h:mm a', 'ar').format(DateTime.fromMillisecondsSinceEpoch((e['t'] as num?)?.toInt() ?? 0))}\n${e['detail'] ?? ""}",
                            style: const TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.4),
                          ),
                          isThreeLine: true,
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
