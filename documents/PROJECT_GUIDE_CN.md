# VASP Show 项目说明文档

## 1. 项目定位

VASP Show 是一个本地分子、聚合物和材料量化计算数据集展示项目。当前版本已经接入 10 个独立数据集，并统一转换到 H2 展示库，供 Spring Boot API 和 Vue 前端分页、筛选、查看详情使用。

仓库名叫 `vasp-show`，但当前代码不是 VASP 原始输出文件解析器，也没有直接解析 `POSCAR`、`OUTCAR`、`vasprun.xml`。当前展示对象是已经整理好的分子/聚合物量化计算记录。

当前主要运行方式是 Windows 本地开发：

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 后端 Spring Boot | `http://localhost:8080` | REST API |
| 前端 Vite | `http://localhost:5173` | 页面入口 |
| Docker | `http://localhost:8088` | 备用部署方案，当前机器未启用 |

## 2. 当前已接入数据集

首页 `/` 展示 10 个入口：

| 数据集 ID | 原始文件 | 规模 | 简述 |
| --- | --- | --- | --- |
| `ani_gdb_s03` | `documents/data/ani_gdb_s03.h5` | 151,200 条构象 / 20 个分子组 | ANI-1 / GDB-11 subset |
| `data0000_aselmdb` | `documents/data/data0000.aselmdb` | 2,524 条聚合物量化计算记录 | OPoly26-val / ASE-LMDB 风格 |
| `openpoly_calculated` | `documents/data/calculated_polymer_data.xlsx` | 1,000 条聚合物量化计算记录 | OpenPoly 论文配套计算数据 |
| `ani1x_less_is_more` | `documents/data/ani1x-release.h5` | 4,956,005 条构象 / 3,114 个化学式 group | Less is more / ANI-1x 主动学习数据 |
| `transition1x` | `documents/data/Transition1x.h5` | 9,624,594 条构象 / 10,073 个收敛反应 | Transition1x NEB 反应路径数据 |
| `twod_matpedia` | `../数据集及论文/2Dmatpedia/2Dmatpedia.json` | 6,351 个二维材料结构 | 2DMatPedia 二维材料 VASP DFT 数据 |
| `jarvis_dft_3d` | `../数据集及论文/jdft/jdft_3d-9-24-2025.json` | 93,902 条三维晶体材料记录 | JARVIS-DFT 3D bulk materials |
| `jarvis_dft_2d` | `../数据集及论文/jdft/6815705.zip` | 1,103 条二维材料记录 | JARVIS-DFT 2D materials |
| `polymer_genome_1073` | `../数据集及论文/polymer-cif/Polymer-CIF.tgz` | 1,073 个聚合物 CIF 结构 | Polymer Genome 1073 |
| `qmof_database` | `../数据集及论文/qmof/qmof_database.zip` | 20,372 个 MOF / 配位聚合物结构 | QMOF Database |

统一展示库：

```text
documents/data/frontend_template_data.mv.db
```

当前展示库共 290,712 条记录。ANI-GDB 仍按 conformer 展开；ANI-1x 按化学式 group 建立可浏览索引；Transition1x 按 reaction group 建立可浏览索引，每条记录展示一个代表构象；材料类数据集按 JSON/CIF 记录展开：

| 数据集 | 记录数 |
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
| 合计 | 290,712 |

注意：`ani_gdb_s03` 的 151,200 是 conformer 数，不是 151,200 个不同分子。该 HDF5 文件包含 20 个 molecule group，每个 group 下有多个 conformer。

## 3. 数据来源说明

### 3.1 ANI-GDB s03

| 项 | 值 |
| --- | --- |
| 文件 | `documents/data/ani_gdb_s03.h5` |
| 数据集 | ANI-1 / GDB-11 subset |
| 论文 | ANI-1, A data set of 20 million calculated off-equilibrium conformations for organic molecules |
| DOI | `10.1038/sdata.2017.193` |
| 泛函数 | `ωB97X` |
| 基组 | `6-31G(d)` |
| 软件 | Gaussian 09 |
| H5 字段 | `coordinates`, `coordinatesHE`, `energies`, `energiesHE`, `smiles`, `species` |

