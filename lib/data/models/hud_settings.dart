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
  final bool speakVerdict; // say the verdict aloud for each new request
  final String vibrateLevels; // comma list of EXCELLENT, GOOD, POOR, LOSS, UNKNOWN that vibrate
  final String speakLevels; // same, for the voice
  final String vibrationStrength; // LIGHT | NORMAL | STRONG
  final String speakDetail; // VERDICT | NET | NET_KM
  final double speechRate; // 0.5 - 2.0
  final bool speakStopWhenGone; // stop talking when the request leaves the screen
  final bool alertOncePerRequest; // one alert per request, even when it shows again
  final bool muteDuringAutomation; // quiet for requests the automation opens
  final bool showFareComparison;
  final bool showProfit;
  final bool showDistance;
  final bool showCostBreakdown;
  final bool showZones;
  final int autoCollapseSeconds; // 0 = never
  final bool readMapDistance; // OCR of the trip length drawn on the Ride-request map
  final bool tripFromHistory; // B: reuse lengths read earlier for the same pickup → destination
  final bool tripFromAddresses; // A: estimate from the two addresses (Geocoder, online)
  final bool tripAutoOpen; // C: open unread requests, read the map, go back
  final double autoOpenStaySeconds; // C: longest time a request stays open
  final double autoOpenGapSeconds; // C: pause after closing one before opening the next
  final bool autoOpenBackWhenRead; // C: go back as soon as the trip length is read
  final String bidChipsMode; // OFF | HIGHLIGHT | AUTO_TAP: circles over the request's bid buttons

  const HudSettings({
    this.style = "COMPACT_CIRCLE",
    this.size = "MEDIUM",
    this.primaryValue = "NET_PROFIT",
    this.opacity = 1.0,
    this.side = "LEFT",
    this.showLossBadges = true,
    this.vibrate = true,
    this.vibrateProfitableOnly = false,
    this.speakVerdict = false,
    this.vibrateLevels = allLevels,
    this.speakLevels = allLevels,
    this.vibrationStrength = "NORMAL",
    this.speakDetail = "NET",
    this.speechRate = 1.0,
    this.speakStopWhenGone = true,
    this.alertOncePerRequest = true,
    this.muteDuringAutomation = true,
    this.showFareComparison = true,
    this.showProfit = true,
    this.showDistance = true,
    this.showCostBreakdown = false,
    this.showZones = true,
    this.autoCollapseSeconds = 0,
    this.readMapDistance = true,
    this.tripFromHistory = true,
    this.tripFromAddresses = false,
    this.tripAutoOpen = false,
    this.autoOpenStaySeconds = 7.0,
    this.autoOpenGapSeconds = 8.0,
    this.autoOpenBackWhenRead = true,
    this.bidChipsMode = "HIGHLIGHT",
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
    bool? speakVerdict,
    String? vibrateLevels,
    String? speakLevels,
    String? vibrationStrength,
    String? speakDetail,
    double? speechRate,
    bool? speakStopWhenGone,
    bool? alertOncePerRequest,
    bool? muteDuringAutomation,
    bool? showFareComparison,
    bool? showProfit,
    bool? showDistance,
    bool? showCostBreakdown,
    bool? showZones,
    int? autoCollapseSeconds,
    bool? readMapDistance,
    bool? tripFromHistory,
    bool? tripFromAddresses,
    bool? tripAutoOpen,
    double? autoOpenStaySeconds,
    double? autoOpenGapSeconds,
    bool? autoOpenBackWhenRead,
    String? bidChipsMode,
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
      speakVerdict: speakVerdict ?? this.speakVerdict,
      vibrateLevels: vibrateLevels ?? this.vibrateLevels,
      speakLevels: speakLevels ?? this.speakLevels,
      vibrationStrength: vibrationStrength ?? this.vibrationStrength,
      speakDetail: speakDetail ?? this.speakDetail,
      speechRate: speechRate ?? this.speechRate,
      speakStopWhenGone: speakStopWhenGone ?? this.speakStopWhenGone,
      alertOncePerRequest: alertOncePerRequest ?? this.alertOncePerRequest,
      muteDuringAutomation: muteDuringAutomation ?? this.muteDuringAutomation,
      showFareComparison: showFareComparison ?? this.showFareComparison,
      showProfit: showProfit ?? this.showProfit,
      showDistance: showDistance ?? this.showDistance,
      showCostBreakdown: showCostBreakdown ?? this.showCostBreakdown,
      showZones: showZones ?? this.showZones,
      autoCollapseSeconds: autoCollapseSeconds ?? this.autoCollapseSeconds,
      readMapDistance: readMapDistance ?? this.readMapDistance,
      tripFromHistory: tripFromHistory ?? this.tripFromHistory,
      tripFromAddresses: tripFromAddresses ?? this.tripFromAddresses,
      tripAutoOpen: tripAutoOpen ?? this.tripAutoOpen,
      autoOpenStaySeconds: autoOpenStaySeconds ?? this.autoOpenStaySeconds,
      autoOpenGapSeconds: autoOpenGapSeconds ?? this.autoOpenGapSeconds,
      autoOpenBackWhenRead: autoOpenBackWhenRead ?? this.autoOpenBackWhenRead,
      bidChipsMode: bidChipsMode ?? this.bidChipsMode,
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
        'speakVerdict': speakVerdict,
        'vibrateLevels': vibrateLevels,
        'speakLevels': speakLevels,
        'vibrationStrength': vibrationStrength,
        'speakDetail': speakDetail,
        'speechRate': speechRate,
        'speakStopWhenGone': speakStopWhenGone,
        'alertOncePerRequest': alertOncePerRequest,
        'muteDuringAutomation': muteDuringAutomation,
        'showFareComparison': showFareComparison,
        'showProfit': showProfit,
        'showDistance': showDistance,
        'showCostBreakdown': showCostBreakdown,
        'showZones': showZones,
        'autoCollapseSeconds': autoCollapseSeconds,
        'readMapDistance': readMapDistance,
        'tripFromHistory': tripFromHistory,
        'tripFromAddresses': tripFromAddresses,
        'tripAutoOpen': tripAutoOpen,
        'autoOpenStaySeconds': autoOpenStaySeconds,
        'autoOpenGapSeconds': autoOpenGapSeconds,
        'autoOpenBackWhenRead': autoOpenBackWhenRead,
        'bidChipsMode': bidChipsMode,
      };

  factory HudSettings.fromJson(Map<String, dynamic> json, {HudSettings fallback = const HudSettings()}) {
    // Before 1.20 there was one "profitable only" switch for both vibration and voice.
    final legacyLevels = json['vibrateProfitableOnly'] == true ? "EXCELLENT,GOOD" : null;
    return HudSettings(
      style: json['style'] as String? ?? fallback.style,
      size: json['size'] as String? ?? fallback.size,
      primaryValue: json['primaryValue'] as String? ?? fallback.primaryValue,
      opacity: ((json['opacity'] as num?)?.toDouble() ?? fallback.opacity).clamp(0.4, 1.0),
      side: json['side'] as String? ?? fallback.side,
      showLossBadges: json['showLossBadges'] as bool? ?? fallback.showLossBadges,
      vibrate: json['vibrate'] as bool? ?? fallback.vibrate,
      vibrateProfitableOnly: json['vibrateProfitableOnly'] as bool? ?? fallback.vibrateProfitableOnly,
      speakVerdict: json['speakVerdict'] as bool? ?? fallback.speakVerdict,
      vibrateLevels: json['vibrateLevels'] as String? ?? legacyLevels ?? fallback.vibrateLevels,
      speakLevels: json['speakLevels'] as String? ?? legacyLevels ?? fallback.speakLevels,
      vibrationStrength: json['vibrationStrength'] as String? ?? fallback.vibrationStrength,
      speakDetail: json['speakDetail'] as String? ?? fallback.speakDetail,
      speechRate: ((json['speechRate'] as num?)?.toDouble() ?? fallback.speechRate).clamp(0.5, 2.0),
      speakStopWhenGone: json['speakStopWhenGone'] as bool? ?? fallback.speakStopWhenGone,
      alertOncePerRequest: json['alertOncePerRequest'] as bool? ?? fallback.alertOncePerRequest,
      muteDuringAutomation: json['muteDuringAutomation'] as bool? ?? fallback.muteDuringAutomation,
      showFareComparison: json['showFareComparison'] as bool? ?? fallback.showFareComparison,
      showProfit: json['showProfit'] as bool? ?? fallback.showProfit,
      showDistance: json['showDistance'] as bool? ?? fallback.showDistance,
      showCostBreakdown: json['showCostBreakdown'] as bool? ?? fallback.showCostBreakdown,
      showZones: json['showZones'] as bool? ?? fallback.showZones,
      autoCollapseSeconds: (json['autoCollapseSeconds'] as num?)?.toInt() ?? fallback.autoCollapseSeconds,
      readMapDistance: json['readMapDistance'] as bool? ?? fallback.readMapDistance,
      tripFromHistory: json['tripFromHistory'] as bool? ?? fallback.tripFromHistory,
      tripFromAddresses: json['tripFromAddresses'] as bool? ?? fallback.tripFromAddresses,
      tripAutoOpen: json['tripAutoOpen'] as bool? ?? fallback.tripAutoOpen,
      autoOpenStaySeconds: (json['autoOpenStaySeconds'] as num?)?.toDouble() ?? fallback.autoOpenStaySeconds,
      autoOpenGapSeconds: (json['autoOpenGapSeconds'] as num?)?.toDouble() ?? fallback.autoOpenGapSeconds,
      autoOpenBackWhenRead: json['autoOpenBackWhenRead'] as bool? ?? fallback.autoOpenBackWhenRead,
      bidChipsMode: json['bidChipsMode'] as String? ?? fallback.bidChipsMode,
    );
  }

  static const String allLevels = "EXCELLENT,GOOD,POOR,LOSS,UNKNOWN";

  /// Verdicts the driver can pick for vibration / voice, in display order.
  static const Map<String, String> levelLabels = {
    "EXCELLENT": "ممتاز",
    "GOOD": "رابح",
    "POOR": "هامش ضعيف",
    "LOSS": "خسارة أو محظور",
    "UNKNOWN": "المسافة مش مقروءة",
  };

  static const Map<String, String> strengthLabels = {"LIGHT": "خفيفة", "NORMAL": "عادية", "STRONG": "قوية"};

  static const Map<String, String> speakDetailLabels = {
    "VERDICT": "الحكم بس",
    "NET": "الحكم والصافي",
    "NET_KM": "الحكم والصافي والمسافة",
  };

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
