import 'package:flutter/material.dart';
import '../../engine/fare_calculator_engine.dart';
import '../../theme/app_colors.dart';

class ProfitStatusBanner extends StatelessWidget {
  final FareAnalysisResult analysis;
  final String currency;

  const ProfitStatusBanner({
    super.key,
    required this.analysis,
    required this.currency,
  });

  @override
  Widget build(BuildContext context) {
    Color bannerBg;
    Color bannerBorder;
    Color statusColor;
    IconData icon;

    switch (analysis.profitabilityLevel) {
      case ProfitabilityLevel.lossDanger:
        bannerBg = AppColors.lossRed.withOpacity(0.18);
        bannerBorder = AppColors.lossRed;
        statusColor = AppColors.lossRed;
        icon = Icons.warning_amber_rounded;
        break;
      case ProfitabilityLevel.poorMargin:
        bannerBg = AppColors.warningYellow.withOpacity(0.14);
        bannerBorder = AppColors.warningYellow;
        statusColor = AppColors.warningYellow;
        icon = Icons.warning_amber_rounded;
        break;
      case ProfitabilityLevel.excellentProfit:
        bannerBg = AppColors.neonGreen.withOpacity(0.18);
        bannerBorder = AppColors.neonGreen;
        statusColor = AppColors.neonGreen;
        icon = Icons.check_circle_rounded;
        break;
      case ProfitabilityLevel.goodProfit:
        bannerBg = AppColors.profitGreen.withOpacity(0.14);
        bannerBorder = AppColors.profitGreen;
        statusColor = AppColors.profitGreen;
        icon = Icons.check_circle_rounded;
        break;
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: bannerBg,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: bannerBorder, width: 2),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    Icon(icon, color: statusColor, size: 24),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        analysis.statusMessage,
                        style: TextStyle(
                          color: statusColor,
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Text(
                "${analysis.netProfit >= 0 ? "+" : ""}${analysis.netProfit.toStringAsFixed(1)} $currency",
                style: TextStyle(
                  color: statusColor,
                  fontWeight: FontWeight.w900,
                  fontSize: 20,
                ),
              ),
            ],
          ),
          if (analysis.warningNote != null) ...[
            const SizedBox(height: 8),
            Text(
              analysis.warningNote!,
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 12.5,
                height: 1.4,
              ),
            ),
          ],
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _buildSubMetric(
                  label: "صافي/كم",
                  value: "${analysis.profitPerKm.toStringAsFixed(1)} $currency",
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: _buildSubMetric(
                  label: "تكلفة الوقود",
                  value: "${analysis.fuelCost.toStringAsFixed(1)} $currency",
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: _buildSubMetric(
                  label: "عمولة التطبيق",
                  value: "${analysis.commissionCost.toStringAsFixed(1)} $currency",
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSubMetric({required String label, required String value}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.darkSurfaceVariant.withOpacity(0.85),
        borderRadius: BorderRadius.circular(10),
      ),
      child: FittedBox(
        fit: BoxFit.scaleDown,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              "$label: ",
              style: const TextStyle(color: AppColors.textSecondary, fontSize: 11),
            ),
            Text(
              value,
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.bold,
                fontSize: 11.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
