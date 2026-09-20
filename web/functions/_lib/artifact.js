/* ==========================================================================
   发布物约定（单一版本来源）

   R2 桶 `softwares` 中固定 key `<app>/latest.<ext>` 始终存最新版；
   版本号与下载文件名存放在该对象的 Content-Disposition 元数据里，例如：
     attachment; filename="LiangBuLiang-v26.08.r145.apk"

   更新版本只需用 wrangler 覆盖上传该 key，并带上新的 --content-disposition，
   无需改动任何代码：
     wrangler r2 object put softwares/liangbuliang/latest.apk \
       --file <新文件> --remote \
       --content-type application/vnd.android.package-archive \
       --content-disposition 'attachment; filename="LiangBuLiang-v<新版本>.apk"'
   ========================================================================== */

import { SITE } from './site';

export const ARTIFACT_KEY = SITE.app + '/latest.' + SITE.ext;

// 从 Content-Disposition 头解析文件名。
export function filenameFromContentDisposition(cd) {
  if (!cd) return null;
  const m = cd.match(/filename="?([^";]+)"?/i);
  return m ? m[1] : null;
}

// 从文件名解析版本号："LiangBuLiang-v26.08.r145.apk" -> "v26.08.r145"
// 先去掉扩展名，再按配置的前缀剥掉软件名。
export function versionFromFilename(filename) {
  if (!filename) return null;
  const base = filename.replace(/\.[^.]+$/, '');
  return SITE.namePrefix && base.indexOf(SITE.namePrefix) === 0
    ? base.slice(SITE.namePrefix.length)
    : base;
}
