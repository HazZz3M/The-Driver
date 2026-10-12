### The Driver v1.12.0 - Real trip length from the map

- **The app now reads the real trip length.** When you open a request ("Ride request" screen), the app takes one screenshot and reads the number inDrive draws on the map next to the destination (for example "11 min 2,7 km").
  - Profit and the badge are then calculated on the real length, without "≈".
  - The length is remembered for that ride and appears on its card in the list.
- **Privacy:** processing happens on the phone (on-device text recognition). No image is saved and nothing is sent outside the phone.
- **New setting:** "قراءة طول المشوار من الخريطة" (Settings → العلامة العائمة → القراءة) to turn the feature on or off.
- Requires Android 11 or newer. On older versions the assumed trip length is used.
- If the feature doesn't work after updating, turn The Driver's accessibility service off and on again from the phone's settings.
