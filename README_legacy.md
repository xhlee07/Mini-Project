# ⚡ TFLY Gaming Café Management System

> **Enterprise Esports Arena & Cyber Café Desktop Management System**  
> Built with **Python + Tkinter / ttkbootstrap + SQLite + Matplotlib + Pillow**  
> Strict English Interface & Academic Evaluation Ready

---

## 📸 System Visual & Functional Architecture

This desktop application features a modern **Cyberpunk / Dark Esports Theme (Dark Mode)** with high-contrast accent colors: Neon Cyan (`#00f2fe`), Neon Purple (`#8a2be2`), Amber Gold (`#fbbf24`), and Emerald Green (`#10b981`).

* **Esports Arena Branding**: Custom team emblem and tournament arena banners (`assets/logo.jpg`, `assets/banner.jpg`, `assets/tournament_banner.jpg`).
* **Live Real-Time Seat Countdown Timer**: Driven by Tkinter's `root.after(1000)` engine for second-by-second countdown and automatic station release upon session expiry.
* **Interactive Single-Elimination Bracket Tree**: Dynamic canvas rendering with connecting match lines, live score entry, and automated winner progression to the grand finals.
* **Physics-Decelerated Lucky Wheel & 16-Band Equalizer**: Interactive gacha prize spinner with deceleration animation and a real-time RGB frequency equalizer simulation.
* **Embedded Matplotlib Dark Analytics**: Donut charts for revenue channels, hourly peak occupancy bar charts, top-selling refreshment rankings, and game popularity distribution.

---

## 👥 4-Person Assignment Module & Sub-Module Matrix

To facilitate academic evaluation and group member grading, the system is strictly decoupled into distinct functional modules. Each module contains **dedicated Sub-Modules (with independent tabs)** featuring full CRUD operations, business logic, and reporting:

| Group Member | Primary System Feature | Structured Sub-Modules | Core CRUD Entities | Demo Highlights for Lecturer |
|---|---|---|---|---|
| **Entry Point** | **Google Play Onboarding & Auth** | • Google Play Esports Onboarding<br>• Link Google / Gmail Direct Sign-In<br>• Date of Birth Dropdown Validation (Age ≥ 15)<br>• Fresh 0% Usage Account Auto-Provisioning | `User`<br>`Member` | • Google 4-color branding & direct `accounts.google.com` link<br>• 3-part DOB comboboxes with real-time age verification<br>• 50 Free Welcome Points & Bronze VIP on new signup |
| **Member 1** (Lead / Arch) | **Stations & Sessions Center** | • Live Floor Map & Seat Grid<br>• Session Check-In & Packages<br>• Rig Hardware Manager (CRUD)<br>• Session History & Audit Trail | `Station`<br>`Session` | • Color-coded live seat cards (Available, Occupied, Reserved, Maintenance)<br>• 1h/2h/3h/8h duration packages<br>• **Real-time countdown timer & auto station release**<br>• Full Rig hardware CRUD |
| **Member 2** | **Café Shop & Stock Inventory** | • Food & Beverage Menu<br>• Seat Delivery & Order Dispatch<br>• Product & Price Registry (CRUD)<br>• Stock Audit & Restock Log | `Product`<br>`Category`<br>`StockRecord`<br>`Order` | • **Seat Delivery Cart (deliver directly to PC rig)**<br>• Automatic stock deduction upon checkout<br>• **Low-stock alert red badges**<br>• Quick replenishment modal & audit log |
| **Member 3** | **Esports Tournaments & Arena** | • Events & Scheduling (CRUD)<br>• Team Registrations & Rosters<br>• Interactive Bracket & Scoreboard | `Event`<br>`Team`<br>`Registration`<br>`Match` | • Event capacity validation (auto cutoff when full)<br>• **Interactive Canvas Single-Elimination Bracket** (Quarterfinals -> Semifinals -> Finals)<br>• Live score recording & **automatic winner progression** |
| **Member 4** | **VIP Members & Cashier** | • Unified Cashier & Checkout<br>• VIP Member CRM & Tiers (CRUD)<br>• Loyalty Points & Rewards Shop<br>• Invoices & Thermal Receipts | `Member`<br>`Bill`<br>`Reward`<br>`PointsTransaction` | • Multi-tier VIP CRM (Bronze / Silver / Gold / Diamond)<br>• Points redemption shop (RM1 = 1 Point)<br>• **Consolidated billing (PC Time + F&B + Tournaments)**<br>• **Official thermal receipt popup with print simulation** |
| **Member 5** | **Staff Desk & Database Architecture** | • Staff Duty Shifts & Register Cash Float<br>• Cafe Incident & Maintenance Tickets (CRUD)<br>• **Live Database Architecture & Schema Inspector** | `StaffShift`<br>`IncidentTicket`<br>`All 19 Tables` | • Duty shift timer & cashier handover receipt<br>• Hardware incident ticketing & status tracking<br>• **Live Database Viewer explaining what each table is used for** |
| **Group Bonus Feature** | **Cyber Lounge & Gamer Quests** | • Lucky Spin Prize Wheel<br>• Daily Gamer Quests & Challenges<br>• Arena Jukebox & 16-Band Equalizer | `Quest`<br>`UserQuest`<br>`WheelSpin` | • **Interactive Physics Deceleration Lucky Wheel**<br>• Daily Gamer Quests with real-time progress bars & point claims<br>• Ambient music player with live animated RGB frequency bars |

