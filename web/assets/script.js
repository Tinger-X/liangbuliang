/* ==========================================================================
   亮不亮 · LiangBuLiang — Official Website
   Interactions: i18n, theme, mobile nav, brightness demo, copy, reveal
   ========================================================================== */
(function () {
  'use strict';

  /* ----------------------------- i18n ----------------------------- */
  var I18N = {
    zh: {
      'a11y.skip': '跳到主要内容',

      'nav.features': '功能',
      'nav.demo': '演示',
      'nav.download': '下载',
      'nav.guide': '使用指南',
      'nav.privacy': '隐私',
      'nav.cta': '下载',

      'hero.badge': '{version} · 免费 · 开源',
      'hero.title': '亮不亮',
      'hero.tagline': '极致简洁的屏幕亮度与熄屏时长调节工具，支持后台持续生效。',
      'hero.desc': '在阅读、观影或任何需要固定屏幕亮度与熄屏时长的场景下，一键锁定你的屏幕设置。',
      'hero.cta_download': '下载安装',
      'hero.cta_guide': '使用指南',
      'hero.stat_brightness': '精细亮度调节',
      'hero.stat_timeout': '25 档熄屏时长',
      'hero.stat_android': '广泛兼容',
      'hero.stat_privacy': '隐私优先',
      'hero.stat_privacy_val': '本地存储',
      'hero.stat_downloads': '累计下载',

      'features.eyebrow': '核心功能',
      'features.title': '把屏幕的控制权还给你',
      'features.sub': '少即是多。亮不亮只做两件事，并把它做到极致。',
      'features.f1_title': '屏幕亮度调节',
      'features.f1_desc': '0.1%~10% 精细调节，启用后覆盖系统亮度，读屏、观影不再刺眼。',
      'features.f2_title': '熄屏时长控制',
      'features.f2_desc': '从 5 秒到常亮共 25 个档位，灵活应对阅读、导航、演示等场景。',
      'features.f3_title': '后台守护',
      'features.f3_desc': '通过前台服务在后台持续维持设置，切换应用也不会丢失。',
      'features.f4_title': '一键恢复',
      'features.f4_desc': '关闭功能后自动还原你的原始系统亮度与熄屏时长，不留痕迹。',
      'features.f5_title': 'Extra Dim 超低亮度',
      'features.f5_desc': '借助系统原生「Extra Dim」实现 0.1%~1% 的平滑超低亮度，深夜无打扰。',
      'features.f6_title': '隐私优先',
      'features.f6_desc': '所有设置仅存储在设备本地，不上传任何服务器，无需联网即可使用。',

      'demo.eyebrow': '上手即用',
      'demo.title': '拖动滑块，感受一下',
      'demo.sub': '这是亮不亮的真实界面，1:1 还原。开启开关、拖动滑块，直观感受它的实际效果。',
      'demo.brightness': '屏幕亮度',
      'demo.timeout': '熄屏时长',
      'demo.off': '关闭',
      'demo.always': '常亮',
      'demo.wake': '轻触唤醒',
      'demo.caption': '小提示：把熄屏时长设为 5s，等待几秒屏幕就会熄灭，轻触手机屏幕即可唤醒。',
      'demo.brightness_switch': '屏幕亮度开关',
      'demo.timeout_switch': '熄屏时长开关',

      'download.eyebrow': '获取应用',
      'download.title': '下载亮不亮',
      'download.sub': '当前最新版本 {version}，支持 Android 7.0 及以上。',
      'download.meta': 'Android 7.0+ · arm64-v8a · 免费 · 无广告',
      'download.cta_direct': '官网下载安装包',
      'download.cta_github': 'Github 下载',
      'download.cta_star': 'Github 标星',
      'download.note': 'APK 由 Tinger 构建并签名，构建过程与源码完全公开，可在 GitHub 仓库中审计。',

      'guide.eyebrow': '进阶使用',
      'guide.title': '授予 WRITE_SECURE_SETTINGS 权限（推荐）',
      'guide.sub': '在 Android 12 及以上，0.1%~1% 的超低亮度借助系统原生「Extra Dim」实现，需要手动授予一项系统保护权限。',
      'guide.s1_title': '开启 USB 调试',
      'guide.s1_desc': '在设备的「开发者选项」中开启 USB 调试，并用数据线连接电脑。',
      'guide.s2_title': '执行授权命令',
      'guide.s2_desc': '在电脑终端执行下方命令，即可授予权限。',
      'guide.s3_title': '重启应用',
      'guide.s3_desc': '回到亮不亮，将亮度调至 1% 以下，即可体验平滑无跳变的超低亮度。',
      'guide.code_label': '授权命令',
      'guide.copy': '复制',
      'guide.shizuku': '也可通过 Shizuku 等工具在设备上直接授予该权限，无需电脑。',
      'guide.granted_title': '✅ 已授予权限',
      'guide.granted_desc': '应用使用系统原生「Extra Dim」平滑调节 0.1%~1% 超低亮度，变化连续无跳变；即使后台进程被清理，1% 以下的亮度仍会保留。',
      'guide.nogrant_title': '⚠️ 未授予权限',
      'guide.nogrant_desc': '应用退化为「屏幕遮罩」方案实现超低亮度；后台被清理时遮罩失效，1% 以下的亮度会回弹到 1%。',
      'guide.revoke_title': '撤销权限',

      'privacy.eyebrow': '隐私说明',
      'privacy.title': '你的数据，只属于你',
      'privacy.sub': '亮不亮申请的所有权限均服务于「调节屏幕」这一单一目的，且数据只在本地。',
      'privacy.col_perm': '权限 / 数据',
      'privacy.col_purpose': '用途',
      'privacy.r1': '调节系统屏幕亮度与熄屏时长',
      'privacy.r2': '在 Android 12+ 通过「Extra Dim」实现 0.1%~1% 超低亮度平滑调节',
      'privacy.r3': '后台持续维护用户设置',
      'privacy.r4': '前台服务通知',
      'privacy.r5': '保持屏幕常亮（仅当用户主动选择时）',
      'privacy.note': '所有设置数据仅存储在设备本地，不上传至任何服务器，无需注册、无需联网、无任何分析或广告追踪。',

      'faq.eyebrow': '常见问题',
      'faq.title': '你可能想知道',
      'faq.q1': '亮不亮收费吗？',
      'faq.a1': '完全免费、开源、无广告。你可以自由下载使用，也可以前往 GitHub 查看源码。',
      'faq.q2': '为什么低于 1% 需要额外授权？',
      'faq.a2': 'Android 12+ 的 0.1%~1% 亮度依赖系统原生「Extra Dim」功能，该功能受系统保护，需通过 ADB 或 Shizuku 授予 WRITE_SECURE_SETTINGS 权限。',
      'faq.q3': '退出应用后设置会失效吗？',
      'faq.a3': '不会。启用后应用通过前台服务在后台持续维持设置，切换或退出应用都不会丢失；主动关闭功能时会自动还原原始设置。',
      'faq.q4': '数据会被上传吗？',
      'faq.a4': '不会。所有设置仅保存在设备本地，应用无需任何网络权限即可正常使用。',

      'footer.tagline': '极致简洁的屏幕亮度与熄屏时长调节工具。',
      'footer.product': '产品',
      'footer.resources': '资源',
      'footer.contact': '联系',
      'footer.faq': '常见问题',
      'footer.rights': '保留所有权利'
    },
    en: {
      'a11y.skip': 'Skip to content',

      'nav.features': 'Features',
      'nav.demo': 'Demo',
      'nav.download': 'Download',
      'nav.guide': 'Guide',
      'nav.privacy': 'Privacy',
      'nav.cta': 'Download',

      'hero.badge': '{version} · Free · Open source',
      'hero.title': 'LiangBuLiang',
      'hero.tagline': 'An ultra-simple screen brightness and screen-off timeout tool that keeps working in the background.',
      'hero.desc': 'Lock your screen settings with one tap for reading, watching videos, or any scenario where you need a fixed brightness and timeout.',
      'hero.cta_download': 'Download',
      'hero.cta_guide': 'Guidance',
      'hero.stat_brightness': 'Fine brightness control',
      'hero.stat_timeout': '25 timeout levels',
      'hero.stat_android': 'Broad compatibility',
      'hero.stat_privacy': 'Privacy first',
      'hero.stat_privacy_val': 'On-device',
      'hero.stat_downloads': 'Total downloads',

      'features.eyebrow': 'Core features',
      'features.title': 'Take back control of your screen',
      'features.sub': 'Less is more. LiangBuLiang does two things — and does them extremely well.',
      'features.f1_title': 'Brightness control',
      'features.f1_desc': 'Fine-grained 0.1%–10% control that overrides the system brightness once enabled — no more glare.',
      'features.f2_title': 'Screen-off',
      'features.f2_desc': '25 levels from 5 seconds to always-on, for reading, navigation, and presentations.',
      'features.f3_title': 'Background daemon',
      'features.f3_desc': 'A foreground service keeps your settings applied in the background, even when switching apps.',
      'features.f4_title': 'One-tap restore',
      'features.f4_desc': 'Turning a feature off restores your original system brightness and timeout automatically.',
      'features.f5_title': 'Extra Dim ultra-low brightness',
      'features.f5_desc': 'Uses the system-native "Extra Dim" for a smooth 0.1%–1% ultra-low brightness for late nights.',
      'features.f6_title': 'Privacy first',
      'features.f6_desc': 'All settings are stored locally on your device. Nothing is uploaded, and no network is required.',

      'demo.eyebrow': 'Try it now',
      'demo.title': 'Drag the slider and feel it',
      'demo.sub': 'This is the real LiangBuLiang UI, recreated 1:1. Toggle the switches and drag the sliders to feel the actual effect.',
      'demo.brightness': 'Brightness',
      'demo.timeout': 'Screen-off',
      'demo.off': 'Off',
      'demo.always': 'Always',
      'demo.wake': 'Tap to wake',
      'demo.caption': 'Tip: set the timeout to 5s, wait a few seconds and the screen turns off — tap it to wake.',
      'demo.brightness_switch': 'Brightness toggle',
      'demo.timeout_switch': 'Timeout toggle',

      'download.eyebrow': 'Get the app',
      'download.title': 'Download LiangBuLiang',
      'download.sub': 'Latest version {version}, for Android 7.0 and above.',
      'download.meta': 'Android 7.0+ · arm64-v8a · Free · No ads',
      'download.cta_direct': 'Download APK Here',
      'download.cta_github': 'GitHub Download',
      'download.cta_star': 'GitHub Star',
      'download.note': 'The APK is built and signed by Tinger. The build process and source are fully public and auditable on GitHub.',

      'guide.eyebrow': 'Power user',
      'guide.title': 'Grant WRITE_SECURE_SETTINGS (recommended)',
      'guide.sub': 'On Android 12+, the 0.1%–1% ultra-low brightness uses the system-native "Extra Dim" and requires a manually-granted protected permission.',
      'guide.s1_title': 'Enable USB debugging',
      'guide.s1_desc': 'Turn on USB debugging in Developer Options and connect your device with a cable.',
      'guide.s2_title': 'Run the grant command',
      'guide.s2_desc': 'Run the command below in a terminal on your computer to grant the permission.',
      'guide.s3_title': 'Restart the app',
      'guide.s3_desc': 'Return to LiangBuLiang and set brightness below 1% to enjoy a smooth, jump-free ultra-low brightness.',
      'guide.code_label': 'Grant command',
      'guide.copy': 'Copy',
      'guide.shizuku': 'You can also grant the permission on-device using tools such as Shizuku — no computer needed.',
      'guide.granted_title': '✅ Permission granted',
      'guide.granted_desc': 'The app uses the system-native "Extra Dim" for a smooth 0.1%–1% brightness with no jumps; brightness below 1% is preserved even if the background process is killed.',
      'guide.nogrant_title': '⚠️ Permission not granted',
      'guide.nogrant_desc': 'The app falls back to a screen overlay for ultra-low brightness; when the background is cleared the overlay stops and brightness below 1% snaps back to 1%.',
      'guide.revoke_title': 'Revoke the permission',

      'privacy.eyebrow': 'Privacy',
      'privacy.title': 'Your data belongs to you',
      'privacy.sub': 'Every permission exists for one purpose — adjusting your screen — and all data stays local.',
      'privacy.col_perm': 'Permission / Data',
      'privacy.col_purpose': 'Purpose',
      'privacy.r1': 'Adjusts system screen brightness and screen-off timeout',
      'privacy.r2': 'Achieves smooth 0.1%–1% ultra-low brightness via "Extra Dim" on Android 12+',
      'privacy.r3': 'Keeps settings applied in the background',
      'privacy.r4': 'Foreground service notification',
      'privacy.r5': 'Keeps the screen on (only when you actively choose it)',
      'privacy.note': 'All settings are stored locally on your device only. No registration, no network, no analytics, no ad tracking.',

      'faq.eyebrow': 'FAQ',
      'faq.title': 'Things you may wonder',
      'faq.q1': 'Is LiangBuLiang free?',
      'faq.a1': 'Completely free, open source, and ad-free. Download and use it freely, or read the source on GitHub.',
      'faq.q2': 'Why does below-1% brightness need extra permission?',
      'faq.a2': 'On Android 12+, 0.1%–1% brightness relies on the system-native "Extra Dim", which is protected and requires granting WRITE_SECURE_SETTINGS via ADB or Shizuku.',
      'faq.q3': 'Do my settings reset when I leave the app?',
      'faq.a3': 'No. Once enabled, a foreground service keeps settings applied in the background; leaving the app won\'t lose them, and disabling a feature restores your original settings.',
      'faq.q4': 'Is my data uploaded anywhere?',
      'faq.a4': 'No. All settings are stored locally on your device, and the app needs no network permission to work.',

      'footer.tagline': 'An ultra-simple screen brightness and screen-off timeout tool.',
      'footer.product': 'Product',
      'footer.resources': 'Resources',
      'footer.contact': 'Contact',
      'footer.faq': 'FAQ',
      'footer.rights': 'All rights reserved'
    }
  };

  var STORAGE_LANG = 'lbl.lang';
  var lang = localStorage.getItem(STORAGE_LANG) || 'zh';
  var appVersion = 'v26.08.r145'; // 默认版本；/api/stats 返回后动态更新

  function applyLang() {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.querySelectorAll('[data-i18n]').forEach(function (el) {
      var key = el.getAttribute('data-i18n');
      if (I18N[lang] && I18N[lang][key] != null) {
        el.textContent = I18N[lang][key].replace(/\{version\}/g, appVersion);
      }
    });
    document.querySelectorAll('.lang-btn').forEach(function (btn) {
      var active = btn.getAttribute('data-lang') === lang;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-pressed', String(active));
    });
    document.querySelectorAll('[data-i18n-label]').forEach(function (el) {
      var key = el.getAttribute('data-i18n-label');
      if (I18N[lang] && I18N[lang][key] != null) el.setAttribute('aria-label', I18N[lang][key]);
    });
    syncPhoneLabels();
    renderDownloadCount();
    renderVersion();
    localStorage.setItem(STORAGE_LANG, lang);
  }

  function setLang(next) {
    if (next !== 'zh' && next !== 'en') return;
    lang = next;
    applyLang();
  }

  document.querySelectorAll('.lang-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      setLang(btn.getAttribute('data-lang'));
    });
  });

  /* ----------------------------- Theme ----------------------------- */
  var STORAGE_THEME = 'lbl.theme';
  function currentTheme() {
    var saved = localStorage.getItem(STORAGE_THEME);
    if (saved === 'light' || saved === 'dark') return saved;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  function applyTheme() {
    var theme = currentTheme();
    document.documentElement.setAttribute('data-theme', theme);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', theme === 'dark' ? '#0F172A' : '#F59E0B');
  }
  function toggleTheme() {
    var next = currentTheme() === 'dark' ? 'light' : 'dark';
    localStorage.setItem(STORAGE_THEME, next);
    applyTheme();
  }
  document.getElementById('theme-toggle').addEventListener('click', toggleTheme);

  /* ----------------------------- Mobile nav ----------------------------- */
  var navToggle = document.getElementById('nav-toggle');
  var siteNav = document.querySelector('.site-nav');
  navToggle.addEventListener('click', function () {
    var open = siteNav.classList.toggle('is-open');
    navToggle.setAttribute('aria-expanded', String(open));
  });
  siteNav.querySelectorAll('a').forEach(function (a) {
    a.addEventListener('click', function () {
      siteNav.classList.remove('is-open');
      navToggle.setAttribute('aria-expanded', 'false');
    });
  });

  /* ----------------------------- Phone demo (1:1 app replica) ----------------------------- */
  // Mirrors the app's TimeoutOption enum: 25 discrete levels, index 24 = always-on.
  var TIMEOUT_LABELS = ['5s','10s','15s','30s','45s','1min','2min','3min','5min','10min','15min','20min','25min','30min','40min','45min','50min','1h','1.5h','2h','2.5h','3h','4h','5h'];
  var TIMEOUT_MS = [5000,10000,15000,30000,45000,60000,120000,180000,300000,600000,900000,1200000,1500000,1800000,2400000,2700000,3000000,3600000,5400000,7200000,9000000,10800000,14400000,18000000];
  var ALWAYS_ON_INDEX = 24;

  var phoneApp = document.getElementById('phone-app');
  var phoneScreen = document.getElementById('phone-screen');
  var screensaver = document.getElementById('screensaver');
  var screensaverClock = document.getElementById('screensaver-clock');

  var brightnessRange = document.getElementById('brightness-range');
  var brightnessSwitch = document.getElementById('brightness-switch');
  var brightnessValue = document.getElementById('brightness-value');
  var brightnessSlider = document.getElementById('brightness-slider-wrap');

  var timeoutRange = document.getElementById('timeout-range');
  var timeoutSwitch = document.getElementById('timeout-switch');
  var timeoutValue = document.getElementById('timeout-value');
  var timeoutSlider = document.getElementById('timeout-slider-wrap');

  var brightnessOn = false;
  var timeoutOn = false;
  var timeoutIndex = 3; // default 30s
  var offTimer = null;

  function t(key) { return (I18N[lang] && I18N[lang][key]) || ''; }
  function timeoutLabel(index) {
    if (index === ALWAYS_ON_INDEX) return lang === 'zh' ? '常亮' : 'Always';
    return TIMEOUT_LABELS[index];
  }
  function fmtBrightness(v) {
    if (v >= 1) return Math.round(v) + '%';
    return (Math.floor(v * 10) / 10) + '%'; // matches the app's formatBrightnessValue (floored)
  }
  function paintRange(input, fraction) {
    var w = input.clientWidth || 1;
    var thumb = 30;
    var half = thumb / 2;
    var fillPx = half + fraction * (w - thumb);
    var pct = (fillPx / w) * 100;
    input.style.setProperty('--fill', Math.max(0, Math.min(100, pct)).toFixed(2) + '%');
  }
  function applyBrightnessVisual() {
    if (!brightnessOn) { phoneApp.style.filter = ''; return; }
    var v = parseFloat(brightnessRange.value);
    var b = 0.05 + 0.95 * (v / 10); // 0.1% → very dim, 10% → full
    phoneApp.style.filter = 'brightness(' + b.toFixed(3) + ')';
  }

  function onBrightnessInput() {
    brightnessValue.textContent = fmtBrightness(parseFloat(brightnessRange.value));
    paintRange(brightnessRange, (parseFloat(brightnessRange.value) - 0.1) / 9.9);
    applyBrightnessVisual();
  }
  function setBrightnessOn(on) {
    brightnessOn = on;
    brightnessSwitch.setAttribute('aria-checked', String(on));
    brightnessSlider.classList.toggle('is-open', on);
    if (on) {
      brightnessValue.classList.remove('is-off');
      brightnessValue.textContent = fmtBrightness(parseFloat(brightnessRange.value));
      paintRange(brightnessRange, (parseFloat(brightnessRange.value) - 0.1) / 9.9);
      applyBrightnessVisual();
    } else {
      brightnessValue.classList.add('is-off');
      brightnessValue.textContent = t('demo.off');
      phoneApp.style.filter = '';
    }
  }

  function onTimeoutInput() {
    timeoutIndex = Math.round(parseFloat(timeoutRange.value));
    timeoutValue.textContent = timeoutLabel(timeoutIndex);
    paintRange(timeoutRange, timeoutIndex / 24);
    resetOffTimer();
  }
  function setTimeoutOn(on) {
    timeoutOn = on;
    timeoutSwitch.setAttribute('aria-checked', String(on));
    timeoutSlider.classList.toggle('is-open', on);
    if (on) {
      timeoutValue.classList.remove('is-off');
      timeoutValue.textContent = timeoutLabel(timeoutIndex);
      paintRange(timeoutRange, timeoutIndex / 24);
      resetOffTimer();
    } else {
      timeoutValue.classList.add('is-off');
      timeoutValue.textContent = t('demo.off');
      clearOffTimer();
      wake();
    }
  }

  function resetOffTimer() {
    clearOffTimer();
    if (!timeoutOn || timeoutIndex === ALWAYS_ON_INDEX) return;
    offTimer = setTimeout(showScreensaver, TIMEOUT_MS[timeoutIndex]);
  }
  function clearOffTimer() {
    if (offTimer) { clearTimeout(offTimer); offTimer = null; }
  }
  function showScreensaver() {
    var d = new Date();
    screensaverClock.textContent = pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    screensaver.classList.add('is-on');
    screensaver.setAttribute('aria-hidden', 'false');
  }
  function wake() {
    screensaver.classList.remove('is-on');
    screensaver.setAttribute('aria-hidden', 'true');
    resetOffTimer();
  }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  // Any tap on the phone wakes it (and resets the idle timeout).
  phoneScreen.addEventListener('pointerdown', wake);
  brightnessSwitch.addEventListener('click', function () { setBrightnessOn(!brightnessOn); });
  timeoutSwitch.addEventListener('click', function () { setTimeoutOn(!timeoutOn); });
  brightnessRange.addEventListener('input', onBrightnessInput);
  timeoutRange.addEventListener('input', onTimeoutInput);
  window.addEventListener('resize', function () {
    paintRange(brightnessRange, (parseFloat(brightnessRange.value) - 0.1) / 9.9);
    paintRange(timeoutRange, timeoutIndex / 24);
  });

  // Re-render the dynamic demo labels when the language changes.
  function syncPhoneLabels() {
    brightnessValue.textContent = brightnessOn ? fmtBrightness(parseFloat(brightnessRange.value)) : t('demo.off');
    timeoutValue.textContent = timeoutOn ? timeoutLabel(timeoutIndex) : t('demo.off');
  }

  /* ----------------------------- Copy buttons ----------------------------- */
  document.querySelectorAll('.copy-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var text = btn.getAttribute('data-copy');
      var done = function () {
        var original = btn.textContent;
        btn.textContent = I18N[lang]['guide.copied'] || (lang === 'zh' ? '已复制' : 'Copied');
        btn.classList.add('is-copied');
        setTimeout(function () {
          btn.textContent = original;
          btn.classList.remove('is-copied');
        }, 1800);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done, function () { fallbackCopy(text); done(); });
      } else {
        fallbackCopy(text);
        done();
      }
    });
  });

  function fallbackCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); } catch (e) { /* ignore */ }
    document.body.removeChild(ta);
  }

  /* ----------------------------- Year ----------------------------- */
  var yearEl = document.getElementById('year');
  if (yearEl) yearEl.textContent = String(new Date().getFullYear());

  /* ----------------------------- Reveal on scroll ----------------------------- */
  var revealEls = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('in-view');
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });
    revealEls.forEach(function (el) { io.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add('in-view'); });
  }

  /* ----------------------------- Download count / version ----------------------------- */
  var statDownloads = document.getElementById('stat-downloads');
  var lastDownloadTotal = null;

  function formatCount(n) {
    return n.toLocaleString(lang === 'zh' ? 'zh-CN' : 'en-US');
  }
  function renderDownloadCount() {
    if (statDownloads && lastDownloadTotal != null) {
      statDownloads.textContent = formatCount(lastDownloadTotal);
    }
  }
  function renderVersion() {
    document.querySelectorAll('.js-version').forEach(function (el) {
      el.textContent = appVersion;
    });
  }
  function loadStats() {
    fetch('/api/stats', { headers: { 'Accept': 'application/json' } })
      .then(function (res) {
        if (!res.ok) throw new Error('stats unavailable');
        return res.json();
      })
      .then(function (data) {
        if (typeof data.total === 'number') {
          lastDownloadTotal = data.total;
          renderDownloadCount();
        }
        if (data.version) {
          appVersion = data.version;
          applyLang(); // 重新渲染含 {version} 的文案（badge / download.sub / 版本标签）
        }
      })
      .catch(function () {
        // 拉取失败保持占位符「—」与默认版本，不影响页面。
      });
  }

  /* ----------------------------- Init ----------------------------- */
  applyTheme();
  applyLang();
  loadStats();
})();
