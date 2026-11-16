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
- **第二轮界面交互修复**：「模型」页组件树搜索框（并释放 CTRL+F）、时间轴跳帧按钮、大纲树右键 / Delete 删除节点、视口中文按钮与粒子显隐开关、粒子编辑面板汉化与布局、丝带「假动态」按钮（详见下方已勾选条目）
- **从 war3 本体导入原生特效**：`添加 → 粒子 → 从 war3 本体导入特效…`。可一键注册本机游戏目录里的 MPQ 数据源，浏览/搜索全部原生技能特效模型并把粒子 / 丝带发射器（含贴图与材质）导入当前模型继续编辑；右侧带实时 3D 预览（雷电、霜冻、冲击波等无需自制预设）
- **Blender 风格持续优化**：建模页补齐变换工具与常用编辑按钮、视口右键加粒子/建模操作、全局按钮统一为深底白字风格；同时修了重置布局崩溃、大纲搜索、粒子重力调节、队色与参数面板汉化（详见下方已勾选条目）
- **父级绑定与时间轴深色**：右键骨骼添加粒子会自动预选该骨骼为父级并同步坐标；war3 特效导入可手动选绑定父级；时间轴统一深色刻线与按钮；修了节点树样式错乱并补全 `ModeButton` 系列按钮风格（详见下方已勾选条目）
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

产物 jar 位于 `source/` 对应模块下（具体见 `source/README.md` 与 `build.gradle`）。一般无需自己编——`汉化版/RMS_zh_CN.jar` 已是可运行成品。

### 本项目的汉化 / 增强改动如何重编

构建脚本**自包含**：它只读 `汉化工具/界面修复源码/` 里本次改动的少量源码（仓库已自带，不依赖外部 `RMS_Twilac_Source`），以**差分定向注入**把中文 UI 与修复写入现成的 `汉化版/RMS_zh_CN.jar`，并重编增强层 `汉化版/rms-blender.jar`。`git clone` 后无需任何额外文件即可复现构建。

前置：安装 **JDK 17**（仓库不含 JDK；脚本会自动探测系统 `javac`/`java`，也可用 `--javac=<绝对路径>` 显式指定）。

```bash
# 主程序 jar（汉化 + UI 修复注入）+ 增强层 jar 一起重编
python 汉化工具/构建第十轮.py --javac="C:/Program Files/Java/jdk-17/bin/javac.exe"

# 只验证编译与注入、不写文件
python 汉化工具/构建第十轮.py --dry-run --javac="..."

# 仅重编增强层（BlenderHooks / BlenderButtonStyle 等）
python 汉化工具/blender/构建Blender模式.py --skip-grid --skip-prefs
```

