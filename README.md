# RMS 汉化版（Retera Model Studio · 中文增强版）

> ⚠️ **本项目目前 bug 较多、功能仍不稳定**，原作者精力有限，**非常希望有能力的朋友一起来修复、完善**。如果你熟悉 Java / Swing / 3D 模型编辑器，欢迎提 Issue 或 PR！

## 这是什么

本项目是 [Retera/ReterasModelStudio](https://github.com/Retera/ReterasModelStudio)（一款开源的魔兽争霸 3 模型编辑器，MIT 协议）的**中文汉化 + Blender 风格操作增强**版本，主要面向魔兽地图 / 模型改图爱好者。

在官方 RMS 基础上增加 / 修改了：

- 全中文界面与大量**悬停气泡说明**（约 300 条）；粒子参数统一改成一眼能懂的大白话，并修正生命周期/透明度/可见性/子贴图等错误说明
- 粒子参数名支持在 `提示说明.txt` 里用 `!标签!原名=新名` 自定义改写，改文本重启即可，无需重编程序
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

- [x] **挤出 / 分离面语义搞反（已修复）**：`EXTRUDE` 实为「分离面」（断开、无侧壁），`EXTEND` 才是真「挤出」（带连接侧壁）。此前 E/P 键位、工具栏按钮文字、帮助文档与悬停提示互相矛盾，现已全部统一为 `E = 挤出`、`P = 分离面`。
- [x] **挤出后新面和点无法操作（已修复）**：上游 `ExtrudeAction` / `ExtendAction` 只把新几何加进 `Geoset`，未通知 `ModelView` 刷新 `editableVertices` 缓存，导致拾取时被 `isEditable()` 过滤掉。现已补结构同步并自动选中新顶点。
- [x] **粒子参数名生硬、说明有错（已修复）**：原说明把生命周期写成「约 0.1 秒」、透明度写成「255 为不透明」、可见性写成「大于 0 显示」（实际阈值 0.75）、子贴图写成「随机取一格」（实际按生命进度顺序播放）；气泡注入器还会用泛词模糊匹配把错误说明套到不相干的按钮上。现已全部改为大白话、补全丝带专属参数说明、修正单位，并改成最长精确匹配。
- [x] **爆米花粒子参数不可见等三个面板 BUG（已修复）**：`ComponentPopcornPanel` 的 6 个参数控件创建了却没 add 到界面；第一代面板「可见性」错绑到 `EMISSION_RATE` 会污染数据；第二代面板切换节点时刷新下拉框会往撤销栈压入假动作、`editParticle` 按钮残留英文。均已修复。
- [ ] **粒子渲染层仍有上游缺陷**：`RenderModel` 只把 `ParticleEmitter2` 送入渲染管线，第一代 / 爆米花 / 丝带三类发射器在视口不渲染；`RenderParticleEmitter2` 有 `glBegin` 后提前 return 漏 `glEnd`、rows/columns 为 0 时除零、`ALPHAKEY` 未启用 alpha test、Squirt 只支持 DontInterp 插值。这些改动面大，尚未处理。
- [ ] **粒子关键帧标量类型缺陷**：`AnimFlag` 把 Visibility / EmissionRate / Speed / Gravity 等所有标量关键帧都当成 ALPHA（typeid 0），导致只在一个动画打过关键帧时，其它动画里该参数会变成 1.0 而不是模型静态值。属全局动画体系重构，尚未处理。
- [ ] **添加粒子不可撤销 / 预设缺失时静默失败**：`AddParticlePanel` 的三个添加入口直接 `model.add`，未压入 `UndoAction`；预设贴图缺失时 `ImageIO.read(null)` 抛 `IllegalArgumentException`（非 IOException）不被捕获，可能让「添加 → 粒子」子菜单构建中断或点了没反应。
- [ ] **粒子面板静态可见性仍不可编辑**：多个面板的 `visibilityPanel` setter 为 null，爆米花模型的 `setStaticVis` 是空实现；关键帧轨道可编辑，但静态值改不了。
- [ ] **缺少魔兽 3 游戏素材时的资源告警**：未配置游戏目录时，启动阶段会在控制台刷 `JMpqException: File Not Found <...>`（主要是 `stock\particles\*.png` 粒子贴图，以及少量 TabIcons 图标）。异常被捕获，不影响运行；正式启动脚本使用 `javaw.exe`（无控制台），不显示也不写日志文件，仅调试模式可见。
- [ ] **面模式选择**：单击可能仍会多选被遮挡的面（已加按质心距离去重，但共面 / 斜对角情况未全覆盖）。
- [ ] **E / P 抓取交互**：挤出与分离面沿用原版 Manipulator，暂不支持 X/Y/Z 锁轴与数字精确输入（如需锁轴请先确认，再按 G 移动）；部分视角下拖动行为仍可能不符合预期。
- [ ] **中文汉化不全**：可能仍有未翻译字符串、或翻译与界面实际不符之处。
- [ ] **编辑器稳定性**：部分操作偶发异常 / 崩溃，尚未系统排查。
- [ ] **实时绘制纹理缺失**：只有贴图增删替换（`工具 → 编辑贴图`）与 UV 坐标编辑（`工具 → 编辑 UV 映射`），没有画笔 / 像素写回；需在外部图像编辑器绘制后导回。
- [ ] **丝带拖尾无编辑视口预览**：`RibbonEmitter` 拖尾只在游戏里渲染，编辑器视口看不到，需导出验证；且丝带缺少速度 / 发散角 / 宽度等参数。
- [ ] **建模页工具有限**：目前只有顶点 / 由所选创建面 / 骨骼 / 平面 / 立方体，以及数值变换；环切、倒角、细分、布尔、镜像、软选择等尚未实现（上游多为注释或空壳）。
- [ ] **时间轴默认禁用易误认为损坏**：需先按 `A` 进入「动画」模式，且模型需自带动画序列，再按 `Ctrl + 空格` 播放。已在软件内帮助补说明。
- [ ] （请使用者 / 作者持续补充……）

## 致谢

- [Retera](https://github.com/Retera/ReterasModelStudio) 与原 RMS 项目团队（MIT 协议）。
- 本项目的全部中文汉化与 Blender 风格增强由 **金闪电侠** 完成，AI 助手 **闪电助手** 协助开发。

---

如果本项目对你有帮助，或你修好了上面的某个 bug，欢迎在 Issue / PR 里告诉我 🙂
