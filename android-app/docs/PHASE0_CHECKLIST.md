# Phase 0 checklist — on the real tablet

Install the dev build, open **Settings → Diagnostics**. Write each result here.

## 1. Tag number format (most important)
- [ ] *Tag test* tab. Tap one yellow tag on the built-in NFC area → note canonical id.
- [ ] Tap the **same** tag on the USB reader → canonical id must be identical.
  - If not: Settings → USB reader byte order → switch, repeat.
- [ ] Repeat with 5 tags. Also note the number printed on the disc and which format it matches.
- [ ] Ask the ERP team which format their tables hold → set *Format the ERP expects*.

| Tag | NFC canonical | USB raw | Printed on disc | Match? |
|---|---|---|---|---|
| 1 | | | | |

## 2. ERP replies
- [ ] *API probe* tab, demo mode **off** in Settings. Use test data only.
- [ ] Send each template (login with a test user, req-scan, …). Copy each reply.
- [ ] Paste replies as "examples" into `PDA.postman_collection.json` and send to the app team.
  If key names differ from those in `OrdsErpApi.kt`, add them to the candidate lists there.

## 3. Speed
- [ ] *Tap speed* tab: tap 50 tags at working pace → seconds per tag and minutes per 150-roll vehicle.

## 4. Heat press
- [ ] Heat-press one tagged roll as in Step 6. Does the tag still read in *Tag test*?

## 5. Open questions (plan section 8)
Get answers from the business / ERP team before UAT.
