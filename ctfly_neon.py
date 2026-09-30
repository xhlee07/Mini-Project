"""CTFLY Neon Arena: photographic cyber lounge UI on the existing five modules.

Only presentation changes here. Authentication, billing and all business rules
continue to live in ctfly_store.py and ctfly_app.Application's workflows.
"""
from datetime import datetime, timedelta
from functools import lru_cache
from pathlib import Path
import tkinter as tk
from tkinter import ttk
from tkinter import font as tkfont
import sys
import struct
import zlib
import re
from date_controls import DateControl

import ctfly_app as core
from ctfly_store import now, stamp, money

# Shared widgets and inherited screens use exactly the same design tokens.
BG = core.BG = "#070a14"
PANEL = core.PANEL = "#101729"
SURFACE = core.SURFACE = "#182238"
BORDER = core.BORDER = "#26344f"
TEXT = core.TEXT = "#f1f6ff"
MUTED = core.MUTED = "#98abc9"
ACCENT = core.ACCENT = "#58e5ff"
BLUE = core.BLUE = "#9f8bff"
RED = core.RED = "#ff789d"
AMBER = core.AMBER = "#ffd487"
FONT = "Segoe UI"
ASSETS = Path(__file__).resolve().parent / "assets"
label, button = core.label, core.button


@lru_cache(maxsize=24)
def shade_png(width,height):
    """Code-native translucent gradient used behind hero copy (RGBA PNG)."""
    row=bytearray()
    for x in range(width):
        fraction=x/max(1,width-1)
        alpha=int(225*max(0,1-fraction/.7)**.65)
        row.extend((7,10,20,alpha))
    raw=(b'\0'+bytes(row))*height
    def chunk(kind,data):
        return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',width,height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')


