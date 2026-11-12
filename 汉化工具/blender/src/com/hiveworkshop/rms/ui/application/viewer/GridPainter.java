package com.hiveworkshop.rms.ui.application.viewer;

import org.lwjgl.opengl.GL11;

import java.awt.Component;
import java.lang.reflect.Field;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor3f;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glNormal3f;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL11.glVertex3f;

/**
 * Blender 风格视口背景与网格（替换类，公共接口与原版完全一致：
 * GridPainter(CameraHandler) + paintGrid()）。
 *
 * paintGrid() 在 glClear 之后、模型渲染之前被调用，因此可以在这里同时绘制
 * 全屏渐变背景与地面网格，无需改动 PerspectiveViewport 的字节码。
 */
public class GridPainter {

    private final CameraHandler cameraHandler;

    public GridPainter(CameraHandler cameraHandler) {
        this.cameraHandler = cameraHandler;
    }

    public void paintGrid() {
        paintGradientBackground();
        paintBlenderGrid();
    }

    // -------------------------------------------------------------- 渐变背景

    private void paintGradientBackground() {
        int[] wh = viewportSize();
        float w = Math.max(1, wh[0]);
        float h = Math.max(1, wh[1]);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        // 关键：背景只写颜色，绝不写深度，否则会把后面的网格与模型全部挡掉
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);

        // Blender 默认 3D 视口：上方略深、下方略浅的中性灰
        glBegin(GL_QUADS);
        glColor3f(0.175f, 0.175f, 0.190f);
        glVertex2f(0, 0);
        glVertex2f(w, 0);
        glColor3f(0.275f, 0.275f, 0.295f);
        glVertex2f(w, h);
        glVertex2f(0, h);
        glEnd();

        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private int[] viewportSize() {
        try {
            Field f = CameraHandler.class.getDeclaredField("viewport");
            f.setAccessible(true);
            Object v = f.get(cameraHandler);
            if (v instanceof Component) {
                Component c = (Component) v;
                if (c.getWidth() > 0 && c.getHeight() > 0) {
                    return new int[]{c.getWidth(), c.getHeight()};
                }
            }
        } catch (Throwable ignored) {
        }
        return new int[]{800, 600};
    }

    // -------------------------------------------------------------- 网格

    private void paintBlenderGrid() {
        glDepthMask(false);
        glEnable(GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 依据当前像素尺度自适应网格间距，缩放时网格会像 Blender 一样换档
        float pixelSize = Math.max(1e-6f, cameraHandler.getPixelSize());
        double exponent = Math.floor(Math.log10(pixelSize));
        float base = (float) Math.pow(10, exponent);
        float spacing = base * 10f;
        float extent = spacing * 20f;
        if (extent <= 0 || !Float.isFinite(extent)) {
            extent = 200f;
            spacing = 10f;
        }

        glBegin(GL_LINES);
        glNormal3f(0, 0, 0);

        // 细网格：低对比灰，避免抢模型的视觉焦点
        glColor4f(0.58f, 0.58f, 0.62f, 0.20f);
        for (float d = spacing; d <= extent; d += spacing) {
            gridLine(extent, d);
            gridLine(extent, -d);
        }

        // 每 10 格一条稍亮的主网格线
        glColor4f(0.66f, 0.66f, 0.70f, 0.38f);
        float major = spacing * 10f;
        for (float d = major; d <= extent; d += major) {
            gridLine(extent, d);
            gridLine(extent, -d);
        }

        // Blender 三轴：X 红 / Y 绿 / Z 蓝
        glColor4f(0.90f, 0.28f, 0.28f, 0.92f);
        glVertex3f(-extent, 0, 0);
        glVertex3f(extent, 0, 0);
        glColor4f(0.36f, 0.78f, 0.38f, 0.92f);
        glVertex3f(0, -extent, 0);
        glVertex3f(0, extent, 0);
        glColor4f(0.32f, 0.52f, 0.95f, 0.80f);
        glVertex3f(0, 0, -extent);
        glVertex3f(0, 0, extent);

        glEnd();
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    /** 画一组平行于 X 和 Y 轴、位于 z=0 平面的网格线。 */
    private static void gridLine(float extent, float d) {
        glVertex3f(-extent, d, 0);
        glVertex3f(extent, d, 0);
        glVertex3f(d, -extent, 0);
        glVertex3f(d, extent, 0);
    }
}
