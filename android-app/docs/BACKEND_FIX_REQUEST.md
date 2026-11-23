# RollTrack PDA – Backend Fix Request

## English

**Problem:** The PDA app now reaches the server correctly. Login works, and Load and Unload return `SUCCESS "2 item(s)"`. But **the roll tags are not saved**. The procedures behind Load and Unload (`TAGGING_RFID_TO_LOAD`, `TAGGING_RFID_TO_UNLOAD`) only count the tags; their UPDATE line is commented out.

**Why it matters:** The app shows "success" while the database stays unchanged, so later steps can never find these rolls.

**What needs to be done:**
1. Make `TAGGING_RFID_TO_LOAD` and `TAGGING_RFID_TO_UNLOAD` actually mark each roll. The database already has working procedures for this: `LOADING_WORK_ORDER_PROS` and `UNLOADING_WORK_ORDER_PROS`, which set `LOAD_FLG` / `UNLOAD_FLG` in `TEX_WO_TUBE_DTLS_RF`. Just call them for each tag.
2. Return how many rolls were saved and which ones failed, plus the real DB error instead of `-1`.
3. Tag Issue has the same problem: `sub-contract-issue` calls `TAGGING_RFID_TO_ROLL`, which is also a placeholder (counts tags, saves nothing). And `req-scan` returns `NO_OF_ITEMS` hard-coded to `10`; it should return the real required / issued / balance counts for the requisition.
4. Production weights: `ref-weight` is also a placeholder. Its code has only the comment `-- UPDATE ROLL WEIGHT HERE` and echoes ITEM and WEIGHT back with SUCCESS, so no weight is saved. It needs the real UPDATE of the roll weight.
5. In the Postman collection, change `xact_erp/api/sub-contract-issue` to `snym_erp/api/sub-contract-issue` (the `xact_erp` path gives 404).

No request parameters change. The app sends `USER_ID` and `ITEMS` exactly as before.

---

## বাংলা

**সমস্যা:** PDA অ্যাপ এখন সার্ভারে ঠিকমতো পৌঁছাচ্ছে। লগইন কাজ করছে, Load ও Unload `SUCCESS "2 item(s)"` ফেরত দিচ্ছে। কিন্তু **রোল ট্যাগগুলো ডাটাবেসে সেভ হচ্ছে না।** Load/Unload-এর পেছনের প্রসিডিউর (`TAGGING_RFID_TO_LOAD`, `TAGGING_RFID_TO_UNLOAD`) শুধু ট্যাগ গোনে; UPDATE লাইনটা কমেন্ট করে রাখা।

**কেন জরুরি:** অ্যাপে "সফল" দেখায়, কিন্তু ডাটাবেসে কিছুই বদলায় না। ফলে পরের ধাপে রোলগুলো পাওয়া যাবে না।

**যা করতে হবে:**
1. `TAGGING_RFID_TO_LOAD` ও `TAGGING_RFID_TO_UNLOAD` যেন প্রতিটি রোল আসলে মার্ক করে। এর জন্য ডাটাবেসে আগে থেকেই কাজের প্রসিডিউর আছে: `LOADING_WORK_ORDER_PROS` ও `UNLOADING_WORK_ORDER_PROS`, যেগুলো `TEX_WO_TUBE_DTLS_RF` টেবিলে `LOAD_FLG` / `UNLOAD_FLG` সেট করে। প্রতিটি ট্যাগের জন্য শুধু এগুলো কল করলেই হবে।
2. কয়টা রোল সেভ হলো আর কোনগুলো হলো না, সেটা ফেরত দিতে হবে। আর `-1`-এর বদলে আসল DB error দিতে হবে।
3. Tag Issue-এও একই সমস্যা: `sub-contract-issue` যে `TAGGING_RFID_TO_ROLL` কল করে, সেটাও শুধু গোনে, কিছু সেভ করে না। আর `req-scan` সবসময় `NO_OF_ITEMS = 10` ফেরত দেয়; আসল required / issued / balance দিতে হবে।
4. Production-এর ওজন: `ref-weight`-ও শুধু নমুনা কোড। সেখানে শুধু `-- UPDATE ROLL WEIGHT HERE` মন্তব্য আছে, ITEM আর WEIGHT ফেরত দেয় কিন্তু কিছু সেভ করে না। রোলের ওজন আসলে আপডেট করতে হবে।
5. Postman collection-এ `xact_erp/api/sub-contract-issue` বদলে `snym_erp/api/sub-contract-issue` করতে হবে (`xact_erp` দিলে 404 আসে)।

কোনো প্যারামিটার বদলাচ্ছে না। অ্যাপ আগের মতোই `USER_ID` আর `ITEMS` পাঠায়।

---
*A proposed SQL change is in `proposed_db_fix.sql` (review before running). / প্রস্তাবিত SQL পরিবর্তন `proposed_db_fix.sql` ফাইলে আছে (চালানোর আগে রিভিউ করুন)।*
