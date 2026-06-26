# VASP Show 前端视觉规范

版本：V1.0  
定位：国家级科研计算数据库展示系统  
适用范围：首页、数据发现、质量验证、智能助手、模型、工作流、详情页与后台审核页。

## 1. 当前前端结构

当前项目为 Vue 3 + Vite 单页应用，不使用 Tailwind。

主要入口：

- `frontend/src/main.js`：挂载 Vue 应用，引入全局主题 `styles/theme.css`。
- `frontend/src/App.vue`：全局应用框架，引入底部信息组件。
- `frontend/src/styles/theme.css`：当前全局主题变量与跨页面基础样式。
- `frontend/src/components/AppTopbar.vue`：顶部导航、登录状态、主题切换。
- `frontend/src/components/AppFooter.vue`：底部联系与支持信息。
- `frontend/src/views/Home.vue`：首页/dashboard 主体，包含数据中心、质量验证、模型、工作流等 tab。
- `frontend/src/views/Explore.vue`：数据发现页面。
- `frontend/src/views/DatasetRecords.vue`：单数据集记录列表。
- `frontend/src/views/Detail.vue`：单条记录与三维结构详情。
- `frontend/src/components/Molecule3DViewer.vue`：Three.js/WebGL 三维结构 viewer。
- `frontend/src/components/DatasetStatsCharts.vue`：Chart.js 数据统计图表。

当前 dashboard 相关内容主要集中在 `Home.vue`，后续建议拆分为可复用组件，避免单文件继续膨胀。

## 2. 目标视觉定位

整体风格应是高级、干净、稳重、有科技感的材料计算数据库界面。

关键词：

- 国家项目展示
- 计算材料数据库
- VASP / DFT 工作流
- 数据证据链
- 可审计、可追溯、可建模

不采用：

- 廉价大面积渐变
- 塑料感高饱和按钮
- 模糊 PNG 背景
- 文字画进图片
- 过度游戏化动效

采用：

- DOM 文本
- SVG / CSS / Canvas / Three.js 图形
- 高质量矢量图标
- 克制的玻璃态层次
- 精准的数据图表
- 大屏友好的网格与留白

## 3. 色彩系统

主色限定为深蓝、浅蓝、青色、白色，少量紫色只用于高光或模型/智能相关状态。

核心 token 已放入：

`frontend/src/styles/theme.css`

### 主色

- `--vs-blue-950`：深背景、深色面板核心色
- `--vs-blue-900`：国家项目感深蓝
- `--vs-blue-700`：主导航、重点边框
- `--vs-blue-500`：主操作按钮、关键数字
- `--vs-blue-100`：浅蓝信息底色

### 青色

- `--vs-cyan-700`：科研图形深青
- `--vs-cyan-500`：数据流、连接线、状态高亮
- `--vs-cyan-100`：低风险状态背景

### 紫色

- `--vs-purple-600`：AI、模型、智能助手高光
- `--vs-purple-100`：轻量辅助背景

### 中性色

- `--vs-slate-950`：标题文本
- `--vs-slate-700`：正文文本
- `--vs-slate-500`：说明文本
- `--vs-slate-300`：边框
- `--vs-slate-100`：页面浅背景
- `--vs-white`：卡片底色

## 4. 间距系统

使用 4px 基准栅格：

- `--vs-space-1`: 4px
- `--vs-space-2`: 8px
- `--vs-space-3`: 12px
- `--vs-space-4`: 16px
- `--vs-space-5`: 20px
- `--vs-space-6`: 24px
- `--vs-space-8`: 32px
- `--vs-space-10`: 40px
- `--vs-space-12`: 48px
- `--vs-space-16`: 64px

规则：

- dashboard 大区块间距使用 24-32px。
- 卡片内部 padding 使用 16-24px。
- 表格与筛选控件间距使用 8-16px。
- 大屏展示时主内容宽度可放大，但卡片内部密度不能过松。

## 5. 字体与排版

字体 token：

- `--vs-font-sans`: Inter, Segoe UI, PingFang SC, Microsoft YaHei
- `--vs-font-mono`: SFMono-Regular, Consolas, Liberation Mono

字号 token：

- `--vs-type-xs`: 12px，用于标签、辅助说明
- `--vs-type-sm`: 13px，用于表格、按钮
- `--vs-type-md`: 15px，用于正文
- `--vs-type-lg`: 18px，用于卡片标题
- `--vs-type-xl`: 24px，用于区块标题
- `--vs-type-2xl`: 32px，用于 dashboard 强指标
- `--vs-type-hero`: 48px，用于首页主标题

规则：

- 字间距保持 0，不使用负字距。
- 中文正文行高建议 1.55-1.72。
- 英文缩写如 VASP、DFT、PAW、HDF5 可使用更高字重，但不应过度大写堆叠。
- 大屏标题可以强，但数据卡片内部标题应克制。

## 6. 卡片系统

卡片用于承载独立数据对象、图表、审核项、模型任务。

基础规则：

- 圆角：优先 `--vs-radius-md`，即 8px。
- 边框：使用 `--vs-border` 或 `--vs-border-strong`。
- 背景：白色或极浅蓝白，不使用厚重渐变。
- 阴影：只表达层级，不制造漂浮塑料感。

