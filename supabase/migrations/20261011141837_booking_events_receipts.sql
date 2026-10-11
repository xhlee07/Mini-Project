-- Native booking UX, staff competitions/codes, immutable receipts and server email outbox.
begin;
alter table public.events add column description text not null default '' check(length(description)<=3000);
alter table public.events add column poster_url text not null default '';
alter table public.events add column prize_first text not null default 'To be announced' check(length(prize_first)<=800);
alter table public.events add column prize_second text not null default 'To be announced' check(length(prize_second)<=800);
alter table public.events add column prize_third text not null default 'To be announced' check(length(prize_third)<=800);
alter table public.events add column prize_consolation text not null default 'To be announced' check(length(prize_consolation)<=800);
alter table public.events add column registration_closes_at timestamptz;
alter table public.events add column created_by uuid references public.profiles;
create index events_creator_idx on public.events(created_by);
alter table public.events drop constraint events_status_check;
alter table public.events add constraint events_status_check check(status in ('Draft','Open','Running','Completed','Cancelled'));

create function private.require_number(d jsonb,k text,label text,lo int,hi int) returns int
language plpgsql set search_path=public,pg_temp as $$
declare n numeric;
begin
 if coalesce(d->>k,'')!~'^-?[0-9]+$' then raise exception '%: enter a whole number.',label; end if;
 n:=(d->>k)::numeric;
 if n<lo or n>hi then raise exception '%: use a value between % and %.',label,lo,hi; end if;
 return n::int;
end $$;

create function private.validate_club_input(a text,d jsonb) returns void
language plpgsql set search_path=public,pg_temp as $$
declare f record; label text; st timestamptz; en timestamptz; r resources; day date;
begin
 for f in select * from (values
 ('save_resource','name','Space name',1,80),('save_resource','description','Specifications / description',3,2000),
 ('save_product','name','Product name',1,120),('save_category','name','Category name',1,80),
 ('save_reward','name','Reward name',1,120),('register_event','team','Team name',1,80),
 ('task','title','Mission',3,200),('handover','title','Title',3,200),('handover','note','Handover details',3,2000),
 ('ticket','asset','PC / equipment name',2,120),('ticket','title','What needs attention?',3,2000),
 ('resolve','resolution','What was fixed?',3,2000),('leave','reason','Reason',3,2000),
 ('void_bill','reason','Reason / cash refund note',3,2000),('profile','name','Full name',2,80),('member_profile','name','Member name',2,80)
 ) x(action,key,label,minlen,maxlen) where action=a loop
  if coalesce(length(btrim(d->>f.key)),0) not between f.minlen and f.maxlen then raise exception '%: enter %–% characters.',f.label,f.minlen,f.maxlen; end if;
 end loop;
 if a='book' then
  perform private.require_number(d,'minutes','Duration (minimum 30 minutes)',30,180);
  perform private.require_number(d,'guests','Players / group size',1,20);
  begin st:=(d->>'starts_at')::timestamptz; exception when others then raise exception 'Reservation date / start time is invalid. Choose a valid date, hour and minute.'; end;
  if st is null then raise exception 'Reservation date / start time is required.'; end if;
  day:=(st at time zone 'Asia/Kuala_Lumpur')::date;
  if day<(now() at time zone 'Asia/Kuala_Lumpur')::date then raise exception 'Reservation date is in the past. Choose today or a future date.'; end if;
  if day>(now() at time zone 'Asia/Kuala_Lumpur')::date+90 then raise exception 'Reservation date must be within the next 90 days.'; end if;
  if st<now()-interval '1 minute' then raise exception 'Start time has already passed. Choose the current time or a later time.'; end if;
  select * into r from resources where id=(d->>'resource_id')::bigint for update;
  if r.id is null or r.status<>'Available' then raise exception 'Selected space is unavailable. Refresh and choose another space.'; end if;
  if (d->>'guests')::int>r.capacity then raise exception 'Players / group size exceeds this space capacity of %.',r.capacity; end if;
  if (r.kind='GoKart' and (d->>'minutes')::int not in (30,60)) or (r.kind='Arcade' and (d->>'minutes')::int not in (30,60,90,120)) or (r.kind not in ('GoKart','Arcade') and (d->>'minutes')::int not in (30,60,90,120,180)) then raise exception 'Duration is unavailable for this activity. Choose one of the listed durations (minimum 30 minutes).'; end if;
 elsif a='save_resource' then
  perform private.require_number(d,'capacity','Player capacity',1,20);perform private.require_number(d,'rate','Hourly rate',1,10000000);
 elsif a='save_product' then
  perform private.require_number(d,'price','Product price',0,10000000);perform private.require_number(d,'stock','Stock quantity',0,1000000);perform private.require_number(d,'threshold','Low-stock threshold',0,1000000);
 elsif a='save_reward' then
  perform private.require_number(d,'points','Points required',1,10000000);perform private.require_number(d,'stock','Available quantity',0,1000000);
 elsif a='restock' then
  perform private.require_number(d,'quantity','Restock quantity',1,1000000);
 elsif a in ('schedule','save_event') then
  begin st:=(d->>'starts_at')::timestamptz; exception when others then raise exception 'Start date / time is invalid.'; end;
  begin en:=(d->>'ends_at')::timestamptz; exception when others then raise exception 'End date / time is invalid.'; end;
  if st is null or en is null then raise exception 'Start and end date / time are required.'; end if;
  if st<now() then raise exception 'Start date / time has already passed.'; end if;
  if en<=st then raise exception 'End time must be later than start time.'; end if;
  if a='schedule' and en>st+interval '16 hours' then raise exception 'Shift duration cannot exceed 16 hours.'; end if;
 end if;
