import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';

/// Asks before a destructive action. Resolves to true only when the driver confirms.
Future<bool> confirmAction(
  BuildContext context, {
  required String title,
  required String message,
  String confirmLabel = "حذف",
  bool destructive = true,
}) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
      content: Text(message, style: const TextStyle(fontSize: 13, height: 1.5)),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text("إلغاء")),
        ElevatedButton(
          style: destructive
              ? ElevatedButton.styleFrom(backgroundColor: AppColors.lossRed, foregroundColor: Colors.white)
              : null,
          onPressed: () => Navigator.pop(ctx, true),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return result ?? false;
}

/// Lets the driver type an exact value (Arabic or Latin digits) within [min]..[max].
Future<double?> promptNumber(
  BuildContext context, {
  required String title,
  required double initial,
  required double min,
  required double max,
  String? unit,
}) {
  final ctrl = TextEditingController(text: formatNumber(initial));
  String? error;

  return showDialog<double>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, setDlgState) {
        void submit() {
          final v = parseLocalizedDouble(ctrl.text);
          if (v == null) {
            setDlgState(() => error = "أدخل رقماً صحيحاً");
          } else if (v < min || v > max) {
            setDlgState(() => error = "القيمة يجب أن تكون بين ${formatNumber(min)} و ${formatNumber(max)}");
          } else {
            Navigator.pop(ctx, v);
          }
        }

        return AlertDialog(
          title: Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            keyboardType: decimalKeyboard,
            inputFormatters: decimalInputFormatters,
            decoration: InputDecoration(suffixText: unit, errorText: error),
            onSubmitted: (_) => submit(),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("إلغاء")),
            ElevatedButton(onPressed: submit, child: const Text("حفظ")),
          ],
        );
      },
    ),
  );
}

/// A numeric text field that accepts Arabic digits and shows inline validation.
class NumberField extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final String? hint;
  final String? errorText;

  const NumberField({
    super.key,
    required this.controller,
    required this.label,
    this.hint,
    this.errorText,
  });

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      keyboardType: decimalKeyboard,
      inputFormatters: decimalInputFormatters,
      decoration: InputDecoration(labelText: label, hintText: hint, errorText: errorText),
    );
  }
}