页面列表仍按 conformer 展开，但名称会自然化为类似：

```text
ANI 分子组 0 · C3H8 · 11 原子 · 构象 1
```

### 3.2 ASE-LMDB polymers

| 项 | 值 |
| --- | --- |
| 文件 | `documents/data/data0000.aselmdb` |
| 数据集归属 | 最合理对应 OPoly26-val / ASE-LMDB |
| 论文 | The Open Polymers 2026 (OPoly26) Dataset and Evaluations |
| arXiv / DOI | `10.48550/arXiv.2512.23117` |
| 泛函数 | `ωB97M-V` |
| 基组 | `def2-TZVPD` |
| 软件 | ORCA 6.0.0 |

文件内部没有显式 DOI，当前归属基于字段、路径和数据形态判断。页面上的 material 名称由 UUID、composition 和原子数派生。

### 3.3 OpenPoly calculated polymers

| 项 | 值 |
| --- | --- |
| 文件 | `documents/data/calculated_polymer_data.xlsx` |
| 论文 PDF | `d:\Downloads\s10118-025-3402-y.pdf` |
| 论文 | OpenPoly: A Polymer Database Empowering Benchmarking and Multi-property Predictions |
| DOI | `10.1007/s10118-025-3402-y` |
| 规模 | 1,000 条 |
| 软件 | ORCA |
| Source | QC |
| reaction_type | Polyamidation |

`Theory_Level`：

```text
opt+freq: b3lyp/def2-TZVP em=gd3bj
energy: wB97M-V/ma-def2-TZVP
```

Excel 中没有三维原子坐标，所以详情页不会渲染 3D viewer，会显示“当前记录没有原子三维坐标”。补充计算属性展示在详情页 meta tab。

### 3.4 ANI-1x / Less is more

| 项 | 值 |
| --- | --- |
| 数据集 ID | `ani1x_less_is_more` |
| 论文 | Less is more: Sampling chemical space with active learning |
| DOI | `10.1063/1.5023802` |
| 规模 | 当前 HDF5 文件含 4,956,005 条结构构象；3,114 个化学式 group |
| 元素 | H、C、N、O |
| 泛函数 | `ωB97X` |
| 基组 | `6-31G(d)` |
| 论文链接 | `https://github.com/isayev/ASE_ANI`、`https://github.com/isayev/COMP6` |
| HDF5 文件 | `documents/data/ani1x-release.h5` |
| 文件内规模 | 3,114 个化学式 group；4,956,005 条构象 |
| 字段 | `atomic_numbers`, `coordinates`, `wb97x_dz.energy`, `wb97x_dz.forces`, `ccsd(t)_cbs.energy` 等 |
| Figshare DOI | `10.6084/m9.figshare.10047041.v1` |

当前项目按 HDF5 化学式 group 建立可浏览索引，每条记录展示该 group 的第 0 个代表构象和可用能量/力摘要，避免把近 500 万构象全部展开到 H2。

### 3.5 Transition1x

| 项 | 值 |
| --- | --- |
| 数据集 ID | `transition1x` |
| 论文 | Transition1x - a dataset for building generalizable reactive machine learning potentials |
| 论文 DOI | `10.1038/s41597-022-01870-w` |
| 数据 DOI | `10.6084/m9.figshare.19614657.v4` |
| 规模 | 9,624,594 条 DFT 构象；10,073 个收敛反应 |
| HDF5 文件 | `documents/data/Transition1x.h5` |
| 元素 | H、C、N、O；最多 7 个重原子 |
| 泛函数 | `ωB97X` |
| 基组 | `6-31G(d)` |
| 软件 | ORCA 5.0.2；ASE 3.22.1 |
| HDF5 字段 | `atomic_numbers`, `energy`, `forces`, `positions` |

当前项目按 reaction group 建立可浏览索引，每条记录展示该反应路径的第 0 个代表构象和可用能量/力摘要，避免把 962 万构象全部展开到 H2。

## 4. 技术栈

### 4.1 后端

后端目录：`backend/`

