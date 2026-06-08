# Kikoeru Android - 界面设计风格分析文档

## 一、主题风格 (Theme Style)

### 1.1 日间主题 (Light Theme)
**文件路径：** `app/src/main/res/values/themes.xml`

```xml
Theme.Kikoeru (MaterialComponents.DayNight.DarkActionBar)
```

**主要配色：**
- **主色调 (Primary)**: `#5DAC81` (green_40) - 薄荷绿
- **主色调变体 (PrimaryVariant)**: `#5DAC81` 
- **次级色 (Secondary)**: `#FF753F` (pine_700) - 橙红色
- **次级色变体 (SecondaryVariant)**: `#FF753F`
- **状态栏颜色**: 主色调变体
- **导航栏颜色**: 主色调
- **文字颜色**: `#FF000000` (黑色)

### 1.2 夜间主题 (Dark Theme)
**文件路径：** `app/src/main/res/values-night/themes.xml`

**主要配色：**
- **主色调 (Primary)**: `#5DAC81` (main_color) - 薄荷绿
- **主色调变体 (PrimaryVariant)**: `#FF000000` (黑色)
- **次级色 (Secondary)**: `#5DAC81` 
- **次级色变体 (SecondaryVariant)**: `#FF753F` (pine_700)
- **状态栏颜色**: 黑色
- **文字颜色**: `#FFFFFFFF` (白色)

### 1.3 完整配色板 (Color Palette)
**文件路径：** `app/src/main/res/values/colors.xml`

| 颜色名称 | 色值 | 用途 |
|---------|------|------|
| main_color / green_40 | `#5DAC81` | 主色调 - 薄荷绿 |
| pure_80 | `#9B8EC7` | 紫色辅助色 |
| pure_50 | `#BDA6CE` | 浅紫色 |
| gray | `#B4D3D9` | 灰蓝色 |
| orange | `#F2EAE0` | 米橙色 |
| black | `#FF000000` | 黑色 |
| black_tran | `#8A000000` | 半透明黑色 |
| black_tran_dark | `#B3000000` | 深半透明黑色 |
| white | `#FFFFFFFF` | 白色 |
| white_tran | `#40FFFFFF` | 半透明白色 |
| play_control_icon_color | `#FFFFFFFF` | 播放控制图标颜色 |
| pine_700 | `#FF753F` | 橙红色 |

**色板灵感来源：** https://colorhunt.co/palette/091413285a48408a71b0e4cc

---

## 二、主要界面结构

### 2.1 主页 (WorksActivity / MainActivity)
**布局文件：** `app/src/main/res/layout/activity_main.xml`
**Activity文件：** `app/src/main/java/com/zinhao/kikoeru/WorksActivity.java`

#### 界面组成：
1. **RecyclerView** - 作品列表展示区域
   - 支持4种布局模式：
     - 列表布局 (List Layout)
     - 小网格布局 (Cover Grid - 3列)
     - 大网格布局 (Detail Grid - 2列)
     - 瀑布流布局 (Staggered - 2列)

2. **底部播放控制栏** (layout_current_play)
   - 封面图片 (52x52dp)
   - 标题和副标题
   - 播放/暂停按钮
   - 歌词显示按钮
   - 背景：`card_bg_current_play` (主色调圆角卡片)
   - 动画：上下滑动出入动画

3. **底部导航栏** (60dp高度)
   - 背景色：`colorPrimaryVariant` (夜间模式为黑色)
   - 三个图标按钮：
     - **bt1**: 所有作品 (ic_baseline_widgets_24)
     - **bt2**: 收藏/进度 (ic_baseline_favorite_24)
     - **bt3**: 更多选项 (ic_baseline_more_horiz_24)

#### 功能区域：

**bt1 - 全部作品：**
- 点击回到所有作品列表

**bt2 - 进度筛选菜单 (ListPopupWindow)：**
- MARKED (已标记)
- LISTENING (正在听)
- LISTENED (已听过)
- REPLAY (重听)
- POSTPONED (延后)

**bt3 - 更多筛选菜单 (ListPopupWindow)：**
- VA (声优)
- Tag (标签)
- Circles (社团)
- Local Works (本地作品)

#### 顶部菜单栏选项：
- 切换账号
- 布局切换 (列表/小网格/大网格/瀑布流)
- 排序方式：
  - 发布日期 (Release Date)
  - RJ编号 (RJ Number)
  - 价格 (Prize)
  - 入库时间 (Last in Lib)
- 下载任务 (Download Mission)
- 历史记录 (Local History)
- 搜索 (Search) - 始终显示图标
- 更多设置 (More)

---

### 2.2 搜索页面 (SearchActivity)
**布局文件：** `app/src/main/res/layout/activity_search.xml`
**Activity文件：** `app/src/main/java/com/zinhao/kikoeru/SearchActivity.java`

#### 界面组成：
1. **RecyclerView** - 搜索结果列表
   - 占据大部分屏幕空间
   
2. **底部搜索输入框** (60dp高度)
   - EditText - 数字输入类型
   - 提示文字：RJ编号
   - 居中对齐
   - 自动输入6位数字后触发搜索

