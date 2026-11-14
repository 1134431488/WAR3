#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
构建「界面修复 5 项」：编译改动源码 -> 定向注入中文 -> 替换进 RMS_zh_CN.jar

修复内容：
  1. 删除 / X 按钮白字（ComponentPanel.getDeleteButton / getXButton、ComponentLayerPanel）
  2. 粒子贴图阵列分割（RenderParticleEmitter2：UV 除以 cols / rows）
  3. 时间轴可直接切换动画序列（TimeSliderPanel 增加 JComboBox<Sequence>）
  4. 大纲结构树搜索框 + CTRL+F（ComponentThingTree / ModelViewManagingView）
  5. 点击大纲节点高亮 3D 视口（ComponentTreeNode.doSelection 接入 mouseReleased）

【注入策略：差分定向还原】
  早期 rms_patch 全量套词典会连字段名、方法名、MDL 数据令牌一起翻，导致
  NoSuchFieldError / ClassCastException。这里绝不全量套词典，而是逐个 class：
    对词典中的 (英文 eng, 中文 zh)，仅当同时满足
      A. eng 出现在【新编译 class】的 UTF8 中
      B. zh  出现在【旧 jar 同名 class】的 UTF8 中
      C. eng【不在】旧 jar 同名 class 的 UTF8 中（说明当初它确实被翻掉了）
    才把 eng -> zh 加入该 class 的映射。
  标识符（字段名/方法名/类名/描述符）在旧 jar 里本就是英文，会被条件 C 排除，
  因此不会重蹈无差别替换的覆辙。新增的中文 tooltip 编译后已是中文，无需注入。

用法：
    python 构建界面修复.py --dry-run      # 只打印每个 class 的候选映射，不改任何文件
    python 构建界面修复.py --inject-only  # 跳过编译，用已有 _sandbox/fix5build 产物注入
    python 构建界面修复.py                # 编译 + 注入 + 备份 + 替换 + 验证
    python 构建界面修复.py --javac=<路径> # 显式指定 JDK 的 javac（发布仓库不含 JDK）
    RMS_JAVAC=<路径> python 构建界面修复.py   # 等价的环境变量写法
