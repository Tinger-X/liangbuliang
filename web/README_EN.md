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
│   ├── download.js       # /download: count, then stream the APK from R2
│   ├── api/stats.js      # /api/stats: returns download stats & version
│   └── _lib/apk.js       # Shared: APK key & version parsing
├── _headers              # Security headers + /assets/* long cache
├── robots.txt / sitemap.xml
├── wrangler.jsonc        # Deploy config (contains D1 ID; gitignored)
└── wrangler.example.jsonc  # Example config (committed; placeholder D1 ID)
```

## Required Resources

- Cloudflare Pages project `liangbuliang` (domain `liangbuliang.tin.edu.kg`)
- D1 database `liangbuliang`, table `counters(key, value)`: `direct` / `github` / `github_updated_at`
- R2 bucket `liangbuliang`, object with fixed key `LiangBuLiang-latest.apk`

## Prerequisites

- Node.js + wrangler (`npm install -g wrangler`)
- Authenticated: `wrangler login`

## First-time Setup

1. Copy `wrangler.example.jsonc` to `wrangler.jsonc`.
2. Replace `d1_databases[].database_id` with the real D1 database ID.

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
wrangler r2 object put liangbuliang/LiangBuLiang-latest.apk \
  --file <path-to-new-apk> --remote \
  --content-type application/vnd.android.package-archive \
  --content-disposition 'attachment; filename="LiangBuLiang-v<version>.apk"'
```

After overwriting the fixed key `LiangBuLiang-latest.apk`, `/download` serves the new filename automatically, and the frontend version (badge / download subtitle / version tag) updates dynamically via `/api/stats`.

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
  - Direct: `/download` atomically increments D1 `direct`, then streams the APK from R2.
  - GitHub: `/api/stats` fetches the GitHub API server-side every 15 minutes and caches it in D1.
- `wrangler.jsonc` must keep `nodejs_compat` (R2 streaming requires `node:stream`).
