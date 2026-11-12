import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 验证用截屏工具：可先把鼠标移到指定坐标，等待 Swing 气泡弹出后再截全屏。
 * 用法：java -cp _downloads TooltipShot <输出.png> [鼠标X 鼠标Y]
 */
public class TooltipShot {
    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        int sw = gd.getDisplayMode().getWidth();
        int sh = gd.getDisplayMode().getHeight();
        System.out.println("SCREEN " + sw + "x" + sh);

        if (args.length >= 8 && "activate".equals(args[7])) {
            // 单击 RMS 窗口标题栏，把窗口切成活动窗口（ToolTipManager 更可靠）
            robot.mouseMove(900, 392);
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            System.out.println("ACTIVATED WINDOW");
            Thread.sleep(400);
        }
        if (args.length >= 3) {
            int mx = Integer.parseInt(args[1]);
            int my = Integer.parseInt(args[2]);
            robot.mouseMove(mx, my);
            System.out.println("MOUSE " + mx + "," + my);
            Thread.sleep(2200); // ToolTipManager 默认约 750ms + 扫描周期 1500ms
        }
        int x = 0, y = 0, w = sw, h = sh;
        if (args.length >= 7) {
            x = Integer.parseInt(args[3]);
            y = Integer.parseInt(args[4]);
            w = Integer.parseInt(args[5]);
            h = Integer.parseInt(args[6]);
        }
        BufferedImage img = robot.createScreenCapture(new Rectangle(x, y, w, h));
        ImageIO.write(img, "png", new File(args[0]));
        System.out.println("SAVED " + args[0] + " (" + w + "x" + h + ")");
    }
}
