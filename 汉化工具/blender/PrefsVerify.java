import com.hiveworkshop.rms.ui.preferences.SaveProfile;
import java.lang.reflect.Field;

/** 只读校验：从磁盘加载 SaveProfile，确认 Blender 偏好确实落盘。 */
public class PrefsVerify {
    public static void main(String[] args) throws Exception {
        SaveProfile sp = SaveProfile.get();
        Field pf = SaveProfile.class.getDeclaredField("preferences");
        pf.setAccessible(true);
        Object p = pf.get(sp);
        for (String n : new String[]{"threeDCameraSpinMouseEx",
                "threeDCameraPanMouseEx", "cameraToggleOrtho",
                "cameraFrontKB", "cameraTopKB", "cameraOppositeKB"}) {
            Field f = p.getClass().getDeclaredField(n);
            f.setAccessible(true);
            System.out.println(n + " = " + f.get(p));
        }
        Field ec = p.getClass().getDeclaredField("editorColors");
        ec.setAccessible(true);
        String colors = String.valueOf(ec.get(p));
        for (String line : colors.split("\n")) {
            if (line.contains("BACKGROUND_COLOR") || line.contains("VERTEX_SELECTED")
                    || line.contains("TRIANGLE_LINE_SELECTED")) {
                System.out.println("editorColors | " + line.trim());
            }
        }
    }
}
