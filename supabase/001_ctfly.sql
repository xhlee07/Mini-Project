-- CTFLY Android v1. Run once in a NEW Supabase project's SQL Editor.
-- No legacy Python database is modified. Money is integer Malaysian sen.
begin;
create extension if not exists btree_gist;
create table public.profiles (
 id uuid primary key references auth.users on delete cascade,
 name text not null check(length(name) between 1 and 80), phone text not null default '',
 dob date not null, role text not null default 'customer' check(role in ('customer','staff','admin')),
 points int not null default 0 check(points>=0), spent int not null default 0 check(spent>=0),
 active boolean not null default true, created_at timestamptz not null default now());
create table public.resources (
 id bigint generated always as identity primary key, name text not null unique,
 kind text not null check(kind in ('PC','Snooker','Pool','Console','Racing')), zone text not null default 'Standard',
 description text not null, capacity int not null default 1 check(capacity between 1 and 20),
 rate int not null check(rate>0), status text not null default 'Available' check(status in ('Available','Maintenance','Retired')));
create table public.bookings (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 resource_id bigint not null references public.resources, starts_at timestamptz not null, ends_at timestamptz not null,
 guests int not null check(guests>0), total int not null check(total>=0),
 status text not null default 'Reserved' check(status in ('Reserved','Confirmed','Active','Completed','Cancelled','Expired')),
 paid boolean not null default false, token uuid not null unique default gen_random_uuid(), checked_in timestamptz,
 created_at timestamptz not null default now(), check(ends_at>starts_at),
 exclude using gist(resource_id with =, tstzrange(starts_at,ends_at,'[)') with &&)
 where(status in ('Reserved','Confirmed','Active')));
create table public.categories(id bigint generated always as identity primary key, name text not null unique);
create table public.products (
 id bigint generated always as identity primary key, category_id bigint not null references public.categories,
 name text not null, price int not null check(price>=0), stock int not null check(stock>=0),
 threshold int not null default 5 check(threshold>=0), image text not null default 'iced_coffee', active boolean not null default true);
create table public.stock_records(id bigint generated always as identity primary key, product_id bigint not null references public.products,
 quantity int not null, reason text not null, created_at timestamptz not null default now());
create table public.orders(id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 booking_id uuid references public.bookings, total int not null check(total>=0), paid boolean not null default false,
 status text not null default 'Pending' check(status in ('Pending','Preparing','Ready','Delivered','Cancelled')), created_at timestamptz not null default now());
create table public.order_items(id bigint generated always as identity primary key, order_id uuid not null references public.orders,
 product_id bigint not null references public.products, name text not null, qty int not null check(qty>0), price int not null check(price>=0));
create table public.events(id bigint generated always as identity primary key, name text not null, game text not null,
 starts_at timestamptz not null, ends_at timestamptz not null, fee int not null check(fee>=0), capacity int not null check(capacity between 2 and 64),
 status text not null default 'Open' check(status in ('Open','Running','Completed','Cancelled')), check(ends_at>starts_at),
 exclude using gist(tstzrange(starts_at,ends_at,'[)') with &&) where(status in ('Open','Running')));
create table public.registrations(id uuid primary key default gen_random_uuid(), event_id bigint not null references public.events,
 user_id uuid not null references public.profiles, team text not null check(length(team) between 1 and 80), fee int not null,
 paid boolean not null default false, status text not null default 'Registered' check(status in ('Registered','Withdrawn')),
 unique(event_id,user_id));
create table public.matches(id bigint generated always as identity primary key, event_id bigint not null references public.events,
 round int not null, slot int not null, a uuid references public.registrations, b uuid references public.registrations,
 winner uuid references public.registrations, done boolean not null default false, unique(event_id,round,slot));
create table public.bills(id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 kind text not null check(kind in ('Booking','Order','Event','Extension')), source_id uuid not null, subtotal int not null, discount int not null,
 total int not null check(total>=0), points int not null, method text not null default 'Cash at counter', created_at timestamptz not null default now(),
 status text not null default 'Paid' check(status in ('Paid','Void')), void_reason text not null default '');
create unique index one_paid_bill on public.bills(kind,source_id) where status='Paid';
create table public.booking_extensions(id uuid primary key default gen_random_uuid(), booking_id uuid not null references public.bookings,
 minutes int not null check(minutes in (30,60)), amount int not null check(amount>=0), created_at timestamptz not null default now());
create table public.rewards(id bigint generated always as identity primary key, name text not null, points int not null check(points>0), stock int not null check(stock>=0), active boolean not null default true);
create table public.redemptions(id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 reward_id bigint not null references public.rewards, name text not null, points int not null, status text not null default 'Pending', created_at timestamptz not null default now());
create table public.points_transactions(id bigint generated always as identity primary key, user_id uuid not null references public.profiles,
 points int not null, reason text not null, created_at timestamptz not null default now());
create table public.favorites(user_id uuid not null references public.profiles, product_id bigint not null references public.products, primary key(user_id,product_id));
create table public.shifts(id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 starts_at timestamptz not null, ends_at timestamptz not null, status text not null default 'Scheduled' check(status in ('Scheduled','Cancelled')),
 check(ends_at>starts_at), exclude using gist(user_id with =,tstzrange(starts_at,ends_at,'[)') with &&) where(status='Scheduled'));
create table public.attendance(id uuid primary key default gen_random_uuid(), shift_id uuid not null unique references public.shifts,
 clock_in timestamptz not null default now(), clock_out timestamptz, late_minutes int not null default 0, early_minutes int not null default 0, minutes int not null default 0);