end $$;

create function private.save_club_event(d jsonb) returns jsonb
language plpgsql set search_path=public,pg_temp as $$
declare me profiles; old events; eid bigint; st timestamptz; en timestamptz; close_at timestamptz; fee_value int; cap int; state text; poster text; k text;
begin
 select * into me from profiles where id=auth.uid() and active and role in ('staff','admin');
 if me.id is null then raise exception 'Staff access required to create competitions.'; end if;
 if coalesce(length(btrim(d->>'name')),0) not between 3 and 120 then raise exception 'Competition name: enter 3–120 characters.'; end if;
 if coalesce(length(btrim(d->>'game')),0) not between 2 and 80 then raise exception 'Game: enter 2–80 characters.'; end if;
 st:=(d->>'starts_at')::timestamptz;en:=(d->>'ends_at')::timestamptz;
 close_at:=coalesce(nullif(d->>'registration_closes_at','')::timestamptz,st);
 if close_at>st or close_at<=now() then raise exception 'Registration closing date / time must be in the future and no later than the competition start.'; end if;
 fee_value:=private.require_number(d,'fee','Entry fee',0,10000000);cap:=private.require_number(d,'capacity','Team slots',2,64);
 state:=coalesce(d->>'status','Open');if state not in ('Draft','Open','Cancelled') then raise exception 'Competition status: choose Draft, Open or Cancelled.'; end if;
 poster:=coalesce(d->>'poster_url','');
 if poster<>'' and poster!~'^https://shfatnjwpdyxdbehvjil[.]supabase[.]co/storage/v1/object/public/event-posters/[a-f0-9-]+/[a-f0-9-]+[.]jpg$' then raise exception 'Competition image is invalid. Use Upload image or the default poster.'; end if;
 if length(coalesce(d->>'description',''))>3000 then raise exception 'Competition description: use at most 3000 characters.'; end if;
 foreach k in array array['prize_first','prize_second','prize_third','prize_consolation'] loop
  if coalesce(length(btrim(d->>k)),16) not between 1 and 800 then raise exception '%: enter a prize or explicitly write No prize (maximum 800 characters).',replace(k,'_',' '); end if;
 end loop;
 if nullif(d->>'id','') is not null then
  select * into old from events where id=(d->>'id')::bigint for update;
  if old.id is null then raise exception 'Competition no longer exists. Refresh the list.'; end if;
  if old.status in ('Running','Completed') then raise exception 'Competition has already started or completed. Its booking details cannot be edited.'; end if;
  if exists(select 1 from registrations where event_id=old.id and status='Registered' and paid) and (state='Cancelled' or st<>old.starts_at or en<>old.ends_at or fee_value<>old.fee or cap<>old.capacity or d->>'game'<>old.game) then raise exception 'Paid entries exist. Handle refunds before changing the competition date, time, fee, game or capacity.'; end if;
  if cap<(select count(*) from registrations where event_id=old.id and status='Registered') then raise exception 'Team slots cannot be fewer than existing registrations.'; end if;
  update events set name=btrim(d->>'name'),game=btrim(d->>'game'),starts_at=st,ends_at=en,registration_closes_at=close_at,fee=fee_value,capacity=cap,status=state,description=coalesce(d->>'description',''),poster_url=poster,
    prize_first=coalesce(d->>'prize_first',old.prize_first),prize_second=coalesce(d->>'prize_second',old.prize_second),prize_third=coalesce(d->>'prize_third',old.prize_third),prize_consolation=coalesce(d->>'prize_consolation',old.prize_consolation) where id=old.id returning id into eid;
 else
  insert into events(name,game,starts_at,ends_at,registration_closes_at,fee,capacity,status,description,poster_url,prize_first,prize_second,prize_third,prize_consolation,created_by)
  values(btrim(d->>'name'),btrim(d->>'game'),st,en,close_at,fee_value,cap,state,coalesce(d->>'description',''),poster,coalesce(d->>'prize_first','To be announced'),coalesce(d->>'prize_second','To be announced'),coalesce(d->>'prize_third','To be announced'),coalesce(d->>'prize_consolation','No consolation prize'),me.id) returning id into eid;
 end if;
 return jsonb_build_object('id',eid,'message',case when state='Draft' then 'Competition saved as draft.' else 'Competition updated.' end);
