# CTFLY Gaming Café — Neon Arena

Python + Tkinter/ttk + SQLite desktop application with exactly five business modules. The current UI uses cinematic local gaming-café photographs, cyan/violet accents, a horizontal main navigation and a vertical contextual control panel. English interface for assignment presentation. Core modules work without third-party packages; Pillow improves responsive image scaling, matplotlib adds charts, and google-auth adds genuine Google sign-in.

The redesigned layouts include a full-background sign-in/register screen, zone-based PC floor map with a booking console, image-based café catalog with an editable order tray, event cards and competition details, a membership pass and cashier terminal, reward cards, and a weekly staff calendar. Primary booking, order and checkout buttons stay visible while their detail panels scroll. Windows DPI scaling is handled before creating the Tk window.

## Latest usability update

- Café menu gives more space to products and the basket, with search, category filters, saved favorites, quantity controls and **Order again** from history. Delivery options contain only the selected member's active PCs. Empty baskets cannot be submitted; orders require review/confirmation before stock is deducted.
- Coffee, energy drink, water, lemon tea, noodles, chips, chicken burger and fries each have a separate local photograph in `assets/menu`. Digital game cards use a separate drawn graphic. Lemon tea, burger and fries are added once to existing catalogs, without resetting records or restoring products you have deactivated.
- Birth dates, booking dates, event start/end, staff hire dates, roster dates, weekly starts and task dates use read-only calendar dropdowns. Hours/minutes are dropdowns too. February and leap years are handled automatically. Booking offers **Now** or a future date.
- **Staff** is visible in the main navigation. Customers see a module explanation; employee records require `staff1 / Staff@123` or `admin / Admin@123`. Staff can see their own attendance and task board; managers assign tasks and employees complete/reopen their own missions. Weekly roster has previous/next week controls.
- Normal page switches reuse widgets and keep scroll/subview state. Data-changing refreshes invalidate cached pages, and database file changes invalidate stale views. Background sources/resized images are cached, report charts load only when opened, idle timers do not write SQLite, and one wheel dispatcher routes scrolling to the panel under the pointer.

