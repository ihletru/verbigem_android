# Verbigem Android — 更新日志


> ⚠️ `v1.0.1`–`v1.0.3` 标签属于**早期历史构建**（versionCode 2–3）。
> 当前版本为 **v1.0.42**（versionCode 43）。
> 版本号保存在 `app/build.gradle.kts`（`versionCode` / `versionName`）。

---

## v1.0.42（2026-09-06）— versionCode 43

**禁用的控件现在也能打开帮助窗口。**

- `Modifier.helpClickable(enabled = false)` 会把 `enabled` 传给
  `combinedClickable`，而后者会**连长按一起禁用** —— 于是变灰的控件上帮助窗口
  会无声消失。现在 `enabled` 只拦截动作：`combinedClickable` 始终收到
  `enabled = true`，判断放在 `onClick = { if (enabled) onClick() }` 里。
- 涉及：输入为空时的“翻译”按钮、免费账户下的引擎图标（精确 / 两者 / 在线）、
  没有图片时的 OCR 按钮。
- README：帮助窗口陷阱清单新增第 10 条。

## v1.0.41（2026-09-06）— versionCode 42

**全局帮助弹窗规则（v40）之后的 UI 修复。**

- **「我知道了」** 按钮现在跟随界面语言（之前始终显示波兰语）。`HelpWindow` 在 `Dialog{}` 之前捕获 `LocalContext`，并通过 `CompositionLocalProvider` 恢复。
- **底栏回到 5 个图标**（翻译、对话、聊天、联系人、个人资料）。第六个 OCR 位置让底栏过于拥挤。
- **翻译**：按下「翻译」按钮时，`bringIntoViewRequester` + `LaunchedEffect(WindowInsets.isImeVisible)` 配合 `delay(250)` 让它自动滚到键盘上方。
- **引擎图标（精确、双引擎、在线）** 即使在免费用户下被禁用，长按仍可查看帮助。
- **对话和 OCR**：移除了标题下多余的副标题——「?」按钮已经说明了页面内容。
- **联系人 → 从手机**：按钮「在我的通讯录中查找好友」和「导入 .vcf」现在都有帮助（长按）。
- **个人资料**：界面语言选择器现在有可见的边框；隐私卡片下方新增**关于**卡片，显示版本号与**更新日志**链接。
- **应用图标**：自适应图标的背景由绿色（#2C6B85）改为奶油色（`CalmDayBg` #F7F5F1），与页面背景一致。PNG 分为五档密度 108/162/216/324/432 px（Lanczos）。

---

## v1.0.40（2026-09-06）— versionCode 41

**全局界面规则：轻点图标 = 执行其任务，长按图标 = 弹出帮助窗口。**

- **应用中的每一个图标**现在都会自我说明：它是什么、做什么、怎么用。
  新的共用基础组件位于 `ui/components/HelpDialog.kt`（`HelpWindow`、`helpClickable`、
  `HelpIconButton`、`HelpFramedIconButton`、`QuestionMarkButton`、`ScreenHeader`）。
- **每个页面的标题栏**：萤火虫标志（透明背景）+ 标题 + 右侧的 **„?"** 按钮，
  点击即可查看整页说明。
- **翻译页**：两个语言选择框和交换图标均有帮助；四个引擎改为短标签
  （快速 / 精确 / 两者 / 在线）并配完整说明窗口 —— 引擎下方原有的描述**已删除**；
  麦克风 / 相机 / 相机 Pro 现在带**边框**和 „从语音" / „从照片" / „从照片 pro" 标签；
  „翻译"按钮、历史与结果卡片上的五个图标以及底部导航栏均有帮助。
- **对话页**：标志 + „?"（其中说明对话内容不会被保存、也不会离开设备），
  语言字段、交换、麦克风和发送按钮均有帮助。
- **OCR 已加入底部导航栏** —— 导航栏现在有六个标签（此前它是唯一没有导航的页面）。
- **联系人**：朋友 / 邀请 / 来自手机 / 外部 改为 **图标在上、11.sp 文字在下**
  （与底部导航栏一致），并带帮助窗口。
- **聊天、个人资料、我的二维码、手机验证** —— 均带 „?" 标题栏和图标帮助。
- **6 种语言新增约 50 条帮助文案**（394 个键，各语言均无缺失）。

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
