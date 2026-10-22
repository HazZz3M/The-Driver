import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../engine/request_insights.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../components/dialogs.dart';
import '../components/ui_kit.dart';
import 'request_log_screen.dart';

/// Analysis of every request seen in inDrive (accepted or not) and recommendations:
/// peak hours, best hours to work, busiest and most profitable areas, frequent routes.
class InsightsScreen extends StatefulWidget {
  const InsightsScreen({super.key});

  @override
  State<InsightsScreen> createState() => _InsightsScreenState();
}

class _InsightsScreenState extends State<InsightsScreen> with WidgetsBindingObserver {
  int _periodDays = 30; // 0 = all

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    context.read<AssistantProvider>().loadRequestLog();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) context.read<AssistantProvider>().loadRequestLog();
  }

  Future<void> _share(bool csv) async {
    final messenger = ScaffoldMessenger.of(context);
    final ok = await context.read<AssistantProvider>().shareRequestLog(csv: csv);
    if (!ok) messenger.showSnackBar(const SnackBar(content: Text("تعذر تجهيز الملف للمشاركة")));
  }

  Future<void> _clear() async {
    final provider = context.read<AssistantProvider>();
    final ok = await confirmAction(
      context,
      title: "مسح سجل الطلبات؟",
      message: "هيتمسح كل الطلبات المسجلة وتقييماتها، والتحليلات هتبدأ من الصفر. "
          "لو عايز تحتفظ بنسخة، شاركها كملف الأول.",
      confirmLabel: "مسح السجل",
    );
    if (ok) await provider.clearRequestLog();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final s = provider.driverSettings;
    final c = s.currency;
    final since = _periodDays == 0 ? null : DateTime.now().subtract(Duration(days: _periodDays));
    final ins = RequestInsights.from(provider.requestLog, zones: [...s.favoriteZones, ...s.blacklistedZones], since: since);
    final recs = ins.recommendations(now: DateTime.now(), currency: c);

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: provider.loadRequestLog,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
            children: [
              const PageHeader(title: "التحليلات والتوصيات", subtitle: "من كل الطلبات اللي ظهرتلك في إن درايف"),
              SegmentedButton<int>(
                showSelectedIcon: false,
                segments: const [
                  ButtonSegment(value: 7, label: Text("7 أيام")),
                  ButtonSegment(value: 30, label: Text("30 يوم")),
                  ButtonSegment(value: 0, label: Text("الكل")),
                ],
                selected: {_periodDays},
                onSelectionChanged: (v) => setState(() => _periodDays = v.first),
              ),

              // Summary
              const SectionLabel("ملخص"),
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 10,
                crossAxisSpacing: 10,
                childAspectRatio: 1.55,
                children: [
                  StatTile(
                    label: "طلبات مسجلة",
                    value: "${ins.total.count}",
                    caption: "${(ins.total.count / ins.days).toStringAsFixed(0)} في اليوم",
                    icon: Icons.inbox_outlined,
                  ),
                  StatTile(
                    label: "اتقبلت",
                    value: "${ins.total.accepted}",
                    caption: ins.opened == 0 ? "من ${ins.opened} اتفتحت" : "${(ins.acceptanceRate * 100).round()}% من اللي اتفتحت",
                    icon: Icons.check_circle_outline,
                    color: AppColors.accentCyan,
                  ),
                  StatTile(
                    label: "متوسط السعر",
                    value: "${ins.total.avgFare.toStringAsFixed(0)} $c",
                    caption: ins.total.avgTripKm == null ? null : "متوسط المشوار ${ins.total.avgTripKm!.toStringAsFixed(1)} كم",
                    icon: Icons.payments_outlined,
                    color: AppColors.accentAmber,
                  ),
                  StatTile(
                    label: "متوسط الربح لكل كم",
                    value: ins.total.avgProfitPerKm == null ? "—" : "${ins.total.avgProfitPerKm!.toStringAsFixed(1)} $c",
                    caption: ins.total.avgRating == null ? "مفيش تقييمات لسه" : "تقييمك ${ins.total.avgRating!.toStringAsFixed(1)} ★",
                    icon: Icons.speed,
                    color: AppColors.accentPurple,
                  ),
                ],
              ),

              // Recommendations
              const SectionLabel("التوصيات"),
              for (final r in recs) _RecommendationCard(rec: r),

              // Hours
              const SectionLabel("الطلبات حسب الساعة"),
              AppCard(child: _HourChart(ins: ins)),

              // Weekdays
              const SectionLabel("الطلبات حسب اليوم"),
              AppCard(
                child: Column(
                  children: [
                    for (var d = 0; d < 7; d++)
                      _BarRow(
                        label: RequestInsights.weekdayNames[d],
                        value: ins.byWeekday[d].count,
                        max: ins.byWeekday.map((b) => b.count).fold(0, (a, b) => a > b ? a : b),
                        trailing: "${ins.byWeekday[d].count}",
                      ),
                  ],
                ),
              ),

              // Areas & routes
              const SectionLabel("أكتر مناطق بيبدأ منها الطلب"),
              _AreaList(items: ins.top(ins.pickupAreas), currency: c),
              const SectionLabel("أكتر مناطق الوصول"),
              _AreaList(items: ins.top(ins.dropoffAreas), currency: c),
              const SectionLabel("أكتر مشاوير بتتكرر"),
              _AreaList(items: ins.top(ins.routes), currency: c, showKm: true),

              // Log & export
              const SectionLabel("السجل"),
              SettingsGroup(children: [
                NavTile(
                  icon: Icons.list_alt,
                  title: "سجل الطلبات",
                  subtitle: "كل طلب بتفاصيله ولقطته النصية، وتقييم الرحلات",
                  trailingText: "${provider.requestLog.length}",
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RequestLogScreen())),
                ),
                NavTile(
                  icon: Icons.table_chart_outlined,
                  color: AppColors.accentCyan,
                  title: "حفظ ومشاركة السجل (Excel)",
                  subtitle: "ملف CSV بكل الطلبات، يفتح على Excel أو Google Sheets",
                  onTap: () => _share(true),
                ),
                NavTile(
                  icon: Icons.data_object,
                  color: AppColors.accentCyan,
                  title: "حفظ ومشاركة السجل (JSON)",
                  subtitle: "نسخة كاملة بكل التفاصيل واللقطات النصية",
                  onTap: () => _share(false),
                ),
                NavTile(
                  icon: Icons.delete_outline,
                  color: AppColors.lossRed,
                  title: "مسح السجل",
                  onTap: _clear,
                ),
              ]),
              const SizedBox(height: Insets.sm),
              const Text(
                "السجل محفوظ على موبايلك بس. فيه أسماء الركاب والعناوين، فخلي بالك قبل ما تشاركه.",
                style: TextStyle(color: AppColors.textTertiary, fontSize: 12, height: 1.5),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RecommendationCard extends StatelessWidget {
  final Recommendation rec;
  const _RecommendationCard({required this.rec});

  @override
  Widget build(BuildContext context) {
    final (icon, color) = switch (rec.kind) {
      "PLACE" => (Icons.place_outlined, AppColors.accentAmber),
      "ROUTE" => (Icons.route_outlined, AppColors.accentCyan),
      "PRICE" => (Icons.sell_outlined, AppColors.warningYellow),
      "QUALITY" => (Icons.star_outline, AppColors.accentPurple),
      _ => (Icons.schedule, AppColors.emeraldPrimary),
    };
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: AppCard(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TileIcon(icon, color: color),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(rec.title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14.5)),
                  const SizedBox(height: 4),
                  Text(rec.detail, style: const TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.5)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 24 bars (requests per hour); the three peak hours are highlighted.
class _HourChart extends StatelessWidget {
  final RequestInsights ins;
  const _HourChart({required this.ins});

  @override
  Widget build(BuildContext context) {
    final maxCount = ins.byHour.map((b) => b.count).fold(0, (a, b) => a > b ? a : b);
    final peaks = ins.peakHours.toSet();
    if (maxCount == 0) {
      return const Text("لسه مفيش طلبات مسجلة في الفترة دي.", style: TextStyle(color: AppColors.textSecondary));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          height: 110,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              for (var h = 0; h < 24; h++)
                Expanded(
                  child: Tooltip(
                    message: "${RequestInsights.hourRange(h)}: ${ins.byHour[h].count} طلب",
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 1),
                      child: Container(
                        height: 4 + 96 * ins.byHour[h].count / maxCount,
                        decoration: BoxDecoration(
                          color: peaks.contains(h) ? AppColors.emeraldPrimary : AppColors.emeraldPrimary.withValues(alpha: 0.3),
                          borderRadius: BorderRadius.circular(3),
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 6),
        const Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text("12 ص", style: TextStyle(color: AppColors.textTertiary, fontSize: 12)),
            Text("6 ص", style: TextStyle(color: AppColors.textTertiary, fontSize: 12)),
            Text("12 م", style: TextStyle(color: AppColors.textTertiary, fontSize: 12)),
            Text("6 م", style: TextStyle(color: AppColors.textTertiary, fontSize: 12)),
            Text("11 م", style: TextStyle(color: AppColors.textTertiary, fontSize: 12)),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          "الأعمدة الغامقة = ساعات الذروة: ${ins.peakHours.map(RequestInsights.hourRange).join("، ")}",
          style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5),
        ),
      ],
    );
  }
}

class _BarRow extends StatelessWidget {
  final String label;
  final int value;
  final int max;
  final String trailing;
  const _BarRow({required this.label, required this.value, required this.max, required this.trailing});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(width: 56, child: Text(label, style: const TextStyle(fontSize: 13))),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(value: max == 0 ? 0 : value / max, minHeight: 10),
            ),
          ),
          SizedBox(
            width: 40,
            child: Text(trailing, textAlign: TextAlign.end, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5)),
          ),
        ],
      ),
    );
  }
}

class _AreaList extends StatelessWidget {
  final List<Named<Bucket>> items;
  final String currency;
  final bool showKm;
  const _AreaList({required this.items, required this.currency, this.showKm = false});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const EmptyState(icon: Icons.place_outlined, title: "مفيش بيانات كفاية لسه");
    }
    return AppCard(
      padding: const EdgeInsets.symmetric(horizontal: Insets.lg, vertical: Insets.sm),
      child: Column(
        children: [
          for (final a in items)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                children: [
                  Expanded(
                    child: Text(a.name, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600)),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    "${a.value.count} طلب"
                    "${a.value.avgProfitPerKm != null ? " • ${a.value.avgProfitPerKm!.toStringAsFixed(1)} $currency/كم" : ""}"
                    "${showKm && a.value.avgTripKm != null ? " • ${a.value.avgTripKm!.toStringAsFixed(1)} كم" : ""}",
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
