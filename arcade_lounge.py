"""
TFLY Gaming Café Management System
Feature: Cyber Arcade & Entertainment Lounge
Sub-Modules:
- Cyber Lucky Spin (Animated Gacha Prize Wheel with physics deceleration)
- Daily Gamer Quests & Challenges (Daily missions with real reward points claim)
- Arena Jukebox & 16-Band Equalizer (Ambient stream selector & live RGB visualizer)
"""

import tkinter as tk
from tkinter import ttk, messagebox
import ttkbootstrap as tb
from ttkbootstrap.constants import *
import math
import random
from datetime import datetime

from database import get_connection
from auth import AuthManager
from ui_components import COLORS, StatCard, StatusBadge, TabBar


# Lucky Wheel Prize Segments
PRIZES = [
    {"label": "JACKPOT 500 PTS", "color": "#fbbf24", "text_col": "#000000", "type": "points", "val": 500},
    {"label": "Free 1h PC Pass", "color": "#00f2fe", "text_col": "#000000", "type": "pass", "val": 1},
    {"label": "Monster Energy", "color": "#10b981", "text_col": "#000000", "type": "drink", "val": 1},
    {"label": "+50 Loyalty Pts", "color": "#8a2be2", "text_col": "#ffffff", "type": "points", "val": 50},
    {"label": "Shin Ramyun Bowl", "color": "#f97316", "text_col": "#000000", "type": "food", "val": 1},
    {"label": "+20 Loyalty Pts", "color": "#3b82f6", "text_col": "#ffffff", "type": "points", "val": 20},
    {"label": "15% F&B Voucher", "color": "#ec4899", "text_col": "#ffffff", "type": "voucher", "val": 15},
    {"label": "VIP Pass 1-Day", "color": "#a855f7", "text_col": "#ffffff", "type": "vip", "val": 1},
]

