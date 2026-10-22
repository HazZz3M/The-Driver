import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../providers/assistant_provider.dart';
import '../components/rating_sheet.dart';
import 'calculator_screen.dart';
import 'expenses_screen.dart';
import 'insights_screen.dart';
import 'overlay_control_screen.dart';
import 'settings_screen.dart';
import 'shift_dashboard_screen.dart';

class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({super.key});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> with WidgetsBindingObserver {
  int _currentIndex = 0;
  final Set<int> _loadedIndices = {0};
  bool _ratingOpen = false;

  static const List<Widget> _screens = [
    CalculatorScreen(),
    OverlayControlScreen(),
    ShiftDashboardScreen(),
    InsightsScreen(),
    ExpensesScreen(),
    SettingsScreen(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _askRatingIfDue());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _askRatingIfDue();
  }

  /// After an accepted ride (the HUD posts a "rate your ride" notification), ask once.
  Future<void> _askRatingIfDue() async {
    if (_ratingOpen) return;
    final provider = context.read<AssistantProvider>();
    await provider.loadRequestLog();
    final ride = provider.rideAwaitingRating;
    if (ride == null || !mounted) return;
    _ratingOpen = true;
    final saved = await showRatingSheet(context, ride);
    _ratingOpen = false;
    if (!saved) provider.dismissRating(ride.id);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: List.generate(
          _screens.length,
          (i) => _loadedIndices.contains(i) ? _screens[i] : const SizedBox.shrink(),
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        // Six tabs: only the selected one shows its label, so none get cut on small phones.
        labelBehavior: NavigationDestinationLabelBehavior.onlyShowSelected,
        onDestinationSelected: (index) {
          HapticFeedback.selectionClick();
          setState(() {
            _currentIndex = index;
            _loadedIndices.add(index);
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.calculate_outlined),
            selectedIcon: Icon(Icons.calculate),
            label: "الحاسبة",
          ),
          NavigationDestination(
            icon: Icon(Icons.bubble_chart_outlined),
            selectedIcon: Icon(Icons.bubble_chart),
            label: "المساعد",
          ),
          NavigationDestination(
            icon: Icon(Icons.insights_outlined),
            selectedIcon: Icon(Icons.insights),
            label: "الوردية",
          ),
          NavigationDestination(
            icon: Icon(Icons.query_stats_outlined),
            selectedIcon: Icon(Icons.query_stats),
            label: "التحليلات",
          ),
          NavigationDestination(
            icon: Icon(Icons.directions_car_outlined),
            selectedIcon: Icon(Icons.directions_car),
            label: "المركبة",
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: "الإعدادات",
          ),
        ],
      ),
    );
  }
}
