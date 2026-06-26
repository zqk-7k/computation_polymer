# VASP Show 当前技术文档

更新时间：2026-05-27

## 1. 项目定位

VASP Show 是一个面向分子、聚合物、二维材料、晶体材料、MOF 和反应路径的本地计算数据库展示系统。当前目标是把不同来源的 HDF5、LMDB、Excel、JSON、ZIP、TGZ/CIF 数据统一整理到一个只读 H2 展示库中，由 Spring Boot 提供 API，由 Vue/Vite 前端展示结构、属性、图表和元数据。

## 2. 当前运行方式

本地开发运行，不是 Docker。

| 模块 | 地址 / 命令 |
|---|---|
| 后端 | `http://localhost:8080` |
| 前端 | `http://localhost:5173` |
| Ollama 模型 | Linux 服务器运行 `qwen3:8b`，Windows 开发期经 SSH 隧道访问 `http://127.0.0.1:11434` |
| JDK | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` |
| Maven | `C:\Users\zhangqikai\tools\apache-maven-3.9.9` |
| Python | `D:\anaconda3\python.exe` |

## 3. 已接入数据集

当前统一展示库包含 10 个数据集，共 290,712 条展示记录。

| dataset id | 记录数 | 展示策略 |
|---|---:|---|
| `ani_gdb_s03` | 151,200 | conformer 全量展开 |
| `data0000_aselmdb` | 2,524 | LMDB record 全量展开 |
| `openpoly_calculated` | 1,000 | Excel 行全量展开 |
| `ani1x_less_is_more` | 3,114 | HDF5 化学式 group 级索引，展示代表构象 |
| `transition1x` | 10,073 | HDF5 reaction group 级索引，展示代表反应图像 |
| `twod_matpedia` | 6,351 | JSON/NDJSON 结构记录展开 |
| `jarvis_dft_3d` | 93,902 | JARVIS 3D JSON 记录展开 |
| `jarvis_dft_2d` | 1,103 | JARVIS 2D ZIP 内 JSON 记录展开 |
| `polymer_genome_1073` | 1,073 | TGZ 内 CIF 结构展开 |
| `qmof_database` | 20,372 | QMOF JSON + CIF 结构展开 |

## 4. 数据库与构建

统一展示库文件：

```text
documents/data/frontend_template_data.mv.db
```

数据库技术：

- H2 Database，嵌入式文件型关系数据库
- 后端通过 JDBC 只读连接
- 核心表：`DISPLAY_RECORDS`
- 查询索引：`idx_display_dataset_id`、`idx_display_dataset_source`、`idx_display_dataset_material`
- 后端启动后保持只读连接并预热数据集目录；数据集详情和统计结果按数据集缓存。
- 用户认证库：`documents/data/vasp_auth.mv.db`，由后端单独读写，不修改展示库
- 数据接入库：`documents/data/vasp_intake.mv.db`，保存投稿、审核决定与管线阶段，不直接修改展示库

构建脚本：

```text
python/build_frontend_template_h2.py
```

重建命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
D:\anaconda3\python.exe python\build_frontend_template_h2.py
```

重建展示库前需停止后端服务，避免只读连接占用 H2 文件；新的构建脚本会自动创建查询索引。

## 5. 后端 API

主要服务文件：

```text
backend/src/main/java/com/vaspshow/backend/service/DisplayDatasetService.java
backend/src/main/java/com/vaspshow/backend/controller/DatasetController.java
backend/src/main/java/com/vaspshow/backend/service/DatasetIntakeService.java
backend/src/main/java/com/vaspshow/backend/controller/DatasetIntakeController.java
```

核心接口：

| 接口 | 说明 |
|---|---|
| `GET /api/health` | 健康检查 |
| `GET /api/datasets` | 数据集卡片 |
| `GET /api/datasets/catalog` | 数据发现目录：类型、元素、性质、方法、泛函、基组/赝势、软件与展示策略 |
| `GET /api/datasets/{datasetId}` | 数据集详情 |
| `GET /api/datasets/{datasetId}/records` | 记录分页、搜索、能量/原子数筛选 |
| `GET /api/datasets/{datasetId}/stats` | 数据集统计图表数据 |
| `GET /api/datasets/{datasetId}/records/{recordId}` | 单条记录详情 |
| `GET /api/datasets/{datasetId}/records/{recordId}/download.csv` | 登录用户下载单条坐标 CSV |
| `GET /api/datasets/{datasetId}/records/{recordId}/download.json` | 登录用户下载单条完整展示记录 JSON |
| `GET /api/datasets/{datasetId}/download.csv` | 管理员下载该数据集展示记录 CSV |
| `POST /api/auth/login` | 登录并返回会话令牌 |
| `POST /api/auth/register` | 注册普通用户并登录 |
| `GET /api/auth/me` | 查询当前用户 |
| `POST /api/auth/logout` | 退出登录 |
| `GET /api/auth/users` | 超级管理员查看用户列表 |
| `PATCH /api/auth/users/{username}/role` | 超级管理员设置管理员或注册用户角色 |
| `POST /api/assistant/chat` | 登录用户向智能助手提问；可携带数据集或记录上下文 |
| `GET /api/intake/sources` | 浏览可作为接入候选的公开数据来源 |
| `POST /api/intake/submissions` | 注册用户提交论文或公开数据来源；最少填写说明和一个网页链接 |
| `GET /api/intake/submissions` | 查询自己的投稿；超级管理员查询审核队列 |
| `PATCH /api/intake/submissions/{id}/review` | 超级管理员批准或退回投稿 |
| `POST /api/intake/submissions/{id}/prepare` | 超级管理员将已批准来源推进至格式适配阶段 |

