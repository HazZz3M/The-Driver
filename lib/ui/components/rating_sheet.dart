import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../data/models/request_record.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';

/// Quick tags offered when rating a ride; they feed the analysis (counted per area / passenger).
const rideRatingTags = [
  "راكب محترم",
  "دفع زيادة / بقشيش",
  "استنى كتير",
  "طريق زحمة",
  "مشكلة في الدفع",
  "عنوان غلط",
  "مشوار سهل",
];

/// Bottom sheet to rate an accepted ride: 1–5 stars, quick tags and a note.
/// Resolves to true when the rating was saved.
Future<bool> showRatingSheet(BuildContext context, RequestRecord ride) async {
  final saved = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    builder: (_) => _RatingSheet(ride: ride),
  );
  return saved ?? false;
}

class _RatingSheet extends StatefulWidget {
  final RequestRecord ride;
  const _RatingSheet({required this.ride});

  @override
  State<_RatingSheet> createState() => _RatingSheetState();
}

class _RatingSheetState extends State<_RatingSheet> {
  late int _stars = widget.ride.rating ?? 0;
  late final Set<String> _tags = {...widget.ride.ratingTags};
  late final _note = TextEditingController(text: widget.ride.ratingNote);
  bool _saving = false;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    final ok = await context.read<AssistantProvider>().rateRequest(widget.ride.id, _stars, _tags.toList(), _note.text.trim());
    if (mounted) Navigator.pop(context, ok);
  }

  @override
  Widget build(BuildContext context) {
    final r = widget.ride;
    final c = context.read<AssistantProvider>().driverSettings.currency;
    final when = DateFormat('EEEE h:mm a', 'ar').format(r.firstSeen);
    return Padding(
      padding: EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, MediaQuery.of(context).viewInsets.bottom + Insets.lg),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("قيّم الرحلة", style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          const SizedBox(height: 4),
          Text(
            "$when • ${(r.bidValue ?? r.fare).toInt()} $c"
            "${r.passengerName.isNotEmpty ? " • ${r.passengerName}" : ""}"
            "${r.dropoffAddress.isNotEmpty ? "\nإلى ${r.dropoffAddress}" : ""}",
            style: const TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.5),
          ),
          const SizedBox(height: Insets.md),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              for (var i = 1; i <= 5; i++)
                IconButton(
                  iconSize: 38,
                  tooltip: "$i من 5",
                  onPressed: () => setState(() => _stars = i),
                  icon: Icon(i <= _stars ? Icons.star_rounded : Icons.star_outline_rounded,
                      color: i <= _stars ? AppColors.accentAmber : AppColors.textTertiary),
                ),
            ],
          ),
          const SizedBox(height: Insets.sm),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final t in rideRatingTags)
                FilterChip(
                  label: Text(t),
                  selected: _tags.contains(t),
                  onSelected: (v) => setState(() => v ? _tags.add(t) : _tags.remove(t)),
                ),
            ],
          ),
          const SizedBox(height: Insets.md),
          TextField(
            controller: _note,
            maxLines: 2,
            decoration: const InputDecoration(hintText: "ملاحظة (اختياري)"),
          ),
          const SizedBox(height: Insets.md),
          Row(
            children: [
              TextButton(onPressed: () => Navigator.pop(context, false), child: const Text("بعدين")),
              const Spacer(),
              FilledButton(
                onPressed: _stars == 0 || _saving ? null : _save,
                child: const Text("حفظ التقييم"),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
