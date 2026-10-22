import 'package:flutter/material.dart';
import '../../engine/fare_calculator_engine.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';

/// Compact counter-offer option. Tapping it uses this price as the offer.
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

  static const Map<String, String> _tagLabels = {"MINIMUM": "الحد الأدنى", "OPTIMAL": "موصى به", "HIGH MARGIN": "ذروة"};

  @override
  Widget build(BuildContext context) {
    final tagColor = switch (bid.tag) {
      "MINIMUM" => AppColors.warningYellow,
      "HIGH MARGIN" => AppColors.accentCyan,
      _ => AppColors.emeraldPrimary,
    };

    return Material(
      color: isSelected ? tagColor.withValues(alpha: 0.12) : AppColors.darkSurfaceVariant,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Insets.radiusMd),
        side: BorderSide(color: isSelected ? tagColor : AppColors.darkCardBorder, width: isSelected ? 1.6 : 1),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onSelect,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 10),
          child: Column(
            children: [
              Text(
                _tagLabels[bid.tag] ?? bid.tag,
                style: TextStyle(color: tagColor, fontSize: 12, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 6),
              FittedBox(
                child: Text(
                  "${bid.suggestedFare.toInt()}",
                  style: const TextStyle(color: AppColors.textPrimary, fontSize: 22, fontWeight: FontWeight.w900),
                ),
              ),
              Text(currency, style: const TextStyle(color: AppColors.textTertiary, fontSize: 12)),
              const SizedBox(height: 6),
              FittedBox(
                child: Text(
                  "صافي ${signed(bid.netProfit, decimals: 0)}",
                  style: TextStyle(
                    color: bid.netProfit >= 0 ? AppColors.emeraldLight : AppColors.lossRed,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
