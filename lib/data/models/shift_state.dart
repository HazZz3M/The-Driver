class ShiftState {
  final bool isShiftActive;
  final int shiftStartTime;
  final int shiftEndTime;
  final double targetEarnings;
  final int completedTripsCount;
  final double totalGrossFare;
  final double totalFuelSpent;
  final double totalNetProfit;
  final double totalKilometers;

  const ShiftState({
    this.isShiftActive = false,
    this.shiftStartTime = 0,
    this.shiftEndTime = 0,
    this.targetEarnings = 1500.0,
    this.completedTripsCount = 0,
    this.totalGrossFare = 0.0,
    this.totalFuelSpent = 0.0,
    this.totalNetProfit = 0.0,
    this.totalKilometers = 0.0,
  });

  int get durationMinutes {
    if (!isShiftActive || shiftStartTime == 0) return 0;
    return (DateTime.now().millisecondsSinceEpoch - shiftStartTime) ~/ (1000 * 60);
  }

  double get progressPercent {
    if (targetEarnings <= 0) return 0.0;
    final percent = (totalGrossFare / targetEarnings) * 100.0;
    return percent.clamp(0.0, 100.0);
  }

  ShiftState copyWith({
    bool? isShiftActive,
    int? shiftStartTime,
    int? shiftEndTime,
    double? targetEarnings,
    int? completedTripsCount,
    double? totalGrossFare,
    double? totalFuelSpent,
    double? totalNetProfit,
    double? totalKilometers,
  }) {
    return ShiftState(
      isShiftActive: isShiftActive ?? this.isShiftActive,
      shiftStartTime: shiftStartTime ?? this.shiftStartTime,
      shiftEndTime: shiftEndTime ?? this.shiftEndTime,
      targetEarnings: targetEarnings ?? this.targetEarnings,
      completedTripsCount: completedTripsCount ?? this.completedTripsCount,
      totalGrossFare: totalGrossFare ?? this.totalGrossFare,
      totalFuelSpent: totalFuelSpent ?? this.totalFuelSpent,
      totalNetProfit: totalNetProfit ?? this.totalNetProfit,
      totalKilometers: totalKilometers ?? this.totalKilometers,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'isShiftActive': isShiftActive,
      'shiftStartTime': shiftStartTime,
      'shiftEndTime': shiftEndTime,
      'targetEarnings': targetEarnings,
      'completedTripsCount': completedTripsCount,
      'totalGrossFare': totalGrossFare,
      'totalFuelSpent': totalFuelSpent,
      'totalNetProfit': totalNetProfit,
      'totalKilometers': totalKilometers,
    };
  }

  factory ShiftState.fromJson(Map<String, dynamic> json) {
    return ShiftState(
      isShiftActive: json['isShiftActive'] as bool? ?? false,
      shiftStartTime: (json['shiftStartTime'] as num?)?.toInt() ?? 0,
      shiftEndTime: (json['shiftEndTime'] as num?)?.toInt() ?? 0,
      targetEarnings: (json['targetEarnings'] as num?)?.toDouble() ?? 1500.0,
      completedTripsCount: (json['completedTripsCount'] as num?)?.toInt() ?? 0,
      totalGrossFare: (json['totalGrossFare'] as num?)?.toDouble() ?? 0.0,
      totalFuelSpent: (json['totalFuelSpent'] as num?)?.toDouble() ?? 0.0,
      totalNetProfit: (json['totalNetProfit'] as num?)?.toDouble() ?? 0.0,
      totalKilometers: (json['totalKilometers'] as num?)?.toDouble() ?? 0.0,
    );
  }
}
