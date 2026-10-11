# CTFLY — Android gaming club

Native Kotlin + Jetpack Compose / Material 3, Supabase Auth + PostgreSQL, HTTPS RPCs. English, Simplified Chinese and Bahasa Melayu UI. Start with **START_HERE_中文.md** for the exact dashboard steps.

Customer **1.13 / code 14**, Staff **1.4 / code 5** add 30-minute minimum/current-time booking, frontmost field-specific validation, encrypted cached login/snapshot retention, staff competitions/posters/prizes, quota-controlled promotion codes, frozen receipt details, native PDF open/share and a server receipt-mail outbox. `booking_events_receipts` and `receipt_worker_configuration` are deployed to the user's project. Email delivery still requires server Secrets; see `MAIL_SETUP_中文.md`. Payments remain simulated. Staff Transactions is now in the top More menu. See `UPDATE_1.13_中文.md` and `REVISION_VERIFICATION.md`.

Customer **1.12 / code 13** and Staff **1.3 / code 4** add server-backed **simulated** TNG/FPX/card checkout for bookings, café orders and event entries, ten-minute holds, test receipts, separate demo points, resumable attempts and simulated refunds. No live payment gateway, financial credentials or money deduction is included. Migration `supabase/migrations/20261011122341_test_checkout.sql` is applied to the current school project; do not rerun it manually. See `PAYMENTS_中文.md` and `PAYMENT_VERIFICATION.md`.

Customer **1.11 / code 12** replaces the solid trackside paddock box with an enterable washroom pavilion on the pedestrian side of the parking area. Its roof and canopy clear the road, rails and 5 m chase-camera envelope. All four tour areas now have a separate wash corridor, three toilet cubicles and two shower cubicles, with 24 working doors including the entrances. Doors animate, prevent walking through the leaf, reject use within the swing, and retain their state across floor switches/recreation. Choose Washroom in each floor's destination menu. Upstairs rear wings provide room for these facilities. Shower fittings, drains, basin taps, mirrors, signs and warmer material detail are included. Optional derivative-based surface grain keeps the GLES2 fallback, grass uses filtered variation, and door wood grain follows the moving mesh. This remains a real-time concept scene, not photographic reconstruction or Genshin-level production art. Install `CTFLY-Gaming-Cafe-1.11.apk` over 1.10; no new SQL is needed.

Customer **1.10 / code 11** rebuilds the phone tour controls around available landscape height. Jump is a large circular white arrow; Run sits beside it at the bottom right. Kart entry, floor/ground exit and door actions move above movement controls. Short-screen HUD line heights/padding and joystick/pedal budgets prevent overlap; gas/brake stay at least 80dp high. Race restart/exit share the phone top bar, avoiding another toolbar row. Phone destinations open a scrollable dialog, while the tablet keeps inline destinations. Reverse has a distinct down arrow. Pedals track their own finger, allowing repeated gas/brake presses while another thumb remains on steering. Walking maps sit below venue actions, and the racing map toggle can hide/show the map. Short layouts keep the current lap time visible. Paddock pavement is lifted slightly above the grass to avoid depth occlusion at low render resolutions. No new SQL is required; update with `CTFLY-Gaming-Cafe-1.10.apk`.

Customer **1.9 / code 10** fixes phone tour presentation and interaction. Entering the tour requests sensor landscape and restores the previous orientation on exit, preserving that original setting across activity recreation. Compact phone landscape defaults to the expanded scene; destinations can still be shown and floor/quality dialogs scroll. `TourSurface.kt` renders OpenGL into a TextureView with a dedicated EGL thread so the scene shares the normal view hierarchy and clipping. Adaptive render budgets and GLES2 shader fallback remain. Independent per-pointer Compose input supports repeated look swipes while the joystick stays held; input is batched per GL frame and camera turning has short frame-rate-independent easing. Date selectors keep labels on one line and use two rows on narrow screens or large font scales. Guest exploration is removed, including bypass via a saved preview flag or Tour route; signed-out refresh no longer requests the public catalog. No new cloud migration for 1.9. Physical Huawei/EMUI validation remains necessary.

