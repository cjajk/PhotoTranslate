# PhotoTranslate · 拍照翻译

拍照/选图 → OCR 词框叠加 → 点词翻译（规划中）→ 收藏复习。

## v0.1.0（当前）

- 拍照（系统相机）/ 相册选图
- OCR 词框叠加（Google ML Kit Text Recognition v2，设备需带 Google Play 服务）
- 点击词框 → 底部卡片 → 收藏到本机（DataStore）
- 设置页：翻译引擎与 API Key 配置（翻译功能下一版接入）

## 云构建

推送到 main 分支触发 GitHub Actions，产物在 Actions 或 Release v0.1.0 下载。

## 路线图

- v0.2：在线翻译接入（百度/腾讯/DeepL/OpenAI 兼容）+ 离线兜底（ML Kit Translation）
- v0.3：PaddleOCR 离线主引擎（免 GMS）、点词释义（词典+词形还原/分词）
- v0.4：应用内卡片复习（SRS）
