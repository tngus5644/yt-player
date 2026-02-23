import 'package:flutter/material.dart';

abstract class AppColors {
  // Primary
  static const Color primary = Color(0xFFCF5140);
  static const Color primaryDark = Color(0xFFB5382A);

  // Video List
  static const Color videoTitle = Color(0xFF0A0A0A);
  static const Color videoInfo = Color(0xFF6C6C6C);
  static const Color videoBg = Color(0xFFFFFFFF);

  // Bottom Navigation
  static const Color bottomNavBg = Color(0xFFF7F7F7);
  static const Color bottomNavItemOn = primary;
  static const Color bottomNavItemOff = Color(0xFF000000);

  // Feature Colors
  static const Color watchHistory = Color(0xFF66BB6A);
  static const Color videoSearch = Color(0xFF42A5F5);
  static const Color videoStorage = Color(0xFFD9CB71);

  // Status Colors
  static const Color liveBadge = Color(0xFFFC0007);
  static const Color newBadge = Color(0xFF3392FF);

  // Channel
  static const Color channelName = Color(0xFF0D0D0D);

  // General
  static const Color background = Color(0xFFFFFFFF);
  static const Color surface = Color(0xFFF7F7F7);
  static const Color textPrimary = Color(0xFF0A0A0A);
  static const Color textSecondary = Color(0xFF6C6C6C);
  static const Color divider = Color(0xFFE0E0E0);
}
