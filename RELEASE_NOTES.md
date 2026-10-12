### The Driver v1.10.1 - Reading ride cards in the list

- **Ride data is read from the card itself, without opening it.** Each card in the list is read as a whole (price, distance, places), instead of just the price.
- **No more "؟" on every ride.** "؟" now appears only when no distance at all appears on the card. If one distance (usually the trip) isn't on the card, the profit is shown as approximate "≈" and the missing number is clearly marked in the details.
- **Zone tagging from the popup works.** The pickup and drop-off places, plus any place name on the card, now appear with "+ مفضلة" and "+ محظورة" buttons.
- **New diagnostic capture tool** (from the floating-assistant screen or the notification). It captures the structure of the inDrive screen so you can send it to the developer to improve reading accuracy.
- Auto-accept still doesn't act on any ride whose distance is approximate.
