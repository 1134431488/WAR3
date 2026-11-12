#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Retera Model Studio 汉化补丁工具

原理：Java class 文件的常量池是一张索引表，界面文本以 CONSTANT_Utf8 常量存放。
把命中的 UTF8 常量替换成中文，其余字节（包括方法字节码、属性表）原样保留，
因此不需要 JDK、不需要 Gradle、不需要重新编译，也不会破坏程序逻辑。

用法：
    python rms_patch.py extract <输入.jar> <输出词条.json> [包前缀 ...]
    python rms_patch.py apply   <输入.jar> <汉化词典.json> <输出.jar> [包前缀 ...]
    python rms_patch.py verify  <汉化后.jar> <汉化词典.json>

包前缀用于限定只处理主程序自身的类，避免误改第三方库。
默认前缀：com/hiveworkshop  com/matrixeater  com/etheller  com/rmsh  rms
"""

import json
import os
import struct
import sys
import zipfile
from collections import Counter

DEFAULT_PREFIXES = (
    "com/hiveworkshop",
    "com/matrixeater",
    "com/etheller",
    "com/rmsh",
    "rms/",
)

# CONSTANT pool tag -> 固定长度（含 1 字节 tag）
FIXED_SIZE = {
    3: 5,   # Integer
    4: 5,   # Float
    5: 9,   # Long   (占两个索引)
    6: 9,   # Double (占两个索引)
    7: 3,   # Class
    8: 3,   # String
    9: 5,   # Fieldref
    10: 5,  # Methodref
    11: 5,  # InterfaceMethodref
    12: 5,  # NameAndType
    15: 4,  # MethodHandle
    16: 3,  # MethodType
    17: 5,  # Dynamic
    18: 5,  # InvokeDynamic
    19: 3,  # Module
    20: 3,  # Package
}
WIDE_TAGS = (5, 6)


class BadClassFile(Exception):
    pass


def parse_constant_pool(data):
    """返回 (常量池结束偏移, cp_count, entries)。
    entries 元素为 (tag, 原始字节, utf8_bytes 或 None)。"""
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise BadClassFile("不是有效的 class 文件")
    cp_count = struct.unpack_from(">H", data, 8)[0]
    pos = 10
    entries = []
    idx = 1
    while idx < cp_count:
        if pos >= len(data):
            raise BadClassFile("常量池被截断")
        tag = data[pos]
        if tag == 1:  # CONSTANT_Utf8
            if pos + 3 > len(data):
                raise BadClassFile("Utf8 长度字段被截断")
            length = struct.unpack_from(">H", data, pos + 1)[0]
            end = pos + 3 + length
            if end > len(data):
                raise BadClassFile("Utf8 内容被截断")
            entries.append((tag, data[pos:end], data[pos + 3:end]))
            pos = end
            idx += 1
        elif tag in FIXED_SIZE:
            size = FIXED_SIZE[tag]
            if pos + size > len(data):
                raise BadClassFile("常量项被截断")
            entries.append((tag, data[pos:pos + size], None))
            pos += size
            idx += 2 if tag in WIDE_TAGS else 1
        else:
            raise BadClassFile("未知常量池 tag: %d @ %d" % (tag, pos))
    return pos, cp_count, entries


def mutf8_decode(raw):
    """解码 Java class 的 modified UTF-8。
    差异点：U+0000 编码为 C0 80；补充平面字符按 UTF-16 代理对各编码 3 字节。
    其余 BMP 字符（含中文）与标准 UTF-8 一致。"""
    out = []
    i, n = 0, len(raw)
    while i < n:
        b = raw[i]
        if b < 0x80:
            out.append(chr(b)); i += 1
        elif (b & 0xE0) == 0xC0 and i + 1 < n:
            out.append(chr(((b & 0x1F) << 6) | (raw[i + 1] & 0x3F))); i += 2
        elif (b & 0xF0) == 0xE0 and i + 2 < n:
            out.append(chr(((b & 0x0F) << 12) | ((raw[i + 1] & 0x3F) << 6) | (raw[i + 2] & 0x3F))); i += 3
        else:
            out.append("\ufffd"); i += 1
    return "".join(out)


def mutf8_encode(text):
    out = bytearray()
    for ch in text:
        c = ord(ch)
        if 0x0001 <= c <= 0x007F:
            out.append(c)
        elif c <= 0x07FF:  # U+0000 与 U+0080..U+07FF
            out.append(0xC0 | (c >> 6))
            out.append(0x80 | (c & 0x3F))
        elif c <= 0xFFFF:  # BMP，含中文
            out.append(0xE0 | (c >> 12))
            out.append(0x80 | ((c >> 6) & 0x3F))
            out.append(0x80 | (c & 0x3F))
        else:  # 补充平面：拆成 UTF-16 代理对，各编码 3 字节
            v = c - 0x10000
            for half in (0xD800 + (v >> 10), 0xDC00 + (v & 0x3FF)):
                out.append(0xE0 | (half >> 12))
                out.append(0x80 | ((half >> 6) & 0x3F))
                out.append(0x80 | (half & 0x3F))
    return bytes(out)


def utf8_to_str(raw):
    return mutf8_decode(raw)


def rebuild_class(data, mapping):
    """按 mapping 替换 UTF8 常量，返回 (新字节, 命中数)。未改变返回 (None, 0)。"""
    cp_end, _, entries = parse_constant_pool(data)
    out = bytearray()
    hits = 0
    changed = False
    for tag, raw, u8 in entries:
        if tag == 1 and u8 is not None:
            text = utf8_to_str(u8)
            if text in mapping:
                new_text = mapping[text]
                if new_text and new_text != text:
                    new_bytes = mutf8_encode(new_text)
                    if len(new_bytes) > 65535:
                        continue  # CONSTANT_Utf8 长度上限，跳过超长项
                    out += b"\x01" + struct.pack(">H", len(new_bytes)) + new_bytes
                    hits += 1
                    changed = True
                    continue
        out += raw
    if not changed:
        return None, 0
    return data[:8] + struct.pack(">H", struct.unpack_from(">H", data, 8)[0]) + bytes(out) + data[cp_end:], hits


EXCLUDE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "排除类.txt")


def load_excludes():
    """读取需要整体跳过汉化的 class 前缀。
    这些类把同一个字符串同时当作显示文本和程序内部键（类名 switch、
    枚举名比较、模型数据名等），改写会破坏逻辑，因此保留原英文。"""
    if not os.path.exists(EXCLUDE_FILE):
        return ()
    with open(EXCLUDE_FILE, encoding="utf-8") as f:
        return tuple(
            line.strip().replace("\\", "/")
            for line in f
            if line.strip() and not line.lstrip().startswith("#")
        )


def is_target(name, prefixes, excludes=()):
    if not name.endswith(".class"):
        return False
    if any(name.startswith(e) for e in excludes):
        return False
    return any(name.startswith(p) for p in prefixes)


def load_jar(path):
    zf = zipfile.ZipFile(path)
    items = []
    for info in zf.infolist():
        # 丢弃签名文件（内容已改，原签名会失效）
        n = info.filename.upper()
        if n.startswith("META-INF/") and n.endswith((".SF", ".RSA", ".DSA", ".EC")):
            continue
        items.append((info, zf.read(info.filename)))
    zf.close()
    return items


def cmd_extract(jar_path, out_path, prefixes):
    items = load_jar(jar_path)
    excludes = load_excludes()
    counter = Counter()
    class_count = 0
    bad = 0
    for info, data in items:
        if not is_target(info.filename, prefixes, excludes):
            continue
        try:
            _, _, entries = parse_constant_pool(data)
        except BadClassFile:
            bad += 1
            continue
        class_count += 1
        for tag, _, u8 in entries:
            if tag == 1 and u8 is not None:
                text = utf8_to_str(u8)
                # 跳过代理项/控制字符，避免写入 JSON 时报错
                if text and not any(0xD800 <= ord(c) <= 0xDFFF or (ord(c) < 0x20 and c not in "\n\r\t") for c in text):
                    counter[text] += 1
    ordered = sorted(counter.items(), key=lambda kv: (-kv[1], kv[0].lower()))
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump([{"en": s, "n": c} for s, c in ordered], f, ensure_ascii=False, indent=1)
    print("已提取 %d 个 class（跳过 %d 个异常），共 %d 条不同 UTF8 常量 -> %s"
          % (class_count, bad, len(counter), out_path))


def cmd_apply(jar_path, dict_path, out_path, prefixes):
    with open(dict_path, encoding="utf-8") as f:
        raw = json.load(f)
    if isinstance(raw, dict):
        mapping = raw
    elif isinstance(raw, list):
        mapping = {}
        for item in raw:
            if isinstance(item, dict):
                en, zh = item.get("en"), item.get("zh")
                if en and zh:
                    mapping[en] = zh
    else:
        raise SystemExit("词典格式错误：应为 {\"英文\":\"中文\"} 或 [{\"en\":..,\"zh\":..}]")

    items = load_jar(jar_path)
    excludes = load_excludes()
    total_hits = 0
    patched_classes = 0
    out_items = []
    for info, data in items:
        if is_target(info.filename, prefixes, excludes):
            try:
                new_data, hits = rebuild_class(data, mapping)
            except BadClassFile as e:
                print("  跳过 %s: %s" % (info.filename, e))
                new_data, hits = None, 0
            if new_data is not None:
                patched_classes += 1
                total_hits += hits
                data = new_data
        out_items.append((info, data))

    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or ".", exist_ok=True)
    with zipfile.ZipFile(out_path, "w") as zout:
        for info, data in out_items:
            new_info = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            new_info.compress_type = info.compress_type
            new_info.external_attr = info.external_attr
            new_info.internal_attr = info.internal_attr
            new_info.create_system = info.create_system
            new_info.comment = info.comment
            zout.writestr(new_info, data)
    print("已改写 %d 个 class，共替换 %d 处字符串 -> %s" % (patched_classes, total_hits, out_path))
    print("词典条目 %d 条；输出大小 %.2f MB" % (len(mapping), os.path.getsize(out_path) / 1048576))
    # 报告词典中未命中的条目，便于精简
    print("（提示）未命中统计请在 verify 子命令中查看")


def cmd_verify(jar_path, dict_path, prefixes):
    with open(dict_path, encoding="utf-8") as f:
        raw = json.load(f)
    mapping = raw if isinstance(raw, dict) else {i["en"]: i["zh"] for i in raw if i.get("en") and i.get("zh")}
    items = load_jar(jar_path)
    excludes = load_excludes()
    counter = Counter()
    for info, data in items:
        if not is_target(info.filename, prefixes, excludes):
            continue
        try:
            _, _, entries = parse_constant_pool(data)
        except BadClassFile:
            continue
        for tag, _, u8 in entries:
            if tag == 1 and u8 is not None:
                text = utf8_to_str(u8)
                if text and not any(0xD800 <= ord(c) <= 0xDFFF for c in text):
                    counter[text] += 1
    zh_hit = sum(1 for zh in mapping.values() if zh in counter)
    en_left = [en for en in mapping if en in counter]
    print("词典 %d 条；汉化后 jar 中已出现中文译文 %d 条" % (len(mapping), zh_hit))
    print("仍存在原英文词条 %d 条（可能被同一 class 的其他位置复用，或属于第三方库）" % len(en_left))
    for s in en_left[:30]:
        print("   ", repr(s))


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    cmd = sys.argv[1]
    if cmd == "extract":
        prefixes = tuple(sys.argv[4:]) if len(sys.argv) > 4 else DEFAULT_PREFIXES
    elif cmd == "apply":
        prefixes = tuple(sys.argv[5:]) if len(sys.argv) > 5 else DEFAULT_PREFIXES
    elif cmd == "verify":
        prefixes = tuple(sys.argv[4:]) if len(sys.argv) > 4 else DEFAULT_PREFIXES
    else:
        prefixes = DEFAULT_PREFIXES

    if cmd == "extract":
        cmd_extract(sys.argv[2], sys.argv[3], prefixes)
    elif cmd == "apply":
        cmd_apply(sys.argv[2], sys.argv[3], sys.argv[4], prefixes)
    elif cmd == "verify":
        cmd_verify(sys.argv[2], sys.argv[3], prefixes)
    else:
        print(__doc__)
        sys.exit(1)


if __name__ == "__main__":
    main()
