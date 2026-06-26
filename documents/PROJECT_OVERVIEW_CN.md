# VASP Show 项目完整介绍

更新时间：2026-06-14

## 1. 项目概述

VASP Show 是一个面向分子、高分子、二维材料、晶体材料、MOF 和反应路径计算数据的科研数据库展示与治理平台。项目目标不是简单罗列数据文件，而是把不同来源、不同格式、不同理论层级的计算数据统一整理成可浏览、可检索、可审查、可下载、可用于模型训练前筛选的数据产品。

当前系统采用本地开发运行方式：

| 模块 | 当前地址 / 说明 |
|---|---|
| 前端 | `http://localhost:5173`，Vue 3 + Vite |
| 后端 | `http://localhost:8080`，Spring Boot |
| 展示数据库 | H2 文件库，`documents/data/frontend_template_data.mv.db` |
| 智能助手 | Ollama `qwen3:8b`，后端通过 `http://127.0.0.1:11434` 调用 |
| 原始数据 | HDF5、LMDB、Excel、JSON、CSV、CIF、EXTXYZ、ZIP/TGZ 等 |
| Docker | 备用部署方案，默认端口 `8088` |

当前统一展示库包含：

```text
21 个数据集
919,611 条展示记录
质量验证平均分约 98
```

注意：部分大型数据集如 ANI-1x、Transition1x 的原始构象数量达到数百万级。为了避免 H2 体积和内存爆炸，当前展示库只建立 group/reaction 级索引并展示代表构象；原始 HDF5 仍保留后续任意构象懒加载的扩展空间。

## 2. 项目目标

项目主要服务三类场景：

1. **科研展示**
   - 向专家展示当前已整理的计算数据资产。
   - 显示数据来源、计算方法、结构、性质、质量评分和缺失字段。

2. **数据发现**
   - 用户可以按数据集类型、元素、原子数、性质、泛函、基组/赝势、软件、关键词等条件寻找合适数据源。
   - 解决数据集数量增长后“找不到合适数据”的问题。

3. **数据治理与持续接入**
   - 支持注册用户提交新数据集线索。
   - 支持 DOI/网页链接真实性验证。
   - 支持常见文件格式抽样预检。
   - 支持超级管理员审核、发布或隐藏数据集。
   - 支持数据质量评分和专家复核流程。

## 3. 已接入数据集

当前项目共接入 21 个数据集，覆盖分子构象、高分子、二维材料、三维晶体、MOF、反应路径和材料性质 benchmark。

