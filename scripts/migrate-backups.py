#!/usr/bin/env python3
"""Migrate the Jan-2026 receipts backups onto the current schema.

The backups predate three separate changes: the package rename to dev.vasoft,
the split into per-domain packages, and the rename of `name` to
`canonicalName`. They also predate user-scoped purchases, so nothing in them
carries a userId — import them untouched and the app shows an empty list.

Reads mongoexport-style JSON arrays and writes migrated JSON arrays ready for
mongoimport. Does not touch the input files.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from datetime import datetime, timedelta, timezone
from pathlib import Path

CLASS = {
    "products": "dev.vasoft.homeapp.receipts.products.model.entities.Product",
    "stores": "dev.vasoft.homeapp.receipts.stores.model.entities.Store",
    "purchases": "dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase",
    "users": "dev.vasoft.homeapp.users.model.entities.User",
}


def normalize(value: str | None) -> str:
    """Port of NormalizationService.normalize, matched behaviour for behaviour.

    NFD-decompose, strip combining marks, lowercase, collapse every run of
    non-alphanumeric characters to a single space, trim.

    The mark-stripping step is not cosmetic for Bulgarian: it decomposes
    Cyrillic Й (U+0419) into И plus a breve and then drops the breve, so Й
    normalizes to И. The Java service does this too. Diverging here would make
    imported products unmatchable by the scanner.
    """
    if value is None:
        return ""
    decomposed = unicodedata.normalize("NFD", value)
    without_marks = "".join(c for c in decomposed if not unicodedata.combining(c))
    kept = "".join(c if (c.isalpha() or c.isdigit()) else " " for c in without_marks)
    return re.sub(r"\s+", " ", kept.lower()).strip()


def load(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as fh:
        data = json.load(fh)
    if not isinstance(data, list):
        sys.exit(f"{path.name}: expected a JSON array, got {type(data).__name__}")
    return data


def find(src: Path, collection: str) -> Path:
    matches = sorted(src.glob(f"*.{collection}-*.json")) or sorted(
        src.glob(f"*{collection}*.json")
    )
    if not matches:
        sys.exit(f"no backup file for '{collection}' in {src}")
    return matches[-1]


def midnight_utc(stamp: str) -> str:
    """Re-anchor a stored instant to midnight UTC of the day it represents.

    Every date in the backup is midnight Europe/Sofia expressed as UTC, so it
    reads as 21:00Z in summer and 22:00Z in winter: the old app mapped dates
    through the JVM's zone. Left as-is, the current API would read each one as
    the previous day.

    Midnight UTC is how the API stores every date: MongoConfig uses the
    MongoDB driver's codecs, which read and write a LocalDate as midnight UTC
    whatever zone the JVM runs in. So the re-anchored value means the same day
    everywhere, and the app's timezone only affects its log timestamps.
    """
    dt = datetime.fromisoformat(stamp.replace("Z", "+00:00")).astimezone(timezone.utc)
    # 21:00Z and 22:00Z are midnight of the *following* day in Europe/Sofia.
    intended = (dt + timedelta(days=1)).date() if dt.hour >= 12 else dt.date()
    return f"{intended.isoformat()}T00:00:00.000Z"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--src", type=Path,
                    default=Path.home() / "docker-apps/homeapp/db/db-backup")
    ap.add_argument("--out", type=Path, default=Path("./migrated"))
    ap.add_argument("--user-id", help="ObjectId to own every purchase "
                                      "(default: the _id from the users backup)")
    ap.add_argument("--keep-instants", action="store_true",
                    help="leave dates as stored, for inspecting the raw backup "
                         "only: the API decodes dates in UTC, so every one "
                         "would read back a day early")
    args = ap.parse_args()

    if not args.src.is_dir():
        sys.exit(f"source directory not found: {args.src}")
    args.out.mkdir(parents=True, exist_ok=True)

    report: list[str] = []

    # users -------------------------------------------------------------- #
    users = load(find(args.src, "users"))
    for u in users:
        u["_class"] = CLASS["users"]
    user_id = args.user_id or (users[0]["_id"]["$oid"] if users else None)
    if not user_id:
        sys.exit("no user id available; pass --user-id")
    report.append(f"users      {len(users):>4}  owner for purchases: {user_id}")

    # products ----------------------------------------------------------- #
    products = []
    for p in load(find(args.src, "products")):
        name = p.get("name") or p.get("canonicalName") or ""
        products.append({
            "_id": p["_id"],
            "canonicalName": name,
            "normalizedCanonicalName": normalize(name),
            "aliases": p.get("aliases", []),
            "normalizedAliases": p.get("normalizedAliases", []),
            "iconID": p.get("iconID"),
            "_class": CLASS["products"],
        })
    report.append(f"products   {len(products):>4}  name -> canonicalName, normalized")

    # stores ------------------------------------------------------------- #
    stores = []
    for s in load(find(args.src, "stores")):
        name = s.get("name") or s.get("canonicalName") or ""
        stores.append({
            "_id": s["_id"],
            "canonicalName": name,
            "normalizedCanonicalName": normalize(name),
            "iconID": s.get("iconID"),
            "_class": CLASS["stores"],
        })
    report.append(f"stores     {len(stores):>4}  name -> canonicalName, normalized")

    # purchases ---------------------------------------------------------- #
    purchases = []
    discounted = 0
    for p in load(find(args.src, "purchases")):
        if p.get("discount") is True:
            discounted += 1
        date = p.get("date")
        if date and not args.keep_instants:
            date = {"$date": midnight_utc(date["$date"])}
        purchases.append({
            "_id": p["_id"],
            "userId": {"$oid": user_id},
            "product": p.get("product"),
            "store": p.get("store"),
            "price": p.get("price"),
            # Every backup row predates the euro changeover. Stored, never
            # inferred (SPEC §9.5); the API's startup backfill would tag
            # them too, but a restore should not depend on it.
            "currency": "BGN",
            "date": date,
            # The old schema stored a boolean. The amount was never recorded,
            # so it cannot be recovered; 0.0 means "unknown", not "none".
            "discountAmount": 0.0,
            "_class": CLASS["purchases"],
        })
    report.append(f"purchases  {len(purchases):>4}  userId backfilled, "
                  f"discount bool -> discountAmount, currency BGN")

    for name, docs in (("users", users), ("products", products),
                       ("stores", stores), ("purchases", purchases)):
        target = args.out / f"{name}.json"
        with target.open("w", encoding="utf-8") as fh:
            json.dump(docs, fh, ensure_ascii=False, indent=1)

    print("\n".join(report))
    print(f"\nwritten to {args.out.resolve()}")
    print("\nData that could not be recovered, because it was never stored:")
    print(f"  - quantity and unit on all {len(purchases)} purchases (defect D1)")
    print(f"  - discount amounts on {discounted} discounted purchases; the old")
    print("    schema held only a true/false flag")
    if not args.keep_instants:
        print("\nDates re-anchored to midnight UTC, which is how the API stores and reads")
        print("every date, whatever timezone it runs in.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
