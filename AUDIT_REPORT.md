# The Driver (inDrive Assistant): Full Technical Audit

**Scope:** every tracked file in the repo (Flutter `lib/`, native Android `android/`, CI workflow, web preview).
**Version audited:** `v1.9.0+11` (commit `e1a76c7`).
**Severity scale:**

| Level | Meaning |
|---|---|
| 🔴 **FATAL** | Breaks a core advertised feature, compromises users' phones, or silently destroys data. Fix before the next release. |
| 🟠 **CRITICAL** | Produces wrong money decisions, misleads the driver, or crashes or breaks a whole screen. |
| 🟡 **HIGH** | Serious bug or UX failure that most users will hit. |
| 🔵 **MEDIUM** | Real problems that are less visible: quality, performance or maintainability. |

---

## 0. Executive summary

The app is really **two separate apps that don't talk to each other**:

1. **The Flutter app** (calculator, shift dashboard, expenses, settings). The driver configures everything here.
2. **The native Android layer** (Accessibility reader, floating HUD, auto-accept). This is what runs over inDrive.

They store their data in **two different SharedPreferences files**. Nothing the driver sets in the Flutter UI (fuel price, consumption, commission, zones, overlay style/size, auto-accept) ever reaches the HUD or the auto-accept engine. The HUD always runs on hard-coded defaults. On its own, this one bug makes the product's main promise ("the HUD uses *your* real costs") untrue.

On top of that:
- **Security:** the release signing keystore and its passwords are committed to a public repo. Combined with the in-app APK updater and an Accessibility service, a malicious "update" can be signed with the same key and installed over the real app.
- **Auto-accept can't work.** The Accessibility config is missing `canPerformGestures`, so every tap is silently rejected. If that were fixed, it would still be dangerous: it can tap **Skip** or the wrong card, and by default it ignores the blacklist.
- **Release pipeline:** every push publishes a **debug** APK under the same hard-coded tag `v1.9.0`, so the in-app updater can never detect a new version.
- **Light theme is unusable:** text is hard-coded near-white on a white background.
- **Data storage:** any JSON corruption silently wipes all trips or expenses on the next save.

There are **0 tests** in the project.

### Counts

| Severity | Count |
|---|---|
| 🔴 Fatal | 9 |
| 🟠 Critical | 14 |
| 🟡 High | 20 |
| 🔵 Medium | 20+ |

---

## 1. 🔴 FATAL issues

### F1. Flutter and native use different storage, so the HUD ignores every setting
- **Native:** `context.getSharedPreferences("driver_prefs", ...)` in [PreferencesManager.kt:14](android/app/src/main/java/com/indrive/assistant/data/local/PreferencesManager.kt:14)
- **Flutter:** the `shared_preferences` plugin writes to the `FlutterSharedPreferences` file with a `flutter.` prefix on every key ([preferences_manager.dart:37](lib/data/local/preferences_manager.dart:37)).
- The formats also differ. Zones are a `StringList` in Dart and a comma-joined `String` in Kotlin ([PreferencesManager.kt:45](android/app/src/main/java/com/indrive/assistant/data/local/PreferencesManager.kt:45) vs [preferences_manager.dart:50](lib/data/local/preferences_manager.dart:50)). Doubles are also stored differently.
- **Effects:**
  - The HUD and auto-accept always use the defaults: 8.5 L/100km, 15 EGP/L, 10 %, 1.2 EGP/km, and the default zones.
  - The *Overlay style* and *size* tabs in Settings do nothing.
  - The *Auto-accept* switch in Settings does nothing. The native side always reads `false`.
  - Zones the driver adds from the HUD popup never appear in the Flutter Zones tab, and the reverse is also true.
  - Fuel refills that "auto-update consumption" only affect the in-app calculator.
- **Fix:** keep one source of truth. Either read Flutter's file from Kotlin (`FlutterSharedPreferences`, `flutter.`-prefixed keys, and Flutter's double/list encoding), or better, push settings to native over the existing `MethodChannel` (`syncSettings(json)`) whenever they change and at app start.

