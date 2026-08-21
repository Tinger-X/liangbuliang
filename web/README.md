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
│   ├── download.js       # /download：计数后从 R2 流式返回 APK
│   ├── api/stats.js      # /api/stats：返回下载统计与版本
│   └── _lib/apk.js       # 共享：APK key 与版本解析
├── _headers              # 安全响应头 + /assets/* 长缓存
├── robots.txt / sitemap.xml
├── wrangler.jsonc        # 部署配置（含 D1 ID，已被 .gitignore 忽略）
└── wrangler.example.jsonc  # 配置示例（提交用，占位 D1 ID）
```

## 依赖资源

- Cloudflare Pages 项目 `liangbuliang`（域名 `liangbuliang.tin.edu.kg`）
- D1 数据库 `liangbuliang`，表 `counters(key, value)`：`direct` / `github` / `github_updated_at`
- R2 桶 `liangbuliang`，对象固定 key `LiangBuLiang-latest.apk`

## 前置要求

- Node.js + wrangler（`npm install -g wrangler`）
- 已登录：`wrangler login`

## 首次配置

1. 复制 `wrangler.example.jsonc` 为 `wrangler.jsonc`。
2. 把 `d1_databases[].database_id` 替换为真实 D1 数据库 ID。

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
wrangler r2 object put liangbuliang/LiangBuLiang-latest.apk \
  --file <新 APK 路径> --remote \
  --content-type application/vnd.android.package-archive \
  --content-disposition 'attachment; filename="LiangBuLiang-v<版本>.apk"'
```

覆盖固定 key `LiangBuLiang-latest.apk` 后，`/download` 自动带出新文件名，前端版本号（badge / 下载副标题 / 版本标签）由 `/api/stats` 动态读取并更新。

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
  - 本站：`/download` 对 D1 `direct` 原子 +1，再从 R2 流式返回 APK。
  - GitHub：`/api/stats` 每 15 分钟在 Cloudflare 侧拉取一次 GitHub API，写入 D1 兜底。
- `wrangler.jsonc` 需保留 `nodejs_compat`（R2 流式返回依赖 `node:stream`）。
