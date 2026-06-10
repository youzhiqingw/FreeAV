# JAVBrowser Material Design 3 设计规范

**版本：** v1.1.5  
**最后更新：** 2026-06-09  
**设计系统：** Material Design 3 (Material You)

---

## 🎨 颜色系统

### Brand Colors (品牌色)

```
Primary (主色 - 品牌绿)
├─ Light: #5DAC81
└─ Dark:  #A8D5BA

Secondary (辅助色 - 品牌橙)
├─ Light: #FF753F
└─ Dark:  #FFB499

Tertiary (第三色 - 青蓝)
├─ Light: #4FC3F7
└─ Dark:  #81D4FA
```

### Surface Colors (表面色)

#### 浅色模式
```
Surface:                #FFFBFE
Surface Container:      #F3EDF7
Surface Container Low:  #F7F2FA
Surface Container High: #ECE6F0
Surface Variant:        #E7E0EC
```

#### 深色模式
```
Surface:                #1C1B1F
Surface Container:      #211F26
Surface Container Low:  #1D1B20
Surface Container High: #2B2930
Surface Variant:        #49454F
```

### Text Colors (文字色)

```
浅色模式:
├─ On Surface:         #1D1B20 (主要文字)
└─ On Surface Variant: #49454F (次要文字)

深色模式:
├─ On Surface:         #E6E0E9 (主要文字)
└─ On Surface Variant: #CAC4D0 (次要文字)
```

### Outline Colors (边框色)

```
浅色模式:
├─ Outline:         #79747E (标准边框)
└─ Outline Variant: #CAC4D0 (淡化边框)

深色模式:
├─ Outline:         #938F99 (标准边框)
└─ Outline Variant: #49454F (淡化边框)
```

---

## 📐 圆角规范 (Corner Radius)

### 组件圆角
```
Extra Small: 4dp   (Chip, Badge)
Small:       8dp   (图标背景)
Medium:      12dp  (Card 卡片, Button 按钮)
Large:       16dp  (FAB, Dialog 对话框)
Extra Large: 24dp  (Bottom Sheet, 导航栏顶部, 设置卡片)
Full:        28dp  (搜索栏, Pill Button)
```

### 具体应用
```
底部导航栏:      24dp (顶部左右圆角)
设置页卡片:      24dp (四角)
收藏项卡片:      12dp (四角)
播放按钮:        20dp (四角)
搜索栏:          28dp (全圆角)
PIN 键盘按钮:    40dp (圆形)
图标背景圆圈:    50% (完全圆形)
```

---

## 📏 间距规范 (Spacing)

### 标准间距单位
```
spacing_xs:  4dp   (紧密间距)
spacing_sm:  8dp   (小间距)
spacing_md:  16dp  (标准间距)
spacing_lg:  24dp  (大间距)
spacing_xl:  32dp  (特大间距)
```

### 内边距 (Padding)
```
卡片内边距:       16-20dp
按钮内边距:       12-16dp (横向), 8-12dp (纵向)
列表项内边距:     16dp (左右), 12dp (上下)
页面边距:         16dp (手机), 24dp (平板)
```

### 外边距 (Margin)
```
卡片间距:         8-16dp
章节间距:         24dp
页面顶部间距:     16dp
页面底部间距:     100dp (为底部导航栏留空)
```

---

## 🔲 阴影规范 (Elevation)

### 阴影层级
```
Level 0:  0dp   (Flat, 平面元素)
Level 1:  1dp   (Card 卡片)
Level 2:  2dp   (FAB 浮动按钮)
Level 3:  3dp   (App Bar 应用栏)
Level 4:  4dp   (Dialog 对话框)
Level 5:  8dp   (Navigation Drawer 抽屉)
```

### 具体应用
```
设置卡片:        elevation_sm (1dp)
收藏卡片:        0dp (使用 Filled Card)
FAB:             elevation_md (2dp)
对话框:          elevation_lg (4dp)
底部导航栏:      0dp (使用边框分隔)
```

---

## 🎯 触摸目标 (Touch Target)

### 最小尺寸
```
标准触摸目标:    48dp × 48dp (Material Design 基准)
导航按钮:        48dp × 48dp
工具栏按钮:      48dp × 48dp
列表项:          48dp (最小高度)
PIN 键盘:        80dp × 80dp (更大更易按)
```

### 间距要求
```
按钮之间:        ≥8dp
可点击元素:      ≥8dp
边缘到元素:      ≥12dp
```

---

## 📝 字体规范 (Typography)

### 字体家族
```
Display:  Roboto (粗体, 大标题)
Body:     Roboto (正文)
Label:    Roboto (标签, 按钮)
```

