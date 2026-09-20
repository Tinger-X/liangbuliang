/* ==========================================================================
   站点配置 —— 接入新软件时【只需要改这个文件】

   所有软件共用同一套 Cloudflare 资源（无需为每个项目新建）：
     D1  `softwares`  表 counters(app, key, value)，用 app 列隔离各软件的计数
     R2  `softwares`  用 `<app>/` 目录前缀隔离各软件的发布物

   D1 建表语句见 web/schema.sql（整个账号只需执行一次）。
   完整接入步骤见 web/README.md 的「接入新软件」章节。
   ========================================================================== */

export const SITE = {
  // 共享资源中的唯一标识：D1 的 app 列 + R2 的目录前缀。
  // 上线后请勿修改，改了会丢失历史计数、读不到已上传的发布物。
  app: 'liangbuliang',

  // GitHub 仓库 owner/name，用于统计 release 附件下载量。
  githubRepo: 'Tinger-X/liangbuliang',

  // 发布物：R2 中固定 key 为 `<app>/latest.<ext>`。
  // 换软件类型时改这两项即可（如 exe / zip / dmg）。
  ext: 'apk',
  contentType: 'application/vnd.android.package-archive',

  // 发布文件名前缀，用于从 Content-Disposition 解析版本号。
  // 约定文件名形如 `<namePrefix><版本>.<ext>`：
  //   LiangBuLiang-v26.08.r145.apk  →  v26.08.r145
  namePrefix: 'LiangBuLiang-',
};
