# 文档归档说明

本目录包含已整合的旧版文档，仅供参考。

## 归档文件

### 广告屏蔽与隐私保护
- **AD_BLOCK_GOALS.md** (归档日期: 2026-06-09)
  - 原因: 内容已整合到 `PRIVACY_AND_ADBLOCK.md`
  - 状态: 理论设计，部分未实现

- **AD_BLOCKING_ENHANCED.md** (归档日期: 2026-06-09)
  - 原因: 内容已整合到 `PRIVACY_AND_ADBLOCK.md`
  - 状态: 详细实现方案，过于复杂，部分内容已简化

- **PRIVACY_GOALS.md** (归档日期: 2026-06-09)
  - 原因: 内容已整合到 `PRIVACY_AND_ADBLOCK.md`
  - 状态: 8个隐私模块方案，已根据实际实现调整优先级

### UI设计文档
- **UI_UX_DESIGN_SPEC.md** (归档日期: 2026-06-09)
  - 原因: 内容已重构为 `DESIGN_SYSTEM_MD3.md`
  - 状态: 基于旧版Material Design，新版基于Material Design 3和freeav原型

- **UI_LOGIC_RELATIONSHIP.md** (归档日期: 2026-06-09)
  - 原因: 内容已整合到新设计指南
  - 状态: UI逻辑关系说明，部分过时

- **UI_REFACTOR_PLAN.md** (归档日期: 2026-06-09)
  - 原因: 重构已完成，计划文档归档
  - 状态: 历史重构计划记录

- **UI_DESIGN_ANALYSIS.md** (归档日期: 2026-06-10)
  - 原因: 描述的是 Kikoeru 开源项目（不同项目），非本项目的设计分析
  - 状态: 外部参考，仅供参考 Kikoeru 的 UI 模式

- **UI_REFACTOR_COMPLETE.md** (归档日期: 2026-06-10)
  - 原因: 描述旧 4-tab 导航结构，已被 `MD3_REFACTOR_SUMMARY.md` 替代
  - 状态: 内容过时

- **UI_DESIGN_UPDATE_2026-06-09.md** (归档日期: 2026-06-10)
  - 原因: 临时整理记录，内容已合并到其他文档
  - 状态: 历史参考

- **javbrowser-extension-proposal.md** (归档日期: 2026-06-10)
  - 原因: 外部研究提案，使用旧包名，与当前开发方向不直接相关
  - 状态: 外部参考

## 当前有效文档

请参考以下文档：

### 主文档
- **CLAUDE.md** (项目根目录) - 项目总览和开发指南
- **PRIVACY_AND_ADBLOCK.md** - 隐私保护与广告屏蔽统一方案
- **AD_BLOCKING_ARCHITECTURE.md** - 双引擎架构技术细节

### UI设计文档
- **DESIGN_SYSTEM_MD3.md** - Material Design 3 设计规范（当前版本）
- **MD3_REFACTOR_SUMMARY.md** - MD3 重构完成总结报告
- **FRONTEND_CODE_REVIEW.md** - 前端代码审查报告

## 整合变更说明

### 主要改进

#### 广告屏蔽与隐私保护（2026-06-09）
1. **统一架构**：将广告屏蔽和隐私保护整合为统一文档
2. **对齐实现**：基于 AdFilterRules.kt 实际代码，移除未实现的理论设计
3. **优先级明确**：根据价值和实现难度重新排序功能优先级
4. **减少冗余**：合并重复内容，保留实用技术方案

#### UI设计（2026-06-09）
1. **Material Design 3升级**：从MD2升级到MD3，采用最新设计语言
2. **基于freeav原型**：参考freeav文件夹中的HTML原型设计
3. **对齐实际代码**：所有示例基于项目实际布局文件
4. **简化内容**：移除过时设计，保留实用指南

### 保留内容

**广告屏蔽与隐私保护**：
- ✅ 双引擎广告屏蔽架构（JSON + Adblock Plus）
- ✅ 元素隐藏和动态监听方案
- ✅ 8个隐私保护模块设计（P-01 到 P-08）
- ✅ 性能优化建议（Bloom Filter等）
- ✅ 开发指南和调试方法

**UI设计**：
- ✅ Material Design 3完整主题系统
- ✅ 颜色、字体、间距、圆角规范
- ✅ 组件库（按钮、卡片、导航栏等）
- ✅ 页面设计规范（MainActivity、Settings等）
- ✅ 交互与动画指南
- ✅ 实施代码示例

### 简化内容

**广告屏蔽与隐私保护**：
- ❌ FilterListConfig/FilterListParser 复杂设计 → 保留现有 AdFilterRules.kt
- ❌ 过多规则订阅源配置 → 简化为当前使用的源
- ❌ WorkManager 后台同步 → 保留现有更新机制
- ❌ 未验证的 DOM 规则映射系统 → 保留通用 CSS 注入

**UI设计**：
- ❌ 详细的Kikoeru UI分析 → 简化为设计原则
- ❌ 过时的Material 2规范 → 升级到Material 3
- ❌ 复杂的导航架构 → 保留现有底部导航
- ❌ 未实现的搜索功能 → 标注为待实现

### 新增内容

**广告屏蔽与隐私保护**：
- ✅ 实际代码示例（基于 AdFilterRules.kt）
- ✅ 故障排查指南
- ✅ 安全守卫清单
- ✅ 配置文件说明

**UI设计**：
- ✅ Material Design 3完整Token系统
- ✅ 基于freeav原型的实际设计
- ✅ 现有代码改进建议
- ✅ 响应式布局规范
- ✅ 触摸反馈和状态层规范

---

**注意**：归档文档中的方案仍可作为未来功能扩展的参考，但实现前需评估复杂度和实际价值。

**最后更新**: 2026-06-09 18:30

