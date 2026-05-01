create extension if not exists pgcrypto;

create table if not exists public.users (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null,
    name text not null default '',
    avatar_url text,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.flashcards (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    term text not null,
    definition text not null,
    category text not null,
    media_url text,
    media_type text not null default 'NONE',
    cloud_name text,
    public_id text,
    is_public boolean not null default true,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.bookmarks (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    flashcard_id uuid not null references public.flashcards(id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    unique (user_id, flashcard_id)
);

create table if not exists public.study_progress (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    flashcard_id uuid not null references public.flashcards(id) on delete cascade,
    review_state text not null default 'NEW',
    ease_factor real not null default 2.5,
    interval_days integer not null default 1,
    next_review_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    unique (user_id, flashcard_id)
);

create table if not exists public.qa_questions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    question text not null,
    category text not null,
    tags text[] not null default '{}',
    upvotes integer not null default 0,
    is_answered boolean not null default false,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.qa_answers (
    id uuid primary key default gen_random_uuid(),
    question_id uuid not null references public.qa_questions(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    content text not null,
    upvotes integer not null default 0,
    is_accepted boolean not null default false,
    created_at timestamptz not null default timezone('utc', now())
);

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

create unique index if not exists flashcards_term_unique_ci
    on public.flashcards (lower(term));

create unique index if not exists users_email_unique_ci
    on public.users (lower(email));

create index if not exists flashcards_public_created_idx
    on public.flashcards (is_public, created_at desc);

create index if not exists flashcards_search_term_idx
    on public.flashcards using gin (to_tsvector('simple', coalesce(term, '') || ' ' || coalesce(definition, '')));

create index if not exists flashcards_user_idx
    on public.flashcards (user_id, created_at desc);

create index if not exists qa_questions_created_idx
    on public.qa_questions (created_at desc);

create index if not exists qa_questions_user_idx
    on public.qa_questions (user_id, created_at desc);

create index if not exists qa_answers_question_idx
    on public.qa_answers (question_id, is_accepted desc, upvotes desc, created_at desc);

create index if not exists qa_answers_parent_idx
    on public.qa_answers (parent_answer_id, created_at asc);

create index if not exists qa_answers_user_idx
    on public.qa_answers (user_id, created_at desc);

create index if not exists bookmarks_user_idx
    on public.bookmarks (user_id, created_at desc);

create index if not exists study_progress_user_review_idx
    on public.study_progress (user_id, next_review_at asc);

create index if not exists qa_question_votes_question_idx
    on public.qa_question_votes (question_id, created_at desc);

create index if not exists qa_question_votes_user_idx
    on public.qa_question_votes (user_id, created_at desc);

create index if not exists qa_answer_votes_answer_idx
    on public.qa_answer_votes (answer_id, created_at desc);

create index if not exists qa_answer_votes_user_idx
    on public.qa_answer_votes (user_id, created_at desc);

alter table public.users enable row level security;
alter table public.flashcards enable row level security;
alter table public.bookmarks enable row level security;
alter table public.study_progress enable row level security;
alter table public.qa_questions enable row level security;
alter table public.qa_answers enable row level security;
alter table public.qa_question_votes enable row level security;
alter table public.qa_answer_votes enable row level security;

drop policy if exists "users can read own profile" on public.users;
create policy "users can read own profile"
on public.users
for select
to authenticated
using (auth.uid() = id);

drop policy if exists "users can insert own profile" on public.users;
create policy "users can insert own profile"
on public.users
for insert
to authenticated
with check (auth.uid() = id);

drop policy if exists "users can update own profile" on public.users;
create policy "users can update own profile"
on public.users
for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

drop policy if exists "public can read public flashcards" on public.flashcards;
create policy "public can read public flashcards"
on public.flashcards
for select
to anon, authenticated
using (is_public = true or auth.uid() = user_id);

drop policy if exists "authenticated users can insert flashcards" on public.flashcards;
create policy "authenticated users can insert flashcards"
on public.flashcards
for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "owners can update flashcards" on public.flashcards;
create policy "owners can update flashcards"
on public.flashcards
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "owners can delete flashcards" on public.flashcards;
create policy "owners can delete flashcards"
on public.flashcards
for delete
to authenticated
using (auth.uid() = user_id);

drop policy if exists "users can manage own bookmarks" on public.bookmarks;
create policy "users can manage own bookmarks"
on public.bookmarks
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "users can manage own study progress" on public.study_progress;
create policy "users can manage own study progress"
on public.study_progress
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "public can read questions" on public.qa_questions;
create policy "public can read questions"
on public.qa_questions
for select
to anon, authenticated
using (true);

drop policy if exists "authenticated users can insert questions" on public.qa_questions;
create policy "authenticated users can insert questions"
on public.qa_questions
for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "owners can update questions" on public.qa_questions;
create policy "owners can update questions"
on public.qa_questions
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "owners can delete questions" on public.qa_questions;
create policy "owners can delete questions"
on public.qa_questions
for delete
to authenticated
using (auth.uid() = user_id);

drop policy if exists "public can read answers" on public.qa_answers;
create policy "public can read answers"
on public.qa_answers
for select
to anon, authenticated
using (true);

drop policy if exists "authenticated users can insert answers" on public.qa_answers;
create policy "authenticated users can insert answers"
on public.qa_answers
for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "owners can update answers" on public.qa_answers;
create policy "owners can update answers"
on public.qa_answers
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "owners can delete answers" on public.qa_answers;
create policy "owners can delete answers"
on public.qa_answers
for delete
to authenticated
using (auth.uid() = user_id);

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
