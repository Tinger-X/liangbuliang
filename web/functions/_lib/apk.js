/* ==========================================================================
   APK 元数据约定（单一版本来源）

   R2 中固定 key `LiangBuLiang-latest.apk` 始终存最新版；版本号与下载文件名
   存放在该对象的 Content-Disposition 元数据里，例如：
     attachment; filename="LiangBuLiang-v26.08.r145.apk"

   更新版本只需用 wrangler 覆盖上传该 key，并带上新的 --content-disposition 即可，
   无需改动任何代码：
     wrangler r2 object put liangbuliang/LiangBuLiang-latest.apk \
       --file <新 APK> --remote \
       --content-type application/vnd.android.package-archive \
       --content-disposition 'attachment; filename="LiangBuLiang-v<新版本>.apk"'
   ========================================================================== */

export const APK_KEY = 'LiangBuLiang-latest.apk';
const APK_PREFIX = 'LiangBuLiang-';
const APK_SUFFIX = '.apk';

// 从 Content-Disposition 头解析文件名。
export function filenameFromContentDisposition(cd) {
  if (!cd) return null;
  const m = cd.match(/filename="?([^";]+)"?/i);
  return m ? m[1] : null;
}

// 从文件名解析版本号："LiangBuLiang-v26.08.r145.apk" -> "v26.08.r145"
export function versionFromFilename(filename) {
  if (!filename) return null;
  return filename
    .replace(new RegExp('^' + APK_PREFIX), '')
    .replace(new RegExp(APK_SUFFIX + '$'), '');
}
