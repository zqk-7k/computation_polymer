---
name: vasp-datasets
description: Work with the local molecular datasets in this repository, especially documents/data/ani_gdb_s03.h5 and documents/data/data0000.aselmdb. Use when Codex needs to inspect dataset fields, summarize schemas, sample records, decode HDF5 molecule groups, decode single-file ASE-LMDB zlib JSON records, or write code that reads these files in the vasp-show project.
---

# VASP Datasets

## Quick Start

Use this skill for the two local datasets under `documents/data/`:

- `ani_gdb_s03.h5`: HDF5 ANI-style conformer data.
- `data0000.aselmdb`: single-file LMDB containing ASE-style records encoded as zlib-compressed JSON.

When Python is needed in this repository, run it through the `small_tools` conda environment:

```bash
conda run -n small_tools python .claude/skills/vasp-datasets/scripts/inspect_datasets.py --root documents/data
```

Read `references/dataset-schemas.md` before changing parsing logic, conversion code, or UI assumptions based on these files.

## Workflow

1. Confirm the target files exist under `documents/data/`.
2. Use `scripts/inspect_datasets.py` for a fresh schema summary when the user asks what fields exist, asks for samples, or when the files may have changed.
3. For `ani_gdb_s03.h5`, use `h5py`. The root group is `gdb11_s03`; each molecule group contains `coordinates`, `coordinatesHE`, `energies`, `energiesHE`, `smiles`, and `species`.
4. For `data0000.aselmdb`, use `lmdb.open(path, subdir=False, readonly=True, lock=False, readahead=False)`. Skip the metadata key `nextid`. Decode each numeric key's value with `zlib.decompress`, then `json.loads`.
5. Decode LMDB arrays from `{"__ndarray__": [shape, dtype, flat_values]}` with `numpy.asarray(flat_values, dtype=dtype).reshape(shape)`.

## Cautions

- Do not infer units unless the user provides source documentation. The local files expose energies, forces, coordinates, and molecular metadata, but units are not encoded in attributes.
- HDF5 `smiles` and `species` are fixed-width byte-character arrays; join and decode them before displaying.
- LMDB key order is lexicographic from the cursor. Sort numeric keys as integers for record-order iteration.
- The skill name follows the repository name, but these files are molecular datasets rather than raw VASP output files.