Customer **1.8 / code 9**, staff **1.2 / code 3** add Settings with English / Simplified Chinese / Bahasa Melayu and four persistent dark background palettes. Preferences apply immediately and are stored per app on the device. Presentation translation leaves canonical routes/action IDs unchanged; submitted feedback is displayed verbatim. Signed-in customers can send a rating, category, title and message and follow their own status. Staff/admin have a feedback inbox with New/Reviewed/Resolved filters and processing actions. Apply `supabase/006_settings_feedback.sql` to the existing cloud project, then refresh both apps; the isolated migration test does not modify the hosted database. Submission uses the existing transaction/idempotency flow, preserves drafts on failure and prevents duplicate writes on retries.

Customer **1.7 / code 8** replaces small kart controls with large pedal targets, brake+steer drifting, reverse, a separate top restart/exit toolbar and gentle frame-independent kerb friction. Jump is a larger 80dp target. The esports floor has 24 PCs in four six-seat rows plus nine private rooms (three each of 1/2/4 PCs), booked as exclusive rooms for 1–4 guests. Proposed whole-room rates are RM15/28/52 per hour. The simulator showroom has four Standard and four VIP cockpits, RM20/35 per hour, including detailed extrusion frames, bucket seats, harness slots, wheels, pedals, shifters and curved displays. Reworked materials use GGX highlights, environment reflections, tighter shadow depth/bias and a POT 8×8 material/sign atlas. Apply `supabase/005_private_rooms_simulators.sql` after 004, then refresh Play. It preserves known old VIP resource IDs, existing booking totals/tokens and manager-edited prices on rerun. It has not been applied to the user's hosted project.

Customer **1.6 / code 7** adds continuous esports bench desks, detailed curved monitors/arms/keyboards/headsets, smooth upholstered chairs and warmer neutral materials. Walking is 4.5 m/s, Run is 7.2 m/s, and Jump uses gravity/ground landing. The G washroom has a front wash area and three cubicles with interactive doors and matching collision; a door cannot close through the visitor. The outdoor circuit is explicitly at ground level, with a continuous ground plane and a 582 m spline road. At the paddock, **Take a kart** starts an offline time trial: joystick steering, hold GAS/BRAKE, hold DRIFT (maintains throttle), chase camera, eight ordered gates, lap timer, device-local best, restart and exit. Camera, race and door state survive activity recreation; held controls release on pause/disposal. Existing cloud inventory is unchanged; no new SQL for 1.6.

Two application modules: `app` builds **CTFLY Gaming Cafe** (`com.ctfly.app`, customer only); `staff` builds **Staff CTFLY** (`com.ctfly.staff`, staff/admin only). Select either run configuration in Android Studio. Both can coexist and use the same existing Supabase project, with separate Android app storage and Keystore credentials. No database migration is required for this split. Shared Kotlin/resources are compiled separately by the two modules; each generates its own BuildConfig with an immutable app role. Role checks apply on login and restored-session refresh; the customer app has no staff portal and the staff app has no public signup or customer catalog navigation.

Open this folder directly in Android Studio. Compile SDK 35, minimum API 26; Kotlin 2.1.0, AGP 8.8.0, Gradle 8.12. No handwritten Java source. Gradle wrapper included. `local.properties` is local-only; Android Studio can recreate it for another computer.

## Structure

- `TourScreen.kt`, `TourSurface.kt`, `TourLook.kt`, `TourRenderer.kt`, `TourWorld.kt`, `TourMesh.kt`, `TourDynamics.kt`, `VenueDirectory.kt`: customer-only campus tour and offline kart time trial, entirely Kotlin with native OpenGL ES in Compose. Four separate scene meshes, material textures, adaptive graphics budgets, joystick/look controls, collision, lift navigation, maps and saved experience state. The original concept geometry is not a real-site scan. No external 3D dependency or network asset downloads; live booking expansion uses migration 004.

