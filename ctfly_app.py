"""CTFLY desktop — five modules, Tkinter/ttk, role-aware screens."""
import csv
from datetime import datetime, timedelta
from pathlib import Path
import queue
import sqlite3
import threading
import tkinter as tk
from tkinter import ttk, messagebox, filedialog

from ctfly_store import Store, now, stamp, money, sen, adult

BG = "#0c1119"
PANEL = "#141c28"
SURFACE = "#1c2736"
BORDER = "#2b394b"
TEXT = "#edf3fa"
MUTED = "#9cacc0"
ACCENT = "#b6f36b"
BLUE = "#74baff"
RED = "#ff8585"
AMBER = "#ffd178"
FONT = "Segoe UI"

MODULES = [
    ("stations", "01", "PC Stations", "Floor plan, bookings & sessions"),
    ("shop", "02", "Café & Inventory", "Menu, delivery & stock control"),
    ("events", "03", "Tournaments", "Events, registrations & brackets"),
    ("billing", "04", "Members & Billing", "Checkout, loyalty & rewards"),
    ("staff", "05", "Staff & Attendance", "Shifts, roster & clock-in"),
]


def label(parent, text, size=10, color=TEXT, bold=False, **kwargs):
    return tk.Label(parent, text=text, bg=parent.cget("bg"), fg=color,
                    font=(FONT, size, "bold" if bold else "normal"), **kwargs)


def button(parent, text, command, primary=False, danger=False):
    bg = RED if danger else ACCENT if primary else SURFACE
    widget = tk.Button(parent, text=text, command=command, bg=bg,
                       fg=BG if primary or danger else TEXT, activebackground=BLUE,
                       activeforeground=BG, font=(FONT, 10, "bold"), relief="flat",
                       bd=0, padx=14, pady=9, cursor="hand2", takefocus=True)
    return widget


