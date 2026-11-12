#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把界面增强类编译后注入 RMS_zh_CN.jar。

包含：
  - 直角中文工具栏按钮（ToolBar / ToolbarButton2 / ModeButton2）
  - 中文枚举名（ModelEditorActionType3 / SelectionItemTypes）
  - 粒子对话框：父级搜索、XYZ 提示、实时坐标、丝带粒子
  - 添加菜单中文 + 丝带粒子入口

用法：
    python "汉化工具\\blender\\构建界面增强.py"
"""

import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CND = "\u6c49\u5316\u7248"
JAR = os.path.join(ROOT, CND, "RMS_zh_CN.jar")
BLENDER_JAR = os.path.join(ROOT, CND, "rms-blender.jar")
JAVAC = os.path.join(ROOT, "_toolchain", "jdk-17", "bin", "javac.exe")
SRC = os.path.join(ROOT, "RMS_Twilac_Source", "modelstudio", "src")
BUILD = os.path.join(ROOT, "_sandbox", "uibuild")

SOURCES = [
    "com/hiveworkshop/rms/ui/gui/modeledit/toolbar/ModelEditorActionType3.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/toolbar/ToolbarButton2.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/toolbar/ModeButton2.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/selection/SelectionItemTypes.java",
    "com/hiveworkshop/rms/ui/application/ToolBar.java",
    "com/hiveworkshop/rms/ui/application/AddParticlePanel.java",
    "com/hiveworkshop/rms/ui/application/model/nodepanels/ComponentRibbonPanel.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/creator/CreatorModelingPanel.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/creator/ModelingCreatorToolsView.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/creator/ManualTransformPanel.java",
    "com/hiveworkshop/rms/ui/application/MenuBar1/AddMenu.java",
    "com/hiveworkshop/rms/ui/application/MenuBar1/AboutMenu.java",
    "com/hiveworkshop/rms/ui/application/edit/animation/TimeBarPainter.java",
]

# 这些类的所有内部类都需要一起替换（旧内部类可能已不存在）
REPLACE_PREFIXES = [
    "com/hiveworkshop/rms/ui/gui/modeledit/toolbar/ModelEditorActionType3",
    "com/hiveworkshop/rms/ui/gui/modeledit/toolbar/ToolbarButton2",
    "com/hiveworkshop/rms/ui/gui/modeledit/toolbar/ModeButton2",
    "com/hiveworkshop/rms/ui/gui/modeledit/selection/SelectionItemTypes",
    "com/hiveworkshop/rms/ui/application/ToolBar",
    "com/hiveworkshop/rms/ui/application/AddParticlePanel",
    "com/hiveworkshop/rms/ui/application/model/nodepanels/ComponentRibbonPanel",
    "com/hiveworkshop/rms/ui/gui/modeledit/creator/CreatorModelingPanel",
    "com/hiveworkshop/rms/ui/gui/modeledit/creator/ModelingCreatorToolsView",
    "com/hiveworkshop/rms/ui/gui/modeledit/creator/ManualTransformPanel",
    "com/hiveworkshop/rms/ui/application/MenuBar1/AddMenu",
    "com/hiveworkshop/rms/ui/application/MenuBar1/AboutMenu",
    "com/hiveworkshop/rms/ui/application/edit/animation/TimeBarPainter",
]

# 文档路径 -> 本地文件（注入到 jar 内 docs/ 目录）
DOC_SOURCES = {
    "docs/someHelp.html": "帮助文档/someHelp.html",
    "docs/about.html": "帮助文档/about.html",
    "docs/changelog.html": "帮助文档/changelog.html",
}
DOCS = {}


def load_docs():
    base = os.path.dirname(os.path.abspath(__file__))
    for jar_path, local in DOC_SOURCES.items():
        full = os.path.join(base, local)
        if os.path.exists(full):
            with open(full, "rb") as f:
                DOCS[jar_path] = f.read()
        else:
            print("缺少文档：", full)


def run(cmd):
    print("+", " ".join(cmd))
    result = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    if result.stdout:
        print(result.stdout)
    if result.stderr:
        print(result.stderr)
    if result.returncode != 0:
        raise SystemExit("命令失败: %s" % cmd[0])


def compile_sources():
    if os.path.isdir(BUILD):
        shutil.rmtree(BUILD)
    os.makedirs(BUILD)
    classpath = JAR + os.pathsep + BLENDER_JAR
    files = [os.path.join(SRC, s.replace("/", os.sep)) for s in SOURCES]
    run([JAVAC, "--release", "8", "-encoding", "UTF-8", "-nowarn",
         "-cp", classpath, "-d", BUILD] + files)


def collect_classes():
    classes = []
    for dirpath, _, files in os.walk(BUILD):
        for name in files:
            if name.endswith(".class"):
                full = os.path.join(dirpath, name)
                rel = os.path.relpath(full, BUILD).replace(os.sep, "/")
                classes.append((rel, full))
    return classes


def replace_prefix(entry):
    for prefix in REPLACE_PREFIXES:
        if entry == prefix + ".class":
            return True
        if entry.startswith(prefix + "$") and entry.endswith(".class"):
            return True
    return False


def inject(classes):
    backup = JAR + ".orig"
    if not os.path.exists(backup):
        shutil.copy2(JAR, backup)
        print("已备份原 jar ->", backup)

    tmp = JAR + ".tmp"
    replaced = []
    with zipfile.ZipFile(JAR, "r") as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            if replace_prefix(item.filename) or item.filename in DOCS:
                continue
            dst.writestr(item, src.read(item.filename))
        for rel, full in classes:
            with open(full, "rb") as f:
                dst.writestr(rel, f.read())
            replaced.append(rel)
        for rel, data in DOCS.items():
            dst.writestr(rel, data)
    os.replace(tmp, JAR)
    print("注入 %d 个类：" % len(replaced))
    for rel in sorted(replaced):
        print("   ", rel)
    for rel in DOCS:
        print("    文档:", rel)


def main():
    if not os.path.exists(JAVAC):
        raise SystemExit("找不到 javac: " + JAVAC)
    if not os.path.exists(JAR):
        raise SystemExit("找不到主 jar: " + JAR)
    compile_sources()
    classes = collect_classes()
    if not classes:
        raise SystemExit("没有编译出 class")
    load_docs()
    inject(classes)
    print("完成。")


if __name__ == "__main__":
    main()
