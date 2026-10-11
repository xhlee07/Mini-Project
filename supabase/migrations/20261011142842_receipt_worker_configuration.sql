begin;
do $$ declare def text; begin
 def:=pg_get_functiondef('private.receipt_worker(text,text,jsonb)'::regprocedure);
 if position('update private.mail_runtime set configured=coalesce((d->>''configured'')::boolean,false);' in def)=0 then raise exception 'Unexpected receipt worker definition';end if;
 def:=replace(def,'update private.mail_runtime set configured=coalesce((d->>''configured'')::boolean,false);','update private.mail_runtime set configured=coalesce((d->>''configured'')::boolean,false) where id=true;');
 execute def;
end $$;
commit;
