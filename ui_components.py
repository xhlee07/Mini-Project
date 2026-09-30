"""
TFLY Gaming Cafe Management System
UI Components & Design System
Premium Esports Aesthetic — Custom widgets, color palette, tab bars, cards.
"""

import tkinter as tk
from tkinter import ttk, messagebox
import os
import csv
from datetime import datetime
from PIL import Image, ImageTk, ImageDraw

# ============================================================
# ESPORTS COLOR PALETTE
# ============================================================
COLORS = {
    # Backgrounds
    'bg':           '#060b18',   # Deepest background
    'bg_card':      '#0d1526',   # Card surface
    'bg_input':     '#111e35',   # Input / table row bg
    'bg_hover':     '#162040',   # Hover state
    'bg_active':    '#0f1e3a',   # Active/selected

    # Sidebar
    'sidebar_bg':   '#080e1f',
    'sidebar_item': '#0c1428',
    'sidebar_hover':'#111e35',
    'sidebar_active':'#0a1c3e',

    # Accent colors
    'cyan':    '#22d3ee',   # Primary — neon cyan
    'cyan_dim':'#0891b2',   # Dimmed cyan
    'purple':  '#8b5cf6',   # Secondary — electric purple
    'green':   '#10b981',   # Success — emerald
    'amber':   '#f59e0b',   # Warning — gold
    'rose':    '#f43f5e',   # Danger — hot rose
    'blue':    '#3b82f6',   # Info — bright blue

    # Text
    'text':       '#e2e8f0',   # Primary text
    'text_dim':   '#94a3b8',   # Secondary text
    'text_muted': '#475569',   # Muted labels

    # Borders
    'border':      '#1e2d4a',
    'border_light':'#253352',
}

# Image cache (prevents Tkinter GC from killing photos)
_IMAGE_CACHE = {}

def get_cached_image(filepath, size=None):
    """Load, resize and cache a PhotoImage."""
    key = (filepath, size)
    if key in _IMAGE_CACHE:
        return _IMAGE_CACHE[key]
    if os.path.exists(filepath):
        try:
            img = Image.open(filepath)
            if size:
                img = img.resize(size, Image.Resampling.LANCZOS)
            photo = ImageTk.PhotoImage(img)
            _IMAGE_CACHE[key] = photo
            return photo
        except Exception as e:
            print(f"Image load error {filepath}: {e}")
    fallback = _make_placeholder(size or (64, 64))
    _IMAGE_CACHE[key] = fallback
    return fallback

def _make_placeholder(size):
    w, h = size
    img = Image.new('RGB', (w, h), color=(13, 21, 38))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, w-1, h-1], outline=(34, 211, 238), width=2)
    draw.line([4, 4, w-4, h-4], fill=(30, 45, 74), width=1)
    draw.line([4, h-4, w-4, 4], fill=(30, 45, 74), width=1)
    return ImageTk.PhotoImage(img)


