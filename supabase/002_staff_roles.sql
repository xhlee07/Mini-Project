-- First register these people through CTFLY's Customer signup, including birthday.
-- Replace the example email below with YOUR registered email, then run.
-- This changes only an existing account's role. It does not create a password.
do $$
declare account_email text := 'REPLACE_WITH_YOUR_ADMIN_EMAIL'; target_id uuid;
begin
 select id into target_id from auth.users where lower(email)=lower(account_email);
 if target_id is null then raise exception 'Register this email in the app first, then replace the email in this SQL.'; end if;
 update public.profiles set role='admin' where id=target_id;
 if not found then raise exception 'Profile missing: register using the app after running 001_ctfly.sql.'; end if;
end $$;

-- To make a second registered account a staff member, run this separately after
-- replacing the email (remove the leading -- from the two SQL lines).
-- update public.profiles set role='staff'
-- where id=(select id from auth.users where lower(email)=lower('REPLACE_WITH_STAFF_EMAIL'));

-- Sign out in CTFLY, choose Staff / Admin, then sign in again.
