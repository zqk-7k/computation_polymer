# VASP Show

面向分子、高分子、晶体、二维材料、反应路径与 MOF 的计算数据展示与治理平台。

## 主要模块

- `backend/`：Spring Boot 后端，提供数据集、记录详情、质量验证、数据发现、数据接入、用户权限和智能助手接口。
- `frontend/`：Vite + Vue 前端，包含数据中心、数据发现、质量验证、数据接入中心、模型、工作流和智能助手页面。
- `python/`：数据构建、CSV 导出、科学质量预检等脚本。
- `documents/`：中文技术文档、设计文档、部署与运维说明。
- `deploy/`、`docker/`：Linux 和 Docker 部署参考。

## 数据说明

大型原始数据集、H2 展示库、HDF5、LMDB、压缩包和构建产物不纳入 Git。

需要本地恢复的数据目录：

- `documents/data/`
- `backend/src/main/resources/data/`

请根据 `documents/PROJECT_OVERVIEW_CN.md`、`documents/CURRENT_TECH_DATA_GUIDE_CN.md` 和 `deploy/linux/README_RAW_DATA_CN.md` 恢复或重建数据文件。

## 本地开发

后端：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;C:\Users\zhangqikai\tools\apache-maven-3.9.9\bin;$env:Path"
cd backend
mvn spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

默认访问：

- 前端：http://localhost:5173/
- 后端：http://localhost:8080/

## 安全提醒

正式部署前必须通过环境变量替换管理员密码，并配置 HTTPS、反向代理和数据目录权限。
