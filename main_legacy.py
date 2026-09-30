"""
TFLY Gaming Cafe Management System
Main Application  —  Premium Esports Edition
Starts with beautiful Login/Register screen, then launches dashboard.
"""

import tkinter as tk
from tkinter import ttk, messagebox
import ttkbootstrap as tb
from ttkbootstrap.constants import *
from PIL import Image, ImageTk
import os
from datetime import datetime

from database import init_db, get_connection
from auth import AuthManager
from ui_components import COLORS, get_cached_image


# ──────────────────────────────────────────────────────────────
def _apply_global_ttk_styles():
    s = ttk.Style()
    try:
        s.configure("TCombobox",
                    fieldbackground=COLORS["bg_input"],
                    background=COLORS["bg_input"],
                    foreground=COLORS["text"],
                    arrowcolor=COLORS["cyan"],
                    relief="flat")
        s.configure("TEntry",
                    fieldbackground=COLORS["bg_input"],
                    foreground=COLORS["text"],
                    insertcolor=COLORS["cyan"])
        s.configure("Vertical.TScrollbar",
                    background=COLORS["bg_input"],
                    troughcolor=COLORS["bg"],
                    arrowcolor=COLORS["text_muted"])
    except Exception:
        pass


# ══════════════════════════════════════════════════════════════
#  MAIN APPLICATION  (dashboard — shown after login)
# ══════════════════════════════════════════════════════════════
class MainApplication(tb.Window):

    def __init__(self):
        super().__init__(themename="darkly")
        self.title("TFLY Gaming Cafe — Management System")
        self.geometry("1440x880")
        self.minsize(1200, 740)
        self.configure(bg=COLORS["bg"])

        _apply_global_ttk_styles()
        init_db()

        # Set window icon
        logo_path = os.path.join(os.path.dirname(__file__), "assets", "logo.jpg")
        if os.path.exists(logo_path):
            try:
                icon_img = ImageTk.PhotoImage(
                    Image.open(logo_path).resize((32, 32)))
                self.iconphoto(False, icon_img)
            except Exception:
                pass

        # Frame cache: key -> Frame (lazy-created, never destroyed)
        self._frame_cache: dict = {}
        self._active_key = ""

        # Show login overlay first — covers the entire window
        self._show_login_overlay()

    # ──────────────────────────────────────────────────────────
    #  LOGIN OVERLAY
    # ──────────────────────────────────────────────────────────
    def _show_login_overlay(self):
        """Show full-window login/register before the dashboard."""
        # Resize to login-friendly dimensions
        self.geometry("1140x720")
        self.resizable(False, False)
        self._center(1140, 720)

        from login_screen import LoginFrame
        self._login = LoginFrame(self, on_success=self._after_login)
        self._login.place(x=0, y=0, relwidth=1, relheight=1)
        self._login.lift()

    def _center(self, w, h):
        self.update_idletasks()
        sw = self.winfo_screenwidth()
        sh = self.winfo_screenheight()
        x = (sw - w) // 2
        y = max(0, (sh - h) // 2)
        self.geometry(f"{w}x{h}+{x}+{y}")

    def _after_login(self):
        """Called after successful authentication. Build the dashboard."""
        # Remove login overlay
        try:
            self._login.destroy()
        except Exception:
            pass

        # Expand to full dashboard
        self.resizable(True, True)
        self.geometry("1440x880")
        self._center(1440, 880)

        # Build dashboard
        self._build_header()
        self._build_body()
        self._build_statusbar()
        self._navigate("stations")
        self._tick_clock()

    # ──────────────────────────────────────────────────────────
    #  HEADER
    # ──────────────────────────────────────────────────────────
    def _build_header(self):
        hdr = tk.Frame(self, bg="#070d1f", height=68)
        hdr.pack(fill=X, side=TOP)
        hdr.pack_propagate(False)
        tk.Frame(self, bg=COLORS["cyan"], height=2).pack(fill=X, side=TOP)

        # ── Left: logo + brand ──
        left = tk.Frame(hdr, bg="#070d1f")
        left.pack(side=LEFT, padx=(20, 0))

        logo_path = os.path.join(os.path.dirname(__file__), "assets", "logo.jpg")
        self._logo = get_cached_image(logo_path, size=(40, 40))
        tk.Label(left, image=self._logo, bg="#070d1f").pack(side=LEFT, padx=(0, 12))

        brand = tk.Frame(left, bg="#070d1f")
        brand.pack(side=LEFT)
        tk.Label(brand, text="TFLY ESPORTS GAMING CAFE",
                 font=("Helvetica", 14, "bold"),
                 fg=COLORS["cyan"], bg="#070d1f").pack(anchor=W)
        tk.Label(brand, text="Management System  v2.0   |   Enterprise Arena Edition",
                 font=("Helvetica", 8),
                 fg=COLORS["text_muted"], bg="#070d1f").pack(anchor=W)

        # ── Right: clock + user + role switcher ──
        right = tk.Frame(hdr, bg="#070d1f")
        right.pack(side=RIGHT, padx=(0, 20))

        # Clock
        clk = tk.Frame(right, bg=COLORS["bg_input"],
                       highlightthickness=1,
                       highlightbackground=COLORS["border"])
        clk.pack(side=RIGHT, padx=(14, 0), pady=14)
        self._clock = tk.Label(clk, text="",
                               font=("Helvetica", 10, "bold"),
                               fg=COLORS["amber"], bg=COLORS["bg_input"],
                               padx=10, pady=4)
        self._clock.pack()

        # Switch user / Logout button
        tk.Button(right, text="🚪 Switch User / Logout",
                  font=("Helvetica", 9, "bold"),
                  fg="#ffffff", bg="#1e293b",
                  activebackground=COLORS["rose"],
                  activeforeground="#ffffff",
                  relief="flat", cursor="hand2",
                  padx=12, pady=5,
                  command=self._logout_to_login_screen).pack(
            side=RIGHT, padx=(0, 12), pady=16)

        # User info
        ubox = tk.Frame(right, bg="#070d1f")
        ubox.pack(side=RIGHT, padx=(0, 14))
        self._user_lbl = tk.Label(ubox, text="",
                                  font=("Helvetica", 10, "bold"),
                                  fg="#f8fafc", bg="#070d1f")
        self._user_lbl.pack(anchor=E)
        self._tier_lbl = tk.Label(ubox, text="",
                                  font=("Helvetica", 8, "bold"),
                                  fg=COLORS["cyan"], bg="#070d1f")
        self._tier_lbl.pack(anchor=E)


        # Role switcher (demo helper)
        rbox = tk.Frame(right, bg="#070d1f")
        rbox.pack(side=RIGHT, padx=(0, 18))
        tk.Label(rbox, text="Role View",
                 font=("Helvetica", 8, "bold"),
                 fg=COLORS["text_muted"], bg="#070d1f").pack(anchor=W)
        self._role_var = tk.StringVar()
        cb = ttk.Combobox(rbox, textvariable=self._role_var,
                          values=["Admin", "Staff", "Customer"],
                          state="readonly", width=10,
                          font=("Helvetica", 9))
        cb.pack()
        cb.bind("<<ComboboxSelected>>", self._on_role_switch)

        self._refresh_user_header()

    def _tick_clock(self):
        if not self.winfo_exists():
            return
        if hasattr(self, "_clock") and self._clock.winfo_exists():
            self._clock.config(
                text=datetime.now().strftime(" %Y-%m-%d   %H:%M:%S "))
            self._clock_timer = self.after(1000, self._tick_clock)


    def _refresh_user_header(self):
        user = AuthManager.get_current_user()
        if user:
            provider_tag = " • Google" if user.get("auth_provider") == "google" else ""
            self._user_lbl.config(
                text=f"{user['full_name']} ({user['role'].upper()}{provider_tag})")
            if user.get("tier"):
                pts = user.get("points", 0)
                bal = user.get("balance", 0.0)
                self._tier_lbl.config(
                    text=f"VIP {user['tier']}  |  {pts} pts  |  Wallet: RM {bal:.2f}",
                    fg=COLORS["amber"])
            else:
                self._tier_lbl.config(
                    text=f"System {user['role'].upper()} Access  |  Terminal Ready",
                    fg=COLORS["cyan"])
            self._role_var.set(user["role"].capitalize())


    def _on_role_switch(self, event=None):
        role = self._role_var.get().lower()
        new_user = AuthManager.switch_demo_role(role)
        self._refresh_user_header()
        self._force_reload(self._active_key)

    # ──────────────────────────────────────────────────────────
    #  BODY — sidebar + content
    # ──────────────────────────────────────────────────────────
    def _build_body(self):
        body = tk.Frame(self, bg=COLORS["bg"])
        body.pack(fill=BOTH, expand=True)

        # Sidebar
        self._sidebar = tk.Frame(body, bg=COLORS["sidebar_bg"], width=232)
        self._sidebar.pack(side=LEFT, fill=Y)
        self._sidebar.pack_propagate(False)
        tk.Frame(body, bg=COLORS["border"], width=1).pack(side=LEFT, fill=Y)

        # Sidebar top accent
        tk.Frame(self._sidebar, bg=COLORS["cyan"], height=2).pack(fill=X)
        tk.Label(self._sidebar, text="  MENU",
                 font=("Helvetica", 8, "bold"),
                 fg=COLORS["text_muted"],
                 bg=COLORS["sidebar_bg"],
                 pady=14).pack(anchor=W)

        NAV = [
            ("stations",    "\U0001f5a5", "PC Stations",
             "Floor Map  &  Live Sessions"),
            ("inventory",   "\U0001f354", "Cafe Shop",
             "Menu  &  Inventory"),
            ("tournaments", "\U0001f3c6", "Tournaments",
             "Events  &  Brackets"),
            ("billing",     "\U0001f48e", "Members & Billing",
             "VIP CRM  &  Cashier"),
            ("arcade",      "\U0001f3b0", "Arcade Lounge",
             "Lucky Spin  &  Quests"),
            ("reports",     "\U0001f4ca", "Analytics",
             "Charts  &  Reports"),
            ("staff",       "🏢", "Staff Desk & DB",
             "Shift Register & Live DB Inspector"),
        ]


        self._nav_widgets = {}
        for key, icon, main, sub in NAV:
            self._nav_widgets[key] = self._make_nav_item(key, icon, main, sub)

        # Sidebar bottom
        bot = tk.Frame(self._sidebar, bg=COLORS["sidebar_bg"], padx=16, pady=12)
        bot.pack(fill=X, side=BOTTOM)
        tk.Frame(self._sidebar, bg=COLORS["border"], height=1).pack(fill=X, side=BOTTOM)

        tk.Label(bot, text="TFLY  Arena  v2.0",
                 font=("Helvetica", 9, "bold"),
                 fg=COLORS["text"], bg=COLORS["sidebar_bg"]).pack(anchor=W)
        tk.Label(bot, text="SQLite3  |  Tkinter + ttkbootstrap",
                 font=("Helvetica", 8),
                 fg=COLORS["text_muted"],
                 bg=COLORS["sidebar_bg"]).pack(anchor=W)

        # Content area
        self._content = tk.Frame(body, bg=COLORS["bg"])
        self._content.pack(side=LEFT, fill=BOTH, expand=True)

    def _make_nav_item(self, key, icon, main_text, sub_text):
        outer = tk.Frame(self._sidebar, bg=COLORS["sidebar_bg"], cursor="hand2")
        outer.pack(fill=X, padx=10, pady=2)

        stripe = tk.Frame(outer, bg=COLORS["cyan"], width=3)

        inner = tk.Frame(outer, bg=COLORS["sidebar_item"], padx=10, pady=11)
        inner.pack(side=RIGHT, fill=BOTH, expand=True)

        ico = tk.Label(inner, text=icon,
                       font=("Segoe UI Emoji", 16),
                       fg=COLORS["text_muted"],
                       bg=COLORS["sidebar_item"])
        ico.grid(row=0, column=0, rowspan=2, padx=(0, 12), sticky="ns")

        mlbl = tk.Label(inner, text=main_text,
                        font=("Helvetica", 10, "bold"),
                        fg=COLORS["text_dim"],
                        bg=COLORS["sidebar_item"], anchor=W)
        mlbl.grid(row=0, column=1, sticky=W)

        slbl = tk.Label(inner, text=sub_text,
                        font=("Helvetica", 8),
                        fg=COLORS["text_muted"],
                        bg=COLORS["sidebar_item"], anchor=W)
        slbl.grid(row=1, column=1, sticky=W)

        widgets = {"outer": outer, "inner": inner, "stripe": stripe,
                   "icon": ico, "main": mlbl, "sub": slbl}
        self._nav_widgets[key] = widgets  # pre-assign (overwritten by caller)

        for w in (outer, inner, ico, mlbl, slbl):
            w.bind("<Button-1>", lambda e, k=key: self._navigate(k))
            w.bind("<Enter>",    lambda e, k=key: self._nav_hover(k, True))
            w.bind("<Leave>",    lambda e, k=key: self._nav_hover(k, False))

        return widgets

    def _nav_hover(self, key, entering):
        if key == self._active_key:
            return
        w = self._nav_widgets.get(key)
        if not w:
            return
        bg = COLORS["sidebar_hover"] if entering else COLORS["sidebar_item"]
        for part in ("inner", "icon", "main", "sub"):
            try:
                w[part].config(bg=bg)
            except Exception:
                pass

    def _set_nav_active(self, key):
        for k, w in self._nav_widgets.items():
            if k == key:
                for part in ("inner", "icon", "main", "sub"):
                    try:
                        w[part].config(bg=COLORS["sidebar_active"])
                    except Exception:
                        pass
                try:
                    w["icon"].config(fg=COLORS["cyan"])
                    w["main"].config(fg=COLORS["cyan"])
                    w["sub"].config(fg=COLORS["cyan_dim"])
                    w["stripe"].pack(side=LEFT, fill=Y, before=w["inner"])
                except Exception:
                    pass
            else:
                for part in ("inner", "icon", "main", "sub"):
                    try:
                        w[part].config(bg=COLORS["sidebar_item"])
                    except Exception:
                        pass
                try:
                    w["icon"].config(fg=COLORS["text_muted"])
                    w["main"].config(fg=COLORS["text_dim"])
                    w["sub"].config(fg=COLORS["text_muted"])
                    w["stripe"].pack_forget()
                except Exception:
                    pass

    # ──────────────────────────────────────────────────────────
    #  FRAME-CACHED NAVIGATION
    # ──────────────────────────────────────────────────────────
    from station_manager   import StationManagementFrame
    from cafe_shop         import InventoryManagementFrame
    from tournament_arena  import TournamentManagementFrame
    from member_billing    import MemberBillingManagementFrame
    from arcade_lounge     import ArcadeLoungeFrame
    from reports           import ReportsDashboardFrame
    from staff_manager     import StaffManagementFrame

    _CLASSES = {
        "stations":    StationManagementFrame,
        "inventory":   InventoryManagementFrame,
        "tournaments": TournamentManagementFrame,
        "billing":     MemberBillingManagementFrame,
        "arcade":      ArcadeLoungeFrame,
        "reports":     ReportsDashboardFrame,
        "staff":       StaffManagementFrame,
    }
    _NAMES = {
        "stations":    "PC Stations  &  Sessions",
        "inventory":   "Cafe Shop  &  Inventory",
        "tournaments": "Tournament Arena",
        "billing":     "Members  &  Billing",
        "arcade":      "Arcade Lounge",
        "reports":     "Analytics  &  Reports",
        "staff":       "Staff Desk  &  Database Architecture",
    }


    def _navigate(self, key: str):
        if key == self._active_key:
            return
        if self._active_key in self._frame_cache:
            self._frame_cache[self._active_key].pack_forget()

        if key not in self._frame_cache:
            cls = self._CLASSES.get(key)
            if cls:
                self._frame_cache[key] = cls(self._content)

        if key in self._frame_cache:
            self._frame_cache[key].pack(fill=BOTH, expand=True)

        self._active_key = key
        self._set_nav_active(key)
        if hasattr(self, "_status_lbl"):
            self._status_lbl.config(
                text=f"  Active:   {self._NAMES.get(key, key)}"
                     f"   |   Database: tfly_gaming.db")

    def _force_reload(self, key: str):
        if key in self._frame_cache:
            self._frame_cache[key].destroy()
            del self._frame_cache[key]
        old = self._active_key
        self._active_key = ""
        self._navigate(old)

    # ──────────────────────────────────────────────────────────
    #  STATUS BAR
    # ──────────────────────────────────────────────────────────
    def _build_statusbar(self):
        tk.Frame(self, bg=COLORS["border"], height=1).pack(fill=X, side=BOTTOM)
        bar = tk.Frame(self, bg=COLORS["sidebar_bg"], height=26)
        bar.pack(fill=X, side=BOTTOM)
        bar.pack_propagate(False)

        self._status_lbl = tk.Label(
            bar,
            text="  System Ready   |   Database: tfly_gaming.db",
            font=("Helvetica", 8),
            fg=COLORS["text_muted"],
            bg=COLORS["sidebar_bg"])
        self._status_lbl.pack(side=LEFT)

        user = AuthManager.get_current_user()
        uname = user.get("username", "?") if user else "?"
        tk.Label(bar,
                 text=f"\u25cf  Logged in as {uname}   |  ",
                 font=("Helvetica", 8),
                 fg=COLORS["green"],
                 bg=COLORS["sidebar_bg"]).pack(side=RIGHT)
        tk.Label(bar,
                 text="TFLY Gaming Cafe Management System   |  ",
                 font=("Helvetica", 8),
                 fg=COLORS["text_muted"],
                 bg=COLORS["sidebar_bg"]).pack(side=RIGHT)

    # ──────────────────────────────────────────────────────────
    #  LOGOUT / RETURN TO FULL LOGIN SCREEN
    # ──────────────────────────────────────────────────────────
    def _logout_to_login_screen(self):
        """Log out current user and return to the full Login & Register screen."""
        if hasattr(self, "_clock_timer") and self._clock_timer:
            try:
                self.after_cancel(self._clock_timer)
                self._clock_timer = None
            except Exception:
                pass

        AuthManager.set_current_user(None)

        for w in self.winfo_children():
            try:
                w.destroy()
            except Exception:
                pass

        self._frame_cache.clear()
        self._active_key = ""
        self._show_login_overlay()



# ══════════════════════════════════════════════════════════════
if __name__ == "__main__":
    app = MainApplication()
    app.mainloop()
