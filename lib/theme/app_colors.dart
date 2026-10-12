import 'package:flutter/material.dart';

/// Design tokens for the dark "cockpit" UI: calm, low-glare surfaces with a single emerald
/// accent and clear semantic colours for money (profit / thin margin / loss).
class AppColors {
  // Brand
  static const Color emeraldPrimary = Color(0xFF10B981);
  static const Color emeraldDark = Color(0xFF059669);
  static const Color emeraldLight = Color(0xFF34D399);

  // Compatibility aliases
  static const Color neonGreen = emeraldPrimary;
  static const Color neonGreenDark = emeraldDark;
  static const Color neonGreenLight = emeraldLight;

  // Surfaces (from back to front)
  static const Color darkBackground = Color(0xFF0A0E17);
  static const Color darkSurface = Color(0xFF121826);
  static const Color darkSurfaceVariant = Color(0xFF1A2233);
  static const Color surfaceHigh = Color(0xFF212B3F);
  static const Color darkCardBorder = Color(0xFF263149);
  static const Color divider = Color(0xFF1E273A);

  // Semantic
  static const Color profitGreen = Color(0xFF10B981);
  static const Color warningYellow = Color(0xFFF5B83D);
  static const Color lossRed = Color(0xFFF0525F);
  static const Color info = Color(0xFF4FA3FF);

  // Accents
  static const Color accentCyan = Color(0xFF38BDF8);
  static const Color accentBlue = Color(0xFF4F7CFF);
  static const Color accentPurple = Color(0xFF9B7BFF);
  static const Color accentAmber = Color(0xFFF5A524);

  // Text
  static const Color textPrimary = Color(0xFFF1F5F9);
  static const Color textSecondary = Color(0xFF9AA6B8);
  static const Color textTertiary = Color(0xFF66738A);

  // Light palette (kept for the unused light theme definition)
  static const Color lightBackground = Color(0xFFF8FAFC);
  static const Color lightSurface = Color(0xFFFFFFFF);
  static const Color lightSurfaceVariant = Color(0xFFF1F5F9);
  static const Color lightCardBorder = Color(0xFFE2E8F0);
  static const Color lightTextPrimary = Color(0xFF0F172A);
  static const Color lightTextSecondary = Color(0xFF475569);
  static const Color lightTextTertiary = Color(0xFF94A3B8);
}

/// Spacing and radius scale used across the app.
class Insets {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;

  static const double radiusSm = 10;
  static const double radiusMd = 14;
  static const double radiusLg = 20;
}
