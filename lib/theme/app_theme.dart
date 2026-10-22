import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app_colors.dart';

class AppTheme {
  static TextTheme _textTheme() {
    const base = TextStyle(color: AppColors.textPrimary, height: 1.35);
    return TextTheme(
      displaySmall: base.copyWith(fontSize: 34, fontWeight: FontWeight.w800, letterSpacing: -0.5),
      headlineSmall: base.copyWith(fontSize: 22, fontWeight: FontWeight.w800),
      titleLarge: base.copyWith(fontSize: 18, fontWeight: FontWeight.w700),
      titleMedium: base.copyWith(fontSize: 15, fontWeight: FontWeight.w700),
      titleSmall: base.copyWith(fontSize: 13.5, fontWeight: FontWeight.w600),
      bodyLarge: base.copyWith(fontSize: 15),
      bodyMedium: base.copyWith(fontSize: 13.5),
      bodySmall: base.copyWith(fontSize: 12, color: AppColors.textSecondary),
      labelLarge: base.copyWith(fontSize: 14, fontWeight: FontWeight.w700),
      labelMedium: base.copyWith(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.textSecondary),
      labelSmall: base.copyWith(fontSize: 12, color: AppColors.textTertiary),
    );
  }

  static ThemeData get darkTheme {
    final textTheme = _textTheme();
    final shape12 = RoundedRectangleBorder(borderRadius: BorderRadius.circular(Insets.radiusMd));

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: AppColors.darkBackground,
      textTheme: textTheme,
      colorScheme: const ColorScheme.dark(
        primary: AppColors.emeraldPrimary,
        onPrimary: Color(0xFF04140D),
        primaryContainer: Color(0xFF0E3B2C),
        onPrimaryContainer: AppColors.emeraldLight,
        secondary: AppColors.accentCyan,
        onSecondary: AppColors.darkBackground,
        surface: AppColors.darkSurface,
        onSurface: AppColors.textPrimary,
        surfaceContainerHighest: AppColors.darkSurfaceVariant,
        outline: AppColors.darkCardBorder,
        outlineVariant: AppColors.divider,
        error: AppColors.lossRed,
      ),
      dividerTheme: const DividerThemeData(color: AppColors.divider, thickness: 1, space: 1),
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.darkBackground,
        surfaceTintColor: Colors.transparent,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: textTheme.titleLarge,
        systemOverlayStyle: const SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.light,
          systemNavigationBarColor: AppColors.darkSurface,
          systemNavigationBarIconBrightness: Brightness.light,
        ),
      ),
      cardTheme: CardThemeData(
        color: AppColors.darkSurface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(Insets.radiusLg),
          side: const BorderSide(color: AppColors.darkCardBorder),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.darkSurface,
        surfaceTintColor: Colors.transparent,
        indicatorColor: AppColors.emeraldPrimary.withValues(alpha: 0.16),
        height: 66,
        elevation: 0,
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return TextStyle(
            fontSize: 12,
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: selected ? AppColors.emeraldLight : AppColors.textSecondary,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(color: selected ? AppColors.emeraldLight : AppColors.textSecondary, size: 24);
        }),
      ),
      sliderTheme: SliderThemeData(
        activeTrackColor: AppColors.emeraldPrimary,
        inactiveTrackColor: AppColors.surfaceHigh,
        thumbColor: AppColors.emeraldLight,
        overlayColor: AppColors.emeraldPrimary.withValues(alpha: 0.18),
        trackHeight: 4,
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 9),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.selected) ? Colors.white : AppColors.textSecondary),
        trackColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.selected) ? AppColors.emeraldPrimary : AppColors.surfaceHigh),
        trackOutlineColor: WidgetStateProperty.all(Colors.transparent),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: AppColors.darkSurface,
        surfaceTintColor: Colors.transparent,
        titleTextStyle: textTheme.titleMedium,
        contentTextStyle: textTheme.bodyMedium,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Insets.radiusLg)),
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: AppColors.darkSurface,
        surfaceTintColor: Colors.transparent,
        showDragHandle: true,
        dragHandleColor: AppColors.surfaceHigh,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(Insets.radiusLg))),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: AppColors.surfaceHigh,
        contentTextStyle: textTheme.bodyMedium,
        actionTextColor: AppColors.emeraldLight,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Insets.radiusSm)),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.darkSurfaceVariant,
        selectedColor: AppColors.emeraldPrimary.withValues(alpha: 0.22),
        side: const BorderSide(color: AppColors.darkCardBorder),
        labelStyle: textTheme.labelMedium!.copyWith(color: AppColors.textPrimary),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Insets.radiusSm)),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.darkSurfaceVariant,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        hintStyle: textTheme.bodyMedium!.copyWith(color: AppColors.textTertiary),
        labelStyle: textTheme.bodyMedium!.copyWith(color: AppColors.textSecondary),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Insets.radiusMd),
          borderSide: const BorderSide(color: AppColors.darkCardBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Insets.radiusMd),
          borderSide: const BorderSide(color: AppColors.darkCardBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Insets.radiusMd),
          borderSide: const BorderSide(color: AppColors.emeraldPrimary, width: 1.5),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.emeraldPrimary,
          foregroundColor: const Color(0xFF04140D),
          elevation: 0,
          minimumSize: const Size(64, 48),
          textStyle: textTheme.labelLarge,
          shape: shape12,
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.emeraldPrimary,
          foregroundColor: const Color(0xFF04140D),
          minimumSize: const Size(64, 48),
          textStyle: textTheme.labelLarge,
          shape: shape12,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.textPrimary,
          side: const BorderSide(color: AppColors.darkCardBorder),
          minimumSize: const Size(64, 46),
          textStyle: textTheme.labelLarge,
          shape: shape12,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: AppColors.emeraldLight, textStyle: textTheme.labelLarge),
      ),
      listTileTheme: const ListTileThemeData(
        iconColor: AppColors.textSecondary,
        textColor: AppColors.textPrimary,
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 2),
      ),
      segmentedButtonTheme: SegmentedButtonThemeData(
        style: ButtonStyle(
          backgroundColor: WidgetStateProperty.resolveWith((s) =>
              s.contains(WidgetState.selected) ? AppColors.emeraldPrimary.withValues(alpha: 0.2) : AppColors.darkSurfaceVariant),
          foregroundColor: WidgetStateProperty.resolveWith(
              (s) => s.contains(WidgetState.selected) ? AppColors.emeraldLight : AppColors.textSecondary),
          side: WidgetStateProperty.all(const BorderSide(color: AppColors.darkCardBorder)),
          textStyle: WidgetStateProperty.all(const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600)),
        ),
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.emeraldPrimary,
        linearTrackColor: AppColors.surfaceHigh,
      ),
    );
  }
}
