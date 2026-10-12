import 'dart:math';
import '../data/models/driver_settings.dart';

enum ProfitabilityLevel {
  lossDanger, // Money-losing ride (costs > fare)
  poorMargin, // Below target minimum profit per km
  goodProfit, // Meets driver target profit
  excellentProfit // High-yield profitable ride
}

class SmartBidOption {
  final String title;
  final double suggestedFare;
  final double netProfit;
  final double profitPerKm;
  final String description;
  final String tag;

  const SmartBidOption({
    required this.title,
    required this.suggestedFare,
    required this.netProfit,
    required this.profitPerKm,
    required this.description,
    required this.tag,
  });
}

class FareAnalysisResult {
  final double pickupDistanceKm;
  final double tripDistanceKm;
  final double totalDistanceKm;
  final int estimatedMinutes;
  final double offeredFare;

  // Cost breakdown
  final double fuelCost;
  final double fuelLitersUsed;
  final double commissionCost;
  final double wearAndTearCost;
  final double totalOperatingCost;

  // Profit outcomes
  final double netProfit;
  final double profitMarginPercent;
  final double profitPerKm;
  final double profitPerHour;

  // Evaluation
  final ProfitabilityLevel profitabilityLevel;
  final String statusMessage;
  final String? warningNote;

  // Counter-Offer Recommendations
  final double breakEvenFare;
  final double recommendedFare;
  final double aggressiveFare;
  final List<SmartBidOption> smartBids;

  // Geographic Zone Analysis
  final bool isFavoriteZone;
  final String? matchedFavoriteZone;
  final bool isBlacklistedZone;
  final String? matchedBlacklistZone;

  const FareAnalysisResult({
    required this.pickupDistanceKm,
    required this.tripDistanceKm,
    required this.totalDistanceKm,
    required this.estimatedMinutes,
    required this.offeredFare,
    required this.fuelCost,
    required this.fuelLitersUsed,
    required this.commissionCost,
    required this.wearAndTearCost,
    required this.totalOperatingCost,
    required this.netProfit,
    required this.profitMarginPercent,
    required this.profitPerKm,
    required this.profitPerHour,
    required this.profitabilityLevel,
    required this.statusMessage,
    this.warningNote,
    required this.breakEvenFare,
    required this.recommendedFare,
    required this.aggressiveFare,
    required this.smartBids,
    this.isFavoriteZone = false,
    this.matchedFavoriteZone,
    this.isBlacklistedZone = false,
    this.matchedBlacklistZone,
  });
}

class FareCalculatorEngine {
  /// City driving estimate; must match MINUTES_PER_KM in FareCalculatorEngine.kt.
  static const double minutesPerKm = 2.5;

  static final RegExp _alefVariants = RegExp(r'[أإآ]');
  static final RegExp _punctOrSpace = RegExp(r'[\p{P}\s]+', unicode: true);

  static int estimateMinutes(double totalDistanceKm) =>
      (totalDistanceKm * minutesPerKm).toInt().clamp(5, 300);

  /// Normalizes Arabic characters and trims punctuation for robust string matching
  static String normalizeArabicText(String str) {
    return str
        .toLowerCase()
        .replaceAll(_alefVariants, 'ا')
        .replaceAll('ى', 'ي')
        .replaceAll('ة', 'ه')
        .replaceAll(_punctOrSpace, ' ')
        .trim();
  }

  /// Returns the first zone whose normalized name appears in [address], or null.
  static String? matchZone(String? address, List<String> zones) {
    if (address == null || address.trim().isEmpty) return null;
    final normAddress = normalizeArabicText(address);
    for (final z in zones) {
      final normZ = normalizeArabicText(z);
      if (normZ.isNotEmpty && normAddress.contains(normZ)) return z;
    }
    return null;
  }

