-- PostgreSQL/Supabase: estado da conversa de cada morador.
create table if not exists public.sessoes_conversa (
  telefone text primary key,
  morador_id text not null,
  morador_nome text not null,
  etapa text not null,
  mediacao_ativa_id text,
  mensagem_pendente text,
  ultima_mensagem text
);

create table if not exists public.sessoes_participacoes (
  telefone text not null references public.sessoes_conversa (telefone) on delete cascade,
  ordem integer not null,
  mediacao_id text not null,
  papel text not null,
  resumo text not null,
  primary key (telefone, mediacao_id)
);

create table if not exists public.sessoes_dados (
  telefone text not null references public.sessoes_conversa (telefone) on delete cascade,
  chave text not null,
  valor text,
  primary key (telefone, chave)
);

alter table public.sessoes_conversa enable row level security;
alter table public.sessoes_participacoes enable row level security;
alter table public.sessoes_dados enable row level security;

revoke all on public.sessoes_conversa from anon, authenticated;
revoke all on public.sessoes_participacoes from anon, authenticated;
revoke all on public.sessoes_dados from anon, authenticated;

grant all on public.sessoes_conversa to service_role;
grant all on public.sessoes_participacoes to service_role;
grant all on public.sessoes_dados to service_role;