新增统计接口返回：

- 原子数直方图
- 能量直方图
- Band gap / HOMO-LUMO gap 直方图
- 元素组成统计
- 字段可用性统计

数据集详情接口还返回可用的来源链接，包括论文 DOI、数据 DOI 或官方数据门户；没有可靠公开地址的数据集不强行提供下载链接。

## 6. 认证与下载权限

当前使用 Bearer token 会话和独立 H2 用户库。权限规则如下：

| 角色 | 浏览结构与属性 | 智能助手 | 下载单条记录 | 下载数据集展示记录 | 管理用户角色 |
|---|---|---|---|---|---|
| 游客 | 是 | 否 | 否 | 否 | 否 |
| 注册用户 | 是 | 是 | 是 | 否 | 否 |
| 管理员 | 是 | 是 | 是 | 是 | 否 |
| 超级管理员 | 是 | 是 | 是 | 是 | 是 |

注册用户、管理员和超级管理员均可提交候选数据集；只有超级管理员可以审核来源并推进接入管线。

系统启动时提供 `superadmin` 和 `admin` 两个管理账号。公开部署前必须通过环境变量替换初始密码：

```bash
export VASP_SUPERADMIN_PASSWORD='替换为高强度密码'
export VASP_ADMIN_PASSWORD='替换为高强度密码'
```

注意：

- 会话令牌当前保存在后端内存中，服务重启后需要重新登录。
- 管理员下载的是统一 H2 展示库中的该数据集全部展示记录；ANI-1x 与 Transition1x 仍不等于原始 HDF5 中数百万个构象/反应图像的全量导出。
- 游客查看 3D 结构时，浏览器必然接收当前记录的坐标；权限控制的是应用内下载与批量导出入口，无法阻止用户保存已公开展示的数据。
- 公网部署应配置 HTTPS，避免密码和令牌以明文链路传输。

## 7. 前端结构

主要文件：

```text
frontend/src/views/Home.vue
frontend/src/views/Explore.vue
frontend/src/views/DatasetRecords.vue
frontend/src/views/Detail.vue
frontend/src/views/Login.vue
frontend/src/views/AdminUsers.vue
frontend/src/views/Assistant.vue
frontend/src/views/DataIntake.vue
frontend/src/components/AppTopbar.vue
frontend/src/components/AppFooter.vue
frontend/src/components/Molecule3DViewer.vue
frontend/src/components/DatasetStatsCharts.vue
frontend/src/styles/theme.css
frontend/src/api/index.js
```

前端能力：

- 首页数据域、元素图谱和数据集卡片
- 数据发现页组合筛选：模糊搜索、研究对象、元素、性质、原子数、方法、泛函、基组/赝势和软件
- 数据集记录页搜索、筛选、分页和图表
- 记录列表筛选由后端 H2 查询执行，支持关键词、能量字段范围和原子数范围；用户通过“检索记录”按钮或回车提交条件；概览图表当前仍表示整个数据集分布
- 数据集记录页展示论文链接和可用的数据下载来源
- 详情页 3D 结构、晶胞线框、元素组成和仅存在字段的属性标签页
- 登录/注册页与超级管理员用户权限管理
- 智能助手页：全局咨询、数据集上下文咨询、当前结构记录上下文咨询
- 日间/夜间主题
- 全局底部数据合作、技术支持和权限入口
- 数据接入中心：人工维护候选公开来源、注册用户轻量投稿、超级管理员审核和接入阶段追踪
- 始终可见且反馈角色限制的下载入口：登录用户单条 JSON，管理员数据集 CSV

数据发现目录当前由后端统一整理数据集级元数据，前端筛选条件写入 URL 查询参数；从发现页进入数据集时，原子数约束会继续应用于记录列表。随着数据集持续扩大，目录元数据可进一步迁移到独立表中，支持服务端分页与索引检索。

## 8. 3D 结构绘制算法

当前 3D 结构展示是可视化算法，不是量子化学计算算法。