阴影 token：

- `--vs-shadow-xs`：轻边界
- `--vs-shadow-sm`：小卡片
- `--vs-shadow-md`：dashboard 主要卡片
- `--vs-shadow-lg`：弹窗、首屏重点模块
- `--vs-shadow-glow-blue`：少量关键高亮，不可滥用

规则：

- 不要卡片套卡片。
- 表格类区域优先使用平面分区，不使用重阴影。
- hover 只做 1-4px 位移或边框增强。

## 7. Glassmorphism 规则

玻璃态只用于顶部导航、悬浮工具条、首屏信息层，不作为所有卡片的默认风格。

Token：

- `--vs-glass-bg`
- `--vs-glass-border`
- `--vs-glass-blur`

规则：

- 透明度必须保证文字对比度。
- 禁止大面积毛玻璃叠加。
- 背景要有明确层级，不做模糊 PNG。
- 暗色模式下减少透明度，避免发灰。

## 8. 3D Hero 规则

3D hero 用于表达计算材料、分子结构、晶格、数据流和模型空间。

允许：

- CSS 3D 分子轨道
- SVG 晶格线框
- Three.js/WebGL 分子或晶体结构
- Canvas 粒子化数据流

不允许：

- 文字烘焙进图片
- 模糊背景图
- 大面积高饱和霓虹
- 不可解释的装饰球体

Token：

- `--vs-hero-perspective`
- `--vs-hero-depth`

规则：

- 3D 场景必须服务主题：结构、能量、力、晶格、数据流。
- 首屏 3D 不遮挡标题和关键指标。
- 动效应低速、可关闭，遵守 `prefers-reduced-motion`。
- 详情页结构 viewer 继续使用 Three.js，首页 hero 可优先使用 CSS/SVG/Canvas，避免首屏过重。

## 9. 图表风格规则

图表用于说明数据规模、元素覆盖、性质分布、质量评分和模型训练结果。

Token：

- `--vs-chart-axis`
- `--vs-chart-grid`
- `--vs-chart-series-blue`
- `--vs-chart-series-cyan`
- `--vs-chart-series-purple`

规则：

- 坐标轴弱化，数据本身突出。
- 网格线使用浅色、低对比。
- 蓝色用于主序列，青色用于流程/增长，紫色用于模型/AI。
- 图例必须是 DOM 文本或 Canvas 原生文字，不用图片。
- 图表卡片需要标题、单位、数据口径说明。
- 大屏展示时优先用横向比较、矩阵、桑基/流程图，而不是堆满小卡片。

## 10. 页面级设计策略

### 首页 / Dashboard

目标：国家级项目门面。

应展示：

- 平台定位
- 数据总规模
- 数据类型覆盖
- 元素覆盖
- 质量门控
- 模型可用性
- 数据接入流程

不应展示：

- 过多长段说明
- 重复的 hero
- 只为装饰存在的图形

### 数据发现

目标：让专家快速找到可用数据集。

应强化：

- 元素、原子数、性质、方法、基组、软件、数据规模筛选
- 数据集卡片的质量状态
- 是否有结构、力、能量、下载权限

### 质量验证

目标：审核工作台。

应强化：

- 公开摘要和内部审核分层
- 数据集质量分
- 缺失字段
- 预检入口
- 发布/隐藏状态

### 详情页

目标：单条数据证据链。

应强化：

- 三维结构
- 元素颜色图例
- 能量/力/性质单位
- 来源 DOI、原始记录 ID
- 数据下载权限提示

## 11. 后续改动计划

本轮不大改页面，仅建立规范和 token。

建议后续按以下顺序改造：

1. 把 `Home.vue` 拆为 `DashboardHero.vue`、`DatasetOverview.vue`、`QualityDashboard.vue`、`ModelDashboard.vue`、`WorkflowDashboard.vue`。
2. 把重复卡片样式抽成基础类：`.vs-card`、`.vs-panel`、`.vs-stat-card`、`.vs-toolbar`。
3. 将 `AppTopbar.vue` 和 `AppFooter.vue` 切换到统一 glass/card token。
4. 将 `DatasetStatsCharts.vue` 的 Chart.js 颜色改为 chart token。
5. 将 `Molecule3DViewer.vue` 增加国家项目风格的 viewer 外观、元素图例与单位说明。
6. 将首页 hero 的 CSS 3D 视觉替换为可复用 `ScientificHero3D.vue`，必要时使用 Three.js。
7. 清理页面内硬编码颜色，统一迁移到 `theme.css`。

## 12. 本轮涉及文件

已更新：

- `frontend/src/styles/theme.css`
- `docs/frontend_design_system.md`

本轮不改：

- `frontend/src/views/Home.vue`
- `frontend/src/views/Explore.vue`
- `frontend/src/views/DatasetRecords.vue`
- `frontend/src/views/Detail.vue`
- `frontend/src/components/Molecule3DViewer.vue`
- `frontend/src/components/DatasetStatsCharts.vue`

这些文件将在后续页面级改造时按规范逐步收敛。
