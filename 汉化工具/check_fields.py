#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""全 jar 交叉校验：只检查 Fieldref(tag 9)，沿父类链解析字段。
输出所有 NoSuchFieldError 隐患（声明方与引用方名字不一致）。"""
import struct, sys, zipfile, collections

JAR = sys.argv[1] if len(sys.argv) > 1 else "汉化版/RMS_zh_CN.jar"

def parse(data):
    """返回 (this_class, super_class, declared_fields{(name,desc)}, fieldrefs[(owner,name,desc)])"""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return None
    idx = 8
    count = struct.unpack_from('>H', data, idx)[0]
    idx += 2
    consts = [None] * count
    i = 1
    try:
        while i < count:
            tag = data[idx]
            if tag == 1:
                ln = struct.unpack_from('>H', data, idx+1)[0]
                consts[i] = ('u', data[idx+3:idx+3+ln]); idx += 3 + ln
            elif tag == 7:
                consts[i] = ('c', struct.unpack_from('>H', data, idx+1)[0]); idx += 3
            elif tag in (9, 10, 11):
                consts[i] = ('r', tag, struct.unpack_from('>HH', data, idx+1)); idx += 5
            elif tag in (3, 4):
                idx += 5
            elif tag in (5, 6):
                idx += 9; i += 1
            elif tag == 8:
                consts[i] = ('s', struct.unpack_from('>H', data, idx+1)[0]); idx += 3
            elif tag == 12:
                consts[i] = ('nt', struct.unpack_from('>HH', data, idx+1)); idx += 5
            elif tag == 15:
                idx += 4
            elif tag == 16:
                idx += 3
            elif tag in (17, 18):
                idx += 5
            elif tag in (19, 20):
                consts[i] = ('c', struct.unpack_from('>H', data, idx+1)[0]); idx += 3
            else:
                return None
            i += 1
    except Exception:
        return None

    def U(j):
        e = consts[j]
        if e and e[0] == 'u':
            return e[1].decode('utf-8', 'replace')
        return '?'

    def C(j):
        e = consts[j]
        return U(e[1]) if e and e[0] == 'c' else '?'

    idx += 6
    this_c = C(struct.unpack_from('>H', data, idx-4)[0])
    super_c = C(struct.unpack_from('>H', data, idx-2)[0])
    ifc = struct.unpack_from('>H', data, idx)[0]; idx += 2 + 2 * ifc

    def skip_attr(idx):
        ac = struct.unpack_from('>H', data, idx)[0]; idx += 2
        for _ in range(ac):
            idx += 2
            alen = struct.unpack_from('>I', data, idx)[0]; idx += 4 + alen
        return idx

    cnt = struct.unpack_from('>H', data, idx)[0]; idx += 2
    declared = set()
    for _ in range(cnt):
        name_i, desc_i = struct.unpack_from('>HH', data, idx+2)
        declared.add((U(name_i), U(desc_i)))
        idx += 6
        idx = skip_attr(idx)
    cnt = struct.unpack_from('>H', data, idx)[0]; idx += 2
    for _ in range(cnt):
        idx += 6
        idx = skip_attr(idx)

    refs = []
    for j in range(1, count):
        e = consts[j]
        if e and e[0] == 'r' and e[1] == 9:
            ci, nti = e[2]
            nt = consts[nti]
            if nt and nt[0] == 'nt':
                refs.append((C(ci), U(nt[1][0]), U(nt[1][1])))
    return this_c, super_c, declared, refs

classes = {}
z = zipfile.ZipFile(JAR)
for info in z.infolist():
    if info.filename.endswith('.class'):
        try:
            p = parse(z.read(info.filename))
        except Exception:
            p = None
        if p:
            classes[info.filename[:-6]] = p

# 父类链闭包
parents = {c: (p if p and p in classes else None) for c, (_, p, _, _) in classes.items()}

def class_declares(cls, name, desc):
    c = cls
    seen = set()
    while c and c in classes and c not in seen:
        seen.add(c)
        d = classes[c][2]
        if (name, desc) in d:
            return True
        if any(n == name for n, _ in d):
            return True
        c = parents.get(c)
    return False

bad = collections.defaultdict(list)
for cname, (_, _, _, refs) in classes.items():
    for owner, name, desc in refs:
        if owner.startswith('java/') or owner.startswith('javax/') \
                or owner.startswith('sun/') or owner.startswith('jdk/') \
                or owner.startswith('ch/') or owner.startswith('org/') \
                or owner.startswith('com/sun/') or owner.startswith('net/'):
            continue
        if owner not in classes:
            continue
        if not class_declares(owner, name, desc):
            bad[(owner, name)].append((cname, desc))

print("解析 %d 个类，发现 %d 处字段不一致：" % (len(classes), len(bad)))
for (o, name), users in sorted(bad.items()):
    print("❌ %s.%s 被 %d 处引用但未声明:" % (o, name, len(users)))
    for u, desc in users[:3]:
        print("     <- %s  desc=%s" % (u, desc))
