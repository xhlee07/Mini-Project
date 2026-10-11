# Staff CTFLY

Independent Android application module, application ID `com.ctfly.staff`. Select **staff** in Android Studio and Run, or build with `gradlew.bat :staff:assembleDebug`.

The module compiles the shared Kotlin and Compose resources from `../app/src/main` with its own generated `BuildConfig.STAFF_APP=true`, app label, launcher icon and application ID. It does not depend on or launch the customer APK. Android storage and encrypted sessions are separate from the customer app. Shared code lets bug fixes and layout improvements reach both applications together.

Only staff/admin roles can sign in; restored sessions are checked again. Navigation exposes Operations, My shifts, Handover, Equipment, plus Management for admins. There is no customer booking/shopping navigation, role switcher, public signup or catalog preview. Password recovery remains available.

Both apps use the existing Supabase project. Existing employee credentials work here unchanged. To provision a new employee, register the account through the customer app, then assign the staff/admin role using `supabase/002_staff_roles.sql`, and sign in here. The customer app no longer accepts that promoted account.

APK: `build/outputs/apk/debug/staff-debug.apk`.
