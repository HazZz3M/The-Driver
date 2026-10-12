import 'package:flutter/services.dart';

/// Converts Arabic-Indic (٠-٩) and Persian (۰-۹) digits to ASCII and the Arabic decimal
/// separator (٫) / comma to '.', so values typed on an Arabic keyboard parse correctly.
String normalizeDigits(String input) {
  final sb = StringBuffer();
  for (final rune in input.runes) {
    if (rune >= 0x0660 && rune <= 0x0669) {
      sb.writeCharCode(0x30 + rune - 0x0660);
    } else if (rune >= 0x06F0 && rune <= 0x06F9) {
      sb.writeCharCode(0x30 + rune - 0x06F0);
    } else if (rune == 0x066B) {
      sb.write('.'); // Arabic decimal separator
    } else if (rune == 0x060C || rune == 0x066C) {
      sb.write(','); // Arabic comma / thousands separator
    } else {
      sb.writeCharCode(rune);
    }
  }
  return sb.toString().trim();
}

/// Parses a user-typed number in Arabic or Latin digits. Returns null when it is not a number.
/// A comma followed by exactly three digits ("1,500") is a thousands separator; otherwise a
/// comma is treated as the decimal separator ("15,25").
double? parseLocalizedDouble(String input) {
  var normalized = normalizeDigits(input).replaceAll(' ', '');
  if (normalized.isEmpty) return null;
  if (normalized.contains(',')) {
    final isThousands = !normalized.contains('.') && RegExp(r'^\d{1,3}(,\d{3})+$').hasMatch(normalized);
    normalized = isThousands ? normalized.replaceAll(',', '') : normalized.replaceAll(',', '.');
  }
  return double.tryParse(normalized);
}

int? parseLocalizedInt(String input) {
  final value = parseLocalizedDouble(input);
  if (value == null || value != value.roundToDouble()) return null;
  return value.toInt();
}

/// Keyboard for decimal input fields.
const TextInputType decimalKeyboard = TextInputType.numberWithOptions(decimal: true);

/// Allows Latin/Arabic/Persian digits and the decimal separators only.
final List<TextInputFormatter> decimalInputFormatters = [
  FilteringTextInputFormatter.allow(RegExp(r'[0-9٠-٩۰-۹.,٫]')),
];

/// Formats a number with up to [maxDecimals] decimals, trimming trailing zeros.
String formatNumber(double value, {int maxDecimals = 2}) {
  var s = value.toStringAsFixed(maxDecimals);
  if (s.contains('.')) {
    s = s.replaceAll(RegExp(r'0+$'), '');
    if (s.endsWith('.')) s = s.substring(0, s.length - 1);
  }
  return s;
}

/// Prefixes a sign, using a real minus for negatives (never "+-").
String signed(double value, {int decimals = 1}) {
  final abs = value.abs().toStringAsFixed(decimals);
  return value < 0 ? "−$abs" : "+$abs";
}
