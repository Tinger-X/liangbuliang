/* ==========================================================================
   /download — 本站直接下载
   计数（D1 counters 中本软件的 direct +1）后，从 R2 流式返回发布物。
   发布物不再作为公开静态资源，公开 URL /assets/*.apk 将 404，
   从而防止用户绕过计数直接下载。

   软件标识 / GitHub 仓库 / 文件类型见 _lib/site.js，
   版本与文件名来自 R2 对象元数据（见 _lib/artifact.js），更新版本无需改此文件。
   ========================================================================== */

import { SITE } from './_lib/site';
import { ARTIFACT_KEY } from './_lib/artifact';

export async function onRequest(context) {
  const { env, request } = context;

  // 仅 GET 计为一次下载（HEAD 不计数）。
  if (request.method !== 'GET') {
    return new Response('Method Not Allowed', { status: 405, headers: { Allow: 'GET' } });
  }

  // 计数失败不阻断下载。UPSERT：新软件首次下载时自动建行，无需手工初始化。
  try {
    await env.DB.prepare(
      "INSERT INTO counters (app, key, value) VALUES (?, 'direct', 1) " +
      'ON CONFLICT(app, key) DO UPDATE SET value = value + 1'
    ).bind(SITE.app).run();
  } catch (e) {
    console.error('failed to count direct download', e);
  }

  const object = await env.BUCKET.get(ARTIFACT_KEY);
  if (!object) {
    return new Response('Not Found', { status: 404 });
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers); // 写入 Content-Type / Content-Disposition 等元数据

  // 关键头显式兜底（版本/文件名唯一来源是 R2 元数据）
  const md = object.httpMetadata || {};
  headers.set('Content-Type', md.contentType || SITE.contentType);
  headers.set(
    'Content-Disposition',
    md.contentDisposition || 'attachment; filename="' + ARTIFACT_KEY.split('/').pop() + '"'
  );
  headers.set('Content-Length', String(object.size));
  // 不缓存：确保每次点击都触发 Function 计数。
  headers.set('Cache-Control', 'no-store');

  return new Response(object.body, { headers });
}
