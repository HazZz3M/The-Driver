import 'driver_settings.dart';

class VehicleProfile {
  final String id;
  final String name; // e.g. "هيونداي فيرنا", "بي واي دي F3 (غاز)", "سكوتر بينيلي"
  final String modelYear;
  final String fuelType; // "بنزين 92", "بنزين 95", "بنزين 80", "غاز طبيعي / CNG"
  final double fuelConsumptionLPer100Km;
  final double fuelPricePerLiter;
  final double wearAndTearPerKm;
  final double inDriveCommissionPercent;
  final double minProfitPerKm;
  final String notes;

  VehicleProfile({
    String? id,
    required this.name,
    this.modelYear = "2020",
    this.fuelType = "بنزين 92",
    this.fuelConsumptionLPer100Km = 8.5,
    this.fuelPricePerLiter = 15.25,
    this.wearAndTearPerKm = 1.2,
    this.inDriveCommissionPercent = 10.0,
    this.minProfitPerKm = 8.0,
    this.notes = "",
  }) : id = id ?? DateTime.now().millisecondsSinceEpoch.toString();

  DriverSettings toDriverSettings({
    required String currency,
    required double targetDailyEarnings,
    required List<String> favoriteZones,
    required List<String> blacklistedZones,
  }) {
    return DriverSettings(
      fuelConsumptionLPer100Km: fuelConsumptionLPer100Km,
      fuelPricePerLiter: fuelPricePerLiter,
      inDriveCommissionPercent: inDriveCommissionPercent,
      wearAndTearPerKm: wearAndTearPerKm,
      targetDailyEarnings: targetDailyEarnings,
      minProfitPerKm: minProfitPerKm,
      currency: currency,
      favoriteZones: favoriteZones,
      blacklistedZones: blacklistedZones,
    );
  }

  VehicleProfile copyWith({
    String? id,
    String? name,
    String? modelYear,
    String? fuelType,
    double? fuelConsumptionLPer100Km,
    double? fuelPricePerLiter,
    double? wearAndTearPerKm,
    double? inDriveCommissionPercent,
    double? minProfitPerKm,
    String? notes,
  }) {
    return VehicleProfile(
      id: id ?? this.id,
      name: name ?? this.name,
      modelYear: modelYear ?? this.modelYear,
      fuelType: fuelType ?? this.fuelType,
      fuelConsumptionLPer100Km: fuelConsumptionLPer100Km ?? this.fuelConsumptionLPer100Km,
      fuelPricePerLiter: fuelPricePerLiter ?? this.fuelPricePerLiter,
      wearAndTearPerKm: wearAndTearPerKm ?? this.wearAndTearPerKm,
      inDriveCommissionPercent: inDriveCommissionPercent ?? this.inDriveCommissionPercent,
      minProfitPerKm: minProfitPerKm ?? this.minProfitPerKm,
      notes: notes ?? this.notes,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'modelYear': modelYear,
      'fuelType': fuelType,
      'fuelConsumptionLPer100Km': fuelConsumptionLPer100Km,
      'fuelPricePerLiter': fuelPricePerLiter,
      'wearAndTearPerKm': wearAndTearPerKm,
      'inDriveCommissionPercent': inDriveCommissionPercent,
      'minProfitPerKm': minProfitPerKm,
      'notes': notes,
    };
  }

  factory VehicleProfile.fromJson(Map<String, dynamic> json) {
    return VehicleProfile(
      id: json['id'] as String?,
      name: json['name'] as String? ?? "مركبة",
      modelYear: json['modelYear'] as String? ?? "2020",
      fuelType: json['fuelType'] as String? ?? "بنزين 92",
      fuelConsumptionLPer100Km: (json['fuelConsumptionLPer100Km'] as num?)?.toDouble() ?? 8.5,
      fuelPricePerLiter: (json['fuelPricePerLiter'] as num?)?.toDouble() ?? 15.25,
      wearAndTearPerKm: (json['wearAndTearPerKm'] as num?)?.toDouble() ?? 1.2,
      inDriveCommissionPercent: (json['inDriveCommissionPercent'] as num?)?.toDouble() ?? 10.0,
      minProfitPerKm: (json['minProfitPerKm'] as num?)?.toDouble() ?? 8.0,
      notes: json['notes'] as String? ?? "",
    );
  }
}
