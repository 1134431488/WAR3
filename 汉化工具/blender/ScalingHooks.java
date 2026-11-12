/**
 * UI 全局放大（运行时注入）。
 *
 * 旧方案是「只放大字体」：控件尺寸、内边距、固定高度都不变，文字会被裁掉。
 * 新方案改用 JDK 自带的 HiDPI 缩放（sun.java2d.uiScale），由 Java2D 统一缩放
 * 字体、控件、布局和自绘内容，文字和界面比例始终一致，不会截断；OpenGL 视口
 * 也按逻辑像素渲染，缩放后仍然正常。
 *
 * 倍率可用 JVM 参数调整：-Drms.ui.scale=1.35（默认 1.35，范围 1.0 ~ 3.0）。
 * 如果外部已经显式指定 -Dsun.java2d.uiScale，则以外部参数为准。
 */
public final class ScalingHooks {

    private ScalingHooks() {
    }

    public static float scale() {
        String p = System.getProperty("rms.ui.scale", "1.35");
        try {
            float f = Float.parseFloat(p.trim());
            if (f >= 1.0f && f <= 3.0f) return f;
        } catch (NumberFormatException ignored) {
        }
        return 1.35f;
    }

    public static void install() {
        float k = scale();
        if (System.getProperty("sun.java2d.uiScale") == null) {
            System.setProperty("sun.java2d.uiScale", Float.toString(k));
        }
        // 字体渲染：使用 GASP 模式 + FractionalMetrics + LCD subpixel，
        // 减少非整数缩放（如 1.35x）下的字体模糊。
        if (System.getProperty("awt.useSystemAAFontSettings") == null) {
            System.setProperty("awt.useSystemAAFontSettings", "gasp");
        }
        if (System.getProperty("swing.aatext") == null) {
            System.setProperty("swing.aatext", "true");
        }
        if (System.getProperty("sun.java2d.fontSettings.useFractionalMetrics") == null) {
            System.setProperty("sun.java2d.fontSettings.useFractionalMetrics", "true");
        }
        System.out.println("[Scale] 使用 Java2D 统一缩放 x"
                + System.getProperty("sun.java2d.uiScale")
                + "，AA=" + System.getProperty("awt.useSystemAAFontSettings")
                + "，fractionalMetrics=" + System.getProperty("sun.java2d.fontSettings.useFractionalMetrics"));
    }
}