实现技术：

- Three.js WebGL 渲染
- `OrbitControls` 旋转、缩放和自动旋转
- 原子以球体 `SphereGeometry` 表示
- 键以圆柱体 `CylinderGeometry` 表示
- 晶胞以 `LineSegments` 线框表示

绘制流程：

1. 从 API 获取原子元素和笛卡尔坐标。
2. 根据原子坐标计算包围盒中心。
3. 将坐标平移到中心附近，并按最大空间跨度缩放到视图范围。
4. 每个原子按元素配色和显示半径绘制球体。
5. 键连线使用共价半径启发式判断：

```text
distance(atomA, atomB) <= 1.22 * (covalentRadiusA + covalentRadiusB)
```

6. 如果 `structure_json.lattice` 存在，则使用 3 个晶格矢量生成 8 个晶胞顶点和 12 条边，绘制真实周期晶胞线框。
7. 如果没有 lattice，则绘制坐标包围盒作为空间范围辅助线。

注意：

- 键连线是显示用的几何启发式，不代表源数据提供了键级。
- 分子数据通常没有真实晶胞。
- 材料、CIF、JARVIS、QMOF、2DMatPedia 等数据若构建时保留 lattice，可显示周期晶胞。
- 详情页的“元素组成”区域会显示颜色图例，用来说明不同颜色球体对应的元素。

## 9. 局域网访问

前端 Vite 已配置为监听 `0.0.0.0:5173`。在同一局域网或同一 VPN 内，其他电脑可通过当前主机 IPv4 访问：

```text
http://当前主机IP:5173
```

例如当前机器曾检测到的内网地址为：

```text
http://10.1.140.221:5173
```

说明：

- 这不是公网互联网链接，只是内网/局域网链接。
- 如果其他电脑打不开，通常需要检查 Windows 防火墙是否允许 Node/Vite 的 5173 端口入站。
- 真正公网访问建议部署到 Linux 服务器，并使用 Nginx/域名/HTTPS；也可临时使用 cloudflared、frp、ngrok 等内网穿透。

## 10. 智能助手接入

当前采用“前端 -> Spring Boot -> Ollama”的后端代理方式，浏览器不直接访问模型端口：

```text
Vue /assistant -> POST /api/assistant/chat -> http://127.0.0.1:11434/api/chat -> qwen3:8b
```

- Windows 开发阶段：Linux 运行 Ollama，Windows 通过 SSH 隧道映射 `11434`，本地 Spring Boot 访问映射后的地址。
- Linux 正式部署阶段：Spring Boot 与 Ollama 同机运行，后端直接访问本机 `127.0.0.1:11434`，不公开 Ollama 端口。
- 后端文件：`AssistantController.java`、`AssistantService.java`、`AssistantProperties.java`。
- 模型请求固定为非流式响应、关闭思考过程显示，并保持模型驻留时间可配置。
- 后端会把目录、数据集或当前记录的已存在字段组成受控上下文；模型被要求对未提供字段明确说明缺失，不将推测冒充入库事实。
- 为控制 GPU 消耗，当前只有登录用户可以发起模型问答。

配置项：

```yaml
vasp.assistant.base-url: ${OLLAMA_BASE_URL:http://127.0.0.1:11434}
vasp.assistant.model: ${OLLAMA_MODEL:qwen3:8b}
vasp.assistant.keep-alive: ${OLLAMA_KEEP_ALIVE:30m}
```

## 11. 当前限制

- ANI-1x 和 Transition1x 当前仍是 group/reaction 级代表构象，不支持任意构象懒加载。
- 图表目前基于统一字段：原子数、能量、gap、composition；更细的 VASP 参数还未入库。
- 详情页未展示 POSCAR/CIF 下载。
- 真实 VASP 输入参数如 ENCUT、KPOINTS、POTCAR、EDIFF、ISMEAR 尚未统一结构化。
- 当前认证适用于第一版应用权限管理，尚未接入邮箱验证、密码找回、审计日志和长期持久化会话。
- 智能助手当前依赖单模型回答与结构化上下文，尚未加入论文全文检索、引用片段、限流与问答审计。
- Chart.js 已引入，前端构建包会变大；后续可做动态加载拆包。
- `energy` 与 `atom_count` 仍以文本保存在展示表中，范围筛选需要类型转换；后续应增加数值列及组合索引。
- 大型数据集统计首次访问仍需计算；后续可在构建阶段生成统计汇总表。
- 面向公网并发访问和持续扩容时，可将展示查询层迁移到 PostgreSQL，原始 HDF5 等文件继续作为懒加载数据源。
- 数据接入中心当前完成来源投稿、审核和格式适配排期；候选公开来源清单由后端人工维护，不是实时自动抓取。将外部数据自动下载、解析、质检并发布到展示库仍需按来源开发适配器与构建任务。
