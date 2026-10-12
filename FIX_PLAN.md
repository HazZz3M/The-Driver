# Fix Plan: The Driver v1.10.0

This plan is based on [AUDIT_REPORT.md](AUDIT_REPORT.md). IDs (F1, C2, H5…) refer to that report.
**Target release:** `v1.10.0+12`. It is built by GitHub Actions as a **release** APK and signed with the existing key, now supplied through CI secrets.

## Constraints and decisions
- **No local Android toolchain.** CI is the compiler. Work goes to the branch `fix/audit-v1.10` first. That branch only builds (no release is published). It merges to `main` once it's green.
- **Signing.** Keep the existing key so users can update in place. Remove it and its passwords from the repo. CI reads it from secrets. The key is still compromised through git history, so rotation remains a follow-up (see the end of this plan).
- **Light mode is removed** (C9). The whole UI is built on dark-only tokens. Making it theme-aware means rewriting every screen, and with no local compiler that's too risky right now.

## Phase 1: Security and release pipeline
| Fix | Change |
|---|---|
| F2 | `build.gradle` reads signing from env vars (CI) or a git-ignored `key.properties` (local). Untrack the keystore and add it to `.gitignore`. |
| F3 | CI builds `--release`. |
| F4 | The tag comes from `pubspec.yaml`. Releases are only created from `main`. The app reads its real version from Android instead of a hard-coded constant. |
| H17 | Semantic version comparison. A failed check is shown as a failure, not as "up to date". |
| H18 | CI publishes a `.sha256`. The installer checks the hash natively before installing, and reports "permission needed" correctly. |
| M18 | `FileProvider` is limited to `cache-path`. |
| M19 | Web preview server: block path traversal and bind to `127.0.0.1`. |
| M23 | Drop the Litterbox upload. CI runs `flutter analyze` and `flutter test`. |

## Phase 2: One data flow between Flutter and native
| Fix | Change |
|---|---|
| F1 | Dart pushes all settings and the overlay style/size to native over the `MethodChannel` at startup and after every change (`syncSettings`). |
| F1 | Zones the driver edits from the HUD are pulled back into Flutter when the app resumes (`getNativeZones`). |
| M16 | Native stores zone lists as a JSON array. The old comma format is still readable. |
| F9 | `autoAcceptIgnoreBlacklist` is replaced by `autoAcceptRejectBlacklisted`, default **true**. |

## Phase 3: Native accessibility and HUD
| Fix | Change |
|---|---|
| F7 | Target app is detected by exact inDrive package names. Our own app is excluded. |
| F5, F6 | Auto-accept is rewritten: wait, **re-scan the screen**, find the *same* card, find its "Accept / قبول" button node, and `ACTION_CLICK` it. Never tap coordinates. |
| C1 | The parser marks guessed distances (`distanceEstimated`). The HUD shows a grey "?" badge, and auto-accept refuses those cards. |
| C14, H1 | Regexes are precompiled. Parsing runs on a worker thread with a throttle that always fires on the trailing edge. Child nodes are recycled. |
| H2 | A null root or transient window no longer wipes the popups. |
| C2, C3 | Profitability decides the colour. A favourite zone is shown as ★ and does not override it. The badge shows **net profit**, not a "fair price". |
| C4 | The expanded card is a wrap-content window that lets touches through to inDrive and collapses when you tap outside it. |
| C5 | Popups only re-render when their content changes. |
| C6 | The drag offset is remembered per card. |
| H5 | Overlay coordinates use `FLAG_LAYOUT_IN_SCREEN`. |
| C7 | The fingerprint no longer jitters with distances. Collisions get a suffix. |
| H6 | Safe `updateViewLayout`. |
| H7, H8 | The service won't start without overlay permission. Notification permission is requested. The notification gets a **Stop** action. |
| C8 | The Kotlin engine uses Unicode punctuation and the same zone matching and minutes-per-km as Dart. |

## Phase 4: Flutter app
| Fix | Change |
|---|---|
| F8 | Storage never overwrites data it couldn't parse. It keeps a raw backup and skips only the bad items. |
| M14 | Collision-resistant IDs. |
| C10 | Arabic and Persian digits plus the Arabic decimal separator are accepted everywhere. Decimal keyboards. Inline validation errors. No fake pre-filled values. |
| C11 | Vehicle cost fields (consumption, fuel price, wear) live on the active vehicle and stay in sync with settings. Switching vehicles no longer resets commission or min-profit. |
| C12 | Spare parts only change the wear rate if you opt in (now off by default). Deleting the part removes it again. |
| C13 | Fuel consumption is the average of the last 3 refills. |
| H11 | The shift is persisted, with its start time and a live duration. |
| H13 | The Save-trip button is protected against double taps. |
| H14 | Confirmation before deleting trips, expenses or zones. |
| H15 | Settings numbers can be typed exactly, have min/max bounds, show 2 decimals when needed, and rapid taps no longer race. |
| H16 | Vehicles can be edited and deleted. |
| H9 | Prominent disclosure before opening Accessibility settings. Warning and consent before enabling auto-accept. |
| H10 | Permission cards refresh when the app resumes. |
| H19 | The root widget no longer rebuilds `MaterialApp` on every slider tick. |
| H20 | Overflow fixes on 360 dp phones. |
| M1–M4 | Correct +/− signs. The loss warning shows the full cost. "Zone" instead of "destination". Arabic bid titles. |
| M15 | Consistent default fuel price. |
| M20, M22 | Remove dead code. Add `analysis_options.yaml` and unit tests for the engine and number parsing. |

## Deferred (not in this release)
- **Rotating the signing key.** Every user would have to reinstall once.
- **Purging the keystore from git history** (`git filter-repo` plus a force-push).
- **Auto-logging rides seen by the HUD as trips** (H12). This needs native-side storage that Flutter can read.
- **Moving storage to SQLite with migrations** (M13).
- **Parser fixes that need real inDrive screen dumps to verify** (H3, H4). These are the fare-vs-counter-bid choice and the A/B marker logic.
