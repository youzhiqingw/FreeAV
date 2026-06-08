# 项目文档索引

> **更新日期**: 2026-06-08
> **项目**: JAV Browser (Android)
> **技术栈**: Kotlin + Android SDK + WebView
> **当前版本**: v1.1.4

---

## 📚 文档结构

### 🔴 核心文档（必读）

1. **[README.md](./README.md)** - 项目简介与快速开始
2. **[CLAUDE.md](./docs/implementation/CLAUDE.md)** - Claude Code 工作指南
3. **[TECH_STACK.md](./docs/architecture/TECH_STACK.md)** - 技术栈与架构文档

---

### 🆕 版本文档（v1.1.4 新增）

4. **[CHANGELOG.md](./docs/release-notes/CHANGELOG.md)** - 完整变更日志
5. **[RELEASE_NOTES.md](./docs/release-notes/RELEASE_NOTES.md)** - 用户版本说明
6. **[HENTAI_SITES_TECH_DOC.md](./docs/implementation/HENTAI_SITES_TECH_DOC.md)** - Hentai 站点技术实现

---

### 🟡 规划文档（开发参考）

7. **[FEATURES.md](./docs/FEATURES.md)** - 完整功能清单
8. **[ROADMAP.md](./docs/roadmap/ROADMAP.md)** - 开发路线图
9. **[IMPLEMENTATION_GUIDE.md](./docs/implementation/IMPLEMENTATION_GUIDE.md)** - Android 实施指南

---

### 🟢 安全文档

10. **[SECURITY_AUDIT.md](./docs/security/SECURITY_AUDIT.md)** - 安全审计报告

---

### 🔵 设计文档

11. **[UI_DESIGN_ANALYSIS.md](./docs/design/UI_DESIGN_ANALYSIS.md)** - UI 设计分析
12. **[UI_REFACTOR_COMPLETE.md](./docs/design/UI_REFACTOR_COMPLETE.md)** - UI 重构完成报告
13. **[ui-md3-modernization-log.md](./docs/design/ui-md3-modernization-log.md)** - MD3 改造记录

---

### 🟣 部署文档

14. **[AGENTS.md](./docs/deployment/AGENTS.md)** - CI/CD 配置

---

### 🟠 研究存档

15. **[javbrowser-extension-proposal.md](./docs/community/javbrowser-extension-proposal.md)** - 功能扩展研究（Python 项目算法借鉴）

---

## 📖 阅读路径

### 新成员 Onboarding
```
1. README.md                              （了解项目）
   ↓
2. docs/release-notes/RELEASE_NOTES.md     （最新更新）
   ↓
3. docs/implementation/CLAUDE.md           （开发环境）
   ↓
4. docs/architecture/TECH_STACK.md         （技术架构）
   ↓
5. docs/FEATURES.md                       （功能清单）
   ↓
6. docs/security/SECURITY_AUDIT.md         （安全规范）
```

### 了解最新版本
```
1. docs/release-notes/RELEASE_NOTES.md     （快速了解更新）
   ↓
2. docs/release-notes/CHANGELOG.md         （详细技术变更）
   ↓
3. docs/implementation/HENTAI_SITES_TECH_DOC.md （新功能技术细节）
```

### 功能开发
```
1. docs/FEATURES.md                       （确认功能需求）
   ↓
2. docs/roadmap/ROADMAP.md                （查看优先级）
   ↓
3. docs/implementation/IMPLEMENTATION_GUIDE.md （实施方案）
   ↓
4. docs/implementation/HENTAI_SITES_TECH_DOC.md （视频提取技术）
   ↓
5. docs/community/javbrowser-extension-proposal.md （算法参考）
```

### 技术决策
```
1. docs/architecture/TECH_STACK.md         （当前技术栈）
   ↓
2. docs/release-notes/CHANGELOG.md         （最新改进）
   ↓
3. docs/roadmap/ROADMAP.md                （技术债务）
   ↓
4. docs/security/SECURITY_AUDIT.md         （安全风险）
   ↓
5. docs/implementation/IMPLEMENTATION_GUIDE.md （实施风险）
```

---

## ⚠️ 重要说明

### 技术栈一致性
- ✅ **当前项目**: Android Native App (Kotlin)
- ✅ **核心文档**: 100% 基于 Android/Kotlin 技术栈
- ⚠️ **研究文档**: 包含 Python/浏览器扩展内容（已标注，仅供算法借鉴）
- ⚠️ **长期愿景**: iOS/桌面版为未来方向（已标注）

### 文档维护规范
1. 新增功能时同步更新 `FEATURES.md`
2. 修复技术债务时更新 `ROADMAP.md`
3. 发现安全问题时更新 `SECURITY_AUDIT.md`
4. 引用外部项目时必须标注技术栈

---

## 📊 文档统计

| 类型 | 数量 | 说明 |
|------|------|------|
| 核心文档 | 3 | README, CLAUDE, TECH_STACK |
| 实现文档 | 5 | IMPLEMENTATION_GUIDE, JAVBROWSER_IMPLEMENTATION_PLAN, FEATURE_IMPLEMENTATION_SPECS, HENTAI_SITES_TECH_DOC, UI_REFACTOR_COMPLETE |
| 设计文档 | 3 | UI_DESIGN_ANALYSIS, UI_REFACTOR_COMPLETE, ui-md3-modernization-log |
| 发布文档 | 3 | CHANGELOG, RELEASE_NOTES, UPDATE_SUMMARY |
| 路线图文档 | 1 | ROADMAP |
| 安全文档 | 1 | SECURITY_AUDIT |
| 部署文档 | 1 | AGENTS |
| 社区文档 | 1 | javbrowser-extension-proposal |
| 架构文档 | 1 | TECH_STACK |
| **总计** | **19** | |

---

## 🗂️ 完整文档目录结构

```
docs/
├── README.md                          # 文档入口与索引
├── FEATURES.md                        # 功能清单
│
├── architecture/
│   └── TECH_STACK.md                  # 技术栈与架构
│
├── deployment/
│   └── AGENTS.md                      # CI/CD 配置
│
├── design/
│   ├── UI_DESIGN_ANALYSIS.md          # UI 设计分析
│   ├── UI_REFACTOR_COMPLETE.md        # UI 重构完成报告
│   └── ui-md3-modernization-log.md    # MD3 改造记录
│
├── implementation/
│   ├── CLAUDE.md                      # 开发环境指南
│   ├── IMPLEMENTATION_GUIDE.md        # 实施指南
│   ├── JAVBROWSER_IMPLEMENTATION_PLAN.md # 实施计划
│   ├── FEATURE_IMPLEMENTATION_SPECS.md # 功能规格
│   └── HENTAI_SITES_TECH_DOC.md       # 站点技术文档
│
├── release-notes/
│   ├── CHANGELOG.md                   # 变更日志
│   ├── RELEASE_NOTES.md              # 发布说明
│   └── UPDATE_SUMMARY.md             # 更新摘要
│
├── roadmap/
│   └── ROADMAP.md                     # 开发路线图
│
├── security/
│   └── SECURITY_AUDIT.md             # 安全审计
│
└── community/
    └── javbrowser-extension-proposal.md # 扩展提案
```

---

**最后更新**: 2026-06-08
**维护者**: development team
**下次审查**: 每季度或重大版本发布前
