-- ===========================================================================
-- 共享 D1 数据库 `softwares` 表结构
--
-- 整个 Cloudflare 账号只需执行一次（所有软件共用同一个库，不要重复建库）：
--   wrangler d1 execute softwares --remote --file=web/schema.sql
--
-- 计数按 app 列隔离，每个软件对应 _lib/site.js 里的 SITE.app：
--   ('liangbuliang', 'direct',            500)  本站直接下载
--   ('liangbuliang', 'github',              1)  GitHub release 下载总量
--   ('liangbuliang', 'github_updated_at',  ...)  上次拉取 GitHub 的时间戳（秒）
--
-- 无需手工插入初始行：首次下载 / 首次访问统计接口时会自动建行(UPSERT)。
-- ===========================================================================

CREATE TABLE IF NOT EXISTS counters (
  app   TEXT    NOT NULL,  -- 软件标识，对应 _lib/site.js 的 SITE.app
  key   TEXT    NOT NULL,  -- 'direct' | 'github' | 'github_updated_at'
  value INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (app, key)
) WITHOUT ROWID;
