import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../data/models/hud_settings.dart';
import '../../providers/assistant_provider.dart';
import '../../services/native_bridge_service.dart';
import '../../theme/app_colors.dart';
import '../components/dialogs.dart';
import '../components/ui_kit.dart';

/// Live requests map: every passenger as a pin at their pickup with their route to the destination,
/// placed from inDrive's own map and the driver's position (not from the address text), and
/// accept / hide / inDrive's price buttons from the map. Floating over inDrive, next to it in split
/// screen, or full screen.
class LiveMapPage extends StatefulWidget {
  const LiveMapPage({super.key});

  static const Map<String, String> sizeLabels = {"SMALL": "صغيرة", "MEDIUM": "متوسطة", "LARGE": "كبيرة"};

  static String summary(HudSettings h) {
    if (!h.liveMapEnabled) return "متوقفة";
    return h.liveMapFloating ? "مفعّلة • عائمة فوق إن درايف" : "مفعّلة";
  }

  @override
  State<LiveMapPage> createState() => _LiveMapPageState();
}

class _LiveMapPageState extends State<LiveMapPage> with WidgetsBindingObserver {
  bool _location = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkLocation();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _checkLocation();
  }

  Future<void> _checkLocation() async {
    final ok = await NativeBridgeService.hasLocationPermission();
    if (mounted) setState(() => _location = ok);
  }

  Future<void> _askLocation() async {
    await NativeBridgeService.requestLocationPermission();
    await Future.delayed(const Duration(seconds: 1));
    await _checkLocation();
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
            title: "خريطة الطلبات",
            message: "كل راكب هيظهر على الخريطة بدبوس عند مكانه عليه السعر، ومسار المشوار لحد ما هو رايح.\n\n"
                "• الأماكن بتتحسب من خريطة إن درايف نفسها ومن مكانك (إذن الموقع)، مش من اسم العنوان.\n"
                "• عشان كده التطبيق بيفتح كل طلب جديد بسرعة ويرجع للقائمة، وفي المشاوير الطويلة بيصغّر خريطة إن درايف.\n"
                "• زرار القبول والإخفاء والأسعار بيضغط فعلاً في إن درايف، زي ما تكون ضغطت بإيدك.\n"
                "• فتح الطلبات بسرعة وباستمرار ممكن يخالف شروط إن درايف ويعرّض حسابك للإيقاف. انت المسؤول عن استخدامه.",
            confirmLabel: "أفهم، فعّل",
            destructive: false,
          )) {
        return;
      }
      change((h) => h.copyWith(liveMapEnabled: v, liveMapFloating: v ? true : null, readMapDistance: v ? true : null));
      if (v && !_location) await _askLocation();
    }

    return Scaffold(
      appBar: AppBar(title: const Text("خريطة الطلبات")),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, Insets.sm, Insets.lg, Insets.xl),
          children: [
            const Text(
              "شوف كل الركاب على خريطة واحدة: كل راكب دبوس عند مكانه عليه السعر ولونه حسب الحكم، "
              "ومسار مشواره لحد ما هو رايح زي ما إن درايف راسمه. دوس على أي دبوس عشان تقبل أو تخفي أو تختار سعر.",
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.5),
            ),
            const SizedBox(height: Insets.md),
            SettingsGroup(children: [
              SwitchTile(
                icon: Icons.map_outlined,
                iconColor: AppColors.accentCyan,
                title: "تشغيل خريطة الطلبات",
                subtitle: "محتاجة قراءة الشاشة شغالة، وإذن الموقع، وأندرويد 11 أو أحدث",
                value: hud.liveMapEnabled,
                onChanged: enable,
              ),
              if (hud.liveMapEnabled && !_location)
                NavTile(
                  icon: Icons.location_off_outlined,
                  color: AppColors.lossRed,
                  title: "اسمح بالموقع",
                  subtitle: "من غير مكانك الطلبات مش هتتحط على الخريطة",
                  onTap: _askLocation,
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
                  subtitle: "الخريطة في نص الشاشة وإن درايف في النص التاني، والاتنين بيتحدّثوا لحظة بلحظة",
                  onTap: () => NativeBridgeService.openLiveMap(split: true),
                ),
                NavTile(
                  icon: Icons.fullscreen,
                  color: AppColors.accentCyan,
                  title: "افتح الخريطة على الشاشة كلها",
                  subtitle: "بتعرض الطلبات اللي اتقرت آخر شوية. عشان تقبل لازم إن درايف يبقى ظاهر",
                  onTap: () => NativeBridgeService.openLiveMap(split: false),
                ),
              ]),
              const SectionLabel("المسارات"),
              SettingsGroup(children: [
                SwitchTile(
                  icon: Icons.bolt_outlined,
                  iconColor: AppColors.warningYellow,
                  title: "افتح كل طلب جديد بسرعة وحطه على الخريطة",
                  subtitle: "بيفتح الطلب، يستنى المسار يترسم، يحسب الأماكن ويرجع على طول (حوالي ثانية لكل طلب). "
                      "لو مقفول: الطلب بيتحط على الخريطة بس لما تفتحه بنفسك",
                  value: hud.boardAutoOpen,
                  onChanged: (v) => change((h) => h.copyWith(boardAutoOpen: v)),
                ),
                SwitchTile(
                  icon: Icons.alt_route,
                  title: "اعرض طريق الوصول للراكب",
                  subtitle: "المسار الأزرق من مكانك لحد الراكب، مع مسار المشوار",
                  value: hud.boardShowApproach,
                  onChanged: (v) => change((h) => h.copyWith(boardShowApproach: v)),
                ),
              ]),
              const SectionLabel("الإعدادات"),
              SettingsGroup(children: [
                if (hud.liveMapFloating)
                  ChoiceTile<String>(
                    icon: Icons.aspect_ratio,
                    title: "حجم الخريطة العائمة",
                    value: hud.liveMapSize,
                    options: LiveMapPage.sizeLabels,
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
                  subtitle: "زرار القبول محتاج ضغطة تانية خلال 3 ثواني. اقفله لو عايز أسرع قبول",
                  value: hud.liveMapConfirmAccept,
                  onChanged: (v) => change((h) => h.copyWith(liveMapConfirmAccept: v)),
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