| 技术 | 用途 |
| --- | --- |
| Java 17 | 运行环境 |
| Spring Boot 3.5.14 | REST API |
| Maven | 依赖、测试、打包 |
| H2 JDBC | 读取统一展示库 |
| JHDF | 保留 ANI HDF5 group/conformer 旧接口读取能力 |
| JUnit 5 / MockMvc | 后端测试 |

关键文件：

| 文件 | 作用 |
| --- | --- |
| `backend/src/main/resources/application.yml` | 后端端口和数据路径配置 |
| `backend/src/main/java/com/vaspshow/backend/controller/DatasetController.java` | API 控制器 |
| `backend/src/main/java/com/vaspshow/backend/service/DisplayDatasetService.java` | 三数据集展示库查询服务 |
| `backend/src/main/java/com/vaspshow/backend/service/AniDatasetService.java` | ANI HDF5 旧接口读取服务 |
| `backend/src/main/java/com/vaspshow/backend/dto/` | API 响应 DTO |
| `backend/src/test/java/com/vaspshow/backend/AniDatasetServiceTest.java` | 后端接口测试 |

当前配置：

```yaml
server:
  port: 8080

vasp:
  datasets:
    ani-path: classpath:data/ani_gdb_s03.h5
    display-db-path: documents/data/frontend_template_data
```

### 4.2 前端

前端目录：`frontend/`

| 技术 | 用途 |
| --- | --- |
| Vue 3.4 | 页面开发 |
| Vue Router 4 | 路由 |
| Vite 5 | 本地开发和构建 |
| Three.js | 3D 分子构象展示 |
| Chart.js | 能量曲线等图表 |
| 原生 Fetch API | API 请求 |

关键文件：

| 文件 | 作用 |
| --- | --- |
| `frontend/src/views/Home.vue` | 三数据集首页卡片 |
| `frontend/src/views/DatasetRecords.vue` | 数据集记录列表、搜索、筛选、分页 |
| `frontend/src/views/Detail.vue` | 单条记录详情、3D 展示、补充属性 |
| `frontend/src/components/Molecule3DViewer.vue` | Three.js 3D 分子组件 |
| `frontend/src/api/index.js` | API 请求封装 |
| `frontend/src/router/index.js` | 路由配置 |

主要路由：

| 路由 | 说明 |
| --- | --- |
| `/` | 首页 |
| `/datasets/:id` | 数据集记录列表 |
| `/datasets/:id/records/:recordId` | 单条记录详情 |
| `/detail/:id` | 旧路由，重定向到数据集记录列表 |

## 5. 后端 API

接口统一以 `/api` 开头。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查，返回当前五数据集 ID |
| GET | `/api/datasets` | 获取首页数据集卡片 |
| GET | `/api/datasets/{datasetId}` | 获取数据集摘要 |
| GET | `/api/datasets/{datasetId}/records` | 分页获取记录列表 |
| GET | `/api/datasets/{datasetId}/records/{recordId}` | 获取记录详情 |
| GET | `/api/datasets/{datasetId}/records/{recordId}/download.csv` | 下载记录坐标 CSV |
| GET | `/api/datasets/{datasetId}/groups/{groupId}` | ANI 旧接口：分子组摘要 |
| GET | `/api/datasets/{datasetId}/groups/{groupId}/conformers/{index}` | ANI 旧接口：conformer 详情 |
| GET | `/api/datasets/{datasetId}/groups/{groupId}/conformers/{index}/download.csv` | ANI 旧接口：conformer CSV |

记录列表支持查询参数：

| 参数 | 说明 |
| --- | --- |
| `search` | 按名称、材料 ID、SMILES、composition 等搜索 |
| `offset` | 分页偏移 |
| `limit` | 每页数量，后端最大限制为 80 |
| `energyMin`, `energyMax` | 能量范围 |
| `atomMin`, `atomMax` | 原子数范围 |