| 数据集 ID | 类型 | 展示/源规模 | 主要内容 |
|---|---|---:|---|
| `ani_gdb_s03` | 小分子构象 | 151,200 条 conformer | ANI-GDB s03，SMILES、元素、坐标、能量 |
| `ani1x_less_is_more` | 小分子主动学习 | 4,956,005 个原始构象 / 3,114 个展示 group | ANI-1x，能量、力、高层级能量标签 |
| `qm9_molecular_dft` | 小分子 DFT 性质 | 133,885 条表格记录 | QM9，HOMO/LUMO、gap、偶极矩、热力学属性；当前 CSV 版无三维坐标 |
| `data0000_aselmdb` | 高分子量化计算 | 2,524 条 LMDB record | OPoly26-val / ASE-LMDB 风格数据，含结构、能量、力 |
| `openpoly_calculated` | 高分子性质 | 1,000 条 Excel 行 | OpenPoly 计算聚合物属性，无三维坐标 |
| `polymer_genome_1073` | 高分子晶体 | 1,073 个 CIF 结构 | Polymer Genome 1073，聚合物晶体结构 |
| `transition1x` | 反应路径 | 9,624,594 个原始图像 / 10,073 个展示 reaction group | 反应路径数据，能量、力、代表图像 |
| `hydrocarbons_gap_ch` | 反应型势函数训练 | 35,663 条结构 | C/H GAP 训练构型，EXTXYZ 结构、能量、力 |
| `twod_matpedia` | 二维材料 | 6,351 条结构 | 2DMatPedia 二维材料 VASP DFT 数据 |
| `jarvis_dft_2d` | 二维材料 | 1,103 条记录 | JARVIS-DFT 2D materials |
| `jarvis_dft_3d` | 三维晶体材料 | 93,902 条记录 | JARVIS-DFT 3D bulk materials |
| `qmof_database` | MOF / 配位聚合物 | 20,372 条结构 | QMOF 结构、电子性质和热力学扩展数据 |
| `matbench_wbm_summary` | 稳定性 benchmark | 256,963 条记录 | Matbench Discovery WBM 稳定性摘要 |
| `matbench_mp_energies` | 参考能量 | 154,718 条记录 | Materials Project reference energies |
| `matbench_phonondb_pbe_103` | 声子/热输运 | 103 条结构 | PhononDB PBE 103 thermal conductivity |
| `matbench_v01_dielectric` | 介电性质 benchmark | 4,764 条记录 | Matbench v0.1 dielectric |
| `matbench_v01_jdft2d` | 二维剥离能 benchmark | 636 条记录 | Matbench v0.1 JDFT2D |
| `matbench_v01_phonons` | 声子 benchmark | 1,265 条记录 | Matbench v0.1 phonons |
| `matbench_v01_perovskites` | 钙钛矿 benchmark | 18,928 条记录 | Matbench v0.1 perovskites |
| `matbench_v01_log_gvrh` | 剪切模量 benchmark | 10,987 条记录 | Matbench v0.1 log GVRH |
| `matbench_v01_log_kvrh` | 体积模量 benchmark | 10,987 条记录 | Matbench v0.1 log KVRH |

## 4. 数据统一方式

由于原始数据来源复杂，项目没有让前端直接读取 HDF5、LMDB、CIF 或 Excel，而是采用统一展示库：

```text
原始数据文件
  ↓
Python 构建脚本解析与抽取
  ↓
H2 展示库 display_records
  ↓
Spring Boot JDBC 只读查询
  ↓
Vue 前端展示、筛选、详情、图表和下载
```

核心构建脚本：

```text
python/build_frontend_template_h2.py
```

核心展示库：

```text
documents/data/frontend_template_data.mv.db
```

主要统一字段包括：

- 数据集 ID
- 原始记录 ID
- 材料名 / 分子名 / 结构名
- SMILES / composition
- 原子数
- 三维结构 JSON
- 能量
- 力
- HOMO / LUMO / gap
- 计算软件
- DOI
- 额外属性 JSON
- warning 信息

不是所有数据集都必须拥有所有字段。项目现在支持“字段缺失但清楚标注”，避免强行把不同数据源塞成同一种物理含义。

## 5. 前端功能

### 5.1 数据中心

数据中心是首页入口，展示：

- 数据域概览：分子、高分子、二维/晶体、反应路径、MOF。
- 元素图谱。
- 数据集卡片。
- 数据集规模、原子数范围、泛函、基组/赝势、元素标签。
- 数据集入口。

### 5.2 数据发现

数据发现页用于在越来越多的数据集中快速找到合适数据源，支持：

- 模糊搜索：名称、描述、DOI、元素、性质、方法。
- 数据类型筛选。
- 元素筛选。
- 原子数范围筛选。
- 性质字段筛选。
- 泛函 / 方法 / 软件筛选。

### 5.3 数据集记录页

进入某个数据集后可查看：

- 数据集简介。
- 论文和数据来源链接。
- 记录列表。
- 关键词搜索。
- 能量上下限筛选。
- 原子数上下限筛选。
- 原子数、能量、gap、元素分布等图表。
- 数据集级下载入口。

### 5.4 记录详情页

点击具体记录后可查看：

- 基本元数据。
- 已存在字段。
- 三维结构。
- extraProperties。
- 单条记录下载。
- 无坐标数据会明确显示“无三维坐标”，不会伪造结构。