"""

import io
import json
import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rms_patch as R  # noqa: E402

ROOT = os.path.dirname(HERE)
JAR = os.path.join(ROOT, "汉化版", "RMS_zh_CN.jar")
BLENDER_JAR = os.path.join(ROOT, "汉化版", "rms-blender.jar")
# 目标字节码级别。默认 8；含 Java 14 switch 表达式的源码（ModelComponentBrowserTree 等）
# 需由调用方改为 14（见 构建第七轮.py）。
RELEASE = "8"
JAVAC_DEFAULT = os.path.join(ROOT, "_toolchain", "jdk-17", "bin", "javac.exe")
SRC = os.path.join(ROOT, "RMS_Twilac_Source", "modelstudio", "src")
# 发布仓库里没有完整的 RMS_Twilac_Source，只带本轮改动的源码存档；
# 脚本会自动回退到该存档，保证在发布仓库里也能复现构建。
FALLBACK_SRC = os.path.join(ROOT, "汉化工具", "界面修复源码")
BUILD = os.path.join(ROOT, "_sandbox", "fix5build")
DICT_PATH = os.path.join(HERE, "汉化词典.json")
BACKUP = os.path.join(ROOT, "汉化版", "备份", "RMS_zh_CN.jar.before-ui-fix5.bak")

# 注意：ComponentSDLayersPanel 故意不参与。
# 它的源码调用了父类新增的 duplicateMaterial()，而 jar 内父类没有该方法（源码比 jar 新），
# 硬编会引入 NoSuchMethodError；且它的「添加图层」按钮背景是深灰蓝 RGB(100,118,135)，
# 白字本就清晰，属设计意图，并非截图里的白字故障。
SOURCES = [
    "com/hiveworkshop/rms/ui/application/model/ComponentPanel.java",
    "com/hiveworkshop/rms/editor/render3d/RenderParticleEmitter2.java",
    "com/hiveworkshop/rms/ui/application/model/material/ComponentLayerPanel.java",
    "com/hiveworkshop/rms/ui/application/edit/animation/TimeSliderPanel.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ComponentThingTree.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ModelViewManagingView.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ComponentTreeNode.java",
]

# 这些类的所有内部类一起替换（旧内部类可能已不存在或编号变化）
PREFIXES = [
    "com/hiveworkshop/rms/ui/application/model/ComponentPanel",
    "com/hiveworkshop/rms/editor/render3d/RenderParticleEmitter2",
    "com/hiveworkshop/rms/ui/application/model/material/ComponentLayerPanel",
    "com/hiveworkshop/rms/ui/application/edit/animation/TimeSliderPanel",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ComponentThingTree",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ComponentTreeNode",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ModelViewManagingView",
]

# 源码里新增的中文 UI 文本，编译后应直接出现在 class 中（自检用）
EXPECTED_NATIVE_CJK = [
    "切换当前编辑的动画序列",
    "搜索节点名称",
]

# 【内部键黑名单】绝不可翻译成 UI 中文的英文原文。
#
# 背景：MdlUtils.TOKEN_* 是 public static final String 编译期常量，javac 会把它们
# 内联进调用方 class 的常量池。于是 UI 类（如 ComponentLayerPanel）的常量池里同时
# 存在「界面标签」和「MDL 数据令牌」两种字符串。早期无差别词典替换把后者也翻了，
# 导致 layer.find("透明度") / find("贴图 ID") 查不到 AnimFlag，材质动画静默失效；
# RenderParticleEmitter2 的 find("EmissionRate") 同理，粒子发射速率读不到。
# 重编这些类时保持英文才是正确修复，故注入阶段必须拦下这些词条。
FORBIDDEN_ENG = {
    # 本次 dry-run 实际混入候选映射、且已确认是 MdlUtils 内联令牌的项
    "Alpha", "TextureID", "EmissiveGain",
    "FresnelColor", "FresnelOpacity", "FresnelTeamColor",
    "EmissionRate",
    # 防御性补充：其它常见 MDL / 动画数据令牌
    "Translation", "Rotation", "Scaling", "Color", "Visibility",
    "LifeSpan", "Speed", "Gravity", "Variation", "Latitude", "Longitude",
    "FilterMode", "TVertexAnim", "Matrices", "Name", "GeosetId", "MaterialID",
    "Unfogged", "Unshaded", "SortPrimsFarZ", "SphereEnvMap", "TwoSided",
    "Unlit", "NoDepthTest", "NoDepthSet", "DontInterp", "Linear", "Hermite",
    "Bezier", "Billboarded", "DontInherit", "AttachmentID", "ObjectId",
}


def log(msg):
    print(msg)
    sys.stdout.flush()


def source_path(rel):
    primary = os.path.join(SRC, *rel.split("/"))
    if os.path.exists(primary):
        return primary
    return os.path.join(FALLBACK_SRC, *rel.split("/"))


def resolve_javac():
    """优先 --javac / RMS_JAVAC，其次主工作区捆绑 JDK，最后系统 PATH。"""
    for arg in sys.argv:
        if arg.startswith("--javac="):
            return arg.split("=", 1)[1]
    if os.environ.get("RMS_JAVAC"):
        return os.environ["RMS_JAVAC"]
    if os.path.exists(JAVAC_DEFAULT):
        return JAVAC_DEFAULT
    found = shutil.which("javac")
    if found:
        return found
    raise SystemExit(
        "找不到 JDK 的 javac。请用 --javac=<路径> 或环境变量 RMS_JAVAC 指定 JDK 17。\n"
        "（发布仓库不含 JDK；主工作区默认用 _toolchain/jdk-17/bin/javac.exe）")


def compile_sources():
    javac = resolve_javac()
    for path in (JAR, BLENDER_JAR):
        if not os.path.exists(path):
            raise SystemExit("缺少文件: " + path)
    if not os.path.exists(javac):
        raise SystemExit("找不到 javac: " + javac)
    shutil.rmtree(BUILD, ignore_errors=True)
    os.makedirs(BUILD)
    classpath = JAR + ";" + BLENDER_JAR
    files = [source_path(s) for s in SOURCES]
    missing = [f for f in files if not os.path.exists(f)]
    if missing:
        raise SystemExit("找不到源码（既不在 RMS_Twilac_Source 也不在 界面修复源码）:\n  "
                         + "\n  ".join(missing))
    cmd = [javac, "--release", RELEASE, "-encoding", "UTF-8", "-nowarn",
           "-cp", classpath, "-d", BUILD] + files
    result = subprocess.run(cmd, capture_output=True)
    out = result.stdout.decode("gbk", "replace")
    err = result.stderr.decode("gbk", "replace")
    if out.strip():
        log(out[-4000:])
    if err.strip():
        log(err[-12000:])
    if result.returncode != 0:
        raise SystemExit("javac 编译失败 (rc=%d)" % result.returncode)
    log("编译完成")


def collect_classes():
    classes = []
    for dirpath, _, filenames in os.walk(BUILD):
        for name in filenames:
            if name.endswith(".class"):
                full = os.path.join(dirpath, name)
                rel = os.path.relpath(full, BUILD).replace(os.sep, "/")
                with open(full, "rb") as f:
                    classes.append((rel, f.read()))
    return sorted(classes)


def belongs(entry):
    for p in PREFIXES:
        if entry == p + ".class":
            return True
        if entry.startswith(p + "$") and entry.endswith(".class"):
            return True
    return False


def utf8_texts(data):
    if not data:
        return set()
    try:
        _, _, entries = R.parse_constant_pool(data)
    except R.BadClassFile:
        return set()
    return {R.utf8_to_str(u8) for tag, raw, u8 in entries
            if tag == 1 and u8 is not None}


def build_mapping(old_data, new_data, dictionary):
    """差分定向映射：只还原旧 jar 中确实被翻成中文的英文 UI 原文。"""
    old = utf8_texts(old_data)
    new = utf8_texts(new_data)
    mapping = {}
    blocked = []
    for eng, zh in dictionary.items():
        if not isinstance(eng, str) or not isinstance(zh, str):
            continue
        if eng in new and zh in old and eng not in old:
            if eng in FORBIDDEN_ENG:
                blocked.append(eng)
            else:
                mapping[eng] = zh
    return mapping, sorted(blocked)


def is_signature(name):
    n = name.upper()
    return n.startswith("META-INF/") and n.endswith((".SF", ".RSA", ".DSA", ".EC"))


def main():
    dry_run = "--dry-run" in sys.argv
    inject_only = "--inject-only" in sys.argv

    if not inject_only:
        compile_sources()
    elif not os.path.isdir(BUILD):
        raise SystemExit("找不到编译产物目录: " + BUILD)

    classes = collect_classes()
    if not classes:
        raise SystemExit("没有编译产物 class")
    with io.open(DICT_PATH, encoding="utf-8") as f:
        dictionary = json.load(f)
    log("词典词条数: %d，编译产物 class 数: %d" % (len(dictionary), len(classes)))

    with zipfile.ZipFile(JAR, "r") as zin:
        jar_entries = [(info, zin.read(info.filename)) for info in zin.infolist()]
    old_map = {info.filename: data for info, data in jar_entries}

    final_classes = []
    total_hits = 0
    log("")
    log("===== 定向注入映射 =====")
    for rel, new_data in classes:
        old_data = old_map.get(rel)
        mapping, blocked = build_mapping(old_data, new_data, dictionary)
        injected, hits = R.rebuild_class(new_data, mapping)
        final = injected if injected is not None else new_data
        total_hits += hits
        status = "新增类" if old_data is None else "替换类"
        log("[%s] %-95s 映射 %2d 项, 命中 %2d" % (status, rel, len(mapping), hits))
        for eng in sorted(mapping):
            log("        %r -> %r" % (eng, mapping[eng]))
        for eng in blocked:
            log("        [拦下·内部键保持英文] %r" % eng)
        final_classes.append((rel, final))
    log("合计注入命中 %d 处" % total_hits)

    # 自检：源码里新增的中文 tooltip 是否编译进 class
    log("")
    log("===== 新增中文文本自检 =====")
    all_new_text = set()
    for rel, data in final_classes:
        all_new_text |= utf8_texts(data)
    for expected in EXPECTED_NATIVE_CJK:
        ok = any(expected in t for t in all_new_text)
        log("  %s %s" % ("OK  " if ok else "缺失", expected))
        if not ok and not dry_run:
            raise SystemExit("自检失败：新增中文文本未编译进 class: " + expected)

    if dry_run:
        log("")
        log("--dry-run：未修改任何文件")
        return

    # 备份
    os.makedirs(os.path.dirname(BACKUP), exist_ok=True)
    if not os.path.exists(BACKUP):
        shutil.copy2(JAR, BACKUP)
        log("已备份原 jar -> " + BACKUP)

    tmp = JAR + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for info, data in jar_entries:
            if belongs(info.filename) or is_signature(info.filename):
                continue
            ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.internal_attr = info.internal_attr
            ni.create_system = info.create_system
            ni.comment = info.comment
            zout.writestr(ni, data)
        for rel, data in final_classes:
            zout.writestr(rel, data)
    os.replace(tmp, JAR)
    log("")
    log("已替换 %d 个 class 进主 jar" % len(final_classes))

    # 验证
    with zipfile.ZipFile(JAR, "r") as zf:
        bad = zf.testzip()
        names = zf.namelist()
        cjk_classes = 0
        for n in names:
            if not n.endswith(".class"):
                continue
            d = zf.read(n)
            if any(t and any(0x4E00 <= ord(c) <= 0x9FFF for c in t)
                   for t in utf8_texts(d)):
                cjk_classes += 1
    log("testzip: %s | 条目数: %d | 含中文 class 数: %d" %
        (bad if bad else "OK", len(names), cjk_classes))
    if bad:
        raise SystemExit("jar 校验失败: " + bad)
    log("完成。")


if __name__ == "__main__":
    main()
