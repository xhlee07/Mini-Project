-- School-project payment simulator. No financial provider, credentials or real funds.
begin;
create schema if not exists private;
revoke all on schema private from public,anon,authenticated;
alter table public.profiles add column test_points int not null default 0 check(test_points>=0);
alter table public.profiles add column test_spent int not null default 0 check(test_spent>=0);
alter table public.bills add column environment text not null default 'counter' check(environment in ('counter','test'));
alter table public.bills add column payment_reference text;
alter table public.bookings add column hold_expires_at timestamptz;
alter table public.orders add column hold_expires_at timestamptz;
alter table public.registrations add column hold_expires_at timestamptz;
alter table public.bookings alter column hold_expires_at set default now()+interval '10 minutes';
alter table public.orders alter column hold_expires_at set default now()+interval '10 minutes';
alter table public.registrations alter column hold_expires_at set default now()+interval '10 minutes';
create table public.test_payments (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles,
 kind text not null check(kind in ('Booking','Order','Event')), source_id uuid not null,
 method text not null check(method in ('TNG','FPX','Card')),
 environment text not null default 'test' check(environment='test'),
 provider text not null default 'simulator' check(provider='simulator'),
 currency text not null default 'MYR' check(currency='MYR'),
 reference text not null unique default 'TEST-'||upper(replace(gen_random_uuid()::text,'-','')),
 subtotal int not null check(subtotal>=0), discount int not null check(discount between 0 and subtotal),
 total int not null check(total=subtotal-discount), points int not null check(points>=0),
 items jsonb not null, status text not null default 'Pending'
 check(status in ('Pending','Processing','Succeeded','Failed','Cancelled','Expired','Refunded')),
 expires_at timestamptz not null, created_at timestamptz not null default now(), settled_at timestamptz,
 bill_id uuid unique references public.bills);
create unique index one_active_test_checkout on public.test_payments(kind,source_id) where status in ('Pending','Processing');
create index test_payments_user_created on public.test_payments(user_id,created_at desc);
create index test_payments_status_expiry on public.test_payments(status,expires_at);
alter table public.test_payments enable row level security;
revoke all on public.test_payments from public,anon,authenticated;

create function private.restore_test_order(p_id uuid) returns void
language plpgsql set search_path=public,pg_temp as $$
declare i record;
begin
 for i in select product_id,qty from order_items where order_id=p_id order by product_id loop
  update products set stock=stock+i.qty where id=i.product_id;
  insert into stock_records(product_id,quantity,reason) values(i.product_id,i.qty,'Released test checkout hold');
 end loop;
end $$;

create function private.expire_test_holds() returns void
language plpgsql set search_path=public,pg_temp as $$
declare held record;
begin
 update bookings set status='Expired' where not paid and status='Reserved' and hold_expires_at<=now();
 for held in select id from orders where not paid and status='Pending' and hold_expires_at<=now() order by id for update loop
  update orders set status='Cancelled' where id=held.id;
  perform private.restore_test_order(held.id);
 end loop;
 update registrations set status='Withdrawn' where not paid and status='Registered' and hold_expires_at<=now();
 update test_payments p set status=case when p.expires_at<=now() then 'Expired' else 'Cancelled' end
 where p.status in ('Pending','Processing') and (p.expires_at<=now()
 or (p.kind='Booking' and not exists(select 1 from bookings b where b.id=p.source_id and not b.paid and b.status='Reserved'))
 or (p.kind='Order' and not exists(select 1 from orders o where o.id=p.source_id and not o.paid and o.status='Pending'))
 or (p.kind='Event' and not exists(select 1 from registrations r where r.id=p.source_id and not r.paid and r.status='Registered')));
end $$;

