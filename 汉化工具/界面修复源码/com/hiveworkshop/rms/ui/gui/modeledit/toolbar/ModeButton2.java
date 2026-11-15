package com.hiveworkshop.rms.ui.gui.modeledit.toolbar;

import javax.swing.*;
import java.awt.*;

/**
 * 与工具栏 ToolbarButton2.FlatTextButton 完全一致的「深底 + 白字 + 直角」按钮。
 * 原实现用 prefs 里的浅红/深红棕渐变，和工具栏那套深色按钮格格不入；
 * 现在统一为同一配色（激活态深蓝），且不依赖 setBackground，避免主题白字不可见。
 */
public class ModeButton2 extends JButton {
	private static final Color BG = new Color(0x3C, 0x3F, 0x41);
	private static final Color BG_HOVER = new Color(0x4A, 0x4E, 0x51);
	private static final Color BG_PRESSED = new Color(0x2E, 0x31, 0x33);
	private static final Color BG_ACTIVE = new Color(0x47, 0x72, 0xB3);
	private static final Color FG = new Color(0xDC, 0xDC, 0xDC);
	private static final Color FG_ACTIVE = Color.WHITE;
	private static final Color EDGE = new Color(0x18, 0x18, 0x18);

	private boolean activeLook;
	private Color activeColor1;
	private Color activeColor2;

	public ModeButton2(final String s, Color activeColor1, Color activeColor2) {
		super(s);
		this.activeColor1 = activeColor1;
		this.activeColor2 = activeColor2;
		setFocusPainted(false);
		setContentAreaFilled(false);
		setOpaque(false);
		setRolloverEnabled(true);
		setForeground(FG);
		setBorder(BorderFactory.createLineBorder(EDGE));
		setMargin(new Insets(3, 9, 3, 9));
		setFont(getFont().deriveFont(Font.BOLD, 12f));
	}

	@Override
	protected void paintComponent(final Graphics g) {
		ButtonModel model = getModel();
		Color fill = activeLook ? BG_ACTIVE
				: model.isPressed() ? BG_PRESSED
				: model.isRollover() ? BG_HOVER : BG;
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setColor(fill);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setColor(EDGE);
		g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		g2.dispose();
		super.paintComponent(g);
	}

	public void setActive(boolean active) {
		activeLook = active;
		setForeground(active ? FG_ACTIVE : FG);
		repaint();
	}

	/** 保留旧接口：构造时传入的渐变色不再使用，统一走工具栏深色。 */
	public void setColors(Color activeColor1, Color activeColor2) {
		this.activeColor1 = activeColor1;
		this.activeColor2 = activeColor2;
	}

	public boolean isColorModeActive() {
		return activeLook;
	}
}
