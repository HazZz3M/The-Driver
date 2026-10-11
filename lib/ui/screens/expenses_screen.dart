import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../data/models/expense.dart';
import '../../data/models/vehicle_profile.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';

class ExpensesScreen extends StatefulWidget {
  const ExpensesScreen({super.key});

  @override
  State<ExpensesScreen> createState() => _ExpensesScreenState();
}

class _ExpensesScreenState extends State<ExpensesScreen> {
  String _selectedCategoryFilter = "ALL";

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final activeProfile = provider.activeProfile;
    final expenses = provider.allExpenses;

    final filteredExpenses = _selectedCategoryFilter == "ALL"
        ? expenses
        : expenses.where((e) => e.category == _selectedCategoryFilter).toList();

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Screen Header
            const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  "المركبات والمصاريف التشغيلية",
                  style: TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Text(
                  "تخصيص استهلاكات أسطول المركبات وحساب تكلفة الكيلومتر والوقود والزيوت",
                  style: TextStyle(color: AppColors.textSecondary, fontSize: 12),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // 1. Active Vehicle Profile Hub Card
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: AppColors.darkSurface,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: AppColors.neonGreen.withOpacity(0.5)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              color: AppColors.neonGreen.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Icon(Icons.directions_car, color: AppColors.neonGreen, size: 24),
                          ),
                          const SizedBox(width: 12),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                activeProfile?.name ?? "المركبة النشطة",
                                style: const TextStyle(
                                  color: AppColors.textPrimary,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 16,
                                ),
                              ),
                              Text(
                                "${activeProfile?.fuelType ?? 'بنزين 92'} • موديل ${activeProfile?.modelYear ?? ''}",
                                style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
                              ),
                            ],
                          ),
                        ],
                      ),
                      ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.darkSurfaceVariant,
                          foregroundColor: AppColors.neonGreen,
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                        icon: const Icon(Icons.add, size: 16),
                        label: const Text("+ مركبة", style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                        onPressed: () => _showAddVehicleDialog(context, provider),
                      ),
                    ],
                  ),

                  const SizedBox(height: 14),

                  const Text(
                    "التبديل السريع بين مركبات الأسطول:",
                    style: TextStyle(color: AppColors.textSecondary, fontSize: 11),
                  ),

                  const SizedBox(height: 6),

                  // Quick Switcher Chips Row
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: provider.vehicleProfiles.map((prof) {
                        final isCurrent = prof.id == provider.activeVehicleId;
                        return Padding(
                          padding: const EdgeInsets.only(left: 8),
                          child: ChoiceChip(
                            label: Text(prof.name),
                            selected: isCurrent,
                            selectedColor: AppColors.neonGreen,
                            backgroundColor: AppColors.darkSurfaceVariant,
                            labelStyle: TextStyle(
                              color: isCurrent ? AppColors.darkBackground : AppColors.textPrimary,
                              fontSize: 11.5,
                              fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
                            ),
                            onSelected: (_) => provider.switchActiveVehicle(prof),
                          ),
                        );
                      }).toList(),
                    ),
                  ),

                  const SizedBox(height: 14),

                  // Metrics Grid
                  Row(
                    children: [
                      Expanded(
                        child: _buildMetricMini(
                          label: "الاستهلاك المعتمد",
                          value: "${activeProfile?.fuelConsumptionLPer100Km ?? 8.5}L/100كم",
                          color: AppColors.accentCyan,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: _buildMetricMini(
                          label: "سعر اللتر الحالي",
                          value: "${activeProfile?.fuelPricePerLiter ?? 15.25} ج.م",
                          color: AppColors.neonGreen,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: _buildMetricMini(
                          label: "معدل الاستهلاك",
                          value: "${activeProfile?.wearAndTearPerKm ?? 1.2} ج/كم",
                          color: AppColors.accentPurple,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // 2. Action Buttons (Fuel, Oil, Spare Part)
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.darkSurfaceVariant,
                      foregroundColor: AppColors.neonGreen,
                      side: const BorderSide(color: AppColors.neonGreen, width: 1.2),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    icon: const Icon(Icons.local_gas_station, size: 18),
                    label: const Text("تفويلة بنزين", style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                    onPressed: () => _showFuelDialog(context, provider),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.darkSurfaceVariant,
                      foregroundColor: AppColors.accentCyan,
                      side: const BorderSide(color: AppColors.accentCyan, width: 1.2),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    icon: const Icon(Icons.opacity, size: 18),
                    label: const Text("غيار زيت", style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                    onPressed: () => _showOilDialog(context, provider),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.darkSurfaceVariant,
                      foregroundColor: AppColors.accentPurple,
                      side: const BorderSide(color: AppColors.accentPurple, width: 1.2),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    icon: const Icon(Icons.build, size: 18),
                    label: const Text("قطعة غيار", style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                    onPressed: () => _showSparePartDialog(context, provider),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // 3. Category Filter Tabs
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _buildFilterTab("الكل", "ALL"),
                  _buildFilterTab("بنزين", "FUEL"),
                  _buildFilterTab("غيار زيت", "OIL_CHANGE"),
                  _buildFilterTab("قطع غيار وصيانة", "SPARE_PART"),
                ],
              ),
            ),

            const SizedBox(height: 12),

            // 4. Expenses List
            if (filteredExpenses.isEmpty)
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: AppColors.darkSurface,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.darkCardBorder),
                ),
                alignment: Alignment.center,
                child: const Text(
                  "لا توجد مصاريف مسجلة في هذا القسم",
                  style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
                ),
              )
            else
              ...filteredExpenses.map((exp) => _buildExpenseCard(context, exp, provider)),
          ],
        ),
      ),
    );
  }

  Widget _buildFilterTab(String label, String category) {
    final isSelected = _selectedCategoryFilter == category;
    return Padding(
      padding: const EdgeInsets.only(left: 8),
      child: FilterChip(
        label: Text(label),
        selected: isSelected,
        selectedColor: AppColors.neonGreen,
        backgroundColor: AppColors.darkSurface,
        labelStyle: TextStyle(
          color: isSelected ? AppColors.darkBackground : AppColors.textPrimary,
          fontSize: 11.5,
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
        ),
        onSelected: (_) => setState(() => _selectedCategoryFilter = category),
      ),
    );
  }

  Widget _buildMetricMini({required String label, required String value, required Color color}) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.darkSurfaceVariant,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: AppColors.textSecondary, fontSize: 10)),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 12),
          ),
        ],
      ),
    );
  }

  Widget _buildExpenseCard(BuildContext context, Expense exp, AssistantProvider provider) {
    Color badgeColor;
    String badgeTitle;
    IconData icon;

    switch (exp.category) {
      case "FUEL":
        badgeColor = AppColors.neonGreen;
        badgeTitle = "تفويلة بنزين";
        icon = Icons.local_gas_station;
        break;
      case "OIL_CHANGE":
        badgeColor = AppColors.accentCyan;
        badgeTitle = "غيار زيت";
        icon = Icons.opacity;
        break;
      case "SPARE_PART":
        badgeColor = AppColors.accentPurple;
        badgeTitle = "صيانة / قطع غيار";
        icon = Icons.build;
        break;
      default:
        badgeColor = AppColors.accentAmber;
        badgeTitle = "مصروف تشغيلي";
        icon = Icons.receipt_long;
    }

    final dateStr = DateFormat('yyyy/MM/dd - hh:mm a')
        .format(DateTime.fromMillisecondsSinceEpoch(exp.timestamp));

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.darkSurface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.darkCardBorder),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: badgeColor.withOpacity(0.18),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          Icon(icon, size: 14, color: badgeColor),
                          const SizedBox(width: 4),
                          Text(
                            badgeTitle,
                            style: TextStyle(color: badgeColor, fontWeight: FontWeight.bold, fontSize: 11),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      dateStr,
                      style: const TextStyle(color: AppColors.textTertiary, fontSize: 11),
                    ),
                  ],
                ),
                Row(
                  children: [
                    Text(
                      "${exp.amount.toStringAsFixed(1)} ج.م",
                      style: TextStyle(color: badgeColor, fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, size: 18, color: AppColors.lossRed),
                      onPressed: () => provider.deleteExpense(exp.id),
                    ),
                  ],
                ),
              ],
            ),
            if (exp.description.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(
                exp.description,
                style: const TextStyle(color: AppColors.textPrimary, fontSize: 12.5),
              ),
            ],
            if (exp.consumptionLPer100Km != null && exp.consumptionLPer100Km! > 0) ...[
              const SizedBox(height: 6),
              Row(
                children: [
                  _buildSubBadge(
                    "استهلاك: ${exp.consumptionLPer100Km!.toStringAsFixed(1)}L/100كم",
                    AppColors.accentCyan,
                  ),
                  const SizedBox(width: 6),
                  if (exp.fuelCostPerKm != null)
                    _buildSubBadge(
                      "تكلفة: ${exp.fuelCostPerKm!.toStringAsFixed(2)} ج/كم",
                      AppColors.neonGreen,
                    ),
                ],
              ),
            ],
            if (exp.oilCostPerKm != null && exp.oilCostPerKm! > 0) ...[
              const SizedBox(height: 6),
              _buildSubBadge(
                "تكلفة الزيت: ${exp.oilCostPerKm!.toStringAsFixed(2)} ج/كم",
                AppColors.accentCyan,
              ),
            ],
            if (exp.partCostPerKm != null && exp.partCostPerKm! > 0) ...[
              const SizedBox(height: 6),
              _buildSubBadge(
                "استهلاك القطعة: ${exp.partCostPerKm!.toStringAsFixed(2)} ج/كم",
                AppColors.accentPurple,
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSubBadge(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(text, style: TextStyle(color: color, fontSize: 10.5, fontWeight: FontWeight.bold)),
    );
  }

  // Dialog: Add Fuel Refill
  void _showFuelDialog(BuildContext context, AssistantProvider provider) {
    final litersCtrl = TextEditingController(text: "30");
    final priceCtrl = TextEditingController(text: provider.driverSettings.fuelPricePerLiter.toString());
    final kmCtrl = TextEditingController(text: "350");

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text("تسجيل تفويلة بنزين", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: litersCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "عدد اللترات (Liters)"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: priceCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "سعر اللتر (EGP)"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: kmCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "الكيلومترات المقطوعة منذ آخر تفويلة"),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            child: const Text("إلغاء"),
            onPressed: () => Navigator.pop(ctx),
          ),
          ElevatedButton(
            child: const Text("حفظ وتحديث الاستهلاك"),
            onPressed: () {
              final liters = double.tryParse(litersCtrl.text) ?? 0.0;
              final price = double.tryParse(priceCtrl.text) ?? 0.0;
              final km = double.tryParse(kmCtrl.text) ?? 0.0;

              if (liters > 0 && price > 0) {
                provider.addFuelRefill(
                  liters: liters,
                  pricePerLiter: price,
                  kmSinceLastRefill: km,
                );
                Navigator.pop(ctx);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text("تم تسجيل التفويلة وتحديث معدل استهلاك السيارة تلقائياً")),
                );
              }
            },
          ),
        ],
      ),
    );
  }

  // Dialog: Add Engine Oil
  void _showOilDialog(BuildContext context, AssistantProvider provider) {
    final priceCtrl = TextEditingController(text: "350");
    final volumeCtrl = TextEditingController(text: "4000");
    final lifespanCtrl = TextEditingController(text: "5000");

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text("تسجيل غيار زيت موتور", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: priceCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "سعر لتر الزيت (EGP)"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: volumeCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "كمية الزيت بالملل (مثلاً 4000)"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: lifespanCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "عمر الزيت المقطوع بالكيلومتر (مثلاً 5000)"),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            child: const Text("إلغاء"),
            onPressed: () => Navigator.pop(ctx),
          ),
          ElevatedButton(
            child: const Text("حفظ الغيار"),
            onPressed: () {
              final price = double.tryParse(priceCtrl.text) ?? 0.0;
              final volume = int.tryParse(volumeCtrl.text) ?? 0;
              final lifespan = double.tryParse(lifespanCtrl.text) ?? 0.0;

              if (price > 0 && volume > 0) {
                provider.addOilChange(
                  oilPricePerLiter: price,
                  oilVolumeMl: volume,
                  oilLifespanKm: lifespan,
                );
                Navigator.pop(ctx);
              }
            },
          ),
        ],
      ),
    );
  }

  // Dialog: Add Spare Part
  void _showSparePartDialog(BuildContext context, AssistantProvider provider) {
    final nameCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    final lifespanCtrl = TextEditingController(text: "40000");
    bool updateWearTear = true;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDlgState) => AlertDialog(
          title: const Text("تسجيل قطعة غيار / صيانة", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: "اسم القطعة (تيل فرامل، كاوتش، سيور...)"),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: amountCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: "التكلفة الإجمالية (EGP)"),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: lifespanCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: "العمر الافتراضي بالكيلومتر"),
                ),
                const SizedBox(height: 10),
                CheckboxListTile(
                  title: const Text("إضافة تكلفة الكيلومتر لاستهلاك السيارة العام", style: TextStyle(fontSize: 12)),
                  value: updateWearTear,
                  onChanged: (v) => setDlgState(() => updateWearTear = v ?? true),
                  contentPadding: EdgeInsets.zero,
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              child: const Text("إلغاء"),
              onPressed: () => Navigator.pop(ctx),
            ),
            ElevatedButton(
              child: const Text("حفظ القطعة"),
              onPressed: () {
                final name = nameCtrl.text.trim();
                final amount = double.tryParse(amountCtrl.text) ?? 0.0;
                final lifespan = double.tryParse(lifespanCtrl.text);

                if (name.isNotEmpty && amount > 0) {
                  provider.addSparePart(
                    partName: name,
                    amount: amount,
                    lifespanKm: lifespan,
                    updateWearTear: updateWearTear,
                  );
                  Navigator.pop(ctx);
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  // Dialog: Add Vehicle Profile
  void _showAddVehicleDialog(BuildContext context, AssistantProvider provider) {
    final nameCtrl = TextEditingController();
    final yearCtrl = TextEditingController(text: "2022");
    final fuelTypeCtrl = TextEditingController(text: "بنزين 92");
    final consumptionCtrl = TextEditingController(text: "8.5");
    final fuelPriceCtrl = TextEditingController(text: "15.25");
    final wearCtrl = TextEditingController(text: "1.2");

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text("إضافة مركبة جديدة للأسطول", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: "اسم وموديل المركبة (مثلاً نيسان صني)"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: yearCtrl,
                decoration: const InputDecoration(labelText: "سنة الصنع"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: fuelTypeCtrl,
                decoration: const InputDecoration(labelText: "نوع الوقود (بنزين 92، غاز...)"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: consumptionCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "الاستهلاك (لتر/100كم)"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: fuelPriceCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "سعر لتر الوقود (EGP)"),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: wearCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: "معدل الاستهلاك والصيانة (ج/كم)"),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            child: const Text("إلغاء"),
            onPressed: () => Navigator.pop(ctx),
          ),
          ElevatedButton(
            child: const Text("إضافة وتفعيل"),
            onPressed: () {
              final name = nameCtrl.text.trim();
              if (name.isNotEmpty) {
                final prof = VehicleProfile(
                  name: name,
                  modelYear: yearCtrl.text.trim(),
                  fuelType: fuelTypeCtrl.text.trim(),
                  fuelConsumptionLPer100Km: double.tryParse(consumptionCtrl.text) ?? 8.5,
                  fuelPricePerLiter: double.tryParse(fuelPriceCtrl.text) ?? 15.25,
                  wearAndTearPerKm: double.tryParse(wearCtrl.text) ?? 1.2,
                );
                provider.addVehicleProfile(prof);
                Navigator.pop(ctx);
              }
            },
          ),
        ],
      ),
    );
  }
}