-- Caller already owns the account lock; source locks precede payment locks.
create function private.test_quote(p_kind text,p_id uuid,p_uid uuid) returns jsonb
language plpgsql set search_path=public,pg_temp as $$
declare amount int; deadline timestamptz; lines jsonb; spending int; deduction int;
begin
 if p_kind='Booking' then
  select b.total,coalesce(b.hold_expires_at,now()+interval '10 minutes'),jsonb_build_array(jsonb_build_object('name',r.name,'qty',b.guests,'detail',b.starts_at,'price',b.total))
   into amount,deadline,lines from bookings b join resources r on r.id=b.resource_id
   where b.id=p_id and b.user_id=p_uid and not b.paid and b.status='Reserved' and b.ends_at>now() for update of b;
  update bookings set hold_expires_at=deadline where id=p_id and user_id=p_uid and hold_expires_at is null;
 elsif p_kind='Order' then
  select total,coalesce(hold_expires_at,now()+interval '10 minutes') into amount,deadline from orders
   where id=p_id and user_id=p_uid and not paid and status='Pending' for update;
  select jsonb_agg(jsonb_build_object('name',name,'qty',qty,'price',price)) into lines from order_items where order_id=p_id;
  update orders set hold_expires_at=deadline where id=p_id and user_id=p_uid and hold_expires_at is null;
 elsif p_kind='Event' then
  select r.fee,least(coalesce(r.hold_expires_at,now()+interval '10 minutes'),e.starts_at),jsonb_build_array(jsonb_build_object('name',e.name,'qty',1,'price',r.fee))
   into amount,deadline,lines from registrations r join events e on e.id=r.event_id
   where r.id=p_id and r.user_id=p_uid and not r.paid and r.status='Registered' and e.status='Open' and e.starts_at>now() for update of r,e;
  update registrations set hold_expires_at=deadline where id=p_id and user_id=p_uid and hold_expires_at is null;
 else raise exception 'Choose Booking, Order or Event.'; end if;
 if amount is null or deadline<=now() then raise exception 'Already paid, cancelled or checkout hold expired.'; end if;
 select spent into spending from profiles where id=p_uid;
 deduction:=amount*(case when spending>=100000 then 10 when spending>=30000 then 5 else 0 end)/100;
 return jsonb_build_object('environment','test','provider','simulator','kind',p_kind,'source_id',p_id,'subtotal',amount,'discount',deduction,'total',amount-deduction,'points',(amount-deduction)/100,'items',lines,'expires_at',deadline);
end $$;

create function private.test_payment_action(p_action text,p_data jsonb) returns jsonb
language plpgsql set search_path=public,pg_temp as $$
declare me profiles; owner profiles; pay test_payments; q jsonb; chosen_kind text; sid uuid; bid uuid; outcome text; allowed boolean;
begin
 select * into me from profiles where id=auth.uid() and active;
 if me.id is null then raise exception 'An active CTFLY account is required.'; end if;
 if p_action in ('test_quote','test_checkout') then
  if me.role<>'customer' then raise exception 'Use a customer account for test checkout.'; end if;
  chosen_kind:=p_data->>'kind'; sid:=(p_data->>'id')::uuid;
  q:=private.test_quote(chosen_kind,sid,me.id);
  if p_action='test_quote' then return q||jsonb_build_object('message','Test mode. No money will be charged.'); end if;
  if coalesce(p_data->>'method','') not in ('TNG','FPX','Card') then raise exception 'Choose a test payment method.'; end if;
  if (p_data->>'expected_total')::int is distinct from (q->>'total')::int then raise exception 'Amount changed. Reload checkout.'; end if;
  select * into pay from test_payments where test_payments.kind=chosen_kind and source_id=sid and user_id=me.id and status in ('Pending','Processing') for update;
  if pay.id is null then
   insert into test_payments(user_id,kind,source_id,method,subtotal,discount,total,points,items,expires_at)
   values(me.id,chosen_kind,sid,p_data->>'method',(q->>'subtotal')::int,(q->>'discount')::int,(q->>'total')::int,(q->>'points')::int,q->'items',(q->>'expires_at')::timestamptz) returning * into pay;
  end if;
 else
  select * into pay from test_payments where id=(p_data->>'id')::uuid;
  if pay.id is null or (pay.user_id<>me.id and not(p_action='test_refund' and me.role in ('staff','admin'))) then raise exception 'Test payment not found.'; end if;
  select * into owner from profiles where id=pay.user_id for update;
  -- Lock source first, including terminal rows, to serialize counter collection and refunds.
  if pay.kind='Booking' then perform 1 from bookings where id=pay.source_id for update;
  elsif pay.kind='Order' then perform 1 from orders where id=pay.source_id for update;
  else perform 1 from registrations where id=pay.source_id for update;
   perform 1 from events where id=(select event_id from registrations where id=pay.source_id) for update; end if;
  select * into pay from test_payments where id=pay.id for update;
  if p_action='test_refund' then
   if pay.status='Refunded' then return jsonb_build_object('payment',to_jsonb(pay),'message','Test payment already refunded.'); end if;
   if pay.status<>'Succeeded' then raise exception 'Only successful test payments can be refunded.'; end if;
   if pay.kind='Booking' then update bookings set paid=false,status='Cancelled' where id=pay.source_id and paid and status='Confirmed' and starts_at>now() and not exists(select 1 from booking_extensions where booking_id=pay.source_id); allowed:=found;
   elsif pay.kind='Order' then update orders set paid=false,status='Cancelled' where id=pay.source_id and paid and status='Pending'; allowed:=found;
    if allowed then perform private.restore_test_order(pay.source_id); end if;
   else update registrations set paid=false,status='Withdrawn' where id=pay.source_id and paid and status='Registered' and exists(select 1 from events where id=registrations.event_id and status='Open' and starts_at>now()); allowed:=found;
   end if;
   if not allowed then raise exception 'This service has already started or been fulfilled. Test refund unavailable.'; end if;
   update profiles set test_points=test_points-pay.points,test_spent=test_spent-pay.total where id=pay.user_id;
   update bills set status='Void',void_reason='Simulated refund. No real money moved.' where id=pay.bill_id and environment='test';
   update test_payments set status='Refunded',settled_at=now() where id=pay.id returning * into pay;
  elsif p_action='test_resolve' then
   outcome:=p_data->>'outcome';
   if coalesce(outcome,'') not in ('Success','Failure','Pending','Cancel') then raise exception 'Choose a simulator outcome.'; end if;
   if pay.status in ('Pending','Processing') then
    q:=private.test_quote(pay.kind,pay.source_id,pay.user_id);
    if pay.expires_at<=now() then raise exception 'Checkout hold expired.'; end if;
    if outcome='Success' then
     if (q->>'subtotal')::int<>pay.subtotal then raise exception 'Amount changed. Cancel and restart checkout.'; end if;
     insert into bills(user_id,kind,source_id,subtotal,discount,total,points,method,environment,payment_reference)
      values(pay.user_id,pay.kind,pay.source_id,pay.subtotal,pay.discount,pay.total,pay.points,'Test '||pay.method,'test',pay.reference) returning id into bid;
     update profiles set test_points=test_points+pay.points,test_spent=test_spent+pay.total where id=pay.user_id;
     if pay.kind='Booking' then update bookings set paid=true,status='Confirmed' where id=pay.source_id;
     elsif pay.kind='Order' then update orders set paid=true where id=pay.source_id;
     else update registrations set paid=true where id=pay.source_id; end if;
     update test_payments set status='Succeeded',bill_id=bid,settled_at=now() where id=pay.id returning * into pay;
    else update test_payments set status=case outcome when 'Failure' then 'Failed' when 'Cancel' then 'Cancelled' else 'Processing' end where id=pay.id returning * into pay;
    end if;
   end if;
  else raise exception 'Unknown test payment action.'; end if;
 end if;
 return jsonb_build_object('payment',to_jsonb(pay),'message','Test mode. No money was charged.','receipt',(select to_jsonb(b) from bills b where b.id=pay.bill_id));