class Artwork(tk.Canvas):
    """Responsive, locally stored background. Pillow enhances scaling if present.

    Native Tk PNG fallback keeps photographic backgrounds usable without pip.
    Text stays real UI text rather than being baked into generated photographs.
    """
    def __init__(self,parent,asset="neon_arena.png",height=160,shade=True,**kwargs):
        super().__init__(parent,bg=BG,height=height,highlightthickness=0,**kwargs)
        self.path=ASSETS/asset
        self.shade=shade
        self.photo=None
        self.source=None
        self.native=None
        self._pending=None
        cache=self.winfo_toplevel().__dict__.setdefault('_art_sources',{})
        try:
            from PIL import Image
            if self.path.exists():
                if str(self.path) not in cache:
                    with Image.open(self.path) as file:
                        cache[str(self.path)]=file.convert("RGB")
                self.source=cache[str(self.path)]
        except ImportError:
            if self.path.exists():
                if str(self.path) not in cache:
                    cache[str(self.path)]=tk.PhotoImage(file=str(self.path),master=self)
                self.native=cache[str(self.path)]
        self.bind("<Configure>",self.schedule)

    def schedule(self,event=None):
        if self._pending:
            self.after_cancel(self._pending)
        self._pending=self.after(45,self.paint)

    def paint(self):
        self._pending=None
        width,height=max(1,self.winfo_width()),max(1,self.winfo_height())
        self.delete("backdrop")
        cache=self.winfo_toplevel().__dict__.setdefault('_art_sizes',{})
        key=(str(self.path),width,height)
        if self.source:
            from PIL import Image, ImageOps, ImageTk
            if key not in cache:
                if self.path.parent.name=='menu':
                    image=Image.new('RGB',(width,height),'#0b1120')
                    thumb=ImageOps.contain(self.source,(width,height),method=Image.Resampling.LANCZOS)
                    image.paste(thumb,((width-thumb.width)//2,(height-thumb.height)//2))
                else:
                    image=ImageOps.fit(self.source,(width,height),method=Image.Resampling.LANCZOS,centering=(.5,.52))
                cache[key]=ImageTk.PhotoImage(image,master=self)
            self.photo=cache[key]
            self.create_image(0,0,image=self.photo,anchor="nw",tags="backdrop")
        elif self.native:
            if key not in cache:
                import math
                ratio=max(self.native.width()/width,self.native.height()/height) if self.path.parent.name=='menu' else min(self.native.width()/width,self.native.height()/height)
                factor=max(1,math.ceil(ratio) if self.path.parent.name=='menu' else math.floor(ratio))
                cache[key]=self.native.subsample(factor,factor)
            self.photo=cache[key]
            self.create_image(width//2,height//2,image=self.photo,tags="backdrop")
        else:
            # Graceful fallback if a deployment accidentally omits image assets.
            for y in range(0,height,2):
                t=y/max(1,height)
                color=f"#{int(10+9*t):02x}{int(17+9*t):02x}{int(37+21*t):02x}"
                self.create_rectangle(0,y,width,y+2,fill=color,outline="",tags="backdrop")
        if self.shade:
            self.gradient=tk.PhotoImage(data=shade_png(width,height),format='png',master=self)
            self.create_image(0,0,image=self.gradient,anchor='nw',tags='backdrop')
        self.create_line(0,height-1,width,height-1,fill=BORDER,tags="backdrop")
        while len(cache)>72:
            cache.pop(next(iter(cache)))
        self.tag_lower("backdrop")
        if hasattr(self,"draw_foreground"):
            self.draw_foreground(width,height)

    def destroy(self):
        if self._pending:
            self.after_cancel(self._pending)
        super().destroy()


class Selection:
    """Card selection presents the same interface as a record table."""
    def __init__(self,rows,current=None):
        self.data=rows
        self.current=current

    def selected(self):
        if self.current is None:
            raise ValueError("Choose a card first.")
        return next(row for row in self.data if row["id"]==self.current)


class ViewDeck(tk.Frame):
    """Vertical contextual navigation instead of a row of notebook tabs."""
    def __init__(self,parent,names,module):
        super().__init__(parent,bg=BG)
        self.frames=[]
        self.buttons=[]
        self.current=0
        self.lazy={}
        rail=tk.Frame(self,bg=PANEL,width=178,padx=12,pady=18)
        self.rail=rail
        rail.pack(side="left",fill="y",padx=(0,18))
        rail.pack_propagate(False)
        label(rail,"CONTROL PANEL",8,MUTED,True).pack(anchor="w",padx=8,pady=(0,18))
        self.area=tk.Frame(self,bg=BG)
        self.area.pack(fill="both",expand=True)
        for index,name in enumerate(names):
            frame=tk.Frame(self.area,bg=BG)
            self.frames.append(frame)
            btn=button(rail,name,lambda i=index:self.select(i))
            btn.configure(anchor="w",font=(FONT,9,"bold"),wraplength=135,pady=11)
            btn.pack(fill="x",pady=3)
            self.buttons.append(btn)
        panel=tk.Frame(rail,bg=PANEL)
        panel.pack(side="bottom",fill="x",padx=8)
        label(panel,"CTFLY NETWORK",8,ACCENT,True).pack(anchor="w")
        label(panel,"●  Café online\nMalaysia · UTC+8",8,MUTED,justify="left").pack(anchor="w",pady=(8,0))
        self.select(0)

    def tabs(self):
        return tuple(str(frame) for frame in self.frames)

    def insert(self,index,name):
        frame=tk.Frame(self.area,bg=BG)
        self.frames.insert(index,frame)
        item=button(self.rail,name,lambda:None)
        item.configure(anchor='w',font=(FONT,9,'bold'),wraplength=135,pady=11)
        item.pack(fill='x',pady=3,before=self.buttons[index])
        self.buttons.insert(index,item)
        for i,btn in enumerate(self.buttons):
            btn.configure(command=lambda n=i:self.select(n))
        self.select(self.current)
        return frame

    def index(self,target):
        if isinstance(target,int):
            return target
        return self.tabs().index(str(target))

    def select(self,target=None):
        if target is None:
            return str(self.frames[self.current])
        self.current=self.index(target)
        for i,frame in enumerate(self.frames):
            frame.pack_forget()
            self.buttons[i].config(bg="#1b3050" if i==self.current else PANEL,fg=ACCENT if i==self.current else MUTED)
        self.frames[self.current].pack(fill="both",expand=True)
        callback=self.lazy.pop(str(self.frames[self.current]),None)
        if callback:
            callback()


class NeonApplication(core.Application):
    modern_menu=True
    def __init__(self,store=None):
        if sys.platform=='win32':
            # Avoid Windows bitmap scaling/cropping at 125% or 150% display size.
            import ctypes
            try:
                ctypes.windll.user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
            except (AttributeError,OSError):
                ctypes.windll.user32.SetProcessDPIAware()
        super().__init__(store)
        self.title("CTFLY | Neon Arena · Gaming Café")
        # Fit normal laptop screens rather than forcing an oversized window.
        w=min(1440,self.winfo_screenwidth()-70)
        h=min(930,self.winfo_screenheight()-100)
        self.minsize(1120,710)
        self.geometry(f"{max(1120,w)}x{max(710,h)}+25+25")
        self._resize_timer=None
        self.bind('<Configure>',self.resize_layout,add='+')

    def resize_layout(self,event):
        if event.widget is not self or not self.store.user:
            return
        compact=event.height<820
        if compact!=getattr(self,'layout_compact',compact):
            if self._resize_timer:
                self.after_cancel(self._resize_timer)
            def rebuild():
                self._resize_timer=None
                if self.store.user:
                    self.refresh()
            self._resize_timer=self.after(120,rebuild)

    def styles(self):
        super().styles()
        style=ttk.Style(self)
        style.configure("TEntry",fieldbackground="#0c1325",padding=10,bordercolor=BORDER,lightcolor=BORDER,darkcolor=BORDER)
        style.configure("TCombobox",fieldbackground="#0c1325",padding=9,bordercolor=BORDER,lightcolor=BORDER,darkcolor=BORDER,arrowcolor=ACCENT)
        style.configure('Compact.TCombobox',padding=(7,3))
        style.configure("Treeview",background=PANEL,fieldbackground=PANEL,rowheight=42,borderwidth=0)
        style.configure("Treeview.Heading",background="#1a2841",foreground="#b8cae3",padding=(8,11),font=(FONT,8,"bold"),relief='flat',borderwidth=0)
        style.map("Treeview",background=[("selected","#193d59")],foreground=[("selected",ACCENT)])
        style.map("TCombobox",fieldbackground=[("readonly","#0c1325")],foreground=[("readonly",TEXT)],selectbackground=[("readonly","#0c1325")])

    def show_login(self,register=False):
        self.clear()
        self.store.user=None
        self.login_mode=register
        background=Artwork(self,height=800,shade=False)
        background.pack(fill="both",expand=True)
        def draw(width,height):
            background.delete("copy")
            if width<1200:
                heading=40
                left=44
            else:
                heading=54
                left=65
            background.create_text(left,52,anchor="nw",text="CTFLY",fill=TEXT,font=(FONT,30,"bold"),tags="copy")
            background.create_text(left,117,anchor="nw",text="G A M I N G   C A F É",fill=ACCENT,font=(FONT,9,"bold"),tags="copy")
            titlefont=tkfont.Font(family=FONT,size=heading,weight='bold')
            title_y=int(height*.31)
            background.create_text(left,title_y,anchor="nw",text="ENTER THE\nNEXT LEVEL.",fill=TEXT,font=titlefont,tags="copy")
            background.create_text(left,title_y+titlefont.metrics('linespace')*2+26,anchor="nw",text="Your rig. Your crew. Your arena.",fill="#c6d9f0",font=(FONT,14),tags="copy")
            background.create_line(left,height-90,left+65,height-90,fill=ACCENT,width=3,tags="copy")
            background.create_text(left,height-67,anchor="nw",text="HIGH-PERFORMANCE PCs    /    ESPORTS    /    CAFÉ",fill="#c6d9f0",font=(FONT,9,"bold"),tags="copy")
        background.draw_foreground=draw
        panel=tk.Frame(background,bg=PANEL,highlightthickness=1,highlightbackground=BORDER)
        panel.place(relx=1,x=-38,rely=.5,anchor="e",width=550 if register else 440,relheight=.91 if register else .82)
        strip=tk.Frame(panel,bg=ACCENT,height=3)
        strip.pack(fill="x")
        scroll=core.Scroll(panel,bg=PANEL)
        scroll.pack(fill="both",expand=True)
        box=tk.Frame(scroll.inner,bg=PANEL,padx=30,pady=24)
        box.pack(fill="x")
        switch=tk.Frame(box,bg=PANEL)
        switch.pack(fill="x",pady=(0,24))
        for text,mode in [("SIGN IN",False),("REGISTER",True)]:
            item=button(switch,text,lambda m=mode:self.show_login(m),primary=register==mode)
            item.configure(font=(FONT,9,"bold"),pady=10)
            item.pack(side="left",fill="x",expand=True,padx=(0,5))
        label(box,"CREATE YOUR PLAYER ID" if register else "WELCOME TO THE ARENA",8,ACCENT,True).pack(anchor="w")
        label(box,"Join CTFLY." if register else "Welcome back.",26,TEXT,True).pack(anchor="w",pady=(9,5))
        label(box,"Create an account to unlock your next session." if register else "Sign in. Pick your station. Start playing.",10,MUTED,wraplength=350,justify="left").pack(anchor="w",pady=(0,15))
        self.login_values={}
        fields=[("username","Username or email"),("password","Password")]
        if register:
            fields=[("name","Full name"),("username","Username"),("email","Email · optional"),("phone","Phone"),("dob","Birth date · YYYY-MM-DD"),("password","Password · 8+ characters"),("password_confirm","Confirm password")]
        fields_area=tk.Frame(box,bg=PANEL)
        fields_area.pack(fill='x')
        for col in range(2):
            fields_area.columnconfigure(col,weight=1,uniform='registration')
        positions={'name':(0,0,1),'username':(0,1,1),'email':(1,0,1),'phone':(1,1,1),'dob':(2,0,2),'password':(3,0,1),'password_confirm':(3,1,1)}
        for index,(key,caption) in enumerate(fields):
            slot=tk.Frame(fields_area,bg=PANEL)
            if register:
                row,col,span=positions[key]
                slot.grid(row=row,column=col,columnspan=span,sticky='ew',padx=(0,10 if col==0 and span==1 else 0))
            else:
                slot.grid(row=index,column=0,columnspan=2,sticky='ew')
            label(slot,caption,9,MUTED).pack(anchor="w",pady=(7,4))
            var=tk.StringVar()
            self.login_values[key]=var
            entry=DateControl(slot,var,birth=True) if key=='dob' else ttk.Entry(slot,textvariable=var,show="•" if "password" in key else "")
            entry.pack(fill="x")
            if key=="password" and not register:
                entry.bind("<Return>",lambda e:self.local_login())
        self.login_error=label(box,"",9,RED,wraplength=350,justify="left")
        self.login_error.pack(anchor="w",pady=(10,3))
        button(box,"CREATE PLAYER ID  →" if register else "ENTER CTFLY  →",self.local_login,primary=True).pack(fill="x",pady=(4,12))
        label(box,"OR",8,MUTED).pack(pady=(0,10))
        self.google_button=button(box,"G    Continue with Google",self.start_google)
        self.google_button.configure(bg="#eaf0fa",fg="#13213a",activebackground=ACCENT)
        self.google_button.pack(fill="x")
        label(box,"18+ venue. Your date of birth is required for booking.",8,MUTED,wraplength=350,justify="left").pack(anchor="w",pady=(16,5))
        if not register:
            label(box,"DEMO ACCESS",8,BLUE,True).pack(anchor="w",pady=(15,5))
            label(box,"admin / Admin@123\nstaff1 / Staff@123   ·   gamer1 / Gamer@123",9,MUTED,justify="left").pack(anchor="w")

    def shell(self):
        self.clear()
        self.login_mode=False
        header=tk.Frame(self,bg="#0b1120",height=78)
        header.pack(fill="x")
        header.pack_propagate(False)
        brand=tk.Frame(header,bg="#0b1120")
        brand.pack(side="left",padx=(26,28),pady=8)
        label(brand,"CTFLY",23,TEXT,True).pack(anchor="w")
        self.nav={}
        short={"stations":"PC Arena","shop":"Café Menu","events":"Tournaments","billing":"Membership","staff":"Staff"}
        for key,num,title,subtitle in core.MODULES:
            nav=button(header,short[key],lambda k=key:self.navigate(k))
            nav.configure(padx=10,pady=12,font=(FONT,10,"bold"))
            nav.pack(side="left",padx=3,pady=14)
            self.nav[key]=nav
        profile=tk.Frame(header,bg="#0b1120")
        profile.pack(side="right",padx=20)
        button(profile,"Log out",self.logout).pack(side="right",padx=(16,0))
        user=self.store.user
        label(profile,user["name"][:19],10,TEXT,True).pack(anchor="e")
        label(profile,user["role"].upper(),8,ACCENT,True).pack(anchor="e",pady=3)
        tk.Frame(self,bg=BORDER,height=1).pack(fill="x")
        self.content=tk.Frame(self,bg=BG,padx=24,pady=18)
        self.content.pack(fill="both",expand=True)
        self.page_host=self.content
        self._page_cache={}
        footer=tk.Frame(self,bg="#0b1120",height=30)
        footer.pack(side='bottom',fill="x",before=self.content)
        self.status=label(footer,"●  CTFLY café network online",8,MUTED,anchor="w",padx=25,pady=7)
        self.status.pack(side="left",fill="x",expand=True)
        label(footer,"MALAYSIA / UTC+8",8,MUTED).pack(side="right",padx=25)
        self.navigate(self.module if self.module in self.nav else "stations")

    def logout(self):
        self.menu_category='All'
        self.menu_search=''
        self.saved_cart_member=''
        self.task_day=now().date().isoformat()
        if getattr(self,'_menu_search_timer',None):
            self.after_cancel(self._menu_search_timer)
            self._menu_search_timer=None
        super().logout()

    def navigate(self,key,keep=False):
        if getattr(self,'_menu_search_timer',None):
            self.after_cancel(self._menu_search_timer)
            self._menu_search_timer=None
        self.update_idletasks()
        self.layout_compact=self.winfo_height()<820
        if hasattr(self,"book") and self.book.winfo_exists():
            self.tab_index[self.module]=self.book.index(self.book.select())
        previous=getattr(self,'_page_cache',{}).get(self.module)
        if previous:
            previous['state']={name:getattr(self,name) for name in previous['keys']}
        # Any explicit refresh follows a data mutation; invalidate all dependent
        # module views. Normal navigation reuses existing widgets and scroll state.
        if keep:
            for page in self._page_cache.values():
                page['frame'].destroy()
            self._page_cache={}
        for child in self.page_host.winfo_children():
            child.pack_forget()
        self.module=key
        self.timers=[]
        for k,nav in self.nav.items():
            nav.config(bg="#1b3050" if k==key else "#0b1120",fg=ACCENT if k==key else MUTED)
        if key=='stations':
            self.store.tick()
        revision=Path(self.store.path).stat().st_mtime_ns
        cached=self._page_cache.get(key)
        if cached and cached['revision']==revision and cached['compact']==self.layout_compact:
            self.content=cached['frame']
            self.content.pack(fill='both',expand=True)
            for name,value in cached['state'].items():
                setattr(self,name,value)
            if key=='shop':
                self.render_cart()
            return
        if cached:
            cached['frame'].destroy()
        self.content=tk.Frame(self.page_host,bg=BG)
        self.content.pack(fill='both',expand=True)
        before=dict(self.__dict__)
        headings={
            "stations":("YOUR NEXT SESSION STARTS HERE","Choose your battlestation.","Standard & VIP rigs. Book now or secure your next session."),
            "shop":("REFUEL. RELOAD. REPEAT.","Good games need good fuel.","Coffee, snacks and game credits — straight to your station."),
            "events":("PLAY TOGETHER. WIN TOGETHER.","Step into the competition.","Find your event. Build your team. Take the next round."),
            "billing":("ONE ACCOUNT. MORE POSSIBILITIES.","Your CTFLY player hub.","Sessions, orders, rewards and one simple checkout."),
            "staff":("BEHIND EVERY GREAT SESSION","The crew control room.","Plan the week. Keep the floor running. Track every shift."),
        }
        eyebrow,title,subtitle=headings[key]
        hero=Artwork(self.content,"neon_cafe.png" if key=="shop" else "neon_arena.png",height=56 if key=='shop' else 100 if self.layout_compact else 148)
        hero.pack(fill="x",pady=(0,16))
        def draw(w,h):
            hero.delete("copy")
            if key=='shop':
                hero.create_text(26,4,anchor='nw',text='CTFLY Café · Gaming fuel, delivered.',fill=TEXT,font=(FONT,18,'bold'),tags='copy')
                hero.create_text(28,36,anchor='nw',text='Choose your favorites, check your basket, then place your order.',fill=MUTED,font=(FONT,8),tags='copy')
                return
            hero.create_text(28,13 if self.layout_compact else 24,anchor="nw",text=eyebrow,fill=ACCENT,font=(FONT,8,"bold"),tags="copy")
            hero.create_text(26,33 if self.layout_compact else 48,anchor="nw",text=title,fill=TEXT,font=(FONT,22 if self.layout_compact else 27,"bold"),tags="copy")
            hero.create_text(28,77 if self.layout_compact else 103,anchor="nw",text=subtitle,fill="#c9d9f1",font=(FONT,9 if self.layout_compact else 10),tags="copy")
            hero.create_rectangle(w-149,25,w-23,54,fill="#111d33",outline=ACCENT,tags="copy")
            hero.create_text(w-86,39,text="● LIVE CAFÉ",fill=ACCENT,font=(FONT,9,"bold"),tags="copy")
        hero.draw_foreground=draw
        getattr(self,f"page_{key}")()
        index=self.tab_index.get(key,0)
        if index<len(self.book.tabs()):
            self.book.select(index)
        names={'book','timers','metric_labels','station_signature','match_table'}
        for name,value in self.__dict__.items():
            if name not in before or before[name] is not value:
                if isinstance(value,(tk.Widget,tk.Variable,Selection)) or isinstance(value,dict) and any(isinstance(v,tk.Widget) for v in value.values()):
                    names.add(name)
        names.discard('content')
        names={name for name in names if hasattr(self,name)}
        self._page_cache[key]={'frame':self.content,'keys':names,'state':{name:getattr(self,name) for name in names},'revision':Path(self.store.path).stat().st_mtime_ns,'compact':self.layout_compact}

    def tabs(self,names):
        self.book=ViewDeck(self.content,names,self.module)
        self.book.pack(fill="both",expand=True)
        return self.book.frames

    def report_page(self,parent,module):
        # Importing matplotlib and building charts is deferred until opened.
        self.book.lazy[str(parent)]=lambda:core.Application.report_page(self,parent,module)

    def stats(self,items):
        self.metric_labels={}
        if self.module=='shop':
            return
        row=tk.Frame(self.content,bg=BG)
        row.pack(fill="x",pady=(0,17))
        colors=[ACCENT,BLUE,AMBER,RED]
        for i,(caption,value,detail) in enumerate(items):
            row.columnconfigure(i,weight=1,uniform="metrics")
            card=tk.Frame(row,bg=PANEL,padx=17,pady=8 if self.layout_compact else 12,highlightbackground=BORDER,highlightthickness=1)
            card.grid(row=0,column=i,sticky="ew",padx=(0,10 if i<len(items)-1 else 0))
            line=tk.Frame(card,bg=PANEL)
            line.pack(fill="x")
            label(line,caption.upper(),8,MUTED,True).pack(side="left")
            metric=label(line,str(value),18 if self.layout_compact else 21,colors[i%4],True)
            metric.pack(side='right')
            self.metric_labels[caption]=metric
            if not self.layout_compact:
                label(card,detail,8,MUTED).pack(anchor="w",pady=(5,0))

    def section(self,parent,title,description=""):
        line=tk.Frame(parent,bg=BG)
        line.pack(fill="x",pady=(0,13))
        label(line,title,16,TEXT,True).pack(side="left")
        if description:
            label(line,description,9,MUTED).pack(side="right",padx=6)

    def table(self,parent,columns,rows,height=12):
        widget=super().table(parent,columns,rows,height)
        widget.tree.tag_configure("odd",background="#141e32")
        return widget

    def page_stations(self):
        # The existing CRUD/history/report workflows remain available in the new deck.
        super().page_stations()
        self.timers=[]
        floor=self.book.frames[0]
        for child in floor.winfo_children():
            child.destroy()
        rows=self.store.rows("SELECT * FROM stations WHERE status!='Retired' ORDER BY name")
        right=tk.Frame(floor,bg=PANEL,width=285,padx=20,pady=20,highlightbackground=BORDER,highlightthickness=1)
        right.pack(side="right",fill="y",padx=(17,0))
        right.pack_propagate(False)
        self.station_action=tk.Frame(right,bg=PANEL)
        self.station_action.pack(side='bottom',fill='x',pady=(12,0))
        inspector_scroll=core.Scroll(right,bg=PANEL)
        inspector_scroll.pack(fill='both',expand=True)
        self.station_inspector=inspector_scroll.inner
        arena=tk.Frame(floor,bg=BG)
        arena.pack(fill="both",expand=True)
        self.section(arena,"Arena floor", "Select a rig to see packages")
        legend=tk.Frame(arena,bg=BG)
        legend.pack(fill="x",pady=(0,10))
        for text,color in [("Available",ACCENT),("Playing",BLUE),("Reserved",AMBER),("Offline",RED)]:
            label(legend,"● "+text,8,color).pack(side="left",padx=(0,18))
        scroll=core.Scroll(arena,bg=BG)
        scroll.pack(fill="both",expand=True)
        self.seat_cards={}
        selected=getattr(self,"selected_station",None)
        if selected not in [r['id'] for r in rows]:
            selected=rows[0]['id'] if rows else None
        for zone in ("Standard","VIP"):
            group=[r for r in rows if r["zone"]==zone]
            heading=tk.Frame(scroll.inner,bg=BG)
            heading.pack(fill="x",pady=(10,10))
            label(heading,"STANDARD ZONE" if zone=="Standard" else "VIP / ELITE ZONE",9,ACCENT if zone=="Standard" else BLUE,True).pack(side="left")
            label(heading,f"{len(group)} RIGS",8,MUTED).pack(side="right")
            grid=tk.Frame(scroll.inner,bg=BG)
            grid.pack(fill="x")
            for col in range(4):
                grid.columnconfigure(col,weight=1,uniform="rig")
            for i,row in enumerate(group):
                color={"Available":ACCENT,"Occupied":BLUE,"Reserved":AMBER,"Maintenance":RED}[row['status']]
                card=tk.Frame(grid,bg=PANEL,padx=10,pady=12,highlightbackground=ACCENT if row['id']==selected else BORDER,highlightthickness=1,cursor="hand2")
                card.grid(row=i//4,column=i%4,sticky="nsew",padx=4,pady=4)
                monitor=tk.Canvas(card,bg=PANEL,height=57,highlightthickness=0,cursor="hand2")
                monitor.pack(fill="x")
                def rigdraw(event,cv=monitor,c=color):
                    cv.delete("all")
                    w=event.width
                    cv.create_polygon(w*.18,2,w*.82,2,w*.82,39,w*.18,39,fill="#0a1021",outline=c,width=1)
                    cv.create_line(w*.5,39,w*.5,48,fill=c,width=2)
                    cv.create_line(w*.37,49,w*.63,49,fill=c,width=2)
                    cv.create_line(w*.25,29,w*.47,11,w*.62,24,w*.75,10,fill=c,width=2)
                    cv.create_line(w*.24,34,w*.76,34,fill=BORDER)
                monitor.bind('<Configure>',rigdraw)
                label(card,row['name'],12,TEXT,True).pack(anchor="center",pady=(6,4))
                label(card,"● "+row['status'],8,color).pack(anchor="center")
                label(card,money(row['rate'])+" / hr",8,MUTED).pack(anchor="center",pady=(5,0))
                self.seat_cards[row['id']]=card
                for child in [card,*card.winfo_children()]:
                    child.bind('<Button-1>',lambda e,r=row:self.inspect_station(r))
        if selected:
            self.inspect_station(next(r for r in rows if r['id']==selected))

    def inspect_station(self,row):
        self.selected_station=row['id']
        for sid,card in self.seat_cards.items():
            card.config(highlightbackground=ACCENT if sid==row['id'] else BORDER)
        parent=self.station_inspector
        for child in parent.winfo_children():
            child.destroy()
        for child in self.station_action.winfo_children():
            child.destroy()
        self.timers=[]
        label(parent,"SELECTED BATTLESTATION",8,ACCENT,True).pack(anchor="w")
        label(parent,row['name'],24,TEXT,True).pack(anchor="w",pady=(8,2))
        label(parent,row['zone'].upper()+"  /  "+row['status'].upper(),9,BLUE,True).pack(anchor="w")
        label(parent,row['spec'],9,MUTED,wraplength=230,justify="left").pack(anchor="w",pady=(9,7))
        label(parent,money(row['rate'])+" / hour",15,ACCENT,True).pack(anchor="w",pady=(0,10))
        session=self.store.one("SELECT * FROM sessions WHERE station_id=? AND status='Active'",(row['id'],))
        if session:
            timer=label(parent,"Live session",11,AMBER,True)
            timer.pack(anchor="w",pady=(0,15))
            self.timers.append((timer,session['end']))
        tk.Frame(parent,bg=BORDER,height=1).pack(fill="x",pady=(0,14))
        choices=self.member_choices()
        self.booking_member=tk.StringVar(value=choices[0] if choices else "")
        label(parent,"Member",9,MUTED).pack(anchor="w",pady=(0,5))
        ttk.Combobox(parent,textvariable=self.booking_member,values=choices,state="readonly").pack(fill="x")
        label(parent,"Session package",9,MUTED).pack(anchor="w",pady=(12,5))
        self.booking_package=tk.StringVar(value="2 hours")
        ttk.Combobox(parent,textvariable=self.booking_package,values=["1 hour","2 hours","3 hours","4 hours","8 hours"],state="readonly").pack(fill="x")
        label(parent,"Start time",9,MUTED).pack(anchor="w",pady=(12,5))
        self.booking_start=tk.StringVar()
        DateControl(parent,self.booking_start,time=True,optional=True).pack(fill="x")
        def book():
            self.store.book(row['id'],self.choice_id(self.booking_member.get()),int(self.booking_package.get().split()[0])*60,self.booking_start.get().strip() or None)
            self.notice(f"{row['name']} booked. Your next session is ready.")
        btn=button(self.station_action,"RESERVE THIS RIG  →",lambda:self.run(book),primary=True)
        self.booking_submit=btn
        btn.pack(fill="x")
        if row['status']=='Maintenance':
            btn.config(state='disabled',text='RIG OFFLINE')
        label(self.station_action,"18+ only · prepaid packages",8,MUTED).pack(anchor="w",pady=(7,0))

    def page_shop(self):
        super().page_shop()
        menu=self.book.frames[0]
        for child in menu.winfo_children():
            child.destroy()
        cart=tk.Frame(menu,bg=PANEL,width=300,padx=20,pady=14,highlightbackground=BORDER,highlightthickness=1)
        cart.pack(side='right',fill='y',padx=(18,0))
        cart.pack_propagate(False)
        label(cart,"2 / CHECK YOUR ORDER",8,ACCENT,True).pack(anchor='w')
        heading=tk.Frame(cart,bg=PANEL)
        heading.pack(fill='x',pady=(6,6))
        label(heading,"Your basket",17,TEXT,True).pack(side='left')
        clear=button(heading,'Clear',self.clear_cart)
        clear.configure(font=(FONT,8),pady=2,padx=8)
        clear.pack(side='right')
        self.cart_lines=core.Scroll(cart,bg=PANEL)
        self.cart_lines.pack(fill='both',expand=True)
        self.cart_lines.canvas.configure(height=90)
        checkout=tk.Frame(cart,bg=PANEL)
        checkout.pack(side='bottom',fill='x',before=self.cart_lines,pady=(8,0))
        choices=self.member_choices()
        previous=getattr(self,'saved_cart_member','')
        self.cart_member=tk.StringVar(value=previous if previous in choices else choices[0] if choices else '')
        if self.staff:
            label(checkout,'Member',8,MUTED).pack(anchor='w')
            ttk.Combobox(checkout,textvariable=self.cart_member,values=choices,state='readonly',style='Compact.TCombobox').pack(fill='x')
        def delivery_choices(*args):
            self.saved_cart_member=self.cart_member.get()
            mid=self.choice_id(self.cart_member.get()) if self.cart_member.get() else 0
            rows=self.store.rows("SELECT DISTINCT s.id,s.name FROM stations s JOIN sessions se ON se.station_id=s.id WHERE se.member_id=? AND se.status='Active'",(mid,))
            seats=['Counter pickup']+[f"{r['id']} · {r['name']}" for r in rows]
            self.delivery_combo.configure(values=seats)
            if self.cart_seat.get() not in seats:
                self.cart_seat.set('Counter pickup')
        label(checkout,'Delivery · pickup or your active PC',8,MUTED).pack(anchor='w',pady=(6,2))
        self.cart_seat=tk.StringVar(value='Counter pickup')
        self.delivery_combo=ttk.Combobox(checkout,textvariable=self.cart_seat,state='readonly',style='Compact.TCombobox')
        self.delivery_combo.pack(fill='x')
        self.cart_member.trace_add('write',delivery_choices)
        delivery_choices()
        totalrow=tk.Frame(checkout,bg=PANEL)
        totalrow.pack(fill='x',pady=(7,5))
        label(totalrow,"TOTAL",9,MUTED,True).pack(side='left')
        self.cart_total=label(totalrow,"",17,ACCENT,True)
        self.cart_total.pack(side='right')
        send=button(checkout,"PLACE ORDER  →",self.submit_menu_order,primary=True)
        self.order_submit=send
        send.config(pady=5,font=(FONT,9,'bold'))
        send.pack(fill='x')
        label(checkout,"Payment at the café counter.",8,MUTED,wraplength=245,justify='left').pack(anchor='w')
        catalogue=tk.Frame(menu,bg=BG)
        catalogue.pack(fill='both',expand=True)
        self.section(catalogue,"1 / Choose your food & drinks")
        search=tk.StringVar(value=getattr(self,'menu_search',''))
        searchbar=tk.Frame(catalogue,bg=BG)
        searchbar.pack(fill='x',pady=(0,12))
        ttk.Entry(searchbar,textvariable=search).pack(side='left',fill='x',expand=True)
        button(searchbar,'Clear search',lambda:search.set('')).pack(side='left',padx=(8,0))
        filterbar=tk.Frame(catalogue,bg=BG)
        filterbar.pack(fill='x',pady=(0,12))
        category=tk.StringVar(value=getattr(self,'menu_category','All'))
        products=self.store.rows("SELECT p.*,c.name category FROM products p JOIN categories c ON c.id=p.category_id WHERE p.active=1 ORDER BY c.name,p.name")
        pills={}
        listing=core.Scroll(catalogue,bg=BG)
        listing.pack(fill='both',expand=True)
        def render(*args):
            self.menu_search,self.menu_category=search.get(),category.get()
            favorites={r['product_id'] for r in self.store.rows('SELECT product_id FROM product_favorites WHERE user_id=?',(self.store.user['id'],))}
            for child in listing.inner.winfo_children():
                child.destroy()
            for name,pill in pills.items():
                pill.config(bg='#1b3050' if name==category.get() else SURFACE,fg=ACCENT if name==category.get() else MUTED)
            visible=[p for p in products if (category.get()=='All' or category.get()==p['category'] or category.get()=='Favorites' and p['id'] in favorites) and search.get().casefold() in (p['name']+p['category']).casefold()]
            for i,p in enumerate(visible):
                listing.inner.columnconfigure(i%2,weight=1,uniform='menu')
                card=tk.Frame(listing.inner,bg=PANEL,highlightbackground=BORDER,highlightthickness=1)
                card.grid(row=i//2,column=i%2,sticky='nsew',padx=4,pady=5)
                art=Artwork(card,self.product_image(p),height=120 if self.layout_compact else 150,shade=False)
                art.pack(fill='x')
                if not (ASSETS/self.product_image(p)).exists():
                    def digital(w,h,canvas=art,price=p['price'],category=p['category']):
                        canvas.delete('credit')
                        canvas.create_rectangle(w*.18,20,w*.82,h-20,fill='#182238',outline=BLUE,width=2,tags='credit')
                        canvas.create_text(w/2,h*.4,text=category.upper(),fill=BLUE,font=(FONT,11,'bold'),tags='credit')
                        canvas.create_text(w/2,h*.65,text=money(price),fill=TEXT,font=(FONT,21,'bold'),tags='credit')
                    art.draw_foreground=digital
                content=tk.Frame(card,bg=PANEL,padx=14,pady=10)
                content.pack(fill='x')
                label(content,p['name'],12,TEXT,True,wraplength=220,justify='left').pack(anchor='w')
                star=button(content,'★ Saved' if p['id'] in favorites else '☆ Save',lambda pid=p['id']:self.run(lambda:(self.store.favorite(pid),render()),False))
                star.configure(font=(FONT,8),pady=0,padx=4)
                star.pack(anchor='e',pady=0)
                line=tk.Frame(content,bg=PANEL)
                line.pack(fill='x',pady=(9,10))
                label(line,money(p['price']),15,ACCENT,True).pack(side='left')
                label(line,f"{p['stock']} left",8,AMBER if p['stock']<=p['threshold'] else MUTED).pack(side='right')
                add=button(content,'+  Add to tray' if p['stock'] else 'Sold out',lambda r=p:self.run(lambda:self.add_cart(r),False))
                add.pack(fill='x')
                if not p['stock']:
                    add.config(state='disabled')
            if not visible:
                label(listing.inner,'Nothing matches this search.',11,MUTED).pack(pady=30)
        for name in ['All','Favorites']+list(dict.fromkeys(p['category'] for p in products)):
            pill=button(filterbar,name,lambda n=name:category.set(n))
            pill.configure(padx=12,pady=7,font=(FONT,9,'bold'))
            pill.pack(side='left',padx=(0,6))
            pills[name]=pill
        def search_changed(*args):
            self.menu_search=search.get()
            if getattr(self,'_menu_search_timer',None):
                self.after_cancel(self._menu_search_timer)
            def apply():
                self._menu_search_timer=None
                if listing.winfo_exists():
                    render()
            self._menu_search_timer=self.after(150,apply)
        search.trace_add('write',search_changed)
        category.trace_add('write',render)
        render()
        self.render_cart()
        self.actions(self.book.frames[1],[('Order again',self.reorder)])

    def product_image(self,p):
        name=p['name'].lower()
        for text,file in [('coffee','iced_coffee'),('energy','energy_drink'),('water','mineral_water'),('tea','iced_lemon_tea'),('noodle','cup_noodles'),('chip','potato_chips'),('burger','chicken_burger'),('fries','fries')]:
            if re.search(r'\b'+text,name):
                return f'menu/{file}.png'
        # Digital game credits have their own graphic, without a food photograph.
        return 'game_credit.png'

    def submit_menu_order(self):
        if not self.cart:
            return
        def place():
            self.place_order()
            self.tab_index['shop']=0
        self.confirm('Place this order? Payment will be collected at the café counter.',place)

    def reorder(self):
        row=self.orders_table.selected()
        products={p['id']:p for p in self.store.rows('SELECT * FROM products WHERE active=1')}
        cart={}
        skipped=[]
        for item in self.store.rows('SELECT * FROM order_items WHERE order_id=?',(row['id'],)):
            p=products.get(item['product_id'])
            qty=min(item['qty'],p['stock']) if p else 0
            if qty:
                cart[p['id']]=qty
            if qty<item['qty']:
                skipped.append(item['name'])
        if not cart:
            raise ValueError('The items in this order are currently unavailable.')
        self.cart=cart
        self.render_cart()
        self.book.select(0)
        self.notice('Order loaded into your basket. Review before placing.' + (' Stock limited: '+', '.join(skipped) if skipped else ''))

    def render_cart(self):
        # The superclass builds its first menu before we replace its layout.
        if not hasattr(self,'cart_lines') or not self.cart_lines.winfo_exists():
            return super().render_cart()
        for child in self.cart_lines.inner.winfo_children():
            child.destroy()
        total=0
        for product in self.store.rows('SELECT * FROM products WHERE active=1'):
            quantity=self.cart.get(product['id'],0)
            if not quantity:
                continue
            total+=product['price']*quantity
            row=tk.Frame(self.cart_lines.inner,bg=SURFACE,padx=11,pady=8)
            row.pack(fill='x',pady=4)
            label(row,product['name'],10,TEXT,True,wraplength=220,justify='left').pack(anchor='w')
            line=tk.Frame(row,bg=SURFACE)
            line.pack(fill='x',pady=(8,0))
            label(line,money(product['price']*quantity),10,ACCENT,True).pack(side='left')
            button(line,'−',lambda p=product:self.change_cart(p['id'],-1)).pack(side='right')
            label(line,str(quantity),10,TEXT,padx=8).pack(side='right')
            button(line,'+',lambda p=product:self.run(lambda:self.add_cart(p),False)).pack(side='right')
        if not self.cart:
            label(self.cart_lines.inner,'Your tray is empty.\nPick your gaming fuel.',11,MUTED,justify='center').pack(pady=25)
        self.cart_total.config(text=money(total))
        self.order_submit.config(state='normal' if self.cart else 'disabled')
        metric=getattr(self,'metric_labels',{}).get('Your cart')
        if metric and metric.winfo_exists():
            metric.config(text=str(sum(self.cart.values())))

    def change_cart(self,pid,delta):
        value=self.cart.get(pid,0)+delta
        if value<=0:
            self.cart.pop(pid,None)
        else:
            self.cart[pid]=value
        self.render_cart()

    def page_events(self):
        super().page_events()
        page=self.book.frames[0]
        for child in page.winfo_children():
            child.destroy()
        rows=self.store.rows("SELECT e.*,count(r.id) enrolled FROM events e LEFT JOIN registrations r ON r.event_id=e.id AND r.status='Registered' GROUP BY e.id ORDER BY e.start")
        selected=getattr(self,'selected_event',rows[0]['id'] if rows else None)
        if selected not in [r['id'] for r in rows]:
            selected=rows[0]['id'] if rows else None
        self.event_table=Selection(rows,selected)
        panel=tk.Frame(page,bg=PANEL,width=290,padx=20,pady=20,highlightbackground=BORDER,highlightthickness=1)
        panel.pack(side='right',fill='y',padx=(18,0))
        panel.pack_propagate(False)
        self.event_action=tk.Frame(panel,bg=PANEL)
        self.event_action.pack(side='bottom',fill='x',pady=(12,0))
        inspector_scroll=core.Scroll(panel,bg=PANEL)
        inspector_scroll.pack(fill='both',expand=True)
        self.event_inspector=inspector_scroll.inner
        listarea=tk.Frame(page,bg=BG)
        listarea.pack(fill='both',expand=True)
        self.section(listarea,'Upcoming competitions','Choose your event')
        if self.staff:
            button(listarea,'+ Create event',lambda:self.run(lambda:self.event_form(),False),primary=True).pack(anchor='w',pady=(0,12))
        scroll=core.Scroll(listarea,bg=BG)
        scroll.pack(fill='both',expand=True)
        self.event_cards={}
        for row in rows:
            card=tk.Frame(scroll.inner,bg=PANEL,highlightbackground=ACCENT if row['id']==selected else BORDER,highlightthickness=1,cursor='hand2')
            card.pack(fill='x',pady=(0,12))
            art=Artwork(card,'neon_arena.png',height=92)
            art.pack(fill='x')
            art.create_text(18,17,anchor='nw',text=row['game'].upper(),fill=ACCENT,font=(FONT,8,'bold'))
            art.create_text(17,40,anchor='nw',text=row['name'],fill=TEXT,font=(FONT,18,'bold'))
            info=tk.Frame(card,bg=PANEL,padx=18,pady=14)
            info.pack(fill='x')
            label(info,row['start'].replace('T',' ')[:16]+' MYT',10,MUTED).pack(side='left')
            label(info,f"{row['enrolled']} / {row['capacity']} TEAMS   ·   {money(row['fee'])}",9,BLUE,True).pack(side='right')
            self.event_cards[row['id']]=card
            for child in [card,art,info,*info.winfo_children()]:
                child.bind('<Button-1>',lambda e,r=row:self.inspect_event(r))
        if selected:
            self.inspect_event(self.event_table.selected())
        else:
            label(panel,'No events yet.',11,MUTED).pack(anchor='w')

    def inspect_event(self,row):
        self.selected_event=self.event_table.current=row['id']
        for eid,card in self.event_cards.items():
            card.config(highlightbackground=ACCENT if eid==row['id'] else BORDER)
        panel=self.event_inspector
        for child in panel.winfo_children():
            child.destroy()
        for child in self.event_action.winfo_children():
            child.destroy()
        label(panel,'EVENT BRIEF',8,ACCENT,True).pack(anchor='w')
        label(panel,row['name'],22,TEXT,True,wraplength=245,justify='left').pack(anchor='w',pady=(12,8))
        label(panel,row['game'],11,BLUE,True).pack(anchor='w')
        label(panel,row['status'].upper(),9,AMBER,True).pack(anchor='w',pady=(18,12))
        for caption,value in [('START',row['start'].replace('T',' ')[:16]),('ENTRY',money(row['fee'])),('CAPACITY',f"{row['enrolled']} / {row['capacity']} teams")]:
            label(panel,caption,8,MUTED,True).pack(anchor='w',pady=(10,4))
            label(panel,value,12,TEXT,True).pack(anchor='w')
        join=button(self.event_action,'JOIN THE EVENT  →',lambda:self.run(self.event_signup,False),primary=True)
        join.pack(fill='x')
        if row['status']!='Open':
            join.config(state='disabled',text='REGISTRATION CLOSED')
        if self.staff:
            button(panel,'Generate bracket',lambda:self.run(self.generate_bracket,False)).pack(fill='x',pady=4)
            button(panel,'Edit event',lambda:self.run(lambda:self.event_form(self.event_table.selected()),False)).pack(fill='x',pady=4)
            button(panel,'Cancel event',lambda:self.confirm('Cancel this unstarted event?',lambda:self.store.cancel_event(row['id']))).pack(fill='x',pady=4)

    def account_page(self,parent):
        choices=self.member_choices()
        bar=tk.Frame(parent,bg=BG)
        bar.pack(fill='x',pady=(0,16))
        label(bar,'CASHIER TERMINAL' if self.staff else 'YOUR PLAYER ACCOUNT',9,ACCENT,True).pack(side='left')
        previous=getattr(self,'last_cashier',None)
        self.cashier_member=tk.StringVar(value=previous if previous in choices else choices[0] if choices else '')
        cb=ttk.Combobox(bar,textvariable=self.cashier_member,values=choices,state='readonly',width=29)
        cb.pack(side='right')
        cb.bind('<<ComboboxSelected>>',lambda e:self.render_account())
        self.account_body=tk.Frame(parent,bg=BG)
        self.account_body.pack(fill='both',expand=True)
        self.render_account()

    def render_account(self):
        for child in self.account_body.winfo_children():
            child.destroy()
        if not self.cashier_member.get():
            label(self.account_body,'Register a member to start.',12,MUTED).pack(pady=40)
            return
        self.last_cashier=self.cashier_member.get()
        mid=self.choice_id(self.last_cashier)
        member=self.store.member(mid)
        quote=self.store.quote(mid)
        receipt_outer=tk.Frame(self.account_body,bg=PANEL,width=325,padx=18,pady=20,highlightbackground=BORDER,highlightthickness=1)
        receipt_outer.pack(side='right',fill='y',padx=(20,0))
        receipt_outer.pack_propagate(False)
        payment=tk.Frame(receipt_outer,bg=PANEL)
        payment.pack(side='bottom',fill='x',pady=(12,0))
        receipt_scroll=core.Scroll(receipt_outer,bg=PANEL)
        receipt_scroll.pack(fill='both',expand=True)
        receipt=receipt_scroll.inner
        label(receipt,'CHECKOUT SUMMARY',8,ACCENT,True).pack(anchor='w')
        label(receipt,'Ready to settle?',18,TEXT,True,wraplength=270,justify='left').pack(anchor='w',pady=(10,12))
        for caption,key in [('PC sessions','Session'),('Café orders','Order'),('Event entries','Event'),('Tier discount','discount')]:
            line=tk.Frame(receipt,bg=PANEL)
            line.pack(fill='x',pady=9)
            label(line,caption,10,MUTED).pack(side='left')
            label(line,('− ' if key=='discount' else '')+money(quote[key]),11,TEXT,True).pack(side='right')
        tk.Frame(receipt,bg=BORDER,height=1).pack(fill='x',pady=16)
        label(payment,'TOTAL DUE',9,MUTED,True).pack(anchor='w')
        label(payment,money(quote['total']),26,ACCENT,True).pack(anchor='w',pady=(4,8))
        label(receipt,f"{quote['count']} items · {quote['total']//100} points on payment",9,MUTED).pack(anchor='w',pady=(0,18))
        if self.staff:
            self.payment_method=tk.StringVar(value='Cash')
            ttk.Combobox(payment,textvariable=self.payment_method,values=['Cash','Card','TNG eWallet'],state='readonly').pack(fill='x')
            paid=button(payment,'CONFIRM PAYMENT  →',lambda:self.run(lambda:self.pay_member(mid)),primary=True)
            self.payment_submit=paid
            paid.config(pady=7)
            paid.pack(fill='x',pady=10)
            label(payment,'Confirm after receiving payment at the counter.',8,MUTED,wraplength=275,justify='left').pack(anchor='w')
        else:
            label(payment,'Pay at the café counter.',11,BLUE,True).pack(anchor='w',pady=12)
        left_scroll=core.Scroll(self.account_body,bg=BG)
        left_scroll.pack(fill='both',expand=True)
        left=left_scroll.inner
        card=Artwork(left,'neon_arena.png',height=175)
        card.pack(fill='x',pady=(0,18))
        def draw(w,h):
            card.delete('copy')
            card.create_text(22,23,anchor='nw',text='CTFLY   /   PLAYER MEMBERSHIP',fill=ACCENT,font=(FONT,8,'bold'),tags='copy')
            card.create_text(20,58,anchor='nw',text=member['name'],fill=TEXT,font=(FONT,25,'bold'),tags='copy')
            card.create_text(22,114,anchor='nw',text=f"CTFLY-{mid:04}     /     {member['tier'].upper()}     /     {member['points']} POINTS",fill='#cbdcff',font=(FONT,10,'bold'),tags='copy')
        card.draw_foreground=draw
        self.section(left,'Your account at a glance')
        line=tk.Frame(left,bg=BG)
        line.pack(fill='x',pady=(0,20))
        for caption,value,color in [('LOYALTY POINTS',member['points'],ACCENT),('LIFETIME SPEND',money(member['spent']),BLUE)]:
            tile=tk.Frame(line,bg=PANEL,padx=20,pady=16)
            tile.pack(side='left',fill='both',expand=True,padx=(0,9))
            label(tile,caption,8,MUTED,True).pack(anchor='w')
            label(tile,str(value),23,color,True).pack(anchor='w',pady=(8,0))
        tier=tk.Frame(left,bg=PANEL,padx=20,pady=17)
        tier.pack(fill='x')
        label(tier,'LEVEL UP YOUR MEMBERSHIP',9,ACCENT,True).pack(anchor='w')
        label(tier,'Bronze  →  Silver  →  Gold',15,TEXT,True).pack(anchor='w',pady=12)
        label(tier,'Silver at RM 300: 5% off. Gold at RM 1,000: 10% off.\nEarn 1 point per whole RM paid. New tiers apply to your next bill.',10,MUTED,wraplength=460,justify='left').pack(anchor='w')

    def page_billing(self):
        super().page_billing()
        offset=2 if self.staff else 1
        page=self.book.frames[offset+1]
        # Keep the member selector and management toolbar, replace reward rows.
        old=self.reward_table
        old.destroy()
        rows=self.store.rows('SELECT * FROM rewards WHERE active=1')
        self.reward_table=Selection(rows,rows[0]['id'] if rows else None)
        footer=page.winfo_children()[-1]
        scroll=core.Scroll(page,bg=BG)
        scroll.pack(fill='both',expand=True,before=footer)
        self.reward_cards={}
        for i,row in enumerate(rows):
            scroll.inner.columnconfigure(i%3,weight=1,uniform='reward')
            card=tk.Frame(scroll.inner,bg=PANEL,padx=20,pady=24,highlightbackground=ACCENT if i==0 else BORDER,highlightthickness=1,cursor='hand2')
            card.grid(row=i//3,column=i%3,sticky='nsew',padx=6,pady=7)
            label(card,'CTFLY REWARDS',8,BLUE,True).pack(anchor='w')
            label(card,'✦',38,ACCENT).pack(anchor='w',pady=(10,8))
            label(card,row['name'],15,TEXT,True,wraplength=215,justify='left').pack(anchor='w')
            label(card,f"{row['points']} POINTS",20,ACCENT,True).pack(anchor='w',pady=(18,6))
            label(card,f"{row['stock']} available · Collect at counter",9,MUTED,wraplength=220).pack(anchor='w')
            self.reward_cards[row['id']]=card
            def select(event,rid=row['id']):
                self.reward_table.current=rid
                for key,widget in self.reward_cards.items():
                    widget.config(highlightbackground=ACCENT if key==rid else BORDER)
            for child in [card,*card.winfo_children()]:
                child.bind('<Button-1>',select)

    def page_staff(self):
        if not self.staff:
            pages=self.tabs(['Staff & Attendance'])
            panel=tk.Frame(pages[0],bg=PANEL,padx=35,pady=30)
            panel.pack(fill='x',pady=15)
            label(panel,'Staff & Attendance',25,TEXT,True).pack(anchor='w')
            label(panel,'Your café team workspace',12,ACCENT).pack(anchor='w',pady=10)
            label(panel,'Weekly schedules · clock-in / clock-out · leave requests\nStaff directory · shift templates · task board · attendance reports',12,MUTED,justify='left').pack(anchor='w',pady=20)
            label(panel,'Sign in with a staff or manager account to access employee records.',11,TEXT).pack(anchor='w',pady=10)
            button(panel,'Switch to staff sign-in',self.logout,primary=True).pack(anchor='w',pady=15)
            return
        super().page_staff()
        page=self.book.frames[0]
        table=self.roster_table
        rows=table.data
        columns=table.columns
        table.destroy()
        # Calendar and attendance receive separate workspaces, preserving room
        # for readable rows on laptop screens.
        ledger=self.book.insert(1,'Attendance ledger')
        self.book.buttons[0].configure(text='Weekly roster')
        for child in page.winfo_children():
            child.destroy()
        if self.admin:
            self.actions(page,[('Assign shift',lambda:self.roster_form()),('Generate week',self.week_form)])
        clock_actions=[('Clock in',lambda:self.run(lambda:self.store.clock(self.roster_table.selected()['id']))),
                       ('Clock out',lambda:self.run(lambda:self.store.clock(self.roster_table.selected()['id'],out=True))),
                       ('Request leave',self.leave_form)]
        if self.admin:
            clock_actions += [('Reschedule',lambda:self.roster_form(self.roster_table.selected())),
                              ('Cancel shift',lambda:self.confirm('Cancel this shift?',lambda:self.store.roster_action(self.roster_table.selected()['id'],'Cancelled'))),
                              ('Approve leave',lambda:self.run(lambda:self.store.roster_action(self.roster_table.selected()['id'],'Leave approved'))),
                              ('Decline leave',lambda:self.run(lambda:self.store.roster_action(self.roster_table.selected()['id'],'Scheduled')))]
        self.actions(ledger,clock_actions)
        self.roster_table=self.table(ledger,columns,rows)
        week_scroll=core.Scroll(page,bg=BG)
        week_scroll.pack(fill='both',expand=True,pady=(0,15))
        week=week_scroll.inner
        monday=getattr(self,'roster_week',now().date()-timedelta(days=now().weekday()))
        pager=tk.Frame(week,bg=BG)
        pager.pack(fill='x')
        def move_week(offset):
            self.roster_week=monday+timedelta(days=7*offset)
            self.refresh()
        button(pager,'‹ Previous week',lambda:move_week(-1)).pack(side='left')
        def this_week():
            self.roster_week=now().date()-timedelta(days=now().weekday())
            self.refresh()
        button(pager,'This week',this_week).pack(side='left',padx=6)
        button(pager,'Next week ›',lambda:move_week(1)).pack(side='left')
        self.section(week,'This week',f"{monday:%d %b} — {(monday+timedelta(days=6)):%d %b}")
        calendar=tk.Frame(week,bg=BG)
        calendar.pack(fill='x')
        for i in range(7):
            calendar.columnconfigure(i,weight=1,uniform='day')
            day=monday+timedelta(days=i)
            today=day==now().date()
            cell=tk.Frame(calendar,bg='#172c45' if today else PANEL,padx=10,pady=10,highlightbackground=ACCENT if today else BORDER,highlightthickness=1)
            cell.grid(row=0,column=i,sticky='nsew',padx=3)
            label(cell,day.strftime('%a').upper(),8,ACCENT if today else MUTED,True).pack(anchor='w')
            label(cell,f'{day.day:02}',21,TEXT,True).pack(anchor='w',pady=(5,7))
            roster=[r for r in rows if r['start'].startswith(str(day)) and r['status'] not in ('Cancelled','Leave approved')]
            label(cell,f'{len(roster)} shifts',9,BLUE).pack(anchor='w')
            for row in roster[:5]:
                entry=button(cell,row['staff'].split()[0]+'\n'+row['start'][11:16],lambda r=row:self.select_roster(r))
                entry.configure(font=(FONT,8),padx=5,pady=8,anchor='w')
                entry.pack(fill='x',pady=(9,0))
            if not roster:
                label(cell,'No shifts',8,MUTED).pack(anchor='w',pady=(4,0))
        label(week,'Select a shift card to open attendance, clock-in and leave actions.',10,MUTED).pack(anchor='w',pady=20)
        tasks=self.book.insert(len(self.book.frames)-1,'Crew task board')
        self.build_tasks(tasks)

    def build_tasks(self,parent):
        today=now().date().isoformat()
        self.task_date=tk.StringVar(value=getattr(self,'task_day',today))
        filters=tk.Frame(parent,bg=BG)
        filters.pack(fill='x',pady=(0,12))
        label(filters,'Task date',10,MUTED).pack(side='left',padx=(0,12))
        DateControl(filters,self.task_date).pack(side='left')
        def reload(*args):
            self.task_day=self.task_date.get()
            render()
        self.task_date.trace_add('write',reload)
        if self.admin:
            button(filters,'+ Assign task',lambda:self.run(self.task_form,False),primary=True).pack(side='right')
        self.task_progress=label(parent,'',12,ACCENT,True)
        self.task_progress.pack(anchor='w',pady=(0,12))
        listing=core.Scroll(parent,bg=BG)
        listing.pack(fill='both',expand=True)
        def render():
            for child in listing.inner.winfo_children():
                child.destroy()
            scope='' if self.admin else ' AND s.user_id=?'
            params=(self.task_date.get(),) if self.admin else (self.task_date.get(),self.store.user['id'])
            rows=self.store.rows('SELECT t.*,s.name staff FROM crew_tasks t JOIN staff s ON s.id=t.staff_id WHERE t.day=?'+scope+' ORDER BY t.status DESC,t.id',params)
            done=sum(r['status']=='Done' for r in rows)
            self.task_progress.config(text=f'{done} / {len(rows)} missions complete'+(' · Floor ready!' if rows and done==len(rows) else ''))
            for row in rows:
                card=tk.Frame(listing.inner,bg=PANEL,padx=18,pady=15,highlightbackground=BORDER,highlightthickness=1)
                card.pack(fill='x',pady=5)
                label(card,row['title'],13,TEXT,True,wraplength=500,justify='left').pack(anchor='w')
                label(card,row['staff']+' · '+row['status'],9,ACCENT if row['status']=='Done' else MUTED).pack(anchor='w',pady=6)
                action='To do' if row['status']=='Done' else 'Done'
                button(card,'Reopen' if action=='To do' else '✓ Complete task',lambda r=row,a=action:self.run(lambda:(self.store.task_action(r['id'],a),render()),False)).pack(side='left')
                if self.admin:
                    button(card,'Delete',lambda r=row:self.confirm('Delete this task?',lambda:self.store.task_action(r['id'],'Delete'))).pack(side='right')
            if not rows:
                label(listing.inner,'No tasks for this date.\nManagers can assign opening, cleaning and restock missions.',12,MUTED,justify='center').pack(pady=35)
        render()

    def task_form(self):
        employees=[f"{r['id']} · {r['name']}" for r in self.store.rows('SELECT * FROM staff WHERE active=1')]
        if not employees:
            raise ValueError('Add an employee first.')
        self.form('Assign crew mission',[('staff','Employee',employees[0],employees),('title','Mission','Check headsets and clean keyboards',None),('day','Task date',self.task_date.get(),None)],lambda v:self.store.save_task(self.choice_id(v['staff']),v['title'],v['day']),'Examples: opening checks, stock drinks, sanitize stations, tournament setup.')

    def select_roster(self,row):
        self.roster_table.tree.selection_set(str(row['id']))
        self.roster_table.tree.see(str(row['id']))
        self.book.select(1)


def launch():
    NeonApplication().mainloop()
