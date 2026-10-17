# teaz_pro_study
Teaz Study, at `https://study.teaz.pro`: turns lecture material into
interactive study pages.

1. Create a course, upload slides, scripts or photos of notes (PDF, PNG, JPEG, GIF, WebP).
2. Claude (Opus) splits the material into lectures.
3. Opening a lecture has Claude (Sonnet) write its study pages: explanations
   mixed with tasks that are checked right away (multiple choice, cloze gaps
   incl. calculations, tables such as simplex tableaus), all with LaTeX math.
4. Progress per page, lecture and course. A page counts once read (info
   pages) or once all its tasks are solved.
5. A chat per lecture answers questions about the material and can propose
   changes to the pages (more practice, other explanations), applied only
   when the learner clicks Apply.

More material can be added to a course any time; it becomes new lectures
after the existing ones.

Monorepo: `frontend/` (Vue 3 + TypeScript) and `backend/` (Spring Boot, `pro.teaz:study`).

Stack, conventions, style guide and color tokens shared by every Teaz app
live in **`teaz_pro`**: start with `docs/TECH_STACK.md` there. This
README only covers what is specific to this app.

## How it works

| Step | Model | Input | Output |
| ---- | ----- | ----- | ------ |
| Analysis (after each upload) | `claude-opus-5-5` | All not yet analyzed files, via the Files API | Lecture plan: titles, summaries, file + page ranges (structured output) |
| Pages (on first open of a lecture) | `claude-sonnet-5-5` | Only the lecture's page ranges, cut out of the PDFs (PDFBox) | Pages of blocks (structured output) |
| Chat (per message) | `claude-sonnet-5-5` | Lecture material (prompt-cached), current pages, chat history | Answer, optionally a `propose_page_changes` tool call |

Models are set in `application.properties` (`study.ai.*`). All calls stream,
use adaptive thinking and opt into server-side refusal fallbacks. Analysis
and page generation run as background jobs; the UI polls. A restart marks
running jobs as failed; they can be retried from the UI.

Content is German regardless of the material's language; the UI is English.

### Blocks (the task "sandbox")

Every page is a list of blocks. The backend defines what Claude may produce
(`ai/AiSchemas.java`), checks it (`ai/BlockValidator.java`: indices in range,
one answer per gap, full table rows; broken tasks are dropped), and the
frontend renders and checks answers (`components/blocks/`, `answers.ts`):

| Block | Use |
| ----- | --- |
| `text` | Explanations, Markdown + LaTeX |
| `multiple_choice` | One or several correct options |
| `cloze` | Text with gaps `{{1}}`, `{{2}}`; text answers (case-insensitive, list of accepted spellings) or numbers with tolerance |
| `table` | Given and fill-in cells, e.g. simplex tableaus, value tables |

Number inputs accept `3,5`, `3.5`, `−2`, `1/4`. A new block type needs a
schema in `AiSchemas`, a check in `BlockValidator` and a component in
`BlockView.vue`. Practice exercises (planned) are meant to reuse these blocks.

## Running locally

```sh
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

Or use the IntelliJ run configs in `.idea/runConfigurations/`. Locally:

- **No Docker, no database install:** the backend uses a file-based H2
  database in PostgreSQL mode (`backend/data/`, gitignored). Delete that
  folder for a fresh start.
- **No API key needed:** without `ANTHROPIC_API_KEY`, `MockStudyAi` plans
  one lecture per file (PDFs in chunks of 20 pages) and returns the same
  sample pages for every lecture, covering every block type. Asking the chat
  for "mehr Übungen" returns a sample proposal.
- To use Claude locally, set `ANTHROPIC_API_KEY` in the backend's environment
  (in IntelliJ: the run config's environment variables). This costs money.
- Auth is mocked on both sides; everything belongs to one local user.
- Backend port 8080 is also portal's and pulse's default: only one of them
  can run locally at a time.

## Shared UI

`TopBar`, `useTheme()` and the theme tokens come from **`teaz_pro_ui`**
(pinned in `frontend/package.json`), see `teaz_pro/docs/UI_LIBRARY.md`.

## Auth

- Frontend `frontend/src/auth/`: Keycloak (`.env.production`: `auth.teaz.pro`,
  realm `teazpro`, client `study`) in builds, mock user in `npm run dev`.
- Backend `backend/src/main/java/pro/teaz/study/security/`, with the `prod`
  profile: every request needs a Keycloak Bearer JWT **and the realm role
  `study-user`**, since every generated lecture costs money. Courses are
  private: users only ever see their own (`course/Access.java`).

Keycloak setup:

1. Client `study`, as in the Auth section of `teaz_pro/docs/TECH_STACK.md`.
2. **Realm roles → Create role** `study-user`, assign it to everyone who
   may use Study.

## Database

Study uses the shared Postgres server on the VPS (`/opt/teazpro/postgres`),
reached over the external Docker network `db`, with its **own database
`study` and own user `study`** (owner of that database, no superuser
rights). It never reads another app's database; other apps' data only via
their APIs.

- Schema: Flyway migrations in `backend/src/main/resources/db/migration`,
  applied on startup as the `study` user. Hibernate never changes the schema
  (`ddl-auto=none`). No extensions needed (UUIDs are generated in Java).
- Connection only from the environment: `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` in `.env`.
- Locally: H2 instead (see Running locally), no Postgres needed.

## Deployment

Once, on the Postgres server: create the user and database (step by step
in "First deploy" below). Then `.env` next to `compose.yml` (gitignored):

```sh
ANTHROPIC_API_KEY=sk-ant-...
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/study
SPRING_DATASOURCE_USERNAME=study
SPRING_DATASOURCE_PASSWORD=<the password given to the study role>
```

Then, as for every app:

```sh
git pull && docker compose up -d --build
```

`compose.yml` routes `study.teaz.pro` to the frontend (nginx) and
`study.teaz.pro/api/*` to the backend, via the shared Traefik (network
`proxy`, cert resolver `le`). The backend also joins the external network
`db` for Postgres. Uploads live in the volume `files`; back it up together
with the `study` database, which holds all generated pages and progress.

Limits: up to 100 MB per file; one upload batch is analyzed in one request,
so at most 550 PDF pages and 90 images per batch (larger material: upload in
parts).

### First deploy: database on the shared Postgres server

Run in `/opt/teazpro/postgres`. Replace `postgres` (the compose service) and
`-U postgres` (the server's admin user, `POSTGRES_USER` of that container) if
they differ there.

1. Check the service name and that the network exists:
   `docker compose ps` and `docker network inspect db`.
2. Create the user (use a long random password, e.g. from `openssl rand -hex 24`):
   `docker compose exec postgres psql -U postgres -c "CREATE ROLE study WITH LOGIN PASSWORD '<password>';"`
3. Create the database, owned by that user:
   `docker compose exec postgres psql -U postgres -c "CREATE DATABASE study OWNER study;"`
4. Keep other apps' users out of it:
   `docker compose exec postgres psql -U postgres -c "REVOKE ALL ON DATABASE study FROM PUBLIC;"`
5. Check that the user can log in over the network with its password:
   `docker compose exec postgres psql "postgresql://study:<password>@localhost:5432/study" -c "SELECT current_user, current_database();"`

Flyway creates the tables on the app's first start.