end $$;

create table public.promotions (
 id uuid primary key default gen_random_uuid(), code text unique not null check(code~'^[A-Z0-9-]{3,24}$'), title text not null check(length(title) between 3 and 120),
 scope text not null check(scope in ('All','Booking','Order','Event')), benefit text not null check(benefit in ('Percentage','Fixed','Free')),
 amount int not null check(amount>=0), resource_kind text not null default 'All', max_minutes int not null default 0 check(max_minutes between 0 and 180),
 starts_at timestamptz not null, ends_at timestamptz not null check(ends_at>starts_at), usage_limit int not null check(usage_limit between 1 and 1000000),
 per_user_limit int not null default 1 check(per_user_limit between 1 and 100), active boolean not null default true,
 target_user uuid references public.profiles, created_by uuid not null references public.profiles, created_at timestamptz not null default now());
create index promotions_target_idx on public.promotions(target_user);create index promotions_creator_idx on public.promotions(created_by);
alter table public.promotions enable row level security;revoke all on public.promotions from public,anon,authenticated;
alter table public.test_payments add column promotion_id uuid references public.promotions;
alter table public.test_payments add column promo_code text;
create index test_payments_promotion_idx on public.test_payments(promotion_id);
create table public.promo_claims (
 payment_id uuid primary key references public.test_payments, promotion_id uuid not null references public.promotions,user_id uuid not null references public.profiles,
 status text not null check(status in ('Held','Redeemed','Released')),expires_at timestamptz not null,created_at timestamptz not null default now());
create index promo_claims_promo_status_idx on public.promo_claims(promotion_id,status);
create index promo_claims_user_idx on public.promo_claims(user_id,promotion_id,status);
alter table public.promo_claims enable row level security;revoke all on public.promo_claims from public,anon,authenticated;