### F2. The signing keystore and its passwords are committed to a public repository
- [android/app/build.gradle:48-55](android/app/build.gradle:48): `storePassword "thedriver123"`, `keyPassword "thedriver123"`.
- `android/app/keystore/fixed-release.keystore` is tracked in git.
- The same password is in [build-apk.yml:37](.github/workflows/build-apk.yml:37).
- The repo has to be public for the unauthenticated GitHub "latest release" API that the updater uses to work, so **anyone can download this key**.
- **Effect:** anyone can build an APK signed with your key. Android accepts it as a valid update over the installed app. That app has the **Accessibility** permission (it can read and tap anything on screen) and the **overlay** permission. This is a complete device-takeover path for every user.
- **Fix:**
  1. Generate a new key.
  2. Store it in GitHub Secrets (base64 keystore plus passwords) and inject it in CI.
  3. Remove it from the repo and purge it from history (`git filter-repo`).
  4. Users will have to uninstall and reinstall once, because the signature changes.

### F3. Users get a debug build
- [build-apk.yml:56](.github/workflows/build-apk.yml:56) runs `flutter build apk --debug`. That is the build that gets released and that the in-app updater installs.
- **Effects:**
  - Flutter runs in JIT debug mode: much slower, larger, and noticeably janky.
  - The APK is `debuggable=true`, so anyone with USB access can `run-as` and read all the app's data (trips, earnings, zones).
  - Assertions are on.
- **Fix:** run `flutter build apk --release` with the release signing config from F2. Turn on R8 (`minifyEnabled true`) with keep rules for Flutter.

### F4. The in-app updater can never see an update (the tag is hard-coded)
- [build-apk.yml:74](.github/workflows/build-apk.yml:74) sets `tag_name: v1.9.0` on every push, so the same release is overwritten each time.
- [update_manager.dart:23](lib/utils/update_manager.dart:23) hard-codes `currentAppVersion = "v1.9.0"`.
- **Effects:**
  - The remote tag always equals the local version, so the app always says "up to date", even after 20 new pushes.
  - If someone bumps the tag but forgets the Dart constant, the reverse happens: an **endless "update available" loop**.
- The release body also says **v1.8.0** while the name says v1.9.0 ([build-apk.yml:77](.github/workflows/build-apk.yml:77)).
- **Fix:** derive the tag from `pubspec.yaml` (`v${version}`). Read the running version with `package_info_plus` instead of a constant. Compare versions **semantically** (see H17).

### F5. Auto-accept can never tap (the gesture capability is missing)
- [accessibility_service_config.xml:8](android/app/src/main/res/xml/accessibility_service_config.xml:8) has no `android:canPerformGestures="true"`.
- Without it, `dispatchGesture()` ([InDriveAccessibilityService.kt:171](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:171)) is always rejected. The failure is invisible, but the toast still says "auto-accepted ✔".
- **Effect:** the headline automation feature doesn't work, and the driver is told that it did.

### F6. Auto-accept taps the wrong target (Skip, a distance label, or a different card)
Even after F1 and F5 are fixed, the tap logic is unsafe:
- **The anchor list includes "Skip/تخطي", any price text, and any "~N km" text** ([InDriveAccessibilityService.kt:358-368](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:358)).
  - The ride's `anchorBounds` comes from whichever anchor produced the card.
  - Auto-accept taps `anchor.centerX/centerY` ([:141-148](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:141)).
  - So it can tap **Skip** (losing a good ride), a plain text label (nothing happens), or "Offer your fare" (opens the bid sheet).
- **The tap is delayed by 0.2–8 s** (`postDelayed`, [:147](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:147)) and uses coordinates captured earlier. If the feed scrolls or a new card arrives in the meantime, it taps **another passenger's ride**. Nothing is checked again before the tap.
- **Fix:**
  - Find the **"Accept for / قبول مقابل" button node** inside the card and use `performAction(ACTION_CLICK)` on that node, not coordinates.
  - Just before clicking, re-read the window and confirm that the same fingerprint, fare and distance are still on screen.

### F7. The target-app filter matches your own app and other ride-hailing apps
- [InDriveAccessibilityService.kt:53-57](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:53): `pkg.contains("driver") || pkg.contains("assistant") || pkg.contains("indrive")…`
- `com.indrive.assistant` (this app) matches. Opening the calculator, which shows "EGP" amounts, makes the HUD put popups over the app itself and run auto-accept against its own buttons.
- `"driver"` matches Uber Driver (`com.ubercab.driver`), Careem Captain and others. inDrive-specific parsing and **auto-tapping** then run inside a competitor's app.
- **Fix:** whitelist exact package names (`sinet.startup.inDriver` and the real driver package). Also set `android:packageNames` in the accessibility XML so the OS only sends inDrive events.

