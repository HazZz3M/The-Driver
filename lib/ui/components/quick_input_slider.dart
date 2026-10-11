import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';

class QuickInputSlider extends StatelessWidget {
  final String title;
  final double value;
  final String unit;
  final double min;
  final double max;
  final double step;
  final ValueChanged<double> onValueChange;

  const QuickInputSlider({
    super.key,
    required this.title,
    required this.value,
    required this.unit,
    required this.min,
    required this.max,
    this.step = 0.5,
    required this.onValueChange,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
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
              Text(
                title,
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.neonGreen.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.neonGreen.withOpacity(0.3)),
                ),
                child: Text(
                  "${value.toStringAsFixed(value % 1 == 0 ? 0 : 1)} $unit",
                  style: const TextStyle(
                    color: AppColors.neonGreen,
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              IconButton(
                icon: const Icon(Icons.remove_circle_outline, size: 20),
                color: AppColors.textSecondary,
                onPressed: () {
                  final next = (value - step).clamp(min, max);
                  onValueChange(next);
                },
              ),
              Expanded(
                child: SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    activeTrackColor: AppColors.neonGreen,
                    inactiveTrackColor: AppColors.darkSurfaceVariant,
                    thumbColor: AppColors.neonGreen,
                    overlayColor: AppColors.neonGreen.withOpacity(0.2),
                    trackHeight: 4,
                  ),
                  child: Slider(
                    value: value.clamp(min, max),
                    min: min,
                    max: max,
                    onChanged: (v) {
                      final rounded = (v / step).round() * step;
                      onValueChange(rounded.clamp(min, max));
                    },
                  ),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.add_circle_outline, size: 20),
                color: AppColors.neonGreen,
                onPressed: () {
                  final next = (value + step).clamp(min, max);
                  onValueChange(next);
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}
