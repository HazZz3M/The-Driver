import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../data/models/hud_settings.dart';
import '../../providers/assistant_provider.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';
import '../components/dialogs.dart';
import '../components/ui_kit.dart';

/// Live requests map: the requests read from inDrive on an OpenStreetMap map, each with a line to
/// its destination, and accept / hide / bid from the map. Shown as a floating window over inDrive,
/// next to inDrive in split screen, or as a screen of its own.
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
            title: "خريطة الطلبات المباشرة",
            message: "كل طلب بيظهر في إن درايف هيتحط على الخريطة، بخط من مكان العميل لمكان وصوله.\n\n"
                "• عشان يتحط على الخريطة، نص العنوان بيتبعت لخدمة العناوين بتاعة جوجل (مش اسم الراكب).\n"
                "• الخريطة نفسها من OpenStreetMap ومحتاجة إنترنت.\n"
                "• زرار القبول والإخفاء والعرض على الخريطة بيضغط فعلاً في إن درايف، زي ما تكون ضغطت بإيدك.\n"
                "• الطلبات بتتقري بس وإن درايف ظاهر على الشاشة (لوحده أو متقسّم مع الخريطة).",
            confirmLabel: "موافق، فعّل",
            destructive: false,
          )) {
        return;
      }
      change((h) => h.copyWith(liveMapEnabled: v, liveMapFloating: v ? true : null));
      if (v && hud.liveMapShowMe) await NativeBridgeService.requestLocationPermission();
    }

    return Scaffold(
      appBar: AppBar(title: const Text("خريطة الطلبات")),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, Insets.sm, Insets.lg, Insets.xl),
          children: [
            const Text(
              "شوف كل الطلبات اللي في إن درايف على خريطة واحدة: كل طلب دبوس عند مكان العميل عليه السعر ولونه حسب الحكم، "
              "وخط لحد مكان وصوله. دوس على أي طلب عشان تقبله أو تخفيه أو تعرض سعر.",
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.5),
            ),
            const SizedBox(height: Insets.md),
            SettingsGroup(children: [
              SwitchTile(
                icon: Icons.map_outlined,
                iconColor: AppColors.accentCyan,
                title: "تشغيل خريطة الطلبات",
                subtitle: "محتاجة قراءة الشاشة شغالة",
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
                  title: "خريطة عائمة فوق إن درايف",
                  subtitle: "شباك تحرّكه من شريطه، تكبّره بـ ⤢ وتصغّره لزرار بـ –. محتاج المساعد العائم شغال",
                  value: hud.liveMapFloating,
                  onChanged: (v) => change((h) => h.copyWith(liveMapFloating: v)),
                ),
                NavTile(
                  icon: Icons.vertical_split_outlined,
                  color: AppColors.accentCyan,
                  title: "تقسيم الشاشة مع إن درايف",
                  subtitle: "الخريطة في نص الشاشة وإن درايف في النص التاني، والاتنين live",
                  onTap: () => NativeBridgeService.openLiveMap(split: true),
                ),
                NavTile(
                  icon: Icons.fullscreen,
                  color: AppColors.accentCyan,
                  title: "افتح الخريطة على الشاشة كلها",
                  subtitle: "بتعرض الطلبات اللي اتقرت آخر شوية. عشان تقبل لازم ترجع لإن درايف",
                  onTap: () => NativeBridgeService.openLiveMap(split: false),
                ),
              ]),
              const SectionLabel("الإعدادات"),
              SettingsGroup(children: [
                if (hud.liveMapFloating)
                  ChoiceTile<String>(
                    icon: Icons.aspect_ratio,
                    title: "حجم الخريطة العائمة",
                    value: hud.liveMapSize,
                    options: sizeLabels,
                    onChanged: (v) => change((h) => h.copyWith(liveMapSize: v)),
                  ),
                if (hud.liveMapFloating)
                  SwitchTile(
                    icon: Icons.layers_clear_outlined,
                    title: "اخفيها لما أقفل إن درايف",
                    subtitle: "الخريطة العائمة تظهر بس وإن درايف قدامك",
                    value: hud.liveMapOnlyInDrive,
                    onChanged: (v) => change((h) => h.copyWith(liveMapOnlyInDrive: v)),
                  ),
                NumberStepperTile(
                  icon: Icons.timelapse,
                  title: "الطلب يفضل على الخريطة",
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
                  subtitle: "زرار القبول على الخريطة محتاج ضغطة تانية خلال 3 ثواني",
                  value: hud.liveMapConfirmAccept,
                  onChanged: (v) => change((h) => h.copyWith(liveMapConfirmAccept: v)),
                ),
                SwitchTile(
                  icon: Icons.my_location,
                  title: "اعرض مكاني على الخريطة",
                  subtitle: "محتاج إذن الموقع. بيساعد كمان إن العناوين تتحط في المكان الصح",
                  value: hud.liveMapShowMe,
                  onChanged: (v) async {
                    change((h) => h.copyWith(liveMapShowMe: v));
                    if (v) await NativeBridgeService.requestLocationPermission();
                  },
                ),
              ]),
              const SizedBox(height: Insets.lg),
              Center(
                child: TextButton.icon(
                  icon: const Icon(Icons.cleaning_services_outlined),
                  label: const Text("امسح الطلبات من الخريطة"),
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
