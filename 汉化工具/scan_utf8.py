#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""列出常量池 UTF8 常量等于目标串（UTF-8/GBK/Latin-1 字节形态）的所有 class。"""
import struct, sys, zipfile

TARGET = sys.argv[1] if len(sys.argv) > 1 else "透明"
JAR = sys.argv[2] if len(sys.argv) > 2 else "汉化版/RMS_zh_CN.jar"

patterns = set()
patterns.add(TARGET.encode('utf-8'))
patterns.add(TARGET.encode('gbk', 'ignore'))
try:
    patterns.add(TARGET.encode('utf-8').decode('latin-1').encode('latin-1'))
except Exception:
    pass

def utf8_consts(data):
    """粗扫：按常量池结构走一遍，返回所有 utf8 字节串。"""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return []
    idx = 8
    count = struct.unpack_from('>H', data, idx)[0]
    idx += 2
    out = []
    i = 1
    try:
        while i < count:
            tag = data[idx]
            if tag == 1:
                ln = struct.unpack_from('>H', data, idx+1)[0]
                out.append(data[idx+3:idx+3+ln])
                idx += 3 + ln
            elif tag in (3, 4):
                idx += 5
            elif tag in (5, 6):
                idx += 9; i += 1
            elif tag in (7, 8, 16, 19, 20):
                idx += 3
            elif tag in (9, 10, 11, 12, 17, 18):
                idx += 5
            elif tag == 15:
                idx += 4
            else:
                return out
            i += 1
    except Exception:
        pass
    return out

z = zipfile.ZipFile(JAR)
hits = {}
for info in z.infolist():
    if not info.filename.endswith('.class'):
        continue
    for b in utf8_consts(z.read(info.filename)):
        if b in patterns:
            hits.setdefault(info.filename, []).append(b.decode('gbk', 'replace'))
for f in sorted(hits):
    print(f, hits[f][:3])
print("共", len(hits), "个类")