三维结构使用 Three.js / WebGL 渲染。当前方式是基于原子坐标绘制球体，并用颜色区分元素。它不是量子化学波函数可视化，也不是 VASP 电子密度等值面算法，而是常见的 ball-and-stick / atom sphere 空间结构展示方式。

### 5.5 数据质量验证

质量验证页已经从简单评分升级为审核工作台，包含：

- 全库质量概览。
- 审核流程。
- 专家审核门控规则。
- 数据集质量明细。
- 数据集审核台账。
- 缺失字段清单。
- 待处理质量问题。
- DOI / 数据链接真实性验证。
- 文件抽样预检。
- 预检评分。
- 提交超级管理员审核。
- 数据集发布/隐藏控制。

支持预检的文件格式：

```text
CSV / TSV
JSON / JSONL
XYZ / extxyz
CIF
HDF5 / H5
部分 .gz 压缩表格
```

当前预检只读取抽样数据，不长期保存上传文件。正式入库仍需要管理员配置解析适配器和字段映射。

### 5.6 智能助手

智能助手用于帮助用户理解当前数据库，而不是开放式闲聊工具。当前机制是：

```text
前端问题
  ↓
Spring Boot 根据 datasetId / recordId 读取真实上下文
  ↓
调用 Ollama qwen3:8b
  ↓
返回基于当前数据库上下文的回答
```

模型没有被微调。它能回答当前数据集问题，是因为后端把数据集目录、记录字段和上下文传给模型。

### 5.7 模型页面

模型页面用于展示当前数据如何服务机器学习任务，包括：

- 数据集候选选择。
- 模型任务规划。
- 目标性质选择。
- 简单浏览器内基线训练演示。
- 训练/验证/测试划分说明。
- 可导出训练计划 JSON。

当前训练演示是前端轻量基线，不等同于正式深度学习势函数训练。

### 5.8 工作流页面

工作流页展示计算数据生产链路：

```text
任务登记
VASP/DFT 计算
结果解析
质量校验
标准入库
模型回流
```

目前主要用于展示流程和未来接入方向，真实计算队列和自动任务调度仍属于后续扩展。

## 6. 权限体系

系统包含四类角色：

| 角色 | 权限 |
|---|---|
| 游客 | 浏览已发布数据集、查看记录、查看公开结构和来源链接 |
| 注册用户 | 游客能力 + 使用智能助手 + 下载单条记录 + 提交候选数据集 + 做在线预检 |
| 管理员 | 注册用户能力 + 下载整个数据集展示记录 |
| 超级管理员 | 管理用户角色、审核数据投稿、控制数据集发布/隐藏、推进接入流程 |

内置账号：

```text
superadmin
admin
```

默认密码来自 `application.yml` 中的环境变量兜底值。正式部署前必须通过环境变量替换：

```text
VASP_SUPERADMIN_PASSWORD
VASP_ADMIN_PASSWORD
```

## 7. 后端架构

后端使用 Spring Boot，主要职责：

- 读取 H2 展示库。
- 提供数据集目录、记录列表、详情和统计接口。
- 执行服务端搜索和筛选。
- 控制下载权限。
- 提供质量验证接口。
- 提供 DOI/链接验证和文件预检接口。
- 管理用户、角色和登录状态。
- 管理数据投稿、审核和发布状态。
- 调用 Ollama 智能助手。

主要后端文件：

| 文件 | 作用 |
|---|---|
| `DatasetController.java` | 数据集、记录、质量、下载、发布控制接口 |
| `DisplayDatasetService.java` | H2 展示库读取、筛选、统计、质量评分 |
| `DatasetIntakeService.java` | 数据集投稿和审核流程 |
| `DatasetGovernanceService.java` | 数据集发布/隐藏状态管理 |
| `QualityPreflightService.java` | 链接真实性验证和上传文件字段预检 |
| `AuthService.java` | 用户、角色、登录、权限校验 |
| `AssistantService.java` | 智能助手上下文组织和模型调用 |

