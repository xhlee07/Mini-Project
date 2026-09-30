"""Developer-only GUI smoke check with a temporary DB; python smoke_ui.py.

Creates actual Tk widgets; no changes to the application's database.
"""
from pathlib import Path
import sys
import tkinter as tk
import tempfile
from time import perf_counter
from date_controls import DateControl
from ctfly_app import Form
from ctfly_store import Store, now
from ctfly_neon import NeonApplication as Application


def main():
    with tempfile.TemporaryDirectory() as directory:
        store=Store(Path(directory)/"ui.db")
        app=Application(store)
        errors=[]
        app.report_callback_exception=lambda kind,error,tb:errors.append(str(error))
        def capture(name):
            print('CAPTURE '+name,flush=True)
            destination=Path(__file__).resolve().parent/'test-artifacts'
            destination.mkdir(exist_ok=True)
            app.lift()
            app.update()
            ready=tk.BooleanVar(value=False)
            app.after(180,lambda:ready.set(True))
            app.wait_variable(ready)
            x,y=app.winfo_rootx(),app.winfo_rooty()
            if sys.platform=='win32':
                from capture_ctfly import capture as render_window
                render_window(app,destination/f'{name}.png')
            else:
                from PIL import ImageGrab
                ImageGrab.grab(bbox=(x,y,x+app.winfo_width(),y+app.winfo_height())).save(destination/f'{name}.png')
        app.update()
        capture('login')
        def sign_in(name,password):
            app.login_values['username'].set(name)
            app.login_values['password'].set(password)
            app.login_submit.invoke()
            app.update()
        def drain_oauth():
            ready=tk.BooleanVar(value=False)
            app.after(400,lambda:ready.set(True))
            app.wait_variable(ready)
            assert app.oauth_queue.empty()
        for name,password in [('staff1','Staff@123'),('admin','Admin@123')]:
            sign_in(name,password)
            assert store.user is None and 'Staff / Admin Login' in app.login_error.cget('text')
        abandoned=app.oauth_generation
        app.portal_buttons['staff'].invoke()
        app.update()
        assert not app.login_mode
        assert not app.google_button.winfo_exists()
        capture('staff_login')
        # A delayed Google success/error must not change the newly selected portal.
        app.oauth_queue.put((abandoned,True,{'sub':'stale','email':'stale@example.com'}))
        app.oauth_queue.put((abandoned,False,'Cancelled old request'))
        drain_oauth()
        assert store.user is None and not app.login_error.cget('text')
        sign_in('gamer1','Gamer@123')
        assert store.user is None and 'Customer Login' in app.login_error.cget('text')
        sign_in('staff1','wrong')
        assert store.user is None and 'Incorrect' in app.login_error.cget('text')
        sign_in('staff1','Staff@123')
        assert app.module=='staff' and store.user['role']=='staff'
        app.logout()
        assert app.login_portal=='customer'
        app.portal_buttons['staff'].invoke()
        sign_in('admin','Admin@123')
        assert app.module=='staff' and store.user['role']=='admin'
        app.logout()
        abandoned=app.oauth_generation
        sign_in('gamer1','Gamer@123')
        app.oauth_queue.put((abandoned,False,'Late Google error'))
        drain_oauth()
        assert app.module=='stations' and store.user['role']=='customer' and 'staff' not in app.nav
        app.logout()
        normal_geometry=f'{app.winfo_width()}x{app.winfo_height()}'
        app.geometry('1120x710')
        for portal in ('customer','staff'):
            app.show_login(portal=portal)
            app.update()
            capture('small_'+portal+'_login')
            for widget in [*app.portal_buttons.values(),app.login_submit]:
                parent=widget.master
                while parent is not app:
                    assert widget.winfo_rooty()+widget.winfo_height()<=parent.winfo_rooty()+parent.winfo_height()+2
                    parent=parent.master
        app.geometry(normal_geometry)
        print('PASS customer/staff/admin entrances, role checks, landing pages and cancelled OAuth')
        app.show_login(True)
        app.update()
        def descendants(widget):
            for child in widget.winfo_children():
                yield child
                yield from descendants(child)
        dob=next(w for w in descendants(app) if isinstance(w,DateControl))
        dob.parts['year'].set('2000')
        dob.parts['month'].set('02')
        dob.parts['day'].set('31')
        dob.changed()
        assert app.login_values['dob'].get()=='2000-02-29'
        capture('register')
        app.show_login()
        store.login("admin","Admin@123")
        store.book(1,1,60)
        store.order(1,None,{1:1,2:1})
        store.register_team(1,1,"Alpha")
        actor=store.user
        second=store.register("second","Password123","Second","second@example.com","","2000-01-01")
        store.user=actor
        store.register_team(1,second['member_id'],"Bravo")
        store.bracket(1)
        store.roster(1,1,str(now().date()))
        task=store.save_task(2,'Check headsets and clean keyboards',str(now().date()))
        store.save_task(2,'Restock the drinks fridge',str(now().date()))
        app.shell()
        app.update()
        for module in ('stations','shop','events','billing','staff'):
            app.navigate(module)
            app.update()
            capture(module)
            for index in range(len(app.book.tabs())):
                app.book.select(index)
                app.update()
            print(f"PASS admin {module}")
        app.navigate('staff')
        app.book.select(4)
        capture('crew_tasks')
        popup=Form(app,'Date dropdown check',[('start','Start · YYYY-MM-DD HH:MM','2028-01-31 18:00',None),('day','Date','2028-01-31',None)],lambda values:None)
        dates=[w for w in descendants(popup) if isinstance(w,DateControl)]
        assert len(dates)==2
        dates[0].parts['month'].set('02')
        dates[0].changed()
        assert popup.values['start'].get()=='2028-02-29 18:00:00'
        popup.destroy()
        for module in ('stations','shop','events','billing','staff'):
            start=perf_counter()
            app.navigate(module)
            app.update()
            print(f"Warm navigation {module}: {(perf_counter()-start)*1000:.0f} ms")
        # Forms have a separate lifecycle; verify construction and closure.
        app.navigate('stations')
        app.station_form()
        app.update()
        for widget in app.winfo_children():
            if widget.winfo_class()=='Toplevel':
                widget.values['name'].set('PC-TEST')
                widget.values['spec'].set('RTX 4070 / 240 Hz')
                widget.submit.invoke()
        app.update()
        assert store.one("SELECT id FROM stations WHERE name='PC-TEST'")
        app.navigate('shop')
        app.book.select(0)
        cached_book=app.book
        cached_cart=app.cart_lines
        app.navigate('events')
        app.navigate('shop')
        assert app.book is cached_book and app.cart_lines is cached_cart
        store.favorite(1)
        app.navigate('events')
        app.navigate('shop')
        assert app.book is not cached_book  # DB changes invalidate stale menus.
        assert app.product_image({'name':'Steam RM 20 card','category':'Game cards'})=='game_credit.png'
        product=store.one('SELECT * FROM products WHERE id=1')
        app.add_cart(product)
        capture('menu_with_cart')
        app.change_cart(product['id'],-1)
        assert not app.cart
        app.add_cart(product)
        app.place_order()
        app.refresh()
        app.update()
        assert store.one('SELECT count(*) n FROM orders')['n']==2
        app.orders_table.tree.selection_set('2')
        app.reorder()
        assert app.cart=={1:1}
        app.logout()
        store.login('staff1','Staff@123')
        app.shell()
        app.navigate('staff')
        app.book.select(4)
        store.task_action(task,'Done')
        app.refresh()
        capture('staff_tasks')
        print('PASS staff account task board')
        app.logout()
        store.login('gamer1','Gamer@123')
        app.shell()
        for module in ('stations','shop','events','billing'):
            app.navigate(module)
            app.update()
            for index in range(len(app.book.tabs())):
                app.book.select(index)
                app.update()
            print(f"PASS customer {module}")
        assert 'staff' not in app.nav
        try:
            app.navigate('staff')
        except ValueError:
            pass
        else:
            raise AssertionError('Customer opened employee workspace')
        app.geometry('1120x710')
        app.navigate('shop')
        app.book.select(0)
        app.add_cart(store.one('SELECT * FROM products WHERE id=1'))
        app.update()
        capture('small_menu')
        assert app.cart_lines.winfo_height()>120
        submit=app.order_submit
        ancestor=submit.master
        while ancestor is not app:
            assert submit.winfo_rooty()+submit.winfo_height()<=ancestor.winfo_rooty()+ancestor.winfo_height()+2
            ancestor=ancestor.master
        app.logout()
        store.login('admin','Admin@123')
        app.shell()
        for module in ('stations','shop','events','billing','staff'):
            app.navigate(module)
            app.book.select(0)
            app.update()
            capture('small_admin_'+module)
            assert app.logout_button.winfo_rooty()+app.logout_button.winfo_height()<=app.winfo_rooty()+app.winfo_height()
            submit={'stations':'booking_submit','shop':'order_submit','billing':'payment_submit'}.get(module)
            if submit:
                widget=getattr(app,submit)
                parent=widget.master
                while parent is not app:
                    assert widget.winfo_rooty()+widget.winfo_height()<=parent.winfo_rooty()+parent.winfo_height()+2
                    parent=parent.master
        print('PASS five modules at 1120 x 710')
        app.show_login(True)
        values={'name':'Young Gamer','username':'younguser','email':'','phone':'','dob':'2015-01-01','password':'Password123','password_confirm':'Password123'}
        for key,value in values.items():
            app.login_values[key].set(value)
        app.local_login()
        assert store.user is None
        assert '18' in app.login_error.cget('text')
        app.close()
        if errors:
            raise AssertionError(errors)
        print('All GUI screens rendered without callback errors.')


if __name__=='__main__':
    main()
