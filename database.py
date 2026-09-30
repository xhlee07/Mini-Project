"""
TFLY Gaming Café Management System
Database Layer (SQLite)
Handles connection, table migrations, and sample seed data.
"""

import sqlite3
import os
import hashlib
from datetime import datetime, timedelta

DB_FILE = "tfly_gaming.db"

def get_connection():
    """Return a connection to the SQLite database."""
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn

def hash_password(password: str) -> str:
    """Hash password using SHA-256 for basic security."""
    return hashlib.sha256(password.encode("utf-8")).hexdigest()

def init_db():
    """Initialize database tables and pre-populate with rich demo data if empty."""
    conn = get_connection()
    cursor = conn.cursor()

    # 1. Users table (Admin, Staff, Customer)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        role TEXT NOT NULL CHECK(role IN ('admin', 'staff', 'customer')),
        full_name TEXT NOT NULL,
        phone TEXT,
        date_of_birth TEXT DEFAULT '',
        email TEXT DEFAULT '',
        auth_provider TEXT DEFAULT 'local',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    # Migrations for users table
    for col_def in [
        "date_of_birth TEXT DEFAULT ''",
        "email TEXT DEFAULT ''",
        "auth_provider TEXT DEFAULT 'local'"
    ]:
        try:
            cursor.execute(f"ALTER TABLE users ADD COLUMN {col_def}")
        except Exception:
            pass  # Column already exists



    # 2. Members table (Loyalty, Tiers, Balance)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS members (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER UNIQUE,
        member_code TEXT UNIQUE NOT NULL,
        name TEXT NOT NULL,
        phone TEXT NOT NULL,
        tier TEXT NOT NULL DEFAULT 'Bronze' CHECK(tier IN ('Bronze', 'Silver', 'Gold', 'Diamond')),
        points INTEGER NOT NULL DEFAULT 0,
        total_spent REAL NOT NULL DEFAULT 0.0,
        balance REAL NOT NULL DEFAULT 0.0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
    )
    """)

    # 3. Stations table (PC Station Management)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS stations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        station_number TEXT UNIQUE NOT NULL,
        zone TEXT NOT NULL CHECK(zone IN ('Standard', 'VIP Esports', 'Streamer Suite', 'Duo Lounge')),
        hourly_rate REAL NOT NULL,
        specs TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'Available' CHECK(status IN ('Available', 'Occupied', 'Reserved', 'Maintenance'))
    )
    """)

    # 4. Sessions table (Live PC usage)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS sessions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        station_id INTEGER NOT NULL,
        member_id INTEGER,
        guest_name TEXT,
        start_time TIMESTAMP NOT NULL,
        end_time TIMESTAMP NOT NULL,
        duration_minutes INTEGER NOT NULL,
        total_cost REAL NOT NULL,
        status TEXT NOT NULL DEFAULT 'Active' CHECK(status IN ('Active', 'Completed', 'Cancelled')),
        FOREIGN KEY (station_id) REFERENCES stations(id) ON DELETE CASCADE,
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL
    )
    """)

    # 5. Product Categories
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS categories (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL,
        icon TEXT
    )
    """)

    # 6. Products table (Snacks, Drinks, Gear)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS products (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        category_id INTEGER NOT NULL,
        name TEXT NOT NULL,
        price REAL NOT NULL,
        stock INTEGER NOT NULL DEFAULT 0,
        min_stock_alert INTEGER NOT NULL DEFAULT 5,
        image_name TEXT,
        description TEXT,
        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
    )
    """)

    # 7. Stock Records (Inventory history)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS stock_records (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        product_id INTEGER NOT NULL,
        change_qty INTEGER NOT NULL,
        reason TEXT NOT NULL,
        recorded_by TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
    )
    """)

    # 8. Orders table (Seat Delivery Food & Drinks)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS orders (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        order_number TEXT UNIQUE NOT NULL,
        station_number TEXT,
        member_id INTEGER,
        customer_name TEXT,
        total_amount REAL NOT NULL,
        payment_status TEXT NOT NULL DEFAULT 'Paid' CHECK(payment_status IN ('Paid', 'Pending', 'Cancelled')),
        order_status TEXT NOT NULL DEFAULT 'Delivering' CHECK(order_status IN ('Pending', 'Delivering', 'Delivered', 'Cancelled')),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL
    )
    """)

    # 9. Order Items table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS order_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        order_id INTEGER NOT NULL,
        product_id INTEGER NOT NULL,
        product_name TEXT NOT NULL,
        quantity INTEGER NOT NULL,
        unit_price REAL NOT NULL,
        subtotal REAL NOT NULL,
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
    )
    """)

    # 10. Tournaments & Events table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        game_title TEXT NOT NULL,
        entry_fee REAL NOT NULL DEFAULT 0.0,
        prize_pool REAL NOT NULL DEFAULT 0.0,
        max_teams INTEGER NOT NULL DEFAULT 8,
        event_date TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'Open' CHECK(status IN ('Open', 'Ongoing', 'Completed', 'Cancelled')),
        description TEXT
    )
    """)

    # 11. Tournament Teams / Registrations
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS tournament_teams (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        event_id INTEGER NOT NULL,
        team_name TEXT NOT NULL,
        leader_name TEXT NOT NULL,
        leader_phone TEXT NOT NULL,
        member_id INTEGER,
        seed_number INTEGER,
        registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL
    )
    """)

    # 12. Tournament Matches (For bracket viewer)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS tournament_matches (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        event_id INTEGER NOT NULL,
        round_name TEXT NOT NULL,
        match_number INTEGER NOT NULL,
        team_a_name TEXT,
        team_b_name TEXT,
        score_a INTEGER DEFAULT 0,
        score_b INTEGER DEFAULT 0,
        winner_name TEXT,
        status TEXT NOT NULL DEFAULT 'Pending' CHECK(status IN ('Pending', 'In Progress', 'Completed')),
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
    )
    """)

    # 13. Bills (Unified Checkouts: Time + F&B + Tournament)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS bills (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        bill_number TEXT UNIQUE NOT NULL,
        member_id INTEGER,
        customer_name TEXT NOT NULL,
        station_cost REAL NOT NULL DEFAULT 0.0,
        snack_cost REAL NOT NULL DEFAULT 0.0,
        event_cost REAL NOT NULL DEFAULT 0.0,
        discount_amount REAL NOT NULL DEFAULT 0.0,
        subtotal REAL NOT NULL DEFAULT 0.0,
        total_amount REAL NOT NULL,
        points_earned INTEGER NOT NULL DEFAULT 0,
        payment_method TEXT NOT NULL CHECK(payment_method IN ('Cash', 'TNG eWallet', 'Credit Card', 'Member Balance')),
        status TEXT NOT NULL DEFAULT 'Paid',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL
    )
    """)

    # 14. Loyalty Rewards Catalog
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS rewards (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        points_cost INTEGER NOT NULL,
        reward_type TEXT NOT NULL CHECK(reward_type IN ('Time', 'Food', 'Merchandise')),
        description TEXT,
        stock INTEGER NOT NULL DEFAULT 99
    )
    """)

    # 15. Points Transactions
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS points_transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        member_id INTEGER NOT NULL,
        points_change INTEGER NOT NULL,
        reason TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
    )
    """)

    # 16. Gamer Quests (Novel Module 5)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS quests (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        description TEXT NOT NULL,
        reward_points INTEGER NOT NULL,
        icon TEXT NOT NULL,
        target_count INTEGER NOT NULL DEFAULT 1
    )
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS user_quests (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        quest_id INTEGER NOT NULL,
        progress INTEGER NOT NULL DEFAULT 0,
        is_completed INTEGER NOT NULL DEFAULT 0,
        is_claimed INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
    )
    """)

    # 17. Lucky Wheel Spin History (Novel Module 5)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS wheel_spins (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        prize_name TEXT NOT NULL,
        prize_value TEXT NOT NULL,
        spun_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    )
    """)

    # 18. Staff Duty Shifts & Register Log
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS staff_shifts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        staff_username TEXT NOT NULL,
        staff_name TEXT NOT NULL,
        clock_in TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        clock_out TIMESTAMP,
        cash_float REAL DEFAULT 200.0,
        shift_sales REAL DEFAULT 0.0,
        status TEXT DEFAULT 'On Duty' CHECK(status IN ('On Duty', 'Completed')),
        notes TEXT DEFAULT ''
    )
    """)

    # 19. Cafe Incident & Equipment Maintenance Tickets
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS incident_tickets (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        station_number TEXT NOT NULL,
        reported_by TEXT NOT NULL,
        issue_type TEXT NOT NULL,
        description TEXT NOT NULL,
        priority TEXT DEFAULT 'Medium' CHECK(priority IN ('Low', 'Medium', 'High', 'Critical')),
        status TEXT DEFAULT 'Open' CHECK(status IN ('Open', 'In Progress', 'Resolved')),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        resolved_at TIMESTAMP,
        resolution_notes TEXT DEFAULT ''
    )
    """)

    conn.commit()

    # Seed shift and incident if tables are empty
    cursor.execute("SELECT COUNT(*) FROM staff_shifts")
    if cursor.fetchone()[0] == 0:
        cursor.execute("""
            INSERT INTO staff_shifts (staff_username, staff_name, clock_in, cash_float, shift_sales, status, notes)
            VALUES ('staff1', 'Sarah Connor (Floor Manager)', datetime('now', '-3 hours'), 250.0, 184.50, 'On Duty', 'Morning gaming shift - smooth operation')
        """)
        cursor.execute("""
            INSERT INTO staff_shifts (staff_username, staff_name, clock_in, clock_out, cash_float, shift_sales, status, notes)
            VALUES ('admin', 'Alex Vance (System Admin)', datetime('now', '-1 day'), datetime('now', '-18 hours'), 200.0, 430.00, 'Completed', 'Weekend tournament prep shift')
        """)

    cursor.execute("SELECT COUNT(*) FROM incident_tickets")
    if cursor.fetchone()[0] == 0:
        cursor.execute("""
            INSERT INTO incident_tickets (station_number, reported_by, issue_type, description, priority, status)
            VALUES ('PC-04', 'Kenji Sato', 'Peripherals', 'Right mouse click double-clicking intermittently', 'Medium', 'In Progress')
        """)
        cursor.execute("""
            INSERT INTO incident_tickets (station_number, reported_by, issue_type, description, priority, status)
            VALUES ('PC-12', 'Staff Desk', 'Cleaning', 'Desk sanitized and keyboard keycaps steam cleaned', 'Low', 'Resolved')
        """)
        cursor.execute("""
            INSERT INTO incident_tickets (station_number, reported_by, issue_type, description, priority, status)
            VALUES ('PC-08', 'Gamer-VIP', 'Display', '240Hz monitor DP cable loose, re-seated connector', 'High', 'Resolved')
        """)


    # Seed Sample Data if users table is empty
    cursor.execute("SELECT COUNT(*) FROM users")
    if cursor.fetchone()[0] == 0:
        seed_sample_data(conn)

    conn.close()

