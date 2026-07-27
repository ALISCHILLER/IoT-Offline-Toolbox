#!/usr/bin/env python3
"""Generate the deterministic source SHA-256 manifest used by release verification."""

from __future__ import annotations

import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs/SOURCE_SHA256SUMS.txt"
IGNORED_PARTS = {"build", ".gradle", ".git", ".kotlin", ".idea", "node_modules", "__pycache__"}


def included_files() -> list[Path]:
    return sorted(
        path
        for path in ROOT.rglob("*")
        if path.is_file()
        and path != OUTPUT
        and not any(part in IGNORED_PARTS for part in path.parts)
    )


def main() -> None:
    lines = []
    for path in included_files():
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        relative = path.relative_to(ROOT).as_posix()
        lines.append(f"{digest}  ./{relative}")
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(lines)} checksums to {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
