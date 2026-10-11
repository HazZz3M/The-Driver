import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import 'calculator_screen.dart';
import 'expenses_screen.dart';
import 'overlay_control_screen.dart';
import 'settings_screen.dart';
import 'shift_dashboard_screen.dart';

class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({super.key});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  int _currentIndex = 0;
  final Set<int> _loadedIndices = {0};

  final List<Widget> _screenBuilders = const [
    CalculatorScreen(),
    ShiftDashboardScreen(),
    ExpensesScreen(),
    OverlayControlScreen(),
    SettingsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.darkBackground,
      body: IndexedStack(
        index: _currentIndex,
        children: List.generate(
          _screenBuilders.length,
          (index) => _loadedIndices.contains(index)
              ? _screenBuilders[index]
              : const SizedBox.shrink(),
        ),
      ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          border: Border(
            top: BorderSide(color: AppColors.darkCardBorder, width: 1),
          ),
        ),
        child: NavigationBar(
          selectedIndex: _currentIndex,
          backgroundColor: AppColors.darkSurface,
          indicatorColor: AppColors.emeraldPrimary.withOpacity(0.18),
          elevation: 0,
          onDestinationSelected: (index) {
            setState(() {
              _currentIndex = index;
              _loadedIndices.add(index);
            });
          },
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.calculate_outlined, color: AppColors.textSecondary),
              selectedIcon: Icon(Icons.calculate, color: AppColors.emeraldPrimary),
              label: "الحاسبة",
            ),
            NavigationDestination(
              icon: Icon(Icons.dashboard_outlined, color: AppColors.textSecondary),
              selectedIcon: Icon(Icons.dashboard, color: AppColors.emeraldPrimary),
              label: "الوردية",
            ),
            NavigationDestination(
              icon: Icon(Icons.directions_car_outlined, color: AppColors.textSecondary),
              selectedIcon: Icon(Icons.directions_car, color: AppColors.emeraldPrimary),
              label: "المركبات",
            ),
            NavigationDestination(
              icon: Icon(Icons.layers_outlined, color: AppColors.textSecondary),
              selectedIcon: Icon(Icons.layers, color: AppColors.emeraldPrimary),
              label: "الزر العائم",
            ),
            NavigationDestination(
              icon: Icon(Icons.settings_outlined, color: AppColors.textSecondary),
              selectedIcon: Icon(Icons.settings, color: AppColors.emeraldPrimary),
              label: "الإعدادات",
            ),
          ],
        ),
      ),
    );
  }
}
