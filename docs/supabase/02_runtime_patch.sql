alter table public.users
    add column if not exists updated_at timestamptz not null default timezone('utc', now());

alter table public.flashcards
    add column if not exists audio_url text,
    add column if not exists updated_at timestamptz not null default timezone('utc', now());

alter table public.qa_questions
    add column if not exists updated_at timestamptz not null default timezone('utc', now());

alter table public.qa_answers
    add column if not exists parent_answer_id uuid references public.qa_answers(id) on delete cascade,
    add column if not exists updated_at timestamptz not null default timezone('utc', now());

create table if not exists public.qa_question_votes (
    id uuid primary key default gen_random_uuid(),
    question_id uuid not null references public.qa_questions(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    unique (question_id, user_id)
);

create table if not exists public.qa_answer_votes (
    id uuid primary key default gen_random_uuid(),
    answer_id uuid not null references public.qa_answers(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    unique (answer_id, user_id)
);

create unique index if not exists users_email_unique_ci
    on public.users (lower(email));

create index if not exists flashcards_user_idx
    on public.flashcards (user_id, created_at desc);

create index if not exists qa_questions_user_idx
    on public.qa_questions (user_id, created_at desc);

create index if not exists qa_answers_parent_idx
    on public.qa_answers (parent_answer_id, created_at asc);

create index if not exists qa_answers_user_idx
    on public.qa_answers (user_id, created_at desc);

create index if not exists qa_question_votes_question_idx
    on public.qa_question_votes (question_id, created_at desc);

create index if not exists qa_question_votes_user_idx
    on public.qa_question_votes (user_id, created_at desc);

create index if not exists qa_answer_votes_answer_idx
    on public.qa_answer_votes (answer_id, created_at desc);

create index if not exists qa_answer_votes_user_idx
    on public.qa_answer_votes (user_id, created_at desc);

alter table public.qa_question_votes enable row level security;
alter table public.qa_answer_votes enable row level security;

drop policy if exists "public can read question votes" on public.qa_question_votes;
create policy "public can read question votes"
on public.qa_question_votes
for select
to anon, authenticated
using (true);

drop policy if exists "users can manage own question votes" on public.qa_question_votes;
create policy "users can manage own question votes"
on public.qa_question_votes
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "public can read answer votes" on public.qa_answer_votes;
create policy "public can read answer votes"
on public.qa_answer_votes
for select
to anon, authenticated
using (true);

drop policy if exists "users can manage own answer votes" on public.qa_answer_votes;
create policy "users can manage own answer votes"
on public.qa_answer_votes
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = timezone('utc', now());
    return new;
end;
$$;

drop trigger if exists users_touch_updated_at on public.users;
create trigger users_touch_updated_at
before update on public.users
for each row execute function public.touch_updated_at();

drop trigger if exists flashcards_touch_updated_at on public.flashcards;
create trigger flashcards_touch_updated_at
before update on public.flashcards
for each row execute function public.touch_updated_at();

drop trigger if exists qa_questions_touch_updated_at on public.qa_questions;
create trigger qa_questions_touch_updated_at
before update on public.qa_questions
for each row execute function public.touch_updated_at();

drop trigger if exists qa_answers_touch_updated_at on public.qa_answers;
create trigger qa_answers_touch_updated_at
before update on public.qa_answers
for each row execute function public.touch_updated_at();

create or replace function public.sync_question_answer_state()
returns trigger
language plpgsql
as $$
declare
    target_question_id uuid;
    has_root_answer boolean;
    has_accepted_answer boolean;
begin
    if tg_op = 'DELETE' then
        target_question_id := old.question_id;
    else
        target_question_id := new.question_id;
    end if;

    select exists (
        select 1
        from public.qa_answers
        where question_id = target_question_id
          and parent_answer_id is null
    ) into has_root_answer;

    select exists (
        select 1
        from public.qa_answers
        where question_id = target_question_id
          and is_accepted = true
    ) into has_accepted_answer;

    update public.qa_questions
    set is_answered = has_root_answer or has_accepted_answer,
        updated_at = timezone('utc', now())
    where id = target_question_id;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

drop trigger if exists qa_answers_sync_question_state_ins on public.qa_answers;
create trigger qa_answers_sync_question_state_ins
after insert on public.qa_answers
for each row execute function public.sync_question_answer_state();

drop trigger if exists qa_answers_sync_question_state_upd on public.qa_answers;
create trigger qa_answers_sync_question_state_upd
after update on public.qa_answers
for each row execute function public.sync_question_answer_state();

drop trigger if exists qa_answers_sync_question_state_del on public.qa_answers;
create trigger qa_answers_sync_question_state_del
after delete on public.qa_answers
for each row execute function public.sync_question_answer_state();
