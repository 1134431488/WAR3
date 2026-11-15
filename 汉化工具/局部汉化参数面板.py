#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把各节点/材质面板里 ValuePanel 的英文标题直接写成中文。

背景：这些标题的英文词（Speed / Gravity / EmissionRate / Visibility / Alpha …）
同时是 MdlUtils.TOKEN_* 的值。上一轮为避免污染内部键，构建脚本的
FORBIDDEN_ENG 黑名单拦下了它们的词典注入，结果界面反而残留英文。

正确做法：只改「构造 ValuePanel 时传入的标题」为中文字面量，
reloadNewValue() 里作为内部键的 MdlUtils.TOKEN_* 一个都不动。
javac 编译后中文标题与英文 token 是常量池里两个独立 UTF8，互不干扰。

用法：python 汉化工具/局部汉化参数面板.py   （幂等）
"""

import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "RMS_Twilac_Source", "modelstudio", "src",
                    "com", "hiveworkshop", "rms", "ui", "application", "model")

# 每项：(相对路径, [(原串, 新串), ...])，原串都是完整调用片段，避免误伤内部键
PATCHES = {
    "nodepanels/ComponentParticlePanel.java": [
        ('new FloatValuePanel(modelHandler, "Longitude")', 'new FloatValuePanel(modelHandler, "经度")'),
        ('new FloatValuePanel(modelHandler, "Latitude")', 'new FloatValuePanel(modelHandler, "发射张角")'),
        ('new FloatValuePanel(modelHandler, "Speed")', 'new FloatValuePanel(modelHandler, "速度")'),
        ('new FloatValuePanel(modelHandler, "Gravity")', 'new FloatValuePanel(modelHandler, "重力")'),
        ('new FloatValuePanel(modelHandler, "EmissionRate")', 'new FloatValuePanel(modelHandler, "发射速率")'),
        ('new FloatValuePanel(modelHandler, "Visibility")', 'new FloatValuePanel(modelHandler, "可见性")'),
    ],
    "nodepanels/ComponentPopcornPanel.java": [
        ('new FloatValuePanel(modelHandler, "LifeSpan")', 'new FloatValuePanel(modelHandler, "生命周期")'),
        ('new FloatValuePanel(modelHandler, "EmissionRate")', 'new FloatValuePanel(modelHandler, "发射速率")'),
        ('new FloatValuePanel(modelHandler, "Speed")', 'new FloatValuePanel(modelHandler, "速度")'),
        ('new FloatValuePanel(modelHandler, MdlUtils.TOKEN_ALPHA)', 'new FloatValuePanel(modelHandler, "透明度")'),
        ('new FloatValuePanel(modelHandler, "Visibility")', 'new FloatValuePanel(modelHandler, "可见性")'),
        ('new ColorValuePanel(modelHandler, MdlUtils.TOKEN_COLOR)', 'new ColorValuePanel(modelHandler, "颜色")'),
    ],
    "nodepanels/ComponentLightPanel.java": [
        ('new FloatValuePanel(modelHandler, MdlUtils.TOKEN_VISIBILITY)', 'new FloatValuePanel(modelHandler, "可见性")'),
        ('new FloatValuePanel(modelHandler, MdlUtils.TOKEN_ATTENUATION_START)', 'new FloatValuePanel(modelHandler, "衰减起点")'),
        ('new FloatValuePanel(modelHandler, MdlUtils.TOKEN_ATTENUATION_END)', 'new FloatValuePanel(modelHandler, "衰减终点")'),
        ('new FloatValuePanel(modelHandler, MdlUtils.TOKEN_INTENSITY)', 'new FloatValuePanel(modelHandler, "强度")'),
        ('new Vec3ValuePanel(modelHandler, MdlUtils.TOKEN_COLOR)', 'new Vec3ValuePanel(modelHandler, "颜色")'),
        ('new FloatValuePanel(modelHandler, MdlUtils.TOKEN_AMB_INTENSITY)', 'new FloatValuePanel(modelHandler, "环境光强度")'),
        ('new Vec3ValuePanel(modelHandler, MdlUtils.TOKEN_AMB_COLOR)', 'new Vec3ValuePanel(modelHandler, "环境光颜色")'),
    ],
    "ComponentCameraPanel.java": [
        ('new Vec3ValuePanel(modelHandler, MdlUtils.TOKEN_TRANSLATION + " Target")',
         'new Vec3ValuePanel(modelHandler, "目标位置")'),
        ('new Vec3ValuePanel(modelHandler, MdlUtils.TOKEN_TRANSLATION)',
         'new Vec3ValuePanel(modelHandler, "位移")'),
        ('new FloatValuePanel(modelHandler, MdlUtils.TOKEN_ROTATION)',
         'new FloatValuePanel(modelHandler, "旋转")'),
    ],
    "material/ComponentTextureAnimPanel.java": [
        ('new Vec3ValuePanel(modelHandler, MdlUtils.TOKEN_TRANSLATION)',
         'new Vec3ValuePanel(modelHandler, "位移")'),
        ('new Vec3ValuePanel(modelHandler, MdlUtils.TOKEN_SCALING)',
         'new Vec3ValuePanel(modelHandler, "缩放")'),
        ('new Vec3ValuePanel(modelHandler, MdlUtils.TOKEN_ROTATION)',
         'new Vec3ValuePanel(modelHandler, "旋转")'),
    ],
    # material/ComponentSDLayer.java 引用了源码里存在、成品 jar 里却没有的
    # SetLayerTextureAnimAction，无法安全重编，故不改它（保持源码与 jar 一致）。
}


def main():
    total = 0
    missed = []
    for rel, pairs in PATCHES.items():
        path = os.path.join(BASE, *rel.split("/"))
        with io.open(path, encoding="utf-8") as f:
            text = f.read()
        print("=== " + rel)
        for old, new in pairs:
            count = text.count(old)
            if count == 0:
                missed.append(rel + " :: " + old)
            text = text.replace(old, new)
            total += count
            print("  %2d  %s" % (count, old))
        with io.open(path, "w", encoding="utf-8", newline="") as f:
            f.write(text)
    print()
    print("替换总数: %d" % total)
    print("未命中: %s" % (missed if missed else "无"))


if __name__ == "__main__":
    main()