### F8. Corrupted JSON silently wipes trips, expenses and vehicles
- [database_service.dart:35-38](lib/data/local/database_service.dart:35) and [:97-100](lib/data/local/database_service.dart:97): on a parse error, the cache is set to `[]`. The **next** `recordTrip`/`recordExpense` then writes `[newItem]` over the old, still-recoverable data.
- [preferences_manager.dart:167-169](lib/data/local/preferences_manager.dart:167) has the same pattern for vehicle profiles.
- A single bad field, or one bad migration, deletes the driver's whole history with no warning.
- **Fix:**
  - Never overwrite on a parse failure. Back up the raw string to a `_corrupt_<ts>` key and surface an error.
  - Parse item by item and skip only the bad items.
  - Move to a real database (sqflite/drift/Isar) with schema versions.

### F9. Default settings make auto-accept ignore blacklisted zones
- `autoAcceptIgnoreBlacklist = true` by default in [DriverSettings.kt:20](android/app/src/main/java/com/indrive/assistant/data/model/DriverSettings.kt:20) and [preferences_manager.dart:59](lib/data/local/preferences_manager.dart:59).
- Out of the box, as soon as auto-accept is enabled, the blacklist check is **skipped** ([InDriveAccessibilityService.kt:96](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:96)), and rides to the driver's "never go there" zones are auto-accepted.
- The Settings switch is labelled with the inverted meaning ("reject blacklisted zones" = `!ignore`), which hides the bad default.
- **Fix:** rename the setting to `autoAcceptRejectBlacklisted` and default it to `true`.

---

## 2. 🟠 CRITICAL issues

### C1. Invented distances drive the money decisions
[InDriveAccessibilityService.kt:617-630](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:617): if parsing fails, the code fills in **1.0 km pickup, 3.5 or 4.0 km trip**. These made-up numbers then drive:
- the HUD colour,
- the "fair price",
- **auto-accept**.

Nothing tells the driver the distance was guessed.

**Fix:** return `null` or "unknown". Show a grey "?" badge. Never auto-accept a card with unknown distances.

### C2. Colour priority hides losing rides
[FloatingHudService.kt:247-269](android/app/src/main/java/com/indrive/assistant/service/FloatingHudService.kt:247): the `isFavoriteZone` branch is checked **before** profitability.
- A **money-losing** ride to a favourite zone shows **gold** "favourite zone" instead of **red**.
- "Poor margin" yellow `#EAB308` and favourite amber `#F59E0B` look almost the same; the code comment admits this confusion.

The driver has about 10 seconds and only sees a colour.

**Fix:** profitability decides the colour. Show the zone as a separate icon or star.

### C3. The HUD's "fair price" is not a fair price
- The compact badge shows `recommendedFare`, labelled «السعر العادل» ("fair price"). That number is the driver's own *target* (minProfit × km), not the passenger's offer and not a market price.
- The badge colour describes the **offer**, but the number shown is the **recommendation**. Drivers will read "120 ج" in green as "this ride pays 120".
- **Fix:** show the net profit (or the offer) together with a clear label. Show the recommended counter-bid only in the expanded card.

### C4. The expanded popup blocks inDrive during the offer timer
- Expanding sets the overlay to `MATCH_PARENT × MATCH_PARENT` and touchable ([FloatingHudService.kt:222-223](android/app/src/main/java/com/indrive/assistant/service/FloatingHudService.kt:222)), with a scrim.
- While it is open, the driver **can't tap Accept in inDrive**, and the offer can expire.
- **Fix:** use a wrap-content card with `FLAG_WATCH_OUTSIDE_TOUCH`, and collapse on `ACTION_OUTSIDE` instead of covering the whole screen.

### C5. The HUD rebuilds every popup's views every 250 ms
- Every accessibility event calls `renderPopup(existing)` ([:136](android/app/src/main/java/com/indrive/assistant/service/FloatingHudService.kt:136)), which runs `removeAllViews()` and rebuilds everything, and also restarts the pulse animator.
- Buttons in the expanded card are destroyed **while the finger is on them**, so taps get lost. The animation stutters and the main thread does extra work.
- **Fix:** re-render only when `analysis` or `isExpanded` actually change (compare a hash).