  /// Evaluates fare, operating expenses, profitability, and generates 3 intelligent counter-bids
  static FareAnalysisResult analyze({
    required double pickupDistanceKm,
    required double tripDistanceKm,
    required int estimatedMinutes,
    required double offeredFare,
    required DriverSettings settings,
    String pickupAddress = "",
    String dropoffAddress = "",
  }) {
    final totalDistanceKm = max(0.1, pickupDistanceKm + tripDistanceKm);
    final validMinutes = max(1, estimatedMinutes);

    // 1. Calculate Fuel Usage & Cost
    final fuelLitersUsed = totalDistanceKm * (settings.fuelConsumptionLPer100Km / 100.0);
    final fuelCost = fuelLitersUsed * settings.fuelPricePerLiter;

    // 2. inDrive Platform Commission (~10%)
    final commissionRate = settings.inDriveCommissionPercent / 100.0;
    final commissionCost = offeredFare * commissionRate;

    // 3. Wear & Tear (Tires, Motor Oil, Depreciation)
    final wearAndTearCost = totalDistanceKm * settings.wearAndTearPerKm;

    // 4. Operating Costs & Net Profit
    final totalOperatingCost = fuelCost + commissionCost + wearAndTearCost;
    final netProfit = offeredFare - totalOperatingCost;
    final profitMarginPercent = offeredFare > 0 ? (netProfit / offeredFare) * 100.0 : 0.0;
    final profitPerKm = netProfit / totalDistanceKm;
    final profitPerHour = (netProfit / validMinutes) * 60.0;

    // 5. Calculate Smart Counter-Bids
    // Formula: NeededGross = (FuelCost + WearCost + DesiredProfit) / (1 - CommissionRate)
    final nonCommissionCosts = fuelCost + wearAndTearCost;
    final netDivisor = max(0.01, 1.0 - commissionRate);

    // Break Even Floor
    final rawBreakEven = nonCommissionCosts / netDivisor;
    final breakEvenFare = roundToSmartStep(rawBreakEven);

    // Recommended Sweet Spot (Yields target profit per km)
    final targetProfit = totalDistanceKm * settings.minProfitPerKm;
    final rawRecommended = (nonCommissionCosts + targetProfit) / netDivisor;
    final recommendedFare = max(breakEvenFare + 10.0, roundToSmartStep(rawRecommended));

    // Aggressive Bid (Target profit * 1.6)
    final rawAggressive = (nonCommissionCosts + (targetProfit * 1.6)) / netDivisor;
    final aggressiveFare = max(recommendedFare + 15.0, roundToSmartStep(rawAggressive));

    // 6. Geographic Zone Analysis
    final combinedAddress = "$pickupAddress $dropoffAddress";
    final matchedFavorite = matchZone(combinedAddress, settings.favoriteZones);
    final matchedBlacklist = matchZone(combinedAddress, settings.blacklistedZones);

    // 7. Evaluate Profitability
    final evaluation = _evaluateProfitability(
      netProfit: netProfit,
      profitPerKm: profitPerKm,
      minTargetProfitPerKm: settings.minProfitPerKm,
      totalOperatingCost: totalOperatingCost,
      offeredFare: offeredFare,
    );

    ProfitabilityLevel level = evaluation.$1;
    String statusMessage = evaluation.$2;
    String? warningNote = evaluation.$3;

    // Apply Zone Business Rules
    if (matchedBlacklist != null) {
      if (offeredFare >= aggressiveFare || profitPerKm >= settings.minProfitPerKm * 1.4) {
        level = ProfitabilityLevel.goodProfit;
        statusMessage = "مقبول فقط لارتفاع السعر الاستثنائي";
        warningNote = "تنبيه: الرحلة تمر بمنطقة غير مرغوبة ($matchedBlacklist). القبول مشروط بالسعر المرتفع.";
      } else {
        level = ProfitabilityLevel.lossDanger;
        statusMessage = "مرفوض - منطقة غير مرغوبة ($matchedBlacklist)";
        warningNote = "الرحلة تمر بمنطقة غير مرغوبة ($matchedBlacklist)، والعرض المالي لا يبرر التوجه إليها.";
      }
    } else if (matchedFavorite != null) {
      if (level == ProfitabilityLevel.goodProfit || level == ProfitabilityLevel.excellentProfit) {
        statusMessage = "قبول فوري - منطقة مفضلة ($matchedFavorite)";
      } else if (level == ProfitabilityLevel.poorMargin) {
        statusMessage = "منطقة مفضلة ($matchedFavorite) - ارفع السعر قليلاً";
      }
    }

    // Create Smart Bid Cards
    final smartBids = [
      SmartBidOption(
        title: "الحد الأدنى (التعادل)",
        suggestedFare: breakEvenFare,
        netProfit: max(0.0, breakEvenFare - (breakEvenFare * commissionRate) - nonCommissionCosts),
        profitPerKm: max(0.0, (breakEvenFare - (breakEvenFare * commissionRate) - nonCommissionCosts) / totalDistanceKm),
        description: "أقل عرض لتفادي الخسارة، يغطي البنزين والعمولة والاستهلاك بالكامل.",
        tag: "MINIMUM",
      ),
      SmartBidOption(
        title: "العرض الموصى به",
        suggestedFare: recommendedFare,
        netProfit: recommendedFare - (recommendedFare * commissionRate) - nonCommissionCosts,
        profitPerKm: (recommendedFare - (recommendedFare * commissionRate) - nonCommissionCosts) / totalDistanceKm,
        description: "العرض المتوازن: أعلى نسبة قبول من الراكب مع تحقيق هامش ربح مستهدف.",
        tag: "OPTIMAL",
      ),
      SmartBidOption(
        title: "عرض الذروة",
        suggestedFare: aggressiveFare,
        netProfit: aggressiveFare - (aggressiveFare * commissionRate) - nonCommissionCosts,
        profitPerKm: (aggressiveFare - (aggressiveFare * commissionRate) - nonCommissionCosts) / totalDistanceKm,
        description: "عرض الذروة أو الزحام الشديد والمسافات الطويلة مع طريق عودة فارغ.",
        tag: "HIGH MARGIN",
      ),
    ];

    return FareAnalysisResult(
      pickupDistanceKm: pickupDistanceKm,
      tripDistanceKm: tripDistanceKm,
      totalDistanceKm: totalDistanceKm,
      estimatedMinutes: validMinutes,
      offeredFare: offeredFare,
      fuelCost: fuelCost,
      fuelLitersUsed: fuelLitersUsed,
      commissionCost: commissionCost,
      wearAndTearCost: wearAndTearCost,
      totalOperatingCost: totalOperatingCost,
      netProfit: netProfit,
      profitMarginPercent: profitMarginPercent,
      profitPerKm: profitPerKm,
      profitPerHour: profitPerHour,
      profitabilityLevel: level,
      statusMessage: statusMessage,
      warningNote: warningNote,
      breakEvenFare: breakEvenFare,
      recommendedFare: recommendedFare,
      aggressiveFare: aggressiveFare,
      smartBids: smartBids,
      isFavoriteZone: matchedFavorite != null,
      matchedFavoriteZone: matchedFavorite,
      isBlacklistedZone: matchedBlacklist != null,
      matchedBlacklistZone: matchedBlacklist,
    );
  }