# ============================================================
# CUSTOM TAB BAR — replaces ttkbootstrap Notebook
# ============================================================
class TabBar(tk.Frame):
    """
    Premium pill-style tab bar. Create tabs with add_tab(),
    then call build() to finalize. Switching tabs calls the
    supplied on_switch(key) callback and shows/hides the
    associated content frames.
    """
    def __init__(self, parent, on_switch=None, style="underline", **kwargs):
        super().__init__(parent, bg=COLORS['bg_card'], **kwargs)
        self._tabs = []          # list of (key, label, frame)
        self._buttons = {}       # key -> button widget
        self._active = None
        self._on_switch = on_switch
        self._style = style      # "pill" | "underline"
        self._bar_frame = tk.Frame(self, bg=COLORS['bg_card'])
        self._bar_frame.pack(fill=tk.X, side=tk.TOP)
        # Thin separator below bar
        tk.Frame(self, bg=COLORS['border'], height=1).pack(fill=tk.X)
        self._content_host = tk.Frame(self, bg=COLORS['bg_card'])
        self._content_host.pack(fill=tk.BOTH, expand=True)

    def add_tab(self, key, label, frame=None):
        """Register a tab. frame can be a pre-built tk.Frame child of content_host."""
        self._tabs.append((key, label, frame))

    @property
    def content_host(self):
        return self._content_host

    def build(self, default_key=None):
        """Build all tab buttons and activate the default (or first) tab."""
        for key, label, frame in self._tabs:
            btn = tk.Label(
                self._bar_frame,
                text=f"  {label}  ",
                font=("Helvetica", 9, "bold"),
                fg=COLORS['text_muted'],
                bg=COLORS['bg_card'],
                cursor="hand2",
                padx=6, pady=10,
            )
            btn.pack(side=tk.LEFT)
            btn.bind("<Button-1>", lambda e, k=key: self.switch(k))
            btn.bind("<Enter>", lambda e, b=btn, k=key: self._hover(b, k, True))
            btn.bind("<Leave>", lambda e, b=btn, k=key: self._hover(b, k, False))
            self._buttons[key] = btn

        first = default_key or (self._tabs[0][0] if self._tabs else None)
        if first:
            self.switch(first)

    def _hover(self, btn, key, entering):
        if key == self._active:
            return
        btn.config(fg=COLORS['text'] if entering else COLORS['text_muted'])

    def switch(self, key):
        prev = self._active
        self._active = key

        # Update button styles
        for k, _, _ in self._tabs:
            btn = self._buttons.get(k)
            if not btn:
                continue
            if k == key:
                if self._style == "pill":
                    btn.config(fg=COLORS['bg'], bg=COLORS['cyan'],
                               font=("Helvetica", 9, "bold"))
                else:  # underline
                    btn.config(fg=COLORS['cyan'], bg=COLORS['bg_card'],
                               font=("Helvetica", 9, "bold"),
                               relief="flat")
                    # Draw underline by adding a thin colored frame below btn
                    self._draw_underline(btn, active=True)
            else:
                if self._style == "pill":
                    btn.config(fg=COLORS['text_muted'], bg=COLORS['bg_card'],
                               font=("Helvetica", 9, "bold"))
                else:
                    btn.config(fg=COLORS['text_muted'], bg=COLORS['bg_card'],
                               font=("Helvetica", 9, "bold"),
                               relief="flat")
                    self._draw_underline(btn, active=False)

        # Show/hide frames
        for k, _, frame in self._tabs:
            if frame is None:
                continue
            if k == key:
                frame.pack(fill=tk.BOTH, expand=True)
            else:
                frame.pack_forget()

        if self._on_switch:
            self._on_switch(key)

    def _draw_underline(self, btn, active):
        # We simulate underline by adjusting border config
        if active:
            btn.config(bd=0, highlightthickness=2,
                       highlightbackground=COLORS['cyan'],
                       highlightcolor=COLORS['cyan'])
        else:
            btn.config(bd=0, highlightthickness=0)

    def select(self, key):
        self.switch(key)


# ============================================================
# STAT CARD — premium metric display
# ============================================================
class StatCard(tk.Frame):
    """
    Floating metric card with a glowing left border accent,
    large value label, icon, title, and subtitle.
    """
    def __init__(self, parent, title, value, icon="", accent=None, subtitle=""):
        if accent is None:
            accent = COLORS['cyan']
        elif accent in COLORS:
            accent = COLORS[accent]
        super().__init__(parent, bg=COLORS['bg_card'],
                         highlightthickness=1,
                         highlightbackground=COLORS['border'])
        self._accent = accent

        # Left accent bar
        bar = tk.Frame(self, bg=accent, width=3)
        bar.pack(side=tk.LEFT, fill=tk.Y)

        body = tk.Frame(self, bg=COLORS['bg_card'], padx=14, pady=10)
        body.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        top = tk.Frame(body, bg=COLORS['bg_card'])
        top.pack(fill=tk.X)

        tk.Label(top, text=title.upper(),
                 font=("Helvetica", 8, "bold"),
                 fg=COLORS['text_muted'],
                 bg=COLORS['bg_card']).pack(side=tk.LEFT)

        if icon:
            tk.Label(top, text=icon,
                     font=("Segoe UI Emoji", 12),
                     bg=COLORS['bg_card'], fg=accent).pack(side=tk.RIGHT)

        self.val_lbl = tk.Label(body, text=str(value),
                                font=("Helvetica", 18, "bold"),
                                fg=COLORS['text'],
                                bg=COLORS['bg_card'])
        self.val_lbl.pack(anchor=tk.W, pady=(2, 0))

        if subtitle:
            tk.Label(body, text=subtitle,
                     font=("Helvetica", 8),
                     fg=COLORS['text_muted'],
                     bg=COLORS['bg_card']).pack(anchor=tk.W)

    def set_value(self, val):
        self.val_lbl.config(text=str(val))