create table public.leave_requests(id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 day date not null, reason text not null, status text not null default 'Pending' check(status in ('Pending','Approved','Declined')), created_at timestamptz not null default now());
create table public.tasks(id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 title text not null, day date not null, done boolean not null default false);
create table public.checklist(user_id uuid not null references public.profiles, day date not null, item text not null, primary key(user_id,day,item));
create table public.handovers(id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 title text not null, note text not null, category text not null, created_at timestamptz not null default now());
create table public.handover_reads(handover_id uuid not null references public.handovers, user_id uuid not null references public.profiles, primary key(handover_id,user_id));
create table public.tickets(id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 asset text not null, title text not null, priority text not null check(priority in ('Low','Normal','Urgent')), status text not null default 'Open',
 assigned_user uuid references public.profiles, resolution text not null default '', created_at timestamptz not null default now());
-- Idempotent operations: a network retry cannot repeat an order, payment or booking.
create table public.requests(user_id uuid not null references public.profiles, request_id uuid not null, result jsonb not null, primary key(user_id,request_id));
create index booking_member on public.bookings(user_id,starts_at);
create index order_member on public.orders(user_id,created_at);
create index shifts_member on public.shifts(user_id,starts_at);

create function public.create_profile() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare birthday date;
begin
 birthday := (new.raw_user_meta_data->>'dob')::date;
 if birthday is null or birthday>(current_timestamp at time zone 'Asia/Kuala_Lumpur')::date-interval '18 years'
 or birthday<(current_timestamp at time zone 'Asia/Kuala_Lumpur')::date-interval '120 years' then
 raise exception 'You must be 18 or older. Enter a valid birth date.'; end if;
 insert into public.profiles(id,name,dob,phone) values(new.id,coalesce(nullif(trim(new.raw_user_meta_data->>'name'),''),'Player'),birthday,coalesce(new.raw_user_meta_data->>'phone',''));
 return new;
end $$;
create trigger on_ctfly_signup after insert on auth.users for each row execute function public.create_profile();

create function public.expire_bookings() returns void language sql security definer set search_path=public,pg_temp as $$
 update bookings set status=case when status='Active' then 'Completed' else 'Expired' end
 where (status in ('Active','Confirmed') and ends_at<=now()) or (status='Reserved' and starts_at+interval '15 minutes'<=now());
$$;

create function public.ctfly_catalog() returns jsonb language sql security definer set search_path=public,pg_temp as $$
 select jsonb_build_object(
 'resources',coalesce((select jsonb_agg(r order by id) from resources r where status<>'Retired'),'[]'::jsonb),
 'products',coalesce((select jsonb_agg(p order by id) from products p where active),'[]'::jsonb),
 'categories',coalesce((select jsonb_agg(c order by id) from categories c),'[]'::jsonb),
 'events',coalesce((select jsonb_agg(e order by starts_at) from events e where status<>'Cancelled' and ends_at>now()),'[]'::jsonb),
 'rewards',coalesce((select jsonb_agg(r order by id) from rewards r where active),'[]'::jsonb));
$$;

create function public.ctfly_snapshot() returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare me profiles; result jsonb; crew boolean;
begin
 select * into me from profiles where id=auth.uid() and active;
 if me.id is null then raise exception 'Please sign in with an active CTFLY account.'; end if;
 crew := me.role in ('staff','admin');
 perform expire_bookings();
 result := ctfly_catalog() || jsonb_build_object('profile',to_jsonb(me), 'server_time',now());
 result := result || jsonb_build_object(
 'bookings',coalesce((select jsonb_agg(b order by starts_at desc) from (select * from bookings where user_id=me.id or crew order by starts_at desc limit 300) b),'[]'::jsonb),
 'orders',coalesce((select jsonb_agg(o order by created_at desc) from (select * from orders where user_id=me.id or crew order by created_at desc limit 300) o),'[]'::jsonb),
 'order_items',coalesce((select jsonb_agg(i) from order_items i join orders o on o.id=i.order_id where o.user_id=me.id or crew),'[]'::jsonb),
 'registrations',coalesce((select jsonb_agg(r) from registrations r where r.user_id=me.id or crew),'[]'::jsonb),
 'matches',coalesce((select jsonb_agg(jsonb_build_object('id',m.id,'event_id',m.event_id,'round',m.round,'slot',m.slot,'a',m.a,'b',m.b,'winner',m.winner,'done',m.done,'team_a',a.team,'team_b',b.team,'team_winner',w.team) order by m.round,m.slot)
 from matches m left join registrations a on a.id=m.a left join registrations b on b.id=m.b left join registrations w on w.id=m.winner),'[]'::jsonb),
 'bills',coalesce((select jsonb_agg(b order by created_at desc) from bills b where b.user_id=me.id or me.role='admin'),'[]'::jsonb),
 'redemptions',coalesce((select jsonb_agg(r) from redemptions r where r.user_id=me.id or crew),'[]'::jsonb),
 'points_transactions',coalesce((select jsonb_agg(p order by created_at desc) from points_transactions p where p.user_id=me.id),'[]'::jsonb),
 'favorites',coalesce((select jsonb_agg(f) from favorites f where user_id=me.id),'[]'::jsonb));
 if crew then
 result := result || jsonb_build_object(
 'people',coalesce((select jsonb_agg(jsonb_build_object('id',id,'name',name,'role',role,'active',active,'phone',case when me.role='admin' then phone else '' end)) from profiles where me.role='admin' or role in ('staff','admin')),'[]'::jsonb),
 'shifts',coalesce((select jsonb_agg(s order by starts_at) from shifts s where user_id=me.id or me.role='admin'),'[]'::jsonb),
 'attendance',coalesce((select jsonb_agg(a) from attendance a join shifts s on s.id=a.shift_id where s.user_id=me.id or me.role='admin'),'[]'::jsonb),
 'leave_requests',coalesce((select jsonb_agg(l) from leave_requests l where user_id=me.id or me.role='admin'),'[]'::jsonb),
 'tasks',coalesce((select jsonb_agg(t order by day) from tasks t where user_id=me.id or me.role='admin'),'[]'::jsonb),
 'checklist',coalesce((select jsonb_agg(c) from checklist c where user_id=me.id and day=(now() at time zone 'Asia/Kuala_Lumpur')::date),'[]'::jsonb),
 'handovers',coalesce((select jsonb_agg(h order by created_at desc) from handovers h),'[]'::jsonb),
 'handover_reads',coalesce((select jsonb_agg(h) from handover_reads h where user_id=me.id),'[]'::jsonb),
 'tickets',coalesce((select jsonb_agg(t order by created_at desc) from tickets t),'[]'::jsonb));
 end if;
 if me.role='admin' then result := result || jsonb_build_object(
 'resources',(select coalesce(jsonb_agg(r order by id),'[]'::jsonb) from resources r),
 'products',(select coalesce(jsonb_agg(p order by id),'[]'::jsonb) from products p),
 'events',(select coalesce(jsonb_agg(e order by starts_at),'[]'::jsonb) from events e),
 'rewards',(select coalesce(jsonb_agg(r order by id),'[]'::jsonb) from rewards r),
 'stock_records',(select coalesce(jsonb_agg(s order by created_at desc),'[]'::jsonb) from stock_records s)); end if;
 return result;