> 说明：脚本默认字节码级别 `--release 14`（含 Java 14 switch 表达式的源码需要）。只要系统有 JDK 17 即可。

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
- [x] **第二轮界面交互 7 项修复（已修复）**：① **「模型」页树搜索** —— 上一轮把搜索框误加到了「编辑」页的大纲视图（`ModelViewManagingView`），而用户用的是「模型」页 `ModelComponentBrowserTree`；且 `BlenderHooks.java` 的全局 `KeyEventDispatcher` 把 **CTRL+F 绑成「创建面」并 `consume()`**，导致按 CTRL+F 弹 `A face can only be created...`、所有 Swing 键绑定被短路。现给 `ModelComponentsView` 加顶部搜索框 + `ModelComponentBrowserTree` 按节点显示名过滤（自动保留父节点链），并把「创建面」改键为 **CTRL+SHIFT+F**（同步视口菜单 accelerator 与建模页按钮文案），CTRL+F 让给两处树搜索。② **粒子编辑面板汉化** —— `ParticleEditPanel` 的 Head/Tail、Modulate/Modulate2x/AlphaKey、LineEmitter、带单位标签、UV 面板、start/end/repeat、按钮与窗口标题全部中文化；这些裸词同时是 `MdlUtils.TOKEN_*`，**没有**写进全量词典（会污染数据令牌），而是直接在源码里写中文。③ **底部按钮被截断** —— `FramePopup.show()` 原来只 `pack()`，内容高于屏幕时按钮被裁；现套 `JScrollPane` 并按屏幕限高，`ParticleEditPanel.paintComponent` 补空指针防护。④ **时间轴跳帧按钮** —— 补首帧 / 尾帧 / 上一帧 / 下一帧，走 `jumpTo()`（clamp 到 `[0,length]`；不用 `timeStep`，它在尾帧会环绕取模跳回 0）。⑤ **大纲树删除节点** —— 右键菜单（由 `BlenderHooks` 运行时注入）补「删除节点」，并让树上的 Delete 键按**树选中行**删除（全局 Delete 只认 3D 视口选区），复用 `DeleteNodesAction` / `DeleteGeosetAction` 保证父子关系与撤销。⑥ **视口按钮** —— 标题栏 4 个 Unicode 符号按钮改中文「贴图 / 线框 / 顶点 / 法线」，并新增第 5 个「粒子」开关（底层 `RenderModel.setSpawnParticles` 早已存在，只缺 UI）。⑦ **丝带假动态** —— KRIB 只有 KRHA/KRHB/KRAL/KRCO/KRTX/KRVS 六条动画通道，生命周期 / 发射速率 / 重力是纯静态字段，禁用其「转为动态」避免生成存不进文件的 AnimFlag。另给 `rms_patch.py` 加了**令牌级黑名单**，防止误用全量 `apply` 时把内联 MDL 令牌翻成中文。统一构建入口：`汉化工具/构建第七轮.py`（`--release 14`，差分定向注入 107 处，替换 45 个 class，并重编 `rms-blender.jar`）。
- [x] **从 war3 本体导入原生特效（已实现）**：`添加 → 粒子 → 从 war3 本体导入特效…` 打开 `War3EffectImporter`：复用 `GameDataFileSystem.getMergedListfile()` 列出全部 `.mdx`，支持关键字搜索；「添加本机 war3 目录…」可扫描目录下的 `War3*.mpq` 并注册为数据源（写入 `SaveProfile` + `GameDataFileSystem.refresh`）；选中后 `MdxUtils.loadEditable` 载入，把 `ParticleEmitter2` / `RibbonEmitter` 拷进当前模型，贴图按路径去重追加并重映射 `textureId`。**注意**：只搬发射器与贴图，不搬源模型动画序列，拷贝对象自带的 AnimFlag 可能仍引用源模型 Sequence，导入后请检查/重建。第八轮已补齐：右侧实时 3D 预览、`AddNodeAction` 结构刷新与撤销、`RenderModel.updateNodes` 视口刷新、以及 `Material.deepCopy()` + `Layer.textureId` + `RibbonEmitter.materialId` 的完整材质链重映射。
- [x] **第八轮：回归修复、导入/丝带失效与 Blender 风格优化（已修复）**：① **「重置布局」崩溃** —— `ModelComponentBrowserTree.buildTreeModel` 里 `nodeToTreeElement.get(parent)` 可能为 null（父级是相机节点或已移除的骨骼，不在 `getIdObjects()` 列表里），原代码直接 `parentTreeNode.add(...)` 抛 NPE；现兜底挂到「节点」分组并跳过已挂载的子节点。② **大纲搜索不展开** —— 原过滤条件 `!visible.isEmpty() && !visible.contains(object)` 在「无匹配」时会放行全部节点，且过滤后沿用旧的折叠状态；现改为 `isFilterActive() && !visible.contains(object)`，并在过滤激活时自动展开命中项。③ **war3 特效导入无反应** —— 原实现只 `model.add()` + `repaint()`，既没通知结构变化、也没重建渲染器发射器列表；现改用 `AddNodeAction`（自带 `nodesUpdated()` 且可撤销）+ `RenderModel.updateNodes(true)`，并断开拷贝对象的跨模型父级引用。④ **丝带导入没效果** —— 丝带走 `materialId → Material → Layer → Bitmap` 链，原实现只搬发射器；现 `Material.deepCopy()` 追加材质、`Layer.textureId` 二次重映射、`copy.updateMaterialRef(...)` 回写。⑤ **导入对话框右侧空白** —— 内嵌 `AnimationViewer`，点击列表即后台加载 `.mdx` 实时预览（GL 组件只创建一次，带防乱序号）。⑥ **粒子「重力」滑块无反应** —— `RenderParticle2` 原只在粒子生成时快照一次 gravity，且 `getRenderGravity` 会被 Gravity AnimFlag 覆盖；现改为每帧重读、滑块回调清除该动画曲线。⑦ **参数面板英文标题** —— `ComponentParticle2Panel` 等 6 个面板共 25 处 ValuePanel 标题直接在源码写中文（内部键仍用 `MdlUtils.TOKEN_*`，两者编译后是独立 UTF8 常量，互不干扰）；`WESTRING_UNITCOLOR_13/14/15` 是运行时从 MPQ 的 `WorldEditStrings.txt` 查表生成、词典注入够不着，改在 `TeamColorMenu` 做中文兜底（海军蓝 / 青绿 / 紫罗兰）。⑧ **Blender 风格** —— 建模页取消 `getEditToolsPanel()` 注释并新增「常用编辑」按钮网格（焊点/翻转法线/简化几何/创建面/删除/添加丝带）；视口右键菜单补「添加粒子特效」子菜单、空丝带、空爆米花、创建面、焊点、翻转法线、删除所选；新增 `BlenderButtonStyle` 把普通 JButton 统一为「自绘深底 + 白字」风格（**不依赖 `setBackground`**，规避 InfoNode 主题白字不可见的坑），由增强层 500ms 扫描幂等套用。统一构建入口 `汉化工具/构建第八轮.py`（`--release 14`，差分定向注入 118 处，替换 53 个 class，重编 `rms-blender.jar`）。
- [x] **第九轮：树渲染器错乱回归、按钮风格补全、父级绑定、时间轴深色（已修复）**：① **节点树错乱** —— 第八轮的全局按钮样式把 TreeCellRenderer 里反复复用的 E / V 状态圆按钮也套了深底、粗体与新 UI，导致文字重叠、缩进错乱（它们的 `setBackground` 还承载开/关语义）；现 `BlenderButtonStyle` 跳过 `CellRendererPane` 下的所有 renderer 组件，并跳过单字符按钮。② **按钮风格补全** —— `ModeButton` / `ModeButton2` 原是浅红渐变、被样式扫描主动跳过，现直接改成与工具栏 `ToolbarButton2.FlatTextButton` 相同的深底白字（激活态深蓝），建模页那批按钮与工具栏完全一致。③ **右键添加粒子自动设父级** —— `AddParticlePanel` 新增 `addParticleButtons(JMenu, IdObject)` 重载，用参数透传（不用静态字段，菜单监听器延迟执行会被覆盖），在 `configureSearchable` 之前预选父级并同步 XYZ 到骨骼 pivot；`BlenderHooks` 视口右键通过 `firstSelectedIdObject(vp)` 取刚点中的骨骼，节点树右键直接把被右击的 `node` 作为默认父级。④ **war3 导入绑定父级** —— 对话框底部新增可搜索「绑定父级」下拉（复用同包的 `AddParticlePanel.configureSearchable`）、「特效落在骨骼位置」复选框与「刷新节点」按钮；导入时校验父级仍属于当前模型（对话框非模态，期间可能换模型），再 `setParent` 并可选同步 pivot。⑤ **时间轴深色** —— `TimeBarPainter.isDarkTheme()` 恒 true（`0x2B2D30` 底 / `0xB8C0CA` 刻度 / `0x4772B3` 当前帧 / `0xE6E6E6` 文字），`TimeSliderPanel` 按钮区与刻度区同步深底、复选框改浅字、跳帧按钮拉开间距。统一构建入口 `汉化工具/构建第九轮.py`（`--release 14`，62 个 class、定向注入 118 处，并重编 `rms-blender.jar`）。
- [x] **第十轮：右键添加粒子父级预选失效、粒子面板布局与时间条（已修复）**：① **右键添加 war3 粒子父级不跟随所选节点** —— 两个根因：视口右键菜单弹出前 `BlenderHooks` 会做一次程序化点选（把光标下物体整体替换选区），点在空白处就把大纲树里选中的节点清掉了，`firstSelectedIdObject` 自然拿到 null；且 `ModelView.getSelectedIdObjects()` 在 `idObjectsEditable/idObjectsVisible` 关闭时（视图模式）恒返回空集。现右键前先快照选区、拾取为空则恢复，`firstSelectedIdObject` 增加原始 `selectedIdObjects` 字段兜底；`War3EffectImporter` 打开时也按当前模型唯一选中节点预选「绑定父级」（在 `configureSearchable` 之前设置，避免被延迟 refilter 覆盖）。② **粒子参数面板右侧大片空白** —— 组件面板直接放进 `JScrollPane` 时只按首选宽度显示，现 `ComponentsPanel` 用 `BorderLayout.NORTH` 包裹让宽度撑满视口；`ComponentParticle2Panel` 的 8 个参数从单列堆叠改为两列网格。③ **粒子页时间控制** —— 参照 Blender 直觉给 `ComponentParticle2Panel` 加了时间条：动画序列下拉 + 帧滑杆 + 播放/暂停，拖动直接驱动全局 `TimeEnvironment`，配合每行「转为动画」可逐帧查看关键帧效果（不必完全模仿 Blender 粒子系统，但基本操作同样直观）。统一构建入口 `汉化工具/构建第十轮.py`（`--release 14`，65 个 class、定向注入 119 处，并重编 `rms-blender.jar`）。
- [x] **第十一轮：视口标题栏按钮风格统一、粒子面板播放 Timer 泄漏修复（已修复）**：① **视口标题栏 5 个显示开关（贴图 / 线框 / 顶点 / 法线 / 粒子）与全局 Blender 深底白字风格不一致** —— 这些是 `TinyToggleButton` 自绘按钮，原先浅灰底黑字、且 `toggleOn` 残留 `System.out.println` 调试输出；现改为 on=深蓝激活 `0x4772B3`、off=深灰 `0x3C3F41`、文字统一浅灰白 `0xDCDCDC`，并移除调试输出，与工具栏 / `ModeButton` 完全一致。② **粒子参数面板播放 Timer 泄漏** —— 时间控制条的 33ms 播放 `Timer` 原只在「停止」时 `stop()`，面板被卡片切换隐藏后仍持续 30fps 全局重绘；现加 `ComponentListener`，面板 `componentHidden` 时自动停 Timer 并复位播放按钮。统一构建入口 `汉化工具/构建第十轮.py`（已追加 `TinyToggleButton` 到 SOURCES，67 个 class、定向注入 119 处）。
- [x] **第十二轮：粒子编辑对话框 UV 区裁切修复、Vec3SpinnerArray 竖向紧凑统一（已修复）**:① **粒子编辑对话框（ParticleEditPanel）右侧 UV 区「重复」列被窗口右边界裁切** —— 真实布局 bug，根因有二：`Vec3SpinnerArray.spinnerPanel()` 把 x/y/z 三个分量**横向并排**（单组宽≈180px），在右侧窄面板里整体溢出窗口；且 `ParticleEditPanel` 根面板首行无列约束时，窄的 3D 预览组件会把整列宽度锁死，导致后续 UV 区宽组件被压窄、右侧被裁。② **修复** —— `Vec3SpinnerArray` 改为三个分量**竖向堆叠**（统一紧凑、与全局一致，不再横向并排溢出）；`ParticleEditPanel` 预览行由 `add(viewportPanel, "")` 改为 `add(viewportPanel, "spanx, wrap")`，解锁首行列宽，使后续 UV 区每行一个 `Vec3SpinnerArray`（起始 / 结束 / 重复）能正常撑满、不再被右边界裁切。统一构建入口仍为 `汉化工具/构建第十轮.py`（已追加 `Vec3SpinnerArray` 到 SOURCES，现为 68 个 class、定向注入 119 处）。
- [x] **第十三轮：动画预览滚轮切换除零崩溃修复（已修复）**：① **崩溃现象** —— 在动画预览面板（`AnimationController`）的动画下拉框上滚动鼠标滚轮切换序列时弹出 `java.lang.ArithmeticException: / by zero`，堆栈定位到 `AnimationController.changeAnimation(AnimationController.java:120)`。② **根因** —— 该方法用 `animationBox.getItemCount()` 作取模除数；当模型没有任何动画序列且 `allowUnanimated=false`（或 `modelHandler==null`）时，`updateAnimationList` 不会往下拉框添加任何项，`getItemCount()` 为 0，取模即除零崩溃；此外单次 `%` 在滚轮为负且越过起点时还会得到负索引。③ **修复** —— 增加 `itemCount <= 0` 守卫（无动画可切换时直接返回，不抛异常），并把索引计算改为双重取模 `(((itemCount + prev + wheel) % itemCount) + itemCount) % itemCount`，保证结果为非负索引。`AnimationController` 加入 `构建第十轮.py` SOURCES（现为 69 个 class、定向注入 126 处）；差分注入自动保留该类原有的 7 处中文 UI（播放动画 / 默认循环 / 始终循环 / 不循环 / 细节层次 / 速度 / 默认动画名），均属界面文本、无内部令牌。
- [x] **第十四轮：右键视口菜单与材质/相机面板残留英文 UI 中文化（已修复）**：① **范围澄清** —— 经排查确认：顶层菜单栏在成品里已基本中文化（源码英文被旧版注入还原），用户早前截图里的 `Texture` 来自那张确认是旧 0.5.7 乱码的截图，当前成品无此菜单；真正残留英文的是**右键 3D 视口上下文菜单**与若干面板标签（这些恰是用户「继续完善」所指的视觉一致性缺口）。② **右键视口菜单（ViewportPopupMenu）整菜单翻译** —— 5 个子菜单标题 `View/Mesh/Edit/Rig/Node` → 视图/网格/编辑/绑定/节点；26 个菜单项（前/后/顶/底/左/右、创建面、拆分几何集[并添加队色]、平移/旋转/定位/缩放输入、选中网格绑定到选中节点、重新分配/查看矩阵、重新分配/查看高清蒙皮、查看蒙皮、设置父级、自动居中骨骼、重命名骨骼、追加骨骼后缀）全部中文化。③ **面板标签** —— 材质面板：图层、过滤模式、TVertex 动画、选择 TVertex 动画、坐标 ID、删除按钮、「该材质正被 N 个几何集使用，无法删除。」提示；菲涅尔颜色 / 不透明度 / 队伍颜色、自发光增益；相机面板：位置 / 目标 / 视野 / 远裁剪 / 近裁剪；通用：删除按钮→删除、转为静态 / 转为动态 / 插值、导出按钮→导出、空状态提示→「请选择一个模型组件以开始…」。④ 全部落在已能安全重编的 34 个源内（这些类当前构建即通过，无需新增 SOURCES），重新编译 + 差分注入，主 jar 替换 69 个 class、testzip OK，JVM `-Xverify:all` 加载 8 个改动类全通过。
- [x] **第十五轮：绑定父级撤销未解除 `parent.childrenNodes` 解绑修复（已修复）**：① **bug 现象** —— 给一个「绑定了父级的节点」（如 war3 导入的粒子 / 丝带指定了挂载骨骼、或右键添加粒子特效挂到所选节点）执行添加后，再点撤销：节点从模型列表移除，却仍残留在父级 `childrenNodes` 里；此时保存模型会把「不属于模型的孤儿节点」写进文件导致损坏 / 加载报错，重做（redo）时还可能重复挂接。根因：`EditableModel.add/remove(IdObject)` 只维护模型节点列表、**不联动** `IdObject.setParent()`，而父子关系完全由 `setParent()` 维护（写入 `parent.childrenNodes`）；原 `AddNodeAction.undo/redo` 只做 `model.remove/add`，从不触碰 `childrenNodes`。② **修复** —— `AddNodeAction` 构造时快照 `parent = node.getParent()`；`undo()` 在 `model.remove(node)` 后，若节点仍挂在该父级则 `node.setParent(null)` 解绑；`redo()` 在 `model.add(node)` 后，若节点未挂回则 `node.setParent(parent)` 恢复。`setParent` 幂等，重复调用不会产生重复条目；无父级的节点（如菜单新建骨骼）快照为 null，修复为 no-op，无任何回归。③ **安全边界** —— `AddNodeAction` 此前未编进 jar（仅由上游基础构建提供），本轮把它加入 `构建第十轮.py` 的 SOURCES 重新编译替换；因编译以成品 jar 为 classpath，链接阶段即保证其引用的 `IdObject.getParent/setParent` 必存在于 jar（否则编译失败），差分注入对无中文串的该类为 no-op，等于直接替换为修复版。验证：主 jar 替换 70 个 class（原 69 + AddNodeAction）、testzip OK；字节码核验 `undo` 含 `getParent` 比对 + `setParent(null)`，`JVM -Xverify:all` 加载通过。
- [x] **第十六轮：面模式单击多选被遮挡的面（已修复）**：① **bug 根因** —— 面模式单击时底层拾取会选中射线穿过的所有面（透视多选），增强层 `BlenderHooks.dedupeFaceClick` 负责事后收敛到用户点的那一层。但旧判据用「质心到相机实际位置的欧氏距离」并保留 `dist <= best + best*0.02`：欧氏距离在斜对角方向会把更远的面误判为更近，且 `best*0.02` 随绝对距离放大，在大而远的模型上阈值过松、连带保留后一层。② **修复** —— 改在增强层（不碰主 jar class）：深度改用**沿视线的相机空间深度** `rotateCam(质心).x`（`rotateCam` 与渲染管线 `glRotatef x/y/z` 同序，x 越大越靠近相机）；命中面按深度降序求相邻间隙，仅当**最大间隙显著大于次大间隙**（`maxGap > 2×secondMax` 且 `maxGap >= max(span*0.15, 1e-3)`）才在断层处切分、只保留最近一层；共面 / 连续曲面因间隙均匀而整体保留（符合 Blender 习惯：点一个四边形保留其两个三角形）。③ **验证** —— 判据先在独立 Java 程序覆盖 9 类场景（共面四边形 / 连续曲面 / 前后两层 / 斜对角 / 三层 / 单个 / 极小尺度 / 近单远双 / 近双远单）全部 PASS；构建后 `javap` 确认 `dedupeFaceClick` 调用 `rotateCam` 与 `setSelectedVertices`，新中文日志串均在 class 常量池；`JVM -Xverify:all` 加载通过。增强层重编 20 个 class（新增匿名 Comparator 合成类，预期），`rms-blender.jar` testzip OK。
- [ ] **全量 `rms_patch.py apply` 已不用于正式构建**：历次崩溃都源于无差别替换常量池。现在统一用 `汉化工具/构建第八轮.py` 的差分定向注入（只还原旧 jar 中确实被翻成中文的英文 UI 原文）+ `FORBIDDEN_ENG` 内部键黑名单。若确实需要全量 apply，`rms_patch.py` 已内置令牌级黑名单兜底。另有遗留：`MdlxParticleEmitter2.class` 的 `InnerClasses` 属性里嵌套类名 `FilterMode` 曾被误翻成中文（**不影响运行时**，JVM 用 `$` 类名，但会让「拿成品 jar 当 classpath 编译源码」时解析不到嵌套类型，源码里已用全限定二进制名绕过）。
- [ ] **这几项仍待处理（本轮未纳入）**：时间轴滚轮缩放完整实现（time↔x 换算散在 5 处，风险高，当前只补了跳帧按钮）；（视口标题栏 5 个显示开关按钮的样式已在第十一轮统一为深底白字）；PNG 导入时自动转 BLP（项目自带 `BLPWriter` + SPI，只差一步）；粒子预设扩充（需自制/提取 `.mdx` 模板，已由上面的 war3 导入功能替代大部分需求）；丝带拖尾视口预览；Blender 级高级建模工具（环切/倒角/细分/布尔）；建模页 XYZ 输入框聚焦时 G/R/S/E/P 快捷键被 `typing` 判断拦截、面板也缺快捷键提示；`ComponentSDLayer.java` 引用了源码里存在、成品 jar 里却没有的 `SetLayerTextureAnimAction`（jar 里只有 `SetLayerTextureAction`），因源码≠jar 无法安全重编，该面板的 Alpha 标题仍显示英文；导入的 war3 特效动画曲线可能仍引用源模型 Sequence，未做序列映射（只搬发射器/贴图/材质与静态参数）。另：绑定父级后若执行撤销，`AddNodeAction.undo()` 只做 `model.remove(node)`，不会从 `parent.childrenNodes` 解绑（上游框架既有局限），重做可能重复挂接——**该问题已在第十五轮修复**（见上），撤销 / 重做现在会同步解绑 / 恢复父级子节点关系。
- [ ] **缺少魔兽 3 游戏素材时的资源告警**：未配置游戏目录时，启动阶段会在控制台刷 `JMpqException: File Not Found <...>`（主要是 `stock\particles\*.png` 粒子贴图，以及少量 TabIcons 图标）。异常被捕获，不影响运行；正式启动脚本使用 `javaw.exe`（无控制台），不显示也不写日志文件，仅调试模式可见。
- [x] **面模式选择：单击多选被遮挡的面（第十六轮已修复）**：旧的后处理用「质心到相机的欧氏距离 + `best*0.02` 相对阈值」判最近面，斜对角时欧氏距离≠视线深度会判错前后，且大而远的模型阈值过松会连带选中后一层。现改为「沿视线的相机空间深度（与渲染相机同一变换）+ 层间间隙断层检测」：命中面按深度降序，仅当存在**一个显著大于其它间隙的断层**（最大间隙 > 2×次大间隙且超过绝对下限）时只保留最近一层；共面 / 连续曲面间隙均匀则整体保留。判据经 9 类场景独立单元测试验证，改在增强层 `BlenderHooks.dedupeFaceClick`（不碰主 jar class）。
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
