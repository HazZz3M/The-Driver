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
   - Includes full native Android Gradle project ready for **Android Studio**.
   - Includes interactive browser-based Android phone simulator for instant testing and demonstration.

---

## 📂 Project Structure

```
INDRIVE ASSESSTANT/
├── android/                      # Native Android Kotlin + Jetpack Compose Codebase
│   ├── app/                      # App module (Compose UI, ViewModels, Room DB, Services)
│   ├── gradle/wrapper/           # Gradle Wrapper files
│   ├── build.gradle.kts          # Root build script
│   ├── settings.gradle.kts       # Gradle settings
│   └── gradle.properties         # JVM & Android properties
├── web-preview/                  # Live interactive Android Phone Simulator
│   ├── index.html                # Simulator UI & InDrive Driver HUD
│   ├── style.css                 # Sleek dark mode styling
│   └── app.js                    # Calculation engine & interactive simulator logic
└── README.md
```
