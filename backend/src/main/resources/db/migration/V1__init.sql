-- Portable between Postgres (prod) and H2 in PostgreSQL mode (local dev, tests).
-- JSON is stored as text: the app reads and writes it whole, never queries into it.

create table course (
    id uuid primary key,
    -- Keycloak user id (JWT "sub"); courses are private to their owner.
    owner_id varchar(255) not null,
    title varchar(500) not null,
    -- READY, ANALYZING, FAILED
    status varchar(32) not null,
    status_message text,
    created_at timestamp with time zone not null
);
create index course_owner_idx on course (owner_id);

create table source_file (
    id uuid primary key,
    course_id uuid not null references course (id) on delete cascade,
    original_name varchar(500) not null,
    media_type varchar(100) not null,
    size_bytes bigint not null,
    -- PDFs only
    page_count integer,
    storage_path varchar(1000) not null,
    -- Claude Files API id, set once uploaded there for the analysis.
    anthropic_file_id varchar(255),
    -- Already split into lectures; new uploads are analyzed on their own.
    analyzed boolean not null,
    uploaded_at timestamp with time zone not null
);
create index source_file_course_idx on source_file (course_id);

create table lecture (
    id uuid primary key,
    course_id uuid not null references course (id) on delete cascade,
    position integer not null,
    title varchar(500) not null,
    summary text not null,
    -- JSON list of {fileId, fromPage, toPage}: which material the lecture is made of.
    sources text not null,
    -- NOT_GENERATED, GENERATING, READY, FAILED
    pages_status varchar(32) not null,
    status_message text,
    created_at timestamp with time zone not null
);
create index lecture_course_idx on lecture (course_id);

create table lecture_page (
    id uuid primary key,
    lecture_id uuid not null references lecture (id) on delete cascade,
    position integer not null,
    title varchar(500) not null,
    -- JSON list of blocks (text, multiple_choice, cloze, table), see ai/pages-schema.json.
    blocks text not null,
    completed boolean not null
);
create index lecture_page_lecture_idx on lecture_page (lecture_id);

create table chat_message (
    id uuid primary key,
    lecture_id uuid not null references lecture (id) on delete cascade,
    -- USER, ASSISTANT
    role varchar(16) not null,
    text text not null,
    -- JSON {summary, operations} when the assistant proposed changes to the pages.
    proposal text,
    -- PENDING, APPLIED, DISCARDED
    proposal_status varchar(16),
    created_at timestamp with time zone not null
);
create index chat_message_lecture_idx on chat_message (lecture_id);
