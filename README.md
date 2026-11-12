# RMS 汉化版（Retera Model Studio · 中文增强版）

> ⚠️ **本项目目前 bug 较多、功能仍不稳定**，原作者精力有限，**非常希望有能力的朋友一起来修复、完善**。如果你熟悉 Java / Swing / 3D 模型编辑器，欢迎提 Issue 或 PR！

## 这是什么

本项目是 [Retera/ReterasModelStudio](https://github.com/Retera/ReterasModelStudio)（一款开源的魔兽争霸 3 模型编辑器，MIT 协议）的**中文汉化 + Blender 风格操作增强**版本，主要面向魔兽地图 / 模型改图爱好者。

在官方 RMS 基础上增加 / 修改了：

- 全中文界面与大量**悬停气泡说明**（约 280 条功能说明）
- **Blender 式快捷键**：`G` 移动、`R` 旋转、`S` 缩放、`E` 挤出、`P` 分离面（抓取式交互，可 X/Y/Z 锁轴，左键确认 / ESC 取消）
- 面模式**防透选**修复（线框模式仍保留透视多选，符合 Blender 习惯）
- 工具栏标签修正：`EXTRUDE` →「分离面」、`EXTEND` →「挤出」（与真实语义一致）
- 界面整体放大（高 DPI 适配）

## 许可证

Retera Model Studio 原项目采用 **MIT License**（Copyright 2023 Retera），本项目沿用其协议并保留版权声明，见仓库根目录 `LICENSE`。

> 说明：RMS 运行时从你本机已安装的魔兽争霸 3 读取游戏素材（模型 / 贴图等），**本仓库不包含任何暴雪游戏资源**，仅包含编辑器程序本身。

## 如何运行（下载即跑）

仓库已内置便携 **OpenJDK 17**（`汉化版/runtime/`，GPLv2 + Classpath Exception，允许再分发），**无需在本机安装 Java**：

1. 下载本仓库（或用 GitHub 的 “Download ZIP”）。
2. 进入 `汉化版/`，双击 **`启动汉化版.bat`** 即可启动。
   - 调试模式（保留控制台、可见报错）用 `启动汉化版_调试模式.bat`。
3. 如果你本机已有 Java 17，也可以只取 `汉化版/` 下的 `RMS_zh_CN.jar` + `rms-tooltips.jar` + `rms-blender.jar`，自行用 `java -cp "RMS_zh_CN.jar;rms-tooltips.jar;rms-blender.jar" BlenderLauncher` 启动。

> 注：因内置了 JDK，`汉化版/runtime/` 约 54MB，导致整个仓库较大。介意克隆体积的话，可只下载 Release 里“不含 JDK”的压缩包（若提供），或自行安装 Java 后仅取 jar。

## 目录结构

| 目录 | 说明 |
|---|---|
| `汉化版/` | **可直接运行的成品**：主程序 jar、`rms-blender.jar` / `rms-tooltips.jar`（汉化与增强注入）、启动脚本、说明文档、内置 JDK |
| `source/` | RMS 上游源码（Gradle 工程，可重新编译） |
| `汉化工具/` | **本项目的汉化与增强改动源码 / 脚本**（如 `BlenderHooks.java`、标签改名脚本等） |

## 如何从源码编译

```bash
cd source
./gradlew build      # 需要联网拉取依赖
```

产物 jar 位于 `source/` 对应模块下（具体见 `source/README.md` 与 `build.gradle`）。
之后把生成的 / 修改的 class 注入到 `汉化版/RMS_zh_CN.jar` 的方式，可参考 `汉化工具/` 下的脚本与说明。

## 已知问题 / Bug（欢迎来修 🛠️）

以下为本项目目前已知或高度可疑的问题，**非常欢迎指教与修复**：

- [ ] **工具栏图标缺失告警**：启动时会刷 `JMpqException: File Not Found <UI\Widgets\ReteraStudio\TabIcons\MDLIcon_16.png>`，属缺失图标资源，被捕获后不影响运行，但会持续写日志，体验差。
- [ ] **挤出 / 分离面语义曾长期搞反**：`EXTRUDE` 实为「分离面」（断开、无侧壁），`EXTEND` 才是真「挤出」（带连接侧壁）。相关标签与快捷键已修正，但交互边界仍可能有遗漏。
- [ ] **面模式选择**：单击可能仍会多选被遮挡的面（已加按质心距离去重，但共面 / 斜对角情况未全覆盖）。
- [ ] **Blender 式抓取交互**：`E` / `P` 等抓取在某些视角 / 模型下行为不符合预期，锁轴与确认逻辑待完善。
- [ ] **中文汉化不全**：可能仍有未翻译字符串、或翻译与界面实际不符之处。
- [ ] **编辑器稳定性**：部分操作偶发异常 / 崩溃，尚未系统排查。
- [ ] （请使用者 / 作者持续补充……）

## 致谢

- [Retera](https://github.com/Retera/ReterasModelStudio) 与原 RMS 项目团队（MIT 协议）。
- 本项目的全部中文汉化与 Blender 风格增强由 **金闪电侠** 完成，AI 助手 **闪电助手** 协助开发。

---

如果本项目对你有帮助，或你修好了上面的某个 bug，欢迎在 Issue / PR 里告诉我 🙂
