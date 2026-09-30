"""
TFLY Gaming Café Management System
Feature: Café Shop & Inventory Management
Sub-Modules:
- Food & Beverage Menu Catalog (Cards, filters, search, low-stock badges)
- Seat Delivery & Order Dispatch (Cart, PC station delivery, fulfillment tracker)
- Product & Price Registry (Full Product CRUD)
- Inventory Stock Audit & Restock Log (Stock records, replenishments, CSV export)
"""

import tkinter as tk
from tkinter import ttk, messagebox
import ttkbootstrap as tb
from ttkbootstrap.constants import *
from datetime import datetime

from database import get_connection
from auth import AuthManager
from ui_components import COLORS, StatCard, StatusBadge, TabBar, styled_treeview, export_table_to_csv

class InventoryManagementFrame(tb.Frame):
    def __init__(self, parent, on_checkout_bill=None):
        super().__init__(parent)
        self.on_checkout_bill = on_checkout_bill
        self.cart = {} # product_id -> {product, qty}
        self.selected_category_id = None

        self.setup_ui()
        self.refresh_products()

    def setup_ui(self):
        # 1. Top Metrics Banner
        metrics_frame = tb.Frame(self)
        metrics_frame.pack(fill=X, padx=15, pady=(15, 10))

        self.card_total_items = StatCard(metrics_frame, "Total Products", "0", "📦", COLORS['cyan'], "Active Menu Items")
        self.card_total_items.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_low_stock = StatCard(metrics_frame, "Low-Stock Alerts", "0", "⚠️", COLORS['rose'], "Requires Immediate Restock")
        self.card_low_stock.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_total_orders = StatCard(metrics_frame, "Pending Deliveries", "0", "🛵", COLORS['amber'], "Orders in Dispatch Queue")
        self.card_total_orders.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_fnb_revenue = StatCard(metrics_frame, "F&B Sales", "RM 0.00", "🍹", COLORS['green'], "Total Refreshment Revenue")
        self.card_fnb_revenue.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.tabbar = TabBar(self, style="underline")
        self.tabbar.pack(fill=BOTH, expand=True)

        self.tab_catalog   = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=10, pady=10)
        self.tab_delivery  = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_crud      = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_stock_log = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)

        self.tabbar.add_tab("catalog",   "Food & Beverage Menu",  self.tab_catalog)
        self.tabbar.add_tab("delivery",  "Seat Delivery & Orders", self.tab_delivery)
        self.tabbar.add_tab("crud",      "Product Registry",       self.tab_crud)
        self.tabbar.add_tab("stock",     "Stock Audit & Restock",  self.tab_stock_log)

        self.setup_submodule_catalog()
        self.setup_submodule_delivery()
        self.setup_submodule_crud()
        self.setup_submodule_stock_log()

        self.tabbar.build(default_key="catalog")
        self.notebook = self.tabbar

    # ========================================================
    # MENU CATALOG
    # ========================================================
    def setup_submodule_catalog(self):
        container = self.tab_catalog

        # Top Bar
        top_bar = tb.Frame(container)
        top_bar.pack(fill=X, pady=(0, 10))

        # Search bar
        self.search_var = tk.StringVar()
        self.search_var.trace_add("write", lambda *args: self.render_product_grid())
        search_box = tb.Entry(top_bar, textvariable=self.search_var, width=28)
        search_box.pack(side=LEFT, padx=(0, 10))
        tb.Label(top_bar, text="🔍 Search Menu Items", font=("Helvetica", 9), bootstyle="secondary").pack(side=LEFT)

        tb.Button(top_bar, text="🛒 View Delivery Cart", bootstyle="info", 
                  command=lambda: self.notebook.select(self.tab_delivery)).pack(side=RIGHT, padx=4)

        # Category filter buttons
        self.cat_bar = tb.Frame(container)
        self.cat_bar.pack(fill=X, pady=(0, 10))

        # Scrollable Product Grid
        catalog_scroll_frame = tb.Frame(container)
        catalog_scroll_frame.pack(fill=BOTH, expand=True)

        self.canvas = tk.Canvas(catalog_scroll_frame, bg=COLORS['bg'], highlightthickness=0)
        self.scrollbar = tb.Scrollbar(catalog_scroll_frame, orient=VERTICAL, command=self.canvas.yview)
        self.grid_frame = tb.Frame(self.canvas)

        self.grid_frame.bind("<Configure>", lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all")))
        self.canvas_window = self.canvas.create_window((0, 0), window=self.grid_frame, anchor="nw")

        self.canvas.pack(side=LEFT, fill=BOTH, expand=True)
        self.scrollbar.pack(side=RIGHT, fill=Y)
        self.canvas.configure(yscrollcommand=self.scrollbar.set)
        self.canvas.bind('<Configure>', lambda e: self.canvas.itemconfig(self.canvas_window, width=e.width))

    # ========================================================
    # SEAT DELIVERY & ORDER DISPATCH
    # ========================================================
    def setup_submodule_delivery(self):
        container = self.tab_delivery
        paned = ttk.PanedWindow(container, orient=HORIZONTAL)
        paned.pack(fill=BOTH, expand=True)

        # Left: Current Order Cart
        left = tb.Frame(paned, padding=10)
        paned.add(left, weight=3)

        tb.Label(left, text="🛵 Dispatch Order to PC Station", font=("Helvetica", 14, "bold"), bootstyle="info").pack(anchor=W)
        tb.Label(left, text="Items added from menu catalog will be delivered directly to the selected seat", 
                 font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 12))

        # Station Selector
        station_box = tb.Frame(left)
        station_box.pack(fill=X, pady=5)
        tb.Label(station_box, text="Deliver To Rig:", font=("Helvetica", 9, "bold")).pack(side=LEFT, padx=(0, 15))
        self.order_station_cb = tb.Combobox(station_box, state="readonly", width=18)
        self.order_station_cb.pack(side=LEFT)

        # Customer Nickname / Member
        name_box = tb.Frame(left)
        name_box.pack(fill=X, pady=5)
        tb.Label(name_box, text="Gamer / Guest:", font=("Helvetica", 9, "bold")).pack(side=LEFT, padx=(0, 13))
        self.order_customer_var = tk.StringVar(value="Gamer at Seat")
        tb.Entry(name_box, textvariable=self.order_customer_var, width=22).pack(side=LEFT)

        # Cart Table
        tb.Label(left, text="Cart Items:", font=("Helvetica", 10, "bold")).pack(anchor=W, pady=(10, 2))
        columns = ("Item", "Qty", "Price", "Subtotal")
        self.cart_tree = ttk.Treeview(left, columns=columns, show="headings", height=8)
        self.cart_tree.heading("Item", text="Product")
        self.cart_tree.heading("Qty", text="Qty")
        self.cart_tree.heading("Price", text="Unit Price")
        self.cart_tree.heading("Subtotal", text="Subtotal")

        self.cart_tree.column("Item", width=220)
        self.cart_tree.column("Qty", width=50, anchor="center")
        self.cart_tree.column("Price", width=80, anchor="e")
        self.cart_tree.column("Subtotal", width=90, anchor="e")
        self.cart_tree.pack(fill=BOTH, expand=True, pady=5)

        cart_tools = tb.Frame(left)
        cart_tools.pack(fill=X, pady=2)
        tb.Button(cart_tools, text="Remove Selected", bootstyle="danger-outline", command=self.remove_cart_item).pack(side=LEFT)
        tb.Button(cart_tools, text="Clear Cart", bootstyle="secondary-outline", command=self.clear_cart).pack(side=LEFT, padx=5)

        # Right: Checkout & Live Deliveries Tracker
        right = tb.Frame(paned, bootstyle="dark", padding=15)
        paned.add(right, weight=2)

        tb.Label(right, text="🧾 Order Checkout", font=("Helvetica", 13, "bold"), bootstyle="warning").pack(anchor=W)
        self.cart_total_lbl = tb.Label(right, text="Total: RM 0.00", font=("Helvetica", 15, "bold"), bootstyle="light")
        self.cart_total_lbl.pack(anchor=W, pady=(5, 15))

        tb.Button(right, text="🚀 Dispatch Order to Rig", bootstyle="success", command=self.submit_order).pack(fill=X, pady=4)
        tb.Button(right, text="➕ Add More Items from Menu", bootstyle="info-outline", command=lambda: self.notebook.select(self.tab_catalog)).pack(fill=X, pady=2)

        tb.Separator(right).pack(fill=X, pady=15)
        tb.Label(right, text="Live Station Deliveries", font=("Helvetica", 11, "bold"), bootstyle="info").pack(anchor=W)

        self.orders_tree = ttk.Treeview(right, columns=("Order", "Seat", "Status", "Total"), show="headings", height=6)
        self.orders_tree.heading("Order", text="Order No")
        self.orders_tree.heading("Seat", text="Seat")
        self.orders_tree.heading("Status", text="Status")
        self.orders_tree.heading("Total", text="Total")

        self.orders_tree.column("Order", width=95)
        self.orders_tree.column("Seat", width=60, anchor="center")
        self.orders_tree.column("Status", width=80, anchor="center")
        self.orders_tree.column("Total", width=65, anchor="e")
        self.orders_tree.pack(fill=BOTH, expand=True, pady=5)

        if AuthManager.is_staff():
            tb.Button(right, text="✅ Mark Selected as Delivered", bootstyle="success-outline", command=self.mark_order_delivered).pack(fill=X, pady=4)

    # ========================================================
    # PRODUCT & PRICE REGISTRY (CRUD)
    # ========================================================
    def setup_submodule_crud(self):
        container = self.tab_crud

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        tb.Label(top, text="⚙️ Product Catalogue & Pricing Manager (CRUD)", font=("Helvetica", 13, "bold"), bootstyle="info").pack(side=LEFT)
        
        if AuthManager.is_staff():
            tb.Button(top, text="➕ Add New Product", bootstyle="success", command=self.open_add_product_modal).pack(side=RIGHT, padx=4)

        # Products Table
        cols = ("ID", "Category", "Product Name", "Price (RM)", "Stock", "Min Alert Level", "Status")
        self.product_crud_tree = ttk.Treeview(container, columns=cols, show="headings", height=12)
        for c in cols:
            self.product_crud_tree.heading(c, text=c)

        self.product_crud_tree.column("ID", width=50, anchor="center")
        self.product_crud_tree.column("Category", width=140)
        self.product_crud_tree.column("Product Name", width=220)
        self.product_crud_tree.column("Price (RM)", width=90, anchor="e")
        self.product_crud_tree.column("Stock", width=70, anchor="center")
        self.product_crud_tree.column("Min Alert Level", width=100, anchor="center")
        self.product_crud_tree.column("Status", width=110, anchor="center")
        self.product_crud_tree.pack(fill=BOTH, expand=True)

        act_box = tb.Frame(container)
        act_box.pack(fill=X, pady=8)
        if AuthManager.is_staff():
            tb.Button(act_box, text="✏️ Edit Selected Product", bootstyle="info-outline", command=self.edit_selected_product_from_crud).pack(side=LEFT, padx=3)
            tb.Button(act_box, text="📦 Restock Selected Item", bootstyle="warning-outline", command=self.open_restock_modal).pack(side=LEFT, padx=3)
            tb.Button(act_box, text="🗑️ Delete Product", bootstyle="danger-outline", command=self.delete_selected_product_from_crud).pack(side=LEFT, padx=3)

    def edit_selected_product_from_crud(self):
        sel = self.product_crud_tree.selection()
        if not sel:
            messagebox.showinfo("Select Product", "Please select a product from the table to edit.")
            return
        pid = int(self.product_crud_tree.item(sel[0])['values'][0])
        prod = next((p for p in self.products if p['id'] == pid), None)
        if prod:
            self.open_edit_product_modal(prod)

    def delete_selected_product_from_crud(self):
        sel = self.product_crud_tree.selection()
        if not sel:
            return
        pid = int(self.product_crud_tree.item(sel[0])['values'][0])
        prod = next((p for p in self.products if p['id'] == pid), None)
        if prod and messagebox.askyesno("Confirm Delete", f"Delete '{prod['name']}' from the catalogue?"):
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("DELETE FROM products WHERE id = ?", (pid,))
            conn.commit()
            conn.close()
            self.refresh_products()

    # ========================================================
    # INVENTORY STOCK AUDIT LOG
    # ========================================================
    def setup_submodule_stock_log(self):
        container = self.tab_stock_log

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        tb.Label(top, text="📋 Inventory Stock Record Log & Audit Trail", font=("Helvetica", 13, "bold"), bootstyle="info").pack(side=LEFT)
        tb.Button(top, text="📥 Export Stock Log CSV", bootstyle="info-outline", command=self.export_stock_log_csv).pack(side=RIGHT, padx=4)
        if AuthManager.is_staff():
            tb.Button(top, text="📦 Quick Restock", bootstyle="warning-outline", command=self.open_restock_modal).pack(side=RIGHT, padx=4)

        cols = ("Timestamp", "Product Name", "Qty Change", "Reason / Event", "Recorded By")
        self.stock_log_tree = ttk.Treeview(container, columns=cols, show="headings", height=12)
        for c in cols:
            self.stock_log_tree.heading(c, text=c)

        self.stock_log_tree.column("Timestamp", width=140)
        self.stock_log_tree.column("Product Name", width=220)
        self.stock_log_tree.column("Qty Change", width=90, anchor="center")
        self.stock_log_tree.column("Reason / Event", width=240)
        self.stock_log_tree.column("Recorded By", width=100)
        self.stock_log_tree.pack(fill=BOTH, expand=True)

    def export_stock_log_csv(self):
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("""
            SELECT sr.created_at, p.name, sr.change_qty, sr.reason, sr.recorded_by
            FROM stock_records sr
            JOIN products p ON sr.product_id = p.id
            ORDER BY sr.id DESC
        """)
        rows = cur.fetchall()
        conn.close()
        headers = ["Timestamp", "Product Name", "Quantity Change", "Reason / Event", "Recorded By"]
        export_table_to_csv(headers, [list(r) for r in rows], "tfly_stock_audit_log.csv")

    # ========================================================
    # SHARED LOGIC & REFRESH
    # ========================================================
    def refresh_products(self):
        conn = get_connection()
        cur = conn.cursor()

        cur.execute("SELECT * FROM categories ORDER BY id")
        self.categories = [dict(c) for c in cur.fetchall()]

        cur.execute("""
            SELECT p.*, c.name as category_name, c.icon as category_icon
            FROM products p
            JOIN categories c ON p.category_id = c.id
            ORDER BY p.category_id, p.name
        """)
        self.products = [dict(p) for p in cur.fetchall()]

        low_stock_count = sum(1 for p in self.products if p['stock'] <= p['min_stock_alert'])

        cur.execute("SELECT COUNT(*) FROM orders WHERE order_status IN ('Pending', 'Delivering')")
        pending_count = cur.fetchone()[0]

        cur.execute("SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE payment_status = 'Paid'")
        total_fnb_sales = cur.fetchone()[0]

        cur.execute("SELECT * FROM orders ORDER BY id DESC LIMIT 15")
        self.recent_orders = [dict(o) for o in cur.fetchall()]

        # Stock records
        cur.execute("""
            SELECT sr.created_at, p.name as product_name, sr.change_qty, sr.reason, sr.recorded_by
            FROM stock_records sr
            JOIN products p ON sr.product_id = p.id
            ORDER BY sr.id DESC LIMIT 40
        """)
        self.stock_records = [dict(r) for r in cur.fetchall()]

        # Active stations for seat delivery dropdown
        cur.execute("SELECT station_number FROM stations ORDER BY station_number")
        all_stations = [row['station_number'] for row in cur.fetchall()]
        conn.close()

        # Update Metrics
        self.card_total_items.set_value(str(len(self.products)))
        self.card_low_stock.set_value(str(low_stock_count))
        self.card_total_orders.set_value(str(pending_count))
        self.card_fnb_revenue.set_value(f"RM {total_fnb_sales:.2f}")

        # Update delivery station options
        self.order_station_cb.config(values=all_stations)
        if all_stations and not self.order_station_cb.get():
            self.order_station_cb.set(all_stations[0])

        # Populate Sub-Module 2.3 CRUD Table
        for item in self.product_crud_tree.get_children():
            self.product_crud_tree.delete(item)
        for p in self.products:
            st_text = "LOW STOCK" if p['stock'] <= p['min_stock_alert'] else "In Stock"
            self.product_crud_tree.insert("", "end", values=(
                p['id'], p['category_name'], p['name'], f"{p['price']:.2f}", p['stock'], p['min_stock_alert'], st_text
            ))

        # Populate Sub-Module 2.4 Stock Log Table
        for item in self.stock_log_tree.get_children():
            self.stock_log_tree.delete(item)
        for r in self.stock_records:
            chg = f"+{r['change_qty']}" if r['change_qty'] > 0 else str(r['change_qty'])
            self.stock_log_tree.insert("", "end", values=(
                r['created_at'][:16], r['product_name'], chg, r['reason'], r['recorded_by'] or 'System'
            ))

        self.render_category_tabs()
        self.render_product_grid()
        self.render_orders_list()

    def render_category_tabs(self):
        for widget in self.cat_bar.winfo_children():
            widget.destroy()

        all_style = "info" if self.selected_category_id is None else "secondary-outline"
        tb.Button(self.cat_bar, text="🌟 All Items", bootstyle=all_style, 
                  command=lambda: self.select_category(None)).pack(side=LEFT, padx=3)

        for cat in self.categories:
            c_style = "info" if self.selected_category_id == cat['id'] else "secondary-outline"
            label = f"{cat.get('icon', '')} {cat['name']}"
            tb.Button(self.cat_bar, text=label, bootstyle=c_style,
                      command=lambda cid=cat['id']: self.select_category(cid)).pack(side=LEFT, padx=3)

    def select_category(self, cat_id):
        self.selected_category_id = cat_id
        self.render_category_tabs()
        self.render_product_grid()

    def render_product_grid(self):
        for widget in self.grid_frame.winfo_children():
            widget.destroy()

        query = self.search_var.get().strip().lower()

        filtered = [
            p for p in self.products
            if (self.selected_category_id is None or p['category_id'] == self.selected_category_id) and
               (not query or query in p['name'].lower() or query in p['description'].lower())
        ]

        if not filtered:
            tb.Label(self.grid_frame, text="No products found matching your search.", font=("Helvetica", 11), bootstyle="secondary").pack(pady=40)
            return

        cols = 3
        for idx, prod in enumerate(filtered):
            row = idx // cols
            col = idx % cols
            self.create_product_card(self.grid_frame, prod, row, col)

    def create_product_card(self, parent, prod, row, col):
        is_low_stock = prod['stock'] <= prod['min_stock_alert']
        out_of_stock = prod['stock'] <= 0

        accent = COLORS['rose'] if (is_low_stock or out_of_stock) else COLORS['cyan']

        card = tk.Frame(parent, bg=COLORS['bg_card'],
                        highlightthickness=1,
                        highlightbackground=COLORS['border'],
                        padx=12, pady=10)
        card.grid(row=row, column=col, padx=6, pady=6, sticky="nsew")
        parent.columnconfigure(col, weight=1)

        # Top 2px accent stripe
        tk.Frame(card, bg=accent, height=2).pack(fill=X, pady=(0, 6))

        top = tk.Frame(card, bg=COLORS['bg_card'])
        top.pack(fill=X)
        tk.Label(top, text=prod['category_icon'], font=("Helvetica", 14),
                 bg=COLORS['bg_card']).pack(side=LEFT)
        tk.Label(top, text=f"RM {prod['price']:.2f}", font=("Helvetica", 12, "bold"),
                 fg=COLORS['amber'], bg=COLORS['bg_card']).pack(side=RIGHT)

        title_lbl = tk.Label(card, text=prod['name'], font=("Helvetica", 10, "bold"),
                             fg=COLORS['text'], bg=COLORS['bg_card'],
                             wraplength=180, justify=LEFT)
        title_lbl.pack(fill=X, pady=(4, 2))

        desc_lbl = tk.Label(card, text=prod['description'] or "", font=("Helvetica", 8),
                            fg=COLORS['text_muted'], bg=COLORS['bg_card'],
                            wraplength=180, justify=LEFT)
        desc_lbl.pack(fill=X, pady=(0, 6))

        stock_box = tk.Frame(card, bg=COLORS['bg_card'])
        stock_box.pack(fill=X, pady=3)

        if out_of_stock:
            tk.Label(stock_box, text="❌ OUT OF STOCK", font=("Helvetica", 8, "bold"),
                     fg=COLORS['rose'], bg=COLORS['bg_card']).pack(side=LEFT)
        elif is_low_stock:
            tk.Label(stock_box, text=f"⚠️ LOW STOCK: {prod['stock']} LEFT", font=("Helvetica", 8, "bold"),
                     fg=COLORS['amber'], bg=COLORS['bg_card']).pack(side=LEFT)
        else:
            tk.Label(stock_box, text=f"✓ In Stock: {prod['stock']} units", font=("Helvetica", 8),
                     fg=COLORS['green'], bg=COLORS['bg_card']).pack(side=LEFT)

        act_box = tk.Frame(card, bg=COLORS['bg_card'])
        act_box.pack(fill=X, pady=(8, 0))

        if prod['stock'] > 0:
            add_btn = tk.Button(act_box, text="🛒 Add to Cart",
                                font=("Helvetica", 9, "bold"),
                                fg="#040817", bg=COLORS['cyan'],
                                activebackground=COLORS['cyan_dim'],
                                relief="flat", cursor="hand2", pady=5,
                                command=lambda p=prod: self.add_to_cart(p))
            add_btn.pack(side=LEFT, fill=X, expand=True, padx=(0, 4))
        else:
            dis_btn = tk.Button(act_box, text="Unavailable", state="disabled",
                                font=("Helvetica", 9), fg=COLORS['text_muted'],
                                bg=COLORS['bg_input'], relief="flat")
            dis_btn.pack(side=LEFT, fill=X, expand=True)

        if AuthManager.is_staff():
            edit_btn = tk.Button(act_box, text="✏️",
                                 font=("Helvetica", 9),
                                 fg=COLORS['text'], bg=COLORS['bg_input'],
                                 activebackground=COLORS['bg_hover'],
                                 relief="flat", cursor="hand2", width=3, pady=4,
                                 command=lambda p=prod: self.open_edit_product_modal(p))
            edit_btn.pack(side=RIGHT, padx=(2, 0))

    def add_to_cart(self, prod):
        pid = prod['id']
        current_qty = self.cart.get(pid, {}).get('qty', 0)
        
        if current_qty + 1 > prod['stock']:
            messagebox.showwarning("Stock Limit", f"Cannot order more than available stock ({prod['stock']})!")
            return

        if pid in self.cart:
            self.cart[pid]['qty'] += 1
        else:
            self.cart[pid] = {'product': prod, 'qty': 1}

        self.update_cart_view()
        messagebox.showinfo("Cart Updated", f"Added '{prod['name']}' to cart! Check Sub-Module 2.2 to dispatch.")

    def remove_cart_item(self):
        selected = self.cart_tree.selection()
        if not selected:
            return
        item_text = self.cart_tree.item(selected[0])['values'][0]
        for pid, data in list(self.cart.items()):
            if data['product']['name'] == item_text:
                del self.cart[pid]
                break
        self.update_cart_view()

    def clear_cart(self):
        self.cart.clear()
        self.update_cart_view()

    def update_cart_view(self):
        for item in self.cart_tree.get_children():
            self.cart_tree.delete(item)

        grand_total = 0.0
        for pid, data in self.cart.items():
            p = data['product']
            qty = data['qty']
            sub = qty * p['price']
            grand_total += sub
            self.cart_tree.insert("", "end", values=(p['name'], qty, f"{p['price']:.2f}", f"{sub:.2f}"))

        self.cart_total_lbl.config(text=f"Total: RM {grand_total:.2f}")

    def submit_order(self):
        if not self.cart:
            messagebox.showwarning("Cart Empty", "Please add snacks or drinks to your cart first.")
            return

        station = self.order_station_cb.get()
        cust_name = self.order_customer_var.get().strip() or "Guest"
        now = datetime.now()
        ord_num = f"ORD-{now.strftime('%Y%m%d%H%M%S')}"

        total_amount = sum(d['qty'] * d['product']['price'] for d in self.cart.values())

        conn = get_connection()
        cur = conn.cursor()

        try:
            cur.execute("""
                INSERT INTO orders (order_number, station_number, customer_name, total_amount, payment_status, order_status)
                VALUES (?, ?, ?, ?, 'Paid', 'Pending')
            """, (ord_num, station, cust_name, total_amount))
            order_id = cur.lastrowid

            for pid, d in self.cart.items():
                p = d['product']
                qty = d['qty']
                sub = qty * p['price']

                cur.execute("""
                    INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, subtotal)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, (order_id, pid, p['name'], qty, p['price'], sub))

                cur.execute("UPDATE products SET stock = stock - ? WHERE id = ?", (qty, pid))

                cur.execute("""
                    INSERT INTO stock_records (product_id, change_qty, reason, recorded_by)
                    VALUES (?, ?, ?, ?)
                """, (pid, -qty, f"Seat Delivery {ord_num} to {station}", cust_name))

            conn.commit()
            messagebox.showinfo("Order Dispatched", f"Order {ord_num} dispatched!\nDelivering to {station}.\nTotal: RM {total_amount:.2f}")
            self.clear_cart()
            self.refresh_products()

        except Exception as e:
            conn.rollback()
            messagebox.showerror("Order Error", f"Failed to place order:\n{e}")
        finally:
            conn.close()

    def render_orders_list(self):
        for item in self.orders_tree.get_children():
            self.orders_tree.delete(item)

        for ord in self.recent_orders:
            self.orders_tree.insert("", "end", values=(
                ord['order_number'],
                ord['station_number'] or "Counter",
                ord['order_status'],
                f"RM {ord['total_amount']:.2f}"
            ))

    def mark_order_delivered(self):
        sel = self.orders_tree.selection()
        if not sel:
            messagebox.showinfo("Select Order", "Select an order to mark as delivered.")
            return

        ord_num = self.orders_tree.item(sel[0])['values'][0]
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("UPDATE orders SET order_status = 'Delivered' WHERE order_number = ?", (ord_num,))
        conn.commit()
        conn.close()
        messagebox.showinfo("Delivered", f"Order {ord_num} marked as Delivered!")
        self.refresh_products()

    def open_add_product_modal(self):
        win = tb.Toplevel(self)
        win.title("Add New Product / Snack")
        win.geometry("450x480")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text="Add New Product to Menu", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        cat_names = [f"{cat['id']} - {cat['name']}" for cat in self.categories]
        cat_var = tk.StringVar(value=cat_names[0] if cat_names else "")
        tb.Label(c, text="Category:").pack(anchor=W)
        tb.Combobox(c, textvariable=cat_var, values=cat_names, state="readonly").pack(fill=X, pady=(2, 8))

        name_var = tk.StringVar()
        tb.Label(c, text="Product Name:").pack(anchor=W)
        tb.Entry(c, textvariable=name_var).pack(fill=X, pady=(2, 8))

        price_var = tk.DoubleVar(value=5.00)
        tb.Label(c, text="Selling Price (RM):").pack(anchor=W)
        tb.Entry(c, textvariable=price_var).pack(fill=X, pady=(2, 8))

        stock_var = tk.IntVar(value=20)
        tb.Label(c, text="Initial Stock Quantity:").pack(anchor=W)
        tb.Entry(c, textvariable=stock_var).pack(fill=X, pady=(2, 8))

        min_alert_var = tk.IntVar(value=5)
        tb.Label(c, text="Low-Stock Alert Threshold:").pack(anchor=W)
        tb.Entry(c, textvariable=min_alert_var).pack(fill=X, pady=(2, 8))

        desc_var = tk.StringVar()
        tb.Label(c, text="Description:").pack(anchor=W)
        tb.Entry(c, textvariable=desc_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            name = name_var.get().strip()
            if not name:
                messagebox.showerror("Error", "Product name cannot be empty.")
                return

            cat_id = int(cat_var.get().split(" - ")[0])
            price = price_var.get()
            stock = stock_var.get()
            min_alert = min_alert_var.get()
            desc = desc_var.get().strip()

            db = get_connection()
            cur = db.cursor()
            cur.execute("""
                INSERT INTO products (category_id, name, price, stock, min_stock_alert, description)
                VALUES (?, ?, ?, ?, ?, ?)
            """, (cat_id, name, price, stock, min_alert, desc))
            new_id = cur.lastrowid

            cur.execute("""
                INSERT INTO stock_records (product_id, change_qty, reason, recorded_by)
                VALUES (?, ?, 'Initial Stock In', 'Admin')
            """, (new_id, stock))

            db.commit()
            db.close()

            win.destroy()
            messagebox.showinfo("Success", f"Product '{name}' added successfully!")
            self.refresh_products()

        tb.Button(btn_box, text="Save Product", bootstyle="success", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def open_edit_product_modal(self, prod):
        win = tb.Toplevel(self)
        win.title(f"Edit Product — {prod['name']}")
        win.geometry("450x460")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text=f"Edit Product Details", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        name_var = tk.StringVar(value=prod['name'])
        tb.Label(c, text="Product Name:").pack(anchor=W)
        tb.Entry(c, textvariable=name_var).pack(fill=X, pady=(2, 8))

        price_var = tk.DoubleVar(value=prod['price'])
        tb.Label(c, text="Selling Price (RM):").pack(anchor=W)
        tb.Entry(c, textvariable=price_var).pack(fill=X, pady=(2, 8))

        stock_var = tk.IntVar(value=prod['stock'])
        tb.Label(c, text="Current Stock:").pack(anchor=W)
        tb.Entry(c, textvariable=stock_var).pack(fill=X, pady=(2, 8))

        alert_var = tk.IntVar(value=prod['min_stock_alert'])
        tb.Label(c, text="Low-Stock Alert Level:").pack(anchor=W)
        tb.Entry(c, textvariable=alert_var).pack(fill=X, pady=(2, 8))

        desc_var = tk.StringVar(value=prod['description'] or "")
        tb.Label(c, text="Description:").pack(anchor=W)
        tb.Entry(c, textvariable=desc_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            db = get_connection()
            cur = db.cursor()
            cur.execute("""
                UPDATE products
                SET name = ?, price = ?, stock = ?, min_stock_alert = ?, description = ?
                WHERE id = ?
            """, (name_var.get().strip(), price_var.get(), stock_var.get(), alert_var.get(), desc_var.get().strip(), prod['id']))
            db.commit()
            db.close()
            win.destroy()
            messagebox.showinfo("Updated", f"Product '{prod['name']}' updated.")
            self.refresh_products()

        def delete():
            if messagebox.askyesno("Confirm Delete", f"Delete '{prod['name']}' from the menu?"):
                db = get_connection()
                cur = db.cursor()
                cur.execute("DELETE FROM products WHERE id = ?", (prod['id'],))
                db.commit()
                db.close()
                win.destroy()
                messagebox.showinfo("Deleted", "Product deleted from inventory.")
                self.refresh_products()

        tb.Button(btn_box, text="Save Changes", bootstyle="info", command=save).pack(side=LEFT, expand=True, fill=X, padx=2)
        tb.Button(btn_box, text="Delete Item", bootstyle="danger", command=delete).pack(side=LEFT, expand=True, fill=X, padx=2)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=2)

    def open_restock_modal(self):
        win = tb.Toplevel(self)
        win.title("Replenish Inventory Stock")
        win.geometry("450x380")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text="📦 Replenish Product Stock", font=("Helvetica", 13, "bold"), bootstyle="warning").pack(anchor=W, pady=(0, 15))

        prod_names = [f"{p['id']} - {p['name']} (Current: {p['stock']})" for p in self.products]
        prod_var = tk.StringVar(value=prod_names[0] if prod_names else "")
        tb.Label(c, text="Select Product:").pack(anchor=W)
        tb.Combobox(c, textvariable=prod_var, values=prod_names, state="readonly").pack(fill=X, pady=(2, 8))

        qty_var = tk.IntVar(value=24)
        tb.Label(c, text="Add Quantity (+):").pack(anchor=W)
        tb.Entry(c, textvariable=qty_var).pack(fill=X, pady=(2, 8))

        reason_var = tk.StringVar(value="Supplier Restock Shipment")
        tb.Label(c, text="Restock Reason / Notes:").pack(anchor=W)
        tb.Combobox(c, textvariable=reason_var, values=["Supplier Restock Shipment", "Emergency Warehouse Transfer", "Inventory Adjustment"], state="readonly").pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            pid = int(prod_var.get().split(" - ")[0])
            add_qty = qty_var.get()
            reason = reason_var.get()

            if add_qty <= 0:
                messagebox.showerror("Error", "Quantity must be greater than zero.")
                return

            db = get_connection()
            cur = db.cursor()
            cur.execute("UPDATE products SET stock = stock + ? WHERE id = ?", (add_qty, pid))
            cur.execute("""
                INSERT INTO stock_records (product_id, change_qty, reason, recorded_by)
                VALUES (?, ?, ?, 'Staff')
            """, (pid, add_qty, reason))
            db.commit()
            db.close()

            win.destroy()
            messagebox.showinfo("Restocked", f"Added +{add_qty} units to inventory!")
            self.refresh_products()

        tb.Button(btn_box, text="Confirm Restock", bootstyle="warning", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)




