# VASP Show 技术文档

## 1. 当前运行状态

当前项目运行在 Windows 本地开发环境，不是 Docker 容器部署。

| 服务 | 当前方式 | 地址 | 说明 |
| --- | --- | --- | --- |
| 后端 | 本地 Spring Boot | `http://localhost:8080` | 通过 `mvn spring-boot:run` 启动 |
| 前端 | 本地 Vite | `http://localhost:5173` | 通过 `npm run dev` 启动 |
| Docker | 未运行 | `http://localhost:8088` | 仓库提供 Docker 配置，但当前机器未启用 Docker |

当前本机可以看到 `8080` 和 `5173` 端口在监听，`8088` 未监听；同时当前终端环境中 `docker` 命令不可用。因此当前展示页面和 API 都来自本地进程。

## 2. 项目定位

VASP Show 是一个本地分子/材料计算数据展示系统。它不是在线数据库，也不是直接解析 VASP 原始输出文件的工具；当前主要目标是把本仓库中的本地计算数据整理成可浏览、可检索、可查看结构详情的 Web 应用。

当前已接入 10 个独立数据集：

| 数据集 ID | 来源 | 展示规模 | 说明 |
| --- | --- | ---: | --- |
| `ani_gdb_s03` | `ani_gdb_s03.h5` | 151,200 条构象 | ANI 风格 HDF5 分子构象数据 |
| `data0000_aselmdb` | `data0000.aselmdb` | 2,524 条记录 | ASE-LMDB 聚合物量化计算记录 |
| `openpoly_calculated` | `calculated_polymer_data.xlsx` | 1,000 条记录 | OpenPoly 论文配套聚合物量化计算记录 |
| `ani1x_less_is_more` | `ani1x-release.h5` | 3,114 条可浏览 group 记录；文件内 4,956,005 条构象 | Less is more / ANI-1x 主动学习数据 |
| `transition1x` | `Transition1x.h5` | 10,073 条 reaction 记录；文件内 9,624,594 条构象 | Transition1x NEB 反应路径数据 |
| `twod_matpedia` | `2Dmatpedia.json` | 6,351 条二维材料结构记录 | 2DMatPedia |
| `jarvis_dft_3d` | `jdft_3d-9-24-2025.json` | 93,902 条三维晶体材料记录 | JARVIS-DFT 3D |
| `jarvis_dft_2d` | `6815705.zip` | 1,103 条二维材料记录 | JARVIS-DFT 2D |
| `polymer_genome_1073` | `Polymer-CIF.tgz` | 1,073 个聚合物 CIF 结构 | Polymer Genome 1073 |
| `qmof_database` | `qmof_database.zip` | 20,372 个 MOF / 配位聚合物结构 | QMOF Database |

首页应把 10 个数据集作为独立入口展示。点击某个数据集后进入该数据集自己的记录列表，再点击具体记录进入详情页。

## 3. 技术栈

后端：

- Java 17
- Spring Boot 3.5.x
- Spring Web MVC
- Maven
- H2 Database Driver
- `io.jhdf:jhdf`，用于读取 HDF5

前端：

- Vue 3
- Vue Router
- Vite
- Three.js
- 原生 Fetch API
- 单文件组件内 scoped CSS

辅助数据处理：

- Python 脚本位于 `python/`
- 本仓库约定 Python 使用 conda 环境 `small_tools`
- H2 展示库由本地数据转换生成

## 4. 数据来源和展示库

原始数据文件位于：

```text
documents/data/ani_gdb_s03.h5
documents/data/data0000.aselmdb
documents/data/calculated_polymer_data.xlsx
documents/data/ani1x-release.h5
documents/data/Transition1x.h5
```

当前 API 和前端列表主要读取统一的 H2 展示库：

```text
documents/data/frontend_template_data.mv.db
```

需要注意：

