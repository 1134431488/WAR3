#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
就地修复：把因词典注入被误改成中文的「字段名 / 方法名 / 声明名」UTF8 还原回英文，
但保留字符串字面量（界面文案）的中文不变。

两类破坏都必须修：
  (a) 只改了引用名，声明仍是英文 -> 仅还原引用名即可。
  (b) 引用名与声明名都被改成同一中文 -> 两者都要还原，否则运行时 NoSuchMethodError。

英文名来源（按可靠性排序）：
  1. 引用名：用「owner 类的声明成员」按 (kind, descriptor) 对照，再与词典逆映射取交集
     —— 不依赖常量池索引对齐，最稳。
  2. 声明名 / 兜底：底包同 (kind, order) 对照、同常量池索引对照（需底包结构一致），
     词典逆映射唯一，以及枚举/字段/方法的大小写消歧。

用法：
    python repair_names.py <当前.jar> <底包.jar> <汉化词典.json> <输出.jar>
"""
import json
import os
import struct
import sys
import zipfile

FIXED_SIZE = {
    3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 9: 5, 10: 5, 11: 5, 12: 5,
    15: 4, 16: 3, 17: 5, 18: 5, 19: 3, 20: 3,
}
WIDE = (5, 6)
ACC_ENUM = 0x4000


def mutf8_decode(raw):
    out, i, n = [], 0, len(raw)
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
        elif c <= 0x07FF:
            out.append(0xC0 | (c >> 6)); out.append(0x80 | (c & 0x3F))
        elif c <= 0xFFFF:
            out.append(0xE0 | (c >> 12)); out.append(0x80 | ((c >> 6) & 0x3F)); out.append(0x80 | (c & 0x3F))
        else:
            v = c - 0x10000
            for half in (0xD800 + (v >> 10), 0xDC00 + (v & 0x3FF)):
                out.append(0xE0 | (half >> 12)); out.append(0x80 | ((half >> 6) & 0x3F)); out.append(0x80 | (half & 0x3F))
    return bytes(out)


def has_cjk(s):
    return any('\u4e00' <= c <= '\u9fff' for c in s)


def parse_cp(data):
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        return None, []
    cp_count = struct.unpack_from(">H", data, 8)[0]
    pos = 10
    cp = {}
    order = []
    idx = 1
    while idx < cp_count:
        tag = data[pos]
        if tag == 1:
            length = struct.unpack_from(">H", data, pos + 1)[0]
            u = data[pos + 3:pos + 3 + length]
            cp[idx] = (1, data[pos:pos + 3 + length], u)
            pos += 3 + length; order.append(idx); idx += 1
        elif tag in FIXED_SIZE:
            size = FIXED_SIZE[tag]
            cp[idx] = (tag, data[pos:pos + size], None)
            pos += size; order.append(idx); idx += 2 if tag in WIDE else 1
        else:
            raise ValueError("未知常量池 tag %d @ %d" % (tag, pos))
    return cp, order


def parse_struct(data):
    """解析常量池之后的 fields/methods 段，返回：
       cp_end: 常量池结束偏移
       decls: [(name_offset_in_data, name_idx, desc_idx, kind, order_k, access), ...]  kind in {'f','m'}
    """
    cp_count = struct.unpack_from(">H", data, 8)[0]
    pos = 10
    idx = 1
    while idx < cp_count:
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack_from(">H", data, pos + 1)[0]; pos += 3 + ln; idx += 1
        elif tag in FIXED_SIZE:
            s = FIXED_SIZE[tag]; pos += s; idx += 2 if tag in WIDE else 1
        else:
            return pos, []
    cp_end = pos

    def skip_attributes(p):
        ac = struct.unpack_from(">H", data, p)[0]; p += 2
        for _ in range(ac):
            al = struct.unpack_from(">I", data, p + 2)[0]; p += 6 + al
        return p

    p = cp_end + 6  # access + this + super
    ifc = struct.unpack_from(">H", data, p)[0]; p += 2 + 2 * ifc
    decls = []
    fc = struct.unpack_from(">H", data, p)[0]; p += 2
    for k in range(fc):
        access = struct.unpack_from(">H", data, p)[0]
        name_offset = p + 2
        name_idx = struct.unpack_from(">H", data, name_offset)[0]
        desc_idx = struct.unpack_from(">H", data, name_offset + 2)[0]
        decls.append((name_offset, name_idx, desc_idx, 'f', k, access))
        p += 6; p = skip_attributes(p)
    mc = struct.unpack_from(">H", data, p)[0]; p += 2
    for k in range(mc):
        access = struct.unpack_from(">H", data, p)[0]
        name_offset = p + 2
        name_idx = struct.unpack_from(">H", data, name_offset)[0]
        desc_idx = struct.unpack_from(">H", data, name_offset + 2)[0]
        decls.append((name_offset, name_idx, desc_idx, 'm', k, access))
        p += 6; p = skip_attributes(p)
    return cp_end, decls


def get_refs(cp, order):
    refs = {}
    for i in order:
        tag, body, _ = cp[i]
        if tag in (9, 10, 11):
            class_idx, nat = struct.unpack_from(">HH", body, 1)
            owner = None
            centry = cp.get(class_idx)
            if centry and centry[0] == 7:
                name_idx = struct.unpack_from(">H", centry[1], 1)[0]
                owner = mutf8_decode(cp[name_idx][2]) if cp.get(name_idx, (0,))[0] == 1 else None
            nte = cp.get(nat)
            desc = None
            if nte and nte[0] == 12:
                _, desc_idx = struct.unpack_from(">HH", nte[1], 1)
                desc = mutf8_decode(cp[desc_idx][2]) if cp.get(desc_idx, (0,))[0] == 1 else None
            refs[i] = (nat, owner, desc, tag)
        elif tag == 18:
            _, nat = struct.unpack_from(">HH", body, 1)
            nte = cp.get(nat)
            desc = None
            if nte and nte[0] == 12:
                _, desc_idx = struct.unpack_from(">HH", nte[1], 1)
                desc = mutf8_decode(cp[desc_idx][2]) if cp.get(desc_idx, (0,))[0] == 1 else None
            refs[i] = (nat, None, desc, 18)
    return refs


def parse_base_decl(data, cp):
    """返回 {(kind, order_k): (name_idx, desc_idx)}。"""
    cp_end, decls = parse_struct(data)
    out = {}
    for name_offset, name_idx, desc_idx, kind, k, access in decls:
        out[(kind, k)] = (name_idx, desc_idx)
    return out


def build_global_decls(jar_path):
    """返回 {internal_class_name(无.class): [(name, desc, kind, access), ...]}。"""
    out = {}
    z = zipfile.ZipFile(jar_path)
    for info in z.infolist():
        if not info.filename.endswith(".class"):
            continue
        data = z.read(info.filename)
        if data[:4] != b"\xca\xfe\xba\xbe":
            continue
        try:
            cp, order = parse_cp(data)
            _, decls = parse_struct(data)
        except Exception:
            continue
        lst = []
        for name_offset, name_idx, desc_idx, kind, k, access in decls:
            nm = mutf8_decode(cp[name_idx][2]) if cp.get(name_idx, (0,))[0] == 1 else "?"
            ds = mutf8_decode(cp[desc_idx][2]) if cp.get(desc_idx, (0,))[0] == 1 else "?"
            lst.append((nm, ds, kind, access))
        out[info.filename[:-6]] = lst
    z.close()
    return out


def repair_class(data, base_data, inverse, global_decls):
    if data[:4] != b"\xca\xfe\xba\xbe":
        return None
    cp, order = parse_cp(data)
    if cp is None:
        return None
    cp_end, decls = parse_struct(data)
    total_cp_len = cp_end - 10
    string_used = set()
    for i in order:
        if cp[i][0] == 8:
            string_used.add(struct.unpack_from(">H", cp[i][1], 1)[0])
    refs = get_refs(cp, order)

    # 底包
    base_cp, base_order = parse_cp(base_data) if base_data else (None, [])
    base_refs = get_refs(base_cp, base_order) if base_cp else {}
    base_decl = parse_base_decl(base_data, base_cp) if base_cp else {}

    # 当前类的 this_class 名
    this_class = None
    if cp_end + 4 <= len(data):
        tc_idx = struct.unpack_from(">H", data, cp_end + 2)[0]
        tcentry = cp.get(tc_idx)
        if tcentry and tcentry[0] == 7:
            tn_idx = struct.unpack_from(">H", tcentry[1], 1)[0]
            this_class = mutf8_decode(cp[tn_idx][2]) if cp.get(tn_idx, (0,))[0] == 1 else None

    def ref_name_idx_of(ri):
        nte = cp.get(refs[ri][0])
        if nte and nte[0] == 12:
            return struct.unpack_from(">HH", nte[1], 1)[0]
        return None

    # 收集所有「名字用途」的中文 UTF8（引用名 + 声明名）
    name_utf8s = set()
    for ri, (nat, owner, desc, tag) in refs.items():
        if tag == 18:
            continue
        nte = cp.get(nat)
        if nte and nte[0] == 12:
            name_idx, _ = struct.unpack_from(">HH", nte[1], 1)
            if cp.get(name_idx, (0,))[0] == 1 and has_cjk(mutf8_decode(cp[name_idx][2])):
                name_utf8s.add(name_idx)
    for (name_offset, name_idx, desc_idx, kind, k, access) in decls:
        if cp.get(name_idx, (0,))[0] == 1 and has_cjk(mutf8_decode(cp[name_idx][2])):
            name_utf8s.add(name_idx)

    def cjk_eng_via_owner(uidx):
        """引用名：用 owner 类声明成员按 (kind, desc) 对照，再与逆映射取交集。返回英文名或 None。"""
        text = mutf8_decode(cp[uidx][2])
        cands = inverse.get(text)
        if not cands:
            return None
        for ri in refs:
            if ref_name_idx_of(ri) != uidx:
                continue
            _, owner, desc, tag = refs[ri]
            if not owner or desc is None:
                continue
            kind_match = 'f' if tag == 9 else 'm'
            owner_decls = global_decls.get(owner)
            if not owner_decls:
                continue
            # owner 中与该引用同 (kind, desc) 的声明名集合
            decl_names = {n for (n, d, k, a) in owner_decls if k == kind_match and d == desc}
            inter = cands & decl_names
            if len(inter) == 1:
                return next(iter(inter))
        return None

    def cjk_eng_decl_disambiguate(text, access):
        """声明名逆映射歧义时消歧。"""
        cands = inverse.get(text)
        if not cands or len(cands) <= 1:
            return (next(iter(cands)) if cands else None)
        # 枚举常量 -> 全大写候选
        if access & ACC_ENUM:
            ups = [c for c in cands if c.isupper()]
            if len(ups) == 1:
                return ups[0]
        # 字段/方法按大小写惯例：全大写优先（枚举/常量），否则全小写首字母（方法）优先
        ups = [c for c in cands if c.isupper()]
        if len(ups) == 1 and len([c for c in cands if not c.isupper()]) >= 1:
            return ups[0]
        lows = [c for c in cands if c and c[0].islower()]
        if len(lows) == 1 and len([c for c in cands if not (c and c[0].islower())]) >= 1:
            return lows[0]
        return None

    # 第一遍：确定候选英文名
    cand = {}
    for uidx in name_utf8s:
        text = mutf8_decode(cp[uidx][2])
        eng = None
        # 0) 引用名：owner 声明交集（最可靠）
        eng = cjk_eng_via_owner(uidx)
        # 1) 声明顺序对照（底包）
        if eng is None:
            for (no, ni, di, kind, k, access) in decls:
                if ni == uidx:
                    b = base_decl.get((kind, k))
                    if b:
                        bname_idx, bdesc_idx = b
                        cur_desc = mutf8_decode(cp[di][2]) if cp.get(di, (0,))[0] == 1 else None
                        base_desc = mutf8_decode(base_cp[bdesc_idx][2]) if base_cp.get(bdesc_idx, (0,))[0] == 1 else None
                        if cur_desc == base_desc:
                            eng = mutf8_decode(base_cp[bname_idx][2]) if base_cp.get(bname_idx, (0,))[0] == 1 else None
                    break
        # 2) 引用索引对照（底包）
        if eng is None:
            for ri in refs:
                if ref_name_idx_of(ri) == uidx:
                    bref = base_refs.get(ri)
                    if bref and bref[1] == refs[ri][1] and bref[2] == refs[ri][2]:
                        bnte = base_cp.get(bref[0])
                        if bnte and bnte[0] == 12:
                            bname_idx, _ = struct.unpack_from(">HH", bnte[1], 1)
                            eng = mutf8_decode(base_cp[bname_idx][2]) if base_cp.get(bname_idx, (0,))[0] == 1 else None
                    break
        # 3) 逆映射（唯一 or 消歧）
        if eng is None:
            # 若为声明名，尝试消歧
            access = None
            for (no, ni, di, kind, k, a) in decls:
                if ni == uidx:
                    access = a; break
            eng = cjk_eng_decl_disambiguate(text, access if access is not None else 0)
        cand[uidx] = eng

    # 第二遍：借用同文本、同属类的已确定英文名（枚举常量等描述符不唯一场景）
    for uidx in name_utf8s:
        if cand[uidx] is not None:
            continue
        text = mutf8_decode(cp[uidx][2])
        ow = set()
        for ri in refs:
            if ref_name_idx_of(ri) == uidx and refs[ri][1]:
                ow.add(refs[ri][1])
        for vidx in name_utf8s:
            if vidx == uidx or cand[vidx] is None:
                continue
            if mutf8_decode(cp[vidx][2]) != text:
                continue
            vow = set()
            for ri in refs:
                if ref_name_idx_of(ri) == vidx and refs[ri][1]:
                    vow.add(refs[ri][1])
            if (ow & vow) or (this_class in (ow | vow)) or (not ow and this_class in vow) or (not vow and this_class in ow):
                cand[uidx] = cand[vidx]
                break

    need = {u: e for u, e in cand.items() if e and not has_cjk(e)}

    if not need:
        return None

    new_entries = []
    next_idx = (max(order) + 1) if order else 1
    changed_bodies = {}
    decl_patches = []

    def alloc_utf8(text):
        nonlocal next_idx
        idx = next_idx; new_entries.append((1, b"\x01" + struct.pack(">H", len(mutf8_encode(text))) + mutf8_encode(text)))
        next_idx += 1
        return idx

    # 处理引用（共享则拆分）
    for ri, (nat, owner, desc, tag) in refs.items():
        if tag == 18:
            continue
        nte = cp.get(nat)
        if not nte or nte[0] != 12:
            continue
        name_idx, desc_idx0 = struct.unpack_from(">HH", nte[1], 1)
        if name_idx not in need:
            continue
        eng = need[name_idx]
        if name_idx in string_used:
            nui = alloc_utf8(eng)
            nti = next_idx; new_entries.append((12, b"\x0c" + struct.pack(">HH", nui, desc_idx0))); next_idx += 1
            body = bytearray(cp[ri][1]); struct.pack_into(">H", body, 3, nti)
            changed_bodies[ri] = bytes(body)
        else:
            nb = mutf8_encode(eng)
            if len(nb) <= 65535:
                cp[name_idx] = (1, b"\x01" + struct.pack(">H", len(nb)) + nb, nb)

    # 处理声明（共享则拆分）
    for name_offset, name_idx, desc_idx, kind, k, access in decls:
        if name_idx not in need:
            continue
        eng = need[name_idx]
        if name_idx in string_used:
            nui = alloc_utf8(eng)
            decl_patches.append((name_offset, nui))
        else:
            nb = mutf8_encode(eng)
            if len(nb) <= 65535:
                cp[name_idx] = (1, b"\x01" + struct.pack(">H", len(nb)) + nb, nb)

    # 序列化常量池
    out = bytearray()
    for i in order:
        out += changed_bodies.get(i, cp[i][1])
    for tag, body in new_entries:
        out += body
    new_cp_count = (max(order) + 1 + len(new_entries)) if order else 1
    new_cp_len = len(out)
    delta = new_cp_len - total_cp_len
    new_data = bytearray(data[:8] + struct.pack(">H", new_cp_count) + bytes(out) + data[10 + total_cp_len:])

    for name_offset, nui in decl_patches:
        struct.pack_into(">H", new_data, name_offset + delta, nui)

    return new_data


def main():
    if len(sys.argv) < 5:
        print("用法: python repair_names.py <当前.jar> <底包.jar> <汉化词典.json> <输出.jar>")
        sys.exit(1)
    cur_jar, base_jar, dict_path, out_jar = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

    with open(dict_path, encoding="utf-8") as f:
        mapping = json.load(f)
    inverse = {}
    for en, zh in mapping.items():
        if zh:
            inverse.setdefault(zh, set()).add(en)
    # 逆映射用 list 便于确定性
    inverse = {k: set(v) for k, v in inverse.items()}

    print("  构建全局声明索引 ...")
    global_decls = build_global_decls(cur_jar)

    base_zip = zipfile.ZipFile(base_jar)
    base_cache = {}
    for info in base_zip.infolist():
        if info.filename.endswith(".class"):
            base_cache[info.filename] = base_zip.read(info.filename)
    base_zip.close()

    zin = zipfile.ZipFile(cur_jar)
    total = 0
    out_items = []
    for info in zin.infolist():
        data = zin.read(info.filename)
        fixed = None
        if info.filename.endswith(".class"):
            try:
                fixed = repair_class(data, base_cache.get(info.filename) if info.filename in base_cache else None,
                                    inverse, global_decls)
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
    print("已修复 %d 个 class 的字段/方法名 -> %s" % (total, out_jar))


if __name__ == "__main__":
    main()
