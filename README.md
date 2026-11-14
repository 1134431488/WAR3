# RMS 汉化版（Retera Model Studio · 中文增强版）

> ⚠️ **本项目目前 bug 较多、功能仍不稳定**，原作者精力有限，**非常希望有能力的朋友一起来修复、完善**。如果你熟悉 Java / Swing / 3D 模型编辑器，欢迎提 Issue 或 PR！

## 这是什么

本项目是 [Retera/ReterasModelStudio](https://github.com/Retera/ReterasModelStudio)（一款开源的魔兽争霸 3 模型编辑器，MIT 协议）的**中文汉化 + Blender 风格操作增强**版本，主要面向魔兽地图 / 模型改图爱好者。

在官方 RMS 基础上增加 / 修改了：

- 全中文界面与大量**悬停气泡说明**（约 300 条）；粒子参数统一改成一眼能懂的大白话，并修正生命周期/透明度/可见性/子贴图等错误说明
- 粒子参数名支持在 `提示说明.txt` 里用 `!标签!原名=新名` 自定义改写，改文本重启即可，无需重编程序
- **Blender 式快捷键**：`G` 移动、`R` 旋转、`S` 缩放、`E` 挤出、`P` 分离面（抓取式交互；G/R/S 支持 X/Y/Z 锁轴与数字输入，E/P 挤出/分离面暂不支持，左键确认 / ESC 取消）
- 面模式**防透选**修复（线框模式仍保留透视多选，符合 Blender 习惯）
- 工具栏标签修正：`EXTRUDE` →「分离面」、`EXTEND` →「挤出」（与真实语义一致）
- **上游缺陷清理**：修复粒子渲染层与建模系统 24 个文件约 40 处低风险 BUG（详见下方已勾选条目）
- **界面交互 5 项修复**：删除按钮白字不可见、粒子贴图未做图集分割、时间轴无法直接切换动画序列、大纲树无搜索（CTRL+F 无效）、点击大纲节点视口不高亮（详见下方已勾选条目）
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
- [x] **粒子渲染层若干崩溃 / 显示错乱缺陷（已修复）**：`RenderParticleEmitter2` 的 `glBegin` 后提前 return 漏 `glEnd`（会留下未闭合 GL 状态导致视口错乱）、rows/columns 为 0 时除零、缓冲数据为 null 时的崩溃；`RenderParticle2` 的子贴图行列数保护与插值分母 clamp；`RenderModel` 的渲染器映射表不清理（粒子删除后仍残留幽灵渲染器）。第一代 / 爆米花 / 丝带发射器仍未进渲染管线（上游整类为注释状态），属大工程，欢迎帮忙实现。
- [ ] **粒子关键帧标量静态值缺陷**：当某标量参数仅在一个动画里打过关键帧时，其它动画里该参数（如 Visibility / EmissionRate / Speed / Gravity）会回退成 1.0 而非模型静态值，属动画体系静态值回退逻辑缺陷，尚未处理。（注：此前同路径下出现的 `Float cannot be cast to Vec3` 崩溃是「汉化把数据令牌翻成中文」的继发故障，已由下方令牌修复解决，与此条的静态值缺陷是两回事。）
- [x] **添加粒子预设缺失时静默失败（已修复）**：`AddParticlePanel` 的 `loadImage` 改为 try-with-resources + 异常兜底（原 `ImageIO.read(null)` 抛 `IllegalArgumentException` 不被捕获，会让「添加 → 粒子」子菜单构建中断或点了没反应），缺失时自动补占位图标；空预设列表与父级组件异常也做了防护。三个添加入口的 UndoAction（撤销支持）仍未实现。
- [x] **爆米花粒子静态可见性空实现（已修复）**：`ParticleEmitterPopcorn.setStaticVis` 原为空方法，现已写入 alpha；爆米花拷贝构造补齐 `always` 与动画可见性映射。各粒子面板 `visibilityPanel` 的 setter 仍为 null，静态值界面编辑尚未接通。
- [x] **建模系统 24 个文件约 40 处低风险缺陷（已修复）**：含 `AnimFlag.equals` 逻辑条件错误（`||` 应为 `&&`）、`MoveAction` 撤销方向写反（`set` 误当 `sub` 用）、`CopyCutPast` 动画模式快捷键分派丢失、挤出/分离面后 `fillGap` 重复执行、`MergeGeosets` 重复移除顶点、`SplitGeoset` 重复调用、`CreateFace` 零向量除零、空选择集焊点 / 选择中心返回 NaN、立方体绘制高度判据用错变量、手动变换面板缺 default 分支、视口右键菜单骨骼命名实为替换而非追加、爆米花粒子 `forEach`+`remove` 并发修改异常、`RibbonEmitter` 材质引用越界、rows/columns 默认 0 等，均已修复并编译注入。另清理约 30 处调试 `println` 与 1 个误导性循环变量命名（lambda 变量遮蔽外层 `geoset`）。
- [x] **汉化注入误翻字段名 / 数据令牌致崩溃（已修复）**：上一版注入阶段把词典**无差别**替换常量池里所有匹配 UTF8，连「字段名 / 方法名 / 类引用」和「MDL/MDX 文件格式与动画数据令牌」也一起翻成中文，引发两类崩溃：① `NoSuchFieldError: 面向摄像机` / `ModelEditorActionType3.位移` —— 引用名被翻中文、但声明仍是英文，链接失败；② `ClassCastException: Float cannot be cast to Vec3` —— `Translation`/`Rotation`/`Scaling`/`Color`/`TextureID`/`LifeSpan`/`Alpha`/`Visibility` 等数据令牌被翻中文，而 `MdlUtils.TOKEN_*` / `AnimFlag.generateTypeId()` 仍按英文字面量匹配 `switch`，失配落到默认 `ALPHA`，`Vec3AnimFlag` 把 `1.f`（Float）强转成 `Vec3` 崩溃。已由 `汉化工具/repair_names.py`（按 owner 类声明成员 + 词典逆映射还原字段/方法引用名）与 `汉化工具/repair_tokens.py`（按 class 维度把数据令牌还原回该 class 源码实际使用的英文）修复，覆盖 `AnimFlag`、`MdlxParticleEmitterPopcorn` 等；并在 `排除类.txt` 加入 5 个数据令牌类前缀作为根因预防。修复后 jar 经 testzip 与启动验证，rms 包注入类字段隐患归零，UI 中文文案完好（如「网格基础」「顶点」「平面」）。
- [x] **界面交互 5 项缺陷（已修复）**：① **删除 / X 按钮白字不可见** —— `ComponentPanel.getDeleteButton/getXButton` 与 `ComponentLayerPanel` 写死 `setForeground(Color.WHITE)`，而 InfoNode 主题忽略 `setBackground(Color.RED)` 却保留白字，白字配浅底看不见；现改用主题默认深色前景。② **粒子贴图未做阵列分割** —— `RenderParticleEmitter2.renderParticles` 把 `RenderParticle2` 打包的整数格号（`left/right/top/bottom`）直接当 UV 喂 `glTexCoord2f`，归一化 `uv/=cols/rows` 只写在 `isRibbonEmitter` 分支，而该方法恒为 `false`，于是整张贴图被播放；现补非 ribbon 分支除法并修正 ribbon 分支的重复除法。③ **时间轴不能切换动画** —— 原本时间轴没有序列选择器，只能去建模页下拉框切，属缺控件；现时间轴自带 `JComboBox<Sequence>`，`setModelHandler` 填充、`timeBoundsChanged` 双向同步。④ **大纲树无查找** —— 树从未实现搜索、全局 `CTRL+F` 也空闲无冲突，属缺特性；现树上方加搜索框 + 按名称过滤（自动保留命中项的父节点链）+ `CTRL+F` 聚焦。⑤ **点击大纲节点视口不高亮** —— `ComponentThingTree.mouseReleased` 的选中逻辑整段被注释，`ComponentTreeNode.doSelection` 又只挂在 renderer 的 JLabel 上（`ButtonCellEditor.isCellEditable` 恒 false，永不触发），写选区与重绘两层断链；现接回 `doSelection` 并刷新 3D 视口。改动由 `汉化工具/构建界面修复.py` 统一编译 → **差分定向注入中文** → 替换 20 个 class，源码存档在 `汉化工具/界面修复源码/`。
- [ ] **部分 UI 面板仍残留「内联 MDL 令牌」误翻（待逐类排查）**：`MdlUtils.TOKEN_*` 是 `public static final String` 编译期常量，javac 会把它们内联进调用方（含 UI 面板类）的常量池。早期无差别词典替换因此把 `Alpha`/`TextureID`/`EmissiveGain`/`FresnelColor`/`FresnelOpacity`/`FresnelTeamColor` 等也翻成中文，导致 `layer.find("透明度")` 查不到 AnimFlag、材质动画静默失效；`RenderParticleEmitter2.find("EmissionRate")` 同理（粒子发射速率读不到）。本轮重编的 `ComponentLayerPanel` 与 `RenderParticleEmitter2` 已恢复英文（构建脚本用 `FORBIDDEN_ENG` 黑名单拦下），但**其它未重编的 UI 类里同类内联令牌可能仍被误翻**。整类排除不可行（会让界面退回英文），必须做令牌级黑名单；若日后再次全量套词典，请先补上这层防护。
- [ ] **「复制材质」按钮实际会删除材质（上游缺陷，未修）**：SD 材质面板 `ComponentSDLayersPanel` 的 "Duplicate Material" 按钮在成品 jar 里绑定的 lambda 调用的是 `deleteMaterial()`。源码树虽已改成 `duplicateMaterial()`，但父类 `ComponentLayersPanel` 的新方法并未编进 jar（`RMS_Twilac_Source` 比成品 jar 新），直接重编会 `NoSuchMethodError`。本轮为不破坏二进制兼容未动它，**请勿点该按钮**，需要时手动复制材质。
- [ ] **缺少魔兽 3 游戏素材时的资源告警**：未配置游戏目录时，启动阶段会在控制台刷 `JMpqException: File Not Found <...>`（主要是 `stock\particles\*.png` 粒子贴图，以及少量 TabIcons 图标）。异常被捕获，不影响运行；正式启动脚本使用 `javaw.exe`（无控制台），不显示也不写日志文件，仅调试模式可见。
- [ ] **面模式选择**：单击可能仍会多选被遮挡的面（已加按质心距离去重，但共面 / 斜对角情况未全覆盖）。
- [ ] **E / P 抓取交互**：挤出与分离面沿用原版 Manipulator，暂不支持 X/Y/Z 锁轴与数字精确输入（如需锁轴请先确认，再按 G 移动）；部分视角下拖动行为仍可能不符合预期。
- [ ] **中文汉化不全**：可能仍有未翻译字符串、或翻译与界面实际不符之处。
- [ ] **编辑器稳定性**：部分操作偶发异常 / 崩溃，尚未系统排查。
- [ ] **实时绘制纹理缺失**：只有贴图增删替换（`工具 → 编辑贴图`）与 UV 坐标编辑（`工具 → 编辑 UV 映射`），没有画笔 / 像素写回；需在外部图像编辑器绘制后导回。
- [ ] **丝带拖尾无编辑视口预览**：`RibbonEmitter` 拖尾只在游戏里渲染，编辑器视口看不到，需导出验证；且丝带缺少速度 / 发散角 / 宽度等参数。
- [ ] **建模页工具有限**：目前只有顶点 / 由所选创建面 / 骨骼 / 平面 / 立方体，以及数值变换；环切、倒角、细分、布尔、镜像、软选择等尚未实现（上游多为注释或空壳）。
- [ ] **时间轴默认禁用易误认为损坏**：需先按 `A` 进入「动画」模式，且模型需自带动画序列，再按 `Ctrl + 空格` 播放。已在软件内帮助补说明；本轮已给时间轴加上动画序列下拉框，可直接切换序列，但「先按 A、模型自带序列」这两个前提不变。
- [ ] （请使用者 / 作者持续补充……）

## 致谢

- [Retera](https://github.com/Retera/ReterasModelStudio) 与原 RMS 项目团队（MIT 协议）。
- 本项目的全部中文汉化与 Blender 风格增强由 **金闪电侠** 完成，AI 助手 **闪电助手** 协助开发。

---

如果本项目对你有帮助，或你修好了上面的某个 bug，欢迎在 Issue / PR 里告诉我 🙂
