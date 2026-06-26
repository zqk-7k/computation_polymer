#!/usr/bin/env python3
"""Build the H2 display data source for the frontend template."""

from __future__ import annotations

import argparse
import base64
import csv
import gzip
from collections import Counter
import io
import json
import math
import re
import shutil
import subprocess
import sys
import tarfile
import textwrap
import urllib.request
import zlib
from pathlib import Path
from typing import Any, Iterable
from zipfile import ZipFile
from xml.etree import ElementTree as ET

import h5py
import lmdb
import numpy as np


H2_VERSION = "2.3.232"
H2_DOWNLOAD_URL = (
    "https://repo1.maven.org/maven2/com/h2database/h2/"
    f"{H2_VERSION}/h2-{H2_VERSION}.jar"
)

ROOT_GROUP = "gdb11_s03"
ANI_DATASET_KEY = "ani_gdb_s03"
LMDB_DATASET_KEY = "data0000_aselmdb"
OPENPOLY_DATASET_KEY = "openpoly_calculated"
ANI1X_DATASET_KEY = "ani1x_less_is_more"
TRANSITION1X_DATASET_KEY = "transition1x"
TWOD_MATPEDIA_DATASET_KEY = "twod_matpedia"
JARVIS_3D_DATASET_KEY = "jarvis_dft_3d"
JARVIS_2D_DATASET_KEY = "jarvis_dft_2d"
POLYMER_GENOME_DATASET_KEY = "polymer_genome_1073"
QMOF_DATASET_KEY = "qmof_database"
MATBENCH_WBM_DATASET_KEY = "matbench_wbm_summary"
MATBENCH_MP_ENERGIES_DATASET_KEY = "matbench_mp_energies"
MATBENCH_PHONONDB_DATASET_KEY = "matbench_phonondb_pbe_103"
HYDROCARBONS_GAP_DATASET_KEY = "hydrocarbons_gap_ch"
MATBENCH_V01_DIELECTRIC_DATASET_KEY = "matbench_v01_dielectric"
MATBENCH_V01_JDFT2D_DATASET_KEY = "matbench_v01_jdft2d"
MATBENCH_V01_PHONONS_DATASET_KEY = "matbench_v01_phonons"
MATBENCH_V01_PEROVSKITES_DATASET_KEY = "matbench_v01_perovskites"
MATBENCH_V01_LOG_GVRH_DATASET_KEY = "matbench_v01_log_gvrh"
MATBENCH_V01_LOG_KVRH_DATASET_KEY = "matbench_v01_log_kvrh"
QM9_DATASET_KEY = "qm9_molecular_dft"

DISPLAY_COLUMNS = [
    "dataset_key",
    "source_record_id",
    "dataset_name",
    "dataset_size",
    "dataset_description",
    "material_name",
    "material_id",
    "force_field",
    "simulation_type",
    "validated_status",
    "smiles",
    "polymerization_degree",
    "radius_gyration_rg",
    "chain_conformation",
    "structure_json",
    "calculation_software",
    "ensemble",
    "temperature",
    "density",
    "glass_transition_temperature_tg",
    "youngs_modulus",
    "tensile_strength",
    "homo",
    "lumo",
    "homo_lumo_gap",
    "doi",
    "category",
    "calculation_platform",
    "calculation_time",
    "energy",
    "forces_json",
    "composition",
    "atom_count",
    "charge",
    "spin",
    "warnings_json",
    "properties_json",
]

CLOB_COLUMNS = {
    "dataset_description",
    "smiles",
    "structure_json",
    "forces_json",
    "warnings_json",
    "properties_json",
}

ATOMIC_MASSES_BY_SYMBOL = {
    "H": 1.00784,
    "He": 4.002602,
    "Li": 6.94,
    "Be": 9.0121831,
    "B": 10.81,
    "C": 12.011,
    "N": 14.0067,
    "O": 15.999,
    "F": 18.998403163,
    "Ne": 20.1797,
    "Na": 22.98976928,
    "Mg": 24.305,
    "Al": 26.9815385,
    "Si": 28.085,
    "P": 30.973761998,
    "S": 32.06,
    "Cl": 35.45,
    "Ar": 39.948,
    "K": 39.0983,
    "Ca": 40.078,
    "Sc": 44.955908,
    "Ti": 47.867,
    "V": 50.9415,
    "Cr": 51.9961,
    "Mn": 54.938044,
    "Fe": 55.845,
    "Co": 58.933194,
    "Ni": 58.6934,
    "Cu": 63.546,
    "Zn": 65.38,
    "Ga": 69.723,
    "Ge": 72.630,
    "As": 74.921595,
    "Se": 78.971,
    "Br": 79.904,
    "Rb": 85.4678,
    "Sr": 87.62,
    "Y": 88.90584,
    "Zr": 91.224,
    "Nb": 92.90637,
    "Mo": 95.95,
    "Tc": 98.0,
    "Ru": 101.07,
    "Rh": 102.90550,
    "Pd": 106.42,
    "Ag": 107.8682,
    "Cd": 112.414,
    "In": 114.818,
    "Sn": 118.710,
    "Sb": 121.760,
    "Te": 127.60,
    "I": 126.90447,
    "Cs": 132.90545196,
    "Ba": 137.327,
    "La": 138.90547,
    "Hf": 178.49,
    "Ta": 180.94788,
    "W": 183.84,
    "Re": 186.207,
    "Os": 190.23,
    "Ir": 192.217,
    "Pt": 195.084,
    "Au": 196.966569,
    "Hg": 200.592,
    "Tl": 204.38,
    "Pb": 207.2,
    "Bi": 208.98040,
}

SYMBOL_BY_ATOMIC_NUMBER = {
    1: "H",
    2: "He",
    3: "Li",
    4: "Be",
    5: "B",
    6: "C",
    7: "N",
    8: "O",
    9: "F",
    10: "Ne",
    11: "Na",
    12: "Mg",
    13: "Al",
    14: "Si",
    15: "P",
    16: "S",
    17: "Cl",
    18: "Ar",
    19: "K",
    20: "Ca",
    28: "Ni",
    35: "Br",
    53: "I",
}

SMILES_ELEMENT_PATTERN = re.compile(r"Cl|Br|Si|Na|Li|Mg|Ca|Ni|[BCNOFPSI]")


def json_cell(value: Any) -> str:
    return json.dumps(to_jsonable(value), ensure_ascii=False, separators=(",", ":"))


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


def decode_bytes_array(values: Any) -> str:
    chunks: list[bytes] = []
    for value in values:
        if isinstance(value, (bytes, np.bytes_)):
            chunks.append(bytes(value))
        else:
            chunks.append(str(value).encode("utf-8"))
    return b"".join(chunks).decode("utf-8", errors="replace").strip()


def decode_lmdb_value(value: Any) -> Any:
    if isinstance(value, dict) and "__ndarray__" in value:
        shape, dtype, flat = value["__ndarray__"]
        return np.asarray(flat, dtype=np.dtype(dtype)).reshape(shape)
    if isinstance(value, dict):
        return {key: decode_lmdb_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [decode_lmdb_value(item) for item in value]
    return value


def load_lmdb_json(raw: bytes) -> Any:
    return json.loads(zlib.decompress(raw).decode("utf-8"))


def load_xlsx_rows(path: Path) -> list[dict[str, str]]:
    ns = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}

    def col_index(cell_ref: str) -> int:
        letters = "".join(ch for ch in cell_ref if ch.isalpha())
        index = 0
        for ch in letters:
            index = index * 26 + ord(ch.upper()) - ord("A") + 1
        return max(0, index - 1)

    def shared_strings(zip_file: ZipFile) -> list[str]:
        if "xl/sharedStrings.xml" not in zip_file.namelist():
            return []
        root = ET.fromstring(zip_file.read("xl/sharedStrings.xml"))
        values = []
        for item in root.findall("x:si", ns):
            values.append("".join(node.text or "" for node in item.findall(".//x:t", ns)))
        return values

    def cell_value(cell: ET.Element, shared: list[str]) -> str:
        cell_type = cell.get("t")
        if cell_type == "inlineStr":
            return "".join(node.text or "" for node in cell.findall(".//x:t", ns))
        value = cell.find("x:v", ns)
        if value is None or value.text is None:
            return ""
        if cell_type == "s":
            try:
                return shared[int(value.text)]
            except (ValueError, IndexError):
                return value.text
        return value.text

    with ZipFile(path) as zip_file:
        shared = shared_strings(zip_file)
        sheet = ET.fromstring(zip_file.read("xl/worksheets/sheet1.xml"))
        rows: list[list[str]] = []
        for row in sheet.findall(".//x:sheetData/x:row", ns):
            values: list[str] = []
            for cell in row.findall("x:c", ns):
                index = col_index(cell.get("r", "A1"))
                while len(values) <= index:
                    values.append("")
                values[index] = cell_value(cell, shared)
            rows.append(values)

    if not rows:
        return []
    headers = [header.strip() for header in rows[0]]
    records: list[dict[str, str]] = []
    for row in rows[1:]:
        record = {header: row[index].strip() if index < len(row) else "" for index, header in enumerate(headers)}
        if any(record.values()):
            records.append(record)
    return records


def format_number(value: Any, precision: int = 12) -> str:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return ""
    if not math.isfinite(number):
        return ""
    return format(number, f".{precision}g")


def format_rg(value: float) -> str:
    if not math.isfinite(value):
        return ""
    return format(value, ".6f")


def format_scalar_or_json(value: Any) -> str:
    if value is None:
        return ""
    array = np.asarray(value)
    if array.size == 0:
        return ""
    if array.size == 1:
        return format_number(array.reshape(-1)[0])
    return json_cell(array)


def radius_of_gyration(coordinates: np.ndarray, masses: np.ndarray) -> str:
    coords = np.asarray(coordinates, dtype=float)
    weights = np.asarray(masses, dtype=float)
    if coords.ndim != 2 or coords.shape[1] != 3 or coords.shape[0] == 0:
        return ""
    if weights.shape[0] != coords.shape[0]:
        return ""
    total_mass = float(weights.sum())
    if total_mass <= 0:
        return ""
    center = (coords * weights[:, None]).sum(axis=0) / total_mass
    squared_distances = ((coords - center) ** 2).sum(axis=1)
    rg = math.sqrt(float((weights * squared_distances).sum() / total_mass))
    return format_rg(rg)


def species_to_masses(species: str) -> np.ndarray:
    return np.asarray([ATOMIC_MASSES_BY_SYMBOL.get(symbol, 1.0) for symbol in species], dtype=float)


def numbers_to_symbols(numbers: Iterable[Any]) -> list[str]:
    symbols: list[str] = []
    for number in numbers:
        atomic_number = int(number)
        symbols.append(SYMBOL_BY_ATOMIC_NUMBER.get(atomic_number, f"Z{atomic_number}"))
    return symbols


def numbers_to_masses(numbers: Iterable[Any]) -> np.ndarray:
    masses: list[float] = []
    for number in numbers:
        atomic_number = int(number)
        symbol = SYMBOL_BY_ATOMIC_NUMBER.get(atomic_number)
        masses.append(ATOMIC_MASSES_BY_SYMBOL.get(symbol or "", float(atomic_number)))
    return np.asarray(masses, dtype=float)


def structure_json_from_species(species: str, coordinates: np.ndarray) -> str:
    atoms = []
    for symbol, (x, y, z) in zip(species, np.asarray(coordinates, dtype=float)):
        atoms.append({"element": symbol, "x": float(x), "y": float(y), "z": float(z)})
    return json_cell({"atoms": atoms})


