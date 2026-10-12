/// What the floating HUD shows over inDrive and how it behaves.
/// Mirrored natively by HudPrefs.kt (keys must match).
class HudSettings {
  final String style; // COMPACT_CIRCLE | SLIM_PILL | SPEEDO_GAUGE | MINI_TAG | CORNER_RIBBON | GLOW_DOT
  final String size; // SMALL | MEDIUM | LARGE
  final String primaryValue; // NET_PROFIT | PROFIT_PER_KM | OFFER | RECOMMENDED
  final double opacity; // 0.4 - 1.0
  final String side; // LEFT | RIGHT
  final bool showLossBadges;
  final bool vibrate;
  final bool vibrateProfitableOnly;
  final bool showFareComparison;
  final bool showProfit;
  final bool showDistance;
  final bool showCostBreakdown;
  final bool showZones;
  final int autoCollapseSeconds; // 0 = never
  final bool readMapDistance; // OCR of the trip length drawn on the Ride-request map

  const HudSettings({
    this.style = "COMPACT_CIRCLE",
    this.size = "MEDIUM",
    this.primaryValue = "NET_PROFIT",
    this.opacity = 1.0,
    this.side = "LEFT",
    this.showLossBadges = true,
    this.vibrate = true,
    this.vibrateProfitableOnly = false,
    this.showFareComparison = true,
    this.showProfit = true,
    this.showDistance = true,
    this.showCostBreakdown = false,
    this.showZones = true,
    this.autoCollapseSeconds = 0,
    this.readMapDistance = true,
  });

  HudSettings copyWith({
    String? style,
    String? size,
    String? primaryValue,
    double? opacity,
    String? side,
    bool? showLossBadges,
    bool? vibrate,
    bool? vibrateProfitableOnly,
    bool? showFareComparison,
    bool? showProfit,
    bool? showDistance,
    bool? showCostBreakdown,
    bool? showZones,
    int? autoCollapseSeconds,
    bool? readMapDistance,
  }) {
    return HudSettings(
      style: style ?? this.style,
      size: size ?? this.size,
      primaryValue: primaryValue ?? this.primaryValue,
      opacity: opacity ?? this.opacity,
      side: side ?? this.side,
      showLossBadges: showLossBadges ?? this.showLossBadges,
      vibrate: vibrate ?? this.vibrate,
      vibrateProfitableOnly: vibrateProfitableOnly ?? this.vibrateProfitableOnly,
      showFareComparison: showFareComparison ?? this.showFareComparison,
      showProfit: showProfit ?? this.showProfit,
      showDistance: showDistance ?? this.showDistance,
      showCostBreakdown: showCostBreakdown ?? this.showCostBreakdown,
      showZones: showZones ?? this.showZones,
      autoCollapseSeconds: autoCollapseSeconds ?? this.autoCollapseSeconds,
      readMapDistance: readMapDistance ?? this.readMapDistance,
    );
  }

  Map<String, dynamic> toJson() => {
        'style': style,
        'size': size,
        'primaryValue': primaryValue,
        'opacity': opacity,
        'side': side,
        'showLossBadges': showLossBadges,
        'vibrate': vibrate,
        'vibrateProfitableOnly': vibrateProfitableOnly,
        'showFareComparison': showFareComparison,
        'showProfit': showProfit,
        'showDistance': showDistance,
        'showCostBreakdown': showCostBreakdown,
        'showZones': showZones,
        'autoCollapseSeconds': autoCollapseSeconds,
        'readMapDistance': readMapDistance,
      };

  factory HudSettings.fromJson(Map<String, dynamic> json, {HudSettings fallback = const HudSettings()}) {
    return HudSettings(
      style: json['style'] as String? ?? fallback.style,
      size: json['size'] as String? ?? fallback.size,
      primaryValue: json['primaryValue'] as String? ?? fallback.primaryValue,
      opacity: ((json['opacity'] as num?)?.toDouble() ?? fallback.opacity).clamp(0.4, 1.0),
      side: json['side'] as String? ?? fallback.side,
      showLossBadges: json['showLossBadges'] as bool? ?? fallback.showLossBadges,
      vibrate: json['vibrate'] as bool? ?? fallback.vibrate,
      vibrateProfitableOnly: json['vibrateProfitableOnly'] as bool? ?? fallback.vibrateProfitableOnly,
      showFareComparison: json['showFareComparison'] as bool? ?? fallback.showFareComparison,
      showProfit: json['showProfit'] as bool? ?? fallback.showProfit,
      showDistance: json['showDistance'] as bool? ?? fallback.showDistance,
      showCostBreakdown: json['showCostBreakdown'] as bool? ?? fallback.showCostBreakdown,
      showZones: json['showZones'] as bool? ?? fallback.showZones,
      autoCollapseSeconds: (json['autoCollapseSeconds'] as num?)?.toInt() ?? fallback.autoCollapseSeconds,
      readMapDistance: json['readMapDistance'] as bool? ?? fallback.readMapDistance,
    );
  }

  static const Map<String, String> styleLabels = {
    "COMPACT_CIRCLE": "دائرة",
    "SLIM_PILL": "كبسولة",
    "SPEEDO_GAUGE": "عداد",
    "MINI_TAG": "شارة ملونة",
    "CORNER_RIBBON": "شريط",
    "GLOW_DOT": "نقطة مضيئة",
  };

  static const Map<String, String> sizeLabels = {"SMALL": "صغير", "MEDIUM": "متوسط", "LARGE": "كبير"};

  static const Map<String, String> primaryValueLabels = {
    "NET_PROFIT": "صافي الربح",
    "PROFIT_PER_KM": "الربح لكل كم",
    "OFFER": "عرض الراكب",
    "RECOMMENDED": "المزايدة المقترحة",
  };

  static const Map<String, String> primaryValueCaptions = {
    "NET_PROFIT": "صافي",
    "PROFIT_PER_KM": "ج/كم",
    "OFFER": "العرض",
    "RECOMMENDED": "اقترح",
  };
}
