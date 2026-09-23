# ULTRAVYX Revenue Leak Detector

A local, presentation-focused web application for finding follow-up gaps in a lead pipeline. Upload a CSV, see the funnel and recorded revenue, open a gap to inspect affected leads, and export that lead list. The included [sample-leads.csv](sample-leads.csv) contains 500 fictional records, so a presenter does not need real customer data.

## What is in this MVP

- An Angular web interface with Upload, Dashboard, Process Gaps, and Leads views.
- A Spring Boot API that validates and imports CSV rows, calculates funnel and leak results, and exports affected leads.
- PostgreSQL for persistent lead data. Flyway creates the database tables when the backend starts.
- Search, filters, paging, filtered CSV export, progress and error messages, and an INR display for **recorded revenue**.
- An **Add lead** form in the Leads view for entering a single record, with field validation and duplicate-ID feedback.

The presentation flow is deliberately simple: **upload the sample CSV → inspect the dashboard → open each process gap → review affected leads → export a CSV**. There is no separate “Load Demo Data” button.

## Prerequisites

- JDK 21
- Node.js 24.19.0 (see `.nvmrc`) and npm; Angular 22 requires Node 22.22.3+, 24.15.0+, or 26+
- Docker Desktop with Compose **or** a native PostgreSQL server

The repository supplies a Maven Wrapper and uses the project-local Angular CLI; a global Maven or Angular installation is not required. The PostgreSQL container is the only service run by Compose. Backend and frontend run on your computer for easy development.

## Start locally

### Prepared Windows workspace

This workspace now has project-local Java 21 and PostgreSQL binaries in the sibling `../.local-runtime` folder. From this folder, run:

```powershell
.\start-local.ps1
```

