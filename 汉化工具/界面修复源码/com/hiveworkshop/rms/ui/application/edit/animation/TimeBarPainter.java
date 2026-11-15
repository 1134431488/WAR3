package com.hiveworkshop.rms.ui.application.edit.animation;

import com.hiveworkshop.rms.ui.application.ProgramGlobals;
import com.hiveworkshop.rms.ui.gui.modeledit.selection.SelectionItemTypes;

import java.awt.*;

/**
 * 时间轴刻度绘制：刻度间隔随窗口宽度自适应，避免标签重叠；
 * 增加当前时间线、帧号提示与明暗主题配色。
 */
public class TimeBarPainter {
	private static final int SLIDER_SIDE_BUTTON_SIZE = 15;
	private static final int SLIDER_TIME_BUTTON_SIZE = 50;
	private static final int SLIDING_TIME_CHOOSER_WIDTH = SLIDER_TIME_BUTTON_SIZE + (SLIDER_SIDE_BUTTON_SIZE * 2);
	private static final int VERTICAL_TICKS_HEIGHT = 10;
	private static final int VERTICAL_SLIDER_HEIGHT = 15;
	private static final int SIDE_OFFSETS = SLIDING_TIME_CHOOSER_WIDTH / 2;
	private static final int MIN_LABEL_SPACING = 62;

	private static final int[] STEPS = {1, 2, 5, 10, 15, 20, 25, 30, 50, 60, 100, 120, 150, 200, 250, 300, 600, 1200, 1800, 3000};

	private int tickStep = 300;
	private TimeEnvironmentImpl timeEnvironment;
	private final TimeSlider timeSlider;

	public TimeBarPainter(TimeSlider timeSlider) {
		this.timeSlider = timeSlider;
	}

	public void setTickStep(int tickStep) {
		this.tickStep = tickStep;
	}

	public TimeBarPainter setTimeEnvironment(TimeEnvironmentImpl timeEnvironment) {
		this.timeEnvironment = timeEnvironment;
		return this;
	}

	public void drawTimeBar(Graphics g, int width) {
		drawTimeBarBase(g, width);
		int maxX = width - SLIDING_TIME_CHOOSER_WIDTH;
		if (maxX <= 0 || timeEnvironment == null) {
			if (timeEnvironment == null) {
				g.setColor(getTextColor());
				g.drawString("时间轴：未载入动画", 8, VERTICAL_SLIDER_HEIGHT + 14);
			}
			return;
		}
		g.setColor(getTickColor());
		drawTimeTicks(g);
		drawCurrentTime(g);
	}

	public void drawTimeBarBase(Graphics g, int width) {
		Color base;
		if (ProgramGlobals.getSelectionItemType() == SelectionItemTypes.ANIMATE) {
			base = new Color(0x8E, 0x2B, 0x2B);
		} else if (isDarkTheme()) {
			base = new Color(0x2B, 0x2D, 0x30);
		} else {
			base = new Color(0xC9, 0xCD, 0xD3);
		}
		g.setColor(base);
		g.fillRect(0, 0, width, VERTICAL_SLIDER_HEIGHT);
		g.setColor(isDarkTheme() ? new Color(0x18, 0x18, 0x18) : new Color(0x9A, 0x9A, 0x9A));
		g.drawLine(0, VERTICAL_SLIDER_HEIGHT - 1, width, VERTICAL_SLIDER_HEIGHT - 1);
	}

	public void drawTimeTicks(Graphics g) {
		if (timeEnvironment == null) return;
		FontMetrics fontMetrics = g.getFontMetrics(g.getFont());
		final int timeSpan = timeEnvironment.getLength();
		if (timeSpan <= 0) return;

		int step = chooseStep(timeSpan);
		((Graphics2D) g).setStroke(new BasicStroke(1));
		// 次刻度
		int minorStep = Math.max(1, step / 5);
		for (int time = minorStep; time < timeSpan; time += minorStep) {
			if (time % step == 0) continue;
			int x = computeXFromTime(time);
			g.drawLine(x, VERTICAL_SLIDER_HEIGHT, x, VERTICAL_SLIDER_HEIGHT + (VERTICAL_TICKS_HEIGHT / 2));
		}
		// 主刻度 + 标签
		((Graphics2D) g).setStroke(new BasicStroke(1.4f));
		drawMajorTick(g, fontMetrics, 0, step);
		for (int time = step; time < timeSpan; time += step) {
			drawMajorTick(g, fontMetrics, time, step);
		}
		drawMajorTick(g, fontMetrics, timeSpan, step);
	}