#### 交互逻辑：
- 进入页面自动弹出键盘
- 输入6位数字自动搜索对应RJ号作品
- 点击标签可跳转到主页的标签筛选
- 点击声优可跳转到主页的声优筛选

---

### 2.3 设置页面 (MoreActivity)
**布局文件：** `app/src/main/res/layout/activity_more.xml`
**Activity文件：** `app/src/main/java/com/zinhao/kikoeru/MoreActivity.java`

#### 界面组成：
垂直线性布局，包含5个RelativeLayout选项卡（70dp高度）：

1. **只加载有歌词的作品** (Only load have lrc)
   - CheckBox控件
   - 背景：item_bg (水波纹效果)
   - 底部分割线：black_tran

2. **开源许可证** (Open Source License)
   - 点击跳转到LicenseActivity
   - 底部分割线

3. **保存到外部存储** (Save at external storage)
   - CheckBox控件
   - 需要存储权限
   - 底部分割线

4. **关于** (About)
   - 点击跳转到AboutActivity
   - 底部分割线

5. **调试模式** (Debug Mode)
   - CheckBox控件
   - 底部分割线

#### 设计风格：
- 统一的列表项高度（70dp）
- 左侧文字（20sp，居中对齐，左边距20dp）
- 右侧CheckBox（右边距20dp）
- 半透明黑色分割线
- 卡片式背景（item_bg - 水波纹点击效果）

---

### 2.4 播放器页面 (AudioPlayerActivity)
**布局文件：** `app/src/main/res/layout/activity_player.xml`

#### 界面组成：
1. **封面图片** (200dp高度)
   - 顶部15dp边距
   - 居中显示
   - 共享元素动画 (hero_bottom)

2. **歌词显示区域** (三行TextView)
   - **tvUpLrc**: 上一句歌词 (18sp, 半透明白色)
   - **tvLrc**: 当前歌词 (22sp, 白色, 阴影效果)
   - **tvNextLrc**: 下一句歌词 (18sp, 半透明白色)

3. **进度条** (TimeProgressView, 45dp高度)
   - 左右24dp边距

4. **播放控制按钮组** (底部对齐)
   - **循环播放** (ibLoop - 48dp)
   - **上一曲** (ib1 - 48dp)
   - **播放/暂停** (ib2 - 68dp，中心按钮，更大)
   - **下一曲** (ib3 - 48dp)
   - **歌词显示** (imageButton2 - 48dp)
   - 按钮间距：24dp
   - 背景：ripple_white_bg (白色水波纹)

5. **背景**
   - player_bg 渐变背景

---

## 三、界面跳转逻辑图

```
┌─────────────────────────────────────────────────────────────┐
│                    LauncherActivity (启动页)                  │
│                           ↓                                   │
│              检查用户登录状态 / 首次启动                        │
└─────────────────────────────────────────────────────────────┘
                              ↓
        ┌─────────────────────┴─────────────────────┐
        ↓                                           ↓
┌───────────────────┐                    ┌──────────────────┐
│ LoginAccountActivity│                    │ WorksActivity    │
│   (登录页面)         │──登录成功──→      │   (主页)         │
└───────────────────┘                    └──────────────────┘
                                                  │
                    ┌─────────────────────────────┼─────────────────────────────┐
                    ↓                             ↓                             ↓
            ┌──────────────┐            ┌──────────────────┐         ┌─────────────────┐
            │ SearchActivity│            │  WorkTreeActivity │         │   MoreActivity   │
            │  (搜索页面)    │            │  (作品详情树)      │         │   (设置页面)     │
            └──────────────┘            └──────────────────┘         └─────────────────┘
                    │                             │                            │
                    │                             │                   ┌────────┼────────┐
                    │                             ↓                   ↓                 ↓
                    │                  ┌──────────────────┐   ┌──────────────┐  ┌────────────┐
                    │                  │AudioPlayerActivity│   │AboutActivity │  │LicenseActivity│
                    │                  │  (音频播放器)      │   │  (关于页面)  │  │(开源许可证) │
                    │                  └──────────────────┘   └──────────────┘  └────────────┘
                    │                             │
                    └──────┬──────────────────────┘
                           ↓
                ┌──────────────────────┐
                │  LrcShowActivity      │
                │  (全屏歌词显示)        │
                └──────────────────────┘

其他功能页面：
├─ VasActivity (声优列表)
├─ TagsActivity (标签列表)
├─ CirclesActivity (社团列表)
├─ DownLoadMissionActivity (下载任务)
├─ LastWatchActivity (历史记录)
├─ ImageBrowserActivity (图片浏览)
├─ VideoPlayerActivity (视频播放器)
└─ UserSwitchActivity (用户切换)
```

---

## 四、核心设计元素

### 4.1 卡片背景样式