示例：

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/datasets
curl "http://localhost:8080/api/datasets/ani_gdb_s03/records?limit=24"
curl "http://localhost:8080/api/datasets/data0000_aselmdb/records?search=C6&atomMin=10&atomMax=200"
curl http://localhost:8080/api/datasets/openpoly_calculated/records/153725
```

## 6. 本地启动

### 6.1 环境

当前机器已确认：

| 工具 | 路径 / 说明 |
| --- | --- |
| JDK 17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` |
| Maven | `C:\Users\zhangqikai\tools\apache-maven-3.9.9` |
| Python | 项目约定 `small_tools`，当前会话实际可用 `D:\anaconda3\python.exe` |

### 6.2 启动后端

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

### 6.3 启动前端

```powershell
cd frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

开发环境下，`frontend/vite.config.js` 会把 `/api` 代理到 `http://localhost:8080`。

### 6.4 停止本地服务

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

## 7. 数据库生成

统一展示库由脚本生成：

```text
python/build_frontend_template_h2.py
```

生成命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
D:\anaconda3\python.exe python/build_frontend_template_h2.py
```

脚本会读取：

```text
documents/data/ani_gdb_s03.h5
documents/data/data0000.aselmdb
documents/data/calculated_polymer_data.xlsx
```

并重建：

```text
documents/data/frontend_template_data.mv.db
```

`display_records` 表包含 `properties_json` 字段，用于保存 OpenPoly 的补充计算属性。

## 8. 构建、测试和检查

### 8.1 后端测试

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;C:\Users\zhangqikai\tools\apache-maven-3.9.9\bin;$env:Path"
cd backend
mvn test
```

最近一次通过结果：8 tests, 0 failures。

### 8.2 前端构建

```powershell
cd frontend
npm run build
```

最近一次构建通过，仅有 Vite chunk 大于 500 kB 的警告。

## 9. Docker 备用部署

Docker 是备用方案，不是当前本地运行方式。默认访问端口：

```text
http://localhost:8088
```

关键文件：

| 文件 | 作用 |
| --- | --- |
| `docker/docker-compose.yml` | 编排后端和前端 |
| `docker/backend.Dockerfile` | 构建 Spring Boot 后端镜像 |
| `docker/frontend.Dockerfile` | 构建前端 Nginx 镜像 |
| `docker/nginx.conf` | 前端静态服务和 `/api` 反向代理 |

后端 Docker 镜像需要包含：

```text
documents/data/frontend_template_data.mv.db
```

当前 `docker/backend.Dockerfile` 已复制该文件到：

```text
/app/documents/data/frontend_template_data.mv.db
```

`.dockerignore` 也已允许该 H2 文件进入 Docker build context。若展示库更新，需要重新构建后端镜像。

启动命令：

```powershell
docker compose -f docker/docker-compose.yml up --build -d
```

健康检查：

```powershell
curl http://localhost:8088/api/health
```

## 10. 目录结构

```text
vasp-show/
├── backend/                 # Spring Boot 后端 API
├── frontend/                # Vue 3 + Vite 前端
├── documents/               # 文档、数据样例和本地数据文件
│   ├── data/                # HDF5、LMDB、Excel、CSV、H2 数据文件
│   └── chat_history/        # 开发过程记录
├── docker/                  # Dockerfile、compose 和 nginx 配置
├── python/                  # 数据导出和 H2 构建辅助脚本
├── output/                  # 工具输出目录
└── .agents/skills/          # 仓库本地 Codex 技能说明
```

## 11. 常见问题

### 11.1 前端能打开，但数据加载失败

先确认后端：

```powershell
curl http://localhost:8080/api/health
```

再确认 Vite 代理目标是否仍是 `http://localhost:8080`。

### 11.2 首页没有新增数据集

通常是 H2 展示库未更新，或后端仍在使用旧进程。重新生成 `documents/data/frontend_template_data.mv.db` 后，重启后端。

### 11.3 OpenPoly 详情页为什么没有 3D 结构

`calculated_polymer_data.xlsx` 没有三维原子坐标。详情页会显示无三维坐标提示，并展示可用的补充计算属性。

### 11.4 现在是否需要 Docker

不需要。当前开发和演示推荐用本地 Spring Boot `8080` 加 Vite `5173`。Docker 仅作为备用部署方案。
