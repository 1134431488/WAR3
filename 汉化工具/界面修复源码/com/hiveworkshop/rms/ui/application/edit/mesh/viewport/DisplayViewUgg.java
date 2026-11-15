package com.hiveworkshop.rms.ui.application.edit.mesh.viewport;

import com.hiveworkshop.rms.ui.application.ProgramGlobals;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.util.ModelDependentView;
import com.hiveworkshop.rms.util.TinyToggleButton;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class DisplayViewUgg extends ModelDependentView {
	DisplayPanel displayPanel;
	ModelPanel modelPanel;
	String name;
	JPanel dudPanel;

	TinyToggleButton renderTextures;
	TinyToggleButton wireFrame;
	TinyToggleButton showNormals;
	TinyToggleButton show3dVerts;
	TinyToggleButton showParticles;
	private boolean particlesVisible = true;


	public DisplayViewUgg(String s) {
		super(s, null, new JPanel());
		this.name = s;
		dudPanel = new JPanel(new MigLayout());
		displayPanel = new DisplayPanel();
		this.setComponent(dudPanel);
		setUpViewAngle();

		// 原标题栏 4 个按钮用 Unicode 几何符号，看不出含义；改为中文文字按钮。
		// 该类在 排除类.txt 中整类排除（setUpViewAngle 的 switch 与视图名比较），
		// 因此中文必须直接写在源码里，不能靠词典注入。
		renderTextures =    getButton("贴图", true, b -> displayPanel.setRenderTextures(b));
		wireFrame =         getButton("线框", false, b -> displayPanel.setWireFrame(b));
		show3dVerts =       getButton("顶点", true, b -> displayPanel.setShow3dVerts(b));
		showNormals =       getButton("法线", false, b -> displayPanel.setShowNormals(b));
		// 底层 RenderModel.setSpawnParticles 早已就绪，只是从来没有 UI 开关。
		showParticles =     getButton("粒子", true, b -> {
			particlesVisible = b;
			setParticlesVisible(b);
		});

		getCustomTitleBarComponents().add(renderTextures);
		getCustomTitleBarComponents().add(wireFrame);
		getCustomTitleBarComponents().add(show3dVerts);
		getCustomTitleBarComponents().add(showNormals);
		getCustomTitleBarComponents().add(showParticles);
	}

	private void setParticlesVisible(boolean visible) {
		if (modelPanel != null && modelPanel.getModelHandler() != null) {
			modelPanel.getModelHandler().getRenderModel().setSpawnParticles(visible);
			modelPanel.getModelHandler().getPreviewRenderModel().setSpawnParticles(visible);
			if (displayPanel != null) {
				displayPanel.repaint();
			}
		}
	}

	private void setUpViewAngle() {
		switch (name) {
			case "Front" -> displayPanel.setFrontView();
			case "Side" -> displayPanel.setLeftView();
			case "Top" -> displayPanel.setTopView();
		}
	}

	@Override
	public DisplayViewUgg setModelPanel(ModelPanel modelPanel) {
		this.modelPanel = modelPanel;
		if (modelPanel == null) {
			this.setComponent(dudPanel);
			displayPanel.setModel(null, null);
		} else {
			displayPanel.setModel(modelPanel.getModelHandler(), modelPanel.getViewportActivityManager());
			displayPanel.setControlsVisible(ProgramGlobals.getPrefs().showVMControls());
			setUpViewAngle();
			this.setComponent(displayPanel);
			// 切换模型后让新建的 RenderModel 与当前按钮状态一致
			setParticlesVisible(particlesVisible);
		}
		System.out.println("name: " + name + ", panel: " + modelPanel);
		return this;
	}

	@Override
	public DisplayViewUgg preferencesUpdated(){
		if(displayPanel != null){
			displayPanel.setControlsVisible(ProgramGlobals.getPrefs().showVMControls());
		}
		return this;
	}

	@Override
	public DisplayViewUgg reload() {
		if (modelPanel != null) {
			displayPanel.reload().repaint();
		}
		return this;
	}

	Color onC = new Color(0x47, 0x72, 0xB3);
	Color offC = new Color(0x3C, 0x3F, 0x41);
	private TinyToggleButton getButton(String text, boolean initial, Consumer<Boolean> boolConsumer){
		TinyToggleButton button = new TinyToggleButton(text, onC, offC, boolConsumer);
		button.setOn(initial);
		boolConsumer.accept(initial);
		return button;

	}
}