# ============================================================
# STATUS BADGE
# ============================================================
class StatusBadge(tk.Label):
    """Pill-shaped color status label."""
    _MAP = {
        'Available':  (COLORS['green'],  '#0d2b1f'),
        'Open':       (COLORS['green'],  '#0d2b1f'),
        'Paid':       (COLORS['green'],  '#0d2b1f'),
        'Delivered':  (COLORS['green'],  '#0d2b1f'),
        'Occupied':   (COLORS['cyan'],   '#0a1e2e'),
        'Ongoing':    (COLORS['cyan'],   '#0a1e2e'),
        'Delivering': (COLORS['blue'],   '#0a172e'),
        'Reserved':   (COLORS['amber'],  '#2b2000'),
        'Pending':    (COLORS['amber'],  '#2b2000'),
        'Maintenance':(COLORS['rose'],   '#2b0a12'),
        'Cancelled':  (COLORS['rose'],   '#2b0a12'),
        'Closed':     (COLORS['rose'],   '#2b0a12'),
    }

    def __init__(self, parent, status, **kwargs):
        fg, bg = self._MAP.get(status, (COLORS['text_muted'], COLORS['bg_input']))
        super().__init__(parent,
                         text=f"  {status}  ",
                         font=("Helvetica", 8, "bold"),
                         fg=fg, bg=bg,
                         padx=2, pady=2, **kwargs)


# ============================================================
# STYLED TREEVIEW helper
# ============================================================
def styled_treeview(parent, columns, show="headings", height=14):
    """Create a dark-themed ttk.Treeview with proper styles."""
    style = ttk.Style()
    style.configure("Dark.Treeview",
                    background=COLORS['bg_input'],
                    foreground=COLORS['text'],
                    fieldbackground=COLORS['bg_input'],
                    rowheight=30,
                    borderwidth=0)
    style.configure("Dark.Treeview.Heading",
                    background=COLORS['bg_card'],
                    foreground=COLORS['cyan'],
                    font=("Helvetica", 9, "bold"),
                    relief="flat")
    style.map("Dark.Treeview",
              background=[("selected", COLORS['bg_active'])],
              foreground=[("selected", COLORS['cyan'])])

    frame = tk.Frame(parent, bg=COLORS['border'], bd=1)
    scrollbar = ttk.Scrollbar(frame, orient=tk.VERTICAL)
    tree = ttk.Treeview(frame, columns=columns, show=show,
                        height=height, style="Dark.Treeview",
                        yscrollcommand=scrollbar.set)
    scrollbar.config(command=tree.yview)
    tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
    scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
    return frame, tree