---


## 📊 Analytics & Performance Reports (Sub-Reports)

Embedded directly into the application with dark-themed Matplotlib canvases and one-click CSV export:
* **Sub-Report A**: Revenue Stream Breakdown Donut Chart (PC Time vs F&B vs Tournaments).
* **Sub-Report B**: 24-Hour Peak Arena Traffic Distribution Bar Chart.
* **Sub-Report C**: Top 6 Best-Selling Refreshments Horizontal Bar Chart.
* **Sub-Report D**: Esports Tournament Popularity & Participation Pie Chart.
* **Sub-Report E**: VIP Member Spending Leaderboard & Point Balances.

---

## 🛠️ Technology Stack & Dependencies

1. **GUI Framework**: `Tkinter` with `ttkbootstrap` (Darkly theme).
2. **Local Database Engine**: Built-in `sqlite3` (no server configuration required, ideal for assignment delivery).
3. **Data Visualization**: `matplotlib` + `numpy` (embedded with `FigureCanvasTkAgg`).
4. **Imaging & Assets**: `Pillow (PIL)` (with in-memory image caching).
5. **Deployment & Packaging**: `pyinstaller` (single-command `.exe` compilation).

---

## 🚀 Installation & Execution Guide

### 1. Install Dependencies
Open Windows PowerShell or Command Prompt in the project folder and run:
```bash
py -m pip install -r requirements.txt
```
*(Or `python -m pip install -r requirements.txt`)*

### 2. Launch the Application
```bash
py main.py
```
*(Or `python main.py`)*

> 💡 **Auto-Seeded Database**: On initial startup, the system automatically initializes `tfly_gaming.db` and populates it with 16 pre-configured gaming stations, active live sessions, catalog items, esports tournaments with bracket matches, VIP members, and receipts.

---

## 🎯 Lecturer Presentation Tips (Demo Features)

During evaluation, use the **"⚡ Fast Role Switch"** combobox located in the top-right header:
1. **Admin View**: Demonstrates hardware rig CRUD, inventory price/stock adjustment, tournament creation, and financial reports.
2. **Staff View**: Demonstrates gamer check-in, station extension, delivery order fulfillment (`Pending` -> `Delivered`), and cashier invoice processing.
3. **Customer View**: Demonstrates browsing open rigs, ordering food/drinks to a specific seat, viewing tournament brackets, and **playing the Lucky Spin Wheel or claiming Quest Points in the Cyber Arcade**!

---

## 📦 Packaging to Standalone Executable (.exe)

To generate a standalone Windows executable for assignment submission:
```bash
pyinstaller --noconfirm --onedir --windowed --add-data "assets;assets" --name "TFLY_Gaming_Cafe" main.py
```
The compiled application will be generated in `dist/TFLY_Gaming_Cafe/TFLY_Gaming_Cafe.exe`.

---

## 📁 Source Code Organization

```text
├── assets/                       # Visual brand assets (Emblem, Banners, Wheel)
│   ├── logo.jpg
│   ├── cafe_banner.jpg
│   ├── tournament_banner.jpg
│   └── arcade_wheel.jpg
│
├── database.py                   # Relational SQLite database schema (19 tables) & migrations
├── auth.py                       # Authentication, Google Play linking & Session Context
├── ui_components.py              # Cyberpunk design system, TabBar, StatCards, Receipts
│
├── login_screen.py               # Google Play onboarding, DOB age verification (>=15) & animated canvas
├── station_manager.py            # PC Stations & Live Session Management
├── cafe_shop.py                  # Cafe Menu, Seat Delivery & Inventory
├── tournament_arena.py           # Esports Tournaments & Interactive Brackets
├── member_billing.py             # VIP Member CRM & Cashier Counter
├── arcade_lounge.py              # Cyber Arcade, Lucky Wheel & Quests
├── reports.py                    # Matplotlib Analytics Dashboard (5 Sub-Reports)
├── staff_manager.py              # Staff Duty Desk, Maintenance Tickets & Live Database Inspector
│
├── main.py                       # Main application entry point, sidebar & frame-cached routing
├── requirements.txt              # Required Python packages
└── README.md                     # Technical documentation & presentation guide
```

