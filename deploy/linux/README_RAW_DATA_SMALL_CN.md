# 原始数据小包说明

本包是 `vasp-show-raw-data-package.tar` 的小体积版本，只包含三个较小的原始数据文件，用于先行部署和功能验证。

包含文件：

```text
documents/data/ani_gdb_s03.h5
documents/data/data0000.aselmdb
documents/data/calculated_polymer_data.xlsx
deploy/linux/README_RAW_DATA_SMALL_CN.md
```

未包含的两个大文件：

```text
documents/data/ani1x-release.h5
documents/data/Transition1x.h5
```

建议解包位置：

```bash
cd /opt/vasp-show
tar -xf vasp-show-raw-data-package-small.tar
```

说明：

- 当前小包用于先验证三个小数据文件相关功能。
- 不包含 `*.lock`、`*_first30.csv`、临时文件和两个大体积 HDF5 数据集。
- 如后续需要完整数据，再单独上传 `ani1x-release.h5` 和 `Transition1x.h5`。
