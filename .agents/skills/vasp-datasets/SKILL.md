---
name: vasp-datasets
description: Work with the local molecular and polymer datasets in this repository, especially documents/data/ani_gdb_s03.h5, documents/data/data0000.aselmdb, documents/data/calculated_polymer_data.xlsx, documents/data/ani1x-release.h5, and documents/data/Transition1x.h5. Use when Codex needs to inspect dataset fields, summarize schemas, sample records, decode HDF5 molecule groups, decode single-file ASE-LMDB zlib JSON records, inspect OpenPoly Excel records, rebuild the H2 display database, or write code that reads these files in the vasp-show project.
---

# VASP Datasets

## Quick Start

Use this skill for the five local datasets under `documents/data/`:

- `ani_gdb_s03.h5`: HDF5 ANI-style conformer data.
- `data0000.aselmdb`: single-file LMDB containing ASE-style records encoded as zlib-compressed JSON.
- `calculated_polymer_data.xlsx`: OpenPoly calculated polymer records without 3D atom coordinates.
- `ani1x-release.h5`: large ANI-1x HDF5. Build display rows by chemical-formula group, using one representative conformer per group.
- `Transition1x.h5`: large Transition1x HDF5. Build display rows by reaction group, using one representative image per reaction.

The current frontend/backend display path uses the generated H2 database:

- `frontend_template_data.mv.db`: unified display database with 167,911 records across `ani_gdb_s03`, `data0000_aselmdb`, `openpoly_calculated`, `ani1x_less_is_more`, and `transition1x`.

When Python is needed in this repository, prefer the project convention `small_tools` conda environment:

```bash
conda run -n small_tools python .agents/skills/vasp-datasets/scripts/inspect_datasets.py --root documents/data
```

In the current local Windows setup, `small_tools` may be unavailable; `D:\anaconda3\python.exe` has been used successfully with `pandas`, `openpyxl`, `lmdb`, and `h5py`.

Read `references/dataset-schemas.md` before changing parsing logic, conversion code, or UI assumptions based on these files.

## Workflow

1. Confirm the target files exist under `documents/data/`.
2. Use `scripts/inspect_datasets.py` for a fresh schema summary when the user asks what fields exist, asks for samples, or when the files may have changed.
3. For `ani_gdb_s03.h5`, use `h5py`. The root group is `gdb11_s03`; each molecule group contains `coordinates`, `coordinatesHE`, `energies`, `energiesHE`, `smiles`, and `species`.
4. For `data0000.aselmdb`, use `lmdb.open(path, subdir=False, readonly=True, lock=False, readahead=False)`. Skip the metadata key `nextid`. Decode each numeric key's value with `zlib.decompress`, then `json.loads`.
5. Decode LMDB arrays from `{"__ndarray__": [shape, dtype, flat_values]}` with `numpy.asarray(flat_values, dtype=dtype).reshape(shape)`.
6. For `calculated_polymer_data.xlsx`, use `pandas.read_excel`; do not expect atom coordinates. Preserve supplemental calculation columns in `properties_json` when building display records.
7. For `ani1x-release.h5`, avoid full conformer expansion. The current display builder indexes each root group and reads only `atomic_numbers`, `coordinates[0]`, `wb97x_dz.energy[0]`, optional `wb97x_dz.forces[0]`, and supplemental fields.
8. For `Transition1x.h5`, avoid full image expansion. The current display builder indexes `data/{formula}/{reaction}` and reads only `atomic_numbers`, `positions[0]`, `wB97x_6-31G(d).energy[0]`, and optional `wB97x_6-31G(d).forces[0]`.
9. Rebuild the frontend display database with `python/build_frontend_template_h2.py` after parser or source-data changes.

## Cautions

- Do not infer units unless the user provides source documentation. The local files expose energies, forces, coordinates, and molecular metadata, but units are not encoded in attributes.
- HDF5 `smiles` and `species` are fixed-width byte-character arrays; join and decode them before displaying.
- LMDB key order is lexicographic from the cursor. Sort numeric keys as integers for record-order iteration.
- OpenPoly Excel records do not include 3D coordinates; frontend details should show a no-coordinates state instead of rendering a 3D viewer.
- The ANI-1x and Transition1x files are 5G/6G scale. Do not read whole datasets into memory or expand all conformers/images into H2 unless explicitly requested.
- The skill name follows the repository name, but these files are molecular datasets rather than raw VASP output files.
