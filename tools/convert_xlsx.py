#!/usr/bin/env python3
"""Convert gamegear.xlsx to games.json for Android app assets."""

import json
import sys
from pathlib import Path

try:
    import openpyxl
except ImportError:
    print("Run: pip install openpyxl", file=sys.stderr)
    sys.exit(1)

XLSX_PATH = Path(__file__).parent.parent / "gamegear.xlsx"
JSON_PATH = Path(__file__).parent.parent / "app/src/main/assets/games.json"


def parse_region(value) -> bool | None:
    """x → True (owned), o → None (never released), blank → False (not owned)."""
    if value is None:
        return False
    s = str(value).strip().lower()
    if s == "x":
        return True
    if s == "o":
        return None
    return False


def main():
    wb = openpyxl.load_workbook(XLSX_PATH, data_only=True)
    ws = wb.active

    games = []
    rows = list(ws.iter_rows(values_only=True))
    for row in rows[1:]:  # skip header row
        title = row[0]
        if not title or not str(title).strip():
            continue

        title = str(title).strip()
        owned_raw = row[1] if len(row) > 1 else None
        japan_raw = row[2] if len(row) > 2 else None
        usa_raw   = row[3] if len(row) > 3 else None
        eur_raw   = row[4] if len(row) > 4 else None
        notes_raw = row[5] if len(row) > 5 else None

        owned_str = str(owned_raw).strip().lower() if owned_raw is not None else ""
        owned = owned_str == "x"

        games.append({
            "title":       title,
            "owned":       owned,
            "japanOwned":  parse_region(japan_raw),
            "usaOwned":    parse_region(usa_raw),
            "europeOwned": parse_region(eur_raw),
            "notes":       str(notes_raw).strip() if notes_raw is not None else None,
        })

    JSON_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(games, f, ensure_ascii=False, indent=2)

    print(f"Wrote {len(games)} games to {JSON_PATH}")


if __name__ == "__main__":
    main()
