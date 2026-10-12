import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../data/models/trip.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import '../components/ui_kit.dart';

class ShiftDashboardScreen extends StatefulWidget {
  const ShiftDashboardScreen({super.key});

  @override
  State<ShiftDashboardScreen> createState() => _ShiftDashboardScreenState();
}

class _ShiftDashboardScreenState extends State<ShiftDashboardScreen> {
  Timer? _ticker;

  @override
  void initState() {
    super.initState();
    // Refreshes the live shift duration once a minute.
    _ticker = Timer.periodic(const Duration(minutes: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }

  String _formatDuration(Duration d) {
    final h = d.inHours;
    final m = d.inMinutes.remainder(60);
    return h > 0 ? "$hس $mد" : "$mد";
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final settings = provider.driverSettings;
    final c = settings.currency;
    final trips = provider.todayTrips;
    final progress = provider.shiftProgress;
    final remaining = (settings.targetDailyEarnings - provider.todayTotalGross).clamp(0.0, double.infinity);
    final perHour = provider.shiftProfitPerHour;
    final week = provider.lastDaysSummary();

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
          children: [
            PageHeader(
              title: "الوردية",
              subtitle: DateFormat('EEEE d MMMM', 'ar').format(DateTime.now()),
              trailing: provider.isShiftActive
                  ? OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(foregroundColor: AppColors.lossRed, minimumSize: const Size(0, 40)),
                      icon: const Icon(Icons.stop_rounded, size: 20),
                      label: const Text("إنهاء"),
                      onPressed: () {
                        HapticFeedback.mediumImpact();
                        provider.toggleShift();
                      },
                    )
                  : FilledButton.icon(
                      style: FilledButton.styleFrom(minimumSize: const Size(0, 40)),
                      icon: const Icon(Icons.play_arrow_rounded, size: 20),
                      label: const Text("بدء الوردية"),
                      onPressed: () {
                        HapticFeedback.mediumImpact();
                        provider.toggleShift();
                      },
                    ),
            ),

            // Target ring
            AppCard(
              child: Row(
                children: [
                  SizedBox(
                    width: 104,
                    height: 104,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        CircularProgressIndicator(
                          value: progress,
                          strokeWidth: 10,
                          strokeCap: StrokeCap.round,
                          backgroundColor: AppColors.surfaceHigh,
                        ),
                        Center(
                          child: Text("${(progress * 100).toInt()}%",
                              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: Insets.lg),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text("إيراد اليوم", style: TextStyle(color: AppColors.textSecondary, fontSize: 12.5)),
                        const SizedBox(height: 2),
                        FittedBox(
                          fit: BoxFit.scaleDown,
                          alignment: AlignmentDirectional.centerStart,
                          child: Text(
                            "${provider.todayTotalGross.toInt()} / ${settings.targetDailyEarnings.toInt()} $c",
                            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          remaining > 0 ? "باقي ${remaining.toInt()} $c على الهدف" : "تم تحقيق الهدف اليومي 🎉",
                          style: TextStyle(
                            color: remaining > 0 ? AppColors.textSecondary : AppColors.emeraldLight,
                            fontSize: 12.5,
                            fontWeight: remaining > 0 ? FontWeight.w400 : FontWeight.w700,
                          ),
                        ),
                        if (provider.isShiftActive) ...[
                          const SizedBox(height: 8),
                          StatusPill(
                            label: "الوردية منذ ${_formatDuration(provider.shiftDuration)}",
                            color: AppColors.emeraldPrimary,
                            icon: Icons.timer_outlined,
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // Stats grid
            const SectionLabel("أرقام اليوم"),
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              mainAxisSpacing: 10,
              crossAxisSpacing: 10,
              childAspectRatio: 1.55,
              children: [
                StatTile(
                  label: "صافي الربح",
                  value: "${provider.todayTotalNetProfit.toInt()} $c",
                  icon: Icons.account_balance_wallet_outlined,
                  color: AppColors.emeraldPrimary,
                ),
                StatTile(
                  label: "الرحلات",
                  value: "${provider.todayTripsCount}",
                  caption: trips.isEmpty ? null : "متوسط ${provider.todayAverageProfitPerTrip.toInt()} $c للرحلة",
                  icon: Icons.local_taxi_outlined,
                  color: AppColors.accentCyan,
                ),
                StatTile(
                  label: "الربح لكل كم",
                  value: "${provider.todayAverageProfitPerKm.toStringAsFixed(1)} $c",
                  caption: "هدفك ${formatNumber(settings.minProfitPerKm)} $c",
                  icon: Icons.speed,
                  color: provider.todayAverageProfitPerKm >= settings.minProfitPerKm || trips.isEmpty
                      ? AppColors.accentPurple
                      : AppColors.warningYellow,
                ),
                StatTile(
                  label: "الربح في الساعة",
                  value: perHour == null ? "—" : "${perHour.toInt()} $c",
                  caption: perHour == null ? "ابدأ الوردية لحسابه" : "خلال الوردية الحالية",
                  icon: Icons.schedule,
                  color: AppColors.accentAmber,
                ),
              ],
            ),

            // Weekly chart
            const SectionLabel("آخر 7 أيام (صافي الربح)"),
            AppCard(child: _WeekChart(days: week, currency: c)),

            // Trip log
            SectionLabel("رحلات اليوم", action: Text("${trips.length}", style: const TextStyle(color: AppColors.textSecondary))),
            if (trips.isEmpty)
              const EmptyState(
                icon: Icons.history,
                title: "لا توجد رحلات اليوم بعد",
                message: "احسب العرض في الحاسبة واضغط \"حفظ الرحلة\"",
              )
            else
              AppCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    for (var i = 0; i < trips.length; i++) ...[
                      _TripRow(trip: trips[i], currency: c),
                      if (i < trips.length - 1) const Divider(indent: 16, endIndent: 16, height: 1),
                    ],
                  ],
                ),
              ),
            if (trips.isNotEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text("اسحب الرحلة لحذفها", textAlign: TextAlign.center, style: TextStyle(color: AppColors.textTertiary, fontSize: 11.5)),
              ),
          ],
        ),
      ),
    );
  }
}

class _TripRow extends StatelessWidget {
  final Trip trip;
  final String currency;

  const _TripRow({required this.trip, required this.currency});

  @override
  Widget build(BuildContext context) {
    final provider = context.read<AssistantProvider>();
    final time = DateFormat('hh:mm a', 'ar').format(DateTime.fromMillisecondsSinceEpoch(trip.timestamp));

    return Dismissible(
      key: ValueKey(trip.id),
      direction: DismissDirection.horizontal,
      background: Container(
        color: AppColors.lossRed.withValues(alpha: 0.18),
        alignment: AlignmentDirectional.centerStart,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Icon(Icons.delete_outline, color: AppColors.lossRed),
      ),
      secondaryBackground: Container(
        color: AppColors.lossRed.withValues(alpha: 0.18),
        alignment: AlignmentDirectional.centerEnd,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Icon(Icons.delete_outline, color: AppColors.lossRed),
      ),
      onDismissed: (_) async {
        await provider.deleteTrip(trip.id);
        if (context.mounted) {
          showUndoSnackBar(context, "تم حذف رحلة ${trip.offeredFare.toInt()} $currency", () => provider.restoreTrip(trip));
        }
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: Insets.lg, vertical: 12),
        child: Row(
          children: [
            Container(
              width: 56,
              padding: const EdgeInsets.symmetric(vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.darkSurfaceVariant,
                borderRadius: BorderRadius.circular(Insets.radiusSm),
              ),
              child: Column(
                children: [
                  Text("${trip.offeredFare.toInt()}", style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
                  Text(currency, style: const TextStyle(color: AppColors.textTertiary, fontSize: 10)),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "${trip.totalDistanceKm.toStringAsFixed(1)} كم • ${trip.profitPerKm.toStringAsFixed(1)} $currency/كم",
                    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    "وصول ${trip.pickupDistanceKm.toStringAsFixed(1)} + مشوار ${trip.tripDistanceKm.toStringAsFixed(1)} كم • $time",
                    style: const TextStyle(color: AppColors.textTertiary, fontSize: 11.5),
                  ),
                ],
              ),
            ),
            Text(
              signed(trip.netProfit, decimals: 0),
              style: TextStyle(color: moneyColor(trip.netProfit), fontWeight: FontWeight.w800, fontSize: 15),
            ),
          ],
        ),
      ),
    );
  }
}

/// Simple bar chart of net profit per day (no chart package needed).
class _WeekChart extends StatelessWidget {
  final List<DaySummary> days;
  final String currency;

