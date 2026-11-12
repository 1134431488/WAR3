import java.lang.reflect.Method;

/**
 * Blender 模式启动入口。
 *
 * 顺序：安装视口增强 -> 调用气泡启动器（若存在）-> 由它反射调用原 Main。
 * 如果 rms-tooltips.jar 被移走，则直接启动原 Main，Blender 增强依然有效。
 */
public class BlenderLauncher {
    public static void main(String[] args) {
        System.out.println("[Blender] 正在安装 Blender 视口增强 ...");
        try {
            BlenderHooks.install();
        } catch (Throwable t) {
            System.out.println("[Blender] 增强安装失败（原程序仍会正常启动）：");
            t.printStackTrace();
        }
        try {
            Class.forName("com.hiveworkshop.rms.ui.application.viewer.MouseEventSpy")
                 .getMethod("install").invoke(null);
        } catch (Throwable t) {
            System.out.println("[Blender] 探针安装失败：");
            t.printStackTrace();
        }

        try {
            Class<?> launcher = Class.forName("RmsTooltipInstaller");
            Method main = launcher.getMethod("main", String[].class);
            main.invoke(null, (Object) args);
            return;
        } catch (ClassNotFoundException ignored) {
            System.out.println("[Blender] 未找到气泡组件，直接启动主程序。");
        } catch (Throwable t) {
            System.out.println("[Blender] 气泡启动器调用失败，改为直接启动主程序：");
            t.printStackTrace();
        }
        try {
            Class<?> mainClass = Class.forName("com.hiveworkshop.rms.Main");
            mainClass.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
