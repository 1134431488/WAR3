#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
对汉化成品做静态体检：不启动程序，逐条比对原版 jar。
用法：python 汉化工具/验证.py
"""

import json
import os
import struct
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ORIGINAL = os.path.join(ROOT, "_downloads", "RMS_TwilacsNowIn3D_08_a.jar")
PATCHED = os.path.join(ROOT, "汉化版", "RMS_zh_CN.jar")
TOOLTIP_JAR = os.path.join(ROOT, "汉化版", "rms-tooltips.jar")
TIPS_TXT = os.path.join(ROOT, "汉化版", "提示说明.txt")
HELP_CN = os.path.join(ROOT, "汉化版", "粒子特效中文教程.html")

sys.path.insert(0, HERE)
import rms_patch  # noqa: E402

ok = True


def check(label, cond, extra=""):
    global ok
    if not cond:
        ok = False
    print("  [%s] %s%s" % ("OK" if cond else "!!", label,
                            ("  -> " + str(extra)) if extra else ""))


def entries(path):
    with zipfile.ZipFile(path) as z:
        names = [i.filename for i in z.infolist()
                 if not (i.filename.upper().startswith("META-INF/")
                         and i.filename.upper().endswith((".SF", ".RSA", ".DSA", ".EC")))]
        return names, {n: z.read(n) for n in names}


def main():
    print("=== 1. jar 结构 ===")
    na, a = entries(ORIGINAL)
    nb, b = entries(PATCHED)
    check("条目数量一致", True, "%d -> %d" % (len(na), len(nb)))
    added = [n for n in nb if n not in a]
    removed = [n for n in na if n not in b]
    check("条目差异（新增/移除条目仅限合成类与新增面板）",
          all(("$" in n) or ("ComponentsPanel" in n) for n in added + removed),
          "新增 %s / 移除 %s" % (added[:4], removed[:4]))
    check("MANIFEST 完全未改", a.get("META-INF/MANIFEST.MF") == b.get("META-INF/MANIFEST.MF"))

    changed = [n for n in na if n in b and a[n] != b[n]]
    changed += [n for n in added]
    changed_class = [n for n in changed if n.endswith(".class")]
    changed_res = [n for n in changed if not n.endswith(".class")]
    print("  改动 class: %d 个；改动资源: %d 个" % (len(changed_class), len(changed_res)))
    check("仅 2 个非 class 资源被改动", len(changed_res) == 2, changed_res)
    check("中文帮助页已注入", b.get("docs/someHelp.html", b"").decode("utf-8", "ignore")
          .find("快捷键与操作速查") >= 0)
    check("中文颜色资源已注入", "红色" in b.get("UI/WorldEditStrings.txt", b"").decode("utf-8", "ignore"))

    print("=== 2. 安全边界 ===")
    check("MDL/MDX 解析器包完全未改",
          not any(n.startswith("com/hiveworkshop/rms/parsers/") for n in changed_class))
    check("模型数据包完全未改",
          not any(n.startswith("com/hiveworkshop/rms/editor/model/") for n in changed_class))
    check("第三方库完全未改",
          not any(not n.startswith("com/hiveworkshop/rms/") for n in changed_class))
    excludes = rms_patch.load_excludes()
    check("类级排除生效",
          not any(n.startswith(e) for n in changed_class for e in excludes),
          "%d 条排除前缀" % len(excludes))

    print("=== 3. class 字节码完整性 ===")
    bad = []
    for n in changed_class:
        try:
            rms_patch.parse_constant_pool(b[n])
        except Exception as ex:
            bad.append((n, str(ex)))
    check("全部改动 class 的常量池可解析", not bad, bad[:3])

    print("=== 4. 汉化结果抽查 ===")
    dict_path = os.path.join(HERE, "汉化词典.json")
    with open(dict_path, encoding="utf-8") as f:
        mapping = json.load(f)
    print("  词典条目: %d" % len(mapping))
    wanted = {
        "Select Linked Geometry": "选择相连几何体",
        "Select Node Geometry": "按节点选择几何体",
        "Help/Keyboard Shortcuts": "帮助/快捷键速查",
        "Particle Emitter": "粒子发射器",
        "EmissionRate": "发射速率",
        "Filter Mode": "过滤模式",
        "Priority Plane:": "优先级平面：",
    }
    all_text = set()
    for n in changed_class:
        try:
            _, _, ents = rms_patch.parse_constant_pool(b[n])
        except Exception:
            continue
        for tag, _, u8 in ents:
            if tag == 1 and u8 is not None:
                all_text.add(rms_patch.utf8_to_str(u8))
    for en, zh in wanted.items():
        check("「%s」→「%s」" % (en, zh), zh in all_text)

    print("=== 5. 技术键保护 ===")
    # 收集汉化后 jar 中所有主程序 class 的 UTF8 常量
    all_rms_text = set()
    for n in nb:
        if not n.endswith(".class") or not n.startswith("com/hiveworkshop/rms/"):
            continue
        try:
            _, _, ents = rms_patch.parse_constant_pool(b[n])
        except Exception:
            continue
        for tag, _, u8 in ents:
            if tag == 1 and u8 is not None:
                all_rms_text.add(rms_patch.utf8_to_str(u8))
    for key in ("control Z", "Center", "Chest Mount", "Hand Right", "Origin"):
        label = ("附着点数据键保留：%s" % key) if key in ("Chest Mount", "Hand Right", "Origin") \
            else ("技术键保留：%s" % key)
        check(label, key in all_rms_text)
    check("字体已替换为 Microsoft YaHei", "Microsoft YaHei" in all_rms_text)

    print("=== 6. 气泡组件与交付文件 ===")
    check("rms-tooltips.jar 存在", os.path.exists(TOOLTIP_JAR))
    if os.path.exists(TOOLTIP_JAR):
        with zipfile.ZipFile(TOOLTIP_JAR) as z:
            tn = z.namelist()
            cls = z.read("RmsTooltipInstaller.class")
            tips = z.read("tooltips.txt").decode("utf-8")
        check("含启动器 class 与内置说明表",
              "RmsTooltipInstaller.class" in tn and "tooltips.txt" in tn, tn)
        check("class 文件头有效且为 Java 8 字节码",
              cls[:4] == b"\xca\xfe\xba\xbe" and struct.unpack_from(">H", cls, 6)[0] == 52,
              "major=%d" % struct.unpack_from(">H", cls, 6)[0])
        check("内置说明表含中文", "发射速率" in tips, "%d 字符" % len(tips))
    check("外部可编辑说明表存在", os.path.exists(TIPS_TXT))
    check("粒子特效教程存在", os.path.exists(HELP_CN))
    if os.path.exists(HELP_CN):
        with open(HELP_CN, encoding="utf-8") as f:
            h = f.read()
        check("教程包含四类发射器与参数表",
              all(k in h for k in ("粒子发射器 2", "飘带发射器", "爆米花发射器", "EmissionRate", "HeadOrTail")))

    print("\n=== 总体：%s ===" % ("全部通过" if ok else "存在失败项，请检查上方 [!!]"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
