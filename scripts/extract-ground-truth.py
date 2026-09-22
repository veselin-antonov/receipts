#!/usr/bin/env python3
"""Extract ground truth from every fixture, for auditing in one pass.

Runs entirely locally: no API, no LLM, no cost. Crops each receipt, applies the
preprocessing that measured best (crop then Otsu), OCRs with Tesseract, parses
the Bulgarian receipt structure, and validates each result against the
receipt's own printed totals.

The arithmetic is what makes this auditable rather than trusted: a transcription
whose line totals minus discounts equal the printed total, and whose lev total
divided by 1.95583 equals the printed euro total, is almost certainly right.
Anything that fails those checks is flagged for a human rather than guessed at.

Outputs expected/<fixture>.json plus a summary table.
"""
from __future__ import annotations
import io, json, os, re, subprocess, sys, unicodedata
from pathlib import Path

FIX = Path(os.environ.get('FIXTURES', '/fixtures'))
OUT = FIX / 'expected'
RATE = 1.95583

# ----------------------------------------------------------------- imaging --
def otsu_threshold(a):
    import numpy as np
    hist = np.bincount(a.ravel(), minlength=256).astype(float)
    tot = a.size; sa = (np.arange(256) * hist).sum(); sB = wB = 0.0; best = (-1, 0)
    for t in range(256):
        wB += hist[t]
        if wB == 0: continue
        wF = tot - wB
        if wF == 0: break
        sB += t * hist[t]; mB = sB / wB; mF = (sa - sB) / wF
        v = wB * wF * (mB - mF) ** 2
        if v > best[0]: best = (v, t)
    return best[1]

def receipt_bbox(gray):
    """Largest bright region: the paper against the mat."""
    import numpy as np
    small = gray.copy(); small.thumbnail((500, 500))
    a = np.array(small)
    hi = np.percentile(a, 92)
    mask = a > max(140, hi - 45)
    rows = np.where(mask.sum(axis=1) > 0.06 * mask.shape[1])[0]
    cols = np.where(mask.sum(axis=0) > 0.06 * mask.shape[0])[0]
    if not len(rows) or not len(cols): return None
    sc = gray.size[0] / small.size[0]
    return (int(cols[0]*sc), int(rows[0]*sc), int(cols[-1]*sc), int(rows[-1]*sc))

def ocr_image(path):
    from PIL import Image, ImageOps, ImageFilter
    import numpy as np
    im = ImageOps.exif_transpose(Image.open(path)).convert('L')
    box = receipt_bbox(im)
    if box:
        x0, y0, x1, y1 = box; W, H = im.size
        im = im.crop((max(0,x0-20), max(0,y0-20), min(W,x1+20), min(H,y1+20)))
    sharp = im.filter(ImageFilter.Kernel((3,3), [0,-1,0,-1,5,-1,0,-1,0], scale=1))
    a = np.array(sharp)
    binar = Image.fromarray(((a > otsu_threshold(a)) * 255).astype('uint8'))
    tmp = '/tmp/_ocr.png'; binar.save(tmp)
    return subprocess.run(['tesseract', tmp, '-', '-l', 'bul+eng', '--psm', '6'],
                          capture_output=True, text=True).stdout

def text_of(path: Path) -> tuple[str, str]:
    """Returns (text, how). PDFs with a text layer skip OCR entirely."""
    if path.suffix.lower() == '.pdf':
        from pypdf import PdfReader
        r = PdfReader(str(path))
        t = ''.join((p.extract_text() or '') for p in r.pages)
        if len(t) > 200:
            return t, 'pdf-text'
        from PIL import Image
        img = Image.open(io.BytesIO(r.pages[0].images[0].data))
        img.save('/tmp/_pdf.png')
        return ocr_image('/tmp/_pdf.png'), 'pdf-ocr'
    if path.suffix.lower() == '.png':          # screenshots: already clean
        return subprocess.run(['tesseract', str(path), '-', '-l', 'bul+eng', '--psm', '6'],
                              capture_output=True, text=True).stdout, 'png-direct'
    return ocr_image(path), 'ocr-cropped'