create function private.save_promotion(d jsonb) returns jsonb
language plpgsql set search_path=public,pg_temp as $$
declare me profiles; promo promotions; result_id uuid; amt int; lim int; peruser int; maxmins int; begin_at timestamptz; finish_at timestamptz; target uuid; benefit_type text; code_value text;
begin
 select * into me from profiles where id=auth.uid() and active and role in ('staff','admin');if me.id is null then raise exception 'Staff access required to create promo codes.'; end if;
 code_value:=upper(btrim(d->>'code'));if coalesce(code_value,'')!~'^[A-Z0-9-]{3,24}$' then raise exception 'Promo code: use 3–24 letters, numbers or hyphens.'; end if;
 if coalesce(length(btrim(d->>'title')),0) not between 3 and 120 then raise exception 'Promo title: enter 3–120 characters.'; end if;
 if coalesce(d->>'scope','') not in ('All','Booking','Order','Event') then raise exception 'Promo applies to: choose Booking, Order, Event or All.'; end if;
 benefit_type:=d->>'benefit';if coalesce(benefit_type,'') not in ('Percentage','Fixed','Free') then raise exception 'Promo benefit: choose Percentage, Fixed or Free.'; end if;
 amt:=case when benefit_type='Free' then 0 else private.require_number(d,'amount','Discount amount',1,case when benefit_type='Percentage' then 100 else 10000000 end) end;
 lim:=private.require_number(d,'usage_limit','Total uses',1,1000000);peruser:=private.require_number(d||jsonb_build_object('per_user_limit',coalesce(d->>'per_user_limit','1')),'per_user_limit','Uses per customer',1,100);
 maxmins:=private.require_number(d||jsonb_build_object('max_minutes',coalesce(d->>'max_minutes','0')),'max_minutes','Maximum session minutes (0 = any)',0,180);
 if coalesce(d->>'resource_kind','All') not in ('All','PC','VIPRoom','Pool','Snooker','Console','Racing','Arcade','GoKart') then raise exception 'Booking type: choose a listed activity.'; end if;
 begin begin_at:=(d->>'starts_at')::timestamptz;exception when others then raise exception 'Promo start date / time is invalid.';end;
 begin finish_at:=(d->>'ends_at')::timestamptz;exception when others then raise exception 'Promo expiry date / time is invalid.';end;
 if begin_at is null or finish_at is null or finish_at<=begin_at or finish_at<=now() then raise exception 'Promo expiry date / time must be in the future and later than its start.'; end if;
 target:=nullif(d->>'target_user','')::uuid;
 if target is not null and not exists(select 1 from profiles where id=target and active and role='customer') then raise exception 'Selected customer is unavailable. Refresh the customer list.'; end if;
 if nullif(d->>'id','') is not null then
  select * into promo from promotions where id=(d->>'id')::uuid for update;if promo.id is null then raise exception 'Promo code no longer exists.';end if;
  if exists(select 1 from promo_claims where promotion_id=promo.id and status in ('Held','Redeemed')) and (code_value<>promo.code or benefit_type<>promo.benefit or amt<>promo.amount or d->>'scope'<>promo.scope or target is distinct from promo.target_user or lim<promo.usage_limit or peruser<promo.per_user_limit or coalesce(d->>'resource_kind','All')<>promo.resource_kind or maxmins<>promo.max_minutes or begin_at<>promo.starts_at or finish_at<>promo.ends_at) then raise exception 'This code has payment claims. Keep its benefit, customer and limits, or create another code.';end if;
  update promotions set title=btrim(d->>'title'),code=code_value,scope=d->>'scope',benefit=benefit_type,amount=amt,resource_kind=coalesce(d->>'resource_kind','All'),max_minutes=maxmins,starts_at=begin_at,ends_at=finish_at,usage_limit=lim,per_user_limit=peruser,target_user=target,active=coalesce((d->>'active')::boolean,true) where id=promo.id returning id into result_id;
 else insert into promotions(code,title,scope,benefit,amount,resource_kind,max_minutes,starts_at,ends_at,usage_limit,per_user_limit,target_user,created_by)
 values(code_value,btrim(d->>'title'),d->>'scope',benefit_type,amt,coalesce(d->>'resource_kind','All'),maxmins,begin_at,finish_at,lim,peruser,target,me.id) returning id into result_id;end if;
 return jsonb_build_object('id',result_id,'message','Promo code saved.');
end $$;

