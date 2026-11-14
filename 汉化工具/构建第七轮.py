#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
第七轮统一构建入口：界面交互修复（A/B/C）+ war3 原生特效导入（D）。

复用 构建界面修复.py 的「编译 -> 差分定向注入中文 -> 备份 -> 替换主 jar -> 验证」
整条链，只覆盖源码清单、release 级别与备份名；随后重编 rms-blender.jar
（BlenderHooks 本轮改了 CTRL+F 改键、节点树删除菜单与 Delete 键）。

用法：
    python 汉化工具/构建第七轮.py --dry-run   # 只看注入映射，不改任何文件
    python 汉化工具/构建第七轮.py             # 全量构建（含增强层重编）
"""

import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import 构建界面修复 as B  # noqa: E402

ROOT = os.path.dirname(HERE)
BLENDER_BUILD = os.path.join(HERE, "blender", "构建Blender模式.py")
BLENDER_JAR = os.path.join(ROOT, "汉化版", "rms-blender.jar")
BLENDER_BACKUP = os.path.join(ROOT, "汉化版", "备份", "rms-blender.jar.before-round7.bak")

# 本轮要重编的全部源码（上一轮 7 个 + 本轮 13 个）
B.SOURCES = [
    "com/hiveworkshop/rms/ui/application/model/ComponentPanel.java",
    "com/hiveworkshop/rms/editor/render3d/RenderParticleEmitter2.java",
    "com/hiveworkshop/rms/ui/application/model/material/ComponentLayerPanel.java",
    "com/hiveworkshop/rms/ui/application/edit/animation/TimeSliderPanel.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ComponentThingTree.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ModelViewManagingView.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelviewtree/ComponentTreeNode.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelcomponenttree/ModelComponentsView.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/modelcomponenttree/ModelComponentBrowserTree.java",
    "com/hiveworkshop/rms/ui/application/edit/mesh/viewport/ViewportPopupMenu.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/creator/CreatorModelingPanel.java",
    "com/hiveworkshop/rms/util/FramePopup.java",
    "com/hiveworkshop/rms/ui/application/tools/ParticleEditPanel.java",
    "com/hiveworkshop/rms/ui/application/model/nodepanels/ComponentParticle2Panel.java",
    "com/hiveworkshop/rms/ui/application/actionfunctions/CreateFace.java",
    "com/hiveworkshop/rms/ui/application/model/editors/ValuePanel.java",
    "com/hiveworkshop/rms/ui/application/model/nodepanels/ComponentRibbonPanel.java",
    "com/hiveworkshop/rms/ui/application/edit/mesh/viewport/DisplayViewUgg.java",
    "com/hiveworkshop/rms/ui/application/War3EffectImporter.java",
    "com/hiveworkshop/rms/ui/application/MenuBar1/AddMenu.java",
]

B.PREFIXES = [s[:-5] for s in B.SOURCES]  # 去掉 ".java"

# ModelComponentBrowserTree / MdlxParticleEmitter2 等源码含 Java 14 switch 表达式，
# --release 8 编不了；主 jar 里 class 版本本就混合（52/58），JVM 17 均可加载。
B.RELEASE = "14"
B.BACKUP = os.path.join(ROOT, "汉化版", "备份", "RMS_zh_CN.jar.before-round7.bak")
B.BUILD = os.path.join(ROOT, "_sandbox", "round7build")
B.EXPECTED_NATIVE_CJK = [
    "切换当前编辑的动画序列",
    "搜索节点名称",
    "搜索模型组件",
    "首帧",
    "从 war3 本体导入特效",
]


def rebuild_blender_jar():
    print("")
    print("===== 重编 rms-blender.jar（BlenderHooks） =====")
    os.makedirs(os.path.dirname(BLENDER_BACKUP), exist_ok=True)
    if not os.path.exists(BLENDER_BACKUP):
        shutil.copy2(BLENDER_JAR, BLENDER_BACKUP)
        print("已备份增强层 -> " + BLENDER_BACKUP)
    # --skip-grid 避免重写主 jar 的 GridPainter，--skip-prefs 避免改用户偏好
    result = subprocess.run(
        [sys.executable, BLENDER_BUILD, "--skip-grid", "--skip-prefs"],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.stdout:
        print(result.stdout[-3000:])
    if result.stderr:
        print("STDERR: " + result.stderr[-3000:])
    if result.returncode != 0:
        raise SystemExit("增强层重编失败 (rc=%d)" % result.returncode)
    with zipfile.ZipFile(BLENDER_JAR, "r") as zf:
        names = zf.namelist()
        bad = zf.testzip()
    assert "BlenderHooks$BlenderKeys.class" in names, "缺少 BlenderKeys"
    assert bad is None, "rms-blender.jar 校验失败: %s" % bad
    print("rms-blender.jar: testzip OK, 条目数 %d" % len(names))


def main():
    dry_run = "--dry-run" in sys.argv
    B.main()  # 内部自行读取 sys.argv 判断是否 dry-run / inject-only
    if not dry_run:
        rebuild_blender_jar()
        print("")
        print("第七轮构建完成：主 jar 与 rms-blender.jar 均已更新。")


if __name__ == "__main__":
    main()
