#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""修复 NoSuchFieldError: 透明
ComponentHDLayer / ComponentSDLayer / TextureThing$1 引用 FilterMode.'透明'，
但 FilterMode 枚举声明为 TRANSPARENT（汉化改名不一致所致）。
本脚本把这三个类里的该 utf8 常量改回 TRANSPARENT 并重写主 jar（先备份）。"""
import os
import shutil
import struct
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "汉化版", "RMS_zh_CN.jar")
BACKUP = os.path.join(ROOT, "汉化版", "备份", "RMS_zh_CN.jar.before-fix-transparent.bak")

TARGETS = [
    "com/hiveworkshop/rms/ui/application/model/material/ComponentHDLayer.class",
    "com/hiveworkshop/rms/ui/application/model/material/ComponentSDLayer.class",
    "com/hiveworkshop/rms/ui/application/viewer/TextureThing$1.class",
]
OLD = "透明".encode("utf-8")
NEW = b"TRANSPARENT"


def patch_class(data):
    """把常量池中唯一的 '透明' utf8 常量改写为 TRANSPARENT，返回新 class 字节。"""
    assert data[:4] == b"\xca\xfe\xba\xbe", "not a class"
    idx = 8
    count = struct.unpack_from(">H", data, idx)[0]
    idx += 2
    cp_start = idx
    pos = idx
    hits = []          # (entry_start, entry_end, payload_start)
    i = 1
    while i < count:
        tag = data[pos]
        entry_start = pos
        if tag == 1:
            ln = struct.unpack_from(">H", data, pos + 1)[0]
            payload = data[pos + 3: pos + 3 + ln]
            if payload == OLD:
                hits.append((entry_start, pos + 3 + ln, pos + 3))
            pos += 3 + ln
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            pos += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag == 15:
            pos += 4
        else:
            raise ValueError("unknown tag %d" % tag)
        i += 1
    cp_end = pos
    if len(hits) != 1:
        raise SystemExit("%s 含 %d 处 '透明'（预期 1），放弃" % (cn_repr(data), len(hits)))

    s, e, p = hits[0]
    out = bytearray()
    out += data[:s]
    out += b"\x01"               # CONSTANT_Utf8 的 tag 字节，绝不能漏
    out += struct.pack(">H", len(NEW))
    out += NEW
    out += data[e:]
    return bytes(out)


def cn_repr(data):
    return data[:8].hex()


def main():
    os.makedirs(os.path.dirname(BACKUP), exist_ok=True)
    if not os.path.exists(BACKUP):
        shutil.copyfile(JAR, BACKUP)
        print("已备份 ->", BACKUP)

    patched = {}
    zin = zipfile.ZipFile(JAR)
    for t in TARGETS:
        new = patch_class(zin.read(t))
        patched[t] = new
        print("已修补:", t, "(%d -> %d bytes)" % (len(zin.read(t)), len(new)))

    tmp = JAR + ".tmp"
    with zipfile.ZipFile(tmp, "w") as zout:
        for info in zin.infolist():
            data = patched.get(info.filename) or zin.read(info.filename)
            ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.internal_attr = info.internal_attr
            ni.create_system = info.create_system
            ni.comment = info.comment
            zout.writestr(ni, data)
    zin.close()
    os.replace(tmp, JAR)
    print("主 jar 已更新。")


if __name__ == "__main__":
    main()
