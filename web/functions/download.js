/* ==========================================================================
   /download — 本站直接下载
   计数（D1 direct +1）后，从 R2 流式返回 APK。
   APK 不再作为公开静态资源，公开 URL /assets/*.apk 将 404，
   从而防止用户绕过计数直接下载。

   版本/文件名来自 R2 对象元数据（见 _lib/apk.js），更新版本无需改此文件。
   ========================================================================== */

import { APK_KEY } from './_lib/apk';

export async function onRequest(context) {
  const { env, request } = context;

  // 仅 GET 计为一次下载（HEAD 不计数）。
  if (request.method !== 'GET') {
    return new Response('Method Not Allowed', { status: 405, headers: { Allow: 'GET' } });
  }

  // 计数失败不阻断下载。
  try {
    await env.DB.prepare("UPDATE counters SET value = value + 1 WHERE key = 'direct'").run();
  } catch (e) {
    console.error('failed to count direct download', e);
  }

  const object = await env.BUCKET.get(APK_KEY);
  if (!object) {
    return new Response('Not Found', { status: 404 });
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers); // 写入 Content-Type / Content-Disposition 等元数据

  // 关键头显式兜底（版本/文件名唯一来源是 R2 元数据）
  const md = object.httpMetadata || {};
  headers.set('Content-Type', md.contentType || 'application/vnd.android.package-archive');
  headers.set('Content-Disposition', md.contentDisposition || 'attachment; filename="' + object.key + '"');
  headers.set('Content-Length', String(object.size));
  // 不缓存：确保每次点击都触发 Function 计数。
  headers.set('Cache-Control', 'no-store');

  return new Response(object.body, { headers });
}
