-- Hosted Supabase: run after 001_ctfly.sql. Ends sessions even when no APK is open.
create extension if not exists pg_cron;
select cron.schedule('ctfly-session-clock','* * * * *', 'select public.expire_bookings()');
-- Inspect status in Supabase Integrations / Cron, or:
-- select * from cron.job where jobname='ctfly-session-clock';
-- Booking actions and snapshot refresh also expire sessions immediately on access.