create function private.apply_promotion(code_value text,q jsonb,uid uuid) returns jsonb
language plpgsql set search_path=public,pg_temp as $$
declare promo promotions; deduction int; held_count int; customer_count int;
begin
 if coalesce(btrim(code_value),'')='' then return q;end if;
 select * into promo from promotions where code=upper(btrim(code_value)) for update;
 if promo.id is null then raise exception 'Promo code does not exist. Check the code.';end if;
 if not promo.active then raise exception 'Promo code has been disabled.';end if;
 if promo.starts_at>now() then raise exception 'Promo code is not active yet. Check its starting date / time.';end if;
 if promo.ends_at<=now() then raise exception 'Promo code has expired.';end if;
 if promo.target_user is not null and promo.target_user<>uid then raise exception 'Promo code is assigned to another customer.';end if;
 if promo.scope not in ('All',q->>'kind') then raise exception 'Promo code does not apply to this booking or order type.';end if;
 if q->>'kind'='Booking' and ((promo.resource_kind<>'All' and promo.resource_kind<>q->'items'->0->>'resource_kind') or (promo.max_minutes>0 and (q->'items'->0->>'duration_minutes')::int>promo.max_minutes)) then raise exception 'Promo code does not apply to this activity or session duration.';end if;
 select count(*),count(*) filter(where c.user_id=uid) into held_count,customer_count from promo_claims c join test_payments t on t.id=c.payment_id where c.promotion_id=promo.id and c.status in ('Held','Redeemed') and (c.status='Redeemed' or c.expires_at>now()) and not(t.source_id=(q->>'source_id')::uuid and t.kind=q->>'kind' and t.status in ('Pending','Processing'));
 if held_count>=promo.usage_limit then raise exception 'Promo code has reached its total usage limit.';end if;
 if customer_count>=promo.per_user_limit then raise exception 'You have already used this promo code as many times as allowed.';end if;
 deduction:=least((q->>'subtotal')::int,case promo.benefit when 'Free' then (q->>'subtotal')::int when 'Fixed' then promo.amount else (q->>'subtotal')::int*promo.amount/100 end);
 if deduction<(q->>'discount')::int then raise exception 'Your member discount is better than this promo code. Remove the code to keep your member price.';end if;
 return q||jsonb_build_object('promotion_id',promo.id,'promo_code',promo.code,'discount',deduction,'total',(q->>'subtotal')::int-deduction,'points',((q->>'subtotal')::int-deduction)/100,'expires_at',least((q->>'expires_at')::timestamptz,promo.ends_at));
end $$;

create function private.sync_promo_claim() returns trigger
language plpgsql set search_path=public,pg_temp as $$
begin
 if new.promotion_id is not null and new.status is distinct from old.status then
  perform 1 from promotions where id=new.promotion_id for update;
  if new.status='Succeeded' then
   update promo_claims set status='Redeemed' where payment_id=new.id and status='Held' and expires_at>now();
   if not found then raise exception 'Promo reservation expired. Restart checkout.';end if;
  elsif new.status in ('Failed','Cancelled','Expired','Refunded') then update promo_claims set status='Released' where payment_id=new.id;end if;
 end if;
 return new;
end $$;
create trigger sync_promo_claim after update on public.test_payments for each row execute function private.sync_promo_claim();

-- Immutable purchase details; later resource edits or extensions cannot rewrite a receipt.
alter table public.bills add column details jsonb not null default '{}'::jsonb;
create function private.bill_details(b public.bills) returns jsonb
language plpgsql set search_path=public,pg_temp as $$
declare d jsonb; session public.bookings; ext public.booking_extensions; ending timestamptz;
begin
 d:=jsonb_build_object('customer_name',(select name from profiles where id=b.user_id),'currency','MYR');
 if b.kind in ('Booking','Extension') then
  if b.kind='Extension' then select * into ext from booking_extensions where id=b.source_id;select * into session from bookings where id=ext.booking_id;
  else select * into session from bookings where id=b.source_id;end if;
  ending:=session.ends_at;
  if b.kind='Booking' then ending:=ending-make_interval(mins=>coalesce((select sum(minutes)::int from booking_extensions where booking_id=session.id),0));end if;
  d:=d||coalesce((select jsonb_build_object('activity',r.kind,'resource',r.name,'zone',r.zone,'starts_at',case when b.kind='Extension' then ending-make_interval(mins=>ext.minutes) else session.starts_at end,'ends_at',ending,'duration_minutes',case when b.kind='Extension' then ext.minutes else extract(epoch from ending-session.starts_at)::int/60 end,'players',session.guests,'hourly_rate',r.rate,'booking_id',session.id,'items',jsonb_build_array(jsonb_build_object('name',r.name,'qty',1,'price',b.subtotal))) from resources r where r.id=session.resource_id),'{}'::jsonb);
 elsif b.kind='Order' then
  d:=d||jsonb_build_object('activity','Café order','items',coalesce((select jsonb_agg(jsonb_build_object('name',name,'qty',qty,'price',price,'line_total',qty*price) order by id) from order_items where order_id=b.source_id),'[]'::jsonb));
 elsif b.kind='Event' then
  d:=d||coalesce((select jsonb_build_object('activity','Competition','resource',e.name,'game',e.game,'team',g.team,'starts_at',e.starts_at,'ends_at',e.ends_at,'duration_minutes',extract(epoch from e.ends_at-e.starts_at)::int/60,'items',jsonb_build_array(jsonb_build_object('name',e.name,'qty',1,'price',b.subtotal))) from registrations g join events e on e.id=g.event_id where g.id=b.source_id),'{}'::jsonb);
 end if;
 if b.payment_reference is not null then d:=d||jsonb_build_object('promo_code',(select promo_code from test_payments where reference=b.payment_reference));end if;
 return d;
