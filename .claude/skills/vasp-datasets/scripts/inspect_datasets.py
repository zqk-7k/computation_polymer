#!/usr/bin/env python3
"""Inspect the local HDF5 and ASE-LMDB molecular datasets."""

from __future__ import annotations

import argparse
import collections
import json
import zlib
from pathlib import Path
from typing import Any

import numpy as np


def _decode_bytes_array(values: Any) -> str:
    return b"".join(bytes(x) for x in values).decode("utf-8", errors="replace")


def _ndarray_meta(value: Any) -> tuple[tuple[int, ...], str, int] | None:
    if not isinstance(value, dict):
        return None
    payload = value.get("__ndarray__")
    if not (isinstance(payload, list) and len(payload) == 3):
        return None
    shape, dtype, flat = payload
    return tuple(shape), str(dtype), len(flat)


def decode_ndarray(value: dict[str, Any]) -> np.ndarray:
    """Decode an ASE JSON ndarray object."""
    shape, dtype, flat = value["__ndarray__"]
    return np.asarray(flat, dtype=np.dtype(dtype)).reshape(shape)


def inspect_h5(path: Path) -> None:
    import h5py

    print(f"# HDF5: `{path}`")
    if not path.exists():
        print("missing\n")
        return

    with h5py.File(path, "r") as handle:
        print(f"root keys: {list(handle.keys())}")
        root = handle["gdb11_s03"]
        rows: list[tuple[Any, ...]] = []
        total_conformers = 0
        total_he = 0
        atom_counts: list[int] = []
        elements: set[str] = set()

        for name in sorted(root.keys(), key=lambda item: int(item.rsplit("-", 1)[1])):
            group = root[name]
            coords = group["coordinates"]
            coords_he = group["coordinatesHE"]
            energies = group["energies"]
            energies_he = group["energiesHE"]
            species = _decode_bytes_array(group["species"][()])
            smiles = _decode_bytes_array(group["smiles"][()])
            n_conformers, n_atoms, _ = coords.shape
            total_conformers += n_conformers
            total_he += coords_he.shape[0]
            atom_counts.append(n_atoms)
            elements.update(species)
            rows.append(
                (
                    name,
                    n_conformers,
                    n_atoms,
                    coords.dtype.name,
                    energies.dtype.name,
                    coords_he.shape[0],
                    energies_he.shape[0],
                    species,
                    smiles,
                    float(np.min(energies)),
                    float(np.max(energies)),
                )
            )

        print(f"molecule groups: {len(rows)}")
        print(f"total conformers: {total_conformers}")
        print(f"total HE conformers: {total_he}")
        print(f"atom count range: {min(atom_counts)}..{max(atom_counts)}")
        print(f"elements: {', '.join(sorted(elements))}")
        print(f"fields per group: {list(root[rows[0][0]].keys())}\n")
        print("| group | conformers | atoms | coord dtype | energy dtype | HE conformers | species | smiles |")
        print("| --- | ---: | ---: | --- | --- | ---: | --- | --- |")
        for row in rows:
            print(
                f"| `{row[0]}` | {row[1]} | {row[2]} | `{row[3]}` | `{row[4]}` | "
                f"{row[5]} | `{row[7]}` | `{row[8]}` |"
            )
    print()


def _load_lmdb_json(value: bytes) -> Any:
    return json.loads(zlib.decompress(value).decode("utf-8"))


def _add_range(ranges: dict[str, list[float]], key: str, value: float) -> None:
    if key not in ranges:
        ranges[key] = [value, value]
        return
    ranges[key][0] = min(ranges[key][0], value)
    ranges[key][1] = max(ranges[key][1], value)