end $$;

create function public.ctfly_quote(p_kind text,p_id uuid) returns jsonb
language plpgsql security definer set search_path=public,pg_temp as $$
declare uid uuid; amount int; spent_total int; discount_total int;
begin
 if not exists(select 1 from profiles where id=auth.uid() and active and role in ('staff','admin')) then raise exception 'Staff access required.'; end if;
 if p_kind='Booking' then select user_id,total into uid,amount from bookings where id=p_id and status='Reserved' and not paid;
 elsif p_kind='Order' then select user_id,total into uid,amount from orders where id=p_id and status<>'Cancelled' and not paid;
 elsif p_kind='Event' then select user_id,fee into uid,amount from registrations where id=p_id and status='Registered' and not paid;
 elsif p_kind in ('Extension30','Extension60') then
  select b.user_id,(r.rate*(case when p_kind='Extension30' then 30 else 60 end)+30)/60 into uid,amount from bookings b join resources r on r.id=b.resource_id where b.id=p_id and b.paid and b.status in ('Confirmed','Active') and b.ends_at>now();
 end if;
 if uid is null then raise exception 'Already paid or no longer payable.'; end if;
 select spent into spent_total from profiles where id=uid;
 discount_total:=amount*(case when spent_total>=100000 then 10 when spent_total>=30000 then 5 else 0 end)/100;
 return jsonb_build_object('id',p_id,'kind',p_kind,'subtotal',amount,'discount',discount_total,'total',amount-discount_total);
end $$;

create function public.ctfly_action(p_action text, p_data jsonb, p_request uuid) returns jsonb
language plpgsql security definer set search_path=public,pg_temp as $$
declare me profiles; r resources; b bookings; pr products; ev events; rw rewards; sh shifts; tk tickets;
 oid uuid; uid uuid; sid uuid; bid bigint; n int; amount int; disc int; earned int; target profiles;
 st timestamptz; en timestamptz; item jsonb; result jsonb; existing jsonb; m matches; ids uuid[]; size int; rd int; j int; winner_id uuid; invoice bills; day_value date;
