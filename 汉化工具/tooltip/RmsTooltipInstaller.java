import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Retera Model Studio 中文版 —— 悬停说明气泡启动器。
 *
 * 设计原则：
 *  1. 不修改任何一个原有 class，也不使用 java.instrument（便携 runtime 未包含该模块）。
 *  2. 仅通过遍历 Swing 组件树，在运行时给按钮 / 菜单项 / 标签 / 页签注入 toolTipText。
 *  3. 说明表来自 jar 内置 tooltips.txt，并可由程序目录下的 提示说明.txt 覆盖或扩充。
 *  4. 菜单项若带有 accelerator，气泡会自动附上真实快捷键。
 *  5. 装好定时器后，反射调用原入口 com.hiveworkshop.rms.Main.main(args)。
 */
public final class RmsTooltipInstaller {

    private static final String MARK = "rms.zh.tooltip.value";
    private static final String TIP_RESOURCE = "/tooltips.txt";
    private static final String EXTERNAL_NAME = "提示说明.txt";

    /** 说明表里以 !标签! 开头的行：把生硬的界面参数名改成一眼能懂的大白话。 */
    private static final String LABEL_PREFIX = "!标签!";
    private static final String LABEL_MARK = "rms.zh.label.fixed";
    private static final Map<String, String> LABEL_FIX = new LinkedHashMap<String, String>();

    private static final Map<String, String> TIPS = new LinkedHashMap<String, String>();
    private static final Map<String, String> RULES = new LinkedHashMap<String, String>();
    // 纯英文界面残留（主要来自第三方 InfoNode Docking 库）只做精确匹配，
    // 避免 "undo" 之类的短词误命中 "undock"。
    private static final Map<String, String> EN_RULES = new LinkedHashMap<String, String>();

    private static int applied = 0;
    private static int scans = 0;
    private static int lastReported = -1;
    private static boolean reportWritten = false;
    private static final List<String> SAMPLES = new ArrayList<String>();

