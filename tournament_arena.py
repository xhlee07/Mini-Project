"""
TFLY Gaming Café Management System
Feature: Esports Tournaments & Arena
Sub-Modules:
- Tournament Events & Scheduling (Full Event CRUD)
- Squad & Team Registrations (Full Team CRUD & slot limit validation)
- Interactive Single-Elimination Bracket Tree (Canvas tree, live score updates, winner progression)
"""

import tkinter as tk
from tkinter import ttk, messagebox
import ttkbootstrap as tb
from ttkbootstrap.constants import *
from datetime import datetime

from database import get_connection
from auth import AuthManager
from ui_components import COLORS, StatCard, StatusBadge, TabBar, styled_treeview, export_table_to_csv

class TournamentManagementFrame(tb.Frame):
    def __init__(self, parent):
        super().__init__(parent)
        self.selected_event_id = None
        self.events = []
        self.matches = []
        self.teams = []

        self.setup_ui()
        self.refresh_events()

    def setup_ui(self):
        # 1. Top Metrics Banner
        metrics_frame = tb.Frame(self)
        metrics_frame.pack(fill=X, padx=15, pady=(15, 10))

        self.card_events_count = StatCard(metrics_frame, "Active Events", "0", "🏆", COLORS['cyan'], "Tournaments on Schedule")
        self.card_events_count.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_prize_pool = StatCard(metrics_frame, "Total Prize Pool", "RM 0.00", "💎", COLORS['amber'], "Cash & Hardware Rewards")
        self.card_prize_pool.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_teams_count = StatCard(metrics_frame, "Registered Teams", "0", "👥", COLORS['purple'], "Esports Competitors")
        self.card_teams_count.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.card_entry_revenue = StatCard(metrics_frame, "Entry Fees Collected", "RM 0.00", "🎟️", COLORS['green'], "Tournament Pool Revenue")
        self.card_entry_revenue.pack(side=LEFT, fill=X, expand=True, padx=5)

        self.tabbar = TabBar(self, style="underline")
        self.tabbar.pack(fill=BOTH, expand=True)

        self.tab_schedule = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_teams    = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=15, pady=15)
        self.tab_bracket  = tk.Frame(self.tabbar.content_host, bg=COLORS['bg'], padx=10, pady=10)

        self.tabbar.add_tab("schedule", "Events & Scheduling",      self.tab_schedule)
        self.tabbar.add_tab("teams",    "Team Registrations",       self.tab_teams)
        self.tabbar.add_tab("bracket",  "Interactive Bracket",      self.tab_bracket)

        self.setup_submodule_schedule()
        self.setup_submodule_teams()
        self.setup_submodule_bracket()

        self.tabbar.build(default_key="schedule")
        self.notebook = self.tabbar

    # ========================================================
    # TOURNAMENT EVENTS & SCHEDULING (CRUD)
    # ========================================================
    def setup_submodule_schedule(self):
        container = self.tab_schedule

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        tb.Label(top, text="🏆 Esports Tournament Calendar & Schedules (CRUD)", font=("Helvetica", 13, "bold"), bootstyle="info").pack(side=LEFT)
        
        if AuthManager.is_staff():
            tb.Button(top, text="➕ Host New Event", bootstyle="success", command=self.open_add_event_modal).pack(side=RIGHT, padx=4)
        tb.Button(top, text="📥 Export Events CSV", bootstyle="info-outline", command=self.export_events_csv).pack(side=RIGHT, padx=4)

        # Table
        event_cols = ("ID", "Game Title", "Tournament Title", "Date & Time", "Status", "Slots (Teams)", "Entry Fee", "Prize Pool")
        self.events_tree = ttk.Treeview(container, columns=event_cols, show="headings", height=12)
        for c in event_cols:
            self.events_tree.heading(c, text=c)

        self.events_tree.column("ID", width=40, anchor="center")
        self.events_tree.column("Game Title", width=120)
        self.events_tree.column("Tournament Title", width=220)
        self.events_tree.column("Date & Time", width=130)
        self.events_tree.column("Status", width=90, anchor="center")
        self.events_tree.column("Slots (Teams)", width=90, anchor="center")
        self.events_tree.column("Entry Fee", width=90, anchor="e")
        self.events_tree.column("Prize Pool", width=100, anchor="e")
        self.events_tree.pack(fill=BOTH, expand=True)
        self.events_tree.bind("<<TreeviewSelect>>", self.on_event_select)

        act_box = tb.Frame(container)
        act_box.pack(fill=X, pady=8)

        tb.Button(act_box, text="📝 Register Squad for Selected", bootstyle="info", command=self.open_register_team_modal).pack(side=LEFT, padx=3)
        tb.Button(act_box, text="⚔️ View Bracket", bootstyle="warning", command=lambda: self.tabbar.select("bracket")).pack(side=LEFT, padx=3)
        if AuthManager.is_staff():
            tb.Button(act_box, text="✏️ Edit Event Details", bootstyle="secondary-outline", command=self.open_edit_event_modal).pack(side=LEFT, padx=3)
            tb.Button(act_box, text="🗑️ Delete Tournament", bootstyle="danger-outline", command=self.delete_event).pack(side=LEFT, padx=3)

    def export_events_csv(self):
        headers = ["ID", "Game Title", "Tournament Title", "Date", "Status", "Max Teams", "Entry Fee (RM)", "Prize Pool (RM)"]
        rows = [[e['id'], e['game_title'], e['title'], e['event_date'], e['status'], e['max_teams'], e['entry_fee'], e['prize_pool']] for e in self.events]
        export_table_to_csv(headers, rows, "tfly_tournaments_schedule.csv")

    # ========================================================
    # SQUAD & TEAM REGISTRATIONS (CRUD)
    # ========================================================
    def setup_submodule_teams(self):
        container = self.tab_teams

        top = tb.Frame(container)
        top.pack(fill=X, pady=(0, 10))

        self.team_header_lbl = tb.Label(top, text="👥 Registered Squads & Competitor Rosters", font=("Helvetica", 13, "bold"), bootstyle="info")
        self.team_header_lbl.pack(side=LEFT)

        tb.Button(top, text="📝 Register Squad", bootstyle="success", command=self.open_register_team_modal).pack(side=RIGHT, padx=4)
        tb.Button(top, text="📥 Export Teams CSV", bootstyle="info-outline", command=self.export_teams_csv).pack(side=RIGHT, padx=4)

        # Teams Table
        team_cols = ("Seed", "Team / Clan Name", "Team Leader / Captain", "Contact Phone", "Registered Date")
        self.teams_tree = ttk.Treeview(container, columns=team_cols, show="headings", height=12)
        for c in team_cols:
            self.teams_tree.heading(c, text=c)

        self.teams_tree.column("Seed", width=60, anchor="center")
        self.teams_tree.column("Team / Clan Name", width=220)
        self.teams_tree.column("Team Leader / Captain", width=160)
        self.teams_tree.column("Contact Phone", width=130)
        self.teams_tree.column("Registered Date", width=140)
        self.teams_tree.pack(fill=BOTH, expand=True)

        act_box = tb.Frame(container)
        act_box.pack(fill=X, pady=8)
        if AuthManager.is_staff():
            tb.Button(act_box, text="🗑️ Remove Team Registration", bootstyle="danger-outline", command=self.delete_selected_team).pack(side=LEFT)

    def delete_selected_team(self):
        sel = self.teams_tree.selection()
        if not sel:
            messagebox.showinfo("Select Team", "Please select a registered team to remove.")
            return
        vals = self.teams_tree.item(sel[0])['values']
        team_name = vals[1]

        if messagebox.askyesno("Confirm Removal", f"Remove team '{team_name}' from tournament?"):
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("DELETE FROM tournament_teams WHERE event_id = ? AND team_name = ?", (self.selected_event_id, team_name))
            conn.commit()
            conn.close()
            self.load_event_details(self.selected_event_id)
            self.refresh_events()

    def export_teams_csv(self):
        headers = ["Seed", "Team Name", "Captain", "Contact", "Registered At"]
        rows = [[t['seed_number'], t['team_name'], t['leader_name'], t['leader_phone'], t['registered_at']] for t in self.teams]
        export_table_to_csv(headers, rows, "tfly_tournament_teams.csv")

    # ========================================================
    # BRACKET & SCOREBOARD
    # ========================================================
    def setup_submodule_bracket(self):
        container = self.tab_bracket

        bracket_header = tb.Frame(container)
        bracket_header.pack(fill=X, pady=(0, 10))

        self.bracket_title_lbl = tb.Label(bracket_header, text="⚔️ Interactive Single-Elimination Bracket Tree", font=("Helvetica", 13, "bold"), bootstyle="warning")
        self.bracket_title_lbl.pack(side=LEFT)

        if AuthManager.is_staff():
            tb.Button(bracket_header, text="⚙️ Update Match Score", bootstyle="info-outline", command=self.open_update_match_modal).pack(side=RIGHT, padx=4)
            tb.Button(bracket_header, text="⚡ Auto-Generate Bracket", bootstyle="primary", command=self.auto_generate_bracket).pack(side=RIGHT, padx=4)

        # Canvas for drawing the bracket tree
        self.bracket_canvas = tk.Canvas(container, bg=COLORS['bg'], highlightthickness=1, highlightbackground=COLORS['border'])
        self.bracket_canvas.pack(fill=BOTH, expand=True)

    # ========================================================
    # REFRESH & BRACKET RENDERING
    # ========================================================
    def refresh_events(self):
        conn = get_connection()
        cur = conn.cursor()

        cur.execute("SELECT * FROM events ORDER BY id DESC")
        self.events = [dict(e) for e in cur.fetchall()]

        total_prize = sum(e['prize_pool'] for e in self.events)
        
        cur.execute("SELECT COUNT(*) FROM tournament_teams")
        total_teams = cur.fetchone()[0]

        cur.execute("""
            SELECT COALESCE(SUM(e.entry_fee), 0)
            FROM tournament_teams t
            JOIN events e ON t.event_id = e.id
        """)
        total_entry_rev = cur.fetchone()[0]
        conn.close()

        self.card_events_count.set_value(str(len(self.events)))
        self.card_prize_pool.set_value(f"RM {total_prize:,.2f}")
        self.card_teams_count.set_value(str(total_teams))
        self.card_entry_revenue.set_value(f"RM {total_entry_rev:,.2f}")

        for item in self.events_tree.get_children():
            self.events_tree.delete(item)

        conn = get_connection()
        cur = conn.cursor()
        for ev in self.events:
            cur.execute("SELECT COUNT(*) FROM tournament_teams WHERE event_id = ?", (ev['id'],))
            team_count = cur.fetchone()[0]
            slot_str = f"{team_count}/{ev['max_teams']}"

            self.events_tree.insert("", "end", iid=str(ev['id']), values=(
                ev['id'],
                ev['game_title'],
                ev['title'],
                ev['event_date'][:16],
                ev['status'],
                slot_str,
                f"{ev['entry_fee']:.2f}",
                f"{ev['prize_pool']:.2f}"
            ))
        conn.close()

        if self.events and self.selected_event_id is None:
            first_id = str(self.events[0]['id'])
            self.events_tree.selection_set(first_id)
            self.on_event_select(None)

    def on_event_select(self, event):
        sel = self.events_tree.selection()
        if not sel:
            return
        self.selected_event_id = int(sel[0])
        self.load_event_details(self.selected_event_id)

    def load_event_details(self, event_id):
        conn = get_connection()
        cur = conn.cursor()

        cur.execute("SELECT * FROM events WHERE id = ?", (event_id,))
        ev = cur.fetchone()
        if ev:
            self.bracket_title_lbl.config(text=f"⚔️ {ev['title']} — Single Elimination Bracket")
            self.team_header_lbl.config(text=f"👥 Registered Squads: {ev['title']}")

        cur.execute("SELECT * FROM tournament_teams WHERE event_id = ? ORDER BY seed_number", (event_id,))
        self.teams = [dict(t) for t in cur.fetchall()]

        cur.execute("SELECT * FROM tournament_matches WHERE event_id = ? ORDER BY round_name, match_number", (event_id,))
        self.matches = [dict(m) for m in cur.fetchall()]
        conn.close()

        for item in self.teams_tree.get_children():
            self.teams_tree.delete(item)

        for t in self.teams:
            self.teams_tree.insert("", "end", values=(
                t['seed_number'] or "-",
                t['team_name'],
                t['leader_name'],
                t['leader_phone'],
                t['registered_at'][:16]
            ))

        self.draw_bracket_tree()

    def draw_bracket_tree(self):
        canvas = self.bracket_canvas
        canvas.delete("all")

        if not self.matches:
            canvas.create_text(380, 180, text="No bracket generated for this tournament yet.\nClick 'Auto-Generate Bracket' to seed registered squads!", 
                               fill="#94a3b8", font=("Helvetica", 12), justify="center")
            return

        col_x = [40, 240, 440, 640]
        qf_matches = [m for m in self.matches if m['round_name'] == 'Quarter-Finals']
        sf_matches = [m for m in self.matches if m['round_name'] == 'Semi-Finals']
        gf_matches = [m for m in self.matches if m['round_name'] == 'Grand Finals']

        canvas.create_text(col_x[0] + 75, 20, text="QUARTER-FINALS", fill=COLORS['cyan'], font=("Helvetica", 9, "bold"))
        canvas.create_text(col_x[1] + 75, 20, text="SEMI-FINALS", fill=COLORS['purple'], font=("Helvetica", 9, "bold"))
        canvas.create_text(col_x[2] + 75, 20, text="GRAND FINALS", fill=COLORS['amber'], font=("Helvetica", 9, "bold"))

        box_w = 150
        box_h = 48

        qf_y = [50, 125, 200, 275]
        for i, m in enumerate(qf_matches[:4]):
            self.draw_match_box(canvas, col_x[0], qf_y[i], box_w, box_h, m)

        sf_y = [87, 237]
        for i, m in enumerate(sf_matches[:2]):
            self.draw_match_box(canvas, col_x[1], sf_y[i], box_w, box_h, m)
            prev_y1 = qf_y[i * 2] + box_h / 2
            prev_y2 = qf_y[i * 2 + 1] + box_h / 2
            curr_y = sf_y[i] + box_h / 2

            canvas.create_line(col_x[0] + box_w, prev_y1, col_x[1] - 15, prev_y1, fill="#475569", width=2)
            canvas.create_line(col_x[0] + box_w, prev_y2, col_x[1] - 15, prev_y2, fill="#475569", width=2)
            canvas.create_line(col_x[1] - 15, prev_y1, col_x[1] - 15, prev_y2, fill="#475569", width=2)
            canvas.create_line(col_x[1] - 15, curr_y, col_x[1], curr_y, fill="#475569", width=2)

        gf_y = 162
        if gf_matches:
            self.draw_match_box(canvas, col_x[2], gf_y, box_w, box_h, gf_matches[0])
            prev_y1 = sf_y[0] + box_h / 2
            prev_y2 = sf_y[1] + box_h / 2
            curr_y = gf_y + box_h / 2

            canvas.create_line(col_x[1] + box_w, prev_y1, col_x[2] - 15, prev_y1, fill="#475569", width=2)
            canvas.create_line(col_x[1] + box_w, prev_y2, col_x[2] - 15, prev_y2, fill="#475569", width=2)
            canvas.create_line(col_x[2] - 15, prev_y1, col_x[2] - 15, prev_y2, fill="#475569", width=2)
            canvas.create_line(col_x[2] - 15, curr_y, col_x[2], curr_y, fill="#475569", width=2)

            if gf_matches[0].get('winner_name'):
                champ = gf_matches[0]['winner_name']
                canvas.create_rectangle(col_x[3], gf_y - 15, col_x[3] + 140, gf_y + 65, fill="#2e1065", outline=COLORS['amber'], width=2)
                canvas.create_text(col_x[3] + 70, gf_y + 10, text="👑 ARENA CHAMPION 👑", fill=COLORS['amber'], font=("Helvetica", 8, "bold"))
                canvas.create_text(col_x[3] + 70, gf_y + 36, text=champ, fill="#ffffff", font=("Helvetica", 11, "bold"), width=130)

    def draw_match_box(self, canvas, x, y, w, h, match):
        t_a = match['team_a_name'] or "TBD"
        t_b = match['team_b_name'] or "TBD"
        s_a = match['score_a']
        s_b = match['score_b']
        winner = match['winner_name']

        border_col = COLORS['border']
        if match['status'] == 'In Progress':
            border_col = COLORS['cyan']
        elif match['status'] == 'Completed':
            border_col = COLORS['purple']

        canvas.create_rectangle(x, y, x + w, y + h, fill=COLORS['bg_card'], outline=border_col, width=1.5)

        col_a = COLORS['green'] if winner == t_a and winner else "#f8fafc"
        canvas.create_text(x + 8, y + 14, text=t_a[:14], fill=col_a, anchor="w", font=("Helvetica", 8, "bold" if winner == t_a else "normal"))
        canvas.create_text(x + w - 8, y + 14, text=str(s_a), fill=col_a, anchor="e", font=("Helvetica", 8, "bold"))

        canvas.create_line(x + 5, y + h / 2, x + w - 5, y + h / 2, fill="#334155")

        col_b = COLORS['green'] if winner == t_b and winner else "#f8fafc"
        canvas.create_text(x + 8, y + 34, text=t_b[:14], fill=col_b, anchor="w", font=("Helvetica", 8, "bold" if winner == t_b else "normal"))
        canvas.create_text(x + w - 8, y + 34, text=str(s_b), fill=col_b, anchor="e", font=("Helvetica", 8, "bold"))

    def auto_generate_bracket(self):
        if not self.selected_event_id:
            messagebox.showwarning("Select Event", "Please select a tournament first.")
            return

        if len(self.teams) < 4:
            messagebox.showwarning("Insufficient Teams", "Need at least 4 registered teams to generate a bracket.")
            return

        conn = get_connection()
        cur = conn.cursor()

        cur.execute("SELECT COUNT(*) FROM tournament_matches WHERE event_id = ?", (self.selected_event_id,))
        if cur.fetchone()[0] > 0:
            if not messagebox.askyesno("Overwrite Bracket", "A bracket already exists for this tournament. Regenerate and reset scores?"):
                conn.close()
                return

        cur.execute("DELETE FROM tournament_matches WHERE event_id = ?", (self.selected_event_id,))

        seeded_teams = [t['team_name'] for t in self.teams]
        while len(seeded_teams) < 8:
            seeded_teams.append("TBD / Bye")

        qf_pairs = [
            (seeded_teams[0], seeded_teams[7]),
            (seeded_teams[3], seeded_teams[4]),
            (seeded_teams[1], seeded_teams[6]),
            (seeded_teams[2], seeded_teams[5]),
        ]

        for idx, (ta, tb_team) in enumerate(qf_pairs, start=1):
            cur.execute("""
                INSERT INTO tournament_matches (event_id, round_name, match_number, team_a_name, team_b_name, score_a, score_b, status)
                VALUES (?, 'Quarter-Finals', ?, ?, ?, 0, 0, 'Pending')
            """, (self.selected_event_id, idx, ta, tb_team))

        cur.execute("""
            INSERT INTO tournament_matches (event_id, round_name, match_number, team_a_name, team_b_name, status)
            VALUES (?, 'Semi-Finals', 5, 'Winner QF1', 'Winner QF2', 'Pending'),
                   (?, 'Semi-Finals', 6, 'Winner QF3', 'Winner QF4', 'Pending')
        """, (self.selected_event_id, self.selected_event_id))

        cur.execute("""
            INSERT INTO tournament_matches (event_id, round_name, match_number, team_a_name, team_b_name, status)
            VALUES (?, 'Grand Finals', 7, 'Winner SF1', 'Winner SF2', 'Pending')
        """, (self.selected_event_id,))

        cur.execute("UPDATE events SET status = 'Ongoing' WHERE id = ?", (self.selected_event_id,))
        conn.commit()
        conn.close()

        messagebox.showinfo("Bracket Created", "Single-elimination bracket generated successfully!")
        self.load_event_details(self.selected_event_id)

    def open_update_match_modal(self):
        if not self.selected_event_id:
            return

        win = tb.Toplevel(self)
        win.title("Update Match Score & Declare Winner")
        win.geometry("450x460")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text="Update Match Results", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        match_choices = [f"M{m['match_number']} ({m['round_name']}): {m['team_a_name']} vs {m['team_b_name']}" for m in self.matches]
        if not match_choices:
            tb.Label(c, text="No matches found in this tournament.").pack()
            return

        match_var = tk.StringVar(value=match_choices[0])
        tb.Label(c, text="Select Match:").pack(anchor=W)
        match_cb = tb.Combobox(c, textvariable=match_var, values=match_choices, state="readonly")
        match_cb.pack(fill=X, pady=(2, 10))

        team_a_lbl = tb.Label(c, text="Team A Score:", font=("Helvetica", 9, "bold"))
        team_a_lbl.pack(anchor=W)
        score_a_var = tk.IntVar(value=13)
        tb.Entry(c, textvariable=score_a_var).pack(fill=X, pady=(2, 8))

        team_b_lbl = tb.Label(c, text="Team B Score:", font=("Helvetica", 9, "bold"))
        team_b_lbl.pack(anchor=W)
        score_b_var = tk.IntVar(value=9)
        tb.Entry(c, textvariable=score_b_var).pack(fill=X, pady=(2, 8))

        winner_var = tk.StringVar(value="Team A")
        tb.Label(c, text="Declare Winner:").pack(anchor=W)
        winner_cb = tb.Combobox(c, textvariable=winner_var, values=["Team A", "Team B"], state="readonly")
        winner_cb.pack(fill=X, pady=(2, 15))

        def update_labels(*args):
            idx = match_cb.current()
            if idx >= 0 and idx < len(self.matches):
                m = self.matches[idx]
                team_a_lbl.config(text=f"{m['team_a_name']} Score:")
                team_b_lbl.config(text=f"{m['team_b_name']} Score:")
                score_a_var.set(m['score_a'])
                score_b_var.set(m['score_b'])
                winner_cb.config(values=[m['team_a_name'], m['team_b_name']])
                winner_var.set(m['winner_name'] or m['team_a_name'])

        match_cb.bind("<<ComboboxSelected>>", update_labels)
        update_labels()

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            idx = match_cb.current()
            m = self.matches[idx]
            w_name = winner_var.get()
            s_a = score_a_var.get()
            s_b = score_b_var.get()

            conn = get_connection()
            cur = conn.cursor()
            cur.execute("""
                UPDATE tournament_matches 
                SET score_a = ?, score_b = ?, winner_name = ?, status = 'Completed'
                WHERE id = ?
            """, (s_a, s_b, w_name, m['id']))

            m_num = m['match_number']
            if m_num in (1, 2):
                field = 'team_a_name' if m_num == 1 else 'team_b_name'
                cur.execute(f"UPDATE tournament_matches SET {field} = ? WHERE event_id = ? AND match_number = 5", (w_name, self.selected_event_id))
            elif m_num in (3, 4):
                field = 'team_a_name' if m_num == 3 else 'team_b_name'
                cur.execute(f"UPDATE tournament_matches SET {field} = ? WHERE event_id = ? AND match_number = 6", (w_name, self.selected_event_id))
            elif m_num in (5, 6):
                field = 'team_a_name' if m_num == 5 else 'team_b_name'
                cur.execute(f"UPDATE tournament_matches SET {field} = ? WHERE event_id = ? AND match_number = 7", (w_name, self.selected_event_id))
            elif m_num == 7:
                cur.execute("UPDATE events SET status = 'Completed' WHERE id = ?", (self.selected_event_id,))

            conn.commit()
            conn.close()

            win.destroy()
            messagebox.showinfo("Result Saved", f"Match result saved! Winner {w_name} advanced in bracket.")
            self.load_event_details(self.selected_event_id)

        tb.Button(btn_box, text="Save & Advance Winner", bootstyle="success", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def open_register_team_modal(self):
        if not self.selected_event_id:
            messagebox.showwarning("Select Event", "Select a tournament first.")
            return

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("SELECT max_teams, title, entry_fee FROM events WHERE id = ?", (self.selected_event_id,))
        ev = cur.fetchone()
        cur.execute("SELECT COUNT(*) FROM tournament_teams WHERE event_id = ?", (self.selected_event_id,))
        curr_count = cur.fetchone()[0]

        if curr_count >= ev['max_teams']:
            conn.close()
            messagebox.showwarning("Slots Full", f"Tournament '{ev['title']}' has reached its maximum capacity of {ev['max_teams']} teams!")
            return

        win = tb.Toplevel(self)
        win.title("Register Tournament Squad")
        win.geometry("450x420")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text=f"Register Squad: {ev['title']}", font=("Helvetica", 12, "bold"), bootstyle="info").pack(anchor=W)
        tb.Label(c, text=f"Entry Fee: RM {ev['entry_fee']:.2f}  |  Slots Remaining: {ev['max_teams'] - curr_count}", font=("Helvetica", 9), bootstyle="secondary").pack(anchor=W, pady=(0, 15))

        team_name_var = tk.StringVar()
        tb.Label(c, text="Team / Clan Name:").pack(anchor=W)
        tb.Entry(c, textvariable=team_name_var).pack(fill=X, pady=(2, 8))

        leader_name_var = tk.StringVar()
        tb.Label(c, text="Team Leader / Captain Name:").pack(anchor=W)
        tb.Entry(c, textvariable=leader_name_var).pack(fill=X, pady=(2, 8))

        phone_var = tk.StringVar()
        tb.Label(c, text="Captain Phone Number:").pack(anchor=W)
        tb.Entry(c, textvariable=phone_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            t_name = team_name_var.get().strip()
            l_name = leader_name_var.get().strip()
            ph = phone_var.get().strip()

            if not t_name or not l_name or not ph:
                messagebox.showerror("Error", "All registration fields are required.")
                return

            seed = curr_count + 1
            cur.execute("""
                INSERT INTO tournament_teams (event_id, team_name, leader_name, leader_phone, seed_number)
                VALUES (?, ?, ?, ?, ?)
            """, (self.selected_event_id, t_name, l_name, ph, seed))
            conn.commit()
            conn.close()

            win.destroy()
            messagebox.showinfo("Registered", f"Team '{t_name}' registered successfully as Seed #{seed}!")
            self.refresh_events()
            self.load_event_details(self.selected_event_id)

        tb.Button(btn_box, text="Confirm Registration", bootstyle="success", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def open_add_event_modal(self):
        win = tb.Toplevel(self)
        win.title("Host New Esports Tournament")
        win.geometry("450x500")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text="Host New Tournament", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        title_var = tk.StringVar()
        tb.Label(c, text="Tournament Title:").pack(anchor=W)
        tb.Entry(c, textvariable=title_var).pack(fill=X, pady=(2, 8))

        game_var = tk.StringVar(value="Valorant")
        tb.Label(c, text="Game Title:").pack(anchor=W)
        tb.Combobox(c, textvariable=game_var, values=["Valorant", "League of Legends", "Counter-Strike 2", "Dota 2", "Apex Legends", "Tekken 8"], state="readonly").pack(fill=X, pady=(2, 8))

        fee_var = tk.DoubleVar(value=40.00)
        tb.Label(c, text="Entry Fee per Team (RM):").pack(anchor=W)
        tb.Entry(c, textvariable=fee_var).pack(fill=X, pady=(2, 8))

        prize_var = tk.DoubleVar(value=1500.00)
        tb.Label(c, text="Total Prize Pool (RM):").pack(anchor=W)
        tb.Entry(c, textvariable=prize_var).pack(fill=X, pady=(2, 8))

        date_var = tk.StringVar(value="2026-11-20 14:00")
        tb.Label(c, text="Date & Time (YYYY-MM-DD HH:MM):").pack(anchor=W)
        tb.Entry(c, textvariable=date_var).pack(fill=X, pady=(2, 8))

        desc_var = tk.StringVar(value="5v5 Single Elimination Bracket on LAN.")
        tb.Label(c, text="Tournament Description / Rules:").pack(anchor=W)
        tb.Entry(c, textvariable=desc_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            t = title_var.get().strip()
            g = game_var.get()
            f = fee_var.get()
            p = prize_var.get()
            d = date_var.get().strip()
            desc = desc_var.get().strip()

            if not t:
                messagebox.showerror("Error", "Title is required.")
                return

            conn = get_connection()
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO events (title, game_title, entry_fee, prize_pool, max_teams, event_date, status, description)
                VALUES (?, ?, ?, ?, 8, ?, 'Open', ?)
            """, (t, g, f, p, d, desc))
            conn.commit()
            conn.close()

            win.destroy()
            messagebox.showinfo("Success", f"Tournament '{t}' opened for registration!")
            self.refresh_events()

        tb.Button(btn_box, text="Open Tournament", bootstyle="success", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def open_edit_event_modal(self):
        if not self.selected_event_id:
            return

        conn = get_connection()
        cur = conn.cursor()
        cur.execute("SELECT * FROM events WHERE id = ?", (self.selected_event_id,))
        ev = cur.fetchone()
        conn.close()
        if not ev:
            return

        win = tb.Toplevel(self)
        win.title("Edit Tournament")
        win.geometry("450x460")
        win.resizable(False, False)
        win.transient(self)
        win.grab_set()

        c = tb.Frame(win, padding=20)
        c.pack(fill=BOTH, expand=True)

        tb.Label(c, text=f"Edit: {ev['title']}", font=("Helvetica", 13, "bold"), bootstyle="info").pack(anchor=W, pady=(0, 15))

        title_var = tk.StringVar(value=ev['title'])
        tb.Label(c, text="Title:").pack(anchor=W)
        tb.Entry(c, textvariable=title_var).pack(fill=X, pady=(2, 8))

        prize_var = tk.DoubleVar(value=ev['prize_pool'])
        tb.Label(c, text="Prize Pool (RM):").pack(anchor=W)
        tb.Entry(c, textvariable=prize_var).pack(fill=X, pady=(2, 8))

        status_var = tk.StringVar(value=ev['status'])
        tb.Label(c, text="Status:").pack(anchor=W)
        tb.Combobox(c, textvariable=status_var, values=["Open", "Ongoing", "Completed", "Cancelled"], state="readonly").pack(fill=X, pady=(2, 8))

        desc_var = tk.StringVar(value=ev['description'] or "")
        tb.Label(c, text="Description:").pack(anchor=W)
        tb.Entry(c, textvariable=desc_var).pack(fill=X, pady=(2, 15))

        btn_box = tb.Frame(c)
        btn_box.pack(fill=X)

        def save():
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("""
                UPDATE events
                SET title = ?, prize_pool = ?, status = ?, description = ?
                WHERE id = ?
            """, (title_var.get().strip(), prize_var.get(), status_var.get(), desc_var.get().strip(), self.selected_event_id))
            conn.commit()
            conn.close()

            win.destroy()
            messagebox.showinfo("Updated", "Tournament updated successfully.")
            self.refresh_events()
            self.load_event_details(self.selected_event_id)

        tb.Button(btn_box, text="Save Changes", bootstyle="info", command=save).pack(side=LEFT, expand=True, fill=X, padx=3)
        tb.Button(btn_box, text="Cancel", bootstyle="secondary-outline", command=win.destroy).pack(side=RIGHT, expand=True, fill=X, padx=3)

    def delete_event(self):
        if not self.selected_event_id:
            return

        if messagebox.askyesno("Delete Event", "Are you sure you want to delete this tournament and all its brackets/registrations?"):
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("DELETE FROM events WHERE id = ?", (self.selected_event_id,))
            conn.commit()
            conn.close()
            self.selected_event_id = None
            messagebox.showinfo("Deleted", "Tournament deleted.")
            self.refresh_events()





