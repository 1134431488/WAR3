import com.hiveworkshop.rms.ui.preferences.ColorThing;
import com.hiveworkshop.rms.ui.preferences.EditorColorPrefs;
import com.hiveworkshop.rms.ui.preferences.SaveProfile;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 把 Retera Model Studio 的出厂偏好改成 Blender 风格（离线工具，只写配置文件，不改任何 class）。
 *
 * 关键点：真正控制线框/顶点/选中/视口背景颜色的是 ProgramPreferences.editorColors
 * （EditorColorPrefs 序列化字符串，键是 ColorThing 枚举名），不是那些 Color 字段。
 *
 * 用法：java -cp "RMS_zh_CN.jar;rms-blender.jar" BlenderPrefsTool [备份目录]
 */
public class BlenderPrefsTool {

    private static final int ALT = InputEvent.ALT_DOWN_MASK;          // 512
    private static final int BUTTON1 = InputEvent.BUTTON1_DOWN_MASK;  // 1024
    private static final int BUTTON3 = InputEvent.BUTTON3_DOWN_MASK;  // 4096

    // Blender 默认主题的关键色
    private static final Color ORANGE = new Color(255, 140, 0, 255);   // 选中 / 活动
    private static final Color ORANGE_LIGHT = new Color(255, 190, 100, 255);
    private static final Color BG = new Color(56, 56, 56, 255);         // 3D 视口背景
    private static final Color GRID = new Color(170, 170, 170, 255);    // 线框灰

    public static void main(String[] args) throws Exception {
        String backupDir = args.length > 0 ? args[0] : ".";

        // ---- 0. 先备份用户现有配置 ----
        File profileDir = new File(System.getProperty("user.home")
                + "\\AppData\\Roaming\\ReteraStudioBeta");
        File profileFile = new File(profileDir, "user.profileNew");
        if (profileFile.isFile()) {
            File dir = new File(backupDir);
            dir.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backup = new File(dir, "user.profileNew." + stamp + ".bak");
            Files.copy(profileFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Blender] 已备份原偏好 -> " + backup.getAbsolutePath());
        } else {
            System.out.println("[Blender] 尚无偏好文件，将创建一份。");
        }

        // ---- 1. 用程序自己的类加载/创建配置，保证序列化 100% 兼容 ----
        Object profile = SaveProfile.get();
        Field prefField = SaveProfile.class.getDeclaredField("preferences");
        prefField.setAccessible(true);
        Object prefs = prefField.get(profile);
        if (prefs == null) {
            prefs = Class.forName("com.hiveworkshop.rms.ui.preferences.ProgramPreferences")
                    .getDeclaredConstructor().newInstance();
            prefField.set(profile, prefs);
        }

        // ---- 2. Blender 键鼠导航 ----
        // Alt+左键旋转、Alt+右键平移；普通左键仍是选择、普通右键仍是编辑操作
        set(prefs, "threeDCameraSpinMouseEx", ALT | BUTTON1);
        set(prefs, "threeDCameraPanMouseEx", ALT | BUTTON3);
        // Blender: Num5 切换正交/透视（原版是字母 O）
        set(prefs, "cameraToggleOrtho", KeyEvent.VK_NUMPAD5);
        // 数字键盘 1/3/7/4/6/8/2/0 + Ctrl 反向视图本来就是出厂值，保持不动
        set(prefs, "cameraOppositeKB", InputEvent.CTRL_DOWN_MASK);

        // ---- 3. Blender 配色（旧字段，保留兼容） ----
        set(prefs, "perspectiveBackgroundColor", new Color(56, 56, 56));
        set(prefs, "backgroundColor", new Color(48, 48, 48));
        set(prefs, "selectColor", ORANGE);
        set(prefs, "vertexColor", new Color(20, 20, 20));
        set(prefs, "highlighVertexColor", ORANGE_LIGHT);
        set(prefs, "triangleColor", GRID);
        set(prefs, "highlighTriangleColor", ORANGE);
        set(prefs, "normalsColor", new Color(80, 140, 255));
        set(prefs, "pivotPointsColor", new Color(220, 220, 220));
        set(prefs, "pivotPointsSelectedColor", ORANGE);
        set(prefs, "showPerspectiveGrid", Boolean.TRUE);

        // ---- 4. 真正生效的编辑器配色表 ----
        EditorColorPrefs ecp = editorColorPrefs(prefs);
        putColor(ecp, "BACKGROUND_COLOR", BG);
        putColor(ecp, "VERTEX", new Color(20, 20, 20, 200));
        putColor(ecp, "VERTEX_SELECTED", ORANGE);
        putColor(ecp, "VERTEX_HIGHLIGHTED", ORANGE_LIGHT);
        putColor(ecp, "VERTEX_UNEDITABLE", new Color(110, 110, 110, 110));
        putColor(ecp, "TRIANGLE_LINE", GRID);
        putColor(ecp, "TRIANGLE_LINE_SELECTED", ORANGE);
        putColor(ecp, "TRIANGLE_LINE_HIGHLIGHTED", ORANGE_LIGHT);
        putColor(ecp, "TRIANGLE_LINE_UNEDITABLE", new Color(110, 110, 110, 160));
        putColor(ecp, "TRIANGLE_AREA_SELECTED", new Color(255, 140, 0, 60));
        putColor(ecp, "TRIANGLE_AREA_HIGHLIGHTED", new Color(255, 190, 100, 45));
        putColor(ecp, "NODE_SELECTED", ORANGE);
        putColor(ecp, "NODE_HIGHLIGHTED", ORANGE_LIGHT);
        set(prefs, "editorColors", ecp.toString());

        // ---- 5. 写回 ----
        SaveProfile.save();
        System.out.println("[Blender] Blender 偏好已写入：" + profileFile.getAbsolutePath());
        System.out.println("[Blender] 旋转=Alt+左键  平移=Alt+右键  Num5=正交切换  配色=Blender 深灰+橙");
    }

    private static EditorColorPrefs editorColorPrefs(Object prefs) {
        try {
            Object raw = get(prefs, "editorColors");
            return new EditorColorPrefs().parseString(raw == null ? "" : raw.toString());
        } catch (Throwable t) {
            return new EditorColorPrefs();
        }
    }

    private static void putColor(EditorColorPrefs ecp, String name, Color color) {
        try {
            ecp.setColor(ColorThing.valueOf(name), color);
        } catch (Throwable t) {
            System.out.println("[Blender] 跳过不存在的颜色项: " + name);
        }
    }

    private static Object get(Object target, String name) throws Exception {
        Field f = find(target.getClass(), name);
        if (f == null) return null;
        f.setAccessible(true);
        return f.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = find(target.getClass(), name);
        if (f == null) {
            System.out.println("[Blender] 字段不存在，跳过: " + name);
            return;
        }
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field find(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