end $$;
update public.bills b set details=private.bill_details(b);
create function private.freeze_receipt() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin new.details:=private.bill_details(new);return new;end $$;
create trigger freeze_receipt before insert on public.bills for each row execute function private.freeze_receipt();

create table public.receipt_email_jobs (
 bill_id uuid primary key references public.bills,user_id uuid not null references public.profiles,
 status text not null default 'Queued' check(status in ('Queued','Sending','Sent','NotConfigured','Failed','Unknown')),
 attempts int not null default 0,last_attempt timestamptz,last_error text not null default '',provider_id text,sent_at timestamptz,created_at timestamptz not null default now());
create index receipt_mail_user_idx on public.receipt_email_jobs(user_id,created_at desc);
create index receipt_mail_status_idx on public.receipt_email_jobs(status,created_at);
alter table public.receipt_email_jobs enable row level security;revoke all on public.receipt_email_jobs from public,anon,authenticated;
create table private.mail_runtime(id boolean primary key default true check(id),worker_secret text not null,configured boolean not null default false);
alter table private.mail_runtime enable row level security;revoke all on private.mail_runtime from public,anon,authenticated;
insert into private.mail_runtime(worker_secret) values(replace(gen_random_uuid()::text||gen_random_uuid()::text,'-',''));
create function private.queue_receipt() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin insert into receipt_email_jobs(bill_id,user_id) values(new.id,new.user_id) on conflict do nothing;return new;end $$;
create trigger queue_receipt after insert on public.bills for each row execute function private.queue_receipt();
create function private.receipt_action(a text,d jsonb) returns jsonb language plpgsql set search_path=public,pg_temp as $$
declare b bills;begin
 select * into b from bills where id=(d->>'id')::uuid and exists(select 1 from profiles where id=auth.uid() and active and (id=bills.user_id or role in ('staff','admin')));
 if b.id is null then raise exception 'Receipt not found for this account.';end if;
 if a='receipt_email' then
  insert into receipt_email_jobs(bill_id,user_id) values(b.id,b.user_id) on conflict(bill_id) do update set status='Queued',last_error='' where receipt_email_jobs.status in ('NotConfigured','Failed');
 end if;
 return jsonb_build_object('receipt',to_jsonb(b),'email',(select to_jsonb(j)-'user_id' from receipt_email_jobs j where bill_id=b.id),'message',case when a='receipt_email' then 'Receipt queued for your registered email. Delivery needs a configured mail service.' else 'Receipt ready.' end);
end $$;
-- Only the server's service role can invoke the worker. The additional private secret authenticates cron dispatch.
create function private.receipt_worker(secret_value text,operation text,d jsonb) returns jsonb
language plpgsql security definer set search_path=public,pg_temp as $$
declare jobs jsonb;state text;begin
 if not exists(select 1 from private.mail_runtime where worker_secret=secret_value) then raise exception 'Worker authentication failed.';end if;
 if operation='configure' then
  update private.mail_runtime set configured=coalesce((d->>'configured')::boolean,false);
  if not coalesce((d->>'configured')::boolean,false) then update receipt_email_jobs set status='NotConfigured',last_error='Mail service is not configured.' where status in ('Queued','Failed');
  else update receipt_email_jobs set status='Queued',last_error='' where status='NotConfigured';end if;
  return jsonb_build_object('configured',coalesce((d->>'configured')::boolean,false));
 elsif operation='claim' then
  if not exists(select 1 from private.mail_runtime where configured) then return '[]'::jsonb;end if;
  update receipt_email_jobs set status='Unknown',last_error='Sending was interrupted. Verify delivery before retrying.' where status='Sending' and last_attempt<now()-interval '5 minutes';
  with chosen as(select bill_id from receipt_email_jobs where status='Queued' order by created_at for update skip locked limit 3),claimed as(update receipt_email_jobs j set status='Sending',attempts=attempts+1,last_attempt=now() from chosen where j.bill_id=chosen.bill_id returning j.*)
  select coalesce(jsonb_agg(jsonb_build_object('bill',to_jsonb(b),'email',u.email)),'[]'::jsonb) into jobs from claimed j join bills b on b.id=j.bill_id join auth.users u on u.id=j.user_id;
  return jobs;
 elsif operation='result' then
  state:=d->>'status';if state not in ('Sent','Failed','Unknown') then raise exception 'Invalid worker result.';end if;
  update receipt_email_jobs set status=state,last_error=left(coalesce(d->>'error',''),400),provider_id=d->>'provider_id',sent_at=case when state='Sent' then now() else null end where bill_id=(d->>'id')::uuid and status='Sending';
  return jsonb_build_object('updated',found);
 end if;
 raise exception 'Invalid worker operation.';