The script starts PostgreSQL, the backend, and the frontend, and checks that the API responds through the frontend before reporting success. Open [http://127.0.0.1:4200/upload](http://127.0.0.1:4200/upload). Run `.\start-local.ps1 -Build` after backend changes to test, rebuild, and restart the API. Run `.\stop-local.ps1` to stop these services while preserving imported leads. Database files are in `../.local-runtime/pgdata`; service logs are in `../.local-runtime/logs`. Keep that database directory to retain your data. This local setup does not install Windows services or change the system PATH.

If you copy only the source folder to another computer, use the standard setup below; the ignored runtime binaries are not included in source control.

### Standard setup

Open a terminal in this folder. In three terminals, run:

| Terminal | Windows PowerShell | macOS / Linux |
| --- | --- | --- |
| PostgreSQL | `docker compose up -d` | `docker compose up -d` |
| Backend | `cd backend; .\mvnw.cmd spring-boot:run` | `cd backend && ./mvnw spring-boot:run` |
| Frontend | `cd frontend; npm install; npm start` | `cd frontend && npm install && npm start` |

Then open [http://localhost:4200](http://localhost:4200). The backend listens on [http://localhost:8080](http://localhost:8080). On the first backend start, Flyway applies the schema and the app creates a default organization. You do not need to create a user account for this version.

The local Compose database is `ultravyx` with user `ultravyx`, password `ultravyx_dev`, and port `5432`. These are development-only credentials. To use native PostgreSQL instead, create that database and user or set `DB_URL`, `DB_USER`, and `DB_PASSWORD` in the backend terminal before starting it. For example, `DB_URL=jdbc:postgresql://localhost:5432/ultravyx`. Both `http://localhost:4200` and `http://127.0.0.1:4200` are accepted frontend origins. Set `CORS_ORIGIN` to a comma-separated list to use other frontend addresses.

Stop the database with `docker compose down`. The named volume preserves imported data across stops. To start with an empty database later, remove the Compose volume intentionally; this deletes all imported leads.

## Present the application

1. Open **Upload** and select the included `sample-leads.csv`. It has 500 fictional leads and covers every status and all five gap types.
2. Read the import result. Valid rows are saved even if other rows have errors; rejected rows are shown individually.
3. Open **Dashboard**. The KPIs distinguish all leads, customers, recorded revenue, distinct affected leads, and total gap flags. One lead can have several flags.
4. Open **Process Gaps**, select each gap card, and inspect the filtered lead list. Export affected leads as CSV.
5. Open **Leads** to search and filter the full list. Choose **Export CSV** to download every matching lead across all pages, in the selected sort order. With no filters, this exports the full pipeline. Refresh the browser to confirm the records remain in PostgreSQL.

The numbers in the earlier wireframe were illustrative; this application calculates its numbers from the imported file. Re-uploading the same file updates matching external lead IDs instead of adding another 500 records.

## CSV format

You can also choose **All leads → Add lead** to enter one record manually. Lead ID, name, status, and creation time are required. Dates in the form use the browser's local timezone and are sent to the API in UTC. Leave revenue blank when it is unknown; entering `0` records a known zero. A successful save refreshes the list and opens the new lead's details, preserving your current filters. Use **Clear filters and sorting** to return to the full list. To update an existing lead, import a CSV with the same lead ID.

All eleven columns must be present. `lead_id`, `name`, `status`, and `created_at` require values. Other cells may be blank.

```csv
lead_id,name,status,created_at,contacted_at,assigned_to,source,campaign,appointment_at,attended_at,revenue
SAMPLE-0001,Aarav Mehta,QUALIFIED,2026-09-23T00:00:00Z,2026-09-23T00:12:00Z,,,,,,
```

Supported statuses: `NEW`, `CONTACTED`, `QUALIFIED`, `APPOINTMENT`, `ATTENDED`, `WON`, `LOST`, `NO_SHOW`. Timestamps must be ISO-8601 with an offset, such as `2026-09-22T09:30:00+05:30` or `2026-09-22T04:00:00Z`; the API normalizes them to UTC. Revenue is a decimal number without a currency symbol or thousands separators, for example `25000.00`. Blank revenue stays unknown, not a stored zero. The UI's currency symbol defaults to INR for presentation; the file itself does not declare a currency or perform conversion. Do not mix currencies in one upload.

`lead_id` is the external identifier and must be unique within the organization. A later upload with the same ID updates that lead. Manual creation with a duplicate external ID is rejected. Empty files and missing required columns fail as a whole; invalid data rows are reported while valid rows still import.

## What the five process gaps mean

| Gap | Detection rule | Severity |
| --- | --- | --- |
| Uncontacted | A `NEW` lead with no `contacted_at` at least 24 hours after creation | High |
| Unassigned | An open lead without `assigned_to` (excluding `WON` and `LOST`) | Medium |
| Slow response | First contact at least 60 minutes after creation | Medium |
| Qualified not progressed | Still `QUALIFIED` at least 7 days after lead creation | High |
| No-show | Status is `NO_SHOW` | High |

Thresholds are configured in `backend/src/main/resources/application.yml`. Qualified-not-progressed uses lead creation time as a proxy because the CSV has no qualification-entry timestamp or status history. It is therefore a signal to review, not a precise duration spent in the qualified stage.

Funnel counts are cumulative: Contacted includes `CONTACTED`, `QUALIFIED`, `APPOINTMENT`, `ATTENDED`, and `WON`; Qualified includes `QUALIFIED` and the later stages; Appointments includes `APPOINTMENT`, `ATTENDED`, `WON`, and `NO_SHOW`; Attended includes `ATTENDED` and `WON`; Customers includes `WON`. `LOST` is not assigned an inferred downstream stage because its last reached stage is unknown. Recorded revenue sums only values in the `revenue` column.

## API

The frontend uses the backend endpoints below. List responses use stable JSON paging metadata, not JPA entity or Spring Page serialization.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/leads/upload` | Multipart CSV import |
| GET | `/api/leads` | Search, status/source/gap filters, sorting and pagination |
| GET | `/api/leads/export` | Export all matching leads using search, status, source, gap and sort parameters |
| GET | `/api/leads/{id}` | One lead, including detected problems |
| POST | `/api/leads` | Manually add a lead |
| GET | `/api/dashboard/summary` | KPI totals and gap counts |
| GET | `/api/dashboard/funnel` | Cumulative funnel stages |
| GET | `/api/leaks` | Five gap summaries |
| GET | `/api/leaks/{type}/leads` | Paged affected leads |
| GET | `/api/leaks/{type}/export` | Affected-lead CSV download |
| GET | `/api/analytics/sources` | Source performance |
| GET | `/api/analytics/response-times` | Response-time buckets |

## Build and test

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd package
cd ..\frontend
npm install
npm test
npm run build
```

GitHub Actions runs backend unit and real-PostgreSQL integration tests, frontend tests, and the production frontend build on every push and pull request. You can run the database integration checks locally with `cd backend && bash mvnw -Ppg-integration verify`; this starts a temporary database and leaves your application data untouched.

On macOS/Linux, use `bash mvnw` in place of `.\mvnw.cmd`. When transferring this project from Windows, run `npm ci` inside `frontend` to reinstall platform-specific dependencies. Use `nvm use` from the project root if you manage Node with nvm.

The frontend has a local development proxy for `/api`. API checks that touch PostgreSQL need the database to be running.

If an upload reports **Cannot reach the API** (502/504), run the startup script or start PostgreSQL and the backend manually, then retry. An HTTP 403 on a local upload usually means the frontend's origin is missing from `CORS_ORIGIN`; restart the backend after changing that setting.

With the app running, `python scripts/smoke-test.py` runs the live API checks through the frontend proxy. Python 3.9+ is required only for this optional check. It imports the fictional 500-row sample twice (updating matching sample IDs), checks partial imports and validation, reconciles dashboard totals, and verifies paging, filters, sorting, all gap exports, and allowed origins. It does not delete any records. Use `--base http://127.0.0.1:4200` to specify a different running frontend address.

## MVP boundaries

This is a local demo, not a production CRM or financial reporting system. It has one default organization and no authentication, authorization, account management, CRM/ad integrations, background jobs, status history, or currency conversion. The optional demo login shown in the wireframe is deferred until after the core presentation flow works; a visual-only login would not protect the backend. Data comes from CSV, so detected gaps are only as accurate as its fields. Source and campaign are descriptive labels, not independently verified attribution.

For sharing work between ChatGPT/Codex accounts, keep the whole folder (including this README and sample CSV) on the same computer or transfer it with Git. The second account can inspect the project files but does not inherit the first account's conversation.
