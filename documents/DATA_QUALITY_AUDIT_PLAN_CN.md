# VASP Show 计算数据库质量验证与自动接入方案 V2.0

## 1. 定位

本方案把当前平台的“初始校对”升级为“分级科研数据审计”。它不是一次性宣称所有数据都已完成科学认证，而是把数据从发现、候选、预检、专家审核、适配器开发、展示库构建到正式发布拆成可追踪的门控流程。

当前平台已经做到：

- 自动发现候选数据集、生成候选列表和预评分。
- 管理员审核候选，转入接入申请。
- 注册用户可提交数据集线索、链接和抽样文件。
- 页面可验证 DOI / 论文链接 / 数据下载链接的可访问性。
- 页面可上传 CSV、TSV、JSON、JSONL、XYZ、CIF、HDF5 小样本做字段预检。
- 后端质量页可统计 H2 展示库中的字段覆盖、结构覆盖、数值标签、来源追溯、发布状态。
- 后端已新增结构 JSON 抽样、疑似重复签名、单位/方法证据、自动入库适配成熟度评分。
- 新增 `python/quality_scientific_probe.py`，上传 XYZ/CIF 样本时可选调用 ASE 做结构科学预检。

仍未完成的闭环：

- 自动下载完整数据。
- 自动解析全部原始文件。
- 自动单位转换和字段标准化。
- 自动构建 H2 正式展示库。
- 自动上线、版本回滚和全量科学认证。

因此当前质量页应被解释为“自动预检 + 审核门控”，不是最终科学认证证书。

## 2. 质量门控层级

### L0 来源可信性

目标是确认数据是否值得进入候选库。

检查项：

- DOI 是否可访问。
- 论文链接是否可访问。
- 数据下载链接是否可访问。
- 是否有许可证、版本号、发布机构、作者或维护方。
- 数据来源是否和论文描述一致。

当前实现：

- `POST /api/quality/validate-links` 已做 HTTP 可访问性验证。

不足：

- 还未验证论文正文和数据内容的一致性。
- 还未检查许可证是否允许再分发。
- 还未保存 checksum、文件大小和下载时间。

### L1 格式与字段预检

目标是判断文件是否能被接入。

检查项：

- 文件格式：CSV / TSV / JSON / JSONL / XYZ / EXTXYZ / CIF / HDF5。
- 抽样记录数、字段名、字段类型、示例值。
- 字段映射到统一字段：标识、组成/元素、原子数、结构坐标、目标性质、来源 DOI、计算方法。
- 缺失字段列表。

当前实现：

- `POST /api/quality/preview-file` 已解析小样本。
- HDF5 目前会列出 group/dataset 路径和维度。

不足：

- HDF5 仍只是浅层语义识别，不会自动理解复杂嵌套数据集。
- LMDB、压缩包和多文件数据集仍需要专用适配器。

### L2 科学一致性抽样

目标是从“能读字段”推进到“结构和数值初步可信”。

检查项：

- 结构 JSON 是否可解析。
- 原子数是否和结构原子列表一致。
- 坐标是否为有限数值。
- 周期材料是否有有效晶胞。
- force 向量数量是否和原子数一致。
- 是否有疑似重复记录。
- 是否有异常原子数。

当前实现：

- 后端 `DisplayDatasetService` 已对展示库抽样 160 条结构记录。
- 生成 `structureValidity`、`duplicateSignature`、`unitMethod`、`adapterReadiness` 四个新增指标。
- 上传 XYZ/CIF 文件时可选调用 `python/quality_scientific_probe.py`，若环境安装 ASE，会检查原子数、元素、坐标、短距离接触、周期晶胞、能量/力字段命中情况。

不足：

- 展示库抽样不是全量检查。
- 疑似重复签名不是结构哈希。
- 尚未检查所有键长、晶胞体积分布、能量异常值、单位一致性。
- ASE/pymatgen 依赖需要在服务器 Python 环境安装后才能启用。

### L3 数值标签与单位审计

目标是判断数据能否用于模型训练或专家复现。

检查项：

- energy 是总能、每原子能、形成能还是吸附能。
- force 单位是 eV/A、Hartree/Bohr 还是其他。
- stress、bandgap、phonon、dielectric、modulus 等性质是否有单位。
- VASP/ORCA/Gaussian 的版本、泛函、基组/赝势、色散校正、收敛阈值是否明确。

当前实现：

- 后端用软件覆盖、DOI/链接覆盖、目标标签覆盖估计 `unitMethod`。

不足：

- 还没有结构化 `energy_unit`、`force_unit`、`stress_unit` 字段。
- 还没有自动换算单位。
- 还不能根据 OUTCAR/vasprun.xml 自动校验 INCAR/KPOINTS/POTCAR 参数。

### L4 发布与权限门控

目标是控制哪些数据集可公开展示、下载和建模。

角色：

- 超级管理员：可发布/隐藏数据集，审核候选数据集，管理用户和质量门控。
- 管理员：可查看完整质量报告，可下载整库，可处理接入队列。
- 注册用户：可查看公开质量摘要，提交数据集线索，上传抽样文件，下载单条数据。
- 游客：只可查看已发布数据集和公开摘要。

当前实现：

- 超级管理员可在质量页控制数据集发布/隐藏。
- 游客和注册用户不能查看完整质量台账、问题列表和报告。

不足：

