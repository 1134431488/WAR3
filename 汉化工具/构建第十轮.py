#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
第十轮统一构建入口：修右键添加粒子父级预选失效、粒子参数面板两列布局与时间条、组件面板宽度撑满。

用法：
    python 汉化工具/构建第九轮.py --dry-run
    python 汉化工具/构建第九轮.py
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
BLENDER_BACKUP = os.path.join(ROOT, "汉化版", "备份", "rms-blender.jar.before-round10.bak")

# 第九轮 31 个 + 本轮新增 1 个（ComponentsPanel：面板宽度撑满）
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
    "com/hiveworkshop/rms/ui/application/MenuBar1/TeamColorMenu.java",
    "com/hiveworkshop/rms/editor/render3d/RenderParticle2.java",
    "com/hiveworkshop/rms/ui/application/model/nodepanels/ComponentParticlePanel.java",
    "com/hiveworkshop/rms/ui/application/model/nodepanels/ComponentPopcornPanel.java",
    "com/hiveworkshop/rms/ui/application/model/nodepanels/ComponentLightPanel.java",
    "com/hiveworkshop/rms/ui/application/model/ComponentCameraPanel.java",
    "com/hiveworkshop/rms/ui/application/model/material/ComponentTextureAnimPanel.java",
    # ---- 第九轮新增 ----
    "com/hiveworkshop/rms/ui/application/AddParticlePanel.java",
    "com/hiveworkshop/rms/ui/gui/modeledit/toolbar/ModeButton2.java",
    "com/hiveworkshop/rms/ui/util/ModeButton.java",
    "com/hiveworkshop/rms/ui/application/edit/animation/TimeBarPainter.java",
    # ---- 第十轮新增 ----
    "com/hiveworkshop/rms/ui/application/model/ComponentsPanel.java",
    # ---- 第十一轮新增 ----
    "com/hiveworkshop/rms/util/TinyToggleButton.java",
    "com/hiveworkshop/rms/util/Vec3SpinnerArray.java",
    # ---- 第十三轮新增（动画预览滚轮切换除零崩溃修复）----
    "com/hiveworkshop/rms/ui/application/viewer/AnimationController.java",
]

B.PREFIXES = [s[:-5] for s in B.SOURCES]
B.RELEASE = "14"
B.BACKUP = os.path.join(ROOT, "汉化版", "备份", "RMS_zh_CN.jar.before-round10.bak")
B.BUILD = os.path.join(ROOT, "_sandbox", "round10build")
B.EXPECTED_NATIVE_CJK = [
    "切换当前编辑的动画序列",
    "搜索模型组件",
    "首帧",
    "从 war3 本体导入特效",
    "特效预览",
    "常用编辑",
    "绑定父级",
    "发射速率",
]


def rebuild_blender_jar():
    print("")
    print("===== 重编 rms-blender.jar（BlenderHooks / BlenderButtonStyle） =====")
    os.makedirs(os.path.dirname(BLENDER_BACKUP), exist_ok=True)
    if not os.path.exists(BLENDER_BACKUP):
        shutil.copy2(BLENDER_JAR, BLENDER_BACKUP)
        print("已备份增强层 -> " + BLENDER_BACKUP)
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
    assert "BlenderButtonStyle.class" in names, "缺少 BlenderButtonStyle"
    assert bad is None, "rms-blender.jar 校验失败: %s" % bad
    print("rms-blender.jar: testzip OK, 条目数 %d" % len(names))


def main():
    dry_run = "--dry-run" in sys.argv
    B.main()
    if not dry_run:
        rebuild_blender_jar()
        print("")
        print("第十轮构建完成：主 jar 与 rms-blender.jar 均已更新。")


if __name__ == "__main__":
    main()