end $$;
create function public.ctfly_receipt_worker(p_secret text,p_operation text,p_data jsonb default '{}'::jsonb) returns jsonb
language sql security invoker set search_path=public,pg_temp as $$select private.receipt_worker(p_secret,p_operation,p_data)$$;
revoke all on function public.ctfly_receipt_worker(text,text,jsonb) from public,anon,authenticated;

-- Upgrade existing authenticated APIs without replacing their ownership or request deduplication checks.
do $$ declare def text;begin
 def:=pg_get_functiondef('public.ctfly_action(text,jsonb,uuid)'::regprocedure);
 def:=replace(def,'perform expire_bookings();','perform private.validate_club_input(p_action,p_data); perform expire_bookings();');
 def:=replace(def,'if p_action in (''test_quote''', 'if p_action=''save_event'' then result:=private.save_club_event(p_data); elsif p_action=''save_promotion'' then result:=private.save_promotion(p_data); elsif p_action in (''receipt_detail'',''receipt_email'') then result:=private.receipt_action(p_action,p_data); elsif p_action in (''test_quote''');
 def:=regexp_replace(def,'if r.id is null or r.status<>''Available''.*?Choose an available space, valid time and group size[.]''; end if;', 'if n<30 then raise exception ''Duration: minimum 30 minutes.'';end if;','s');
 def:=replace(def,'(r.rate*n+30)/60','((r.rate::bigint*n+30)/60)::int');
 def:=replace(def,'select * into ev from events where id=(p_data->>''event_id'')::bigint for update;', 'select * into ev from events where id=(p_data->>''event_id'')::bigint for update; if p_action=''register_event'' and coalesce(ev.registration_closes_at,ev.starts_at)<=now() then raise exception ''Registration closing date / time has passed.'';end if;');
 def:=replace(def,'exception when exclusion_violation', $patch$exception when unique_violation then raise exception '%: this name or code is already in use. Choose another.',case p_action when 'save_promotion' then 'Promo code' when 'save_resource' then 'Space name' when 'save_category' then 'Category name' else 'Selected entry' end; when invalid_text_representation then raise exception 'Selection: an item or number is invalid. Refresh and choose a listed option.'; when datetime_field_overflow or invalid_datetime_format then raise exception 'Date / time: choose a valid date, hour and minute.'; when foreign_key_violation then raise exception 'Selection: this item is no longer available. Refresh and select it again.'; when check_violation then raise exception 'Input range: one of the supplied values is outside its allowed range. Check the field limits.'; when exclusion_violation$patch$);
 def:=replace(def,'raise exception ''That time overlaps an existing booking, event or shift. Choose another time.'';', 'raise exception ''%: this time overlaps an existing %. Choose another start time.'',case p_action when ''book'' then ''Start time'' when ''save_event'' then ''Competition time'' else ''Shift time'' end,case p_action when ''book'' then ''reservation for this space'' when ''save_event'' then ''competition'' else ''shift for this staff member'' end;');
 execute def;
 def:=pg_get_functiondef('public.ctfly_catalog()'::regprocedure);def:=replace(def,'status<>''Cancelled''','status not in (''Cancelled'',''Draft'')');execute def;
 def:=pg_get_functiondef('public.ctfly_snapshot()'::regprocedure);
 def:=replace(def,'return result;', $patch$
 result:=result||jsonb_build_object('receipt_emails',coalesce((select jsonb_agg(to_jsonb(j)-'user_id' order by created_at desc) from receipt_email_jobs j where j.user_id=me.id or crew),'[]'::jsonb));
 if crew then result:=result||jsonb_build_object('events',coalesce((select jsonb_agg(e order by starts_at desc) from events e),'[]'::jsonb),'promotions',coalesce((select jsonb_agg(to_jsonb(p)||jsonb_build_object('used',(select count(*) from promo_claims c where c.promotion_id=p.id and c.status='Redeemed'))) from promotions p),'[]'::jsonb));end if;
 return result;$patch$);execute def;
 def:=pg_get_functiondef('private.test_quote(text,uuid,uuid)'::regprocedure);
 def:=replace(def,'''detail'',b.starts_at,''price'',b.total','''detail'',b.starts_at,''resource_kind'',r.kind,''zone'',r.zone,''starts_at'',b.starts_at,''ends_at'',b.ends_at,''duration_minutes'',extract(epoch from b.ends_at-b.starts_at)::int/60,''price'',b.total');execute def;
 def:=pg_get_functiondef('private.test_payment_action(text,jsonb)'::regprocedure);
 def:=replace(def,'q:=private.test_quote(chosen_kind,sid,me.id);', $patch$
 if p_action='test_checkout' then select * into pay from test_payments where kind=chosen_kind and source_id=sid and user_id=me.id and status in ('Pending','Processing');
 if pay.id is not null then return jsonb_build_object('payment',to_jsonb(pay),'message','Existing checkout resumed.');end if;end if;
 q:=private.apply_promotion(p_data->>'promo_code',private.test_quote(chosen_kind,sid,me.id),me.id);$patch$);
 def:=replace(def, '(q->>''expires_at'')::timestamptz) returning * into pay;', $patch$(q->>'expires_at')::timestamptz) returning * into pay;
  if q->>'promotion_id' is not null then
   update test_payments set promotion_id=(q->>'promotion_id')::uuid,promo_code=q->>'promo_code' where id=pay.id returning * into pay;
   insert into promo_claims(payment_id,promotion_id,user_id,status,expires_at) values(pay.id,pay.promotion_id,me.id,'Held',pay.expires_at);
  end if;$patch$);
 def:=replace(def,'select * into pay from test_payments where id=pay.id for update;','if pay.promotion_id is not null then perform 1 from promotions where id=pay.promotion_id for update;end if; select * into pay from test_payments where id=pay.id for update;');execute def;
