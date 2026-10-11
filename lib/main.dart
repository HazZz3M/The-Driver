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

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AssistantProvider(repository)),
      ],
      child: const InDriveAssistantApp(),
    ),
  );
}

class InDriveAssistantApp extends StatelessWidget {
  const InDriveAssistantApp({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AssistantProvider>();
    final isDark = provider.themeMode != "LIGHT";

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
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: isDark ? ThemeMode.dark : ThemeMode.light,
      home: const MainNavigationScreen(),
    );
  }
}