    static {
        // ---------- 英文残留：精确匹配 ----------
        EN_RULES.put("undock", "把当前停靠面板拆成独立浮动窗口，可拖到另一块屏幕。");
        EN_RULES.put("dock", "把浮动面板重新停靠回主窗口。");
        EN_RULES.put("minimize", "把当前面板最小化到窗口边缘。");
        EN_RULES.put("maximize", "把当前面板最大化，占满整个工作区。||空格");
        EN_RULES.put("restore", "还原面板原来的大小与位置。");
        EN_RULES.put("close", "关闭当前面板或窗口。");
        EN_RULES.put("tab list", "列出所有已打开的页签，可快速切换。");
        EN_RULES.put("scroll left", "向左滚动页签栏。");
        EN_RULES.put("scroll right", "向右滚动页签栏。");
        EN_RULES.put("save", "保存当前模型。||Ctrl + S");
        EN_RULES.put("open", "打开模型文件。||Ctrl + O");
        EN_RULES.put("undo", "撤销上一步操作。||Ctrl + Z");
        EN_RULES.put("redo", "重做刚撤销的操作。||Ctrl + Y");
        EN_RULES.put("delete", "删除当前选中的对象。||Delete");
        EN_RULES.put("export", "导出模型或贴图文件。");
        EN_RULES.put("import", "导入外部模型或部件。");
        EN_RULES.put("particle", "编辑粒子发射器参数。");
        EN_RULES.put("texture", "管理模型贴图。");

        // ---------- 中文精确词：优先于下面的关键词模糊匹配 ----------
        RULES.put("取消选择", "从当前选择集中移除对象，或清空选择。");
        RULES.put("添加选择", "把当前对象追加到选择集中。||Shift + 点击");
        RULES.put("播放动画", "播放当前选中的动画序列，再按一次暂停。||Ctrl + 空格");
        RULES.put("选择顶点", "把编辑模式切换为按顶点选择。||S");
        RULES.put("选择面", "把编辑模式切换为按面选择。||F");
        RULES.put("选择组", "把编辑模式切换为按组选择。||G");
        RULES.put("选择顶点簇", "把编辑模式切换为按顶点簇选择。||D");
        RULES.put("选择节点并生成动画", "选中节点并进入动画编辑模式。||A");
        RULES.put("选择并摆成 T 姿态", "选中节点并把模型摆成 T 姿态，便于绑定骨骼。");
        RULES.put("默认循环", "按动画自身的设定决定是否循环播放。");
        RULES.put("始终循环", "无视动画设定，一直循环播放。");
        RULES.put("不循环", "播放一次后停止，不循环。");
        RULES.put("下蹲", "让模型做出下蹲姿态的快捷工具。");
        RULES.put("移动", "切换到位移模式，拖动操纵器移动所选。||W");
        RULES.put("旋转", "切换到旋转模式。||R");
        RULES.put("缩放", "切换到缩放模式。||E");
        RULES.put("扩展", "沿法线方向延伸所选几何体。||Y");
        RULES.put("最小化", "把当前面板收起到窗口边缘。");
        RULES.put("最大化", "把当前面板放大到占满工作区。||空格");
        RULES.put("还原", "把浮动面板恢复到原先的大小与位置。");
        RULES.put("细节层次", "设置模型显示的细节级别（LOD），数值越大越精细。");
        RULES.put("自定义单位", "展开自定义单位数据，可从中挑选模型。");
        RULES.put("标准单位", "展开游戏自带的标准单位数据。");
        RULES.put("预览", "在不写入模型的前提下预览当前设置效果。");
        RULES.put("过滤器", "按类型筛选当前列表里的条目。");
        RULES.put("文本", "只显示文本文件。");
        RULES.put("模型", "只显示模型文件。");
        RULES.put("地图", "只显示地图文件。");
        RULES.put("声音", "只显示声音文件。");
        RULES.put("音乐", "只显示音乐文件。");
        RULES.put("其他", "只显示其他类型的文件。");
        RULES.put("全部", "显示所有类型的文件。");
        RULES.put("war3x.txt", "魔兽 III 的数据表文件，通常不需要手动改动。");

        // ---------- 关键词模糊兜底：长词在前，泛词在后 ----------
        RULES.put("打开最近使用", "列出最近编辑过的模型文件，点一下即可重新打开。||Ctrl + O 附近的下拉菜单");
        RULES.put("另存为", "把当前模型换一个文件名保存，不覆盖原文件。||Ctrl + Q");
        RULES.put("全部清除", "清空当前列表里的所有数据源或条目，操作前请确认。");
        RULES.put("添加魔兽", "指定魔兽争霸 III 的安装目录，让程序能读取游戏内的模型与贴图。||Ctrl + Shift + D");
        RULES.put("重新计算法线", "按当前几何体形状重算法线，修复模型发黑或光照异常。||Ctrl + Shift + N");
        RULES.put("重新计算边界范围", "重算模型的包围盒范围，避免游戏中被错误剔除。||Ctrl + Shift + E");
        RULES.put("吸附邻近顶点", "把距离很近的顶点合并，消除缝隙与破面。||Ctrl + Shift + W");
        RULES.put("选择相连几何体", "选中与当前顶点/面通过边相连的一整片网格。||Ctrl + L");
        RULES.put("按节点选择几何体", "按骨骼或辅助点所属关系，一次选中该节点关联的全部几何体。");
        RULES.put("反选", "把已选和未选的部分互换。||Ctrl + I");
        RULES.put("扩展选择", "把选择范围向外扩一圈相邻顶点或面。||Ctrl + E");
        RULES.put("全选", "选中当前编辑模式下可选的全部对象。||Ctrl + A");
        RULES.put("撤销", "回退上一步编辑操作。||Ctrl + Z");
        RULES.put("重做", "恢复刚刚撤销的操作。||Ctrl + Y");
        RULES.put("复制", "把所选内容复制到剪贴板，或生成一份副本。||Ctrl + C / Ctrl + D");
        RULES.put("粘贴", "把剪贴板中的内容粘贴到当前模型。||Ctrl + V");
        RULES.put("剪切", "把所选内容剪切到剪贴板。||Ctrl + X");
        RULES.put("删除", "删除当前选中的对象；误删可用撤销找回。||Delete");
        RULES.put("重命名", "修改所选节点、动画或材质的名称。");
        RULES.put("新建", "创建一个空白模型，或新建一个模型组件。||Ctrl + N");
        RULES.put("打开", "打开磁盘上的模型文件并载入编辑器。||Ctrl + O");
        RULES.put("保存", "把当前模型写回磁盘。||Ctrl + S");
        RULES.put("关闭", "关闭当前模型或窗口。||Shift + Esc");
        RULES.put("退出", "退出程序，未保存的修改会提示确认。");
        RULES.put("导入", "把外部模型、部件或动画合并进当前模型。||Ctrl + Shift + I / Ctrl + Shift + U");
        RULES.put("导出", "把当前模型或贴图写出为文件。");
        RULES.put("编辑贴图", "打开贴图列表，增删贴图或修改贴图路径。");
        RULES.put("贴图", "管理模型用到的贴图文件与贴图动画。");
        RULES.put("材质", "设置渲染层、过滤模式与着色方式。");
        RULES.put("几何体", "管理模型的网格数据、可见性与材质归属。");
        RULES.put("关键帧", "在时间点上记录数值，让属性随时间变化。||← / → 跳帧");
        RULES.put("动画", "管理动画序列、播放与时间轴。||Ctrl + 空格");
        RULES.put("可见性", "控制该组件在哪些时间段显示；数值 0 表示隐藏，大于 0 表示显示。");
        RULES.put("粒子", "编辑粒子发射器的发射与外观参数。详见目录下的《粒子特效中文教程》。");
        RULES.put("发射器", "在模型中添加或编辑粒子、飘带、爆米花发射器。");
        RULES.put("发射速率", "每秒产生多少粒子；数值越大粒子越密。");
        RULES.put("生命周期", "单个粒子从生成到消失的时间（约以 0.1 秒为单位）。");
        RULES.put("重力", "粒子受到的向下加速度；负值可做出上飘的烟或火花。");
        RULES.put("速度", "粒子离开发射器时的初速度。");
        RULES.put("变化", "给速度、方向加入随机扰动，让粒子不排成整齐的一条线。");
        RULES.put("纬度", "发射锥体的张角，0 为一条直线，越大越发散。");
        RULES.put("过滤模式", "粒子贴图与背景的混合方式：混合 / 叠加 / 调制等。");
        RULES.put("优先级平面", "决定粒子与模型其他部分的绘制先后顺序。");
        RULES.put("子贴图", "把一张贴图切成行列网格，让粒子随机取其中一格。");
        RULES.put("尾长", "飘带或拖尾粒子的长度。");
        RULES.put("粒子缩放", "粒子随时间的大小变化曲线。");
        RULES.put("锁定布局", "固定当前面板排布，避免拖动时改变界面。");
        RULES.put("渲染粒子", "在 3D 预览视窗里显示/隐藏粒子效果，关掉后只显示模型本体。");
        RULES.put("线框", "切换网格线框显示，方便查看顶点与面。||Z");
        RULES.put("编辑快捷键", "打开完整的快捷键绑定列表，可给任意动作指定组合键。");
        RULES.put("保存快捷键", "把修改后的快捷键写入设置，重启后依然生效。");
        RULES.put("重置", "把本页所有设置恢复为程序默认值。");
        RULES.put("偏好设置", "打开设置窗口，配置数据源、颜色、快捷键与鼠标按键。");
        RULES.put("快捷键", "查看或修改本功能对应的键盘组合键。");
        RULES.put("帮助", "打开帮助与快捷键速查。");
        RULES.put("浏览", "在文件系统中选择文件或目录。");
        RULES.put("搜索", "按名称查找模型、节点或字符串。");
        RULES.put("应用", "使当前修改立即生效，但不关闭窗口。");
        RULES.put("确定", "确认并应用当前设置，关闭窗口。");
        RULES.put("取消", "放弃本次修改并关闭窗口。||Esc");
        RULES.put("添加", "新增一个条目或组件。");
        RULES.put("移除", "把所选条目从列表中去掉。");
        RULES.put("镜像", "沿指定轴翻转所选顶点或 UV 坐标。");
        RULES.put("优化", "清理冗余数据，减小模型体积。");
        RULES.put("拼接", "把另一个模型的部件合并进当前模型。");
        RULES.put("选择", "在 3D 视图或列表中选中对象；按住 Shift 追加、按住 Alt 减选。");
        RULES.put("播放", "播放当前动画序列。||Ctrl + 空格");
        RULES.put("暂停", "暂停动画播放。||Ctrl + 空格");
    }

