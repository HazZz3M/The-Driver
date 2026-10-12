import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import '../components/profit_status_banner.dart';
import '../components/quick_input_slider.dart';
import '../components/smart_bid_card.dart';
import '../components/ui_kit.dart';

class CalculatorScreen extends StatefulWidget {
  const CalculatorScreen({super.key});

  @override
  State<CalculatorScreen> createState() => _CalculatorScreenState();
}

class _CalculatorScreenState extends State<CalculatorScreen> {
  double? _selectedBidFare;
  bool _isSaving = false;
  bool _showCosts = false;

  Future<void> _save(AssistantProvider provider) async {
    setState(() => _isSaving = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await provider.saveCurrentRide(_selectedBidFare);
      HapticFeedback.mediumImpact();
      messenger.showSnackBar(const SnackBar(content: Text("تم حفظ الرحلة في سجل الوردية")));
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final settings = provider.driverSettings;
    final analysis = provider.fareAnalysis;
    final activeVehicle = provider.activeProfile;
    final currency = settings.currency;

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
          children: [
            PageHeader(
              title: "حاسبة العرض",
              subtitle: "قيّم أي عرض في ثوانٍ قبل القبول",
              trailing: IconButton(
                tooltip: "إعادة ضبط",
                icon: const Icon(Icons.restart_alt, color: AppColors.textSecondary),
                onPressed: () {
                  setState(() => _selectedBidFare = null);
                  provider.resetCalculator();
                },
              ),
            ),

            // Active vehicle + shift state
            Row(
              children: [
                if (activeVehicle != null)
                  Expanded(
                    child: PopupMenuButton<String>(
                      tooltip: "تبديل المركبة",
                      onSelected: (id) {
                        final target = provider.vehicleProfiles.firstWhere((v) => v.id == id);
                        provider.switchActiveVehicle(target);
                      },
                      itemBuilder: (context) => provider.vehicleProfiles
                          .map((p) => PopupMenuItem(
                                value: p.id,
                                child: Text(p.name,
                                    style: TextStyle(
                                      fontWeight: p.id == provider.activeVehicleId ? FontWeight.w700 : FontWeight.w400,
                                      color: p.id == provider.activeVehicleId ? AppColors.emeraldLight : null,
                                    )),
                              ))
                          .toList(),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        decoration: BoxDecoration(
                          color: AppColors.darkSurface,
                          borderRadius: BorderRadius.circular(Insets.radiusMd),
                          border: Border.all(color: AppColors.darkCardBorder),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.directions_car_filled_outlined, size: 18, color: AppColors.emeraldLight),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                "${activeVehicle.name} • ${formatNumber(settings.fuelConsumptionLPer100Km, maxDecimals: 1)} ل/100كم",
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
                              ),
                            ),
                            const Icon(Icons.expand_more, size: 18, color: AppColors.textSecondary),
                          ],
                        ),
                      ),
                    ),
                  ),
                const SizedBox(width: 8),
                StatusPill(
                  label: provider.isShiftActive ? "الوردية جارية" : "خارج الوردية",
                  color: provider.isShiftActive ? AppColors.emeraldPrimary : AppColors.textTertiary,
                  icon: provider.isShiftActive ? Icons.play_circle_outline : Icons.pause_circle_outline,
                ),
              ],
            ),

            const SizedBox(height: Insets.md),

            // 1. Verdict
            ProfitStatusBanner(analysis: analysis, currency: currency),

            // 2. Inputs
            const SectionLabel("بيانات الطلب"),
            AppCard(
              padding: const EdgeInsets.fromLTRB(Insets.md, Insets.md, Insets.md, Insets.sm),
              child: Column(
                children: [
                  QuickInputSlider(
                    title: "عرض الراكب",
                    icon: Icons.payments_outlined,
                    value: provider.offeredFare,
                    unit: currency,
                    min: 10.0,
                    max: 500.0,
                    step: 5.0,
                    onValueChange: (v) {
                      provider.setOfferedFare(v);
                      setState(() => _selectedBidFare = null);
                    },
                  ),
                  SizedBox(
                    height: 36,
                    child: ListView(
                      scrollDirection: Axis.horizontal,
                      children: [-10.0, -5.0, 5.0, 10.0, 15.0, 20.0].map((delta) {
                        final positive = delta > 0;
                        return Padding(
                          padding: const EdgeInsetsDirectional.only(end: 6),
                          child: ActionChip(
                            visualDensity: VisualDensity.compact,
                            label: Text(
                              positive ? "+${delta.toInt()}" : "${delta.toInt()}",
                              style: TextStyle(
                                color: positive ? AppColors.emeraldLight : AppColors.lossRed,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            onPressed: () {
                              HapticFeedback.selectionClick();
                              provider.setOfferedFare((provider.offeredFare + delta).clamp(10.0, 500.0));
                              setState(() => _selectedBidFare = null);
                            },
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                  const Divider(height: 24),
                  QuickInputSlider(
                    title: "مسافة الوصول للراكب",
                    icon: Icons.near_me_outlined,
                    value: provider.pickupDistanceKm,
                    unit: "كم",
                    min: 0.2,
                    max: 15.0,
                    step: 0.5,
                    onValueChange: provider.setPickupDistance,
                  ),
                  QuickInputSlider(
                    title: "مسافة المشوار",
                    icon: Icons.route_outlined,
                    value: provider.tripDistanceKm,
                    unit: "كم",
                    min: 1.0,
                    max: 60.0,
                    step: 0.5,
                    onValueChange: provider.setTripDistance,
                  ),
                ],
              ),
            ),

            // 3. Costs (collapsible)
            const SectionLabel("التكاليف التشغيلية"),
            AppCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  ListTile(
                    leading: const TileIcon(Icons.receipt_long_outlined, color: AppColors.accentAmber),
                    title: Text(
                      "إجمالي التكلفة ${analysis.totalOperatingCost.toStringAsFixed(1)} $currency",
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
                    ),
                    subtitle: Text(
                      "لمسافة ${analysis.totalDistanceKm.toStringAsFixed(1)} كم",
                      style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                    ),
                    trailing: Icon(_showCosts ? Icons.expand_less : Icons.expand_more, color: AppColors.textSecondary),
                    onTap: () => setState(() => _showCosts = !_showCosts),
                  ),
                  AnimatedCrossFade(
                    duration: const Duration(milliseconds: 200),
                    crossFadeState: _showCosts ? CrossFadeState.showSecond : CrossFadeState.showFirst,
                    firstChild: const SizedBox(width: double.infinity),
                    secondChild: Padding(
                      padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.md),
                      child: Column(
                        children: [
                          KeyValueRow(
                            label: "البنزين (${analysis.fuelLitersUsed.toStringAsFixed(2)} لتر)",
                            value: "${analysis.fuelCost.toStringAsFixed(1)} $currency",
                          ),
                          KeyValueRow(
                            label: "عمولة إن درايف (${formatNumber(settings.inDriveCommissionPercent)}%)",
                            value: "${analysis.commissionCost.toStringAsFixed(1)} $currency",
                          ),
                          KeyValueRow(
                            label: "استهلاك وصيانة",
                            value: "${analysis.wearAndTearCost.toStringAsFixed(1)} $currency",
                          ),
                          const Divider(height: 16),
                          KeyValueRow(
                            label: "التعادل (أقل سعر بلا خسارة)",
                            value: "${analysis.breakEvenFare.toInt()} $currency",
                            emphasize: true,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // 4. Counter-offers
            const SectionLabel("اقتراحات المزايدة"),
            Row(
              children: [
                for (final bid in analysis.smartBids) ...[
                  Expanded(
                    child: SmartBidCard(
                      bid: bid,
                      currency: currency,
                      isSelected: _selectedBidFare == bid.suggestedFare,
                      onSelect: () {
                        HapticFeedback.selectionClick();
                        setState(() => _selectedBidFare = bid.suggestedFare);
                        provider.setOfferedFare(bid.suggestedFare);
                      },
                    ),
                  ),
                  if (bid != analysis.smartBids.last) const SizedBox(width: 8),
                ],
              ],
            ),

            const SizedBox(height: Insets.xl),

            // 5. Save
            FilledButton.icon(
              icon: _isSaving
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.check_rounded),
              label: Text("حفظ الرحلة في السجل • ${(_selectedBidFare ?? provider.offeredFare).toInt()} $currency"),
              onPressed: _isSaving ? null : () => _save(provider),
            ),
          ],
        ),
      ),
    );
  }
}
