import 'package:flutter/material.dart';
import '../../data/models/hud_settings.dart';
import '../../theme/app_colors.dart';

/// Flutter replica of the native floating badge (FloatingHudService.kt) so the driver can see
/// the effect of style / size / value / opacity choices without opening inDrive.
class HudBadgePreview extends StatelessWidget {
  final HudSettings hud;
  final Color color;
  final bool favorite;

  const HudBadgePreview({
    super.key,
    required this.hud,
    this.color = AppColors.emeraldPrimary,
    this.favorite = false,
  });

  String get _value => switch (hud.primaryValue) {
        "OFFER" => "85",
        "RECOMMENDED" => "95",
        "PROFIT_PER_KM" => "6.4",
        _ => "+42",
      };

  String get _caption => HudSettings.primaryValueCaptions[hud.primaryValue] ?? "صافي";

  double get _scale => switch (hud.size) {
        "SMALL" => 0.85,
        "LARGE" => 1.22,
        _ => 1.0,
      };

  @override
  Widget build(BuildContext context) {
    final s = _scale;
    final badge = switch (hud.style) {
      "SLIM_PILL" => _pill(s),
      "SPEEDO_GAUGE" => _speedo(s),
      "MINI_TAG" => _tag(s),
      "CORNER_RIBBON" => _ribbon(s),
      "GLOW_DOT" => _circle(s, diameter: 48, stroke: 3.5, fontSize: 13.5),
      _ => _circle(s, diameter: 52, stroke: 2.5, fontSize: 13),
    };
    return Opacity(
      opacity: hud.opacity,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          badge,
          if (favorite)
            Positioned(
              top: -4,
              left: -4,
              child: Container(
                width: 16,
                height: 16,
                alignment: Alignment.center,
                decoration: const BoxDecoration(color: Color(0xFFFFB800), shape: BoxShape.circle),
                child: const Text("★", style: TextStyle(fontSize: 9, color: Color(0xFF070B14))),
              ),
            ),
        ],
      ),
    );
  }

  TextStyle _valueStyle(double size, {Color c = Colors.white}) =>
      TextStyle(color: c, fontSize: size, fontWeight: FontWeight.w800, height: 1.1);

  Widget _circle(double s, {required double diameter, required double stroke, required double fontSize}) {
    return Container(
      width: diameter * s,
      height: diameter * s,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: const Color(0xFF0B0F19),
        shape: BoxShape.circle,
        border: Border.all(color: color, width: stroke),
      ),
      child: FittedBox(child: Padding(padding: const EdgeInsets.all(4), child: Text(_value, style: _valueStyle(fontSize * s)))),
    );
  }

  Widget _pill(double s) {
    return Container(
      height: 30 * s,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(
        color: const Color(0xFF0B0F19),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color, width: 2.2),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 8, height: 8, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
          const SizedBox(width: 6),
          Text("$_value $_caption", style: _valueStyle(12.5 * s)),
        ],
      ),
    );
  }

  Widget _speedo(double s) {
    return Container(
      width: 58 * s,
      height: 46 * s,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: const Color(0xFF0F172A),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color, width: 2.5),
      ),
      child: Column(
        children: [
          Container(height: 3, color: color),
          Expanded(
            child: FittedBox(
              child: Column(
                children: [
                  Text(_value, style: _valueStyle(13 * s)),
                  Text(_caption, style: TextStyle(color: AppColors.textSecondary, fontSize: 9 * s)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _tag(double s) {
    return Container(
      height: 30 * s,
      padding: const EdgeInsets.symmetric(horizontal: 9),
      alignment: Alignment.center,
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(6)),
      child: Text("$_value $_caption", style: _valueStyle(13 * s, c: const Color(0xFF0F172A))),
    );
  }

  Widget _ribbon(double s) {
    return Container(
      height: 28 * s,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: const Color(0xFF0B132B),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color, width: 1.8),
      ),
      child: Text("$_caption $_value", style: _valueStyle(12 * s, c: color)),
    );
  }
}

/// A mock inDrive request card with the badge placed on the chosen side, for the HUD settings page.
class HudPlacementPreview extends StatelessWidget {
  final HudSettings hud;

  const HudPlacementPreview({super.key, required this.hud});

  @override
  Widget build(BuildContext context) {
    Widget line(double widthFactor, {double h = 8, Color c = AppColors.surfaceHigh}) => FractionallySizedBox(
          widthFactor: widthFactor,
          alignment: AlignmentDirectional.centerStart,
          child: Container(height: h, decoration: BoxDecoration(color: c, borderRadius: BorderRadius.circular(4))),
        );

    final mockCard = Container(
      height: 104,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF1F2937),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          line(0.35, h: 12, c: const Color(0xFF374151)),
          line(0.7),
          line(0.55),
          line(0.3, h: 10, c: const Color(0xFF4B5563)),
        ],
      ),
    );

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF111827),
        borderRadius: BorderRadius.circular(Insets.radiusMd),
        border: Border.all(color: AppColors.darkCardBorder),
      ),
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          mockCard,
          Positioned(
            top: 6,
            left: hud.side == "LEFT" ? 6 : null,
            right: hud.side == "RIGHT" ? 6 : null,
            child: HudBadgePreview(hud: hud),
          ),
        ],
      ),
    );
  }
}
