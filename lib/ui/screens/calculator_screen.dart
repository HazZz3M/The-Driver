import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../components/cost_item_row.dart';
import '../components/profit_status_banner.dart';
import '../components/quick_input_slider.dart';
import '../components/smart_bid_card.dart';

class CalculatorScreen extends StatefulWidget {
  const CalculatorScreen({super.key});

  @override
  State<CalculatorScreen> createState() => _CalculatorScreenState();
}

class _CalculatorScreenState extends State<CalculatorScreen> {
  double? _selectedBidFare;
  final TextEditingController _zoneController = TextEditingController();

  @override
  void dispose() {
    _zoneController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final settings = provider.driverSettings;
    final analysis = provider.fareAnalysis;
    final activeVehicle = provider.activeProfile;

    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header & Shift Status
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        "محلل عروض إن درايف والتسعير",
                        style: TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        "تقييم العرض وحساب الأرباح في ثوانٍ",
                        style: TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: provider.isShiftActive
                          ? AppColors.neonGreen.withOpacity(0.15)
                          : AppColors.darkSurfaceVariant,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: provider.isShiftActive
                            ? AppColors.neonGreen
                            : AppColors.darkCardBorder,
                      ),
                    ),
                    child: Row(
                      children: [
                        Container(
                          width: 8,
                          height: 8,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: provider.isShiftActive
                                ? AppColors.neonGreen
                                : AppColors.textSecondary,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          provider.isShiftActive ? "الوردية جارية" : "وردية متوقفة",
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: provider.isShiftActive
                                ? AppColors.neonGreen
                                : AppColors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 12),

              // Active Vehicle Quick Switcher Strip
              if (activeVehicle != null) ...[
                PopupMenuButton<String>(
                  onSelected: (id) {
                    final target = provider.vehicleProfiles.firstWhere((v) => v.id == id);
                    provider.switchActiveVehicle(target);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text("تم التبديل إلى '${target.name}'")),
                    );
                  },
                  itemBuilder: (context) {
                    return provider.vehicleProfiles.map((p) {
                      final isCurrent = p.id == provider.activeVehicleId;
                      return PopupMenuItem(
                        value: p.id,
                        child: Text(
                          "${p.name} (${p.fuelType})",
                          style: TextStyle(
                            color: isCurrent ? AppColors.neonGreen : AppColors.textPrimary,
                            fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
                          ),
                        ),
                      );
                    }).toList();
                  },
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                    decoration: BoxDecoration(
                      color: AppColors.darkSurface,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppColors.darkCardBorder),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.directions_car, color: AppColors.neonGreen, size: 18),
                            const SizedBox(width: 8),
                            Text(
                              "المركبة النشطة: ${activeVehicle.name}",
                              style: const TextStyle(
                                color: AppColors.textPrimary,
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(width: 6),
                            Text(
                              "(${activeVehicle.fuelConsumptionLPer100Km}L / ${activeVehicle.fuelPricePerLiter} ج.م)",
                              style: const TextStyle(color: AppColors.textSecondary, fontSize: 11),
                            ),
                          ],
                        ),
                        const Icon(Icons.arrow_drop_down, color: AppColors.textSecondary),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
              ],

              // 1. Live Profitability Evaluation Banner
              ProfitStatusBanner(analysis: analysis, currency: settings.currency),

              const SizedBox(height: 16),

              // 2. Sliders & Fast Inputs
              const Text(
                "تفاصيل الطلب المعروض",
                style: TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                ),
              ),

              const SizedBox(height: 8),

              QuickInputSlider(
                title: "مسافة الوصول للراكب (Pickup)",
                value: provider.pickupDistanceKm,
                unit: "كم",
                min: 0.2,
                max: 15.0,
                step: 0.5,
                onValueChange: (v) => provider.setPickupDistance(v),
              ),

              const SizedBox(height: 10),

              QuickInputSlider(
                title: "مسافة الرحلة (Trip Distance)",
                value: provider.tripDistanceKm,
                unit: "كم",
                min: 1.0,
                max: 60.0,
                step: 1.0,
                onValueChange: (v) => provider.setTripDistance(v),
              ),

              const SizedBox(height: 10),

              QuickInputSlider(
                title: "عرض الراكب الحالي (Passenger Offer)",
                value: provider.offeredFare,
                unit: settings.currency,
                min: 10.0,
                max: 500.0,
                step: 5.0,
                onValueChange: (v) {
                  provider.setOfferedFare(v);
                  setState(() => _selectedBidFare = null);
                },
              ),

              const SizedBox(height: 8),

              // Fast Fare Delta Increments (-10, -5, +5, +10, +15, +20)
              Row(
                children: [-10.0, -5.0, 5.0, 10.0, 15.0, 20.0].map((delta) {
                  final label = delta > 0 ? "+${delta.toInt()}" : "${delta.toInt()}";
                  final isPositive = delta > 0;
                  return Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 3),
                      child: InkWell(
                        onTap: () {
                          final next = (provider.offeredFare + delta).clamp(10.0, 600.0);
                          provider.setOfferedFare(next);
                          setState(() => _selectedBidFare = null);
                        },
                        borderRadius: BorderRadius.circular(8),
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          decoration: BoxDecoration(
                            color: AppColors.darkSurfaceVariant,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: AppColors.darkCardBorder),
                          ),
                          alignment: Alignment.center,
                          child: Text(
                            "$label ج",
                            style: TextStyle(
                              color: isPositive ? AppColors.neonGreen : AppColors.lossRed,
                              fontSize: 10.5,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),

              const SizedBox(height: 14),

              // 2.5 Quick Zone Evaluator & Tagger with (!) badge
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.darkSurface,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.darkCardBorder),
                ),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Container(
                              width: 20,
                              height: 20,
                              decoration: const BoxDecoration(
                                shape: BoxShape.circle,
                                color: AppColors.accentAmber,
                              ),
                              alignment: Alignment.center,
                              child: const Text(
                                "!",
                                style: TextStyle(
                                  color: AppColors.darkBackground,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            const Text(
                              "فحص وتصنيف المنطقة (!)",
                              style: TextStyle(
                                color: AppColors.textPrimary,
                                fontWeight: FontWeight.bold,
                                fontSize: 13,
                              ),
                            ),
                          ],
                        ),
                        const Text(
                          "اضغط (!) لإضافة المنطقة",
                          style: TextStyle(color: AppColors.textSecondary, fontSize: 10.5),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _zoneController,
                            style: const TextStyle(color: AppColors.textPrimary, fontSize: 13),
                            decoration: const InputDecoration(
                              hintText: "اكتب اسم منطقة لفحصها أو تصنيفها...",
                              isDense: true,
                            ),
                            onChanged: (_) => setState(() {}),
                          ),
                        ),
                        if (_zoneController.text.trim().isNotEmpty) ...[
                          const SizedBox(width: 8),
                          ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: settings.isFavorite(_zoneController.text)
                                  ? const Color(0xFF475569)
                                  : AppColors.accentAmber,
                              foregroundColor: AppColors.darkBackground,
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                            ),
                            onPressed: () async {
                              final text = _zoneController.text.trim();
                              if (settings.isFavorite(text)) {
                                await provider.removeFavoriteZone(text);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text("تمت إزالة '$text' من المفضلة")),
                                );
                              } else {
                                await provider.addFavoriteZone(text);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text("تمت إضافة '$text' للمفضلة")),
                                );
                              }
                            },
                            child: Text(
                              settings.isFavorite(_zoneController.text) ? "مفضلة" : "+ مفضلة",
                              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
                            ),
                          ),
                          const SizedBox(width: 6),
                          ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: settings.isBlacklisted(_zoneController.text)
                                  ? const Color(0xFF475569)
                                  : AppColors.lossRed,
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                            ),
                            onPressed: () async {
                              final text = _zoneController.text.trim();
                              if (settings.isBlacklisted(text)) {
                                await provider.removeBlacklistZone(text);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text("تم إلغاء حظر '$text'")),
                                );
                              } else {
                                await provider.addBlacklistZone(text);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text("تمت إضافة '$text' للمحظورة")),
                                );
                              }
                            },
                            child: Text(
                              settings.isBlacklisted(_zoneController.text) ? "محظورة" : "+ محظورة",
                              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 16),

              // 3. Operational Costs Breakdown Card
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.darkSurface,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.darkCardBorder),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "تفاصيل التكاليف التشغيلية (Total: ${analysis.totalDistanceKm.toStringAsFixed(1)} كم)",
                      style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.bold,
                        fontSize: 13.5,
                      ),
                    ),
                    const SizedBox(height: 12),
                    CostItemRow(
                      label: "استهلاك البنزين (${analysis.fuelLitersUsed.toStringAsFixed(2)} لتر)",
                      amount: "${analysis.fuelCost.toStringAsFixed(1)} ${settings.currency}",
                    ),
                    CostItemRow(
                      label: "عمولة إن درايف (${settings.inDriveCommissionPercent}%)",
                      amount: "${analysis.commissionCost.toStringAsFixed(1)} ${settings.currency}",
                    ),
                    CostItemRow(
                      label: "استهلاك وصيانة السيارة",
                      amount: "${analysis.wearAndTearCost.toStringAsFixed(1)} ${settings.currency}",
                    ),
                    const Divider(color: AppColors.darkCardBorder, height: 20),
                    CostItemRow(
                      label: "إجمالي التكلفة الحقيقية:",
                      amount: "${analysis.totalOperatingCost.toStringAsFixed(1)} ${settings.currency}",
                      isBold: true,
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 16),

              // 4. Smart Counter-Bids Cards
              const Text(
                "اقتراحات المزايدة الذكية (Counter-Bids)",
                style: TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                ),
              ),

              const SizedBox(height: 10),

              ...analysis.smartBids.map((bid) {
                final isSelected = _selectedBidFare == bid.suggestedFare;
                return Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: SmartBidCard(
                    bid: bid,
                    currency: settings.currency,
                    isSelected: isSelected,
                    onSelect: () {
                      setState(() => _selectedBidFare = bid.suggestedFare);
                      provider.setOfferedFare(bid.suggestedFare);
                    },
                  ),
                );
              }),

              const SizedBox(height: 14),

              // 5. Accept & Save Current Ride Button
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  icon: const Icon(Icons.check, size: 20),
                  label: Text(
                    "حفظ الرحلة في السجل (${(_selectedBidFare ?? provider.offeredFare).toInt()} ${settings.currency})",
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                  ),
                  onPressed: () async {
                    await provider.saveCurrentRide(_selectedBidFare);
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text("تم حفظ الرحلة بنجاح وإضافتها إلى إحصائيات الوردية!"),
                        backgroundColor: AppColors.neonGreenDark,
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