  const _WeekChart({required this.days, required this.currency});

  @override
  Widget build(BuildContext context) {
    final maxNet = days.fold<double>(0, (m, d) => d.net > m ? d.net : m);
    final total = days.fold<double>(0, (s, d) => s + d.net);
    final totalTrips = days.fold<int>(0, (s, d) => s + d.trips);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text("${total.toInt()} $currency", style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
            const SizedBox(width: 8),
            Text("• $totalTrips رحلة", style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5)),
          ],
        ),
        const SizedBox(height: Insets.md),
        SizedBox(
          height: 120,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: days.map((d) {
              final ratio = maxNet <= 0 ? 0.0 : (d.net.clamp(0, maxNet) / maxNet);
              final isToday = DateUtils.isSameDay(d.day, DateTime.now());
              return Expanded(
                child: Tooltip(
                  message: "${d.net.toInt()} $currency • ${d.trips} رحلة",
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        if (d.net > 0)
                          FittedBox(
                            child: Text("${d.net.toInt()}", style: const TextStyle(color: AppColors.textTertiary, fontSize: 10)),
                          ),
                        const SizedBox(height: 4),
                        AnimatedContainer(
                          duration: const Duration(milliseconds: 300),
                          height: 4 + 72 * ratio,
                          decoration: BoxDecoration(
                            color: isToday ? AppColors.emeraldPrimary : AppColors.emeraldPrimary.withValues(alpha: 0.35),
                            borderRadius: BorderRadius.circular(6),
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          DateFormat('E', 'ar').format(d.day),
                          style: TextStyle(
                            fontSize: 10.5,
                            color: isToday ? AppColors.emeraldLight : AppColors.textTertiary,
                            fontWeight: isToday ? FontWeight.w700 : FontWeight.w400,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ),
      ],
    );
  }
}
