[中文](./README.md)

# LiangBuLiang Website (web/)

Official website for LiangBuLiang: a static site with Cloudflare Pages Functions.

Source repo: https://github.com/Tinger-X/liangbuliang

## Directory Structure

```
web/
├── index.html            # Single-page home
├── assets/
│   ├── styles.css        # Styles
│   ├── script.js         # Interactions (i18n / theme / demo / download count & version)
│   ├── logo.png          # App icon
│   └── favicon.svg
├── functions/            # Pages Functions
│   ├── download.js       # /download: count, then stream the artifact from R2
│   ├── api/stats.js      # /api/stats: returns download stats & version
│   └── _lib/
│       ├── site.js       # ★ Site config (app id / GitHub repo / file type)
│       └── artifact.js   # Artifact key & version parsing
├── schema.sql            # Shared D1 schema (account-level; run once)
├── _headers              # Security headers + /assets/* long cache
├── robots.txt / sitemap.xml
├── wrangler.jsonc        # Deploy config (committed)
```

## Required Resources (account-level, shared by every software site)

| Resource | Name | Purpose |
|---|---|---|
| Pages | `liangbuliang` (domain `liangbuliang.tin.edu.kg`) | This site |
| D1 | `softwares`, table `counters(app, key, value)` | Download counters for all software, isolated by the `app` column |
| R2 | `softwares`, key `<app>/latest.<ext>` | Release artifacts for all software, isolated by key prefix |

What this project occupies:

- D1: rows with `app = 'liangbuliang'` — `direct` / `github` / `github_updated_at`
- R2: `liangbuliang/latest.apk`

> **Do not create a new database or bucket for this project.** The shared resources are
> reused account-wide, and `schema.sql` only needs to be run once.

## Prerequisites

- Node.js + wrangler (`npm install -g wrangler`)
- Authenticated: `wrangler login`

## First-time Setup

`wrangler.jsonc` is in the repository with the shared `database_id` / `bucket_name` already
filled in. Normally there is nothing to change: clone it, `wrangler login`, and go. Only when
adding a **new software** do you set `name` to your Pages project name.

> It is committed because there is nothing in it worth hiding. The `database_id` is only a
> resource identifier — on its own it grants access to nothing (that needs account
> credentials), and the bucket name already ships inside the deployed artifact anyway. Keeping
> a separate "example" for everyone to copy just means the two copies drift.

## Local Development

Run from inside `web/`:

```bash
wrangler pages dev --port 8787
```

Open http://127.0.0.1:8787

> Local D1/R2 are local simulations (empty DB), so `/api/stats` returns 500;
> real data lives only after deployment.

## Deploy

Run from inside `web/` (wrangler reads `./wrangler.jsonc` and `./functions/`):

```bash
wrangler pages deploy --project-name=liangbuliang
```

## Update Guide

### Ship a new APK version

The version and filename live only in the R2 object's `Content-Disposition` metadata:

```bash
wrangler r2 object put softwares/liangbuliang/latest.apk \
  --file <path-to-new-apk> --remote \
  --content-type application/vnd.android.package-archive \
  --content-disposition 'attachment; filename="LiangBuLiang-v<version>.apk"'
```

After overwriting the fixed key `liangbuliang/latest.apk`, `/download` serves the new filename automatically, and the frontend version (badge / download subtitle / version tag) updates dynamically via `/api/stats`.

### Update js / css (cache busting)

`assets/*` is set to `Cache-Control: immutable` (one year) via `_headers`. After editing, you MUST bump the `?v=xxx` in `index.html`, otherwise users keep the old content:

```html
<link rel="stylesheet" href="/assets/styles.css?v=260821002" />
<script src="/assets/script.js?v=260821002"></script>
```

> Everything under `assets/` (including `logo.png` and `favicon.svg`) is immutable —
> any change requires bumping the version or renaming the file.

## Key Implementation Notes

- **Download count**: `total = direct site downloads (D1) + GitHub release downloads (GitHub API)`
  - Direct: `/download` atomically increments this app's `direct` in D1, then streams the artifact from R2.
  - GitHub: `/api/stats` fetches the GitHub API server-side every 15 minutes and caches it in D1.
- Every software shares one D1 database and one R2 bucket, isolated by the `counters.app` column and the `<app>/` key prefix.
- `wrangler.jsonc` must keep `nodejs_compat` (R2 streaming requires `node:stream`).

## Adding a New Software

The whole backend (download counting + version + GitHub stats) lives in `functions/`, so onboarding a new software requires **no new Cloudflare database or bucket**.

### 1. Copy the directory

Create a new repo and copy `functions/`, `_headers` and `wrangler.jsonc` from this directory (`index.html` / `assets/` become the new landing page).

### 2. Edit `functions/_lib/site.js`

Only these fields:

```js
export const SITE = {
  app: 'newsoftware',                    // unique id: D1 app column + R2 key prefix
  githubRepo: 'Tinger-X/newsoftware',    // used to count release downloads
  namePrefix: 'NewSoftware-',            // filename prefix, used to parse the version
  ext: 'apk',                            // artifact extension: apk / exe / zip / dmg …
  contentType: 'application/vnd.android.package-archive',
};
```

> Never change `app` after launch — doing so loses historical counts and orphans the uploaded artifact.

### 3. Create the Pages project and deploy

```bash
# set name in wrangler.jsonc to the new project
wrangler pages deploy --project-name=newsoftware
```

Keep the shared `database_id` / `bucket_name` as-is. No D1 schema change is needed — the counter rows are created on first download.

### 4. Upload the first artifact

```bash
wrangler r2 object put softwares/newsoftware/latest.apk \
  --file <path> --remote \
  --content-type application/vnd.android.package-archive \
  --content-disposition 'attachment; filename="NewSoftware-v1.0.0.apk"'
```

`/api/stats` then returns `{ direct: 0, github: <n>, total: <n>, version: "v1.0.0" }`.

### Frontend wiring

The page only needs to call `/api/stats` for `total` and `version`, and point the download button at `/download` — see `loadStats()` in `assets/script.js`.