    public static void main(String[] args) {
        loadTips();
        System.out.println("[中文提示] 已载入约 " + TIPS.size() + " 条功能说明，正在安装悬停气泡……");
        // 给阅读留出时间，同时使用可自由定位的重量级气泡窗口。
        try {
            ToolTipManager.sharedInstance().setInitialDelay(600);
            ToolTipManager.sharedInstance().setDismissDelay(8000);
            ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        } catch (Throwable ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            install();
            new javax.swing.Timer(1500, ev -> install()).start();
            new javax.swing.Timer(30, ev -> repositionVisibleTooltips()).start();
            System.out.println("[中文提示] 首次扫描完成，已设置 " + applied + " 个气泡。");
        });

        try {
            Class<?> cls = Class.forName("com.hiveworkshop.rms.Main");
            Method m = cls.getMethod("main", String[].class);
            m.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                cause.printStackTrace();
            } else {
                e.printStackTrace();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ---------------------------------------------------------------- 说明表

    private static void loadTips() {
        InputStream in = RmsTooltipInstaller.class.getResourceAsStream(TIP_RESOURCE);
        if (in != null) {
            try {
                parse(decode(readAll(in)), TIPS);
            } catch (IOException e) {
                System.out.println("[中文提示] 内置说明表读取失败: " + e);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
        File ext = externalFile();
        if (ext != null) {
            try {
                int n = parse(decode(Files.readAllBytes(ext.toPath())), TIPS);
                System.out.println("[中文提示] 外部说明表已生效：" + ext.getAbsolutePath() + "（" + n + " 条）");
            } catch (IOException e) {
                System.out.println("[中文提示] 外部说明表读取失败（将只用内置表）: " + e);
            }
        } else {
            System.out.println("[中文提示] 未找到外部说明表，使用内置说明表。");
        }
    }

    private static File externalFile() {
        String p = System.getProperty("rms.tips");
        if (p != null && p.trim().length() > 0) {
            return new File(p.trim());
        }
        try {
            URL u = RmsTooltipInstaller.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(u.toURI());
            File dir = f.isDirectory() ? f : f.getParentFile();
            if (dir != null) {
                File c = new File(dir, EXTERNAL_NAME);
                if (c.isFile()) {
                    return c;
                }
            }
        } catch (Exception ignored) {
        }
        File c2 = new File(System.getProperty("user.dir", "."), EXTERNAL_NAME);
        return c2.isFile() ? c2 : null;
    }

    /** 每行：界面文字=中文说明||快捷键。外部表同名条目覆盖内置表。 */
    private static int parse(String text, Map<String, String> out) {
        if (text == null) {
            return 0;
        }
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        int n = 0;
        for (String line : text.split("\\r?\\n")) {
            String s = line.trim();
            if (s.length() == 0 || s.startsWith("#") || s.startsWith("//")) {
                continue;
            }
            int p = s.indexOf('=');
            if (p <= 0) {
                continue;
            }
            String k = normalize(s.substring(0, p));
            String v = s.substring(p + 1).trim();
            if (k.length() == 0 || v.length() == 0) {
                continue;
            }
            // 标签改写行只进 LABEL_FIX，不当作气泡说明，也不计入说明条数。
            if (k.startsWith(LABEL_PREFIX)) {
                String old = normalize(k.substring(LABEL_PREFIX.length()));
                if (old.length() > 0) {
                    LABEL_FIX.put(old.toLowerCase(Locale.ROOT), v);
                }
                continue;
            }
            out.put(k, v);
            out.put(k.toLowerCase(Locale.ROOT), v);
            n++;
        }
        return n;
    }

    /** UFT-8 优先，遇到非法字节自动回退 GBK，兼容记事本另存的 ANSI 文件。 */
    private static String decode(byte[] bytes) {
        try {
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    // ---------------------------------------------------------------- 组件扫描

    private static void install() {
        ensureFont();
        Window[] windows = Window.getWindows();
        for (Window w : windows) {
            walk(w);
        }
        try {
            for (MenuElement me : MenuSelectionManager.defaultManager().getSelectedPath()) {
                walk(me.getComponent());
            }
        } catch (Throwable ignored) {
        }
        scans++;
        if (applied != lastReported || scans % 10 == 0) {
            System.out.println("[中文提示] 第 " + scans + " 次扫描：窗口 " + windows.length
                    + " 个，累计设置气泡 " + applied + " 个");
            lastReported = applied;
        }
        if (!reportWritten && applied > 0) {
            reportWritten = true;
            writeReport();
        }
    }

    /** 写一份可核对的报告，证明每个气泡里到底是什么中文内容。 */
    private static void writeReport() {
        try {
            File f = new File(System.getProperty("user.dir", "."), "气泡注入报告.txt");
            StringBuilder sb = new StringBuilder();
            sb.append("Retera Model Studio 中文版 · 悬停说明气泡注入报告\r\n");
            sb.append("本次共为 ").append(applied).append(" 个界面组件设置中文说明气泡。\r\n");
            sb.append("说明表：提示说明.txt（UTF-8，可自行编辑，重启程序生效）\r\n");
            sb.append("菜单项自带快捷键时，气泡还会自动附带真实组合键。\r\n\r\n");
            sb.append("===== 示例（前 ").append(SAMPLES.size()).append(" 条）=====\r\n");
            for (String s : SAMPLES) {
                sb.append(s).append("\r\n");
            }
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("[中文提示] 报告已写入 " + f.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[中文提示] 报告写入失败（不影响功能）: " + t);
        }
    }

    private static void ensureFont() {
        Object o = UIManager.get("ToolTip.font");
        if (o instanceof Font && "Microsoft YaHei".equals(((Font) o).getFamily())) {
            return;
        }
        try {
            UIManager.put("ToolTip.font",
                    new FontUIResource(new Font("Microsoft YaHei", Font.PLAIN, 12)));
        } catch (Throwable ignored) {
        }
    }

    private static void walk(Component c) {
        if (c == null) {
            return;
        }
        if (c instanceof JComponent) {
            handle((JComponent) c);
        }
        if (c instanceof JTabbedPane) {
            applyTabs((JTabbedPane) c);
        }
        if (c instanceof JMenu) {
            JPopupMenu pm = ((JMenu) c).getPopupMenu();
            if (pm != null) {
                for (Component x : pm.getComponents()) {
                    walk(x);
                }
            }
        }
        if (c instanceof Container) {
            for (Component x : ((Container) c).getComponents()) {
                walk(x);
            }
        }
    }

    /**
     * 把「纬度」「混合时间」这类生硬的参数名改成大白话。
     * 规则来自说明表的 !标签! 行，改文本即可调整，无需重编任何上游 class。
     * 每个组件只改写一次，避免每轮扫描重复写。
     */
    private static void applyLabelFix(JComponent c) {
        if (c.getClientProperty(LABEL_MARK) != null) {
            return;
        }
        String raw = textOf(c);
        if (!isEmpty(raw)) {
            String key = normalize(raw).toLowerCase(Locale.ROOT);
            String fixed = LABEL_FIX.get(key);
            if (fixed != null && !fixed.equals(raw.trim())) {
                // 保留原标签尾部的冒号，避免破坏面板排版
                String suffix = raw.trim().endsWith(":") || raw.trim().endsWith("：") ? "：" : "";
                String text = fixed + suffix;
                if (c instanceof JLabel) {
                    ((JLabel) c).setText(text);
                } else if (c instanceof AbstractButton) {
                    ((AbstractButton) c).setText(text);
                }
            }
        }
        c.putClientProperty(LABEL_MARK, Boolean.TRUE);
    }

    private static void handle(JComponent c) {
        if (!(c instanceof AbstractButton) && !(c instanceof JLabel)) {
            return;
        }
        applyLabelFix(c);
        String existing = c.getToolTipText();
        Object mark = c.getClientProperty(MARK);
        if (mark != null && mark.equals(existing)) {
            return; // 已经是我们设置过的，不重复处理
        }

        String raw = textOf(c);
        if (isEmpty(raw)) {
            raw = existing; // 纯图标按钮：用程序自带的英文 tooltip 当关键词
        }
        if (isEmpty(raw) && c instanceof AbstractButton) {
            Action a = ((AbstractButton) c).getAction();
            if (a != null && a.getValue(Action.NAME) != null) {
                raw = a.getValue(Action.NAME).toString();
            }
        }
        if (isEmpty(raw)) {
            raw = c.getName();
        }
        if (isEmpty(raw)) {
            return;
        }
        String key = normalize(raw);
        if (key.length() == 0) {
            return;
        }
        String tipValue = lookup(key);
        if (tipValue == null) {
            tipValue = guess(key, c instanceof JLabel);
        }
        if (tipValue == null) {
            return;
        }
        String tip = formatTip(key, tipValue, autoShortcut(c), existing);
        c.setToolTipText(tip);
        c.putClientProperty(MARK, tip);
        applied++;
        if (SAMPLES.size() < 80) {
            String[] pp = splitVal(tipValue);
            SAMPLES.add(key + " → " + pp[0]
                    + (pp[1].length() == 0 ? "" : "（快捷键：" + pp[1] + "）"));
        }
    }

    private static void applyTabs(JTabbedPane tp) {
        for (int i = 0; i < tp.getTabCount(); i++) {
            String key = normalize(tp.getTitleAt(i));
            if (key.length() == 0) {
                continue;
            }
            String v = lookup(key);
            if (v == null) {
                v = guess(key, true);
            }
            if (v == null) {
                continue;
            }
            String old = tp.getToolTipTextAt(i);
            String tip = formatTip(key, v, "", old);
            if (!tip.equals(old)) {
                tp.setToolTipTextAt(i, tip);
            }
        }
    }

    private static String textOf(JComponent c) {
        if (c instanceof AbstractButton) {
            return ((AbstractButton) c).getText();
        }
        if (c instanceof JLabel) {
            return ((JLabel) c).getText();
        }
        return null;
    }

    // ---------------------------------------------------------------- 文本处理

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ");
        t = t.replace('\u00A0', ' ').replace('&', ' ');
        t = t.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("\\s*[（(][^）)]{0,40}[）)]\\s*$", "").trim(); // 尾部 (Ctrl+N)
        t = t.replaceAll("[.…]+$", "").trim();                          // 尾部 ...
        t = t.replaceAll("[:：]\\s*$", "").trim();                       // 尾部标签冒号
        return t;
    }

    private static String lookup(String key) {
        return TIPS.get(key.toLowerCase(Locale.ROOT));
    }

    private static String guess(String key, boolean isLabel) {
        if (key.length() < 2 || key.length() > 48) {
            return null;
        }
        if (key.matches("[0-9\\s.+\\-*/:%°]+")) {
            return null; // 纯数字 / 坐标 / 百分比值，不加气泡
        }
        String low = key.toLowerCase(Locale.ROOT);
        // 1) 中文精确词优先，避免「取消选择」被「取消」抢先命中
        String exact = RULES.get(low);
        if (exact != null) {
            return exact;
        }
        // 2) 英文只做整段精确匹配，避免 undo 命中 undock
        String en = EN_RULES.get(low);
        if (en != null) {
            return en;
        }
        // 3) 最后才用中文关键词模糊匹配：取最长命中，并跳过过短的泛词。
        //    旧实现是「第一个 contains 命中即返回」，「粒子」「发射器」这类
        //    泛词会抢先命中，把「空丝带粒子」套成「编辑粒子发射器参数」。
        String bestKey = null;
        String bestVal = null;
        for (Map.Entry<String, String> e : RULES.entrySet()) {
            String rk = e.getKey().toLowerCase(Locale.ROOT);
            if (rk.length() < 3) {
                continue;
            }
            if (low.contains(rk) && (bestKey == null || rk.length() > bestKey.length())) {
                bestKey = rk;
                bestVal = e.getValue();
            }
        }
        if (bestVal != null) {
            return bestVal;
        }
        return isLabel
                ? "用于显示或设置「" + key + "」。修改数值后按回车或让输入框失去焦点即可生效。"
                : "执行「" + key + "」功能。完整操作说明见「帮助 → 快捷键速查」。";
    }

    private static String[] splitVal(String v) {
        int i = v.indexOf("||");
        if (i < 0) {
            return new String[]{v.trim(), ""};
        }
        return new String[]{v.substring(0, i).trim(), v.substring(i + 2).trim()};
    }

    private static String formatTip(String label, String rawTip, String autoShortcut, String original) {
        String[] parts = splitVal(rawTip);
        String desc = esc(parts[0]).replace("\\n", "<br>");
        String sc = parts[1].length() > 0 ? parts[1] : (autoShortcut == null ? "" : autoShortcut);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><div style='width:300px;padding:2px 3px;'>");
        sb.append("<b>").append(esc(label)).append("</b><br>");
        sb.append("<span style='font-size:12px;'>").append(desc).append("</span>");
        if (sc.length() > 0) {
            sb.append("<br><span style='color:#0b6fbd'>快捷键：").append(esc(sc)).append("</span>");
        }
        if (original != null && !original.equals(label) && !original.trim().isEmpty()
                && !original.startsWith("<html>")) {
            sb.append("<br><span style='color:#888888;font-size:10px'>原文：")
                    .append(esc(plain(original))).append("</span>");
        }
        sb.append("</div></html>");
        return sb.toString();
    }

    // ---------------------------------------------------------------- 气泡定位

    private static void repositionVisibleTooltips() {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) return;
        Point pointer = pointerInfo.getLocation();
        for (Window window : Window.getWindows()) {
            if (window instanceof JWindow && window.isShowing() && containsTooltip(window)) {
                Rectangle screen = screenBoundsAt(pointer);
                Point location = tooltipLocation(screen, window.getSize(), pointer);
                if (!location.equals(window.getLocation())) {
                    window.setLocation(location);
                }
            }
        }
    }

    private static boolean containsTooltip(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JToolTip) return true;
            if (child instanceof Container && containsTooltip((Container) child)) return true;
        }
        return false;
    }

    private static Rectangle screenBoundsAt(Point pointer) {
        try {
            for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                GraphicsConfiguration configuration = device.getDefaultConfiguration();
                Rectangle bounds = configuration.getBounds();
                if (bounds.contains(pointer)) {
                    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
                    return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                            bounds.width - insets.left - insets.right,
                            bounds.height - insets.top - insets.bottom);
                }
            }
            GraphicsConfiguration configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
            return configuration.getBounds();
        } catch (Throwable ignored) {
            return new Rectangle(pointer.x - 640, pointer.y - 480, 1280, 960);
        }
    }

