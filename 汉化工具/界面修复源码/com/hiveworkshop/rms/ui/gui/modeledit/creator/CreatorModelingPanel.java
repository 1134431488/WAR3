package com.hiveworkshop.rms.ui.gui.modeledit.creator;

import com.hiveworkshop.rms.ui.application.ProgramGlobals;
import com.hiveworkshop.rms.ui.application.actionfunctions.CreateFace;
import com.hiveworkshop.rms.ui.application.edit.animation.Sequence;
import com.hiveworkshop.rms.ui.application.edit.animation.mdlvisripoff.TSpline;
import com.hiveworkshop.rms.ui.application.edit.mesh.ModelEditorManager;
import com.hiveworkshop.rms.ui.application.edit.mesh.viewport.ViewportPopupMenu;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelHandler;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.ui.gui.modeledit.creator.activity.DrawBoneActivity;
import com.hiveworkshop.rms.ui.gui.modeledit.creator.activity.DrawBoxActivity;
import com.hiveworkshop.rms.ui.gui.modeledit.creator.activity.DrawPlaneActivity;
import com.hiveworkshop.rms.ui.gui.modeledit.creator.activity.DrawVertexActivity;
import com.hiveworkshop.rms.ui.gui.modeledit.toolbar.ModeButton2;
import com.hiveworkshop.rms.ui.gui.modeledit.toolbar.ModelEditorActionType3;
import com.hiveworkshop.rms.ui.gui.modeledit.toolbar.ToolbarButtonGroup2;
import com.hiveworkshop.rms.ui.preferences.ProgramPreferences;
import com.hiveworkshop.rms.ui.util.ModeButton;
import com.hiveworkshop.rms.util.TwiCardPanel;
import com.hiveworkshop.rms.util.TwiComboBoxModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CreatorModelingPanel extends JPanel {
	private static final String ANIMATIONBASICS = "ANIMATIONBASICS";

	private ModelEditorManager modelEditorManager;
	private final ProgramPreferences programPreferences;
	private JComboBox<Sequence> animationChooserBox;
	//	private JComboBox<String> modelingOptionsBox;
//	private CardLayout modelingOptionsCardLayout;
	private TwiCardPanel modelingOptionsCardPanel;
	private ModelHandler modelHandler;
	//	private final CardLayout modeCardLayout;
	private final TwiCardPanel modeCardPanel;

	private JPopupMenu contextMenu;

	public CreatorModelingPanel() {
		this.programPreferences = ProgramGlobals.getPrefs();
		setLayout(new MigLayout("ins 0, fillx, wrap 1", "[grow,fill]"));


		animationChooserBox = getAnimationChooserBox();
		JPanel animationPanel = new JPanel(new MigLayout("ins 0, fillx, wrap 1", "[grow,fill]"));
		animationPanel.add(animationChooserBox, "growx");
//		animationPanel.add(getAnimationBasicsPanel(), "wrap, growx, growy");
//		animationPanel.add(getAnimationBasicsPanel(), "wrap, growx");


		modelingOptionsCardPanel = new TwiCardPanel();

		modelingOptionsCardPanel.add(getMeshBasicsPanel(), "网格基础");
		modelingOptionsCardPanel.add(getStandardPrimitivesPanel(), "标准几何体");
//		modelingOptionsCardPanel.add(getStandardPrimitivesPanel(), "Extended Primitives");
//		modelingOptionsCardPanel.add(getStandardPrimitivesPanel(), "Animation Nodes");


		JPanel modelingPanel = new JPanel(new MigLayout("ins 0, fillx, wrap 1", "[grow,fill]"));
		modelingPanel.add(modelingOptionsCardPanel.getCombobox(), "growx");
		modelingPanel.add(modelingOptionsCardPanel, "growx");


		modeCardPanel = new TwiCardPanel();
		modeCardPanel.add(animationPanel, "ANIM");
		modeCardPanel.add(modelingPanel, "MESH");

		JButton popupMenuButton = new JButton("显示操作菜单");
		popupMenuButton.setToolTipText("打开与视口右键相同的真实操作菜单");
		popupMenuButton.addActionListener(e -> showVPPopup(popupMenuButton));

		add(modeCardPanel, "growx, pushx");
		add(popupMenuButton, "growx");
	}

	private JPanel getStandardPrimitivesPanel() {
		JPanel drawPrimitivesPanel = new JPanel(new MigLayout("ins 0, gap 0, wrap 1, fillx", "[grow,fill]"));
		drawPrimitivesPanel.setBorder(BorderFactory.createTitledBorder("绘制"));
		drawPrimitivesPanel.add(getModeButton("平面", this::drawPlane), "growx");
		drawPrimitivesPanel.add(getModeButton("立方体", this::drawBox), "growx");

		JPanel standardPrimitivesPanel = new JPanel(new MigLayout("fillx, ins 0, gap 0, wrap 1", "[grow,fill]"));
		standardPrimitivesPanel.add(drawPrimitivesPanel, "growx");
		return standardPrimitivesPanel;
	}

	private ModeButton getModeButton(String text, Consumer<ModeButton> action) {
		ModeButton modeButton = new ModeButton(text);
		modeButton.addActionListener(e -> action.accept(modeButton));
		return modeButton;
	}

	private JComboBox<Sequence> getAnimationChooserBox() {
		final TwiComboBoxModel<Sequence> animationChooserBoxModel = new TwiComboBoxModel<>();
		JComboBox<Sequence> animationChooserBox = new JComboBox<>(animationChooserBoxModel);
//		animationChooserBox.setPrototypeDisplayValue(new Animation("temporary prototype animation", 0, 1));
		animationChooserBox.addItemListener(this::chooseAnimation);
		return animationChooserBox;
	}

	private void chooseAnimation(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Sequence selectedItem = (Sequence) animationChooserBox.getSelectedItem();
			if (selectedItem != null && modelHandler != null) {
				modelHandler.getEditTimeEnv().setSequence(selectedItem);
			}
		}
	}

	public JPanel getMeshBasicsPanel() {
		JPanel meshBasicsPanel = new JPanel(new MigLayout("wrap 1, ins 0, fillx", "[grow,fill]"));
		meshBasicsPanel.add(getDrawToolsPanel(), "growx");
		// 原来这两块被注释/隐藏，导致「建模」页只有 4 个按钮、下面一大片空白
		meshBasicsPanel.add(getEditToolsPanel(), "growx");
		meshBasicsPanel.add(getMeshToolsPanel(), "growx");
		return meshBasicsPanel;

	}

	private JPanel getEditToolsPanel() {
		JPanel editToolsPanel = new JPanel(new MigLayout("wrap 1, gap 0, ins 0, fill", "[grow]", ""));
		editToolsPanel.setBorder(BorderFactory.createTitledBorder("变换工具"));

		var group = ProgramGlobals.getActionTypeGroup();
		if (group != null) {
			for (ModeButton2 modeButton2 : group.getModeButtons()) {
				editToolsPanel.add(modeButton2);
			}
		}
		return editToolsPanel;
	}

	/** 把已实现但一直没暴露的建模操作做成按钮网格。 */
	private JPanel getMeshToolsPanel() {
		JPanel panel = new JPanel(new MigLayout("wrap 2, ins 2, fill", "[grow,fill][grow,fill]"));
		panel.setBorder(BorderFactory.createTitledBorder("常用编辑"));
		addTool(panel, "焊点", () -> com.hiveworkshop.rms.ui.application.actionfunctions.WeldVerts.doWeld(modelHandler));
		addTool(panel, "翻转法线", () -> com.hiveworkshop.rms.ui.application.actionfunctions.FlipNormals.insideOutNormals(modelHandler));
		addTool(panel, "简化几何", () -> com.hiveworkshop.rms.ui.application.actionfunctions.SimplifyGeometry.simplifyGeometry(modelHandler));
		addTool(panel, "由所选创建面", () -> CreateFace.createFace(modelHandler));
		addTool(panel, "删除所选", () -> com.hiveworkshop.rms.ui.application.actionfunctions.Delete.deleteActionRes());
		addTool(panel, "添加空丝带", () -> com.hiveworkshop.rms.ui.application.AddParticlePanel.addEmptyRibbon());
		return panel;
	}

	private void addTool(JPanel panel, String text, Runnable action) {
		JButton button = new JButton(text);
		button.addActionListener(e -> {
			try {
				action.run();
			} catch (Throwable t) {
				JOptionPane.showMessageDialog(panel, "操作失败：" + t);
			}
		});
		panel.add(button, "growx");
	}

	private JPanel getDrawToolsPanel() {
		JPanel drawToolsPanel = new JPanel(new MigLayout("wrap 1, gap 0, ins 0, fillx", "[grow,fill]"));
		drawToolsPanel.setBorder(BorderFactory.createTitledBorder("绘制"));

		drawToolsPanel.add(getModeButton2("顶点", this::addVertex), "growx");
		ModeButton2 faceButton = getModeButton2("由所选内容创建面 (Ctrl+Shift+F)", this::createFace);
		faceButton.setToolTipText("选中同一几何体中恰好三个顶点后创建面。快捷键：Ctrl+Shift+F（Ctrl+F 已用于树搜索，F 保留用于选择面）");
		drawToolsPanel.add(faceButton, "growx");
		drawToolsPanel.add(getModeButton2("骨骼", this::addBone), "growx");
		return drawToolsPanel;
	}

	private ModeButton2 getModeButton2(String text, Consumer<ModeButton2> action) {
		ModeButton2 modeButton = new ModeButton2(text, programPreferences.getActiveColor1(), programPreferences.getActiveColor2());
		modeButton.addActionListener(e -> action.accept(modeButton));
		return modeButton;
	}

	private void createFace(ModeButton2 modeButton) {
		CreateFace.createFace(modelHandler);
	}

	private void addBone(ModeButton2 modeButton) {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null) {
			DrawBoneActivity activity = new DrawBoneActivity(modelHandler, modelEditorManager);
			modeButton.setColors(programPreferences.getActiveColor1(), programPreferences.getActiveColor2());
			modelPanel.changeActivity(activity);
		}
	}

	private void addVertex(ModeButton2 modeButton) {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null) {
			DrawVertexActivity activity = new DrawVertexActivity(modelHandler, modelEditorManager, modelPanel.getEditorActionType());
			modeButton.setColors(programPreferences.getActiveColor1(), programPreferences.getActiveColor2());
			modelPanel.changeActivity(activity);
		}
	}

	private void drawBox(ModeButton modeButton) {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null) {
			DrawBoxActivity activity = new DrawBoxActivity(modelHandler, modelEditorManager, 1, 1, 1);
			modeButton.setColors(programPreferences.getActiveColor1(), programPreferences.getActiveColor2());
			modelPanel.changeActivity(activity);
		}
	}

	private void drawPlane(ModeButton modeButton) {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null) {
			DrawPlaneActivity activity = new DrawPlaneActivity(modelHandler, modelEditorManager, 1, 1, 1);
			modeButton.setColors(programPreferences.getActiveColor1(), programPreferences.getActiveColor2());
			modelPanel.changeActivity(activity);
		}
	}

	public JPanel getAnimationBasicsPanel() {
		JPanel animationBasicsPanel = new JPanel(new MigLayout("fill"));

		ToolbarButtonGroup2<ModelEditorActionType3> actionTypeGroup = ProgramGlobals.getActionTypeGroup();

//		JPanel editToolsPanel = new JPanel(new MigLayout("debug, fill"));
		JPanel editToolsPanel = new JPanel(new MigLayout("fill"));
		editToolsPanel.setBorder(BorderFactory.createTitledBorder("Manipulate"));
		// 上一轮修复 NoSuchFieldError 时枚举字段名已还原为英文，源码里的中文名同步改回
		editToolsPanel.add(actionTypeGroup.getModeButton(ModelEditorActionType3.TRANSLATION), "wrap");
		editToolsPanel.add(actionTypeGroup.getModeButton(ModelEditorActionType3.ROTATION), "wrap");
		editToolsPanel.add(actionTypeGroup.getModeButton(ModelEditorActionType3.SCALING), "wrap");
		editToolsPanel.add(actionTypeGroup.getModeButton(ModelEditorActionType3.SQUAT), "wrap");

		animationBasicsPanel.add(editToolsPanel, "growx, growy, wrap");

		boolean temp = false;
//		boolean temp = true;
		if (temp) {
			TSpline tSpline = new TSpline();
			animationBasicsPanel.add(tSpline, "wrap, growy");
		}

//		editToolsPanel.add(new JLabel("UGG"), "wrap");
		return animationBasicsPanel;
	}

	private void showVPPopup(JButton button) {
		if (contextMenu != null) {
			contextMenu.show(button, 0, 0);
		}
	}

	public void setAnimationModeState(boolean animationModeState) {
		modeCardPanel.show(animationModeState ? "ANIM" : "MESH");
//		if (animationModeState) {
////			modelingOptionsCardLayout.show(modelingOptionsCardPanel, ANIMATIONBASICS);
//			modelingOptionsCardPanel.show(ANIMATIONBASICS);
//		} else {
////			modelingOptionsCardLayout.show(modelingOptionsCardPanel, modelingOptionsBox.getSelectedItem().toString());
////			modelingOptionsCardPanel.show(modelingOptionsBox.getSelectedItem().toString());
//			modelingOptionsCardPanel.show("Mesh Basics");
//		}
	}

	public void setModelPanel(ModelPanel modelPanel) {
		if (modelPanel != null) {
			this.modelHandler = modelPanel.getModelHandler();
			this.modelEditorManager = modelPanel.getModelEditorManager();
//			contextMenu = new ViewportPopupMenu(viewportListener.getViewport(), ProgramGlobals.getMainPanel(), modelPanel.getModelHandler(), modelPanel.getModelEditorManager());
			contextMenu = new ViewportPopupMenu(null, ProgramGlobals.getMainPanel(), modelPanel.getModelHandler(), modelPanel.getModelEditorManager());
			reloadAnimationList();
		} else {
			this.modelHandler = null;
			this.modelEditorManager = null;
			contextMenu = null;
		}
	}

	public void reloadAnimationList() {
		Sequence selectedItem = (Sequence) animationChooserBox.getSelectedItem();
//		animationChooserBoxModel.getSelectedItem()
		List<Sequence> allSequences = new ArrayList<>();
		allSequences.addAll(modelHandler.getModel().getAnims());
		allSequences.addAll(modelHandler.getModel().getGlobalSeqs());
		TwiComboBoxModel<Sequence> animationChooserBoxModel = new TwiComboBoxModel<>(allSequences);
		animationChooserBox.setModel(animationChooserBoxModel);
//		animationChooserBoxModel.removeAllElements();

//		EditableModel model = modelHandler.getModel();
//		for (Animation animation : model.getAnims()) {
//			animationChooserBoxModel.addElement(animation);
//		}

//		for (GlobalSeq globalSeq : model.getGlobalSeqs()) {
//			animationChooserBoxModel.addElement(globalSeq);
//		}
		if (animationChooserBoxModel.getSize() >= 1) {
			if (selectedItem != null && allSequences.contains(selectedItem)) {
//			animationChooserBoxModel.setSelectedItem(selectedItem);
				animationChooserBox.setSelectedItem(selectedItem);
			} else {
//				animationChooserBoxModel.setSelectedItem(0);
				animationChooserBox.setSelectedIndex(0);
			}
		}
	}
}
