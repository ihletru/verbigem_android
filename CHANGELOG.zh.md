# Verbigem Android — 更新日志

最新内容在最上方。每个版本也会作为 GitHub Release 发布：
<https://github.com/ihletru/verbigem_android/releases>

> ⚠️ `v1.0.1`–`v1.0.3` 标签属于**早期历史构建**（versionCode 2–3）。
> 当前版本为 **v1.0.39**（versionCode 40）。
> 版本号保存在 `app/build.gradle.kts`（`versionCode` / `versionName`）。

---

## v1.0.39（2026-09-05）— versionCode 40

从 v1.0.3 至今全部开发工作的汇总。

**更新内容**

- **1:1 聊天 + 联系人（第 1–3 阶段）**
  - 收件箱、聊天会话、联系人中的标签页（TabRow，4 个标签）。
  - `.vcf` 导入（自研解析器，零依赖）、消息搜索、「你可能认识的人」（`suggestFriends`）、按手机号邀请。
- **手机号与短信验证（第 2.4 / 2.6 阶段）**
  - 更易理解的 Phone Auth 错误提示，SMS 地区已解锁（**ALL** — 全球）。
  - 崩溃修复：「no activity」（v37）、点击「发送短信」后崩溃（v38）、错误提示可读化（v39）。
- **FCM 推送通知**（第 2 阶段）：Cloud Functions + FCM、App Check（HMAC 密钥）、`matchContacts`。
- **图片与 OCR**（第 5 阶段）：全屏图片预览 + 加载进度、聊天中的 OCR、实时 STT 转写。
- **二维码**（第 4 阶段）：我的二维码（ZXing）、GMS Code Scanner、App Links + `assetlinks.json`。
- **Firefly 品牌化**：透明启动器图标（v40）、Logo，以及 Play Store 上架计划中的 App Check 步骤。
- **隐私**：6 种语言的隐私政策，`READ_CONTACTS` 的醒目披露。
- **6 种语言**（pl、en、es、zh、de、tr）；文案核对 ×6（257 个键，无缺失）。
- **Cloud Functions 运行时**：Node 20 → nodejs22（7 个 Android 函数）。

---

## v1.0.3 — versionCode 3

从 GitHub raw（`master`）自动更新，界面修复：用 Pro 朗读喇叭代替星标，
付费 OCR 与免费 OCR 并列，键盘上方隐藏菜单。

## v1.0.2

响应式同步、红色垃圾桶、免费用户的 Pro 朗读喇叭（带提示气泡），
Pro OCR + OCR 历史记录，键盘不再遮挡翻译器。

## v1.0.1 — versionCode 2

自动更新测试构建。Pro 朗读 / 删除图标、Firestore 同步、
从 GitHub Releases 自动更新。
