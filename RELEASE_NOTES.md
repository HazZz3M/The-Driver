### The Driver v1.18.1 - Correct areas in the analysis

- **Areas are now the real neighbourhood.** inDrive addresses look like "Place (street, neighbourhood, police district, city, country)". The analysis used the last part, so "مصر)" (the country) was counted as an area. It now takes the neighbourhood, e.g. "التجنيد (الحمراء الثانية، قسم أول أسيوط، مصر)" → "الحمراء الثانية".
  - The country, police districts (قسم / مركز / أول / ثان), the city ("Asyut 2") and plus codes are never used as an area; streets only when nothing better is left.
  - Applies to your whole existing log at once (areas are worked out when the analysis is shown).
