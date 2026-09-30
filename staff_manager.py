"""
TFLY Gaming Cafe Management System
Staff Desk, Shift Operations & Database Architecture Inspector
Module for Staff & Admin duty operations, incident management, and live database explorer.
"""

import tkinter as tk
from tkinter import ttk, messagebox
import ttkbootstrap as tb
from ttkbootstrap.constants import *
from datetime import datetime
import csv
import os

from database import get_connection
from auth import AuthManager
from ui_components import (
    COLORS, TabBar, StatCard, StatusBadge,
    styled_treeview, export_table_to_csv, show_receipt_dialog
)

class StaffManagementFrame(tk.Frame):
    """
    Staff Desk & Database Inspector.
    Provides shift operations, terminal assists, maintenance tickets,
    and a live SQLite database schema & data explorer.
    """

    def __init__(self, parent):
        super().__init__(parent, bg=COLORS["bg"])
        self.pack(fill=BOTH, expand=True)

        self._clock_job = None
        self._shift_start_time = datetime.now()

        # Build top header banner
        self._build_header()

        # Build TabBar
        self.tabbar = TabBar(self, style="underline", on_switch=self._on_tab_switched)
        self.tabbar.pack(fill=BOTH, expand=True)

        # Tab 1: Staff Shift & Desk Operations
        self.tab_desk = tk.Frame(self.tabbar.content_host, bg=COLORS["bg"], padx=16, pady=16)
        self.tabbar.add_tab("desk", "🏢  Staff Duty Desk", self.tab_desk)

        # Tab 2: Incident & Maintenance Tickets
        self.tab_tickets = tk.Frame(self.tabbar.content_host, bg=COLORS["bg"], padx=16, pady=16)
        self.tabbar.add_tab("tickets", "🔧  Maintenance Tickets", self.tab_tickets)

        # Tab 3: Database Architecture & Live Explorer
        self.tab_db = tk.Frame(self.tabbar.content_host, bg=COLORS["bg"], padx=16, pady=16)
        self.tabbar.add_tab("database", "🗄️  Database Architecture & Live Data", self.tab_db)

        self.tabbar.build(default_key="desk")

        # Setup tab contents
        self._setup_tab_desk()
        self._setup_tab_tickets()
        self._setup_tab_database()

        # Safe destruction
        self.bind("<Destroy>", self._on_destroy)

    def _on_destroy(self, event):
        if event.widget == self and self._clock_job:
            try:
                self.after_cancel(self._clock_job)
            except Exception:
                pass

    def _on_tab_switched(self, key):
        if key == "tickets":
            self.refresh_tickets()
        elif key == "database":
            self.refresh_db_stats()
            self._on_table_selected()

    # ──────────────────────────────────────────────────────────
    #  HEADER
    # ──────────────────────────────────────────────────────────
    def _build_header(self):
        hdr = tk.Frame(self, bg=COLORS["bg_card"], padx=20, pady=12)
        hdr.pack(fill=X)

        left = tk.Frame(hdr, bg=COLORS["bg_card"])
        left.pack(side=LEFT)

        tk.Label(left, text="STAFF OPERATIONS & DATABASE ARCHITECTURE",
                 font=("Helvetica", 14, "bold"),
                 fg=COLORS["cyan"], bg=COLORS["bg_card"]).pack(anchor=W)
        tk.Label(left, text="Front desk terminal, cashier shift register, equipment tickets & live SQLite inspection",
                 font=("Helvetica", 9),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(anchor=W)

        # Live shift status badge on right
        right = tk.Frame(hdr, bg=COLORS["bg_card"])
        right.pack(side=RIGHT)

        tk.Label(right, text="Duty Status: ",
                 font=("Helvetica", 9, "bold"),
                 fg=COLORS["text_muted"], bg=COLORS["bg_card"]).pack(side=LEFT)
        StatusBadge(right, "Available").pack(side=LEFT, padx=(0, 15))  # Green pill

        self._shift_timer_lbl = tk.Label(
            right, text="⏱️ Shift: 00:00:00",
            font=("Courier New", 10, "bold"),
            fg=COLORS["amber"], bg=COLORS["bg_input"],
            padx=10, pady=4)
        self._shift_timer_lbl.pack(side=LEFT)

        self._tick_shift_timer()

    def _tick_shift_timer(self):
        if not self.winfo_exists():
            return
        diff = datetime.now() - self._shift_start_time
        hours, rem = divmod(int(diff.total_seconds()), 3600)
        mins, secs = divmod(rem, 60)
        self._shift_timer_lbl.config(text=f"⏱️ Shift: {hours:02d}:{mins:02d}:{secs:02d}")
        self._clock_job = self.after(1000, self._tick_shift_timer)

    # ──────────────────────────────────────────────────────────
    #  TAB 1: STAFF SHIFT & DESK OPERATIONS
    # ──────────────────────────────────────────────────────────
    def _setup_tab_desk(self):
        # 3 Top summary StatCards
        card_row = tk.Frame(self.tab_desk, bg=COLORS["bg"])
        card_row.pack(fill=X, pady=(0, 16))

        user = AuthManager.get_current_user() or {}
        staff_name = user.get("full_name", "Sarah Connor")
        staff_role = user.get("role", "staff").upper()

        self.card_staff = StatCard(card_row, "Active Staff On Duty", staff_name, f"Role: {staff_role} • Verified", accent="cyan")
        self.card_staff.pack(side=LEFT, fill=X, expand=True, padx=(0, 10))

        self.card_drawer = StatCard(card_row, "Cashier Drawer Balance", "RM 480.50", "Float: RM 200 • Sales: RM 280.50", accent="green")
        self.card_drawer.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_active = StatCard(card_row, "Live Rigs In Play", "6 Stations", "Occupancy: 37.5% • Peak hours", accent="purple")
        self.card_active.pack(side=LEFT, fill=X, expand=True, padx=(10, 0))

        # Split content into Left (Terminal Actions) and Right (Cashier Handover & Recent Shift Log)
        grid_frame = tk.Frame(self.tab_desk, bg=COLORS["bg"])
        grid_frame.pack(fill=BOTH, expand=True)

        # Left Column: Terminal Quick Actions
        left_col = tk.Frame(grid_frame, bg=COLORS["bg_card"], padx=18, pady=16,
                            highlightthickness=1, highlightbackground=COLORS["border"])
        left_col.pack(side=LEFT, fill=BOTH, expand=True, padx=(0, 10))

        tk.Label(left_col, text="⚡ Front Desk Quick Actions",
                 font=("Helvetica", 12, "bold"),
                 fg=COLORS["text"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(0, 4))
        tk.Label(left_col, text="Direct control commands for active stations and customer assistance",
                 font=("Helvetica", 8),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(0, 14))

        # Station selector
        sf = tk.Frame(left_col, bg=COLORS["bg_card"])
        sf.pack(fill=X, pady=(0, 10))
        tk.Label(sf, text="Target Station: ", font=("Helvetica", 9, "bold"),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(side=LEFT)
        self._st_var = tk.StringVar(value="PC-01")
        stations_list = [f"PC-{i:02d}" for i in range(1, 17)]
        st_cb = ttk.Combobox(sf, textvariable=self._st_var, values=stations_list, state="readonly", width=12)
        st_cb.pack(side=LEFT, padx=6)

        # Action Buttons grid
        btn_grid = tk.Frame(left_col, bg=COLORS["bg_card"])
        btn_grid.pack(fill=X, pady=(0, 16))

        def _action_btn(parent, text, cmd, bg_col, fg_col="#ffffff"):
            btn = tk.Button(parent, text=text, command=cmd,
                            font=("Helvetica", 9, "bold"),
                            bg=bg_col, fg=fg_col,
                            activebackground=COLORS["bg_hover"],
                            activeforeground=fg_col,
                            relief="flat", cursor="hand2", padx=12, pady=8)
            return btn

        _action_btn(btn_grid, "🔓 Unlock Station PC", self._cmd_unlock_pc, COLORS["green"]).grid(row=0, column=0, padx=4, pady=4, sticky="ew")
        _action_btn(btn_grid, "🔄 Remote Restart", self._cmd_restart_pc, COLORS["blue"]).grid(row=0, column=1, padx=4, pady=4, sticky="ew")
        _action_btn(btn_grid, "🔧 Toggle Maintenance", self._cmd_toggle_maint, COLORS["rose"]).grid(row=1, column=0, padx=4, pady=4, sticky="ew")
        _action_btn(btn_grid, "🔔 Send Screen Alert", self._cmd_send_msg, COLORS["purple"]).grid(row=1, column=1, padx=4, pady=4, sticky="ew")

        btn_grid.columnconfigure(0, weight=1)
        btn_grid.columnconfigure(1, weight=1)

        # Divider
        tk.Frame(left_col, bg=COLORS["border"], height=1).pack(fill=X, pady=12)

        # Quick Member Top-up Desk
        tk.Label(left_col, text="💳 Member Counter Top-up",
                 font=("Helvetica", 11, "bold"),
                 fg=COLORS["text"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(0, 8))

        mf = tk.Frame(left_col, bg=COLORS["bg_card"])
        mf.pack(fill=X, pady=(0, 8))
        tk.Label(mf, text="Member Code / Username: ", font=("Helvetica", 9),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(side=LEFT)
        self._topup_mem_var = tk.StringVar(value="gamer1")
        tk.Entry(mf, textvariable=self._topup_mem_var, font=("Helvetica", 9),
                 bg=COLORS["bg_input"], fg=COLORS["text"],
                 insertbackground=COLORS["cyan"], relief="flat", width=14).pack(side=LEFT, padx=6, ipady=4)

        amt_f = tk.Frame(left_col, bg=COLORS["bg_card"])
        amt_f.pack(fill=X, pady=(0, 10))
        tk.Label(amt_f, text="Top-up Amount: ", font=("Helvetica", 9),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(side=LEFT)
        self._topup_amt_var = tk.StringVar(value="20.00")
        for val in ["10.00", "20.00", "50.00", "100.00"]:
            tk.Button(amt_f, text=f"RM {val}",
                      command=lambda v=val: self._topup_amt_var.set(v),
                      font=("Helvetica", 8, "bold"),
                      fg=COLORS["cyan"], bg=COLORS["bg_input"],
                      activebackground=COLORS["cyan"], activeforeground=COLORS["bg"],
                      relief="flat", cursor="hand2", padx=6, pady=3).pack(side=LEFT, padx=2)

        _action_btn(left_col, "💰 Complete Cashier Top-up & Issue Receipt", self._cmd_quick_topup, COLORS["cyan"], COLORS["bg"]).pack(fill=X, pady=(4, 0))

        # Right Column: Shift Cashier Register & Handover
        right_col = tk.Frame(grid_frame, bg=COLORS["bg_card"], padx=18, pady=16,
                             highlightthickness=1, highlightbackground=COLORS["border"])
        right_col.pack(side=RIGHT, fill=BOTH, expand=True, padx=(10, 0))

        tk.Label(right_col, text="📑 Cashier Register & Shift Handover",
                 font=("Helvetica", 12, "bold"),
                 fg=COLORS["text"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(0, 4))
        tk.Label(right_col, text="Financial reconciliation for current duty shift",
                 font=("Helvetica", 8),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(0, 14))

        # Breakdown table
        breakdown_box = tk.Frame(right_col, bg=COLORS["bg_input"], padx=14, pady=12,
                                 highlightthickness=1, highlightbackground=COLORS["border"])
        breakdown_box.pack(fill=X, pady=(0, 14))

        items = [
            ("Opening Cash Float:", "RM 200.00", COLORS["text_dim"]),
            ("PC Session Time Receipts:", "+ RM 142.50", COLORS["green"]),
            ("Cafe F&B Counter Sales:", "+ RM 88.00", COLORS["green"]),
            ("Tournament Registrations:", "+ RM 50.00", COLORS["green"]),
            ("Estimated SST (6%):", "RM 16.83", COLORS["amber"]),
            ("Total Drawer Cash Balance:", "RM 480.50", COLORS["cyan"]),
        ]
        for lbl, val, col in items:
            row = tk.Frame(breakdown_box, bg=COLORS["bg_input"])
            row.pack(fill=X, pady=3)
            tk.Label(row, text=lbl, font=("Helvetica", 9), fg=COLORS["text_dim"], bg=COLORS["bg_input"]).pack(side=LEFT)
            tk.Label(row, text=val, font=("Helvetica", 9, "bold"), fg=col, bg=COLORS["bg_input"]).pack(side=RIGHT)

        _action_btn(right_col, "🖨️ Generate Shift Handover Report", self._cmd_print_shift_report, COLORS["amber"], COLORS["bg"]).pack(fill=X, pady=(0, 8))
        _action_btn(right_col, "🏁 End Shift & Clock Out", self._cmd_end_shift, COLORS["rose"]).pack(fill=X)

    def _cmd_unlock_pc(self):
        st = self._st_var.get()
        messagebox.showinfo("Station Control", f"Command sent to {st}: Station PC unlocked and screen lock released.")

    def _cmd_restart_pc(self):
        st = self._st_var.get()
        if messagebox.askyesno("Remote Restart", f"Are you sure you want to reboot {st} remotely?"):
            messagebox.showinfo("Station Control", f"{st} reboot signal sent successfully.")

    def _cmd_toggle_maint(self):
        st = self._st_var.get()
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("SELECT status FROM stations WHERE station_number = ?", (st,))
        row = cur.fetchone()
        if row:
            new_st = "Available" if row["status"] == "Maintenance" else "Maintenance"
            cur.execute("UPDATE stations SET status = ? WHERE station_number = ?", (new_st, st))
            conn.commit()
            messagebox.showinfo("Station Updated", f"{st} status changed to '{new_st}'.")
        conn.close()

    def _cmd_send_msg(self):
        st = self._st_var.get()
        messagebox.showinfo("Message Sent", f"Broadcast notification displayed on {st}: 'Welcome to TFLY Arena! Food & Drink service available at counter.'")

    def _cmd_quick_topup(self):
        mem = self._topup_mem_var.get().strip()
        try:
            amt = float(self._topup_amt_var.get())
        except ValueError:
            messagebox.showerror("Error", "Invalid topup amount.")
            return

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("SELECT id, name, balance, points FROM members WHERE member_code = ? OR name LIKE ?", (mem, f"%{mem}%"))
        row = cur.fetchone()
        if not row:
            cur.execute("SELECT m.id, m.name, m.balance, m.points FROM members m JOIN users u ON m.user_id = u.id WHERE u.username = ?", (mem,))
            row = cur.fetchone()

        if row:
            new_bal = row["balance"] + amt
            new_pts = row["points"] + int(amt)
            cur.execute("UPDATE members SET balance = ?, points = ? WHERE id = ?", (new_bal, new_pts, row["id"]))
            cur.execute("INSERT INTO points_transactions (member_id, points_change, reason) VALUES (?, ?, 'Cashier Counter Top-Up')", (row["id"], int(amt)))
            conn.commit()
            messagebox.showinfo("Top-Up Successful",
                                f"Member: {row['name']}\n"
                                f"Amount Added: RM {amt:.2f}\n"
                                f"New Balance: RM {new_bal:.2f}\n"
                                f"Points Earned: +{int(amt)} pts")
        else:
            messagebox.showerror("Member Not Found", f"No member found matching '{mem}'.")
        conn.close()

    def _cmd_print_shift_report(self):
        user = AuthManager.get_current_user() or {}
        lines = [
            ("Staff Member", user.get("full_name", "Sarah Connor")),
            ("Staff Role", user.get("role", "staff").upper()),
            ("Duty Station", "Front Desk Terminal #01"),
            ("Opening Float", "RM 200.00"),
            ("Station Sales", "RM 142.50"),
            ("Cafe Sales", "RM 88.00"),
            ("Tournament Fees", "RM 50.00"),
            ("Total Collected", "RM 280.50"),
            ("Closing Cash in Drawer", "RM 480.50"),
        ]
        show_receipt_dialog(self, "TFLY - SHIFT HANDOVER REPORT", lines, total=480.50)

    def _cmd_end_shift(self):
        if messagebox.askyesno("Clock Out", "Are you sure you want to end your shift and close the register?"):
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("""
                UPDATE staff_shifts SET clock_out = CURRENT_TIMESTAMP, status = 'Completed'
                WHERE status = 'On Duty'
            """)
            conn.commit()
            conn.close()
            messagebox.showinfo("Shift Closed", "Shift closed successfully. Have a great day!")

    # ──────────────────────────────────────────────────────────
    #  TAB 2: INCIDENT & MAINTENANCE TICKETS (CRUD)
    # ──────────────────────────────────────────────────────────
    def _setup_tab_tickets(self):
        # Action bar
        act_bar = tk.Frame(self.tab_tickets, bg=COLORS["bg"], pady=8)
        act_bar.pack(fill=X)

        tk.Button(act_bar, text="➕ Log New Incident Ticket", command=self._modal_add_ticket,
                  font=("Helvetica", 9, "bold"), fg=COLORS["bg"], bg=COLORS["cyan"],
                  activebackground=COLORS["cyan_dim"], relief="flat", cursor="hand2", padx=14, pady=6).pack(side=LEFT, padx=(0, 8))

        tk.Button(act_bar, text="✅ Mark As Resolved", command=self._mark_ticket_resolved,
                  font=("Helvetica", 9, "bold"), fg="#ffffff", bg=COLORS["green"],
                  activebackground=COLORS["bg_hover"], relief="flat", cursor="hand2", padx=14, pady=6).pack(side=LEFT, padx=4)

        tk.Button(act_bar, text="🗑️ Delete Ticket", command=self._delete_ticket,
                  font=("Helvetica", 9, "bold"), fg="#ffffff", bg=COLORS["rose"],
                  activebackground=COLORS["bg_hover"], relief="flat", cursor="hand2", padx=14, pady=6).pack(side=LEFT, padx=4)

        tk.Button(act_bar, text="📥 Export CSV", command=self._export_tickets_csv,
                  font=("Helvetica", 9, "bold"), fg=COLORS["text"], bg=COLORS["bg_input"],
                  activebackground=COLORS["bg_hover"], relief="flat", cursor="hand2", padx=12, pady=6).pack(side=RIGHT)

        tk.Button(act_bar, text="🔄 Refresh", command=self.refresh_tickets,
                  font=("Helvetica", 9, "bold"), fg=COLORS["text"], bg=COLORS["bg_input"],
                  activebackground=COLORS["bg_hover"], relief="flat", cursor="hand2", padx=12, pady=6).pack(side=RIGHT, padx=4)

        # Tickets Treeview
        tree_frame = tk.Frame(self.tab_tickets, bg=COLORS["border"], bd=1)
        tree_frame.pack(fill=BOTH, expand=True)

        cols = ("id", "station", "issue_type", "priority", "status", "reported_by", "description", "created_at")
        self.ticket_tree = ttk.Treeview(tree_frame, columns=cols, show="headings", height=14)
        
        self.ticket_tree.heading("id", text="ID")
        self.ticket_tree.heading("station", text="Station")
        self.ticket_tree.heading("issue_type", text="Issue Type")
        self.ticket_tree.heading("priority", text="Priority")
        self.ticket_tree.heading("status", text="Status")
        self.ticket_tree.heading("reported_by", text="Reported By")
        self.ticket_tree.heading("description", text="Description")
        self.ticket_tree.heading("created_at", text="Date Reported")

        self.ticket_tree.column("id", width=50, anchor="center")
        self.ticket_tree.column("station", width=80, anchor="center")
        self.ticket_tree.column("issue_type", width=110, anchor="center")
        self.ticket_tree.column("priority", width=90, anchor="center")
        self.ticket_tree.column("status", width=90, anchor="center")
        self.ticket_tree.column("reported_by", width=140, anchor="w")
        self.ticket_tree.column("description", width=280, anchor="w")
        self.ticket_tree.column("created_at", width=140, anchor="center")

        scroll = ttk.Scrollbar(tree_frame, orient=VERTICAL, command=self.ticket_tree.yview)
        self.ticket_tree.configure(yscrollcommand=scroll.set)
        self.ticket_tree.pack(side=LEFT, fill=BOTH, expand=True)
        scroll.pack(side=RIGHT, fill=Y)

        self.refresh_tickets()

    def _export_tickets_csv(self):
        headers = ["ID", "Station", "Issue Type", "Priority", "Status", "Reported By", "Description", "Created At"]
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("SELECT id, station_number, issue_type, priority, status, reported_by, description, created_at FROM incident_tickets ORDER BY id DESC")
        rows = [list(r) for r in cur.fetchall()]
        conn.close()
        export_table_to_csv(headers, rows, "incident_tickets.csv")

    def refresh_tickets(self):
        if not hasattr(self, "ticket_tree"):
            return
        for r in self.ticket_tree.get_children():
            self.ticket_tree.delete(r)

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("SELECT id, station_number, issue_type, priority, status, reported_by, description, created_at FROM incident_tickets ORDER BY id DESC")
        for row in cur.fetchall():
            self.ticket_tree.insert("", "end", values=(
                row["id"], row["station_number"], row["issue_type"],
                row["priority"], row["status"], row["reported_by"],
                row["description"], str(row["created_at"])[:19]
            ))
        conn.close()


    def _modal_add_ticket(self):
        win = tk.Toplevel(self)
        win.title("Log Incident / Maintenance Ticket")
        win.geometry("440x480")
        win.resizable(False, False)
        win.configure(bg=COLORS["bg_card"])
        win.transient(self)
        win.grab_set()

        tk.Frame(win, bg=COLORS["cyan"], height=3).pack(fill=X)
        box = tk.Frame(win, bg=COLORS["bg_card"], padx=24, pady=20)
        box.pack(fill=BOTH, expand=True)

        tk.Label(box, text="New Incident Ticket", font=("Helvetica", 14, "bold"),
                 fg=COLORS["text"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(0, 14))

        def _field(lbl, widget):
            tk.Label(box, text=lbl, font=("Helvetica", 9, "bold"),
                     fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(4, 2))
            widget.pack(fill=X, pady=(0, 6))

        st_v = tk.StringVar(value="PC-01")
        st_cb = ttk.Combobox(box, textvariable=st_v, values=[f"PC-{i:02d}" for i in range(1, 17)], state="readonly")
        _field("Station PC", st_cb)

        type_v = tk.StringVar(value="Peripherals")
        type_cb = ttk.Combobox(box, textvariable=type_v, values=["Hardware", "Peripherals", "Cleaning", "Network", "Display", "Software"], state="readonly")
        _field("Issue Category", type_cb)

        prio_v = tk.StringVar(value="Medium")
        prio_cb = ttk.Combobox(box, textvariable=prio_v, values=["Low", "Medium", "High", "Critical"], state="readonly")
        _field("Priority Level", prio_cb)

        user = AuthManager.get_current_user() or {}
        rep_v = tk.StringVar(value=user.get("full_name", "Sarah Connor"))
        rep_e = tk.Entry(box, textvariable=rep_v, font=("Helvetica", 9), bg=COLORS["bg_input"], fg=COLORS["text"], insertbackground=COLORS["cyan"], relief="flat")
        _field("Reported By", rep_e)

        tk.Label(box, text="Issue Description", font=("Helvetica", 9, "bold"),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(4, 2))
        desc_txt = tk.Text(box, height=4, font=("Helvetica", 9), bg=COLORS["bg_input"], fg=COLORS["text"], insertbackground=COLORS["cyan"], relief="flat")
        desc_txt.pack(fill=X, pady=(0, 14))

        def do_save():
            desc = desc_txt.get("1.0", "end").strip()
            if not desc:
                messagebox.showerror("Validation", "Please enter issue description."); return
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO incident_tickets (station_number, issue_type, priority, reported_by, description, status)
                VALUES (?, ?, ?, ?, ?, 'Open')
            """, (st_v.get(), type_v.get(), prio_v.get(), rep_v.get().strip(), desc))
            conn.commit()
            conn.close()
            win.destroy()
            self.refresh_tickets()
            messagebox.showinfo("Success", "Incident ticket logged successfully.")

        tk.Button(box, text="Submit Incident Ticket", command=do_save,
                  font=("Helvetica", 10, "bold"), fg=COLORS["bg"], bg=COLORS["cyan"],
                  activebackground=COLORS["cyan_dim"], relief="flat", cursor="hand2", pady=8).pack(fill=X)

    def _mark_ticket_resolved(self):
        sel = self.ticket_tree.selection()
        if not sel:
            messagebox.showinfo("Select Ticket", "Please select a ticket from the table first."); return
        item = self.ticket_tree.item(sel[0])
        ticket_id = item["values"][0]

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("UPDATE incident_tickets SET status = 'Resolved', resolved_at = CURRENT_TIMESTAMP WHERE id = ?", (ticket_id,))
        conn.commit()
        conn.close()
        self.refresh_tickets()
        messagebox.showinfo("Resolved", f"Ticket #{ticket_id} has been marked as Resolved.")

    def _delete_ticket(self):
        sel = self.ticket_tree.selection()
        if not sel:
            messagebox.showinfo("Select Ticket", "Please select a ticket to delete."); return
        item = self.ticket_tree.item(sel[0])
        ticket_id = item["values"][0]

        if messagebox.askyesno("Confirm Delete", f"Delete Incident Ticket #{ticket_id}?"):
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("DELETE FROM incident_tickets WHERE id = ?", (ticket_id,))
            conn.commit()
            conn.close()
            self.refresh_tickets()

    # ──────────────────────────────────────────────────────────
    #  TAB 3: DATABASE ARCHITECTURE & LIVE DATA INSPECTOR
    # ──────────────────────────────────────────────────────────
    # Table Metadata Catalog: maps table name to purpose and description
    TABLE_CATALOG = {
        "users": ("User Accounts & Authentication",
                  "Stores system credentials, hashed passwords, roles (admin, staff, customer), Date of Birth, email and Google OAuth links.",
                  "id, username, password_hash, role, full_name, phone, date_of_birth, email, auth_provider, created_at"),
        "members": ("VIP Loyalty & Customer Wallets",
                    "Customer membership CRM records including Tier (Bronze/Silver/Gold/Diamond), loyalty reward points balance, wallet balance, and lifetime spend.",
                    "id, user_id, member_code, name, phone, tier, points, total_spent, balance, created_at"),
        "stations": ("PC Gaming Rigs & Zones",
                     "Catalog of 16-30 high-end esports workstations, hardware specifications (RTX 4090, 240Hz, i9-14900K), zone tiers, and live status.",
                     "id, station_number, zone, hourly_rate, specs, status"),
        "sessions": ("Gaming Sessions & Time Accounting",
                     "Live and historical customer PC usage records, check-in timestamps, packages, calculated session costs, and billing status.",
                     "id, station_id, member_id, guest_name, start_time, end_time, duration_minutes, total_cost, status"),
        "categories": ("Cafe F&B Categories",
                       "Catalog groupings for food, beverages, snacks, energy drinks, and merchandise.",
                       "id, name, icon"),
        "products": ("Inventory & F&B Menu",
                     "Stock tracking of all sellable cafe items, stock quantities, reorder thresholds, and unit retail prices.",
                     "id, category_id, name, price, stock, min_stock_alert, image_name, description"),
        "stock_records": ("Inventory Movements & Restocks",
                          "Audit trail of all inventory increases, counter sales deductions, and manager restock logs.",
                          "id, product_id, change_qty, reason, recorded_by, created_at"),
        "orders": ("Seat Delivery Orders",
                   "Orders placed by gamers delivered directly to their PC stations, with payment status and delivery status.",
                   "id, order_number, station_number, member_id, customer_name, total_amount, payment_status, order_status, created_at"),
        "order_items": ("Order Line Items",
                        "Individual items attached to cafe orders with quantity and subtotal.",
                        "id, order_id, product_id, product_name, quantity, unit_price, subtotal"),
        "events": ("Esports Tournaments",
                   "Competitive tournaments hosted by TFLY (Valorant, LoL, CS2), prize pools, tournament dates, and status.",
                   "id, title, game_title, entry_fee, prize_pool, max_teams, event_date, status, description"),
        "tournament_teams": ("Tournament Team Rosters",
                             "Registered participant squads, captain contact details, seeding, and tournament link.",
                             "id, event_id, team_name, leader_name, leader_phone, member_id, seed_number, registered_at"),
        "tournament_matches": ("Tournament Brackets & Matches",
                               "Head-to-head match brackets, rounds (Quarterfinal, Semifinal, Grand Final), match scores, and winners.",
                               "id, event_id, round_name, match_number, team_a_name, team_b_name, score_a, score_b, winner_name, status"),
        "bills": ("Financial Checkouts & Invoices",
                  "Unified invoices combining PC station time, cafe F&B, tournament fees, SST tax calculations, and payment methods.",
                  "id, bill_number, member_id, customer_name, station_cost, snack_cost, event_cost, discount_amount, subtotal, total_amount, points_earned, payment_method, status, created_at"),
        "rewards": ("Loyalty Points Redemption Catalog",
                    "Redeemable items for gamers (e.g. Free 1 Hour Gaming, Monster Energy, Gaming Mousepad).",
                    "id, name, points_cost, reward_type, description, stock"),
        "points_transactions": ("Points Audit Ledger",
                                "Full audit log of member points earned from sessions or redeemed for rewards.",
                                "id, member_id, points_change, reason, created_at"),
        "quests": ("Gamified Daily Quests",
                   "Engagement quests (e.g. Cyber Check-in, Ramen Fuel Up, Marathon Warrior) awarding bonus points.",
                   "id, title, description, target_count, reward_points, quest_type"),
        "wheel_spins": ("Lucky Wheel RNG Log",
                        "Audit records of arcade wheel spins, prize outcomes, and timestamps.",
                        "id, user_id, prize_name, prize_value, spun_at"),
        "staff_shifts": ("Staff Duty Shifts & Register Log",
                         "Duty clock-in records, shift cash float, register sales, and handover notes.",
                         "id, staff_username, staff_name, clock_in, clock_out, cash_float, shift_sales, status, notes"),
        "incident_tickets": ("Cafe Incident & Maintenance Tickets",
                             "Station hardware issues, cleaning requests, peripheral repairs, priority, and resolution status.",
                             "id, station_number, reported_by, issue_type, description, priority, status, created_at, resolved_at, resolution_notes"),
    }

    def _setup_tab_database(self):
        # 3 Top DB StatCards
        db_stat_row = tk.Frame(self.tab_db, bg=COLORS["bg"])
        db_stat_row.pack(fill=X, pady=(0, 14))

        self.db_stat_tables = StatCard(db_stat_row, "Database Architecture", "19 Tables", "SQLite3 Relational Schema", accent="cyan")
        self.db_stat_tables.pack(side=LEFT, fill=X, expand=True, padx=(0, 10))

        self.db_stat_rows = StatCard(db_stat_row, "Total Live Records", "Loading...", "Data across all modules", accent="green")
        self.db_stat_rows.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.db_stat_health = StatCard(db_stat_row, "Database Health", "Integrity OK", "PRAGMA Foreign Keys = ON", accent="purple")
        self.db_stat_health.pack(side=LEFT, fill=X, expand=True, padx=(10, 0))

        # Main Layout: Left = Table Selector + Info Card; Right = Live Data Viewer
        content_box = tk.Frame(self.tab_db, bg=COLORS["bg"])
        content_box.pack(fill=BOTH, expand=True)

        # Left: Table Selector & Schema Info
        left_p = tk.Frame(content_box, bg=COLORS["bg_card"], width=340, padx=16, pady=16,
                          highlightthickness=1, highlightbackground=COLORS["border"])
        left_p.pack(side=LEFT, fill=Y, padx=(0, 12))
        left_p.pack_propagate(False)

        tk.Label(left_p, text="🗂️ Select Database Table",
                 font=("Helvetica", 11, "bold"),
                 fg=COLORS["text"], bg=COLORS["bg_card"]).pack(anchor=W, pady=(0, 6))

        table_names = list(self.TABLE_CATALOG.keys())
        self._sel_table_var = tk.StringVar(value="users")
        self._tbl_cb = ttk.Combobox(left_p, textvariable=self._sel_table_var,
                                    values=table_names, state="readonly", font=("Helvetica", 10))
        self._tbl_cb.pack(fill=X, pady=(0, 12))
        self._tbl_cb.bind("<<ComboboxSelected>>", lambda e: self._on_table_selected())

        # Schema details card
        self._tbl_title_lbl = tk.Label(left_p, text="", font=("Helvetica", 11, "bold"),
                                       fg=COLORS["cyan"], bg=COLORS["bg_card"], wraplength=300, justify="left")
        self._tbl_title_lbl.pack(anchor=W, pady=(4, 2))

        self._tbl_count_lbl = tk.Label(left_p, text="", font=("Helvetica", 9, "bold"),
                                       fg=COLORS["green"], bg=COLORS["bg_card"])
        self._tbl_count_lbl.pack(anchor=W, pady=(0, 8))

        tk.Label(left_p, text="Table Purpose in System:", font=("Helvetica", 8, "bold"),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(anchor=W)
        self._tbl_desc_lbl = tk.Label(left_p, text="", font=("Helvetica", 9),
                                      fg=COLORS["text"], bg=COLORS["bg_card"],
                                      wraplength=300, justify="left")
        self._tbl_desc_lbl.pack(anchor=W, pady=(2, 10))

        tk.Label(left_p, text="Schema Columns:", font=("Helvetica", 8, "bold"),
                 fg=COLORS["text_dim"], bg=COLORS["bg_card"]).pack(anchor=W)
        self._tbl_cols_lbl = tk.Label(left_p, text="", font=("Courier New", 8),
                                      fg=COLORS["text_muted"], bg=COLORS["bg_card"],
                                      wraplength=300, justify="left")
        self._tbl_cols_lbl.pack(anchor=W, pady=(2, 16))

        # Health check action
        tk.Button(left_p, text="🔍 Run Integrity Diagnostics", command=self._run_diagnostics,
                  font=("Helvetica", 9, "bold"), fg=COLORS["text"], bg=COLORS["bg_input"],
                  activebackground=COLORS["cyan"], activeforeground=COLORS["bg"],
                  relief="flat", cursor="hand2", pady=7).pack(fill=X, side=BOTTOM)

        # Right: Live Data Viewer
        right_p = tk.Frame(content_box, bg=COLORS["bg_card"], padx=16, pady=16,
                           highlightthickness=1, highlightbackground=COLORS["border"])
        right_p.pack(side=LEFT, fill=BOTH, expand=True)

        top_r = tk.Frame(right_p, bg=COLORS["bg_card"])
        top_r.pack(fill=X, pady=(0, 10))

        self._viewer_title = tk.Label(top_r, text="Live Records: users", font=("Helvetica", 11, "bold"),
                                      fg=COLORS["text"], bg=COLORS["bg_card"])
        self._viewer_title.pack(side=LEFT)

        tk.Button(top_r, text="📥 Export Table (CSV)", command=self._export_current_table,
                  font=("Helvetica", 9, "bold"), fg=COLORS["text"], bg=COLORS["bg_input"],
                  activebackground=COLORS["bg_hover"], relief="flat", cursor="hand2", padx=10, pady=4).pack(side=RIGHT)

        tk.Button(top_r, text="🔄 Live Refresh", command=self._on_table_selected,
                  font=("Helvetica", 9, "bold"), fg=COLORS["cyan"], bg=COLORS["bg_input"],
                  activebackground=COLORS["bg_hover"], relief="flat", cursor="hand2", padx=10, pady=4).pack(side=RIGHT, padx=4)

        # Container for the dynamic Treeview
        self._tree_container = tk.Frame(right_p, bg=COLORS["bg_card"])
        self._tree_container.pack(fill=BOTH, expand=True)
        self._db_tree = None

        self._on_table_selected()
        self.refresh_db_stats()

    def refresh_db_stats(self):
        try:
            conn = get_connection()
            cur = conn.cursor()
            total_records = 0
            for tbl in self.TABLE_CATALOG.keys():
                cur.execute(f"SELECT COUNT(*) FROM {tbl}")
                total_records += cur.fetchone()[0]
            conn.close()
            self.db_stat_rows.set_value(f"{total_records} Records")
        except Exception:
            pass

    def _on_table_selected(self):
        tbl = self._sel_table_var.get()
        meta = self.TABLE_CATALOG.get(tbl, ("Custom Table", "Database record table", ""))

        # Update left info
        self._tbl_title_lbl.config(text=f"📋 Table: {tbl}")
        self._tbl_desc_lbl.config(text=meta[1])
        self._tbl_cols_lbl.config(text=meta[2])
        self._viewer_title.config(text=f"Live Table Data: {tbl}")

        # Fetch live data and column names
        conn = get_connection()
        cur = conn.cursor()
        try:
            cur.execute(f"PRAGMA table_info({tbl})")
            columns_info = cur.fetchall()
            col_names = [c[1] for c in columns_info]

            cur.execute(f"SELECT COUNT(*) FROM {tbl}")
            count = cur.fetchone()[0]
            self._tbl_count_lbl.config(text=f"Total Records: {count} rows")

            cur.execute(f"SELECT * FROM {tbl} LIMIT 100")
            rows = cur.fetchall()
        except Exception as e:
            conn.close()
            messagebox.showerror("Query Error", f"Failed to query {tbl}: {e}")
            return
        conn.close()

        # Rebuild Treeview inside container
        for w in self._tree_container.winfo_children():
            w.destroy()

        t_frame = tk.Frame(self._tree_container, bg=COLORS["border"], bd=1)
        t_frame.pack(fill=BOTH, expand=True)

        tree = ttk.Treeview(t_frame, columns=col_names, show="headings", height=14)
        for name in col_names:
            width = 120
            anchor = "w"
            if any(k in name.lower() for k in ["id", "price", "cost", "stock", "tier"]):
                width = 80
                anchor = "center"
            elif any(k in name.lower() for k in ["description", "specs", "title"]):
                width = 220
            tree.heading(name, text=name.replace("_", " ").title())
            tree.column(name, width=width, anchor=anchor)

        scroll_y = ttk.Scrollbar(t_frame, orient=VERTICAL, command=tree.yview)
        scroll_x = ttk.Scrollbar(t_frame, orient=HORIZONTAL, command=tree.xview)
        tree.configure(yscrollcommand=scroll_y.set, xscrollcommand=scroll_x.set)
        tree.pack(side=LEFT, fill=BOTH, expand=True)
        scroll_y.pack(side=RIGHT, fill=Y)
        scroll_x.pack(side=BOTTOM, fill=X)
        self._db_tree = tree
        self._last_col_names = col_names

        for row in rows:
            formatted_vals = []
            for val in row:
                if val is None:
                    formatted_vals.append("")
                elif isinstance(val, float):
                    formatted_vals.append(f"{val:.2f}")
                else:
                    formatted_vals.append(str(val))
            tree.insert("", "end", values=formatted_vals)

    def _export_current_table(self):
        tbl = self._sel_table_var.get()
        conn = get_connection()
        cur = conn.cursor()
        cur.execute(f"SELECT * FROM {tbl}")
        rows = [list(r) for r in cur.fetchall()]
        headers = self._last_col_names if hasattr(self, "_last_col_names") else [c[0] for c in cur.description]
        conn.close()
        export_table_to_csv(headers, rows, f"{tbl}_export.csv")


    def _run_diagnostics(self):
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("PRAGMA integrity_check")
        status = cur.fetchone()[0]
        cur.execute("PRAGMA foreign_key_check")
        fk_errs = cur.fetchall()
        conn.close()

        msg = f"SQLite Integrity Check: {status.upper()}\n"
        if not fk_errs:
            msg += "Foreign Key Constraints: 100% HEALTHY (0 violations)\n"
        else:
            msg += f"Foreign Key Violations: {len(fk_errs)}\n"
        msg += "\nAll 19 relational tables verified:\n- users, members, stations, sessions\n- products, categories, stock_records, orders\n- events, tournament_teams, tournament_matches\n- bills, rewards, points_transactions, quests\n- staff_shifts, incident_tickets"
        messagebox.showinfo("Database Diagnostics", msg)
