#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""生成中文版 credits.rtf / changelist.rtf 并注入主 jar。"""
import os
import struct
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "汉化版", "RMS_zh_CN.jar")
OUT = os.path.join(ROOT, "汉化工具")


def esc(text):
    """RTF 转义：ASCII 直出，非 ASCII 用 \\uN? 。"""
    out = []
    for ch in text:
        if ord(ch) < 128 and ch not in "\\{}":
            out.append(ch)
        else:
            v = ord(ch)
            if v > 32767:
                v -= 65536
            out.append("\\u%d?" % v)
    return "".join(out)


HEADER = r"{\rtf1\ansi\ansicpg1252\deff0\nouicompat\deflang1033{\fonttbl{\f0\fnil\fcharset134 SimSun;}}{\*\generator RmsZh}\viewkind4\uc1" + "\r\n"


def para(text, style=""):
    return "\\pard\\sa120\\sl240\\slmult1" + style + " " + esc(text) + "\\par" + "\r\n"


def build_credits():
    b = [HEADER]
    b.append(para("Retera Model Studio", r"\qc\b\f0\fs40\lang9"))
    b.append(para("一款由 Eric \"Retera\" 用 Java 编写的《魔兽争霸 III》模型编辑器与导入工具，"
                  "补齐了其他同类软件缺失的功能。", r"\qc\f0\fs20"))
    b.append(para("致谢：", r"\qc\b\f0\fs32\lang9"))
    b.append(para("更完整的致谢请见 GitHub 页面的 README.md：", r"\i\f0\fs22"))
    b.append(para("https://github.com/Retera/ReterasModelStudio", r"\f0\fs22"))
    b.append(para("twilac：持续进行功能改进，在「模型」页签补充了 Retera 无暇顾及的材质管理功能，"
                  "并对 0.05 版本做了代码重组。", r"\f0\fs20"))
    b.append(para("BogdanW3：修复了大量 bug，并对源码提出了许多建设性意见。", r"\f0\fs20"))
    b.append(para("Ghostwolf：没有他的开源贡献就没有今天的本工具。近期他研究了《魔兽争霸 III》"
                  "重制版（Reforged）测试版的模型格式并与我分享了心得。更早之前，Ghostwolf 开源了他的"
                  "魔兽 III 动画渲染 OpenGL 代码，正是那段代码教会了我如何把动画系统加入本软件。"
                  "有了它，我才有底气设想单独运行这个程序、而不必依附于 Magos War3ModelEditor 与 "
                  "MdlVis。向 Ghostwolf 致敬！（他编写的 mdx-m3-viewer 系统可以直接在浏览器里运行；"
                  "你在 Hive Workshop 浏览自定义模型时点「View in 3D」用的就是他的代码。）", r"\f0\fs20"))
    b.append(para("Dr Super Good：他在 Hive Workshop（hiveworkshop.com）提供了纯 Java 的 MPQ 解析器"
                  "源码，同时提供了正在使用的 BLP 解析器，为整个程序打下了坚实的地基。加载重制版游戏数据"
                  "所用的 JCASC 也是他写的——1.32.10 补丁导致 CASC 无法读取部分资源时，他在补丁发布后"
                  "24-48 小时内就给出了修复！", r"\f0\fs20"))
    b.append(para("Oger-Lord：他编写了优秀的 Java MDX 库（「使用原生 MDX 解析器」选项即基于它），"
                  "仓库中还有 Java 原生 BLP 读取代码，让本程序的内部实现更可靠。", r"\f0\fs20"))
    b.append(para("Hayate：为我绘制了本工具未来 UI 的设计草稿，他的想法超前于我的进度，"
                  "但确实影响了项目方向。", r"\f0\fs20"))
    b.append(para("CanFight：多次在 Discord 上展示他用本编辑器做出的作品，参与内测并指出缺陷，"
                  "推动我持续开发。", r"\f0\fs20"))
    b.append(para("Unryze：另一位参与内测并做出贡献的用户，「T-Pose 编辑器」正是他提议的功能。", r"\f0\fs20"))
    b.append(para("Shadow Daemon：提供了出色的 BLP→TGA 命令行转换工具（BLP Lab），"
                  "让早期版本的贴图加载器得以工作。", r"\f0\fs20"))
    b.append(para("Oracle/Sun：感谢 Java 语言与 Javadoc 文档！", r"\f0\fs20"))
    b.append(para("Terai Atsuhiro 与 Jay Warrick：感谢他们编写的可拖拽页签 JTabbedPane 代码"
                  "（java-swing-tips 博客），我只做了少量修改。", r"\f0\fs20"))
    b.append(para("LWJGL（轻量级 Java 游戏库）：提供了显示 3D 所需的 OpenGL 绑定——"
                  "「透视」视图全靠它。", r"\f0\fs20"))
    b.append(para("Slick2D：内置于部分 MatrixEater 构建中的优秀 2D 库。", r"\f0\fs20"))
    b.append(para("ZZ Coder 与 StackOverflow 上的朋友们：提供了 TGA 图片加载代码"
                  "（stackoverflow.com/questions/1514035）。", r"\f0\fs20"))
    b.append(para("Ladislav Zezula：StormLib 库的作者。", r"\f0\fs20"))
    b.append(para("TimoHanisch、Deaod 及 GitHub 上 JStormLib 的贡献者们：把 StormLib 封装成 Java 可用的"
                  " JStormLib，让本程序早年能够打开 MPQ 文件。", r"\f0\fs20"))
    b.append(para("seanrowens：编写了 Java 的 OBJ 导入器，本程序的 OBJ 解析基于它。", r"\f0\fs20"))
    b.append(para("—— 中文汉化版由「闪电编辑器」项目附加：界面汉化、悬停说明气泡、"
                  "Blender 风格视口增强。", r"\qc\f0\fs20"))
    b.append("}")
    return "".join(b)


