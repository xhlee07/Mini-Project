"""
TFLY Gaming Café Management System
Feature: PC Station & Session Management
Sub-Modules:
- Live Floor Map & Seat Grid (Real-time countdown timer & auto-release)
- Session Check-In & Duration Packages
- Hardware & Rig Manager (Full Station CRUD)
- Active Session Registry & Logs (Full Session CRUD)
"""

import tkinter as tk
from tkinter import ttk, messagebox
import ttkbootstrap as tb
from ttkbootstrap.constants import *
from datetime import datetime, timedelta

from database import get_connection
from auth import AuthManager
from ui_components import COLORS, StatCard, StatusBadge, TabBar, styled_treeview, export_table_to_csv

class StationManagementFrame(tb.Frame):
    def __init__(self, parent, on_billing_request=None):
        super().__init__(parent)
        self.on_billing_request = on_billing_request
        self.countdown_job = None
        self.active_timers = {} # station_id -> remaining seconds
        
        self.zone_filter_var = tk.StringVar(value="All Zones")
        self.status_filter_var = tk.StringVar(value="All Statuses")
        
        self.setup_ui()
        self.refresh_stations()
        self.start_live_timer_loop()
        self.bind("<Destroy>", self._on_destroy)

    def _on_destroy(self, event):
        if event.widget == self:
            if hasattr(self, "countdown_job") and self.countdown_job:
                try:
                    self.after_cancel(self.countdown_job)
                    self.countdown_job = None
                except Exception:
                    pass


    def setup_ui(self):
        # 1. Top Metrics Banner
        metrics_frame = tb.Frame(self)
        metrics_frame.pack(fill=X, padx=15, pady=(15, 10))

        self.card_total = StatCard(metrics_frame, "Total Gaming Rigs", "16", "🖥️", COLORS['cyan'], "Across 4 Arena Zones")
        self.card_total.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_occupied = StatCard(metrics_frame, "Active Sessions", "0", "🎮", COLORS['purple'], "Gamers Currently Online")
        self.card_occupied.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_available = StatCard(metrics_frame, "Available Rigs", "0", "✨", COLORS['green'], "Ready for Immediate Check-In")
        self.card_available.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_revenue = StatCard(metrics_frame, "Hall Hourly Rate", "RM 0.00/h", "💰", COLORS['amber'], "Active Hourly Yield")
        self.card_revenue.pack(side=LEFT, fill=X, expand=True, padx=5)

        # Tab bar replaces Notebook
        self.tabbar = TabBar(self, style="underline")
        self.tabbar.pack(fill=BOTH, expand=True, padx=0, pady=0)

        self.tab_map      = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=10, pady=10)
        self.tab_checkin  = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_crud     = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_sessions = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)

        self.tabbar.add_tab("map",      "Live Floor Map",         self.tab_map)
        self.tabbar.add_tab("checkin",  "Check-In & Packages",    self.tab_checkin)
        self.tabbar.add_tab("crud",     "Hardware & Rig Manager", self.tab_crud)
        self.tabbar.add_tab("sessions", "Session History",        self.tab_sessions)

        self.setup_submodule_map()
        self.setup_submodule_checkin()
        self.setup_submodule_crud()
        self.setup_submodule_sessions()

        self.tabbar.build(default_key="map")
        self.notebook = self.tabbar  # backwards-compat alias

    # ========================================================
    # LIVE FLOOR MAP & SEAT GRID
    # ========================================================
    def setup_submodule_map(self):
        container = self.tab_map

        toolbar = tb.Frame(container, bootstyle="dark", padding=8)
        toolbar.pack(fill=X, pady=(0, 10))

        tb.Label(toolbar, text="Filter By Zone:", font=("Helvetica", 9, "bold")).pack(side=LEFT, padx=(5, 5))
        zone_options = ["All Zones", "Standard", "VIP Esports", "Streamer Suite", "Duo Lounge"]
        self.zone_cb = tb.Combobox(toolbar, textvariable=self.zone_filter_var, values=zone_options, state="readonly", width=13)
        self.zone_cb.pack(side=LEFT, padx=5)
        self.zone_cb.bind("<<ComboboxSelected>>", lambda e: self.render_station_grid())

        tb.Label(toolbar, text="Status:", font=("Helvetica", 9, "bold")).pack(side=LEFT, padx=(10, 5))
        status_options = ["All Statuses", "Available", "Occupied", "Reserved", "Maintenance"]
        self.status_cb = tb.Combobox(toolbar, textvariable=self.status_filter_var, values=status_options, state="readonly", width=13)
        self.status_cb.pack(side=LEFT, padx=5)
        self.status_cb.bind("<<ComboboxSelected>>", lambda e: self.render_station_grid())

        # Legend
        legend_frame = tb.Frame(toolbar)
        legend_frame.pack(side=LEFT, padx=15)
        tb.Label(legend_frame, text="● Available", bootstyle="success", font=("Helvetica", 8)).pack(side=LEFT, padx=3)
        tb.Label(legend_frame, text="● Occupied", bootstyle="info", font=("Helvetica", 8)).pack(side=LEFT, padx=3)
        tb.Label(legend_frame, text="● Reserved", bootstyle="warning", font=("Helvetica", 8)).pack(side=LEFT, padx=3)
        tb.Label(legend_frame, text="● Maintenance", bootstyle="danger", font=("Helvetica", 8)).pack(side=LEFT, padx=3)

        tb.Button(toolbar, text="🔄 Refresh", bootstyle="secondary-outline", command=self.refresh_stations).pack(side=RIGHT, padx=4)
        tb.Button(toolbar, text="📥 Export CSV", bootstyle="info-outline", command=self.export_csv).pack(side=RIGHT, padx=4)

        # Scrollable Station Cards Grid
        grid_container = tb.Frame(container)
        grid_container.pack(fill=BOTH, expand=True)

        self.canvas = tk.Canvas(grid_container, bg=COLORS['bg'], highlightthickness=0)
        self.scrollbar = tb.Scrollbar(grid_container, orient=VERTICAL, command=self.canvas.yview)
        self.grid_frame = tb.Frame(self.canvas)

        self.grid_frame.bind("<Configure>", lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all")))
        self.canvas_window = self.canvas.create_window((0, 0), window=self.grid_frame, anchor="nw")

        self.canvas.pack(side=LEFT, fill=BOTH, expand=True)
        self.scrollbar.pack(side=RIGHT, fill=Y)
        self.canvas.configure(yscrollcommand=self.scrollbar.set)
        self.canvas.bind('<Configure>', lambda e: self.canvas.itemconfig(self.canvas_window, width=e.width))

    # ========================================================
    # SESSION CHECK-IN & PACKAGES
    # ========================================================
    def setup_submodule_checkin(self):
        container = self.tab_checkin

        paned = ttk.PanedWindow(container, orient=HORIZONTAL)
        paned.pack(fill=BOTH, expand=True)

        left = tb.Frame(paned, padding=10)
        paned.add(left, weight=3)

        tb.Label(left, text="⚡ Fast Session Check-In & Package Assignment", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W)
        tb.Label(left, text="Select an available station, choose a gaming package, and assign to member or walk-in guest", 
                 font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        # Station Selector
        st_row = tb.Frame(left)
        st_row.pack(fill=X, pady=5)
        tb.Label(st_row, text="Select Station:", font=("Helvetica", 9, "bold")).pack(side=LEFT, padx=(0, 15))
        self.checkin_station_cb = tb.Combobox(st_row, state="readonly", width=35)
        self.checkin_station_cb.pack(side=LEFT)
        self.checkin_station_cb.bind("<<ComboboxSelected>>", self.on_checkin_station_changed)

        # Gamer Selection
        gamer_row = tb.Frame(left)
        gamer_row.pack(fill=X, pady=5)
        tb.Label(gamer_row, text="Gamer Profile:", font=("Helvetica", 9, "bold")).pack(side=LEFT, padx=(0, 18))
        self.checkin_member_cb = tb.Combobox(gamer_row, state="readonly", width=35)
        self.checkin_member_cb.pack(side=LEFT)

        guest_row = tb.Frame(left)
        guest_row.pack(fill=X, pady=5)
        tb.Label(guest_row, text="Guest Nickname:", font=("Helvetica", 9, "bold")).pack(side=LEFT, padx=(0, 8))
        self.checkin_guest_var = tk.StringVar(value="Player_One")
        tb.Entry(guest_row, textvariable=self.checkin_guest_var, width=25).pack(side=LEFT)

        # Duration Package Radio buttons
        pkg_box = tb.Labelframe(left, text=" Choose Duration Package ", padding=10)
        pkg_box.pack(fill=X, pady=12)

        self.checkin_duration_var = tk.IntVar(value=60)
        packages = [
            ("⚡ 1 Hour Quick Play (Standard Rate)", 60),
            ("🔥 2 Hours Pro Session (Popular)", 120),
            ("🏆 3 Hours Ranked Grind (Tournament Ready)", 180),
            ("🌙 8 Hours Night Owl Package (25% Discount)", 480),
        ]
        for label, mins in packages:
            tb.Radiobutton(pkg_box, text=label, variable=self.checkin_duration_var, value=mins,
                           command=self.update_checkin_summary).pack(anchor=W, pady=3)

        # Right Summary Panel
        right = tb.Frame(paned, bootstyle="dark", padding=15)
        paned.add(right, weight=2)

        tb.Label(right, text="📋 Check-In Summary", font=("Helvetica", 13, "bold"), bootstyle="warning").pack(anchor=W)
        tb.Label(right, text="Review details before launching session", font=("Helvetica", 8), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        self.summary_st_lbl = tb.Label(right, text="Selected Rig: None", font=("Helvetica", 10))
        self.summary_st_lbl.pack(anchor=W, pady=3)

        self.summary_zone_lbl = tb.Label(right, text="Zone & Specs: N/A", font=("Helvetica", 9), bootstyle="secondary")
        self.summary_zone_lbl.pack(anchor=W, pady=2)

        self.summary_rate_lbl = tb.Label(right, text="Hourly Rate: RM 0.00/hr", font=("Helvetica", 10))
        self.summary_rate_lbl.pack(anchor=W, pady=3)

        self.summary_dur_lbl = tb.Label(right, text="Duration: 60 mins (1.0 hr)", font=("Helvetica", 10))
        self.summary_dur_lbl.pack(anchor=W, pady=3)

        tb.Separator(right).pack(fill=X, pady=10)

        self.summary_cost_lbl = tb.Label(right, text="TOTAL COST: RM 0.00", font=("Helvetica", 14, "bold"), bootstyle="info")
        self.summary_cost_lbl.pack(anchor=W, pady=(5, 20))

        tb.Button(right, text="🚀 Activate Session & Start Timer", bootstyle="success", command=self.submit_tab_checkin).pack(fill=X, pady=5)

    def on_checkin_station_changed(self, event=None):
        self.update_checkin_summary()

    def update_checkin_summary(self):
        st_text = self.checkin_station_cb.get()
        if not st_text or st_text.startswith("["):
            return

        st_num = st_text.split(" - ")[0]
        st = next((s for s in self.stations if s['station_number'] == st_num), None)
        if not st:
            return

        mins = self.checkin_duration_var.get()
        hours = mins / 60.0
        rate = st['hourly_rate']
        cost = hours * rate
        if mins == 480:
            cost *= 0.75 # 25% discount

        self.summary_st_lbl.config(text=f"Selected Rig: {st['station_number']} ({st['zone']})")
        self.summary_zone_lbl.config(text=f"Specs: {st['specs'][:38]}...")
        self.summary_rate_lbl.config(text=f"Hourly Rate: RM {rate:.2f}/hr")
        self.summary_dur_lbl.config(text=f"Duration: {mins} mins ({hours:.1f} hrs)")
        self.summary_cost_lbl.config(text=f"TOTAL COST: RM {cost:.2f}")

    def submit_tab_checkin(self):
        st_text = self.checkin_station_cb.get()
        if not st_text or st_text.startswith("["):
            messagebox.showwarning("Select Station", "Please select an available gaming station.")
            return

        st_num = st_text.split(" - ")[0]
        st = next((s for s in self.stations if s['station_number'] == st_num), None)
        if not st:
            return

        mem_text = self.checkin_member_cb.get()
        mem_id = None
        guest_name = self.checkin_guest_var.get().strip() or "Guest Player"

        if mem_text and not mem_text.startswith("["):
            mem_code = mem_text.split(" - ")[0]
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("SELECT id, name FROM members WHERE member_code = ?", (mem_code,))
            row = cur.fetchone()
            conn.close()
            if row:
                mem_id = row['id']
                guest_name = row['name']

        mins = self.checkin_duration_var.get()
        hours = mins / 60.0
        cost = hours * st['hourly_rate']
        if mins == 480:
            cost *= 0.75

        now = datetime.now()
        start_str = now.strftime("%Y-%m-%d %H:%M:%S")
        end_str = (now + timedelta(minutes=mins)).strftime("%Y-%m-%d %H:%M:%S")

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("""
            INSERT INTO sessions (station_id, member_id, guest_name, start_time, end_time, duration_minutes, total_cost, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'Active')
        """, (st['id'], mem_id, guest_name, start_str, end_str, mins, cost))
        cur.execute("UPDATE stations SET status = 'Occupied' WHERE id = ?", (st['id'],))
        conn.commit()
        conn.close()

        messagebox.showinfo("Session Activated", f"Rig {st['station_number']} is now online for {guest_name}!\nDuration: {mins} minutes.")
        self.refresh_stations()
        self.tabbar.select("map")

    # ========================================================
    # HARDWARE & RIG MANAGER (CRUD)
    # ========================================================
    def setup_submodule_crud(self):
        container = self.tab_crud

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        tb.Label(top, text="⚙️ PC Station Hardware & Rates Registry (CRUD)", font=("Helvetica", 13, "bold"), bootstyle="info").pack(side=LEFT)
        
        if AuthManager.is_staff():
            tb.Button(top, text="➕ Add New Rig", bootstyle="success", command=self.open_add_station_modal).pack(side=RIGHT, padx=4)

        # Table
        cols = ("Station No", "Zone", "Hourly Rate (RM)", "Status", "Hardware Specifications")
        self.crud_tree = ttk.Treeview(container, columns=cols, show="headings", height=12)
        for c in cols:
            self.crud_tree.heading(c, text=c)

        self.crud_tree.column("Station No", width=100, anchor="center")
        self.crud_tree.column("Zone", width=120)
        self.crud_tree.column("Hourly Rate (RM)", width=110, anchor="e")
        self.crud_tree.column("Status", width=110, anchor="center")
        self.crud_tree.column("Hardware Specifications", width=380)
        self.crud_tree.pack(fill=BOTH, expand=True)

        act_box = tb.Frame(container)
        act_box.pack(fill=X, pady=8)
        if AuthManager.is_staff():
            tb.Button(act_box, text="✏️ Edit Selected Rig", bootstyle="info-outline", command=self.edit_selected_from_crud).pack(side=LEFT, padx=3)
            tb.Button(act_box, text="🔧 Toggle Maintenance", bootstyle="warning-outline", command=self.toggle_maintenance_from_crud).pack(side=LEFT, padx=3)
            tb.Button(act_box, text="🗑️ Delete Rig", bootstyle="danger-outline", command=self.delete_selected_from_crud).pack(side=LEFT, padx=3)

    def edit_selected_from_crud(self):
        sel = self.crud_tree.selection()
        if not sel:
            messagebox.showinfo("Select Rig", "Please select a rig from the table to edit.")
            return
        st_num = self.crud_tree.item(sel[0])['values'][0]
        st = next((s for s in self.stations if s['station_number'] == st_num), None)
        if st:
            self.open_edit_station_modal(st)

    def toggle_maintenance_from_crud(self):
        sel = self.crud_tree.selection()
        if not sel:
            return
        st_num = self.crud_tree.item(sel[0])['values'][0]
        st = next((s for s in self.stations if s['station_number'] == st_num), None)
        if st:
            new_status = "Available" if st['status'] == "Maintenance" else "Maintenance"
            self.toggle_maintenance(st, new_status)

    def delete_selected_from_crud(self):
        sel = self.crud_tree.selection()
        if not sel:
            return
        st_num = self.crud_tree.item(sel[0])['values'][0]
        st = next((s for s in self.stations if s['station_number'] == st_num), None)
        if st and messagebox.askyesno("Confirm Delete", f"Delete {st['station_number']} completely?"):
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("DELETE FROM stations WHERE id = ?", (st['id'],))
            conn.commit()
            conn.close()
            self.refresh_stations()

    # ========================================================
    # ACTIVE SESSION REGISTRY & LOGS (CRUD)
    # ========================================================
    def setup_submodule_sessions(self):
        container = self.tab_sessions

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        tb.Label(top, text="📋 Gaming Session Audit & History", font=("Helvetica", 13, "bold"), bootstyle="info").pack(side=LEFT)
        tb.Button(top, text="📥 Export Sessions CSV", bootstyle="info-outline", command=self.export_sessions_csv).pack(side=RIGHT)

        cols = ("Session ID", "Rig", "Gamer Name", "Start Time", "End Time", "Duration (mins)", "Cost (RM)", "Status")
        self.session_tree = ttk.Treeview(container, columns=cols, show="headings", height=12)
        for c in cols:
            self.session_tree.heading(c, text=c)

        self.session_tree.column("Session ID", width=80, anchor="center")
        self.session_tree.column("Rig", width=80, anchor="center")
        self.session_tree.column("Gamer Name", width=140)
        self.session_tree.column("Start Time", width=140)
        self.session_tree.column("End Time", width=140)
        self.session_tree.column("Duration (mins)", width=100, anchor="center")
        self.session_tree.column("Cost (RM)", width=90, anchor="e")
        self.session_tree.column("Status", width=90, anchor="center")
        self.session_tree.pack(fill=BOTH, expand=True)

        act_box = tb.Frame(container)
        act_box.pack(fill=X, pady=8)
        if AuthManager.is_staff():
            tb.Button(act_box, text="⏹️ Stop Selected Active Session", bootstyle="danger-outline", command=self.stop_session_from_table).pack(side=LEFT)

    def stop_session_from_table(self):
        sel = self.session_tree.selection()
        if not sel:
            messagebox.showinfo("Select Session", "Please select an active session to terminate.")
            return
        vals = self.session_tree.item(sel[0])['values']
        sess_id = vals[0]
        st_num = vals[1]
        st = next((s for s in self.stations if s['station_number'] == st_num), None)
        if st:
            self.confirm_end_session(st)

    def export_sessions_csv(self):
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("""
            SELECT s.id, st.station_number, COALESCE(m.name, s.guest_name), s.start_time, s.end_time, s.duration_minutes, s.total_cost, s.status
            FROM sessions s
            JOIN stations st ON s.station_id = st.id
            LEFT JOIN members m ON s.member_id = m.id
            ORDER BY s.id DESC
        """)
        rows = cur.fetchall()
        conn.close()
        headers = ["Session ID", "Station Number", "Customer Name", "Start Time", "End Time", "Duration (mins)", "Total Cost (RM)", "Status"]
        export_table_to_csv(headers, [list(r) for r in rows], "tfly_sessions_history.csv")

    # ========================================================
    # SHARED LOGIC & REFRESH
    # ========================================================
    def refresh_stations(self):
        """Fetch all stations and active sessions from DB."""
        conn = get_connection()
        cursor = conn.cursor()

        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        cursor.execute("""
            UPDATE stations 
            SET status = 'Available' 
            WHERE id IN (
                SELECT station_id FROM sessions 
                WHERE status = 'Active' AND end_time <= ?
            )
        """, (now_str,))
        
        cursor.execute("""
            UPDATE sessions 
            SET status = 'Completed' 
            WHERE status = 'Active' AND end_time <= ?
        """, (now_str,))
        conn.commit()

        cursor.execute("SELECT * FROM stations ORDER BY station_number")
        self.stations = [dict(row) for row in cursor.fetchall()]

        cursor.execute("""
            SELECT s.*, m.member_code, m.tier, m.name as member_name
            FROM sessions s
            LEFT JOIN members m ON s.member_id = m.id
            WHERE s.status = 'Active'
        """)
        self.active_sessions = {row['station_id']: dict(row) for row in cursor.fetchall()}

        # Fetch all sessions for Sub-Module 1.4
        cursor.execute("""
            SELECT s.id, st.station_number, COALESCE(m.name, s.guest_name) as cust_name, s.start_time, s.end_time, s.duration_minutes, s.total_cost, s.status
            FROM sessions s
            JOIN stations st ON s.station_id = st.id
            LEFT JOIN members m ON s.member_id = m.id
            ORDER BY s.id DESC LIMIT 40
        """)
        all_sessions = cursor.fetchall()

        # Members for check-in dropdown
        cursor.execute("SELECT member_code, name, tier FROM members ORDER BY name")
        all_mems = cursor.fetchall()
        conn.close()

        # Update Metrics
        total = len(self.stations)
        occupied = sum(1 for s in self.stations if s['status'] == 'Occupied')
        available = sum(1 for s in self.stations if s['status'] == 'Available')
        hourly_rev = sum(s['hourly_rate'] for s in self.stations if s['status'] == 'Occupied')

        self.card_total.set_value(str(total))
        self.card_occupied.set_value(str(occupied))
        self.card_available.set_value(str(available))
        self.card_revenue.set_value(f"RM {hourly_rev:.2f}/h")

        # Synchronize active timers
        now = datetime.now()
        for station_id, session in self.active_sessions.items():
            end_dt = datetime.strptime(session['end_time'], "%Y-%m-%d %H:%M:%S")
            rem_secs = max(0, int((end_dt - now).total_seconds()))
            self.active_timers[station_id] = rem_secs

        # Populate Sub-Module 1.2 Check-In dropdowns
        avail_st_choices = [f"{s['station_number']} - {s['zone']} (RM {s['hourly_rate']:.2f}/hr)" for s in self.stations if s['status'] == 'Available']
        self.checkin_station_cb.config(values=avail_st_choices or ["[No Available Stations]"])
        if avail_st_choices and not self.checkin_station_cb.get():
            self.checkin_station_cb.set(avail_st_choices[0])

        mem_choices = ["[Walk-In Guest]"] + [f"{m['member_code']} - {m['name']} ({m['tier']})" for m in all_mems]
        self.checkin_member_cb.config(values=mem_choices)
        if not self.checkin_member_cb.get():
            self.checkin_member_cb.set("[Walk-In Guest]")

        # Populate Sub-Module 1.3 CRUD table
        for item in self.crud_tree.get_children():
            self.crud_tree.delete(item)
        for s in self.stations:
            self.crud_tree.insert("", "end", values=(s['station_number'], s['zone'], f"{s['hourly_rate']:.2f}", s['status'], s['specs']))

        # Populate Sub-Module 1.4 Sessions table
        for item in self.session_tree.get_children():
            self.session_tree.delete(item)
        for row in all_sessions:
            self.session_tree.insert("", "end", values=(
                row['id'], row['station_number'], row['cust_name'], row['start_time'][:16], row['end_time'][:16],
                row['duration_minutes'], f"{row['total_cost']:.2f}", row['status']
            ))

        self.render_station_grid()
        self.update_checkin_summary()

    def render_station_grid(self):
        """Render station cards inside the scrollable grid frame."""
        for widget in self.grid_frame.winfo_children():
            widget.destroy()

        selected_zone = self.zone_filter_var.get()
        selected_status = self.status_filter_var.get()

        filtered = [
            s for s in self.stations
            if (selected_zone == "All Zones" or s['zone'] == selected_zone) and
               (selected_status == "All Statuses" or s['status'] == selected_status)
        ]

        if not filtered:
            empty_lbl = tb.Label(self.grid_frame, text="No gaming stations found matching the filter criteria.", font=("Helvetica", 12), bootstyle="secondary")
            empty_lbl.pack(pady=40)
            return

        columns = 4
        for idx, station in enumerate(filtered):
            row = idx // columns
            col = idx % columns
            self.create_station_card(self.grid_frame, station, row, col)

    def create_station_card(self, parent, station, row, col):
        """Create a sleek modern dark battlestation card with glowing accent."""
        st_id = station['id']
        st_num = station['station_number']
        status = station['status']
        zone = station['zone']
        rate = station['hourly_rate']
        specs = station['specs']
        session = self.active_sessions.get(st_id)

        accent = COLORS['green']
        if status == "Occupied":
            accent = COLORS['cyan']
        elif status == "Reserved":
            accent = COLORS['amber']
        elif status == "Maintenance":
            accent = COLORS['rose']

        card = tk.Frame(parent, bg=COLORS['bg_card'],
                        highlightthickness=1,
                        highlightbackground=COLORS['border'],
                        padx=12, pady=10)
        card.grid(row=row, column=col, padx=8, pady=8, sticky="nsew")
        parent.columnconfigure(col, weight=1)

        # Top 3px accent line
        tk.Frame(card, bg=accent, height=3).pack(fill=X, pady=(0, 8))

        top_row = tk.Frame(card, bg=COLORS['bg_card'])
        top_row.pack(fill=X, pady=(0, 4))

        st_lbl = tk.Label(top_row, text=st_num, font=("Helvetica", 14, "bold"),
                          fg=COLORS['text'], bg=COLORS['bg_card'])
        st_lbl.pack(side=LEFT)

        badge = StatusBadge(top_row, status)
        badge.pack(side=RIGHT)

        zone_frame = tk.Frame(card, bg=COLORS['bg_card'])
        zone_frame.pack(fill=X, pady=(0, 4))
        tk.Label(zone_frame, text=f"{zone}", font=("Helvetica", 9),
                 fg=COLORS['cyan'], bg=COLORS['bg_card']).pack(side=LEFT)
        tk.Label(zone_frame, text=f"RM {rate:.2f}/hr", font=("Helvetica", 9, "bold"),
                 fg=COLORS['amber'], bg=COLORS['bg_card']).pack(side=RIGHT)

        spec_lbl = tk.Label(card, text=specs, font=("Helvetica", 8),
                            fg=COLORS['text_muted'], bg=COLORS['bg_card'],
                            wraplength=210, justify=LEFT)
        spec_lbl.pack(fill=X, pady=(2, 6))

        info_frame = tk.Frame(card, bg=COLORS['bg_input'], padx=10, pady=8,
                              highlightthickness=1, highlightbackground=COLORS['border'])
        info_frame.pack(fill=X, pady=4)

        if status == "Occupied" and session:
            cust_name = session.get('member_name') or session.get('guest_name') or "Guest Gamer"
            tk.Label(info_frame, text=f"👤 {cust_name[:16]}", font=("Helvetica", 9, "bold"),
                     fg=COLORS['text'], bg=COLORS['bg_input']).pack(anchor=W)

            rem_secs = self.active_timers.get(st_id, 0)
            hours, remainder = divmod(rem_secs, 3600)
            mins, secs = divmod(remainder, 60)
            time_str = f"⏱️ {hours:02d}:{mins:02d}:{secs:02d}"

            timer_lbl = tk.Label(info_frame, text=time_str, font=("Courier New", 11, "bold"),
                                 fg=COLORS['amber'], bg=COLORS['bg_input'])
            timer_lbl.pack(anchor=W, pady=2)
            card.timer_label = timer_lbl
            card.station_id = st_id
        elif status == "Available":
            tk.Label(info_frame, text="⚡ Station Available", font=("Helvetica", 9, "bold"),
                     fg=COLORS['green'], bg=COLORS['bg_input']).pack(anchor=W)
            tk.Label(info_frame, text="Ready for Check-In", font=("Helvetica", 8),
                     fg=COLORS['text_muted'], bg=COLORS['bg_input']).pack(anchor=W)
        elif status == "Reserved":
            tk.Label(info_frame, text="🔒 Reserved Rig", font=("Helvetica", 9, "bold"),
                     fg=COLORS['amber'], bg=COLORS['bg_input']).pack(anchor=W)
            tk.Label(info_frame, text="Hold for Tournament/VIP", font=("Helvetica", 8),
                     fg=COLORS['text_muted'], bg=COLORS['bg_input']).pack(anchor=W)
        else:
            tk.Label(info_frame, text="⚠️ Maintenance Diagnostic", font=("Helvetica", 9, "bold"),
                     fg=COLORS['rose'], bg=COLORS['bg_input']).pack(anchor=W)
            tk.Label(info_frame, text="Under Hardware Check", font=("Helvetica", 8),
                     fg=COLORS['text_muted'], bg=COLORS['bg_input']).pack(anchor=W)

        action_frame = tk.Frame(card, bg=COLORS['bg_card'])
        action_frame.pack(fill=X, pady=(6, 0))

        if status == "Available":
            start_btn = tk.Button(action_frame, text="⚡ Start Session",
                                  font=("Helvetica", 9, "bold"),
                                  fg="#040817", bg=COLORS['cyan'],
                                  activebackground=COLORS['cyan_dim'],
                                  relief="flat", cursor="hand2", pady=6,
                                  command=lambda s=station: self.open_start_session_modal(s))
            start_btn.pack(fill=X)
        elif status == "Occupied":
            btn_row = tk.Frame(action_frame, bg=COLORS['bg_card'])
            btn_row.pack(fill=X)
            ext_btn = tk.Button(btn_row, text="+ Extend", font=("Helvetica", 8, "bold"),
                                fg=COLORS['cyan'], bg=COLORS['bg_input'],
                                activebackground=COLORS['bg_hover'],
                                relief="flat", cursor="hand2", pady=5,
                                command=lambda s=station: self.open_extend_session_modal(s))
            ext_btn.pack(side=LEFT, expand=True, fill=X, padx=(0, 2))
            
            end_btn = tk.Button(btn_row, text="⏹️ Stop", font=("Helvetica", 8, "bold"),
                                fg=COLORS['rose'], bg=COLORS['bg_input'],
                                activebackground=COLORS['bg_hover'],
                                relief="flat", cursor="hand2", pady=5,
                                command=lambda s=station: self.confirm_end_session(s))
            end_btn.pack(side=RIGHT, expand=True, fill=X, padx=(2, 0))
        elif status == "Maintenance":
            fix_btn = tk.Button(action_frame, text="🔧 Set Available", font=("Helvetica", 9, "bold"),
                                fg=COLORS['green'], bg=COLORS['bg_input'],
                                activebackground=COLORS['bg_hover'],
                                relief="flat", cursor="hand2", pady=5,
                                command=lambda s=station: self.toggle_maintenance(s, "Available"))
            fix_btn.pack(fill=X)
        elif status == "Reserved":
            rel_btn = tk.Button(action_frame, text="🔓 Release Hold", font=("Helvetica", 9, "bold"),
                                fg=COLORS['amber'], bg=COLORS['bg_input'],
                                activebackground=COLORS['bg_hover'],
                                relief="flat", cursor="hand2", pady=5,
                                command=lambda s=station: self.toggle_maintenance(s, "Available"))
            rel_btn.pack(fill=X)

    def start_live_timer_loop(self):
        """Live 1-second countdown loop using Tkinter after()."""
        if not self.winfo_exists():
            return
        expired_stations = []

        for st_id, rem_secs in list(self.active_timers.items()):
            if rem_secs > 0:
                self.active_timers[st_id] = rem_secs - 1
            else:
                expired_stations.append(st_id)

        if expired_stations:
            self.handle_expired_sessions(expired_stations)

        for widget in self.grid_frame.winfo_children():
            if hasattr(widget, 'station_id') and hasattr(widget, 'timer_label'):
                st_id = widget.station_id
                secs_left = self.active_timers.get(st_id, 0)
                hours, rem = divmod(secs_left, 3600)
                mins, secs = divmod(rem, 60)
                time_str = f"⏱️ {hours:02d}:{mins:02d}:{secs:02d}"
                color = COLORS['rose'] if secs_left < 300 else (COLORS['amber'] if secs_left < 900 else COLORS['cyan'])
                try:
                    widget.timer_label.config(text=time_str, fg=color)
                except Exception:
                    pass

        self.countdown_job = self.after(1000, self.start_live_timer_loop)


    def handle_expired_sessions(self, expired_ids):
        """Auto release expired stations in database."""
        conn = get_connection()
        cursor = conn.cursor()
        for st_id in expired_ids:
            cursor.execute("UPDATE stations SET status = 'Available' WHERE id = ?", (st_id,))
            cursor.execute("UPDATE sessions SET status = 'Completed' WHERE station_id = ? AND status = 'Active'", (st_id,))
            if st_id in self.active_timers:
                del self.active_timers[st_id]
        conn.commit()
        conn.close()
        self.refresh_stations()

    def open_start_session_modal(self, station):
        """Modal to start session directly from grid card."""
        self.tabbar.select("checkin")
        # Find station match in combobox
        for val in self.checkin_station_cb['values']:
            if val.startswith(station['station_number']):
                self.checkin_station_cb.set(val)
                self.update_checkin_summary()
                break

    def open_extend_session_modal(self, station):
        session = self.active_sessions.get(station['id'])
        if not session:
            messagebox.showerror("Error", "No active session found for this station.")
            return

        win = tb.Toplevel(self)
        win.title(f"Extend Session — Station {station['station_number']}")
        win.geometry("400x320")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        container = tb.Frame(win, padding=20)
        container.pack(fill=BOTH, expand=True)

        tb.Label(container, text=f"Extend Session: {station['station_number']}", font=("Helvetica", 14, "bold"), bootstyle="info").pack(anchor=W)
        tb.Label(container, text=f"Currently occupied by: {session.get('member_name') or session.get('guest_name')}", font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        add_mins_var = tk.IntVar(value=60)
        tb.Label(container, text="Select Extra Time to Add:", font=("Helvetica", 10, "bold")).pack(anchor=W, pady=(0, 5))

        opts = [("+30 Minutes", 30), ("+1 Hour", 60), ("+2 Hours", 120)]
        for text, m in opts:
            tb.Radiobutton(container, text=text, variable=add_mins_var, value=m).pack(anchor=W, pady=3)

        btn_box = tb.Frame(container)
        btn_box.pack(fill=X, pady=(20, 0))

        def confirm_extend():
            added = add_mins_var.get()
            cur_end = datetime.strptime(session['end_time'], "%Y-%m-%d %H:%M:%S")
            new_end = cur_end + timedelta(minutes=added)
            new_end_str = new_end.strftime("%Y-%m-%d %H:%M:%S")

            added_cost = (added / 60.0) * station['hourly_rate']
            new_cost = session['total_cost'] + added_cost
            new_dur = session['duration_minutes'] + added

            db = get_connection()
            cur = db.cursor()
            cur.execute("""
                UPDATE sessions 
                SET end_time = ?, duration_minutes = ?, total_cost = ?
                WHERE id = ?
            """, (new_end_str, new_dur, new_cost, session['id']))
            db.commit()
            db.close()

            win.destroy()
            messagebox.showinfo("Session Extended", f"Added +{added} minutes to Station {station['station_number']}!")
            self.refresh_stations()

        tb.Button(btn_box, text="Confirm Extension", bootstyle="info", command=confirm_extend).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def confirm_end_session(self, station):
        session = self.active_sessions.get(station['id'])
        if not session:
            return

        cust = session.get('member_name') or session.get('guest_name')
        if not messagebox.askyesno("End Session Early", f"End active session for {cust} on station {station['station_number']} now?"):
            return

        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("UPDATE sessions SET end_time = ?, status = 'Completed' WHERE id = ?", (now_str, session['id']))
        cur.execute("UPDATE stations SET status = 'Available' WHERE id = ?", (station['id'],))
        conn.commit()
        conn.close()

        if station['id'] in self.active_timers:
            del self.active_timers[station['id']]

        messagebox.showinfo("Session Closed", f"Station {station['station_number']} is now Available!")
        self.refresh_stations()

    def toggle_maintenance(self, station, target_status):
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("UPDATE stations SET status = ? WHERE id = ?", (target_status, station['id']))
        conn.commit()
        conn.close()
        self.refresh_stations()

    def open_add_station_modal(self):
        win = tb.Toplevel(self)
        win.title("Add New PC Rig")
        win.geometry("450x420")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text="Add New Gaming Rig", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        st_num_var = tk.StringVar(value=f"PC-{len(self.stations)+1:02d}")
        tb.Label(c, text="Station Number (e.g. PC-17, VIP-05):").pack(anchor=W)
        tb.Entry(c, textvariable=st_num_var).pack(fill=X, pady=(2, 8))

        zone_var = tk.StringVar(value="Standard")
        tb.Label(c, text="Arena Zone:").pack(anchor=W)
        tb.Combobox(c, textvariable=zone_var, values=["Standard", "VIP Esports", "Streamer Suite", "Duo Lounge"], state="readonly").pack(fill=X, pady=(2, 8))

        rate_var = tk.DoubleVar(value=4.50)
        tb.Label(c, text="Hourly Rate (RM):").pack(anchor=W)
        tb.Entry(c, textvariable=rate_var).pack(fill=X, pady=(2, 8))

        specs_var = tk.StringVar(value='i5-13400F | RTX 4060 | 16GB RAM | 165Hz IPS')
        tb.Label(c, text="Hardware Specs:").pack(anchor=W)
        tb.Entry(c, textvariable=specs_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            num = st_num_var.get().strip()
            z = zone_var.get()
            r = rate_var.get()
            sp = specs_var.get().strip()

            if not num:
                messagebox.showerror("Error", "Station number is required.")
                return

            db = get_connection()
            cur = db.cursor()
            try:
                cur.execute("INSERT INTO stations (station_number, zone, hourly_rate, specs, status) VALUES (?, ?, ?, ?, 'Available')",
                            (num, z, r, sp))
                db.commit()
                win.destroy()
                messagebox.showinfo("Success", f"Station {num} added successfully!")
                self.refresh_stations()
            except Exception as e:
                messagebox.showerror("Error", f"Failed to add station:\n{e}")
            finally:
                db.close()

        tb.Button(btn_box, text="Save Rig", bootstyle="success", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def open_edit_station_modal(self, station):
        win = tb.Toplevel(self)
        win.title(f"Manage Station {station['station_number']}")
        win.geometry("450x460")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text=f"Edit Station: {station['station_number']}", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        st_num_var = tk.StringVar(value=station['station_number'])
        tb.Label(c, text="Station Number:").pack(anchor=W)
        tb.Entry(c, textvariable=st_num_var).pack(fill=X, pady=(2, 8))

        zone_var = tk.StringVar(value=station['zone'])
        tb.Label(c, text="Arena Zone:").pack(anchor=W)
        tb.Combobox(c, textvariable=zone_var, values=["Standard", "VIP Esports", "Streamer Suite", "Duo Lounge"], state="readonly").pack(fill=X, pady=(2, 8))

        rate_var = tk.DoubleVar(value=station['hourly_rate'])
        tb.Label(c, text="Hourly Rate (RM):").pack(anchor=W)
        tb.Entry(c, textvariable=rate_var).pack(fill=X, pady=(2, 8))

        status_var = tk.StringVar(value=station['status'])
        tb.Label(c, text="Current Status:").pack(anchor=W)
        tb.Combobox(c, textvariable=status_var, values=["Available", "Occupied", "Reserved", "Maintenance"], state="readonly").pack(fill=X, pady=(2, 8))

        specs_var = tk.StringVar(value=station['specs'])
        tb.Label(c, text="Hardware Specs:").pack(anchor=W)
        tb.Entry(c, textvariable=specs_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            db = get_connection()
            cur = db.cursor()
            cur.execute("""
                UPDATE stations 
                SET station_number = ?, zone = ?, hourly_rate = ?, status = ?, specs = ?
                WHERE id = ?
            """, (st_num_var.get().strip(), zone_var.get(), rate_var.get(), status_var.get(), specs_var.get().strip(), station['id']))
            db.commit()
            db.close()
            win.destroy()
            messagebox.showinfo("Updated", "Station details updated successfully.")
            self.refresh_stations()

        def delete():
            if messagebox.askyesno("Confirm Delete", f"Delete station {station['station_number']} completely?"):
                db = get_connection()
                cur = db.cursor()
                cur.execute("DELETE FROM stations WHERE id = ?", (station['id'],))
                db.commit()
                db.close()
                win.destroy()
                messagebox.showinfo("Deleted", "Station deleted.")
                self.refresh_stations()

        tb.Button(btn_box, text="Save Changes", bootstyle="info", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Delete Station", bootstyle="danger", command=delete).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def export_csv(self):
        headers = ["Station ID", "Station Number", "Zone", "Hourly Rate (RM)", "Status", "Hardware Specs"]
        rows = [[s['id'], s['station_number'], s['zone'], s['hourly_rate'], s['status'], s['specs']] for s in self.stations]
        export_table_to_csv(headers, rows, "tfly_stations_report.csv")



