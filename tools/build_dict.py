#!/usr/bin/env python3
"""ECDICT 离线词典裁剪生成器：从 ecdict.csv 生成 app/src/main/assets/dict.sqlite。
用法: python3 tools/build_dict.py <ecdict.csv> <output.sqlite>"""
import csv, os, re, sqlite3, sys

SRC, OUT = sys.argv[1], sys.argv[2]
os.makedirs(os.path.dirname(OUT), exist_ok=True)

pat = re.compile(r"^[A-Za-z][A-Za-z' -]*$")
rows = []
with open(SRC, encoding='utf-8') as f:
    for r in csv.DictReader(f):
        w = (r['word'] or '').strip()
        if not pat.match(w):
            continue
        trans = (r['translation'] or '').strip()
        if not trans:
            continue
        frq = int(r['frq'] or 0)
        bnc = int(r['bnc'] or 0)
        tag = (r['tag'] or '').strip()
        if frq <= 0 and (bnc <= 0 or bnc > 40000) and not tag:
            continue
        rows.append((w, (r['phonetic'] or '').strip(), trans, (r['pos'] or '').strip(),
                     tag, (r['exchange'] or '').strip(), bnc, frq))

def score(x):
    w, _, _, _, _, _, bnc, frq = x
    return (frq if frq > 0 else (100000 - bnc if bnc > 0 else -1), -bnc)

rows.sort(key=score, reverse=True)
rows = rows[:60000]

if os.path.exists(OUT):
    os.remove(OUT)
conn = sqlite3.connect(OUT)
conn.execute('CREATE TABLE dict (word TEXT PRIMARY KEY, phonetic TEXT, translation TEXT, pos TEXT, tag TEXT, exchange TEXT, bnc INTEGER, frq INTEGER)')
conn.executemany('INSERT OR IGNORE INTO dict VALUES (?,?,?,?,?,?,?,?)', rows)
conn.commit()
conn.execute('CREATE INDEX idx_dict_word ON dict(word)')
conn.commit()
conn.close()
print('dict rows:', len(rows), '| size:', os.path.getsize(OUT))