def inspect_lmdb(path: Path, sample_limit: int) -> None:
    import lmdb

    print(f"# LMDB: `{path}`")
    if not path.exists():
        print("missing\n")
        return

    env = lmdb.open(
        str(path),
        subdir=False,
        readonly=True,
        lock=False,
        readahead=False,
        max_readers=1,
    )
    with env.begin() as txn:
        stat = txn.stat()
        keys = [key for key, _ in txn.cursor()]
        numeric_keys = sorted(int(key.decode()) for key in keys if key.decode(errors="ignore").isdigit())
        non_numeric = [key.decode(errors="replace") for key in keys if not key.decode(errors="ignore").isdigit()]
        nextid = txn.get(b"nextid")
        nextid_value = zlib.decompress(nextid).decode("utf-8") if nextid else None

        print(f"entries: {stat['entries']}")
        print(f"numeric keys: {numeric_keys[0]}..{numeric_keys[-1]}")
        print(f"numeric keys contiguous: {numeric_keys == list(range(numeric_keys[0], numeric_keys[-1] + 1))}")
        print(f"non-numeric keys: {non_numeric}")
        print(f"nextid: {nextid_value}\n")

        top_key_sets: collections.Counter[tuple[str, ...]] = collections.Counter()
        data_key_sets: collections.Counter[tuple[str, ...]] = collections.Counter()
        array_shapes: dict[str, collections.Counter[tuple[int, ...]]] = collections.defaultdict(collections.Counter)
        array_dtypes: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
        scalar_types: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
        data_scalar_types: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
        ranges: dict[str, list[float]] = {}
        unique_atomic_numbers: set[int] = set()
        sample_rows: list[str] = []

        for index, key in enumerate(numeric_keys):
            record = _load_lmdb_json(txn.get(str(key).encode()))
            data = record.get("data", {})
            top_key_sets.update([tuple(record.keys())])
            data_key_sets.update([tuple(data.keys())])

            for field, value in record.items():
                if field == "data":
                    continue
                meta = _ndarray_meta(value)
                if meta:
                    shape, dtype, _ = meta
                    array_shapes[field].update([shape])
                    array_dtypes[field].update([dtype])
                    if shape:
                        _add_range(ranges, f"{field}.shape0", float(shape[0]))
                else:
                    scalar_types[field].update([type(value).__name__])
                    if isinstance(value, (int, float)) and not isinstance(value, bool):
                        _add_range(ranges, field, float(value))

            numbers = _ndarray_meta(record.get("numbers"))
            if numbers:
                unique_atomic_numbers.update(record["numbers"]["__ndarray__"][2])

            for field, value in data.items():
                meta = _ndarray_meta(value)
                if meta:
                    shape, dtype, _ = meta
                    array_shapes[f"data.{field}"].update([shape])
                    array_dtypes[f"data.{field}"].update([dtype])
                    if shape:
                        _add_range(ranges, f"data.{field}.shape0", float(shape[0]))
                else:
                    data_scalar_types[field].update([type(value).__name__])
                    if isinstance(value, (int, float)) and not isinstance(value, bool):
                        _add_range(ranges, f"data.{field}", float(value))

            if index < sample_limit:
                sample_rows.append(
                    "| {key} | {atoms} | `{numbers}` | `{positions}` | `{forces}` | {energy} | `{composition}` |".format(
                        key=key,
                        atoms=data.get("num_atoms"),
                        numbers=record["numbers"]["__ndarray__"][0],
                        positions=record["positions"]["__ndarray__"][0],
                        forces=record["forces"]["__ndarray__"][0],
                        energy=record.get("energy"),
                        composition=data.get("composition"),
                    )
                )

        print(f"top-level key sets: {top_key_sets}")
        print(f"nested data key sets: {data_key_sets}\n")
        print("## Array fields")
        for field in sorted(array_shapes):
            print(f"- `{field}` shapes {array_shapes[field].most_common(8)} dtypes {dict(array_dtypes[field])}")
        print("\n## Scalar field types")
        for field in sorted(scalar_types):
            print(f"- `{field}`: {dict(scalar_types[field])}")
        for field in sorted(data_scalar_types):
            print(f"- `data.{field}`: {dict(data_scalar_types[field])}")
        print("\n## Ranges")
        for field in sorted(ranges):
            low, high = ranges[field]
            print(f"- `{field}`: {low:g}..{high:g}")
        print(f"\nunique atomic numbers: {sorted(unique_atomic_numbers)}\n")
        if sample_rows:
            print("## Samples")
            print("| key | num_atoms | numbers shape | positions shape | forces shape | energy | composition |")
            print("| ---: | ---: | --- | --- | --- | ---: | --- |")
            print("\n".join(sample_rows))
    print()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("documents/data"), help="Directory containing local data files.")
    parser.add_argument("--h5", type=Path, default=None, help="Override HDF5 path.")
    parser.add_argument("--lmdb", type=Path, default=None, help="Override LMDB path.")
    parser.add_argument("--sample-limit", type=int, default=5, help="Number of LMDB records to show in the sample table.")
    args = parser.parse_args()

    h5_path = args.h5 or args.root / "ani_gdb_s03.h5"
    lmdb_path = args.lmdb or args.root / "data0000.aselmdb"

    inspect_h5(h5_path)
    inspect_lmdb(lmdb_path, args.sample_limit)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