  static (ProfitabilityLevel, String, String?) _evaluateProfitability({
    required double netProfit,
    required double profitPerKm,
    required double minTargetProfitPerKm,
    required double totalOperatingCost,
    required double offeredFare,
  }) {
    if (netProfit < 0) {
      return (
        ProfitabilityLevel.lossDanger,
        "رحلة خاسرة - التكلفة التشغيلية تتجاوز العرض",
        "تكلفة الوقود والاستهلاك والعمولة (${totalOperatingCost.toStringAsFixed(1)} ج.م) تتجاوز عرض الراكب (${offeredFare.toStringAsFixed(1)} ج.م). يوصى بالمزايدة لرفع السعر أو رفض الطلب."
      );
    }
    if (profitPerKm < minTargetProfitPerKm) {
      return (
        ProfitabilityLevel.poorMargin,
        "هامش ربح منخفض دون المستهدف",
        "صافي العائد أقل من الحد الأدنى المطلوب (${minTargetProfitPerKm.toStringAsFixed(1)} ج.م/كم). يفضل المزايدة لرفع السعر."
      );
    }
    if (profitPerKm >= minTargetProfitPerKm * 1.5) {
      return (
        ProfitabilityLevel.excellentProfit,
        "عائد ممتاز وعالي الربحية",
        null,
      );
    }
    return (
      ProfitabilityLevel.goodProfit,
      "عرض رابح ومجدٍ تشغيلياً",
      null,
    );
  }

  /// Rounds fares to realistic Egyptian street ride-hailing steps (e.g. 5 or 10 EGP)
  static double roundToSmartStep(double fare, {double step = 5.0}) {
    return (fare / step).ceil() * step;
  }
}
