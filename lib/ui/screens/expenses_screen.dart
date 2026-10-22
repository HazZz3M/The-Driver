import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../data/models/expense.dart';
import '../../data/models/vehicle_profile.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import '../components/dialogs.dart';
import '../components/ui_kit.dart';

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
    final settings = provider.driverSettings;
    // Each vehicle keeps its own log; switching the vehicle switches the list.
    final expenses = provider.vehicleExpenses;

    final filteredExpenses = _selectedCategoryFilter == "ALL"
        ? expenses
        : expenses.where((e) => e.category == _selectedCategoryFilter).toList();

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
          children: [
            const PageHeader(
              title: "المركبة والمصاريف",
              subtitle: "تكلفة الكيلومتر الحقيقية من البنزين والزيت وقطع الغيار",
            ),

            // 1. Active Vehicle Card
            AppCard(
              borderColor: AppColors.emeraldPrimary.withValues(alpha: 0.4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
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
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              activeProfile?.name ?? "المركبة النشطة",
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 16),
                            ),
                            Text(
                              "${activeProfile?.fuelType ?? ''} • موديل ${activeProfile?.modelYear ?? ''}",
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
                            ),
                          ],
                        ),
                      ),
                      if (activeProfile != null)
                        IconButton(
                          tooltip: "تعديل المركبة",
                          icon: const Icon(Icons.edit, size: 20, color: AppColors.textSecondary),
                          onPressed: () => _showVehicleDialog(context, provider, existing: activeProfile),
                        ),
                      IconButton(
                        tooltip: "إضافة مركبة",
                        icon: const Icon(Icons.add_circle, size: 24, color: AppColors.neonGreen),
                        onPressed: () => _showVehicleDialog(context, provider),
                      ),
                    ],
                  ),

                  const SizedBox(height: 14),

                  const Text(
                    "التبديل بين المركبات (اضغط مطولاً للتعديل أو الحذف):",
                    style: TextStyle(color: AppColors.textSecondary, fontSize: 12),
                  ),

                  const SizedBox(height: 6),

                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: provider.vehicleProfiles.map((prof) {
                        final isCurrent = prof.id == provider.activeVehicleId;
                        return Padding(
                          padding: const EdgeInsetsDirectional.only(end: 8),
                          child: GestureDetector(
                            onLongPress: () => _showVehicleActions(context, provider, prof),
                            child: ChoiceChip(
                              label: Text(prof.name),
                              selected: isCurrent,
                              selectedColor: AppColors.neonGreen,
                              backgroundColor: AppColors.darkSurfaceVariant,
                              labelStyle: TextStyle(
                                color: isCurrent ? AppColors.darkBackground : AppColors.textPrimary,
                                fontSize: 12,
                                fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
                              ),
                              onSelected: (_) => provider.switchActiveVehicle(prof),
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                  ),

                  const SizedBox(height: 14),

                  // Values actually used by the pricing engine (kept in sync with the vehicle)
                  Row(
                    children: [
                      Expanded(
                        child: _buildMetricMini(
                          label: "الاستهلاك",
                          value: "${formatNumber(settings.fuelConsumptionLPer100Km, maxDecimals: 1)} ل/100كم",
                          color: AppColors.accentCyan,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: _buildMetricMini(
                          label: "سعر اللتر",
                          value: "${formatNumber(settings.fuelPricePerLiter)} ج.م",
                          color: AppColors.neonGreen,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: _buildMetricMini(
                          label: "الاستهلاك والصيانة",
                          value: "${formatNumber(settings.wearAndTearPerKm)} ج.م/كم",
                          color: AppColors.accentPurple,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            // 2. Action Buttons
            SectionLabel("تسجيل مصروف لـ ${activeProfile?.name ?? 'المركبة'}"),
            Row(
              children: [
                Expanded(child: _actionButton("تفويلة", Icons.local_gas_station, AppColors.neonGreen, () => _showFuelDialog(context, provider))),
                const SizedBox(width: 8),
                Expanded(child: _actionButton("غيار زيت", Icons.opacity, AppColors.accentCyan, () => _showOilDialog(context, provider))),
                const SizedBox(width: 8),
                Expanded(child: _actionButton("قطعة غيار", Icons.build, AppColors.accentPurple, () => _showSparePartDialog(context, provider))),
              ],
            ),

            // 3. Category Filter
            SectionLabel("سجل ${activeProfile?.name ?? 'المركبة'}"),
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

            if (filteredExpenses.isEmpty)
              const EmptyState(
                icon: Icons.receipt_long_outlined,
                title: "لا توجد مصاريف هنا",
                message: "سجّل التفويلة أو غيار الزيت لهذه المركبة لحساب تكلفة الكيلومتر الحقيقية",
              )
            else
              ...filteredExpenses.map((exp) => _buildExpenseCard(context, exp, provider)),
          ],
        ),
      ),
    );
  }

  Widget _actionButton(String label, IconData icon, Color color, VoidCallback onPressed) {
    return ElevatedButton.icon(
      style: ElevatedButton.styleFrom(
        backgroundColor: AppColors.darkSurfaceVariant,
        foregroundColor: color,
        side: BorderSide(color: color, width: 1.2),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 6),
      ),
      icon: Icon(icon, size: 18),
      label: FittedBox(
        fit: BoxFit.scaleDown,
        child: Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
      ),
      onPressed: onPressed,
    );
  }

  Widget _buildFilterTab(String label, String category) {
    final isSelected = _selectedCategoryFilter == category;
    return Padding(
      padding: const EdgeInsetsDirectional.only(end: 8),
      child: FilterChip(
        label: Text(label),
        selected: isSelected,
        selectedColor: AppColors.neonGreen,
        backgroundColor: AppColors.darkSurface,
        labelStyle: TextStyle(
          color: isSelected ? AppColors.darkBackground : AppColors.textPrimary,
          fontSize: 12,
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
          Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)),
          const SizedBox(height: 2),
          FittedBox(
            fit: BoxFit.scaleDown,
            child: Text(value, style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 12.5)),
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
        badgeTitle = "تفويلة";
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

    final dateStr = DateFormat('yyyy/MM/dd - hh:mm a').format(DateTime.fromMillisecondsSinceEpoch(exp.timestamp));

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: AppCard(
        padding: const EdgeInsets.fromLTRB(14, 10, 6, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
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
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(icon, size: 14, color: badgeColor),
                      const SizedBox(width: 4),
                      Text(badgeTitle, style: TextStyle(color: badgeColor, fontWeight: FontWeight.bold, fontSize: 12)),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    dateStr,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: AppColors.textTertiary, fontSize: 12),
                  ),
                ),
                Text(
                  "${exp.amount.toStringAsFixed(1)} ج.م",
                  style: TextStyle(color: badgeColor, fontWeight: FontWeight.bold, fontSize: 14),
                ),
                IconButton(
                  tooltip: "حذف",
                  icon: const Icon(Icons.delete_outline, size: 20, color: AppColors.lossRed),
                  onPressed: () async {
                    await provider.deleteExpense(exp);
                    if (context.mounted) {
                      showUndoSnackBar(context, "تم حذف المصروف", () => provider.restoreExpense(exp));
                    }
                  },
                ),
              ],
            ),
            if (exp.description.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(exp.description, style: const TextStyle(color: AppColors.textPrimary, fontSize: 12.5)),
            ],
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                if (exp.consumptionLPer100Km != null && exp.consumptionLPer100Km! > 0)
                  _buildSubBadge("استهلاك: ${exp.consumptionLPer100Km!.toStringAsFixed(1)} ل/100كم", AppColors.accentCyan),
                if (exp.fuelCostPerKm != null && exp.fuelCostPerKm! > 0)
                  _buildSubBadge("تكلفة: ${exp.fuelCostPerKm!.toStringAsFixed(2)} ج.م/كم", AppColors.neonGreen),
                if (exp.oilCostPerKm != null && exp.oilCostPerKm! > 0)
                  _buildSubBadge("تكلفة الزيت: ${exp.oilCostPerKm!.toStringAsFixed(2)} ج.م/كم", AppColors.accentCyan),
                if (exp.partCostPerKm != null && exp.partCostPerKm! > 0)
                  _buildSubBadge("استهلاك القطعة: ${exp.partCostPerKm!.toStringAsFixed(2)} ج.م/كم", AppColors.accentPurple),
                if (exp.appliedWearPerKm != null && exp.appliedWearPerKm! > 0)
                  _buildSubBadge("مضاف لمعدل الاستهلاك", AppColors.accentAmber),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSubBadge(String text, Color color) {
    return Container(
      margin: const EdgeInsets.only(top: 6),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(text, style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.bold)),
    );
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  // ---------------------------------------------------------------------------
  // Dialog: Fuel Refill
  // ---------------------------------------------------------------------------
  void _showFuelDialog(BuildContext context, AssistantProvider provider) {
    final litersCtrl = TextEditingController();
    final priceCtrl = TextEditingController(text: formatNumber(provider.driverSettings.fuelPricePerLiter));
    final kmCtrl = TextEditingController();
    String? litersError, priceError, kmError;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) => AlertDialog(
          title: const Text("تسجيل تفويلة", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                NumberField(controller: litersCtrl, label: "عدد اللترات", hint: "مثلاً 30", errorText: litersError),
                const SizedBox(height: 10),
                NumberField(controller: priceCtrl, label: "سعر اللتر (ج.م)", errorText: priceError),
                const SizedBox(height: 10),
                NumberField(
                  controller: kmCtrl,
                  label: "كم قطعت منذ آخر تفويلة (فل تانك)",
                  hint: "اتركه فارغاً إن لم تعرف",
                  errorText: kmError,
                ),
                const SizedBox(height: 8),
                const Text(
                  "لحساب الاستهلاك بدقة: املأ التانك بالكامل في كل مرة وسجّل الكيلومترات بين التفويلتين.",
                  style: TextStyle(color: AppColors.textSecondary, fontSize: 12, height: 1.4),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(child: const Text("إلغاء"), onPressed: () => Navigator.pop(ctx)),
            ElevatedButton(
              child: const Text("حفظ"),
              onPressed: () async {
                final liters = parseLocalizedDouble(litersCtrl.text);
                final price = parseLocalizedDouble(priceCtrl.text);
                final kmText = kmCtrl.text.trim();
                final km = kmText.isEmpty ? 0.0 : parseLocalizedDouble(kmText);
                setDlg(() {
                  litersError = (liters == null || liters <= 0 || liters > 200) ? "أدخل عدد لترات صحيح (1 - 200)" : null;
                  priceError = (price == null || price <= 0 || price > 500) ? "أدخل سعراً صحيحاً" : null;
                  kmError = (km == null || km < 0 || km > 3000) ? "أدخل مسافة صحيحة (0 - 3000)" : null;
                });
                if (litersError != null || priceError != null || kmError != null) return;

                Navigator.pop(ctx);
                final avg = await provider.addFuelRefill(liters: liters!, pricePerLiter: price!, kmSinceLastRefill: km!);
                _snack(avg != null
                    ? "تم التسجيل. الاستهلاك المعتمد الآن ${avg.toStringAsFixed(1)} لتر/100كم (متوسط آخر التفويلات)"
                    : "تم التسجيل وتحديث سعر اللتر");
              },
            ),
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Dialog: Engine Oil
  // ---------------------------------------------------------------------------
  void _showOilDialog(BuildContext context, AssistantProvider provider) {
    final priceCtrl = TextEditingController();
    final volumeCtrl = TextEditingController();
    final lifespanCtrl = TextEditingController();
    String? priceError, volumeError, lifespanError;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) => AlertDialog(
          title: const Text("تسجيل غيار زيت موتور", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                NumberField(controller: priceCtrl, label: "سعر لتر الزيت (ج.م)", hint: "مثلاً 350", errorText: priceError),
                const SizedBox(height: 10),
                NumberField(controller: volumeCtrl, label: "كمية الزيت بالملل", hint: "مثلاً 4000", errorText: volumeError),
                const SizedBox(height: 10),
                NumberField(controller: lifespanCtrl, label: "عمر الزيت بالكيلومتر", hint: "مثلاً 5000", errorText: lifespanError),
              ],
            ),
          ),
          actions: [
            TextButton(child: const Text("إلغاء"), onPressed: () => Navigator.pop(ctx)),
            ElevatedButton(
              child: const Text("حفظ"),
              onPressed: () async {
                final price = parseLocalizedDouble(priceCtrl.text);
                final volume = parseLocalizedInt(volumeCtrl.text);
                final lifespan = parseLocalizedDouble(lifespanCtrl.text);
                setDlg(() {
                  priceError = (price == null || price <= 0) ? "أدخل سعراً صحيحاً" : null;
                  volumeError = (volume == null || volume <= 0 || volume > 20000) ? "أدخل الكمية بالملل (مثلاً 4000)" : null;
                  lifespanError = (lifespan == null || lifespan <= 0) ? "أدخل عمر الزيت بالكيلومتر" : null;
                });
                if (priceError != null || volumeError != null || lifespanError != null) return;

                Navigator.pop(ctx);
                await provider.addOilChange(oilPricePerLiter: price!, oilVolumeMl: volume!, oilLifespanKm: lifespan!);
                _snack("تم تسجيل غيار الزيت");
              },
            ),
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Dialog: Spare Part
  // ---------------------------------------------------------------------------
  void _showSparePartDialog(BuildContext context, AssistantProvider provider) {
    final nameCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    final lifespanCtrl = TextEditingController();
    bool addToWear = false;
    String? nameError, amountError, lifespanError;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) => AlertDialog(
          title: const Text("تسجيل قطعة غيار / صيانة", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: InputDecoration(labelText: "اسم القطعة (تيل فرامل، كاوتش...)", errorText: nameError),
                ),
                const SizedBox(height: 10),
                NumberField(controller: amountCtrl, label: "التكلفة الإجمالية (ج.م)", errorText: amountError),
                const SizedBox(height: 10),
                NumberField(
                  controller: lifespanCtrl,
                  label: "العمر الافتراضي بالكيلومتر (اختياري)",
                  hint: "مثلاً 40000",
                  errorText: lifespanError,
                ),
                const SizedBox(height: 10),
                CheckboxListTile(
                  title: const Text("إضافة تكلفتها لكل كم إلى معدل الاستهلاك", style: TextStyle(fontSize: 12)),
                  subtitle: const Text(
                    "فعّلها فقط لقطع غير محسوبة في المعدل الحالي. حذف المصروف يلغي الإضافة.",
                    style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
                  ),
                  value: addToWear,
                  onChanged: (v) => setDlg(() => addToWear = v ?? false),
                  contentPadding: EdgeInsets.zero,
                ),
              ],
            ),
          ),
          actions: [
            TextButton(child: const Text("إلغاء"), onPressed: () => Navigator.pop(ctx)),
            ElevatedButton(
              child: const Text("حفظ"),
              onPressed: () async {
                final name = nameCtrl.text.trim();
                final amount = parseLocalizedDouble(amountCtrl.text);
                final lifespanText = lifespanCtrl.text.trim();
                final lifespan = lifespanText.isEmpty ? null : parseLocalizedDouble(lifespanText);
                setDlg(() {
                  nameError = name.isEmpty ? "اكتب اسم القطعة" : null;
                  amountError = (amount == null || amount <= 0) ? "أدخل التكلفة" : null;
                  lifespanError = (lifespanText.isNotEmpty && (lifespan == null || lifespan <= 0))
                      ? "أدخل رقماً صحيحاً أو اتركه فارغاً"
                      : (addToWear && lifespanText.isEmpty ? "مطلوب لحساب التكلفة لكل كم" : null);
                });
                if (nameError != null || amountError != null || lifespanError != null) return;

                Navigator.pop(ctx);
                await provider.addSparePart(partName: name, amount: amount!, lifespanKm: lifespan, addToWearRate: addToWear);
                _snack("تم تسجيل القطعة");
              },
            ),
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Vehicles: add / edit / delete
  // ---------------------------------------------------------------------------
  void _showVehicleActions(BuildContext context, AssistantProvider provider, VehicleProfile prof) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.darkSurface,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit, color: AppColors.neonGreen),
              title: Text("تعديل '${prof.name}'"),
              onTap: () {
                Navigator.pop(ctx);
                _showVehicleDialog(context, provider, existing: prof);
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete_outline, color: AppColors.lossRed),
              title: const Text("حذف المركبة"),
              enabled: provider.vehicleProfiles.length > 1,
              subtitle: provider.vehicleProfiles.length > 1 ? null : const Text("لا يمكن حذف آخر مركبة"),
              onTap: () async {
                Navigator.pop(ctx);
                final ok = await confirmAction(
                  context,
                  title: "حذف المركبة؟",
                  message: "سيتم حذف '${prof.name}' وكل مصاريفها المسجلة (تفويلات، زيت، قطع غيار). لا يمكن التراجع.",
                );
                if (ok) await provider.deleteVehicleProfile(prof);
              },
            ),
          ],
        ),
      ),
    );
  }

  void _showVehicleDialog(BuildContext context, AssistantProvider provider, {VehicleProfile? existing}) {
    final nameCtrl = TextEditingController(text: existing?.name ?? "");
    final yearCtrl = TextEditingController(text: existing?.modelYear ?? "");
    final fuelTypeCtrl = TextEditingController(text: existing?.fuelType ?? "بنزين 92");
    final consumptionCtrl = TextEditingController(text: existing != null ? formatNumber(existing.fuelConsumptionLPer100Km) : "");
    final fuelPriceCtrl = TextEditingController(
      text: formatNumber(existing?.fuelPricePerLiter ?? provider.driverSettings.fuelPricePerLiter),
    );
    final wearCtrl = TextEditingController(text: formatNumber(existing?.wearAndTearPerKm ?? 1.2));
    String? nameError, consumptionError, priceError, wearError;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) => AlertDialog(
          title: Text(existing == null ? "إضافة مركبة" : "تعديل المركبة",
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: InputDecoration(labelText: "اسم وموديل المركبة (مثلاً نيسان صني)", errorText: nameError),
                ),
                const SizedBox(height: 10),
                TextField(controller: yearCtrl, decoration: const InputDecoration(labelText: "سنة الصنع")),
                const SizedBox(height: 10),
                TextField(controller: fuelTypeCtrl, decoration: const InputDecoration(labelText: "نوع الوقود")),
                const SizedBox(height: 10),
                NumberField(controller: consumptionCtrl, label: "الاستهلاك (لتر/100كم)", hint: "مثلاً 8.5", errorText: consumptionError),
                const SizedBox(height: 10),
                NumberField(controller: fuelPriceCtrl, label: "سعر لتر الوقود (ج.م)", errorText: priceError),
                const SizedBox(height: 10),
                NumberField(controller: wearCtrl, label: "الاستهلاك والصيانة (ج.م/كم)", errorText: wearError),
              ],
            ),
          ),
          actions: [
            TextButton(child: const Text("إلغاء"), onPressed: () => Navigator.pop(ctx)),
            ElevatedButton(
              child: Text(existing == null ? "إضافة وتفعيل" : "حفظ"),
              onPressed: () async {
                final name = nameCtrl.text.trim();
                final consumption = parseLocalizedDouble(consumptionCtrl.text);
                final price = parseLocalizedDouble(fuelPriceCtrl.text);
                final wear = parseLocalizedDouble(wearCtrl.text);
                setDlg(() {
                  nameError = name.isEmpty ? "اكتب اسم المركبة" : null;
                  consumptionError = (consumption == null || consumption <= 0 || consumption > 40) ? "بين 1 و 40" : null;
                  priceError = (price == null || price <= 0 || price > 500) ? "أدخل سعراً صحيحاً" : null;
                  wearError = (wear == null || wear < 0 || wear > 50) ? "بين 0 و 50" : null;
                });
                if (nameError != null || consumptionError != null || priceError != null || wearError != null) return;

                final base = existing ?? VehicleProfile(name: name);
                final prof = base.copyWith(
                  name: name,
                  modelYear: yearCtrl.text.trim(),
                  fuelType: fuelTypeCtrl.text.trim(),
                  fuelConsumptionLPer100Km: consumption,
                  fuelPricePerLiter: price,
                  wearAndTearPerKm: wear,
                );
                Navigator.pop(ctx);
                if (existing == null) {
                  await provider.addVehicleProfile(prof);
                } else {
                  await provider.updateVehicleProfile(prof);
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