def build_changelog():
    b = [HEADER]
    b.append(para("更新日志", r"\qc\b\f0\fs40\lang9"))
    b.append(para("Retera Model Studio", r"\qc\f0\fs20"))
    b.append(para("v0.05 公开测试版：", r"\b\f0\fs32\lang9"))
    items05 = [
        "代码改为支持 Java 15（自 Java 8 升级），Gradle 配置相应调整。",
        "工程结构从多个相互依赖的子项目（craft3data、craft3editor、matrixeater、disarm）"
        "重组为更清晰的根级布局（modelstudio 与 thirdparty）。",
        "弃用移植版 JCASC，改用官方 JCASC 并以 Gradle 依赖集成。",
        "弃用 DrSuperGood 的 MPQ 解析器（无法作为 Gradle 依赖），改用 Ghostwolf 接入的 JMPQ3。",
        "弃用 Oger-Lord 的 MDX 解析器与 Retera 2012 年的 MDL 解析器：0.05 的模型解析器完全重写，"
        "由 Retera 从 Ghostwolf 的 JavaScript 项目 mdx-m3-viewer 转写为 Java，"
        "再由 Ghostwolf 重组改进、加入重制版支持并与本软件的模型类对接。",
        "沿用自 2012 年的 MDL 类更名为 EditableModel，与解析逻辑解耦。",
        "Ghostwolf 做了大量代码重组，统一了数学库（此前混杂着自制代码与旧版 LWJGL 的弃用类）。",
        "twilac 持续重组代码，并补齐「模型」页签缺失的功能。",
    ]
    for t in items05:
        b.append(para("· " + t, r"\f0\fs20"))
    b.append(para("v0.04：", r"\b\f0\fs32\lang9"))
    b.append(para("· 加入重制版（Reforged）支持及少量新功能，发布了未完成的「模型」页签初稿。"
                  "进度详情见 Hive Workshop 与本项目的 GitHub 提交历史。", r"\f0\fs20"))
    b.append(para("v0.03：", r"\b\f0\fs32\lang9"))
    items03 = [
        "程序改用 Java 11；因 Eclipse 导出 JAR 出现问题又暂时退回 Java 8。",
        "改用最新版 JCASC（需 Java 11），随后修改其代码使其不再强制要求 Java 11。",
        "启动流程改为「数据源选择器」视图（偏好设置中也可用），加载 CASC 或 MPQ 更直观；"
        "CASC 用户可选择游戏语言区域，未下载的区域显示为深红色（需先启动一次对应语言的游戏）。",
        "程序崩溃时会自动回到数据源选择器，方便随时重新配置。",
        "模型预览器加入粒子发射器渲染（上百行代码复刻游戏行为，几乎逐行译自 Ghostwolf 的代码）。",
        "已知问题：Squirter 模式粒子会在不该发射时随机发射；优先平面与图层关系也有问题。",
        "CASC 模式加入 filealiases.json 解析（1.30/1.31 客户端用它映射被移除的旧文件），"
        "修复部分贴图丢失问题。",
        "默认数据目录从 JWC3 改名 ReteraStudio，避免与 Matrix Eater 相互覆盖配置"
        "（该版本会重置用户设置；可从 AppData/Roaming/JWC3 复制旧配置，但不保证兼容）。",
        "修复「快速浏览」开关；「按比例修改动画长度」弹窗加入滚动条并移入「脚本」菜单（感谢 Mythic）。",
        "新增 3 套界面主题：HiFi、Acryl、Aluminium（来自 JTattoo 库）；原「Dark」实为 JTattoo 的 "
        "Noire，已相应改名；默认主题改为 Aluminium。",
        "改进 BLP 加载管线，避免带 alpha 通道的贴图数据被破坏，消除与游戏实际显示不符的视觉瑕疵。",
        "「时间范围选择」面板加入「编辑」按钮（暂不可用，后续版本修复）。",
        "改进程序「X」关闭按钮与模型文件关闭逻辑，避免意外无法关闭。",
        "3D 显示加入宽高比校验，尝试修复开启 UI 缩放的桌面环境下的渲染问题"
        "（Win10 缩放下已测；多显示器配置仍可能失败）。",
    ]
    for t in items03:
        b.append(para("· " + t, r"\f0\fs20"))
    b.append(para("—— 以上由「闪电编辑器」汉化，仅供学习参考。", r"\qc\i\f0\fs18"))
    b.append("}")
    return "".join(b)


def inject(entry, content):
    data = content.encode("latin-1", "replace")
    zin = zipfile.ZipFile(JAR)
    tmp = JAR + ".tmp"
    with zipfile.ZipFile(tmp, "w") as zout:
        for info in zin.infolist():
            payload = data if info.filename == entry else zin.read(info.filename)
            ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.internal_attr = info.internal_attr
            ni.create_system = info.create_system
            ni.comment = info.comment
            zout.writestr(ni, payload)
    zin.close()
    os.replace(tmp, JAR)
    print("已替换", entry, len(data), "bytes")


if __name__ == "__main__":
    open(os.path.join(OUT, "_credits_zh.rtf"), "w", encoding="ascii").write(build_credits())
    open(os.path.join(OUT, "_changelist_zh.rtf"), "w", encoding="ascii").write(build_changelog())
    inject("docs/credits.rtf", build_credits())
    inject("docs/changelist.rtf", build_changelog())
    print("完成。")
