# 原始数据包说明

此包包含后续“任意构象懒加载”可能需要的原始数据文件。

解包位置建议与部署包保持一致：

```bash
cd /opt/vasp-show
tar -xf vasp-show-raw-data-package.tar
```

解包后文件路径应为：

```text
documents/data/ani_gdb_s03.h5
documents/data/data0000.aselmdb
documents/data/calculated_polymer_data.xlsx
documents/data/ani1x-release.h5
documents/data/Transition1x.h5
```

说明：

- 当前线上展示只依赖 `documents/data/frontend_template_data.mv.db`。
- 原始数据文件主要用于后续新增懒加载接口，例如按 group/reaction 和 index 读取任意构象。
- 不需要上传 `*.lock`、`first30.csv`、Excel 临时锁文件。
