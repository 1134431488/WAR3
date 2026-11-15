import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * 把普通 JButton 统一成工具栏那套「深底 + 白字 + 直角」的 Blender 风格。
 *
 * 关键约束：本项目的 InfoNode / JTattoo 主题会忽略 setBackground 却保留
 * setForeground，历史上有过「白字落在浅底上看不见」的事故。所以这里
 * 绝不依赖 setBackground / UIManager 颜色，而是和 ToolbarButton2.FlatTextButton
 * 一样：contentAreaFilled=false + 自绘 fillRect 深底 + setForeground 白字，
 * 两者成对出现。
 *
 * 只改增强层（rms-blender.jar），不动任何原 class；apply 幂等，
 * 并按 UI 实例判断，主题切换导致 updateUI 后会自动重新套用。
 */
public final class BlenderButtonStyle {

    private static final Color BG = new Color(0x3C, 0x3F, 0x41);
    private static final Color BG_HOVER = new Color(0x4A, 0x4E, 0x51);
    private static final Color BG_PRESSED = new Color(0x2E, 0x31, 0x33);
    private static final Color FG = new Color(0xDC, 0xDC, 0xDC);
    private static final Color EDGE = new Color(0x18, 0x18, 0x18);
    private static final String KEY = "rms.blender.buttonStyle";

    private BlenderButtonStyle() {
    }

    public static void apply(AbstractButton button) {
        if (button == null) return;
        // 已经自绘的类跳过，避免双层绘制
        String className = button.getClass().getName();
        if (className.contains("ModeButton") || className.contains("FlatTextButton")
                || className.contains("TinyToggleButton") || className.contains("TinyButton")) {
            return;
        }
        // 菜单项 / 复选 / 单选保持原生外观；纯图标按钮也不动（深底对图标没意义）
        if (button instanceof JMenuItem || button instanceof JCheckBox
                || button instanceof JRadioButton || button instanceof JToggleButton) {
            return;
        }
        String text = button.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        // 大纲树每行前面的 E / V 状态圆按钮：它们的背景色承载「开/关」语义，
        // 且组件被 TreeCellRenderer 反复复用，套新 UI 会导致文字重叠、缩进错乱。
        if (text.length() <= 1) {
            return;
        }
        // UI 实例一致 => 已套用且主题未重置；换了 UI 则重新套用
        if (button.getClientProperty(KEY) == button.getUI()) {
            return;
        }
        try {
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setRolloverEnabled(true);
            button.setForeground(FG);
            button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
            button.setBorder(BorderFactory.createLineBorder(EDGE));
            button.setUI(new BasicButtonUI() {
                @Override
                public void paint(Graphics g, JComponent c) {
                    AbstractButton b = (AbstractButton) c;
                    ButtonModel m = b.getModel();
                    Color fill = m.isPressed() ? BG_PRESSED
                            : m.isRollover() ? BG_HOVER : BG;
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(fill);
                    g2.fillRect(0, 0, c.getWidth(), c.getHeight());
                    g2.setColor(EDGE);
                    g2.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
                    g2.dispose();
                    super.paint(g, c);
                }
            });
            button.putClientProperty(KEY, button.getUI());
        } catch (Throwable ignored) {
            // 单个按钮失败不影响其它组件
        }
    }

    public static void applyToTree(Container container) {
        if (container == null) return;
        // JTree / JTable / JList 的 renderer 组件临时挂在 CellRendererPane 下，
        // 它们不是真正的界面按钮（且被复用），一律不碰。
        if (container instanceof CellRendererPane) return;
        Component[] components;
        try {
            components = container.getComponents();
        } catch (Throwable t) {
            return;
        }
        for (Component component : components) {
            if (component instanceof AbstractButton) {
                apply((AbstractButton) component);
            }
            if (component instanceof Container) {
                applyToTree((Container) component);
            }
        }
    }

    /** 由 BlenderHooks 的 500ms 定时扫描调用，覆盖运行时新建 / 重排的窗口。 */
    public static void applyToAllWindows() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window instanceof Container) {
                applyToTree((Container) window);
            }
        }
    }
}
