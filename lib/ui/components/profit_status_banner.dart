import 'package:flutter/material.dart';
import '../../engine/fare_calculator_engine.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import 'ui_kit.dart';

/// Colour and short label for a profitability level.
(Color, String, IconData) profitabilityStyle(ProfitabilityLevel level) => switch (level) {
      ProfitabilityLevel.excellentProfit => (AppColors.emeraldLight, "ربح ممتاز", Icons.trending_up),
      ProfitabilityLevel.goodProfit => (AppColors.emeraldPrimary, "عرض رابح", Icons.check_circle_outline),
      ProfitabilityLevel.poorMargin => (AppColors.warningYellow, "هامش ضعيف", Icons.error_outline),
      ProfitabilityLevel.lossDanger => (AppColors.lossRed, "رحلة خاسرة", Icons.warning_amber_rounded),
    };

/// Hero result of the calculator: verdict, big net profit and key ratios.
class ProfitStatusBanner extends StatelessWidget {
  final FareAnalysisResult analysis;
  final String currency;

  const ProfitStatusBanner({super.key, required this.analysis, required this.currency});

  @override
  Widget build(BuildContext context) {
    final (color, label, icon) = profitabilityStyle(analysis.profitabilityLevel);

    return Container(
      padding: const EdgeInsets.all(Insets.lg + 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(Insets.radiusLg),
        border: Border.all(color: color.withValues(alpha: 0.45)),
        gradient: LinearGradient(
          begin: AlignmentDirectional.topStart,
          end: AlignmentDirectional.bottomEnd,
          colors: [color.withValues(alpha: 0.16), AppColors.darkSurface],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              StatusPill(label: label, color: color, icon: icon),
              const Spacer(),
              Text("صافي ربحك", style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Text(
                  analysis.statusMessage,
                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.4),
                ),
              ),
              const SizedBox(width: 12),
              Text(
                signed(analysis.netProfit, decimals: 0),
                style: TextStyle(color: color, fontSize: 36, fontWeight: FontWeight.w900, height: 1),
              ),
              const SizedBox(width: 4),
              Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Text(currency, style: const TextStyle(color: AppColors.textSecondary, fontSize: 13)),
              ),
            ],
          ),
          if (analysis.warningNote != null) ...[
            const SizedBox(height: 10),
            Text(analysis.warningNote!, style: const TextStyle(color: AppColors.textPrimary, fontSize: 12.5, height: 1.45)),
          ],
          const SizedBox(height: 14),
          Row(
            children: [
              _Ratio(label: "لكل كم", value: "${analysis.profitPerKm.toStringAsFixed(1)} $currency"),
              _Ratio(label: "لكل ساعة", value: "${analysis.profitPerHour.toStringAsFixed(0)} $currency"),
              _Ratio(label: "هامش الربح", value: "${analysis.profitMarginPercent.toStringAsFixed(0)}%"),
            ],
          ),
        ],
      ),
    );
  }
}

class _Ratio extends StatelessWidget {
  final String label;
  final String value;

  const _Ratio({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        margin: const EdgeInsetsDirectional.only(end: 6),
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
        decoration: BoxDecoration(
          color: AppColors.darkBackground.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(Insets.radiusSm),
        ),
        child: Column(
          children: [
            Text(label, style: const TextStyle(color: AppColors.textTertiary, fontSize: 11)),
            const SizedBox(height: 2),
            FittedBox(
              child: Text(value, style: const TextStyle(color: AppColors.textPrimary, fontSize: 13.5, fontWeight: FontWeight.w700)),
            ),
          ],
        ),
      ),
    );
  }
}