- `app/src/main/kotlin/com/ctfly/app`: Kotlin application, network/auth boundary, lifecycle state, responsive Compose screens.
- `supabase/001_ctfly.sql`: first-install schema, constrained transactions, RPCs, grants, RLS and catalog seed. Must be run on the target Supabase project before use.
- `supabase/002_staff_roles.sql`: bootstrap existing registered users as staff/admin. Owner-only SQL, never embedded in the APK.
- `supabase/003_session_clock.sql`: hosted Supabase scheduled session expiry.
- `supabase/email-*.html`: signup/recovery OTP email templates.
- `tests/database.mjs`: real PostgreSQL semantics via isolated PGlite, no cloud writes. Produces instrumentation-only UI fixtures.
- `app/src/androidTest`: phone, tablet, account-role and rotation/recreation tests with an injected fixture gateway. Test fixtures are not packaged into the normal APK.

UI assets are retained from the original project. Photos were generated for CTFLY; original prompts remain in `../Mini-Project/assets`. Lounge illustrations are Compose Canvas/vector icons. Photos decode off the main thread with bounded sample size.

## Data and permissions

The publishable key is intentionally a client key, not an administrative secret. Access/refresh tokens are encrypted by Android Keystore. All public business tables have RLS enabled and no direct anon/authenticated table grants. Catalog reads are public; account snapshots and mutations use explicit role/ownership checks inside fixed-search-path SECURITY DEFINER functions. Signup role metadata is ignored. Concurrency uses row locks and exclusion constraints. Prices, discounts, stock, age and points are server-validated. Mutation request IDs deduplicate uncertain network retries.

Bookings use Malaysia-facing dates and UTC instants. Paid check-in requires an unused token and the scheduled time window. Late arrivals retain the original end. Expiry is run on access and by the optional hosted cron job; exact PC locking is outside this APK. Cash collection is a staff-attested counter payment, not an online payment gateway.

Current snapshots cap bookings/orders at the most recent 300; the initial dashboard is suitable for a course project/small venue. Production-scale history needs pagination, monitoring and operating policies. Transactions use server truth; the UI does not optimistically claim payment or booking success while offline.

## Build and test

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :staff:assembleDebug :staff:testDebugUnitTest
# With an emulator/device running:
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :staff:connectedDebugAndroidTest
# Isolated PostgreSQL tests (Node.js; install tests/package.json with pnpm first):
node tests/database.mjs
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a development build; store publication needs your own release signing key and current Play requirements.

Staff debug APK: `staff/build/outputs/apk/debug/staff-debug.apk`.

Customer 1.4 separates the tour into L1 Gaming, L2 Billiards, G Social Lounge and Outdoor Circuit. Planned capacity is 24 standard PCs + 8 VIP PCs, 8 pool + 4 snooker tables, 4 console suites + 4 simulators + 6 arcade cabinets, and 6 karts. Floors load independently and connect through the elevator UI. The renderer includes material textures, curved furniture, industrial ducts, suspended lighting, shadows, contact shading and adaptive graphics budgets. Models are bundled offline; normal Android Studio builds use the checked-in assets. See `TOUR_ASSETS.md` for provenance and regeneration instructions.

Existing Supabase installations must run `supabase/004_venue_campus.sql` once to activate the expanded live inventory and short kart/arcade sessions. It is repeatable and preserves account/resource IDs, bookings and payments. Customer Play shows counts from the actual cloud catalog rather than pretending the migration has run.

Customer 1.5 improves the interactive tour's clarity with native-resolution Ultra mode, optional Balanced/Smooth modes, an expanded scene view, dedicated material/background textures, derivative-correct mip filtering where supported, anisotropy and cleaner stone/wood/felt/leather. No new SQL is needed for 1.5. Keep the existing 004 setup if already applied.

Customer/staff/admin test data is injected only by instrumentation. Live end-to-end auth/email/booking against the user's Supabase is pending their schema and SMTP setup. FPX/TNG, Google OAuth and PC-control agents require external setup and are not presented as working integrations.
