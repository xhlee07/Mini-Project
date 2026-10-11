# Test payment verification — 2026-10-11

Delivered customer 1.12 / code 13 (`com.ctfly.app`) and staff 1.3 / code 4 (`com.ctfly.staff`). Both are development APKs, signed with the same certificate as their previous versions, so an ordinary update preserves app storage. Minimum Android API remains 26.

## Payment scope

TNG, FPX and cards use CTFLY's authenticated server simulator, not a financial provider's connected sandbox. There are no live merchant credentials, wallet/bank redirects, card-number inputs or production payment switch. Successful simulation credits only `test_points` and `test_spent`. Test bills are excluded from counter revenue and best-seller reports. Existing staff cash recording has no wallet or card debit capability.

## Local checks

- `payment-database.log`: 75 payment assertions/negative cases plus 110 existing domain assertions passed against isolated PGlite. Coverage includes all three source kinds; all three methods; failure/processing/cancel/success; deduplicated checkout and settlement; late callbacks; expired/cancelled sources; stock released once; unchanged hold deadline on retries; amount changes; test refund versus counter void; fulfillment guards; active-account and ownership restrictions; direct table/private-function denial; counter-versus-simulator settlement.
- Customer and staff each passed 26 JVM unit tests (52 total). `payment-release-validation.log` includes build, unit tests and lint; `payment-final-artifacts.log` confirms build and lint after the final dialog height fix.
- Payment UI checks cover a 360 × 640 dp phone at 130% font, card checkout, saved-state recreation, receipt/share-button visibility, test refund, FPX failure/retry, processing/cancellation, TNG confirmation after a deliberately lost response, and Chinese tablet checkout. The lost-response retry used the same request ID and produced one receipt.
- Staff checks cover test transaction visibility, customer name/status/reference/details, customer payment controls absent in staff, and existing operations, feedback, extension quotes and manager pages.
- Existing customer regressions cover booking payloads, shopping draft recreation, all customer pages/search, birth-date layout, settings/languages/feedback, login gating, password preservation and tablet navigation. Across the final and preceding relevant runs, 16 distinct applicable device scenarios passed. One earlier feedback retry run timed out while a transient snackbar was present; it passed in the subsequent run without a payment-related change to the feedback feature.
- `payment-dialog-delivery.log`: all three final checks passed after constraining dialogs to the available phone height: test card receipt/share/refund, birth-date form and private-room/simulator reservation form.

Selected screenshots are under `screenshots/payments/`: `payment-phone-test-receipt.png`, `payment-phone-success.png`, `payment-tablet-chinese.png`, and `payment-staff-transactions.png`. The final receipt was visually inspected with its share button fully above the navigation area. All device tests used only task-owned `emulator-5554`, API 37 with software rendering. Huawei nova 5T / Android 9 / EMUI 12 hardware remains untested.

## Hosted school project

Migration `test_checkout` was successfully applied to the existing school project. The local CLI-generated file was renamed to `supabase/migrations/20261011122341_test_checkout.sql` to match the version recorded by the hosted migration tool. Do not manually rerun it.

Post-deployment metadata confirmed: migration recorded once; simulation snapshot capability enabled; test-payment RLS enabled; no direct table access for anon/authenticated; authenticated cannot use the private schema; zero new SECURITY DEFINER helpers. Existing `ctfly-session-clock` remains active every minute and invokes the extended expiry function. Expiry is also checked by authorized snapshots/actions.

An authenticated cloud validation transaction created a temporary validation account, completed and refunded TNG/FPX/Card test orders, checked request deduplication, unchanged real membership balances, restored test balances/stock, and denied direct table access. The entire validation transaction was rolled back. A subsequent query confirmed zero validation accounts and zero persisted test transactions. No real payment platform, email delivery or personal Android device was used.

## Existing advisor findings

Security advisor warnings were unchanged after deployment. The new table adds one INFO entry for RLS with no policy: direct grants are revoked and access is intentionally through the role/ownership-checked RPC. See [RLS no-policy guidance](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy).

The project already had warnings for [btree_gist in public](https://supabase.com/docs/guides/database/database-linter?lint=0014_extension_in_public), the existing [anon catalog definer RPC](https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable), four existing [authenticated definer RPCs](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable), and [disabled leaked-password protection](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection). These were recorded, not silently reconfigured as part of payment work.

Performance advisor reported 22 existing [unindexed foreign-key](https://supabase.com/docs/guides/database/database-linter?lint=0001_unindexed_foreign_keys) INFO findings and two [unused-index](https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index) INFO findings; none involved the new payment table.

## APK SHA-256

- CTFLY-Gaming-Cafe-1.12.apk: `186746A89C8296336B0147D2D56DCBBF519EFD4CB9CD6DE6FF7B8E296A324319`
- Staff-CTFLY-1.3.apk: `A77CF9A345606CDC3768A45209B125526FAC5CA9C7336714D6F8B9945B5E49CB`
- Signing certificate SHA-256: `ae52756d7c5af2657fa82e7742845920dc26d4e7fa4d42c0f8109b8d433f436f`

Genuine collection requires a separate merchant/provider integration, verified provider callbacks, reconciliation and production authorization. It is not enabled by changing a client flag.
