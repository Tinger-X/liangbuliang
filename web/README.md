[English](./README_EN.md)

# 亮不亮官网（web/）

亮不亮（LiangBuLiang）官网：纯静态站点 + Cloudflare Pages Functions。

源码仓库：https://github.com/Tinger-X/liangbuliang

## 目录结构

```
web/
├── index.html            # 首页（单页）
├── assets/
│   ├── styles.css        # 样式
│   ├── script.js         # 交互（i18n / 主题 / 演示 / 下载计数与版本）
│   ├── logo.png          # 应用图标
│   └── favicon.svg
├── functions/            # Pages Functions
│   ├── download.js       # /download：计数后从 R2 流式返回发布物
│   ├── api/stats.js      # /api/stats：返回下载统计与版本
│   └── _lib/
│       ├── site.js       # ★ 站点配置（软件标识 / GitHub 仓库 / 文件类型）
│       └── artifact.js   # 发布物 key 与版本解析
├── schema.sql            # 共享 D1 建表语句（账号级，只需执行一次）
├── _headers              # 安全响应头 + /assets/* 长缓存
├── robots.txt / sitemap.xml
├── wrangler.jsonc        # 部署配置（含共享 D1 ID，已被 .gitignore 忽略）
└── wrangler.example.jsonc  # 配置示例（提交用）
```

## 依赖资源（账号级共享，所有软件官网共用）

| 资源 | 名称 | 用途 |
|---|---|---|
| Pages | `liangbuliang`（域名 `liangbuliang.tin.edu.kg`） | 本项目站点 |
| D1 | `softwares`，表 `counters(app, key, value)` | 所有软件的下载计数，按 `app` 列隔离 |
| R2 | `softwares`，key 为 `<app>/latest.<ext>` | 所有软件的发布物，按目录前缀隔离 |

本项目占用：

- D1：`app = 'liangbuliang'` 的行 —— `direct` / `github` / `github_updated_at`
- R2：`liangbuliang/latest.apk`

> **不要为本项目新建数据库或存储桶。** 共享资源由整个账号复用，`schema.sql` 也只需执行一次。

## 前置要求

- Node.js + wrangler（`npm install -g wrangler`）
- 已登录：`wrangler login`

## 首次配置

1. 复制 `wrangler.example.jsonc` 为 `wrangler.jsonc`。
2. 共享资源的 `database_id` / `bucket_name` 已经填好，通常无需改动；只有 `name` 改成你的 Pages 项目名。

## 本地开发

在 `web/` 目录内执行：

```bash
wrangler pages dev --port 8787
```

访问 http://127.0.0.1:8787

> 本地 D1/R2 是本地模拟（空库），`/api/stats` 会返回 500；真实数据以线上为准。

## 部署

在 `web/` 目录内执行（wrangler 会读取 `./wrangler.jsonc` 与 `./functions/`）：

```bash
wrangler pages deploy --project-name=liangbuliang
```

## 更新指引

### 发布新版本 APK

版本与文件名只存在 R2 对象的 `Content-Disposition` 元数据中：

```bash
wrangler r2 object put softwares/liangbuliang/latest.apk \
  --file <新 APK 路径> --remote \
  --content-type application/vnd.android.package-archive \
  --content-disposition 'attachment; filename="LiangBuLiang-v<版本>.apk"'
```

覆盖固定 key `liangbuliang/latest.apk` 后，`/download` 自动带出新文件名，前端版本号（badge / 下载副标题 / 版本标签）由 `/api/stats` 动态读取并更新。

### 更新 js / css（缓存刷新）

`assets/*` 被 `_headers` 设为 `Cache-Control: immutable`（一年）。更新后**必须**修改 `index.html` 里对应的 `?v=xxx`，否则用户拿不到新内容：

```html
<link rel="stylesheet" href="/assets/styles.css?v=260821002" />
<script src="/assets/script.js?v=260821002"></script>
```

> `assets/` 下所有文件（含 `logo.png`、`favicon.svg`）都是 immutable，
> 任何改动都需要改版本号或换文件名。

## 关键实现

- **下载计数**：`total = 本站直接下载(D1) + GitHub release 下载(GitHub API)`
  - 本站：`/download` 对 D1 中本软件的 `direct` 原子 +1，再从 R2 流式返回发布物。
  - GitHub：`/api/stats` 每 15 分钟在 Cloudflare 侧拉取一次 GitHub API，写入 D1 兜底。
- 所有软件共用一个 D1 / 一个 R2，靠 `counters.app` 列与 R2 的 `<app>/` 前缀隔离。
- `wrangler.jsonc` 需保留 `nodejs_compat`（R2 流式返回依赖 `node:stream`）。

## 接入新软件

整套后端能力（下载计数 + 版本号 + GitHub 统计）都在 `functions/` 里，接入新软件**不需要新建任何 Cloudflare 数据库或存储桶**。

### 1. 复制目录

新建仓库，把本目录的 `functions/`、`_headers`、`wrangler.example.jsonc` 复制过去（`index.html` / `assets/` 换成新软件的落地页）。

### 2. 改 `functions/_lib/site.js`

只需改这三项：

```js
export const SITE = {
  app: 'newsoftware',                    // 唯一标识，用于 D1 app 列 + R2 目录前缀
  githubRepo: 'Tinger-X/newsoftware',    // 用于统计 release 下载量
  namePrefix: 'NewSoftware-',            // 发布文件名前缀，用于解析版本号
  ext: 'apk',                            // 发布物扩展名：apk / exe / zip / dmg …
  contentType: 'application/vnd.android.package-archive',
};
```

> `app` 一旦上线不要改，改了会丢失历史计数、读不到已上传的发布物。

### 3. 建 Pages 项目并部署

```bash
# wrangler.example.jsonc → wrangler.jsonc，把 name 改成新项目名
wrangler pages deploy --project-name=newsoftware
```

共享资源的 `database_id` / `bucket_name` 保持原样。D1 表结构不用重建——计数行会在首次下载时自动创建。

### 4. 上传首个发布物

```bash
wrangler r2 object put softwares/newsoftware/latest.apk \
  --file <文件路径> --remote \
  --content-type application/vnd.android.package-archive \
  --content-disposition 'attachment; filename="NewSoftware-v1.0.0.apk"'
```

完成后 `/api/stats` 即返回 `{ direct: 0, github: <n>, total: <n>, version: "v1.0.0" }`。

### 前端接入

页面只需请求 `/api/stats` 拿 `total` 与 `version`，下载按钮指向 `/download`，参见 `assets/script.js` 的 `loadStats()`。