end $$;
revoke all on all functions in schema private from public,anon,authenticated;
do $$ begin if exists(select 1 from pg_roles where rolname='service_role') then
 grant usage on schema private to service_role;
 grant execute on function private.receipt_worker(text,text,jsonb),public.ctfly_receipt_worker(text,text,jsonb) to service_role;
end if;end $$;
-- Public read-only posters; uploads are validated and performed by the staff-authenticated Edge Function.
do $$ begin if to_regclass('storage.buckets') is not null then
 insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('event-posters','event-posters',true,4194304,array['image/jpeg']) on conflict(id) do nothing;
end if;end $$;
-- Local database tests omit unavailable extensions; the hosted project has pg_net and pg_cron.
do $$ begin if exists(select 1 from pg_available_extensions where name='pg_net') then create extension if not exists pg_net with schema extensions;end if;end $$;
create function private.kick_receipt_worker() returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if to_regprocedure('net.http_post(text,jsonb,jsonb,jsonb,integer)') is not null then
  perform net.http_post(url:='https://shfatnjwpdyxdbehvjil.supabase.co/functions/v1/club-services',headers:=jsonb_build_object('Content-Type','application/json','X-Receipt-Worker',(select worker_secret from private.mail_runtime)),body:='{"operation":"dispatch"}'::jsonb,timeout_milliseconds:=60000);
 end if;
end $$;
revoke all on function private.kick_receipt_worker() from public,anon,authenticated;
do $$ begin if to_regclass('cron.job') is not null then perform cron.schedule('ctfly-receipt-mail','* * * * *','select private.kick_receipt_worker()');end if;end $$;
commit;
