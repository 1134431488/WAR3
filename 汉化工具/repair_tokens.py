#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
修复「数据/文件格式令牌被误翻成中文」的事故：
  rms_patch 无差别替换所有命中词典的 UTF8，连 MDL/MDX 文件格式令牌
  （如 Translation/Rotation/Scaling/Color/TextureID、LifeSpan/Alpha/Visibility、
   Tangents/Shader …）也翻了，但这些令牌是程序内部数据键、必须由英文原样保留，
  否则：
    - AnimFlag.generateTypeId() 的 switch 失配 -> 默认 ALPHA ->
      Vec3AnimFlag.getIdentity(ALPHA) 把 Float 当 Vec3 强转 -> ClassCastException；
    - 各类 readMdl 的 switch 失配 -> MDL 导入静默失败。

做法：扫描源码，找出每个 class 里以字符串字面量形式用于 case/equals 的
「帕斯卡命名格式令牌」，凡在词典里被翻成中文的，就在 jar 中把对应中文还原回
该 class 源码实际使用的那个英文（按 class 维度还原，规避一词多义歧义）。
界面文案不受影响（界面句子不是单个帕斯卡令牌，不会被本规则命中）。

用法：python repair_tokens.py <输入.jar> <词典.json> <源码根目录> <输出.jar>
"""
import json
import os
import re
import struct
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import repair_names as R

# 源码里出现的「帕斯卡命名格式令牌」：大小写开头的大写字母串，且用于 case "X" / equals("X") / "X".equals
PAT = re.compile(r'(?:case\s+|equals\()\s*"([A-Z][A-Za-z0-9]+)"|"([A-Z][A-Za-z0-9]+)"\s*\.equals')


def collect_source_tokens(src_root, mapping):
    """返回 {jar内部类名: {中文: 英文}} —— 仅收录「在词典里被翻中文」的帕斯卡令牌。"""
    restore = {}
    for base, _, files in os.walk(src_root):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(base, fn)
            try:
                txt = open(path, encoding="utf-8").read()
            except Exception:
                continue
            toks = set()
            for m in PAT.finditer(txt):
                t = m.group(1) or m.group(2)
                if t and t in mapping and mapping[t]:
                    toks.add(t)
            if toks:
                rel = os.path.relpath(path, src_root).replace(os.sep, "/")[:-5]
                restore[rel] = {mapping[t]: t for t in toks}
    return restore


def repair_class_tokens(data, restore_map):
    """把本 class 中属于数据令牌的中文 UTF8 还原英文。返回新字节或 None。"""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return None
    cp, order = R.parse_cp(data)
    if cp is None or not restore_map:
        return None
    changed = False
    for i in order:
        tag, body, u8 = cp[i]
        if tag == 1 and u8 is not None:
            text = R.mutf8_decode(u8)
            if text in restore_map:
                eng = restore_map[text]
                if eng and eng != text:
                    nb = R.mutf8_encode(eng)
                    if len(nb) <= 65535:
                        cp[i] = (1, b"\x01" + struct.pack(">H", len(nb)) + nb, nb)
                        changed = True
    if not changed:
        return None
    # 重新序列化常量池（引用按索引，不受长度变化影响）
    cp_end = _cp_end(data, struct.unpack_from(">H", data, 8)[0])
    out = bytearray()
    for i in order:
        out += cp[i][1]
    new_data = data[:8] + struct.pack(">H", struct.unpack_from(">H", data, 8)[0]) + bytes(out) + data[cp_end:]
    return new_data


def _cp_end(data, cp_count):
    pos = 10
    idx = 1
    while idx < cp_count:
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack_from(">H", data, pos + 1)[0]; pos += 3 + ln; idx += 1
        elif tag in R.FIXED_SIZE:
            s = R.FIXED_SIZE[tag]; pos += s; idx += 2 if tag in R.WIDE else 1
        else:
            break
    return pos


def main():
    if len(sys.argv) < 5:
        print("用法: python repair_tokens.py <输入.jar> <词典.json> <源码根> <输出.jar>")
        sys.exit(1)
    in_jar, dict_path, src_root, out_jar = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    mapping = json.load(open(dict_path, encoding="utf-8"))
    restore = collect_source_tokens(src_root, mapping)
    # 只还原「数据/文件格式令牌」类：解析器包、AnimFlag、模型解析辅助类。
    # 这些类的令牌必须与外部英文来源（MDL 文件 / MdlUtils 常量 / AnimationMap）一致。
    # 跳过 UI 编辑器类（KeyframePanel 等）——它们的令牌同时用作界面标签与 switch 键，
    # 已一致地翻成中文，改动反而会破坏；也跳过已在 排除类.txt 中的类（本身就是英文）。
    SAFE_PREFIX = (
        "com/hiveworkshop/rms/parsers/mdlx",
        "com/hiveworkshop/rms/editor/model/animflag/AnimFlag",
        "com/hiveworkshop/rms/editor/model/util/TwiMdlxParser",
        "com/hiveworkshop/rms/editor/model/util/ModelFactory/TempOpenModelStuff",
        "com/hiveworkshop/rms/util/TestTimeline/KeyFrame",
    )
    restore = {c: m for c, m in restore.items() if any(c.startswith(p) for p in SAFE_PREFIX)}
    print("  需还原数据令牌的 class 数: %d" % len(restore))
    for c, m in restore.items():
        print("    %s: %s" % (c, {k: v for k, v in m.items()}))

    zin = zipfile.ZipFile(in_jar)
    total = 0
    out_items = []
    for info in zin.infolist():
        data = zin.read(info.filename)
        fixed = None
        if info.filename.endswith(".class") and info.filename[:-6] in restore:
            try:
                fixed = repair_class_tokens(data, restore[info.filename[:-6]])
            except Exception as ex:
                print("  跳过 %s: %s" % (info.filename, ex))
        if fixed is not None:
            total += 1
            data = fixed
        out_items.append((info, data))
    zin.close()

    with zipfile.ZipFile(out_jar, "w") as zout:
        for info, data in out_items:
            ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.internal_attr = info.internal_attr
            ni.create_system = info.create_system
            ni.comment = info.comment
            zout.writestr(ni, data)
    print("已还原 %d 个 class 的数据令牌 -> %s" % (total, out_jar))


if __name__ == "__main__":
    main()
