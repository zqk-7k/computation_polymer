# Local Dataset Schemas

This reference describes the two local files currently present in `documents/data/`.

## `ani_gdb_s03.h5`

- Format: HDF5.
- Root group: `gdb11_s03`.
- Molecule groups: 20 groups named `gdb11_s03-0` through `gdb11_s03-19`.
- Total conformers in `coordinates`: 151200.
- Elements present in `species`: `C`, `H`, `N`, `O`.
- Atom count range: 3 to 11 atoms per molecule group.
- HDF5 attributes: none found on root, groups, or datasets.
- Compression: all six datasets in each molecule group use gzip compression.

Every molecule group has the same fields:

| Field | Shape pattern | Dtype | Meaning |
| --- | --- | --- | --- |
| `coordinates` | `(n_conformers, n_atoms, 3)` | `float32` | Cartesian coordinates per conformer. |
| `coordinatesHE` | `(0, n_atoms, 3)` in the current file | `float32` | Higher-energy coordinate set placeholder; empty in this file. |
| `energies` | `(n_conformers,)` | `float64` | Energy per conformer. |
| `energiesHE` | `(0,)` in the current file | `float64` | Higher-energy energy placeholder; empty in this file. |
| `smiles` | `(n_chars,)` | `|S1` | One byte per SMILES character; join bytes before decoding. |
| `species` | `(n_atoms,)` | `|S1` | One byte per atomic symbol; join or decode each element. |

Molecule group summary:

| Group | Conformers | Atoms | Species | SMILES |
| --- | ---: | ---: | --- | --- |
| `gdb11_s03-0` | 12960 | 11 | `CCCHHHHHHHH` | `[H]C([H])([H])C([H])([H])C([H])([H])[H]` |
| `gdb11_s03-1` | 11520 | 10 | `CCNHHHHHHH` | `[H]N([H])C([H])([H])C([H])([H])[H]` |
| `gdb11_s03-2` | 10080 | 9 | `CCOHHHHHH` | `[H]OC([H])([H])C([H])([H])[H]` |
| `gdb11_s03-3` | 11520 | 10 | `CNCHHHHHHH` | `[H]N(C([H])([H])[H])C([H])([H])[H]` |
| `gdb11_s03-4` | 10080 | 9 | `COCHHHHHH` | `[H]C([H])([H])OC([H])([H])[H]` |
| `gdb11_s03-5` | 10080 | 9 | `CCCHHHHHH` | `[H]C([H])=C([H])C([H])([H])[H]` |
| `gdb11_s03-6` | 7200 | 7 | `CCOHHHH` | `[H]C(=O)C([H])([H])[H]` |
| `gdb11_s03-7` | 1440 | 3 | `OCO` | `[H]C([H])([H])C#N` |
| `gdb11_s03-8` | 5760 | 6 | `CCNHHH` | `[H]N=C([H])N([H])[H]` |
| `gdb11_s03-9` | 7200 | 7 | `NCNHHHH` | `[H]C(=O)N([H])[H]` |
| `gdb11_s03-10` | 5760 | 6 | `NCOHHH` | `[H]OC([H])=O` |
| `gdb11_s03-11` | 4320 | 5 | `OCOHH` | `[H]C([H])=NN([H])[H]` |
| `gdb11_s03-12` | 7200 | 7 | `NNCHHHH` | `[H]ON=C([H])[H]` |
| `gdb11_s03-13` | 5760 | 6 | `ONCHHH` | `[H]C#CC([H])([H])[H]` |
| `gdb11_s03-14` | 7200 | 7 | `CCCHHHH` | `[H]C1([H])C([H])([H])C1([H])[H]` |
| `gdb11_s03-15` | 10080 | 9 | `CCCHHHHHH` | `[H]N1C([H])([H])C1([H])[H]` |
| `gdb11_s03-16` | 8640 | 8 | `CCNHHHHH` | `[H]C1([H])OC1([H])[H]` |
| `gdb11_s03-17` | 7200 | 7 | `CCOHHHH` | `[H]OC([H])=C([H])[H]` |
| `gdb11_s03-18` | 2880 | 4 | `ONOH` | `[H]ON=O` |
| `gdb11_s03-19` | 4320 | 5 | `OOOHH` | `[H]OOO[H]` |

## `data0000.aselmdb`

