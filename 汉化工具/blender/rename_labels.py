#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安全改名 v2（防重复改名版）：
  1. 始终以「已知干净基线」备份 备份/RMS_zh_CN.jar.before-extrude-detach-rename.bak 为准；
     若当前 RMS_zh_CN.jar 不符合干净基线，先自动还原再操作，杜绝重复改名。
  2. 用「完整 UTF8 条目字节」(tag 0x01 + 2字节长度 + 内容) 做二进制替换，不做常量池解析，
     因此绝不会因偏移漂移而漏改/错改；长度变化由长度前缀自动处理。
  3. 改名前后各做一次断言（每个目标类的 挤出/分离面/法线推移 出现次数必须符合预期），
     任一不符立即中止，绝不写回主 jar。
  4. 每个类改完用 javap 解析校验，整体写回前再 testzip。

目标（用户实测结论）：
  EXTRUDE(工具3, 实为分离面) 标签 「挤出」 -> 「分离面」
  EXTEND (工具4, 实为挤出)   标签 「法线推移」 -> 「挤出」
"""
import zipfile, struct, os, subprocess, sys, tempfile, shutil

SRC = "RMS_zh_CN.jar"
CLEAN_BAK = "备份/RMS_zh_CN.jar.before-extrude-detach-rename.bak"
JAVAP = "C:/Users/13068/Desktop/魔兽特效插件/_toolchain/jdk-17/bin/javap.exe"

TARGETS = [
    "com/hiveworkshop/rms/ui/gui/modeledit/toolbar/ModelEditorActionType3.class",
    "com/hiveworkshop/rms/ui/application/actionfunctions/ActivateEditingMode.class",
    "com/hiveworkshop/rms/ui/application/actionfunctions/ActivateEditingMode$Extrude.class",
    "com/hiveworkshop/rms/ui/application/actionfunctions/ActivateEditingMode$Extend.class",
]
SHORT = {c: c.split("/")[-1] for c in TARGETS}

# 完整 UTF8 常量条目 = 0x01 + 长度(uint16) + utf8 字节
def entry(s):
    b = s.encode("utf-8")
    return b"\x01" + struct.pack(">H", len(b)) + b

REPL = [
    (entry("挤出"),    entry("分离面")),   # EXTRUDE = 分离面
    (entry("法线推移"), entry("挤出")),     # EXTEND  = 挤出
]

LABELS = {
    "挤出":   bytes.fromhex("e68ca4e587ba"),
    "分离面": bytes.fromhex("e58886e7a6bbe99da2"),
    "法线推移": bytes.fromhex("e6b395e7babfe68ea8e7a7bb"),
}

# 干净基线（改名前）每个类的标签出现次数
PRE = {
    "ModelEditorActionType3.class":        dict(挤出=1, 分离面=0, 法线推移=1),
    "ActivateEditingMode.class":           dict(挤出=1, 分离面=0, 法线推移=1),
    "ActivateEditingMode$Extrude.class":   dict(挤出=1, 分离面=0, 法线推移=0),
    "ActivateEditingMode$Extend.class":    dict(挤出=0, 分离面=0, 法线推移=1),
}
# 改名后（预期最终态）
POST = {
    "ModelEditorActionType3.class":        dict(挤出=1, 分离面=1, 法线推移=0),
    "ActivateEditingMode.class":           dict(挤出=1, 分离面=1, 法线推移=0),
    "ActivateEditingMode$Extrude.class":   dict(挤出=0, 分离面=1, 法线推移=0),
    "ActivateEditingMode$Extend.class":    dict(挤出=1, 分离面=0, 法线推移=0),
}


def counts(b):
    return {k: b.count(v) for k, v in LABELS.items()}


def patch_class(data):
    out = data
    for old, new in REPL:
        out = out.replace(old, new)
    return out


def validate_class(class_bytes, class_name):
    d = tempfile.mkdtemp()
    try:
        path = os.path.join(d, class_name.replace(".", os.sep) + ".class")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(class_bytes)
        r = subprocess.run([JAVAP, "-p", "-classpath", d, class_name],
                           capture_output=True, timeout=60)
        if r.returncode != 0:
            print("    javap stdout:", (r.stdout or b"").decode("utf-8", "replace")[:300])
            print("    javap stderr:", (r.stderr or b"").decode("utf-8", "replace")[:300])
        return r.returncode == 0
    finally:
        shutil.rmtree(d, ignore_errors=True)


def ensure_clean_base():
    """若当前 SRC 不符合干净基线，则从 CLEAN_BAK 还原。返回是否做过还原。"""
    try:
        z = zipfile.ZipFile(SRC)
        ok = True
        for c in TARGETS:
            cnt = counts(z.read(c))
            if any(cnt[k] != PRE[SHORT[c]][k] for k in PRE[SHORT[c]]):
                ok = False
                break
    except Exception as e:
        print(f"读取 SRC 失败：{e}，将还原干净基线")
        ok = False
    if ok:
        print("当前 jar 已是干净基线，无需还原。")
        return False
    print(f"当前 jar 非干净基线，正在从 {CLEAN_BAK} 还原 ...")
    with open(CLEAN_BAK, "rb") as f:
        blob = f.read()
    with open(SRC, "wb") as f:
        f.write(blob)
    # 复检
    z = zipfile.ZipFile(SRC)
    for c in TARGETS:
        cnt = counts(z.read(c))
        assert all(cnt[k] == PRE[SHORT[c]][k] for k in PRE[SHORT[c]]), \
            f"还原后 {SHORT[c]} 仍不符合干净基线: {cnt}"
    print("已还原到干净基线。")
    return True


def main():
    ensure_clean_base()

    z = zipfile.ZipFile(SRC)
    patched = {}
    for name in TARGETS:
        data = z.read(name)
        # 改名前断言
        pre = counts(data)
        assert all(pre[k] == PRE[SHORT[name]][k] for k in PRE[SHORT[name]]), \
            f"PRE 断言失败 {SHORT[name]}: 实际 {pre} 期望 {PRE[SHORT[name]]}"
        nd = patch_class(data)
        # 改名后断言
        post = counts(nd)
        assert all(post[k] == POST[SHORT[name]][k] for k in POST[SHORT[name]]), \
            f"POST 断言失败 {SHORT[name]}: 实际 {post} 期望 {POST[SHORT[name]]}"
        if not validate_class(nd, name[:-6].replace("/", ".")):
            print(f"✗ javap 校验失败（类已损坏）{name}，已中止，主 jar 未改动")
            sys.exit(1)
        patched[name] = nd
        print(f"✓ 已重写并校验通过: {SHORT[name]:34s} 前{pre} -> 后{post}")

    # 写新 jar 到临时文件
    tmp = SRC + ".new"
    with zipfile.ZipFile(SRC) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            d = patched.get(info.filename, zin.read(info.filename))
            zout.writestr(info, d)

    z2 = zipfile.ZipFile(tmp)
    bad = z2.testzip()
    assert bad is None, f"新 jar 损坏于：{bad}"
    print("整体 testzip: OK")

    # 整体覆盖主 jar（不依赖 os.replace 原子重命名）
    with open(tmp, "rb") as f:
        blob = f.read()
    with open(SRC, "wb") as f:
        f.write(blob)
    print(f"已写回主 jar（{len(blob)} 字节）")

    # 最终核对
    z3 = zipfile.ZipFile(SRC)
    print("\n最终标签核对：")
    allok = True
    for c in TARGETS:
        cnt = counts(z3.read(c))
        ok = all(cnt[k] == POST[SHORT[c]][k] for k in POST[SHORT[c]])
        allok &= ok
        print(f"  {SHORT[c]:34s} {cnt} -> {'OK' if ok else 'WRONG'}")
    print("\n全部符合预期:", allok)
    if not allok:
        sys.exit(1)


if __name__ == "__main__":
    main()
