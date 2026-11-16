import com.hiveworkshop.rms.editor.actions.UndoAction;
import com.hiveworkshop.rms.editor.actions.mesh.DeleteGeosetAction;
import com.hiveworkshop.rms.editor.actions.nodes.DeleteNodesAction;
import com.hiveworkshop.rms.editor.actions.nodes.NameChangeAction;
import com.hiveworkshop.rms.editor.actions.selection.SetSelectionUggAction;
import com.hiveworkshop.rms.editor.actions.util.GenericMoveAction;
import com.hiveworkshop.rms.editor.actions.util.GenericRotateAction;
import com.hiveworkshop.rms.editor.actions.util.GenericScaleAction;
import com.hiveworkshop.rms.editor.model.CameraNode;
import com.hiveworkshop.rms.editor.model.ExtLog;
import com.hiveworkshop.rms.editor.model.Geoset;
import com.hiveworkshop.rms.editor.model.GeosetVertex;
import com.hiveworkshop.rms.editor.model.IdObject;
import com.hiveworkshop.rms.editor.model.Triangle;
import com.hiveworkshop.rms.editor.wrapper.v2.ModelView;
import com.hiveworkshop.rms.ui.application.ProgramGlobals;
import com.hiveworkshop.rms.ui.application.edit.ModelStructureChangeListener;
import com.hiveworkshop.rms.ui.application.edit.mesh.ModelEditor;
import com.hiveworkshop.rms.ui.application.edit.mesh.activity.UndoManager;
import com.hiveworkshop.rms.ui.application.edit.mesh.activity.ViewportActivityManager;
import com.hiveworkshop.rms.ui.application.viewer.CameraHandler;
import com.hiveworkshop.rms.ui.application.viewer.MouseListenerThing;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.ui.gui.modeledit.manipulator.ExtendManipulator;
import com.hiveworkshop.rms.ui.gui.modeledit.manipulator.ExtrudeManipulator;
import com.hiveworkshop.rms.ui.gui.modeledit.manipulator.Manipulator;
import com.hiveworkshop.rms.ui.gui.modeledit.manipulator.MoveDimension;
import com.hiveworkshop.rms.ui.gui.modeledit.manipulator.MoveManipulator;
import com.hiveworkshop.rms.ui.gui.modeledit.manipulator.RotateManipulator;
import com.hiveworkshop.rms.ui.gui.modeledit.manipulator.ScaleManipulator;
import com.hiveworkshop.rms.ui.gui.modeledit.modelviewtree.ComponentThingTree;
import com.hiveworkshop.rms.ui.gui.modeledit.modelviewtree.NodeThing;
import com.hiveworkshop.rms.ui.gui.modeledit.selection.SelectionBundle;
import com.hiveworkshop.rms.ui.gui.modeledit.selection.SelectionItemTypes;
import com.hiveworkshop.rms.util.Vec3;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Blender 风格视口增强（运行时注入，不修改任何原有 class）。
 *
 * 1. 性能：接管每个 PerspectiveViewport 的 paintTimer（可见 30FPS、不可见停）。
 * 2. 键鼠：Alt+右键平移、Alt+左键旋转；围绕所选渐进旋转。
 * 3. 抓取：G/R/S + X/Y/Z 轴向约束（Shift+X 排除轴），直接调用 ModelEditor 的
 *    beginTranslation / beginRotation(axis) / beginScaling，轴向约束真正生效。
 * 4. 联动：视口里点选骨骼后，左侧「节点树」自动展开并选中同一节点；
 *    节点树右键菜单（选中 / 聚焦 / 重命名），视口右键菜单（聚焦 / 重命名 / 视图）。
 * 5. 聚焦：小键盘 . 与主键盘 . 均聚焦所选（或整个模型）。
 */
public final class BlenderHooks {

    private static final String VIEWPORT_CLASS =
            "com.hiveworkshop.rms.ui.application.viewer.PerspectiveViewport";
    private static final int ALT = InputEvent.ALT_DOWN_MASK;
    private static final int BUTTON1 = InputEvent.BUTTON1_DOWN_MASK;
    private static final int BUTTON3 = InputEvent.BUTTON3_DOWN_MASK;
    private static final int VISIBLE_FPS_DELAY = 33;   // ~30 FPS

