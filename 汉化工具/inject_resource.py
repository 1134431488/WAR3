#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把本地文件注入替换主 jar 中的单个资源条目。
用法: python inject_resource.py <jar内条目名> <本地文件>
自动先备份主 jar（仅首次）。"""
import os
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "汉化版", "RMS_zh_CN.jar")
BACKUP = os.path.join(ROOT, "汉化版", "备份", "RMS_zh_CN.jar.before-resource-fix.bak")

def main():
    entry, local = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.dirname(BACKUP), exist_ok=True)
    if not os.path.exists(BACKUP):
        shutil.copyfile(JAR, BACKUP)
        print("已备份 ->", BACKUP)
    with open(local, "rb") as f:
        data = f.read()
    zin = zipfile.ZipFile(JAR)
    assert any(i.filename == entry for i in zin.infolist()), "条目不存在: " + entry
    tmp = JAR + ".tmp"
    with zipfile.ZipFile(tmp, "w") as zout:
        for info in zin.infolist():
            content = data if info.filename == entry else zin.read(info.filename)
            ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.internal_attr = info.internal_attr
            ni.create_system = info.create_system
            ni.comment = info.comment
            zout.writestr(ni, content)
    zin.close()
    os.replace(tmp, JAR)
    print("已替换", entry, "(", len(data), "bytes )")

if __name__ == "__main__":
    main()
