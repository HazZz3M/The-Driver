import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../data/models/hud_settings.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import '../components/ui_kit.dart';
import 'automation_screen.dart';
import 'settings_pages.dart';
import 'tutorial_screen.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  void _open(BuildContext context, Widget page) =>
      Navigator.push(context, MaterialPageRoute(builder: (_) => page));

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final s = provider.driverSettings;
    final hud = provider.hud;

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.xl),
          children: [
            const PageHeader(title: "الإعدادات", subtitle: "خصّص المساعد حسب سيارتك وطريقة عملك"),

            const SectionLabel("المساعد العائم"),
            SettingsGroup(children: [
              NavTile(
                icon: Icons.smart_toy_outlined,
                color: AppColors.warningYellow,
                title: "الأتمتة",
                subtitle: "مفتاح واحد لكل الأتمتة، وسجل اللي عملته",
                onTap: () => _open(context, const AutomationScreen()),
              ),
              NavTile(
                icon: Icons.palette_outlined,
                title: "العلامة العائمة",
                subtitle: "${HudSettings.styleLabels[hud.style]} • ${HudSettings.primaryValueLabels[hud.primaryValue]}",
                onTap: () => _open(context, const HudSettingsPage()),
              ),
              NavTile(
                icon: Icons.notifications_active_outlined,
                title: "الاهتزاز والصوت",
                subtitle: AlertsPage.summary(hud),
                onTap: () => _open(context, const AlertsPage()),
              ),
              NavTile(
                icon: Icons.bolt_outlined,
                color: AppColors.warningYellow,
                title: "القبول التلقائي",
                subtitle: "شروط القبول، وإخفاء الطلبات الخاسرة والمحظورة",
                trailingText: s.autoAcceptEnabled ? "مفعّل" : "متوقف",
                onTap: () => _open(context, const AutoAcceptPage()),
              ),
              NavTile(
                icon: Icons.place_outlined,
                color: AppColors.accentAmber,
                title: "المناطق",
                subtitle: "${s.favoriteZones.length} مفضلة • ${s.blacklistedZones.length} محظورة",
                onTap: () => _open(context, const ZonesPage()),
              ),
              NavTile(
                icon: Icons.people_alt_outlined,
                color: AppColors.accentAmber,
                title: "العملاء",
                subtitle: "${s.favoritePassengers.length} مميز • ${s.blacklistedPassengers.length} محظور",
                onTap: () => _open(context, const PassengersPage()),
              ),
            ]),

            const SectionLabel("الحسابات"),
            SettingsGroup(children: [
              NavTile(
                icon: Icons.tune,
                color: AppColors.accentCyan,
                title: "التسعير والتكاليف",
                subtitle:
                    "عمولة ${formatNumber(s.inDriveCommissionPercent)}% • هدف ${formatNumber(s.minProfitPerKm)} ${s.currency}/كم"
                    "${s.pickupPricingEnabled ? " • الوصول ${formatNumber(s.pickupProfitPerKm)}" : ""}",
                onTap: () => _open(context, const PricingPage()),
              ),
            ]),

            const SectionLabel("التطبيق"),
            SettingsGroup(children: [
              NavTile(
                icon: Icons.school_outlined,
                color: AppColors.emeraldPrimary,
                title: "شرح التطبيق",
                subtitle: "جولة سريعة على كل المميزات",
                onTap: () => _open(context, const TutorialScreen()),
              ),
              NavTile(
                icon: Icons.system_update_outlined,
                color: AppColors.accentPurple,
                title: "التحديثات وحول التطبيق",
                onTap: () => _open(context, const UpdatesPage()),
              ),
            ]),
          ],
        ),
      ),
    );
  }
}
