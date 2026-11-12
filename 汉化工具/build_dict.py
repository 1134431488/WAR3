#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
根据 词典源.txt + 术语表.txt + jar 常量池词条清单，生成最终 汉化词典.json

用法：python build_dict.py <jar_strings.json> [输出的汉化词典.json]

生成规则：
  1. 精确词条：词典源.txt 中逐条 "英文=中文"
  2. 自动变体：为词条生成 "X:" / "X: " / "X " 等常见 UI 标签形式
  3. 组合翻译：对 jar 中真实存在的多词短语，用词表逐词翻译（仅在全部词可译时启用）
  4. 安全过滤：文件名、路径、布局约束、内部标识符一律不翻译
"""

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

# 这些字符串既是显示文本、又是程序内部的技术键（布局约束 / switch 分支 /
# 新建节点名前缀 / InputMap 键等）。翻译它们会改变程序逻辑，一律保留英文。
PROTECTED_KEYS = {
    # AWT BorderLayout / CardLayout 约束（散布在大量 UI 类中，无法按类排除）
    "Center", "North", "South", "East", "West",
    "First", "Last", "Before", "After",
    "LineStart", "LineEnd", "PageStart", "PageEnd",
    "BEFORE_FIRST_LINE", "AFTER_LAST_LINE", "PAGE_START", "PAGE_END", "LINE_START", "LINE_END",
}
# KeyStroke.getKeyStroke() 的参数，形如 "control Z" / "control shift S"
KEYSTROKE_RE = re.compile(r"^(control|ctrl|alt|shift|meta)\b", re.I)


def is_protected(en):
    base = en.strip().rstrip(": ")
    return base in PROTECTED_KEYS or bool(KEYSTROKE_RE.match(base))

# 绝不能翻译的技术性内容
SKIP_PATTERNS = [
    r"\.(png|jpg|jpeg|blp|dds|tga|bmp|gif|mdx|mdl|mpq|w3m|w3x|w3u|w3d|wts|slk|txt|html|java|class|jar|exe|dll|db|json|xml|properties|obj|fbx|mtl|rtf|ico|xcf)$",
    r"^(WESTRING|ToolBarIcon|Shader_|MDLIcon|ComboBox|ComboPopup|ToolBar|unitClass|Bone_|Mesh\d|ABILITY|ANIM|BUFF|DEST|DOOD|ITEM|UNIT|UPGR|ABIL|LOCALE)",
    r"^(mouse|key|action|state|focus|window|component|item|table|tree|list|caret|popup|property|hierarchy|ancestor|vetoable)[A-Z]",
    r"^[A-Za-z]+_[A-Za-z0-9_]+$",           # 内部标识符
    r"^[a-z][A-Za-z0-9]*[A-Z][A-Za-z0-9]*$",  # 驼峰标识符
    r"^[\[\]a-zA-Z0-9 ,%:]+$",               # 交给下面的布局关键字判断
]
LAYOUT_WORDS = re.compile(
    r"\b(grow|growx|growy|wrap|fill|spanx|spany|span|gap|gapx|gapy|ins|align|alignx|aligny|"
    r"cell|push|pushx|pushy|hidemode|dock|split|sg|sgx|sgy|north|south|west|east|shrink|top|bottom)\b",
    re.I,
)

# 组合翻译用词表（小写键）
TERMS_FILE = os.path.join(HERE, "术语表.txt")
# 只丢弃真正的冠词/系动词；介词与量词交给词表翻译，避免语义丢失
STOPWORDS = {
    "a", "an", "the", "of", "is", "are", "was", "were", "be", "been", "will",
    "it", "this", "that", "your", "which", "has", "have", "can",
}


def load_pairs(path):
    pairs = {}
    if not os.path.exists(path):
        return pairs
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.lstrip().startswith("#"):
                continue
            if "=" not in line:
                continue
            en, zh = line.split("=", 1)
            if en and zh:
                pairs[en] = zh
    return pairs


def is_safe(s):
    """判断一条字符串是否可能是可翻译的界面文本。"""
    if not s or len(s) > 120:
        return False
    if not re.search(r"[A-Za-z]", s):
        return False
    for p in SKIP_PATTERNS[:-1]:
        if re.search(p, s, re.I):
            return False
    # MigLayout 约束
    if re.fullmatch(r"[\[\]a-zA-Z0-9 ,%:]+", s):
        if LAYOUT_WORDS.search(s):
            return False
    if "/" in s or "\\" in s:
        return False
    if s.startswith("#") or s.startswith("."):
        return False
    # 单个全大写缩写（ABIL / UNIT / MDL ...）不翻
    if re.fullmatch(r"[A-Z0-9_]{2,8}", s):
        return False
    return True


def variant_key(en, zh):
    """为精确词条生成常见 UI 标签变体。"""
    out = {}
    if en.endswith(": ") or en.endswith(":") or en.endswith(" "):
        return out
    # 只给"短语/单词标签"生成冒号变体，句子不生成
    if len(en) <= 30 and not re.search(r"[.!?]", en):
        out[en + ":"] = zh + "："
        out[en + ": "] = zh + "："
        out[en + " "] = zh
    return out


SHORTCUT_RE = re.compile(r"^control (shift )?(.+)$", re.I)


def shortcut_text(text):
    """把 'control shift S' 这类快捷键显示文本规范成 'Ctrl+Shift+S'。"""
    m = SHORTCUT_RE.match(text.strip())
    if not m:
        return None
    key = m.group(2).strip()
    if key.upper() == "SPACE":
        key = "空格"
    elif re.fullmatch(r"[A-Za-z0-9]", key):
        key = key.upper()
    elif re.fullmatch(r"F\d{1,2}", key, re.I):
        key = key.upper()
    else:
        return None
    prefix = "Ctrl+Shift+" if m.group(1) else "Ctrl+"
    lead = " " if text.startswith(" ") else ""
    trail = " " if text.endswith(" ") else ""
    return lead + prefix + key + trail


def combo_translate(text, terms):
    """仅在所有实词都能在词表中找到时，组合翻译一条多词短语。"""
    if not is_safe(text):
        return None
    core = text.strip()
    if " " not in core:
        return None
    # 分离尾随标点
    m = re.match(r"^(.*?)([:!?.,]+)$", core)
    tail = ""
    if m:
        core, tail = m.group(1), m.group(2)
    words = core.split(" ")
    if not (2 <= len(words) <= 5):
        return None
    pieces = []
    for w in words:
        low = w.lower()
        if low in STOPWORDS:
            continue
        if low in terms:
            pieces.append(terms[low])
        elif w in terms:
            pieces.append(terms[w])
        else:
            return None
    if not pieces:
        return None
    tail_map = {":": "：", "!": "！", "?": "？", ".": "。"}
    zh_tail = "".join(tail_map.get(c, c) for c in tail)
    zh = "".join(pieces) + zh_tail
    # 保留原字符串的前后空格
    lead = " " if text.startswith(" ") else ""
    trail = " " if text.endswith(" ") and not tail else ""
    return lead + zh + trail


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    strings_json = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, "汉化词典.json")

    with open(strings_json, encoding="utf-8") as f:
        raw = json.load(f)
    jar_strings = set()
    for item in raw:
        jar_strings.add(item["en"] if isinstance(item, dict) else item)

    exact = load_pairs(os.path.join(HERE, "词典源.txt"))
    terms = {k.lower(): v for k, v in load_pairs(TERMS_FILE).items()}

    mapping = {}
    # 1. 精确词条（只保留 jar 中真实存在的，避免无用条目）
    hit_exact = 0
    for en, zh in exact.items():
        if en in jar_strings:
            mapping[en] = zh
            hit_exact += 1
    # 2. 自动变体（含标签冒号形式与尾部空格形式）
    for en, zh in exact.items():
        for k, v in variant_key(en, zh).items():
            if k in jar_strings and k not in mapping:
                mapping[k] = v
        for suffix in (" ", "  ", "   "):
            k = en + suffix
            if k in jar_strings and k not in mapping:
                mapping[k] = zh + suffix
    # 3. 注意：形如 "control Z" 的字符串是 KeyStroke.getKeyStroke() 的参数，
    #    属于技术键而非显示文本，绝不转换（shortcut_text 保留仅作工具函数）。
    shortcuts = 0
    # 4. 组合翻译长尾
    combo = 0
    for s in jar_strings:
        if s in mapping or not is_safe(s):
            continue
        zh = combo_translate(s, terms)
        if zh and zh != s:
            mapping[s] = zh
            combo += 1

    # 5. 最后一道安全闸：剔除所有技术键
    removed = [k for k in mapping if is_protected(k)]
    for k in removed:
        del mapping[k]

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(mapping, f, ensure_ascii=False, indent=1, sort_keys=True)
    print("jar 中不同字符串: %d" % len(jar_strings))
    print("精确词条命中: %d，组合翻译: %d，安全闸剔除技术键: %d"
          % (hit_exact, combo, len(removed)))
    if removed:
        print("剔除示例:", removed[:12])
    print("最终词典共 %d 条 -> %s" % (len(mapping), out_path))


if __name__ == "__main__":
    main()
