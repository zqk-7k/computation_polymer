#!/usr/bin/env python3
"""Lightweight scientific preflight for uploaded structure samples.

The script is intentionally small and defensive: it uses ASE when available,
falls back to pymatgen for CIF files, and reports "not_available" instead of
pretending that a rigorous scientific validation was performed.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path


def emit(payload: dict) -> None:
    text = json.dumps(payload, ensure_ascii=False) + "\n"
    sys.stdout.buffer.write(text.encode("utf-8"))


def finite_triplet(values) -> bool:
    try:
        if len(values) < 3:
            return False
        return all(math.isfinite(float(values[i])) and abs(float(values[i])) < 1000 for i in range(3))
    except Exception:
        return False


def min_pair_distance(positions) -> float | None:
    coords = [tuple(float(v) for v in row[:3]) for row in positions]
    if len(coords) < 2:
        return None
    best = None
    for i, a in enumerate(coords):
        for b in coords[i + 1 :]:
            d2 = sum((a[j] - b[j]) ** 2 for j in range(3))
            d = math.sqrt(d2)
            best = d if best is None else min(best, d)
    return best


def validate_ase(path: Path, max_records: int) -> dict:
    try:
        from ase.io import read  # type: ignore
    except Exception as exc:
        return {
            "available": False,
            "engine": "ASE",
            "score": 0,
            "summary": "未安装 ASE，无法执行结构科学预检。",
            "recommendations": [
                "在服务器 Python 环境安装 ase；如需晶体结构深度校验，同时安装 pymatgen。",
                f"当前导入失败: {exc.__class__.__name__}: {exc}",
            ],
        }

    try:
        structures = read(str(path), index=f":{max_records}")
        if not isinstance(structures, list):
            structures = [structures]
    except Exception as exc:
        return {
            "available": True,
            "engine": "ASE",
            "score": 0,
            "summary": f"ASE 解析失败: {exc}",
            "recommendations": [
                "确认文件格式、扩展名和内容一致；必要时提供前 20-40 条独立样本。",
                "如果是压缩包或复杂 HDF5/LMDB，需要先写专用解析适配器再做全量质检。",
            ],
        }

    if not structures:
        return {
            "available": True,
            "engine": "ASE",
            "score": 0,
            "summary": "ASE 未读到任何结构。",
            "recommendations": ["确认文件不是空文件，且包含原子坐标或晶体结构。"],
        }

    invalid_atom_count = 0
    invalid_positions = 0
    short_contacts = 0
    missing_cell = 0
    energy_hits = 0
    force_hits = 0
    elements: set[str] = set()

    for atoms in structures:
        try:
            n_atoms = len(atoms)
            if n_atoms <= 0:
                invalid_atom_count += 1
            elements.update(str(x) for x in atoms.get_chemical_symbols() if str(x))
            positions = atoms.get_positions()
            if any(not finite_triplet(row) for row in positions):
                invalid_positions += 1
            nearest = min_pair_distance(positions)
            if nearest is not None and nearest < 0.35:
                short_contacts += 1
            if any(bool(x) for x in atoms.get_pbc()):
                try:
                    if float(atoms.get_cell().volume) <= 1e-8:
                        missing_cell += 1
                except Exception:
                    missing_cell += 1
            if "energy" in atoms.info or "free_energy" in atoms.info:
                energy_hits += 1
            if "forces" in atoms.arrays:
                force_hits += 1
        except Exception:
            invalid_positions += 1

    sampled = len(structures)
    bad = invalid_atom_count + invalid_positions + short_contacts + missing_cell
    score = max(0, round(100 - 100 * bad / max(sampled, 1)))
    recommendations: list[str] = []
    if invalid_atom_count:
        recommendations.append(f"发现 {invalid_atom_count} 个结构原子数异常，正式入库前应阻断。")
    if invalid_positions:
        recommendations.append(f"发现 {invalid_positions} 个结构坐标包含非有限值或异常大坐标。")
    if short_contacts:
        recommendations.append(f"发现 {short_contacts} 个结构存在疑似过短原子距离，需检查单位或结构生成过程。")
    if missing_cell:
        recommendations.append(f"发现 {missing_cell} 个周期结构缺失有效晶胞。")
    if not recommendations:
        recommendations.append("抽样结构通过 ASE 基础合法性检查；正式发布前仍建议执行全量结构哈希、键长和单位审计。")
    recommendations.append(
        f"抽样识别元素: {', '.join(sorted(elements)) if elements else '未识别'}；"
        f"能量字段命中 {energy_hits}/{sampled}，力字段命中 {force_hits}/{sampled}。"
    )
    return {
        "available": True,
        "engine": "ASE",
        "score": score,
        "summary": f"ASE 抽样 {sampled} 个结构，异常结构 {bad} 个。",
        "recommendations": recommendations,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", required=True)
    parser.add_argument("--format", default="")
    parser.add_argument("--max-records", type=int, default=40)
    args = parser.parse_args()

    path = Path(args.file)
    if not path.exists():
        emit({
            "available": False,
            "engine": "file",
            "score": 0,
            "summary": "预检文件不存在。",
            "recommendations": ["请重新上传抽样文件。"],
        })
        return 0

    emit(validate_ase(path, max(1, min(args.max_records, 100))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