## 8. 前端架构

前端使用 Vue 3 + Vite，主要页面：

| 页面 | 路由 | 作用 |
|---|---|---|
| 数据中心 | `/` | 首页、数据集卡片、质量/模型/工作流 tab |
| 数据发现 | `/explore` | 多条件寻找合适数据集 |
| 数据集记录 | `/datasets/:id` | 单数据集记录列表、筛选、图表 |
| 记录详情 | `/datasets/:id/records/:recordId` | 单条记录详情和 3D 结构 |
| 智能助手 | `/assistant` | 基于当前数据上下文问答 |
| 数据接入 | `/intake` | 用户提交数据集、管理员审核 |
| 登录注册 | `/login` | 用户登录和注册 |
| 权限管理 | `/admin/users` | 超级管理员管理用户角色 |

主要前端文件：

| 文件 | 作用 |
|---|---|
| `frontend/src/views/Home.vue` | 数据中心、质量验证、模型、工作流 |
| `frontend/src/views/Explore.vue` | 数据发现 |
| `frontend/src/views/DatasetRecords.vue` | 数据集记录页 |
| `frontend/src/views/Detail.vue` | 记录详情页 |
| `frontend/src/views/Assistant.vue` | 智能助手 |
| `frontend/src/views/DataIntake.vue` | 数据接入中心 |
| `frontend/src/components/Molecule3DViewer.vue` | Three.js 结构渲染 |
| `frontend/src/api/index.js` | 前端 API 封装 |

## 9. API 概览

常用接口：

| 方法 | 接口 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| GET | `/api/datasets` | 数据集卡片，普通用户只看到已发布数据集 |
| GET | `/api/datasets/catalog` | 数据发现目录 |
| GET | `/api/datasets/{datasetId}` | 数据集详情 |
| GET | `/api/datasets/{datasetId}/records` | 记录列表，支持分页、关键词、能量和原子数筛选 |
| GET | `/api/datasets/{datasetId}/stats` | 图表统计 |
| GET | `/api/datasets/{datasetId}/records/{recordId}` | 单条记录详情 |
| GET | `/api/quality/overview` | 质量验证总览 |
| POST | `/api/quality/validate-links` | DOI / 数据链接真实性验证 |
| POST | `/api/quality/preview-file` | 上传抽样文件并解析字段 |
| GET | `/api/datasets/publication` | 超级管理员查看发布状态 |
| PATCH | `/api/datasets/{datasetId}/publication` | 超级管理员发布/隐藏数据集 |
| POST | `/api/assistant/chat` | 智能助手问答 |
| POST | `/api/intake/submissions` | 注册用户提交候选数据集 |
| PATCH | `/api/intake/submissions/{id}/review` | 超级管理员审核投稿 |
| POST | `/api/intake/submissions/{id}/prepare` | 推进接入管线 |

下载权限：

- 注册用户可以下载单条记录。
- 管理员和超级管理员可以下载整个数据集展示记录。
- 游客点击下载会收到权限提示。

## 10. 数据质量审核体系

质量审核当前包含 6 类门控：

1. 来源与引用审核
2. 字段映射审核
3. 结构一致性审核
4. 数值标签审核
5. 理论层级审核
6. 高分子专项审核

发布等级：

| 等级 | 含义 |
|---|---|
| Gold | 标杆训练集 |
| Silver | 可展示与初步建模 |
| Bronze | 可展示但需补元数据 |
| Quarantine | 内部排查，暂缓发布 |

审核状态：

| 状态 | 含义 |
|---|---|
| 通过发布 | 可公开展示 |
| 专家复核 | 可展示但需补充确认 |
| 暂缓发布 | 有阻断项，建议隐藏或内部排查 |

## 11. 当前运行命令

