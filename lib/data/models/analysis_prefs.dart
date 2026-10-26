import '../../engine/fare_calculator_engine.dart';

/// How the Insights tab reads the request log: which part of an address is the "area",
/// the driver's own names for areas (rename / merge), areas left out, and which requests count.
class AnalysisPrefs {
  /// NEIGHBOURHOOD (from inside the parentheses) | PLACE (the name before them) | ZONES_ONLY
  final String areaSource;
  /// Area name as read → the driver's name for it. Several names mapped to one = merged.
  final Map<String, String> aliases;
  /// Areas left out of the area lists, routes and recommendations (after renaming).
  final Set<String> hidden;
  /// Count only requests the driver opened (skip ones only seen on the list).
  final bool onlyOpened;
  /// Leave requests whose distances weren't read out of every figure.
  final bool excludeEstimated;
  /// Merge suggestions the driver said are different places (by AnalysisPrefs.skeleton).
  final Set<String> notSame;

  const AnalysisPrefs({
    this.areaSource = "NEIGHBOURHOOD",
    this.aliases = const {},
    this.hidden = const {},
    this.onlyOpened = false,
    this.excludeEstimated = false,
    this.notSame = const {},
  });

  static const Map<String, String> areaSourceLabels = {
    "NEIGHBOURHOOD": "الحي",
    "PLACE": "اسم المكان",
    "ZONES_ONLY": "مناطقي بس",
  };

  static const Map<String, String> areaSourceHints = {
    "NEIGHBOURHOOD": "من «التجنيد (الحمراء الثانية، قسم أول أسيوط، مصر)» ياخد «الحمراء الثانية»",
    "PLACE": "ياخد اسم المكان نفسه: «التجنيد». أدق، بس المناطق بتبقى كتير",
    "ZONES_ONLY": "يستخدم بس المناطق المفضلة والمحظورة اللي انت كاتبها، والباقي ميتحسبش",
  };

  bool get isDefault =>
      areaSource == "NEIGHBOURHOOD" && aliases.isEmpty && hidden.isEmpty && !onlyOpened && !excludeEstimated;

  static String _key(String name) => FareCalculatorEngine.normalizeArabicText(name);

  /// The driver's name for [area] (follows merges of merges), or [area] itself.
  String rename(String area) {
    if (area.isEmpty || aliases.isEmpty) return area;
    final byKey = {for (final e in aliases.entries) _key(e.key): e.value};
    var name = area;
    for (var i = 0; i < 5; i++) {
      final next = byKey[_key(name)];
      if (next == null || _key(next) == _key(name)) break;
      name = next;
    }
    return name;
  }

  bool isHidden(String area) {
    final k = _key(area);
    return hidden.any((h) => _key(h) == k);
  }

  AnalysisPrefs copyWith({
    String? areaSource,
    Map<String, String>? aliases,
    Set<String>? hidden,
    bool? onlyOpened,
    bool? excludeEstimated,
    Set<String>? notSame,
  }) {
    return AnalysisPrefs(
      areaSource: areaSource ?? this.areaSource,
      aliases: aliases ?? this.aliases,
      hidden: hidden ?? this.hidden,
      onlyOpened: onlyOpened ?? this.onlyOpened,
      excludeEstimated: excludeEstimated ?? this.excludeEstimated,
      notSame: notSame ?? this.notSame,
    );
  }

  /// [names] (as shown now) all become [target]; earlier aliases pointing at them follow.
  AnalysisPrefs merge(Iterable<String> names, String target) {
    final next = Map<String, String>.from(aliases);
    final from = names.map(_key).toSet();
    next.updateAll((k, v) => from.contains(_key(v)) ? target : v);
    for (final n in names) {
      if (_key(n) != _key(target)) next[n] = target;
    }
    next.removeWhere((k, v) => _key(k) == _key(v));
    return copyWith(aliases: next);
  }

  /// Undoes every rename / merge into [name].
  AnalysisPrefs unmerge(String name) {
    final k = _key(name);
    return copyWith(aliases: Map.of(aliases)..removeWhere((from, to) => _key(to) == k || _key(from) == k));
  }

