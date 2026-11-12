#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
编译并打包「悬停功能说明气泡」组件。

产出：
    汉化版/rms-tooltips.jar   启动器 class + 内置说明表
    汉化版/提示说明.txt        外部可编辑说明表（已存在则保留用户修改）

依赖：_toolchain/jdk-17/bin/javac.exe（便携 JDK，仅构建时需要，运行不需要）
用法：python 汉化工具/tooltip/构建气泡组件.py
"""

import glob
import os
import shutil
import subprocess
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
ROOT = os.path.dirname(TOOLS)

SRC = os.path.join(HERE, "RmsTooltipInstaller.java")
BUILD = os.path.join(HERE, "_build")
TIPS_SRC = os.path.join(HERE, "tooltips.txt")
OUT_JAR = os.path.join(ROOT, "汉化版", "rms-tooltips.jar")
TIPS_DST = os.path.join(ROOT, "汉化版", "提示说明.txt")


def find_javac():
    p = os.path.join(ROOT, "_toolchain", "jdk-17", "bin",
                     "javac.exe" if os.name == "nt" else "javac")
    return p if os.path.exists(p) else "javac"


def tip_keys(path):
    keys = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith("#") or s.startswith("//") or "=" not in s:
                continue
            keys.add(s.split("=", 1)[0].strip())
    return keys


def sync_external_tips():
    """内置说明表升级后，把新增条目自动补进外部表，但不改用户已有的行。"""
    have = tip_keys(TIPS_DST)
    with open(TIPS_SRC, encoding="utf-8") as f:
        lines = f.read().splitlines()
    missing = [ln for ln in lines
               if ln.strip() and not ln.lstrip().startswith("#") and "=" in ln
               and ln.split("=", 1)[0].strip() not in have]
    if missing:
        with open(TIPS_DST, "a", encoding="utf-8") as f:
            f.write("\r\n# ===== 以下条目由版本更新自动补充，可自由修改 =====\r\n")
            f.write("\r\n".join(missing) + "\r\n")
        print("    自动补充 %d 条新说明到外部表（用户原有内容保持不变）" % len(missing))
    else:
        print("    外部表内容已是最新")


def main():
    os.makedirs(BUILD, exist_ok=True)
    os.makedirs(os.path.dirname(OUT_JAR), exist_ok=True)

    print("[1/3] 编译启动器 ...")
    subprocess.run([find_javac(), "--release", "8", "-encoding", "UTF-8",
                    "-d", BUILD, SRC], check=True)
    classes = sorted(glob.glob(os.path.join(BUILD, "*.class")))
    print("    生成 class: %s" % ", ".join(os.path.basename(c) for c in classes))

    print("[2/3] 打包 rms-tooltips.jar ...")
    with zipfile.ZipFile(OUT_JAR, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n")
        for c in classes:
            z.write(c, os.path.basename(c))
        z.write(TIPS_SRC, "tooltips.txt")
    print("    -> %s (%d bytes)" % (OUT_JAR, os.path.getsize(OUT_JAR)))

    print("[3/3] 准备外部说明表 ...")
    if os.path.exists(TIPS_DST):
        print("    已存在，保留用户修改：%s" % TIPS_DST)
        sync_external_tips()
    else:
        shutil.copyfile(TIPS_SRC, TIPS_DST)
        print("    已生成：%s" % TIPS_DST)

    print("\n完成。请用 汉化版/启动汉化版.bat 启动程序。")


if __name__ == "__main__":
    main()
