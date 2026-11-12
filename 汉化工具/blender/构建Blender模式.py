#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
构建 Blender 模式增强组件。

产出：
    汉化版/rms-blender.jar    启动器 + 视口增强（运行时注入，不改任何原 class）
    汉化版/备份/user.profileNew.*.bak   写偏好前自动备份用户原配置

用法：python 汉化工具/blender/构建Blender模式.py [--skip-prefs]
      --skip-prefs  只编译打包，不改动用户偏好文件
"""

import glob
import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
MAIN_JAR = os.path.join(ROOT, "汉化版", "RMS_zh_CN.jar")
OUT_JAR = os.path.join(ROOT, "汉化版", "rms-blender.jar")
BACKUP_DIR = os.path.join(ROOT, "汉化版", "备份")
BUILD = os.path.join(HERE, "_build")
GRID_OUT = os.path.join(HERE, "classes")
GRID_SRC = os.path.join(HERE, "src", "com", "hiveworkshop", "rms",
                        "ui", "application", "viewer", "GridPainter.java")
GRID_ENTRY = "com/hiveworkshop/rms/ui/application/viewer/GridPainter.class"


def inject_grid_class():
    """编译 Blender 风格 GridPainter，并替换进主 jar（自动备份原始 jar）。"""
    os.makedirs(GRID_OUT, exist_ok=True)
    subprocess.run([java_bin("javac"), "--release", "8", "-encoding", "UTF-8",
                    "-cp", MAIN_JAR, "-d", GRID_OUT, GRID_SRC], check=True)
    class_path = os.path.join(GRID_OUT, *GRID_ENTRY.split("/"))
    if not os.path.exists(class_path):
        raise SystemExit("编译产物未找到：" + class_path)
    with open(class_path, "rb") as f:
        new_class = f.read()

    os.makedirs(BACKUP_DIR, exist_ok=True)
    backup = os.path.join(BACKUP_DIR, "RMS_zh_CN.jar.before-blender.bak")
    if not os.path.exists(backup):
        shutil.copyfile(MAIN_JAR, backup)
        print("    已备份原始主程序 -> " + backup)

    tmp = MAIN_JAR + ".tmp"
    with zipfile.ZipFile(MAIN_JAR) as zin, zipfile.ZipFile(tmp, "w") as zout:
        for info in zin.infolist():
            data = new_class if info.filename == GRID_ENTRY else zin.read(info.filename)
            ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.internal_attr = info.internal_attr
            ni.create_system = info.create_system
            ni.comment = info.comment
            zout.writestr(ni, data)
    os.replace(tmp, MAIN_JAR)
    print("    已替换 %s (%d bytes)" % (GRID_ENTRY, len(new_class)))


def java_bin(name):
    p = os.path.join(ROOT, "_toolchain", "jdk-17", "bin",
                     name + (".exe" if os.name == "nt" else ""))
    return p if os.path.exists(p) else name


def main():
    if not os.path.exists(MAIN_JAR):
        raise SystemExit("找不到主程序：%s" % MAIN_JAR)

    if os.path.isdir(BUILD):
        shutil.rmtree(BUILD)
    os.makedirs(BUILD, exist_ok=True)
    sources = [os.path.join(HERE, n) for n in (
        "BlenderPrefsTool.java", "BlenderLauncher.java", "BlenderHooks.java",
        "ScalingHooks.java")]

    print("[1/3] 编译增强类 ...")
    subprocess.run([java_bin("javac"), "--release", "8", "-encoding", "UTF-8",
                    "-cp", MAIN_JAR, "-d", BUILD] + sources, check=True)
    spy_src = os.path.join(HERE, "src", "com", "hiveworkshop", "rms", "ui",
                           "application", "viewer", "MouseEventSpy.java")
    subprocess.run([java_bin("javac"), "--release", "8", "-encoding", "UTF-8",
                    "-cp", MAIN_JAR, "-d", BUILD, spy_src], check=True)
    classes = sorted(glob.glob(os.path.join(BUILD, "**", "*.class"), recursive=True))
    print("    生成 %d 个 class" % len(classes))

    print("[2/3] 打包 rms-blender.jar ...")
    with zipfile.ZipFile(OUT_JAR, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n")
        for c in classes:
            rel = os.path.relpath(c, BUILD)
            z.write(c, rel.replace(os.sep, "/"))
    print("    -> %s (%d bytes)" % (OUT_JAR, os.path.getsize(OUT_JAR)))

    if "--skip-grid" not in sys.argv:
        print("[2.5/3] 编译并注入 Blender 渐变背景 / 网格渲染类 ...")
        inject_grid_class()

    if "--skip-prefs" in sys.argv:
        print("[3/3] 按参数跳过偏好写入。")
    else:
        print("[3/3] 写入 Blender 偏好（自动备份原配置）...")
        os.makedirs(BACKUP_DIR, exist_ok=True)
        cp = os.pathsep.join([MAIN_JAR, OUT_JAR])
        r = subprocess.run([java_bin("java"), "-cp", cp,
                            "BlenderPrefsTool", BACKUP_DIR])
        if r.returncode != 0:
            raise SystemExit("偏好写入失败，请用调试模式查看输出。")

    print("\n完成。请确保启动脚本使用 BlenderLauncher 作为主类。")


if __name__ == "__main__":
    main()
