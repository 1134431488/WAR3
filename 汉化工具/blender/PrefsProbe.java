import com.hiveworkshop.rms.ui.preferences.ProgramPreferences;
import java.awt.Color;
import java.lang.reflect.Field;

/** 只读探针：打印 release jar 里 ProgramPreferences 的真实出厂默认值。 */
public class PrefsProbe {
    public static void main(String[] args) throws Exception {
        ProgramPreferences p = new ProgramPreferences();
        String[] fields = {
            "threeDCameraSpinMouseEx", "threeDCameraPanMouseEx",
            "threeDCameraSpinButton", "threeDCameraPanButton",
            "selectMouseButton", "modifyMouseButton",
            "addSelectModifier", "removeSelectModifier",
            "cameraFrontKB", "cameraSideKB", "cameraTopKB",
            "cameraLocZoomReset", "cameraOppositeKB",
            "cameraRotateSidePosKB", "cameraRotateSideNegKB",
            "cameraRotateUpKB", "cameraRotateDownKB", "cameraToggleOrtho",
            "backgroundColor", "perspectiveBackgroundColor",
            "vertexColor", "highlighVertexColor", "triangleColor",
            "highlighTriangleColor", "selectColor", "normalsColor",
            "showPerspectiveGrid", "theme"
        };
        for (String name : fields) {
            try {
                Field f = ProgramPreferences.class.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(p);
                if (v instanceof Color) {
                    Color c = (Color) v;
                    v = "Color(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")";
                }
                System.out.println(name + " = " + v);
            } catch (NoSuchFieldException e) {
                System.out.println(name + " = <jar 中无此字段>");
            }
        }
        System.out.println("serialVersionUID = "
                + ProgramPreferences.class.getDeclaredField("serialVersionUID").get(null));
    }
}
