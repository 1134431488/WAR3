package com.hiveworkshop.rms.ui.application.MenuBar1;

import com.hiveworkshop.rms.editor.model.Material;
import com.hiveworkshop.rms.parsers.blp.BLPHandler;
import com.hiveworkshop.rms.ui.application.ProgramGlobals;
import com.hiveworkshop.rms.ui.browsers.jworldedit.WEString;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class TeamColorMenu extends JMenu {

	public TeamColorMenu() {
		super("Team Color");
		setMnemonic(KeyEvent.VK_C);
		getAccessibleContext().setAccessibleDescription("Allows the user to control team color settings.");
		createTeamColorMenuItems();
	}

	public void updateTeamColors() {
		removeAll();
		createTeamColorMenuItems();
	}

	private void createTeamColorMenuItems() {
		for (int i = 0; i < 25; i++) {
			String colorNumber = String.format("%2s", i).replace(' ', '0');
			try {
				String key = "WESTRING_UNITCOLOR_" + colorNumber;
				String colorName = WEString.getString(key);
				// 中文版 war3 的 WorldEditStrings.txt 通常只定义到 12，13/14/15 查不到时
				// WEString.getString 会原样返回 key，界面就显示 WESTRING_UNITCOLOR_1x。
				// 这三个是 WE 扩展队色：海军蓝 / 青绿 / 紫罗兰。
				if (colorName.equals(key)) {
					switch (colorNumber) {
						case "13": colorName = "海军蓝"; break;
						case "14": colorName = "青绿"; break;
						case "15": colorName = "紫罗兰"; break;
						default: break;
					}
				}
				String iconTexturePath = "ReplaceableTextures\\TeamColor\\TeamColor" + colorNumber + ".blp";
				ImageIcon icon = new ImageIcon(BLPHandler.getGameTex(iconTexturePath));
				JMenuItem menuItem = new JMenuItem(colorName, icon);
				add(menuItem);
				int teamColor = i;
				menuItem.addActionListener(e -> setCurrentTeamColor(teamColor));
			} catch (Exception e) {
				// load failed
				break;
			}
		}
	}

	private void setCurrentTeamColor(int teamColor) {
		Material.teamColor = teamColor;
//		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
//		if (modelPanel != null) {
//			modelPanel.getAnimationViewer().reloadAllTextures();
//			modelPanel.getPerspArea().reloadAllTextures();
//
////			modelPanel.reloadComponentBrowser();
//		}
		ProgramGlobals.getRootWindowUgg().getWindowHandler2().reloadThings();
		ProgramGlobals.getPrefs().setTeamColor(teamColor);
	}
}
