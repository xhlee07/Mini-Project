"""
TFLY Gaming Cafe Management System
Login & Registration Screen — Google Play Esports Edition
High-tech animated cyber canvas with Google Play onboarding flow,
DOB dropdown age verification (>= 15), Google account linking,
and fresh 0% data account initialization.
"""

import tkinter as tk
from tkinter import ttk, messagebox
from datetime import datetime, date
import webbrowser
import random
import os

from auth import AuthManager
from ui_components import COLORS

# Dropdown constants
MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
MONTH_NUM = {m: i + 1 for i, m in enumerate(MONTHS)}

_LP = 460  # Left panel width on canvas


class LoginFrame(tk.Frame):
    """
    Full-screen overlay for Google Play-style login, registration & Google linking.
    Parent should be the main root window.
    on_success() is called when authentication succeeds.
    """

    def __init__(self, parent, on_success=None):
        super().__init__(parent, bg="#040817")
        self._ok = on_success
        self._anim = True
        self._particles = []
        self._canvas = None
        self._right = None
        self._pw = 1140
        self._ph = 720
        self._initialized = False

        # Initialize immediately with standard size
        self._init_canvas(1140, 720)
        self.bind("<Configure>", self._on_configure)

    def _on_configure(self, event):
        w, h = event.width, event.height
        if w < 50 or (w == self._pw and h == self._ph):
            return
        self._pw, self._ph = w, h
        # Only adjust canvas placement, DO NOT destroy the active card!
        if self._canvas:
            self._canvas.config(width=w, height=h)
        if self._right:
            self._right.config(width=max(100, w - _LP), height=h)

    def _init_canvas(self, W, H):
        if self._initialized:
            return
        self._initialized = True

        self._canvas = tk.Canvas(self, width=W, height=H,
                                 bg="#040817", highlightthickness=0)
        self._canvas.place(x=0, y=0, width=W, height=H)

        rw = max(100, W - _LP)
        self._right = tk.Frame(self._canvas, bg="#080e22",
                               width=rw, height=H)
        self._right.place(x=_LP, y=0)
        self._right.pack_propagate(False)

        self._draw_left(W, H)
        self._show_login()
        self._tick()


    # ──────────────────────────────────────────────────────────
    #  LEFT HERO PANEL (Esports Cyber Canvas)
    # ──────────────────────────────────────────────────────────
    def _draw_left(self, W, H):
        c = self._canvas
        lw = _LP

        # Vertical Cyber Gradient
        for i in range(H):
            r = int(4  + (12 - 4)  * i / H)
            g = int(8  + (22 - 8)  * i / H)
            b = int(24 + (52 - 24) * i / H)
            c.create_line(0, i, lw, i, fill=f"#{r:02x}{g:02x}{b:02x}")

        # Hex / Grid lines
        for x in range(0, lw, 36):
            c.create_line(x, 0, x, H, fill="#0c1836", width=1)
        for y in range(0, H, 36):
            c.create_line(0, y, lw, y, fill="#0c1836", width=1)

        # Ambient Glow Orbs
        self._orb(c, lw * 0.22, H * 0.16, 140, "#8b5cf6")
        self._orb(c, lw * 0.78, H * 0.68, 160, "#22d3ee")
        self._orb(c, lw * 0.50, H * 0.42,  80, "#06b6d4")

        # Neon Divider line
        c.create_line(lw, 0, lw, H, fill="#22d3ee", width=2)

        # Google Play Partner Header Badge
        c.create_rectangle(lw // 2 - 130, H * 0.05, lw // 2 + 130, H * 0.05 + 26,
                           fill="#0a1530", outline="#1e3a6a", width=1)
        c.create_text(lw // 2, H * 0.05 + 13,
                      text="▶ Google Play Games  •  Verified Client",
                      font=("Helvetica", 8, "bold"), fill="#38bdf8")

        # Logo & Branding
        c.create_text(lw // 2, H * 0.15,
                      text="⚡ TFLY ARENA", anchor="center",
                      font=("Helvetica", 36, "bold"),
                      fill="#22d3ee")
        c.create_text(lw // 2, H * 0.15 + 46,
                      text="NEXT-GEN ESPORTS CAFE MANAGEMENT", anchor="center",
                      font=("Helvetica", 10, "bold"),
                      fill="#7dd3fc")
        c.create_text(lw // 2, H * 0.15 + 68,
                      text="High-FPS Workstations • Tournaments • Seat Dining",
                      anchor="center",
                      font=("Helvetica", 8),
                      fill="#476899")

        # Horizontal accent
        pad = 40
        c.create_line(pad, H * 0.32, lw - pad, H * 0.32, fill="#1c305c", width=1)

        # Feature Highlights
        features = [
            ("💻", "RTX 4090 Battlestations", "i9-14900K • 240Hz Fast-IPS Monitors"),
            ("🌐", "Google & Gmail Direct Link", "Instant sign-in with your Google account"),
            ("🏆", "Esports Weekly Cups", "Compete in Valorant, LoL & CS2 Brackets"),
            ("🍔", "Gamer Fuel Seat Delivery", "Order ramen, energy drinks & snacks to PC"),
            ("🎁", "VIP 0% Fresh Start", "50 Free Welcome Points upon registration"),
        ]

        fy = H * 0.36
        for icon, title, sub in features:
            c.create_oval(pad, fy - 16, pad + 36, fy + 20,
                          fill="#0c1a36", outline="#254278")
            c.create_text(pad + 18, fy + 2, text=icon,
                          font=("Segoe UI Emoji", 11), anchor="center")
            c.create_text(pad + 48, fy - 6, text=title,
                          font=("Helvetica", 10, "bold"),
                          fill="#f1f5f9", anchor="w")
            c.create_text(pad + 48, fy + 10, text=sub,
                          font=("Helvetica", 8),
                          fill="#64748b", anchor="w")
            fy += 56

        # Footer
        c.create_text(lw // 2, H - 24,
                      text="TFLY Arena v2.0  •  Enterprise SQLite  •  © 2026",
                      anchor="center", font=("Helvetica", 8), fill="#2e4875")

    def _orb(self, canvas, cx, cy, r, color):
        steps = 6
        for i in range(steps, 0, -1):
            t = i / steps * 0.24
            col = _blend(color, "#040817", t)
            s = r * i // steps
            canvas.create_oval(cx - s, cy - s, cx + s, cy + s, fill=col, outline="")

    # ──────────────────────────────────────────────────────────
    #  PARTICLE ANIMATION
    # ──────────────────────────────────────────────────────────
    def _tick(self):
        if not self._anim:
            return
        try:
            c = self._canvas
            if not c or not c.winfo_exists():
                return
            H = self._ph

            if random.random() < 0.35:
                x = random.randint(15, _LP - 15)
                r = random.randint(1, 3)
                col = "#22d3ee" if random.random() > 0.4 else "#a855f7"
                spd = random.uniform(0.5, 1.4)
                pid = c.create_oval(x - r, H, x + r, H + r * 2,
                                    fill=col, outline="", tags="ptcl")
                self._particles.append([pid, x, float(H), spd, r])

            alive = []
            for p in self._particles:
                pid, x, y, spd, r = p
                ny = y - spd
                c.coords(pid, x - r, ny - r, x + r, ny + r)
                if ny > -5:
                    p[2] = ny
                    alive.append(p)
                else:
                    c.delete(pid)
            self._particles = alive
        except tk.TclError:
            pass

        self.after(40, self._tick)

    # ──────────────────────────────────────────────────────────
    #  FORM HELPERS
    # ──────────────────────────────────────────────────────────
    def _clear_right(self):
        if self._right:
            for w in self._right.winfo_children():
                w.destroy()

    def _card(self):
        outer = tk.Frame(self._right, bg="#080e22")
        outer.place(relx=0.5, rely=0.5, anchor="center")
        return outer

    def _lbl(self, parent, text, size=10, bold=False, color=None):
        color = color or COLORS["text_dim"]
        f = ("Helvetica", size, "bold") if bold else ("Helvetica", size)
        return tk.Label(parent, text=text, font=f, fg=color, bg="#080e22")

    def _entry(self, parent, label, var, width=380, secret=False):
        self._lbl(parent, label, size=9, bold=True).pack(anchor="w", pady=(2, 2))
        wrap = tk.Frame(parent, bg=COLORS["bg_input"],
                        highlightthickness=1,
                        highlightbackground=COLORS["border"],
                        width=width, height=42)
        wrap.pack(anchor="w", pady=(0, 8))
        wrap.pack_propagate(False)
        e = tk.Entry(wrap, textvariable=var,
                     font=("Helvetica", 11),
                     fg=COLORS["text"], bg=COLORS["bg_input"],
                     insertbackground=COLORS["cyan"],
                     relief="flat", bd=0,
                     show="•" if secret else "")
        e.pack(fill="both", expand=True, padx=12, pady=6)
        e.bind("<FocusIn>",  lambda _: wrap.config(
            highlightbackground=COLORS["cyan"], highlightthickness=2))
        e.bind("<FocusOut>", lambda _: wrap.config(
            highlightbackground=COLORS["border"], highlightthickness=1))
        return e


    # ──────────────────────────────────────────────────────────
    #  VIEW 1: SIGN IN SCREEN
    # ──────────────────────────────────────────────────────────
    def _show_login(self):
        self._clear_right()
        if not self._right:
            return

        card = self._card()

        # Header with Logo Icon
        tk.Label(card, text="⚡", font=("Helvetica", 32),
                 fg=COLORS["cyan"], bg="#080e22").pack(pady=(0, 4))
        tk.Label(card, text="Welcome to TFLY Arena",
                 font=("Helvetica", 22, "bold"),
                 fg=COLORS["text"], bg="#080e22").pack()
        tk.Label(card, text="Sign in with your TFLY or Google Account",
                 font=("Helvetica", 9),
                 fg=COLORS["text_dim"], bg="#080e22").pack(pady=(2, 16))

        # Primary Google Button (Google Colors accent)
        g_wrap = tk.Frame(card, bg="#111c38", width=380, height=48,
                          highlightthickness=1, highlightbackground="#253a6e", cursor="hand2")
        g_wrap.pack(anchor="w", pady=(0, 14))
        g_wrap.pack_propagate(False)

        # Google 4-Color Stripe on top of button
        stripe = tk.Frame(g_wrap, height=3)
        stripe.pack(fill="x", side="top")
        for col in ["#4285F4", "#EA4335", "#FBBC05", "#34A853"]:
            tk.Frame(stripe, bg=col, height=3).pack(side="left", fill="both", expand=True)

        g_inner = tk.Frame(g_wrap, bg="#111c38", cursor="hand2")
        g_inner.pack(fill="both", expand=True)
        tk.Label(g_inner, text="🌐  Continue with Google Account",
                 font=("Helvetica", 10, "bold"),
                 fg="#ffffff", bg="#111c38", cursor="hand2").pack(pady=10)

        for w in (g_wrap, g_inner):
            w.bind("<Button-1>", lambda _: self._show_google_onboarding())
            w.bind("<Enter>", lambda _: g_wrap.config(highlightbackground=COLORS["cyan"]))
            w.bind("<Leave>", lambda _: g_wrap.config(highlightbackground="#253a6e"))

        # Divider "or sign in with password"
        df = tk.Frame(card, bg="#080e22")
        df.pack(fill="x", pady=6)
        tk.Frame(df, bg=COLORS["border"], height=1).pack(fill="x", pady=6)
        tk.Label(df, text="or enter username / email", font=("Helvetica", 8),
                 fg=COLORS["text_muted"], bg="#080e22").place(relx=0.5, rely=0.5, anchor="center")

        u_var = tk.StringVar()
        p_var = tk.StringVar()
        self._entry(card, "Username or Gmail Address", u_var)
        self._entry(card, "Password", p_var, secret=True)


        err = tk.Label(card, text="", font=("Helvetica", 9),
                       fg=COLORS["rose"], bg="#080e22", wraplength=380)
        err.pack(anchor="w", pady=(0, 4))

        def do_login():
            ident = u_var.get().strip()
            pwd = p_var.get().strip()
            ok, msg = AuthManager.login(ident, pwd)
            if ok:
                self._succeed()
            else:
                err.config(text=msg)

        # Standard Sign In Button
        sbtn = tk.Button(card, text="Sign In to TFLY", command=do_login,
                         font=("Helvetica", 10, "bold"),
                         fg="#040817", bg=COLORS["cyan"],
                         activebackground=COLORS["cyan_dim"],
                         relief="flat", cursor="hand2", width=42, pady=10)
        sbtn.pack(anchor="w", pady=(2, 10))

        # Quick Demo Buttons (for student / examiner demo)
        qf = tk.Frame(card, bg="#080e22")
        qf.pack(pady=(4, 0), anchor="w")
        tk.Label(qf, text="Quick Demo: ", font=("Helvetica", 8),
                 fg=COLORS["text_muted"], bg="#080e22").pack(side="left")
        for lbl, u, p, c in [
            ("Admin",  "admin",  "admin123", COLORS["rose"]),
            ("Staff",  "staff1", "staff123", COLORS["amber"]),
            ("Gamer",  "gamer1", "gamer123", COLORS["green"]),
        ]:
            def _q(uu=u, pp=p):
                u_var.set(uu); p_var.set(pp); do_login()
            tk.Button(qf, text=lbl, command=_q,
                      font=("Helvetica", 8, "bold"),
                      fg=c, bg=COLORS["bg_input"],
                      activebackground=COLORS["bg_hover"],
                      relief="flat", cursor="hand2", padx=8, pady=3).pack(side="left", padx=2)

        # Footer Links
        rf = tk.Frame(card, bg="#080e22")
        rf.pack(pady=(16, 0))
        tk.Label(rf, text="New to TFLY Arena?  ", font=("Helvetica", 9),
                 fg=COLORS["text_muted"], bg="#080e22").pack(side="left")
        tk.Button(rf, text="Register New Account",
                  font=("Helvetica", 9, "bold"),
                  fg=COLORS["cyan"], bg="#080e22",
                  activeforeground=COLORS["cyan_dim"],
                  activebackground="#080e22",
                  relief="flat", cursor="hand2", bd=0,
                  command=self._show_register).pack(side="left")

    # ──────────────────────────────────────────────────────────
    #  VIEW 2: GOOGLE PLAY ONBOARDING & GMAIL REGISTRATION
    # ──────────────────────────────────────────────────────────
    def _show_google_onboarding(self):
        self._clear_right()
        if not self._right:
            return

        card = self._card()

        # Google 4-Color top banner
        g_banner = tk.Frame(card, height=4)
        g_banner.pack(fill="x", pady=(0, 10))
        for col in ["#4285F4", "#EA4335", "#FBBC05", "#34A853"]:
            tk.Frame(g_banner, bg=col, height=4).pack(side="left", fill="both", expand=True)

        tk.Label(card, text="🌐 Google Play Onboarding",
                 font=("Helvetica", 18, "bold"),
                 fg="#ffffff", bg="#080e22").pack(anchor="w")
        tk.Label(card, text="Link your Google Account • Creates fresh 0% data account",
                 font=("Helvetica", 9),
                 fg=COLORS["text_dim"], bg="#080e22").pack(anchor="w", pady=(2, 10))

        # Helper button to open official Google site if user doesn't have an account
        ext_f = tk.Frame(card, bg="#0f1a38", padx=10, pady=8,
                         highlightthickness=1, highlightbackground="#1e346b")
        ext_f.pack(fill="x", pady=(0, 12))
        tk.Label(ext_f, text="Need a Google Account?", font=("Helvetica", 8, "bold"),
                 fg="#93c5fd", bg="#0f1a38").pack(side="left")
        tk.Button(ext_f, text="Open Google.com to Create One ↗",
                  command=lambda: webbrowser.open("https://accounts.google.com/signup"),
                  font=("Helvetica", 8, "bold"),
                  fg=COLORS["cyan"], bg="#0f1a38",
                  activeforeground="#ffffff", activebackground="#0f1a38",
                  relief="flat", cursor="hand2", bd=0).pack(side="right")

        # Form variables
        email_var = tk.StringVar()
        pass_var  = tk.StringVar()

        name_var  = tk.StringVar()
        ph_var    = tk.StringVar()
        day_v     = tk.StringVar(value="15")
        mon_v     = tk.StringVar(value="Jun")
        yr_v      = tk.StringVar(value="2004")

        W = 380
        self._entry(card, "Google Email Address (Gmail)", email_var, W)
        self._entry(card, "Password (min. 6 chars)", pass_var, W, secret=True)
        self._entry(card, "Full Name / Gamer Tag", name_var, W)
        self._entry(card, "Mobile Number", ph_var, W)

        # Date of Birth dropdowns (Age >= 15 check)
        tk.Label(card, text="Date of Birth (Must be 15 or older)",
                 font=("Helvetica", 9, "bold"),
                 fg=COLORS["text_dim"], bg="#080e22").pack(anchor="w", pady=(2, 2))

        b_row = tk.Frame(card, bg="#080e22")
        b_row.pack(anchor="w", pady=(0, 10))

        now_yr = datetime.now().year
        # Dropdowns
        for var, vals, w_cb in [
            (day_v, [str(d) for d in range(1, 32)], 5),
            (mon_v, MONTHS, 7),
            (yr_v,  [str(y) for y in range(now_yr - 15, 1940, -1)], 7)
        ]:
            wrap = tk.Frame(b_row, bg=COLORS["bg_input"],
                            highlightthickness=1, highlightbackground=COLORS["border"])
            wrap.pack(side="left", padx=(0, 8))
            cb = ttk.Combobox(wrap, textvariable=var, values=vals, state="readonly", width=w_cb)
            cb.pack(ipady=4, padx=4, pady=2)

        err_lbl = tk.Label(card, text="", font=("Helvetica", 9),
                           fg=COLORS["rose"], bg="#080e22", wraplength=W, justify="left")
        err_lbl.pack(anchor="w", pady=(0, 4))

        def do_link_google():
            em = email_var.get().strip()
            pwd = pass_var.get().strip()
            name = name_var.get().strip()
            ph = ph_var.get().strip()
            day = day_v.get()
            mon = mon_v.get()
            yr = yr_v.get()

            if not em or "@" not in em:
                err_lbl.config(text="Please enter a valid Gmail address."); return
            if not pwd or len(pwd) < 6:
                err_lbl.config(text="Password must be at least 6 characters."); return
            if not name:
                err_lbl.config(text="Please enter your Gamer Name or Full Name."); return

            # Age validation (>= 15)
            try:
                dob = date(int(yr), MONTH_NUM[mon], int(day))
                age = (date.today() - dob).days // 365
                if age < 15:
                    err_lbl.config(
                        text=f"⚠️ Age Requirement: You must be at least 15 years old. (Current age: {age})")
                    return
            except Exception:
                err_lbl.config(text="Invalid date of birth selected."); return

            dob_str = f"{day} {mon} {yr}"
            ok, msg = AuthManager.link_or_register_google(
                email=em, full_name=name, phone=ph, password=pwd, dob=dob_str
            )

            if ok:
                messagebox.showinfo(
                    "Google Play Account Connected! 🎮",
                    f"🎉 Account Verified & Ready!\n\n"
                    f"Welcome to TFLY Arena, {name}!\n"
                    f"• Account Status: Fresh 0% Usage Data\n"
                    f"• Tier: VIP Bronze\n"
                    f"• Welcome Gift: 50 Bonus Points Awarded\n\n"
                    f"Launching your gaming dashboard now...")
                self._succeed()
            else:
                err_lbl.config(text=msg)

        tk.Button(card, text="🌐 Link Google & Launch TFLY Arena (0% Data)",
                  command=do_link_google,
                  font=("Helvetica", 10, "bold"),
                  fg="#ffffff", bg="#2563eb",
                  activebackground="#1d4ed8",
                  relief="flat", cursor="hand2", width=42, pady=10).pack(anchor="w", pady=(4, 8))

        # Back to standard sign in
        back_f = tk.Frame(card, bg="#080e22")
        back_f.pack(pady=(8, 0))
        tk.Button(back_f, text="← Back to Standard Sign In",
                  command=self._show_login,
                  font=("Helvetica", 9, "bold"),
                  fg=COLORS["cyan"], bg="#080e22",
                  activeforeground=COLORS["cyan_dim"],
                  activebackground="#080e22",
                  relief="flat", cursor="hand2", bd=0).pack()

    # ──────────────────────────────────────────────────────────
    #  VIEW 3: STANDARD REGISTRATION
    # ──────────────────────────────────────────────────────────
    def _show_register(self):
        self._clear_right()
        if not self._right:
            return

        card = self._card()

        tk.Label(card, text="Create TFLY Account",
                 font=("Helvetica", 20, "bold"),
                 fg=COLORS["text"], bg="#080e22").pack(anchor="w")
        tk.Label(card, text="Join TFLY Esports Arena • 100% Free Registration",
                 font=("Helvetica", 9),
                 fg=COLORS["text_dim"], bg="#080e22").pack(anchor="w", pady=(2, 12))

        u_var    = tk.StringVar()
        em_var   = tk.StringVar()
        n_var    = tk.StringVar()
        ph_var   = tk.StringVar()
        p_var    = tk.StringVar()
        p2_var   = tk.StringVar()
        day_v    = tk.StringVar(value="1")
        mon_v    = tk.StringVar(value="Jan")
        yr_v     = tk.StringVar(value="2005")

        W = 380
        self._entry(card, "Desired Username", u_var, W)
        self._entry(card, "Email Address / Gmail", em_var, W)
        self._entry(card, "Full Name / Gamer Tag", n_var, W)
        self._entry(card, "Mobile Phone Number", ph_var, W)
        self._entry(card, "Password (min. 6 characters)", p_var, W, secret=True)
        self._entry(card, "Confirm Password", p2_var, W, secret=True)

        # Birthday dropdowns
        tk.Label(card, text="Date of Birth (Must be 15 or older)",
                 font=("Helvetica", 9, "bold"),
                 fg=COLORS["text_dim"], bg="#080e22").pack(anchor="w", pady=(2, 2))

        b_row = tk.Frame(card, bg="#080e22")
        b_row.pack(anchor="w", pady=(0, 10))

        now_yr = datetime.now().year
        for var, vals, w_cb in [
            (day_v, [str(d) for d in range(1, 32)], 5),
            (mon_v, MONTHS, 7),
            (yr_v,  [str(y) for y in range(now_yr - 15, 1940, -1)], 7)
        ]:
            wrap = tk.Frame(b_row, bg=COLORS["bg_input"],
                            highlightthickness=1, highlightbackground=COLORS["border"])
            wrap.pack(side="left", padx=(0, 8))
            cb = ttk.Combobox(wrap, textvariable=var, values=vals, state="readonly", width=w_cb)
            cb.pack(ipady=4, padx=4, pady=2)

        err_lbl = tk.Label(card, text="", font=("Helvetica", 9),
                           fg=COLORS["rose"], bg="#080e22", wraplength=W, justify="left")
        err_lbl.pack(anchor="w", pady=(0, 4))

        def do_register():
            u = u_var.get().strip()
            em = em_var.get().strip()
            name = n_var.get().strip()
            ph = ph_var.get().strip()
            p1 = p_var.get().strip()
            p2 = p2_var.get().strip()
            day = day_v.get()
            mon = mon_v.get()
            yr = yr_v.get()

            if not all([u, name, p1]):
                err_lbl.config(text="Please fill in all required fields."); return
            if p1 != p2:
                err_lbl.config(text="Passwords do not match."); return
            if len(p1) < 6:
                err_lbl.config(text="Password must be at least 6 characters."); return

            try:
                dob = date(int(yr), MONTH_NUM[mon], int(day))
                age = (date.today() - dob).days // 365
                if age < 15:
                    err_lbl.config(text=f"⚠️ Age Requirement: You must be at least 15 years old. (Current age: {age})")
                    return
            except Exception:
                err_lbl.config(text="Invalid date of birth selected."); return

            dob_str = f"{day} {mon} {yr}"
            ok, msg = AuthManager.register(
                username=u, password=p1, full_name=name, phone=ph,
                role="customer", dob=dob_str, email=em, auth_provider="local"
            )

            if ok:
                # Auto log in directly so user does not need to retype!
                AuthManager.login(u, p1)
                messagebox.showinfo(
                    "Welcome to TFLY! 🎮",
                    f"🎉 Account Successfully Created!\n\n"
                    f"Welcome, {name}!\n"
                    f"• Status: Fresh 0% Usage Data\n"
                    f"• 50 Welcome Points have been credited.\n\n"
                    f"Launching your gaming dashboard now...")
                self._succeed()
            else:
                err_lbl.config(text=msg)

        tk.Button(card, text="Create Account & Enter TFLY",
                  command=do_register,
                  font=("Helvetica", 10, "bold"),
                  fg="#ffffff", bg=COLORS["purple"],
                  activebackground="#7c3aed",
                  relief="flat", cursor="hand2", width=42, pady=10).pack(anchor="w", pady=(4, 6))

        # Bottom row
        bf = tk.Frame(card, bg="#080e22")
        bf.pack(pady=(6, 0))
        tk.Label(bf, text="Already have an account?  ", font=("Helvetica", 9),
                 fg=COLORS["text_muted"], bg="#080e22").pack(side="left")
        tk.Button(bf, text="Sign in", font=("Helvetica", 9, "bold"),
                  fg=COLORS["cyan"], bg="#080e22",
                  activeforeground=COLORS["cyan_dim"], activebackground="#080e22",
                  relief="flat", cursor="hand2", bd=0,
                  command=self._show_login).pack(side="left")

    # ──────────────────────────────────────────────────────────
    #  SUCCESS
    # ──────────────────────────────────────────────────────────
    def _succeed(self):
        self._anim = False
        if self._ok:
            self._ok()


# ──────────────────────────────────────────────────────────────
# COLOR HELPERS
# ──────────────────────────────────────────────────────────────
def _blend(c1, c2, t):
    try:
        r1, g1, b1 = int(c1[1:3], 16), int(c1[3:5], 16), int(c1[5:7], 16)
        r2, g2, b2 = int(c2[1:3], 16), int(c2[3:5], 16), int(c2[5:7], 16)
        r = int(r1 * t + r2 * (1 - t))
        g = int(g1 * t + g2 * (1 - t))
        b = int(b1 * t + b2 * (1 - t))
        return f"#{r:02x}{g:02x}{b:02x}"
    except Exception:
        return c1
