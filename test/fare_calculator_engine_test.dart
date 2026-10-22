import 'package:flutter_test/flutter_test.dart';
import 'package:indrive_assistant/data/models/driver_settings.dart';
import 'package:indrive_assistant/engine/fare_calculator_engine.dart';

void main() {
  const settings = DriverSettings(
    fuelConsumptionLPer100Km: 10.0,
    fuelPricePerLiter: 15.0,
    inDriveCommissionPercent: 10.0,
    wearAndTearPerKm: 1.0,
    minProfitPerKm: 8.0,
    favoriteZones: ["المعادي"],
    blacklistedZones: ["حلوان"],
    // The tests below check the flat formula; pickup pricing has its own group.
    pickupPricingEnabled: false,
    blacklistedPassengers: ["محمود"],
    favoritePassengers: ["سارة"],
  );

  group('FareCalculatorEngine.analyze', () {
    test('computes costs and net profit', () {
      final r = FareCalculatorEngine.analyze(
        pickupDistanceKm: 2,
        tripDistanceKm: 8,
        estimatedMinutes: 25,
        offeredFare: 100,
        settings: settings,
      );
      // 10 km: fuel 1 L * 15 = 15, wear 10, commission 10% of 100 = 10
      expect(r.fuelCost, closeTo(15, 1e-9));
      expect(r.wearAndTearCost, closeTo(10, 1e-9));
      expect(r.commissionCost, closeTo(10, 1e-9));
      expect(r.netProfit, closeTo(65, 1e-9));
      expect(r.profitPerKm, closeTo(6.5, 1e-9));
      expect(r.profitabilityLevel, ProfitabilityLevel.poorMargin);
    });

    test('loss warning reports the full operating cost, not only fuel', () {
      final r = FareCalculatorEngine.analyze(
        pickupDistanceKm: 5,
        tripDistanceKm: 15,
        estimatedMinutes: 50,
        offeredFare: 40,
        settings: settings,
      );
      expect(r.profitabilityLevel, ProfitabilityLevel.lossDanger);
      expect(r.warningNote, contains(r.totalOperatingCost.toStringAsFixed(1)));
    });

    test('recommended bid covers costs plus target profit', () {
      final r = FareCalculatorEngine.analyze(
        pickupDistanceKm: 2,
        tripDistanceKm: 8,
        estimatedMinutes: 25,
        offeredFare: 100,
        settings: settings,
      );
      final rec = r.smartBids[1];
      expect(rec.suggestedFare, greaterThanOrEqualTo(r.breakEvenFare + 10));
      expect(rec.profitPerKm, greaterThanOrEqualTo(settings.minProfitPerKm - 1e-9));
    });

    test('blacklisted zone turns an ordinary offer into a rejection', () {
      final r = FareCalculatorEngine.analyze(
        pickupDistanceKm: 1,
        tripDistanceKm: 5,
        estimatedMinutes: 15,
        offeredFare: 90,
        settings: settings,
        dropoffAddress: "شارع ٩ - حلوان",
      );
      expect(r.isBlacklistedZone, isTrue);
      expect(r.matchedBlacklistZone, "حلوان");
    });
  });

  group('zone matching', () {
    test('ignores hamza / taa marbuta / punctuation differences', () {
      expect(FareCalculatorEngine.matchZone("مدينة نصر، القاهرة", ["مدينه نصر"]), "مدينه نصر");
      expect(FareCalculatorEngine.matchZone("الإسكندرية", ["الاسكندريه"]), "الاسكندريه");
    });

    test('settings.isFavorite uses the same normalized matching', () {
      expect(settings.isFavorite("كورنيش المعادى"), isTrue);
      expect(settings.isBlacklisted("مصر الجديدة"), isFalse);
    });

    test('empty address never matches', () {
      expect(FareCalculatorEngine.matchZone("", ["حلوان"]), isNull);
      expect(FareCalculatorEngine.matchZone(null, ["حلوان"]), isNull);
    });
  });

  group('pickup pricing', () {
    final withPickup = settings.copyWith(
      pickupPricingEnabled: true,
      pickupProfitPerKm: 3.0,
      pickupCommissionSharePercent: 50.0,
    );

    test('fair price prices pickup km with their own margin and half the commission', () {
      // cost/km = 1.5 fuel + 1 wear = 2.5 ; trip 8 km: 8 × (2.5 + 8) / 0.9 ; pickup 2 km: 2 × (2.5 + 3) / 0.95
      final expected = 8 * 10.5 / 0.9 + 2 * 5.5 / 0.95;
      expect(
        FareCalculatorEngine.fairPrice(pickupDistanceKm: 2, tripDistanceKm: 8, settings: withPickup),
        closeTo(expected, 1e-9),
      );
    });

    test('off: every km is priced like a trip km', () {
      expect(
        FareCalculatorEngine.fairPrice(pickupDistanceKm: 2, tripDistanceKm: 8, settings: settings),
        closeTo(10 * 10.5 / 0.9, 1e-9),
      );
    });

    test('an offer at the fair price is not rated as a thin margin', () {
      final fair = FareCalculatorEngine.fairPrice(pickupDistanceKm: 2, tripDistanceKm: 8, settings: withPickup);
      final r = FareCalculatorEngine.analyze(
        pickupDistanceKm: 2,
        tripDistanceKm: 8,
        estimatedMinutes: 25,
        offeredFare: fair + 0.01, // a hair above, so float rounding can't tip it under the target
        settings: withPickup,
      );
      expect(r.profitabilityLevel, ProfitabilityLevel.goodProfit);
      expect(r.recommendedFare, lessThan(FareCalculatorEngine.roundToSmartStep(10 * 10.5 / 0.9) + 1e-9));
    });
  });

  group('passenger lists', () {
    test('a blocked passenger is always a rejection', () {
      final r = FareCalculatorEngine.analyze(
        pickupDistanceKm: 1,
        tripDistanceKm: 5,
        estimatedMinutes: 15,
        offeredFare: 500,
        settings: settings,
        passengerName: "محمود",
      );
      expect(r.isBlacklistedPassenger, isTrue);
      expect(r.profitabilityLevel, ProfitabilityLevel.lossDanger);
    });

    test('names match exactly (normalised), never as a substring', () {
      expect(FareCalculatorEngine.matchPassenger("ساره", ["سارة"]), "سارة");
      expect(FareCalculatorEngine.matchPassenger("محمود علي", ["محمود"]), isNull);
      expect(FareCalculatorEngine.matchPassenger("", ["محمود"]), isNull);
    });
  });

  test('estimateMinutes is clamped to a sane range', () {
    expect(FareCalculatorEngine.estimateMinutes(0), 5);
    expect(FareCalculatorEngine.estimateMinutes(10), 25);
    expect(FareCalculatorEngine.estimateMinutes(1000), 300);
  });
}
