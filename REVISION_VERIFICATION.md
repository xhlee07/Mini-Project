# Booking / competitions / receipts verification — 2026-10-11

Final packages: customer **1.13 / code 14**, staff **1.4 / code 5**; Android min API 26 / target 35. Same debug signing certificate as earlier builds; both can be installed as updates.

## Checks completed

- Isolated PostgreSQL/PGlite: **110 existing domain checks + 75 payment checks + 33 revision checks = 218**. New checks exercise the 30-minute minimum, precise date/time/player errors, staff-only events, Draft visibility and publishing, consolation prizes, coupon eligibility/limits/reservation/cancellation/refund, repeated checkout, frozen receipt metadata, cross-account receipt denial, private worker authorization and missing-email configuration.
- Customer and staff each passed **29 JVM tests**. Three new tests cover current-minute booking grace, each incorrect booking field, decimal-money parsing without silent rounding, and reporting multiple invalid form fields. Final builds and Android lint passed in `revision-artifacts.log`; the final custom-code localization fix was compiled and linted in `revision-package-final.log`.
- Final revision/payment device run: **8 applicable customer + 5 applicable staff checks passed**, in `revision-final-device-and-payments.log`. Covers offline cached cold start and explicit logout, encrypted vault persistence, booking reset/current minute/30 minutes, error before dialog fields, staff heading/button spacing, competition/prize/poster/promo forms, Unicode native PDF, small phone at 130% font, recreation, card/TNG/FPX simulation, lost-response retry, refunds, Chinese tablet and invalid-coupon recovery. Role-inapplicable cases are skipped deliberately.
- Two final existing-flow checks passed in `revision-existing-flows.log`: settings/language/background persistence and feedback retry without duplicate submission; VIP party selection and racing-simulator booking at the new 30-minute prices.
- **8 mocked Edge contracts** passed: unsigned/expired/customer access denied, JPEG validation, staff-owned Storage path, modern service-key use, incorrect worker secret denied, and no sending before configuration. `revision-edge-contract.log`. The mock made no external request and sent no email.
- `revision-mail-test.log` and generated examples verify all receipt dates/times/duration/prices, escaped HTML and Chinese PDF generation. Both actual native and server PDFs were rendered and visually inspected. Native PDF includes the year, duration and Chinese name. A variable-font subset initially lost glyphs; the final server PDF uses Helvetica for Latin text and an embedded static Noto CJK font for Unicode. CJK PDFs are larger (~14 MB); native PDFs remain small.
- An unrelated Pixel Launcher ANR dialog contaminated early emulator screenshots. It was cleared and the final run was repeated; clean images are in `screenshots/revision/device-final/`. No CTFLY ANR was observed in these final checks.

All device testing used only the task-owned `emulator-5554`, API 37/software rendering. Huawei nova 5T / Android 9 / EMUI 12 hardware is **not** verified by these emulator checks. Test fixtures are not packaged into the normal APK.

## Hosted project

Target: `shfatnjwpdyxdbehvjil` — the user's existing project. Applied migrations, with local filenames aligned to hosted versions:

- `supabase/migrations/20261011141837_booking_events_receipts.sql`
- `supabase/migrations/20261011142842_receipt_worker_configuration.sql`

The follow-up adds the explicit singleton WHERE condition required by the hosted REST safe-update setting. It does not turn off that setting.

`club-services` Edge Function is ACTIVE, version 3. Platform JWT verification is disabled **because the function implements custom authentication**: client calls validate the Bearer token using Auth `/user`, then require an active staff/admin snapshot; worker calls require a separate high-entropy secret stored in the private database schema and a service-role-only RPC. An unsigned deployed request returns HTTP 401. User-supplied mail recipients are not accepted; recipients come from the receipt owner's Auth email.

The private cron secret is neither in the APK nor printed in logs. New public tables have RLS enabled, direct anon/authenticated grants revoked, and role/ownership checks through the existing RPC boundary. The public worker wrapper is SECURITY INVOKER and service-role-only; privileged worker/cron functions live in the private schema.

Hosted rollback verification created a temporary account/resource, reserved a current-minute 30-minute session, applied a free code, settled a simulated payment, checked frozen receipt details and queued outbox status. **The entire transaction was rolled back**. Later checks confirmed zero temporary accounts/resources. No test message was left queued or sent.

The existing session-expiry cron remains. The new `ctfly-receipt-mail` cron runs every minute; its final observed HTTP response was **200 / {configured:false,processed:0}**. Mail service configuration is still absent. SMTP network delivery and real Gmail inbox arrival have **not** been tested. See `MAIL_SETUP_中文.md` for the two server Secrets needed. A successful provider acceptance is recorded as Sent; uncertain delivery is retained as Unknown rather than silently resent.

New bill details freeze resource/activity/session/customer/items and prices at creation. Historical bills keep their original amounts and are supplemented from available linked records at migration time; metadata that was never historically stored cannot be independently reconstructed.

## Advisor review

No new publicly callable privileged mail function was introduced. RLS/no-policy INFO notices are expected for the RPC-only tables: direct table access is revoked. [Supabase RLS guidance](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy).

Existing warnings remain for the public btree_gist extension, intentionally callable original fixed-search-path RPCs, and disabled leaked-password protection. This feature did not change those settings. [Extension guidance](https://supabase.com/docs/guides/database/database-linter?lint=0014_extension_in_public), [authenticated RPC guidance](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable), [password protection](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection). New foreign keys have covering indexes; advisor unindexed-key notices concern existing tables.

## Release hashes

- Customer APK SHA-256: `496EA8294A2FABF002BC3AC73EB42E30C3B316A59690511D8E88DD010E4257A9`
- Staff APK SHA-256: `91044A7E5BAD4620FCB30D4C517437BA781128B2524971F8C5F28E88E18C3D7C`
- Both signing certificates SHA-256: `ae52756d7c5af2657fa82e7742845920dc26d4e7fa4d42c0f8109b8d433f436f`

Payment remains a server simulator. No live financial provider, money transfer or real payment credential was used.
