import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../data/models/analysis_prefs.dart';
import '../../engine/request_insights.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../components/ui_kit.dart';

void _updateAnalysis(BuildContext context, AnalysisPrefs Function(AnalysisPrefs a) change) {
  final p = context.read<AssistantProvider>();
  p.updateAnalysis(change(p.analysis));
}

/// Area names as read from the whole log, with the driver's current settings.
RequestInsights _allTime(AssistantProvider p) {
  final s = p.driverSettings;
  return RequestInsights.from(p.requestLog, zones: [...s.favoriteZones, ...s.blacklistedZones], prefs: p.analysis);
}

// =============================================================================
// ANALYSIS METHOD
// =============================================================================

/// How the Insights tab reads the request log: what counts as an area, and which requests count.
class AnalysisSettingsPage extends StatelessWidget {
  const AnalysisSettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final a = provider.analysis;
    void change(AnalysisPrefs Function(AnalysisPrefs a) c) => _updateAnalysis(context, c);
    final renamed = a.aliases.length;

    return Scaffold(
      appBar: AppBar(title: const Text("طريقة التحليل")),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
          children: [
            const SectionLabel("المنطقة بتتاخد منين"),
            SettingsGroup(children: [
              for (final e in AnalysisPrefs.areaSourceLabels.entries)
                RadioListTile<String>(
                  value: e.key,
                  groupValue: a.areaSource,
                  title: Text(e.value),
                  subtitle: Text(AnalysisPrefs.areaSourceHints[e.key]!,
                      style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5, height: 1.4)),
                  onChanged: (v) => change((x) => x.copyWith(areaSource: v)),
                ),
            ]),

            const SectionLabel("أسماء المناطق"),
            SettingsGroup(children: [
              NavTile(
                icon: Icons.edit_location_alt_outlined,
                color: AppColors.accentAmber,
                title: "المناطق: تسمية ودمج وإخفاء",
                subtitle: "$renamed اسم متغيّر أو مدموج • ${a.hidden.length} مخفية",
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const AreasPage())),
              ),
            ]),

            const SectionLabel("الطلبات اللي بتتحسب"),
            SettingsGroup(children: [
              SwitchTile(
                icon: Icons.touch_app_outlined,
                title: "الطلبات اللي فتحتها بس",
                subtitle: "يتجاهل الطلبات اللي ظهرت في القائمة ومتفتحتش",
                value: a.onlyOpened,
                onChanged: (v) => change((x) => x.copyWith(onlyOpened: v)),
              ),
              SwitchTile(
                icon: Icons.straighten,
                title: "تجاهل الطلبات اللي مسافتها مش مقروءة",
                subtitle: "من كل الأرقام، مش بس الربح (الربح أصلاً مش بيحسبها)",
                value: a.excludeEstimated,
                onChanged: (v) => change((x) => x.copyWith(excludeEstimated: v)),
              ),
            ]),

            const SizedBox(height: Insets.lg),
            Center(
              child: TextButton.icon(
                icon: const Icon(Icons.restart_alt),
                label: const Text("رجّع طريقة التحليل الافتراضية"),
                onPressed: a.isDefault ? null : () => provider.updateAnalysis(const AnalysisPrefs()),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// =============================================================================
// AREAS: rename, merge, hide
// =============================================================================

/// One area as shown in the Insights tab, with the names it was read as.
class _AreaRow {
  final String name;
  final List<String> sources; // names as read, merged into [name]
  final int count;
  final bool hidden;
  const _AreaRow(this.name, this.sources, this.count, this.hidden);
}

class AreasPage extends StatefulWidget {
  const AreasPage({super.key});

  @override
  State<AreasPage> createState() => _AreasPageState();
}

class _AreasPageState extends State<AreasPage> {
  final Set<String> _selected = {};
  String _query = "";

  void _change(AnalysisPrefs Function(AnalysisPrefs a) c) => _updateAnalysis(context, c);

  List<_AreaRow> _rows(AssistantProvider p) {
    final raw = _allTime(p).rawAreaCounts;
    final a = p.analysis;
    final byName = <String, List<String>>{};
    raw.forEach((source, _) => byName.putIfAbsent(a.rename(source), () => []).add(source));
    final rows = byName.entries
        .map((e) => _AreaRow(e.key, e.value, e.value.fold(0, (s, n) => s + raw[n]!), a.isHidden(e.key)))
        .toList()
      ..sort((x, y) => y.count.compareTo(x.count));
    return rows;
  }

  Future<String?> _askName(String title, String initial) {
    final ctrl = TextEditingController(text: initial);
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(hintText: "اسم المنطقة"),
          onSubmitted: (v) => Navigator.pop(ctx, v.trim()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("إلغاء")),
          FilledButton(onPressed: () => Navigator.pop(ctx, ctrl.text.trim()), child: const Text("حفظ")),
        ],
      ),
    );
  }

  Future<void> _rename(_AreaRow row) async {
    final name = await _askName("اسم جديد لـ «${row.name}»", row.name);
    if (name == null || name.isEmpty || name == row.name) return;
    _change((a) => a.merge([row.name, ...row.sources], name));
  }

  Future<void> _mergeSelected(List<_AreaRow> rows) async {
    final picked = rows.where((r) => _selected.contains(r.name)).toList();
    if (picked.length < 2) return;
    final target = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text("الاسم اللي يفضل"),
        children: [
          for (final r in picked)
            SimpleDialogOption(onPressed: () => Navigator.pop(ctx, r.name), child: Text("${r.name} (${r.count})")),
          SimpleDialogOption(
            onPressed: () => Navigator.pop(ctx, "\u0000"),
            child: const Text("اسم جديد…", style: TextStyle(color: AppColors.accentCyan)),
          ),
        ],
      ),
    );
    if (target == null) return;
    final name = target == "\u0000" ? await _askName("اسم المنطقة بعد الدمج", picked.first.name) : target;
    if (name == null || name.isEmpty) return;
    _change((a) => a.merge([for (final r in picked) ...[r.name, ...r.sources]], name));
    setState(_selected.clear);
  }

  void _toggleHidden(_AreaRow row) {
    _change((a) {
      final h = Set<String>.from(a.hidden);
      if (row.hidden) {
        h.removeWhere((x) => AnalysisPrefs(hidden: {x}).isHidden(row.name));
      } else {
        h.add(row.name);
      }
      return a.copyWith(hidden: h);
    });
  }

  void _actions(_AreaRow row) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: Text(row.name, style: const TextStyle(fontWeight: FontWeight.w700)),
              subtitle: row.sources.length > 1 || row.sources.first != row.name
                  ? Text("بيتقري كـ: ${row.sources.join("، ")}")
                  : null,
            ),
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text("إعادة تسمية"),
              onTap: () {
                Navigator.pop(ctx);
                _rename(row);
              },
            ),
            ListTile(
              leading: const Icon(Icons.merge_type),
              title: const Text("دمج مع مناطق تانية"),
              subtitle: const Text("اختار المناطق اللي هي نفس المكان وبعدين «دمج»"),
              onTap: () {
                Navigator.pop(ctx);
                setState(() => _selected.add(row.name));
              },
            ),
            ListTile(
              leading: Icon(row.hidden ? Icons.visibility_outlined : Icons.visibility_off_outlined),
              title: Text(row.hidden ? "إظهار في التحليلات" : "إخفاء من التحليلات"),
              subtitle: row.hidden ? null : const Text("مثلاً اسم مضلل أو مش منطقة أصلاً"),
              onTap: () {
                Navigator.pop(ctx);
                _toggleHidden(row);
              },
            ),
            if (row.sources.length > 1 || row.sources.first != row.name)
              ListTile(
                leading: const Icon(Icons.call_split),
                title: const Text("فك الدمج ورجّع الأسماء الأصلية"),
                onTap: () {
                  Navigator.pop(ctx);
                  _change((a) => a.unmerge(row.name));
                },
              ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final a = provider.analysis;
    final rows = _rows(provider);
    final visible = {for (final r in rows) if (!r.hidden) r.name: r.count};
    final suggestions = AnalysisPrefs.suggestMerges(visible, ignore: a.notSame);
    final q = _query.trim();
    final shown = q.isEmpty ? rows : rows.where((r) => [r.name, ...r.sources].any((n) => n.contains(q))).toList();
    final selecting = _selected.isNotEmpty;

    return Scaffold(
      appBar: AppBar(
        title: Text(selecting ? "اختار المناطق (${_selected.length})" : "المناطق في التحليلات"),
        leading: selecting ? IconButton(icon: const Icon(Icons.close), onPressed: () => setState(_selected.clear)) : null,
      ),
      floatingActionButton: _selected.length >= 2
          ? FloatingActionButton.extended(
              icon: const Icon(Icons.merge_type),
              label: Text("دمج ${_selected.length}"),
              onPressed: () => _mergeSelected(rows),
            )
          : null,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, 96),
          children: [
            const SizedBox(height: Insets.sm),
            const Text(
              "دوس على منطقة عشان تغيّر اسمها أو تخفيها، أو اضغط مطوّل واختار كذا منطقة وادمجهم لو هما نفس المكان "
              "(زي اسم عربي واسم إنجليزي). التغيير بيطبق على كل الطلبات القديمة والجديدة، والسجل نفسه مش بيتغيّر.",
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.5),
            ),

            if (suggestions.isNotEmpty) ...[
              SectionLabel("غالباً نفس المكان (${suggestions.length})"),
              for (final g in suggestions.take(10))
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: AppCard(
                    borderColor: AppColors.accentAmber.withValues(alpha: 0.4),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(g.map((n) => "$n (${visible[n]})").join("  •  "),
                            style: const TextStyle(fontWeight: FontWeight.w600, height: 1.5)),
                        const SizedBox(height: Insets.sm),
                        Row(
                          children: [
                            FilledButton.tonalIcon(
                              icon: const Icon(Icons.merge_type, size: 18),
                              label: Text("ادمج باسم «${g.first}»"),
                              onPressed: () => _change((x) => x.merge(g, g.first)),
                            ),
                            const SizedBox(width: Insets.sm),
                            TextButton(
                              onPressed: () => _change(
                                  (x) => x.copyWith(notSame: {...x.notSame, AnalysisPrefs.skeleton(g.first)})),
                              child: const Text("مش نفس المكان"),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
            ],

            const SizedBox(height: Insets.md),
            TextField(
              decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: "دوّر على منطقة"),
              onChanged: (v) => setState(() => _query = v),
            ),
            SectionLabel("كل المناطق (${rows.length})"),
            if (rows.isEmpty)
              const EmptyState(
                icon: Icons.place_outlined,
                title: "مفيش مناطق لسه",
                message: "المناطق بتظهر هنا بعد ما المساعد يسجل طلبات من إن درايف",
              )
            else
              SettingsGroup(children: [
                for (final r in shown)
                  ListTile(
                    leading: selecting
                        ? Checkbox(
                            value: _selected.contains(r.name),
                            onChanged: (_) => setState(() =>
                                _selected.contains(r.name) ? _selected.remove(r.name) : _selected.add(r.name)),
                          )
                        : Icon(r.hidden ? Icons.visibility_off_outlined : Icons.place_outlined,
                            color: r.hidden ? AppColors.textTertiary : AppColors.accentAmber),
                    title: Text(
                      r.name,
                      style: TextStyle(
                        decoration: r.hidden ? TextDecoration.lineThrough : null,
                        color: r.hidden ? AppColors.textTertiary : null,
                      ),
                    ),
                    subtitle: r.sources.length > 1 || r.sources.first != r.name
                        ? Text("مدموجة: ${r.sources.join("، ")}",
                            maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12))
                        : null,
                    trailing: Text("${r.count}", style: const TextStyle(color: AppColors.textSecondary)),
                    onTap: selecting
                        ? () => setState(
                            () => _selected.contains(r.name) ? _selected.remove(r.name) : _selected.add(r.name))
                        : () => _actions(r),
                    onLongPress: () => setState(() => _selected.add(r.name)),
                  ),
              ]),
          ],
        ),
      ),
    );
  }
}
