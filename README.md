# inDrive Assistant (مساعد كابتن إن درايف) 🚖
### Smart Driver Companion, Fare Optimizer & Floating HUD for Android

An intelligent Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and a **Reactive Architecture**, designed specifically for ride-hailing drivers (specialized for **inDrive** drivers in Egypt and international markets).

---

## 🌟 Key Features

1. **⚡ 10-Second Smart Bid Calculator**:
   - Evaluates incoming passenger offers before the timer runs out.
   - Calculates exact fuel consumption, platform commission (~10%), wear & tear, and net profit.
   - Computes **Net Profit per Kilometer** and **Net Profit per Hour**.
   - Generates 3 intelligent counter-offer options:
     - 🛡️ *Break-Even Floor* (Never lose money)
     - 🎯 *Recommended Sweet Spot* (High conversion + healthy profit)
     - 🚀 *Aggressive / High-Yield Bid* (Peak demand pricing)

2. **🚨 Unprofitable Ride Detector**:
   - Visual and audio cues if a passenger's offer is a "money-losing trip" where fuel + commission exceed the fare.
   - Customizable profit threshold (EGP/km).

3. **🫧 Floating HUD Bubble (Screen Overlay)**:
   - Android Foreground Overlay Service (`SYSTEM_ALERT_WINDOW`).
   - Floats on top of the active inDrive app screen.
   - One-tap mini-calculator without switching between apps.

4. **📊 Daily Shift & Target Tracker**:
   - Live shift timer (Active hours, idle time).
   - Real-time earnings progress bar towards daily goal (e.g. 1,500 EGP).
   - Trip logger saving completed rides with timestamps and profitability metrics.

5. **⛽ Advanced Fuel & Engine Oil Analytics**:
   - **Dedicated Fuel Refill Logger (تفويلة بنزين)**:
     - Inputs: Liters pumped, Price per liter (EGP), and Km driven since last refill.
     - Automatically calculates real-world consumption: $\text{Liters}/100\text{km} = (\text{Liters} / \text{Km}) \times 100$.
     - Automatically calculates true fuel cost per kilometer: $\text{Cost}/\text{km} = (\text{Total Fuel Cost}) / \text{Km}$.
     - Auto-updates the driver's fare calculator engine with the car's measured real-world fuel economy.
   - **Dedicated Engine Oil Change Logger (غيار زيت موتور)**:
     - Inputs: Oil price per liter, Oil volume added (ml), and Lifespan km before drain (e.g. 5,000 km or 10,000 km).
     - Automatically calculates oil cost per kilometer: $\text{Oil Cost}/\text{km} = \text{Total Cost} / \text{Lifespan Km}$.
     - Detailed expense log with analytical badges (Km, Consumption, Cost/Km).

6. **📱 Live Web Simulator & Native Android Source**:
   - Full Flutter application with reactive state management, Material 3 Dark/Light themes, Arabic RTL localization.
   - Native Android background overlay service and Accessibility integration via MethodChannel.
   - Includes interactive browser-based Android phone simulator for instant testing and demonstration.

---

## 📂 Flutter Project Structure (هيكل مشروع فلاتر)

```
INDRIVE ASSESSTANT/
├── pubspec.yaml                  # Flutter package definition & dependencies
├── lib/                          # Flutter Codebase (Dart)
│   ├── main.dart                 # App Entry Point, RTL Localization & Theme Setup
│   ├── theme/                    # Design System & Cockpit Tokens
│   │   ├── app_colors.dart       # Obsidian Dark & Electric Neon Green Palette
│   │   └── app_theme.dart        # Material 3 Dark & Light ThemeData
│   ├── data/
│   │   ├── models/               # Domain Models
│   │   │   ├── driver_settings.dart   # Auto-Accept, Zone Rules & Cost Settings
│   │   │   ├── vehicle_profile.dart   # Fleet Vehicles & Consumption Profiles
│   │   │   ├── expense.dart           # Fuel, Oil & Spare Parts Expenses
│   │   │   ├── trip.dart              # Completed Trips & Profit Metrics
│   │   │   ├── parsed_ride_data.dart  # Ride OCR & Distance Breakdown
│   │   │   └── shift_state.dart       # Shift Tracking & Daily Targets
│   │   ├── local/
│   │   │   ├── preferences_manager.dart # Settings & State Persistence
│   │   │   └── database_service.dart    # Trips & Expenses Local Storage
│   │   └── repository/
│   │       └── assistant_repository.dart# Unified Data Repository
│   ├── engine/
│   │   └── fare_calculator_engine.dart  # 10s Smart Bid & Zone Analysis Engine
│   ├── providers/
│   │   └── assistant_provider.dart      # Reactive Provider State Management
│   ├── services/
│   │   └── native_bridge_service.dart   # MethodChannel Bridge to Android HUD
│   ├── utils/
│   │   └── update_manager.dart          # GitHub In-App Release Updater
│   └── ui/
│       ├── components/           # Reusable Widgets
│       │   ├── profit_status_banner.dart# Live Profit & Zone Alerts
│       │   ├── quick_input_slider.dart  # Interactive Distance/Fare Sliders
│       │   ├── smart_bid_card.dart      # Floor, Recommended & Aggressive Bids
│       │   └── cost_item_row.dart       # Operating Costs Breakdown
│       └── screens/              # App Screens
│           ├── main_navigation_screen.dart # Navigation Bar & Page Controller
│           ├── calculator_screen.dart      # Smart Fare Calculator
│           ├── shift_dashboard_screen.dart # Daily Targets & Shift Tracker
│           ├── expenses_screen.dart        # Vehicle Fleet & Expense Logger
│           ├── overlay_control_screen.dart # Floating HUD Service Controls
│           └── settings_screen.dart        # 5 Tabbed Customization Settings
├── android/                      # Native Android Host & Foreground Services
├── web-preview/                  # Browser Phone Simulator
└── README.md
```
