#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
一键重新生成汉化版。

改完 词典源.txt / 术语表.txt / 排除类.txt 后，直接运行：
    python 重新生成.py

流程：从原版 jar 提取常量池词条 -> 生成 汉化词典.json -> 改写 class
      -> 注入中文帮助页与中文颜色字符串资源 -> 输出成品。
不需要 JDK、不需要 Gradle、不需要重新编译。
"""

import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

ORIGINAL_JAR = os.path.join(ROOT, "_downloads", "RMS_TwilacsNowIn3D_08_a.jar")
STRINGS_JSON = os.path.join(HERE, "词条清单.json")
DICT_JSON = os.path.join(HERE, "汉化词典.json")
OUTPUT_JAR = os.path.join(ROOT, "汉化版", "RMS_zh_CN.jar")

# 只改写界面层；MDL/MDX 解析器、模型数据、第三方库一律不动
PREFIXES = (
    "com/hiveworkshop/rms/ui/",
    "com/hiveworkshop/rms/editor/actions/",
    "com/hiveworkshop/rms/util/",
    "com/hiveworkshop/rms/ztempteststuff/",
    "com/hiveworkshop/rms/Main",
)

# 非 class 资源：目录里的中文版 -> jar 内的目标路径
RESOURCE_MAP = {
    os.path.join(HERE, "资源汉化", "someHelp.html"): "docs/someHelp.html",
    os.path.join(HERE, "资源汉化", "WorldEditStrings.txt"): "UI/WorldEditStrings.txt",
    # Blender 风格渲染类（由 汉化工具/blender/构建Blender模式.py 编译；不存在则跳过）
    os.path.join(HERE, "blender", "classes", "com", "hiveworkshop", "rms", "ui",
                 "application", "viewer", "GridPainter.class"):
        "com/hiveworkshop/rms/ui/application/viewer/GridPainter.class",
}


def inject_resources(jar_path):
    """把中文帮助页 / 颜色字符串替换进已生成的 jar，其余条目原样保留。"""
    repl = {}
    for src, entry in RESOURCE_MAP.items():
        if os.path.exists(src):
            with open(src, "rb") as f:
                repl[entry] = f.read()
    if not repl:
        return

    tmp = jar_path + ".tmp"
    with zipfile.ZipFile(jar_path) as zin, zipfile.ZipFile(tmp, "w") as zout:
        for info in zin.infolist():
            data = repl.get(info.filename)
            if data is None:
                data = zin.read(info.filename)
            new_info = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            new_info.compress_type = info.compress_type
            new_info.external_attr = info.external_attr
            new_info.internal_attr = info.internal_attr
            new_info.create_system = info.create_system
            new_info.comment = info.comment
            zout.writestr(new_info, data)
    os.replace(tmp, jar_path)
    print("已注入 %d 个中文资源：%s" % (len(repl), ", ".join(sorted(repl))))


def main():
    if not os.path.exists(ORIGINAL_JAR):
        raise SystemExit("找不到原版 jar：%s\n请确认 _downloads 目录未被移动。" % ORIGINAL_JAR)
    sys.path.insert(0, HERE)
    import rms_patch
    import build_dict

    print("[1/4] 提取原版 jar 中的常量池字符串 ...")
    rms_patch.cmd_extract(ORIGINAL_JAR, STRINGS_JSON, PREFIXES)

    print("[2/4] 生成汉化词典 ...")
    sys.argv = ["build_dict.py", STRINGS_JSON, DICT_JSON]
    build_dict.main()

    print("[3/4] 改写 class 并打包 ...")
    rms_patch.cmd_apply(ORIGINAL_JAR, DICT_JSON, OUTPUT_JAR, PREFIXES)

    print("[4/4] 注入中文帮助页与资源 ...")
    inject_resources(OUTPUT_JAR)

    print("\n完成：%s" % OUTPUT_JAR)
    print("用 汉化版/启动汉化版.bat 启动即可（自带便携 Java 运行时）。")


if __name__ == "__main__":
    main()
