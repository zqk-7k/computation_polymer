# 当前技术与数据说明

## 1. 项目运行方式

当前项目以本地开发方式运行，不依赖 Docker。

| 服务 | 技术 | 默认地址 | 说明 |
| --- | --- | --- | --- |
| 后端 | Spring Boot | `http://localhost:8080` | 提供 REST API |
| 前端 | Vue + Vite | `http://localhost:5173` | 提供页面入口 |
| 智能助手模型 | Ollama / `qwen3:8b` | Windows 经隧道访问 `http://127.0.0.1:11434` | Linux 服务器运行模型 |
| Docker | Docker Compose + Nginx | `http://localhost:8088` | 备用部署方案 |

### 1.1 启动后端

在 PowerShell 中执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;C:\Users\zhangqikai\tools\apache-maven-3.9.9\bin;$env:Path"
cd backend
mvn spring-boot:run
```

健康检查：

```powershell
curl http://localhost:8080/api/health
```

### 1.2 启动前端

另开一个 PowerShell：

```powershell
cd frontend
npm run dev
```

访问：

```text
http://localhost:5173
```

开发环境下，Vite 会把 `/api` 请求代理到：

```text
http://localhost:8080
```

### 1.3 停止本地服务

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

### 1.4 智能助手开发连接

Linux 服务器当前运行 `Ollama + qwen3:8b`；Windows 后端不直接暴露模型端口，而是经 SSH 隧道连接。详细启动、关闭和排查命令见 `documents/OLLAMA_MODEL_OPERATION_GUIDE_CN.md`：

```powershell
while ($true) {
  ssh -NT `
    -i "$env:USERPROFILE\.ssh\autodl_ollama" `
    -p 2338 `
    -o ServerAliveInterval=30 `
    -o ServerAliveCountMax=3 `
    -o ExitOnForwardFailure=yes `
    -L 11434:127.0.0.1:11434 `
    root@gpu.chzmark.com
  Start-Sleep -Seconds 5
}
```

连通性检查：

```powershell
curl.exe http://127.0.0.1:11434/api/tags
```

## 2. 当前技术栈

### 2.1 后端

| 技术 | 用途 |
| --- | --- |
| Java 17 | 后端运行环境 |
| Spring Boot 3.5.x | 后端应用框架 |
| Spring Web MVC | REST API |
| Java HttpClient / Jackson | 代理请求 Ollama 聊天 API 并处理结构化上下文 |
| Maven | 依赖管理、测试、打包 |
| H2 JDBC Driver | 读取 H2 展示数据库 |
| JHDF | 保留早期 ANI HDF5 group/conformer 接口读取能力 |
| JUnit 5 / MockMvc | 后端接口测试 |

### 2.2 前端

| 技术 | 用途 |
| --- | --- |
| Vue 3 | 前端页面框架 |
| Vue Router | 页面路由 |
| Vite | 本地开发服务器和前端构建 |
| Three.js | 分子三维结构展示 |
| Chart.js | 图表展示 |
| 原生 Fetch API | 请求后端 API |

### 2.3 数据处理脚本

| 技术 | 用途 |
| --- | --- |
| Python | 数据转换与展示库构建 |
| h5py | 读取 HDF5 数据 |
| lmdb | 读取 ASE-LMDB 数据 |
| numpy | 数组处理、坐标和力数据转换 |
| zip/xml 解析 | 读取 OpenPoly Excel、JARVIS/QMOF ZIP 数据 |
| tarfile / CIF 解析 | 读取 Polymer Genome、QMOF CIF 结构 |
| gzip/csv/extxyz 解析 | 读取 Matbench Discovery 与 hydrocarbons C/H GAP 数据 |

项目约定 Python 环境是 `small_tools`；当前机器上实际可用的是：

```text
D:\anaconda3\python.exe
```

## 3. 当前数据库和数据文件

这里的“数据库”分两类：

1. 真正被后端 API 直接查询的展示数据库。
2. 作为源数据存在的 HDF5 / LMDB / Excel 文件。

### 3.1 H2 展示数据库

| 项 | 说明 |
| --- | --- |
| 文件 | `documents/data/frontend_template_data.mv.db` |
| 数据库技术 | H2 Database |
| 数据库类型 | 嵌入式关系型数据库，文件型数据库 |
| 后端访问方式 | JDBC，只读连接 |
| 主要表 | `display_records` |
| 作用 | 给前端统一分页、搜索、筛选和详情展示 |

H2 是当前前后端展示使用的主数据库。它不是原始科研数据，而是由 Python 脚本从多个源数据文件转换出来的展示层数据库。

当前性能优化：

- H2 文件增加 `idx_display_dataset_id`、`idx_display_dataset_source`、`idx_display_dataset_material` 查询索引。
- 后端维持只读数据库连接，并缓存目录、详情和已计算统计，减少大文件库反复打开及重复扫描。
- 重建 H2 前需先停止后端服务；新的构建脚本会自动创建上述索引。

生成脚本：

```text
python/build_frontend_template_h2.py
```

当前展示库记录数：

| 数据集 | 展示记录数 |
| --- | ---: |
| `ani_gdb_s03` | 151,200 |
| `data0000_aselmdb` | 2,524 |
| `openpoly_calculated` | 1,000 |
| `ani1x_less_is_more` | 3,114 |
| `transition1x` | 10,073 |
| `twod_matpedia` | 6,351 |
| `jarvis_dft_3d` | 93,902 |
| `jarvis_dft_2d` | 1,103 |
| `polymer_genome_1073` | 1,073 |
| `qmof_database` | 20,372 |
| `matbench_wbm_summary` | 256,963 |
| `matbench_mp_energies` | 154,718 |
| `matbench_phonondb_pbe_103` | 103 |
| `hydrocarbons_gap_ch` | 35,663 |
| 合计 | 738,159 |

### 3.2 源数据文件

| 文件 | 技术/格式 | 作用 |
| --- | --- | --- |
| `documents/data/ani_gdb_s03.h5` | HDF5 | ANI-GDB s03 小分子构象源数据 |
| `documents/data/data0000.aselmdb` | LMDB | ASE-LMDB 聚合物量化计算源数据 |
| `documents/data/calculated_polymer_data.xlsx` | Excel | OpenPoly 聚合物量化计算源数据 |
| `documents/data/ani1x-release.h5` | HDF5 | ANI-1x / Less is more 大型源数据 |
| `documents/data/Transition1x.h5` | HDF5 | Transition1x 反应路径大型源数据 |
| `../数据集及论文/2Dmatpedia/2Dmatpedia.json` | NDJSON | 2DMatPedia 二维材料源数据 |
| `../数据集及论文/jdft/jdft_3d-9-24-2025.json` | JSON | JARVIS-DFT 3D 源数据 |
| `../数据集及论文/jdft/6815705.zip` | ZIP | JARVIS-DFT 2D 源数据 |
| `../数据集及论文/polymer-cif/Polymer-CIF.tgz` | TGZ/CIF | Polymer Genome 1073 源数据 |
| `../数据集及论文/qmof/qmof_database.zip` | ZIP/CIF | QMOF 结构与电子性质源数据 |
| `../数据集及论文/qmof/qmof_thermo_database.zip` | ZIP/JSON | QMOF 热力学性质源数据 |
| `../数据集及论文/matbench_discovery/01_wbm_core/wbm_summary.csv.gz` | CSV.GZ | Matbench Discovery WBM 稳定性摘要 |
| `../数据集及论文/matbench_discovery/02_materials_project_reference/mp_energies.csv.gz` | CSV.GZ | Materials Project 参考能量表 |
| `../数据集及论文/matbench_discovery/04_phonondb_thermal/*` | EXTXYZ/JSON.GZ | PhononDB PBE 103 结构与热输运标签 |
| `../数据集及论文/hydrocarbons/train_tagged.zip` | ZIP/EXTXYZ | C/H GAP 反应型机器学习势训练构型 |

## 4. 各数据集展示方式

| 数据集 ID | 展示方式 | 说明 |
| --- | --- | --- |
| `ani_gdb_s03` | 按 conformer 展开 | 151,200 条构象全部进入 H2 |
| `data0000_aselmdb` | 按 LMDB record 展开 | 2,524 条记录全部进入 H2 |
| `openpoly_calculated` | 按 Excel 行展开 | 1,000 条记录全部进入 H2 |
| `ani1x_less_is_more` | 按 HDF5 化学式 group 建索引 | 每个 group 展示第 0 个代表构象 |
| `transition1x` | 按 HDF5 reaction group 建索引 | 每个 reaction 展示第 0 个代表图像 |
| `twod_matpedia` | 按 NDJSON 行展开 | 6,351 个二维材料结构进入 H2 |
| `jarvis_dft_3d` | 按 JSON record 展开 | 93,902 条三维晶体材料记录进入 H2 |
| `jarvis_dft_2d` | 按 JSON record 展开 | 1,103 条二维材料记录进入 H2 |
| `polymer_genome_1073` | 按 CIF 文件展开 | 1,073 个聚合物结构进入 H2 |
| `qmof_database` | 按 QMOF record 展开 | 20,372 个 MOF / 配位聚合物结构进入 H2 |
| `matbench_wbm_summary` | 按 WBM material_id 展开 | 256,963 条 WBM 候选材料性质表记录进入 H2；无三维结构 |
| `matbench_mp_energies` | 按 MP material_id 展开 | 154,718 条 MP 参考能量记录进入 H2；无三维结构 |
| `matbench_phonondb_pbe_103` | 按 PhononDB 结构展开 | 103 条结构与热导率标签进入 H2 |
| `hydrocarbons_gap_ch` | 按 extxyz frame 展开 | 35,663 条 C/H 构型进入 H2，含坐标、能量、力、应力和 config_type |

ANI-1x 和 Transition1x 是 5G/6G 级文件，不适合把数百万构象全部展开到 H2。当前采用轻量索引方案：保留全量规模信息，但页面只展示 group/reaction 级代表记录。

## 5. 各数据集字段缺失情况

### 5.1 `ani_gdb_s03`

已有重点字段：

- SMILES
- 原子坐标
- 能量
- 回转半径 Rg
- 原子数

主要缺失字段：

- 力
- HOMO / LUMO / gap
- 电荷、自旋
- 温度、密度、Tg
- 杨氏模量、拉伸强度
- 计算软件、DOI
- 补充属性

### 5.2 `data0000_aselmdb`

已有重点字段：

- 原子坐标
- 能量
- 力
- HOMO / LUMO / gap
- 电荷、自旋
- composition
- 原子数
- warnings

主要缺失字段：

- SMILES
- DOI
- 数据集说明
- 力场、模拟类型
- 温度、密度、Tg
- 杨氏模量、拉伸强度
- 计算时间
- 补充属性

### 5.3 `openpoly_calculated`

已有重点字段：

- SMILES / PSMILES
- 能量
- HOMO / LUMO / gap
- 偶极矩
- 极化率
- 热力学校正量
- 计算软件
- DOI

主要缺失字段：

- 三维原子坐标
- Rg
- 力
- composition
- 电荷、自旋
- 密度、Tg
- 杨氏模量、拉伸强度
- 计算时间

### 5.4 `ani1x_less_is_more`

已有重点字段：

- 代表构象坐标
- 能量
- 力
- composition / 化学式 group
- 原子数
- Rg
- 可用 HDF5 字段摘要

主要缺失字段：

- SMILES
- HOMO / LUMO / gap
- 电荷、自旋
- 温度、密度、Tg
- 杨氏模量、拉伸强度
- 计算时间

### 5.5 `transition1x`

已有重点字段：

- 代表反应图像坐标
- 能量
- 力
- reaction id
- composition / 化学式
- 原子数
- Rg
- reactant / transition state / product 结构组信息摘要

主要缺失字段：

- SMILES
- HOMO / LUMO / gap
- 电荷、自旋
- 温度、密度、Tg
- 杨氏模量、拉伸强度
- 计算时间

## 6. 后端 API 概览

| 接口 | 说明 |
| --- | --- |
| `GET /api/health` | 健康检查，返回当前数据集 ID |
| `GET /api/datasets` | 首页数据集卡片 |
| `GET /api/datasets/{datasetId}` | 数据集概要 |
| `GET /api/datasets/{datasetId}/records` | 数据集记录列表，支持分页和筛选 |
| `GET /api/datasets/{datasetId}/records/{recordId}` | 单条记录详情 |
| `GET /api/datasets/{datasetId}/records/{recordId}/download.csv` | 下载记录坐标 CSV |
| `GET /api/datasets/{datasetId}/records/{recordId}/download.json` | 登录用户下载单条完整展示记录 JSON |
| `GET /api/datasets/{datasetId}/download.csv` | 管理员下载整个数据集展示记录 CSV |
| `POST /api/assistant/chat` | 登录用户使用智能助手，可携带数据集或当前记录上下文 |
| `GET /api/intake/sources` | 查看公开来源候选目录 |
| `POST /api/intake/submissions` | 注册用户提交候选数据集 |
| `GET /api/intake/submissions` | 查询投稿或超级管理员审核队列 |
| `PATCH /api/intake/submissions/{id}/review` | 超级管理员审核投稿 |
| `POST /api/intake/submissions/{id}/prepare` | 将批准来源推进到适配阶段 |

记录列表支持：

- `search`
- `energyMin`
- `energyMax`
- `atomMin`
- `atomMax`
- `offset`
- `limit`

## 7. 智能助手接入

当前调用链路：

```text
Vue 智能助手页 -> Spring Boot /api/assistant/chat -> Ollama /api/chat -> qwen3:8b
```

- 前端新增 `/assistant` 页面；首页、数据发现页、数据集页与结构详情页均可进入。
- 从数据集或结构页面进入时，前端仅传递 `datasetId` / `recordId`，后端从展示库读取真实字段构造上下文。
- 游客只有查看权限；注册用户、管理员和超级管理员可发起模型问答。
- Linux 正式部署时，后端应与 Ollama 同机运行并仅访问 `127.0.0.1:11434`，不要将 Ollama API 直接暴露到公网。

## 8. 数据接入与更新管线

前端新增 `/intake` 数据接入中心，后端使用独立文件库 `documents/data/vasp_intake.mv.db` 保存流程状态：

1. 游客可浏览候选公开来源和接入流程。
2. 注册用户、管理员和超级管理员可提交候选数据集；最小必填信息为“数据集说明 + 至少一个论文链接或数据链接”，名称、类型、格式、许可证和可提供字段均可选填。
3. 超级管理员审核来源，批准后推进到格式适配阶段。
4. 外部异构数据只有在开发相应解析适配器、字段/单位映射和质量校验后，才能重建展示库并正式发布。

当前“候选公开来源”是后端人工维护的来源清单，不是实时自动抓取结果。当前实现的是可追溯的投稿、审核与接入排期管线；定时扫描公开 API、下载原始数据、自动重建 H2/PostgreSQL 并发布，仍属于下一阶段执行器建设内容。

## 9. 需要提升的地方

1. 查询性能与数据库演进

能量和原子数目前为展示表中的文本字段，数值范围检索仍需类型转换。后续应增加数值列和组合索引，并在构建阶段生成统计汇总表。面向公网多用户并发和持续扩容时，可将展示查询层迁移到 PostgreSQL。

2. 大 HDF5 懒加载

当前 ANI-1x 和 Transition1x 只展示代表构象。后续可以增加接口，按 group/reaction 和 index 动态读取任意构象，而不是只看第 0 个代表结构。

3. 单位标注

当前页面主要按原始数值展示。后续应在详情页明确坐标、能量、力等字段单位，例如 Hartree、eV、Å、eV/Å。

4. 数据集专属筛选

当前筛选比较通用。后续可以增加：

- 化学式筛选
- reaction id 筛选
- 构象数/图像数范围
- 是否包含 CCSD(T) 字段
- 是否包含力字段

5. 展示库构建流程

当前 H2 需要手动运行脚本重建。后续可以增加：

- 数据版本记录
- 构建日志
- 增量构建
- 构建后自动校验记录数

6. 详情页分区

当前详情页是通用结构。后续可以针对不同数据集做专属展示：

- Transition1x：突出 reactant / transition state / product。
- ANI-1x：突出不同理论层级能量和力字段。
- OpenPoly：突出热力学校正、偶极矩、极化率。

7. Docker 数据体积

当前 Docker 镜像如果包含完整 H2 展示库可以展示页面，但 5G/6G 原始 HDF5 不适合直接打进镜像。后续应考虑外部 volume 挂载或只打包 H2 展示库。
