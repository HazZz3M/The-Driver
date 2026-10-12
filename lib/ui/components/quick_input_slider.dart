import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import 'dialogs.dart';

/// Labelled slider with -/+ steppers and tap-to-type value (accepts Arabic digits).
class QuickInputSlider extends StatelessWidget {
  final String title;
  final IconData icon;
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
    required this.onValueChange,
    this.icon = Icons.tune,
    this.step = 0.5,
  });

  double _snap(double v) => ((v / step).round() * step).clamp(min, max).toDouble();

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            Icon(icon, size: 18, color: AppColors.textSecondary),
            const SizedBox(width: 8),
            Expanded(child: Text(title, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600))),
            InkWell(
              borderRadius: BorderRadius.circular(8),
              onTap: () async {
                final v = await promptNumber(context, title: title, initial: value, min: min, max: max, unit: unit);
                if (v != null) onValueChange(v);
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                child: Text(
                  "${formatNumber(value, maxDecimals: 1)} $unit",
                  style: const TextStyle(color: AppColors.emeraldLight, fontWeight: FontWeight.w800, fontSize: 15),
                ),
              ),
            ),
          ],
        ),
        Row(
          children: [
            IconButton(
              visualDensity: VisualDensity.compact,
              icon: const Icon(Icons.remove_circle_outline, size: 22),
              color: AppColors.textSecondary,
              onPressed: value <= min ? null : () => onValueChange(_snap(value - step)),
            ),
            Expanded(
              child: Slider(
                value: value.clamp(min, max),
                min: min,
                max: max,
                onChanged: (v) => onValueChange(_snap(v)),
              ),
            ),
            IconButton(
              visualDensity: VisualDensity.compact,
              icon: const Icon(Icons.add_circle_outline, size: 22),
              color: AppColors.emeraldLight,
              onPressed: value >= max ? null : () => onValueChange(_snap(value + step)),
            ),
          ],
        ),
      ],
    );
  }
}