| 文件名 | 颜色 | 圆角 | 用途 |
|--------|------|------|------|
| card_bg_work.xml | colorOnPrimary | 5-10dp | 作品卡片背景 |
| card_bg_current_play.xml | colorPrimary | 5dp (顶部) | 当前播放卡片 |
| card_bg_tag.xml | colorPrimaryVariant | 15dp | 标签背景 |
| card_bg_va.xml | colorSecondaryVariant | 5dp | 声优标签背景 |
| card_bg_circles.xml | colorOnSecondary | 5dp | 社团标签背景 |
| card_bg_lrc.xml | - | - | 歌词背景 |
| item_bg.xml | 水波纹效果 | - | 通用项背景 |
| player_bg.xml | - | - | 播放器背景 |

### 4.2 动画效果
**文件路径：** `app/src/main/res/anim/`
- **move_bottom_in.xml** - 从底部滑入
- **move_bottom_out.xml** - 向底部滑出
- 用于底部播放控制栏的显示/隐藏动画

### 4.3 共享元素转场动画
- **hero_image** - 作品封面点击到详情页
- **hero_bottom** - 底部播放栏点击到播放器页面

---

## 五、主要图标资源

### 5.1 播放控制类
- `ic_baseline_play_arrow_24` - 播放（黑/白色版本）
- `ic_baseline_pause_24` - 暂停（黑/白色版本）
- `ic_baseline_skip_next_24` - 下一曲
- `ic_baseline_skip_previous_24` - 上一曲
- `ic_baseline_loop_24` - 循环播放
- `ic_baseline_volume_up_24` - 音量

### 5.2 导航与功能类
- `ic_baseline_widgets_24` - 所有作品
- `ic_baseline_favorite_24` - 收藏
- `ic_baseline_more_horiz_24` - 更多
- `ic_baseline_search_24` - 搜索
- `ic_baseline_text_fields_24` - 歌词显示
- `ic_baseline_view_column_24` - 布局切换

### 5.3 内容类型类
- `ic_baseline_audiotrack_24` - 音频
- `ic_baseline_video_library_24` - 视频
- `ic_baseline_image_24` - 图片
- `ic_baseline_folder_24` - 文件夹
- `ic_baseline_people_24` - 声优
- `ic_baseline_tag_24` - 标签
- `ic_baseline_work_24` - 作品

### 5.4 操作类
- `ic_baseline_sort_24` - 排序
- `ic_baseline_delete_forever_24` - 删除（黑/白色版本）
- `ic_baseline_close_24` - 关闭
- `ic_baseline_flip_camera_android_24` - 翻转

---

## 六、设计特点总结

### 6.1 色彩风格
- **主色调**：薄荷绿 (#5DAC81) - 清新、舒适
- **辅助色**：橙红色 (#FF753F) - 活力、重点强调
- **支持完整日/夜间模式切换**
- 采用Material Design规范

### 6.2 布局特点
- **卡片式设计**：所有内容以圆角卡片形式呈现
- **底部导航**：采用固定底部导航栏，方便单手操作
- **多种列表布局**：支持列表、网格、瀑布流等多种展示方式
- **悬浮播放控制**：底部播放条可以在任何页面显示

### 6.3 交互特点
- **水波纹反馈**：所有可点击元素都有水波纹点击效果
- **共享元素动画**：页面跳转使用共享元素转场动画，流畅自然
- **滑动动画**：底部播放栏使用滑入滑出动画
- **即时反馈**：搜索页面输入6位数字自动触发

### 6.4 细节设计
- **半透明元素**：使用半透明黑色/白色营造层次感
- **一致的间距**：统一使用8dp倍数作为边距和间距
- **文字大小分级**：标题20-22sp，正文16-18sp，小字14sp
- **分割线**：使用1dp半透明黑色线条分隔内容

---

## 七、设置功能清单

从 `MoreActivity` 中包含的功能：

1. ✅ **只加载有歌词的作品** - 过滤显示选项
2. ✅ **开源许可证** - 查看使用的开源项目
3. ✅ **保存到外部存储** - 存储位置选择（需要权限）
4. ✅ **关于** - 应用信息
5. ✅ **调试模式** - 开发者选项

从主页菜单可访问的功能：
- ✅ 切换账号
- ✅ 布局模式切换（4种）
- ✅ 排序方式（4种）
- ✅ 下载任务管理
- ✅ 本地历史记录
- ✅ 搜索功能

---

## 八、技术实现要点

### 8.1 主题系统
- 基于 Material Components DayNight 主题
- 支持自动日/夜间模式切换
- 使用 `?attr/colorPrimary` 等主题属性确保主题一致性

### 8.2 布局技术
- **ConstraintLayout** - 主要布局容器
- **RecyclerView** - 列表展示（支持多种LayoutManager）
- **LinearLayoutManager** - 列表模式
- **GridLayoutManager** - 网格模式
- **StaggeredGridLayoutManager** - 瀑布流模式

### 8.3 自定义组件
- **TagsView** - 标签流式布局组件
- **TimeProgressView** - 时间进度条组件
- **ImageIndicator** - 图片指示器组件

### 8.4 图片加载
- 使用 **Glide** 库加载网络图片和封面

---

*文档生成日期：2026-06-08*
*项目：Kikoeru Android*
*仓库：https://github.com/Zinhao/Kikoeru-android*