class Scroll(tk.Frame):
    def __init__(self, parent, bg=BG):
        super().__init__(parent, bg=bg)
        self.canvas = tk.Canvas(self, bg=bg, highlightthickness=0)
        bar = ttk.Scrollbar(self, orient="vertical", command=self.canvas.yview)
        self.canvas.configure(yscrollcommand=bar.set)
        bar.pack(side="right", fill="y")
        self.canvas.pack(side="left", fill="both", expand=True)
        self.inner = tk.Frame(self.canvas, bg=bg)
        self.window = self.canvas.create_window(0, 0, anchor="nw", window=self.inner)
        self.inner.bind("<Configure>", lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all")))
        self.canvas.bind("<Configure>", lambda e: self.canvas.itemconfigure(self.window, width=e.width))
        # One dispatcher per window, routed to the area beneath the pointer.
        # Enter/Leave events on nested controls previously added duplicate bindings.
        root=self.winfo_toplevel()
        if not getattr(root,'_scroll_dispatch',False):
            root._scroll_dispatch=True
            def dispatch(event):
                widget=root.winfo_containing(event.x_root,event.y_root)
                while widget is not None:
                    if isinstance(widget,Scroll):
                        widget._wheel(event)
                        return 'break'
                    widget=widget.master
            root.bind('<MouseWheel>',dispatch,add='+')

    def _bind_wheel(self, event):
        self._wheel_id = self.winfo_toplevel().bind("<MouseWheel>", self._wheel, add="+")

    def _unbind_wheel(self, event=None):
        if getattr(self,"_wheel_id",None):
            self.winfo_toplevel().unbind("<MouseWheel>",self._wheel_id)
            self._wheel_id = None

    def _wheel(self, event):
        if self.winfo_exists():
            first,last=self.canvas.yview()
            if last-first<.999:
                self.canvas.yview_scroll(-int(event.delta/120), "units")

    def destroy(self):
        self._unbind_wheel()
        super().destroy()


class Table(tk.Frame):
    def __init__(self, parent, columns, rows=(), height=14):
        super().__init__(parent, bg=BG)
        self.columns = columns
        self.data = []
        self.search = tk.StringVar()
        toolbar = tk.Frame(self,bg=BG)
        toolbar.pack(fill="x",pady=(2,10))
        label(toolbar,"Search",color=MUTED).pack(side="left",padx=(0,10))
        ttk.Entry(toolbar,textvariable=self.search,width=32).pack(side="left")
        self.count=label(toolbar,"",color=MUTED)
        self.count.pack(side="right")
        self.search.trace_add("write",lambda *args:self.render())
        body=tk.Frame(self,bg=BG)
        body.pack(fill="both",expand=True)
        self.tree=ttk.Treeview(body,columns=[c[0] for c in columns],show="headings",height=height,selectmode="browse")
        for key,title,width in columns:
            self.tree.heading(key,text=title,command=lambda k=key:self.sort(k))
            self.tree.column(key,width=width,minwidth=70,stretch=True,anchor="w")
        y=ttk.Scrollbar(body,orient="vertical",command=self.tree.yview)
        x=ttk.Scrollbar(self,orient="horizontal",command=self.tree.xview)
        self.tree.configure(yscrollcommand=y.set,xscrollcommand=x.set)
        y.pack(side="right",fill="y")
        self.tree.pack(fill="both",expand=True)
        x.pack(fill="x")
        self.tree.tag_configure("odd",background=PANEL)
        self.tree.tag_configure("alert",foreground=AMBER)
        self.set(rows)

    def set(self, rows):
        self.data=[dict(r) for r in rows]
        self.render()

    def render(self):
        selected=self.tree.selection()
        self.tree.delete(*self.tree.get_children())
        query=self.search.get().casefold()
        count=0
        for index,row in enumerate(self.data):
            if query and query not in " ".join(str(v) for v in row.values()).casefold():
                continue
            iid=str(row.get("id",index))
            tag="alert" if row.get("alert") else "odd" if count%2 else ""
            self.tree.insert("","end",iid=iid,values=[row.get(k,"") for k,_,_ in self.columns],tags=(tag,))
            count+=1
        if selected and self.tree.exists(selected[0]):
            self.tree.selection_set(selected[0])
        self.count.config(text=f"{count} records")

    def selected(self):
        selection=self.tree.selection()
        if not selection:
            raise ValueError("Select a record in the table first.")
        iid=selection[0]
        return next(row for i,row in enumerate(self.data) if str(row.get("id",i))==iid)

    def sort(self,key):
        reverse=getattr(self,"_sort",None)==(key,False)
        self.data.sort(key=lambda r:(r.get(key) is None,r.get(key) if r.get(key) is not None else ""),reverse=reverse)
        self._sort=(key,reverse)
        self.render()


class Form(tk.Toplevel):
    """fields = (key, caption, default, choices); choices=None means text."""
    def __init__(self, parent, title, fields, callback, note=""):
        super().__init__(parent)
        self.title(title)
        self.configure(bg=PANEL)
        self.resizable(False,False)
        self.transient(parent)
        self.grab_set()
        content=tk.Frame(self,bg=PANEL,padx=28,pady=24)
        content.pack(fill="both",expand=True)
        label(content,title,20,bold=True).grid(row=0,column=0,columnspan=2,sticky="w",pady=(0,16))
        if note:
            label(content,note,10,MUTED,wraplength=470,justify="left").grid(row=1,column=0,columnspan=2,sticky="w",pady=(0,15))
        self.values={}
        for i,(key,caption,default,choices) in enumerate(fields,2):
            label(content,caption,10,MUTED).grid(row=i,column=0,sticky="w",padx=(0,20),pady=7)
            var=tk.StringVar(value=str(default))
            self.values[key]=var
            if choices is not None:
                entry=ttk.Combobox(content,textvariable=var,values=choices,state="readonly",width=34)
            else:
                from date_controls import field_control
                entry=field_control(content,var,key,caption)
                if entry is None:
                    entry=ttk.Entry(content,textvariable=var,width=37,show="•" if "password" in key else "")
            entry.grid(row=i,column=1,sticky="ew",pady=7)
        self.error=label(content,"",10,RED,wraplength=470,justify="left")
        self.error.grid(row=len(fields)+2,column=0,columnspan=2,sticky="w",pady=(14,0))
        actions=tk.Frame(content,bg=PANEL)
        actions.grid(row=len(fields)+3,column=0,columnspan=2,sticky="e",pady=(16,0))
        button(actions,"Cancel",self.destroy).pack(side="left",padx=8)
        self.submit=button(actions,"Save",lambda:self.save(callback),primary=True)
        self.submit.pack(side="left")
        self.bind("<Return>",lambda event:self.save(callback))
        self.bind("<Escape>",lambda event:self.destroy())
        self.update_idletasks()
        x=parent.winfo_rootx()+max(0,(parent.winfo_width()-self.winfo_width())//2)
        y=parent.winfo_rooty()+max(0,(parent.winfo_height()-self.winfo_height())//2)
        self.geometry(f"+{x}+{y}")

    def save(self,callback):
        try:
            callback({key:var.get().strip() for key,var in self.values.items()})
        except sqlite3.IntegrityError:
            self.error.config(text="This record conflicts with an existing username, email, name or registration.")
        except (ValueError,TypeError) as exc:
            self.error.config(text=str(exc))
        except sqlite3.Error:
            self.error.config(text="Could not save. Check database access and try again.")
        else:
            self.destroy()


class Application(tk.Tk):
    def __init__(self, store=None):
        super().__init__()
        self.title("CTFLY · Gaming Café")
        self.geometry("1320x860")
        self.minsize(1060,720)
        self.configure(bg=BG)
        self.store=store or Store()
        self.module="stations"
        self.tab_index={}
        self.cart={}
        self.timers=[]
        self.oauth_queue=queue.Queue()
        self.oauth_cancel=threading.Event()
        self.protocol("WM_DELETE_WINDOW",self.close)
        self.styles()
        self.show_login()
        self.after(1000,self.tick)
        self.after(150,self.poll_oauth)

    def styles(self):
        style=ttk.Style(self)
        style.theme_use("clam")
        style.configure(".",background=PANEL,foreground=TEXT,font=(FONT,10),bordercolor=BORDER)
        style.configure("TEntry",fieldbackground=SURFACE,foreground=TEXT,insertcolor=TEXT,padding=8)
        style.configure("TCombobox",fieldbackground=SURFACE,background=SURFACE,foreground=TEXT,padding=7,arrowsize=14)
        style.map("TCombobox",fieldbackground=[("readonly",SURFACE)],foreground=[("readonly",TEXT)],selectbackground=[("readonly",SURFACE)],selectforeground=[("readonly",TEXT)])
        self.option_add("*TCombobox*Listbox.background",SURFACE)
        self.option_add("*TCombobox*Listbox.foreground",TEXT)
        style.configure("Treeview",background=BG,fieldbackground=BG,foreground=TEXT,rowheight=38,borderwidth=0)
        style.configure("Treeview.Heading",background=SURFACE,foreground=MUTED,padding=10,font=(FONT,9,"bold"))
        style.map("Treeview",background=[("selected","#2c4431")],foreground=[("selected",ACCENT)])
        style.configure("TNotebook",background=BG,borderwidth=0)
        style.configure("TNotebook.Tab",background=PANEL,foreground=MUTED,padding=(18,12))
        style.map("TNotebook.Tab",background=[("selected",SURFACE)],foreground=[("selected",ACCENT)])
        style.configure("TScrollbar",background=SURFACE,troughcolor=BG,arrowcolor=MUTED)

    @property
    def staff(self):
        return self.store.user and self.store.user["role"] in ("admin","staff")

    @property
    def admin(self):
        return self.store.user and self.store.user["role"]=="admin"

    def clear(self):
        self.timers=[]
        for widget in self.winfo_children():
            widget.destroy()

    def show_login(self,register=False):
        self.cancel_google()
        self.clear()
        self.store.user=None
        self.login_mode=register
        hero=tk.Frame(self,bg="#172520",width=460)
        hero.pack(side="left",fill="both")
        hero.pack_propagate(False)
        label(hero,"CTFLY",34,ACCENT,True).pack(anchor="w",padx=46,pady=(55,8))
        label(hero,"GAMING CAFÉ",11,MUTED,True).pack(anchor="w",padx=48)
        label(hero,"Your next\ngreat game\nstarts here.",38,TEXT,True,justify="left").pack(anchor="w",padx=46,pady=(72,22))
        label(hero,"Fast rigs. Good company.\nA seat that's yours.",15,MUTED,justify="left").pack(anchor="w",padx=48)
        art=tk.Canvas(hero,bg="#172520",height=180,highlightthickness=0)
        art.pack(fill="x",padx=45,pady=24)
        for i in range(3):
            x=12+i*119
            art.create_rectangle(x,30,x+99,105,outline=ACCENT if i==1 else "#537658",width=2)
            art.create_line(x+49,105,x+49,126,fill=MUTED,width=3)
            art.create_line(x+30,126,x+68,126,fill=MUTED,width=3)
            art.create_text(x+50,67,text=f"0{i+1}",fill=ACCENT if i==1 else MUTED,font=(FONT,24,"bold"))
        label(hero,"18+ venue  ·  Standard & VIP stations",10,MUTED).pack(side="bottom",anchor="w",padx=48,pady=30)
        right=Scroll(self,PANEL)
        right.pack(fill="both",expand=True)
        box=tk.Frame(right.inner,bg=PANEL,padx=54,pady=35)
        box.pack(fill="x",pady=20)
        label(box,"CREATE YOUR ACCOUNT" if register else "WELCOME BACK",10,ACCENT,True).pack(anchor="w")
        label(box,"Join the café." if register else "Ready to play?",30,bold=True).pack(anchor="w",pady=(8,6))
        label(box,"Register to book a station, order snacks and join events." if register else "Sign in to your CTFLY account.",11,MUTED,wraplength=490,justify="left").pack(anchor="w",pady=(0,22))
        self.login_values={}
        fields=[("username","Username"),("password","Password")]
        if register:
            fields=[("name","Full name"),("username","Username"),("email","Email (optional)"),("phone","Phone"),("dob","Date of birth · YYYY-MM-DD"),("password","Password · at least 8 characters"),("password_confirm","Confirm password")]
        for key,caption in fields:
            label(box,caption,10,MUTED).pack(anchor="w",pady=(9,5))
            var=tk.StringVar()
            self.login_values[key]=var
            ttk.Entry(box,textvariable=var,show="•" if "password" in key else "").pack(fill="x")
        self.login_error=label(box,"",10,RED,wraplength=470,justify="left")
        self.login_error.pack(anchor="w",pady=(12,5))
        button(box,"Create account" if register else "Sign in",self.local_login,primary=True).pack(fill="x",pady=5)
        label(box,"───────  or continue with  ───────",10,MUTED).pack(pady=14)
        self.google_button=button(box,"G   Continue with Google",self.start_google)
        self.google_button.pack(fill="x")
        button(box,"Already registered? Sign in" if register else "New here? Create an account",lambda:self.show_login(not register)).pack(fill="x",pady=12)
        label(box,"Registration requires age 18+. Date of birth is declared by the user.",9,MUTED,wraplength=480,justify="left").pack(anchor="w",pady=6)
        if not register:
            label(box,"DEMO ACCOUNTS",9,ACCENT,True).pack(anchor="w",pady=(20,4))
            label(box,"admin / Admin@123    ·    staff1 / Staff@123\ngamer1 / Gamer@123",10,MUTED,justify="left").pack(anchor="w")

    def local_login(self):
        values={k:v.get().strip() for k,v in self.login_values.items()}
        try:
            if self.login_mode:
                if values["password"]!=values["password_confirm"]:
                    raise ValueError("Passwords do not match.")
                self.store.register(values["username"],values["password"],values["name"],values["email"],values["phone"],values["dob"])
            else:
                self.store.login(values["username"],values["password"],portal=getattr(self,'login_portal',None))
            self.complete_login()
        except (ValueError,sqlite3.Error) as exc:
            self.login_error.config(text="Username or email is already registered." if isinstance(exc,sqlite3.IntegrityError) else str(exc))

    def complete_login(self):
        self.cancel_google()
        self.module='staff' if self.staff else 'stations'
        self.shell()

    def cancel_google(self):
        self.oauth_cancel.set()
        self.oauth_generation=getattr(self,'oauth_generation',0)+1
        self.oauth_busy=False

    def start_google(self):
        if getattr(self,'login_portal','customer') != 'customer':
            return
        if getattr(self,"oauth_busy",False):
            return
        self.oauth_busy=True
        self.oauth_cancel=threading.Event()
        cancel=self.oauth_cancel
        generation=self.oauth_generation
        self.google_button.config(state="disabled",text="Waiting for Google in your browser…")
        self.login_error.config(text="")
        def worker():
            try:
                from google_oauth import sign_in
                self.oauth_queue.put((generation,True,sign_in(cancel)))
            except Exception as exc:
                self.oauth_queue.put((generation,False,str(exc)))
        threading.Thread(target=worker,daemon=True).start()

    def poll_oauth(self):
        try:
            generation,ok,result=self.oauth_queue.get_nowait()
        except queue.Empty:
            pass
        else:
            if generation != self.oauth_generation:
                self.after(150,self.poll_oauth)
                return
            self.oauth_busy=False
            if hasattr(self,"google_button") and self.google_button.winfo_exists():
                self.google_button.config(state="normal",text="G   Continue with Google")
            if not self.store.user:
                if not ok:
                    self.login_error.config(text=result)
                else:
                    try:
                        user=self.store.google_login(result)
                        if user:
                            self.complete_login()
                        else:
                            def finish(values):
                                if generation != self.oauth_generation:
                                    raise ValueError("This sign-in was cancelled. Close this form and sign in again.")
                                self.store.google_login(result,values["dob"],values["phone"])
                                # Defer shell until Form has closed and released its grab.
                                self.after_idle(self.complete_login)
                            Form(self,"Complete your Google account",[("dob","Date of birth · YYYY-MM-DD","",None),("phone","Phone","",None)],finish,
                                 note=f"Signed in as {result['email']}. Confirm your date of birth; registration is for adults 18+.")
                    except (ValueError,sqlite3.Error) as exc:
                        self.login_error.config(text=str(exc))
        self.after(150,self.poll_oauth)

    def shell(self):
        self.clear()
        self.login_mode=False
        side=tk.Frame(self,bg=PANEL,width=235)
        side.pack(side="left",fill="y")
        side.pack_propagate(False)
        label(side,"CTFLY",25,ACCENT,True).pack(anchor="w",padx=24,pady=(30,5))
        label(side,"CAFÉ OPERATIONS" if self.staff else "YOUR GAMING SPACE",9,MUTED,True).pack(anchor="w",padx=25,pady=(0,34))
        self.nav={}
        for key,num,title,desc in MODULES:
            if key=="staff" and not self.staff:
                continue
            btn=button(side,f"{num}   {title}",lambda k=key:self.navigate(k))
            btn.configure(anchor="w",pady=14)
            btn.pack(fill="x",padx=12,pady=4)
            self.nav[key]=btn
        footer=tk.Frame(side,bg=PANEL,padx=23,pady=24)
        footer.pack(side="bottom",fill="x")
        label(footer,"LOCAL CAFÉ",9,ACCENT,True).pack(anchor="w")
        label(footer,"Kuala Lumpur · MYT\nStandard RM 6 / VIP RM 10",9,MUTED,justify="left").pack(anchor="w",pady=8)
        button(footer,"Sign out",self.logout).pack(fill="x",pady=10)
        body=tk.Frame(self,bg=BG)
        body.pack(fill="both",expand=True)
        header=tk.Frame(body,bg=BG,padx=30,pady=19)
        header.pack(fill="x")
        self.breadcrumb=label(header,"",10,MUTED)
        self.breadcrumb.pack(side="left")
        user=self.store.user
        label(header,f"{user['name']}  ·  {user['role'].title()}",10,TEXT,True).pack(side="right")
        self.content=tk.Frame(body,bg=BG,padx=30)
        self.content.pack(fill="both",expand=True)
        self.status=label(body,"All changes are saved locally.",9,MUTED,anchor="w",padx=30,pady=12)
        self.status.pack(fill="x")
        self.navigate(self.module if self.module in self.nav else "stations")

    def logout(self):
        self.cart={}
        self.tab_index={}
        self.show_login()

    def navigate(self,key,keep=False):
        if hasattr(self,"book") and self.book.winfo_exists():
            self.tab_index[self.module]=self.book.index(self.book.select())
        self.module=key
        self.timers=[]
        for child in self.content.winfo_children():
            child.destroy()
        for k,btn in self.nav.items():
            btn.config(bg="#2b3e29" if k==key else SURFACE,fg=ACCENT if k==key else TEXT)
        _,num,title,subtitle=next(m for m in MODULES if m[0]==key)
        self.breadcrumb.config(text=f"WORKSPACE  /  {title.upper()}")
        label(self.content,title,28,bold=True).pack(anchor="w",pady=(8,4))
        label(self.content,subtitle,11,MUTED).pack(anchor="w",pady=(0,20))
        getattr(self,f"page_{key}")()
        index=self.tab_index.get(key,0)
        if index<len(self.book.tabs()):
            self.book.select(index)

    def refresh(self):
        self.navigate(self.module,True)

    def notice(self,text):
        if hasattr(self,"status") and self.status.winfo_exists():
            self.status.config(text=text,fg=ACCENT)

    def run(self,callback,refresh=True):
        try:
            result=callback()
            if refresh:
                self.refresh()
            return result
        except sqlite3.IntegrityError:
            messagebox.showerror("Record conflict","This name or registration already exists.",parent=self)
        except (ValueError,TypeError,sqlite3.Error) as exc:
            messagebox.showerror("Cannot complete action",str(exc),parent=self)

    def confirm(self,text,callback):
        if messagebox.askyesno("Confirm action",text,parent=self):
            self.run(callback)

    def form(self,title,fields,callback,note=""):
        def save(values):
            callback(values)
            self.after_idle(self.refresh)
        return Form(self,title,fields,save,note)

    def tabs(self,names):
        self.book=ttk.Notebook(self.content)
        self.book.pack(fill="both",expand=True,pady=(0,5))
        pages=[]
        for name in names:
            frame=tk.Frame(self.book,bg=BG,padx=4,pady=18)
            self.book.add(frame,text=name)
            pages.append(frame)
        return pages

    def actions(self,parent,items):
        bar=tk.Frame(parent,bg=BG)
        bar.pack(fill="x",pady=(0,14))
        # Wrap long action bars instead of clipping controls on smaller screens.
        widgets=[]
        for i,(text,callback) in enumerate(items):
            widgets.append(button(bar,text,lambda fn=callback:self.run(fn,False),primary=i==0))
        def layout(event):
            x=y=0
            row_height=42
            for widget in widgets:
                width=widget.winfo_reqwidth()+8
                if x and x+width>event.width:
                    x=0
                    y+=row_height+8
                widget.place(x=x,y=y,width=width-8,height=row_height)
                x+=width
            bar.config(height=y+row_height if widgets else 1)
        bar.bind('<Configure>',layout)
        return bar

    def stats(self,items):
        row=tk.Frame(self.content,bg=BG)
        row.pack(fill="x",pady=(0,20))
        for i,(caption,value,detail) in enumerate(items):
            row.columnconfigure(i,weight=1,uniform="stat")
            card=tk.Frame(row,bg=PANEL,padx=18,pady=14,highlightbackground=BORDER,highlightthickness=1)
            card.grid(row=0,column=i,sticky="nsew",padx=(0,10 if i<len(items)-1 else 0))
            label(card,caption.upper(),9,MUTED,True).pack(anchor="w")
            label(card,str(value),24,ACCENT,True).pack(anchor="w",pady=5)
            label(card,detail,9,MUTED).pack(anchor="w")

    def table(self,parent,columns,rows,height=12):
        table=Table(parent,columns,rows,height)
        table.pack(fill="both",expand=True)
        return table

    def member_choices(self):
        if self.staff:
            rows=self.store.members()
        else:
            rows=[self.store.member(self.store.user["member_id"])]
        return [f"{r['id']} · {r['name']}" for r in rows if r["active"]]

    @staticmethod
    def choice_id(value):
        try:
            return int(value.split(" · ")[0])
        except (ValueError,IndexError):
            raise ValueError("Choose a member or record from the dropdown.") from None

    def scope(self,alias=""):
        if self.staff:
            return "",()
        return f" WHERE {alias + '.' if alias else ''}member_id=?",(self.store.user["member_id"],)

    def tick(self):
        try:
            expired=self.store.tick()
            current=now()
            for widget,end in list(self.timers):
                if widget.winfo_exists():
                    seconds=max(0,int((datetime.fromisoformat(end)-current).total_seconds()))
                    widget.config(text=f"{seconds//3600:02}:{seconds%3600//60:02}:{seconds%60:02} remaining",fg=AMBER if seconds<300 else TEXT)
            if expired and self.store.user:
                self.notice(f"Session ended on {len(set(expired))} station(s). Seats have been released.")
                if self.module=="stations":
                    self.refresh()
            # Reservations becoming active need the cards rebuilt too.
            if self.store.user and self.module=="stations":
                states=self.store.rows("SELECT id,status FROM stations WHERE status!='Retired'")
                signature=tuple((r["id"],r["status"]) for r in states)
                if getattr(self,"station_signature",signature)!=signature:
                    self.refresh()
                self.station_signature=signature
        except sqlite3.Error as exc:
            self.notice(f"Database update failed: {exc}")
        self.after(1000,self.tick)

    def page_stations(self):
        self.store.tick()
        rows=self.store.rows("SELECT * FROM stations WHERE status!='Retired' ORDER BY name")
        self.station_signature=tuple((r["id"],r["status"]) for r in rows)
        self.stats([("Available",sum(r["status"]=="Available" for r in rows),"Ready for check-in"),
                    ("Playing",sum(r["status"]=="Occupied" for r in rows),"Live sessions"),
                    ("Reserved",sum(r["status"]=="Reserved" for r in rows),"Upcoming bookings"),
                    ("Maintenance",sum(r["status"]=="Maintenance" for r in rows),"Temporarily offline")])
        names=["Floor plan","Sessions & bookings"]+(["Station registry","Report"] if self.staff else [])
        pages=self.tabs(names)
        floor,history=pages[:2]
        if not getattr(self,"modern_menu",False):
            bar=tk.Frame(floor,bg=BG)
            bar.pack(fill="x",pady=(0,12))
            label(bar,"SELECT A STATION",10,MUTED,True).pack(side="left")
            for text,color in [("Available",ACCENT),("Occupied",BLUE),("Reserved",AMBER),("Maintenance",RED)]:
                label(bar,f"● {text}",9,color).pack(side="right",padx=8)
            scroll=Scroll(floor)
            scroll.pack(fill="both",expand=True)
            for i,row in enumerate(rows):
                col=i%4
                scroll.inner.columnconfigure(col,weight=1,uniform="seat")
                card=tk.Frame(scroll.inner,bg=PANEL,padx=16,pady=15,highlightbackground=BORDER,highlightthickness=1)
                card.grid(row=i//4,column=col,sticky="nsew",padx=5,pady=5)
                color={"Available":ACCENT,"Occupied":BLUE,"Reserved":AMBER,"Maintenance":RED}[row["status"]]
                label(card,f"●  {row['status']}",9,color,True).pack(anchor="w")
                label(card,row["name"],22,bold=True).pack(anchor="w",pady=(12,3))
                label(card,f"{row['zone']}   /   {money(row['rate'])} per hour",9,MUTED).pack(anchor="w")
                label(card,row["spec"],9,MUTED,wraplength=190,justify="left").pack(anchor="w",pady=(5,12))
                session=self.store.one("SELECT * FROM sessions WHERE station_id=? AND status='Active'",(row["id"],))
                if session:
                    timer=label(card,"Live session",11,TEXT,True)
                    timer.pack(anchor="w",pady=(0,10))
                    self.timers.append((timer,session["end"]))
                else:
                    label(card,"18+ · 1 / 2 / 3 / 4 / 8 hour packages",8,MUTED).pack(anchor="w",pady=(0,10))
                if row["status"]!="Maintenance":
                    button(card,"Book station" if not session else "Reserve later",lambda r=row:self.run(lambda:self.booking_form(r),False),primary=not session).pack(fill="x")
        where,params=self.scope("s")
        sessions=self.store.rows("SELECT s.*,st.name station,u.name member FROM sessions s JOIN stations st ON st.id=s.station_id JOIN members m ON m.id=s.member_id JOIN users u ON u.id=m.user_id"+where+" ORDER BY s.id DESC",params)
        for r in sessions:
            r["amount"]=money(r["cost"])
            r["start"]=r["start"].replace("T"," ")
            r["end"]=r["end"].replace("T"," ")
        actions=[("Extend +1h",lambda:self.session_action("Extend")),("Cancel reservation",lambda:self.session_action("Cancel"))]
        if self.staff:
            actions.append(("End early",lambda:self.session_action("End")))
        actions.append(("Refresh",self.refresh))
        self.actions(history,actions)
        self.session_table=self.table(history,[("id","ID",60),("station","Station",90),("member","Member",130),("start","Start",170),("end","End",170),("amount","Package charge",110),("status","Status",100)],sessions)
        if self.staff:
            registry,report=pages[2:]
            items=[("Add station",lambda:self.station_form())] if self.admin else []
            if self.admin:
                items += [("Edit",lambda:self.station_form(self.station_table.selected())),("Maintenance / release",self.station_maintenance),("Retire station",self.station_retire)]
            self.actions(registry,items)
            for r in rows:
                r["amount"]=money(r["rate"])
            self.station_table=self.table(registry,[("id","ID",60),("name","Station",100),("zone","Zone",100),("spec","Specification",280),("amount","Hourly rate",110),("status","Status",120)],rows)
            self.report_page(report,"stations")

    def booking_form(self,station):
        choices=self.member_choices()
        if not choices:
            raise ValueError("Create a member account first.")
        def save(v):
            start=v["start"].replace(" ","T") if v["start"] else None
            self.store.book(station["id"],self.choice_id(v["member"]),int(v["package"].split()[0])*60,start)
            self.notice("Booking saved. Prepaid packages keep their price when ended early.")
        self.form(f"Book {station['name']}",[("member","Member",choices[0],choices),("package","Package","1 hour",["1 hour","2 hours","3 hours","4 hours","8 hours"]),("start","Start (blank = now)","",None)],save,
                  "Future booking: YYYY-MM-DD HH:MM. Overlapping reservations are blocked. Packages are prepaid and billed at the current station rate.")

    def session_action(self,action):
        row=self.session_table.selected()
        self.confirm(f"{action} session #{row['id']}? Early ending keeps the prepaid package charge.",lambda:self.store.change_session(row["id"],action))

    def station_form(self,row=None):
        row=row or {}
        self.form("Edit station" if row else "Add station",[("name","Station name",row.get("name",""),None),("zone","Zone",row.get("zone","Standard"),["Standard","VIP"]),("spec","Specification",row.get("spec",""),None),("rate","Hourly rate (RM)",row.get("rate",600)/100,None)],
                  lambda v:self.store.save_station(row.get("id"),v["name"],v["zone"],v["spec"],sen(v["rate"])))

    def station_maintenance(self):
        row=self.station_table.selected()
        self.run(lambda:self.store.station_state(row["id"],row["status"]!="Maintenance"))

    def station_retire(self):
        row=self.station_table.selected()
        self.confirm(f"Retire {row['name']}? History will be kept.",lambda:self.store.station_state(row["id"],remove=True))

    def page_shop(self):
        products=self.store.rows("SELECT p.*,c.name category FROM products p JOIN categories c ON c.id=p.category_id WHERE p.active=1 ORDER BY c.name,p.name")
        self.stats([("Menu items",len(products),"Snacks, drinks & game cards"),("Low stock",sum(r["stock"]<=r["threshold"] for r in products),"At or below reorder level"),("Your cart",sum(self.cart.values()),"Ready for pickup or delivery")])
        pages=self.tabs(["Menu & cart","Orders"]+(["Products & stock","Categories","Stock ledger","Report"] if self.staff else []))
        menu,orders=pages[:2]
        if not getattr(self,"modern_menu",False):
            left=tk.Frame(menu,bg=BG)
            left.pack(side="left",fill="both",expand=True,padx=(0,18))
            filterbar=tk.Frame(left,bg=BG)
            filterbar.pack(fill="x",pady=(0,12))
            query=tk.StringVar()
            ttk.Entry(filterbar,textvariable=query).pack(side="left",fill="x",expand=True)
            label(filterbar,"Search menu",9,MUTED).pack(side="left",padx=10)
            scroll=Scroll(left)
            scroll.pack(fill="both",expand=True)
            def render(*args):
                for child in scroll.inner.winfo_children():
                    child.destroy()
                visible=[p for p in products if query.get().casefold() in (p["name"]+p["category"]).casefold()]
                for i,p in enumerate(visible):
                    scroll.inner.columnconfigure(i%2,weight=1,uniform="product")
                    card=tk.Frame(scroll.inner,bg=PANEL,padx=18,pady=16,highlightbackground=BORDER,highlightthickness=1)
                    card.grid(row=i//2,column=i%2,sticky="nsew",padx=4,pady=5)
                    label(card,p["category"].upper(),8,BLUE,True).pack(anchor="w")
                    label(card,p["name"],14,bold=True,wraplength=220,justify="left").pack(anchor="w",pady=10)
                    label(card,money(p["price"]),18,ACCENT,True).pack(anchor="w")
                    label(card,f"{p['stock']} in stock"+(" · Low stock" if p["stock"]<=p["threshold"] else ""),9,AMBER if p["stock"]<=p["threshold"] else MUTED).pack(anchor="w",pady=8)
                    add=button(card,"Add to cart" if p["stock"] else "Sold out",lambda r=p:self.run(lambda:self.add_cart(r),False))
                    add.pack(fill="x")
                    if not p["stock"]:
                        add.config(state="disabled")
            query.trace_add("write",render)
            render()
            cart=tk.Frame(menu,bg=PANEL,width=300,padx=18,pady=18)
            cart.pack(side="right",fill="y")
            cart.pack_propagate(False)
            label(cart,"Your order",19,bold=True).pack(anchor="w",pady=(0,12))
            self.cart_text=tk.Text(cart,bg=PANEL,fg=TEXT,relief="flat",font=(FONT,10),height=10,wrap="word",state="disabled")
            self.cart_text.pack(fill="both",expand=True)
            choices=self.member_choices()
            label(cart,"Member",9,MUTED).pack(anchor="w",pady=(12,5))
            self.cart_member=tk.StringVar(value=choices[0] if choices else "")
            ttk.Combobox(cart,textvariable=self.cart_member,values=choices,state="readonly").pack(fill="x")
            stations=self.store.rows("SELECT id,name FROM stations WHERE status!='Retired'")
            station_choices=["Counter pickup"]+[f"{r['id']} · {r['name']}" for r in stations]
            label(cart,"Delivery",9,MUTED).pack(anchor="w",pady=(12,5))
            self.cart_seat=tk.StringVar(value="Counter pickup")
            ttk.Combobox(cart,textvariable=self.cart_seat,values=station_choices,state="readonly").pack(fill="x")
            self.cart_total=label(cart,"",21,ACCENT,True)
            self.cart_total.pack(anchor="w",pady=20)
            button(cart,"Place order",lambda:self.run(self.place_order),primary=True).pack(fill="x",pady=4)
            button(cart,"Clear cart",self.clear_cart).pack(fill="x",pady=4)
            label(cart,"Payment is recorded at the cashier.\nSeat delivery needs your active booking.",9,MUTED,justify="left",wraplength=260).pack(anchor="w",pady=12)
            self.render_cart()
        where,params=self.scope("o")
        rows=self.store.rows("SELECT o.*,u.name member,coalesce(s.name,'Counter pickup') seat FROM orders o JOIN members m ON m.id=o.member_id JOIN users u ON u.id=m.user_id LEFT JOIN stations s ON s.id=o.station_id"+where+" ORDER BY o.id DESC",params)
        for r in rows:
            r["amount"]=money(r["total"])
        actions=[("View items",self.order_detail),("Cancel pending",lambda:self.order_action("Cancel"))]
        if self.staff:
            actions.insert(0,("Next delivery stage",lambda:self.order_action("Next")))
        self.actions(orders,actions)
        self.orders_table=self.table(orders,[("id","Order",65),("member","Member",140),("seat","Delivery",125),("amount","Total",110),("status","Status",110),("at","Created",180)],rows)
        if self.staff:
            registry,categories,ledger,report=pages[2:]
            actions=[("Restock",self.stock_form)]
            if self.admin:
                actions=[("Add product",lambda:self.product_form()),("Edit",lambda:self.product_form(self.product_table.selected())),("Restock",self.stock_form),("Deactivate",lambda:self.confirm("Take this product off the menu?",lambda:self.store.deactivate("products",self.product_table.selected()["id"])))]
            self.actions(registry,actions)
            for r in products:
                r["amount"]=money(r["price"])
                r["alert"]=r["stock"]<=r["threshold"]
            self.product_table=self.table(registry,[("id","ID",60),("name","Product",180),("category","Category",110),("amount","Price",100),("stock","Stock",85),("threshold","Reorder level",100)],products)
            if self.admin:
                self.actions(categories,[("Add category",lambda:self.category_form()),("Rename",lambda:self.category_form(self.category_table.selected())),("Delete",lambda:self.confirm("Delete this unused category?",lambda:self.store.save_category(self.category_table.selected()["id"],"",True)))])
            self.category_table=self.table(categories,[("id","ID",80),("name","Category",300)],self.store.rows("SELECT * FROM categories"))
            self.table(ledger,[("id","ID",60),("name","Product",180),("quantity","Change",80),("reason","Reason",260),("at","Time",180)],self.store.rows("SELECT r.*,p.name FROM stock_records r JOIN products p ON p.id=r.product_id ORDER BY r.id DESC"))
            self.report_page(report,"shop")

    def add_cart(self,p):
        amount=self.cart.get(p["id"],0)+1
        if amount>p["stock"]:
            raise ValueError("You've reached the available stock for this item.")
        self.cart[p["id"]]=amount
        self.render_cart()

    def render_cart(self):
        rows=self.store.rows("SELECT * FROM products WHERE active=1")
        total=0
        lines=[]
        for row in rows:
            qty=self.cart.get(row["id"],0)
            if qty:
                total+=row["price"]*qty
                lines.append(f"{qty} × {row['name']}\n{money(row['price']*qty)}\n")
        self.cart_text.config(state="normal")
        self.cart_text.delete("1.0","end")
        self.cart_text.insert("end","\n".join(lines) or "Your cart is empty.\nAdd something from the menu.")
        self.cart_text.config(state="disabled")
        self.cart_total.config(text=money(total))

    def clear_cart(self):
        self.cart={}
        self.render_cart()

    def place_order(self):
        sid=None if self.cart_seat.get()=="Counter pickup" else self.choice_id(self.cart_seat.get())
        oid=self.store.order(self.choice_id(self.cart_member.get()),sid,self.cart)
        self.cart={}
        self.notice(f"Order #{oid} placed. Payment is waiting at the cashier.")

    def order_action(self,action):
        row=self.orders_table.selected()
        self.confirm(f"{action} order #{row['id']}?",lambda:self.store.order_action(row["id"],action))

    def order_detail(self):
        row=self.orders_table.selected()
        items=self.store.rows("SELECT * FROM order_items WHERE order_id=?",(row["id"],))
        self.text_window(f"Order #{row['id']}","\n".join(f"{r['qty']} × {r['name']}   {money(r['qty']*r['price'])}" for r in items)+f"\n\nTotal: {money(row['total'])}")

    def product_form(self,row=None):
        row=row or {}
        categories=self.store.rows("SELECT * FROM categories")
        choices=[f"{r['id']} · {r['name']}" for r in categories]
        if not choices:
            raise ValueError("Add a category first.")
        category=next((c for c in choices if c.startswith(str(row.get("category_id"))+" · ")),choices[0])
        fields=[("name","Product name",row.get("name",""),None),("category","Category",category,choices),("price","Price (RM)",row.get("price",0)/100,None),("threshold","Reorder level",row.get("threshold",5),None)]
        if not row:
            fields.append(("stock","Opening stock",0,None))
        self.form("Edit product" if row else "Add product",fields,lambda v:self.store.save_product(row.get("id"),v["name"],self.choice_id(v["category"]),sen(v["price"]),int(v.get("stock",0)),int(v["threshold"])),"Use Restock to add inventory; every stock movement is recorded.")

    def stock_form(self):
        row=self.product_table.selected()
        self.form(f"Restock {row['name']}",[("qty","Quantity",10,None),("reason","Note / supplier","Stock delivery",None)],lambda v:self.store.restock(row["id"],int(v["qty"]),v["reason"]))

    def category_form(self,row=None):
        row=row or {}
        self.form("Category",[("name","Name",row.get("name",""),None)],lambda v:self.store.save_category(row.get("id"),v["name"]))

    def page_events(self):
        rows=self.store.rows("SELECT e.*,count(r.id) enrolled FROM events e LEFT JOIN registrations r ON r.event_id=e.id AND r.status='Registered' GROUP BY e.id ORDER BY e.start")
        self.stats([("Open events",sum(r["status"]=="Open" for r in rows),"Registration available"),("Entries",sum(r["enrolled"] for r in rows),"Registered teams / players"),("Live brackets",sum(r["status"]=="Ongoing" for r in rows),"Single elimination")])
        pages=self.tabs(["Events","Registrations","Bracket"]+(["Report"] if self.staff else []))
        events,registrations,bracket=pages[:3]
        actions=[("Register team / player",self.event_signup)]
        if self.staff:
            actions += [("Create event",lambda:self.event_form()),("Edit",lambda:self.event_form(self.event_table.selected())),("Generate bracket",self.generate_bracket),("Cancel event",lambda:self.confirm("Cancel this event and void unpaid entries?",lambda:self.store.cancel_event(self.event_table.selected()["id"])))]
        self.actions(events,actions)
        for r in rows:
            r["slots"]=f"{r['enrolled']} / {r['capacity']}"
            r["amount"]=money(r["fee"])
        self.event_table=self.table(events,[("id","ID",55),("name","Event",200),("game","Game",140),("start","Start",170),("amount","Entry fee",90),("slots","Slots",80),("status","Status",100)],rows)
        where,params=self.scope("r")
        registrations_rows=self.store.rows("SELECT r.*,e.name event,t.name team,u.name member,CASE WHEN c.bill_id IS NULL THEN 'Unpaid' ELSE 'Paid' END payment FROM registrations r JOIN events e ON e.id=r.event_id JOIN teams t ON t.id=r.team_id JOIN members m ON m.id=r.member_id JOIN users u ON u.id=m.user_id LEFT JOIN charges c ON c.kind='Event' AND c.source_id=r.id"+where+" ORDER BY r.id DESC",params)
        self.actions(registrations,[("Rename team",self.rename_team_form),("Withdraw entry",lambda:self.confirm("Withdraw this entry?",lambda:self.store.withdraw(self.reg_table.selected()["id"])) )])
        self.reg_table=self.table(registrations,[("id","ID",65),("event","Event",190),("team","Team / player",160),("member","Leader",140),("payment","Payment",95),("status","Status",110)],registrations_rows)
        selector=tk.Frame(bracket,bg=BG)
        selector.pack(fill="x",pady=(0,15))
        choices=[f"{r['id']} · {r['name']}" for r in rows]
        self.bracket_event=tk.StringVar(value=getattr(self,"last_bracket",choices[0] if choices else ""))
        if self.bracket_event.get() not in choices:
            self.bracket_event.set(choices[0] if choices else "")
        cb=ttk.Combobox(selector,textvariable=self.bracket_event,values=choices,state="readonly",width=42)
        cb.pack(side="left")
        cb.bind("<<ComboboxSelected>>",lambda e:self.render_bracket())
        if self.staff:
            button(selector,"Record selected match result",lambda:self.run(self.match_form,False),primary=True).pack(side="right")
        self.bracket_body=tk.Frame(bracket,bg=BG)
        self.bracket_body.pack(fill="both",expand=True)
        self.render_bracket()
        if self.staff:
            self.report_page(pages[3],"events")

    def event_form(self,row=None):
        row=row or {}
        start=now().replace(hour=18,minute=0,second=0)+timedelta(days=7)
        self.form("Edit event" if row else "Create event",[("name","Event name",row.get("name",""),None),("game","Game",row.get("game","Valorant"),None),("start","Start · YYYY-MM-DD HH:MM",row.get("start",stamp(start)).replace("T"," "),None),("end","End · YYYY-MM-DD HH:MM",row.get("end",stamp(start+timedelta(hours=4))).replace("T"," "),None),("fee","Entry fee (RM)",row.get("fee",1500)/100,None),("capacity","Team / player slots",row.get("capacity",8),None)],lambda v:self.store.save_event(row.get("id"),v["name"],v["game"],v["start"],v["end"],sen(v["fee"]),int(v["capacity"])),"The café has one tournament arena. Event schedules cannot overlap.")

    def event_signup(self):
        row=self.event_table.selected()
        choices=self.member_choices()
        if not choices:
            raise ValueError("Create a member account first.")
        self.form(f"Join {row['name']}",[("member","Team leader",choices[0],choices),("name","Team / player name","",None)],lambda v:self.store.register_team(row["id"],self.choice_id(v["member"]),v["name"]),f"Entry fee {money(row['fee'])}. One entry per member per event. Fee is added to the cashier account.")

    def rename_team_form(self):
        row=self.reg_table.selected()
        self.form("Rename team / player",[("name","Name",row["team"],None)],lambda v:self.store.rename_team(row["id"],v["name"]))

    def generate_bracket(self):
        row=self.event_table.selected()
        self.confirm("Close registration and generate a single-elimination bracket? Unfilled slots receive byes. This cannot be regenerated after starting.",lambda:self.store.bracket(row["id"]))

    def render_bracket(self):
        for child in self.bracket_body.winfo_children():
            child.destroy()
        if not self.bracket_event.get():
            label(self.bracket_body,"Create an event to get started.",12,MUTED).pack(pady=40)
            self.match_table=None
            return
        self.last_bracket=self.bracket_event.get()
        eid=self.choice_id(self.last_bracket)
        matches=self.store.rows("SELECT m.*,coalesce(a.name,'Awaiting team') team_a,coalesce(b.name,'Awaiting team') team_b,coalesce(w.name,'—') winner_name FROM matches m LEFT JOIN teams a ON a.id=m.a LEFT JOIN teams b ON b.id=m.b LEFT JOIN teams w ON w.id=m.winner WHERE m.event_id=? ORDER BY m.round,m.slot",(eid,))
        if not matches:
            label(self.bracket_body,"Bracket opens after staff generate the draw.\nAt least two registered teams are needed.",12,MUTED,justify="center").pack(pady=60)
            self.match_table=None
            return
        canvas=tk.Canvas(self.bracket_body,bg=BG,height=250,highlightthickness=0)
        canvas.pack(fill="x")
        xbar=ttk.Scrollbar(self.bracket_body,orient="horizontal",command=canvas.xview)
        xbar.pack(fill="x")
        canvas.configure(xscrollcommand=xbar.set)
        rounds=max(m["round"] for m in matches)
        height=max(250,sum(m["round"]==1 for m in matches)*85+60)
        ybar=ttk.Scrollbar(self.bracket_body,orient="vertical",command=canvas.yview)
        ybar.pack(side="right",fill="y")
        canvas.configure(yscrollcommand=ybar.set,scrollregion=(0,0,rounds*275,height))
        positions={}
        for m in matches:
            x=15+(m["round"]-1)*275
            y=50+(m["slot"]+.5)*(2**(m["round"]-1))*85-35
            positions[(m["round"],m["slot"])]=(x,y)
        for m in matches:
            x,y=positions[(m["round"],m["slot"])]
            following=positions.get((m["round"]+1,m["slot"]//2))
            if following:
                nx,ny=following
                canvas.create_line(x+235,y+30,x+255,y+30,x+255,ny+30,nx,ny+30,fill=BORDER,width=2)
        for rnd in range(1,rounds+1):
            canvas.create_text(20+(rnd-1)*275,20,text="FINAL" if rnd==rounds else f"ROUND {rnd}",anchor="w",fill=ACCENT,font=(FONT,10,"bold"))
        for m in matches:
            x,y=positions[(m["round"],m["slot"])]
            canvas.create_rectangle(x,y,x+235,y+65,fill=PANEL,outline=ACCENT if m["done"] else BORDER)
            canvas.create_text(x+12,y+18,anchor="w",text=m["team_a"][:25],fill=ACCENT if m["winner"]==m["a"] else TEXT,font=(FONT,10))
            canvas.create_text(x+12,y+43,anchor="w",text="BYE" if m["score"]=="BYE" else m["team_b"][:25],fill=ACCENT if m["winner"]==m["b"] else MUTED,font=(FONT,10))
        for m in matches:
            m["state"]="Completed" if m["done"] else "Ready" if m["a"] and m["b"] else "Waiting"
        self.match_table=self.table(self.bracket_body,[("id","Match",60),("round","Round",70),("team_a","Team A",150),("team_b","Team B",150),("score","Score",90),("winner_name","Winner",150),("state","Status",90)],matches,height=4)

    def match_form(self):
        if not self.match_table:
            raise ValueError("Generate a bracket first.")
        row=self.match_table.selected()
        self.form("Record match result",[("a",row["team_a"]+" score",0,None),("b",row["team_b"]+" score",0,None)],lambda v:self.store.result(row["id"],int(v["a"]),int(v["b"])),"The winner advances automatically. Recorded results are final.")

    def page_billing(self):
        if self.staff:
            amount=self.store.one("SELECT coalesce(sum(total),0) total FROM bills WHERE status='Paid'")["total"]
            member_count=self.store.one("SELECT count(*) n FROM members")["n"]
            outstanding=self.store.one("SELECT coalesce(sum(amount),0) n FROM charges WHERE bill_id IS NULL AND void=0")["n"]
            self.stats([("Paid revenue",money(amount),"After tier discounts"),("Members",member_count,"Bronze / Silver / Gold"),("Unbilled",money(outstanding),"Before discounts")])
        else:
            member=self.store.member(self.store.user["member_id"])
            quote=self.store.quote(member["id"])
            self.stats([("Your tier",member["tier"],"Silver at RM 300 · Gold at RM 1,000"),("Points",member["points"],"1 point per whole RM paid"),("To pay",money(quote["total"]),"Pay at the café cashier")])
        names=(["Cashier","Members"] if self.staff else ["My account"])+["Bills","Rewards","Points & vouchers"]+(["Report"] if self.staff else [])
        pages=self.tabs(names)
        offset=2 if self.staff else 1
        account=pages[0]
        self.account_page(account)
        if self.staff:
            members=pages[1]
            self.actions(members,[("Register member",self.new_member_form),("Edit member",lambda:self.member_form(self.member_table.selected()))])
            rows=self.store.members()
            for r in rows:
                r["paid"]=money(r["spent"])
                r["state"]="Active" if r["active"] else "Inactive"
            self.member_table=self.table(members,[("id","ID",60),("name","Name",150),("phone","Phone",120),("dob","Birth date",110),("tier","Tier",85),("points","Points",75),("paid","Paid spend",115),("state","Status",90)],rows)
        bills,rewards,points=pages[offset:offset+3]
        where,params=self.scope("b")
        rows=self.store.rows("SELECT b.*,u.name member FROM bills b JOIN members m ON m.id=b.member_id JOIN users u ON u.id=m.user_id"+where+" ORDER BY b.id DESC",params)
        for r in rows:
            r["amount"]=money(r["total"])
        items=[("View receipt",self.receipt)]
        if self.admin:
            items.append(("Void bill",lambda:self.confirm("Void this bill, reverse its points and reopen charges? Any actual payment refund must be handled at the counter.",lambda:self.store.void_bill(self.bill_table.selected()["id"]))))
        self.actions(bills,items)
        self.bill_table=self.table(bills,[("id","Bill",60),("member","Member",140),("at","Date",175),("amount","Total",115),("method","Payment",115),("status","Status",80),("points","Points",75)],rows)
        choices=self.member_choices()
        rbar=tk.Frame(rewards,bg=BG)
        rbar.pack(fill="x",pady=(0,14))
        self.reward_member=tk.StringVar(value=choices[0] if choices else "")
        ttk.Combobox(rbar,textvariable=self.reward_member,values=choices,state="readonly",width=22).pack(side="left",padx=(0,8))
        button(rbar,"Redeem selected",lambda:self.run(self.redeem_reward),primary=True).pack(side="left",padx=4)
        if self.admin:
            button(rbar,"Add reward",lambda:self.run(lambda:self.reward_form(),False)).pack(side="left",padx=4)
            button(rbar,"Edit",lambda:self.run(lambda:self.reward_form(self.reward_table.selected()),False)).pack(side="left",padx=4)
            button(rbar,"Deactivate",lambda:self.run(lambda:self.confirm("Remove this reward from the catalog?",lambda:self.store.deactivate("rewards",self.reward_table.selected()["id"])),False)).pack(side="left",padx=4)
        self.reward_table=self.table(rewards,[("id","ID",60),("name","Reward",350),("points","Point cost",120),("stock","Available",110)],self.store.rows("SELECT * FROM rewards WHERE active=1"))
        label(rewards,"Redeem for a voucher, then collect at the counter. Time vouchers are fulfilled by staff.",10,MUTED).pack(anchor="w",pady=12)
        point_where,point_params=self.scope("p")
        transactions=self.store.rows("SELECT p.*,u.name member FROM points_transactions p JOIN members m ON m.id=p.member_id JOIN users u ON u.id=m.user_id"+point_where+" ORDER BY p.id DESC",point_params)
        label(points,"Points ledger",13,bold=True).pack(anchor="w",pady=(0,8))
        self.table(points,[("id","ID",60),("member","Member",130),("points","Change",80),("reason","Reason",300),("at","Date",170)],transactions,height=4)
        label(points,"Reward vouchers",13,bold=True).pack(anchor="w",pady=(16,8))
        if self.staff:
            self.actions(points,[("Mark collected",lambda:self.run(lambda:self.store.fulfill_reward(self.voucher_table.selected()["id"])))])
        voucher_where,voucher_params=self.scope("r")
        self.voucher_table=self.table(points,[("id","Voucher",70),("member","Member",130),("name","Reward",270),("status","Status",105),("at","Date",170)],self.store.rows("SELECT r.*,u.name member FROM redemptions r JOIN members m ON m.id=r.member_id JOIN users u ON u.id=m.user_id"+voucher_where+" ORDER BY r.id DESC",voucher_params),height=4)
        if self.staff:
            self.report_page(pages[-1],"billing")

    def account_page(self,parent):
        choices=self.member_choices()
        bar=tk.Frame(parent,bg=BG)
        bar.pack(fill="x",pady=(0,20))
        label(bar,"Select member" if self.staff else "Your membership",11,MUTED).pack(side="left",padx=(0,14))
        last=getattr(self,"last_cashier",None)
        self.cashier_member=tk.StringVar(value=last if last in choices else choices[0] if choices else "")
        cb=ttk.Combobox(bar,textvariable=self.cashier_member,values=choices,state="readonly",width=35)
        cb.pack(side="left")
        cb.bind("<<ComboboxSelected>>",lambda e:self.render_account())
        self.account_body=tk.Frame(parent,bg=BG)
        self.account_body.pack(fill="both",expand=True)
        self.render_account()

    def render_account(self):
        for child in self.account_body.winfo_children():
            child.destroy()
        if not self.cashier_member.get():
            label(self.account_body,"Register a member to start billing.",12,MUTED).pack(pady=40)
            return
        self.last_cashier=self.cashier_member.get()
        mid=self.choice_id(self.last_cashier)
        member=self.store.member(mid)
        quote=self.store.quote(mid)
        card=tk.Frame(self.account_body,bg=PANEL,padx=28,pady=24)
        card.pack(side="left",fill="both",expand=True,padx=(0,18))
        label(card,member["name"],24,bold=True).pack(anchor="w")
        label(card,f"CTFLY-{mid:04}  ·  {member['tier']}  ·  {member['points']} points",11,ACCENT).pack(anchor="w",pady=(6,25))
        for caption,key in [("PC sessions","Session"),("Café orders","Order"),("Event entries","Event"),("Subtotal","subtotal"),("Tier discount","discount")]:
            line=tk.Frame(card,bg=PANEL)
            line.pack(fill="x",pady=9)
            label(line,caption,12,MUTED).pack(side="left")
            label(line,("− " if key=="discount" else "")+money(quote[key]),13,TEXT,True).pack(side="right")
        tk.Frame(card,bg=BORDER,height=1).pack(fill="x",pady=18)
        line=tk.Frame(card,bg=PANEL)
        line.pack(fill="x")
        label(line,"Total due",14,bold=True).pack(side="left")
        label(line,money(quote["total"]),26,ACCENT,True).pack(side="right")
        label(card,f"{quote['count']} unbilled items  ·  {quote['total']//100} points after payment",10,MUTED).pack(anchor="w",pady=18)
        right=tk.Frame(self.account_body,bg=PANEL,padx=22,pady=24,width=270)
        right.pack(side="right",fill="y")
        label(right,"Payment",19,bold=True).pack(anchor="w",pady=(0,12))
        if self.staff:
            self.payment_method=tk.StringVar(value="Cash")
            ttk.Combobox(right,textvariable=self.payment_method,values=["Cash","Card","TNG eWallet"],state="readonly",width=24).pack(fill="x")
            button(right,"Confirm paid & issue bill",lambda:self.run(lambda:self.pay_member(mid)),primary=True).pack(fill="x",pady=20)
            label(right,"Record this only after receiving payment.\nCard/eWallet processing happens outside this app.",10,MUTED,wraplength=230,justify="left").pack(anchor="w")
        else:
            label(right,"Pay at the counter",13,ACCENT,True).pack(anchor="w",pady=10)
            label(right,"Staff will settle your sessions, orders and event entries in one bill.",11,MUTED,wraplength=230,justify="left").pack(anchor="w")
        label(right,"LOYALTY TIERS",9,BLUE,True).pack(anchor="w",pady=(35,10))
        label(right,"Bronze · no discount\nSilver · RM 300 spent · 5%\nGold · RM 1,000 spent · 10%\n\nTier upgrades apply to the next bill.",10,MUTED,justify="left",wraplength=240).pack(anchor="w")

    def pay_member(self,mid):
        bid=self.store.checkout(mid,self.payment_method.get())
        self.notice(f"Bill #{bid} paid. Points and membership tier updated.")
        self.show_receipt(bid)

    def new_member_form(self):
        self.form("Register member",[("name","Full name","",None),("username","Username","",None),("password","Password","",None),("dob","Birth date · YYYY-MM-DD","",None),("phone","Phone","",None),("email","Email (optional)","",None)],self.create_member,"Registration requires age 18+.")

    def create_member(self,v):
        actor=self.store.user
        try:
            self.store.register(v["username"],v["password"],v["name"],v["email"],v["phone"],v["dob"])
        finally:
            self.store.user=actor

    def member_form(self,row):
        self.form("Edit membership",[("name","Name",row["name"],None),("phone","Phone",row["phone"],None),("dob","Birth date",row["dob"],None),("active","Status","Active" if row["active"] else "Inactive",["Active","Inactive"])],lambda v:self.store.edit_member(row["id"],v["name"],v["phone"],v["dob"],v["active"]=="Active"),"Tier and points are computed from payments and redemptions.")

    def reward_form(self,row=None):
        row=row or {}
        self.form("Reward",[("name","Name",row.get("name",""),None),("points","Points required",row.get("points",100),None),("stock","Stock",row.get("stock",10),None)],lambda v:self.store.save_reward(row.get("id"),v["name"],int(v["points"]),int(v["stock"])))

    def redeem_reward(self):
        row=self.reward_table.selected()
        mid=self.choice_id(self.reward_member.get())
        rid=self.store.redeem(mid,row["id"])
        self.notice(f"Voucher #{rid} created for {row['name']}. Collect it at the counter.")

    def receipt(self):
        self.show_receipt(self.bill_table.selected()["id"])

    def show_receipt(self,bid):
        row=self.store.one("SELECT b.*,u.name member FROM bills b JOIN members m ON m.id=b.member_id JOIN users u ON u.id=m.user_id WHERE b.id=?",(bid,))
        self.store.owns(row["member_id"])
        text=(f"CTFLY GAMING CAFÉ\nRECEIPT #{bid:06}\n{'─'*36}\n{row['at'].replace('T',' ')} MYT\nMember: {row['member']}\n\n"
              f"PC sessions        {money(row['session_amt'])}\nCafé orders        {money(row['order_amt'])}\nTournament fees    {money(row['event_amt'])}\nTier discount     −{money(row['discount'])}\n{'─'*36}\nTOTAL              {money(row['total'])}\n\nPayment: {row['method']}\nStatus: {row['status']}\nPoints earned: {row['points']}\n\nThank you. See you next game!")
        self.text_window(f"Receipt #{bid}",text,True)

    def text_window(self,title,text,export=False):
        win=tk.Toplevel(self)
        win.title(title)
        win.geometry("560x590")
        win.configure(bg=PANEL)
        body=tk.Text(win,bg=PANEL,fg=TEXT,font=("Consolas",12),relief="flat",padx=24,pady=24,wrap="word")
        body.pack(fill="both",expand=True)
        body.insert("end",text)
        body.config(state="disabled")
        if export:
            def save():
                path=filedialog.asksaveasfilename(parent=win,defaultextension=".txt",initialfile=title.replace("#","")+".txt",filetypes=[("Text receipt","*.txt")])
                if path:
                    try:
                        Path(path).write_text(text,encoding="utf-8")
                    except OSError as exc:
                        messagebox.showerror("Export failed",str(exc),parent=win)
            button(win,"Save receipt",save,primary=True).pack(pady=15)

    def page_staff(self):
        self.store.require()
        staff=self.store.rows("SELECT * FROM staff WHERE active=1")
        today=now().date().isoformat()
        roster_today=self.store.one("SELECT count(*) n FROM rosters WHERE substr(start,1,10)=? AND status IN ('Scheduled','Leave requested')",(today,))["n"]
        requests=self.store.one("SELECT count(*) n FROM rosters WHERE status='Leave requested'")["n"]
        self.stats([("Active staff",len(staff),"Café team"),("Today's shifts",roster_today,"Scheduled & pending leave"),("Leave requests",requests,"Waiting for approval")])
        pages=self.tabs(["Roster & attendance","Staff directory","Shift templates","Report"])
        roster,directory,shifts,report=pages
        actions=[]
        if self.admin:
            actions=[("Assign shift",lambda:self.roster_form()),("Generate week",self.week_form),("Reschedule",lambda:self.roster_form(self.roster_table.selected())),("Cancel",lambda:self.confirm("Cancel this roster entry?",lambda:self.store.roster_action(self.roster_table.selected()["id"],"Cancelled")))]
        actions += [("Clock in",lambda:self.run(lambda:self.store.clock(self.roster_table.selected()["id"]))),("Clock out",lambda:self.run(lambda:self.store.clock(self.roster_table.selected()["id"],out=True))),("Request leave",self.leave_form)]
        self.actions(roster,actions)
        if self.admin:
            self.actions(roster,[("Approve leave",lambda:self.run(lambda:self.store.roster_action(self.roster_table.selected()["id"],"Leave approved"))),("Decline leave",lambda:self.run(lambda:self.store.roster_action(self.roster_table.selected()["id"],"Scheduled")))])
        scope="" if self.admin else " WHERE s.user_id=?"
        params=() if self.admin else (self.store.user["id"],)
        rows=self.store.rows("SELECT r.*,s.name staff,sh.name shift,a.clock_in,a.clock_out,coalesce(a.late,0) late,coalesce(a.early,0) early,round(coalesce(a.minutes,0)/60.0,2) hours FROM rosters r JOIN staff s ON s.id=r.staff_id JOIN shifts sh ON sh.id=r.shift_id LEFT JOIN attendance a ON a.roster_id=r.id"+scope+" ORDER BY r.start DESC",params)
        for row in rows:
            row["state"]=row["status"]
            if row["status"] in ("Scheduled","Leave requested"):
                if row["clock_out"]:
                    row["state"]="Early departure" if row["early"] else "Completed"
                elif row["clock_in"]:
                    row["state"]="Late / on duty" if row["late"] else "On duty"
                elif row["end"]<stamp():
                    row["state"]="Absent"
            row["alert"]=row["late"]>0 or row["state"]=="Absent"
        self.roster_table=self.table(roster,[("id","ID",50),("staff","Staff",130),("shift","Shift",90),("start","Start",175),("end","End",175),("state","Status",140),("late","Late min",85),("early","Early min",85),("hours","Hours",75),("note","Leave note",180)],rows)
        label(roster,"Clock-in uses café time. Overnight shifts end the next day. Staff can clock only their own roster.",9,MUTED).pack(anchor="w",pady=12)
        if self.admin:
            self.actions(directory,[("Add staff",lambda:self.staff_form()),("Edit",lambda:self.staff_form(self.staff_table.selected())),("Deactivate",lambda:self.confirm("Deactivate this employee?",lambda:self.store.deactivate("staff",self.staff_table.selected()["id"])))])
        self.staff_table=self.table(directory,[("id","ID",60),("name","Name",170),("role","Job role",140),("phone","Phone",150),("hire_date","Hired",120)],staff)
        label(directory,"Add an optional staff login when creating an employee, so they can clock their own shifts.",9,MUTED,wraplength=900,justify="left").pack(anchor="w",pady=12)
        if self.admin:
            self.actions(shifts,[("Add shift",lambda:self.shift_form()),("Edit template",lambda:self.shift_form(self.shift_table.selected())),("Delete unused",lambda:self.confirm("Delete this unused shift template?",lambda:self.store.save_shift(self.shift_table.selected()["id"],"","","",True)))])
        self.shift_table=self.table(shifts,[("id","ID",60),("name","Shift",230),("start","Start time",150),("end","End time",150)],self.store.rows("SELECT * FROM shifts"))
        label(shifts,"Template changes apply to new assignments. Existing roster times are kept until rescheduled.",9,MUTED).pack(anchor="w",pady=12)
        self.report_page(report,"staff")

    def staff_form(self,row=None):
        row=row or {}
        fields=[("name","Name",row.get("name",""),None),("role","Job role",row.get("role","Crew"),None),("phone","Phone",row.get("phone",""),None),("hire","Hire date",row.get("hire_date",str(now().date())),None)]
        if not row:
            fields += [("username","Login username (optional)","",None),("password","Login password","",None)]
        self.form("Staff member",fields,lambda v:self.store.save_staff(row.get("id"),v["name"],v["role"],v["phone"],v["hire"],v.get("username",""),v.get("password","")))

    def week_form(self):
        employees=[f"{r['id']} · {r['name']}" for r in self.store.rows("SELECT * FROM staff WHERE active=1")]
        shifts=[f"{r['id']} · {r['name']}" for r in self.store.rows("SELECT * FROM shifts")]
        if not employees or not shifts:
            raise ValueError("Add staff and shifts first.")
        monday=now().date()-timedelta(days=now().weekday())
        self.form("Generate weekly roster",[("staff","Employee",employees[0],employees),("shift","Shift",shifts[0],shifts),("start","Week starts · YYYY-MM-DD",str(monday),None),("days","Work days","Mon–Fri",["Mon–Fri","Every day","Sat–Sun"])],lambda v:self.store.weekly_roster(self.choice_id(v['staff']),self.choice_id(v['shift']),v['start'],{"Mon–Fri":list(range(5)),"Every day":list(range(7)),"Sat–Sun":[5,6]}[v['days']]),"Assigns the selected weekdays within seven days from the start. If any shift conflicts, the entire week is left unchanged.")

    def shift_form(self,row=None):
        row=row or {}
        self.form("Shift template",[("name","Name",row.get("name",""),None),("start","Start · HH:MM",row.get("start","09:00"),None),("end","End · HH:MM",row.get("end","17:00"),None)],lambda v:self.store.save_shift(row.get("id"),v["name"],v["start"],v["end"]),"An end time earlier than start is treated as the next day.")

    def roster_form(self,row=None):
        row=row or {}
        employees=[f"{r['id']} · {r['name']}" for r in self.store.rows("SELECT * FROM staff WHERE active=1")]
        shifts=[f"{r['id']} · {r['name']}" for r in self.store.rows("SELECT * FROM shifts")]
        if not employees or not shifts:
            raise ValueError("Add staff and a shift template first.")
        employee=next((v for v in employees if v.startswith(str(row.get("staff_id"))+" · ")),employees[0])
        shift=next((v for v in shifts if v.startswith(str(row.get("shift_id"))+" · ")),shifts[0])
        self.form("Assign / reschedule shift",[("staff","Employee",employee,employees),("shift","Shift",shift,shifts),("day","Date · YYYY-MM-DD",row.get("start",str(now().date()))[:10],None)],lambda v:self.store.roster(self.choice_id(v["staff"]),self.choice_id(v["shift"]),v["day"],row.get("id")),"Overlap checks include shifts crossing midnight. Multiple days can be assigned individually to build the weekly roster.")

    def leave_form(self):
        row=self.roster_table.selected()
        self.form("Request leave",[("note","Reason","",None)],lambda v:self.store.roster_action(row["id"],"Leave requested",v["note"]))

    def report_page(self,parent,module):
        rows=self.store.report(module)
        label(parent,self.store.metrics(module),10,MUTED,wraplength=900,justify='left').pack(anchor='w',pady=(0,10))
        self.actions(parent,[("Export CSV",lambda:self.export_rows(rows,module)),("Refresh",self.refresh)])
        if not rows:
            label(parent,"No records yet. Reports update as you use the café.",12,MUTED).pack(pady=30)
            return
        columns=[(key,key.replace("_"," "),140) for key in rows[0]]
        self.table(parent,columns,rows,height=5)
        chart=tk.Frame(parent,bg=BG)
        chart.pack(fill="both",expand=True,pady=(18,0))
        try:
            from matplotlib.figure import Figure
            from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
        except ImportError:
            label(chart,"Install matplotlib to display charts. The report and CSV export are available.",11,MUTED).pack(pady=24)
            return
        figure=Figure(figsize=(8,2.6),dpi=100,facecolor=BG)
        axis=figure.add_subplot(111)
        axis.set_facecolor(BG)
        if module=="billing":
            totals=self.store.one("SELECT coalesce(sum(session_amt),0) Sessions,coalesce(sum(order_amt),0) Café,coalesce(sum(event_amt),0) Events FROM bills WHERE status='Paid'")
            labels=list(totals)
            values=[v/100 for v in totals.values()]
            title="Paid revenue by channel (before tier discounts) · RM"
        else:
            numeric={"stations":"Hours","shop":"Units_sold","events":"Teams","staff":"Hours"}[module]
            labels=[str(next(iter(r.values()))) for r in rows][:16]
            values=[r[numeric] or 0 for r in rows][:16]
            title={"stations":"Booked package hours per station","shop":"Best-selling items · units","events":"Registrations by game","staff":"Completed work hours per employee"}[module]
        axis.bar(labels,values,color=ACCENT,width=.55)
        axis.set_title(title,color=TEXT,fontsize=11,pad=15)
        axis.tick_params(colors=MUTED,labelsize=8)
        axis.tick_params(axis="x",rotation=25)
        axis.spines[["top","right"]].set_visible(False)
        for spine in axis.spines.values():
            spine.set_color(BORDER)
        axis.grid(axis="y",alpha=.12,color=MUTED)
        axis.set_axisbelow(True)
        figure.tight_layout()
        plot=FigureCanvasTkAgg(figure,master=chart)
        plot.draw()
        plot.get_tk_widget().pack(fill="both",expand=True)

    def export_rows(self,rows,name):
        if not rows:
            raise ValueError("No report data to export.")
        path=filedialog.asksaveasfilename(parent=self,defaultextension=".csv",initialfile=f"ctfly_{name}_{now().date()}.csv",filetypes=[("CSV","*.csv")])
        if path:
            try:
                with open(path,"w",encoding="utf-8-sig",newline="") as file:
                    writer=csv.DictWriter(file,fieldnames=list(rows[0]))
                    writer.writeheader()
                    # Avoid spreadsheet formula interpretation of user-provided names.
                    writer.writerows({k:("'"+v if isinstance(v,str) and v.startswith(("=","+","-","@")) else v) for k,v in row.items()} for row in rows)
            except OSError as exc:
                raise ValueError(f"Could not export report: {exc}") from None
            self.notice(f"Report exported to {path}")

    def close(self):
        self.oauth_cancel.set()
        self.destroy()


def launch():
    Application().mainloop()
