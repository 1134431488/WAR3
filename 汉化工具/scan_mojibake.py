#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""找出所有 class 中「双重编码」的 UTF8 常量：本应是 CJK 文本，却被存成
Latin-1 字节形态（如 Í¸Ã÷ 应为 透明）。输出每处所在类与修正映射。"""
import struct, sys, zipfile, collections

JAR = sys.argv[1] if len(sys.argv) > 1 else "汉化版/RMS_zh_CN.jar"

def utf8_consts(data):
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

def is_mojibake(s):
    """Latin-1 形态的双编码 CJK：含 Ã/Î 等高位字符，且按 latin-1 字节重组后能解出 CJK"""
    try:
        b = s.encode('latin-1')
        fixed = b.decode('utf-8')
    except Exception:
        return None
    if fixed == s:
        return None
    has_cjk = any('\u4e00' <= ch <= '\u9fff' for ch in fixed)
    has_high = any(ord(ch) > 0x7f for ch in s)
    return fixed if (has_cjk and has_high) else None

mapping = collections.Counter()
examples = {}
z = zipfile.ZipFile(JAR)
for info in z.infolist():
    if not info.filename.endswith('.class'):
        continue
    for b in utf8_consts(z.read(info.filename)):
        try:
            s = b.decode('utf-8')
        except Exception:
            continue
        fixed = is_mojibake(s)
        if fixed:
            mapping[s] += 1
            examples.setdefault(s, (fixed, info.filename))

print("发现 %d 种双编码乱码：" % len(mapping))
for s, n in mapping.most_common():
    fixed, ex = examples[s]
    print("  %-16r x%d -> %-8s 例: %s" % (s, n, fixed, ex))
