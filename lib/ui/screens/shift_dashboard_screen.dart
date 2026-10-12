import 'dart:async';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../data/models/trip.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import '../components/dialogs.dart';

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
    return h > 0 ? "$h س $m د" : "$m د";
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final settings = provider.driverSettings;
    final trips = provider.todayTrips;
    final progress = provider.shiftProgress;

    final remaining = (settings.targetDailyEarnings - provider.todayTotalGross).clamp(0.0, double.infinity);

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Top Header & Shift Toggle Button
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "لوحة متابعة الوردية والأرباح",
                      style: TextStyle(
                        color: AppColors.textPrimary,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    Text(
                      "متابعة أهداف الدخل وسجل الرحلات اليومية",
                      style: TextStyle(color: AppColors.textSecondary, fontSize: 12),
                    ),
                  ],
                ),
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: provider.isShiftActive ? AppColors.lossRed : AppColors.neonGreen,
                    foregroundColor: provider.isShiftActive ? Colors.white : AppColors.darkBackground,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  ),
                  icon: Icon(provider.isShiftActive ? Icons.stop : Icons.play_arrow, size: 20),
                  label: Text(
                    provider.isShiftActive ? "إنهاء الوردية" : "بدء الوردية",
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                  ),
                  onPressed: () => provider.toggleShift(),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // Daily Target Progress Card
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: AppColors.darkSurface,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: AppColors.darkCardBorder),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        "الهدف اليومي (Target)",
                        style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
                      ),
                      Text(
                        "${(progress * 100).toInt()}% منجز",
                        style: const TextStyle(
                          color: AppColors.neonGreen,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        "${provider.todayTotalGross.toInt()}",
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontWeight: FontWeight.w900,
                          fontSize: 32,
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.only(bottom: 6, right: 6),
                        child: Text(
                          " / ${settings.targetDailyEarnings.toInt()} ${settings.currency}",
                          style: const TextStyle(
                            color: AppColors.textSecondary,
                            fontWeight: FontWeight.w600,
                            fontSize: 15,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(5),
                    child: LinearProgressIndicator(
                      value: progress,
                      minHeight: 10,
                      backgroundColor: AppColors.darkSurfaceVariant,
                      valueColor: const AlwaysStoppedAnimation<Color>(AppColors.neonGreen),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    remaining > 0
                        ? "المتبقي لتحقيق الهدف: ${remaining.toInt()} ${settings.currency}"
                        : "تم تحقيق الهدف اليومي وتجاوزه بنجاح! 🎉",
                    style: TextStyle(
                      color: remaining > 0 ? AppColors.textSecondary : AppColors.neonGreen,
                      fontSize: 12,
                      fontWeight: remaining > 0 ? FontWeight.normal : FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // Stats Summary Grid (2x2)
            Row(
              children: [
                Expanded(
                  child: _buildStatTile(
                    title: "إجمالي الإيراد",
                    value: "${provider.todayTotalGross.toInt()} ${settings.currency}",
                    color: AppColors.neonGreen,
                    icon: Icons.monetization_on_outlined,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _buildStatTile(
                    title: "صافي الأرباح",
                    value: "${provider.todayTotalNetProfit.toInt()} ${settings.currency}",
                    color: AppColors.accentCyan,
                    icon: Icons.trending_up,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 10),

            Row(
              children: [
                Expanded(
                  child: _buildStatTile(
                    title: "الرحلات المكتملة",
                    value: "${provider.todayTripsCount} رحلات",
                    color: AppColors.accentAmber,
                    icon: Icons.directions_car,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _buildStatTile(
                    title: "حالة الوردية",
                    value: provider.isShiftActive ? "نشطة منذ ${_formatDuration(provider.shiftDuration)}" : "متوقفة",
                    color: provider.isShiftActive ? AppColors.neonGreen : AppColors.textSecondary,
                    icon: Icons.timer_outlined,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 20),

            // Completed Trips Log Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  "سجل رحلات اليوم المكتملة",
                  style: TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  "${trips.length} رحلة",
                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
                ),
              ],
            ),

            const SizedBox(height: 10),

            if (trips.isEmpty)
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: AppColors.darkSurface,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.darkCardBorder),
                ),
                alignment: Alignment.center,
                child: const Column(
                  children: [
                    Icon(Icons.history, color: AppColors.textSecondary, size: 36),
                    SizedBox(height: 8),
                    Text(
                      "لا توجد رحلات مسجلة اليوم حتى الآن",
                      style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
                    ),
                    SizedBox(height: 4),
                    Text(
                      "احسب رحلة في الحاسبة واضغط 'حفظ الرحلة'",
                      style: TextStyle(color: AppColors.textTertiary, fontSize: 11),
                    ),
                  ],
                ),
              )
            else
              ...trips.map((t) => _buildTripCard(context, t, settings.currency, provider)),
          ],
        ),
      ),
    );
  }

  Widget _buildStatTile({
    required String title,
    required String value,
    required Color color,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.darkSurface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.darkCardBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(title, style: const TextStyle(color: AppColors.textSecondary, fontSize: 11.5)),
              Icon(icon, color: color, size: 18),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w800,
              fontSize: 16,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTripCard(
    BuildContext context,
    Trip trip,
    String currency,
    AssistantProvider provider,
  ) {
    final timeStr = DateFormat('hh:mm a').format(DateTime.fromMillisecondsSinceEpoch(trip.timestamp));

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.darkSurface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.darkCardBorder),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.neonGreen.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    "${trip.offeredFare.toInt()} $currency",
                    style: const TextStyle(
                      color: AppColors.neonGreen,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    "${trip.totalDistanceKm.toStringAsFixed(1)} كم (${trip.pickupDistanceKm.toStringAsFixed(1)} وصول + ${trip.tripDistanceKm.toStringAsFixed(1)} مشوار)",
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: AppColors.textPrimary, fontSize: 12),
                  ),
                ),
                IconButton(
                  tooltip: "حذف الرحلة",
                  icon: const Icon(Icons.delete_outline, size: 20, color: AppColors.lossRed),
                  onPressed: () async {
                    final ok = await confirmAction(
                      context,
                      title: "حذف الرحلة؟",
                      message: "سيتم حذف رحلة ${trip.offeredFare.toInt()} $currency من سجل اليوم نهائياً.",
                    );
                    if (ok) await provider.deleteTrip(trip.id);
                  },
                ),
              ],
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                Expanded(
                  child: Text(
                    "صافي الربح: ${signed(trip.netProfit)} $currency (${trip.profitPerKm.toStringAsFixed(1)} $currency/كم)",
                    style: TextStyle(
                      color: trip.netProfit >= 0 ? AppColors.accentCyan : AppColors.lossRed,
                      fontWeight: FontWeight.bold,
                      fontSize: 12,
                    ),
                  ),
                ),
                Text(
                  timeStr,
                  style: const TextStyle(color: AppColors.textTertiary, fontSize: 11),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
