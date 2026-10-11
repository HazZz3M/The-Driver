import 'package:flutter/material.dart';
import '../../engine/fare_calculator_engine.dart';
import '../../theme/app_colors.dart';

class SmartBidCard extends StatelessWidget {
  final SmartBidOption bid;
  final String currency;
  final bool isSelected;
  final VoidCallback onSelect;

  const SmartBidCard({
    super.key,
    required this.bid,
    required this.currency,
    required this.isSelected,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    final borderColor = isSelected ? AppColors.neonGreen : AppColors.darkCardBorder;
    final bgColor = isSelected ? AppColors.neonGreen.withOpacity(0.08) : AppColors.darkSurface;

    Color tagColor;
    switch (bid.tag) {
      case "MINIMUM":
        tagColor = AppColors.warningYellow;
        break;
      case "OPTIMAL":
        tagColor = AppColors.neonGreen;
        break;
      case "HIGH MARGIN":
        tagColor = AppColors.accentCyan;
        break;
      default:
        tagColor = AppColors.neonGreen;
    }

    return InkWell(
      onTap: onSelect,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: bgColor,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: borderColor, width: isSelected ? 2 : 1),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: tagColor.withOpacity(0.18),
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: tagColor.withOpacity(0.4)),
                      ),
                      child: Text(
                        bid.tag,
                        style: TextStyle(
                          color: tagColor,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      bid.title,
                      style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                Text(
                  "${bid.suggestedFare.toInt()} $currency",
                  style: TextStyle(
                    color: isSelected ? AppColors.neonGreen : AppColors.textPrimary,
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              "صافي الربح المتوقع: +${bid.netProfit.toStringAsFixed(1)} $currency (${bid.profitPerKm.toStringAsFixed(1)} ج/كم)",
              style: TextStyle(
                color: bid.netProfit >= 0 ? AppColors.accentCyan : AppColors.lossRed,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              bid.description,
              style: const TextStyle(
                color: AppColors.textSecondary,
                fontSize: 11,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
