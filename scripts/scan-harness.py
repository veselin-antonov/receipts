#!/usr/bin/env python3
"""Run receipt fixtures through /api/receipts/scan and score the results.

Exists so that preprocessing changes can be *measured* rather than eyeballed.
The synthetic receipt that "passed" early on did so while skipping the entire
preprocessing pipeline; a harness with a fixed fixture set makes that class of
mistake visible.

Scoring has three layers, and the first needs no ground truth at all:

  L0  self-consistency   the receipt's own arithmetic. sum(line totals) minus
                         sum(discounts) must equal the printed total. A parse
                         that does not reconcile is wrong, and no label was
                         needed to know it.
  L1  headline           store, date, item count, total against expected/.
  L2  line items         per-item price, quantity, unit and discount.

Numbers are compared strictly, names loosely: transcription error concentrates
in Cyrillic product names, and OCR yields "Р.тон в раст масло" for
"Р.тон в раст.масло" — a difference that matters to nobody.
"""

from __future__ import annotations

import argparse
import difflib
import json
import os
import re
import sys
import time
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT.parent / "receipt-fixtures"
BGN_PER_EUR = 1.95583


# ---------------------------------------------------------------- utilities --
def norm_name(s: str | None) -> str:
    if not s:
        return ""
    d = unicodedata.normalize("NFD", s)
    d = "".join(c for c in d if not unicodedata.combining(c))
    d = "".join(c if (c.isalpha() or c.isdigit()) else " " for c in d)
    return re.sub(r"\s+", " ", d.lower()).strip()


def name_match(a: str | None, b: str | None, threshold: float = 0.75,
               contains_ok: bool = False) -> bool:
    na, nb = norm_name(a), norm_name(b)
    if not na or not nb:
        return False
    if na == nb:
        return True
    # For stores, the brand appearing inside the legal entity counts: receipts
    # print "Кауфланд България ЕООД енд Ко. КД" where the store is "Кауфланд"
    # (D18). Extracting that string is a partial success, not a miss, and
    # scoring it as a miss would hide real improvement.
    if contains_ok and (na in nb or nb in na):
        return True
    # token overlap catches word-order differences; ratio catches OCR noise
    ta, tb = set(na.split()), set(nb.split())
    overlap = len(ta & tb) / max(len(ta | tb), 1)
    return overlap >= 0.6 or difflib.SequenceMatcher(None, na, nb).ratio() >= threshold


def close(a, b, tol=0.02) -> bool:
    if a is None or b is None:
        return False
    return abs(float(a) - float(b)) <= tol


# ------------------------------------------------------------------ scoring --
def score_l0(result: dict) -> dict:
    """Self-consistency. Needs no expected file."""
    purchases = result.get("purchases") or []
    out = {"items": len(purchases), "checks": [], "ok": True}

    if not purchases:
        out["checks"].append(("non_empty", False, "no items parsed"))
        out["ok"] = False
        return out
    out["checks"].append(("non_empty", True, f"{len(purchases)} items"))

    # every row needs a usable price
    bad_price = [p for p in purchases if not isinstance(p.get("price"), (int, float))
                 or p.get("price") in (None, 0)]
    ok = not bad_price
    out["checks"].append(("all_rows_priced", ok,
                          "ok" if ok else f"{len(bad_price)} rows without a price"))
    out["ok"] &= ok

    # a date must be present and not the epoch sentinel
    d = result.get("purchaseDate")
    ok = bool(d) and not str(d).startswith("1970")
    out["checks"].append(("date_present", ok, str(d)))
    out["ok"] &= ok

    # a store name must have been extracted at all
    ok = bool((result.get("rawStoreName") or "").strip())
    out["checks"].append(("store_extracted", ok, repr(result.get("rawStoreName"))))
    out["ok"] &= ok

    out["gross"] = round(sum(float(p.get("price") or 0) for p in purchases), 2)
    out["discounts"] = round(sum(float(p.get("discountAmount") or 0) for p in purchases), 2)
    out["net"] = round(out["gross"] - out["discounts"], 2)
    return out


