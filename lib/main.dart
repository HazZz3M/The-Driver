import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';
import 'data/local/database_service.dart';
import 'data/local/preferences_manager.dart';
import 'data/repository/assistant_repository.dart';
import 'providers/assistant_provider.dart';
import 'theme/app_theme.dart';
import 'ui/screens/main_navigation_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final preferencesManager = await PreferencesManager.init();
  final databaseService = DatabaseService(preferencesManager.prefs);
  final repository = AssistantRepository(
    database: databaseService,
    preferencesManager: preferencesManager,
  );
  final provider = AssistantProvider(repository);

  runApp(
    ChangeNotifierProvider.value(
      value: provider,
      child: const InDriveAssistantApp(),
    ),
  );

  // Adopt HUD zone edits and push settings to the native HUD (does not block first frame).
  provider.syncWithNative();
}

class InDriveAssistantApp extends StatefulWidget {
  const InDriveAssistantApp({super.key});

  @override
  State<InDriveAssistantApp> createState() => _InDriveAssistantAppState();
}

class _InDriveAssistantAppState extends State<InDriveAssistantApp> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // The driver may have edited zones from the floating HUD while in inDrive.
      context.read<AssistantProvider>().syncWithNative();
    }
  }

  @override
  Widget build(BuildContext context) {
    // The UI is designed as a dark "cockpit" only; there is no light variant.
    return MaterialApp(
      title: 'The Driver (مساعد إن درايف)',
      debugShowCheckedModeBanner: false,
      locale: const Locale('ar', 'EG'),
      supportedLocales: const [
        Locale('ar', 'EG'),
        Locale('en', 'US'),
      ],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: AppTheme.darkTheme,
      themeMode: ThemeMode.dark,
      home: const MainNavigationScreen(),
    );
  }
}