- Format: single-file LMDB, not a directory-backed LMDB.
- Open with `subdir=False`.
- Main DB entries: 2525.
- Numeric data records: keys `1` through `2524`, contiguous.
- Metadata key: `nextid`, zlib-compressed value `2525`.
- Record value encoding: `zlib.decompress(value)` gives UTF-8 JSON.
- Array encoding: `{"__ndarray__": [shape, dtype, flat_values]}`.

Each numeric record has these top-level fields:

| Field | Type / shape | Notes |
| --- | --- | --- |
| `numbers` | ndarray `(num_atoms,)`, `int64` | Atomic numbers. Unique atomic numbers observed: 1, 3, 6, 7, 8, 9, 11, 12, 15, 16, 17, 20, 28, 35, 53. |
| `positions` | ndarray `(num_atoms, 3)`, `float64` | Atomic positions. |
| `unique_id` | `str` | Record UUID-like identifier. |
| `calculator` | `str` | Always `unknown` in current file. |
| `calculator_parameters` | `dict` | Empty in current file. |
| `energy` | `float` | Energy scalar; observed range `-1053302.0532304419` to `-36051.75295008887`. |
| `forces` | ndarray `(num_atoms, 3)`, `float64` | Atomic forces. |
| `cell` | ndarray `(3, 3)`, `float64` | All records have this shape. |
| `pbc` | ndarray `(3,)`, `bool` | Periodic boundary flags. |
| `ctime` | `float` | Creation timestamp-like scalar. |
| `user` | `str` | Always `mshuaibi` in current file. |
| `mtime` | `float` | Modification timestamp-like scalar. |
| `data` | `dict` | Nested metadata and quantum-chemistry values. |

Nested `data` fields normally present:

| Field | Type / shape | Observed notes |
| --- | --- | --- |
| `source` | `str` | Unique path-like source per record in current file. |
| `reference_source` | `None` | Always `None` in current file. |
| `data_id` | `str` | Always `polymers`. |
| `charge` | `int` | Observed range `-4` to `3`; most records are `0`. |
| `spin` | `int` | Always `1`. |
| `num_atoms` | `int` | Observed range `67` to `310`; should match `numbers.shape[0]`. |
| `num_electrons` | `int` | Observed range `236` to `2176`. |
| `num_ecp_electrons` | `int` | Observed range `0` to `420`. |
| `n_scf_steps` | `int` | Observed range `2` to `257`. |
| `n_basis` | `int` | Observed range `1401` to `10836`. |
| `unrestricted` | `bool` | `False` for 2460 records; `True` for 64 records. |
| `nl_energy` | `float` | Observed range `24.140567300623324` to `225.36739998409382`. |
| `integrated_densities` | ndarray `(3,)`, `float64` | Present in all numeric records. |
| `homo_energy` | ndarray `(1,)` or `(2,)`, `float64` | Shape `(2,)` appears on unrestricted records. |
| `homo_lumo_gap` | ndarray `(1,)` or `(2,)`, `float64` | Shape `(2,)` appears on unrestricted records. |
| `multipoles` | `list` | Length 3 in observed records. |
| `s_squared` | `float` | Observed range `0.0` to `1.054706`. |
| `s_squared_dev` | `float` | Observed range `0.0` to `1.054706`. |
| `warnings` | `list[str]` | Length 3 for 1520 records; length 4 for 1004 records. |
| `mulliken_charges` | ndarray `(num_atoms,)`, `float64` | Present in all numeric records. |
| `lowdin_charges` | ndarray `(num_atoms,)`, `float64` | Present in 2523 records. |
| `composition` | `str` | 2022 unique compositions observed. |
| `sid` | `int` | Observed range `9882` to `6699822`. |

Occasional nested `data` fields:

| Field | Type / shape | Count |
| --- | --- | ---: |
| `mulliken_spins` | ndarray `(num_atoms,)`, `float64` | 64 |
| `lowdin_spins` | ndarray `(num_atoms,)`, `float64` | 64 |
| `nbo_charges` | ndarray `(67,)`, `float64` | 1 |

Use this decoder for LMDB arrays:

```python
def decode_ndarray(value):
    shape, dtype, flat = value["__ndarray__"]
    return np.asarray(flat, dtype=np.dtype(dtype)).reshape(shape)
```