end $$;
revoke all on all functions in schema private from public,anon,authenticated;

-- Keep the existing authenticated API and its request deduplication.
do $$ declare definition text; begin
 definition:=pg_get_functiondef('public.expire_bookings()'::regprocedure);
 if position('language sql' in lower(definition))=0 then raise exception 'Unexpected expiry function.'; end if;
 definition:=replace(definition,'AS $function$', 'AS $function$'||chr(10)||' select private.expire_test_holds();');
 execute definition;
 definition:=pg_get_functiondef('public.ctfly_action(text,jsonb,uuid)'::regprocedure);
 if position('if p_action=''feedback'' then' in definition)=0 then raise exception 'Apply feedback migration first.'; end if;
 definition:=replace(definition,'if p_action=''feedback'' then', 'if p_action in (''test_quote'',''test_checkout'',''test_resolve'',''test_refund'') then result:=private.test_payment_action(p_action,p_data); elsif p_action=''feedback'' then');
 definition:=replace(definition,'select * into target from profiles where id=invoice.user_id for update;', 'if invoice.environment=''test'' then raise exception ''Use the test refund action for simulated payments.''; end if; select * into target from profiles where id=invoice.user_id for update;');
 -- Re-registration receives a fresh hold; event result supplies its source ID.
 definition:=replace(definition,'fee=excluded.fee where registrations.status=''Withdrawn''','fee=excluded.fee,hold_expires_at=now()+interval ''10 minutes'' where registrations.status=''Withdrawn''');
 definition:=replace(definition,'if not found then raise exception ''You are already registered.''; end if;', 'if not found then raise exception ''You are already registered.''; end if; select id into oid from registrations where event_id=ev.id and user_id=me.id; result:=jsonb_build_object(''id'',oid,''message'',''Registered. Complete test checkout.'');');
 execute definition;
 definition:=pg_get_functiondef('public.ctfly_snapshot()'::regprocedure);
 definition:=replace(definition,'return result;', $patch$
 result:=result||jsonb_build_object('payment_mode','simulation','test_payments',coalesce((select jsonb_agg(x order by x.created_at desc) from
 (select t.*,p.name as customer_name,(select to_jsonb(b) from bills b where b.id=t.bill_id) as receipt from test_payments t join profiles p on p.id=t.user_id where t.user_id=me.id or crew order by t.created_at desc limit 300) x),'[]'::jsonb));
 return result;$patch$);
 execute definition;
end $$;
commit;