begin
 -- Serializes mutations per account and keeps retry responses consistent.
 select * into me from profiles where id=auth.uid() and active for update;
 if me.id is null then raise exception 'Please sign in with an active CTFLY account.'; end if;
 if p_request is null then raise exception 'Missing request identifier.'; end if;
 select q.result into existing from requests q where q.user_id=me.id and q.request_id=p_request;
 if existing is not null then return existing; end if;
 perform expire_bookings();
 result := jsonb_build_object('message','Saved');
 if p_action='book' then
  if me.role<>'customer' then raise exception 'Use a customer account to book.'; end if;
  if me.dob>(now() at time zone 'Asia/Kuala_Lumpur')::date-interval '18 years' then raise exception 'Bookings are 18+.'; end if;
  select * into r from resources where id=(p_data->>'resource_id')::bigint for update;
  n:=(p_data->>'minutes')::int; st:=(p_data->>'starts_at')::timestamptz; en:=st+make_interval(mins=>n);
  if r.id is null or r.status<>'Available' or n not in (60,90,120,180) or st<now()-interval '1 minute' or st>now()+interval '90 days'
   or (p_data->>'guests')::int not between 1 and r.capacity then raise exception 'Choose an available space, valid time and group size.'; end if;
  if (select count(*) from bookings where user_id=me.id and status in ('Reserved','Confirmed','Active'))>=3 then raise exception 'You can hold up to 3 upcoming bookings.'; end if;
  insert into bookings(user_id,resource_id,starts_at,ends_at,guests,total) values(me.id,r.id,st,en,(p_data->>'guests')::int,(r.rate*n+30)/60) returning id into oid;
  result:=jsonb_build_object('message','Reserved. Pay at the counter before check-in.','id',oid);
 elsif p_action='cancel_booking' then
  update bookings set status='Cancelled' where id=(p_data->>'id')::uuid and user_id=me.id and status='Reserved' and starts_at>now();
  if not found then raise exception 'Only your unpaid, upcoming reservation can be cancelled.'; end if;
 elsif p_action='extend_booking' then
  if me.role<>'admin' then raise exception 'Manager access required.'; end if;
  select * into b from bookings where id=(p_data->>'id')::uuid for update;
  if b.status<>'Reserved' then raise exception 'Extend unpaid reservations only. For paid sessions create another booking.'; end if;
  select * into r from resources where id=b.resource_id for update;
  n:=(p_data->>'minutes')::int;
  if n not in (30,60) then raise exception 'Choose 30 or 60 minutes.'; end if;
  update bookings set ends_at=ends_at+make_interval(mins=>n),total=total+(r.rate*n+30)/60 where id=b.id;
 elsif p_action='order' then
  if me.role<>'customer' then raise exception 'Use a customer account to order.'; end if;
  if jsonb_typeof(p_data->'items')<>'array' or jsonb_array_length(p_data->'items') not between 1 and 30 then raise exception 'Choose 1 to 30 products.'; end if;
  if exists(select 1 from jsonb_array_elements(p_data->'items') x group by x->>'id' having count(*)>1) then raise exception 'Duplicate product.'; end if;
  sid:=nullif(p_data->>'booking_id','')::uuid;
  if sid is not null and not exists(select 1 from bookings where id=sid and user_id=me.id and status='Active' and ends_at>now()) then raise exception 'Delivery needs your active session.'; end if;
  insert into orders(user_id,booking_id,total) values(me.id,sid,0) returning id into oid;
  amount:=0;
  for item in select value from jsonb_array_elements(p_data->'items') order by (value->>'id')::bigint loop
   n:=(item->>'qty')::int;
   select * into pr from products where id=(item->>'id')::bigint for update;
   if pr.id is null or not pr.active or n not between 1 and 20 or pr.stock<n then raise exception 'A product is unavailable or has insufficient stock.'; end if;
   update products set stock=stock-n where id=pr.id;
   insert into order_items(order_id,product_id,name,qty,price) values(oid,pr.id,pr.name,n,pr.price);
   insert into stock_records(product_id,quantity,reason) values(pr.id,-n,'Order '||oid);
   amount:=amount+pr.price*n;
  end loop;
  update orders set total=amount where id=oid;
  result:=jsonb_build_object('message','Order sent. Pay at the counter.','id',oid);
 elsif p_action='cancel_order' then
  perform 1 from orders where id=(p_data->>'id')::uuid and user_id=me.id and status='Pending' and not paid for update;
  if not found then raise exception 'Only your pending unpaid order can be cancelled.'; end if;
  for item in select to_jsonb(i) from order_items i where order_id=(p_data->>'id')::uuid order by product_id loop
   update products set stock=stock+(item->>'qty')::int where id=(item->>'product_id')::bigint;
   insert into stock_records(product_id,quantity,reason) values((item->>'product_id')::bigint,(item->>'qty')::int,'Cancelled order');
  end loop;
  update orders set status='Cancelled' where id=(p_data->>'id')::uuid;
 elsif p_action='favorite' then
  delete from favorites where user_id=me.id and product_id=(p_data->>'id')::bigint;
  if not found then insert into favorites values(me.id,(p_data->>'id')::bigint); end if;
 elsif p_action='register_event' then
  if me.role<>'customer' then raise exception 'Use a customer account to enter.'; end if;
  select * into ev from events where id=(p_data->>'event_id')::bigint for update;
  if ev.id is null or ev.status<>'Open' or ev.starts_at<=now() then raise exception 'Registration is closed.'; end if;
  if (select count(*) from registrations where event_id=ev.id and status='Registered')>=ev.capacity then raise exception 'This event is full.'; end if;
  insert into registrations(event_id,user_id,team,fee) values(ev.id,me.id,trim(p_data->>'team'),ev.fee)
  on conflict(event_id,user_id) do update set status='Registered',team=excluded.team,fee=excluded.fee where registrations.status='Withdrawn';
  if not found then raise exception 'You are already registered.'; end if;
 elsif p_action='withdraw_event' then
  select e.* into ev from events e join registrations g on g.event_id=e.id where g.id=(p_data->>'id')::uuid for update of e;
  if ev.status<>'Open' or ev.starts_at<=now() then raise exception 'Registration is closed.'; end if;
  update registrations set status='Withdrawn' where id=(p_data->>'id')::uuid and user_id=me.id and not paid;
  if not found then raise exception 'Only unpaid entries can be withdrawn.'; end if;
 elsif p_action='redeem' then
  if me.role<>'customer' then raise exception 'Customer membership required.'; end if;
  select * into rw from rewards where id=(p_data->>'id')::bigint for update;
  if rw.id is null or not rw.active or rw.stock<1 or me.points<rw.points then raise exception 'Not enough points or reward stock.'; end if;
  update profiles set points=points-rw.points where id=me.id;
  update rewards set stock=stock-1 where id=rw.id;
  insert into redemptions(user_id,reward_id,name,points) values(me.id,rw.id,rw.name,rw.points);
  insert into points_transactions(user_id,points,reason) values(me.id,-rw.points,'Redeemed '||rw.name);
 elsif p_action='profile' then
  update profiles set name=trim(p_data->>'name'),phone=left(p_data->>'phone',30) where id=me.id;
 elsif p_action in ('pay','extend_paid','check_in','end_session','order_status','collect_reward','clock','leave','task','task_done','checklist','handover','ack','ticket','claim','resolve','reopen') then
  if me.role not in ('staff','admin') then raise exception 'Staff access required.'; end if;
  if p_action='pay' then
   sid:=(p_data->>'id')::uuid;
   if p_data->>'kind'='Booking' then
    select user_id,total into uid,amount from bookings where id=sid and status='Reserved' and not paid for update;
   elsif p_data->>'kind'='Order' then
    select user_id,total into uid,amount from orders where id=sid and status<>'Cancelled' and not paid for update;
   elsif p_data->>'kind'='Event' then
    select user_id,fee into uid,amount from registrations where id=sid and status='Registered' and not paid for update;
   else raise exception 'Invalid payment source.'; end if;
   if uid is null then raise exception 'Already paid or no longer payable.'; end if;
   select * into target from profiles where id=uid for update;
   disc:=amount*(case when target.spent>=100000 then 10 when target.spent>=30000 then 5 else 0 end)/100;
   if nullif(p_data->>'expected_total','')::int is distinct from amount-disc then raise exception 'The payment amount changed. Close and reopen payment to get a fresh quote.'; end if;
   earned:=(amount-disc)/100;
   insert into bills(user_id,kind,source_id,subtotal,discount,total,points) values(uid,p_data->>'kind',sid,amount,disc,amount-disc,earned);
   update profiles set points=points+earned,spent=spent+amount-disc where id=uid;
   insert into points_transactions(user_id,points,reason) values(uid,earned,'Counter payment');
   if p_data->>'kind'='Booking' then update bookings set paid=true,status='Confirmed' where id=sid;
   elsif p_data->>'kind'='Order' then update orders set paid=true where id=sid;
   else update registrations set paid=true where id=sid; end if;
   result:=jsonb_build_object('message','Cash payment recorded: RM '||to_char((amount-disc)/100.0,'FM9999990.00'));
  elsif p_action='extend_paid' then
   select * into b from bookings where id=(p_data->>'id')::uuid for update;
   n:=(p_data->>'minutes')::int;
   if b.id is null or not b.paid or b.status not in ('Confirmed','Active') or b.ends_at<=now() or n not in (30,60) then raise exception 'Choose a live paid booking and 30 or 60 extra minutes.'; end if;
   select * into r from resources where id=b.resource_id for update;
   if r.status<>'Available' then raise exception 'Space is not available.'; end if;
   select * into target from profiles where id=b.user_id for update;
   amount:=(r.rate*n+30)/60;
   disc:=amount*(case when target.spent>=100000 then 10 when target.spent>=30000 then 5 else 0 end)/100;
   if nullif(p_data->>'expected_total','')::int is distinct from amount-disc then raise exception 'The payment amount changed. Reopen the extension quote.'; end if;
   earned:=(amount-disc)/100;
   update bookings set ends_at=ends_at+make_interval(mins=>n),total=total+amount where id=b.id;
   insert into booking_extensions(booking_id,minutes,amount) values(b.id,n,amount) returning id into oid;
   insert into bills(user_id,kind,source_id,subtotal,discount,total,points) values(b.user_id,'Extension',oid,amount,disc,amount-disc,earned);
   update profiles set spent=spent+amount-disc,points=points+earned where id=b.user_id;
   insert into points_transactions(user_id,points,reason) values(b.user_id,earned,'Session extension payment');
   result:=jsonb_build_object('message','Cash received and session extended by '||n||' minutes.');
  elsif p_action='check_in' then
   select * into b from bookings where token=(replace(p_data->>'token','ctfly:',''))::uuid for update;
   if b.id is null or not b.paid or b.status<>'Confirmed' or now()<b.starts_at or now()>=b.ends_at then raise exception 'QR must be paid, unused and within the reserved time.'; end if;
   if not exists(select 1 from resources where id=b.resource_id and status='Available') then raise exception 'Space is unavailable. Contact a manager.'; end if;
   update bookings set status='Active',checked_in=now() where id=b.id;
   result:=jsonb_build_object('message','Checked in. The original booking end time applies.');
  elsif p_action='end_session' then
   update bookings set status='Completed' where id=(p_data->>'id')::uuid and status='Active';
   if not found then raise exception 'Choose an active session.'; end if;
  elsif p_action='order_status' then
   update orders set status=p_data->>'status' where id=(p_data->>'id')::uuid and paid and
   ((status='Pending' and p_data->>'status'='Preparing') or (status='Preparing' and p_data->>'status'='Ready') or (status='Ready' and p_data->>'status'='Delivered'));
   if not found then raise exception 'Pay first, then advance the order one stage.'; end if;
  elsif p_action='collect_reward' then
   update redemptions set status='Collected' where id=(p_data->>'id')::uuid and status='Pending';
   if not found then raise exception 'Voucher already collected.'; end if;
  elsif p_action='clock' then
   select * into sh from shifts where id=(p_data->>'id')::uuid and user_id=me.id and status='Scheduled' for update;
   if sh.id is null then raise exception 'Choose your scheduled shift.'; end if;
   if not exists(select 1 from attendance where shift_id=sh.id) then
    if now()<sh.starts_at-interval '30 minutes' or now()>=sh.ends_at then raise exception 'Clock in from 30 minutes before your shift until its end.'; end if;
    insert into attendance(shift_id,late_minutes) values(sh.id,greatest(0,floor(extract(epoch from now()-sh.starts_at)/60)::int));
   else
    update attendance set clock_out=now(),minutes=greatest(0,floor(extract(epoch from now()-clock_in)/60)::int),early_minutes=greatest(0,floor(extract(epoch from sh.ends_at-now())/60)::int) where shift_id=sh.id and clock_out is null;
    if not found then raise exception 'This shift is already clocked out.'; end if;
   end if;
  elsif p_action='leave' then
   if (p_data->>'day')::date<(now() at time zone 'Asia/Kuala_Lumpur')::date or length(trim(p_data->>'reason'))<3 then raise exception 'Choose a future date and reason.'; end if;
   insert into leave_requests(user_id,day,reason) values(me.id,(p_data->>'day')::date,p_data->>'reason');
  elsif p_action='task' then
   uid:=case when me.role='admin' then coalesce(nullif(p_data->>'user_id','')::uuid,me.id) else me.id end;
   if not exists(select 1 from profiles where id=uid and active and role in ('staff','admin')) or length(trim(p_data->>'title'))<2 then raise exception 'Choose a staff account and task title.'; end if;
   insert into tasks(user_id,title,day) values(uid,trim(p_data->>'title'),(p_data->>'day')::date);
  elsif p_action='task_done' then
   update tasks set done=not done where id=(p_data->>'id')::uuid and (user_id=me.id or me.role='admin');
  elsif p_action='checklist' then
   if p_data->>'item' not in ('Sanitize desks','Test headsets','Check network','Count cues & balls','Stock drinks','Closing safety check') then raise exception 'Unknown checklist item.'; end if;
   delete from checklist c where c.user_id=me.id and c.day=(now() at time zone 'Asia/Kuala_Lumpur')::date and c.item=p_data->>'item';
   if not found then insert into checklist values(me.id,(now() at time zone 'Asia/Kuala_Lumpur')::date,p_data->>'item'); end if;
  elsif p_action='handover' then
   if length(trim(p_data->>'title'))<2 or length(trim(p_data->>'note'))<2 then raise exception 'Enter a title and note.'; end if;
   insert into handovers(user_id,title,note,category) values(me.id,p_data->>'title',p_data->>'note',p_data->>'category');
  elsif p_action='ack' then
   if not exists(select 1 from handovers where id=(p_data->>'id')::uuid and user_id<>me.id) then raise exception 'Acknowledge another colleague’s note.'; end if;
   insert into handover_reads values((p_data->>'id')::uuid,me.id) on conflict do nothing;
  elsif p_action='ticket' then
   if length(trim(p_data->>'asset'))<2 or length(trim(p_data->>'title'))<2 then raise exception 'Enter the equipment and issue.'; end if;
   insert into tickets(user_id,asset,title,priority) values(me.id,p_data->>'asset',p_data->>'title',p_data->>'priority');
  else
   select * into tk from tickets where id=(p_data->>'id')::uuid for update;
   if tk.id is null then raise exception 'Issue not found.'; end if;
   if p_action='claim' and tk.status='Open' then update tickets set assigned_user=me.id,status='In progress' where id=tk.id;
   elsif p_action='resolve' and tk.status='In progress' and (tk.assigned_user=me.id or me.role='admin') and length(trim(p_data->>'resolution'))>=3 then
    update tickets set status='Resolved',resolution=p_data->>'resolution' where id=tk.id;
   elsif p_action='reopen' and tk.status='Resolved' and (tk.user_id=me.id or tk.assigned_user=me.id or me.role='admin') then
    update tickets set status='Open',assigned_user=null,resolution='' where id=tk.id;
   else raise exception 'This issue cannot be changed. Resolution requires a note and the assigned staff member.'; end if;
  end if;
 elsif p_action in ('save_resource','save_product','restock','save_event','save_reward','save_category','schedule','weekly_roster','void_bill','member_profile','cancel_shift','leave_decision','member_status','bracket','winner') then
  if me.role<>'admin' then raise exception 'Manager access required.'; end if;
  bid:=nullif(p_data->>'id','')::bigint;
  if p_action='save_resource' then
   if bid is not null then
    perform 1 from resources where id=bid for update;
    if p_data->>'status'<>'Available' and exists(select 1 from bookings where resource_id=bid and status in ('Reserved','Confirmed','Active')) then raise exception 'Resolve upcoming bookings before taking a space offline.'; end if;
    update resources set name=trim(p_data->>'name'),kind=p_data->>'kind',zone=p_data->>'zone',description=p_data->>'description',rate=(p_data->>'rate')::int,capacity=(p_data->>'capacity')::int,status=p_data->>'status' where id=bid;
   else insert into resources(name,kind,zone,description,rate,capacity) values(trim(p_data->>'name'),p_data->>'kind',p_data->>'zone',p_data->>'description',(p_data->>'rate')::int,(p_data->>'capacity')::int); end if;
  elsif p_action='save_category' then
   if bid is null then insert into categories(name) values(trim(p_data->>'name'));
   else update categories set name=trim(p_data->>'name') where id=bid; end if;
  elsif p_action='save_product' then
   if bid is null then insert into products(category_id,name,price,stock,threshold,image) values((p_data->>'category_id')::bigint,p_data->>'name',(p_data->>'price')::int,0,(p_data->>'threshold')::int,p_data->>'image');
   else update products set category_id=(p_data->>'category_id')::bigint,name=p_data->>'name',price=(p_data->>'price')::int,threshold=(p_data->>'threshold')::int,image=p_data->>'image',active=(p_data->>'active')::boolean where id=bid; end if;
  elsif p_action='restock' then
   n:=(p_data->>'quantity')::int;
   if n not between 1 and 10000 then raise exception 'Quantity must be 1–10000.'; end if;
   update products set stock=stock+n where id=bid;
   insert into stock_records(product_id,quantity,reason) values(bid,n,p_data->>'reason');
  elsif p_action='save_event' then
   if bid is null then insert into events(name,game,starts_at,ends_at,fee,capacity) values(p_data->>'name',p_data->>'game',(p_data->>'starts_at')::timestamptz,(p_data->>'ends_at')::timestamptz,(p_data->>'fee')::int,(p_data->>'capacity')::int);
   else
    perform 1 from events where id=bid for update;
    if exists(select 1 from registrations where event_id=bid and status='Registered') then raise exception 'Events with entrants cannot be edited or cancelled.'; end if;
    update events set name=p_data->>'name',game=p_data->>'game',starts_at=(p_data->>'starts_at')::timestamptz,ends_at=(p_data->>'ends_at')::timestamptz,fee=(p_data->>'fee')::int,capacity=(p_data->>'capacity')::int,status=p_data->>'status' where id=bid;
   end if;
  elsif p_action='save_reward' then
   if bid is null then insert into rewards(name,points,stock) values(p_data->>'name',(p_data->>'points')::int,(p_data->>'stock')::int);
   else update rewards set name=p_data->>'name',points=(p_data->>'points')::int,stock=(p_data->>'stock')::int,active=(p_data->>'active')::boolean where id=bid; end if;
  elsif p_action='schedule' then
   uid:=(p_data->>'user_id')::uuid; st:=(p_data->>'starts_at')::timestamptz; en:=(p_data->>'ends_at')::timestamptz;
   if not exists(select 1 from profiles where id=uid and active and role in ('staff','admin')) or en>st+interval '16 hours' or st<now() then raise exception 'Choose active staff and a future shift of at most 16 hours.'; end if;
   if exists(select 1 from leave_requests where user_id=uid and status='Approved' and tstzrange(day::timestamp at time zone 'Asia/Kuala_Lumpur',(day+1)::timestamp at time zone 'Asia/Kuala_Lumpur','[)') && tstzrange(st,en,'[)')) then raise exception 'Employee is on approved leave.'; end if;
   insert into shifts(user_id,starts_at,ends_at) values(uid,st,en);
  elsif p_action='weekly_roster' then
   uid:=(p_data->>'user_id')::uuid;
   perform 1 from profiles where id=uid and active and role in ('staff','admin') for update;
   if not found then raise exception 'Choose an active staff member.'; end if;
   if p_data->>'pattern' not in ('Weekdays','Every day','Weekend') then raise exception 'Choose a working-days pattern.'; end if;
   for j in 0..6 loop
    day_value:=(p_data->>'day')::date+j;
    if (p_data->>'pattern'='Weekdays' and extract(isodow from day_value)>5) or (p_data->>'pattern'='Weekend' and extract(isodow from day_value)<6) then continue; end if;
    st:=(day_value+(p_data->>'start_time')::time) at time zone 'Asia/Kuala_Lumpur';
    en:=(day_value+(p_data->>'end_time')::time) at time zone 'Asia/Kuala_Lumpur';
    if en<=st then en:=en+interval '1 day'; end if;
    if st<now() or en>st+interval '16 hours' then raise exception 'Choose future shifts of at most 16 hours.'; end if;
    if exists(select 1 from leave_requests where user_id=uid and status='Approved' and tstzrange(day::timestamp at time zone 'Asia/Kuala_Lumpur',(day+1)::timestamp at time zone 'Asia/Kuala_Lumpur','[)') && tstzrange(st,en,'[)')) then raise exception 'Employee is on leave. No shifts were created.'; end if;
    insert into shifts(user_id,starts_at,ends_at) values(uid,st,en);
   end loop;
   result:=jsonb_build_object('message','Weekly roster created.');
  elsif p_action='void_bill' then
   select * into invoice from bills where id=(p_data->>'bill_id')::uuid and status='Paid' for update;
   if invoice.id is null or length(trim(p_data->>'reason'))<3 then raise exception 'Choose a paid bill and enter a reason.'; end if;
   select * into target from profiles where id=invoice.user_id for update;
   if target.points<invoice.points then raise exception 'Earned points have already been spent. Cannot void this payment.'; end if;
   if invoice.kind='Booking' then
    if exists(select 1 from booking_extensions where booking_id=invoice.source_id) then raise exception 'An extended booking cannot be voided here.'; end if;
    update bookings set paid=false,status='Reserved' where id=invoice.source_id and status='Confirmed' and starts_at>now();
   elsif invoice.kind='Order' then update orders set paid=false where id=invoice.source_id and status='Pending';
   elsif invoice.kind='Event' then
    update registrations set paid=false where id=invoice.source_id and exists(select 1 from events where id=registrations.event_id and status='Open');
   else raise exception 'Session extensions cannot be voided after time has been allocated.'; end if;
   if not found then raise exception 'Only unfulfilled payments can be voided. Resolve used services with the manager.'; end if;
   update bills set status='Void',void_reason=p_data->>'reason' where id=invoice.id;
   update profiles set points=points-invoice.points,spent=spent-invoice.total where id=invoice.user_id;
   insert into points_transactions(user_id,points,reason) values(invoice.user_id,-invoice.points,'Voided counter payment');
   result:=jsonb_build_object('message','Payment voided. Return any cash to the customer and reconcile the till.');
  elsif p_action='member_profile' then
   update profiles set name=trim(p_data->>'name'),phone=p_data->>'phone' where id=(p_data->>'user_id')::uuid and role='customer';
  elsif p_action='cancel_shift' then
   update shifts set status='Cancelled' where id=(p_data->>'shift_id')::uuid and starts_at>now() and not exists(select 1 from attendance where shift_id=(p_data->>'shift_id')::uuid);
   if not found then raise exception 'Only upcoming shifts without attendance can be cancelled.'; end if;
  elsif p_action='leave_decision' then
   if p_data->>'status' not in ('Approved','Declined') then raise exception 'Invalid leave decision.'; end if;
   if p_data->>'status'='Approved' and exists(select 1 from shifts s join leave_requests l on l.user_id=s.user_id where l.id=(p_data->>'leave_id')::uuid and s.status='Scheduled' and tstzrange(s.starts_at,s.ends_at,'[)') && tstzrange(l.day::timestamp at time zone 'Asia/Kuala_Lumpur',(l.day+1)::timestamp at time zone 'Asia/Kuala_Lumpur','[)')) then raise exception 'Cancel overlapping shifts before approving leave.'; end if;
   update leave_requests set status=p_data->>'status' where id=(p_data->>'leave_id')::uuid and status='Pending';
  elsif p_action='member_status' then
   update profiles set active=(p_data->>'active')::boolean where id=(p_data->>'user_id')::uuid and role='customer';
  elsif p_action='bracket' then
   select * into ev from events where id=(p_data->>'event_id')::bigint for update;
   if ev.id is null or ev.status<>'Open' then raise exception 'Choose an open event.'; end if;
   if exists(select 1 from registrations where event_id=ev.id and status='Registered' and not paid) then raise exception 'Collect all entry fees before generating the bracket.'; end if;
   select array_agg(id order by id) into ids from registrations where event_id=ev.id and status='Registered';
   n:=coalesce(array_length(ids,1),0); if n<2 then raise exception 'At least two paid teams are required.'; end if;
   size:=2; while size<n loop size:=size*2; end loop;
   -- Spread byes: each first-round pair has at least one team.
   for j in 1..size/2 loop insert into matches(event_id,round,slot,a,b) values(ev.id,1,j,ids[j],ids[j+size/2]); end loop;
   rd:=2; size:=size/2;
   while size>1 loop for j in 1..size/2 loop insert into matches(event_id,round,slot) values(ev.id,rd,j); end loop; size:=size/2; rd:=rd+1; end loop;
   for m in select mm.* from matches mm where mm.event_id=ev.id and mm.round=1 and mm.b is null loop
    update matches set winner=m.a,done=true where id=m.id;
    if mod(m.slot,2)=1 then update matches set a=m.a where event_id=ev.id and round=2 and slot=(m.slot+1)/2;
    else update matches set b=m.a where event_id=ev.id and round=2 and slot=(m.slot+1)/2; end if;
   end loop;
   update events set status='Running' where id=ev.id;
  elsif p_action='winner' then
   select * into m from matches where id=(p_data->>'match_id')::bigint for update;
   winner_id:=(p_data->>'winner')::uuid;
   if m.id is null or m.done or m.a is null or m.b is null or winner_id is null or winner_id not in (m.a,m.b) then raise exception 'Choose a participant in an unfinished match.'; end if;
   update matches set winner=winner_id,done=true where id=m.id;
   if mod(m.slot,2)=1 then update matches set a=winner_id where event_id=m.event_id and round=m.round+1 and slot=(m.slot+1)/2;
   else update matches set b=winner_id where event_id=m.event_id and round=m.round+1 and slot=(m.slot+1)/2; end if;
   if not found then update events set status='Completed' where id=m.event_id; end if;
  end if;
 else raise exception 'Unknown action.';
 end if;
 insert into requests values(me.id,p_request,result);
 return result;
