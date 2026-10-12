import 'package:flutter_test/flutter_test.dart';
import 'package:indrive_assistant/utils/number_parsing.dart';
import 'package:indrive_assistant/utils/update_manager.dart';

void main() {
  group('parseLocalizedDouble', () {
    test('Latin digits', () {
      expect(parseLocalizedDouble("30"), 30);
      expect(parseLocalizedDouble("15.25"), 15.25);
    });

    test('Arabic-Indic and Persian digits', () {
      expect(parseLocalizedDouble("٣٠"), 30);
      expect(parseLocalizedDouble("۴۵"), 45);
    });

    test('Arabic decimal separator and decimal comma', () {
      expect(parseLocalizedDouble("١٥٫٢٥"), 15.25);
      expect(parseLocalizedDouble("15,25"), 15.25);
    });

    test('thousands separator', () {
      expect(parseLocalizedDouble("1,500"), 1500);
      expect(parseLocalizedDouble("١٬٥٠٠"), 1500);
    });

    test('invalid input returns null', () {
      expect(parseLocalizedDouble(""), isNull);
      expect(parseLocalizedDouble("abc"), isNull);
    });
  });

  test('parseLocalizedInt rejects fractions', () {
    expect(parseLocalizedInt("٤٠٠٠"), 4000);
    expect(parseLocalizedInt("4.5"), isNull);
  });

  test('signed never produces "+-"', () {
    expect(signed(12.0), "+12.0");
    expect(signed(-12.5), "−12.5");
  });

  test('formatNumber trims trailing zeros but keeps precision', () {
    expect(formatNumber(15.25), "15.25");
    expect(formatNumber(15.0), "15");
    expect(formatNumber(8.5), "8.5");
  });

  group('UpdateManager.compareVersions', () {
    test('semantic, not string, comparison', () {
      expect(UpdateManager.compareVersions("v1.10.0", "1.9.0"), greaterThan(0));
      expect(UpdateManager.compareVersions("1.9.0", "v1.10.0"), lessThan(0));
      expect(UpdateManager.compareVersions("v1.10.0", "1.10.0+12"), 0);
    });

    test('an older tag is never an update', () {
      expect(UpdateManager.compareVersions("v1.8.0", "1.10.0"), lessThan(0));
    });
  });
}
