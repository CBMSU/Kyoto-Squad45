-- PostgreSQL/Supabase: persistence used by the Java backend.
create table public.whatsapp_inbox (
  message_id text primary key,
  status text not null check (status in ('processing', 'failed', 'sent', 'uncertain')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.whatsapp_inbox enable row level security;
revoke all on public.whatsapp_inbox from anon, authenticated;
grant all on public.whatsapp_inbox to service_role;

-- Atomic claim: concurrent deliveries cannot both invoke the AI.
create function public.claim_whatsapp_message(p_message_id text)
returns boolean language sql security invoker set search_path = '' as $$
  with claimed as (
    insert into public.whatsapp_inbox (message_id, status)
    values (p_message_id, 'processing')
    on conflict (message_id) do update
      set status = 'processing', updated_at = now()
      where public.whatsapp_inbox.status = 'failed'
    returning message_id
  ) select exists(select 1 from claimed);
$$;
revoke all on function public.claim_whatsapp_message(text) from public, anon, authenticated;
grant execute on function public.claim_whatsapp_message(text) to service_role;