### 字体大小
```
Display Large:    57sp (大型展示标题)
Display Medium:   45sp
Display Small:    36sp

Headline Large:   32sp (页面标题)
Headline Medium:  28sp
Headline Small:   24sp (卡片标题)

Title Large:      22sp (列表标题)
Title Medium:     16sp
Title Small:      14sp

Body Large:       16sp (正文)
Body Medium:      14sp (次要正文)
Body Small:       12sp (说明文字)

Label Large:      14sp (按钮文字)
Label Medium:     12sp (标签)
Label Small:      11sp (辅助标签)
```

### 字重 (Font Weight)
```
Regular:  400 (正文)
Medium:   500 (标题, 按钮)
Bold:     700 (强调标题)
```

### 行高 (Line Height)
```
Display:   1.2 (紧凑)
Headline:  1.3
Title:     1.4
Body:      1.5 (舒适阅读)
Label:     1.4
```

---

## 🧩 组件规范

### 底部导航栏 (Bottom Navigation)
```
高度:            80dp
背景:            colorSurfaceContainer
顶部圆角:        24dp (左右)
分隔线:          1dp, colorOutlineVariant
图标大小:        24dp
标签大小:        12sp (Label Medium)
按钮数量:        3 个 (浏览, 收藏, 设置)
活动指示器:      64dp × 32dp 圆角矩形
```

### 顶部工具栏 (Top App Bar)
```
高度:            56dp (标准), 64dp (大型)
背景:            colorSurfaceContainerLow
图标按钮:        48dp × 48dp
标题大小:        22sp (Title Large)
标题居中:        是 (Material 3 Center-aligned)
返回按钮:        左侧 4dp
```

### 卡片 (Card)
```
设置卡片:
├─ 类型:         Elevated Card
├─ 圆角:         24dp
├─ 阴影:         1dp
├─ 内边距:       16-20dp
└─ 间距:         8dp

收藏卡片:
├─ 类型:         Filled Card
├─ 圆角:         12dp
├─ 阴影:         0dp
├─ 内边距:       12dp
├─ 宽高比:       16:9 (缩略图)
└─ 布局:         2 列网格 (手机), 3-4 列 (平板)
```

### 按钮 (Button)
```
Icon Button:
├─ 尺寸:         48dp × 48dp (圆形)
├─ 图标:         24dp
├─ 悬停:         8% 半透明遮罩
└─ 点击:         12% 半透明遮罩

Text Button:
├─ 高度:         40dp
├─ 内边距:       12dp (横向)
├─ 圆角:         20dp
└─ 文字:         14sp (Label Large)

Tonal Button:
├─ 高度:         40dp
├─ 内边距:       16-24dp (横向)
├─ 圆角:         20dp
├─ 背景:         colorSecondaryContainer
└─ 文字:         colorOnSecondaryContainer

Extended FAB:
├─ 高度:         56dp
├─ 内边距:       16-20dp (横向)
├─ 圆角:         16dp
├─ 图标:         24dp
├─ 文字:         14sp (Label Large)
├─ 阴影:         2dp
└─ 位置:         右下角, margin 16dp
```

### 输入框 (Text Field)
```
搜索栏:
├─ 高度:         56dp
├─ 圆角:         28dp (全圆角)
├─ 背景:         colorSurfaceVariant
├─ 内边距:       16-20dp (横向)
├─ 文字:         16sp (Body Large)
└─ 提示:         colorOnSurfaceVariant

Outlined Text Field:
├─ 高度:         56dp
├─ 边框:         1dp, colorOutline
├─ 圆角:         4dp (顶部)
├─ 聚焦边框:     2dp, colorPrimary
└─ 文字:         16sp
```

### 列表项 (List Item)
```
单行:
├─ 高度:         56dp
├─ 内边距:       16dp (横向)
└─ 文字:         16sp (Body Large)

两行:
├─ 高度:         72dp
├─ 标题:         16sp (Body Large)
└─ 副标题:       14sp (Body Medium)

三行:
├─ 高度:         88dp
├─ 标题:         16sp
└─ 副标题:       14sp (最多2行)
```

---

## 🎭 状态样式

### 按钮状态
```
Normal:   默认样式
Hover:    背景透明度 +8%
Pressed:  背景透明度 +12%, 缩放 0.95
Disabled: 透明度 38%
```

### 导航按钮状态
```
Unselected:
├─ 图标:         colorOnSurfaceVariant
├─ 文字:         colorOnSurfaceVariant
└─ 背景:         透明

Selected:
├─ 图标:         colorPrimary (或 colorOnSecondaryContainer)
├─ 文字:         colorOnSurface (加粗)
├─ 背景:         colorSecondaryContainer
└─ 指示器:       64dp × 32dp 圆角矩形
```

