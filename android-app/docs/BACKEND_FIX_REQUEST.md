# RollTrack PDA – Backend Fix Request

## English

**Problem:** The PDA app now reaches the server correctly. Login works, and Load and Unload return `SUCCESS "2 item(s)"`. But **the roll tags are not saved**. The procedures behind Load and Unload (`TAGGING_RFID_TO_LOAD`, `TAGGING_RFID_TO_UNLOAD`) only count the tags; their UPDATE line is commented out.

**Why it matters:** The app shows "success" while the database stays unchanged, so later steps can never find these rolls.

**What needs to be done:**
1. Make `TAGGING_RFID_TO_LOAD` and `TAGGING_RFID_TO_UNLOAD` actually mark each roll. The database already has working procedures for this: `LOADING_WORK_ORDER_PROS` and `UNLOADING_WORK_ORDER_PROS`, which set `LOAD_FLG` / `UNLOAD_FLG` in `TEX_WO_TUBE_DTLS_RF`. Just call them for each tag.
2. Return how many rolls were saved and which ones failed, plus the real DB error instead of `-1`.
3. Tag Issue has the same problem: `sub-contract-issue` calls `TAGGING_RFID_TO_ROLL`, which is also a placeholder (counts tags, saves nothing). And `req-scan` returns `NO_OF_ITEMS` hard-coded to `10`; it should return the real required / issued / balance counts for the requisition.
4. Production weights: `ref-weight` is also a placeholder. Its code has only the comment `-- UPDATE ROLL WEIGHT HERE` and echoes ITEM and WEIGHT back with SUCCESS, so no weight is saved. It needs the real UPDATE of the roll weight.
5. ~~In the Postman collection, change `xact_erp/api/sub-contract-issue` to `snym_erp/api/sub-contract-issue`~~ — **done** in `PDA.postman_collection.json` (the `xact_erp` path gives 404).
6. If the `load` / `unload` ORDS handlers build their own reply text (e.g. `SUCCESS` + `n item(s)`) or run `TO_NUMBER` on `P_RESPONSE`, change them to pass `P_RESPONSE` through unchanged. Otherwise the new reply never reaches the PDA.

No request parameters change. The app sends `USER_ID` and `ITEMS` exactly as before.

**Please confirm before running the SQL** (the app team cannot see these):
- The parameter list of `LOADING_WORK_ORDER_PROS` / `UNLOADING_WORK_ORDER_PROS`, and what the 2nd parameter is. The script passes `NULL`; if it is a user ID it should get `USER_ID`.
- Whether those procedures `COMMIT` on their own. If so, each tag is saved immediately and there is no all-or-nothing rollback.
- The value `LOAD_FLG` / `UNLOAD_FLG` has once set (the script assumes `1`).

**Reply format the app expects:** `2 loaded, 1 rejected - <tag> <reason>` (or `unloaded` / `issued`). `0 loaded…` means nothing was saved. Errors are `-1` (USER_ID missing), `-3` (ITEMS missing) or `-9: <ORA error>`. The PDA app already handles all of these.

---

## বাংলা

**সমস্যা:** PDA অ্যাপ এখন সার্ভারে ঠিকমতো পৌঁছাচ্ছে। লগইন কাজ করছে, Load ও Unload `SUCCESS "2 item(s)"` ফেরত দিচ্ছে। কিন্তু **রোল ট্যাগগুলো ডাটাবেসে সেভ হচ্ছে না।** Load/Unload-এর পেছনের প্রসিডিউর (`TAGGING_RFID_TO_LOAD`, `TAGGING_RFID_TO_UNLOAD`) শুধু ট্যাগ গোনে; UPDATE লাইনটা কমেন্ট করে রাখা।

**কেন জরুরি:** অ্যাপে "সফল" দেখায়, কিন্তু ডাটাবেসে কিছুই বদলায় না। ফলে পরের ধাপে রোলগুলো পাওয়া যাবে না।

