#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把「粒子编辑面板」等界面的英文 UI 字面量直接在源码里写成中文。

为什么不放进 汉化词典.json：
  Head / Tail / Modulate / Modulate2x / AlphaKey / LineEmitter / TailLength /
  Time / LifeSpan / Variation 等裸词同时是 MdlUtils.TOKEN_* 的 MDL 数据令牌，
  写进全量词典会让 rms_patch.py apply 把这些令牌也翻成中文，重蹈
  NoSuchFieldError / ClassCastException 的覆辙。
  这些类本来就要重编（修按钮截断、paintComponent 空指针），所以直接写中文最安全。

用法：python 汉化工具/局部汉化粒子面板.py
（幂等：重复执行时英文串已不存在，命中数为 0，不会二次替换）
"""

import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "RMS_Twilac_Source", "modelstudio", "src",
                   "com", "hiveworkshop", "rms")

# 精确匹配完整字符串字面量（连引号一起），不会误伤 "Head UV (...)" 这类复合串
PARTICLE_PANEL = [
    ('"Head/Tail"', '"头部 / 尾部"'),
    ('"Head UV (sub-texture index)"', '"头部 UV（子贴图索引）"'),
    ('"Tail UV (sub-texture index)"', '"尾部 UV（子贴图索引）"'),
    ('"Head"', '"头部"'),
    ('"Tail"', '"尾部"'),
    ('"Both"', '"两者"'),
    ('"Filter Mode"', '"过滤模式"'),
    ('"Blend"', '"混合"'),
    ('"Additive"', '"叠加"'),
    ('"Modulate2x"', '"双倍调制"'),
    ('"Modulate"', '"调制"'),
    ('"AlphaKey"', '"透明键"'),
    ('"Flags"', '"标志"'),
    ('"Unshaded"', '"无光照"'),
    ('"Unfogged"', '"不受雾影响"'),
    ('"LineEmitter"', '"线发射器"'),
    ('"SortPrimsFarZ"', '"远 Z 排序图元"'),
    ('"ModelSpace"', '"模型空间"'),
    ('"XYQuad"', '"XY 四边形"'),
    ('"Squirt"', '"喷射"'),
    ('"Speed"', '"速度"'),
    ('"Variation (%)"', '"变化 (%)"'),
    ('"EmissionRate"', '"发射速率"'),
    ('"Latitude"', '"发射张角"'),
    ('"Gravity"', '"重力"'),
    ('"Width"', '"宽度"'),
    ('"Length"', '"长度"'),
    ('"LifeSpan (~ 0.1 s)"', '"生命周期 (~ 0.1 秒)"'),
    ('"TailLength"', '"尾迹长度"'),
    ('"Time (%)"', '"时间 (%)"'),
    ('"Sub-Textures:"', '"子贴图："'),
    ('"Rows"', '"行"'),
    ('"Columns"', '"列"'),
    ('"Alpha"', '"透明度"'),
    ('"Particle Scaling"', '"粒子缩放"'),
    ('"Life Span"', '"生命周期"'),
    ('"Decay"', '"尾部渐隐"'),
    ('"start"', '"起始"'),
    ('"end"', '"结束"'),
    ('"repeat"', '"重复"'),
    ('"Apply"', '"应用"'),
    ('"Add as new Emitter"', '"添加为新发射器"'),
    ('"Choose Color"', '"选择颜色"'),
    ('"Chooser Color"', '"选择颜色"'),
    ('"(Unanimated)"', '"（无动画）"'),
]

# 其它文件里的零散英文
OTHERS = {
    "ui/application/model/nodepanels/ComponentParticle2Panel.java": [
        ('"Editing "', '"编辑 "'),
    ],
    "ui/application/actionfunctions/CreateFace.java": [
        ('"A face can only be created from exactly 3 vertices (you have "',
         '"创建面必须恰好选中 3 个顶点（当前 "'),
        ('" selected)"', '" 个）"'),
    ],
}


def apply(path, mapping):
    full = os.path.join(SRC, *path.split("/"))
    with io.open(full, encoding="utf-8") as f:
        text = f.read()
    print("=== " + path)
    missed = []
    for old, new in mapping:
        count = text.count(old)
        if count == 0:
            missed.append(old)
        text = text.replace(old, new)
        print("  %2d  %s -> %s" % (count, old, new))
    with io.open(full, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    return missed


def main():
    all_missed = []
    all_missed += apply("ui/application/tools/ParticleEditPanel.java", PARTICLE_PANEL)
    for path, pairs in OTHERS.items():
        all_missed += apply(path, pairs)
    print()
    print("未命中项: %s" % (all_missed if all_missed else "无（全部替换成功）"))


if __name__ == "__main__":
    main()