- H2 展示库是“查询和展示用”的派生数据源。
- 它把 HDF5、LMDB 和 Excel 中适合页面展示的字段整理到 `display_records` 表中。
- 对 5G/6G 级 HDF5，展示库建立轻量索引：ANI-1x 按化学式 group 展示代表构象，Transition1x 按 reaction group 展示代表构象，不全量展开数百万构象。
- 这不代表多个数据集合并成了一个业务数据集；页面和路由仍按 `ani_gdb_s03`、`data0000_aselmdb`、`openpoly_calculated`、`ani1x_less_is_more`、`transition1x` 五个数据集分开展示。
- 旧说法“只接入 HDF5”已经过时。
- 旧说法“LMDB 未接入”已经过时。

后端仍保留部分 HDF5 解析接口，用于兼容早期 molecule group / conformer 访问能力。

## 5. 页面流程

当前前端路由：

| 页面 | 路由 | 说明 |
| --- | --- | --- |
| 数据中心首页 | `/` | 展示五个独立数据集入口 |
| 数据集记录列表 | `/datasets/:id` | 展示某一个数据集的记录列表和搜索框 |
| 单条记录详情 | `/datasets/:id/records/:recordId` | 展示结构、计算参数、热力学、电子结构、元数据等 |
| 旧路由兼容 | `/detail/:id` | 重定向到 `/datasets/:id` |

页面访问示例：

```text
http://localhost:5173/
http://localhost:5173/datasets/ani_gdb_s03
http://localhost:5173/datasets/data0000_aselmdb
http://localhost:5173/datasets/openpoly_calculated
http://localhost:5173/datasets/ani1x_less_is_more
http://localhost:5173/datasets/transition1x
http://localhost:5173/datasets/data0000_aselmdb/records/151201
```

## 6. 后端 API

后端默认端口：

```text
http://localhost:8080
```

主要接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| GET | `/api/datasets` | 获取五个数据集入口信息 |
| GET | `/api/datasets/{datasetId}` | 获取某个数据集概要 |
| GET | `/api/datasets/{datasetId}/records` | 获取某个数据集的记录列表 |
| GET | `/api/datasets/{datasetId}/records/{recordId}` | 获取单条记录详情 |
| GET | `/api/datasets/{datasetId}/records/{recordId}/download.csv` | 下载单条记录的坐标 CSV |

记录列表支持查询参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `search` | 空 | 按 source id、material id、SMILES、composition 搜索 |
| `offset` | `0` | 分页偏移 |
| `limit` | `24` | 每页数量，后端限制最大 80 |

API 示例：

```powershell
curl http://localhost:8080/api/health
curl http://localhost:8080/api/datasets
curl "http://localhost:8080/api/datasets/data0000_aselmdb/records?limit=5"
curl http://localhost:8080/api/datasets/data0000_aselmdb/records/151201
```

兼容保留的 HDF5 接口：

```text
GET /api/datasets/{datasetId}/groups/{groupId}
GET /api/datasets/{datasetId}/groups/{groupId}/conformers/{index}
GET /api/datasets/{datasetId}/groups/{groupId}/conformers/{index}/download.csv
```

这些接口主要服务于早期 `ani_gdb_s03` molecule group / conformer 详情逻辑。

## 7. 本地启动和停止

### 7.1 环境要求

| 工具 | 建议版本 |
| --- | --- |
| JDK | 17 |
| Maven | 3.8+ |
| Node.js | 20 LTS 或更高 |
| npm | 跟随 Node.js |

当前机器已安装：

```text
JDK 17: C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
Maven: C:\Users\zhangqikai\tools\apache-maven-3.9.9
```

### 7.2 启动后端

在项目根目录打开终端：

```powershell
cd backend
mvn spring-boot:run
```

如果当前终端还没有刷新 PATH，可以临时指定：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:MAVEN_HOME='C:\Users\zhangqikai\tools\apache-maven-3.9.9'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
mvn spring-boot:run
```

后端启动后访问：

```text
http://localhost:8080/api/health
```

### 7.3 启动前端

另开一个终端：

```powershell
cd frontend
npm ci
npm run dev
```

前端访问：

```text
http://localhost:5173
```

前端开发服务器通过 `frontend/vite.config.js` 把 `/api` 代理到：

```text
http://localhost:8080
```

### 7.4 停止当前本地服务

按端口停止当前前后端：

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

验证是否停止：

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen
```