    private static final Set<Component> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<Component, Boolean>());

    public static void install() {
        ScalingHooks.install();
        if (Boolean.getBoolean("rms.blender.selftest")) {
            Thread t = new Thread(new SelfTest(), "BlenderSelfTest");
            t.setDaemon(true);
            t.start();
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new BlenderKeys());
        Toolkit.getDefaultToolkit().addAWTEventListener(new TabClickFixer(),
                AWTEvent.MOUSE_EVENT_MASK);
        Toolkit.getDefaultToolkit().addAWTEventListener(new TreeWatcher(),
                AWTEvent.MOUSE_EVENT_MASK);
        SwingUtilities.invokeLater(() -> {
            scan();
            // 顺带统一普通 JButton 的深色 Blender 风格（apply 幂等，覆盖动态新建的窗口）
            new Timer(500, ev -> {
                scan();
                BlenderButtonStyle.applyToAllWindows();
            }).start();
            System.out.println("[Blender] 视口/节点树联动、G/R/S/E/P 变换、按钮风格统一已启动"
                    + "（G移动 R旋转 S缩放 E挤出 P分离面）。");
        });
    }

    // ------------------------------------------------------------------ 扫描

    private static void scan() {
        for (Window w : Window.getWindows()) {
            walk(w);
        }
    }

    private static void walk(Component c) {
        if (c == null) return;
        try {
            squareStyle(c);
        } catch (Throwable ignored) {
            // 单个控件样式失败不能中断整棵树的接管
        }
        String cn = c.getClass().getName();
        if (DEBUG && cn.toLowerCase().contains("viewport")) {
            System.out.println("[Blender][debug] 发现视口候选: " + cn);
        }
        if (isViewport(c)) {
            manage(c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                walk(child);
            }
        }
    }

    // ------------------------------------------------------------------ 直角 UI

    private static final Color FLAT_EDGE = new Color(0x18, 0x18, 0x18);
    private static final Color FLAT_ACTIVE = new Color(0x47, 0x72, 0xB3);
    private static final javax.swing.border.Border FLAT_BORDER =
            new javax.swing.border.LineBorder(FLAT_EDGE, 1);
    private static final java.util.Map<String, String> TEXT_FIX = new java.util.HashMap<String, String>() {{
        put("All KF", "全部关键帧");
        put("Create Keyframe", "创建关键帧");
        put("Choose Time Bounds", "选择时间范围");
        put("Play", "播放");
        put("Pause", "暂停");
    }};

    /** 运行期补齐少量遗漏的英文按钮/提示。 */
    private static void fixText(AbstractButton b) {
        try {
            String text = b.getText();
            if (text != null && TEXT_FIX.containsKey(text)) b.setText(TEXT_FIX.get(text));
            String tip = b.getToolTipText();
            if (tip != null && TEXT_FIX.containsKey(tip)) b.setToolTipText(TEXT_FIX.get(tip));
        } catch (Throwable ignored) {
        }
    }

    /** 把 Swing 默认的圆角控件改成 Blender 风格直角。 */
    private static void squareStyle(Component c) {
        if (c instanceof JMenuItem) return;
        if (c instanceof javax.swing.JCheckBox || c instanceof javax.swing.JRadioButton) {
            fixText((AbstractButton) c);
            return; // 勾选框保留 LAF 自带的勾选图形，避免只剩文字
        }
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;
            fixText(b);
            if (isOutlineToggleButton(b)) {
                restoreOutlineButtonStyle(b);
                return; // 大纲 E/V 保留系统 LAF 的圆角样式
            }
            if (paintsItself(b.getClass())) return; // 自绘按钮（文字工具栏、模式按钮）保留自己的配色
            if (!(b.getUI() instanceof FlatButtonUI)) {
                b.setUI(FlatButtonUI.INSTANCE);
            }
            return;
        }
        if (c instanceof JComboBox || c instanceof JSpinner) {
            javax.swing.JComponent jc = (javax.swing.JComponent) c;
            if (jc.getBorder() != FLAT_BORDER) jc.setBorder(FLAT_BORDER);
            return;
        }
        if (c instanceof JTextComponent) {
            Container parent = c.getParent();
            if (parent instanceof JSpinner || parent instanceof JComboBox) return;
            JTextComponent jt = (JTextComponent) c;
            if (jt.getBorder() != FLAT_BORDER) jt.setBorder(FLAT_BORDER);
        }
    }

    private static final String OUTLINE_ROUND_MARK = "rms.blender.outline.round";

    /** 只识别大纲节点的 [E][V][名称] 结构，不影响其他同名按钮。 */
    private static boolean isOutlineToggleButton(AbstractButton b) {
        String text = b.getText();
        if (!"E".equals(text) && !"V".equals(text)) return false;
        Container parent = b.getParent();
        if (!(parent instanceof javax.swing.JPanel)
                || !(parent.getLayout() instanceof net.miginfocom.swing.MigLayout)
                || parent.getComponentCount() != 3) return false;
        Component editable = parent.getComponent(0);
        Component visible = parent.getComponent(1);
        return (b == editable || b == visible)
                && editable instanceof AbstractButton && "E".equals(((AbstractButton) editable).getText())
                && visible instanceof AbstractButton && "V".equals(((AbstractButton) visible).getText())
                && parent.getComponent(2) instanceof JLabel;
    }

    private static void restoreOutlineButtonStyle(AbstractButton b) {
        if (Boolean.TRUE.equals(b.getClientProperty(OUTLINE_ROUND_MARK))
                && !(b.getUI() instanceof FlatButtonUI)) return;
        if (b.getUI() instanceof FlatButtonUI) {
            b.setBorder(null); // 让当前 LAF 在 updateUI 时重新安装默认圆角边框
            b.updateUI();
        }
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setFocusPainted(true);
        b.setOpaque(true);
        b.putClientProperty(OUTLINE_ROUND_MARK, Boolean.TRUE);
        b.revalidate();
        b.repaint();
    }

    /** 类自身重写了 paintComponent 的按钮，说明它自己管理配色，不能再套平面 UI。 */
    private static boolean paintsItself(Class<?> cls) {
        for (Class<?> k = cls; k != null && k != javax.swing.JComponent.class; k = k.getSuperclass()) {
            try {
                k.getDeclaredMethod("paintComponent", java.awt.Graphics.class);
                return true;
            } catch (NoSuchMethodException ignored) {
                // 继续向父类查找
            }
        }
        return false;
    }

    /** 平面直角按钮绘制：只用背景色 + 1px 深色边，不画任何圆角。 */
    private static final class FlatButtonUI extends javax.swing.plaf.basic.BasicButtonUI {
        static final FlatButtonUI INSTANCE = new FlatButtonUI();

        @Override
        public void installUI(javax.swing.JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 10, 4, 10));
        }

        @Override
        public void paint(java.awt.Graphics g, javax.swing.JComponent c) {
            AbstractButton b = (AbstractButton) c;
            javax.swing.ButtonModel m = b.getModel();
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                Color base = javax.swing.UIManager.getColor("Button.background");
                if (base == null) base = new Color(0x3C, 0x3F, 0x41);
                Color fill = base;
                if (!b.isEnabled()) {
                    fill = base.darker();
                } else if (m.isArmed() && m.isPressed()) {
                    fill = base.darker();
                } else if (m.isRollover()) {
                    fill = base.brighter();
                }
                if (b instanceof javax.swing.JToggleButton && m.isSelected()) {
                    fill = FLAT_ACTIVE;
                }
                g2.setColor(fill);
                g2.fillRect(0, 0, c.getWidth(), c.getHeight());
                g2.setColor(FLAT_EDGE);
                g2.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
            } finally {
                g2.dispose();
            }
            super.paint(g, c);
        }
    }

    private static final boolean DEBUG = Boolean.getBoolean("rms.blender.debug");
    private static Class<?> VIEWPORT_TYPE;

    private static boolean isViewport(Component c) {
        String cn = c.getClass().getName();
        if (VIEWPORT_CLASS.equals(cn)) return true;
        if (!cn.endsWith("PerspectiveViewport")) return false;
        try {
            if (VIEWPORT_TYPE == null) VIEWPORT_TYPE = Class.forName(VIEWPORT_CLASS);
            return VIEWPORT_TYPE.isInstance(c);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void manage(Component vp) {
        managePaintTimer(vp);
        applyRuntimePrefs(vp);
        manageActivityManager(vp);
        if (INSTALLED.contains(vp)) return;
        try {
            MouseListenerThing original = (MouseListenerThing) field(vp, "mouseAdapter");
            CameraHandler camera = (CameraHandler) field(vp, "cameraHandler");
            if (original == null || camera == null) return;

            vp.removeMouseListener(original);
            vp.removeMouseMotionListener(original);
            vp.removeMouseWheelListener(original);

            BlenderMouse proxy = new BlenderMouse(vp, original, camera);
            vp.addMouseListener(proxy);
            vp.addMouseMotionListener(proxy);
            vp.addMouseWheelListener(proxy);

            INSTALLED.add(vp);
            System.out.println("[Blender] 视口已接管：" + vp.getClass().getSimpleName()
                    + " (共 " + INSTALLED.size() + " 个)");
        } catch (Throwable t) {
            System.out.println("[Blender] 视口接管失败（不影响原功能）: " + t);
        }
    }

    /** 原版透视编辑视口漏接活动管理器，导致鼠标选择和 G/R/S 都没有编辑器。 */
    private static void manageActivityManager(Component vp) {
        try {
            ModelPanel panel = ProgramGlobals.getCurrentModelPanel();
            if (panel == null || field(vp, "modelView") != panel.getModelView()) return;
            ViewportActivityManager am = panel.getViewportActivityManager();
            CameraHandler camera = (CameraHandler) field(vp, "cameraHandler");
            MouseListenerThing mouse = (MouseListenerThing) field(vp, "mouseAdapter");
            if (camera != null && field(camera, "activityManager") != am) {
                camera.setActivityManager(am);
            }
            if (mouse != null && field(mouse, "activityManager") != am) {
                mouse.setActivityManager(am);
            }
        } catch (Throwable t) {
            System.out.println("[Blender] 视口编辑器连接失败: " + t);
        }
    }

    /** 修复原版 paintTimer 停掉后永不重启的问题，并把 60FPS 降到 30FPS。 */
    private static void managePaintTimer(Component vp) {
        try {
            Timer timer = (Timer) field(vp, "paintTimer");
            if (timer == null) return;
            boolean showing = vp.isShowing() && vp.isVisible() && vp.getWidth() > 0;
            if (showing) {
                if (timer.getDelay() != VISIBLE_FPS_DELAY) timer.setDelay(VISIBLE_FPS_DELAY);
                if (!timer.isRunning()) timer.start();
            } else if (timer.isRunning()) {
                timer.stop();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyRuntimePrefs(Component vp) {
        try {
            Object prefs = field(vp, "programPreferences");
            if (prefs == null) return;
            setRuntimePref(prefs, "threeDCameraSpinMouseEx", ALT | BUTTON1);
            setRuntimePref(prefs, "threeDCameraPanMouseEx", ALT | BUTTON3);
            setRuntimePref(prefs, "cameraToggleOrtho", KeyEvent.VK_NUMPAD5);
        } catch (Throwable ignored) {
        }
    }

    /** scan() 每 500ms 调用一次；只在值不同时写入，避免覆盖用户后续自定义的偏好。 */
    private static void setRuntimePref(Object prefs, String name, Object value) throws Exception {
        Object cur = field(prefs, name);
        if (cur == null ? value == null : cur.equals(value)) return;
        setField(prefs, name, value);
    }

    // ------------------------------------------------------------------ 页签点击修复

    private static final class TabClickFixer implements AWTEventListener {
        private static final String STRIP_CLASS = "net.infonode.gui.draggable.DraggableComponentBox";
        private static final String TAB_CLASS = "net.infonode.docking.WindowTab";

        @Override
        public void eventDispatched(AWTEvent ev) {
            if (!(ev instanceof MouseEvent)) return;
            MouseEvent me = (MouseEvent) ev;
            if (me.getID() != MouseEvent.MOUSE_PRESSED) return;
            if ((me.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) == 0) return;
            if (insideButton(me.getComponent())) return;
            try {
                int px = me.getXOnScreen();
                int py = me.getYOnScreen();

                List<Component> strips = new ArrayList<>();
                for (Window w : Window.getWindows()) {
                    collectStrips(w, strips);
                }
                if (strips.isEmpty()) return;

                Component exact = null;
                Component band = null;
                for (Component s : strips) {
                    if (!s.isShowing()) continue;
                    Point sp = s.getLocationOnScreen();
                    Rectangle r = new Rectangle(sp.x, sp.y, s.getWidth(), s.getHeight());
                    if (px < r.x - 4 || px > r.x + r.width + 4) continue;
                    if (py >= r.y - 3 && py <= r.y + r.height + 3) {
                        if (exact == null || s.getWidth() < exact.getWidth()) exact = s;
                    } else if (py > r.y + r.height + 3 && py <= r.y + r.height + 26) {
                        if (band == null || s.getWidth() > band.getWidth()) band = s;
                    }
                }
                Component strip = exact != null ? exact : band;
                if (strip == null) return;

                List<Component> tabs = new ArrayList<>();
                collectTabs(strip, tabs);
                if (tabs.isEmpty()) return;
                tabs.sort(Comparator.comparingInt(c -> c.getLocationOnScreen().x));

                int hit = -1;
                for (int i = 0; i < tabs.size(); i++) {
                    Component t = tabs.get(i);
                    Point tp = t.getLocationOnScreen();
                    if (px >= tp.x - 4 && px <= tp.x + t.getWidth() + 4) {
                        hit = i;
                        break;
                    }
                }
                if (hit < 0 && exact != null) {
                    int best = Integer.MAX_VALUE;
                    for (int i = 0; i < tabs.size(); i++) {
                        Component t = tabs.get(i);
                        int cx = t.getLocationOnScreen().x + t.getWidth() / 2;
                        int d = Math.abs(px - cx);
                        if (d < best) {
                            best = d;
                            hit = i;
                        }
                    }
                    if (best > 40) return;
                }
                if (hit < 0) return;

                final Container tabWindow = findParentTabWindow(strip);
                if (tabWindow == null) return;
                final int idx = hit;
                SwingUtilities.invokeLater(() -> selectTab(tabWindow, idx));
            } catch (Throwable ignored) {
            }
        }

        private void selectTab(Container tabWindow, int idx) {
            try {
                Method m = tabWindow.getClass().getMethod("setSelectedTab", int.class);
                m.setAccessible(true);
                m.invoke(tabWindow, idx);
            } catch (Throwable ignored) {
            }
        }

        private Container findParentTabWindow(Component c) {
            for (Component p = c; p != null; p = p.getParent()) {
                String n = p.getClass().getName();
                if (n.equals("net.infonode.docking.TabWindow")
                        || n.equals("net.infonode.docking.RootWindow")) {
                    return (Container) p;
                }
            }
            return null;
        }

        private boolean insideButton(Component c) {
            for (Component p = c; p != null; p = p.getParent()) {
                if (p instanceof AbstractButton) return true;
            }
            return false;
        }

        private void collectStrips(Component c, List<Component> out) {
            if (c == null) return;
            if (STRIP_CLASS.equals(c.getClass().getName())) {
                out.add(c);
                return;
            }
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    collectStrips(child, out);
                }
            }
        }

        private void collectTabs(Component c, List<Component> out) {
            if (c == null) return;
            if (TAB_CLASS.equals(c.getClass().getName())) {
                if (c.isShowing()) out.add(c);
                return;
            }
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    collectTabs(child, out);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 视口 <-> 节点树联动

    /** 视口里选中的 IdObject 同步到左侧节点树：展开父级、选中行并滚动到可见。 */
    private static void syncTreeToSelection(Component vp) {
        try {
            Object mvObj = field(vp, "modelView");
            if (!(mvObj instanceof ModelView)) return;
            Set<IdObject> sel = new HashSet<>(((ModelView) mvObj).getSelectedIdObjects());
            if (sel.isEmpty()) return;
            for (Window w : Window.getWindows()) {
                syncTreesIn(w, sel);
            }
        } catch (Throwable t) {
            System.out.println("[Blender] 节点树同步失败: " + t);
        }
    }

    private static void syncTreesIn(Component c, Set<IdObject> sel) {
        if (c == null) return;
        if (c instanceof JTree) {
            highlightTree((JTree) c, sel);
            return;
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                syncTreesIn(child, sel);
            }
        }
    }

    /** 节点树里每一行对应的 IdObject / 其它条目。 */
    private static Object itemOf(Object treeNode) {
        if (treeNode instanceof NodeThing) return ((NodeThing<?>) treeNode).getItem();
        if (treeNode instanceof javax.swing.tree.DefaultMutableTreeNode) {
            Object uo = ((javax.swing.tree.DefaultMutableTreeNode) treeNode).getUserObject();
            if (uo != null) {
                if (checkableClass().isInstance(uo)) {
                    try {
                        return uo.getClass().getMethod("getItem").invoke(uo);
                    } catch (Throwable ignored) {
                    }
                }
                // 「模型」页的 ChoosableDisplayElement 把模型对象放在 item 字段里，
                // 没有 getItem()，这里用反射取出，让该树也能右键 / Delete 删除节点。
                if (uo.getClass().getName().endsWith("ChoosableDisplayElement")) {
                    try {
                        return field(uo, "item");
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    /** 从任意组件向上找到它所在的 JTree。 */
    private static JTree treeOf(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof JTree) return (JTree) p;
        }
        return null;
    }

    /**
     * 删除节点树里选中的对象。必须走现成 UndoAction：DeleteNodesAction 内建
     * 子节点 reparent 与顶点骨骼绑定清理，直接改 EditableModel 会留下悬空引用且无法撤销。
     */
    private static boolean deleteTreeItem(JTree tree, Object item) {
        if (item == null) return false;
        try {
            UndoManager undo = undoOf(tree);
            ModelView mv = modelViewOf(tree);
            if (undo == null || mv == null) return false;
            UndoAction action;
            if (item instanceof IdObject) {
                action = new DeleteNodesAction((IdObject) item,
                        ModelStructureChangeListener.changeListener, mv.getModel());
            } else if (item instanceof Geoset) {
                action = new DeleteGeosetAction(mv.getModel(), (Geoset) item,
                        ModelStructureChangeListener.changeListener);
            } else if (item instanceof com.hiveworkshop.rms.editor.model.Camera) {
                action = new DeleteNodesAction((com.hiveworkshop.rms.editor.model.Camera) item,
                        ModelStructureChangeListener.changeListener, mv.getModel());
            } else {
                return false;
            }
            undo.pushAction(action.redo());
            ProgramGlobals.getMainPanel().repaintSelfAndChildren();
            repaintAll();
            return true;
        } catch (Throwable t) {
            System.out.println("[Blender] 删除节点失败: " + t);
            return false;
        }
    }

    private static Class<?> CHECKABLE;

    private static Class<?> checkableClass() {
        if (CHECKABLE == null) {
            try {
                CHECKABLE = Class.forName(
                        "com.hiveworkshop.rms.ui.gui.modeledit.modelviewtree.CheckableDisplayElement");
            } catch (Throwable t) {
                CHECKABLE = Object.class;
            }
        }
        return CHECKABLE;
    }

    private static void highlightTree(JTree tree, Set<IdObject> sel) {
        try {
            TreeModel model = tree.getModel();
            if (model == null || model.getRoot() == null) return;
            List<TreePath> paths = new ArrayList<>();
            collectNodePaths(model, new TreePath(model.getRoot()), sel, paths);
            if (paths.isEmpty()) return;
            for (TreePath p : paths) {
                TreePath parent = p.getParentPath();
                if (parent != null) tree.expandPath(parent);
                // 原版高亮色 alpha 只有 20，几乎看不见，这里调亮
                Object node = p.getLastPathComponent();
                if (node instanceof NodeThing) {
                    setField(node, "color2", HIGHLIGHT);
                    setField(node, "color1", NORMAL_BG);
                }
            }
            tree.setSelectionPaths(paths.toArray(new TreePath[0]));
            tree.scrollPathToVisible(paths.get(0));
            tree.repaint();
            System.out.println("[Blender] 节点树联动: 已定位 " + paths.size() + " 个节点");
        } catch (Throwable t) {
            System.out.println("[Blender] 节点树定位失败: " + t);
        }
    }

    private static final java.awt.Color HIGHLIGHT = new java.awt.Color(255, 145, 0, 120);
    private static final java.awt.Color NORMAL_BG = new java.awt.Color(255, 255, 255, 0);

    private static void collectNodePaths(TreeModel model, TreePath path,
                                         Set<IdObject> sel, List<TreePath> out) {
        if (out.size() >= 200) return;
        Object item = itemOf(path.getLastPathComponent());
        if (item instanceof IdObject && sel.contains(item)) {
            out.add(path);
        }
        Object node = path.getLastPathComponent();
        int n = model.getChildCount(node);
        for (int i = 0; i < n; i++) {
            collectNodePaths(model, path.pathByAddingChild(model.getChild(node, i)), sel, out);
        }
    }

    /** 节点树里右键选中/聚焦/重命名。 */
    private static final class TreeWatcher implements AWTEventListener {
        @Override
        public void eventDispatched(AWTEvent ev) {
            if (!(ev instanceof MouseEvent)) return;
            MouseEvent me = (MouseEvent) ev;
            if (me.getID() == MouseEvent.MOUSE_RELEASED && me.getButton() == MouseEvent.BUTTON1) {
                if (findTree(me.getComponent()) != null) {
                    SwingUtilities.invokeLater(BlenderHooks::repaintAll);
                }
                return;
            }
            if (me.getID() != MouseEvent.MOUSE_PRESSED || me.getButton() != MouseEvent.BUTTON3) return;
            JTree tree = findTree(me.getComponent());
            if (tree == null) return;
            try {
                Point pt = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), tree);
                if (pt.x < 0 || pt.y < 0 || pt.x > tree.getWidth() || pt.y > tree.getHeight()) return;
                int row = tree.getClosestRowForLocation(pt.x, pt.y);
                if (row < 0) return;
                Rectangle rb = tree.getRowBounds(row);
                if (rb != null && !rb.contains(pt)) return;
                TreePath path = tree.getPathForRow(row);
                if (path == null) return;
                Object item = itemOf(path.getLastPathComponent());
                if (!(item instanceof IdObject)) return;
                showNodeMenu(tree, (IdObject) item, pt.x, pt.y);
                me.consume();
            } catch (Throwable t) {
                System.out.println("[Blender] 节点树右键失败: " + t);
            }
        }

        private JTree findTree(Component c) {
            for (Component p = c; p != null; p = p.getParent()) {
                if (p instanceof JTree) return (JTree) p;
            }
            return null;
        }
    }

    private static void showNodeMenu(final JTree tree, final IdObject node, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem sel = new JMenuItem("选中此节点");
        sel.addActionListener(e -> {
            selectNode(tree, node);
            syncTreeToSelectionFromTree(tree, node);
        });
        menu.add(sel);

        JMenuItem focus = new JMenuItem("聚焦此节点 (.)");
        focus.addActionListener(e -> focusNode(node));
        menu.add(focus);

        JMenuItem rename = new JMenuItem("重命名节点…");
        rename.addActionListener(e -> renameNode(tree, node));
        menu.add(rename);

        JMenuItem deleteNode = new JMenuItem("删除节点 (Delete)");
        deleteNode.addActionListener(e -> deleteTreeItem(tree, node));
        menu.add(deleteNode);

        menu.addSeparator();
        // 节点树右键：直接把被右击的 node 作为默认父级，
        // 弹出的添加粒子对话框会预选它并把坐标同步到它的 pivot。
        JMenu addEffectFromNode = new JMenu("添加粒子特效（挂到此节点）");
        try {
            com.hiveworkshop.rms.ui.application.AddParticlePanel
                    .addParticleButtons(addEffectFromNode, node);
        } catch (Throwable t) {
            System.out.println("[Blender] 节点树粒子菜单失败: " + t);
        }
        menu.add(addEffectFromNode);

        JMenuItem focusSel = new JMenuItem("聚焦所选 (.)");
        focusSel.addActionListener(e -> frameSelected(activeViewport()));
        menu.add(focusSel);

        menu.show(tree, x, y);
    }

    private static void selectNode(JTree tree, IdObject node) {
        try {
            ModelView mv = modelViewOf(tree);
            UndoManager undo = undoOf(tree);
            if (mv == null || undo == null) return;
            SelectionBundle bundle = new SelectionBundle(
                    Collections.<IdObject>singleton(node));
            UndoAction action = new SetSelectionUggAction(bundle, mv,
                    ModelStructureChangeListener.changeListener).redo();
            undo.pushAction(action);
            repaintAll();
        } catch (Throwable t) {
            System.out.println("[Blender] 选中节点失败: " + t);
        }
    }

    private static ModelView modelViewOf(Component tree) {
        try {
            Object mv = field(tree, "modelView");
            if (mv instanceof ModelView) return (ModelView) mv;
            Component vp = activeViewport();
            if (vp != null) {
                Object v = field(vp, "modelView");
                if (v instanceof ModelView) return (ModelView) v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static UndoManager undoOf(Component tree) {
        try {
            Object undo = field(tree, "undoManager");
            if (undo instanceof UndoManager) return (UndoManager) undo;
        } catch (Throwable ignored) {
        }
        Component vp = activeViewport();
        if (vp != null) {
            try {
                Object am = field(vp, "viewportActivityManager");
                Object act = field(am, "currentActivity");
                Object u = field(act, "undoManager");
                if (u instanceof UndoManager) return (UndoManager) u;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void syncTreeToSelectionFromTree(JTree tree, IdObject node) {
        try {
            Set<IdObject> one = Collections.singleton(node);
            highlightTree(tree, one);
        } catch (Throwable ignored) {
        }
    }

    private static void renameNode(JTree tree, IdObject node) {
        try {
            UndoManager undo = undoOf(tree);
            if (undo == null) return;
            String name = JOptionPane.showInputDialog(tree, "输入新的骨骼/节点名称：", node.getName());
            if (name == null || name.trim().isEmpty()) return;
            undo.pushAction(new NameChangeAction(node, name.trim(),
                    ModelStructureChangeListener.changeListener).redo());
            syncTreeToSelectionFromTree(tree, node);
            repaintAll();
        } catch (Throwable t) {
            System.out.println("[Blender] 重命名失败: " + t);
        }
    }

    private static void renameSelected(Component vp) {
        try {
            ModelView mv = (ModelView) field(vp, "modelView");
            UndoManager undo = undoManagerOf(vp);
            if (mv == null || undo == null) return;
            Set<IdObject> sel = mv.getSelectedIdObjects();
            if (sel.size() != 1) {
                JOptionPane.showMessageDialog(vp, sel.isEmpty()
                        ? "没有选中骨骼/节点。" : "一次只能重命名一个节点。");
                return;
            }
            IdObject node = sel.iterator().next();
            String name = JOptionPane.showInputDialog(vp, "输入新的骨骼/节点名称：", node.getName());
            if (name == null || name.trim().isEmpty()) return;
            undo.pushAction(new NameChangeAction(node, name.trim(),
                    ModelStructureChangeListener.changeListener).redo());
            syncTreeToSelection(vp);
            repaintAll();
        } catch (Throwable t) {
            System.out.println("[Blender] 重命名所选失败: " + t);
        }
    }

    // ------------------------------------------------------------------ 聚焦

    private static void focusNode(IdObject node) {
        Component vp = activeViewport();
        if (vp == null) return;
        CameraHandler cam = cameraOf(vp);
        if (cam == null) return;
        Vec3 p = node.getPivotPoint();
        double modelRadius = modelRadius(vp);
        applyCamera(cam, p, Math.max(modelRadius * 0.14, 2.0));
        repaintAll();
        System.out.println("[Blender] 聚焦节点: " + node.getName());
    }

    private static void frameSelected(Component vp) {
        if (vp == null) vp = activeViewport();
        if (vp == null) return;
        CameraHandler cam = cameraOf(vp);
        if (cam == null) return;
        double[] b = selectionBounds(vp);
        if (b != null) {
            applyCamera(cam, new Vec3(b[0], b[1], b[2]), Math.max(b[3] * 0.9, 0.5));
            System.out.println("[Blender] 聚焦所选: r=" + (float) b[3]);
        } else {
            double r = modelRadius(vp);
            Vec3 c = modelCenter(vp);
            applyCamera(cam, c, Math.max(r * 0.9, 1.0));
            System.out.println("[Blender] 聚焦整个模型: r=" + (float) r);
        }
        repaintAll();
    }

    /**
     * 把模型点 p 移到视口正中，并让半径为 radius 的球刚好占满画面。
     *
     * 相机模型（CameraHandler.normalSetUpCamera）：
     *   eye = A * (Rx*Ry*Rz*(zoom*v) - cameraPos)
     * 因此要让点 p 居中，只需 cameraPos.y/z = zoom*(R*p).y/z；
     * cameraPos.x 是相机沿视线方向的距离，必须比点的深度 zoom*(R*p).x 更大，
     * 才能把点放在相机前方。
     */
    private static void applyCamera(CameraHandler cam, Vec3 p, double radius) {
        try {
            Vec3 camPos = (Vec3) field(cam, "cameraPos");
            if (camPos == null) return;
            double zoom = cam.getZoom();
            if (!(zoom > 1e-4)) zoom = 0.2;
            Vec3 rc = rotateCam(cam, p);
            int h = Math.max(1, viewportHeightOf(cam));
            // 画面半高对应的世界半径 = (h/2)*camPos.x/(600*zoom)，取 1.15 倍留边
            double fit = 1200.0 * 1.15 * Math.max(radius, 0.2) * zoom / h;
            double front = zoom * rc.x + 1.0;
            camPos.set(Math.max(fit, front), zoom * rc.y, zoom * rc.z);
        } catch (Throwable t) {
            System.out.println("[Blender] 相机聚焦失败: " + t);
        }
    }

    private static int viewportHeightOf(CameraHandler cam) {
        try {
            Object vp = field(cam, "viewport");
            if (vp instanceof Component) return ((Component) vp).getHeight();
        } catch (Throwable ignored) {
        }
        return 800;
    }

    /** 模型坐标 -> 相机坐标系（与 glRotatef x/y/z 顺序一致）。 */
    private static Vec3 rotateCam(CameraHandler cam, Vec3 p) {
        double rx = Math.toRadians(cam.getXAngle());
        double ry = Math.toRadians(cam.getYAngle());
        double rz = Math.toRadians(cam.getZAngle());
        double x1 = p.x * Math.cos(rz) - p.y * Math.sin(rz);
        double y1 = p.x * Math.sin(rz) + p.y * Math.cos(rz);
        double z1 = p.z;
        double x2 = x1 * Math.cos(ry) + z1 * Math.sin(ry);
        double y2 = y1;
        double z2 = -x1 * Math.sin(ry) + z1 * Math.cos(ry);
        double x3 = x2;
        double y3 = y2 * Math.cos(rx) - z2 * Math.sin(rx);
        double z3 = y2 * Math.sin(rx) + z2 * Math.cos(rx);
        return new Vec3(x3, y3, z3);
    }

    // ------------------------------------------------------------------ 鼠标

    private static final class BlenderMouse extends MouseAdapter {
        private final Component viewport;
        private final MouseListenerThing original;
        private final CameraHandler camera;
        private boolean navigating = false;
        private boolean panning = false;
        private boolean grabJustEnded = false;
        private int lastX, lastY;
        private int pressX, pressY;
        private int rightX, rightY;
        private boolean rightPressed = false;
        private double[] pivot;

        BlenderMouse(Component viewport, MouseListenerThing original, CameraHandler camera) {
            this.viewport = viewport;
            this.original = original;
            this.camera = camera;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            LAST_MOUSE_X = e.getX();
            LAST_MOUSE_Y = e.getY();
            int m = e.getModifiersEx();
            if (GRAB != null && GRAB.viewport() == viewport) {
                boolean left = (m & BUTTON1) != 0;
                GRAB.finish(!left);
                grabJustEnded = true;
                e.consume();
                return;
            }
            boolean alt = (m & ALT) != 0;
            boolean left = (m & BUTTON1) != 0;
            boolean right = (m & BUTTON3) != 0;
            if (alt && (left || right)) {
                navigating = true;
                panning = right;
                lastX = e.getX();
                lastY = e.getY();
                pivot = selectionCenter(viewport);
                viewport.requestFocusInWindow();
                e.consume();
                return;
            }
            if (right && !alt) {
                rightPressed = true;
                rightX = e.getX();
                rightY = e.getY();
                e.consume();
                return;
            }
            original.mousePressed(e);
            pressX = e.getX();
            pressY = e.getY();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            LAST_MOUSE_X = e.getX();
            LAST_MOUSE_Y = e.getY();
            if (GRAB != null && GRAB.viewport() == viewport) {
                GRAB.drive(e.getX(), e.getY());
                e.consume();
                return;
            }
            if (!navigating) {
                original.mouseDragged(e);
                return;
            }
            int dx = e.getX() - lastX;
            int dy = e.getY() - lastY;
            lastX = e.getX();
            lastY = e.getY();
            if (dx == 0 && dy == 0) {
                e.consume();
                return;
            }
            if (panning) {
                camera.translate(-dx, dy);
            } else {
                camera.rotate(dx, dy);
                if (pivot != null) {
                    Vec3 pos = camera.getCameraPos();
                    double zoom = camera.getZoom();
                    double targetY = pivot[1] * zoom;
                    double targetZ = pivot[2] * zoom;
                    pos.y += (targetY - pos.y) * 0.18;
                    pos.z += (targetZ - pos.z) * 0.18;
                }
            }
            viewport.repaint();
            e.consume();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            LAST_MOUSE_X = e.getX();
            LAST_MOUSE_Y = e.getY();
            if (GRAB != null && GRAB.viewport() == viewport) {
                GRAB.drive(e.getX(), e.getY());
                e.consume();
                return;
            }
            if (!navigating) {
                try {
                    original.mouseMoved(e);
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (grabJustEnded) {
                grabJustEnded = false;
                e.consume();
                return;
            }
            if (rightPressed) {
                rightPressed = false;
                boolean moved = Math.abs(e.getX() - rightX) > 4 || Math.abs(e.getY() - rightY) > 4;
                if (!moved) {
                    // 右键前先快照选区：下面的程序化点选会整体替换选区，
                    // 若点在空白处会把用户在大纲树选中的骨骼清掉，
                    // 导致「添加粒子特效」的父级预选失效（第十轮反馈）。
                    java.util.List<IdObject> beforePick = java.util.Collections.emptyList();
                    try {
                        beforePick = snapshotSelection(field(viewport, "modelView"));
                    } catch (Exception ignored) {
                    }
                    performSelectClick(e.getX(), e.getY());
                    try {
                        Object mv = field(viewport, "modelView");
                        if (snapshotSelection(mv).isEmpty() && !beforePick.isEmpty()) {
                            restoreSelection(mv, beforePick);
                        }
                    } catch (Exception ignored) {
                    }
                    syncTreeToSelection(viewport);
                    showViewportMenu(viewport, e.getX(), e.getY());
                }
                e.consume();
                return;
            }
            if (navigating) {
                navigating = false;
                pivot = null;
                e.consume();
                return;
            }
            original.mouseReleased(e);
            // 视口里点选骨骼后，左侧节点树自动定位
            syncTreeToSelection(viewport);
            dedupeFaceClick(viewport, camera, e, pressX, pressY);
        }

        /** 用程序化的左键单击在当前视口点选元素。 */
        private void performSelectClick(int x, int y) {
            try {
                MouseEvent press = new MouseEvent(viewport, MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), BUTTON1, x, y, 1, false, MouseEvent.BUTTON1);
                MouseEvent release = new MouseEvent(viewport, MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
                original.mousePressed(press);
                original.mouseReleased(release);
                repaintAll();
            } catch (Throwable t) {
                System.out.println("[Blender] 右键点选失败: " + t);
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            original.mouseWheelMoved(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (!navigating) original.mouseClicked(e);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            original.mouseEntered(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            original.mouseExited(e);
        }
    }

    /** 视口右键那一刻选中的单个 IdObject；无选中 / 多选返回 null。 */
    private static IdObject firstSelectedIdObject(Component vp) {
        try {
            Object mv = field(vp, "modelView");
            if (mv instanceof ModelView) {
                ModelView view = (ModelView) mv;
                Set<IdObject> sel = view.getSelectedIdObjects();
                if (sel != null && sel.size() == 1) {
                    return sel.iterator().next();
                }
                // 兜底：视图模式下 idObjectsEditable/Visible 关闭时
                // getSelectedIdObjects() 恒返回空集，改读原始选区字段。
                try {
                    java.lang.reflect.Field f = ModelView.class.getDeclaredField("selectedIdObjects");
                    f.setAccessible(true);
                    Object raw = f.get(view);
                    if (raw instanceof Set) {
                        Set<?> rawSet = (Set<?>) raw;
                        if (rawSet.size() == 1 && rawSet.iterator().next() instanceof IdObject) {
                            return (IdObject) rawSet.iterator().next();
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 取当前选区的不可变快照；取不到时返回空列表。 */
    private static java.util.List<IdObject> snapshotSelection(Object mv) {
        try {
            if (mv instanceof ModelView) {
                Set<IdObject> sel = ((ModelView) mv).getSelectedIdObjects();
                if (sel == null || sel.isEmpty()) {
                    return java.util.Collections.emptyList();
                }
                return new java.util.ArrayList<>(sel);
            }
        } catch (Throwable ignored) {
        }
        return java.util.Collections.emptyList();
    }

    /** 把快照写回选区（仅当快照非空才调用）。 */
    private static void restoreSelection(Object mv, java.util.List<IdObject> snapshot) {
        try {
            if (mv instanceof ModelView && snapshot != null && !snapshot.isEmpty()) {
                ((ModelView) mv).setSelectedIdObjects(snapshot);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void showViewportMenu(final Component vp, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        // 右键前 BlenderMouse 已做过程序化点选，这里拿到的就是刚点中的骨骼
        IdObject defaultParent = firstSelectedIdObject(vp);

        JMenuItem focusSel = new JMenuItem("聚焦所选 (.)");
        focusSel.addActionListener(e -> frameSelected(vp));
        menu.add(focusSel);

        JMenuItem rename = new JMenuItem("重命名所选骨骼…");
        rename.addActionListener(e -> renameSelected(vp));
        menu.add(rename);

        menu.addSeparator();
        JMenu view = new JMenu("视图");
        addViewItem(view, "前视图", vp, 0, 0);
        addViewItem(view, "后视图", vp, 180, 0);
        addViewItem(view, "左视图", vp, -90, 0);
        addViewItem(view, "右视图", vp, 90, 0);
        addViewItem(view, "顶视图", vp, 0, 90);
        addViewItem(view, "底视图", vp, 0, -90);
        menu.add(view);

        JMenuItem reset = new JMenuItem("重置视角");
        reset.addActionListener(e -> {
            CameraHandler cam = cameraOf(vp);
            if (cam == null) return;
            cam.resetZoom();
            cam.setViewportCamera((int) (modelRadius(vp) / 3), (int) (modelRadius(vp) / 20),
                    0, 20, -25);
            repaintAll();
        });
        menu.add(reset);

        // 视口右键补建模相关操作：按当前选区/模型直接调用现成静态入口
        menu.addSeparator();
        JMenu addEffect = new JMenu("添加粒子特效");
        try {
            com.hiveworkshop.rms.ui.application.AddParticlePanel
                    .addParticleButtons(addEffect, defaultParent);
        } catch (Throwable t) {
            System.out.println("[Blender] 粒子预设菜单构建失败: " + t);
        }
        menu.add(addEffect);

        JMenuItem emptyRibbon = new JMenuItem("添加空丝带");
        emptyRibbon.addActionListener(e ->
                com.hiveworkshop.rms.ui.application.AddParticlePanel.addEmptyRibbon());
        menu.add(emptyRibbon);

        JMenuItem emptyPopcorn = new JMenuItem("添加空爆米花粒子");
        emptyPopcorn.addActionListener(e ->
                com.hiveworkshop.rms.ui.application.AddParticlePanel.addEmptyPopcorn());
        menu.add(emptyPopcorn);

        JMenuItem createFaceItem = new JMenuItem("由所选创建面 (Ctrl+Shift+F)");
        createFaceItem.addActionListener(e -> {
            ModelPanel mp = ProgramGlobals.getCurrentModelPanel();
            if (mp != null && mp.getModelHandler() != null) {
                com.hiveworkshop.rms.ui.application.actionfunctions.CreateFace
                        .createFace(mp.getModelHandler());
            }
        });
        menu.add(createFaceItem);

        JMenuItem weldItem = new JMenuItem("焊点");
        weldItem.addActionListener(e -> {
            ModelPanel mp = ProgramGlobals.getCurrentModelPanel();
            if (mp != null && mp.getModelHandler() != null) {
                com.hiveworkshop.rms.ui.application.actionfunctions.WeldVerts
                        .doWeld(mp.getModelHandler());
            }
        });
        menu.add(weldItem);

        JMenuItem flipNormalsItem = new JMenuItem("翻转法线");
        flipNormalsItem.addActionListener(e -> {
            ModelPanel mp = ProgramGlobals.getCurrentModelPanel();
            if (mp != null && mp.getModelHandler() != null) {
                com.hiveworkshop.rms.ui.application.actionfunctions.FlipNormals
                        .insideOutNormals(mp.getModelHandler());
            }
        });
        menu.add(flipNormalsItem);

        JMenuItem deleteSelected = new JMenuItem("删除所选 (Delete)");
        deleteSelected.addActionListener(e ->
                com.hiveworkshop.rms.ui.application.actionfunctions.Delete.deleteActionRes());
        menu.add(deleteSelected);

        menu.show(vp, x, y);
    }

    private static void addViewItem(JMenu menu, String name, final Component vp,
                                    final float ry, final float rx) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            CameraHandler cam = cameraOf(vp);
            if (cam != null) {
                cam.setCameraRotation(ry, rx);
                repaintAll();
            }
        });
        menu.add(item);
    }

    private static double[] selectionCenter(Component vp) {
        double[] b = selectionBounds(vp);
        return b == null ? null : new double[]{b[0], b[1], b[2]};
    }

    /** 选中元素包围盒：{cx, cy, cz, 半径}。 */
    private static double[] selectionBounds(Component vp) {
        try {
            Object mvObj = field(vp, "modelView");
            if (!(mvObj instanceof ModelView)) return null;
            ModelView mv = (ModelView) mvObj;
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            int n = 0;
            for (GeosetVertex v : mv.getSelectedVertices()) {
                n++;
                if (v.x < minX) minX = v.x;
                if (v.y < minY) minY = v.y;
                if (v.z < minZ) minZ = v.z;
                if (v.x > maxX) maxX = v.x;
                if (v.y > maxY) maxY = v.y;
                if (v.z > maxZ) maxZ = v.z;
            }
            for (IdObject o : mv.getSelectedIdObjects()) {
                Vec3 p = o.getPivotPoint();
                n++;
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                maxZ = Math.max(maxZ, p.z);
            }
            for (CameraNode c : mv.getSelectedCameraNodes()) {
                Vec3 p = c.getPosition();
                n++;
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                maxZ = Math.max(maxZ, p.z);
            }
            if (n == 0) return null;
            double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
            double r = Math.sqrt(sq(maxX - minX) + sq(maxY - minY) + sq(maxZ - minZ)) / 2;
            return new double[]{cx, cy, cz, Math.max(r, 1.0)};
        } catch (Throwable t) {
            return null;
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double modelRadius(Component vp) {
        try {
            Object mv = field(vp, "modelView");
            if (mv instanceof ModelView) {
                ExtLog ext = ((ModelView) mv).getModel().getExtents();
                if (ext != null) {
                    if (ext.hasBoundsRadius() && ext.getBoundsRadius() > 0)
                        return ext.getBoundsRadius();
                    Vec3 min = ext.getMinimumExtent();
                    Vec3 max = ext.getMaximumExtent();
                    if (min != null && max != null) {
                        return Math.sqrt(sq(max.x - min.x) + sq(max.y - min.y)
                                + sq(max.z - min.z)) / 2;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 100;
    }

    private static Vec3 modelCenter(Component vp) {
        try {
            Object mv = field(vp, "modelView");
            if (mv instanceof ModelView) {
                ExtLog ext = ((ModelView) mv).getModel().getExtents();
                if (ext != null && ext.getMinimumExtent() != null && ext.getMaximumExtent() != null) {
                    Vec3 min = ext.getMinimumExtent();
                    Vec3 max = ext.getMaximumExtent();
                    return new Vec3((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2);
                }
            }
        } catch (Throwable ignored) {
        }
        return new Vec3(0, 0, 0);
    }

    // ------------------------------------------------------------------ Blender 抓取（G/R/S）

    /** 当前抓取会话；null 表示空闲。仅 EDT 线程读写。 */
    static Grab GRAB;
    static HintWindow HINT;
    /** G/R/S 过程中输入的数字，用于精确变换。 */
    static final StringBuilder NUMERIC = new StringBuilder();
    /** 视口内最后一次鼠标位置，L 键拓扑选择用来定位起始元素。 */
    static volatile int LAST_MOUSE_X = -1;
    static volatile int LAST_MOUSE_Y = -1;
    /** 自测时固定使用某个视口（正常运行时为 null，走鼠标/焦点判定）。 */
    static volatile Component FORCE_VIEWPORT = null;

    interface Grab {
        Component viewport();

        void drive(int x, int y);

        void finish(boolean cancel);

        void setAxis(int bit, boolean exclude);

        int tool();

        /** Blender 风格数字输入：沿约束轴精确变换。 */
        void applyNumeric(double value);
    }

    static boolean startGrab(int tool) {
        return startGrab(tool, MoveDimension.XYZ);
    }

    static boolean startGrab(int tool, MoveDimension dim) {
        try {
            Component vp = grabViewport();
            if (vp == null) return false;
            CameraHandler camera = (CameraHandler) field(vp, "cameraHandler");
            ViewportActivityManager am = (ViewportActivityManager) field(camera, "activityManager");
            if (am == null) return false;
            ModelEditor modelEditor = (ModelEditor) field(am, "newModelEditor");
            UndoManager undo = undoManagerOf(vp);
            if (modelEditor == null || undo == null) return false;

            double[] b = selectionBounds(vp);
            if (b == null) {
                hint(vp, "先选中点 / 面 / 骨骼，再按 G / R / S / E / P");
                return false;
            }

            Point mp = mouseIn(vp);
            if (mp == null) mp = new Point(vp.getWidth() / 2, vp.getHeight() / 2);

            if (tool >= 3) {
                return startManipulatorGrab(vp, camera, modelEditor, undo, tool, dim, mp, b);
            }

            Vec3 geoStart = camera.getGeoPoint(mp.x, mp.y);
            Vec3 pivot = new Vec3(b[0], b[1], b[2]);
            AxisGrab session = new AxisGrab(vp, camera, modelEditor, undo, tool,
                    geoStart, pivot, mp.x, mp.y);
            session.begin();
            NUMERIC.setLength(0);
            GRAB = session;
            hint(vp, describe(tool, -1)
                    + " ｜ X/Y/Z 锁轴（Shift+X 排除轴） ｜ 左键/回车确认 · ESC/右键取消");
            System.out.println("[Blender] 抓取开始: " + describe(tool, -1));
            return true;
        } catch (Throwable t) {
            System.out.println("[Blender] startGrab 异常: " + t.getMessage());
            return false;
        }
    }

    private static boolean startManipulatorGrab(Component vp, CameraHandler camera,
                                                ModelEditor modelEditor, UndoManager undo,
                                                int tool, MoveDimension dim, Point mp, double[] b) {
        try {
            MouseEvent me = synth(vp, MouseEvent.MOUSE_MOVED, mp, 0);
            com.hiveworkshop.rms.util.Vec2 p = camera.getPoint_ifYZplane(mp.x, mp.y);
            Manipulator m;
            if (tool == 3) {
                m = new ExtrudeManipulator(modelEditor, dim);   // EXTRUDE = 分离面
            } else if (tool == 4) {
                m = new ExtendManipulator(modelEditor, dim);    // EXTEND = 真挤出
            } else {
                // 旧实现把所有 tool>=3 都当成 EXTEND，未知编号会误做挤出。
                hint(vp, "未知的建模工具编号：" + tool);
                return false;
            }
            m.start(me, p, camera);
            NUMERIC.setLength(0);
            GRAB = new ManipulatorGrab(vp, camera, m, undo, p, tool, dim);
            hint(vp, describe(tool, -1)
                    + " ｜ 移动鼠标拖动所选 ｜ X/Y/Z 锁轴 ｜ 左键或回车确认 · ESC/右键取消");
            System.out.println("[Blender] 抓取开始: " + describe(tool, -1));
            return true;
        } catch (Throwable t) {
            System.out.println("[Blender] 抓取启动失败：");
            t.printStackTrace();
            return false;
        }
    }

    private static String describe(int tool, int axisMask) {
        String t;
        switch (tool) {
            case 0: t = "移动"; break;
            case 1: t = "旋转"; break;
            case 2: t = "缩放"; break;
            case 3: t = "分离面"; break;
            case 4: t = "挤出"; break;
            default: t = "工具" + tool; break;
        }
        String d;
        switch (axisMask) {
            case 1: d = "沿 X 轴"; break;
            case 2: d = "沿 Y 轴"; break;
            case 4: d = "沿 Z 轴"; break;
            case 6: d = "排除 X 轴"; break;
            case 5: d = "排除 Y 轴"; break;
            case 3: d = "排除 Z 轴"; break;
            default: d = "自由"; break;
        }
        return t + "（" + d + "）";
    }

    private static Component grabViewport() {
        Component forced = FORCE_VIEWPORT;
        if (forced != null && forced.isShowing()) return forced;
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi != null) {
            Point p = pi.getLocation();
            for (Component vp : INSTALLED) {
                if (!vp.isShowing()) continue;
                Point loc = vp.getLocationOnScreen();
                if (p.x >= loc.x && p.y >= loc.y
                        && p.x < loc.x + vp.getWidth() && p.y < loc.y + vp.getHeight()) {
                    return vp;
                }
            }
        }
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus != null) {
            for (Component vp : INSTALLED) {
                if (vp.isShowing() && SwingUtilities.isDescendingFrom(focus, vp)) {
                    return vp;
                }
            }
        }
        for (Component vp : INSTALLED) {
            if (vp.isShowing()) return vp;
        }
        return null;
    }

    private static Component activeViewport() {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus != null) {
            for (Component vp : INSTALLED) {
                if (vp.isShowing() && SwingUtilities.isDescendingFrom(focus, vp)) return vp;
            }
        }
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi != null) {
            Point p = pi.getLocation();
            for (Component vp : INSTALLED) {
                if (!vp.isShowing()) continue;
                Point loc = vp.getLocationOnScreen();
                if (p.x >= loc.x && p.y >= loc.y
                        && p.x < loc.x + vp.getWidth() && p.y < loc.y + vp.getHeight()) {
                    return vp;
                }
            }
        }
        for (Component vp : INSTALLED) {
            if (vp.isShowing()) return vp;
        }
        return null;
    }

    private static CameraHandler activeCamera() {
        Component vp = activeViewport();
        return vp == null ? null : cameraOf(vp);
    }

    private static CameraHandler cameraOf(Component vp) {
        try {
            return (CameraHandler) field(vp, "cameraHandler");
        } catch (Throwable t) {
            return null;
        }
    }

    private static UndoManager undoManagerOf(Component vp) {
        try {
            CameraHandler cam = cameraOf(vp);
            if (cam == null) return null;
            Object am = field(cam, "activityManager");
            if (am == null) return null;
            Object activity = field(am, "currentActivity");
            return activity == null ? null : (UndoManager) field(activity, "undoManager");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Point mouseIn(Component vp) {
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return null;
        Point p = pi.getLocation();
        Point loc = vp.getLocationOnScreen();
        return new Point(p.x - loc.x, p.y - loc.y);
    }

    private static MouseEvent synth(Component vp, int id, Point pt, int mods) {
        return new MouseEvent(vp, id, System.currentTimeMillis(), mods, pt.x, pt.y, 0, false);
    }

    private static void hint(Component vp, String text) {
        try {
            if (HINT == null) HINT = new HintWindow();
            HINT.show(vp, text);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 直接基于 ModelEditor 的 G/R/S 会话：
     * 位移/旋转/缩放都是「相对起始状态」的绝对量，锁轴时只需要改约束掩码，
     * 不会像旧实现那样先撤销再重新开始（旧实现会丢失当前拖动量）。
     */
    static final class AxisGrab implements Grab {
        final Component viewport;
        final CameraHandler camera;
        final ModelEditor editor;
        final UndoManager undo;
        final int tool;
        final Vec3 geoStart;
        final Vec3 pivot;
        int startX, startY;
        int lastX, lastY;
        int axisMask = 7;           // bit0=X bit1=Y bit2=Z；7 = 自由
        GenericMoveAction move;
        GenericRotateAction rot;
        GenericScaleAction scale;
        Vec3 appliedMove = new Vec3();
        double appliedAngle = 0;
        double appliedScale = 1;
        // 约束旋转的屏幕方向（每次 drive 用固定方向做弧长换算）
        double dirX, dirY;
        double radiusPerp = 1;
        double pxPerWorld = 1;
        double moveScale = 1;

        AxisGrab(Component viewport, CameraHandler camera, ModelEditor editor, UndoManager undo,
                 int tool, Vec3 geoStart, Vec3 pivot, int x, int y) {
            this.viewport = viewport;
            this.camera = camera;
            this.editor = editor;
            this.undo = undo;
            this.tool = tool;
            this.geoStart = geoStart;
            this.pivot = pivot;
            this.startX = x;
            this.startY = y;
            this.lastX = x;
            this.lastY = y;
        }

        @Override
        public Component viewport() {
            return viewport;
        }

        @Override
        public int tool() {
            return tool;
        }

        void begin() {
            prepareAxisGeometry();
            createAction();
        }

        private void createAction() {
            if (tool == 0) {
                move = editor.beginTranslation();
            } else if (tool == 1) {
                rot = editor.beginRotation(new Vec3(pivot), axisVector());
            } else {
                scale = editor.beginScaling(new Vec3(pivot));
            }
        }

        private Vec3 axisVector() {
            if ((axisMask & 1) != 0 && (axisMask & 6) == 0) return new Vec3(1, 0, 0);
            if ((axisMask & 2) != 0 && (axisMask & 5) == 0) return new Vec3(0, 1, 0);
            if ((axisMask & 4) != 0 && (axisMask & 3) == 0) return new Vec3(0, 0, 1);
            return viewAxis();   // 自由旋转 = 绕视线轴
        }

        /** 视线轴在模型空间的方向：R^-1 * (1,0,0)。 */
        private Vec3 viewAxis() {
            double y = Math.toRadians(camera.getYAngle());
            double z = Math.toRadians(camera.getZAngle());
            return new Vec3(Math.cos(y) * Math.cos(z),
                    -Math.cos(y) * Math.sin(z), Math.sin(y));
        }

        private void prepareAxisGeometry() {
            // getGeoPoint 位于相机平面；先移到 pivot 深度，避免把深度差误算成旋转半径。
            Vec3 pivotCam = rotateCam(camera, pivot);
            double camX = Math.max(1e-6, camera.getCameraPos().x);
            double depth = Math.max(1e-6, camX - camera.getZoom() * pivotCam.x);
            pxPerWorld = 600.0 * camera.getZoom() / depth;
            moveScale = Math.max(0.05, depth / camX);
            Vec3 startAtDepth = Vec3.getSum(geoStart,
                    Vec3.getScaled(viewAxis(), (float) pivotCam.x));
            Vec3 r = Vec3.getDiff(startAtDepth, pivot);
            Vec3 a = axisVector();
            double along = r.dot(a);
            Vec3 perp = Vec3.getDiff(r, Vec3.getScaled(a, (float) along));
            // 鼠标从 pivot 中心起步时使用稳定的最小半径，避免无穷大角速度。
            radiusPerp = Math.max(80.0 / Math.max(1e-6, pxPerWorld), perp.length());
            Vec3 tangent = Vec3.getCross(a, r);
            Vec3 plane = rotateCam(camera, tangent);
            double sx = plane.y;
            double sy = -plane.z;
            double len = Math.sqrt(sx * sx + sy * sy);
            if (len < 1e-6) {
                dirX = 1;
                dirY = 0;
            } else {
                dirX = sx / len;
                dirY = sy / len;
            }
        }

        @Override
        public void drive(int x, int y) {
            try {
                lastX = x;
                lastY = y;
                Vec3 geoNow = camera.getGeoPoint(x, y);
                if (tool == 0) {
                    Vec3 total = Vec3.getDiff(geoNow, geoStart);
                    total.scale((float) moveScale);
                    applyMoveMask(total);
                    Vec3 delta = Vec3.getDiff(total, appliedMove);
                    if (delta.lengthSquared() > 1e-12) {
                        move.updateTranslation(delta);
                        appliedMove = total;
                        repaintAll();
                    }
                } else if (tool == 1) {
                    double target = rotationAngle(x, y);
                    double delta = target - appliedAngle;
                    while (delta > Math.PI) delta -= Math.PI * 2;
                    while (delta < -Math.PI) delta += Math.PI * 2;
                    if (Math.abs(delta) > 1e-7) {
                        rot.updateRotation(delta);
                        appliedAngle = target;
                        repaintAll();
                    }
                } else {
                    double factor = scaleFactor(x, y);
                    if (factor < 1e-3) factor = 1e-3;
                    Vec3 inc = new Vec3(
                            (axisMask & 1) != 0 ? factor / appliedScale : 1,
                            (axisMask & 2) != 0 ? factor / appliedScale : 1,
                            (axisMask & 4) != 0 ? factor / appliedScale : 1);
                    if (inc.x != 1 || inc.y != 1 || inc.z != 1) {
                        scale.updateScale(inc);
                        appliedScale = factor;
                        repaintAll();
                    }
                }
            } catch (Throwable t) {
                System.out.println("[Blender] 抓取更新失败：");
                t.printStackTrace();
            }
        }

        @Override
        public void applyNumeric(double value) {
            try {
                if (tool == 0) {
                    // 数字移动必须遵守 axisMask。旧实现用 singleAxis()，
                    // 在「排除轴」模式（Shift+X -> mask 6）下返回 -1，退化成
                    // 只沿 X 移动，直接违反用户设定的约束。
                    Vec3 target;
                    if (axisMask == 7) {
                        target = Vec3.getScaled(new Vec3(1, 0, 0), (float) value);
                    } else if (axisMask == 1 || axisMask == 2 || axisMask == 4) {
                        Vec3 axis = axisMask == 1 ? new Vec3(1, 0, 0)
                                : axisMask == 2 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
                        target = Vec3.getScaled(axis, (float) value);
                    } else {
                        float f = (float) value;
                        target = new Vec3(
                                (axisMask & 1) != 0 ? f : 0f,
                                (axisMask & 2) != 0 ? f : 0f,
                                (axisMask & 4) != 0 ? f : 0f);
                    }
                    Vec3 delta = Vec3.getDiff(target, appliedMove);
                    if (delta.lengthSquared() > 1e-12) {
                        move.updateTranslation(delta);
                        appliedMove = target;
                    }
                } else if (tool == 1) {
                    double target = Math.toRadians(value);
                    double delta = target - appliedAngle;
                    while (delta > Math.PI) delta -= Math.PI * 2;
                    while (delta < -Math.PI) delta += Math.PI * 2;
                    if (Math.abs(delta) > 1e-9) {
                        rot.updateRotation(delta);
                        appliedAngle = target;
                    }
                } else {
                    double factor = Math.max(1e-3, value);
                    Vec3 inc = new Vec3(
                            (axisMask & 1) != 0 ? factor / appliedScale : 1,
                            (axisMask & 2) != 0 ? factor / appliedScale : 1,
                            (axisMask & 4) != 0 ? factor / appliedScale : 1);
                    if (inc.x != 1 || inc.y != 1 || inc.z != 1) {
                        scale.updateScale(inc);
                        appliedScale = factor;
                    }
                }
                repaintAll();
            } catch (Throwable t) {
                System.out.println("[Blender] 数字输入应用失败: " + t);
            }
        }

        private void applyMoveMask(Vec3 total) {
            if ((axisMask & 1) == 0) total.x = 0;
            if ((axisMask & 2) == 0) total.y = 0;
            if ((axisMask & 4) == 0) total.z = 0;
        }

        /** 自由旋转 = 屏幕平面角度；单轴旋转 = 沿该轴切向的鼠标位移 / 半径。 */
        private double rotationAngle(int x, int y) {
            if (singleAxis() < 0) {
                Vec3 s = rotateCam(camera, Vec3.getDiff(geoStart, pivot));
                Vec3 n = rotateCam(camera, Vec3.getDiff(camera.getGeoPoint(x, y), pivot));
                double a0 = Math.atan2(s.z, s.y);
                double a1 = Math.atan2(n.z, n.y);
                double d = a1 - a0;
                while (d > Math.PI) d -= Math.PI * 2;
                while (d < -Math.PI) d += Math.PI * 2;
                return d;
            }
            double px = (x - startX) * dirX + (y - startY) * dirY;
            return px / Math.max(1e-6, pxPerWorld * radiusPerp);
        }

        private double scaleFactor(int x, int y) {
            if (singleAxis() < 0) {
                Vec3 s = rotateCam(camera, Vec3.getDiff(geoStart, pivot));
                Vec3 n = rotateCam(camera, Vec3.getDiff(camera.getGeoPoint(x, y), pivot));
                double d0 = Math.sqrt(s.y * s.y + s.z * s.z);
                double d1 = Math.sqrt(n.y * n.y + n.z * n.z);
                if (d0 < 1e-4) return 1;
                return d1 / d0;
            }
            Vec3 a = axisVector();
            Vec3 plane = rotateCam(camera, a);
            double sx = plane.y;
            double sy = -plane.z;
            double len = Math.sqrt(sx * sx + sy * sy);
            if (len < 1e-5) return 1;
            double px = ((x - startX) * sx / len + (y - startY) * sy / len);
            return 1 + px / Math.max(1e-6, pxPerWorld * Math.max(1.0, radiusPerp));
        }

        private int singleAxis() {
            if (axisMask == 1) return 0;
            if (axisMask == 2) return 1;
            if (axisMask == 4) return 2;
            return -1;
        }

        @Override
        public void setAxis(int bit, boolean exclude) {
            int next = exclude ? (7 & ~bit) : bit;
            if (next == axisMask) next = 7;    // 再按同轴 = 解除约束
            if (next == axisMask) return;
            axisMask = next;
            System.out.println("[Blender] 轴约束 -> " + describe(tool, axisMask));
            hint(viewport, describe(tool, axisMask)
                    + " ｜ X/Y/Z 锁轴（Shift+X 排除轴） ｜ 左键/回车确认 · ESC/右键取消");
            if (tool == 1) {
                // 旋转轴变了：撤销当前旋转，用新轴重建后立刻重放
                try {
                    if (rot != null) rot.undo();
                } catch (Throwable ignored) {
                }
                rot = editor.beginRotation(new Vec3(pivot), axisVector());
                appliedAngle = 0;
                prepareAxisGeometry();
            }
            drive(lastX, lastY);
        }

        @Override
        public void finish(boolean cancel) {
            try {
                UndoAction a = tool == 0 ? move : (tool == 1 ? rot : scale);
                if (a != null) {
                    if (cancel) {
                        a.undo();
                    } else {
                        undo.pushAction(a);
                    }
                }
                System.out.println("[Blender] 抓取结束: " + (cancel ? "已取消" : "已确认")
                        + " " + describe(tool, axisMask));
            } catch (Throwable t) {
                System.out.println("[Blender] 抓取结束异常：");
                t.printStackTrace();
            } finally {
                GRAB = null;
                if (HINT != null) HINT.hide();
                repaintAll();
            }
        }
    }

    /** 挤出/分离面沿用原版 Manipulator 体系。 */
    static final class ManipulatorGrab implements Grab {
        final Component viewport;
        final CameraHandler camera;
        final UndoManager undo;
        final int tool;
        Manipulator manipulator;
        MoveDimension dim;
        com.hiveworkshop.rms.util.Vec2 lastDragPoint;

        ManipulatorGrab(Component viewport, CameraHandler camera, Manipulator manipulator,
                        UndoManager undo, com.hiveworkshop.rms.util.Vec2 startPoint,
                        int tool, MoveDimension dim) {
            this.viewport = viewport;
            this.camera = camera;
            this.manipulator = manipulator;
            this.undo = undo;
            this.lastDragPoint = startPoint;
            this.tool = tool;
            this.dim = dim;
        }

        @Override
        public Component viewport() {
            return viewport;
        }

        @Override
        public int tool() {
            return tool;
        }

        @Override
        public void drive(int x, int y) {
            try {
                com.hiveworkshop.rms.util.Vec2 np = camera.getPoint_ifYZplane(x, y);
                if (np == null || lastDragPoint == null) return;
                if (Math.abs(np.x - lastDragPoint.x) < 1e-9
                        && Math.abs(np.y - lastDragPoint.y) < 1e-9) return;
                Point mp = new Point(x, y);
                MouseEvent me = synth(viewport, MouseEvent.MOUSE_DRAGGED, mp, 0);
                manipulator.update(me, lastDragPoint, np, camera);
                lastDragPoint = np;
                repaintAll();
            } catch (Throwable t) {
                System.out.println("[Blender] 抓取更新失败：");
                t.printStackTrace();
            }
        }

        @Override
        public void applyNumeric(double value) {
            // 挤出/分离使用原版 Manipulator，数字输入暂不参与。
        }

        @Override
        public void setAxis(int bit, boolean exclude) {
            // 原版 Manipulator 的 MoveDimension 是构造期参数，运行中无法切换。
            // 旧实现先 finish(true) 撤销、再 startGrab 重开，会把已经拖出的几何
            // 直接丢掉；Shift+轴的 exclude 语义也根本无法表达。
            // 这里改为明确禁用并保留当前会话，不破坏用户已完成的拖动。
            hint(viewport, describe(tool, -1)
                    + " 不支持 X/Y/Z 锁轴（如需锁轴请先确认，再按 G 移动）"
                    + " ｜ 左键确认 · ESC 取消");
        }

        @Override
        public void finish(boolean cancel) {
            try {
                Point mp = mouseIn(viewport);
                if (mp == null) mp = new Point(viewport.getWidth() / 2, viewport.getHeight() / 2);
                MouseEvent me = synth(viewport, MouseEvent.MOUSE_RELEASED, mp, 0);
                com.hiveworkshop.rms.util.Vec2 end = camera.getPoint_ifYZplane(mp.x, mp.y);
                UndoAction a = manipulator.finish(me, lastDragPoint, end, camera);
                if (a != null) {
                    if (cancel) {
                        a.undo();
                    } else {
                        undo.pushAction(a);
                    }
                }
                System.out.println("[Blender] 抓取结束: " + (cancel ? "已取消" : "已确认"));
            } catch (Throwable t) {
                System.out.println("[Blender] 抓取结束异常：");
                t.printStackTrace();
            } finally {
                GRAB = null;
                if (HINT != null) HINT.hide();
                repaintAll();
            }
        }
    }

    private static final class HintWindow {
        final JWindow window = new JWindow();
        final JLabel label = new JLabel(" ");

        HintWindow() {
            window.setAlwaysOnTop(true);
            window.setFocusableWindowState(false);
            window.setType(Window.Type.UTILITY);
            label.setOpaque(true);
            label.setBackground(new java.awt.Color(35, 35, 38));
            label.setForeground(new java.awt.Color(235, 235, 235));
            label.setFont(label.getFont().deriveFont(14f));
            label.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12));
            // 用 Graphics2D 包装以改进气泡文字在非整数缩放下的渲染
            window.setContentPane(new javax.swing.JPanel() {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    try {
                        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS,
                                java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        super.paintComponent(g2);
                    } finally {
                        g2.dispose();
                    }
                }
            });
            window.add(label);
        }

        void show(Component anchor, String text) {
            label.setText(text);
            window.pack();
            try {
                Point p = anchor.getLocationOnScreen();
                window.setLocation(p.x + 14, p.y + 30);
            } catch (Throwable ignored) {
            }
            window.setVisible(true);
        }

        void hide() {
            window.setVisible(false);
        }
    }

    // ------------------------------------------------------------------ 面点击修正

    static void dedupeFaceClick(Component vp, CameraHandler camera, MouseEvent e,
                                int px, int py) {
        try {
            int mods = e.getModifiersEx();
            if ((mods & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
                    | InputEvent.ALT_DOWN_MASK)) != 0) {
                return;
            }
            if (Math.abs(e.getX() - px) > 3 || Math.abs(e.getY() - py) > 3) {
                return;
            }
            if (ProgramGlobals.getSelectionItemType() != SelectionItemTypes.FACE) {
                return;
            }
            Object wire = field(vp, "wireFrame");
            if (Boolean.TRUE.equals(wire)) {
                return;
            }
            ModelView mv = (ModelView) field(vp, "modelView");
            if (mv == null) return;
            Set<GeosetVertex> sel = mv.getSelectedVertices();
            if (sel == null || sel.isEmpty()) return;

            // 第十五轮改进：判据从「质心到相机的欧氏距离」改为「沿视线的相机空间深度」。
            // 旧算法两个缺陷：
            //  (1) 欧氏距离在斜对角方向会把更远的误判成更近（视线深度才是前后关系）；
            //  (2) eps = best*0.02 随绝对距离放大，大模型会把明显靠后的另一层面也留下。
            // rotateCam() 与渲染管线 glRotatef(x/y/z) 同序，返回的 x 分量越大表示越靠近相机
            // （+X 为 forward，见 applyCamera 注释），因此用它做前后深度判据。
            List<Triangle> tris = new ArrayList<>();
            List<Double> depths = new ArrayList<>();
            for (Geoset g : mv.getEditableGeosets()) {
                for (Triangle t : g.getTriangles()) {
                    GeosetVertex[] v = t.getAll();
                    if (sel.contains(v[0]) && sel.contains(v[1]) && sel.contains(v[2])) {
                        Vec3 c = new Vec3((v[0].x + v[1].x + v[2].x) / 3f,
                                (v[0].y + v[1].y + v[2].y) / 3f,
                                (v[0].z + v[1].z + v[2].z) / 3f);
                        // 视线深度：相机空间 x 越大越靠近相机
                        double depth = rotateCam(camera, c).x;
                        tris.add(t);
                        depths.add(depth);
                    }
                }
            }
            if (tris.isEmpty()) return;

            // 按深度降序（近 -> 远）排列，求相邻层间隙。
            // 关键判据（经独立单元测试 9 类场景验证）：真实「前后层断层」的特征是
            // 某一个间隙【显著大于其它间隙】（突变），而共面 / 连续曲面是均匀小间隙。
            // 因此用「最大间隙 > 2×次大间隙」+ 绝对下限来判定，避免把曲面误切。
            final List<Integer> order = new ArrayList<>();
            for (int i = 0; i < tris.size(); i++) order.add(i);
            Collections.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Double.compare(depths.get(b), depths.get(a));
                }
            });
            if (order.size() < 2) return; // 单个三角形无需修正

            double minDepth = depths.get(order.get(order.size() - 1));
            double maxDepth = depths.get(order.get(0));
            double span = Math.max(0.0, maxDepth - minDepth);

            List<Double> gaps = new ArrayList<>();
            for (int i = 1; i < order.size(); i++) {
                gaps.add(depths.get(order.get(i - 1)) - depths.get(order.get(i)));
            }
            double maxGap = Collections.max(gaps);
            int maxIdx = gaps.indexOf(maxGap);
            double secondMax = 0.0;
            for (int i = 0; i < gaps.size(); i++) {
                if (i != maxIdx) secondMax = Math.max(secondMax, gaps.get(i));
            }
            double absFloor = Math.max(span * 0.15, 1e-3);
            boolean ratioOK = (gaps.size() == 1) || (maxGap > 2.0 * secondMax + 1e-9);
            boolean doSplit = (maxGap >= absFloor) && ratioOK;

            int keepCount = doSplit ? (maxIdx + 1) : order.size();
            Set<GeosetVertex> keep = new HashSet<>();
            for (int i = 0; i < keepCount; i++) {
                for (GeosetVertex gv : tris.get(order.get(i)).getAll()) {
                    keep.add(gv);
                }
            }
            if (keep.size() < sel.size()) {
                mv.setSelectedVertices(keep);
                repaintAll();
                System.out.println("[Blender] 面点击修正: 命中 " + tris.size()
                        + " 个三角形，深度跨度 " + (float) span
                        + "，最大/次大层间隙 " + (float) maxGap + "/" + (float) secondMax
                        + " -> 保留最近 " + keepCount + " 个（"
                        + (doSplit ? "已切分前后层" : "同层全留") + "）");
            }
        } catch (Throwable t) {
            System.out.println("[Blender] 面点击修正失败：");
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ 键盘

    private static final class BlenderKeys implements KeyEventDispatcher {
        private static boolean staticsBroken = false;

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            Component src = e.getComponent();
            boolean typing = src instanceof JTextComponent || src instanceof JSpinner
                    || src instanceof JComboBox
                    || (src instanceof JTable && ((JTable) src).isEditing());

            // 抓取会话进行中时，ESC / 回车 / 锁轴必须优先处理；否则焦点一旦落到
            // 文本框或下拉框，会话将永远无法退出（旧实现在这里直接 return false）。
            if (GRAB != null) {
                if (handleGrabKeys(e)) {
                    repaintAll();
                    e.consume();
                    return true;
                }
                if (typing) return false;
            }
            if (typing) return false;

            // 节点树里按 Delete：按【树选中行】删除。全局 Delete 动作依赖 3D 视口选区，
            // 而树的 TreeSelection 并不写入 ModelView 选区，所以在这里单独处理。
            // 焦点在树上时无论节点是否可删都吞掉事件，避免落到全局 Delete 误删视口选区。
            if (GRAB == null && e.getKeyCode() == KeyEvent.VK_DELETE
                    && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown() && !e.isShiftDown()) {
                JTree tree = treeOf(src);
                if (tree != null) {
                    TreePath path = tree.getSelectionPath();
                    Object item = path == null ? null : itemOf(path.getLastPathComponent());
                    deleteTreeItem(tree, item);
                    e.consume();
                    return true;
                }
            }

            // CTRL+F 已让给「模型 / 大纲」页的树搜索框；创建面改键为 CTRL+SHIFT+F，
            // ViewportPopupMenu 的 accelerator 与建模页按钮文案同步修改。
            if (GRAB == null && e.getKeyCode() == KeyEvent.VK_F && e.isControlDown()
                    && e.isShiftDown() && !e.isAltDown() && !e.isMetaDown()) {
                ModelPanel panel = ProgramGlobals.getCurrentModelPanel();
                if (panel != null) {
                    com.hiveworkshop.rms.ui.application.actionfunctions.CreateFace
                            .createFace(panel.getModelHandler());
                    e.consume();
                    return true;
                }
            }
            if ((e.getModifiersEx() & (ALT | InputEvent.CTRL_DOWN_MASK
                    | InputEvent.META_DOWN_MASK)) != 0) {
                return false;
            }
            boolean modeResult = handleModeKeys(e);
            if (modeResult) {
                repaintAll();
                e.consume();
                return true;
            }
            // Fallback: handleModeKeys 可能被中间某个检查短路了，直接尝试 startGrab。
            // 映射统一走 grabToolForKey，杜绝 E/P 再次写反。
            int tool = grabToolForKey(e.getKeyCode());
            if (GRAB == null && tool >= 0 && startGrab(tool)) {
                repaintAll();
                e.consume();
                return true;
            }
            if (e.getKeyCode() == KeyEvent.VK_PERIOD || e.getKeyCode() == KeyEvent.VK_DECIMAL) {
                frameSelected(activeViewport());
                e.consume();
                return true;
            }
            if (!isNumpad(e.getKeyCode())) return false;
            CameraHandler camera = activeCamera();
            if (camera == null) return false;

            int k = e.getKeyCode();
            boolean ctrl = e.isControlDown();
            int side = ctrl ? -1 : 1;
            switch (k) {
                case KeyEvent.VK_NUMPAD1:
                    camera.setCameraRotation(90 - side * 90, 0); break;
                case KeyEvent.VK_NUMPAD3:
                    camera.setCameraRotation(side * 90, 0); break;
                case KeyEvent.VK_NUMPAD7:
                    camera.setCameraRotation(0, side * 90); break;
                case KeyEvent.VK_NUMPAD4:
                    if (ctrl) camera.translate(10, 0); else camera.rotate(-15, 0);
                    break;
                case KeyEvent.VK_NUMPAD6:
                    if (ctrl) camera.translate(-10, 0); else camera.rotate(15, 0);
                    break;
                case KeyEvent.VK_NUMPAD8:
                    if (ctrl) camera.translate(0, 10); else camera.rotate(0, 15);
                    break;
                case KeyEvent.VK_NUMPAD2:
                    if (ctrl) camera.translate(0, -10); else camera.rotate(0, -15);
                    break;
                case KeyEvent.VK_NUMPAD5:
                    camera.toggleOrtho(); break;
                case KeyEvent.VK_NUMPAD9:
                    camera.rotate(180, 0); break;
                case KeyEvent.VK_NUMPAD0:
                    camera.resetZoom();
                    camera.setPosition(0, 0);
                    break;
                default:
                    return false;
            }
            repaintAll();
            e.consume();
            return true;
        }

        private static boolean isNumpad(int k) {
            return (k >= KeyEvent.VK_NUMPAD0 && k <= KeyEvent.VK_NUMPAD9);
        }

        private static boolean handleGrabKeys(KeyEvent e) {
            int k = e.getKeyCode();
            if (k == KeyEvent.VK_ESCAPE) {
                NUMERIC.setLength(0);
                GRAB.finish(true);
                return true;
            }
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
                if (NUMERIC.length() > 0) {
                    try {
                        GRAB.applyNumeric(Double.parseDouble(NUMERIC.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                    NUMERIC.setLength(0);
                }
                GRAB.finish(false);
                return true;
            }
            int bit = k == KeyEvent.VK_X ? 1 : (k == KeyEvent.VK_Y ? 2 : (k == KeyEvent.VK_Z ? 4 : 0));
            if (bit != 0) {
                GRAB.setAxis(bit, e.isShiftDown());
                return true;
            }
            // 数字精确输入：输入数值后回车确认。
            // 挤出 / 分离面（tool 3/4）沿用原版 Manipulator，applyNumeric 为空实现；
            // 旧实现仍吞掉数字并提示「缩放 N 倍」，既误导又无效果，这里明确告知并忽略。
            boolean numericSupported = GRAB.tool() <= 2;
            char ch = e.getKeyChar();
            if ((ch >= '0' && ch <= '9') || ch == '.' || ch == '-') {
                if (!numericSupported) {
                    hint(GRAB.viewport(), describe(GRAB.tool(), -1)
                            + " 不支持数字精确输入 ｜ 移动鼠标拖动 · 左键确认 · ESC 取消");
                    return true;
                }
                NUMERIC.append(ch);
                showNumericHint();
                return true;
            }
            if (k == KeyEvent.VK_BACK_SPACE && NUMERIC.length() > 0) {
                NUMERIC.deleteCharAt(NUMERIC.length() - 1);
                showNumericHint();
                return true;
            }
            return false;
        }

        private static void showNumericHint() {
            Component vp = GRAB == null ? null : GRAB.viewport();
            if (vp == null) return;
            int tool = GRAB.tool();
            if (tool > 2) return;
            String unit = tool == 0 ? "单位" : (tool == 1 ? "度" : "倍");
            hint(vp, (tool == 0 ? "移动 " : tool == 1 ? "旋转 " : "缩放 ")
                    + NUMERIC + " " + unit + " ｜ 回车确认 · ESC 取消 · 退格删除");
        }

        /**
         * Blender 编辑模式的 L 键：指向网格元素按下 L，选中全部拓扑相连的同类子元素。
         * Shift+L 在现有选择上叠加连通块。
         */
        private static boolean topologicalSelect(boolean additive) {
            return topologicalSelect(additive, null);
        }

        /** forcedSeeds 仅供自测使用；为 null 时按鼠标位置或当前选择取种子。 */
        private static boolean topologicalSelect(boolean additive, java.util.Collection<GeosetVertex> forcedSeeds) {
            try {
                Component vp = grabViewport();
                if (vp == null) return false;
                ModelView mv = (ModelView) field(vp, "modelView");
                if (mv == null) return false;
                if (ProgramGlobals.getSelectionItemType() != SelectionItemTypes.VERTEX) {
                    hint(vp, "L 拓扑选择需要顶点模式（按 1 切换）");
                    return false;
                }
                CameraHandler camera = (CameraHandler) field(vp, "cameraHandler");

                Set<GeosetVertex> seeds = new HashSet<>();
                if (forcedSeeds != null) {
                    seeds.addAll(forcedSeeds);
                } else {
                    int mx = LAST_MOUSE_X >= 0 ? LAST_MOUSE_X : vp.getWidth() / 2;
                    int my = LAST_MOUSE_Y >= 0 ? LAST_MOUSE_Y : vp.getHeight() / 2;
                    // 用相机平面上的世界距离找最近顶点，避免深度投影误差
                    Vec3 mouseGeo = camera.getGeoPoint(mx, my);
                    Vec3 mouseCam = rotateCam(camera, mouseGeo);
                    double maxDist = Math.max(1e-3, camera.geomDist(14));
                    double best = maxDist * maxDist;
                    GeosetVertex seed = null;
                    for (Geoset g : mv.getModel().getGeosets()) {
                        for (GeosetVertex v : g.getVertices()) {
                            Vec3 vc = rotateCam(camera, v);
                            double dy = vc.y - mouseCam.y;
                            double dz = vc.z - mouseCam.z;
                            double d = dy * dy + dz * dz;
                            if (d < best) {
                                best = d;
                                seed = v;
                            }
                        }
                    }
                    if (seed != null) {
                        seeds.add(seed);
                    } else {
                        // 鼠标附近没有顶点时，退回以当前已选顶点为种子
                        seeds.addAll(mv.getSelectedVertices());
                    }
                }
                if (seeds.isEmpty()) {
                    hint(vp, "把鼠标指向网格顶点再按 L");
                    return false;
                }

                Set<GeosetVertex> connected = new HashSet<>(seeds);
                for (Geoset g : mv.getModel().getGeosets()) {
                    java.util.ArrayDeque<GeosetVertex> queue = new java.util.ArrayDeque<>();
                    for (GeosetVertex v : g.getVertices()) {
                        if (connected.contains(v)) queue.add(v);
                    }
                    if (queue.isEmpty()) continue;
                    List<Triangle> triangles = g.getTriangles();
                    while (!queue.isEmpty()) {
                        GeosetVertex cur = queue.poll();
                        for (Triangle t : triangles) {
                            if (!t.containsRef(cur)) continue;
                            for (int i = 0; i < 3; i++) {
                                GeosetVertex n = t.get(i);
                                if (n != null && connected.add(n)) {
                                    queue.add(n);
                                }
                            }
                        }
                    }
                }

                Set<GeosetVertex> result = new HashSet<>(connected);
                if (additive) {
                    result.addAll(mv.getSelectedVertices());
                }
                new SetSelectionUggAction(new SelectionBundle(result), mv,
                        ModelStructureChangeListener.changeListener).redo();
                hint(vp, "L 拓扑选择：" + connected.size() + " 个顶点"
                        + (additive ? "（叠加）" : ""));
                return true;
            } catch (Throwable t) {
                System.out.println("[Blender] L 拓扑选择失败: " + t);
                return false;
            }
        }

        /**
         * 快捷键 -> 抓取 tool 编号的唯一映射（同时用于工具栏 actionType 序号）。
         * G=移动(0) R=旋转(1) S=缩放(2) P=分离面/EXTRUDE(3) E=挤出/EXTEND(4)。
         * 注意 E 与 P 的语义曾长期搞反：EXTRUDE 是「分离面」，EXTEND 才是真「挤出」。
         * 所有按键分支都必须走这里，避免各处硬编码再次写反。
         */
        private static int grabToolForKey(int k) {
            switch (k) {
                case KeyEvent.VK_G: return 0;
                case KeyEvent.VK_R: return 1;
                case KeyEvent.VK_S: return 2;
                case KeyEvent.VK_P: return 3;
                case KeyEvent.VK_E: return 4;
                default: return -1;
            }
        }

        private static boolean handleModeKeys(KeyEvent e) {
            int k = e.getKeyCode();
            int tool = grabToolForKey(k);
            try {
                if (k == KeyEvent.VK_A && e.isShiftDown()) {
                    return showAddMenu();
                }
                if (tool >= 0) {
                    return syncActionType(e, tool);
                }
                if (k == KeyEvent.VK_L) {
                    return topologicalSelect(e.isShiftDown());
                }
                if (k == KeyEvent.VK_1 || k == KeyEvent.VK_3 || k == KeyEvent.VK_4) {
                    int ordinal = k == KeyEvent.VK_1 ? 0 : (k == KeyEvent.VK_3 ? 1 : 2);
                    invokeEnumButtonSafe(
                            "com.hiveworkshop.rms.ui.gui.modeledit.selection.SelectionItemTypes",
                            "setSelectionTypeButton", ordinal);
                    return true;
                }
            } catch (Throwable t) {
                System.out.println("[Blender] 模式键 " + e.getKeyChar()
                        + " 处理异常，尝试独立 Grab: " + t);
                // 即使工具栏按钮更新失败，也要尝试建立 Grab 会话（含 E/P）
                if (tool >= 0 && GRAB == null) {
                    return startGrab(tool);
                }
            }
            return false;
        }

        private static boolean showAddMenu() {
            for (Window window : Window.getWindows()) {
                if (!(window instanceof javax.swing.JFrame)) continue;
                javax.swing.JMenuBar bar = ((javax.swing.JFrame) window).getJMenuBar();
                if (bar == null) continue;
                for (int i = 0; i < bar.getMenuCount(); i++) {
                    JMenu menu = bar.getMenu(i);
                    if (menu != null && "添加".equals(menu.getText())) {
                        menu.doClick(0);
                        return true;
                    }
                }
            }
            return false;
        }

        /** 安全版本：工具栏按钮更新失败不阻断 Grab 链路。 */
        private static boolean invokeEnumButtonSafe(String enumClass, String method, int ordinal) {
            try {
                return invokeEnumButton(enumClass, method, ordinal);
            } catch (Throwable t) {
                System.out.println("[Blender] invokeEnumButtonSafe 失败: " + t.getMessage());
                return false;
            }
        }

        private static boolean syncActionType(KeyEvent e, int ordinal) throws Exception {
            if (ordinal > 4 && GRAB != null) {
                GRAB.finish(false);
            }
            boolean ok = invokeEnumButtonSafe(
                    "com.hiveworkshop.rms.ui.gui.modeledit.toolbar.ModelEditorActionType3",
                    "setEditorActionTypeButton", ordinal);
            if (ordinal > 4) {
                return ok;
            }
            boolean grabbed;
            if (GRAB == null) {
                grabbed = startGrab(ordinal);
            } else if (ordinal == GRAB.tool()) {
                GRAB.finish(false);
                grabbed = true;
            } else {
                GRAB.finish(false);
                grabbed = startGrab(ordinal);
            }
            return ok || grabbed;
        }

        private static boolean invokeEnumButton(String enumClass, String method, int ordinal) throws Exception {
            Class<?> enumType = Class.forName(enumClass);
            Object[] constants = enumType.getEnumConstants();
            if (constants == null || ordinal >= constants.length) return false;
            Class<?> globals = Class.forName("com.hiveworkshop.rms.ui.application.ProgramGlobals");
            Method m = globals.getMethod(method, enumType);
            m.invoke(null, constants[ordinal]);
            return true;
        }
    }

    private static void repaintAll() {
        for (Component vp : INSTALLED) {
            if (vp.isShowing()) vp.repaint();
        }
    }

    // ------------------------------------------------------------------ 反射

    /** 通过反射取出 BlenderKeys dispatcher 并投递事件（SelfTest 专用）。 */
    private static boolean dispatchViaBlenderKeys(KeyEvent ke) {
        try {
            KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            java.lang.reflect.Field f = KeyboardFocusManager.class
                    .getDeclaredField("keyEventDispatchers");
            f.setAccessible(true);
            java.util.List<?> list = (java.util.List<?>) f.get(kfm);
            if (list != null) {
                for (Object d : list) {
                    if (d.getClass().getName().contains("BlenderKeys")) {
                        boolean result = ((KeyEventDispatcher) d).dispatchKeyEvent(ke);
                        System.out.println("[Blender] dispatchViaBlenderKeys result=" + result);
                        return result;
                    }
                }
            }
            System.out.println("[Blender] dispatchViaBlenderKeys: 没找到 BlenderKeys dispatcher，总数="
                    + (list == null ? 0 : list.size()));
        } catch (Throwable t) {
            System.out.println("[Blender] dispatchViaBlenderKeys 异常: " + t);
            t.printStackTrace();
        }
        return false;
    }

    private static Object field(Object target, String name) throws Exception {
        if (target == null) return null;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        if (target == null) return;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ 进程内自测

    /**
     * 用 -Drms.blender.selftest=true 开启：启动后自动等待模型加载，
     * 直接调用内部实现做断言（不依赖鼠标坐标），结果写入
     * -Drms.blender.selftest.out 指定的文件，结束后退出进程。
     */
    static final class SelfTest implements Runnable {
        private final StringBuilder out = new StringBuilder();
        private int passed;
        private int failed;

        private void check(String name, boolean ok, String detail) {
            if (ok) passed++; else failed++;
            String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : "  | " + detail);
            out.append(line).append('\n');
            System.out.println("[SelfTest] " + line);
        }

        @Override
        public void run() {
            int exit = 1;
            try {
                Component vp = awaitEditingModel();
                if (vp == null) {
                    check("模型与视口就绪", false, "120 秒内未找到带骨骼的已接管视口");
                } else {
                    check("模型与视口就绪", true, vp.getClass().getSimpleName());
                    runTests(vp);
                    captureScreenshot();
                }
                exit = failed == 0 ? 0 : 1;
            } catch (Throwable t) {
                out.append("ERROR ").append(t).append('\n');
                t.printStackTrace();
            } finally {
                out.append("RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');
                try {
                    String path = System.getProperty("rms.blender.selftest.out", "blender-selftest.log");
                    java.nio.file.Files.write(new java.io.File(path).toPath(),
                            out.toString().getBytes("UTF-8"));
                    System.out.println("[SelfTest] 结果已写入 " + path);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                System.out.flush();
                System.exit(exit);
            }
        }

        private void captureScreenshot() {
            String path = System.getProperty("rms.blender.selftest.shot");
            if (path == null || path.trim().isEmpty()) return;
            try {
                SwingUtilities.invokeAndWait(() -> {
                    closePopups();
                    repaintAll();
                });
                Thread.sleep(300);
                java.awt.image.BufferedImage image = new java.awt.Robot().createScreenCapture(
                        new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
                javax.imageio.ImageIO.write(image, "png", new java.io.File(path));
                out.append("SCREENSHOT ").append(path).append('\n');
            } catch (Throwable t) {
                check("保存界面截图", false, String.valueOf(t));
            }
        }

        private Component awaitEditingModel() throws Exception {
            Component initial = awaitModel();
            if (initial == null) return null;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Object tabs = ProgramGlobals.getRootWindowUgg().getWindow();
                    tabs.getClass().getMethod("setSelectedTab", int.class).invoke(tabs, 1);
                } catch (Throwable t) {
                    System.out.println("[SelfTest] 激活编辑页失败: " + t);
                }
            });
            Thread.sleep(500);
            return awaitModel();
        }

        /** 等模型加载完成；期间自动确认模态对话框。 */
        private Component awaitModel() throws Exception {
            for (int i = 0; i < 240; i++) {
                final Component[] found = new Component[1];
                SwingUtilities.invokeAndWait(() -> {
                    dismissDialogs();
                    found[0] = findViewport();
                });
                if (found[0] != null) return found[0];
                Thread.sleep(500);
            }
            return null;
        }

        private void dismissDialogs() {
            for (Window w : Window.getWindows()) {
                if (!(w instanceof javax.swing.JDialog) || !w.isShowing()) continue;
                clickConfirm(w);
            }
        }

        private void clickConfirm(Container c) {
            for (Component child : c.getComponents()) {
                if (child instanceof javax.swing.JButton) {
                    String text = ((javax.swing.JButton) child).getText();
                    if ("确定".equals(text) || "OK".equals(text)) {
                        ((javax.swing.JButton) child).doClick();
                        return;
                    }
                }
                if (child instanceof Container) clickConfirm((Container) child);
            }
        }

        private Component findViewport() {
            for (Component vp : INSTALLED) {
                try {
                    Object mv = field(vp, "modelView");
                    if (!(mv instanceof ModelView)) continue;
                    ModelView view = (ModelView) mv;
                    if (view.getModel() == null) continue;
                    if (view.getModel().getIdObjects().isEmpty()) continue;
                    if (!vp.isShowing()) continue;
                    if (cameraOf(vp) == null) continue;
                    return vp;
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        private void runTests(final Component vp) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    ModelView mv = (ModelView) field(vp, "modelView");
                    UndoManager undo = undoManagerOf(vp);
                    IdObject node = mv.getModel().getIdObjects().get(0);
                    testEditorLink(vp);
                    testTreeSync(vp, mv, undo, node);
                    testFocus(vp);
                    testViewportClick(vp, node);
                    testMenus(vp, node);
                    testScaling();
                    testGrab(vp, node, 0);
                    testGrab(vp, node, 1);
                    testGrab(vp, node, 2);
                    testNumeric(vp, node, 0);
                    testNumeric(vp, node, 1);
                    testNumeric(vp, node, 2);
                    testTopology(vp, mv);
                } catch (Throwable t) {
                    check("自测执行", false, String.valueOf(t));
                    t.printStackTrace();
                }
            });
        }

        private void testEditorLink(Component vp) {
            try {
                ModelPanel panel = ProgramGlobals.getCurrentModelPanel();
                CameraHandler camera = cameraOf(vp);
                MouseListenerThing mouse = (MouseListenerThing) field(vp, "mouseAdapter");
                ViewportActivityManager expected = panel == null ? null : panel.getViewportActivityManager();
                Object actual = field(camera, "activityManager");
                boolean linked = expected != null
                        && field(vp, "modelView") == panel.getModelView()
                        && actual == expected
                        && field(mouse, "activityManager") == expected
                        && field(expected, "newModelEditor") != null
                        && field(expected, "currentActivity") != null
                        && undoManagerOf(vp) != null;
                check("透视视口编辑器与鼠标活动连接", linked,
                        "cameraManager=" + (actual == expected) + " mouseManager="
                                + (field(mouse, "activityManager") == expected));
            } catch (Throwable t) {
                check("透视视口编辑器与鼠标活动连接", false, String.valueOf(t));
            }
        }

        private void testTreeSync(Component vp, ModelView mv, UndoManager undo, IdObject node) {
            try {
                SelectionBundle bundle = new SelectionBundle(Collections.singleton(node));
                new SetSelectionUggAction(bundle, mv, ModelStructureChangeListener.changeListener).redo();
                syncTreeToSelection(vp);
                JTree matched = null;
                JTree visible = null;
                int trees = 0;
                for (Window w : Window.getWindows()) {
                    trees += countTrees(w);
                    if (matched == null) matched = findSelectedTree(w, node, false);
                    if (visible == null) visible = findSelectedTree(w, node, true);
                }
                check("视口选中->节点树联动", matched != null, "扫描到 JTree " + trees + " 棵");
                check("可见节点树同步选中", visible != null, null);
                if (visible != null) {
                    TreePath path = visible.getSelectionPath();
                    Rectangle row = visible.getRowBounds(visible.getRowForPath(path));
                    boolean expanded = visible.isVisible(path);
                    boolean scrolled = row != null && visible.getVisibleRect().intersects(row);
                    check("节点树展开并滚动到选中项", expanded && scrolled,
                            "expanded=" + expanded + " scrolled=" + scrolled);
                    Object treeNode = path.getLastPathComponent();
                    if (treeNode instanceof NodeThing) {
                        java.awt.Color color = (java.awt.Color) field(treeNode, "color2");
                        check("节点树高亮", color != null && color.getAlpha() >= 100,
                                String.valueOf(color));
                    } else {
                        check("节点树高亮", visible.isPathSelected(path), "使用 Swing 选中高亮");
                    }
                }
            } catch (Throwable t) {
                check("视口选中->节点树联动", false, String.valueOf(t));
            }
        }

        private int countTrees(Component c) {
            int n = c instanceof JTree ? 1 : 0;
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) n += countTrees(child);
            }
            return n;
        }

        private JTree findSelectedTree(Component c, IdObject node, boolean mustShow) {
            if (c instanceof JTree) {
                JTree tree = (JTree) c;
                if (!mustShow || tree.isShowing()) {
                    TreePath[] paths = tree.getSelectionPaths();
                    if (paths != null) {
                        for (TreePath p : paths) {
                            if (itemOf(p.getLastPathComponent()) == node) return tree;
                        }
                    }
                }
                return null;
            }
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    JTree found = findSelectedTree(child, node, mustShow);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private void testViewportClick(Component vp, IdObject node) {
            try {
                ModelView mv = (ModelView) field(vp, "modelView");
                BlenderMouse mouse = null;
                for (java.awt.event.MouseListener listener : vp.getMouseListeners()) {
                    if (listener instanceof BlenderMouse) mouse = (BlenderMouse) listener;
                }
                if (mouse == null) {
                    check("真实视口左键点选并联动树", false, "视口未被接管");
                    return;
                }
                // testFocus 已把该节点投影到视口中心；先清空选择，再走完整鼠标事件路径。
                new SetSelectionUggAction(new SelectionBundle(Collections.<IdObject>emptySet()), mv,
                        ModelStructureChangeListener.changeListener).redo();
                int x = vp.getWidth() / 2, y = vp.getHeight() / 2;
                mouse.mousePressed(new MouseEvent(vp, MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, x, y,
                        1, false, MouseEvent.BUTTON1));
                mouse.mouseReleased(new MouseEvent(vp, MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
                JTree visible = null;
                for (Window w : Window.getWindows()) {
                    if (visible == null) visible = findSelectedTree(w, node, true);
                }
                check("真实视口左键点选并联动树",
                        mv.getSelectedIdObjects().contains(node) && visible != null,
                        "selected=" + mv.getSelectedIdObjects().contains(node)
                                + " visibleTree=" + (visible != null));
            } catch (Throwable t) {
                check("真实视口左键点选并联动树", false, String.valueOf(t));
            } finally {
                restoreSelection(vp, node);
            }
        }

        private void testMenus(Component vp, IdObject node) {
            try {
                JTree tree = null;
                for (Window w : Window.getWindows()) {
                    if (tree == null) tree = findSelectedTree(w, node, true);
                }
                if (tree == null) {
                    check("节点树右键菜单", false, "没有可见的已选中节点");
                } else {
                    TreePath path = tree.getSelectionPath();
                    Rectangle row = tree.getRowBounds(tree.getRowForPath(path));
                    MouseEvent right = new MouseEvent(tree, MouseEvent.MOUSE_PRESSED,
                            System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK,
                            row.x + Math.max(2, row.width / 2), row.y + row.height / 2,
                            1, false, MouseEvent.BUTTON3);
                    new TreeWatcher().eventDispatched(right);
                    JPopupMenu popup = visiblePopup();
                    check("节点树右键菜单", popup != null
                                    && menuHas(popup, "聚焦此节点 (.)")
                                    && menuHas(popup, "重命名节点…"), null);
                    closePopups();
                }
            } catch (Throwable t) {
                check("节点树右键菜单", false, String.valueOf(t));
                closePopups();
            }
            try {
                BlenderMouse mouse = null;
                for (java.awt.event.MouseListener listener : vp.getMouseListeners()) {
                    if (listener instanceof BlenderMouse) mouse = (BlenderMouse) listener;
                }
                if (mouse == null) {
                    check("视口右键菜单", false, "视口未被接管");
                    restoreSelection(vp, node);
                    return;
                }
                int x = vp.getWidth() / 2, y = vp.getHeight() / 2;
                Point p = new Point(x, y);
                mouse.mousePressed(synth(vp, MouseEvent.MOUSE_PRESSED, p,
                        InputEvent.BUTTON3_DOWN_MASK));
                MouseEvent released = synth(vp, MouseEvent.MOUSE_RELEASED, p, 0);
                mouse.mouseReleased(new MouseEvent(vp, MouseEvent.MOUSE_RELEASED,
                        released.getWhen(), 0, x, y, 1, false, MouseEvent.BUTTON3));
                JPopupMenu popup = visiblePopup();
                check("视口右键菜单", popup != null
                                && menuHas(popup, "聚焦所选 (.)")
                                && menuHas(popup, "重命名所选骨骼…"), null);
                closePopups();
            } catch (Throwable t) {
                check("视口右键菜单", false, String.valueOf(t));
                closePopups();
            }
            restoreSelection(vp, node);
        }

        private void restoreSelection(Component vp, IdObject node) {
            try {
                ModelView mv = (ModelView) field(vp, "modelView");
                new SetSelectionUggAction(new SelectionBundle(Collections.singleton(node)), mv,
                        ModelStructureChangeListener.changeListener).redo();
                syncTreeToSelection(vp);
            } catch (Throwable ignored) {
            }
        }

        private boolean menuHas(JPopupMenu menu, String text) {
            if (menu == null) return false;
            for (Component c : menu.getComponents()) {
                if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText())) return true;
            }
            return false;
        }

        private JPopupMenu visiblePopup() {
            for (Window w : Window.getWindows()) {
                JPopupMenu popup = visiblePopup(w);
                if (popup != null) return popup;
            }
            return null;
        }

        private JPopupMenu visiblePopup(Component c) {
            if (c instanceof JPopupMenu && c.isVisible()) return (JPopupMenu) c;
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    JPopupMenu popup = visiblePopup(child);
                    if (popup != null) return popup;
                }
            }
            return null;
        }

        private void closePopups() {
            JPopupMenu popup;
            while ((popup = visiblePopup()) != null) popup.setVisible(false);
        }

        private void testScaling() {
            try {
                float actual = Float.parseFloat(System.getProperty("sun.java2d.uiScale", "1"));
                check("Java2D 字体与控件同步缩放配置",
                        Math.abs(actual - ScalingHooks.scale()) < 0.001f,
                        "uiScale=" + actual);
            } catch (Throwable t) {
                check("Java2D 字体与控件同步缩放配置", false, String.valueOf(t));
            }
        }

        private void testFocus(Component vp) {
            try {
                CameraHandler cam = cameraOf(vp);
                double[] b = selectionBounds(vp);
                if (b == null) {
                    check("聚焦(.)", false, "没有选中内容");
                    return;
                }
                frameSelected(vp);
                Vec3 camPos = cam.getCameraPos();
                double zoom = cam.getZoom();
                Vec3 rc = rotateCam(cam, new Vec3(b[0], b[1], b[2]));
                boolean centered = Math.abs(camPos.y - zoom * rc.y) < 1e-3
                        && Math.abs(camPos.z - zoom * rc.z) < 1e-3;
                boolean inFront = camPos.x > zoom * rc.x;
                check("聚焦(.)居中", centered, "camPos=" + camPos + " zoom=" + zoom);
                check("聚焦(.)目标在相机前方", inFront,
                        "depth=" + (camPos.x - zoom * rc.x));
            } catch (Throwable t) {
                check("聚焦(.)", false, String.valueOf(t));
            }
        }

        /** tool: 0=G 1=R 2=S，逐个验证 X/Y/Z 轴锁定，最后撤销。 */
        private void testGrab(Component vp, IdObject node, int tool) {
            String[] names = {"G 移动", "R 旋转", "S 缩放"};
            int[] bits = {1, 2, 4};
            String[] axes = {"X", "Y", "Z"};
            for (int i = 0; i < bits.length; i++) {
                AxisGrab g = null;
                try {
                    CameraHandler camera = cameraOf(vp);
                    ViewportActivityManager am = (ViewportActivityManager) field(camera, "activityManager");
                    ModelEditor editor = (ModelEditor) field(am, "newModelEditor");
                    UndoManager undo = undoManagerOf(vp);
                    double[] b = selectionBounds(vp);
                    Point mp = new Point(vp.getWidth() / 2, vp.getHeight() / 2);
                    Vec3 geoStart = camera.getGeoPoint(mp.x, mp.y);
                    Vec3 pivot = new Vec3(b[0], b[1], b[2]);
                    if (tool != 0) {
                        // 单骨骼围绕自身 pivot 旋转/缩放不会产生位移；偏移操作中心以验证真实轴约束。
                        pivot.translate(1, 2, 3);
                    }
                    Vec3 before = new Vec3(node.getPivotPoint());
                    g = new AxisGrab(vp, camera, editor, undo, tool, geoStart, pivot, mp.x, mp.y);
                    g.begin();
                    g.setAxis(bits[i], false);
                    double sx;
                    double sy;
                    if (tool == 1) {
                        // 旋转必须沿切向拖动，沿轴自身投影拖动的正确结果应为零。
                        sx = g.dirX;
                        sy = g.dirY;
                    } else {
                        Vec3 plane = rotateCam(camera, new Vec3(
                                bits[i] == 1 ? 1 : 0, bits[i] == 2 ? 1 : 0, bits[i] == 4 ? 1 : 0));
                        sx = plane.y;
                        sy = -plane.z;
                    }
                    double len = Math.sqrt(sx * sx + sy * sy);
                    if (len < 1e-6) { sx = 1; sy = 0; len = 1; }
                    int dx = (int) Math.round(60 * sx / len);
                    int dy = (int) Math.round(60 * sy / len);
                    g.drive(mp.x + dx, mp.y + dy);
                    Vec3 after = new Vec3(node.getPivotPoint());
                    double ddx = after.x - before.x, ddy = after.y - before.y, ddz = after.z - before.z;
                    double distance = Math.abs(ddx) + Math.abs(ddy) + Math.abs(ddz);
                    String detail = "Δpivot=(" + f(ddx) + "," + f(ddy) + "," + f(ddz) + ")";
                    boolean ok;
                    if (tool == 0) {
                        ok = onlyAxis(ddx, ddy, ddz, i) && distance > 1e-6;
                    } else if (tool == 1) {
                        double axisDelta = i == 0 ? ddx : (i == 1 ? ddy : ddz);
                        ok = Math.abs(g.appliedAngle) > 0.01 && Math.abs(axisDelta) < 1e-4 && distance > 1e-4;
                        detail += " angle=" + f(g.appliedAngle);
                    } else {
                        ok = Math.abs(g.appliedScale - 1) > 1e-6
                                && onlyAxis(ddx, ddy, ddz, i) && distance > 1e-4;
                        detail += " scale=" + f(g.appliedScale);
                    }
                    check(names[tool] + " + " + axes[i] + " 轴锁定", ok, detail);
                    g.finish(true);
                    Vec3 restored = node.getPivotPoint();
                    check(names[tool] + " + " + axes[i] + " 取消可撤销",
                            Math.abs(restored.x - before.x) < 1e-4
                                    && Math.abs(restored.y - before.y) < 1e-4
                                    && Math.abs(restored.z - before.z) < 1e-4, null);
                    g = null;
                } catch (Throwable t) {
                    check(names[tool] + " + " + axes[i], false, String.valueOf(t));
                    if (g != null) {
                        try { g.finish(true); } catch (Throwable ignored) { }
                    }
                }
            }
        }

        /** 数字精确输入：锁定 X 轴后输入 2 或 30（度），结果必须精确且可撤销。 */
        private void testNumeric(Component vp, IdObject node, int tool) {
            String[] names = {"G 移动", "R 旋转", "S 缩放"};
            AxisGrab g = null;
            try {
                CameraHandler camera = cameraOf(vp);
                ViewportActivityManager am = (ViewportActivityManager) field(camera, "activityManager");
                ModelEditor editor = (ModelEditor) field(am, "newModelEditor");
                UndoManager undo = undoManagerOf(vp);
                double[] b = selectionBounds(vp);
                Point mp = new Point(vp.getWidth() / 2, vp.getHeight() / 2);
                Vec3 geoStart = camera.getGeoPoint(mp.x, mp.y);
                Vec3 pivot = new Vec3(b[0], b[1], b[2]);
                if (tool != 0) pivot.translate(1, 2, 3);
                Vec3 before = new Vec3(node.getPivotPoint());
                g = new AxisGrab(vp, camera, editor, undo, tool, geoStart, pivot, mp.x, mp.y);
                g.begin();
                g.setAxis(1, false);
                g.applyNumeric(tool == 1 ? 30.0 : 2.0);
                Vec3 after = new Vec3(node.getPivotPoint());
                double dx = after.x - before.x, dy = after.y - before.y, dz = after.z - before.z;
                boolean ok;
                String detail = "Δpivot=(" + f(dx) + "," + f(dy) + "," + f(dz) + ")";
                if (tool == 0) {
                    ok = Math.abs(dx - 2.0) < 1e-3 && onlyAxis(dx, dy, dz, 0);
                } else if (tool == 1) {
                    ok = Math.abs(g.appliedAngle - Math.toRadians(30)) < 1e-4 && Math.abs(dx) < 1e-4;
                } else {
                    ok = Math.abs(g.appliedScale - 2.0) < 1e-4 && onlyAxis(dx, dy, dz, 0);
                    detail += " scale=" + f(g.appliedScale);
                }
                check(names[tool] + " 数字输入 X 轴", ok, detail);
                g.finish(true);
                Vec3 restored = node.getPivotPoint();
                check(names[tool] + " 数字输入可撤销",
                        Math.abs(restored.x - before.x) < 1e-4
                                && Math.abs(restored.y - before.y) < 1e-4
                                && Math.abs(restored.z - before.z) < 1e-4, null);
                g = null;
            } catch (Throwable t) {
                check(names[tool] + " 数字输入", false, String.valueOf(t));
                if (g != null) {
                    try { g.finish(true); } catch (Throwable ignored) { }
                }
            }
        }

        /** L / Shift+L：验证从鼠标位置出发的拓扑连通块选择。 */
        private void testTopology(Component vp, ModelView mv) {
            try {
                Method setType = ProgramGlobals.class.getDeclaredMethod("setSelectionItemType", SelectionItemTypes.class);
                setType.setAccessible(true);
                Object oldType = ProgramGlobals.getSelectionItemType();
                setType.invoke(null, SelectionItemTypes.VERTEX);
                ProgramGlobals.setSelectionTypeButton(SelectionItemTypes.VERTEX);

                FORCE_VIEWPORT = vp;
                Geoset seedGeoset = null;
                GeosetVertex seed = null;
                for (Geoset g : mv.getModel().getGeosets()) {
                    if (!g.getTriangles().isEmpty() && !g.getVertices().isEmpty()) {
                        seedGeoset = g;
                        seed = g.getVertices().get(0);
                        break;
                    }
                }
                if (seed == null) {
                    check("L 拓扑选择", false, "模型里没有可用的三角面");
                    FORCE_VIEWPORT = null;
                    return;
                }
                LAST_MOUSE_X = -1;
                LAST_MOUSE_Y = -1;
                boolean ok = BlenderKeys.topologicalSelect(false, java.util.Collections.singleton(seed));
                int firstCount = mv.getSelectedVertices().size();
                check("L 拓扑选择", ok && mv.getSelectedVertices().contains(seed) && firstCount > 1,
                        "选中 " + firstCount + " 个顶点");

                GeosetVertex seed2 = null;
                for (Geoset g : mv.getModel().getGeosets()) {
                    if (g != seedGeoset && !g.getTriangles().isEmpty() && !g.getVertices().isEmpty()) {
                        seed2 = g.getVertices().get(0);
                        break;
                    }
                }
                if (seed2 != null) {
                    boolean ok2 = BlenderKeys.topologicalSelect(true, java.util.Collections.singleton(seed2));
                    int secondCount = mv.getSelectedVertices().size();
                    check("Shift+L 叠加连通块",
                            ok2 && secondCount > firstCount
                                    && mv.getSelectedVertices().contains(seed)
                                    && mv.getSelectedVertices().contains(seed2),
                            "叠加后 " + secondCount + " 个顶点");
                }
                LAST_MOUSE_X = -1;
                LAST_MOUSE_Y = -1;
                FORCE_VIEWPORT = null;
                if (oldType instanceof SelectionItemTypes) {
                    SelectionItemTypes restore = (SelectionItemTypes) oldType;
                    setType.invoke(null, restore);
                    ProgramGlobals.setSelectionTypeButton(restore);
                }
            } catch (Throwable t) {
                check("L 拓扑选择", false, String.valueOf(t));
            }
        }

        private boolean onlyAxis(double dx, double dy, double dz, int axis) {
            double[] d = {dx, dy, dz};
            for (int i = 0; i < 3; i++) {
                if (i != axis && Math.abs(d[i]) > 1e-4) return false;
            }
            return true;
        }

        private String f(double v) {
            return String.format(java.util.Locale.US, "%.4f", v);
        }
    }
}
