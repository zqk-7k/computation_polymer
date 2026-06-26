#!/usr/bin/env python3
"""Export the first records from local HDF5 and ASE-LMDB datasets to CSV."""

from __future__ import annotations

import argparse
import csv
import json
import zlib
from pathlib import Path
from typing import Any

import h5py
import lmdb
import numpy as np


def decode_bytes_array(values: Any) -> str:
    return b"".join(bytes(x) for x in values).decode("utf-8", errors="replace")


def to_jsonable(value: Any) -> Any:
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, np.generic):
        return value.item()
    if isinstance(value, dict):
        return {str(key): to_jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [to_jsonable(item) for item in value]
    return value


def json_cell(value: Any) -> str:
    return json.dumps(to_jsonable(value), ensure_ascii=False, separators=(",", ":"))


def decode_lmdb_value(value: Any) -> Any:
    if isinstance(value, dict) and "__ndarray__" in value:
        shape, dtype, flat = value["__ndarray__"]
        return np.asarray(flat, dtype=np.dtype(dtype)).reshape(shape)
    if isinstance(value, dict):
        return {key: decode_lmdb_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [decode_lmdb_value(item) for item in value]
    return value


def flatten_for_csv(prefix: str, value: Any, row: dict[str, Any]) -> None:
    decoded = decode_lmdb_value(value)
    if isinstance(decoded, np.ndarray):
        row[f"{prefix}_shape"] = json_cell(list(decoded.shape))
        row[prefix] = json_cell(decoded)
    elif isinstance(decoded, dict):
        if not decoded:
            row[prefix] = json_cell(decoded)
        for key, item in decoded.items():
            flatten_for_csv(f"{prefix}.{key}", item, row)
    elif isinstance(decoded, list):
        row[prefix] = json_cell(decoded)
    elif decoded is None:
        row[prefix] = ""
    else:
        row[prefix] = decoded


def export_h5(path: Path, output: Path, limit: int) -> int:
    rows: list[dict[str, Any]] = []
    with h5py.File(path, "r") as handle:
        root = handle["gdb11_s03"]
        record_index = 0
        group_names = sorted(root.keys(), key=lambda item: int(item.rsplit("-", 1)[1]))
        for group_name in group_names:
            group = root[group_name]
            coordinates = group["coordinates"]
            energies = group["energies"]
            species = decode_bytes_array(group["species"][()])
            smiles = decode_bytes_array(group["smiles"][()])
            n_atoms = int(coordinates.shape[1])

            for conformer_index in range(coordinates.shape[0]):
                conformer_coordinates = coordinates[conformer_index]
                rows.append(
                    {
                        "record_index": record_index + 1,
                        "group_name": group_name,
                        "conformer_index": conformer_index,
                        "smiles": smiles,
                        "species": species,
                        "n_atoms": n_atoms,
                        "energy": float(energies[conformer_index]),
                        "coordinates_shape": json_cell(list(conformer_coordinates.shape)),
                        "coordinates": json_cell(conformer_coordinates),
                    }
                )
                record_index += 1
                if len(rows) >= limit:
                    break
            if len(rows) >= limit:
                break

    fieldnames = [
        "record_index",
        "group_name",
        "conformer_index",
        "smiles",
        "species",
        "n_atoms",
        "energy",
        "coordinates_shape",
        "coordinates",
    ]
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


def load_lmdb_json(raw: bytes) -> Any:
    return json.loads(zlib.decompress(raw).decode("utf-8"))


def export_lmdb(path: Path, output: Path, limit: int) -> int:
    rows: list[dict[str, Any]] = []
    env = lmdb.open(
        str(path),
        subdir=False,
        readonly=True,
        lock=False,
        readahead=False,
        max_readers=1,
    )
    try:
        with env.begin() as txn:
            numeric_keys = sorted(
                int(key.decode())
                for key, _ in txn.cursor()
                if key.decode(errors="ignore").isdigit()
            )
            for key in numeric_keys[:limit]:
                raw = txn.get(str(key).encode())
                if raw is None:
                    continue
                record = load_lmdb_json(raw)
                row: dict[str, Any] = {"key": key}
                for field, value in record.items():
                    flatten_for_csv(field, value, row)
                rows.append(row)
    finally:
        env.close()

    preferred = [
        "key",
        "unique_id",
        "data.sid",
        "data.data_id",
        "data.composition",
        "data.num_atoms",
        "data.charge",
        "data.spin",
        "energy",
        "data.homo_energy_shape",
        "data.homo_energy",
        "data.homo_lumo_gap_shape",
        "data.homo_lumo_gap",
        "data.n_scf_steps",
        "data.n_basis",
        "data.unrestricted",
        "data.nl_energy",
        "calculator",
        "user",
        "data.source",
    ]
    all_fields: list[str] = []
    for row in rows:
        for field in row:
            if field not in all_fields:
                all_fields.append(field)
    fieldnames = [field for field in preferred if field in all_fields]
    fieldnames.extend(field for field in all_fields if field not in preferred)

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("documents/data"))
    parser.add_argument("--limit", type=int, default=30)
    parser.add_argument("--h5-output", type=Path, default=None)
    parser.add_argument("--lmdb-output", type=Path, default=None)
    args = parser.parse_args()

    h5_path = args.root / "ani_gdb_s03.h5"
    lmdb_path = args.root / "data0000.aselmdb"
    h5_output = args.h5_output or args.root / "ani_gdb_s03_first30.csv"
    lmdb_output = args.lmdb_output or args.root / "data0000_first30.csv"

    h5_count = export_h5(h5_path, h5_output, args.limit)
    lmdb_count = export_lmdb(lmdb_path, lmdb_output, args.limit)

    print(f"wrote {h5_count} rows -> {h5_output}")
    print(f"wrote {lmdb_count} rows -> {lmdb_output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