如果没有输出，说明服务已经停止。

## 8. 构建和测试

后端测试：

```powershell
cd backend
mvn test
```

后端打包：

```powershell
cd backend
mvn -DskipTests package
```

前端构建：

```powershell
cd frontend
npm run build
```

当前测试应覆盖：

- HDF5 数据摘要读取
- 旧 conformer 坐标接口
- `/api/datasets` 返回五个数据集
- `data0000_aselmdb` 记录列表接口
- CSV 下载接口

## 9. Docker 备用部署

Docker 是可选部署方案，不是当前运行方式。

当前仓库提供：

```text
docker/docker-compose.yml
docker/backend.Dockerfile
docker/frontend.Dockerfile
docker/nginx.conf
```

默认 Docker 访问地址：

```text
http://localhost:8088
```

启动命令：

```powershell
docker compose -f docker/docker-compose.yml up --build -d
```

停止命令：

```powershell
docker compose -f docker/docker-compose.yml down
```

端口修改：

```powershell
$env:FRONTEND_PORT=8090
docker compose -f docker/docker-compose.yml up --build -d
```

Docker 部署结构：

| 服务 | 容器内端口 | 宿主机端口 |
| --- | ---: | ---: |
| backend | `8080` | 不直接暴露 |
| frontend / Nginx | `80` | 默认 `8088` |

Docker 模式下访问 API 走前端 Nginx 反向代理：

```text
http://localhost:8088/api/health
http://localhost:8088/api/datasets
```

重要注意：

- 当前机器未安装或未启用 Docker，`docker` 命令不可用。
- Docker 镜像必须包含 `documents/data/frontend_template_data.mv.db`，否则只能启动服务但无法展示五个数据集。
- 当前 `docker/backend.Dockerfile` 已把该 H2 文件复制到镜像内的 `/app/documents/data/frontend_template_data.mv.db`。
- `.dockerignore` 也需要允许该 H2 文件进入 Docker build context。

## 10. 关键文件

| 文件 | 作用 |
| --- | --- |
| `backend/src/main/java/com/vaspshow/backend/controller/DatasetController.java` | 后端 API 入口 |
| `backend/src/main/java/com/vaspshow/backend/service/DisplayDatasetService.java` | 读取 H2 展示库，提供五个数据集和记录详情 |
| `backend/src/main/java/com/vaspshow/backend/service/AniDatasetService.java` | 保留 HDF5 molecule group / conformer 解析能力 |
| `backend/src/main/resources/application.yml` | 后端端口和数据路径配置 |
| `frontend/src/router/index.js` | 前端路由 |
| `frontend/src/views/Home.vue` | 首页，五个数据集入口 |
| `frontend/src/views/DatasetRecords.vue` | 单数据集记录列表 |
| `frontend/src/views/Detail.vue` | 单条记录详情页 |
| `frontend/src/components/Molecule3DViewer.vue` | Three.js 结构展示 |
| `documents/data/frontend_template_data.mv.db` | 当前 API 和页面的主要展示库 |

## 11. 常见问题

### 页面能打开但没有数据

先检查后端：

```powershell
curl http://localhost:8080/api/health
curl http://localhost:8080/api/datasets
```

如果后端不通，前端的 `/api` 请求也会失败。

### 为什么数据来自 H2，而不是直接读 `.h5` 和 `.aselmdb`

原始 HDF5、LMDB 和 Excel 结构差异较大。为了让前端能统一分页、搜索和展示详情，项目先把可展示字段转换为 H2 展示库 `frontend_template_data.mv.db`。这只是展示层查询源，不改变五个数据集独立展示的业务逻辑。

### 3D 结构里的盒子是不是晶胞

当前 LMDB 原始记录中的 `cell` 字段不可作为可靠周期晶胞使用，因此 3D 页面中的盒子是坐标包围盒，用于辅助观察原子空间分布，不应解读为真实晶胞参数。

### 是否需要 Docker 才能运行

不需要。当前项目已经可以在 Windows 本地用 JDK、Maven、Node.js 运行。Docker 只是备用部署方案。
