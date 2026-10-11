-- CTFLY campus expansion. Run AFTER 001_ctfly.sql in Supabase SQL Editor.
-- Safe to run again. Existing resource IDs, reservations, payments and accounts are retained.
begin;
alter table public.resources drop constraint if exists resources_kind_check;
alter table public.resources add constraint resources_kind_check
 check(kind in ('PC','Snooker','Pool','Console','Racing','Arcade','GoKart'));

-- Preserve the existing transaction/authorization function and update only its duration guard.
-- Kart rides support short sessions; the original PC and billiards duration rules remain.
do $$
declare definition text; original_guard text:='n not in (60,90,120,180)';
 replacement_guard text:='((r.kind=''GoKart'' and n not in (10,15,30,60)) or (r.kind=''Arcade'' and n not in (30,60,90,120)) or (r.kind not in (''GoKart'',''Arcade'') and n not in (60,90,120,180)))';
begin
 definition:=pg_get_functiondef('public.ctfly_action(text,jsonb,uuid)'::regprocedure);
 if position(replacement_guard in definition)>0 then null;
 elsif position(original_guard in definition)>0 then
  execute replace(definition,original_guard,replacement_guard);
 else raise exception 'Unexpected booking function version. Nothing changed; contact the developer.';
 end if;
end $$;

-- Give the original demo tables their new room names, without recreating their IDs.
update public.resources r set name='Pool Club 01' where r.name='Pool Social 01'
 and not exists(select 1 from public.resources where name='Pool Club 01');
update public.resources r set name='Pool Club 02' where r.name='Pool Social 02'
 and not exists(select 1 from public.resources where name='Pool Club 02');
do $$
declare i int; new_name text;
begin
 for i in 13..16 loop
  new_name:='VIP Rig '||lpad((i-12)::text,2,'0');
  update public.resources r set name=new_name where r.name='PC-'||lpad(i::text,2,'0') and r.zone='VIP'
   and not exists(select 1 from public.resources where name=new_name);
 end loop;
end $$;

update public.resources set zone=case kind
 when 'Pool' then 'L2 Pool Club' when 'Snooker' then 'L2 Snooker Hall'
 when 'Console' then 'G Console Suites' when 'Racing' then 'G Simulator Lab'
 when 'Arcade' then 'G Arcade' when 'GoKart' then 'Outdoor Circuit' else zone end;
update public.resources set description=case
 when kind='PC' and zone='VIP' then 'L1 · VIP acoustic room · premium rig · 240 Hz'
 when kind='PC' then 'L1 · dedicated esports floor · mechanical keyboard · gaming headset'
 when kind='Pool' then 'L2 · Pool Club · professional blue-cloth table · cues included'
 when kind='Snooker' then 'L2 · Snooker Hall · full-size green-cloth table · cues included'
 when kind='Console' then 'G · private console suite · PS5 · couch co-op · 4 controllers'
 when kind='Racing' then 'G · Simulator Lab · wheel & pedals · racing cockpit'
 else description end;

-- Top up each physical room to its target. Maintenance units count as existing equipment.
-- Custom equipment beyond these targets is retained, and re-running adds no duplicates.
do $$
declare room record; existing_count int; serial int; candidate text;
begin
 for room in select * from (values
  ('PC','Standard',24,1,600,'PC-','L1 · Esports arena · gaming rig · keyboard & headset'),
  ('PC','VIP',8,1,1000,'VIP Rig ','L1 · VIP acoustic room · premium rig · 240 Hz'),
  ('Pool','L2 Pool Club',8,4,1800,'Pool Club ','L2 · professional blue-cloth table · cues included'),
  ('Snooker','L2 Snooker Hall',4,4,2200,'Snooker Club ','L2 · full-size table · dedicated cue clearance'),
  ('Console','G Console Suites',4,4,2500,'Console Suite ','G · private suite · PS5 · four controllers'),
  ('Racing','G Simulator Lab',4,1,2000,'Racing Rig ','G · wheel, pedals & cockpit · solo time trials'),
  ('Arcade','G Arcade',6,1,800,'Arcade Station ','G · arcade cabinet · reserved play station'),
  ('GoKart','Outdoor Circuit',6,1,6000,'Kart ','Outdoor · single-driver kart · arrive at the pit lane')
 ) as x(kind,zone,target,capacity,rate,prefix,description) loop
  select count(*) into existing_count from public.resources
   where kind=room.kind and zone=room.zone and status<>'Retired';
  serial:=1;
  while existing_count<room.target loop
   candidate:=room.prefix||lpad(serial::text,2,'0');
   serial:=serial+1;
   if exists(select 1 from public.resources where name=candidate) then continue; end if;
   insert into public.resources(name,kind,zone,description,capacity,rate)
    values(candidate,room.kind,room.zone,room.description,room.capacity,room.rate);
   existing_count:=existing_count+1;
  end loop;
 end loop;
end $$;
commit;

-- Fresh original demo inventory becomes 64 total facilities:
-- 24 standard PCs + 8 VIP PCs + 8 pool + 4 snooker + 4 console + 4 racing + 6 arcade + 6 karts.
select kind,zone,count(*) as facilities from public.resources
 where status<>'Retired' group by kind,zone order by kind,zone;