### C6. Dragging the badge does nothing
- The driver drags the badge. On the next event (≤250 ms later), `handleCardsSync` resets `params.y` to `calculatePopupY(card)` ([:129-135](android/app/src/main/java/com/indrive/assistant/service/FloatingHudService.kt:129)) and the badge snaps back.
- The tips screen still tells users they can drag it anywhere ([overlay_control_screen.dart:202](lib/ui/screens/overlay_control_screen.dart:202)).
- **Fix:** keep a per-card drag offset and add it to the calculated position, or turn dragging off.

### C7. Card fingerprints collide and change
`fingerprint = name_fare_pickup_trip` ([ParsedRideData.kt:22-23](android/app/src/main/java/com/indrive/assistant/data/model/ParsedRideData.kt:22)):
- The name defaults to the constant «عميل إن درايف» when it can't be parsed. Two rides with the same fare and distances then share one key, and **one popup disappears**.
- Any re-parse jitter, such as a distance flipping from 3.4 to 3.5, gives the card a "new" identity. The popup is destroyed and recreated, flickers, and **vibrates again**.
- **Fix:** key cards on their stable on-screen position or node id plus fare. Use fuzzy matching for distances.

### C8. The Kotlin and Dart engines disagree
- **Zone matching:**
  - Auto-accept uses `DriverSettings.isFavorite/isBlacklisted` (plain `contains`, no Arabic normalization), [DriverSettings.kt:24-34](android/app/src/main/java/com/indrive/assistant/data/model/DriverSettings.kt:24).
  - The HUD uses `FareCalculatorEngine` (normalizes أ/إ/آ→ا, ة→ه, ى→ي).
  - Result: the HUD says "favourite" while auto-accept says "not favourite", and the reverse.
- **Punctuation stripping:** Kotlin's `\p{Punct}` is **ASCII-only** ([FareCalculatorEngine.kt:68](android/app/src/main/java/com/indrive/assistant/engine/FareCalculatorEngine.kt:68)), so «،» is not stripped. Dart uses Unicode `\p{P}`.
- **Minutes per km:** 2.3 in the HUD ([FloatingHudService.kt:119](android/app/src/main/java/com/indrive/assistant/service/FloatingHudService.kt:119)) vs 2.5 in the app ([assistant_provider.dart:75](lib/providers/assistant_provider.dart:75)). Profit per hour differs for the same ride.
- **Fix:** move to a single engine (Kotlin, called from Dart over the channel, or the reverse) and add shared golden tests.

### C9. Light theme is unreadable
- [app_colors.dart:42](lib/theme/app_colors.dart:42): `textPrimary = #F8FAFC` (almost white) is hard-coded in **every** screen and component.
- `darkSurface` and `darkBackground` are also hard-coded (for example [main_navigation_screen.dart:31](lib/ui/screens/main_navigation_screen.dart:31)).
- In light mode, headers sit directly on `#F8FAFC`, so the text is invisible. Inputs and dialogs come out as a mix of light and dark.
- **Fix:** use `Theme.of(context).colorScheme.*` and `textTheme` everywhere, or remove the light-mode switch.

### C10. Arabic-keyboard numbers are rejected without any message
- Every numeric dialog uses `double.tryParse(...) ?? 0.0` ([expenses_screen.dart:496-498](lib/ui/screens/expenses_screen.dart:496) and the other dialogs).
- Arabic-Indic digits (٣٠) and the Arabic decimal separator (٫) fail to parse and become `0`. The dialog then **does nothing**, with no error shown.
- `TextInputType.number` without `decimal: true` can also hide the decimal key.
- The target users are Egyptian drivers with Arabic keyboards.
- **Fix:** normalize digits (the native `normalizeArabicDigits` already exists, so port it), use `numberWithOptions(decimal: true)`, and add `inputFormatters` plus validation messages.

### C11. Two sources of truth for vehicle costs
- A fuel refill updates **DriverSettings** but not the **active VehicleProfile** ([assistant_provider.dart:174-180](lib/providers/assistant_provider.dart:174)). The same is true for Pricing-tab edits and the wear rate.
- Switching vehicles ([assistant_repository.dart:33-44](lib/data/repository/assistant_repository.dart:33)) **overwrites** the measured consumption, the commission and min-profit with the profile's stale defaults.
- The Expenses card shows the *profile* values while the engine uses the *settings*, so the screen shows different numbers from the ones used in the calculation.
- **Fix:** cost parameters should live only on the vehicle profile. Settings should keep only the per-driver values (target, min profit, zones).