Interaction references: [Square item modifiers and ordering](https://squareup.com/help/us/en/article/5119-create-and-manage-item-modifiers), [7shifts employee dashboard](https://kb.7shifts.com/hc/en-us/articles/4417519877011-Employee-Dashboard-Overview), and [7shifts availability/time-off workflow](https://kb.7shifts.com/hc/en-us/articles/33383119814163-Manage-availability-and-time-off-requests-Getting-Started-for-Managers). CTFLY retains its five original business modules; it does not include every feature in those products.

Additional comparisons: [Homebase task assignments and completion tracking](https://www.joinhomebase.com/industry/restaurant-task-management-app), [When I Work personal schedules and leave](https://help.wheniwork.com/article-categories/checking-the-schedule/), and [Toast pickup/delivery ordering](https://support.toasttab.com/en/article/Getting-Started-Online-Ordering). CTFLY's crew task board, weekly calendar and pickup/active-PC delivery choices implement corresponding interactions for a gaming café.

Menu photographs were generated using built-in imagegen. Exact prompts and asset attribution are in `assets/menu/IMAGE_PROMPTS.md`.

## Run in VS Code

Open this **Mini-Project** folder and select a Python 3.10+ interpreter with Tkinter. In the terminal:

```powershell
python -m pip install -r requirements.txt
python main.py
```

Alternatively open `main.py` and choose **Run Python File**. Replace `python` with `py` if that is your interpreter command. Database paths do not depend on your terminal's current folder.

Demo accounts (created on the first run):

- Admin: `admin` / `Admin@123`
- Staff: `staff1` / `Staff@123`
- Customer: `gamer1` / `Gamer@123`

Customer accounts cannot switch into admin/staff roles. Public registration always creates a customer, requires age **18+** and uses salted PBKDF2 password hashes. Booking validates age again in the service layer. Birth dates are self-declared; this app does not verify identity documents. Café timestamps use Malaysia time (UTC+8).

## Five modules

1. **PC Station & Session Management**: live floor plan, Standard/VIP rigs, hourly packages, reservations, overlapping booking detection, live countdown, automatic completion and station release, extension, early termination, cancellation of unstarted reservations, hardware CRUD and retirement. Packages are prepaid: early termination keeps the package charge. Reservations become charges at their start time.
2. **Product, Snack & Inventory Management**: searchable menu/cart, counter pickup or delivery to a member's active station, transactional stock deduction, cancellation with stock restoration, delivery stages, product/category CRUD, restocking and stock ledger. Low stock uses each product's threshold. Old products are deactivated to preserve history.
3. **Tournament & Event Management**: event CRUD, shared-arena scheduling conflict detection, team/player registration, capacity cutoff, withdrawal, single-elimination bracket for 2–64 entrants, automatic byes and winner advancement. Registration closes on bracket generation; recorded results are final. Fee changes do not change previously registered entries.
4. **Member, Billing & Loyalty Management**: member CRUD/deactivation, consolidated cashier account, tier discounts, recorded payments, receipt export, points ledger, reward CRUD, redemption vouchers and collection. Each source charge is billed once. RM 1 paid earns 1 point (whole ringgit). Silver starts at RM 300 lifetime spend with 5% discount; Gold at RM 1,000 with 10%. Upgrades apply to the next bill. Bill voiding reverses spend/points and reopens charges, provided earned points have not been spent. Staff fulfill vouchers at the counter; time vouchers do not automatically extend sessions. Card/TNG are recorded payment methods, not integrated payment gateways.
5. **Staff Scheduling & Attendance Management**: employee CRUD with optional staff logins, shift templates, weekly roster generation, dated assignments/rescheduling/cancellation, overnight conflict checks, leave request/approval/decline, clock-in/out, late/early/absence tracking and worked-hour reports. Staff clock only their own shifts; admins can clock for any employee. Existing roster times are retained when templates change. Weekly generation is all-or-nothing if any day conflicts.

Each module has a staff report and CSV export. Customer screens show only their own sessions, orders, registrations, bills, points and vouchers. There is no Arcade module. Reports distinguish gross charges from paid revenue. Station hours are booked package hours, including active packages, rather than hardware-measured utilization.

## Real Google login configuration

Google login opens your browser and uses a Desktop OAuth client, loopback callback on `127.0.0.1`, PKCE, state/nonce checks and verified ID tokens. It never asks for your Google password inside the app. Your Google Cloud client configuration is required:

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create/select a project.
2. Configure Google Auth Platform branding and audience. Add your testing accounts as test users if the app is in testing mode.
3. Create an OAuth client of type **Desktop app** and download its JSON.
4. Rename it **google_client_secret.json** and place it beside `main.py`. Use the downloaded `installed` configuration, not a Web client.
5. Install `requirements.txt`, then choose **Continue with Google**. Finish in your browser and return to the app.
6. First-time Google users enter a birth date and phone; Google email/profile scopes do not provide an age. The same 18+ rule applies.

Missing configuration/dependencies produce setup instructions; local login remains available. OAuth times out after three minutes. Accounts use Google's verified `sub` identity; a matching local email is never automatically linked. Tokens are not stored in SQLite.

Protocol references: [Google desktop OAuth](https://developers.google.com/identity/protocols/oauth2/native-app), [ID token verification](https://developers.google.com/identity/sign-in/web/backend-auth).

## Files and preserved data

- `main.py`: new entry point.
- `ctfly_app.py`: five redesigned screens, forms, receipts and reports.
- `ctfly_neon.py`: current Neon Arena presentation layer; all five modules keep the existing business rules.
- `assets/neon_arena.png`, `assets/neon_cafe.png`: generated local photographic backgrounds. Prompts/tool details are in `assets/IMAGE_PROMPTS.md`.
- `ctfly_store.py`: database schema and transactional business rules.
- `google_oauth.py`: browser-based OAuth.
- `test_ctfly.py`: isolated regression tests.
- `ctfly_v3.db`: automatically generated new database with 16 stations, catalog, events, rewards and demo users. No artificial sales/active sessions.

All original module source files and **tfly_gaming.db** are retained. `main_legacy.py`, `README_legacy.md` and `requirements_legacy.txt` preserve the old launcher/documentation/dependencies. Version 3 never opens the old database and does not migrate its records. Do not delete real café data to reset a demo; back it up. Replace demo credentials before actual use. This is a local academic prototype.

## Verify

```powershell
python -m unittest -v test_ctfly
python -m compileall -q main.py ctfly_app.py ctfly_neon.py ctfly_store.py date_controls.py google_oauth.py
python smoke_ui.py
```

Tests use temporary databases. `smoke_ui.py` checks actual Tk screens and writes client-window screenshots to `test-artifacts` (uses Pillow or the included Windows capture helper). Real Google end-to-end testing requires your client and a human Google account.

## Optional Windows executable

```powershell
python -m PyInstaller --noconfirm --onedir --windowed --add-data "assets;assets" --name CTFLY main.py
```

Use a writable installation folder for SQLite. For current onedir bundles, runtime source, optional OAuth JSON and database live under `_internal`. Running the source in VS Code is the recommended workflow.
