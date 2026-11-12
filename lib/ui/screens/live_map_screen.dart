import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../data/models/hud_settings.dart';
import '../../providers/assistant_provider.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';
import '../components/dialogs.dart';
import '../components/ui_kit.dart';

/// Requests board: every request read from inDrive as a card with a snapshot of inDrive's own map
/// of the trip, the price and the verdict, and accept / hide / bid / own price from the card.
/// Shown as a floating window over inDrive, next to inDrive in split screen, or full screen.
class LiveMapPage extends StatelessWidget {
  const LiveMapPage({super.key});

  static const Map<String, String> sizeLabels = {"SMALL": "صغيرة", "MEDIUM": "متوسطة", "LARGE": "كبيرة"};

  static String summary(HudSettings h) {
    if (!h.liveMapEnabled) return "متوقفة";
    return h.liveMapFloating ? "مفعّلة • عائمة فوق إن درايف" : "مفعّلة";
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final hud = provider.hud;
    void change(HudSettings Function(HudSettings h) c) => provider.updateHud(c(provider.hud));

    Future<void> enable(bool v) async {
      if (v &&
          !await confirmAction(
            context,
            title: "لوحة الطلبات",
            message: "كل طلب بيظهر في إن درايف هيبقى كارت فيه صورة خريطة إن درايف للمشوار، والسعر، والصافي.\n\n"
                "• عشان ياخد صورة الخريطة، التطبيق بيفتح كل طلب جديد بسرعة ويرجع للقائمة (تقدر تقفل ده تحت).\n"
                "• في المشاوير الطويلة بيصغّر خريطة إن درايف لحد ما المشوار كله يبان.\n"
                "• أزرار القبول والإخفاء والعرض والسعر بتاعك بتضغط فعلاً في إن درايف، زي ما تكون ضغطت بإيدك.\n"
                "• فتح الطلبات بسرعة وباستمرار أوضح من الضغط العادي، وممكن يخالف شروط إن درايف ويعرّض حسابك للإيقاف. انت المسؤول عن استخدامه.",
            confirmLabel: "أفهم، فعّل",
            destructive: false,
          )) {
        return;
      }
      change((h) => h.copyWith(liveMapEnabled: v, liveMapFloating: v ? true : null, readMapDistance: v ? true : null));
    }

    return Scaffold(
      appBar: AppBar(title: const Text("لوحة الطلبات")),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, Insets.sm, Insets.lg, Insets.xl),
          children: [
            const Text(
              "شوف كل الطلبات مع بعض: كل طلب كارت عليه صورة المسار من خريطة إن درايف نفسها، والسعر ولونه حسب الحكم. "
              "اقبل أو اخفي أو دوس على عرض جاهز، أو حط سعرك بـ − و + وابعته، من غير ما تفتح الطلب بإيدك.",
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.5),
            ),
            const SizedBox(height: Insets.md),
            SettingsGroup(children: [
              SwitchTile(
                icon: Icons.view_agenda_outlined,
                iconColor: AppColors.accentCyan,
                title: "تشغيل لوحة الطلبات",
                subtitle: "محتاجة قراءة الشاشة شغالة، وأندرويد 11 أو أحدث لصور المسار",
                value: hud.liveMapEnabled,
                onChanged: enable,
              ),
            ]),
            if (hud.liveMapEnabled) ...[
              const SectionLabel("طريقة العرض"),
              SettingsGroup(children: [
                SwitchTile(
                  icon: Icons.picture_in_picture_alt_outlined,
                  iconColor: AppColors.accentCyan,
                  title: "لوحة عائمة فوق إن درايف",
                  subtitle: "شباك تحرّكه من شريطه، تكبّره بـ ⤢ وتصغّره لزرار بـ –. محتاج المساعد العائم شغال",
                  value: hud.liveMapFloating,
                  onChanged: (v) => change((h) => h.copyWith(liveMapFloating: v)),
                ),
                NavTile(
                  icon: Icons.vertical_split_outlined,
                  color: AppColors.accentCyan,
                  title: "تقسيم الشاشة مع إن درايف",
                  subtitle: "اللوحة في نص الشاشة وإن درايف في النص التاني، والاتنين بيتحدّثوا لحظة بلحظة",
                  onTap: () => NativeBridgeService.openLiveMap(split: true),
                ),
                NavTile(
                  icon: Icons.fullscreen,
                  color: AppColors.accentCyan,
                  title: "افتح اللوحة على الشاشة كلها",
                  subtitle: "بتعرض الطلبات اللي اتقرت آخر شوية. عشان تقبل لازم إن درايف يبقى ظاهر",
                  onTap: () => NativeBridgeService.openLiveMap(split: false),
                ),
              ]),
              const SectionLabel("صورة المسار"),
              SettingsGroup(children: [
                SwitchTile(
                  icon: Icons.bolt_outlined,
                  iconColor: AppColors.warningYellow,
                  title: "افتح كل طلب جديد بسرعة وصوّره",
                  subtitle: "بيفتح الطلب، يستنى المسار يترسم، ياخد الصورة ويرجع على طول (حوالي ثانية لكل طلب). "
                      "لو مقفول: الصورة بتتاخد بس لما تفتح الطلب بنفسك",
                  value: hud.boardAutoOpen,
                  onChanged: (v) => change((h) => h.copyWith(boardAutoOpen: v)),
                ),
                SwitchTile(
                  icon: Icons.alt_route,
                  title: "اعرض طريق الوصول للعميل كمان",
                  subtitle: "لو مقفول: الصورة بتتقص على المشوار نفسه من A لـ B",
                  value: hud.boardShowApproach,
                  onChanged: (v) => change((h) => h.copyWith(boardShowApproach: v)),
                ),
              ]),
              const SectionLabel("الإعدادات"),
              SettingsGroup(children: [
                if (hud.liveMapFloating)
                  ChoiceTile<String>(
                    icon: Icons.aspect_ratio,
                    title: "حجم اللوحة العائمة",
                    value: hud.liveMapSize,
                    options: sizeLabels,
                    onChanged: (v) => change((h) => h.copyWith(liveMapSize: v)),
                  ),
                if (hud.liveMapFloating)
                  SwitchTile(
                    icon: Icons.layers_clear_outlined,
                    title: "اخفيها لما أقفل إن درايف",
                    subtitle: "اللوحة العائمة تظهر بس وإن درايف قدامك",
                    value: hud.liveMapOnlyInDrive,
                    onChanged: (v) => change((h) => h.copyWith(liveMapOnlyInDrive: v)),
                  ),
                NumberStepperTile(
                  icon: Icons.timelapse,
                  title: "الطلب يفضل في اللوحة",
                  subtitle: "بعد ما يختفي من شاشة إن درايف (بيبان باهت)",
                  unit: "ثانية",
                  value: hud.liveMapKeepSeconds.toDouble(),
                  step: 15,
                  min: 15,
                  max: 600,
                  onChanged: (v) => change((h) => h.copyWith(liveMapKeepSeconds: v.round())),
                ),
                SwitchTile(
                  icon: Icons.touch_app_outlined,
                  iconColor: AppColors.warningYellow,
                  title: "تأكيد قبل القبول",
                  subtitle: "زرار القبول محتاج ضغطة تانية خلال 3 ثواني. اقفله لو عايز أسرع قبول",
                  value: hud.liveMapConfirmAccept,
                  onChanged: (v) => change((h) => h.copyWith(liveMapConfirmAccept: v)),
                ),
              ]),
              const SizedBox(height: Insets.lg),
              Center(
                child: TextButton.icon(
                  icon: const Icon(Icons.cleaning_services_outlined),
                  label: const Text("امسح الطلبات من اللوحة"),
                  onPressed: () async {
                    await NativeBridgeService.clearLiveMap();
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("اتمسحت")));
                    }
                  },
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