- 发布决策还没有绑定 H2 构建版本号。
- 隐藏数据集还没有自动生成下线审计记录。

## 3. 当前评分模型

后端当前质量分由以下指标组成：

| 指标 | 权重 | 含义 |
|---|---:|---|
| 基础完整性 | 22% | 标识、组成、原子数覆盖 |
| 结构覆盖 | 18% | 需要结构的数据是否有三维坐标/晶胞 |
| 数值标签 | 18% | energy、force、电子性质、目标性质覆盖 |
| 来源追溯 | 16% | DOI/数据链接、计算软件、记录标识 |
| 结构抽样合法性 | 10% | 结构 JSON 抽样解析、坐标、原子数、晶胞 |
| 疑似重复签名 | 6% | source/material/composition/atom_count/energy 组合唯一性 |
| 单位/方法证据 | 6% | 单位、软件、理论层级、引用证据 |
| 适配器成熟度 | 4% | 是否具备进入自动入库闭环的条件 |

惩罚项：

- `warnings_json` 比例过高会扣分。
- 结构抽样失败、重复风险、单位证据不足、异常原子数会额外扣分。

质量等级：

- 85-100：优，适合展示和优先建模。
- 70-84：良，需要专家复核部分字段。
- 55-69：需补充，只建议浏览，不建议直接建模。
- 0-54：高风险，应暂缓发布或进入内部排查。

## 4. 自动接入闭环目标流程

最终目标不是“发现后直接上线”，而是下面的安全闭环：

1. 自动发现来源  
   定期检索 DataCite、Zenodo、Figshare、Materials Cloud、NOMAD、Materials Project、AFLOW、OQMD、JARVIS、Matbench 等来源。

2. 候选元数据抽取  
   自动抽取标题、DOI、论文链接、数据链接、许可证、文件格式、规模、作者、版本、关键词、计算方法。

3. 来源预检  
   验证链接可访问性、许可证、文件大小、checksum、版本号和论文-数据对应关系。

4. 小样本下载  
   只下载前若干 MB 或官方 sample，避免直接拉取超大数据。

5. 格式识别  
   自动识别 CIF、POSCAR、CONTCAR、XYZ、EXTXYZ、HDF5、LMDB、JSON、CSV、vasprun.xml、OUTCAR。

6. 科学解析  
   调用 ASE / pymatgen / h5py / lmdb / pandas 解析结构和标签。

7. 质量评分  
   生成来源、字段、结构、数值、单位、重复、异常、适配器成熟度评分。

8. 超级管理员审核  
   审核通过后进入适配器开发或启用通用解析器。

9. Staging 入库  
   构建临时 H2 或中间库，生成 diff 报告和质量报告。

10. 正式发布  
   绑定构建版本、发布人、发布时间、回滚版本和下载权限。

## 5. 适配器 Manifest 建议

每个正式接入的数据集应有一个 manifest，例如：

```yaml
dataset_id: example_dataset
name: Example Materials Dataset
source:
  paper_doi: 10.xxxx/example
  data_url: https://...
  license: CC BY 4.0
  version: v1
files:
  format: hdf5
  expected_size: 12GB
  checksum: sha256:...
parser:
  type: hdf5
  entry_path: /data
  record_axis: group
fields:
  structure: positions
  atomic_numbers: atomic_numbers
  energy: energy
  forces: forces
units:
  length: A
  energy: eV
  force: eV/A
validation:
  require_structure: true
  require_energy: true
  require_forces: false
  periodic: true
release:
  default_visibility: hidden
  required_reviewer_role: SUPER_ADMIN
```

## 6. 管理员审核页面应展示的内容

候选数据集进入接入队列后，应展示：

- 当前阶段：候选发现、来源审核、小样本预检、适配器开发、Staging 构建、待发布、已发布、驳回。
- 来源证据：DOI、论文链接、数据链接、许可证、版本。
- 文件证据：格式、大小、checksum、样本记录数、字段字典。
- 科学证据：结构解析率、坐标异常、晶胞缺失、重复签名、单位完整性、异常能量。
- 接入建议：通用解析器可入库、需要专用适配器、仅可展示元数据、暂缓。
- 审核操作：通过来源、驳回来源、转入适配器、触发 Staging 构建、发布、隐藏、回滚。

## 7. 本轮代码已完成

- `DisplayDatasetService` 增强质量评分：结构抽样、重复签名、单位/方法证据、适配器成熟度。
- `QualityPreflightService` 增加可选 Python 科学预检调用。
- `python/quality_scientific_probe.py` 新增 ASE 抽样结构验证脚本。
- `application.yml` 新增 `vasp.quality.python-command`，可用 `VASP_QUALITY_PYTHON` 覆盖。
- 质量页文件预检结果新增“预检建议”展示。
- 审核规则新增“科学一致性与异常检测”“自动入库闭环就绪度”。

## 8. 下一步优先级

1. 在服务器 Python 环境安装 `ase` 和 `pymatgen`，让上传预检真正执行结构解析。
2. 为 HDF5/LMDB 数据集建立 manifest 和专用 adapter。
3. 把预检结果落库，形成历史审核记录，而不是只在页面临时显示。
4. 增加 staging H2 构建目录、构建日志和 diff 报告。
5. 引入结构哈希去重、能量分布异常检测、键长异常检测。
6. 让发布状态绑定构建版本，支持一键回滚。
