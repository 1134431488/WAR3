package com.hiveworkshop.rms.util;

import javax.swing.*;
import java.awt.*;

public class FramePopup {
	public static JFrame show(JComponent jComponent, JComponent parent, String title) {
		final JFrame frame = new JFrame(title);
		// 内容（如粒子编辑面板）可能高于屏幕：原来只 pack()，超出的部分直接被裁，
		// 底部的「应用 / 添加当前发射器」按钮就点不到。这里套滚动条并按屏幕限高。
		frame.setContentPane(new JScrollPane(jComponent));
		frame.pack();
		clampToScreen(frame);
		frame.setLocationRelativeTo(parent);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setVisible(true);
		return frame;
	}

	private static void clampToScreen(JFrame frame) {
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int maxW = Math.max(480, screen.width - 120);
		int maxH = Math.max(400, screen.height - 120);
		if (frame.getWidth() > maxW || frame.getHeight() > maxH) {
			frame.setSize(Math.min(frame.getWidth(), maxW), Math.min(frame.getHeight(), maxH));
		}
	}

	public static JFrame get(JComponent jComponent, JComponent parent, String title) {
		final JFrame frame = new JFrame(title);
		frame.setContentPane(new JScrollPane(jComponent));
		frame.pack();
		clampToScreen(frame);
		frame.setLocationRelativeTo(parent);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		return frame;
	}
	public static JFrame get(JComponent jComponent, JComponent parent, String title, boolean undecorated) {
		final JFrame frame = new JFrame(title);
		frame.setUndecorated(undecorated);
		frame.setContentPane(jComponent);
		frame.pack();
		frame.setLocationRelativeTo(parent);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		return frame;
	}
}
