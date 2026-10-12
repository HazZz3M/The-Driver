import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../theme/app_colors.dart';
import '../../utils/number_parsing.dart';
import 'dialogs.dart';

/// Shared building blocks so every screen looks and behaves the same.

/// Standard page header used at the top of each tab.
class PageHeader extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? trailing;

  const PageHeader({super.key, required this.title, this.subtitle, this.trailing});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(Insets.lg, Insets.lg, Insets.lg, Insets.md),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: t.headlineSmall),
                if (subtitle != null) ...[
                  const SizedBox(height: 2),
                  Text(subtitle!, style: t.bodySmall),
                ],
              ],
            ),
          ),
          if (trailing != null) trailing!,
        ],
      ),
    );
  }
}

/// Small uppercase-style section label above a group of cards / settings.
class SectionLabel extends StatelessWidget {
  final String text;
  final Widget? action;

  const SectionLabel(this.text, {super.key, this.action});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(Insets.xs, Insets.lg, Insets.xs, Insets.sm),
      child: Row(
        children: [
          Expanded(
            child: Text(
              text,
              style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5, fontWeight: FontWeight.w700),
            ),
          ),
          if (action != null) action!,
        ],
      ),
    );
  }
}

/// Rounded surface card with an optional accent border.
class AppCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final Color? borderColor;
  final Color? color;
  final VoidCallback? onTap;

  const AppCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(Insets.lg),
    this.borderColor,
    this.color,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(Insets.radiusLg);
    return Material(
      color: color ?? AppColors.darkSurface,
      shape: RoundedRectangleBorder(
        borderRadius: radius,
        side: BorderSide(color: borderColor ?? AppColors.darkCardBorder),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(padding: padding, child: child),
      ),
    );
  }
}

/// A coloured status capsule ("رابح", "متوقف", ...).
class StatusPill extends StatelessWidget {
  final String label;
  final Color color;
  final IconData? icon;

  const StatusPill({super.key, required this.label, required this.color, this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(99),
        border: Border.all(color: color.withValues(alpha: 0.35)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[Icon(icon, size: 14, color: color), const SizedBox(width: 5)],
          Text(label, style: TextStyle(color: color, fontSize: 11.5, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/// Compact metric tile: label, big value, optional caption.
class StatTile extends StatelessWidget {
  final String label;
  final String value;
  final String? caption;
  final IconData icon;
  final Color color;

  const StatTile({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    this.caption,
    this.color = AppColors.emeraldPrimary,
  });

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: const EdgeInsets.all(Insets.md + 2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, size: 16, color: color),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)),
              ),
            ],
          ),
          const SizedBox(height: 10),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: AlignmentDirectional.centerStart,
            child: Text(value,
                style: const TextStyle(color: AppColors.textPrimary, fontSize: 19, fontWeight: FontWeight.w800)),
          ),
          if (caption != null)
            Text(caption!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: AppColors.textTertiary, fontSize: 11)),
        ],
      ),
    );
  }
}

/// Label / value line used in breakdowns.
class KeyValueRow extends StatelessWidget {
  final String label;
  final String value;
  final bool emphasize;
  final Color? valueColor;

  const KeyValueRow({super.key, required this.label, required this.value, this.emphasize = false, this.valueColor});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                color: emphasize ? AppColors.textPrimary : AppColors.textSecondary,
                fontSize: 13,
                fontWeight: emphasize ? FontWeight.w700 : FontWeight.w400,
              ),
            ),
          ),
          Text(
            value,
            style: TextStyle(
              color: valueColor ?? AppColors.textPrimary,
              fontSize: emphasize ? 15 : 13.5,
              fontWeight: emphasize ? FontWeight.w800 : FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

/// Group of settings rows inside one card, separated by hairlines.
class SettingsGroup extends StatelessWidget {
  final List<Widget> children;

  const SettingsGroup({super.key, required this.children});

  @override
  Widget build(BuildContext context) {
    final items = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      items.add(children[i]);
      if (i < children.length - 1) items.add(const Divider(indent: 56, height: 1));
    }
    return AppCard(padding: EdgeInsets.zero, child: Column(children: items));
  }
}

/// Leading icon in a tinted rounded square.
class TileIcon extends StatelessWidget {
  final IconData icon;
  final Color color;

  const TileIcon(this.icon, {super.key, this.color = AppColors.emeraldPrimary});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 34,
      height: 34,
      decoration: BoxDecoration(color: color.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(10)),
      child: Icon(icon, size: 19, color: color),
    );
  }
}

/// Navigation row that opens a sub-page.
class NavTile extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final String? subtitle;
  final String? trailingText;
  final VoidCallback onTap;

  const NavTile({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.trailingText,
    this.color = AppColors.emeraldPrimary,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: TileIcon(icon, color: color),
      title: Text(title, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600)),
      subtitle: subtitle == null
          ? null
          : Text(subtitle!, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (trailingText != null)
            Text(trailingText!, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5)),
          const SizedBox(width: 4),
          const Icon(Icons.chevron_left, color: AppColors.textTertiary),
        ],
      ),
      onTap: onTap,
    );
  }
}

/// Switch row with title and optional explanation.
class SwitchTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;
  final IconData? icon;
  final Color iconColor;

  const SwitchTile({
    super.key,
    required this.title,
    required this.value,
    required this.onChanged,
    this.subtitle,
    this.icon,
    this.iconColor = AppColors.emeraldPrimary,
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      secondary: icon == null ? null : TileIcon(icon!, color: iconColor),
      title: Text(title, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600)),
      subtitle: subtitle == null
          ? null
          : Text(subtitle!, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.35)),
      value: value,
      onChanged: (v) {
        HapticFeedback.selectionClick();
        onChanged(v);
      },
    );
  }
}