# ============================================================
# CSV EXPORT
# ============================================================
def export_table_to_csv(headers, rows, default_filename="tfly_export.csv"):
    try:
        from tkinter import filedialog
        filepath = filedialog.asksaveasfilename(
            defaultextension=".csv",
            filetypes=[("CSV Files", "*.csv"), ("All Files", "*.*")],
            initialfile=default_filename)
        if not filepath:
            return False
        with open(filepath, mode='w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow(headers)
            for row in rows:
                writer.writerow(row)
        messagebox.showinfo("Export OK", f"Saved to:\n{filepath}")
        return True
    except Exception as e:
        messagebox.showerror("Export Failed", str(e))
        return False


# ============================================================
# RECEIPT DIALOG
# ============================================================
def show_receipt_dialog(parent, bill_data, items_breakdown=None):
    win = tk.Toplevel(parent)
    win.title("TFLY Official Receipt")
    win.geometry("460x700")
    win.resizable(False, False)
    win.configure(bg=COLORS['bg_card'])
    win.transient(parent)
    win.grab_set()

    container = tk.Frame(win, bg=COLORS['bg_card'], padx=24, pady=20)
    container.pack(fill=tk.BOTH, expand=True)

    # Receipt paper
    paper = tk.Frame(container, bg='#ffffff', padx=20, pady=20)
    paper.pack(fill=tk.BOTH, expand=True)

    def row(text, color='#111111', bold=False, size=9, center=False):
        f = ("Courier New", size, "bold") if bold else ("Courier New", size)
        lbl = tk.Label(paper, text=text, font=f, fg=color, bg='#ffffff',
                       justify=tk.CENTER if center else tk.LEFT,
                       anchor=tk.CENTER if center else tk.W)
        lbl.pack(fill=tk.X)
        return lbl

    row("⚡  TFLY GAMING CAFE  ⚡", '#0891b2', bold=True, size=13, center=True)
    row("Cyberpunk Esports Arena & Lounge", '#666666', size=8, center=True)
    row("Lot 4.02, Level 4, Sunway Velocity Mall, KL", '#666666', size=8, center=True)
    row("Tel: +603-9988 7766  |  Reg: TFLY-2026-X", '#666666', size=8, center=True)
    row("=" * 46, '#cccccc')
    row(f"Bill No  : {bill_data.get('bill_number', 'INV-N/A')}", '#333333')
    row(f"Customer : {bill_data.get('customer_name', 'Guest')}", '#333333')
    row(f"Date     : {bill_data.get('created_at', datetime.now().strftime('%Y-%m-%d %H:%M'))}", '#333333')
    row(f"Payment  : {bill_data.get('payment_method', 'Cash')}", '#333333')
    row("-" * 46, '#cccccc')

    row(f"{'ITEM':<28}{'AMOUNT':>12}", '#333333', bold=True)
    row("-" * 46, '#cccccc')

    if items_breakdown:
        for name, amt in items_breakdown:
            row(f"{name[:26]:<28}RM {amt:>8.2f}", '#222222')
    else:
        if bill_data.get('station_cost', 0) > 0:
            row(f"{'PC Gaming Pass':<28}RM {bill_data['station_cost']:>8.2f}", '#222222')
        if bill_data.get('snack_cost', 0) > 0:
            row(f"{'F&B Orders':<28}RM {bill_data['snack_cost']:>8.2f}", '#222222')
        if bill_data.get('event_cost', 0) > 0:
            row(f"{'Tournament Entry':<28}RM {bill_data['event_cost']:>8.2f}", '#222222')

    row("=" * 46, '#cccccc')
    subtotal = bill_data.get('subtotal', bill_data.get('total_amount', 0.0))
    discount = bill_data.get('discount_amount', 0.0)
    final_total = bill_data.get('total_amount', 0.0)
    pts = bill_data.get('points_earned', int(final_total))

    row(f"{'Subtotal':<28}RM {subtotal:>8.2f}", '#333333')
    if discount > 0:
        row(f"{'Loyalty Discount':<28}-RM {discount:>7.2f}", '#059669')
    row(f"{'TOTAL PAID':<28}RM {final_total:>8.2f}", '#0891b2', bold=True, size=11)
    row(f"{'Points Credited':<28}+{pts:>10} pts", '#d97706')
    row("=" * 46, '#cccccc')
    row("THANK YOU FOR GAMING AT TFLY!", '#333333', bold=True, center=True)
    row("GGWP  —  Visit Again Soon!", '#666666', size=8, center=True)

    btn_frame = tk.Frame(container, bg=COLORS['bg_card'])
    btn_frame.pack(fill=tk.X, pady=(14, 0))

    def on_print():
        messagebox.showinfo("Receipt Printer", "Print job dispatched to EPSON TM-T88VI!")
        win.destroy()

    _btn(btn_frame, "Print Receipt", on_print, COLORS['cyan'], expand=True).pack(
        side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 6))
    _btn(btn_frame, "Close", win.destroy, COLORS['text_muted'], expand=True).pack(
        side=tk.LEFT, fill=tk.X, expand=True)


def _btn(parent, text, cmd, color=None, expand=False, **kw):
    """Quick styled button factory."""
    c = color or COLORS['cyan']
    b = tk.Button(parent, text=text, command=cmd,
                  font=("Helvetica", 9, "bold"),
                  fg='#ffffff' if c != COLORS['text_muted'] else COLORS['text'],
                  bg=c,
                  activebackground=COLORS['bg_hover'],
                  activeforeground='#ffffff',
                  relief="flat", cursor="hand2",
                  padx=14, pady=8, **kw)
    return b
