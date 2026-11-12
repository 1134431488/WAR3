#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""扫描 jar 中所有 class 的常量池：找出某名字作为字段被谁声明、被谁引用。"""
import struct, sys, zipfile

TARGET = sys.argv[1] if len(sys.argv) > 1 else "透明"
JAR = sys.argv[2] if len(sys.argv) > 2 else "汉化版/RMS_zh_CN.jar"

def parse_class(data):
    """返回 (declared_field_names, referenced_field_names_and_classes)"""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return None
    n = len(data)
    idx = 8
    count = struct.unpack_from('>H', data, idx)[0]
    idx += 2
    consts = [None] * count  # 1-based
    i = 1
    while i < count:
        tag = data[idx]
        if tag == 1:  # Utf8
            ln = struct.unpack_from('>H', data, idx+1)[0]
            consts[i] = ('u', data[idx+3:idx+3+ln])  # 保留原始字节
            idx += 3 + ln
        elif tag == 7:  # Class
            consts[i] = ('c', struct.unpack_from('>H', data, idx+1)[0]); idx += 3
        elif tag == 9 or tag == 10 or tag == 11:  # Field/Method/Interface ref
            consts[i] = ('r', struct.unpack_from('>HH', data, idx+1)); idx += 5
        elif tag == 3 or tag == 4:  # Int/Float
            consts[i] = ('n',); idx += 5
        elif tag == 5 or tag == 6:  # Long/Double (占两个)
            consts[i] = ('n',); idx += 9; i += 1
        elif tag == 8:  # String
            consts[i] = ('s', struct.unpack_from('>H', data, idx+1)[0]); idx += 3
        elif tag == 12:  # NameAndType
            consts[i] = ('nt', struct.unpack_from('>HH', data, idx+1)); idx += 5
        elif tag == 15:  # MethodHandle
            consts[i] = ('h', struct.unpack_from('>BH', data, idx+1)); idx += 4
        elif tag == 16:  # MethodType
            consts[i] = ('mt', struct.unpack_from('>H', data, idx+1)[0]); idx += 3
        elif tag == 17 or tag == 18:  # Dynamic/InvokeDynamic
            consts[i] = ('d', struct.unpack_from('>HH', data, idx+1)); idx += 5
        elif tag == 19:  # Module
            consts[i] = ('c', struct.unpack_from('>H', data, idx+1)[0]); idx += 3
        elif tag == 20:  # Package
            consts[i] = ('c', struct.unpack_from('>H', data, idx+1)[0]); idx += 3
        else:
            return None
        i += 1

    def utf8(j):
        e = consts[j]
        if e and e[0] == 'u':
            b = e[1]
            for enc in ('utf-8', 'gbk', 'latin-1'):
                try:
                    s = b.decode(enc)
                    if s == TARGET:
                        return s
                except Exception:
                    pass
            # 目标可能是 GBK 字节形态
            if b == TARGET.encode('gbk', 'ignore'):
                return TARGET
            return '?'
        return '?'

    def clsname(j):
        e = consts[j]
        return utf8(e[1]).replace('/', '.') if e and e[0] == 'c' else '?'

    # 跳过 access/this/super/interfaces
    idx += 8
    ifc = struct.unpack_from('>H', data, idx)[0]; idx += 2 + 2*ifc
    declared, referenced = [], []
    for section in range(2):  # fields, methods
        cnt = struct.unpack_from('>H', data, idx)[0]; idx += 2
        for _ in range(cnt):
            idx += 6  # access, name, desc
            ac = struct.unpack_from('>H', data, idx)[0]; idx += 2
            for _ in range(ac):
                tag = data[idx]
                if tag == 1:
                    idx += 3 + struct.unpack_from('>H', data, idx+1)[0]
                elif tag in (3, 4):
                    idx += 5
                elif tag in (5, 6):
                    idx += 9
                elif tag in (7, 8, 16, 19, 20):
                    idx += 3
                elif tag in (9, 10, 11, 12, 17, 18):
                    idx += 5
                elif tag == 15:
                    idx += 4
                else:
                    return None
            if section == 0:
                pass
        if section == 0:
            # 重新解析字段名：太麻烦，换个思路——直接从常量池收集 Fieldref
            pass
    # 简化：从常量池直接收集
    for j in range(1, count):
        e = consts[j]
        if e and e[0] == 'r':
            ci, nti = e[1]
            nt = consts[nti]
            if nt and nt[0] == 'nt':
                name = utf8(nt[1])
                if name == TARGET:
                    referenced.append(clsname(ci))
    return referenced

declared_classes = []
ref_map = {}
z = zipfile.ZipFile(JAR)
for info in z.infolist():
    if not info.filename.endswith('.class'):
        continue
    try:
        refs = parse_class(z.read(info.filename))
    except Exception:
        refs = None
    if refs:
        for c in refs:
            ref_map.setdefault(c, []).append(info.filename[:-6])

print("引用了字段 %r 的类（按声明类分组）：" % TARGET)
for c, users in sorted(ref_map.items()):
    print("  目标类 %s <- %d 处引用，如: %s" % (c, len(users), users[0]))