### C12. The wear rate only ever goes up
- Each spare part with the checkbox ticked (the default) **adds** `amount/lifespan` to `wearAndTearPerKm` permanently ([assistant_provider.dart:233-238](lib/providers/assistant_provider.dart:233)).
- Deleting the expense doesn't undo it. Replacing the same part (tyres again) adds it **again**.
- The base rate of 1.2 is already described as "tyres, oil, depreciation", so parts are counted twice.
- Oil change cost per km is computed but **never** used anywhere.
- **Fix:** compute wear as Σ(active parts' cost/km) + base depreciation, derived from the expense list rather than added up step by step.

### C13. Fuel consumption is overwritten by one noisy refill, with pre-filled demo values
- A single refill replaces the consumption figure ([assistant_provider.dart:174-179](lib/providers/assistant_provider.dart:174)). Partial fills make this wildly wrong.
- The dialog **pre-fills 30 L / 350 km** ([expenses_screen.dart:456-458](lib/ui/screens/expenses_screen.dart:456)). A driver who just taps Save records fake data and changes their pricing.
- **Fix:** start with empty fields, use the full-tank method or an odometer-based rolling average, and ask for confirmation before changing pricing parameters.

### C14. The accessibility service receives events from every app, and parses on the main thread
- There is no `packageNames` filter in the config, so the system wakes the service for **every** app, including banking and chat apps.
- For target apps, the full node tree is walked on the main thread every 250 ms. **Regex objects are compiled inside the loops for every node** ([:366-368](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:366), [:484-505](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:484)). The HUD and that main thread live in the same process.
- `AccessibilityNodeInfo` children are never recycled in `findOfferAnchors`/`collectNodeTexts` ([:374-378](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:374), [:437-441](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:437)). This leaks on API < 33, and minSdk is 26.
- **Effects:** battery drain, heat, and HUD lag on low-end phones.
- **Fix:** add the `packageNames` filter, precompile regexes as `companion` constants, move parsing to a background dispatcher, and recycle nodes.

---

## 3. 🟡 HIGH issues

### Native / HUD
- **H1. The throttle drops the last event.** `if (now - last < 250) return` ([:37-42](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:37)) is a leading-edge throttle. If a card's final update arrives within 250 ms of the previous one, the HUD stays stale until the next event. Use a trailing debounce (`Handler.postDelayed` that resets).
- **H2. Popups disappear when there's no active root.** Opening the notification shade or keyboard, or a transient window, makes `rootInActiveWindow` null, which wipes every popup ([:44-48](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:44)). That causes flicker and repeated vibrations.
- **H3. Fare parsing picks the first currency text in the card.** When "Accept for" is missing, a **counter-bid chip** ("EGP 90") can be taken as the fare ([:491-500](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:491)).
- **H4. A/B marker strategy.** It mixes distances from the previous and next texts ([:574](android/app/src/main/java/com/indrive/assistant/service/InDriveAccessibilityService.kt:574)), so pickup and trip can get the **same** value.
- **H5. Popup Y position ignores the status bar.** `TYPE_APPLICATION_OVERLAY` with `TOP` gravity is laid out below the status bar, but `screenBounds.top` is measured in screen coordinates. Popups drift 24–48 dp off their card. The fixed `x = 20` can also cover inDrive's own price or button in RTL layouts.
- **H6. `refreshAnalysis` calls `updateViewLayout` without try/catch** ([:975](android/app/src/main/java/com/indrive/assistant/service/FloatingHudService.kt:975)). If the card vanished between the tap and the refresh, the service crashes.
- **H7. The service starts even without overlay permission.** `startFloatingService` doesn't check `canDrawOverlays`. `addView` then throws and the exception is swallowed ([:188-191](android/app/src/main/java/com/indrive/assistant/service/FloatingHudService.kt:188)). The UI says "running" while nothing ever shows.
- **H8. `POST_NOTIFICATIONS` is never requested at runtime.** On Android 13+ the foreground-service notification is hidden. The notification also uses the generic `ic_dialog_info` icon and has no Stop action.
- **H9. No consent or disclosure before auto-accept and Accessibility.** Google requires a prominent disclosure before sending the user to Accessibility settings. Auto-tapping in inDrive is also very likely against inDrive's terms, so the **driver's account can be banned**. There is no warning dialog, no "I understand" step and no kill-switch on the HUD.

### Flutter app
- **H10. Permission state is never refreshed on return from Settings.** `_refreshPermissions()` runs immediately after `startActivity`, before the user has done anything ([overlay_control_screen.dart:75-78](lib/ui/screens/overlay_control_screen.dart:75)). The cards stay red until a manual refresh. Add `WidgetsBindingObserver` → `didChangeAppLifecycleState(resumed)`.
- **H11. Shift state is memory-only.** `isShiftActive` and `shiftStartTime` ([assistant_provider.dart:27-28](lib/providers/assistant_provider.dart:27)) are lost when the app is killed, which Android does constantly while the driver is in inDrive. There's also no timer, no idle time, and no link between trips and the shift, although the README advertises "live shift timer / active hours / idle time".
- **H12. HUD rides are never logged.** Trips only exist if the driver goes back to the app and taps "Save" in the calculator. The dashboard and daily target therefore don't match reality.
- **H13. Duplicate trips.** The Save button ([calculator_screen.dart:479](lib/ui/screens/calculator_screen.dart:479)) has no busy state or debounce, and inputs aren't reset after saving. A double tap saves two trips.
- **H14. Destructive actions have no confirmation or undo.** Deleting a trip, an expense, or a zone chip is one tap ([shift_dashboard_screen.dart:334](lib/ui/screens/shift_dashboard_screen.dart:334), [expenses_screen.dart:393](lib/ui/screens/expenses_screen.dart:393), [settings_screen.dart:352](lib/ui/screens/settings_screen.dart:352)).
- **H15. Settings inputs are only +/- buttons** ([settings_screen.dart:719-771](lib/ui/screens/settings_screen.dart:719)):
  - Going from 15 to 22 EGP/L takes 28 taps.
  - The value is shown with 1 decimal, so 15.25 appears as **15.3**.
  - There is **no upper bound**: commission can reach 100 %+. Then `netDivisor = 0.01` and the bids become ×100, or the commission goes negative.
  - Each tap uses the `settings` captured at build time. Fast taps race, increments get lost, and each one awaits **17 sequential disk writes**.
- **H16. The vehicle profile can't be edited or deleted.** Only "add" exists. A typo or a wrong consumption value stays forever.
- **H17. Version comparison.**
  - `cleanRemote != cleanCurrent` treats an **older** tag as an "update".
  - `replaceAll('v','')` strips every "v" in the string ([update_manager.dart:58-62](lib/utils/update_manager.dart:58)).
  - When the check **fails** (no network, rate-limited), the UI says "You're on the latest version" ([settings_screen.dart:579-582](lib/ui/screens/settings_screen.dart:579)).
- **H18. Downloaded APKs aren't integrity-checked.** There's no SHA-256 or signature check before install. If installing needs the "unknown sources" permission, native opens Settings and returns `true`, so the user gets no feedback and has to start over ([UpdateManager.kt:151-159](android/app/src/main/java/com/indrive/assistant/util/UpdateManager.kt:151)). The download `http.Client` leaks on errors ([update_manager.dart:82-117](lib/utils/update_manager.dart:82)).
- **H19. Whole-app rebuild on every slider tick.** The root `InDriveAssistantApp` uses `context.watch<AssistantProvider>()` ([main.dart:36](lib/main.dart:36)), so every slider tick rebuilds `MaterialApp`. Getters like `todayTrips`, `todayTotalGross` and `todayTripsCount` each re-filter the full list on every build. Use `context.select((p) => p.themeMode)` and cache the derived stats.
- **H20. Layout overflows on 360 dp phones.**
  - Vehicle strip `Row` without `Expanded` ([calculator_screen.dart:144-161](lib/ui/screens/calculator_screen.dart:144)). The default profile name and consumption text don't fit.
  - Trip card row ([shift_dashboard_screen.dart:308-330](lib/ui/screens/shift_dashboard_screen.dart:308)).
  - Expense card header ([expenses_screen.dart:356-397](lib/ui/screens/expenses_screen.dart:356)).
  - `SmartBidCard` header.

  All of these give a yellow/black overflow stripe in debug (which is what users have, see F3) and clipped text in release.

---

## 4. 🔵 MEDIUM issues

### UI / UX
- **M1. "+" is always prefixed to net profit**, so losses show as **"+-12.5"** ([shift_dashboard_screen.dart:343](lib/ui/screens/shift_dashboard_screen.dart:343), [smart_bid_card.dart:96](lib/ui/components/smart_bid_card.dart:96)).
- **M2. The loss warning prints only the fuel cost** while calling it "fuel + wear + commission" ([fare_calculator_engine.dart:276](lib/engine/fare_calculator_engine.dart:276)).
- **M3. Blacklist messages always say «الوجهة» (destination)**, even when the match was the **pickup** ([:194-198](lib/engine/fare_calculator_engine.dart:194)).
- **M4. Mixed English/Arabic in the main UI:** "Floor / Break-Even", "MINIMUM", "OPTIMAL", "(Target)", "(Pickup)".
- **M5. Status is shown by colour alone** (red/yellow/green/amber), with tiny text (8.5–11 sp) and small touch targets (the HUD "✕ تصغير" has 4 dp padding). That's poor for colour-blind drivers and for glancing while driving.
- **M6. Delta buttons go up to 600 while the slider max is 500.** The slider is clamped but the label shows 600, so they disagree ([calculator_screen.dart:237](lib/ui/screens/calculator_screen.dart:237)).
- **M7. Choosing a smart bid overwrites the passenger offer.** The driver loses the original number they were comparing against.
- **M8. Direction-unaware padding** (`left:`/`right:` instead of `start`/`end`) in an RTL-first app.
- **M9. No onboarding or empty-state guidance** for the three permissions (overlay, accessibility, notifications), and no explanation of why each is needed.
- **M10. `TextEditingController`s are created in `build` and never disposed.** Two unused ones in the Zones tab are recreated on every rebuild ([settings_screen.dart:314-315](lib/ui/screens/settings_screen.dart:314)). All dialogs leak theirs as well.
- **M11. `context` is used after `await` without `mounted` checks** ([calculator_screen.dart:344](lib/ui/screens/calculator_screen.dart:344), [:481](lib/ui/screens/calculator_screen.dart:481); [settings_screen.dart:566](lib/ui/screens/settings_screen.dart:566), [:662](lib/ui/screens/settings_screen.dart:662)).
- **M12. `isVibrationMuted` is never set.** There is no mute option, even though the code is written for one.

### Data / storage
- **M13. Trips and expenses are stored as one growing JSON string** in SharedPreferences. Every add or delete rewrites the whole history. There's no pagination, no schema version and no migration path.
- **M14. ID collisions.** `id = millisecondsSinceEpoch` (Trip, Expense, VehicleProfile). Two records created in the same millisecond share an id, and `deleteTrip(id)` removes both.
- **M15. Default fuel price differs: 15.0 in settings vs 15.25 in the default vehicle.** On first launch the active vehicle shows 15.25 while the engine uses 15.0.
- **M16. Zone matching is a raw substring check.** Short zone names produce false positives inside longer names. Zones are stored comma-joined in Kotlin, so a zone containing «,» corrupts the list.
- **M17. Backup includes all SharedPreferences** (earnings, passenger addresses saved as zones) in cloud backup and device transfer, with no opt-out.

### Build / infra / code health
- **M18. `FileProvider` exposes `external-path "."`** (the whole external storage root) ([file_paths.xml:4](android/app/src/main/res/xml/file_paths.xml:4)). Only `cache-path` is needed for the APK.
- **M19. The web preview server has a path-traversal bug and is exposed on the LAN.** `path.join(__dirname, req.url)` with no normalization check, listening on `0.0.0.0` ([web-preview/server.js:17-20](web-preview/server.js:17), [:40](web-preview/server.js:40)). `GET /../../<file>` (sent with `--path-as-is`) reads any file on the dev machine. The preview is also a **third**, outdated copy of the pricing logic (it has no zone support at all).
- **M20. Dead code:**
  - Kotlin `UpdateManager.checkForUpdate/downloadApk`, `latestRide` StateFlow, `onNewRideParsed`, `parseAllRidesFromScreen`, `collectAllTexts`.
  - Dart `ShiftState`, `ParsedRideData`, `VehicleProfile.toDriverSettings`.
  - The `autoBidMode` setting (stored, never used).
  - The Kotlin repository flows (theme, vehicles).
- **M21. Version has three sources of truth.** `pubspec.yaml` (1.9.0+11), the Gradle fallback (`1.6.0`/`8`), and the Dart constant `v1.9.0`.
- **M22. No tests and no lints.** There is no `test/` folder and no `analysis_options.yaml`, so `flutter_lints` isn't applied. The parser and pricing engine (where most money bugs live) are pure functions that are easy to unit-test.
- **M23. CI uploads every build to a third-party public file host** (litterbox.catbox.moe) and ignores failures. `--android-skip-build-dependency-validation` hides toolchain mismatches. `targetSdk 34` is behind the current Play requirement.
- **M24. Recurring `catch (_) {}` hides failures.** It appears in native `addView`/`removeView`, the bridge, and the updater. Failures become "nothing happens", with no logs and no user feedback.

---

## 5. Data-flow map (current state)

```
                    ┌─────────────────────── FLUTTER APP ───────────────────────┐
 Driver input ───►  │ Settings / Calculator / Expenses                          │
                    │        │                                                  │
                    │        ▼                                                  │
                    │ AssistantProvider ──► FareCalculatorEngine (Dart)         │
                    │        │                                                  │
                    │        ▼                                                  │
                    │ SharedPreferences  "FlutterSharedPreferences"  (flutter.*)│
                    └────────┬──────────────────────────────────────────────────┘
                             │  MethodChannel: only permissions + start/stop + installApk
                             │  ✗ NO settings sync   ✗ NO ride data back to Flutter
                    ┌────────▼────────────────── NATIVE ────────────────────────┐
 inDrive screen ──► │ AccessibilityService ─► parser (invents defaults)         │
                    │        │                     │                            │
                    │        ▼                     ▼                            │
                    │ FloatingHudService     Auto-accept (no gesture capability)│
                    │        │                     │                            │
                    │        ▼                     ▼                            │
                    │ FareCalculatorEngine (Kotlin, slightly different maths)   │
                    │        │                                                  │
                    │        ▼                                                  │
                    │ SharedPreferences  "driver_prefs"  ← always defaults      │
                    └───────────────────────────────────────────────────────────┘
```

**Target state:** one settings store, pushed to native on every change. One pricing engine. HUD-detected and accepted rides sent back over an `EventChannel` and logged as trips (status OFFERED, ACCEPTED or REJECTED). The shift is persisted.

---

## 6. Recommended fix order

| # | Action | Fixes |
|---|---|---|
| 1 | Rotate the signing key, move it to CI secrets, purge it from git history | F2 |
| 2 | Build **release** APKs; derive the tag from the pubspec; semantic version compare; SHA-256 check | F3, F4, H17, H18, M21 |
| 3 | Unify storage: push settings to native over the channel (or read `FlutterSharedPreferences`) | F1, C11, part of C8 |
| 4 | **Disable auto-accept** until it is rewritten: node `ACTION_CLICK` on the Accept button, re-check before the tap, `canPerformGestures`, safe defaults, consent dialog | F5, F6, F9, H9 |
| 5 | Exact package whitelist plus `packageNames` in the accessibility XML; precompiled regexes; background parsing; recycle nodes | F7, C14, H1 |
| 6 | Make storage safe: no wipe on parse error; move to a real database with migrations | F8, M13, M14 |
| 7 | Parser honesty: no invented distances; show unknown values as "?" | C1, H3, H4 |
| 8 | HUD UX: profit-first colours, honest label, no full-screen blocker, no rebuild loop, keep drag offset, status-bar offset | C2–C7, H5, H6 |
| 9 | Theme-aware colours everywhere (or remove light mode); fix overflows | C9, H20 |
| 10 | Arabic digit input, real numeric fields with bounds, confirmations and undo | C10, H14, H15 |
| 11 | Vehicle profile as the single source of cost truth; derive wear from expenses | C11, C12, C13 |
| 12 | Persist the shift; log HUD rides automatically; resume-aware permission screen | H10–H13 |
| 13 | One shared pricing engine plus a unit-test suite (parser fixtures from real inDrive screens) | C8, M22 |