  Map<String, dynamic> toJson() => {
        'areaSource': areaSource,
        'aliases': aliases,
        'hidden': hidden.toList(),
        'onlyOpened': onlyOpened,
        'excludeEstimated': excludeEstimated,
        'notSame': notSame.toList(),
      };

  factory AnalysisPrefs.fromJson(Map<String, dynamic> json) {
    return AnalysisPrefs(
      areaSource: json['areaSource'] as String? ?? "NEIGHBOURHOOD",
      aliases: ((json['aliases'] as Map?) ?? const {}).map((k, v) => MapEntry(k.toString(), v.toString())),
      hidden: ((json['hidden'] as List?) ?? const []).map((e) => e.toString()).toSet(),
      onlyOpened: json['onlyOpened'] as bool? ?? false,
      excludeEstimated: json['excludeEstimated'] as bool? ?? false,
      notSame: ((json['notSame'] as List?) ?? const []).map((e) => e.toString()).toSet(),
    );
  }

  // ---------------------------------------------------------------------------
  // Same place, different spelling: "الحمراء الثانية" / "Al Hamraa Ath Thaneyah"
  // ---------------------------------------------------------------------------

  /// A rough sound "skeleton" of a place name that is the same in Arabic and in English letters:
  /// articles (ال / Al / El…) dropped, vowels dropped, similar sounds folded together.
  static String skeleton(String name) {
    final words = FareCalculatorEngine.normalizeArabicText(name)
        .split(RegExp(r'[\s\-_.]+'))
        .where((w) => w.isNotEmpty)
        .toList();
    final out = <String>[];
    for (var w in words) {
      if (_latinArticles.contains(w)) continue;
      if (w.startsWith("ال") && w.length > 3) w = w.substring(2);
      final latin = StringBuffer();
      for (final ch in w.split('')) {
        latin.write(_arabicSounds[ch] ?? ch);
      }
      var s = latin.toString()
          .replaceAll("th", "t")
          .replaceAll("dh", "d")
          .replaceAll("kh", "k")
          .replaceAll("sh", "s")
          .replaceAll("gh", "g")
          .replaceAll("ou", "")
          .replaceAll(RegExp(r'[aeiouywء-غـ-يً-ٟ]'), "")
          .replaceAll("q", "k")
          .replaceAll("j", "g")
          .replaceAll("c", "k")
          .replaceAll("x", "ks")
          .replaceAll(RegExp(r'[^a-z0-9]'), "")
          .replaceAllMapped(RegExp(r'(.)\1+'), (m) => m[1]!);
      if (s.length > 1 && s.endsWith("h")) s = s.substring(0, s.length - 1);
      if (s.isNotEmpty) out.add(s);
    }
    return out.join(" ");
  }

  static const _latinArticles = {"al", "el", "ath", "ash", "ad", "an", "ar", "as", "at", "az", "the"};

  static const Map<String, String> _arabicSounds = {
    "ب": "b", "ت": "t", "ث": "t", "ج": "g", "ح": "h", "خ": "k", "د": "d", "ذ": "d", "ر": "r", "ز": "z",
    "س": "s", "ش": "s", "ص": "s", "ض": "d", "ط": "t", "ظ": "z", "غ": "g", "ف": "f", "ق": "k", "ك": "k",
    "ل": "l", "م": "m", "ن": "n", "ه": "h", "ة": "h", "ء": "", "ا": "", "و": "", "ي": "", "ى": "", "ع": "",
    "ئ": "", "ؤ": "", "أ": "", "إ": "", "آ": "",
  };

  /// Groups of area names that look like the same place spelled differently (Arabic / English,
  /// with or without ء / ة…), most requests first. [counts]: requests per area as shown now.
  static List<List<String>> suggestMerges(Map<String, int> counts, {Set<String> ignore = const {}}) {
    final groups = <String, List<String>>{};
    for (final name in counts.keys) {
      final s = skeleton(name);
      if (s.replaceAll(" ", "").length < 3 || ignore.contains(s)) continue;
      groups.putIfAbsent(s, () => []).add(name);
    }
    final out = groups.values.where((g) => g.length > 1).map((g) {
      return g..sort((a, b) => (counts[b] ?? 0).compareTo(counts[a] ?? 0));
    }).toList()
      ..sort((a, b) => (counts[b.first] ?? 0).compareTo(counts[a.first] ?? 0));
    return out;
  }
}
