import 'package:flutter_test/flutter_test.dart';
import 'package:indrive_assistant/data/models/hud_settings.dart';

void main() {
  test('the old "profitable only" switch becomes profitable levels for vibration and voice', () {
    final h = HudSettings.fromJson({'vibrate': true, 'vibrateProfitableOnly': true});
    expect(h.vibrateLevels, "EXCELLENT,GOOD");
    expect(h.speakLevels, "EXCELLENT,GOOD");
    expect(HudSettings.fromJson({'vibrate': true}).vibrateLevels, HudSettings.allLevels);
  });

  test('live map settings: off by default, round trip, keep time clamped', () {
    expect(const HudSettings().liveMapEnabled, isFalse);
    const h = HudSettings(liveMapEnabled: true, liveMapFloating: true, liveMapSize: "LARGE", liveMapKeepSeconds: 120);
    expect(HudSettings.fromJson(h.toJson()).toJson(), h.toJson());
    expect(HudSettings.fromJson({'liveMapKeepSeconds': 5}).liveMapKeepSeconds, 15);
  });

  test('alert settings survive a JSON round trip', () {
    const h = HudSettings(
      vibrateLevels: "LOSS",
      speakLevels: "GOOD,POOR",
      vibrationStrength: "STRONG",
      speakDetail: "NET_KM",
      speechRate: 1.25,
      speakStopWhenGone: false,
      alertOncePerRequest: false,
      muteDuringAutomation: false,
    );
    final back = HudSettings.fromJson(h.toJson());
    expect(back.toJson(), h.toJson());
  });
}