def seed_sample_data(conn):
    """Seed initial realistic data for gaming café demo."""
    cursor = conn.cursor()
    now = datetime.now()

    # 1. Seed Users
    users_data = [
        ('admin', hash_password('admin123'), 'admin', 'Alex Vance (System Admin)', '012-3456789'),
        ('staff1', hash_password('staff123'), 'staff', 'Sarah Connor (Floor Manager)', '012-8889999'),
        ('gamer1', hash_password('gamer123'), 'customer', 'Kenji Sato (Esports Player)', '017-1234567'),
        ('gamer2', hash_password('gamer123'), 'customer', 'Chloe Tan (Streamer)', '018-9876543'),
        ('gamer3', hash_password('gamer123'), 'customer', 'Marcus Lee (Casual Gamer)', '019-5554433'),
    ]
    cursor.executemany("""
    INSERT INTO users (username, password_hash, role, full_name, phone)
    VALUES (?, ?, ?, ?, ?)
    """, users_data)

    # 2. Seed Members (linked to customer users + extra members)
    members_data = [
        (3, 'TFLY-8801', 'Kenji Sato', '017-1234567', 'Diamond', 1450, 890.0, 120.0),
        (4, 'TFLY-8802', 'Chloe Tan', '018-9876543', 'Gold', 680, 420.0, 45.0),
        (5, 'TFLY-8803', 'Marcus Lee', '019-5554433', 'Silver', 250, 180.0, 15.0),
        (None, 'TFLY-8804', 'David Zhang', '016-7788990', 'Bronze', 80, 60.0, 0.0),
        (None, 'TFLY-8805', 'Rachel Wong', '011-2233445', 'Gold', 720, 510.0, 80.0),
    ]
    cursor.executemany("""
    INSERT INTO members (user_id, member_code, name, phone, tier, points, total_spent, balance)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, members_data)

    # 3. Seed Stations (16 Gaming Stations across 4 zones)
    stations_data = [
        # Standard Zone (RM 4.50/hr)
        ('PC-01', 'Standard', 4.50, 'i5-13400F | RTX 4060 | 16GB RAM | 165Hz 27" IPS', 'Occupied'),
        ('PC-02', 'Standard', 4.50, 'i5-13400F | RTX 4060 | 16GB RAM | 165Hz 27" IPS', 'Available'),
        ('PC-03', 'Standard', 4.50, 'i5-13400F | RTX 4060 | 16GB RAM | 165Hz 27" IPS', 'Occupied'),
        ('PC-04', 'Standard', 4.50, 'i5-13400F | RTX 4060 | 16GB RAM | 165Hz 27" IPS', 'Available'),
        ('PC-05', 'Standard', 4.50, 'i5-13400F | RTX 4060 | 16GB RAM | 165Hz 27" IPS', 'Maintenance'),
        ('PC-06', 'Standard', 4.50, 'i5-13400F | RTX 4060 | 16GB RAM | 165Hz 27" IPS', 'Available'),
        # VIP Esports Zone (RM 7.00/hr)
        ('VIP-01', 'VIP Esports', 7.00, 'i7-14700KF | RTX 4080 Super | 32GB RAM | 240Hz Fast IPS', 'Occupied'),
        ('VIP-02', 'VIP Esports', 7.00, 'i7-14700KF | RTX 4080 Super | 32GB RAM | 240Hz Fast IPS', 'Reserved'),
        ('VIP-03', 'VIP Esports', 7.00, 'i7-14700KF | RTX 4080 Super | 32GB RAM | 240Hz Fast IPS', 'Available'),
        ('VIP-04', 'VIP Esports', 7.00, 'i7-14700KF | RTX 4080 Super | 32GB RAM | 240Hz Fast IPS', 'Occupied'),
        # Streamer Suite (RM 10.00/hr)
        ('STRM-01', 'Streamer Suite', 10.00, 'Ryzen 7 7800X3D | RTX 4090 | Dual 280Hz + Cam + Shure Mic', 'Occupied'),
        ('STRM-02', 'Streamer Suite', 10.00, 'Ryzen 7 7800X3D | RTX 4090 | Dual 280Hz + Cam + Shure Mic', 'Available'),
        # Duo Lounge (RM 8.50/hr per seat)
        ('DUO-A1', 'Duo Lounge', 8.50, 'i7-14700 | RTX 4070 Ti | Curved Ultrawide 34" | Sofa Chair', 'Occupied'),
        ('DUO-A2', 'Duo Lounge', 8.50, 'i7-14700 | RTX 4070 Ti | Curved Ultrawide 34" | Sofa Chair', 'Occupied'),
        ('DUO-B1', 'Duo Lounge', 8.50, 'i7-14700 | RTX 4070 Ti | Curved Ultrawide 34" | Sofa Chair', 'Available'),
        ('DUO-B2', 'Duo Lounge', 8.50, 'i7-14700 | RTX 4070 Ti | Curved Ultrawide 34" | Sofa Chair', 'Available'),
    ]
    cursor.executemany("""
    INSERT INTO stations (station_number, zone, hourly_rate, specs, status)
    VALUES (?, ?, ?, ?, ?)
    """, stations_data)

    # 4. Seed Active Sessions
    # PC-01 (1h 15m remaining)
    start_1 = (now - timedelta(minutes=45)).strftime("%Y-%m-%d %H:%M:%S")
    end_1 = (now + timedelta(minutes=75)).strftime("%Y-%m-%d %H:%M:%S")
    # PC-03 (25m remaining)
    start_3 = (now - timedelta(minutes=95)).strftime("%Y-%m-%d %H:%M:%S")
    end_3 = (now + timedelta(minutes=25)).strftime("%Y-%m-%d %H:%M:%S")
    # VIP-01 (180m remaining)
    start_v1 = (now - timedelta(minutes=20)).strftime("%Y-%m-%d %H:%M:%S")
    end_v1 = (now + timedelta(minutes=160)).strftime("%Y-%m-%d %H:%M:%S")
    # VIP-04 (10m remaining)
    start_v4 = (now - timedelta(minutes=110)).strftime("%Y-%m-%d %H:%M:%S")
    end_v4 = (now + timedelta(minutes=10)).strftime("%Y-%m-%d %H:%M:%S")
    # STRM-01 (90m remaining)
    start_s1 = (now - timedelta(minutes=30)).strftime("%Y-%m-%d %H:%M:%S")
    end_s1 = (now + timedelta(minutes=90)).strftime("%Y-%m-%d %H:%M:%S")
    # DUO-A1 & A2 (40m remaining)
    start_d1 = (now - timedelta(minutes=80)).strftime("%Y-%m-%d %H:%M:%S")
    end_d1 = (now + timedelta(minutes=40)).strftime("%Y-%m-%d %H:%M:%S")

    sessions_data = [
        (1, 1, 'Kenji Sato', start_1, end_1, 120, 9.00, 'Active'),
        (3, 3, 'Marcus Lee', start_3, end_3, 120, 9.00, 'Active'),
        (7, 2, 'Chloe Tan', start_v1, end_v1, 180, 21.00, 'Active'),
        (10, None, 'Walk-in Guest Jason', start_v4, end_v4, 120, 14.00, 'Active'),
        (11, 5, 'Rachel Wong', start_s1, end_s1, 120, 20.00, 'Active'),
        (13, None, 'Couple Gaming A', start_d1, end_d1, 120, 17.00, 'Active'),
        (14, None, 'Couple Gaming B', start_d1, end_d1, 120, 17.00, 'Active'),
    ]
    cursor.executemany("""
    INSERT INTO sessions (station_id, member_id, guest_name, start_time, end_time, duration_minutes, total_cost, status)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, sessions_data)

    # 5. Product Categories
    categories = [
        ('Energy Drinks & Sodas', '⚡'),
        ('Ramen & Hot Meals', '🍜'),
        ('Snacks & Chips', '🍟'),
        ('Gaming Gear & Peripherals', '🎧')
    ]
    cursor.executemany("INSERT INTO categories (name, icon) VALUES (?, ?)", categories)

    # 6. Products
    products = [
        (1, 'Monster Energy Mango Loco (500ml)', 7.50, 24, 8, 'monster_mango.png', 'Crisp exotic mango energy blend for high focus.'),
        (1, 'Red Bull Gold Energy Can (250ml)', 6.00, 18, 6, 'redbull.png', 'Classic Austrian formula, revitalizes body & mind.'),
        (1, '100 Plus Active Isotonic (500ml)', 3.50, 4, 10, '100plus.png', 'Electrolyte hydration with light carbonation. [LOW STOCK ALERT]'),
        (1, 'Iced Lemon Peach Tea (Large)', 4.80, 35, 5, 'peach_tea.png', 'Brewed black tea infused with sweet peach & lime slices.'),
        (2, 'TFLY Signature Spicy Shin Ramyun with Egg & Cheese', 9.50, 28, 5, 'ramen_shin.png', 'Korean spicy noodles topped with soft-boiled egg and melted cheddar.'),
        (2, 'Indomie Goreng Double with Crispy Fried Chicken', 10.50, 3, 5, 'indomie_double.png', 'Double portion Indonesian stir-fried noodles with crunchy karaage.'),
        (2, 'Japanese Chicken Katsu Curry Rice', 14.00, 12, 4, 'curry_rice.png', 'Crispy panko chicken breast over steamed rice and fragrant curry.'),
        (3, 'Truffle Parmesan Crinkle Fries', 8.00, 15, 5, 'truffle_fries.png', 'Golden crispy fries drizzled with aromatic white truffle oil.'),
        (3, 'Spicy Popcorn Chicken Bucket', 9.00, 2, 6, 'popcorn_chicken.png', 'Bite-sized seasoned chicken bites with dipping sauce. [LOW STOCK]'),
        (3, 'Doritos Nacho Cheese Big Bag', 6.50, 22, 6, 'doritos.png', 'Crunchy corn tortilla chips packed with bold cheesy flavor.'),
        (4, 'Razer DeathAdder Essential Gaming Mouse', 79.00, 6, 2, 'razer_mouse.png', '6400 DPI optical sensor, ergonomic esports shape.'),
        (4, 'TFLY XL Speed Microfiber Deskmat (900x400mm)', 35.00, 10, 3, 'deskmat.png', 'Water-resistant smooth glide surface with anti-slip rubber base.'),
    ]
    cursor.executemany("""
    INSERT INTO products (category_id, name, price, stock, min_stock_alert, image_name, description)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """, products)

    # 7. Seed Orders
    order_data = [
        ('ORD-20260901', 'PC-01', 1, 'Kenji Sato', 17.00, 'Paid', 'Delivered'),
        ('ORD-20260902', 'VIP-01', 2, 'Chloe Tan', 12.30, 'Paid', 'Delivering'),
        ('ORD-20260903', 'PC-03', 3, 'Marcus Lee', 9.50, 'Paid', 'Pending'),
    ]
    cursor.executemany("""
    INSERT INTO orders (order_number, station_number, member_id, customer_name, total_amount, payment_status, order_status)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """, order_data)

    order_items_data = [
        (1, 1, 'Monster Energy Mango Loco (500ml)', 1, 7.50, 7.50),
        (1, 5, 'TFLY Signature Spicy Shin Ramyun with Egg & Cheese', 1, 9.50, 9.50),
        (2, 4, 'Iced Lemon Peach Tea (Large)', 1, 4.80, 4.80),
        (2, 1, 'Monster Energy Mango Loco (500ml)', 1, 7.50, 7.50),
        (3, 5, 'TFLY Signature Spicy Shin Ramyun with Egg & Cheese', 1, 9.50, 9.50),
    ]
    cursor.executemany("""
    INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, subtotal)
    VALUES (?, ?, ?, ?, ?, ?)
    """, order_items_data)

    # 8. Seed Tournaments
    events_data = [
        ('VALORANT SEA Champions Invitational 2026', 'Valorant', 50.00, 2500.00, 8, '2026-10-15 14:00', 'Open', '5v5 Single Elimination Bracket. Map veto standard VCT pool. Prizes: 1st RM1,500, 2nd RM700, 3rd RM300.'),
        ('League of Legends: Mid-Season Clash 5v5', 'League of Legends', 40.00, 1800.00, 8, '2026-10-22 13:00', 'Open', 'Summoners Rift Tournament Draft. BO1 up to semi-finals, BO3 Grand Finals.'),
        ('Counter-Strike 2: Cyber Smoke LAN Brawl', 'Counter-Strike 2', 30.00, 1200.00, 8, '2026-11-05 15:00', 'Open', 'MR12 Competitive Mode on official 128-tick tournament LAN servers.'),
    ]
    cursor.executemany("""
    INSERT INTO events (title, game_title, entry_fee, prize_pool, max_teams, event_date, status, description)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, events_data)

    # Seed Teams for Valorant Tournament (Event ID 1)
    teams_data = [
        (1, 'Team Apex Predators', 'Kenji Sato', '017-1234567', 1, 1),
        (1, 'Shadow Strikers', 'Rayden Khoo', '012-4455667', None, 2),
        (1, 'Neon Valkyries', 'Chloe Tan', '018-9876543', 2, 3),
        (1, 'Cyber Knights', 'Zack Lim', '013-9988776', None, 4),
        (1, 'Pixel Rogues', 'Bryan Tan', '014-1122334', None, 5),
        (1, 'Echo Mirage', 'Stanley Chen', '016-5544332', None, 6),
        (1, 'Vortex Syndicate', 'Alvin Goh', '017-7788991', None, 7),
        (1, 'Phantom 5', 'Justin Ong', '019-3322110', None, 8),
    ]
    cursor.executemany("""
    INSERT INTO tournament_teams (event_id, team_name, leader_name, leader_phone, member_id, seed_number)
    VALUES (?, ?, ?, ?, ?, ?)
    """, teams_data)

    # Seed Tournament Bracket Matches for Event ID 1 (8-team Single Elimination)
    matches_data = [
        # Quarter Finals (Round 1)
        (1, 'Quarter-Finals', 1, 'Team Apex Predators', 'Phantom 5', 13, 7, 'Team Apex Predators', 'Completed'),
        (1, 'Quarter-Finals', 2, 'Cyber Knights', 'Pixel Rogues', 13, 11, 'Cyber Knights', 'Completed'),
        (1, 'Quarter-Finals', 3, 'Neon Valkyries', 'Echo Mirage', 13, 9, 'Neon Valkyries', 'Completed'),
        (1, 'Quarter-Finals', 4, 'Shadow Strikers', 'Vortex Syndicate', 10, 13, 'Vortex Syndicate', 'Completed'),
        # Semi Finals (Round 2)
        (1, 'Semi-Finals', 5, 'Team Apex Predators', 'Cyber Knights', 13, 8, 'Team Apex Predators', 'Completed'),
        (1, 'Semi-Finals', 6, 'Neon Valkyries', 'Vortex Syndicate', 11, 13, 'Vortex Syndicate', 'Completed'),
        # Grand Finals (Round 3)
        (1, 'Grand Finals', 7, 'Team Apex Predators', 'Vortex Syndicate', 0, 0, None, 'In Progress'),
    ]
    cursor.executemany("""
    INSERT INTO tournament_matches (event_id, round_name, match_number, team_a_name, team_b_name, score_a, score_b, winner_name, status)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, matches_data)

    # 9. Seed Unified Bills
    bills_data = [
        ('INV-20260901-01', 1, 'Kenji Sato', 9.00, 17.00, 50.00, 7.60, 76.00, 68.40, 68, 'TNG eWallet', 'Paid'),
        ('INV-20260901-02', 2, 'Chloe Tan', 21.00, 12.30, 40.00, 3.66, 73.30, 69.64, 70, 'Credit Card', 'Paid'),
        ('INV-20260901-03', 3, 'Marcus Lee', 9.00, 9.50, 0.0, 0.92, 18.50, 17.58, 18, 'Cash', 'Paid'),
        ('INV-20260901-04', 5, 'Rachel Wong', 20.00, 8.00, 0.0, 2.80, 28.00, 25.20, 25, 'Member Balance', 'Paid'),
    ]
    cursor.executemany("""
    INSERT INTO bills (bill_number, member_id, customer_name, station_cost, snack_cost, event_cost, discount_amount, subtotal, total_amount, points_earned, payment_method, status)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, bills_data)

    # 10. Seed Rewards Catalog
    rewards_data = [
        ('1-Hour Free PC Pass (Standard)', 100, 'Time', 'Valid on any standard gaming station Mon-Fri.', 50),
        ('2-Hour VIP Esports Upgrade', 250, 'Time', '2 hours gaming session on RTX 4080 Super VIP Station.', 30),
        ('Free Monster Energy Drink', 120, 'Food', 'Choice of Monster Mango Loco or Ultra White.', 40),
        ('TFLY Signature Shin Ramyun Set', 160, 'Food', 'Hot Shin Ramyun + Iced Peach Tea combo.', 25),
        ('TFLY Neon Microfiber Mousepad', 400, 'Merchandise', 'Official esports merchandise 900x400mm.', 15),
        ('Razer DeathAdder Essential Mouse', 950, 'Merchandise', 'Brand new boxed genuine Razer gaming mouse.', 5),
    ]
    cursor.executemany("""
    INSERT INTO rewards (name, points_cost, reward_type, description, stock)
    VALUES (?, ?, ?, ?, ?)
    """, rewards_data)

    # 11. Seed Points Transactions
    points_tx_data = [
        (1, 68, 'Earned from Invoice INV-20260901-01'),
        (1, -100, 'Redeemed: 1-Hour Free PC Pass'),
        (2, 70, 'Earned from Invoice INV-20260901-02'),
        (3, 18, 'Earned from Invoice INV-20260901-03'),
    ]
    cursor.executemany("""
    INSERT INTO points_transactions (member_id, points_change, reason)
    VALUES (?, ?, ?)
    """, points_tx_data)

    # 12. Seed Gamer Quests (Novel Module 5)
    quests_data = [
        ('Cyber Check-In', 'Log into TFLY Gaming Café and play for at least 1 hour today.', 30, '⚡', 1),
        ('Ramen Fuel Up', 'Order any hot meal or snack from the café menu to your station.', 25, '🍜', 1),
        ('Marathon Warrior', 'Complete a gaming session of 3 hours or more.', 50, '🛡️', 3),
        ('Esports Gladiator', 'Register and participate in any weekend café tournament.', 100, '🏆', 1),
    ]
    cursor.executemany("""
    INSERT INTO quests (title, description, reward_points, icon, target_count)
    VALUES (?, ?, ?, ?, ?)
    """, quests_data)

    # Link quests to user 3 (Kenji)
    cursor.execute("""
    INSERT INTO user_quests (user_id, quest_id, progress, is_completed, is_claimed)
    VALUES
    (3, 1, 1, 1, 1),
    (3, 2, 1, 1, 0),
    (3, 3, 2, 0, 0),
    (3, 4, 1, 1, 0)
    """)

    conn.commit()

if __name__ == '__main__':
    init_db()
    print("Database initialized successfully with rich mock data!")
