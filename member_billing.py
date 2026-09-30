"""
TFLY Gaming Café Management System
Feature: VIP Members & Unified Billing Cashier
Sub-Modules:
- Unified Cashier & Checkout (Consolidated PC Time + F&B + Tournaments)
- VIP Member CRM & Tiers (Full Member CRUD & Balance Top-Up)
- Loyalty Points & Rewards Shop (Points catalog & redemption)
- Invoices & Thermal Receipts (Audit archive & CSV export)
"""

import tkinter as tk
from tkinter import ttk, messagebox
import ttkbootstrap as tb
from ttkbootstrap.constants import *
from datetime import datetime

from database import get_connection
from auth import AuthManager
from ui_components import COLORS, StatCard, StatusBadge, TabBar, styled_treeview, export_table_to_csv, show_receipt_dialog

TIERS = {
    'Bronze': {'min_spent': 0, 'discount': 0.00, 'color': '#94a3b8'},
    'Silver': {'min_spent': 150, 'discount': 0.05, 'color': '#cbd5e1'},
    'Gold': {'min_spent': 400, 'discount': 0.10, 'color': '#fbbf24'},
    'Diamond': {'min_spent': 800, 'discount': 0.15, 'color': '#00f2fe'}
}

class MemberBillingManagementFrame(tb.Frame):
    def __init__(self, parent):
        super().__init__(parent)
        self.members = []
        self.rewards = []
        self.bills = []
        self.selected_member = None

        self.setup_ui()
        self.refresh_data()

    def setup_ui(self):
        # 1. Top Metrics Banner
        metrics_frame = tb.Frame(self)
        metrics_frame.pack(fill=X, padx=15, pady=(15, 10))

        self.card_members = StatCard(metrics_frame, "Total VIP Members", "0", "💎", COLORS['amber'], "Registered Gamers")
        self.card_members.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_points = StatCard(metrics_frame, "Circulating Points", "0", "⚡", COLORS['cyan'], "Available for Rewards")
        self.card_points.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_total_rev = StatCard(metrics_frame, "Total Hall Revenue", "RM 0.00", "💵", COLORS['green'], "Unified Invoiced Sales")
        self.card_total_rev.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_redemptions = StatCard(metrics_frame, "Points Claimed", "0", "🎁", COLORS['purple'], "Total Loyalty Gifts Claimed")
        self.card_redemptions.pack(side=LEFT, fill=X, expand=True, padx=5)

        # 2. Sleek TabBar
        self.tabbar = TabBar(self, style="underline")
        self.tabbar.pack(fill=BOTH, expand=True)

        self.tab_billing  = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_members  = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_rewards  = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_invoices = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)

        self.tabbar.add_tab("billing",  "💳  Unified Cashier & Checkout", self.tab_billing)
        self.tabbar.add_tab("members",  "💎  VIP Member CRM & Tiers",    self.tab_members)
        self.tabbar.add_tab("rewards",  "🎁  Rewards & Points Shop",     self.tab_rewards)
        self.tabbar.add_tab("invoices", "📑  Invoices & Past Receipts",  self.tab_invoices)

        self.setup_tab_billing()
        self.setup_tab_members()
        self.setup_tab_rewards()
        self.setup_tab_invoices()

        self.tabbar.build(default_key="billing")
        self.notebook = self.tabbar


    # ========================================================
    # UNIFIED CASHIER & CHECKOUT
    # ========================================================
    def setup_tab_billing(self):
        container = self.tab_billing
        paned = ttk.PanedWindow(container, orient=HORIZONTAL)
        paned.pack(fill=BOTH, expand=True)

        left_box = tb.Frame(paned, padding=10)
        paned.add(left_box, weight=3)

        tb.Label(left_box, text="⚡ Consolidated Gaming Bill Generator", font=("Helvetica", 14, "bold"), bootstyle="info").pack(anchor=W)
        tb.Label(left_box, text="Seamlessly combine PC Station hours + Snack Orders + Esports Entry into 1 receipt", 
                 font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        # Member Selector
        mem_row = tb.Frame(left_box)
        mem_row.pack(fill=X, pady=5)
        tb.Label(mem_row, text="Select Member:").pack(side=LEFT, padx=(0, 15))
        self.billing_mem_var = tk.StringVar(value="[Walk-in Guest / Non-Member]")
        self.billing_mem_cb = tb.Combobox(mem_row, textvariable=self.billing_mem_var, state="readonly", width=38)
        self.billing_mem_cb.pack(side=LEFT)
        self.billing_mem_cb.bind("<<ComboboxSelected>>", self.on_billing_member_changed)

        self.tier_perk_lbl = tb.Label(left_box, text="Tier Perk: 0% Discount  |  Points Rate: 1x", font=("Helvetica", 9, "bold"), bootstyle="secondary")
        self.tier_perk_lbl.pack(anchor=W, pady=(2, 10))

        # 1. PC Station Time Charge
        pc_box = tb.Labelframe(left_box, text=" 🖥️ 1. PC Station Gaming Time ", padding=10)
        pc_box.pack(fill=X, pady=5)
        
        self.bill_station_var = tk.DoubleVar(value=0.0)
        self.station_select_cb = tb.Combobox(pc_box, state="readonly", width=30)
        self.station_select_cb.pack(side=LEFT, padx=(0, 10))
        self.station_select_cb.bind("<<ComboboxSelected>>", self.on_station_bill_select)

        tb.Label(pc_box, text="Cost (RM):").pack(side=LEFT, padx=(5, 5))
        tb.Entry(pc_box, textvariable=self.bill_station_var, width=10).pack(side=LEFT)

        # 2. Food & Snack Orders Charge
        food_box = tb.Labelframe(left_box, text=" 🍜 2. F&B & Snack Orders ", padding=10)
        food_box.pack(fill=X, pady=5)
        
        self.bill_snack_var = tk.DoubleVar(value=0.0)
        self.snack_order_cb = tb.Combobox(food_box, state="readonly", width=30)
        self.snack_order_cb.pack(side=LEFT, padx=(0, 10))
        self.snack_order_cb.bind("<<ComboboxSelected>>", self.on_snack_bill_select)

        tb.Label(food_box, text="Cost (RM):").pack(side=LEFT, padx=(5, 5))
        tb.Entry(food_box, textvariable=self.bill_snack_var, width=10).pack(side=LEFT)

        # 3. Esports Tournament Entry Fee
        tourney_box = tb.Labelframe(left_box, text=" 🏆 3. Tournament Entry Fee ", padding=10)
        tourney_box.pack(fill=X, pady=5)

        self.bill_event_var = tk.DoubleVar(value=0.0)
        self.event_select_cb = tb.Combobox(tourney_box, state="readonly", width=30)
        self.event_select_cb.pack(side=LEFT, padx=(0, 10))
        self.event_select_cb.bind("<<ComboboxSelected>>", self.on_event_bill_select)

        tb.Label(tourney_box, text="Fee (RM):").pack(side=LEFT, padx=(5, 5))
        tb.Entry(tourney_box, textvariable=self.bill_event_var, width=10).pack(side=LEFT)

        # Payment Mode
        pay_box = tb.Frame(left_box)
        pay_box.pack(fill=X, pady=10)
        tb.Label(pay_box, text="Payment Method:").pack(side=LEFT, padx=(0, 10))
        self.pay_mode_var = tk.StringVar(value="TNG eWallet")
        pay_modes = ["TNG eWallet", "Cash", "Credit Card", "Member Balance"]
        tb.Combobox(pay_box, textvariable=self.pay_mode_var, values=pay_modes, state="readonly", width=16).pack(side=LEFT)

        tb.Button(pay_box, text="🔄 Recalculate Bill", bootstyle="info-outline", command=self.calculate_bill_preview).pack(side=RIGHT)

        # Right Summary Panel
        right_box = tb.Frame(paned, bootstyle="dark", padding=15)
        paned.add(right_box, weight=2)

        tb.Label(right_box, text="🧾 Checkout Summary", font=("Helvetica", 14, "bold"), bootstyle="warning").pack(anchor=W)
        tb.Label(right_box, text="Itemized charges & discount calculation", font=("Helvetica", 8), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        self.bill_subtotal_lbl = tb.Label(right_box, text="Subtotal: RM 0.00", font=("Helvetica", 11))
        self.bill_subtotal_lbl.pack(anchor=W, pady=2)

        self.bill_discount_lbl = tb.Label(right_box, text="Loyalty Discount: -RM 0.00 (0%)", font=("Helvetica", 11), bootstyle="success")
        self.bill_discount_lbl.pack(anchor=W, pady=2)

        self.bill_points_lbl = tb.Label(right_box, text="Points To Credit: +0 Pts", font=("Helvetica", 11), bootstyle="warning")
        self.bill_points_lbl.pack(anchor=W, pady=2)

        tb.Separator(right_box).pack(fill=X, pady=10)

        self.bill_final_lbl = tb.Label(right_box, text="NET TOTAL: RM 0.00", font=("Helvetica", 16, "bold"), bootstyle="info")
        self.bill_final_lbl.pack(anchor=W, pady=(5, 20))

        tb.Button(right_box, text="💳 Process Payment & Issue Receipt", bootstyle="success", command=self.process_bill_checkout).pack(fill=X, pady=5)
        tb.Button(right_box, text="Reset Form", bootstyle="secondary-outline", command=self.reset_billing_form).pack(fill=X, pady=3)

    def on_billing_member_changed(self, event=None):
        mem_str = self.billing_mem_var.get()
        if mem_str.startswith("["):
            self.tier_perk_lbl.config(text="Tier Perk: 0% Discount  |  Points Rate: 1x", bootstyle="secondary")
        else:
            code = mem_str.split(" - ")[0]
            for m in self.members:
                if m['member_code'] == code:
                    tier = m['tier']
                    disc = TIERS[tier]['discount'] * 100
                    self.tier_perk_lbl.config(text=f"Tier: {tier} ({disc:.0f}% Off)  |  Balance: RM {m['balance']:.2f}  |  Points: {m['points']}", 
                                              bootstyle="warning")
                    break
        self.calculate_bill_preview()

    def on_station_bill_select(self, event=None):
        val = self.station_select_cb.get()
        if "RM" in val:
            cost = float(val.split("RM ")[-1].replace(")", ""))
            self.bill_station_var.set(cost)
        self.calculate_bill_preview()

    def on_snack_bill_select(self, event=None):
        val = self.snack_order_cb.get()
        if "RM" in val:
            cost = float(val.split("RM ")[-1].replace(")", ""))
            self.bill_snack_var.set(cost)
        self.calculate_bill_preview()

    def on_event_bill_select(self, event=None):
        val = self.event_select_cb.get()
        if "RM" in val:
            cost = float(val.split("RM ")[-1].replace(")", ""))
            self.bill_event_var.set(cost)
        self.calculate_bill_preview()

    def calculate_bill_preview(self):
        st_cost = self.bill_station_var.get()
        sn_cost = self.bill_snack_var.get()
        ev_cost = self.bill_event_var.get()
        subtotal = st_cost + sn_cost + ev_cost

        disc_rate = 0.0
        mem_str = self.billing_mem_var.get()
        selected_mem = None
        if not mem_str.startswith("["):
            code = mem_str.split(" - ")[0]
            for m in self.members:
                if m['member_code'] == code:
                    selected_mem = m
                    disc_rate = TIERS[m['tier']]['discount']
                    break

        discount_amt = subtotal * disc_rate
        net_total = max(0.0, subtotal - discount_amt)
        pts_earned = int(net_total)

        self.bill_subtotal_lbl.config(text=f"Subtotal: RM {subtotal:.2f}")
        self.bill_discount_lbl.config(text=f"Loyalty Discount: -RM {discount_amt:.2f} ({disc_rate*100:.0f}%)")
        self.bill_points_lbl.config(text=f"Points To Credit: +{pts_earned} Pts")
        self.bill_final_lbl.config(text=f"NET TOTAL: RM {net_total:.2f}")

        return {
            'subtotal': subtotal,
            'discount': discount_amt,
            'net_total': net_total,
            'points': pts_earned,
            'member': selected_mem
        }

    def process_bill_checkout(self):
        calc = self.calculate_bill_preview()
        if calc['net_total'] <= 0 and calc['subtotal'] <= 0:
            messagebox.showwarning("Empty Bill", "Please add at least one charge (PC Station, F&B, or Tournament).")
            return

        now = datetime.now()
        bill_num = f"INV-{now.strftime('%Y%m%d')}-{now.strftime('%H%M%S')[-4:]}"
        pay_method = self.pay_mode_var.get()

        mem = calc['member']
        cust_name = mem['name'] if mem else "Walk-in Guest"
        mem_id = mem['id'] if mem else None

        if pay_method == "Member Balance":
            if not mem:
                messagebox.showerror("Error", "Walk-in guests cannot pay via Member Balance.")
                return
            if mem['balance'] < calc['net_total']:
                messagebox.showerror("Insufficient Balance", f"Member balance (RM {mem['balance']:.2f}) is lower than bill total (RM {calc['net_total']:.2f})!")
                return

        conn = get_connection()
        cur = conn.cursor()

        try:
            cur.execute("""
                INSERT INTO bills (bill_number, member_id, customer_name, station_cost, snack_cost, event_cost, discount_amount, subtotal, total_amount, points_earned, payment_method, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Paid')
            """, (bill_num, mem_id, cust_name, self.bill_station_var.get(), self.bill_snack_var.get(), 
                  self.bill_event_var.get(), calc['discount'], calc['subtotal'], calc['net_total'], calc['points'], pay_method))

            if mem:
                new_spent = mem['total_spent'] + calc['net_total']
                new_points = mem['points'] + calc['points']
                new_bal = mem['balance'] - (calc['net_total'] if pay_method == "Member Balance" else 0.0)

                new_tier = "Bronze"
                if new_spent >= TIERS['Diamond']['min_spent']:
                    new_tier = "Diamond"
                elif new_spent >= TIERS['Gold']['min_spent']:
                    new_tier = "Gold"
                elif new_spent >= TIERS['Silver']['min_spent']:
                    new_tier = "Silver"

                cur.execute("""
                    UPDATE members 
                    SET total_spent = ?, points = ?, balance = ?, tier = ?
                    WHERE id = ?
                """, (new_spent, new_points, new_bal, new_tier, mem['id']))

                cur.execute("""
                    INSERT INTO points_transactions (member_id, points_change, reason)
                    VALUES (?, ?, ?)
                """, (mem['id'], calc['points'], f"Earned from Bill {bill_num}"))

            conn.commit()

            bill_data = {
                'bill_number': bill_num,
                'customer_name': cust_name,
                'created_at': now.strftime("%Y-%m-%d %H:%M:%S"),
                'payment_method': pay_method,
                'station_cost': self.bill_station_var.get(),
                'snack_cost': self.bill_snack_var.get(),
                'event_cost': self.bill_event_var.get(),
                'discount_amount': calc['discount'],
                'subtotal': calc['subtotal'],
                'total_amount': calc['net_total'],
                'points_earned': calc['points']
            }

            self.reset_billing_form()
            self.refresh_data()
            show_receipt_dialog(self, bill_data)

        except Exception as e:
            conn.rollback()
            messagebox.showerror("Checkout Error", f"Failed to record bill:\n{e}")
        finally:
            conn.close()

    def reset_billing_form(self):
        self.bill_station_var.set(0.0)
        self.bill_snack_var.set(0.0)
        self.bill_event_var.set(0.0)
        self.calculate_bill_preview()

    # ========================================================
    # VIP MEMBER CRM & TIERS (CRUD)
    # ========================================================
    def setup_tab_members(self):
        container = self.tab_members

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        tb.Label(top, text="👤 VIP Member CRM & Tier Manager (CRUD)", font=("Helvetica", 13, "bold"), bootstyle="info").pack(side=LEFT)

        if AuthManager.is_staff():
            tb.Button(top, text="💳 Top-Up Balance", bootstyle="warning-outline", command=self.open_topup_modal).pack(side=RIGHT, padx=4)
            tb.Button(top, text="➕ Register Member", bootstyle="success", command=self.open_add_member_modal).pack(side=RIGHT, padx=4)
            tb.Button(top, text="📥 Export Members CSV", bootstyle="info-outline", command=self.export_members_csv).pack(side=RIGHT, padx=4)

        cols = ("Member Code", "Name", "Phone", "Tier", "Points", "Total Spent (RM)", "Balance (RM)")
        self.members_tree = ttk.Treeview(container, columns=cols, show="headings", height=12)
        for c in cols:
            self.members_tree.heading(c, text=c)

        self.members_tree.column("Member Code", width=100)
        self.members_tree.column("Name", width=150)
        self.members_tree.column("Phone", width=120)
        self.members_tree.column("Tier", width=90, anchor="center")
        self.members_tree.column("Points", width=80, anchor="center")
        self.members_tree.column("Total Spent (RM)", width=110, anchor="e")
        self.members_tree.column("Balance (RM)", width=100, anchor="e")
        self.members_tree.pack(fill=BOTH, expand=True)

        act_box = tb.Frame(container)
        act_box.pack(fill=X, pady=8)
        tb.Button(act_box, text="📜 View Points Audit Trail", bootstyle="secondary", command=self.open_member_points_modal).pack(side=LEFT, padx=3)
        if AuthManager.is_staff():
            tb.Button(act_box, text="✏️ Edit Member Details", bootstyle="info-outline", command=self.open_edit_member_modal).pack(side=LEFT, padx=3)
            tb.Button(act_box, text="🗑️ Delete Member", bootstyle="danger-outline", command=self.delete_member).pack(side=LEFT, padx=3)

    # ========================================================
    # LOYALTY POINTS & REWARDS SHOP (CRUD)
    # ========================================================
    def setup_tab_rewards(self):
        container = self.tab_rewards

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        tb.Label(top, text="🎁 Gamer Loyalty Rewards Catalog & Redemption Shop", font=("Helvetica", 13, "bold"), bootstyle="warning").pack(side=LEFT)
        tb.Label(top, text="Gamers earn RM1 = 1 Point to redeem free PC hours, refreshments & gaming gear", font=("Helvetica", 9), bootstyle="secondary").pack(side=LEFT, padx=15)

        if AuthManager.is_staff():
            tb.Button(top, text="➕ Add Reward Item", bootstyle="success", command=self.open_add_reward_modal).pack(side=RIGHT)

        cols = ("ID", "Reward Item", "Type", "Points Required", "Stock Left", "Description")
        self.rewards_tree = ttk.Treeview(container, columns=cols, show="headings", height=10)
        for c in cols:
            self.rewards_tree.heading(c, text=c)

        self.rewards_tree.column("ID", width=40, anchor="center")
        self.rewards_tree.column("Reward Item", width=220)
        self.rewards_tree.column("Type", width=90, anchor="center")
        self.rewards_tree.column("Points Required", width=120, anchor="center")
        self.rewards_tree.column("Stock Left", width=80, anchor="center")
        self.rewards_tree.column("Description", width=250)
        self.rewards_tree.pack(fill=BOTH, expand=True)

        redeem_box = tb.Frame(container, bootstyle="dark", padding=10)
        redeem_box.pack(fill=X, pady=10)

        tb.Label(redeem_box, text="Redeem Reward for Member:").pack(side=LEFT, padx=(0, 10))
        self.redeem_mem_cb = tb.Combobox(redeem_box, state="readonly", width=30)
        self.redeem_mem_cb.pack(side=LEFT, padx=(0, 15))

        tb.Button(redeem_box, text="🎉 Redeem Selected Reward", bootstyle="warning", command=self.process_reward_redemption).pack(side=LEFT)

    # ========================================================
    # INVOICES & THERMAL RECEIPTS
    # ========================================================
    def setup_tab_invoices(self):
        container = self.tab_invoices

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        tb.Label(top, text="🧾 Unified Invoices & Receipts Archive", font=("Helvetica", 13, "bold"), bootstyle="info").pack(side=LEFT)
        tb.Button(top, text="📥 Export Invoices CSV", bootstyle="info-outline", command=self.export_invoices_csv).pack(side=RIGHT)

        cols = ("Invoice No", "Customer", "Date", "PC Time", "Snacks", "Tourney", "Discount", "Total Paid", "Pay Method")
        self.invoices_tree = ttk.Treeview(container, columns=cols, show="headings", height=12)
        for c in cols:
            self.invoices_tree.heading(c, text=c)

        self.invoices_tree.column("Invoice No", width=120)
        self.invoices_tree.column("Customer", width=120)
        self.invoices_tree.column("Date", width=130)
        self.invoices_tree.column("PC Time", width=70, anchor="e")
        self.invoices_tree.column("Snacks", width=70, anchor="e")
        self.invoices_tree.column("Tourney", width=70, anchor="e")
        self.invoices_tree.column("Discount", width=70, anchor="e")
        self.invoices_tree.column("Total Paid", width=85, anchor="e")
        self.invoices_tree.column("Pay Method", width=95, anchor="center")
        self.invoices_tree.pack(fill=BOTH, expand=True)

        btn_box = tb.Frame(container)
        btn_box.pack(fill=X, pady=8)
        tb.Button(btn_box, text="🖨️ Re-Print / View Thermal Receipt", bootstyle="info", command=self.view_selected_invoice_receipt).pack(side=LEFT)

    # ========================================================
    # SHARED LOGIC & REFRESH
    # ========================================================
    def refresh_data(self):
        conn = get_connection()
        cur = conn.cursor()

        cur.execute("SELECT * FROM members ORDER BY total_spent DESC")
        self.members = [dict(m) for m in cur.fetchall()]

        cur.execute("SELECT * FROM rewards ORDER BY points_cost")
        self.rewards = [dict(r) for r in cur.fetchall()]

        cur.execute("SELECT * FROM bills ORDER BY id DESC")
        self.bills = [dict(b) for b in cur.fetchall()]

        cur.execute("""
            SELECT s.id, st.station_number, s.total_cost, s.guest_name, m.name as member_name
            FROM sessions s
            JOIN stations st ON s.station_id = st.id
            LEFT JOIN members m ON s.member_id = m.id
            WHERE s.status = 'Active'
        """)
        active_sess = cur.fetchall()

        cur.execute("SELECT id, order_number, station_number, total_amount FROM orders WHERE payment_status = 'Paid'")
        orders = cur.fetchall()

        cur.execute("SELECT id, title, entry_fee FROM events WHERE status != 'Cancelled'")
        tourneys = cur.fetchall()

        cur.execute("SELECT COALESCE(SUM(total_amount), 0) FROM bills")
        total_rev = cur.fetchone()[0]

        cur.execute("SELECT COALESCE(SUM(points), 0) FROM members")
        total_pts = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM points_transactions WHERE points_change < 0")
        total_redemptions = cur.fetchone()[0]

        conn.close()

        self.card_members.set_value(str(len(self.members)))
        self.card_points.set_value(f"{total_pts:,}")
        self.card_total_rev.set_value(f"RM {total_rev:,.2f}")
        self.card_redemptions.set_value(str(total_redemptions))

        mem_choices = ["[Walk-in Guest / Non-Member]"] + [f"{m['member_code']} - {m['name']} ({m['tier']})" for m in self.members]
        self.billing_mem_cb.config(values=mem_choices)
        self.redeem_mem_cb.config(values=[f"{m['member_code']} - {m['name']} ({m['points']} Pts)" for m in self.members])
        if self.members and not self.redeem_mem_cb.get():
            self.redeem_mem_cb.set(f"{self.members[0]['member_code']} - {self.members[0]['name']} ({self.members[0]['points']} Pts)")

        sess_choices = ["[No PC Time Charge]"] + [f"{s['station_number']} ({s['member_name'] or s['guest_name']}) - RM {s['total_cost']:.2f}" for s in active_sess]
        self.station_select_cb.config(values=sess_choices)
        self.station_select_cb.set(sess_choices[0])

        order_choices = ["[No F&B Charge]"] + [f"{o['order_number']} (Seat {o['station_number']}) - RM {o['total_amount']:.2f}" for o in orders]
        self.snack_order_cb.config(values=order_choices)
        self.snack_order_cb.set(order_choices[0])

        tourney_choices = ["[No Tournament Charge]"] + [f"{t['title'][:25]} - RM {t['entry_fee']:.2f}" for t in tourneys]
        self.event_select_cb.config(values=tourney_choices)
        self.event_select_cb.set(tourney_choices[0])

        for item in self.members_tree.get_children():
            self.members_tree.delete(item)
        for m in self.members:
            self.members_tree.insert("", "end", iid=str(m['id']), values=(
                m['member_code'], m['name'], m['phone'], m['tier'], f"{m['points']:,}", f"{m['total_spent']:.2f}", f"{m['balance']:.2f}"
            ))

        for item in self.rewards_tree.get_children():
            self.rewards_tree.delete(item)
        for r in self.rewards:
            self.rewards_tree.insert("", "end", iid=str(r['id']), values=(
                r['id'], r['name'], r['reward_type'], f"{r['points_cost']} Pts", r['stock'], r['description'] or ""
            ))

        for item in self.invoices_tree.get_children():
            self.invoices_tree.delete(item)
        for b in self.bills:
            self.invoices_tree.insert("", "end", iid=str(b['id']), values=(
                b['bill_number'], b['customer_name'], b['created_at'][:16],
                f"{b['station_cost']:.2f}", f"{b['snack_cost']:.2f}", f"{b['event_cost']:.2f}",
                f"{b['discount_amount']:.2f}", f"{b['total_amount']:.2f}", b['payment_method']
            ))

    def process_reward_redemption(self):
        sel_reward = self.rewards_tree.selection()
        if not sel_reward:
            messagebox.showwarning("Select Reward", "Please select a reward item from the list.")
            return

        reward_id = int(sel_reward[0])
        reward = next((r for r in self.rewards if r['id'] == reward_id), None)
        if not reward:
            return

        if reward['stock'] <= 0:
            messagebox.showerror("Out of Stock", "This reward item is currently out of stock.")
            return

        mem_val = self.redeem_mem_cb.get()
        if not mem_val:
            messagebox.showwarning("Select Member", "Please select a member to redeem for.")
            return

        mem_code = mem_val.split(" - ")[0]
        member = next((m for m in self.members if m['member_code'] == mem_code), None)
        if not member:
            return

        if member['points'] < reward['points_cost']:
            messagebox.showerror("Insufficient Points", f"{member['name']} has {member['points']} points, but needs {reward['points_cost']} points.")
            return

        if not messagebox.askyesno("Confirm Redemption", f"Redeem '{reward['name']}' for {member['name']} for {reward['points_cost']} Points?"):
            return

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("UPDATE members SET points = points - ? WHERE id = ?", (reward['points_cost'], member['id']))
        cur.execute("UPDATE rewards SET stock = stock - 1 WHERE id = ?", (reward['id'],))
        cur.execute("""
            INSERT INTO points_transactions (member_id, points_change, reason)
            VALUES (?, ?, ?)
        """, (member['id'], -reward['points_cost'], f"Redeemed Reward: {reward['name']}"))
        conn.commit()
        conn.close()

        messagebox.showinfo("Redemption Success", f"🎉 Congratulations! {member['name']} successfully redeemed:\n'{reward['name']}'!")
        self.refresh_data()

    def view_selected_invoice_receipt(self):
        sel = self.invoices_tree.selection()
        if not sel:
            messagebox.showinfo("Select Invoice", "Please select an invoice from the list.")
            return

        bill_id = int(sel[0])
        bill = next((b for b in self.bills if b['id'] == bill_id), None)
        if bill:
            show_receipt_dialog(self, bill)

    def open_add_member_modal(self):
        win = tb.Toplevel(self)
        win.title("Register VIP Member")
        win.geometry("450x420")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text="Register New Member", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        code_var = tk.StringVar(value=f"TFLY-{8800 + len(self.members)+1}")
        tb.Label(c, text="Member Code:").pack(anchor=W)
        tb.Entry(c, textvariable=code_var).pack(fill=X, pady=(2, 8))

        name_var = tk.StringVar()
        tb.Label(c, text="Full Name:").pack(anchor=W)
        tb.Entry(c, textvariable=name_var).pack(fill=X, pady=(2, 8))

        phone_var = tk.StringVar()
        tb.Label(c, text="Phone Number:").pack(anchor=W)
        tb.Entry(c, textvariable=phone_var).pack(fill=X, pady=(2, 8))

        tier_var = tk.StringVar(value="Bronze")
        tb.Label(c, text="Initial Tier:").pack(anchor=W)
        tb.Combobox(c, textvariable=tier_var, values=["Bronze", "Silver", "Gold", "Diamond"], state="readonly").pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            name = name_var.get().strip()
            ph = phone_var.get().strip()
            code = code_var.get().strip()

            if not name or not ph:
                messagebox.showerror("Error", "Name and phone are required.")
                return

            conn = get_connection()
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO members (member_code, name, phone, tier, points, total_spent, balance)
                VALUES (?, ?, ?, ?, 50, 0.0, 0.0)
            """, (code, name, ph, tier_var.get()))
            mem_id = cur.lastrowid
            cur.execute("INSERT INTO points_transactions (member_id, points_change, reason) VALUES (?, 50, 'Welcome Bonus Points')", (mem_id,))
            conn.commit()
            conn.close()

            win.destroy()
            messagebox.showinfo("Success", f"Member {name} registered with 50 bonus points!")
            self.refresh_data()

        tb.Button(btn_box, text="Save Member", bootstyle="success", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def open_topup_modal(self):
        sel = self.members_tree.selection()
        if not sel:
            messagebox.showinfo("Select Member", "Please select a member to top up.")
            return

        mem_id = int(sel[0])
        member = next((m for m in self.members if m['id'] == mem_id), None)
        if not member:
            return

        win = tb.Toplevel(self)
        win.title("Top-Up Member Balance")
        win.geometry("400x320")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text=f"Top-Up: {member['name']}", font=("Helvetica", 13, "bold"), bootstyle="warning").pack(anchor=W)
        tb.Label(c, text=f"Current Balance: RM {member['balance']:.2f}", font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        topup_var = tk.DoubleVar(value=50.00)
        tb.Label(c, text="Top-Up Amount (RM):").pack(anchor=W)
        tb.Entry(c, textvariable=topup_var).pack(fill=X, pady=(2, 10))

        quick_box = tb.Frame(c)
        quick_box.pack(fill=X, pady=5)
        for amt in [20, 50, 100, 200]:
            tb.Button(quick_box, text=f"+RM {amt}", bootstyle="secondary-outline", width=6,
                      command=lambda a=amt: topup_var.set(float(a))).pack(side=LEFT, padx=2)

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X, pady=(20, 0))

        def confirm():
            amt = topup_var.get()
            if amt <= 0:
                messagebox.showerror("Error", "Amount must be positive.")
                return

            conn = get_connection()
            cur = conn.cursor()
            cur.execute("UPDATE members SET balance = balance + ? WHERE id = ?", (amt, member['id']))
            conn.commit()
            conn.close()

            win.destroy()
            messagebox.showinfo("Top-Up Successful", f"Added RM {amt:.2f} to {member['name']}'s balance!")
            self.refresh_data()

        tb.Button(btn_box, text="Confirm Top-Up", bootstyle="success", command=confirm).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def open_edit_member_modal(self):
        sel = self.members_tree.selection()
        if not sel:
            messagebox.showinfo("Select Member", "Select a member to edit.")
            return

        mem_id = int(sel[0])
        member = next((m for m in self.members if m['id'] == mem_id), None)
        if not member:
            return

        win = tb.Toplevel(self)
        win.title("Edit Member")
        win.geometry("450x420")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text=f"Edit Member: {member['member_code']}", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        name_var = tk.StringVar(value=member['name'])
        tb.Label(c, text="Full Name:").pack(anchor=W)
        tb.Entry(c, textvariable=name_var).pack(fill=X, pady=(2, 8))

        phone_var = tk.StringVar(value=member['phone'])
        tb.Label(c, text="Phone:").pack(anchor=W)
        tb.Entry(c, textvariable=phone_var).pack(fill=X, pady=(2, 8))

        tier_var = tk.StringVar(value=member['tier'])
        tb.Label(c, text="Loyalty Tier:").pack(anchor=W)
        tb.Combobox(c, textvariable=tier_var, values=["Bronze", "Silver", "Gold", "Diamond"], state="readonly").pack(fill=X, pady=(2, 8))

        points_var = tk.IntVar(value=member['points'])
        tb.Label(c, text="Loyalty Points:").pack(anchor=W)
        tb.Entry(c, textvariable=points_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("""
                UPDATE members
                SET name = ?, phone = ?, tier = ?, points = ?
                WHERE id = ?
            """, (name_var.get().strip(), phone_var.get().strip(), tier_var.get(), points_var.get(), member['id']))
            conn.commit()
            conn.close()

            win.destroy()
            messagebox.showinfo("Updated", "Member updated successfully.")
            self.refresh_data()

        tb.Button(btn_box, text="Save Changes", bootstyle="info", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def delete_member(self):
        sel = self.members_tree.selection()
        if not sel:
            return
        mem_id = int(sel[0])
        member = next((m for m in self.members if m['id'] == mem_id), None)
        if not member:
            return

        if messagebox.askyesno("Delete Member", f"Delete member record '{member['name']}'?"):
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("DELETE FROM members WHERE id = ?", (mem_id,))
            conn.commit()
            conn.close()
            messagebox.showinfo("Deleted", "Member removed.")
            self.refresh_data()

    def open_member_points_modal(self):
        sel = self.members_tree.selection()
        if not sel:
            messagebox.showinfo("Select Member", "Select a member to view points history.")
            return

        mem_id = int(sel[0])
        member = next((m for m in self.members if m['id'] == mem_id), None)
        if not member:
            return

        win = tb.Toplevel(self)
        win.title(f"Points History — {member['name']}")
        win.geometry("550x400")
        win.transient(self)

        c = tb.Frame(win, padding=15)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text=f"⚡ Points Ledger: {member['name']} ({member['points']} Current Pts)", font=("Helvetica", 12, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 10))

        cols = ("Date", "Points Change", "Reason")
        tree = ttk.Treeview(c, columns=cols, show="headings", height=10)
        for col in cols:
            tree.heading(col, text=col)

        tree.column("Date", width=140)
        tree.column("Points Change", width=110, anchor="center")
        tree.column("Reason", width=250)
        tree.pack(fill=BOTH, expand=True)

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("SELECT * FROM points_transactions WHERE member_id = ? ORDER BY id DESC", (mem_id,))
        txs = cur.fetchall()
        conn.close()

        for t in txs:
            chg = f"+{t['points_change']}" if t['points_change'] > 0 else str(t['points_change'])
            tree.insert("", "end", values=(t['created_at'][:16], chg, t['reason']))

    def open_add_reward_modal(self):
        win = tb.Toplevel(self)
        win.title("Add Loyalty Reward Item")
        win.geometry("450x420")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text="Add New Reward", font=("Helvetica", 13, "bold"), bootstyle="warning").pack(anchor=W, pady=(0, 15))

        name_var = tk.StringVar()
        tb.Label(c, text="Reward Title:").pack(anchor=W)
        tb.Entry(c, textvariable=name_var).pack(fill=X, pady=(2, 8))

        cost_var = tk.IntVar(value=150)
        tb.Label(c, text="Points Required:").pack(anchor=W)
        tb.Entry(c, textvariable=cost_var).pack(fill=X, pady=(2, 8))

        type_var = tk.StringVar(value="Food")
        tb.Label(c, text="Reward Type:").pack(anchor=W)
        tb.Combobox(c, textvariable=type_var, values=["Time", "Food", "Merchandise"], state="readonly").pack(fill=X, pady=(2, 8))

        stock_var = tk.IntVar(value=30)
        tb.Label(c, text="Inventory Stock:").pack(anchor=W)
        tb.Entry(c, textvariable=stock_var).pack(fill=X, pady=(2, 8))

        desc_var = tk.StringVar()
        tb.Label(c, text="Description:").pack(anchor=W)
        tb.Entry(c, textvariable=desc_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            name = name_var.get().strip()
            if not name:
                messagebox.showerror("Error", "Reward title is required.")
                return

            conn = get_connection()
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO rewards (name, points_cost, reward_type, stock, description)
                VALUES (?, ?, ?, ?, ?)
            """, (name, cost_var.get(), type_var.get(), stock_var.get(), desc_var.get().strip()))
            conn.commit()
            conn.close()

            win.destroy()
            messagebox.showinfo("Saved", "Reward item added to catalog!")
            self.refresh_data()

        tb.Button(btn_box, text="Save Reward", bootstyle="success", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def export_members_csv(self):
        headers = ["Member Code", "Name", "Phone", "Tier", "Points", "Total Spent (RM)", "Balance (RM)"]
        rows = [[m['member_code'], m['name'], m['phone'], m['tier'], m['points'], m['total_spent'], m['balance']] for m in self.members]
        export_table_to_csv(headers, rows, "tfly_members_crm.csv")

    def export_invoices_csv(self):
        headers = ["Invoice No", "Customer", "Date", "PC Time (RM)", "Snacks (RM)", "Tourney (RM)", "Discount (RM)", "Total Paid (RM)", "Payment Method"]
        rows = [[b['bill_number'], b['customer_name'], b['created_at'], b['station_cost'], b['snack_cost'], b['event_cost'], b['discount_amount'], b['total_amount'], b['payment_method']] for b in self.bills]
        export_table_to_csv(headers, rows, "tfly_invoices_report.csv")