# ------------------------------------------------------------------ parsing --
# Two receipt formats appear in the fixtures and both must parse:
#   printed till roll — "ОБЩА СУМА ЛВ  82.32", dot decimals, quantity lines
#   Kaufland app PDF  — "Сума  4,83", comma decimals, "Цена EUR" header
AMT = r'(\d+[.,]\d{2})'
# OCR frequently merges the trailing currency letter into the number, so
# "2.99 Б" is read as "2.998". Allow a few characters of trailing noise, digits
# included, rather than losing the whole line.
TAIL = r'(?:\d|[^\d\n]){0,5}$'

STOP = re.compile(
    r'(ОБЩА\s*СУМА|МЕЖДИННА|^\s*Сума\b|КУРС|КРЕДИТНА|ДЕБИТНА|КАРТА|БОРИКА|РЕСТО|'
    r'ДДС|VAT|Брутно|Нетно|Kaufland\s*Card|ПЛАТЕНО|БАНКА|ПОКУПКА|АРТИКУЛ|ФИСКАЛЕН|'
    r'БЛАГОДАРИМ|THANK|УНП|ЕИК|ЗДДС|БУЛСТАТ|Оператор|Касиер|Цена\s*EUR|Евро|'
    r'^\s*Б\s*=|Receipt\s*Copy|points for|You saved|SIGNATURE|RETAIN|МОЛЯ|Z-отчет|'
    r'Транзакция|Магазин|МАГАЗИН|ЗАПАЗЕТЕ|Приложение|приложението)', re.IGNORECASE)
DISCOUNT = re.compile(r'[О0O]ТСТ[ЪA-Za-z]?[ПA-Za-z]?К', re.IGNORECASE)

def f(s):
    return float(s.replace(',', '.').replace(' ', ''))

def clean_line(l: str) -> str:
    """Strip the frame junk OCR puts around receipt lines.

    Shadows at the paper's edge and the mat behind it are read as pipes, colons
    and similar, so real lines arrive as "| 2.000 x 1.89 ;". Any pattern
    anchored at the start of a line then fails, and quantity lines in
    particular fall through to item matching, where the unit price is counted
    as a line item. That single effect accounted for most of the inflated
    totals across the fixture set.
    """
    return re.sub(r'^[^\w\d]+', '', l).rstrip()   # leading junk only

def parse(text: str) -> dict:
    lines = [clean_line(l) for l in text.splitlines()]
    joined = '\n'.join(lines)
    out: dict = {'items': [], 'warnings': []}

    for l in lines[:8]:
        if len(l.strip()) > 8 and not re.match(r'^[\d\s#*=|.,-]+$', l):
            out['store_raw'] = l.strip(); break

    def grab(*pats):
        vals = []
        for pat in pats:
            for m in re.finditer(pat, joined, re.IGNORECASE):
                try: vals.append(f(m.group(1)))
                except Exception: pass
        return vals[0] if vals else None

    # "ОБЩА СУМА" beats a bare "Сума"; the app PDFs only have the latter
    out['total'] = grab(r'ОБЩА\s*СУМА(?!\s*В\s*ЕВРО)[^\d\n]{0,18}' + AMT,
                        r'^\s*Сума[^\d\n]{0,24}' + AMT,
                        r'СУМА\s*/\s*AMT[^\d\n]{0,18}' + AMT,
                        r'МЕЖДИННА\s*СУМА[^\d\n]{0,18}' + AMT)
    out['total_eur'] = grab(r'ОБЩА\s*СУМА\s*В\s*ЕВРО[^\d\n]{0,18}' + AMT,
                            r'СУМА\s*В\s*ЕВРО[^\d\n]{0,18}' + AMT)
    m = re.search(r'1\s*(?:ЕВРО|EUR)\s*=\s*(\d+[.,]\d+)', joined, re.IGNORECASE)
    out['rate'] = f(m.group(1)) if m else None
    # which column the prices are in
    if re.search(r'Цена\s*EUR|^\s*Евро\s*$|СУМА\s*/\s*AMT.*EUR', joined, re.I | re.M):
        out['currency'] = 'EUR'
    elif re.search(r'ОБЩА\s*СУМА\s*ЛВ|СУМА\s*ЛВ', joined, re.I):
        out['currency'] = 'BGN'
    else:
        out['currency'] = None

    for pat in (r'(\d{2}[./]\d{2}[./]\d{4})', r'(\d{2}[./]\d{2}[./]\d{2})\b'):
        m = re.search(pat, joined)
        if m:
            d = m.group(1).replace('/', '.'); dd, mm, yy = d.split('.')
            yy = ('20' + yy) if len(yy) == 2 else yy
            out['date'] = f'{yy}-{mm}-{dd}'; break

    pend = None
    for l in lines:
        # OCR leaves stray characters at line ends ("2.000 х 1.89 |"), so a
        # quantity line must tolerate trailing noise. Without this it falls
        # through to item matching and the unit price is counted as a line
        # item, inflating every total.
        mq = re.match(r'\s*(\d+[.,]\d{1,3})\s*[xхX]\s*(\d+[.,]\d{2})[^\d\n]{0,4}$', l)
        if mq:
            pend = (f(mq.group(1)), f(mq.group(2))); continue
        if DISCOUNT.search(l):
            md = re.search(AMT, l)
            if md and out['items']:
                out['items'][-1]['discount'] = abs(f(md.group(1)))
            continue
        if STOP.search(l):
            pend = None; continue
        ml = re.match(r'\s*(.*?[А-Яа-яA-Za-z].*?)\s+' + AMT + TAIL, l)
        if ml and len(ml.group(1).strip()) >= 3:
            it = {'name': ml.group(1).strip(), 'price': f(ml.group(2)), 'discount': 0.0}
            if pend:
                it['quantity'], it['unit_price'] = pend
                it['unit'] = 'KILOGRAM' if pend[0] % 1 else 'PIECE'
            out['items'].append(it); pend = None
    return out