**যা করতে হবে:**
1. `TAGGING_RFID_TO_LOAD` ও `TAGGING_RFID_TO_UNLOAD` যেন প্রতিটি রোল আসলে মার্ক করে। এর জন্য ডাটাবেসে আগে থেকেই কাজের প্রসিডিউর আছে: `LOADING_WORK_ORDER_PROS` ও `UNLOADING_WORK_ORDER_PROS`, যেগুলো `TEX_WO_TUBE_DTLS_RF` টেবিলে `LOAD_FLG` / `UNLOAD_FLG` সেট করে। প্রতিটি ট্যাগের জন্য শুধু এগুলো কল করলেই হবে।
2. কয়টা রোল সেভ হলো আর কোনগুলো হলো না, সেটা ফেরত দিতে হবে। আর `-1`-এর বদলে আসল DB error দিতে হবে।
3. Tag Issue-এও একই সমস্যা: `sub-contract-issue` যে `TAGGING_RFID_TO_ROLL` কল করে, সেটাও শুধু গোনে, কিছু সেভ করে না। আর `req-scan` সবসময় `NO_OF_ITEMS = 10` ফেরত দেয়; আসল required / issued / balance দিতে হবে।
4. Production-এর ওজন: `ref-weight`-ও শুধু নমুনা কোড। সেখানে শুধু `-- UPDATE ROLL WEIGHT HERE` মন্তব্য আছে, ITEM আর WEIGHT ফেরত দেয় কিন্তু কিছু সেভ করে না। রোলের ওজন আসলে আপডেট করতে হবে।
5. ~~Postman collection-এ `xact_erp/api/sub-contract-issue` বদলে `snym_erp/api/sub-contract-issue` করতে হবে~~ — `PDA.postman_collection.json`-এ **করা হয়ে গেছে** (`xact_erp` দিলে 404 আসে)।
6. `load` / `unload` ORDS handler যদি নিজে reply লেখে (যেমন `SUCCESS` + `n item(s)`) বা `P_RESPONSE`-এ `TO_NUMBER` চালায়, তাহলে সেটা বদলে `P_RESPONSE` হুবহু ফেরত দিতে হবে। নইলে নতুন reply PDA পর্যন্ত পৌঁছাবে না।

কোনো প্যারামিটার বদলাচ্ছে না। অ্যাপ আগের মতোই `USER_ID` আর `ITEMS` পাঠায়।

**SQL চালানোর আগে নিশ্চিত করুন** (অ্যাপ টিম এগুলো দেখতে পায় না):
- `LOADING_WORK_ORDER_PROS` / `UNLOADING_WORK_ORDER_PROS`-এর প্যারামিটার লিস্ট, আর ২য় প্যারামিটারটা কী। স্ক্রিপ্ট সেখানে `NULL` দেয়; এটা user ID হলে `USER_ID` দিতে হবে।
- ওই প্রসিডিউরগুলো নিজেরাই `COMMIT` করে কি না। করলে প্রতিটি ট্যাগ সাথে সাথে সেভ হয়, পুরো ব্যাচ rollback হয় না।
- সেট হওয়ার পর `LOAD_FLG` / `UNLOAD_FLG`-এর মান কত (স্ক্রিপ্ট ধরে নিয়েছে `1`)।

**অ্যাপ যে reply আশা করে:** `2 loaded, 1 rejected - <tag> <কারণ>` (বা `unloaded` / `issued`)। `0 loaded…` মানে কিছুই সেভ হয়নি। Error হলে `-1` (USER_ID নেই), `-3` (ITEMS নেই) বা `-9: <ORA error>`। PDA অ্যাপ এগুলো সবই বোঝে।

---
*A proposed SQL change for Load / Unload (items 1, 2, 6) is in `proposed_db_fix.sql`: read-only checks, backup, a new helper procedure tested on its own, then the switch-over. Tag Issue (item 3) has read-only checks only (Part B); its fix needs the requisition tables first. Review before running.*

*Load / Unload-এর (১, ২, ৬ নম্বর) প্রস্তাবিত SQL `proposed_db_fix.sql` ফাইলে আছে: read-only চেক, ব্যাকআপ, নতুন helper প্রসিডিউর আলাদাভাবে টেস্ট, তারপর switch-over। Tag Issue-এর (৩ নম্বর) জন্য শুধু read-only চেক আছে (Part B); requisition টেবিলগুলো জানার পর fix লেখা যাবে। চালানোর আগে রিভিউ করুন।*