def structure_json_from_numbers(numbers: np.ndarray, positions: np.ndarray) -> str:
    atoms = []
    symbols = numbers_to_symbols(numbers)
    for atomic_number, symbol, (x, y, z) in zip(numbers, symbols, np.asarray(positions, dtype=float)):
        atoms.append(
            {
                "element": symbol,
                "atomic_number": int(atomic_number),
                "x": float(x),
                "y": float(y),
                "z": float(z),
            }
        )
    return json_cell({"atoms": atoms})


def normalize_scalar(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    text = str(value).strip()
    if text.lower() in {"", "na", "nan", "none", "null"}:
        return ""
    return text


def normalize_symbol(value: Any) -> str:
    text = str(value or "").strip()
    match = re.match(r"([A-Z][a-z]?)", text)
    return match.group(1) if match else text


def composition_from_symbols(symbols: Iterable[str]) -> str:
    counts = Counter(normalize_symbol(symbol) for symbol in symbols if normalize_symbol(symbol))
    return "".join(f"{symbol}{counts[symbol] if counts[symbol] > 1 else ''}" for symbol in sorted(counts))


def masses_from_symbols(symbols: Iterable[str]) -> np.ndarray:
    return np.asarray([ATOMIC_MASSES_BY_SYMBOL.get(normalize_symbol(symbol), 1.0) for symbol in symbols], dtype=float)


def structure_json_from_symbol_positions(
    symbols: Iterable[str],
    positions: Iterable[Iterable[Any]],
    lattice: Any = None,
) -> str:
    atoms = []
    for symbol, position in zip(symbols, positions):
        xyz = list(position)
        if len(xyz) < 3:
            continue
        atoms.append(
            {
                "element": normalize_symbol(symbol),
                "x": float(xyz[0]),
                "y": float(xyz[1]),
                "z": float(xyz[2]),
            }
        )
    payload: dict[str, Any] = {"atoms": atoms}
    if lattice:
        payload["lattice"] = lattice
    return json_cell(payload)


def compact_formula(value: Any) -> str:
    return re.sub(r"\s+", "", normalize_scalar(value))


def csv_gz_row_count(path: Path) -> int:
    with gzip.open(path, "rt", encoding="utf-8", errors="replace", newline="") as file:
        return max(0, sum(1 for _line in file) - 1)


def iter_csv_gz_rows(path: Path) -> Iterable[dict[str, str]]:
    with gzip.open(path, "rt", encoding="utf-8", errors="replace", newline="") as file:
        reader = csv.DictReader(file)
        for row in reader:
            yield {key: normalize_scalar(value) for key, value in row.items()}


def parse_extxyz_metadata(comment: str) -> dict[str, str]:
    import shlex

    metadata: dict[str, str] = {}
    try:
        tokens = shlex.split(comment)
    except ValueError:
        tokens = comment.split()
    for token in tokens:
        if "=" not in token:
            continue
        key, value = token.split("=", 1)
        metadata[key] = value
    return metadata


def parse_lattice(value: Any) -> list[list[float]]:
    try:
        numbers = [float(item) for item in str(value).split()]
    except ValueError:
        return []
    if len(numbers) != 9:
        return []
    return [numbers[0:3], numbers[3:6], numbers[6:9]]


def iter_extxyz_frames(stream: Any) -> Iterable[tuple[int, list[str], np.ndarray, np.ndarray, dict[str, str]]]:
    frame_index = 0
    while True:
        atom_count_line = stream.readline()
        if not atom_count_line:
            break
        text = atom_count_line.decode("utf-8", errors="replace").strip()
        if not text:
            continue
        try:
            atom_count = int(text)
        except ValueError:
            break
        comment = stream.readline().decode("utf-8", errors="replace").strip()
        metadata = parse_extxyz_metadata(comment)
        symbols: list[str] = []
        positions: list[list[float]] = []
        forces: list[list[float]] = []
        has_forces = False
        for _ in range(atom_count):
            line = stream.readline().decode("utf-8", errors="replace").strip()
            parts = line.split()
            if len(parts) < 4:
                continue
            symbols.append(normalize_symbol(parts[0]))
            positions.append([float(parts[1]), float(parts[2]), float(parts[3])])
            if len(parts) >= 7:
                try:
                    forces.append([float(parts[4]), float(parts[5]), float(parts[6])])
                    has_forces = True
                except ValueError:
                    forces.append([0.0, 0.0, 0.0])
        yield frame_index, symbols, np.asarray(positions, dtype=float), np.asarray(forces if has_forces else [], dtype=float), metadata
        frame_index += 1


def iter_nested_hydrocarbon_frames(zip_path: Path) -> Iterable[tuple[int, list[str], np.ndarray, np.ndarray, dict[str, str]]]:
    with ZipFile(zip_path) as outer:
        nested_name = outer.namelist()[0]
        nested_bytes = outer.read(nested_name)
    with ZipFile(io.BytesIO(nested_bytes)) as inner:
        with inner.open("train.xyz") as stream:
            yield from iter_extxyz_frames(stream)


def summarize_hydrocarbon_zip(zip_path: Path) -> tuple[int, int, int]:
    frame_count = 0
    atom_counts: list[int] = []
    for _index, symbols, _positions, _forces, _metadata in iter_nested_hydrocarbon_frames(zip_path):
        frame_count += 1
        atom_counts.append(len(symbols))
    if not atom_counts:
        return 0, 0, 0
    return frame_count, min(atom_counts), max(atom_counts)


def pymatgen_structure_to_symbols_positions(structure: dict[str, Any]) -> tuple[list[str], np.ndarray, Any]:
    symbols: list[str] = []
    positions: list[list[float]] = []
    for site in structure.get("sites", []) or []:
        species = site.get("species") or []
        symbol = site.get("label")
        if species and isinstance(species, list):
            symbol = species[0].get("element") or symbol
        xyz = site.get("xyz") or []
        if symbol and len(xyz) >= 3:
            symbols.append(normalize_symbol(symbol))
            positions.append([float(xyz[0]), float(xyz[1]), float(xyz[2])])
    lattice = (structure.get("lattice") or {}).get("matrix")
    return symbols, np.asarray(positions, dtype=float), lattice


def jarvis_atoms_to_symbols_positions(atoms: dict[str, Any]) -> tuple[list[str], np.ndarray, Any]:
    symbols = [normalize_symbol(symbol) for symbol in atoms.get("elements", [])]
    coords = np.asarray(atoms.get("coords", []), dtype=float)
    lattice = atoms.get("lattice_mat")
    return symbols, coords, lattice


def lattice_from_cif_cell(cell: dict[str, float]) -> np.ndarray:
    a = cell.get("a", 1.0)
    b = cell.get("b", 1.0)
    c = cell.get("c", 1.0)
    alpha = math.radians(cell.get("alpha", 90.0))
    beta = math.radians(cell.get("beta", 90.0))
    gamma = math.radians(cell.get("gamma", 90.0))
    sin_gamma = math.sin(gamma) or 1.0
    ax, ay, az = a, 0.0, 0.0
    bx, by, bz = b * math.cos(gamma), b * sin_gamma, 0.0
    cx = c * math.cos(beta)
    cy = c * (math.cos(alpha) - math.cos(beta) * math.cos(gamma)) / sin_gamma
    cz_sq = max(c * c - cx * cx - cy * cy, 0.0)
    cz = math.sqrt(cz_sq)
    return np.asarray([[ax, ay, az], [bx, by, bz], [cx, cy, cz]], dtype=float)


def parse_cif_structure(cif_text: str) -> tuple[list[str], np.ndarray, Any, dict[str, str]]:
    import shlex

    lines = [line.strip() for line in cif_text.replace("\r", "\n").splitlines() if line.strip()]
    cell: dict[str, float] = {}
    metadata: dict[str, str] = {}
    for line in lines:
        if line.startswith("_cell_length_a"):
            cell["a"] = float(line.split()[-1])
        elif line.startswith("_cell_length_b"):
            cell["b"] = float(line.split()[-1])
        elif line.startswith("_cell_length_c"):
            cell["c"] = float(line.split()[-1])
        elif line.startswith("_cell_angle_alpha"):
            cell["alpha"] = float(line.split()[-1])
        elif line.startswith("_cell_angle_beta"):
            cell["beta"] = float(line.split()[-1])
        elif line.startswith("_cell_angle_gamma"):
            cell["gamma"] = float(line.split()[-1])
        elif line.startswith("_chemical_formula_sum"):
            metadata["formula_sum"] = line.partition(" ")[2].strip().strip("'\"")
        elif line.startswith("#") and ":" in line:
            key, _sep, value = line[1:].partition(":")
            metadata[key.strip()] = value.strip()

    symbols: list[str] = []
    fractional: list[list[float]] = []
    index = 0
    while index < len(lines):
        if lines[index] != "loop_":
            index += 1
            continue
        index += 1
        headers: list[str] = []
        while index < len(lines) and lines[index].startswith("_"):
            headers.append(lines[index])
            index += 1
        if "_atom_site_type_symbol" not in headers:
            continue
        try:
            symbol_idx = headers.index("_atom_site_type_symbol")
            fx_idx = headers.index("_atom_site_fract_x")
            fy_idx = headers.index("_atom_site_fract_y")
            fz_idx = headers.index("_atom_site_fract_z")
        except ValueError:
            continue
        while index < len(lines):
            line = lines[index]
            if not line or line.startswith("#") or line == "loop_" or line.startswith("_") or line.startswith("data_"):
                break
            try:
                parts = shlex.split(line)
            except ValueError:
                parts = line.split()
            if len(parts) > max(symbol_idx, fx_idx, fy_idx, fz_idx):
                symbols.append(normalize_symbol(parts[symbol_idx]))
                fractional.append([float(parts[fx_idx]), float(parts[fy_idx]), float(parts[fz_idx])])
            index += 1
        break

    lattice = lattice_from_cif_cell(cell)
    if fractional:
        positions = np.asarray(fractional, dtype=float) @ lattice
    else:
        positions = np.asarray([], dtype=float)
    return symbols, positions, lattice.tolist(), metadata


def heavy_atom_count_from_smiles(smiles: str) -> int:
    return len(SMILES_ELEMENT_PATTERN.findall(smiles or ""))


def composition_from_smiles(smiles: str) -> str:
    counts: Counter[str] = Counter()
    for match in SMILES_ELEMENT_PATTERN.findall(smiles or ""):
        symbol = normalize_symbol(match)
        if symbol:
            counts[symbol] += 1
    if not counts:
        return ""
    return "".join(f"{element}{'' if counts[element] == 1 else counts[element]}" for element in sorted(counts))


def derive_lumo(homo: Any, gap: Any) -> str:
    if homo is None or gap is None:
        return ""
    try:
        homo_array = np.asarray(homo, dtype=float)
        gap_array = np.asarray(gap, dtype=float)
    except (TypeError, ValueError):
        return ""
    if homo_array.shape != gap_array.shape:
        return ""
    return format_scalar_or_json(homo_array + gap_array)


def derive_validated_status(data: dict[str, Any]) -> str:
    warnings = data.get("warnings")
    if isinstance(warnings, list) and warnings:
        return "warning"
    gap = data.get("homo_lumo_gap")
    if gap is not None:
        try:
            gap_array = np.asarray(gap, dtype=float)
            if gap_array.size and np.nanmin(gap_array) < 0:
                return "warning"
        except (TypeError, ValueError):
            return "warning"
    return "validated"


def blank_record(dataset_key: str, source_record_id: str) -> dict[str, str]:
    record = {column: "" for column in DISPLAY_COLUMNS}
    record["dataset_key"] = dataset_key
    record["source_record_id"] = source_record_id
    return record


def encode_tsv_value(value: str) -> str:
    return base64.b64encode(value.encode("utf-8")).decode("ascii")


def write_tsv_row(file: Any, row: dict[str, str]) -> None:
    values = [encode_tsv_value(str(row.get(column, "") or "")) for column in DISPLAY_COLUMNS]
    file.write("\t".join(values))
    file.write("\n")


def sort_h5_group_name(name: str) -> tuple[int, str]:
    try:
        return (int(name.rsplit("-", 1)[1]), name)
    except (IndexError, ValueError):
        return (sys.maxsize, name)


def summarize_h5(path: Path) -> tuple[list[str], int, int, int]:
    with h5py.File(path, "r") as handle:
        root = handle[ROOT_GROUP]
        group_names = sorted(root.keys(), key=sort_h5_group_name)
        conformer_count = 0
        atom_counts: list[int] = []
        for group_name in group_names:
            coordinates = root[group_name]["coordinates"]
            conformer_count += int(coordinates.shape[0])
            atom_counts.append(int(coordinates.shape[1]))
    return group_names, conformer_count, min(atom_counts), max(atom_counts)


def summarize_ani1x(path: Path) -> tuple[list[str], int, int, int]:
    with h5py.File(path, "r") as handle:
        group_names = sorted(handle.keys())
        conformer_count = 0
        atom_counts: list[int] = []
        for group_name in group_names:
            group = handle[group_name]
            conformer_count += int(group["coordinates"].shape[0])
            atom_counts.append(int(group["atomic_numbers"].shape[0]))
    return group_names, conformer_count, min(atom_counts), max(atom_counts)


def summarize_transition1x(path: Path) -> tuple[list[tuple[str, str]], int, int, int]:
    reactions: list[tuple[str, str]] = []
    conformer_count = 0
    atom_counts: list[int] = []
    with h5py.File(path, "r") as handle:
        data = handle["data"]
        for formula in sorted(data.keys()):
            formula_group = data[formula]
            for reaction_id in sorted(formula_group.keys()):
                reaction = formula_group[reaction_id]
                reactions.append((formula, reaction_id))
                conformer_count += int(reaction["positions"].shape[0])
                atom_counts.append(int(reaction["atomic_numbers"].shape[0]))
    return reactions, conformer_count, min(atom_counts), max(atom_counts)


def iter_numeric_lmdb_keys(txn: lmdb.Transaction) -> list[int]:
    keys: list[int] = []
    for key, _ in txn.cursor():
        key_text = key.decode("utf-8", errors="ignore")
        if key_text.isdigit():
            keys.append(int(key_text))
    return sorted(keys)


def summarize_lmdb(path: Path) -> tuple[list[int], int, int, int, int]:
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
            numeric_keys = iter_numeric_lmdb_keys(txn)
            atom_counts: list[int] = []
            compositions: set[str] = set()
            for key in numeric_keys:
                raw = txn.get(str(key).encode("utf-8"))
                if raw is None:
                    continue
                record = decode_lmdb_value(load_lmdb_json(raw))
                data = record.get("data") or {}
                if data.get("num_atoms") is not None:
                    atom_counts.append(int(data["num_atoms"]))
                if data.get("composition"):
                    compositions.add(str(data["composition"]))
    finally:
        env.close()

    if not atom_counts:
        return numeric_keys, len(numeric_keys), 0, 0, len(compositions)
    return numeric_keys, len(numeric_keys), min(atom_counts), max(atom_counts), len(compositions)


def write_ani_rows(h5_path: Path, output_tsv: Path) -> int:
    group_names, conformer_count, _min_atoms, _max_atoms = summarize_h5(h5_path)
    dataset_size = str(conformer_count)
    row_count = 0
    with h5py.File(h5_path, "r") as handle, output_tsv.open("a", encoding="utf-8") as file:
        root = handle[ROOT_GROUP]
        for group_name in group_names:
            group = root[group_name]
            coordinates = group["coordinates"]
            energies = group["energies"]
            species = decode_bytes_array(group["species"][()])
            smiles = decode_bytes_array(group["smiles"][()])
            atom_count = str(int(coordinates.shape[1]))
            masses = species_to_masses(species)
            for conformer_index in range(int(coordinates.shape[0])):
                conformer_coordinates = coordinates[conformer_index]
                source_record_id = f"{group_name}#{conformer_index}"
                row = blank_record(ANI_DATASET_KEY, source_record_id)
                row["dataset_name"] = "ANI-GDB s03"
                row["dataset_size"] = dataset_size
                row["material_id"] = group_name
                row["smiles"] = smiles
                row["radius_gyration_rg"] = radius_of_gyration(conformer_coordinates, masses)
                row["structure_json"] = structure_json_from_species(species, conformer_coordinates)
                row["energy"] = format_number(energies[conformer_index])
                row["atom_count"] = atom_count
                write_tsv_row(file, row)
                row_count += 1
    return row_count


def write_lmdb_rows(lmdb_path: Path, output_tsv: Path) -> int:
    numeric_keys, record_count, _min_atoms, _max_atoms, _composition_count = summarize_lmdb(lmdb_path)
    dataset_size = str(record_count)
    row_count = 0
    env = lmdb.open(
        str(lmdb_path),
        subdir=False,
        readonly=True,
        lock=False,
        readahead=False,
        max_readers=1,
    )
    try:
        with env.begin() as txn, output_tsv.open("a", encoding="utf-8") as file:
            for key in numeric_keys:
                raw = txn.get(str(key).encode("utf-8"))
                if raw is None:
                    continue
                record = decode_lmdb_value(load_lmdb_json(raw))
                data = record.get("data") or {}
                unique_id = str(record.get("unique_id") or key)
                numbers = np.asarray(record.get("numbers", []), dtype=int)
                positions = np.asarray(record.get("positions", []), dtype=float)
                forces = record.get("forces")
                homo = data.get("homo_energy")
                gap = data.get("homo_lumo_gap")

                row = blank_record(LMDB_DATASET_KEY, unique_id)
                row["dataset_name"] = str(data.get("data_id") or "data0000.aselmdb")
                row["dataset_size"] = dataset_size
                row["material_id"] = unique_id
                row["validated_status"] = derive_validated_status(data)
                if numbers.size and positions.size:
                    row["radius_gyration_rg"] = radius_of_gyration(positions, numbers_to_masses(numbers))
                    row["structure_json"] = structure_json_from_numbers(numbers, positions)
                row["calculation_software"] = str(record.get("calculator") or "")
                row["homo"] = format_scalar_or_json(homo)
                row["lumo"] = derive_lumo(homo, gap)
                row["homo_lumo_gap"] = format_scalar_or_json(gap)
                row["energy"] = format_number(record.get("energy"))
                row["forces_json"] = json_cell({"forces": forces}) if forces is not None else ""
                row["composition"] = str(data.get("composition") or "")
                row["atom_count"] = str(data.get("num_atoms") or "")
                row["charge"] = str(data.get("charge") if data.get("charge") is not None else "")
                row["spin"] = str(data.get("spin") if data.get("spin") is not None else "")
                row["warnings_json"] = json_cell(data["warnings"]) if "warnings" in data else ""
                write_tsv_row(file, row)
                row_count += 1
    finally:
        env.close()
    return row_count


def write_openpoly_rows(xlsx_path: Path, output_tsv: Path) -> int:
    records = load_xlsx_rows(xlsx_path)
    dataset_size = str(len(records))
    row_count = 0
    with output_tsv.open("a", encoding="utf-8") as file:
      for record in records:
        source_record_id = str(record.get("id") or record.get("Name") or row_count + 1)
        name = str(record.get("Name") or f"OpenPoly record {source_record_id}")
        psmiles = str(record.get("psmiles") or "")
        smiles = str(record.get("SMILES") or psmiles)
        theory_level = str(record.get("Theory_Level") or "")
        reaction_type = str(record.get("reaction_type") or "")
        source = str(record.get("Source") or "QC")
        properties = {
            "P-SMILES": psmiles,
            "Molecular SMILES": smiles,
            "Reaction type": reaction_type,
            "Reactant 1": str(record.get("reactant_1") or ""),
            "Reactant 2": str(record.get("reactant_2") or ""),
            "Similarity score es": str(record.get("es") or ""),
            "Isotropic polarizability (au)": str(record.get("Isotropic_Polarizability_au") or ""),
            "Dipole moment (Debye)": str(record.get("Dipole_Debye") or ""),
            "Inner energy correction (Hartree)": str(record.get("Inner_energy_correction_Hatree") or ""),
            "Thermal correction to enthalpy (Hartree)": str(record.get("Thermal_correction_to_Enthalpy_Hatree") or ""),
            "Thermal correction to Gibbs free energy (Hartree)": str(record.get("Thermal_correction_to_Gibbs_Free_Energy_Hatree") or ""),
            "Entropy (Hartree)": str(record.get("Entropy_Hatree") or ""),
            "Gibbs free energy (Hartree)": str(record.get("Gibbs_Free_Energy_Hatree") or ""),
            "Enthalpy (Hartree)": str(record.get("Enthalpy_Hatree") or ""),
            "Theory level": theory_level,
        }
        row = blank_record(OPENPOLY_DATASET_KEY, source_record_id)
        row["dataset_name"] = "OpenPoly calculated polymers"
        row["dataset_size"] = dataset_size
        row["dataset_description"] = (
            "OpenPoly 论文配套的聚合物量化计算数据，包含聚合物 PSMILES/SMILES、反应类型、"
            "DFT 总能量、HOMO/LUMO、HOMO-LUMO gap、偶极矩、极化率以及热力学修正量。"
        )
        row["material_name"] = name
        row["material_id"] = name
        row["simulation_type"] = "opt+freq + single-point energy"
        row["validated_status"] = "warning" if str(record.get("Energy_Hatree") or "") in {"", "0", "0.0"} else "validated"
        row["smiles"] = psmiles or smiles
        row["chain_conformation"] = reaction_type
        row["calculation_software"] = str(record.get("Software") or "")
        row["homo"] = format_number(record.get("HOMO_eV"))
        row["lumo"] = format_number(record.get("LUMO_eV"))
        row["homo_lumo_gap"] = format_number(record.get("HOMO_LUMO_Gap_eV"))
        row["doi"] = "10.1007/s10118-025-3402-y"
        row["category"] = source
        row["energy"] = format_number(record.get("Energy_Hatree"))
        row["atom_count"] = str(heavy_atom_count_from_smiles(smiles or psmiles))
        row["warnings_json"] = json_cell(["Energy is zero or missing in source row"]) if row["validated_status"] == "warning" else ""
        row["properties_json"] = json_cell({key: value for key, value in properties.items() if value})
        write_tsv_row(file, row)
        row_count += 1
    return row_count


def write_ani1x_rows(h5_path: Path, output_tsv: Path) -> int:
    group_names, conformer_count, _min_atoms, _max_atoms = summarize_ani1x(h5_path)
    dataset_size = str(conformer_count)
    row_count = 0
    with h5py.File(h5_path, "r") as handle, output_tsv.open("a", encoding="utf-8") as file:
        for group_name in group_names:
            group = handle[group_name]
            numbers = np.asarray(group["atomic_numbers"][()], dtype=int)
            coordinates = np.asarray(group["coordinates"][0], dtype=float)
            forces = group["wb97x_dz.forces"][0] if "wb97x_dz.forces" in group else None
            energy = group["wb97x_dz.energy"][0] if "wb97x_dz.energy" in group else ""
            ccsd_energy = group["ccsd(t)_cbs.energy"][0] if "ccsd(t)_cbs.energy" in group else ""
            dipole = group["wb97x_dz.dipole"][0] if "wb97x_dz.dipole" in group else None
            conformers = int(group["coordinates"].shape[0])

            row = blank_record(ANI1X_DATASET_KEY, group_name)
            row["dataset_name"] = "ANI-1x active learning dataset"
            row["dataset_size"] = dataset_size
            row["dataset_description"] = (
                "Less is more 论文提出的主动学习采样数据集，用于训练通用 ANI 势函数。"
                "本项目按 HDF5 化学式 group 建立可浏览索引，每条记录展示该 group 的首个代表构象。"
            )
            row["material_name"] = f"ANI-1x {group_name} · {len(numbers)} 原子 · {conformers} 构象"
            row["material_id"] = group_name
            row["force_field"] = "ANI-1x"
            row["simulation_type"] = "active learning + DFT labeling"
            row["validated_status"] = "sampled"
            row["radius_gyration_rg"] = radius_of_gyration(coordinates, numbers_to_masses(numbers))
            row["structure_json"] = structure_json_from_numbers(numbers, coordinates)
            row["calculation_software"] = "Gaussian 09 / ASE ANI"
            row["doi"] = "10.1063/1.5023802"
            row["category"] = "molecular ML potential training dataset"
            row["calculation_platform"] = "Figshare / HDF5"
            row["energy"] = format_number(energy)
            row["forces_json"] = json_cell({"forces": forces}) if forces is not None else ""
            row["composition"] = group_name
            row["atom_count"] = str(len(numbers))
            row["properties_json"] = json_cell({
                "HDF5 group": group_name,
                "Conformers in group": conformers,
                "Representative conformer index": 0,
                "wb97x_dz.energy": format_number(energy),
                "ccsd(t)_cbs.energy": format_number(ccsd_energy),
                "wb97x_dz.dipole": format_scalar_or_json(dipole),
                "Available fields": ", ".join(group.keys()),
                "Dataset DOI": "10.6084/m9.figshare.10047041.v1",
            })
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def write_transition1x_rows(h5_path: Path, output_tsv: Path) -> int:
    reactions, conformer_count, _min_atoms, _max_atoms = summarize_transition1x(h5_path)
    dataset_size = str(conformer_count)
    energy_key = "wB97x_6-31G(d).energy"
    force_key = "wB97x_6-31G(d).forces"
    row_count = 0
    with h5py.File(h5_path, "r") as handle, output_tsv.open("a", encoding="utf-8") as file:
        data = handle["data"]
        for formula, reaction_id in reactions:
            reaction = data[formula][reaction_id]
            numbers = np.asarray(reaction["atomic_numbers"][()], dtype=int)
            positions = np.asarray(reaction["positions"][0], dtype=float)
            forces = reaction[force_key][0] if force_key in reaction else None
            energy = reaction[energy_key][0] if energy_key in reaction else ""
            conformers = int(reaction["positions"].shape[0])

            row = blank_record(TRANSITION1X_DATASET_KEY, f"{formula}/{reaction_id}")
            row["dataset_name"] = "Transition1x"
            row["dataset_size"] = dataset_size
            row["dataset_description"] = (
                "Transition1x 包含反应路径及其附近的 DFT 能量和力。"
                "本项目按 reaction group 建立可浏览索引，每条记录展示该反应路径的首个代表构象。"
            )
            row["material_name"] = f"Transition1x {reaction_id} · {formula} · {len(numbers)} 原子 · {conformers} 图像"
            row["material_id"] = reaction_id
            row["force_field"] = "PaiNN validation baseline"
            row["simulation_type"] = "NEB/CINEB reaction path DFT calculations"
            row["validated_status"] = "sampled"
            row["radius_gyration_rg"] = radius_of_gyration(positions, numbers_to_masses(numbers))
            row["structure_json"] = structure_json_from_numbers(numbers, positions)
            row["calculation_software"] = "ORCA 5.0.2; ASE 3.22.1"
            row["doi"] = "10.1038/s41597-022-01870-w"
            row["category"] = "reactive molecular ML potential training dataset"
            row["calculation_platform"] = "Figshare / HDF5"
            row["energy"] = format_number(energy)
            row["forces_json"] = json_cell({"forces": forces}) if forces is not None else ""
            row["composition"] = formula
            row["atom_count"] = str(len(numbers))
            row["properties_json"] = json_cell({
                "Formula": formula,
                "Reaction id": reaction_id,
                "Images/configurations in reaction": conformers,
                "Representative image index": 0,
                "Energy field": energy_key,
                "Forces field": force_key,
                "Reactant/Product/TS groups": ", ".join(key for key in ["reactant", "transition_state", "product"] if key in reaction),
                "Dataset DOI": "10.6084/m9.figshare.19614657.v4",
                "Code": "https://gitlab.com/matschreiner/Transition1x",
            })
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def write_twod_matpedia_rows(json_path: Path, output_tsv: Path) -> int:
    row_count = 0
    with output_tsv.open("a", encoding="utf-8") as file, json_path.open("r", encoding="utf-8") as source:
        for line in source:
            if not line.strip():
                continue
            item = json.loads(line)
            material_id = normalize_scalar(item.get("material_id")) or f"2dm-{row_count + 1}"
            structure = item.get("structure") or {}
            symbols, positions, lattice = pymatgen_structure_to_symbols_positions(structure)
            properties = {
                "Discovery process": item.get("discovery_process"),
                "Source id": item.get("source_id"),
                "Space group": item.get("sg_symbol"),
                "Space group number": item.get("sg_number"),
                "Crystal system": (item.get("spacegroup") or {}).get("crystal_system"),
                "Energy per atom (eV)": item.get("energy_per_atom"),
                "vdW energy per atom (eV)": item.get("energy_vdw_per_atom"),
                "Exfoliation energy per atom (eV)": item.get("exfoliation_energy_per_atom"),
                "Decomposition energy per atom (eV)": item.get("decomposition_energy_per_atom"),
                "Band gap (eV)": item.get("bandgap"),
                "Is metal": (item.get("bandstructure") or {}).get("is_metal"),
                "Total magnetization": item.get("total_magnetization"),
                "Formula anonymous": item.get("formula_anonymous"),
            }
            row = blank_record(TWOD_MATPEDIA_DATASET_KEY, material_id)
            row["dataset_name"] = "2DMatPedia"
            row["dataset_size"] = "6351"
            row["dataset_description"] = (
                "2DMatPedia 是面向二维材料发现与筛选的开放计算数据库，包含 top-down 剥离和 "
                "bottom-up 元素替换生成的二维材料结构、电子性质和稳定性相关能量。"
            )
            row["material_name"] = normalize_scalar(item.get("formula_pretty")) or material_id
            row["material_id"] = material_id
            row["simulation_type"] = normalize_scalar(item.get("discovery_process"))
            row["validated_status"] = "computed"
            if len(symbols) and positions.size:
                row["radius_gyration_rg"] = radius_of_gyration(positions, masses_from_symbols(symbols))
                row["structure_json"] = structure_json_from_symbol_positions(symbols, positions, lattice)
            row["calculation_software"] = "VASP"
            row["homo_lumo_gap"] = format_number(item.get("bandgap"))
            row["doi"] = "10.1038/s41597-019-0097-3"
            row["category"] = "2D materials DFT database"
            row["calculation_platform"] = "MPContribs / JSON"
            row["energy"] = format_number((item.get("thermo") or {}).get("energy"))
            row["composition"] = normalize_scalar(item.get("formula_pretty")) or composition_from_symbols(symbols)
            row["atom_count"] = str(len(symbols)) if symbols else ""
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def write_jarvis_rows(json_path: Path, output_tsv: Path, dataset_key: str, dataset_name: str, dataset_size: str) -> int:
    records = json.loads(json_path.read_text(encoding="utf-8"))
    row_count = 0
    with output_tsv.open("a", encoding="utf-8") as file:
        for item in records:
            jid = normalize_scalar(item.get("jid")) or f"{dataset_key}-{row_count + 1}"
            atoms = item.get("atoms") or {}
            symbols, positions, lattice = jarvis_atoms_to_symbols_positions(atoms)
            properties = {
                "Space group": item.get("spg_symbol"),
                "Space group number": item.get("spg_number"),
                "Formation energy per atom (eV)": item.get("formation_energy_peratom"),
                "OptB88vdW band gap (eV)": item.get("optb88vdw_bandgap"),
                "OptB88vdW total energy (eV)": item.get("optb88vdw_total_energy"),
                "Energy above hull (eV)": item.get("ehull"),
                "Magnetic moment OSZICAR": item.get("magmom_oszicar"),
                "Magnetic moment OUTCAR": item.get("magmom_outcar"),
                "Density": item.get("density"),
                "Dimensionality": item.get("dimensionality"),
                "Bulk modulus Kv": item.get("bulk_modulus_kv"),
                "Shear modulus Gv": item.get("shear_modulus_gv"),
                "MBJ band gap (eV)": item.get("mbj_bandgap"),
                "HSE gap (eV)": item.get("hse_gap"),
                "Exfoliation energy": item.get("exfoliation_energy"),
                "ENCUT": item.get("encut"),
                "Reference": item.get("reference"),
            }
            row = blank_record(dataset_key, jid)
            row["dataset_name"] = dataset_name
            row["dataset_size"] = dataset_size
            row["dataset_description"] = (
                "NIST JARVIS-DFT 的周期晶体材料 DFT 数据，包含结构、形成能、带隙、弹性、"
                "介电、磁性和电子结构相关性质。"
            )
            row["material_name"] = normalize_scalar(item.get("formula")) or jid
            row["material_id"] = jid
            row["simulation_type"] = "high-throughput periodic DFT"
            row["validated_status"] = "computed"
            if len(symbols) and positions.size:
                row["radius_gyration_rg"] = radius_of_gyration(positions, masses_from_symbols(symbols))
                row["structure_json"] = structure_json_from_symbol_positions(symbols, positions, lattice)
            row["calculation_software"] = "VASP"
            row["density"] = format_number(item.get("density"))
            row["homo_lumo_gap"] = format_number(item.get("optb88vdw_bandgap"))
            row["doi"] = "10.1038/s41524-020-00440-1"
            row["category"] = "JARVIS-DFT materials database"
            row["calculation_platform"] = "JARVIS-Tools / JSON"
            row["energy"] = format_number(item.get("formation_energy_peratom"))
            row["composition"] = normalize_scalar(item.get("formula")) or composition_from_symbols(symbols)
            row["atom_count"] = str(len(symbols)) if symbols else normalize_scalar(item.get("nat"))
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def extract_latest_jarvis_2d_json(source_zip: Path, work_dir: Path) -> Path:
    output_path = work_dir / "jarvis_dft_2d.json"
    if output_path.exists():
        return output_path
    preferred = "d2-12-12-2022.json.zip"
    with ZipFile(source_zip) as outer:
        nested = outer.read(preferred)
    with ZipFile(io.BytesIO(nested)) as inner:
        name = inner.namelist()[0]
        output_path.write_bytes(inner.read(name))
    return output_path


def write_polymer_genome_rows(tgz_path: Path, output_tsv: Path) -> int:
    row_count = 0
    with tarfile.open(tgz_path, "r:gz") as archive, output_tsv.open("a", encoding="utf-8") as file:
        members = sorted((member for member in archive.getmembers() if member.isfile() and member.name.endswith(".cif")),
                         key=lambda item: item.name)
        for member in members:
            extracted = archive.extractfile(member)
            if extracted is None:
                continue
            text = extracted.read().decode("utf-8", errors="replace")
            symbols, positions, lattice, metadata = parse_cif_structure(text)
            source_id = Path(member.name).stem
            properties = {
                "Class": metadata.get("Class"),
                "Label": metadata.get("Label"),
                "Structure prediction method": metadata.get("Structure prediction method used"),
                "Dielectric constant electronic": metadata.get("Dielectric constant, electronic"),
                "Dielectric constant ionic": metadata.get("Dielectric constant, ionic"),
                "Dielectric constant total": metadata.get("Dielectric constant, total"),
                "Band gap GGA (eV)": metadata.get("Band gap at the GGA level (eV)"),
                "Band gap HSE06 (eV)": metadata.get("Band gap at the HSE06 level (eV)"),
                "Atomization energy (eV/atom)": metadata.get("Atomization energy (eV/atom)"),
                "Unit cell volume (A^3)": metadata.get("Volume of the unit cell (A^3)"),
            }
            row = blank_record(POLYMER_GENOME_DATASET_KEY, source_id)
            row["dataset_name"] = "Polymer Genome 1073"
            row["dataset_size"] = "1073"
            row["dataset_description"] = (
                "Polymer Genome 1073 包含第一性原理优化聚合物晶体结构及介电、带隙、原子化能等性质。"
            )
            row["material_name"] = metadata.get("Label") or metadata.get("formula_sum") or source_id
            row["material_id"] = source_id
            row["simulation_type"] = "first-principles polymer crystal relaxation"
            row["validated_status"] = "computed"
            if len(symbols) and positions.size:
                row["radius_gyration_rg"] = radius_of_gyration(positions, masses_from_symbols(symbols))
                row["structure_json"] = structure_json_from_symbol_positions(symbols, positions, lattice)
            row["calculation_software"] = "VASP"
            row["homo_lumo_gap"] = format_number(metadata.get("Band gap at the GGA level (eV)"))
            row["doi"] = "10.1038/sdata.2016.12"
            row["category"] = metadata.get("Class") or "polymer crystal DFT database"
            row["calculation_platform"] = "Dryad / CIF"
            row["energy"] = format_number(metadata.get("Atomization energy (eV/atom)"))
            row["composition"] = metadata.get("formula_sum") or composition_from_symbols(symbols)
            row["atom_count"] = str(len(symbols)) if symbols else normalize_scalar(metadata.get("Number of atoms"))
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def write_qmof_rows(qmof_zip: Path, thermo_zip: Path, output_tsv: Path) -> int:
    with ZipFile(qmof_zip) as outer:
        qmof_records = json.loads(outer.read("qmof_database/qmof.json").decode("utf-8"))
        relaxed_zip_bytes = outer.read("qmof_database/relaxed_structures.zip")
    with ZipFile(thermo_zip) as thermo_outer:
        thermo_records = {
            item["qmof_id"]: item
            for item in json.loads(thermo_outer.read("qmof_thermo_database/qmof_thermo.json").decode("utf-8"))
        }

    row_count = 0
    with ZipFile(io.BytesIO(relaxed_zip_bytes)) as structures, output_tsv.open("a", encoding="utf-8") as file:
        available = set(structures.namelist())
        for item in qmof_records:
            qmof_id = normalize_scalar(item.get("qmof_id")) or f"qmof-{row_count + 1}"
            cif_name = f"relaxed_structures/{qmof_id}.cif"
            symbols: list[str] = []
            positions = np.asarray([], dtype=float)
            lattice: Any = None
            metadata: dict[str, str] = {}
            if cif_name in available:
                text = structures.read(cif_name).decode("utf-8", errors="replace")
                symbols, positions, lattice, metadata = parse_cif_structure(text)
            info = item.get("info") or {}
            inputs = (item.get("inputs") or {}).get("pbe") or {}
            outputs = (item.get("outputs") or {}).get("pbe") or {}
            thermo = thermo_records.get(qmof_id, {})
            mofid = info.get("mofid") or {}
            properties = {
                "Name": item.get("name"),
                "Formula reduced": info.get("formula_reduced"),
                "MOFid": mofid.get("mofid"),
                "MOFkey": mofid.get("mofkey"),
                "Topology": mofid.get("topology"),
                "PBE theory": inputs.get("theory"),
                "PBE encut": inputs.get("encut"),
                "PBE kpoints": inputs.get("kpoints"),
                "PBE spin": inputs.get("spin"),
                "PBE total energy (eV)": outputs.get("energy_total"),
                "PBE vdW energy (eV)": outputs.get("energy_vdw"),
                "PBE band gap (eV)": outputs.get("bandgap"),
                "PBE direct gap": outputs.get("directgap"),
                "PBE net magmom": outputs.get("net_magmom"),
                "Energy above hull": thermo.get("energy_above_hull"),
                "Formation energy": thermo.get("formation_energy"),
            }
            row = blank_record(QMOF_DATASET_KEY, qmof_id)
            row["dataset_name"] = "QMOF Database"
            row["dataset_size"] = "20372"
            row["dataset_description"] = (
                "QMOF Database 是面向 MOF 和配位聚合物的周期 DFT 数据库，提供优化结构、"
                "PBE-D3(BJ) 电子性质和扩展热力学/电子结构标签。"
            )
            row["material_name"] = normalize_scalar(item.get("name")) or normalize_scalar(info.get("formula")) or qmof_id
            row["material_id"] = qmof_id
            row["simulation_type"] = "periodic DFT MOF relaxation"
            row["validated_status"] = "computed"
            if len(symbols) and positions.size:
                row["radius_gyration_rg"] = radius_of_gyration(positions, masses_from_symbols(symbols))
                row["structure_json"] = structure_json_from_symbol_positions(symbols, positions, lattice)
            row["calculation_software"] = "VASP 5.4.4"
            row["homo_lumo_gap"] = format_number(outputs.get("bandgap"))
            row["doi"] = "10.1016/j.matt.2021.02.015; 10.1038/s41524-022-00796-6"
            row["category"] = "MOF periodic DFT database"
            row["calculation_platform"] = "QMOF / ZIP"
            row["energy"] = format_number(outputs.get("energy_total"))
            row["composition"] = normalize_scalar(info.get("formula")) or metadata.get("formula_sum") or composition_from_symbols(symbols)
            row["atom_count"] = str(len(symbols)) if symbols else ""
            row["charge"] = normalize_scalar(info.get("charge"))
            row["spin"] = normalize_scalar(outputs.get("net_magmom"))
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def load_wbm_symmetry(path: Path) -> dict[str, dict[str, str]]:
    if not path.exists():
        return {}
    result: dict[str, dict[str, str]] = {}
    for row in iter_csv_gz_rows(path):
        material_id = row.get("material_id", "")
        if not material_id:
            continue
        result[material_id] = {
            "spg_num": row.get("spg_num", ""),
            "hall_num": row.get("hall_num", ""),
            "hall_symbol": row.get("hall_symbol", ""),
            "n_sym_ops": row.get("n_sym_ops", ""),
            "symprec": row.get("symprec", ""),
        }
    return result


def write_matbench_wbm_rows(matbench_root: Path, output_tsv: Path) -> int:
    summary_path = matbench_root / "01_wbm_core" / "wbm_summary.csv.gz"
    if not summary_path.exists():
        return 0
    sym_1e2 = load_wbm_symmetry(matbench_root / "01_wbm_core" / "wbm_dft_geo_opt_symprec_1e-2.csv.gz")
    sym_1e5 = load_wbm_symmetry(matbench_root / "01_wbm_core" / "wbm_dft_geo_opt_symprec_1e-5.csv.gz")
    dataset_size = str(csv_gz_row_count(summary_path))
    row_count = 0
    with output_tsv.open("a", encoding="utf-8") as file:
        for item in iter_csv_gz_rows(summary_path):
            material_id = item.get("material_id") or f"wbm-{row_count + 1}"
            formula = item.get("formula", "")
            properties = {
                "化学式": formula,
                "体积 (A^3)": item.get("volume"),
                "未校正总能量 (eV)": item.get("uncorrected_energy"),
                "WBM 形成能 (eV/atom)": item.get("e_form_per_atom_wbm"),
                "WBM 凸包能量 (eV/atom)": item.get("e_above_hull_wbm"),
                "PBE 带隙 (eV)": item.get("bandgap_pbe"),
                "初始结构原型": item.get("protostructure_spglib_initial_structure"),
                "弛豫后结构原型": item.get("protostructure_spglib"),
                "是否唯一原型": item.get("unique_prototype"),
                "MP2020 校正形成能 (eV/atom)": item.get("e_form_per_atom_mp2020_corrected"),
                "MP2020 校正凸包能量 (eV/atom)": item.get("e_above_hull_mp2020_corrected_ppd_mp"),
                "DFT 对称性 symprec=1e-2": sym_1e2.get(material_id),
                "DFT 对称性 symprec=1e-5": sym_1e5.get(material_id),
            }
            row = blank_record(MATBENCH_WBM_DATASET_KEY, material_id)
            row["dataset_name"] = "Matbench Discovery WBM Summary"
            row["dataset_size"] = dataset_size
            row["dataset_description"] = (
                "Matbench Discovery 的 WBM 摘要数据面向无机晶体稳定性筛选，提供 DFT 形成能、"
                "凸包能量、PBE 带隙、原型结构和对称性等字段，适合用于材料发现模型和稳定性基准评估。"
            )
            row["material_name"] = formula or material_id
            row["material_id"] = material_id
            row["simulation_type"] = "晶体稳定性基准"
            row["validated_status"] = "computed"
            row["calculation_software"] = "VASP / Materials Project workflow"
            row["homo_lumo_gap"] = format_number(item.get("bandgap_pbe"))
            row["doi"] = "10.1038/s42256-025-01055-1"
            row["category"] = "无机晶体稳定性基准"
            row["calculation_platform"] = "Matbench Discovery / Figshare"
            row["energy"] = format_number(item.get("uncorrected_energy"))
            row["composition"] = compact_formula(formula)
            row["atom_count"] = format_number(item.get("n_sites"), precision=8)
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def write_matbench_mp_energy_rows(matbench_root: Path, output_tsv: Path) -> int:
    energies_path = matbench_root / "02_materials_project_reference" / "mp_energies.csv.gz"
    if not energies_path.exists():
        return 0
    dataset_size = str(csv_gz_row_count(energies_path))
    row_count = 0
    with output_tsv.open("a", encoding="utf-8") as file:
        for item in iter_csv_gz_rows(energies_path):
            material_id = item.get("material_id") or f"mp-energy-{row_count + 1}"
            formula = item.get("formula", "")
            properties = {
                "化学式": formula,
                "每原子能量 (eV/atom)": item.get("energy_per_atom"),
                "形成能 (eV/atom)": item.get("formation_energy_per_atom"),
                "凸包能量 (eV/atom)": item.get("energy_above_hull"),
                "分解焓 (eV/atom)": item.get("decomposition_enthalpy"),
                "能量类型": item.get("energy_type"),
                "空间群": item.get("spacegroup_symbol"),
                "结构原型 spglib": item.get("protostructure_spglib"),
                "结构原型 moyo": item.get("protostructure_moyo"),
            }
            row = blank_record(MATBENCH_MP_ENERGIES_DATASET_KEY, material_id)
            row["dataset_name"] = "Materials Project Reference Energies"
            row["dataset_size"] = dataset_size
            row["dataset_description"] = (
                "Matbench Discovery 使用的 Materials Project 参考能量表，包含每原子能量、形成能、"
                "凸包能量、分解焓、能量类型、空间群和原型结构等字段，可作为晶体稳定性与能量预测任务的参考数据。"
            )
            row["material_name"] = formula or material_id
            row["material_id"] = material_id
            row["simulation_type"] = "Materials Project DFT 参考能量"
            row["validated_status"] = "computed"
            row["calculation_software"] = "VASP / Materials Project"
            row["doi"] = "10.1038/s42256-025-01055-1"
            row["category"] = "MP 无机晶体参考能量"
            row["calculation_platform"] = "Matbench Discovery / Materials Project"
            row["energy"] = format_number(item.get("energy_per_atom"))
            row["composition"] = compact_formula(formula)
            row["atom_count"] = format_number(item.get("n_sites"), precision=8)
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def load_phonondb_kappa(path: Path) -> dict[str, dict[str, Any]]:
    if not path.exists():
        return {}
    with gzip.open(path, "rt", encoding="utf-8", errors="replace") as file:
        records = json.load(file)
    return {normalize_scalar(item.get("material_id")): item for item in records if normalize_scalar(item.get("material_id"))}


def write_matbench_phonondb_rows(matbench_root: Path, output_tsv: Path) -> int:
    structures_path = matbench_root / "04_phonondb_thermal" / "phonondb_pbe_103_structures.extxyz"
    kappa_path = matbench_root / "04_phonondb_thermal" / "phonondb_pbe_103_kappa_no_nac.json.gz"
    if not structures_path.exists():
        return 0
    kappa_by_id = load_phonondb_kappa(kappa_path)
    with structures_path.open("rb") as structure_file:
        frames = list(iter_extxyz_frames(structure_file))
    dataset_size = str(len(frames))
    row_count = 0
    with output_tsv.open("a", encoding="utf-8") as file:
        for _index, symbols, positions, _forces, metadata in frames:
            material_id = metadata.get("material_id") or f"phonondb-{row_count + 1}"
            kappa = kappa_by_id.get(material_id, {})
            lattice = parse_lattice(metadata.get("Lattice", ""))
            name = metadata.get("name") or normalize_scalar(kappa.get("name")) or composition_from_symbols(symbols)
            kappa_tot = kappa.get("kappa_tot_avg") or []
            properties = {
                "名称": name,
                "材料 ID": material_id,
                "空间群编号": metadata.get("symm.no") or normalize_scalar(kappa.get("spg_num")),
                "q 点网格": metadata.get("q_point_mesh") or normalize_scalar(kappa.get("q_mesh")),
                "二阶力常数超胞": metadata.get("fc2_supercell"),
                "三阶力常数超胞": metadata.get("fc3_supercell"),
                "温度 (K)": kappa.get("temperatures"),
                "平均热导率": kappa_tot[0] if kappa_tot else "",
                "q 点数量": len(kappa.get("q_points", []) or []),
                "声子频率 q 点数量": len(kappa.get("ph_freqs", []) or []),
            }
            row = blank_record(MATBENCH_PHONONDB_DATASET_KEY, material_id)
            row["dataset_name"] = "PhononDB PBE 103 Thermal Conductivity"
            row["dataset_size"] = dataset_size
            row["dataset_description"] = (
                "Matbench Discovery 收录的 PhononDB PBE 103 子集，提供晶体结构、声子相关元数据和"
                "晶格热导率标签，适合展示热输运性质、结构-性质关联和小规模热导率预测示例。"
            )
            row["material_name"] = name
            row["material_id"] = material_id
            row["simulation_type"] = "PBE 声子与热导率计算"
            row["validated_status"] = "computed"
            row["structure_json"] = structure_json_from_symbol_positions(symbols, positions, lattice)
            row["calculation_software"] = "VASP / PhononDB workflow"
            row["doi"] = "10.1038/s42256-025-01055-1"
            row["category"] = "声子与热导率基准"
            row["calculation_platform"] = "Matbench Discovery / PhononDB"
            row["energy"] = format_number(kappa_tot[0] if kappa_tot else "")
            row["composition"] = composition_from_symbols(symbols)
            row["atom_count"] = str(len(symbols))
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def write_hydrocarbon_gap_rows(hydrocarbon_zip: Path, output_tsv: Path) -> int:
    if not hydrocarbon_zip.exists():
        return 0
    frame_count, _min_atoms, _max_atoms = summarize_hydrocarbon_zip(hydrocarbon_zip)
    dataset_size = str(frame_count)
    row_count = 0
    with output_tsv.open("a", encoding="utf-8") as file:
        for frame_index, symbols, positions, forces, metadata in iter_nested_hydrocarbon_frames(hydrocarbon_zip):
            source_record_id = f"train.xyz#{frame_index}"
            config_type = metadata.get("config_type", "unknown")
            lattice = parse_lattice(metadata.get("Lattice", ""))
            composition = composition_from_symbols(symbols)
            properties = {
                "构型类型": config_type,
                "内聚能": metadata.get("energy_cohes"),
                "自由能": metadata.get("free_energy"),
                "应力": metadata.get("stress"),
                "能量 sigma": metadata.get("energy_sigma"),
                "周期性边界": metadata.get("pbc"),
                "晶胞": metadata.get("Lattice"),
                "源数据压缩包": "train_tagged.zip / train.zip / train.xyz",
                "论文": "Unifying the description of hydrocarbons and hydrogenated carbon materials with a chemically reactive ML interatomic potential",
            }
            row = blank_record(HYDROCARBONS_GAP_DATASET_KEY, source_record_id)
            row["dataset_name"] = "Hydrocarbons CH GAP Training Set"
            row["dataset_size"] = dataset_size
            row["dataset_description"] = (
                "面向碳氢化合物和氢化碳材料反应型机器学习势的 DFT 标注构型数据，包含原子坐标、"
                "能量、力、应力和 config_type 构型类别，可用于 GAP 势函数训练与快速数据质量验证。"
            )
            row["material_name"] = f"CH GAP {config_type} #{frame_index}"
            row["material_id"] = source_record_id
            row["force_field"] = "GAP"
            row["simulation_type"] = "DFT 标注的反应型机器学习势训练"
            row["validated_status"] = "computed"
            row["structure_json"] = structure_json_from_symbol_positions(symbols, positions, lattice)
            row["calculation_software"] = "DFT / QUIP-GAP training format"
            row["doi"] = "10.48550/arXiv.2409.08194"
            row["category"] = "碳氢反应型机器学习势训练数据"
            row["calculation_platform"] = "arXiv supplementary data / extxyz"
            row["energy"] = format_number(metadata.get("energy"))
            row["forces_json"] = json_cell({"forces": forces}) if forces.size else ""
            row["composition"] = composition
            row["atom_count"] = str(len(symbols))
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


MATBENCH_V01_CONFIGS = {
    "matbench_dielectric": {
        "dataset_key": MATBENCH_V01_DIELECTRIC_DATASET_KEY,
        "dataset_name": "Matbench v0.1 Dielectric",
        "target": "n",
        "target_label": "折射率 n",
        "simulation_type": "Materials Project 介电性质 DFT 基准",
        "category": "介电性质预测基准",
        "description": (
            "Matbench v0.1 的介电性质预测任务，来自 Materials Project 计算数据，提供晶体结构和"
            "折射率 n 标签，适合展示结构-介电性质预测和小规模材料机器学习基准。"
        ),
    },
    "matbench_jdft2d": {
        "dataset_key": MATBENCH_V01_JDFT2D_DATASET_KEY,
        "dataset_name": "Matbench v0.1 JDFT2D",
        "target": "exfoliation_en",
        "target_label": "剥离能",
        "simulation_type": "JARVIS-DFT 二维材料剥离能基准",
        "category": "二维材料剥离能预测基准",
        "description": (
            "Matbench v0.1 的 JDFT2D 任务，面向二维材料剥离能预测，提供晶体结构和"
            "exfoliation energy 标签，可与当前 JARVIS-DFT 2D 数据互补。"
        ),
        "store_target_as_energy": True,
    },
    "matbench_phonons": {
        "dataset_key": MATBENCH_V01_PHONONS_DATASET_KEY,
        "dataset_name": "Matbench v0.1 Phonons",
        "target": "last phdos peak",
        "target_label": "最后一个声子 DOS 峰",
        "simulation_type": "DFT 声子性质基准",
        "category": "声子性质预测基准",
        "description": (
            "Matbench v0.1 的声子性质任务，提供晶体结构和 phonon DOS 最后峰位标签，"
            "适合展示声子/振动性质预测。"
        ),
    },
    "matbench_perovskites": {
        "dataset_key": MATBENCH_V01_PEROVSKITES_DATASET_KEY,
        "dataset_name": "Matbench v0.1 Perovskites",
        "target": "e_form",
        "target_label": "形成能",
        "simulation_type": "钙钛矿 DFT 形成能基准",
        "category": "钙钛矿形成能预测基准",
        "description": (
            "Matbench v0.1 的钙钛矿形成能任务，提供 ABX3 类晶体结构和 DFT 形成能标签，"
            "适合用于钙钛矿筛选、形成能回归和结构-稳定性关系展示。"
        ),
        "store_target_as_energy": True,
    },
    "matbench_log_gvrh": {
        "dataset_key": MATBENCH_V01_LOG_GVRH_DATASET_KEY,
        "dataset_name": "Matbench v0.1 log GVRH",
        "target": "log10(G_VRH)",
        "target_label": "log10 剪切模量 G_VRH",
        "simulation_type": "DFT 弹性性质基准",
        "category": "剪切模量预测基准",
        "description": (
            "Matbench v0.1 的弹性性质任务，提供晶体结构和 Voigt-Reuss-Hill 剪切模量"
            "log10(G_VRH) 标签，适合展示力学性质机器学习基准。"
        ),
    },
    "matbench_log_kvrh": {
        "dataset_key": MATBENCH_V01_LOG_KVRH_DATASET_KEY,
        "dataset_name": "Matbench v0.1 log KVRH",
        "target": "log10(K_VRH)",
        "target_label": "log10 体积模量 K_VRH",
        "simulation_type": "DFT 弹性性质基准",
        "category": "体积模量预测基准",
        "description": (
            "Matbench v0.1 的弹性性质任务，提供晶体结构和 Voigt-Reuss-Hill 体积模量"
            "log10(K_VRH) 标签，适合展示力学性质机器学习基准。"
        ),
    },
}


def load_matbench_v01(path: Path) -> tuple[list[int], list[str], list[list[Any]]]:
    with gzip.open(path, "rt", encoding="utf-8", errors="replace") as file:
        payload = json.load(file)
    return payload.get("index", []), payload.get("columns", []), payload.get("data", [])


def write_matbench_v01_rows(matbench_v01_root: Path, output_tsv: Path) -> dict[str, int]:
    counts: dict[str, int] = {}
    with output_tsv.open("a", encoding="utf-8") as file:
        for stem, config in MATBENCH_V01_CONFIGS.items():
            path = matbench_v01_root / f"{stem}.json.gz"
            if not path.exists():
                counts[config["dataset_key"]] = 0
                continue
            indexes, columns, data_rows = load_matbench_v01(path)
            column_index = {column: index for index, column in enumerate(columns)}
            structure_index = column_index.get("structure")
            target_index = column_index.get(config["target"])
            if structure_index is None or target_index is None:
                counts[config["dataset_key"]] = 0
                continue
            row_count = 0
            dataset_size = str(len(data_rows))
            for position, values in enumerate(data_rows):
                structure = values[structure_index]
                target = values[target_index]
                source_index = indexes[position] if position < len(indexes) else position
                source_record_id = f"{stem}-{source_index}"
                symbols, coordinates, lattice = pymatgen_structure_to_symbols_positions(structure)
                formula = composition_from_symbols(symbols)
                properties = {
                    "Matbench 任务": stem,
                    config["target_label"]: target,
                    "原始目标字段": config["target"],
                    "晶胞体积 (A^3)": (structure.get("lattice") or {}).get("volume") if isinstance(structure, dict) else "",
                    "晶格常数 a": (structure.get("lattice") or {}).get("a") if isinstance(structure, dict) else "",
                    "晶格常数 b": (structure.get("lattice") or {}).get("b") if isinstance(structure, dict) else "",
                    "晶格常数 c": (structure.get("lattice") or {}).get("c") if isinstance(structure, dict) else "",
                    "源文件": path.name,
                }
                row = blank_record(config["dataset_key"], source_record_id)
                row["dataset_name"] = config["dataset_name"]
                row["dataset_size"] = dataset_size
                row["dataset_description"] = config["description"]
                row["material_name"] = formula or source_record_id
                row["material_id"] = source_record_id
                row["simulation_type"] = config["simulation_type"]
                row["validated_status"] = "benchmark"
                if len(symbols) and coordinates.size:
                    row["radius_gyration_rg"] = radius_of_gyration(coordinates, masses_from_symbols(symbols))
                    row["structure_json"] = structure_json_from_symbol_positions(symbols, coordinates, lattice)
                row["calculation_software"] = "VASP / Materials Project workflow"
                row["doi"] = "10.1038/s41524-020-00406-3"
                row["category"] = config["category"]
                row["calculation_platform"] = "Matbench v0.1 / Materials Project"
                if config.get("store_target_as_energy"):
                    row["energy"] = format_number(target)
                row["composition"] = formula
                row["atom_count"] = str(len(symbols)) if symbols else ""
                row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
                write_tsv_row(file, row)
                row_count += 1
            counts[config["dataset_key"]] = row_count
    return counts


def write_qm9_rows(qm9_csv_path: Path, output_tsv: Path) -> int:
    if not qm9_csv_path.exists():
        return 0
    with qm9_csv_path.open("r", encoding="utf-8", newline="") as source:
        dataset_size = str(sum(1 for _ in source) - 1)
    row_count = 0
    with qm9_csv_path.open("r", encoding="utf-8", newline="") as source, output_tsv.open("a", encoding="utf-8") as file:
        reader = csv.DictReader(source)
        for item in reader:
            mol_id = item.get("mol_id") or f"qm9-{row_count + 1}"
            smiles = item.get("smiles", "")
            properties = {
                "SMILES": smiles,
                "偶极矩 mu (Debye)": item.get("mu"),
                "各向同性极化率 alpha (Bohr^3)": item.get("alpha"),
                "转动常数 A": item.get("A"),
                "转动常数 B": item.get("B"),
                "转动常数 C": item.get("C"),
                "电子空间范围 r2": item.get("r2"),
                "零点振动能 zpve": item.get("zpve"),
                "U0": item.get("u0"),
                "U298": item.get("u298"),
                "H298": item.get("h298"),
                "G298": item.get("g298"),
                "Cv": item.get("cv"),
                "原子化 U0": item.get("u0_atom"),
                "原子化 G298": item.get("g298_atom"),
            }
            row = blank_record(QM9_DATASET_KEY, mol_id)
            row["dataset_name"] = "QM9 Molecular DFT Properties"
            row["dataset_size"] = dataset_size
            row["dataset_description"] = (
                "QM9 是小分子量子化学性质基准，包含约 134k 个 GDB-9 有机小分子的 DFT 电子、"
                "热力学和几何相关性质。本项目接入 DeepChem 表格版，展示 SMILES、HOMO/LUMO、"
                "gap、偶极矩、极化率和热力学能量；该 CSV 版本不包含三维坐标。"
            )
            row["material_name"] = f"QM9 {mol_id}"
            row["material_id"] = mol_id
            row["simulation_type"] = "B3LYP/6-31G(2df,p) quantum chemistry benchmark"
            row["validated_status"] = "benchmark"
            row["smiles"] = smiles
            row["calculation_software"] = "Gaussian 09"
            row["homo"] = format_number(item.get("homo"))
            row["lumo"] = format_number(item.get("lumo"))
            row["homo_lumo_gap"] = format_number(item.get("gap"))
            row["doi"] = "10.1038/sdata.2014.22"
            row["category"] = "小分子量子化学性质基准"
            row["calculation_platform"] = "DeepChem / CSV"
            row["energy"] = format_number(item.get("u0"))
            row["composition"] = composition_from_smiles(smiles)
            row["atom_count"] = str(heavy_atom_count_from_smiles(smiles))
            row["properties_json"] = json_cell({key: normalize_scalar(value) for key, value in properties.items() if normalize_scalar(value)})
            write_tsv_row(file, row)
            row_count += 1
    return row_count


def write_paper_metadata_rows(output_tsv: Path) -> int:
    rows = [
        {
            "dataset_key": ANI1X_DATASET_KEY,
            "source_record_id": "dataset-overview",
            "dataset_name": "ANI-1x active learning dataset",
            "dataset_size": "5496771",
            "dataset_description": (
                "Less is more 论文提出的主动学习采样数据集，用于训练通用 ANI 势函数。"
                "论文表 I 报告 ANI-1x 包含 63,865 个分子构型集合和 5,496,771 个结构构象，"
                "元素范围为 H/C/N/O，平均约 15 个原子。论文同时发布 COMP6 benchmark，"
                "用于评估通用机器学习势函数在外推区域的能量和力预测能力。"
            ),
            "material_name": "ANI-1x 数据集概览 · Less is more",
            "material_id": "ani1x-release.h5",
            "force_field": "ANI-1x",
            "simulation_type": "active learning + DFT labeling",
            "validated_status": "metadata",
            "calculation_software": "Gaussian 09 / ASE ANI",
            "doi": "10.1063/1.5023802",
            "category": "molecular ML potential training dataset",
            "calculation_platform": "Figshare / GitHub",
            "composition": "H/C/N/O",
            "atom_count": "15",
            "properties_json": json_cell({
                "论文": "Less is more: Sampling chemical space with active learning",
                "论文 DOI": "10.1063/1.5023802",
                "数据集": "ANI-1x active learning dataset / COMP6 benchmark",
                "记录规模": "5,496,771 structures; 63,865 molecule configurations",
                "元素": "H, C, N, O",
                "泛函数": "ωB97X",
                "基组": "6-31G(d)",
                "软件": "Gaussian 09; ANI-1x potential packaged with ASE ANI",
                "论文给出的代码/数据链接": "https://github.com/isayev/ASE_ANI ; https://github.com/isayev/COMP6",
                "常用 HDF5 发布文件": "ani1x-release.h5",
                "常用 Figshare DOI": "10.6084/m9.figshare.10047041.v1",
                "本项目说明": "当前先接入论文元数据；完整 HDF5 放入 documents/data 后可继续扩展为真实构象抽样展示。",
            }),
        },
        {
            "dataset_key": TRANSITION1X_DATASET_KEY,
            "source_record_id": "dataset-overview",
            "dataset_name": "Transition1x",
            "dataset_size": "9600000",
            "dataset_description": (
                "Transition1x 是用于训练可泛化反应型机器学习势函数的数据集，包含约 9.6M 个"
                "位于反应路径及其附近的 DFT 能量和力计算。数据由约 10k 个收敛有机反应的 NEB/CINEB "
                "路径生成，HDF5 中按 data/train/val/test、化学式和 reaction group 组织。"
            ),
            "material_name": "Transition1x 数据集概览 · NEB reaction paths",
            "material_id": "Transition1x.h5",
            "force_field": "PaiNN validation baseline",
            "simulation_type": "NEB/CINEB reaction path DFT calculations",
            "validated_status": "metadata",
            "calculation_software": "ORCA 5.0.2; ASE 3.22.1",
            "doi": "10.1038/s41597-022-01870-w",
            "category": "reactive molecular ML potential training dataset",
            "calculation_platform": "Figshare / GitLab",
            "composition": "H/C/N/O",
            "properties_json": json_cell({
                "论文": "Transition1x - a dataset for building generalizable reactive machine learning potentials",
                "论文 DOI": "10.1038/s41597-022-01870-w",
                "数据集 DOI": "10.6084/m9.figshare.19614657.v4",
                "数据集链接": "https://doi.org/10.6084/m9.figshare.19614657.v4",
                "代码链接": "https://gitlab.com/matschreiner/Transition1x",
                "文件": "Transition1x.h5",
                "记录规模": "9.6 million DFT configurations; 10,073 converged reactions",
                "初始反应来源": "11,961 GDB7-based reactant-product pairs from Grambow et al.",
                "元素": "H, C, N, O; up to seven heavy atoms",
                "泛函数": "ωB97X",
                "基组": "6-31G(d)",
                "软件": "ORCA 5.0.2; ASE 3.22.1",
                "HDF5 结构": "data/train/val/test -> formula -> reaction -> atomic_numbers, energy, forces, positions",
                "本项目说明": "当前先接入论文元数据；完整 HDF5 放入 documents/data 后可继续扩展为真实反应路径抽样展示。",
            }),
        },
    ]
    with output_tsv.open("a", encoding="utf-8") as file:
        for values in rows:
            row = blank_record(values["dataset_key"], values["source_record_id"])
            row.update(values)
            write_tsv_row(file, row)
    return len(rows)


def ensure_h2_jar(path: Path, url: str) -> Path:
    if path.exists() and path.stat().st_size > 0:
        return path
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    print(f"Downloading H2 JDBC jar -> {path}")
    with urllib.request.urlopen(url, timeout=120) as response, tmp_path.open("wb") as file:
        shutil.copyfileobj(response, file)
    tmp_path.replace(path)
    return path


def column_type(column: str, with_clob_default: bool) -> str:
    if column in CLOB_COLUMNS:
        return "CLOB DEFAULT ''" if with_clob_default else "CLOB"
    if column == "dataset_key":
        return "VARCHAR(64) NOT NULL"
    if column == "source_record_id":
        return "VARCHAR(128) NOT NULL"
    return "VARCHAR(255) DEFAULT ''"


def create_sql(with_clob_default: bool) -> str:
    lines = ["CREATE TABLE display_records (", "  id BIGINT AUTO_INCREMENT PRIMARY KEY,"]
    for index, column in enumerate(DISPLAY_COLUMNS):
        suffix = "," if index < len(DISPLAY_COLUMNS) - 1 else ""
        lines.append(f"  {column} {column_type(column, with_clob_default)}{suffix}")
    lines.append(")")
    return "\n".join(lines)


def java_string(value: str) -> str:
    return json.dumps(value)


def write_java_importer(source_path: Path) -> None:
    insert_columns = ", ".join(DISPLAY_COLUMNS)
    placeholders = ", ".join(["?"] * len(DISPLAY_COLUMNS))
    insert_sql = f"INSERT INTO display_records ({insert_columns}) VALUES ({placeholders})"
    java_source = f"""
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

public class FrontendTemplateH2Importer {{
    private static final int COLUMN_COUNT = {len(DISPLAY_COLUMNS)};
    private static final String CREATE_SQL = {java_string(create_sql(with_clob_default=True))};
    private static final String CREATE_SQL_FALLBACK = {java_string(create_sql(with_clob_default=False))};
    private static final String INSERT_SQL = {java_string(insert_sql)};

    public static void main(String[] args) throws Exception {{
        if (args.length != 2) {{
            throw new IllegalArgumentException("Usage: FrontendTemplateH2Importer <jdbc-url> <base64-tsv>");
        }}
        String jdbcUrl = args[0];
        String tsvPath = args[1];
        Class.forName("org.h2.Driver");
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {{
            conn.setAutoCommit(false);
            createTable(conn);
            long inserted = importRows(conn, tsvPath);
            createIndexes(conn);
            conn.commit();
            System.out.println("inserted_rows=" + inserted);
        }}
    }}

    private static void createTable(Connection conn) throws SQLException {{
        try (Statement stmt = conn.createStatement()) {{
            stmt.execute("DROP TABLE IF EXISTS display_records");
            try {{
                stmt.execute(CREATE_SQL);
            }} catch (SQLException ex) {{
                stmt.execute(CREATE_SQL_FALLBACK);
            }}
        }}
    }}

    private static long importRows(Connection conn, String tsvPath) throws SQLException, IOException {{
        Base64.Decoder decoder = Base64.getDecoder();
        long inserted = 0L;
        try (
            BufferedReader reader = Files.newBufferedReader(Paths.get(tsvPath), StandardCharsets.UTF_8);
            PreparedStatement statement = conn.prepareStatement(INSERT_SQL)
        ) {{
            String line;
            while ((line = reader.readLine()) != null) {{
                String[] parts = line.split("\\\\t", -1);
                if (parts.length != COLUMN_COUNT) {{
                    throw new IOException("Expected " + COLUMN_COUNT + " columns, got " + parts.length);
                }}
                for (int index = 0; index < COLUMN_COUNT; index++) {{
                    byte[] decoded = decoder.decode(parts[index]);
                    statement.setString(index + 1, new String(decoded, StandardCharsets.UTF_8));
                }}
                statement.addBatch();
                inserted++;
                if (inserted % 1000L == 0L) {{
                    statement.executeBatch();
                    conn.commit();
                }}
            }}
            statement.executeBatch();
        }}
        return inserted;
    }}

    private static void createIndexes(Connection conn) throws SQLException {{
        try (Statement stmt = conn.createStatement()) {{
            stmt.execute("CREATE INDEX idx_display_dataset_id ON display_records(dataset_key, id)");
            stmt.execute("CREATE INDEX idx_display_dataset_source ON display_records(dataset_key, source_record_id)");
            stmt.execute("CREATE INDEX idx_display_dataset_material ON display_records(dataset_key, material_id)");
        }}
    }}
}}
"""
    source_path.write_text(textwrap.dedent(java_source).strip() + "\n", encoding="utf-8")


def remove_existing_h2_files(output_base: Path) -> None:
    for suffix in [".mv.db", ".lock.db", ".trace.db"]:
        path = Path(str(output_base) + suffix)
        if path.exists():
            path.unlink()


def run_checked(command: list[str], cwd: Path) -> None:
    print("+ " + " ".join(command))
    subprocess.run(command, cwd=str(cwd), check=True)


def build_database(
    project_root: Path,
    data_root: Path,
    extra_data_root: Path,
    output_base: Path,
    h2_jar: Path,
    work_dir: Path,
    keep_temp: bool,
) -> None:
    h5_path = data_root / "ani_gdb_s03.h5"
    lmdb_path = data_root / "data0000.aselmdb"
    openpoly_path = data_root / "calculated_polymer_data.xlsx"
    ani1x_path = data_root / "ani1x-release.h5"
    transition1x_path = data_root / "Transition1x.h5"
    twod_matpedia_path = extra_data_root / "2Dmatpedia" / "2Dmatpedia.json"
    jarvis_3d_path = extra_data_root / "jdft" / "jdft_3d-9-24-2025.json"
    jarvis_2d_zip_path = extra_data_root / "jdft" / "6815705.zip"
    polymer_cif_path = extra_data_root / "polymer-cif" / "Polymer-CIF.tgz"
    qmof_zip_path = extra_data_root / "qmof" / "qmof_database.zip"
    qmof_thermo_zip_path = extra_data_root / "qmof" / "qmof_thermo_database.zip"
    matbench_root = extra_data_root / "matbench_discovery"
    matbench_v01_root = extra_data_root / "matbench_v01"
    hydrocarbon_zip_path = extra_data_root / "hydrocarbons" / "train_tagged.zip"
    qm9_csv_path = extra_data_root / "qm9" / "qm9.csv"
    if not h5_path.exists():
        raise FileNotFoundError(h5_path)
    if not lmdb_path.exists():
        raise FileNotFoundError(lmdb_path)
    if not openpoly_path.exists():
        raise FileNotFoundError(openpoly_path)

    work_dir.mkdir(parents=True, exist_ok=True)
    h2_jar = ensure_h2_jar(h2_jar, H2_DOWNLOAD_URL)
    tsv_path = work_dir / "display_records.base64.tsv"
    if tsv_path.exists():
        tsv_path.unlink()
    tsv_path.touch()

    print(f"Writing source rows -> {tsv_path}")
    ani_rows = write_ani_rows(h5_path, tsv_path)
    lmdb_rows = write_lmdb_rows(lmdb_path, tsv_path)
    openpoly_rows = write_openpoly_rows(openpoly_path, tsv_path)
    if ani1x_path.exists() and transition1x_path.exists():
        ani1x_rows = write_ani1x_rows(ani1x_path, tsv_path)
        transition1x_rows = write_transition1x_rows(transition1x_path, tsv_path)
        paper_metadata_rows = 0
    else:
        ani1x_rows = 0
        transition1x_rows = 0
        paper_metadata_rows = write_paper_metadata_rows(tsv_path)
    if twod_matpedia_path.exists():
        twod_matpedia_rows = write_twod_matpedia_rows(twod_matpedia_path, tsv_path)
    else:
        twod_matpedia_rows = 0
    if jarvis_3d_path.exists():
        jarvis_3d_rows = write_jarvis_rows(
            jarvis_3d_path,
            tsv_path,
            JARVIS_3D_DATASET_KEY,
            "JARVIS-DFT 3D Bulk Materials",
            "93902",
        )
    else:
        jarvis_3d_rows = 0
    if jarvis_2d_zip_path.exists():
        jarvis_2d_json = extract_latest_jarvis_2d_json(jarvis_2d_zip_path, work_dir)
        jarvis_2d_rows = write_jarvis_rows(
            jarvis_2d_json,
            tsv_path,
            JARVIS_2D_DATASET_KEY,
            "JARVIS-DFT 2D Materials",
            "1103",
        )
    else:
        jarvis_2d_rows = 0
    if polymer_cif_path.exists():
        polymer_genome_rows = write_polymer_genome_rows(polymer_cif_path, tsv_path)
    else:
        polymer_genome_rows = 0
    if qmof_zip_path.exists() and qmof_thermo_zip_path.exists():
        qmof_rows = write_qmof_rows(qmof_zip_path, qmof_thermo_zip_path, tsv_path)
    else:
        qmof_rows = 0
    matbench_wbm_rows = write_matbench_wbm_rows(matbench_root, tsv_path)
    matbench_mp_energy_rows = write_matbench_mp_energy_rows(matbench_root, tsv_path)
    matbench_phonondb_rows = write_matbench_phonondb_rows(matbench_root, tsv_path)
    matbench_v01_rows = write_matbench_v01_rows(matbench_v01_root, tsv_path)
    hydrocarbon_gap_rows = write_hydrocarbon_gap_rows(hydrocarbon_zip_path, tsv_path)
    qm9_rows = write_qm9_rows(qm9_csv_path, tsv_path)
    print(
        "prepared_rows: "
        f"{ANI_DATASET_KEY}={ani_rows}, "
        f"{LMDB_DATASET_KEY}={lmdb_rows}, "
        f"{OPENPOLY_DATASET_KEY}={openpoly_rows}, "
        f"{ANI1X_DATASET_KEY}={ani1x_rows}, "
        f"{TRANSITION1X_DATASET_KEY}={transition1x_rows}, "
        f"{TWOD_MATPEDIA_DATASET_KEY}={twod_matpedia_rows}, "
        f"{JARVIS_3D_DATASET_KEY}={jarvis_3d_rows}, "
        f"{JARVIS_2D_DATASET_KEY}={jarvis_2d_rows}, "
        f"{POLYMER_GENOME_DATASET_KEY}={polymer_genome_rows}, "
        f"{QMOF_DATASET_KEY}={qmof_rows}, "
        f"{MATBENCH_WBM_DATASET_KEY}={matbench_wbm_rows}, "
        f"{MATBENCH_MP_ENERGIES_DATASET_KEY}={matbench_mp_energy_rows}, "
        f"{MATBENCH_PHONONDB_DATASET_KEY}={matbench_phonondb_rows}, "
        f"{HYDROCARBONS_GAP_DATASET_KEY}={hydrocarbon_gap_rows}, "
        + ", ".join(f"{key}={value}" for key, value in matbench_v01_rows.items()) + ", "
        f"{QM9_DATASET_KEY}={qm9_rows}, "
        f"paper_metadata={paper_metadata_rows}"
    )

    source_path = work_dir / "FrontendTemplateH2Importer.java"
    write_java_importer(source_path)
    run_checked(["javac", "-cp", str(h2_jar), str(source_path)], cwd=project_root)

    output_base.parent.mkdir(parents=True, exist_ok=True)
    remove_existing_h2_files(output_base)
    jdbc_url = "jdbc:h2:file:./documents/data/frontend_template_data"
    run_checked(
        [
            "java",
            "-cp",
            f"{h2_jar}{';' if sys.platform.startswith('win') else ':'}{work_dir}",
            "FrontendTemplateH2Importer",
            jdbc_url,
            str(tsv_path),
        ],
        cwd=project_root,
    )
    output_file = Path(str(output_base) + ".mv.db")
    if not output_file.exists():
        raise FileNotFoundError(output_file)
    print(f"wrote H2 database -> {output_file}")

    if not keep_temp:
        tsv_path.unlink(missing_ok=True)
        for class_file in work_dir.glob("FrontendTemplateH2Importer*.class"):
            class_file.unlink(missing_ok=True)
        source_path.unlink(missing_ok=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-root", type=Path, default=Path("documents/data"))
    parser.add_argument(
        "--extra-data-root",
        type=Path,
        default=Path("../数据集及论文"),
        help="Directory containing newly collected datasets and paper descriptions.",
    )
    parser.add_argument(
        "--output-base",
        type=Path,
        default=Path("documents/data/frontend_template_data"),
        help="H2 file base path without the .mv.db suffix.",
    )
    parser.add_argument("--h2-jar", type=Path, default=Path(f"output/tools/h2-{H2_VERSION}.jar"))
    parser.add_argument("--work-dir", type=Path, default=Path("output/build_frontend_template_h2"))
    parser.add_argument("--keep-temp", action="store_true", help="Keep temporary TSV and Java importer files.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    project_root = Path(__file__).resolve().parents[1]
    build_database(
        project_root=project_root,
        data_root=(project_root / args.data_root).resolve(),
        extra_data_root=(project_root / args.extra_data_root).resolve(),
        output_base=(project_root / args.output_base).resolve(),
        h2_jar=(project_root / args.h2_jar).resolve(),
        work_dir=(project_root / args.work_dir).resolve(),
        keep_temp=args.keep_temp,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
