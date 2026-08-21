/* ==========================================================================
   /api/stats — 下载统计
   返回 { direct, github, total, version, filename }。
   - direct：本站直接下载（D1，实时）。
   - github：GitHub release 下载总数（Cloudflare 侧每 15 分钟拉取一次 GitHub API，
     成功后持久化到 D1 作为兜底；GitHub 不可达时沿用最近已知值，绝不阻塞页面）。
   - version / filename：当前最新版，来自 R2 对象元数据（单一版本来源）。
   ========================================================================== */

import { APK_KEY, filenameFromContentDisposition, versionFromFilename } from '../_lib/apk';

const GITHUB_RELEASES_URL = 'https://api.github.com/repos/Tinger-X/liangbuliang/releases';
const GITHUB_TTL = 900; // 秒，GitHub 未认证限流 60/h，节流避免超限

async function fetchGithubDownloads() {
  try {
    const res = await fetch(GITHUB_RELEASES_URL, {
      headers: {
        'User-Agent': 'liangbuliang-site',
        'Accept': 'application/vnd.github+json'
      }
    });
    if (!res.ok) return null;
    const releases = await res.json();
    let total = 0;
    for (const release of releases) {
      for (const asset of (release.assets || [])) {
        total += asset.download_count || 0;
      }
    }
    return total;
  } catch (e) {
    return null;
  }
}

export async function onRequest(context) {
  const { env } = context;
  const jsonHeaders = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };

  try {
    const { results } = await env.DB.prepare('SELECT key, value FROM counters').all();
    const map = {};
    for (const row of results) map[row.key] = row.value;

    const direct = map.direct || 0;
    let github = map.github || 0;
    const now = Math.floor(Date.now() / 1000);
    const updatedAt = map.github_updated_at || 0;

    if (now - updatedAt > GITHUB_TTL) {
      const fetched = await fetchGithubDownloads();
      if (fetched != null) {
        github = fetched;
        await env.DB.prepare("UPDATE counters SET value = ? WHERE key = 'github'").bind(github).run();
        await env.DB.prepare("UPDATE counters SET value = ? WHERE key = 'github_updated_at'").bind(now).run();
      }
    }

    // 版本信息来自 R2 对象元数据；失败不影响计数返回。
    let version = null;
    let filename = null;
    try {
      const apk = await env.BUCKET.head(APK_KEY);
      if (apk) {
        filename = filenameFromContentDisposition(apk.httpMetadata && apk.httpMetadata.contentDisposition) || apk.key;
        version = versionFromFilename(filename);
      }
    } catch (e) {
      /* ignore */
    }

    return Response.json({ direct, github, total: direct + github, version, filename }, { headers: jsonHeaders });
  } catch (e) {
    // D1 本身异常时的兜底（极少见）。
    return Response.json({ error: 'unavailable' }, { status: 500, headers: jsonHeaders });
  }
}