### 卡片状态
```
Normal:   默认样式
Hover:    阴影提升 2dp, 上移 2-4dp
Pressed:  缩放 0.98
```

---

## 📱 响应式设计

### 断点 (Breakpoints)
```
Compact:     0-599dp   (手机竖屏)
Medium:      600-839dp (手机横屏, 小平板)
Expanded:    840-1279dp (平板)
Large:       1280dp+   (桌面, 大平板)
Extra Large: 1920dp+   (超大屏)
```

### 布局调整
```
导航栏:
├─ Compact:     底部导航 (3 按钮)
├─ Medium:      底部导航 (3 按钮)
├─ Expanded:    侧边导航 (Rail) 或底部导航
└─ Large:       侧边导航 (Drawer)

收藏网格:
├─ Compact:     2 列
├─ Medium:      3 列
├─ Expanded:    4 列
└─ Large:       5 列

边距:
├─ Compact:     16dp
├─ Medium:      24dp
├─ Expanded:    32dp
└─ Large:       48dp
```

---

## 🌗 深浅模式

### 切换行为
```
跟随系统:        默认行为 (DayNight 主题)
用户选择:        可在设置中覆盖系统设置
平滑过渡:        颜色变化使用 200ms 过渡动画
```

### 颜色映射
```
所有颜色使用主题属性引用:
✅ ?attr/colorPrimary
✅ ?attr/colorSurface
❌ @color/green (硬编码颜色)
```

---

## ♿ 无障碍设计

### 对比度要求
```
正文文字:        ≥4.5:1 (WCAG AA)
大型文字:        ≥3:1
图标按钮:        ≥3:1
活动状态:        不仅依赖颜色，需形状/位置变化
```

### ContentDescription
```
所有图标按钮:    必须提供 contentDescription
装饰性图片:      contentDescription=""
信息性图片:      描述图片内容
```

### 触摸目标
```
最小尺寸:        48dp × 48dp
推荐尺寸:        56dp × 56dp (FAB)
间距:            ≥8dp
```

---

## 🎬 动画规范

### 时长 (Duration)
```
Fast:            100ms  (小型状态变化)
Normal:          200ms  (按钮, 卡片悬停)
Medium:          300ms  (页面切换)
Slow:            400ms  (大型元素进出)
```

### 缓动曲线 (Easing)
```
Standard:        cubic-bezier(0.4, 0, 0.2, 1)  (通用)
Decelerate:      cubic-bezier(0, 0, 0.2, 1)    (进入)
Accelerate:      cubic-bezier(0.4, 0, 1, 1)    (退出)
Sharp:           cubic-bezier(0.4, 0, 0.6, 1)  (快速变化)
```

### 具体应用
```
按钮点击:        缩放 0.95, 120ms
卡片悬停:        上移 4dp, 200ms, Standard
页面切换:        淡入淡出, 300ms, Standard
展开面板:        高度变化, 300ms, Decelerate
底部弹窗:        上滑, 400ms, Decelerate
```

---

## 🚀 性能优化

### 图片加载
```
缩略图:          使用 Glide 加载
占位符:          显示图标占位
渐进式:          先加载低质量, 再加载高清
缓存:            内存缓存 + 磁盘缓存
```

### 列表渲染
```
RecyclerView:    使用 DiffUtil 高效更新
ViewHolder:      复用视图
分页:            每页 20-50 项
预加载:          提前 5 项开始加载
```

### 动画性能
```
硬件加速:        启用 (默认)
避免过度绘制:    使用 GPU Overdraw 检测
图层缓存:        频繁动画的视图
```

---

## 📋 设计检查清单

### 颜色
- [ ] 使用主题属性而非硬编码颜色
- [ ] 深浅模式颜色一致
- [ ] 对比度符合 WCAG 标准
- [ ] 品牌色应用正确

### 布局
- [ ] 底部导航栏固定在底部
- [ ] 内容区可滚动
- [ ] 间距符合规范
- [ ] 响应式布局

### 交互
- [ ] 触摸目标 ≥48dp
- [ ] 按钮有状态反馈
- [ ] 跳转逻辑正确
- [ ] 返回按钮功能正常

### 无障碍
- [ ] 图标有 contentDescription
- [ ] 颜色对比度达标
- [ ] 支持大字体
- [ ] 支持屏幕阅读器

### 性能
- [ ] 图片使用压缩格式
- [ ] 列表使用虚拟滚动
- [ ] 动画流畅 (60fps)
- [ ] 首屏加载 <2s

---

**文档版本：** v1.0  
**维护者：** JAVBrowser Team  
**参考标准：** [Material Design 3 Guidelines](https://m3.material.io/)
