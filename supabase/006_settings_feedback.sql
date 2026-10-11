-- Run once after existing CTFLY migrations. Safe to rerun; preserves all feedback.
begin;
create table if not exists public.app_feedback (
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references public.profiles(id),
 title text not null check(char_length(btrim(title)) between 3 and 120),
 body text not null check(char_length(btrim(body)) between 10 and 2000),
 category text not null check(category in ('Issue','Suggestion','Other')),
 rating int not null check(rating between 1 and 5),
 status text not null default 'New' check(status in ('New','Reviewed','Resolved')),
 created_at timestamptz not null default now(),
 updated_at timestamptz,
 updated_by uuid references public.profiles(id)
);
create index if not exists app_feedback_created_idx on public.app_feedback(created_at desc);
create index if not exists app_feedback_user_idx on public.app_feedback(user_id,created_at desc);
alter table public.app_feedback enable row level security;
revoke all on public.app_feedback from public,anon,authenticated;

-- Extend the existing authenticated snapshot; customer sees only their own submissions.
do $$
declare definition text; addition text := $patch$
 -- app_feedback_snapshot_v1
 result := result || jsonb_build_object('feedback',coalesce((select jsonb_agg(f order by f.created_at desc)
 from (select a.*,p.name as customer_name from app_feedback a join profiles p on p.id=a.user_id
 where a.user_id=me.id or crew order by a.created_at desc limit 300) f),'[]'::jsonb));
 return result;$patch$;
begin
 definition:=pg_get_functiondef('public.ctfly_snapshot()'::regprocedure);
 if position('-- app_feedback_snapshot_v1' in definition)=0 then
  if position('return result;' in definition)=0 then raise exception 'Unexpected snapshot version; no changes applied.'; end if;
  execute replace(definition,'return result;',addition);
 end if;
end $$;

-- Reuse the existing per-account transaction lock and idempotent request IDs.
do $$
declare definition text; addition text := $patch$
 -- app_feedback_actions_v1
 if p_action='feedback' then
  if me.role<>'customer' then raise exception 'Use a customer account to send feedback.'; end if;
  if coalesce(char_length(btrim(p_data->>'title')),0) not between 3 and 120
   or coalesce(char_length(btrim(p_data->>'body')),0) not between 10 and 2000
   or coalesce(p_data->>'category','') not in ('Issue','Suggestion','Other')
   or coalesce((p_data->>'rating')::int,0) not between 1 and 5 then
   raise exception 'Choose a category and rating, a title of 3-120 characters, and feedback of 10-2000 characters.';
  end if;
  insert into app_feedback(user_id,title,body,category,rating)
   values(me.id,btrim(p_data->>'title'),btrim(p_data->>'body'),p_data->>'category',(p_data->>'rating')::int)
   returning id into oid;
  result:=jsonb_build_object('message','Feedback sent. Thank you!','id',oid);
 elsif p_action='feedback_status' then
  if me.role not in ('staff','admin') then raise exception 'Staff access required.'; end if;
  if coalesce(p_data->>'status','') not in ('Reviewed','Resolved') then raise exception 'Choose Reviewed or Resolved.'; end if;
  update app_feedback set status=p_data->>'status',updated_at=now(),updated_by=me.id
   where id=(p_data->>'id')::uuid;
  if not found then raise exception 'Feedback no longer exists.'; end if;
  result:=jsonb_build_object('message','Feedback status updated.');
 elsif p_action='book' then$patch$;
begin
 definition:=pg_get_functiondef('public.ctfly_action(text,jsonb,uuid)'::regprocedure);
 if position('-- app_feedback_actions_v1' in definition)=0 then
  if position('if p_action=''book'' then' in definition)=0 then raise exception 'Unexpected action version; no changes applied.'; end if;
  execute replace(definition,'if p_action=''book'' then',addition);
 end if;
end $$;
commit;