class ArcadeLoungeFrame(tb.Frame):
    def __init__(self, parent):
        super().__init__(parent)
        self.wheel_angle = 0
        self.is_spinning = False
        self.spin_speed = 0
        self.visualizer_running = False
        self.viz_timer = None
        self.audio_bars = [random.randint(10, 80) for _ in range(16)]

        self.setup_ui()
        self.refresh_quests()
        self.start_audio_visualizer()
        self.bind("<Destroy>", self._on_destroy)

    def _on_destroy(self, event):
        if event.widget == self:
            self.visualizer_running = False
            if self.viz_timer:
                try:
                    self.after_cancel(self.viz_timer)
                except Exception:
                    pass


    def setup_ui(self):
        # 1. Top Banner
        top_frame = tb.Frame(self)
        top_frame.pack(fill=X, padx=15, pady=(15, 10))

        self.card_spins = StatCard(top_frame, "Daily Lucky Spins", "Active", "🎰", COLORS['amber'], "Spin & Win Daily Prizes")
        self.card_spins.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_quests = StatCard(top_frame, "Available Quests", "4", "🎯", COLORS['cyan'], "Earn Bonus Café Points")
        self.card_quests.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_jukebox = StatCard(top_frame, "Arena Ambience", "Cyber Synthwave", "🎵", COLORS['purple'], "Live Café Audio Stream")
        self.card_jukebox.pack(side=LEFT, fill=X, expand=True, padx=5)

        # 2. Sleek TabBar
        self.tabbar = TabBar(self, style="underline")
        self.tabbar.pack(fill=BOTH, expand=True)

        self.tab_wheel = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_quests = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_jukebox = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)

        self.tabbar.add_tab("wheel", "🎰  Lucky Spin Wheel", self.tab_wheel)
        self.tabbar.add_tab("quests", "🎯  Daily Gamer Quests", self.tab_quests)
        self.tabbar.add_tab("jukebox", "🎵  Arena Jukebox & Equalizer", self.tab_jukebox)

        self.setup_lucky_wheel()
        self.setup_quest_hub()
        self.setup_jukebox()

        self.tabbar.build(default_key="wheel")
        self.notebook = self.tabbar


    # ========================================================
    # CYBER LUCKY WHEEL
    # ========================================================
    def setup_lucky_wheel(self):
        container = self.tab_wheel
        paned = ttk.PanedWindow(container, orient=HORIZONTAL)
        paned.pack(fill=BOTH, expand=True)

        wheel_box = tb.Frame(paned, padding=10)
        paned.add(wheel_box, weight=3)

        tb.Label(wheel_box, text="🎰 Cyber Lucky Prize Wheel", font=("Helvetica", 14, "bold"), bootstyle="warning").pack(anchor=W)
        tb.Label(wheel_box, text="Test your gaming luck! Every spin guarantees a prize credited to your account.", 
                 font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 10))

        self.wheel_canvas = tk.Canvas(wheel_box, width=380, height=380, bg=COLORS['bg'], highlightthickness=0)
        self.wheel_canvas.pack(pady=10)

        self.spin_btn = tb.Button(wheel_box, text="🎲 SPIN THE WHEEL NOW! 🎲", bootstyle="warning", 
                                  command=self.spin_wheel, width=28)
        self.spin_btn.pack(pady=5)

        self.draw_wheel(0)

        history_box = tb.Frame(paned, bootstyle="dark", padding=15)
        paned.add(history_box, weight=2)

        tb.Label(history_box, text="🎁 Wheel Prize Pool", font=("Helvetica", 12, "bold"), bootstyle="info").pack(anchor=W)
        
        for p in PRIZES:
            p_row = tb.Frame(history_box)
            p_row.pack(fill=X, pady=2)
            tk.Frame(p_row, bg=p['color'], width=14, height=14).pack(side=LEFT, padx=(0, 8))
            tb.Label(p_row, text=p['label'], font=("Helvetica", 9, "bold")).pack(side=LEFT)

        tb.Separator(history_box).pack(fill=X, pady=12)
        tb.Label(history_box, text="📜 Recent Lucky Winners", font=("Helvetica", 11, "bold"), bootstyle="light").pack(anchor=W)

        self.history_tree = ttk.Treeview(history_box, columns=("Time", "Winner", "Prize"), show="headings", height=6)
        self.history_tree.heading("Time", text="Time")
        self.history_tree.heading("Winner", text="Player")
        self.history_tree.heading("Prize", text="Won")

        self.history_tree.column("Time", width=70)
        self.history_tree.column("Winner", width=90)
        self.history_tree.column("Prize", width=120)
        self.history_tree.pack(fill=BOTH, expand=True, pady=5)
        self.load_spin_history()

    def draw_wheel(self, angle_deg):
        canvas = self.wheel_canvas
        canvas.delete("all")

        cx, cy, r = 190, 190, 160
        num_slices = len(PRIZES)
        slice_angle = 360 / num_slices

        canvas.create_oval(cx - r - 8, cy - r - 8, cx + r + 8, cy + r + 8, outline=COLORS['cyan'], width=4)
        canvas.create_oval(cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2, outline=COLORS['purple'], width=2)

        for i, p in enumerate(PRIZES):
            start = (angle_deg + i * slice_angle) % 360
            canvas.create_arc(cx - r, cy - r, cx + r, cy + r, start=start, extent=slice_angle, fill=p['color'], outline="#1e293b", width=2)

            mid_rad = math.radians(start + slice_angle / 2)
            tx = cx + (r * 0.65) * math.cos(mid_rad)
            ty = cy - (r * 0.65) * math.sin(mid_rad)
            short_label = p['label'].replace("Loyalty ", "")
            canvas.create_text(tx, ty, text=short_label, fill=p['text_col'], font=("Helvetica", 8, "bold"))

        canvas.create_oval(cx - 28, cy - 28, cx + 28, cy + 28, fill="#0f172a", outline=COLORS['amber'], width=3)
        canvas.create_text(cx, cy, text="TFLY", fill=COLORS['amber'], font=("Helvetica", 9, "bold"))

        canvas.create_polygon(cx, cy - r - 12, cx - 14, cy - r - 28, cx + 14, cy - r - 28, fill=COLORS['rose'], outline="#ffffff", width=2)

    def spin_wheel(self):
        if self.is_spinning:
            return

        self.is_spinning = True
        self.spin_btn.config(state="disabled")
        self.spin_speed = random.uniform(35, 50)
        self.animate_wheel()

    def animate_wheel(self):
        if not self.winfo_exists():
            return
        if self.spin_speed > 0.5:
            self.wheel_angle = (self.wheel_angle + self.spin_speed) % 360
            self.spin_speed *= 0.965
            self.draw_wheel(self.wheel_angle)
            self.after(25, self.animate_wheel)
        else:
            self.is_spinning = False
            self.spin_btn.config(state="normal")
            self.finish_spin()

    def finish_spin(self):
        num_slices = len(PRIZES)
        slice_angle = 360 / num_slices
        
        pointer_angle = (90 - self.wheel_angle) % 360
        winning_idx = int(pointer_angle // slice_angle) % num_slices
        won = PRIZES[winning_idx]

        user = AuthManager.get_current_user()
        user_id = user['id'] if user else 3
        user_name = user['full_name'] if user else "Kenji Sato"

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("""
            INSERT INTO wheel_spins (user_id, prize_name, prize_value)
            VALUES (?, ?, ?)
        """, (user_id, won['label'], str(won['val'])))

        if won['type'] == 'points' and user and user.get('member_id'):
            cur.execute("UPDATE members SET points = points + ? WHERE id = ?", (won['val'], user['member_id']))
            cur.execute("""
                INSERT INTO points_transactions (member_id, points_change, reason)
                VALUES (?, ?, ?)
            """, (user['member_id'], won['val'], f"Won on Cyber Lucky Wheel: {won['label']}"))

        conn.commit()
        conn.close()

        messagebox.showinfo("🎉 JACKPOT WINNER! 🎉", 
                            f"Congratulations {user_name}!\n\nYou landed on:\n⭐ {won['label']} ⭐\n\nReward credited to your café account!")
        self.load_spin_history()

    def load_spin_history(self):
        for item in self.history_tree.get_children():
            self.history_tree.delete(item)

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("""
            SELECT ws.spun_at, u.full_name, ws.prize_name
            FROM wheel_spins ws
            JOIN users u ON ws.user_id = u.id
            ORDER BY ws.id DESC LIMIT 10
        """)
        rows = cur.fetchall()
        conn.close()

        for r in rows:
            self.history_tree.insert("", "end", values=(r['spun_at'][11:16], r['full_name'][:12], r['prize_name']))

    # ========================================================
    # GAMER QUEST HUB
    # ========================================================
    def setup_quest_hub(self):
        container = self.tab_quests

        tb.Label(container, text="🎯 Daily Gamer Challenge Center", font=("Helvetica", 14, "bold"), bootstyle="info").pack(anchor=W)
        tb.Label(container, text="Complete café missions every day to level up your VIP points faster!", font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        self.quests_frame = tb.Frame(container)
        self.quests_frame.pack(fill=BOTH, expand=True)

    def refresh_quests(self):
        for widget in self.quests_frame.winfo_children():
            widget.destroy()

        user = AuthManager.get_current_user()
        user_id = user['id'] if user else 3

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("""
            SELECT q.*, COALESCE(uq.progress, 0) as progress, COALESCE(uq.is_completed, 0) as is_completed, COALESCE(uq.is_claimed, 0) as is_claimed, uq.id as uq_id
            FROM quests q
            LEFT JOIN user_quests uq ON q.id = uq.quest_id AND uq.user_id = ?
            ORDER BY q.id
        """, (user_id,))
        quests = [dict(r) for r in cur.fetchall()]
        conn.close()

        for q in quests:
            self.create_quest_card(self.quests_frame, q, user_id)

    def create_quest_card(self, parent, quest, user_id):
        card = tb.Frame(parent, bootstyle="dark", padding=15)
        card.pack(fill=X, pady=6)

        left = tb.Frame(card)
        left.pack(side=LEFT, fill=X, expand=True)

        title_box = tb.Frame(left)
        title_box.pack(fill=X)
        tb.Label(title_box, text=quest['icon'], font=("Helvetica", 14)).pack(side=LEFT, padx=(0, 6))
        tb.Label(title_box, text=quest['title'], font=("Helvetica", 11, "bold"), bootstyle="light").pack(side=LEFT)
        tb.Label(title_box, text=f"+{quest['reward_points']} Pts", font=("Helvetica", 9, "bold"), bootstyle="warning").pack(side=LEFT, padx=10)

        tb.Label(left, text=quest['description'], font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(2, 6))

        curr = quest['progress']
        target = quest['target_count']
        ratio = min(1.0, curr / target)
        prog_bar = tb.Progressbar(left, value=ratio * 100, bootstyle="info", length=260)
        prog_bar.pack(anchor=W)

        tb.Label(left, text=f"Progress: {curr}/{target}", font=("Helvetica", 8), bootstyle="secondary").pack(anchor=W, pady=(2, 0))

        right = tb.Frame(card)
        right.pack(side=RIGHT, padx=10)

        if quest['is_claimed']:
            tb.Label(right, text="✅ Claimed", font=("Helvetica", 10, "bold"), bootstyle="success").pack()
        elif curr >= target or quest['is_completed']:
            tb.Button(right, text="🎉 Claim Reward", bootstyle="success", 
                      command=lambda q=quest: self.claim_quest_reward(q, user_id)).pack()
        else:
            tb.Button(right, text="In Progress...", bootstyle="secondary-outline", state="disabled").pack()

    def claim_quest_reward(self, quest, user_id):
        conn = get_connection()
        cur = conn.cursor()

        if quest.get('uq_id'):
            cur.execute("UPDATE user_quests SET is_claimed = 1, is_completed = 1 WHERE id = ?", (quest['uq_id'],))
        else:
            cur.execute("INSERT INTO user_quests (user_id, quest_id, progress, is_completed, is_claimed) VALUES (?, ?, ?, 1, 1)",
                        (user_id, quest['id'], quest['target_count']))

        cur.execute("SELECT id FROM members WHERE user_id = ?", (user_id,))
        m = cur.fetchone()
        if m:
            cur.execute("UPDATE members SET points = points + ? WHERE id = ?", (quest['reward_points'], m['id']))
            cur.execute("INSERT INTO points_transactions (member_id, points_change, reason) VALUES (?, ?, ?)",
                        (m['id'], quest['reward_points'], f"Completed Quest: {quest['title']}"))

        conn.commit()
        conn.close()

        messagebox.showinfo("Quest Completed!", f"⭐ Earned +{quest['reward_points']} Loyalty Points for '{quest['title']}'!")
        self.refresh_quests()

    # ========================================================
    # ARENA JUKEBOX & EQUALIZER
    # ========================================================
    def setup_jukebox(self):
        container = self.tab_jukebox

        paned = ttk.PanedWindow(container, orient=HORIZONTAL)
        paned.pack(fill=BOTH, expand=True)

        left = tb.Frame(paned, padding=10)
        paned.add(left, weight=2)

        tb.Label(left, text="🎵 Arena Jukebox & Atmosphere", font=("Helvetica", 14, "bold"), bootstyle="info").pack(anchor=W)
        tb.Label(left, text="Control background ambient tracks & visual audio vibe", font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        stations = [
            ("🌃 Night City Synthwave 2077", "128 BPM | Cyberpunk Electronic"),
            ("🎧 Lo-Fi Rainy Chill Beats", "85 BPM | Cozy Focus Lo-Fi"),
            ("🔥 VCT Champions Hype Trap", "145 BPM | Bass Boosted Esports"),
            ("🕹️ Retro Tokyo 8-Bit Arcade", "130 BPM | Chiptune Nostalgia"),
        ]

        self.music_choice_var = tk.StringVar(value=stations[0][0])
        for title, subtitle in stations:
            f = tb.Frame(left, bootstyle="dark", padding=8)
            f.pack(fill=X, pady=4)
            tb.Radiobutton(f, text=title, variable=self.music_choice_var, value=title).pack(anchor=W)
            tb.Label(f, text=subtitle, font=("Helvetica", 8), bootstyle="secondary").pack(anchor=W, padx=22)

        ctrl = tb.Frame(left)
        ctrl.pack(fill=X, pady=15)
        self.play_btn = tb.Button(ctrl, text="⏸️ Pause Jukebox", bootstyle="info", command=self.toggle_play)
        self.play_btn.pack(side=LEFT, padx=3)

        tb.Label(left, text="Arena Master Volume: 75%").pack(anchor=W, pady=(10, 2))
        vol = tb.Scale(left, from_=0, to=100, value=75, bootstyle="info")
        vol.pack(fill=X)

        right = tb.Frame(paned, bootstyle="dark", padding=15)
        paned.add(right, weight=3)

        tb.Label(right, text="📊 Live 16-Band RGB Audio Visualizer", font=("Helvetica", 12, "bold"), bootstyle="warning").pack(anchor=W)
        tb.Label(right, text="Real-time frequency simulation", font=("Helvetica", 8), bootstyle="secondary").pack(anchor=W, pady=(0, 10))

        self.viz_canvas = tk.Canvas(right, bg=COLORS['bg'], height=240, highlightthickness=1, highlightbackground=COLORS['border'])
        self.viz_canvas.pack(fill=BOTH, expand=True)

    def toggle_play(self):
        self.visualizer_running = not self.visualizer_running
        if self.visualizer_running:
            self.play_btn.config(text="⏸️ Pause Jukebox", bootstyle="info")
        else:
            self.play_btn.config(text="▶️ Play Music", bootstyle="success")

    def start_audio_visualizer(self):
        self.visualizer_running = True
        self.update_visualizer_bars()

    def update_visualizer_bars(self):
        if not self.winfo_exists():
            return
        canvas = self.viz_canvas
        canvas.delete("all")

        w = canvas.winfo_width() or 360
        h = canvas.winfo_height() or 220

        num_bars = len(self.audio_bars)
        bar_w = (w - (num_bars * 6)) / num_bars

        bar_colors = ["#00f2fe", "#00b4d8", "#38bdf8", "#818cf8", "#8a2be2", "#c084fc", "#e879f9", "#f43f5e",
                      "#f43f5e", "#e879f9", "#c084fc", "#8a2be2", "#818cf8", "#38bdf8", "#00b4d8", "#00f2fe"]

        for i in range(num_bars):
            if self.visualizer_running:
                delta = random.randint(-15, 15)
                self.audio_bars[i] = max(10, min(h - 20, self.audio_bars[i] + delta))
            else:
                self.audio_bars[i] = max(6, self.audio_bars[i] - 4)

            bar_h = self.audio_bars[i]
            x1 = 15 + i * (bar_w + 6)
            y1 = h - 15 - bar_h
            x2 = x1 + bar_w
            y2 = h - 15

            col = bar_colors[i % len(bar_colors)]
            canvas.create_rectangle(x1, y1, x2, y2, fill=col, outline=col)
            canvas.create_rectangle(x1, y1 - 4, x2, y1 - 2, fill="#ffffff", outline="#ffffff")

        canvas.create_line(10, h - 15, w - 10, h - 15, fill="#475569", width=2)
        if self.visualizer_running:
            self.viz_timer = self.after(60, self.update_visualizer_bars)





