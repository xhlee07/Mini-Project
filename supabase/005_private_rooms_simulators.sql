-- Run after 001 and 004. 1.7 private PC rooms and standard/VIP racing simulators.
-- Keeps existing IDs, bookings, prices already charged, tokens and payments.
-- Proposed whole-room hourly rates: solo RM15, duo RM28, squad RM52.
-- Capacity is installed PCs, not a per-person price. Three guests use a four-PC room.
begin;
alter table public.resources drop constraint if exists resources_kind_check;
alter table public.resources add constraint resources_kind_check
 check(kind in ('PC','VIPRoom','Snooker','Pool','Console','Racing','Arcade','GoKart'));
alter table public.resources drop constraint if exists resources_vip_room_capacity_check;
alter table public.resources add constraint resources_vip_room_capacity_check
 check(kind<>'VIPRoom' or capacity between 1 and 4);

-- Convert only the known seeded solo VIP rigs; do not retire or recreate booked equipment.
-- On rerun their kind is already VIPRoom, so manager-edited prices remain unchanged.
do $$
declare r record; slot int:=0; cap int; zone_name text; room_name text; number int;
begin
 for r in select * from public.resources where kind='PC' and zone='VIP'
   and capacity=1 and name ~ '^VIP Rig [0-9]{2}$' order by name,id loop
  slot:=slot+1;
  if slot>9 then continue; end if;
  cap:=case when slot<=3 then 1 when slot<=6 then 2 else 4 end;
  zone_name:=case cap when 1 then 'Solo' when 2 then 'Duo' else 'Squad' end;
  number:=(slot-1)%3+1;
  room_name:='VIP Room '||zone_name||' '||lpad(number::text,2,'0');
  if exists(select 1 from public.resources where name=room_name and id<>r.id) then
   raise exception 'Room name % already exists. No changes applied; resolve the name first.',room_name;
  end if;
  update public.resources set kind='VIPRoom',name=room_name,zone=zone_name,capacity=cap,
   rate=case cap when 1 then 1500 when 2 then 2800 else 5200 end,
   description='L1 · Private room · '||cap||' gaming PC(s) · price for the whole room'
   where id=r.id;
 end loop;
end $$;

-- Existing normal racing rigs remain the same bookable resource IDs and rates.
update public.resources set zone='Standard',description='G · Racing simulator · ultrawide monitor, wheel, pedals and aluminium cockpit'
 where kind='Racing' and zone='G Simulator Lab';

do $$
declare room record; existing_count int; serial int; candidate text;
begin
 for room in select * from (values
  ('VIPRoom','Solo',3,1,1500,'VIP Room Solo ','L1 · Private room · 1 gaming PC · whole-room hourly price'),
  ('VIPRoom','Duo',3,2,2800,'VIP Room Duo ','L1 · Private room · 2 gaming PCs · whole-room hourly price'),
  ('VIPRoom','Squad',3,4,5200,'VIP Room Squad ','L1 · Private room · 4 gaming PCs · whole-room hourly price'),
  ('Racing','Standard',4,1,2000,'Racing Simulator ','G · Standard simulator · ultrawide display, wheel, pedals and aluminium cockpit'),
  ('Racing','VIP',4,1,3500,'VIP Racing Simulator ','G · Private simulator booth · direct-drive wheel, shifter, pedals and bucket seat')
 ) as x(kind,zone,target,capacity,rate,prefix,description) loop
  select count(*) into existing_count from public.resources where kind=room.kind and zone=room.zone and status<>'Retired';
  serial:=1;
  while existing_count<room.target loop
   candidate:=room.prefix||lpad(serial::text,2,'0'); serial:=serial+1;
   if exists(select 1 from public.resources where name=candidate) then continue; end if;
   insert into public.resources(name,kind,zone,capacity,rate,description)
    values(candidate,room.kind,room.zone,room.capacity,room.rate,room.description);
   existing_count:=existing_count+1;
  end loop;
 end loop;
end $$;
commit;
select kind,zone,capacity,rate,count(*) from public.resources
 where kind in ('VIPRoom','Racing') and status<>'Retired' group by kind,zone,capacity,rate order by kind,capacity,zone;
