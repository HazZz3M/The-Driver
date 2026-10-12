import 'package:flutter_test/flutter_test.dart';
import 'package:indrive_assistant/data/models/hud_settings.dart';

void main() {
  test('HudSettings survives a JSON round trip', () {
    const original = HudSettings(
      style: "SLIM_PILL",
      size: "LARGE",
      primaryValue: "PROFIT_PER_KM",
      opacity: 0.7,
      side: "RIGHT",
      showLossBadges: false,
      vibrate: true,
      vibrateProfitableOnly: true,
      showCostBreakdown: true,
      autoCollapseSeconds: 10,
    );
    final restored = HudSettings.fromJson(original.toJson());
    expect(restored.toJson(), original.toJson());
  });

  test('missing keys fall back to defaults and opacity is clamped', () {
    final s = HudSettings.fromJson({'opacity': 0.1, 'style': 'GLOW_DOT'});
    expect(s.style, 'GLOW_DOT');
    expect(s.opacity, 0.4);
    expect(s.primaryValue, const HudSettings().primaryValue);
    expect(s.showZones, isTrue);
  });

  test('every option has an Arabic label', () {
    for (final v in ["NET_PROFIT", "PROFIT_PER_KM", "OFFER", "RECOMMENDED"]) {
      expect(HudSettings.primaryValueLabels[v], isNotNull);
      expect(HudSettings.primaryValueCaptions[v], isNotNull);
    }
  });
}