/// Numeric setting with -/+ steppers and tap-to-type exact entry.
class NumberStepperTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final String unit;
  final double value;
  final double step;
  final double min;
  final double max;
  final ValueChanged<double> onChanged;
  final IconData? icon;
  final Color iconColor;

  const NumberStepperTile({
    super.key,
    required this.title,
    required this.unit,
    required this.value,
    required this.step,
    required this.min,
    required this.max,
    required this.onChanged,
    this.subtitle,
    this.icon,
    this.iconColor = AppColors.emeraldPrimary,
  });

  double _clampRound(double v) => (v.clamp(min, max) * 100).round() / 100.0;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: Insets.lg, vertical: 10),
      child: Row(
        children: [
          if (icon != null) ...[TileIcon(icon!, color: iconColor), const SizedBox(width: 16)],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                if (subtitle != null)
                  Text(subtitle!, style: const TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
              ],
            ),
          ),
          _StepButton(
            icon: Icons.remove,
            onTap: value <= min ? null : () => onChanged(_clampRound(value - step)),
          ),
          InkWell(
            borderRadius: BorderRadius.circular(10),
            onTap: () async {
              final v = await promptNumber(context, title: title, initial: value, min: min, max: max, unit: unit);
              if (v != null) onChanged(_clampRound(v));
            },
            child: Container(
              constraints: const BoxConstraints(minWidth: 66, minHeight: 44),
              margin: const EdgeInsets.symmetric(horizontal: 6),
              alignment: Alignment.center,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              decoration: BoxDecoration(
                color: AppColors.darkSurfaceVariant,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppColors.darkCardBorder),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(formatNumber(value),
                      style: const TextStyle(color: AppColors.emeraldLight, fontWeight: FontWeight.w800, fontSize: 15)),
                  Text(unit, style: const TextStyle(color: AppColors.textTertiary, fontSize: 10)),
                ],
              ),
            ),
          ),
          _StepButton(
            icon: Icons.add,
            onTap: value >= max ? null : () => onChanged(_clampRound(value + step)),
          ),
        ],
      ),
    );
  }
}

class _StepButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback? onTap;

  const _StepButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 40,
      height: 40,
      child: IconButton.filledTonal(
        padding: EdgeInsets.zero,
        iconSize: 18,
        style: IconButton.styleFrom(
          backgroundColor: AppColors.darkSurfaceVariant,
          foregroundColor: AppColors.textPrimary,
          disabledBackgroundColor: AppColors.darkSurfaceVariant.withValues(alpha: 0.4),
        ),
        onPressed: onTap == null
            ? null
            : () {
                HapticFeedback.selectionClick();
                onTap!();
              },
        icon: Icon(icon),
      ),
    );
  }
}

/// Row that opens a bottom-sheet picker for one of several options.
class ChoiceTile<T> extends StatelessWidget {
  final String title;
  final IconData icon;
  final Color iconColor;
  final T value;
  final Map<T, String> options;
  final ValueChanged<T> onChanged;
  final String? subtitle;

  const ChoiceTile({
    super.key,
    required this.title,
    required this.icon,
    required this.value,
    required this.options,
    required this.onChanged,
    this.subtitle,
    this.iconColor = AppColors.emeraldPrimary,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: TileIcon(icon, color: iconColor),
      title: Text(title, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600)),
      subtitle: subtitle == null
          ? null
          : Text(subtitle!, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(options[value] ?? "", style: const TextStyle(color: AppColors.emeraldLight, fontSize: 13, fontWeight: FontWeight.w600)),
          const Icon(Icons.unfold_more, color: AppColors.textTertiary, size: 18),
        ],
      ),
      onTap: () async {
        final picked = await showModalBottomSheet<T>(
          context: context,
          builder: (ctx) => SafeArea(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(Insets.lg, 0, Insets.lg, Insets.sm),
                  child: Text(title, style: Theme.of(ctx).textTheme.titleMedium),
                ),
                ...options.entries.map((e) => ListTile(
                      title: Text(e.value),
                      trailing: e.key == value
                          ? const Icon(Icons.check_circle, color: AppColors.emeraldPrimary)
                          : const Icon(Icons.circle_outlined, color: AppColors.textTertiary),
                      onTap: () => Navigator.pop(ctx, e.key),
                    )),
                const SizedBox(height: Insets.sm),
              ],
            ),
          ),
        );
        if (picked != null) onChanged(picked);
      },
    );
  }
}

/// Friendly empty state.
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? message;

  const EmptyState({super.key, required this.icon, required this.title, this.message});

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: const EdgeInsets.symmetric(vertical: 28, horizontal: Insets.lg),
      child: Column(
        children: [
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(color: AppColors.darkSurfaceVariant, borderRadius: BorderRadius.circular(16)),
            child: Icon(icon, color: AppColors.textSecondary, size: 26),
          ),
          const SizedBox(height: 12),
          Text(title, textAlign: TextAlign.center, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
          if (message != null) ...[
            const SizedBox(height: 4),
            Text(message!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5, height: 1.4)),
          ],
        ],
      ),
    );
  }
}

/// Shows a snackbar with an "Undo" action.
void showUndoSnackBar(BuildContext context, String message, VoidCallback onUndo) {
  final messenger = ScaffoldMessenger.of(context);
  messenger.hideCurrentSnackBar();
  messenger.showSnackBar(SnackBar(
    content: Text(message),
    duration: const Duration(seconds: 5),
    action: SnackBarAction(label: "تراجع", onPressed: onUndo),
  ));
}

/// Colour for a money amount.
Color moneyColor(double v) => v >= 0 ? AppColors.emeraldLight : AppColors.lossRed;
