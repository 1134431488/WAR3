package com.hiveworkshop.rms.ui.application.viewer;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

/**
 * 只读诊断探针：记录鼠标按下事件落在了哪个组件上（含父链与边界几何）。
 * 通过 BlenderLauncher 注入，不影响任何业务逻辑。
 */
public final class MouseEventSpy implements AWTEventListener {
	private static boolean installed = false;

	public static void install() {
		if (installed) return;
		installed = true;
		Toolkit.getDefaultToolkit().addAWTEventListener(new MouseEventSpy(),
				AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
		System.out.println("[Spy] 鼠标事件探针已安装");
	}

	@Override
	public void eventDispatched(AWTEvent event) {
		if (!(event instanceof MouseEvent)) return;
		MouseEvent me = (MouseEvent) event;
		if (me.getID() != MouseEvent.MOUSE_PRESSED) return;
		if ((me.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0) return;
		Component c = me.getComponent();
		if (c == null) return;
		StringBuilder sb = new StringBuilder();
		sb.append("[Spy] press @(").append(me.getXOnScreen()).append(',').append(me.getYOnScreen())
				.append(") chain:");
		Component p = c;
		int depth = 0;
		while (p != null && depth < 12) {
			Rectangle b = p.getBounds();
			sb.append(" <").append(p.getClass().getSimpleName())
					.append(" b=").append(b.x).append(',').append(b.y).append(',').append(b.width).append('x').append(b.height)
					.append('>');
			p = p.getParent();
			depth++;
		}
		System.out.println(sb);
	}
}
