"""Regression tests run entirely in temporary databases, never the user's data."""
from datetime import date, datetime, timedelta
from pathlib import Path
import sqlite3
import tempfile
import unittest

from ctfly_store import Store, adult, now, stamp, sen


class CafeTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.store=Store(Path(self.temp.name)/"test.db")
        self.store.login("admin","Admin@123")
        self.mid=1

    def tearDown(self):
        self.temp.cleanup()

    def new_member(self,n):
        actor=self.store.user
        member=self.store.register(f"player{n}","Password123",f"Player {n}",f"player{n}@example.com","0123456789","2000-01-01")
        self.store.user=actor
        return member["member_id"]

    def test_age_exact_boundary(self):
        today=date(2026,9,30)
        self.assertEqual(adult("2008-09-30",today),18)
        for dob in ("2008-10-01","2027-01-01","2000-02-30","", "1800-01-01"):
            with self.assertRaises(ValueError):
                adult(dob,today)

    def test_registration_and_passwords(self):
        user=self.store.register("newgamer","Valid@123","New Gamer","new@example.com","0123","2000-01-01")
        self.assertEqual(user["role"],"customer")
        with self.assertRaises(ValueError):
            self.store.login("newgamer","incorrect")
        self.assertEqual(self.store.login("new@example.com","Valid@123")["id"],user["id"])
        with self.assertRaises(ValueError):
            self.store.register("young","Valid@123","Young","","","2015-01-01")

    def test_book_requires_adult_and_ownership(self):
        other=self.new_member(1)
        self.store.login("gamer1","Gamer@123")
        with self.assertRaises(ValueError):
            self.store.book(1,other,60)
        with self.store.transaction() as db:
            db.execute("UPDATE users SET dob='2015-01-01' WHERE id=3")
        with self.assertRaises(ValueError):
            self.store.book(1,1,60)

    def test_reservation_overlap_cancel_and_no_charge(self):
        begin=now()+timedelta(days=1)
        sid=self.store.book(1,1,60,stamp(begin))
        self.assertEqual(self.store.quote(1)["count"],0)
        with self.assertRaises(ValueError):
            self.store.book(1,1,60,stamp(begin+timedelta(minutes=30)))
        self.store.book(1,1,60,stamp(begin+timedelta(hours=1)))
        self.store.change_session(sid,"Cancel")
        self.assertEqual(self.store.one("SELECT status FROM sessions WHERE id=?",(sid,))["status"],"Cancelled")

    def test_extension_checks_next_reservation(self):
        sid=self.store.book(1,1,60)
        end=self.store.one("SELECT end FROM sessions WHERE id=?",(sid,))["end"]
        self.store.book(1,1,60,end)
        with self.assertRaises(ValueError):
            self.store.change_session(sid,"Extend")
        self.store.change_session(2,"Cancel")
        self.store.change_session(sid,"Extend")
        self.assertEqual(self.store.quote(1)["Session"],1200)

    def test_tick_auto_release_and_restart_reservations(self):
        sid=self.store.book(1,1,60)
        with self.store.transaction() as db:
            db.execute("UPDATE sessions SET end=? WHERE id=?",(stamp(now()-timedelta(seconds=1)),sid))
        self.assertEqual(self.store.tick(),[1])
        self.assertEqual(self.store.one("SELECT status FROM stations WHERE id=1")["status"],"Available")
        rid=self.store.book(1,1,60,stamp(now()+timedelta(days=1)))
        with self.store.transaction() as db:
            db.execute("UPDATE sessions SET start=?,end=? WHERE id=?",(stamp(now()-timedelta(seconds=2)),stamp(now()+timedelta(hours=1)),rid))
        self.store.tick()
        self.store.tick()
        self.assertEqual(self.store.one("SELECT count(*) n FROM charges WHERE source_id=? AND kind='Session'",(rid,))["n"],1)
        self.assertEqual(self.store.one("SELECT status FROM stations WHERE id=1")["status"],"Occupied")

    def test_order_atomic_deduction_and_one_cancel(self):
        before=self.store.one("SELECT stock FROM products WHERE id=1")["stock"]
        oid=self.store.order(1,None,{1:2,2:1})
        self.assertEqual(self.store.one("SELECT stock FROM products WHERE id=1")["stock"],before-2)
        self.store.order_action(oid,"Cancel")
        self.assertEqual(self.store.one("SELECT stock FROM products WHERE id=1")["stock"],before)
        self.assertEqual(self.store.quote(1)["count"],0)
        with self.assertRaises(ValueError):
            self.store.order_action(oid,"Cancel")
        with self.assertRaises(ValueError):
            self.store.order(1,None,{1:1,2:9999})
        self.assertEqual(self.store.one("SELECT stock FROM products WHERE id=1")["stock"],before)

    def test_delivery_requires_members_station(self):
        other=self.new_member(2)
        self.store.book(1,1,60)
        with self.assertRaises(ValueError):
            self.store.order(other,1,{1:1})
        self.store.order(1,1,{1:1})

    def test_consolidated_checkout_once_and_void(self):
        self.store.book(1,1,60)
        self.store.order(1,None,{1:2})
        self.store.register_team(1,1,"Team One")
        quote=self.store.quote(1)
        self.assertEqual((quote["Session"],quote["Order"],quote["Event"]),(600,1300,1500))
        bid=self.store.checkout(1,"Cash")
        self.assertEqual(self.store.member(1)["points"],34)
        self.assertEqual(self.store.quote(1)["count"],0)
        with self.assertRaises(ValueError):
            self.store.checkout(1,"Cash")
        self.store.void_bill(bid)
        self.assertEqual(self.store.member(1)["points"],0)
        self.assertEqual(self.store.quote(1)["total"],3400)
        with self.assertRaises(ValueError):
            self.store.void_bill(bid)

    def test_tier_upgrade_applies_next_bill(self):
        self.store.restock(3,100,"Test stock delivery")
        self.store.order(1,None,{3:120})
        self.store.checkout(1,"Cash")
        self.assertEqual(self.store.member(1)["tier"],"Silver")
        self.store.book(1,1,60)
        self.assertEqual(self.store.quote(1)["discount"],30)
        self.assertEqual(self.store.quote(1)["total"],570)

    def test_reward_voucher_and_points(self):
        self.store.restock(3,100,"Test stock delivery")
        self.store.order(1,None,{3:120})
        bid=self.store.checkout(1,"Cash")
        old=self.store.member(1)["points"]
        rid=self.store.redeem(1,1)
        self.assertEqual(self.store.member(1)["points"],old-100)
        self.assertEqual(self.store.one("SELECT status FROM redemptions WHERE id=?",(rid,))["status"],"Pending")
        with self.assertRaises(ValueError):
            self.store.void_bill(bid)
        self.store.fulfill_reward(rid)
        with self.assertRaises(ValueError):
            self.store.fulfill_reward(rid)

    def test_event_conflict_capacity_and_duplicate(self):
        event=self.store.one("SELECT * FROM events WHERE id=1")
        with self.assertRaises(ValueError):
            self.store.save_event(None,"Overlap","Valorant",event["start"],event["end"],1000,2)
        self.store.save_event(1,event["name"],event["game"],event["start"],event["end"],event["fee"],2)
        self.store.register_team(1,1,"Alpha")
        with self.assertRaises(sqlite3.IntegrityError):
            self.store.register_team(1,1,"Another")
        self.store.register_team(1,self.new_member(3),"Bravo")
        self.assertEqual(self.store.one("SELECT status FROM events WHERE id=1")["status"],"Full")
        with self.assertRaises(ValueError):
            self.store.register_team(1,self.new_member(4),"Charlie")
        self.store.withdraw(1)
        self.assertEqual(self.store.one("SELECT status FROM events WHERE id=1")["status"],"Open")

    def test_odd_bracket_byes_and_final_winner(self):
        self.store.register_team(1,1,"Alpha")
        for i in range(4):
            self.store.register_team(1,self.new_member(i+10),f"Team {i}")
        self.store.bracket(1)
        self.assertEqual(self.store.one("SELECT count(*) n FROM matches WHERE event_id=1")["n"],7)
        self.assertEqual(self.store.one("SELECT count(*) n FROM matches WHERE event_id=1 AND score='BYE'")["n"],3)
        with self.assertRaises(ValueError):
            self.store.bracket(1)
        while True:
            ready=self.store.rows("SELECT * FROM matches WHERE event_id=1 AND done=0 AND a IS NOT NULL AND b IS NOT NULL")
            if not ready:
                break
            for match in ready:
                self.store.result(match["id"],2,1)
        self.assertEqual(self.store.one("SELECT status FROM events WHERE id=1")["status"],"Completed")
        final=self.store.one("SELECT * FROM matches WHERE event_id=1 ORDER BY round DESC LIMIT 1")
        self.assertIsNotNone(final["winner"])
        with self.assertRaises(ValueError):
            self.store.result(final["id"],0,2)

    def test_roster_overnight_overlap(self):
        day=now().date()+timedelta(days=1)
        self.store.roster(1,2,str(day))
        self.store.save_shift(None,"After midnight","00:00","08:00")
        with self.assertRaises(ValueError):
            self.store.roster(1,3,str(day+timedelta(days=1)))
        self.store.roster(2,3,str(day+timedelta(days=1)))

    def test_weekly_roster_rolls_back_entire_week(self):
        monday=now().date()+timedelta(days=7-now().weekday())
        self.store.roster(1,1,str(monday+timedelta(days=2)))
        with self.assertRaises(ValueError):
            self.store.weekly_roster(1,1,str(monday),list(range(5)))
        self.assertEqual(self.store.one("SELECT count(*) n FROM rosters")["n"],1)
        self.store.weekly_roster(2,1,str(monday),list(range(5)))
        self.assertEqual(self.store.one("SELECT count(*) n FROM rosters")["n"],6)

    def test_clock_late_early_hours_and_repeated_clock(self):
        day=now().date()
        self.store.roster(1,1,str(day))
        start=datetime.combine(day,datetime.strptime("09:00","%H:%M").time())
        self.store.clock(1,at=start+timedelta(minutes=12))
        with self.assertRaises(ValueError):
            self.store.clock(1,at=start+timedelta(minutes=13))
        self.store.clock(1,out=True,at=start+timedelta(hours=7,minutes=45))
        attendance=self.store.one("SELECT * FROM attendance WHERE roster_id=1")
        self.assertEqual((attendance["late"],attendance["early"],attendance["minutes"]),(12,15,453))
        with self.assertRaises(ValueError):
            self.store.roster(1,2,str(day),1)

    def test_staff_scope_and_leave_approval(self):
        day=str(now().date()+timedelta(days=1))
        self.store.roster(1,1,day)
        self.store.roster(2,1,day)
        self.store.login("staff1","Staff@123")
        with self.assertRaises(ValueError):
            self.store.clock(1)
        self.store.roster_action(2,"Leave requested","Appointment")
        with self.assertRaises(ValueError):
            self.store.roster_action(2,"Leave approved")
        self.store.login("admin","Admin@123")
        self.store.roster_action(2,"Leave approved","Approved")
        with self.assertRaises(ValueError):
            self.store.clock(2)

    def test_new_employee_login_and_deactivation(self):
        self.store.save_staff(None,"New Staff","Crew","0123",str(now().date()),"newstaff","Staffpass1")
        user=self.store.login("newstaff","Staffpass1")
        self.assertEqual(user["role"],"staff")
        self.store.login("admin","Admin@123")
        self.store.deactivate("staff",3)
        with self.assertRaises(ValueError):
            self.store.login("newstaff","Staffpass1")

    def test_permissions_prohibit_customer_admin_actions(self):
        self.store.login("gamer1","Gamer@123")
        for action in [lambda:self.store.save_station(None,"New","VIP","RTX",1000),
                       lambda:self.store.restock(1,1,"test"),lambda:self.store.checkout(1,"Cash"),
                       lambda:self.store.report("billing"),lambda:self.store.deactivate("products",1)]:
            with self.assertRaises(ValueError):
                action()

    def test_google_subject_binding_and_no_automatic_link(self):
        identity={"sub":"test-subject","email":"google@example.com","name":"Google User"}
        self.assertIsNone(self.store.google_login(identity))
        user=self.store.google_login(identity,"2000-01-01")
        self.store.user=None
        self.assertEqual(self.store.google_login(identity)["id"],user["id"])
        self.assertNotIn("password",self.store.user)
        self.store.user=None
        self.store.register("local","Password123","Local","local@example.com","","2000-01-01")
        with self.assertRaises(ValueError):
            self.store.google_login({"sub":"another-sub","email":"local@example.com","name":"Other"})

    def test_money_rejects_nonfinite_and_rounds(self):
        self.assertEqual(sen("1.005"),101)
        for value in ("NaN","Infinity","-1","text"):
            with self.assertRaises(ValueError):
                sen(value)

    def test_reports_and_metrics_empty_then_populated(self):
        for module in ("stations","shop","events","billing","staff"):
            self.assertTrue(self.store.report(module))
            self.assertIsInstance(self.store.metrics(module),str)
        self.store.book(1,1,60)
        self.store.order(1,None,{1:1})
        self.store.checkout(1,"Cash")
        self.assertEqual(self.store.report("billing")[0]["Paid_RM"],12.5)


    def test_menu_migration_is_idempotent(self):
        row=self.store.one("SELECT id FROM products WHERE name='Iced lemon tea'")
        self.assertIsNotNone(row)
        self.store.deactivate('products',row['id'])
        count=self.store.one('SELECT count(*) n FROM products')['n']
        again=Store(self.store.path)
        self.assertEqual(again.one('SELECT count(*) n FROM products')['n'],count)
        self.assertEqual(again.one('SELECT active FROM products WHERE id=?',(row['id'],))['active'],0)

    def test_favorites_are_per_user_and_toggle(self):
        self.store.favorite(1)
        self.assertEqual(len(self.store.rows('SELECT * FROM product_favorites')),1)
        self.store.login('staff1','Staff@123')
        self.store.favorite(1)
        self.assertEqual(len(self.store.rows('SELECT * FROM product_favorites')),2)
        self.store.favorite(1)
        self.assertEqual(len(self.store.rows('SELECT * FROM product_favorites')),1)
        self.store.user=None
        with self.assertRaises(ValueError):
            self.store.favorite(1)

    def test_crew_tasks_permissions_and_completion(self):
        own=self.store.save_task(2,'Clean station keyboards',str(now().date()))
        other=self.store.save_task(1,'Open the café',str(now().date()))
        self.store.login('staff1','Staff@123')
        self.store.task_action(own,'Done')
        self.assertIsNotNone(self.store.one('SELECT completed_at FROM crew_tasks WHERE id=?',(own,))['completed_at'])
        with self.assertRaises(ValueError):
            self.store.task_action(other,'Done')
        with self.assertRaises(ValueError):
            self.store.task_action(own,'Delete')
        with self.assertRaises(ValueError):
            self.store.save_task(2,'Manager action',str(now().date()))
        self.store.task_action(own,'To do')
        self.assertIsNone(self.store.one('SELECT completed_at FROM crew_tasks WHERE id=?',(own,))['completed_at'])
        self.store.login('gamer1','Gamer@123')
        with self.assertRaises(ValueError):
            self.store.task_action(own,'Done')

    def test_timer_without_due_sessions_never_opens_write_transaction(self):
        from unittest.mock import patch
        self.store.book(1,1,60)
        with patch.object(self.store,'transaction',side_effect=AssertionError('Unexpected database write')):
            self.assertEqual(self.store.tick(),[])

if __name__=="__main__":
    unittest.main()
