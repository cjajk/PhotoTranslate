# PhotoTranslate · 拍照翻译

拍照/选图 → OCR 词框叠加 → 点词完整释义 → 整段翻译 → 查词搜索 → 收藏复习。

## v0.3.0（当前）

- 拍照（系统相机）/ 相册选图，双指缩放跟随质心、可拖动，侧边 ＋/－ 缩放按钮（静止 5 秒自动隐藏）
- OCR 词框叠加（Google ML Kit Text Recognition v2 内置模型版，模型打包在 App 内，**不依赖 Google Play 服务**）
- 默认翻译引擎：**ML Kit 端侧翻译**（零配置免 key，首次使用下载约 30MB 语言模型，之后离线可用）；设置页可选百度翻译 / 有道智云（需自己的免费密钥）
- **离线词典（ECDICT 裁剪版，53k+ 常用词）**：点词/查词本地命中优先——音标、词性分组完整义项、词形变化、考纲标签；未命中自动回退百度 dict + Free Dictionary API 英英释义
- 整段翻译：POST 请求 + 按段落分块拼接，结果限高可滚动，失败可重试
- 手动查词页（底部第 5 项）：单词/词组查词典，句子整句翻译，保留最近 20 条搜索历史
- 收藏复习：单词/句子收藏带完整释义摘要
- 历史记录：拍照自动压缩保存（长边 ≤1600、JPEG 质量 80），可回看、可一键清空；拍照缓存残图自动清理
- 设置页：APP ID 与密钥密文掩码 + 显示/隐藏切换，问号教程页
- 进程内翻译缓存（LruCache），同词不重复打 API
- 固定 debug 签名（keystore 随仓库），后续版本可直接覆盖安装，无需卸载

## 云构建

推送到 main 分支触发 GitHub Actions，产物在 Actions 或 Release v0.3.0 下载（APK 直链 `https://github.com/cjajk/PhotoTranslate/releases/download/v0.3.0/app-debug.apk`）。

## 路线图

- v0.4：应用内卡片复习（SRS）、深色主题
- v0.5：中文/日文源图 OCR（ML Kit 内置 `text-recognition-chinese` / `-japanese` 模型）、Room 迁移（历史/收藏结构化存储）