    /** 纯定位逻辑：优先上右、上左，避免覆盖指针周围区域，并尽量完整留在屏幕内。 */
    static Point tooltipLocation(Rectangle screen, Dimension size, Point pointer) {
        final int gap = 18;
        Point[] candidates = {
                new Point(pointer.x + gap, pointer.y - size.height - gap),
                new Point(pointer.x - size.width - gap, pointer.y - size.height - gap),
                new Point(pointer.x + gap, pointer.y + gap),
                new Point(pointer.x - size.width - gap, pointer.y + gap)
        };
        Rectangle pointerZone = new Rectangle(pointer.x - 10, pointer.y - 10, 21, 21);
        Point best = null;
        long bestScore = Long.MAX_VALUE;
        for (Point candidate : candidates) {
            int maxX = screen.x + Math.max(0, screen.width - size.width);
            int maxY = screen.y + Math.max(0, screen.height - size.height);
            Point clamped = new Point(
                    Math.max(screen.x, Math.min(candidate.x, maxX)),
                    Math.max(screen.y, Math.min(candidate.y, maxY)));
            Rectangle tip = new Rectangle(clamped, size);
            Rectangle visible = screen.intersection(tip);
            long outside = (long) size.width * size.height
                    - (visible.isEmpty() ? 0L : (long) visible.width * visible.height);
            Rectangle overlap = pointerZone.intersection(tip);
            long covered = overlap.isEmpty() ? 0L : (long) overlap.width * overlap.height;
            long score = covered * 1000000L + outside;
            if (score < bestScore) {
                bestScore = score;
                best = clamped;
            }
            if (score == 0) break;
        }
        return best;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String plain(String s) {
        String t = s.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    /** 菜单项自带 accelerator 时，直接把真实快捷键显示到气泡里。 */
    private static String autoShortcut(JComponent c) {
        Object custom = c.getClientProperty("rms.accelerator");
        KeyStroke ks = custom instanceof KeyStroke ? (KeyStroke) custom : null;
        if (ks == null && c instanceof JMenuItem) {
            ks = ((JMenuItem) c).getAccelerator();
        }
        if (ks == null && c instanceof AbstractButton) {
            Action a = ((AbstractButton) c).getAction();
            if (a != null) {
                Object o = a.getValue(Action.ACCELERATOR_KEY);
                if (o instanceof KeyStroke) {
                    ks = (KeyStroke) o;
                }
            }
        }
        if (ks == null) {
            return "";
        }
        String mod = KeyEvent.getKeyModifiersText(ks.getModifiers()).replace("+", " + ");
        String k = ks.getKeyCode() != KeyEvent.VK_UNDEFINED
                ? KeyEvent.getKeyText(ks.getKeyCode())
                : String.valueOf(ks.getKeyChar());
        if (k.length() == 1) {
            k = k.toUpperCase(Locale.ROOT);
        }
        return mod.length() == 0 ? k : mod + " + " + k;
    }
}
