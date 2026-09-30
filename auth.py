"""
TFLY Gaming Café Management System
Authentication & Session Context
Supports Login, Registration, Role-Based Access Control, and Fast Demo Role Switch.
"""

import sqlite3
from tkinter import messagebox
from database import get_connection, hash_password

class AuthManager:
    """Singleton managing logged-in user context and authentication operations."""
    _current_user = None

    @classmethod
    def get_current_user(cls):
        """Get the active user dict: {id, username, role, full_name, phone, member_id}"""
        return cls._current_user

    @classmethod
    def set_current_user(cls, user_dict):
        cls._current_user = user_dict

    @classmethod
    def is_admin(cls):
        return cls._current_user and cls._current_user.get('role') == 'admin'

    @classmethod
    def is_staff(cls):
        return cls._current_user and cls._current_user.get('role') in ('admin', 'staff')

    @classmethod
    def is_customer(cls):
        return cls._current_user and cls._current_user.get('role') == 'customer'

    @classmethod
    def login(cls, username_or_email, password):
        """Authenticate user against database by username or email."""
        if not username_or_email or not password:
            return False, "Username/Email and password cannot be empty."

        conn = get_connection()
        cursor = conn.cursor()
        hashed = hash_password(password)
        ident = username_or_email.strip()

        cursor.execute("""
            SELECT id, username, role, full_name, phone, date_of_birth, email, auth_provider 
            FROM users 
            WHERE (username = ? OR email = ?) AND password_hash = ?
        """, (ident, ident, hashed))
        user_row = cursor.fetchone()

        if not user_row:
            conn.close()
            return False, "Invalid username/email or password."

        # Fetch associated member record if exists
        cursor.execute("SELECT id, member_code, tier, points, balance FROM members WHERE user_id = ?", (user_row['id'],))
        mem_row = cursor.fetchone()

        user_data = dict(user_row)
        if mem_row:
            user_data['member_id'] = mem_row['id']
            user_data['member_code'] = mem_row['member_code']
            user_data['tier'] = mem_row['tier']
            user_data['points'] = mem_row['points']
            user_data['balance'] = mem_row['balance']
        else:
            user_data['member_id'] = None

        cls._current_user = user_data
        conn.close()
        return True, "Login successful!"

    @classmethod
    def register(cls, username, password, full_name, phone,
                 role="customer", dob="", email="", auth_provider="local"):
        """Register a new user and auto-generate membership if customer."""
        if not username or not password or not full_name:
            return False, "All required fields must be filled."

        conn = get_connection()
        cursor = conn.cursor()

        try:
            # Check existing username
            cursor.execute("SELECT id FROM users WHERE username = ?",
                           (username.strip(),))
            if cursor.fetchone():
                return False, f"Username '{username}' is already taken."

            if email:
                cursor.execute("SELECT id FROM users WHERE email = ? AND email != ''",
                               (email.strip(),))
                if cursor.fetchone():
                    return False, f"Email '{email}' is already registered."

            hashed = hash_password(password)
            cursor.execute("""
                INSERT INTO users
                    (username, password_hash, role, full_name, phone, date_of_birth, email, auth_provider)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (username.strip(), hashed, role,
                   full_name.strip(), phone.strip(), dob, email.strip(), auth_provider))
            user_id = cursor.lastrowid

            # If customer, create member record with 50 welcome points
            if role == "customer":
                mem_code = f"TFLY-{1000 + user_id}"
                cursor.execute("""
                    INSERT INTO members
                        (user_id, member_code, name, phone,
                         tier, points, total_spent, balance)
                    VALUES (?, ?, ?, ?, 'Bronze', 50, 0.0, 0.0)
                """, (user_id, mem_code,
                       full_name.strip(), phone.strip()))
                mem_id = cursor.lastrowid
                cursor.execute("""
                    INSERT INTO points_transactions
                        (member_id, points_change, reason)
                    VALUES (?, 50, 'Welcome Gift on Registration')
                """, (mem_id,))

            conn.commit()
            return True, "Account registered successfully! You can now log in."
        except Exception as e:
            return False, f"Database error: {e}"
        finally:
            conn.close()

    @classmethod
    def link_or_register_google(cls, email, full_name, phone, password, dob=""):
        """Link or create account via Google Play / Gmail onboarding."""
        email = email.strip()
        if not email or "@" not in email:
            return False, "Please enter a valid Google / Gmail address."
        if not password or len(password) < 6:
            return False, "Password must be at least 6 characters."
        if not full_name:
            full_name = email.split("@")[0].capitalize()

        conn = get_connection()
        cursor = conn.cursor()

        try:
            # Check if email exists
            cursor.execute("SELECT id, username, role, full_name FROM users WHERE email = ?", (email,))
            existing = cursor.fetchone()

            if existing:
                # Log in directly
                conn.close()
                return cls.login(existing['username'], password)

            # Generate unique username from email
            base_u = email.split("@")[0].replace(".", "_").lower()
            candidate_u = base_u
            idx = 1
            while True:
                cursor.execute("SELECT id FROM users WHERE username = ?", (candidate_u,))
                if not cursor.fetchone():
                    break
                candidate_u = f"{base_u}{idx}"
                idx += 1

            conn.close()
            # Register fresh account
            ok, msg = cls.register(
                username=candidate_u,
                password=password,
                full_name=full_name,
                phone=phone or "012-0000000",
                role="customer",
                dob=dob,
                email=email,
                auth_provider="google"
            )
            if ok:
                # Auto-login to the newly created fresh account
                cls.login(candidate_u, password)
                return True, f"Welcome to TFLY Arena! Connected as {email}."
            return False, msg
        except Exception as e:
            return False, f"Google link error: {e}"



    @classmethod
    def switch_demo_role(cls, target_role):
        """Convenience method for students during grading demo to switch roles instantly."""
        conn = get_connection()
        cursor = conn.cursor()

        username_map = {
            'admin': 'admin',
            'staff': 'staff1',
            'customer': 'gamer1'
        }
        target_username = username_map.get(target_role, 'admin')
        
        cursor.execute("SELECT id, username, role, full_name, phone FROM users WHERE username = ?", (target_username,))
        user_row = cursor.fetchone()
        if user_row:
            user_data = dict(user_row)
            cursor.execute("SELECT id, member_code, tier, points, balance FROM members WHERE user_id = ?", (user_data['id'],))
            mem = cursor.fetchone()
            if mem:
                user_data['member_id'] = mem['id']
                user_data['member_code'] = mem['member_code']
                user_data['tier'] = mem['tier']
                user_data['points'] = mem['points']
                user_data['balance'] = mem['balance']
            else:
                user_data['member_id'] = None
            cls._current_user = user_data
        conn.close()
        return cls._current_user