exception when exclusion_violation then raise exception 'That time overlaps an existing booking, event or shift. Choose another time.';
end $$;

-- No table is writable from the APK. Only validated RPCs can change state.
do $$ declare t text; begin
 foreach t in array array['profiles','resources','bookings','booking_extensions','categories','products','stock_records','orders','order_items','events','registrations','matches','bills','rewards','redemptions','points_transactions','favorites','shifts','attendance','leave_requests','tasks','checklist','handovers','handover_reads','tickets','requests'] loop
  execute format('alter table public.%I enable row level security',t);
  execute format('revoke all on public.%I from anon, authenticated',t);
 end loop;
end $$;
revoke all on function public.create_profile(), public.expire_bookings(), public.ctfly_catalog(), public.ctfly_snapshot(), public.ctfly_action(text,jsonb,uuid) from public,anon,authenticated;
revoke all on function public.ctfly_quote(text,uuid) from public,anon,authenticated;
grant execute on function public.ctfly_catalog() to anon,authenticated;
grant execute on function public.ctfly_snapshot(), public.ctfly_action(text,jsonb,uuid) to authenticated;
grant execute on function public.ctfly_quote(text,uuid) to authenticated;

insert into resources(name,kind,zone,description,rate) select 'PC-'||lpad(i::text,2,'0'),'PC',case when i>12 then 'VIP' else 'Standard' end,case when i>12 then 'RTX 4070 · Ryzen 7 · 240 Hz' else 'RTX 3060 · Ryzen 5 · 144 Hz' end,case when i>12 then 1000 else 600 end from generate_series(1,16) i;
insert into resources(name,kind,zone,description,capacity,rate) values
('Snooker Club 01','Snooker','Social','Full-size table · cues included',4,2200),('Pool Social 01','Pool','Social','American pool · bring your crew',4,1800),('Pool Social 02','Pool','Social','American pool · bring your crew',4,1800),('Console Suite 01','Console','Lounge','PS5 · couch co-op · 4 controllers',4,2500),('Console Suite 02','Console','Lounge','PS5 · couch co-op · 4 controllers',4,2500),('Racing Rig 01','Racing','Simulator','Wheel + pedals · solo time trials',1,2000);
insert into categories(name) values('Drinks'),('Food'),('Game cards');
insert into products(category_id,name,price,stock,image) values
(1,'Iced coffee',650,24,'iced_coffee'),(1,'Energy drink',800,18,'energy_drink'),(1,'Mineral water',250,30,'mineral_water'),(1,'Iced lemon tea',550,20,'iced_lemon_tea'),(2,'Cup noodles',550,12,'cup_noodles'),(2,'Potato chips',450,14,'potato_chips'),(2,'Chicken burger',1250,12,'chicken_burger'),(2,'French fries',600,20,'fries'),(3,'Steam RM 20 card',2000,6,'game_card');
insert into stock_records(product_id,quantity,reason) select id,stock,'Opening stock' from products;
insert into events(name,game,starts_at,ends_at,fee,capacity) values('Friday Night Valorant','Valorant',date_trunc('day',now())+interval '7 days 10 hours',date_trunc('day',now())+interval '7 days 14 hours',1500,8),('Weekend FC Cup','EA Sports FC',date_trunc('day',now())+interval '9 days 10 hours',date_trunc('day',now())+interval '9 days 14 hours',1000,8);
insert into rewards(name,points,stock) values('1 hour Standard PC voucher',100,20),('Free iced coffee',70,15),('CTFLY mouse pad',250,8);
commit;
