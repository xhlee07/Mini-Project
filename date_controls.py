"""Read-only calendar/time dropdowns sharing an ISO StringVar with forms."""
import calendar
from datetime import date, datetime
import tkinter as tk
from tkinter import ttk


class DateControl(tk.Frame):
    def __init__(self, parent, variable, time=False, optional=False, birth=False, clock=False):
        super().__init__(parent, bg=parent.cget('bg'))
        self.variable, self.has_time, self.clock, self.birth = variable, time, clock, birth
        self.busy = False
        self.parts = {}
        self.boxes = {}
        self.optional = optional
        self.mode = tk.StringVar(value='Now' if optional and not variable.get() else 'Choose date')
        if optional:
            mode = ttk.Combobox(self, textvariable=self.mode, values=['Now', 'Choose date'], state='readonly', width=12)
            mode.pack(anchor='w', pady=(0,5))
            mode.bind('<<ComboboxSelected>>', self.changed)
        row = tk.Frame(self, bg=self.cget('bg'))
        row.pack(fill='x')
        year = date.today().year
        fields = [] if clock else [('year', list(range(year-120 if birth else year-5, year+1 if birth else year+11)), 6), ('month', list(range(1,13)), 4), ('day', list(range(1,32)), 4)]
        if time or clock:
            fields += [('hour', list(range(24)), 4), ('minute', list(range(60)), 4)]
        for key, values, width in fields:
            if key=='hour' and not clock:
                row = tk.Frame(self, bg=self.cget('bg'))
                row.pack(fill='x', pady=(5,0))
            part = tk.StringVar()
            self.parts[key] = part
            box = ttk.Combobox(row, textvariable=part, values=[f'{v:02}' for v in values], state='readonly', width=width)
            box.pack(side='left', padx=(0,4))
            box.bind('<<ComboboxSelected>>', self.changed)
            self.boxes[key] = box
        tk.Label(self, text='Hour / Minute' if clock else 'Year / Month / Day' + (' · Hour / Minute' if time else ''), bg=self.cget('bg'), fg='#98abc9', font=('Segoe UI',8)).pack(anchor='w')
        self.trace = variable.trace_add('write', self.sync)
        self.sync()

    def sync(self, *args):
        if self.busy:
            return
        value = self.variable.get()
        try:
            parsed = datetime.strptime(value, '%H:%M') if self.clock else datetime.fromisoformat(value)
        except ValueError:
            parsed = datetime.now()
        for key, part in self.parts.items():
            part.set(key.title() if self.birth and not value else f'{getattr(parsed,key):02}')
        if self.optional:
            self.mode.set('Choose date' if value else 'Now')
        self.update_days()
        for box in self.boxes.values():
            box.configure(state='disabled' if self.optional and self.mode.get()=='Now' else 'readonly')

    def update_days(self):
        if self.clock:
            return
        if not all(self.parts[k].get().isdigit() for k in ('year','month','day')):
            return
        last = calendar.monthrange(int(self.parts['year'].get()), int(self.parts['month'].get()))[1]
        self.boxes['day'].configure(values=[f'{d:02}' for d in range(1,last+1)])
        if int(self.parts['day'].get()) > last:
            self.parts['day'].set(f'{last:02}')

    def changed(self, event=None):
        self.update_days()
        if not all(v.get().isdigit() for v in self.parts.values()):
            return
        self.busy = True
        if self.optional and self.mode.get()=='Now':
            value = ''
        else:
            p = {k:int(v.get()) for k,v in self.parts.items()}
            value = f"{p['hour']:02}:{p['minute']:02}" if self.clock else f"{p['year']:04}-{p['month']:02}-{p['day']:02}"
            if self.has_time:
                value += f" {p['hour']:02}:{p['minute']:02}:00"
        self.variable.set(value)
        self.busy = False
        self.sync()

    def destroy(self):
        self.variable.trace_remove('write', self.trace)
        super().destroy()


def field_control(parent, variable, key, caption):
    text = caption.lower()
    if 'hh:mm' in text and 'yyyy' not in text:
        return DateControl(parent, variable, clock=True)
    if key in ('dob','hire','day') or 'date' in text or 'yyyy' in text or 'birth' in text or 'blank' in text:
        return DateControl(parent, variable, birth=key=='dob', time='hh:mm' in text or len(variable.get())>10 or 'blank' in text, optional='blank' in text or 'now' in text)
    return None