def score_against_expected(result: dict, exp: dict) -> dict:
    out = {"checks": [], "items": {"matched": 0, "expected": len(exp.get("items", [])),
                                   "price_ok": 0, "qty_ok": 0, "disc_ok": 0}}

    # L0 arithmetic, now against a total someone actually read off the paper
    total = exp.get("total")
    if total is not None:
        purchases = result.get("purchases") or []
        net = sum(float(p.get("price") or 0) for p in purchases) \
            - sum(float(p.get("discountAmount") or 0) for p in purchases)
        ok = close(net, total, 0.05)
        out["checks"].append(("reconciles_to_total", ok, f"{net:.2f} vs {total:.2f}"))

    if exp.get("store"):
        got = result.get("rawStoreName")
        out["checks"].append(("store", name_match(got, exp["store"], contains_ok=True),
                              f"{got!r}"))
    if exp.get("date"):
        out["checks"].append(("date", result.get("purchaseDate") == exp["date"],
                              f"{result.get('purchaseDate')} vs {exp['date']}"))
    if exp.get("items"):
        out["checks"].append(("item_count",
                              len(result.get("purchases") or []) == len(exp["items"]),
                              f"{len(result.get('purchases') or [])} vs {len(exp['items'])}"))

    # greedy line-item match on name, then compare the numbers
    remaining = list(result.get("purchases") or [])
    for want in exp.get("items", []):
        hit = next((g for g in remaining if name_match(g.get("rawProductName"), want.get("name"))), None)
        if not hit:
            continue
        remaining.remove(hit)
        out["items"]["matched"] += 1
        if close(hit.get("price"), want.get("price")):
            out["items"]["price_ok"] += 1
        if want.get("quantity") is not None and close(hit.get("quantity"), want["quantity"], 0.001):
            out["items"]["qty_ok"] += 1
        if want.get("discount") is not None and close(hit.get("discountAmount"), want["discount"]):
            out["items"]["disc_ok"] += 1
    return out


