## Supabase Migration Notes

This project is being migrated from Firebase and the custom backend API to direct Supabase access.

Current constraint:
- The provided `anon` key can read/write only after the database schema and RLS policies already exist.
- It cannot create tables, indexes, triggers, or policies.

What is included here:
- [01_schema.sql](/C:/Users/ahmed/Desktop/mom/EduSpecial-Android/docs/supabase/01_schema.sql): base schema, indexes, and RLS policies for the Android app.

Important implementation assumption:
- The Android app already uses `term` and `definition` in its Room entities and repositories.
- To avoid a broad rewrite, the Supabase `flashcards` table keeps `term` and `definition` instead of renaming them to `question` and `answer`.
- Q&A tables are included even though they were not in the initial JSON spec, because the current app has live Q&A screens and repositories.

Required to apply remotely:
- Supabase SQL editor access, or
- `service_role` / higher-privilege project access outside the app.