	private int chooseStep(int timeSpan) {
		int maxX = Math.max(1, timeSlider.getMaxX());
		double pixelsPerTime = maxX / (double) timeSpan;
		for (int candidate : STEPS) {
			if (candidate * pixelsPerTime >= MIN_LABEL_SPACING) return candidate;
		}
		if (tickStep > 0) return tickStep;
		return Math.max(1, timeSpan / 8);
	}

	private void drawMajorTick(Graphics g, FontMetrics fontMetrics, int time, int step) {
		int xCoordPixels = computeXFromTime(time);
		g.setColor(getTickColor());
		int lineEnd = VERTICAL_SLIDER_HEIGHT + VERTICAL_TICKS_HEIGHT;
		g.drawLine(xCoordPixels, VERTICAL_SLIDER_HEIGHT, xCoordPixels, lineEnd);

		String tickLabel = String.valueOf(time);
		int labelWidth = fontMetrics.stringWidth(tickLabel);
		int labelX = xCoordPixels - (labelWidth / 2);
		labelX = Math.max(2, Math.min(labelX, timeSlider.getMaxX() + SLIDING_TIME_CHOOSER_WIDTH - labelWidth - 2));
		g.setColor(getTextColor());
		g.drawString(tickLabel, labelX, lineEnd + fontMetrics.getAscent() + 2);
	}

	/** 当前时间竖线与帧号提示。 */
	private void drawCurrentTime(Graphics g) {
		int current = timeEnvironment.getEnvTrackTime();
		int x = computeXFromTime(current);
		Graphics2D g2 = (Graphics2D) g;
		Stroke old = g2.getStroke();
		g2.setStroke(new BasicStroke(1.6f));
		g2.setColor(new Color(0x47, 0x72, 0xB3));
		g2.drawLine(x, 0, x, VERTICAL_SLIDER_HEIGHT + VERTICAL_TICKS_HEIGHT + 2);
		g2.setStroke(old);

		// 顶部三角指示器（直角风格）
		g2.setColor(new Color(0x47, 0x72, 0xB3));
		g2.fillRect(x - 1, 0, 3, 5);

		String text = "第 " + current + " 帧 / " + timeEnvironment.getLength();
		FontMetrics fm = g2.getFontMetrics(g2.getFont());
		int textWidth = fm.stringWidth(text);
		int boxHeight = fm.getAscent() + 6;
		int boxX = Math.min(Math.max(4, x + 8), Math.max(4, timeSlider.getMaxX() + SLIDING_TIME_CHOOSER_WIDTH - textWidth - 10));
		g2.setColor(new Color(0x47, 0x72, 0xB3));
		g2.fillRect(boxX, 2, textWidth + 10, boxHeight);
		g2.setColor(Color.WHITE);
		g2.drawString(text, boxX + 5, 2 + fm.getAscent() + 2);
	}

	private boolean isDarkTheme() {
		// 时间轴统一成与工具栏一致的深色：0x2B2D30 底 / 0xB8C0CA 刻度 /
		// 0x4772B3 当前帧 / 0xE6E6E6 文字。默认 ALUMINIUM 亮色主题原先会
		// 走浅灰 0xC9CDD3 分支，与全局深色按钮割裂且观感差。
		// 本方法 private、仅本类 4 处调用，恒 true 不会外溢到其它面板。
		return true;
	}

	private Color getTickColor() {
		return isDarkTheme() ? new Color(0xB8, 0xC0, 0xCA) : new Color(0x44, 0x44, 0x44);
	}

	private Color getTextColor() {
		return isDarkTheme() ? new Color(0xE6, 0xE6, 0xE6) : new Color(0x22, 0x22, 0x22);
	}

	private int computeXFromTime(int time) {
		double timeRatio = time / (double) timeEnvironment.getLength();
		return (int) (timeSlider.getMaxX() * timeRatio) + SIDE_OFFSETS;
	}
}