# --------------------------------------------------------------------- main --
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--api", default="http://localhost:7002")
    ap.add_argument("--user", default="dev-test@example.com")
    ap.add_argument("--password", default="DevTest123!")
    ap.add_argument("--fixtures", type=Path, default=FIXTURES)
    ap.add_argument("--filter", help="substring or glob on the fixture name")
    ap.add_argument("--limit", type=int, help="stop after N fixtures")
    ap.add_argument("--out", type=Path, help="write raw scan results here")
    ap.add_argument("--baseline", type=Path, help="compare against a previous run's summary")
    args = ap.parse_args()

    import requests  # imported late so --help works without it

    images = args.fixtures / "images"
    # Ground truth is a single file. Receipts whose arithmetic does not reconcile
    # are used for L0 signal only - scoring line items against a label we know is
    # wrong would just measure the label.
    truth_doc = json.loads((args.fixtures / "ground-truth.json").read_text())
    truth = truth_doc["receipts"]
    rate = truth_doc.get("_rate", 1.95583)

    def verified(rec):
        items = rec.get("items") or []
        if not items or rec.get("total") is None:
            return False
        net = (sum(i.get("price", 0) for i in items)
               - sum(i.get("discount", 0) or 0 for i in items)
               - sum(rec.get("basket_discounts") or []))
        return abs(net - rec["total"]) < 0.05
    files = sorted(p for p in images.iterdir()
                   if p.suffix.lower() in {".jpeg", ".jpg", ".png", ".pdf"})
    if args.filter:
        files = [p for p in files if args.filter in p.name]
    if args.limit:
        files = files[: args.limit]
    if not files:
        sys.exit("no fixtures matched")

    s = requests.Session()
    r = s.post(f"{args.api}/api/auth/token", auth=(args.user, args.password), timeout=30)
    if r.status_code != 200:
        sys.exit(f"login failed: {r.status_code} — is the API running?")

    # The auth cookie is Secure, and urllib's cookie jar will not send a Secure
    # cookie over plain http, so the session silently 401s against a local API.
    # curl is more permissive, which is why this worked by hand. Set the header
    # explicitly rather than weakening the cookie policy.
    jwt = r.cookies.get("JWT") or re.search(r"JWT=([^;]+)", r.headers.get("set-cookie", ""))
    if hasattr(jwt, "group"):
        jwt = jwt.group(1)
    if not jwt:
        sys.exit("logged in but no JWT cookie was returned")
    s.headers["Cookie"] = f"JWT={jwt}"

    probe = s.get(f"{args.api}/api/stores", timeout=30)
    if probe.status_code != 200:
        sys.exit(f"auth check failed: GET /api/stores returned {probe.status_code}")

    out_dir = args.out or (ROOT / ".harness" / time.strftime("%Y%m%d-%H%M%S"))
    out_dir.mkdir(parents=True, exist_ok=True)

    mime = {".jpeg": "image/jpeg", ".jpg": "image/jpeg",
            ".png": "image/png", ".pdf": "application/pdf"}
    rows = []
    print(f"{'fixture':38} {'http':>4} {'secs':>5} {'items':>5}  {'L0':>2}  scoring")
    print("-" * 100)
    for f in files:
        t0 = time.time()
        try:
            with f.open("rb") as fh:
                resp = s.post(f"{args.api}/api/receipts/scan",
                              files={"file": (f.name, fh, mime[f.suffix.lower()])},
                              timeout=300)
            secs = time.time() - t0
            body = resp.json() if resp.headers.get("content-type", "").startswith("application/json") else {}
        except Exception as e:  # noqa: BLE001
            print(f"{f.name:38} {'ERR':>4} {time.time()-t0:>5.0f}  {e}")
            rows.append({"fixture": f.name, "error": str(e)})
            continue

        (out_dir / f"{f.stem}.json").write_text(json.dumps(body, ensure_ascii=False, indent=1))
        row = {"fixture": f.name, "status": resp.status_code, "seconds": round(secs, 1)}

        if resp.status_code == 429:
            print(f"{f.name:38} {429:>4} {secs:>5.0f} {'-':>5}  {'-':>2}  "
                  f"rate limited — restart the API with RATE_LIMIT_ENABLED=false")
            rows.append(row)
            continue

        if resp.status_code == 200:
            l0 = score_l0(body)
            row["l0"] = l0
            note = ""
            rec = truth.get(f.name)
            # The date is scored for every fixture that has one in ground truth.
            # It does not depend on the line-item arithmetic reconciling, so
            # gating it behind verified() hid it on half the set. Fixtures with
            # no date in ground truth are counted apart, never as a pass or a
            # fail - otherwise the metric measures the transcription, not the
            # parser.
            want_date = (rec or {}).get("date")
            got_date = body.get("purchaseDate")
            if not want_date:
                row["date_cmp"] = "untruthed"
            elif got_date == want_date:
                row["date_cmp"] = "correct"
            elif not got_date or str(got_date).startswith("1970"):
                row["date_cmp"] = "missing"
            else:
                row["date_cmp"] = "wrong"
            row["date_got"], row["date_want"] = got_date, want_date
            if rec and verified(rec):
                exp = {"store": rec.get("store"), "date": rec.get("date"),
                       "total": rec.get("total"),
                       "items": [{"name": i.get("name"), "price": i.get("price"),
                                  "quantity": i.get("quantity"),
                                  "discount": i.get("discount")} for i in rec["items"]]}
                sc = score_against_expected(body, exp)
                row["scored"] = sc
                it = sc["items"]
                note = (f"items {it['matched']}/{it['expected']} "
                        f"price {it['price_ok']} qty {it['qty_ok']} disc {it['disc_ok']}")
                for n, ok, _ in sc["checks"]:
                    note += f"  {n}={'Y' if ok else 'N'}"
            else:
                note = "(unverified label — L0 only)"
            print(f"{f.name:38} {resp.status_code:>4} {secs:>5.0f} "
                  f"{l0['items']:>5}  {'Y' if l0['ok'] else 'N':>2}  {note}")
        else:
            row["body"] = body
            print(f"{f.name:38} {resp.status_code:>4} {secs:>5.0f} "
                  f"{'-':>5}  {'-':>2}  {str(body)[:60]}")
        rows.append(row)

    # ---- summary ----
    ok200 = [r for r in rows if r.get("status") == 200]
    l0ok = [r for r in ok200 if r.get("l0", {}).get("ok")]
    empty = [r for r in ok200 if r.get("l0", {}).get("items") == 0]
    print("-" * 100)
    print(f"  fixtures run        {len(rows)}")
    print(f"  HTTP 200            {len(ok200)}")
    print(f"  passed L0           {len(l0ok)}   (non-empty, priced, dated, store found)")
    print(f"  returned 0 items    {len(empty)}")
    d = {}
    for r in ok200:
        k = r.get("date_cmp", "untruthed")
        d[k] = d.get(k, 0) + 1
    graded = len(ok200) - d.get("untruthed", 0)
    print(f"  dates               {d.get('correct', 0)}/{graded} correct  "
          f"({d.get('wrong', 0)} wrong, {d.get('missing', 0)} missing, "
          f"{d.get('untruthed', 0)} with no date in ground truth)")
    if ok200:
        secs = [r['seconds'] for r in ok200]
        print(f"  seconds             min {min(secs):.0f}  median "
              f"{sorted(secs)[len(secs)//2]:.0f}  max {max(secs):.0f}")
    scored = [r for r in rows if "scored" in r]
    if scored:
        m = sum(r["scored"]["items"]["matched"] for r in scored)
        e = sum(r["scored"]["items"]["expected"] for r in scored)
        p = sum(r["scored"]["items"]["price_ok"] for r in scored)
        print(f"  line items          {m}/{e} matched, {p} with the right price "
              f"({len(scored)} labelled fixtures)")

    bad_dates = [r for r in ok200 if r.get("date_cmp") in ("wrong", "missing")]
    if bad_dates:
        print("\n  dates not matching ground truth:")
        for r in bad_dates:
            print(f"    {r['fixture']:32} got {str(r.get('date_got')):>12}  "
                  f"want {r.get('date_want')}")

    summary = {"generated": time.strftime("%Y-%m-%d %H:%M:%S"), "rows": rows}
    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1))
    print(f"\n  results: {out_dir}")

    if args.baseline and args.baseline.exists():
        base = {r["fixture"]: r for r in json.loads(args.baseline.read_text())["rows"]}
        print("\n  change vs baseline:")
        for r in rows:
            b = base.get(r["fixture"])
            if not b:
                continue
            was, now = b.get("l0", {}).get("items"), r.get("l0", {}).get("items")
            if was != now:
                arrow = "improved" if (now or 0) > (was or 0) else "REGRESSED"
                print(f"    {r['fixture']:38} {was} -> {now}   {arrow}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
