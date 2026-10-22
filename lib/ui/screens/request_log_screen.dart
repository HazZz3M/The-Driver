import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../data/models/request_record.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../components/rating_sheet.dart';
import '../components/ui_kit.dart';

/// Every request seen in inDrive, newest first, with its details, text snapshot and rating.
class RequestLogScreen extends StatefulWidget {
  const RequestLogScreen({super.key});

  @override
  State<RequestLogScreen> createState() => _RequestLogScreenState();
}

class _RequestLogScreenState extends State<RequestLogScreen> {
  String _filter = "ALL"; // ALL | OPENED | ACCEPTED

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final c = provider.driverSettings.currency;
    final all = provider.requestLog.reversed.toList();
    final list = switch (_filter) {
      "ACCEPTED" => all.where((r) => r.accepted).toList(),
      "OPENED" => all.where((r) => r.opened).toList(),
      _ => all,
    };

    return Scaffold(
      appBar: AppBar(title: const Text("سجل الطلبات")),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: Insets.lg, vertical: Insets.sm),
              child: SizedBox(
                width: double.infinity,
                child: SegmentedButton<String>(
                  showSelectedIcon: false,
                  segments: const [
                    ButtonSegment(value: "ALL", label: Text("الكل")),
                    ButtonSegment(value: "OPENED", label: Text("اتفتحت")),
                    ButtonSegment(value: "ACCEPTED", label: Text("اتقبلت")),
                  ],
                  selected: {_filter},
                  onSelectionChanged: (v) => setState(() => _filter = v.first),
                ),
              ),
            ),
            Expanded(
              child: list.isEmpty
                  ? const Padding(
                      padding: EdgeInsets.all(Insets.lg),
                      child: EmptyState(
                        icon: Icons.inbox_outlined,
                        title: "مفيش طلبات هنا لسه",
                        message: "شغّل المساعد وافتح إن درايف: كل طلب بيظهر بيتسجل لوحده.",
                      ),
                    )
                  : ListView.separated(
                      padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
                      itemCount: list.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 8),
                      itemBuilder: (_, i) => _LogRow(record: list[i], currency: c),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LogRow extends StatelessWidget {
  final RequestRecord record;
  final String currency;
  const _LogRow({required this.record, required this.currency});

  Color get _statusColor => switch (record.status) {
        "ACCEPTED" => AppColors.emeraldPrimary,
        "BID_SENT" => AppColors.accentCyan,
        "OPENED" => AppColors.accentAmber,
        _ => AppColors.textTertiary,
      };

  @override
  Widget build(BuildContext context) {
    final r = record;
    final time = DateFormat('d MMM • h:mm a', 'ar').format(r.firstSeen);
    return AppCard(
      padding: const EdgeInsets.all(Insets.md),
      onTap: () async {
        final action = await showModalBottomSheet<String>(
          context: context,
          isScrollControlled: true,
          builder: (_) => _RecordDetails(record: r, currency: currency),
        );
        // The rating sheet opens from this (still mounted) row once the details sheet is closed.
        if (action == "rate" && context.mounted) await showRatingSheet(context, r);
      },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text("${(r.bidValue ?? r.fare).toInt()} $currency", style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
              const SizedBox(width: 8),
              StatusPill(label: RequestRecord.statusLabels[r.status] ?? r.status, color: _statusColor),
              const Spacer(),
              if (r.rating != null)
                Text("${"★" * r.rating!}", style: const TextStyle(color: AppColors.accentAmber, fontSize: 13)),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            "$time • وصول ${r.pickupKnown ? r.pickupKm.toStringAsFixed(1) : "؟"} + مشوار ${r.tripKnown ? r.tripKm.toStringAsFixed(1) : "≈${r.tripKm.toStringAsFixed(0)}"} كم"
            "${r.netProfit != null ? " • صافي ${r.netProfit!.toStringAsFixed(0)}" : ""}",
            style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5),
          ),
          if (r.pickupAddress.isNotEmpty || r.dropoffAddress.isNotEmpty)
            Text(
              "${r.pickupAddress} ← ${r.dropoffAddress}",
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: AppColors.textTertiary, fontSize: 12),
            ),
        ],
      ),
    );
  }
}

class _RecordDetails extends StatelessWidget {
  final RequestRecord record;
  final String currency;
  const _RecordDetails({required this.record, required this.currency});

  @override
  Widget build(BuildContext context) {
    final r = record;
    final c = currency;
    String km(double v, bool known) => known ? "${v.toStringAsFixed(1)} كم" : "≈ ${v.toStringAsFixed(1)} كم (مفترضة)";
    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.75,
      maxChildSize: 0.95,
      builder: (_, controller) => ListView(
        controller: controller,
        padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
        children: [
          Text(DateFormat('EEEE d MMMM • h:mm a', 'ar').format(r.firstSeen),
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
          const SizedBox(height: Insets.sm),
          KeyValueRow(label: "الحالة", value: RequestRecord.statusLabels[r.status] ?? r.status),
          KeyValueRow(label: "سعر الراكب", value: "${r.fare.toInt()} $c"),
          if (r.bidValue != null) KeyValueRow(label: "مزايدتك", value: "${r.bidValue} $c"),
          if (r.recommendedFare != null) KeyValueRow(label: "السعر العادل وقتها", value: "${r.recommendedFare!.toInt()} $c"),
          if (r.netProfit != null) KeyValueRow(label: "صافي الربح المتوقع", value: "${r.netProfit!.toStringAsFixed(1)} $c", emphasize: true),
          KeyValueRow(label: "مسافة الوصول", value: km(r.pickupKm, r.pickupKnown)),
          KeyValueRow(label: "مسافة المشوار", value: km(r.tripKm, r.tripKnown)),
          if (r.pickupAddress.isNotEmpty) KeyValueRow(label: "من", value: r.pickupAddress),
          if (r.dropoffAddress.isNotEmpty) KeyValueRow(label: "إلى", value: r.dropoffAddress),
          if (r.passengerName.isNotEmpty)
            KeyValueRow(label: "الراكب", value: "${r.passengerName}${r.passengerRating.isNotEmpty ? " (${r.passengerRating})" : ""}"),
          if (r.bidChips.isNotEmpty) KeyValueRow(label: "أزرار المزايدة", value: r.bidChips.join(" / ")),
          if (r.rating != null) ...[
            KeyValueRow(label: "تقييمك", value: "★" * r.rating!),
            if (r.ratingTags.isNotEmpty) KeyValueRow(label: "", value: r.ratingTags.join("، ")),
            if (r.ratingNote.isNotEmpty) KeyValueRow(label: "ملاحظة", value: r.ratingNote),
          ],
          const SizedBox(height: Insets.md),
          if (r.accepted)
            FilledButton.icon(
              icon: const Icon(Icons.star_rounded),
              label: Text(r.rating == null ? "قيّم الرحلة" : "عدّل التقييم"),
              onPressed: () => Navigator.pop(context, "rate"),
            ),
          if (r.snapshot.isNotEmpty) ...[
            const SectionLabel("اللقطة النصية لشاشة الطلب"),
            AppCard(
              padding: const EdgeInsets.all(Insets.md),
              child: SelectableText(
                r.snapshot.join("\n"),
                style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5, height: 1.5),
              ),
            ),
            TextButton.icon(
              icon: const Icon(Icons.copy_rounded, size: 18),
              label: const Text("نسخ اللقطة"),
              onPressed: () => Clipboard.setData(ClipboardData(text: r.snapshot.join("\n"))),
            ),
          ],
        ],
      ),
    );
  }
}