启动后端：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;C:\Users\zhangqikai\tools\apache-maven-3.9.9\bin;$env:Path"
cd d:\polymer\8_show\vasp-show\backend
mvn spring-boot:run
```

启动前端：

```powershell
cd d:\polymer\8_show\vasp-show\frontend
npm run dev -- --host 0.0.0.0
```

停止服务：

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

重建 H2 展示库：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd d:\polymer\8_show\vasp-show
D:\anaconda3\python.exe python\build_frontend_template_h2.py --extra-data-root '..\数据集及论文'
```

运行测试：

```powershell
cd d:\polymer\8_show\vasp-show\backend
mvn test

cd d:\polymer\8_show\vasp-show\frontend
npm run build
```

## 12. 部署说明

本地开发默认使用：

```text
前端 Vite：5173
后端 Spring Boot：8080
```

Linux 服务器部署建议：

1. 后端和前端构建产物部署到服务器。
2. 使用 Nginx 反向代理前端和 `/api`。
3. 使用 HTTPS。
4. Ollama 只监听服务器本机 `127.0.0.1:11434`。
5. 大型原始 HDF5/LMDB 不建议直接打进 Docker 镜像，应使用 volume 挂载。
6. 若只需要网页展示，部署 H2 展示库即可；若要支持任意构象懒加载，需要同步原始大数据文件。

## 13. 当前局限

1. **H2 适合展示，但不是长期大规模检索数据库**
   - 当前 91 万展示记录可以使用。
   - 如果继续扩展到千万级展示记录，建议评估 PostgreSQL、DuckDB、ClickHouse 或 Elasticsearch。

2. **部分大型数据集没有全量展开**
   - ANI-1x 和 Transition1x 当前展示代表构象。
   - 任意构象懒加载需要后端直接读取原始 HDF5。

3. **单位和理论层级还需要进一步结构化**
   - 当前部分字段在 `extraProperties` 中。
   - 后续应增加 energy_unit、force_unit、functional、basis_set、pseudopotential、software_version 等结构化字段。

4. **三维结构展示是几何结构展示**
   - 当前显示原子球体和空间坐标。
   - 尚未展示电荷密度、轨道、DOS、能带、声子谱等更高级 VASP 结果。

5. **智能助手仍是上下文注入，不是微调模型**
   - 回答质量依赖上下文组织和模型能力。
   - 还没有加入论文全文检索、引用片段、问答审计和限流。

6. **数据接入仍是半自动**
   - 已支持链接验证、文件预检和审核流程。
   - 真正入库仍需要为不同格式配置解析适配器和字段映射。

## 14. 后续规划

优先建议：

1. 增加 ANI-1x / Transition1x 任意构象懒加载接口。
2. 为每个数据集增加单位、理论层级和字段字典。
3. 建立质量报告历史版本，记录每次 H2 构建后的质量变化。
4. 增加结构异常检测：重复原子、异常键长、NaN 坐标、晶胞异常。
5. 增加 API 文档页，方便外部用户使用。
6. 数据发现页改为服务端检索，支持更多数据集后的分页和索引。
7. 将展示库从 H2 迁移或扩展到 PostgreSQL / DuckDB / ClickHouse。
8. 智能助手接入论文、数据说明和字段字典的 RAG 检索。
9. 增加正式模型训练任务队列，与服务器 GPU 训练流程打通。
10. 完善数据接入流水线：自动下载、checksum、抽样预检、字段映射、质量门控、管理员发布。

## 15. 一句话总结

VASP Show 当前已经从“数据展示网页”升级为“计算数据资产展示、发现、审核、接入和智能问答平台”。它能够统一展示 21 个来源复杂的计算数据集，并提供结构查看、属性筛选、质量验证、文件预检、权限管理和智能助手能力。后续重点是让数据接入更自动、质量审核更严格、结构和性质展示更专业，并逐步支撑高分子计算数据的模型训练闭环。
