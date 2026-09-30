"""CTFLY v3: transactional business rules, independent of the desktop UI.

The original tfly_gaming.db is never opened or changed by this version.
Money is stored as integer sen. All dates use local café time (Malaysia).
"""
from contextlib import contextmanager
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
import hashlib
import hmac
import math
import secrets
import sqlite3

BASE = Path(__file__).resolve().parent
MYT = timezone(timedelta(hours=8))


def now():
    return datetime.now(MYT).replace(tzinfo=None, microsecond=0)


def stamp(value=None):
    return (value or now()).isoformat(timespec="seconds")


def adult(dob, today=None):
    try:
        born = date.fromisoformat(dob)
    except (ValueError, TypeError):
        raise ValueError("Enter a valid date of birth: YYYY-MM-DD.") from None
    today = today or now().date()
    years = today.year - born.year - ((today.month, today.day) < (born.month, born.day))
    if born > today or years > 120 or years < 18:
        raise ValueError("You must be at least 18 years old to register or book a station.")
    return years


def password_hash(password):
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), 210000).hex()
    return f"pbkdf2$210000${salt}${digest}"


def password_matches(password, encoded):
    try:
        _, iterations, salt, digest = encoded.split("$")
        actual = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), int(iterations)).hex()
        return hmac.compare_digest(digest, actual)
    except (ValueError, AttributeError):
        return False


def money(sen):
    return f"RM {sen / 100:,.2f}"


def sen(value):
    from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
    try:
        amount = Decimal(str(value))
        if not amount.is_finite() or amount < 0 or amount > 1000000:
            raise ValueError("Amount must be between RM 0 and RM 1,000,000.")
        return int((amount * 100).quantize(Decimal("1"), rounding=ROUND_HALF_UP))
    except InvalidOperation:
        raise ValueError("Enter a valid money amount.") from None


SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
 id INTEGER PRIMARY KEY, username TEXT UNIQUE COLLATE NOCASE NOT NULL,
 password TEXT, name TEXT NOT NULL, email TEXT UNIQUE COLLATE NOCASE,
 phone TEXT NOT NULL DEFAULT '', dob TEXT NOT NULL,
 role TEXT NOT NULL CHECK(role IN ('admin','staff','customer')),
 google_sub TEXT UNIQUE, active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS members (
 id INTEGER PRIMARY KEY, user_id INTEGER UNIQUE NOT NULL REFERENCES users(id),
 points INTEGER NOT NULL DEFAULT 0 CHECK(points>=0), spent INTEGER NOT NULL DEFAULT 0,
 tier TEXT NOT NULL DEFAULT 'Bronze');
CREATE TABLE IF NOT EXISTS stations (
 id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL, zone TEXT NOT NULL,
 spec TEXT NOT NULL, rate INTEGER NOT NULL CHECK(rate>0),
 status TEXT NOT NULL DEFAULT 'Available');
CREATE TABLE IF NOT EXISTS sessions (
 id INTEGER PRIMARY KEY, station_id INTEGER NOT NULL REFERENCES stations(id),
 member_id INTEGER NOT NULL REFERENCES members(id), start TEXT NOT NULL, end TEXT NOT NULL,
 minutes INTEGER NOT NULL, cost INTEGER NOT NULL, rate INTEGER NOT NULL,
 status TEXT NOT NULL DEFAULT 'Active');
CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL);
CREATE TABLE IF NOT EXISTS products (
 id INTEGER PRIMARY KEY, category_id INTEGER NOT NULL REFERENCES categories(id),
 name TEXT NOT NULL, price INTEGER NOT NULL CHECK(price>=0),
 stock INTEGER NOT NULL CHECK(stock>=0), threshold INTEGER NOT NULL CHECK(threshold>=0),
 active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS stock_records (
 id INTEGER PRIMARY KEY, product_id INTEGER NOT NULL REFERENCES products(id),
 quantity INTEGER NOT NULL, reason TEXT NOT NULL, at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS orders (
 id INTEGER PRIMARY KEY, member_id INTEGER NOT NULL REFERENCES members(id),
 station_id INTEGER REFERENCES stations(id), total INTEGER NOT NULL,
 status TEXT NOT NULL DEFAULT 'Pending', at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS order_items (
 id INTEGER PRIMARY KEY, order_id INTEGER NOT NULL REFERENCES orders(id),
 product_id INTEGER NOT NULL REFERENCES products(id), name TEXT NOT NULL,
 qty INTEGER NOT NULL CHECK(qty>0), price INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS events (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, game TEXT NOT NULL, start TEXT NOT NULL,
 end TEXT NOT NULL, fee INTEGER NOT NULL, capacity INTEGER NOT NULL,
 status TEXT NOT NULL DEFAULT 'Open');
CREATE TABLE IF NOT EXISTS teams (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, leader_id INTEGER NOT NULL REFERENCES members(id));
CREATE TABLE IF NOT EXISTS registrations (
 id INTEGER PRIMARY KEY, event_id INTEGER NOT NULL REFERENCES events(id),
 team_id INTEGER NOT NULL REFERENCES teams(id), member_id INTEGER NOT NULL REFERENCES members(id),
 fee INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'Registered', at TEXT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS unique_registration ON registrations(event_id,member_id)
 WHERE status='Registered';
CREATE TABLE IF NOT EXISTS matches (
 id INTEGER PRIMARY KEY, event_id INTEGER NOT NULL REFERENCES events(id),
 round INTEGER NOT NULL, slot INTEGER NOT NULL,
 a INTEGER REFERENCES teams(id), b INTEGER REFERENCES teams(id),
 winner INTEGER REFERENCES teams(id), score TEXT, done INTEGER NOT NULL DEFAULT 0,
 UNIQUE(event_id,round,slot));
CREATE TABLE IF NOT EXISTS bills (
 id INTEGER PRIMARY KEY, member_id INTEGER NOT NULL REFERENCES members(id),
 at TEXT NOT NULL, session_amt INTEGER NOT NULL, order_amt INTEGER NOT NULL,
 event_amt INTEGER NOT NULL, discount INTEGER NOT NULL, total INTEGER NOT NULL,
 points INTEGER NOT NULL, method TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Paid');
CREATE TABLE IF NOT EXISTS charges (
 id INTEGER PRIMARY KEY, member_id INTEGER NOT NULL REFERENCES members(id),
 kind TEXT NOT NULL, source_id INTEGER NOT NULL, amount INTEGER NOT NULL,
 bill_id INTEGER REFERENCES bills(id), void INTEGER NOT NULL DEFAULT 0,
 UNIQUE(kind,source_id));
CREATE TABLE IF NOT EXISTS rewards (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, points INTEGER NOT NULL CHECK(points>0),
 stock INTEGER NOT NULL CHECK(stock>=0), active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS points_transactions (
 id INTEGER PRIMARY KEY, member_id INTEGER NOT NULL REFERENCES members(id),
 bill_id INTEGER REFERENCES bills(id), points INTEGER NOT NULL, reason TEXT NOT NULL, at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS redemptions (
 id INTEGER PRIMARY KEY, member_id INTEGER NOT NULL REFERENCES members(id),
 reward_id INTEGER NOT NULL REFERENCES rewards(id), name TEXT NOT NULL,
 points INTEGER NOT NULL, at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Pending');
CREATE TABLE IF NOT EXISTS staff (
 id INTEGER PRIMARY KEY, user_id INTEGER UNIQUE REFERENCES users(id), name TEXT NOT NULL,
 role TEXT NOT NULL, phone TEXT NOT NULL, hire_date TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS shifts (
 id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL, start TEXT NOT NULL, end TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS rosters (
 id INTEGER PRIMARY KEY, staff_id INTEGER NOT NULL REFERENCES staff(id),
 shift_id INTEGER NOT NULL REFERENCES shifts(id), start TEXT NOT NULL, end TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'Scheduled', note TEXT NOT NULL DEFAULT '');
CREATE TABLE IF NOT EXISTS attendance (
 id INTEGER PRIMARY KEY, roster_id INTEGER UNIQUE NOT NULL REFERENCES rosters(id),
 clock_in TEXT NOT NULL, clock_out TEXT, late INTEGER NOT NULL DEFAULT 0,
 early INTEGER NOT NULL DEFAULT 0, minutes INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS product_favorites (
 user_id INTEGER NOT NULL REFERENCES users(id), product_id INTEGER NOT NULL REFERENCES products(id),
 PRIMARY KEY(user_id,product_id));
CREATE TABLE IF NOT EXISTS crew_tasks (
 id INTEGER PRIMARY KEY, staff_id INTEGER NOT NULL REFERENCES staff(id), title TEXT NOT NULL,
 day TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'To do', completed_at TEXT);
CREATE INDEX IF NOT EXISTS sessions_timer_end ON sessions(status,end);
CREATE INDEX IF NOT EXISTS sessions_timer_start ON sessions(status,start);
CREATE INDEX IF NOT EXISTS crew_tasks_day ON crew_tasks(day,staff_id);
"""


class Store:
    def __init__(self, path=None, seed=True):
        self.path = str(path or BASE / "ctfly_v3.db")
        self.user = None
        with self.transaction() as db:
            db.executescript(SCHEMA)
            if seed and not db.execute("SELECT 1 FROM users").fetchone():
                self._seed(db)
            if seed and not db.execute("SELECT 1 FROM settings WHERE key='menu_photos_v1'").fetchone():
                for category,name,price,stock in [('Drinks','Iced lemon tea',550,20),('Snacks','Crispy chicken burger',1250,12),('Snacks','French fries',600,20)]:
                    row=db.execute('SELECT id FROM categories WHERE name=?',(category,)).fetchone()
                    if not row:
                        cid=db.execute('INSERT INTO categories(name) VALUES(?)',(category,)).lastrowid
                    else:
                        cid=row['id']
                    if not db.execute('SELECT 1 FROM products WHERE lower(name)=lower(?)',(name,)).fetchone():
                        pid=db.execute('INSERT INTO products(category_id,name,price,stock,threshold) VALUES(?,?,?,?,5)',(cid,name,price,stock)).lastrowid
                        db.execute('INSERT INTO stock_records(product_id,quantity,reason,at) VALUES(?,?,?,?)',(pid,stock,'New menu opening stock',stamp()))
                db.execute("INSERT INTO settings VALUES('menu_photos_v1','1')")

    @contextmanager
    def transaction(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        try:
            db.execute("BEGIN IMMEDIATE")
            yield db
            db.commit()
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def rows(self, sql, params=()):
        # Read queries must not take SQLite's writer lock.
        db=sqlite3.connect(self.path,timeout=10)
        db.row_factory=sqlite3.Row
        try:
            return [dict(r) for r in db.execute(sql, params)]
        finally:
            db.close()

    def one(self, sql, params=()):
        values = self.rows(sql, params)
        return values[0] if values else None

    def favorite(self,pid):
        if not self.user:
            raise ValueError('Please sign in first.')
        with self.transaction() as db:
            if not db.execute('SELECT 1 FROM products WHERE id=? AND active=1',(pid,)).fetchone():
                raise ValueError('This item is no longer on the menu.')
            pair=(self.user['id'],pid)
            if db.execute('SELECT 1 FROM product_favorites WHERE user_id=? AND product_id=?',pair).fetchone():
                db.execute('DELETE FROM product_favorites WHERE user_id=? AND product_id=?',pair)
            else:
                db.execute('INSERT INTO product_favorites VALUES(?,?)',pair)

    def save_task(self,staff_id,title,day):
        self.require(True)
        date.fromisoformat(day)
        if not title.strip():
            raise ValueError('Enter a task description.')
        with self.transaction() as db:
            if not db.execute('SELECT 1 FROM staff WHERE id=? AND active=1',(staff_id,)).fetchone():
                raise ValueError('Choose active staff.')
            return db.execute('INSERT INTO crew_tasks(staff_id,title,day) VALUES(?,?,?)',(staff_id,title.strip(),day)).lastrowid

    def task_action(self,tid,action):
        self.require()
        if action not in ('Done','To do','Delete'):
            raise ValueError('Choose a valid task action.')
        with self.transaction() as db:
            task=db.execute('SELECT t.*,s.user_id FROM crew_tasks t JOIN staff s ON s.id=t.staff_id WHERE t.id=?',(tid,)).fetchone()
            if not task or (self.user['role']!='admin' and task['user_id']!=self.user['id']):
                raise ValueError('You can update only your own tasks.')
            if action=='Delete':
                self.require(True)
                db.execute('DELETE FROM crew_tasks WHERE id=?',(tid,))
            else:
                db.execute('UPDATE crew_tasks SET status=?,completed_at=? WHERE id=?',(action,stamp() if action=='Done' else None,tid))

    def _seed(self, db):
        for username, name, role, pw in [
            ("admin", "Alex Tan", "admin", "Admin@123"),
            ("staff1", "Jamie Lim", "staff", "Staff@123"),
            ("gamer1", "Ryan Lee", "customer", "Gamer@123")]:
            uid = db.execute("INSERT INTO users(username,password,name,dob,role) VALUES(?,?,?,?,?)",
                             (username, password_hash(pw), name, "2000-01-01", role)).lastrowid
            if role == "customer":
                db.execute("INSERT INTO members(user_id) VALUES(?)", (uid,))
            else:
                db.execute("INSERT INTO staff(user_id,name,role,phone,hire_date) VALUES(?,?,?,?,?)",
                           (uid, name, "Manager" if role == "admin" else "Crew", "0123456789", str(now().date())))
        for i in range(1, 17):
            vip = i > 12
            db.execute("INSERT INTO stations(name,zone,spec,rate) VALUES(?,?,?,?)",
                       (f"PC-{i:02}", "VIP" if vip else "Standard",
                        "RTX 4070 / Ryzen 7 / 240 Hz" if vip else "RTX 3060 / Ryzen 5 / 144 Hz", 1000 if vip else 600))
        for name in ["Drinks", "Snacks", "Game cards"]:
            db.execute("INSERT INTO categories(name) VALUES(?)", (name,))
        for cat, name, price, stock in [(1,"Iced coffee",650,24),(1,"Energy drink",800,18),
                                       (1,"Mineral water",250,30),(2,"Cup noodles",550,12),
                                       (2,"Potato chips",450,4),(3,"Steam RM 20 card",2000,6)]:
            pid = db.execute("INSERT INTO products(category_id,name,price,stock,threshold) VALUES(?,?,?,?,5)",
                             (cat,name,price,stock)).lastrowid
            db.execute("INSERT INTO stock_records(product_id,quantity,reason,at) VALUES(?,?,?,?)",
                       (pid,stock,"Opening stock",stamp()))
        for name, game, days in [("Friday Night Valorant","Valorant",3),("Weekend FC Cup","EA Sports FC",5)]:
            start = now().replace(hour=18,minute=0,second=0) + timedelta(days=days)
            db.execute("INSERT INTO events(name,game,start,end,fee,capacity) VALUES(?,?,?,?,?,?)",
                       (name,game,stamp(start),stamp(start+timedelta(hours=4)),1500,8))
        for name, points, stock in [("1 hour Standard station voucher",100,20),("Free iced coffee",70,15),("CTFLY mouse pad",250,8)]:
            db.execute("INSERT INTO rewards(name,points,stock) VALUES(?,?,?)", (name,points,stock))
        for name, start, end in [("Morning","09:00","17:00"),("Evening","17:00","01:00")]:
            db.execute("INSERT INTO shifts(name,start,end) VALUES(?,?,?)", (name,start,end))

    def require(self, admin=False):
        if not self.user:
            raise ValueError("Please sign in first.")
        if self.user["role"] not in (("admin",) if admin else ("admin","staff")):
            raise ValueError("This action requires staff access.")

    def owns(self, member_id):
        if not self.user:
            raise ValueError("Please sign in first.")
        if self.user["role"] == "customer" and self.user.get("member_id") != int(member_id):
            raise ValueError("You can only manage your own account.")

    def _member(self, db, member_id):
        self.owns(member_id)
        row = db.execute("SELECT m.*,u.dob,u.active FROM members m JOIN users u ON u.id=m.user_id WHERE m.id=?", (member_id,)).fetchone()
        if not row or not row["active"]:
            raise ValueError("Member is inactive or does not exist.")
        adult(row["dob"])
        return row

    def login(self, username, password, portal=None):
        self.user = None
        if portal not in (None, 'customer', 'staff'):
            raise ValueError("Choose Customer Login or Staff / Admin Login.")
        row = self.one("SELECT u.*,m.id member_id FROM users u LEFT JOIN members m ON m.user_id=u.id WHERE (u.username=? OR u.email=?) AND u.active=1", (username.strip(),username.strip()))
        if not row or not password_matches(password, row["password"]):
            raise ValueError("Incorrect username or password.")
        if portal == 'customer' and row['role'] != 'customer':
            raise ValueError("This is a staff account. Choose Staff / Admin Login above.")
        if portal == 'staff' and row['role'] not in ('staff', 'admin'):
            raise ValueError("This is a customer account. Choose Customer Login above.")
        row.pop("password", None)
        self.user = row
        return row

    def register(self, username, password, name, email, phone, dob):
        adult(dob)
        if len(username.strip()) < 3 or not name.strip():
            raise ValueError("Enter your name and a username of at least 3 characters.")
        if len(password) < 8:
            raise ValueError("Use a password with at least 8 characters.")
        if email and ("@" not in email or "." not in email.split("@")[-1]):
            raise ValueError("Enter a valid email address.")
        with self.transaction() as db:
            uid = db.execute("INSERT INTO users(username,password,name,email,phone,dob,role) VALUES(?,?,?,?,?,?,'customer')",
                             (username.strip(),password_hash(password),name.strip(),email.strip() or None,phone.strip(),dob)).lastrowid
            db.execute("INSERT INTO members(user_id) VALUES(?)", (uid,))
        return self.login(username,password)

    def google_login(self, identity, dob=None, phone=""):
        # identity must come from google_oauth.sign_in's verified ID token.
        self.user = None
        sub, email = identity["sub"], identity["email"]
        row = self.one("SELECT u.*,m.id member_id FROM users u LEFT JOIN members m ON m.user_id=u.id WHERE google_sub=?", (sub,))
        if row:
            if not row["active"]:
                raise ValueError("This account has been deactivated.")
            if row['role'] != 'customer':
                raise ValueError("Use Staff / Admin Login with your staff username and password.")
            adult(row["dob"])
            row.pop("password",None)
            self.user = row
            return row
        if self.one("SELECT id FROM users WHERE email=?", (email,)):
            raise ValueError("Email already belongs to a local account. Sign in with its password; automatic linking is disabled.")
        if dob is None:
            return None
        adult(dob)
        with self.transaction() as db:
            uid = db.execute("INSERT INTO users(username,name,email,phone,dob,role,google_sub) VALUES(?,?,?,?,?,'customer',?)",
                             ("google_"+secrets.token_hex(8),identity.get("name",email),email,phone,dob,sub)).lastrowid
            mid = db.execute("INSERT INTO members(user_id) VALUES(?)",(uid,)).lastrowid
        self.user = self.one("SELECT id,username,name,email,phone,dob,role,google_sub,active FROM users WHERE id=?",(uid,))
        self.user["member_id"] = mid
        return self.user

    def members(self):
        self.require()
        return self.rows("SELECT m.id,u.name,u.phone,u.dob,m.tier,m.points,m.spent,u.active FROM members m JOIN users u ON u.id=m.user_id ORDER BY m.id")

    def member(self, mid):
        self.owns(mid)
        return self.one("SELECT m.*,u.name,u.phone,u.dob,u.active FROM members m JOIN users u ON u.id=m.user_id WHERE m.id=?", (mid,))

    def edit_member(self, mid, name, phone, dob, active):
        self.require()
        adult(dob)
        if not name.strip():
            raise ValueError("Name is required.")
        with self.transaction() as db:
            db.execute("UPDATE users SET name=?,phone=?,dob=?,active=? WHERE id=(SELECT user_id FROM members WHERE id=?)",(name.strip(),phone,dob,int(active),mid))

    def save_station(self, sid, name, zone, spec, rate):
        self.require(True)
        if zone not in ("Standard","VIP") or not name.strip() or not spec.strip() or rate <= 0:
            raise ValueError("Station needs a name, specification, zone and positive hourly rate.")
        with self.transaction() as db:
            if sid:
                db.execute("UPDATE stations SET name=?,zone=?,spec=?,rate=? WHERE id=?", (name.strip(),zone,spec,rate,sid))
            else:
                db.execute("INSERT INTO stations(name,zone,spec,rate) VALUES(?,?,?,?)", (name.strip(),zone,spec,rate))

    def station_state(self, sid, maintenance=False, remove=False):
        self.require(True)
        with self.transaction() as db:
            if db.execute("SELECT 1 FROM sessions WHERE station_id=? AND status IN ('Active','Reserved')",(sid,)).fetchone():
                raise ValueError("End or cancel this station's sessions first.")
            if remove:
                # Retain foreign keys for historical reports.
                db.execute("UPDATE stations SET status='Retired' WHERE id=?",(sid,))
            else:
                db.execute("UPDATE stations SET status=? WHERE id=?",("Maintenance" if maintenance else "Available",sid))

    def _charge(self, db, mid, kind, source, amount):
        db.execute("INSERT INTO charges(member_id,kind,source_id,amount) VALUES(?,?,?,?)",(mid,kind,source,amount))

    def book(self, sid, mid, minutes, start=None):
        if minutes not in (60,120,180,240,480):
            raise ValueError("Select a valid session package.")
        current = now()
        start = datetime.fromisoformat(start) if start else current
        if start < current - timedelta(minutes=1):
            raise ValueError("Booking cannot start in the past.")
        end = start + timedelta(minutes=minutes)
        with self.transaction() as db:
            self._member(db,mid)
            station = db.execute("SELECT * FROM stations WHERE id=?",(sid,)).fetchone()
            if not station or station["status"] in ("Maintenance","Retired"):
                raise ValueError("Station is unavailable.")
            if db.execute("SELECT 1 FROM sessions WHERE station_id=? AND status IN ('Active','Reserved') AND start<? AND end>?",(sid,stamp(end),stamp(start))).fetchone():
                raise ValueError("This time overlaps another session or reservation.")
            cost = (station["rate"] * minutes + 30)//60
            status = "Reserved" if start > current else "Active"
            session = db.execute("INSERT INTO sessions(station_id,member_id,start,end,minutes,cost,rate,status) VALUES(?,?,?,?,?,?,?,?)",(sid,mid,stamp(start),stamp(end),minutes,cost,station["rate"],status)).lastrowid
            if status == "Active":
                self._charge(db,mid,"Session",session,cost)
            self._sync_station(db,sid,current)
        return session

    def _sync_station(self, db, sid, current):
        active = db.execute("SELECT 1 FROM sessions WHERE station_id=? AND status='Active'",(sid,)).fetchone()
        reserved = db.execute("SELECT 1 FROM sessions WHERE station_id=? AND status='Reserved'",(sid,)).fetchone()
        db.execute("UPDATE stations SET status=? WHERE id=? AND status NOT IN ('Maintenance','Retired')",("Occupied" if active else "Reserved" if reserved else "Available",sid))

    def tick(self):
        expired = []
        current = now()
        due="SELECT * FROM sessions WHERE (status='Active' AND end<=?) OR (status='Reserved' AND start<=?)"
        params=(stamp(current),stamp(current))
        if not self.one(due,params):
            return expired
        with self.transaction() as db:
            for row in db.execute(due,params).fetchall():
                if row["end"] <= stamp(current):
                    db.execute("UPDATE sessions SET status='Completed' WHERE id=?",(row["id"],))
                    if row["status"] == "Reserved":
                        self._charge(db,row["member_id"],"Session",row["id"],row["cost"])
                    expired.append(row["station_id"])
                elif row["status"] == "Reserved" and row["start"] <= stamp(current):
                    db.execute("UPDATE sessions SET status='Active' WHERE id=?",(row["id"],))
                    self._charge(db,row["member_id"],"Session",row["id"],row["cost"])
                self._sync_station(db,row["station_id"],current)
        return expired

    def change_session(self, session_id, action):
        with self.transaction() as db:
            row = db.execute("SELECT * FROM sessions WHERE id=?",(session_id,)).fetchone()
            if not row:
                raise ValueError("Session not found.")
            self.owns(row["member_id"])
            if action == "Cancel":
                if row["status"] != "Reserved":
                    raise ValueError("Only reservations which have not started can be cancelled.")
                db.execute("UPDATE sessions SET status='Cancelled' WHERE id=?",(session_id,))
            elif action == "End":
                self.require()
                if row["status"] != "Active":
                    raise ValueError("Select an active session.")
                db.execute("UPDATE sessions SET status='Completed',end=? WHERE id=?",(stamp(),session_id))
            elif action == "Extend":
                if row["status"] != "Active":
                    raise ValueError("Only active sessions can be extended.")
                charge = db.execute("SELECT bill_id FROM charges WHERE kind='Session' AND source_id=?",(session_id,)).fetchone()
                if charge and charge["bill_id"]:
                    raise ValueError("Already billed. Start a new session after this one ends.")
                end = datetime.fromisoformat(row["end"])+timedelta(hours=1)
                if db.execute("SELECT 1 FROM sessions WHERE station_id=? AND id!=? AND status IN ('Reserved','Active') AND start<? AND end>?",(row["station_id"],session_id,stamp(end),row["end"])).fetchone():
                    raise ValueError("Extension overlaps the next reservation.")
                db.execute("UPDATE sessions SET end=?,minutes=minutes+60,cost=cost+rate WHERE id=?",(stamp(end),session_id))
                db.execute("UPDATE charges SET amount=amount+? WHERE kind='Session' AND source_id=?",(row["rate"],session_id))
            else:
                raise ValueError("Unknown session action.")
            self._sync_station(db,row["station_id"],now())

    def save_category(self, cid, name, delete=False):
        self.require(True)
        if not name.strip() and not delete:
            raise ValueError("Category name is required.")
        with self.transaction() as db:
            if delete:
                if db.execute("SELECT 1 FROM products WHERE category_id=?",(cid,)).fetchone():
                    raise ValueError("Move products to another category before deleting this category.")
                db.execute("DELETE FROM categories WHERE id=?",(cid,))
            elif cid:
                db.execute("UPDATE categories SET name=? WHERE id=?",(name.strip(),cid))
            else:
                db.execute("INSERT INTO categories(name) VALUES(?)",(name.strip(),))

    def save_product(self, pid, name, category, price, stock, threshold):
        self.require(True)
        if not name.strip() or min(price,stock,threshold)<0:
            raise ValueError("Enter a name and nonnegative price, stock and threshold.")
        with self.transaction() as db:
            if pid:
                db.execute("UPDATE products SET name=?,category_id=?,price=?,threshold=? WHERE id=?",(name.strip(),category,price,threshold,pid))
            else:
                pid = db.execute("INSERT INTO products(name,category_id,price,stock,threshold) VALUES(?,?,?,?,?)",(name.strip(),category,price,stock,threshold)).lastrowid
                db.execute("INSERT INTO stock_records(product_id,quantity,reason,at) VALUES(?,?,?,?)",(pid,stock,"Opening stock",stamp()))

    def restock(self, pid, qty, reason):
        self.require()
        if qty<=0 or not reason.strip():
            raise ValueError("Enter a positive quantity and a stock note.")
        with self.transaction() as db:
            if not db.execute("SELECT 1 FROM products WHERE id=? AND active=1",(pid,)).fetchone():
                raise ValueError("Select an active product.")
            db.execute("UPDATE products SET stock=stock+? WHERE id=?",(qty,pid))
            db.execute("INSERT INTO stock_records(product_id,quantity,reason,at) VALUES(?,?,?,?)",(pid,qty,reason,stamp()))

    def order(self, mid, sid, cart):
        if not cart:
            raise ValueError("Your cart is empty.")
        with self.transaction() as db:
            self._member(db,mid)
            if sid and not db.execute("SELECT 1 FROM sessions WHERE station_id=? AND member_id=? AND status='Active'",(sid,mid)).fetchone():
                raise ValueError("Seat delivery requires an active session for this member. Choose counter pickup instead.")
            items=[]
            for pid, qty in cart.items():
                row=db.execute("SELECT * FROM products WHERE id=? AND active=1",(pid,)).fetchone()
                if not isinstance(qty,int) or qty<=0 or not row or row["stock"]<qty:
                    raise ValueError("Stock changed or quantity is invalid. Review your cart.")
                items.append((row,qty))
            total=sum(row["price"]*qty for row,qty in items)
            oid=db.execute("INSERT INTO orders(member_id,station_id,total,at) VALUES(?,?,?,?)",(mid,sid,total,stamp())).lastrowid
            for row,qty in items:
                db.execute("INSERT INTO order_items(order_id,product_id,name,qty,price) VALUES(?,?,?,?,?)",(oid,row["id"],row["name"],qty,row["price"]))
                db.execute("UPDATE products SET stock=stock-? WHERE id=?",(qty,row["id"]))
                db.execute("INSERT INTO stock_records(product_id,quantity,reason,at) VALUES(?,?,?,?)",(row["id"],-qty,f"Order #{oid}",stamp()))
            self._charge(db,mid,"Order",oid,total)
        return oid

    def order_action(self, oid, action):
        with self.transaction() as db:
            row=db.execute("SELECT * FROM orders WHERE id=?",(oid,)).fetchone()
            if not row:
                raise ValueError("Order not found.")
            self.owns(row["member_id"])
            if action=="Cancel":
                if row["status"]!="Pending":
                    raise ValueError("Only pending orders can be cancelled.")
                charge=db.execute("SELECT * FROM charges WHERE kind='Order' AND source_id=?",(oid,)).fetchone()
                if charge["bill_id"]:
                    raise ValueError("Void the bill before cancelling a paid order.")
                for item in db.execute("SELECT * FROM order_items WHERE order_id=?",(oid,)).fetchall():
                    db.execute("UPDATE products SET stock=stock+? WHERE id=?",(item["qty"],item["product_id"]))
                    db.execute("INSERT INTO stock_records(product_id,quantity,reason,at) VALUES(?,?,?,?)",(item["product_id"],item["qty"],f"Cancelled order #{oid}",stamp()))
                db.execute("UPDATE charges SET void=1 WHERE kind='Order' AND source_id=?",(oid,))
                db.execute("UPDATE orders SET status='Cancelled' WHERE id=?",(oid,))
            else:
                self.require()
                nxt={"Pending":"Delivering","Delivering":"Delivered","Delivered":"Completed"}.get(row["status"])
                if not nxt:
                    raise ValueError("This order has no next delivery stage.")
                db.execute("UPDATE orders SET status=? WHERE id=?",(nxt,oid))

    def deactivate(self, table, rid):
        self.require(True)
        if table not in ("products","rewards","staff"):
            raise ValueError("Unsupported record type.")
        with self.transaction() as db:
            if table=="staff":
                if db.execute("SELECT 1 FROM rosters WHERE staff_id=? AND status='Scheduled' AND end>?",(rid,stamp())).fetchone():
                    raise ValueError("Cancel upcoming shifts before removing this staff member.")
                db.execute("UPDATE users SET active=0 WHERE id=(SELECT user_id FROM staff WHERE id=?)",(rid,))
            db.execute(f"UPDATE {table} SET active=0 WHERE id=?",(rid,))

    def save_event(self, eid, name, game, start, end, fee, capacity):
        self.require()
        begin, finish=datetime.fromisoformat(start),datetime.fromisoformat(end)
        if not name.strip() or not game.strip() or finish<=begin or capacity<2 or capacity>64 or fee<0:
            raise ValueError("Event needs a name, game, valid time range and 2–64 slots.")
        if begin<now():
            raise ValueError("Schedule events in the future.")
        with self.transaction() as db:
            if eid:
                row=db.execute("SELECT * FROM events WHERE id=?",(eid,)).fetchone()
                if not row or row["status"] not in ("Open","Full"):
                    raise ValueError("Only events before bracket generation can be edited.")
                count=db.execute("SELECT count(*) FROM registrations WHERE event_id=? AND status='Registered'",(eid,)).fetchone()[0]
                if capacity<count:
                    raise ValueError("Capacity cannot be below existing registrations.")
            if db.execute("SELECT 1 FROM events WHERE id!=? AND status!='Cancelled' AND start<? AND end>?",(eid or 0,stamp(finish),stamp(begin))).fetchone():
                raise ValueError("Another event already uses the tournament arena during this period.")
            if eid:
                db.execute("UPDATE events SET name=?,game=?,start=?,end=?,fee=?,capacity=?,status=? WHERE id=?",(name,game,stamp(begin),stamp(finish),fee,capacity,"Full" if count==capacity else "Open",eid))
            else:
                db.execute("INSERT INTO events(name,game,start,end,fee,capacity) VALUES(?,?,?,?,?,?)",(name,game,stamp(begin),stamp(finish),fee,capacity))

    def register_team(self, eid, mid, name):
        if not name.strip():
            raise ValueError("Team/player name is required.")
        with self.transaction() as db:
            self._member(db,mid)
            event=db.execute("SELECT * FROM events WHERE id=?",(eid,)).fetchone()
            if not event or event["status"]!="Open" or event["start"]<=stamp():
                raise ValueError("Registration is closed.")
            if db.execute("SELECT 1 FROM teams t JOIN registrations r ON r.team_id=t.id WHERE r.event_id=? AND r.status='Registered' AND lower(t.name)=lower(?)",(eid,name.strip())).fetchone():
                raise ValueError("This team name is already registered.")
            count=db.execute("SELECT count(*) FROM registrations WHERE event_id=? AND status='Registered'",(eid,)).fetchone()[0]
            if count>=event["capacity"]:
                raise ValueError("Event is full.")
            tid=db.execute("INSERT INTO teams(name,leader_id) VALUES(?,?)",(name.strip(),mid)).lastrowid
            rid=db.execute("INSERT INTO registrations(event_id,team_id,member_id,fee,at) VALUES(?,?,?,?,?)",(eid,tid,mid,event["fee"],stamp())).lastrowid
            self._charge(db,mid,"Event",rid,event["fee"])
            if count+1==event["capacity"]:
                db.execute("UPDATE events SET status='Full' WHERE id=?",(eid,))

    def withdraw(self, rid):
        with self.transaction() as db:
            row=db.execute("SELECT r.*,e.status event_status FROM registrations r JOIN events e ON e.id=r.event_id WHERE r.id=?",(rid,)).fetchone()
            if not row:
                raise ValueError("Registration not found.")
            self.owns(row["member_id"])
            if row["status"]!="Registered" or row["event_status"] not in ("Open","Full"):
                raise ValueError("Withdrawal is only available before bracket generation.")
            charge=db.execute("SELECT * FROM charges WHERE kind='Event' AND source_id=?",(rid,)).fetchone()
            if charge["bill_id"]:
                raise ValueError("Void the bill before withdrawing a paid entry.")
            db.execute("UPDATE registrations SET status='Withdrawn' WHERE id=?",(rid,))
            db.execute("UPDATE charges SET void=1 WHERE kind='Event' AND source_id=?",(rid,))
            db.execute("UPDATE events SET status='Open' WHERE id=?",(row["event_id"],))

    def cancel_event(self, eid):
        self.require()
        with self.transaction() as db:
            event=db.execute("SELECT status FROM events WHERE id=?",(eid,)).fetchone()
            if not event or event["status"] not in ("Open","Full"):
                raise ValueError("Only unstarted events can be cancelled.")
            if db.execute("SELECT 1 FROM charges c JOIN registrations r ON c.source_id=r.id AND c.kind='Event' WHERE r.event_id=? AND c.bill_id IS NOT NULL",(eid,)).fetchone():
                raise ValueError("Void paid entry bills before cancelling this event.")
            db.execute("UPDATE charges SET void=1 WHERE kind='Event' AND source_id IN (SELECT id FROM registrations WHERE event_id=?)",(eid,))
            db.execute("UPDATE registrations SET status='Withdrawn' WHERE event_id=?",(eid,))
            db.execute("UPDATE events SET status='Cancelled' WHERE id=?",(eid,))

    def bracket(self, eid):
        self.require()
        with self.transaction() as db:
            event=db.execute("SELECT * FROM events WHERE id=?",(eid,)).fetchone()
            if not event or event["status"] not in ("Open","Full"):
                raise ValueError("Bracket has already started or event is closed.")
            teams=[r[0] for r in db.execute("SELECT team_id FROM registrations WHERE event_id=? AND status='Registered' ORDER BY id",(eid,))]
            if len(teams)<2:
                raise ValueError("At least two teams are required.")
            size=2**math.ceil(math.log2(len(teams)))
            # Put byes in separate first-round matches.
            byes=size-len(teams)
            pairs=[(teams[i],None) for i in range(byes)]
            remaining=teams[byes:]
            pairs.extend(zip(remaining[::2],remaining[1::2]))
            rounds=int(math.log2(size))
            for rnd in range(1,rounds+1):
                for slot in range(size//(2**rnd)):
                    a,b=pairs[slot] if rnd==1 else (None,None)
                    db.execute("INSERT INTO matches(event_id,round,slot,a,b) VALUES(?,?,?,?,?)",(eid,rnd,slot,a,b))
            db.execute("UPDATE events SET status='Ongoing' WHERE id=?",(eid,))
            for row in db.execute("SELECT * FROM matches WHERE event_id=? AND round=1 AND b IS NULL",(eid,)).fetchall():
                self._advance(db,row,row["a"],"BYE")

    def _advance(self, db, row, winner, score):
        db.execute("UPDATE matches SET winner=?,score=?,done=1 WHERE id=?",(winner,score,row["id"]))
        following=db.execute("SELECT id FROM matches WHERE event_id=? AND round=? AND slot=?",(row["event_id"],row["round"]+1,row["slot"]//2)).fetchone()
        if following:
            column="a" if row["slot"]%2==0 else "b"
            db.execute(f"UPDATE matches SET {column}=? WHERE id=?",(winner,following["id"]))
        else:
            db.execute("UPDATE events SET status='Completed' WHERE id=?",(row["event_id"],))

    def result(self, match_id, score_a, score_b):
        self.require()
        with self.transaction() as db:
            row=db.execute("SELECT * FROM matches WHERE id=?",(match_id,)).fetchone()
            if not row or row["done"] or not row["a"] or not row["b"]:
                raise ValueError("Select a ready, unplayed match with two teams.")
            if min(score_a,score_b)<0 or score_a==score_b:
                raise ValueError("Scores must be nonnegative; knockout matches cannot draw.")
            self._advance(db,row,row["a"] if score_a>score_b else row["b"],f"{score_a} – {score_b}")

    def quote(self, mid, db=None):
        self.owns(mid)
        if db is None:
            with self.transaction() as connection:
                return self.quote(mid,connection)
        member=self._member(db,mid)
        rows=db.execute("SELECT * FROM charges WHERE member_id=? AND bill_id IS NULL AND void=0",(mid,)).fetchall()
        totals={kind:sum(r["amount"] for r in rows if r["kind"]==kind) for kind in ("Session","Order","Event")}
        subtotal=sum(totals.values())
        discount=(subtotal*{"Bronze":0,"Silver":5,"Gold":10}[member["tier"]]+50)//100
        return {**totals,"subtotal":subtotal,"discount":discount,"total":subtotal-discount,"tier":member["tier"],"count":len(rows)}

    def checkout(self, mid, method):
        self.require()
        if method not in ("Cash","Card","TNG eWallet"):
            raise ValueError("Choose a payment method.")
        with self.transaction() as db:
            quote=self.quote(mid,db)
            if not quote["count"]:
                raise ValueError("No unbilled charges. This account is already settled.")
            points=quote["total"]//100
            bid=db.execute("INSERT INTO bills(member_id,at,session_amt,order_amt,event_amt,discount,total,points,method) VALUES(?,?,?,?,?,?,?,?,?)",(mid,stamp(),quote["Session"],quote["Order"],quote["Event"],quote["discount"],quote["total"],points,method)).lastrowid
            db.execute("UPDATE charges SET bill_id=? WHERE member_id=? AND bill_id IS NULL AND void=0",(bid,mid))
            db.execute("UPDATE members SET points=points+?,spent=spent+? WHERE id=?",(points,quote["total"],mid))
            db.execute("INSERT INTO points_transactions(member_id,bill_id,points,reason,at) VALUES(?,?,?,?,?)",(mid,bid,points,f"Paid bill #{bid}",stamp()))
            self._tier(db,mid)
        return bid

    def _tier(self, db, mid):
        db.execute("UPDATE members SET tier=CASE WHEN spent>=100000 THEN 'Gold' WHEN spent>=30000 THEN 'Silver' ELSE 'Bronze' END WHERE id=?",(mid,))

    def void_bill(self, bid):
        self.require(True)
        with self.transaction() as db:
            row=db.execute("SELECT * FROM bills WHERE id=? AND status='Paid'",(bid,)).fetchone()
            if not row:
                raise ValueError("Only paid bills can be voided once.")
            member=db.execute("SELECT * FROM members WHERE id=?",(row["member_id"],)).fetchone()
            if member["points"]<row["points"]:
                raise ValueError("Earned points have been redeemed. Resolve redemptions before voiding.")
            db.execute("UPDATE bills SET status='Void' WHERE id=?",(bid,))
            db.execute("UPDATE charges SET bill_id=NULL WHERE bill_id=?",(bid,))
            db.execute("UPDATE members SET points=points-?,spent=spent-? WHERE id=?",(row["points"],row["total"],row["member_id"]))
            db.execute("INSERT INTO points_transactions(member_id,bill_id,points,reason,at) VALUES(?,?,?,?,?)",(row["member_id"],bid,-row["points"],f"Voided bill #{bid}",stamp()))
            self._tier(db,row["member_id"])

    def save_reward(self, rid, name, points, stock):
        self.require(True)
        if not name.strip() or points<=0 or stock<0:
            raise ValueError("Reward requires a name, positive point cost and nonnegative stock.")
        with self.transaction() as db:
            if rid:
                db.execute("UPDATE rewards SET name=?,points=?,stock=? WHERE id=?",(name,points,stock,rid))
            else:
                db.execute("INSERT INTO rewards(name,points,stock) VALUES(?,?,?)",(name,points,stock))

    def redeem(self, mid, rid):
        with self.transaction() as db:
            member=self._member(db,mid)
            reward=db.execute("SELECT * FROM rewards WHERE id=? AND active=1",(rid,)).fetchone()
            if not reward or reward["stock"]<=0 or member["points"]<reward["points"]:
                raise ValueError("Not enough points, or this reward is out of stock.")
            db.execute("UPDATE members SET points=points-? WHERE id=?",(reward["points"],mid))
            db.execute("UPDATE rewards SET stock=stock-1 WHERE id=?",(rid,))
            redemption=db.execute("INSERT INTO redemptions(member_id,reward_id,name,points,at) VALUES(?,?,?,?,?)",(mid,rid,reward["name"],reward["points"],stamp())).lastrowid
            db.execute("INSERT INTO points_transactions(member_id,points,reason,at) VALUES(?,?,?,?)",(mid,-reward["points"],f"Redeemed {reward['name']} (voucher #{redemption})",stamp()))
        return redemption

    def fulfill_reward(self, rid):
        self.require()
        with self.transaction() as db:
            if not db.execute("SELECT 1 FROM redemptions WHERE id=? AND status='Pending'",(rid,)).fetchone():
                raise ValueError("Select a pending voucher.")
            db.execute("UPDATE redemptions SET status='Collected' WHERE id=?",(rid,))

    def save_staff(self, sid, name, role, phone, hire, username="", password=""):
        self.require(True)
        date.fromisoformat(hire)
        if not name.strip() or not role.strip():
            raise ValueError("Name and job role are required.")
        with self.transaction() as db:
            if sid:
                db.execute("UPDATE staff SET name=?,role=?,phone=?,hire_date=? WHERE id=?",(name,role,phone,hire,sid))
            else:
                uid = None
                if username:
                    if len(username.strip()) < 3 or len(password) < 8:
                        raise ValueError("Staff login needs a username of 3+ characters and password of 8+ characters.")
                    uid = db.execute("INSERT INTO users(username,password,name,phone,dob,role) VALUES(?,?,?,?,?,'staff')",
                                     (username.strip(),password_hash(password),name,phone,"2000-01-01")).lastrowid
                db.execute("INSERT INTO staff(user_id,name,role,phone,hire_date) VALUES(?,?,?,?,?)",(uid,name,role,phone,hire))

    def save_shift(self, sid, name, start, end, delete=False):
        self.require(True)
        with self.transaction() as db:
            if delete:
                if db.execute("SELECT 1 FROM rosters WHERE shift_id=?",(sid,)).fetchone():
                    raise ValueError("Shift is used by historical rosters. Rename or edit it instead.")
                db.execute("DELETE FROM shifts WHERE id=?",(sid,))
                return
            datetime.strptime(start,"%H:%M")
            datetime.strptime(end,"%H:%M")
            if not name.strip() or start==end:
                raise ValueError("Enter a shift name and different start/end times.")
            if sid:
                db.execute("UPDATE shifts SET name=?,start=?,end=? WHERE id=?",(name,start,end,sid))
            else:
                db.execute("INSERT INTO shifts(name,start,end) VALUES(?,?,?)",(name,start,end))

    def roster(self, staff_id, shift_id, day, rid=None):
        self.require(True)
        with self.transaction() as db:
            if not db.execute("SELECT 1 FROM staff WHERE id=? AND active=1",(staff_id,)).fetchone():
                raise ValueError("Choose active staff.")
            shift=db.execute("SELECT * FROM shifts WHERE id=?",(shift_id,)).fetchone()
            if not shift:
                raise ValueError("Choose a valid shift.")
            start=datetime.fromisoformat(f"{day}T{shift['start']}")
            end=datetime.fromisoformat(f"{day}T{shift['end']}")
            if end<=start:
                end+=timedelta(days=1)
            if rid:
                if db.execute("SELECT 1 FROM attendance WHERE roster_id=?",(rid,)).fetchone():
                    raise ValueError("Cannot reschedule a shift after clock-in.")
            if db.execute("SELECT 1 FROM rosters WHERE staff_id=? AND id!=? AND status NOT IN ('Cancelled','Leave approved') AND start<? AND end>?",(staff_id,rid or 0,stamp(end),stamp(start))).fetchone():
                raise ValueError("This staff member already has an overlapping shift (including overnight shifts).")
            if rid:
                db.execute("UPDATE rosters SET staff_id=?,shift_id=?,start=?,end=?,status='Scheduled',note='' WHERE id=?",(staff_id,shift_id,stamp(start),stamp(end),rid))
            else:
                db.execute("INSERT INTO rosters(staff_id,shift_id,start,end) VALUES(?,?,?,?)",(staff_id,shift_id,stamp(start),stamp(end)))

    def weekly_roster(self, staff_id, shift_id, first_day, weekdays):
        """All-or-nothing seven-day assignment, including overnight overlap checks."""
        self.require(True)
        first=date.fromisoformat(first_day)
        if not weekdays or any(day not in range(7) for day in weekdays):
            raise ValueError("Choose at least one weekday.")
        with self.transaction() as db:
            if not db.execute("SELECT 1 FROM staff WHERE id=? AND active=1",(staff_id,)).fetchone():
                raise ValueError("Choose active staff.")
            shift=db.execute("SELECT * FROM shifts WHERE id=?",(shift_id,)).fetchone()
            if not shift:
                raise ValueError("Choose a valid shift.")
            for offset in range(7):
                day=first+timedelta(days=offset)
                if day.weekday() not in weekdays:
                    continue
                start=datetime.fromisoformat(f"{day}T{shift['start']}")
                end=datetime.fromisoformat(f"{day}T{shift['end']}")
                if end<=start:
                    end+=timedelta(days=1)
                if db.execute("SELECT 1 FROM rosters WHERE staff_id=? AND status NOT IN ('Cancelled','Leave approved') AND start<? AND end>?",(staff_id,stamp(end),stamp(start))).fetchone():
                    raise ValueError(f"Conflict on {day}. No assignments were created for this week.")
                db.execute("INSERT INTO rosters(staff_id,shift_id,start,end) VALUES(?,?,?,?)",(staff_id,shift_id,stamp(start),stamp(end)))

    def rename_team(self, rid, name):
        if not name.strip():
            raise ValueError("Team/player name is required.")
        with self.transaction() as db:
            row=db.execute("SELECT r.*,e.status event_status FROM registrations r JOIN events e ON e.id=r.event_id WHERE r.id=?",(rid,)).fetchone()
            if not row:
                raise ValueError("Registration not found.")
            self.owns(row["member_id"])
            if row["status"]!='Registered' or row["event_status"] not in ('Open','Full'):
                raise ValueError("Only registered teams before bracket generation can be renamed.")
            if db.execute("SELECT 1 FROM teams t JOIN registrations r ON r.team_id=t.id WHERE r.event_id=? AND r.status='Registered' AND r.id!=? AND lower(t.name)=lower(?)",(row["event_id"],rid,name.strip())).fetchone():
                raise ValueError("This team name is already in use.")
            db.execute("UPDATE teams SET name=? WHERE id=?",(name.strip(),row["team_id"]))

    def metrics(self, module):
        self.require()
        if module=='stations':
            end=now()
            begin=end-timedelta(days=7)
            rows=self.rows("SELECT start,end FROM sessions WHERE status IN ('Active','Completed') AND start<? AND end>?",(stamp(end),stamp(begin)))
            busy=sum(max(0,(min(end,datetime.fromisoformat(r['end']))-max(begin,datetime.fromisoformat(r['start']))).total_seconds()) for r in rows)
            count=self.one("SELECT count(*) n FROM stations WHERE status!='Retired'")['n']
            utilization=busy/(7*86400*count)*100 if count else 0
            hours=self.rows("SELECT substr(start,12,2) Hour,count(*) Sessions FROM sessions WHERE status IN ('Active','Completed') GROUP BY Hour ORDER BY Sessions DESC")
            return f"Last 7 days · scheduled utilization {utilization:.1f}% (24h capacity) · busiest start hour {hours[0]['Hour']+':00' if hours else '—'}"
        if module=='shop':
            value=self.one("SELECT coalesce(sum(total),0) amount FROM orders WHERE substr(at,1,10)=? AND status!='Cancelled'",(str(now().date()),))['amount']
            return f"Today's order charges {money(value)} · gross before discounts/payment · low stock appears in amber"
        if module=='billing':
            value=self.one("SELECT count(*) n FROM redemptions")['n']
            return f"{value} reward vouchers redeemed · paid revenue excludes void bills"
        if module=='staff':
            rows=self.report('staff')
            due=self.one("SELECT count(*) n FROM rosters WHERE end<? AND status IN ('Scheduled','Leave requested')",(stamp(),))['n']
            attended=self.one("SELECT count(*) n FROM rosters r JOIN attendance a ON a.roster_id=r.id WHERE r.end<? AND r.status IN ('Scheduled','Leave requested')",(stamp(),))['n']
            rate=100*attended/due if due else 0
            monday=now().date()-timedelta(days=now().weekday())
            value=self.one("SELECT coalesce(sum(minutes),0) n FROM attendance WHERE substr(clock_in,1,10)>=? AND substr(clock_in,1,10)<?",(str(monday),str(monday+timedelta(days=7))))['n']
            return f"Attendance on ended shifts {rate:.1f}% · this week's completed work hours {value/60:.2f}"
        return "Registration fees are gross charges; paid fees appear in the billing report."

    def _staff_owns(self, db, row):
        self.require()
        staff=db.execute("SELECT user_id FROM staff WHERE id=?",(row["staff_id"],)).fetchone()
        if self.user["role"]!="admin" and (not staff or staff["user_id"]!=self.user["id"]):
            raise ValueError("Staff can only clock or request leave for their own shifts.")

    def roster_action(self, rid, action, note=""):
        with self.transaction() as db:
            row=db.execute("SELECT * FROM rosters WHERE id=?",(rid,)).fetchone()
            if not row:
                raise ValueError("Select a roster entry.")
            if db.execute("SELECT 1 FROM attendance WHERE roster_id=?",(rid,)).fetchone():
                raise ValueError("Cannot cancel or take leave after clock-in.")
            if action=="Leave requested":
                self._staff_owns(db,row)
                if row["status"]!="Scheduled" or not note.strip():
                    raise ValueError("Leave request requires a scheduled shift and reason.")
            else:
                self.require(True)
                if action not in ("Cancelled","Leave approved","Scheduled"):
                    raise ValueError("Invalid roster action.")
                if action in ("Leave approved","Scheduled") and row["status"]!="Leave requested":
                    raise ValueError("Select a pending leave request.")
            db.execute("UPDATE rosters SET status=?,note=? WHERE id=?",(action,note,rid))

    def clock(self, rid, out=False, at=None):
        current=at or now()
        with self.transaction() as db:
            row=db.execute("SELECT * FROM rosters WHERE id=?",(rid,)).fetchone()
            if not row:
                raise ValueError("Roster entry not found.")
            self._staff_owns(db,row)
            if row["status"] not in ("Scheduled","Leave requested"):
                raise ValueError("This shift is cancelled or on approved leave.")
            attendance=db.execute("SELECT * FROM attendance WHERE roster_id=?",(rid,)).fetchone()
            if out:
                if not attendance or attendance["clock_out"]:
                    raise ValueError("Clock in first; each shift can only be closed once.")
                clockin=datetime.fromisoformat(attendance["clock_in"])
                if current<clockin:
                    raise ValueError("Clock-out cannot be before clock-in.")
                early=max(0,int((datetime.fromisoformat(row["end"])-current).total_seconds()//60))
                minutes=int((current-clockin).total_seconds()//60)
                db.execute("UPDATE attendance SET clock_out=?,early=?,minutes=? WHERE id=?",(stamp(current),early,minutes,attendance["id"]))
            else:
                if attendance:
                    raise ValueError("Already clocked in.")
                if current<datetime.fromisoformat(row["start"])-timedelta(hours=1) or current>=datetime.fromisoformat(row["end"]):
                    raise ValueError("Clock-in opens one hour before the shift and closes at shift end.")
                late=max(0,int((current-datetime.fromisoformat(row["start"])).total_seconds()//60))
                db.execute("INSERT INTO attendance(roster_id,clock_in,late) VALUES(?,?,?)",(rid,stamp(current),late))

    def report(self, module):
        self.require()
        if module=="stations":
            return self.rows("SELECT st.name Station,st.zone Zone,count(s.id) Sessions,round(coalesce(sum(s.minutes),0)/60.0,2) Hours,round(coalesce(avg(s.minutes),0),1) Average_minutes FROM stations st LEFT JOIN sessions s ON s.station_id=st.id AND s.status IN ('Active','Completed') GROUP BY st.id")
        if module=="shop":
            return self.rows("SELECT p.name Product,p.stock Stock,p.threshold Reorder_level,coalesce(sum(CASE WHEN o.status!='Cancelled' THEN i.qty ELSE 0 END),0) Units_sold,round(coalesce(sum(CASE WHEN o.status!='Cancelled' THEN i.qty*i.price ELSE 0 END),0)/100.0,2) Gross_RM FROM products p LEFT JOIN order_items i ON i.product_id=p.id LEFT JOIN orders o ON o.id=i.order_id GROUP BY p.id ORDER BY Units_sold DESC")
        if module=="events":
            return self.rows("SELECT e.game Game,count(DISTINCT e.id) Events,count(r.id) Teams,round(coalesce(sum(r.fee),0)/100.0,2) Entry_charges_RM FROM events e LEFT JOIN registrations r ON r.event_id=e.id AND r.status='Registered' WHERE e.status!='Cancelled' GROUP BY e.game")
        if module=="billing":
            return self.rows("SELECT u.name Member,m.tier Tier,m.points Points,round(m.spent/100.0,2) Paid_RM,count(b.id) Bills FROM members m JOIN users u ON u.id=m.user_id LEFT JOIN bills b ON b.member_id=m.id AND b.status='Paid' GROUP BY m.id ORDER BY m.spent DESC")
        if module=="staff":
            return self.rows("SELECT s.name Staff,count(r.id) Scheduled_shifts,count(a.id) Attended,sum(CASE WHEN a.late>0 THEN 1 ELSE 0 END) Late_shifts,round(coalesce(sum(a.minutes),0)/60.0,2) Hours,sum(CASE WHEN r.end<? AND a.id IS NULL THEN 1 ELSE 0 END) Absent FROM staff s LEFT JOIN rosters r ON r.staff_id=s.id AND r.status IN ('Scheduled','Leave requested') LEFT JOIN attendance a ON a.roster_id=r.id GROUP BY s.id",(stamp(),))
        raise ValueError("Unknown report.")