def validate(p: dict) -> dict:
    gross = round(sum(i['price'] for i in p['items']), 2)
    disc = round(sum(i.get('discount', 0) for i in p['items']), 2)
    net = round(gross - disc, 2)
    v = {'gross': gross, 'discounts': disc, 'net': net, 'checks': {}}
    if p.get('total') is not None:
        v['checks']['reconciles'] = abs(net - p['total']) < 0.05
    if p.get('total') and p.get('total_eur'):
        v['checks']['currency_pair'] = abs(p['total'] / RATE - p['total_eur']) < 0.03
    rows = [i for i in p['items'] if 'quantity' in i and 'unit_price' in i]
    if rows:
        v['checks']['rows_multiply'] = all(
            abs(i['quantity'] * i['unit_price'] - i['price']) < 0.03 for i in rows)
    v['confidence'] = ('high' if v['checks'].get('reconciles') else
                       'medium' if p.get('total') and p['items'] else 'low')
    return v

# --------------------------------------------------------------------- main --
def main():
    OUT.mkdir(exist_ok=True)
    files = sorted(p for p in (FIX/'images').iterdir()
                   if p.suffix.lower() in {'.jpeg','.jpg','.png','.pdf'})
    if len(sys.argv) > 1:
        files = [p for p in files if sys.argv[1] in p.name]
    rows = []
    print(f"{'fixture':34} {'via':11} {'items':>5} {'total':>8} {'net':>8} {'conf':>7}  checks")
    print('-'*104)
    for p in files:
        try:
            text, how = text_of(p)
            parsed = parse(text)
            v = validate(parsed)
        except Exception as e:                                   # noqa: BLE001
            print(f'{p.name:34} ERROR {e}'); continue
        rec = {'fixture': p.name, 'extracted_via': how, **parsed, 'validation': v}
        (OUT/f'{p.stem}.json').write_text(json.dumps(rec, ensure_ascii=False, indent=1))
        (Path('/tmp/ocr')/f'{p.stem}.txt').write_text(text) if Path('/tmp/ocr').exists() else None
        chk = ' '.join(f'{k}={"Y" if x else "N"}' for k, x in v['checks'].items())
        print(f"{p.name:34} {how:11} {len(parsed['items']):>5} "
              f"{parsed.get('total') or 0:>8.2f} {v['net']:>8.2f} {v['confidence']:>7}  {chk}")
        rows.append(rec)
    hi = [r for r in rows if r['validation']['confidence']=='high']
    print('-'*104)
    print(f"  {len(rows)} fixtures · {len(hi)} high confidence (arithmetic reconciles) "
          f"· {len(rows)-len(hi)} need review")
    json.dump(rows, open(FIX/'ground-truth-summary.json','w'), ensure_ascii=False, indent=1)

if __name__ == '__main__':
    main()
