### The Driver v1.14.0 - Trip length in the list, expenses per vehicle, smoother updates

- **Trip length without opening the request.** Three new options in Settings → العلامة العائمة → القراءة:
  - **Remember from earlier rides (on by default):** each trip length read on a request map is saved on the phone for that pickup → destination. When the same trip appears in the list again, its length shows right away.
  - **Estimate from the addresses (off by default):** looks up both addresses with Google's address service and shows an approximate length (≈). The address text is sent to Google; you're asked before it turns on. Auto-accept never accepts an approximate distance.
  - **Open requests automatically (off by default):** if a list card's length is unknown, the app opens the request, reads the map and presses Back. It never presses Accept, Offer or Skip, opens at most one request every 8 seconds, and stops if you open something yourself. You're warned before it turns on: automation may break inDrive's terms.

- **Each vehicle has its own log.** Refills, oil changes and spare parts are saved to the active vehicle, and the Vehicle tab shows only that vehicle's records. Switching vehicles switches the list.
  - Fuel consumption is now averaged from that vehicle's own refills.
  - Records saved before this update are given to the vehicle that was active.
  - Deleting a vehicle also deletes its records (the confirmation says so).
- **In-app update:** if Android asks you to allow installing from this app, you no longer download the update again. Allow it, come back, and the install continues by itself. If you come back without allowing it, the button changes to "تثبيت".
