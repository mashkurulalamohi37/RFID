# RollTrack — operator guide (one page per step)

**Everywhere:** tap a tag on the reader area of the tablet (or the USB reader). Beep = added,
double beep = already on the list, long buzz = refused (read the red card). Green / amber / red
cards at the top tell you what happened. The top bar shows Online/Offline and unsent batches.

## Step 1 — Tag Issue
1. **Scan request QR** (camera) or type the request number.
2. Check supplier, program, **Required / Issued / Balance**.
3. *Scan tags*: tap each tag → **Submit N tags**. *Count only*: type the number → **Issue N tags**.
4. Red rows were refused (e.g. tag already used) — remove or keep them aside.

## Step 2 — Production
1. Pick the work order from the list (or scan the program QR).
2. Tap a roll's tag → type the **weight in kg** → Save. Next roll.
3. Amber "Not FIFO order" = an older tag is waiting; use the oldest roll first if possible.
4. **Save N rolls**. Tap a row to correct a weight before saving.

## Step 4 — Loading
1. Enter **vehicle number** (challan, supplier if known).
2. Tap every roll. Send pallet by pallet if you like.
3. Note the **loading ref** shown after sending. **Finish vehicle** when it leaves.

## Step 5 — Unloading
1. Scan / type the **loading ref**.
2. Tap every roll coming off. Watch **Matched / Missing / Extra**.
3. **Receive** → read the register (by supplier / program / WO). Report missing rolls to the supervisor.

## Step 7 — Store Delivery
1. Enter the **destination store**.
2. **Scan the tube tag / QR** first, then tap the rolls going into that tube.
3. **Send tube**. Repeat for the next tube.
Rolls that have not finished QC + QR in the ERP are refused ("QC/QR not completed").

## Trace
Tap any roll's tag (also works from the home screen) to see where it is and its history.

## Pending
- *Waiting for connection*: will send by itself. **Send now** to try immediately.
- *No reply from ERP*: it **may** already be saved. Check in the ERP, then **ERP has it** or **Resend**.
- Nothing here is lost when you log out.
