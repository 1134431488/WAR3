package com.hiveworkshop.rms.ui.application.model.nodepanels;

import com.hiveworkshop.rms.editor.actions.nodes.ChangeParticleTextureAction;
import com.hiveworkshop.rms.editor.model.Bitmap;
import com.hiveworkshop.rms.editor.model.ParticleEmitter2;
import com.hiveworkshop.rms.editor.model.animflag.FloatAnimFlag;
import com.hiveworkshop.rms.parsers.mdlx.mdl.MdlUtils;
import com.hiveworkshop.rms.ui.application.ProgramGlobals;
import com.hiveworkshop.rms.ui.application.edit.ModelStructureChangeListener;
import com.hiveworkshop.rms.ui.application.model.editors.FloatValuePanel;
import com.hiveworkshop.rms.ui.application.tools.ParticleEditPanel;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelHandler;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelTextureThings;
import com.hiveworkshop.rms.util.FramePopup;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;

public class ComponentParticle2Panel extends ComponentIdObjectPanel<ParticleEmitter2> {

	private final FloatValuePanel widthPanel;
	private final FloatValuePanel lengthPanel;
	private final FloatValuePanel latitudePanel;
	private final FloatValuePanel variationPanel;
	private final FloatValuePanel speedPanel;
	private final FloatValuePanel gravityPanel;
	private final FloatValuePanel emissionPanel;
	private final FloatValuePanel visibilityPanel;


	private JComboBox<Bitmap> textureChooser = new JComboBox<>();
	/** 刷新贴图下拉框时不要把它当成用户手动换贴图，避免污染撤销栈。 */
	private boolean updatingTexture = false;

	// ---- 第十轮：时间控制条 ----
	private JComboBox<Object> sequenceCombo;
	private JSlider timeSlider;
	private JLabel timeLabel;
	private JToggleButton playToggle;
	private Timer playTimer;
	private boolean updatingTime = false;

	public ComponentParticle2Panel(ModelHandler modelHandler) {
		super(modelHandler);

//		textureChooser.setRenderer(new TextureListRenderer(modelHandler.getModel()));
		textureChooser.setRenderer(ModelTextureThings.getTextureListRenderer());
		textureChooser.addItemListener(e -> changeTexture(e));
		topPanel.add(textureChooser);

		JButton editParticle = new JButton("编辑粒子完整参数");
		editParticle.addActionListener(e -> viewParticlePanel());
		topPanel.add(editParticle, "spanx, growx, wrap");

		// 标题直接写中文：这些词同时是 MdlUtils.TOKEN_* 的值，若靠词典注入会把
		// find("Gravity") 之类的内部键一起翻掉。源码写中文后常量池里中文标题与
		// 英文 token 是两个独立 UTF8，互不干扰（updatePanels 里仍用 TOKEN_* 常量）。
		widthPanel = new FloatValuePanel(modelHandler, "宽度");
		lengthPanel = new FloatValuePanel(modelHandler, "长度");
		latitudePanel = new FloatValuePanel(modelHandler, "发射张角");
		variationPanel = new FloatValuePanel(modelHandler, "变化");
		speedPanel = new FloatValuePanel(modelHandler, "速度");
		gravityPanel = new FloatValuePanel(modelHandler, "重力");
		emissionPanel = new FloatValuePanel(modelHandler, "发射速率");
		visibilityPanel = new FloatValuePanel(modelHandler, "可见性");

		// 第十轮：时间控制条（参考 Blender 粒子页的时间轴直觉）。
		// 拖动滑杆直接驱动全局 TimeEnvironment，视口里的粒子实时播到那一帧；
		// 配合每行的「转为动画」即可直观看到关键帧效果。
		JPanel timeRow = buildTimeRow();
		topPanel.add(timeRow, "spanx, growx, wrap");

		// 第十轮：参数改为两列网格。原先 8 个 FloatValuePanel 纵向单列堆叠，
		// 宽屏下右侧整片空白（用户反馈）。
		JPanel paramGrid = new JPanel(new MigLayout("ins 0, gap 6", "[grow][grow]"));
		paramGrid.setOpaque(false);
		paramGrid.add(emissionPanel, "growx");
		paramGrid.add(speedPanel, "growx, wrap");
		paramGrid.add(gravityPanel, "growx");
		paramGrid.add(variationPanel, "growx, wrap");
		paramGrid.add(widthPanel, "growx");
		paramGrid.add(lengthPanel, "growx, wrap");
		paramGrid.add(latitudePanel, "growx");
		paramGrid.add(visibilityPanel, "growx, wrap");
		// 原来用 add(...) 加到 this，可见性面板被挤到父类变换面板之后；
		// 改成整体加入 topPanel 与其他参数排在一起。
		topPanel.add(paramGrid, "spanx, growx, wrap");

		// 第十一轮：面板被切换 / 隐藏时停止播放 Timer，避免后台持续 repaint 泄漏
		addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentHidden(java.awt.event.ComponentEvent e) {
				stopTimer();
			}
		});
	}

	private void stopTimer() {
		if (playTimer != null && playTimer.isRunning()) {
			playTimer.stop();
			playToggle.setSelected(false);
		}
	}

	/** 构建时间控制条：动画序列下拉 + 时间滑杆 + 当前帧标签 + 播放开关。 */
	private JPanel buildTimeRow() {
		JPanel row = new JPanel(new MigLayout("ins 0, gap 6", "[][][grow][][]"));
		row.setOpaque(false);

		sequenceCombo = new JComboBox<>();
		sequenceCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				String label;
				if (value instanceof com.hiveworkshop.rms.ui.application.edit.animation.Sequence) {
					com.hiveworkshop.rms.ui.application.edit.animation.Sequence seq =
							(com.hiveworkshop.rms.ui.application.edit.animation.Sequence) value;
					label = seq.toString();
				} else {
					label = "动画（无）";
				}
				return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
			}
		});
		sequenceCombo.setPreferredSize(new Dimension(150, 24));
		sequenceCombo.setToolTipText("选择动画后拖动滑杆即可逐帧查看粒子效果");
		row.add(new JLabel("时间："), "");
		row.add(sequenceCombo, "");

		timeSlider = new JSlider(0, 1000, 0);
		timeSlider.setToolTipText("拖动查看当前动画对应帧的粒子表现");
		timeSlider.addChangeListener(e -> {
			if (updatingTime || sequenceCombo.getSelectedItem() == null) {
				return;
			}
			int t = timeSlider.getValue();
			timeEnvironment().setAnimationTime(t);
			timeLabel.setText(t + " / " + timeSlider.getMaximum());
			ProgramGlobals.getMainPanel().repaintSelfAndChildren();
		});
		row.add(timeSlider, "growx");

		timeLabel = new JLabel("0");
		row.add(timeLabel, "");

		playToggle = new JToggleButton("▶");
		playToggle.setToolTipText("播放 / 暂停当前动画");
		playToggle.addActionListener(e -> togglePlay());
		row.add(playToggle, "");

		playTimer = new Timer(33, ev -> {
			com.hiveworkshop.rms.ui.application.edit.animation.TimeEnvironmentImpl env = timeEnvironment();
			int max = Math.max(1, timeSlider.getMaximum());
			int next = (timeSlider.getValue() + 1) % (max + 1);
			updatingTime = true;
			try {
				timeSlider.setValue(next);
			} finally {
				updatingTime = false;
			}
			env.setAnimationTime(next);
			timeLabel.setText(next + " / " + max);
			ProgramGlobals.getMainPanel().repaintSelfAndChildren();
		});

		sequenceCombo.addItemListener(e -> {
			if (e.getStateChange() != ItemEvent.SELECTED || updatingTime) {
				return;
			}
			applySelectedSequence();
		});

		return row;
	}

	private com.hiveworkshop.rms.ui.application.edit.animation.TimeEnvironmentImpl timeEnvironment() {
		return modelHandler.getEditTimeEnv();
	}

	private void togglePlay() {
		if (playToggle.isSelected() && sequenceCombo.getSelectedItem() != null) {
			playTimer.start();
		} else {
			playTimer.stop();
		}
	}

	/** 把下拉当前项应用到 TimeEnvironment，并让滑杆范围匹配动画长度。 */
	private void applySelectedSequence() {
		updatingTime = true;
		try {
			Object selected = sequenceCombo.getSelectedItem();
			com.hiveworkshop.rms.ui.application.edit.animation.Sequence seq =
					selected instanceof com.hiveworkshop.rms.ui.application.edit.animation.Sequence
							? (com.hiveworkshop.rms.ui.application.edit.animation.Sequence) selected
							: null;
			timeEnvironment().setSequence(seq);
			int length = seq != null ? Math.max(1, seq.getLength()) : 1000;
			timeSlider.setMaximum(length);
			int t = Math.min(timeEnvironment().getAnimationTime(), length);
			timeSlider.setValue(t);
			timeLabel.setText(t + " / " + length);
			if (playTimer.isRunning()) {
				playTimer.stop();
				playToggle.setSelected(false);
			}
		} finally {
			updatingTime = false;
		}
		ProgramGlobals.getMainPanel().repaintSelfAndChildren();
	}

	/** 刷新时间条的可选动画列表（模型序列变化后调用 updatePanels 时触发）。 */
	private void reloadSequences() {
		updatingTime = true;
		try {
			Object prev = sequenceCombo.getSelectedItem();
			DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
			model.addElement(null);
			for (com.hiveworkshop.rms.ui.application.edit.animation.Sequence seq : modelHandler.getModel().getAllSequences()) {
				model.addElement(seq);
			}
			sequenceCombo.setModel(model);
			boolean restored = false;
			for (int i = 0; i < sequenceCombo.getItemCount(); i++) {
				if (sequenceCombo.getItemAt(i) == prev) {
					sequenceCombo.setSelectedIndex(i);
					restored = true;
					break;
				}
			}
			if (!restored) {
				sequenceCombo.setSelectedIndex(0);
			}
			applySelectedSequence();
		} finally {
			updatingTime = false;
		}
	}

	@Override
	public void updatePanels() {
		widthPanel.reloadNewValue((float) idObject.getWidth(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_WIDTH), idObject, MdlUtils.TOKEN_WIDTH, idObject::setWidth);
		lengthPanel.reloadNewValue((float) idObject.getLength(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_LENGTH), idObject, MdlUtils.TOKEN_LENGTH, idObject::setLength);
		latitudePanel.reloadNewValue((float) idObject.getLatitude(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_LATITUDE), idObject, MdlUtils.TOKEN_LATITUDE, idObject::setLatitude);
		variationPanel.reloadNewValue((float) idObject.getVariation(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_VARIATION), idObject, MdlUtils.TOKEN_VARIATION, idObject::setVariation);
		speedPanel.reloadNewValue((float) idObject.getSpeed(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_SPEED), idObject, MdlUtils.TOKEN_SPEED, idObject::setSpeed);
		gravityPanel.reloadNewValue((float) idObject.getGravity(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_GRAVITY), idObject, MdlUtils.TOKEN_GRAVITY, idObject::setGravity);
		emissionPanel.reloadNewValue((float) idObject.getEmissionRate(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_EMISSION_RATE), idObject, MdlUtils.TOKEN_EMISSION_RATE, idObject::setEmissionRate);
		visibilityPanel.reloadNewValue(1f, idObject.getVisibilityFlag(), idObject, MdlUtils.TOKEN_VISIBILITY, null);
		updateTextureChooser();
		reloadSequences();
	}

	private void viewParticlePanel() {
		ParticleEditPanel panel = new ParticleEditPanel(idObject);
		FramePopup.show(panel, null, "编辑 " + idObject.getName());
	}

	private void updateTextureChooser() {
		// setModel / setSelectedItem 会触发 ItemEvent，刷新期间必须屏蔽，
		// 否则仅仅切换选中节点就会往撤销栈压入一个「改贴图」动作。
		updatingTexture = true;
		try {
			DefaultComboBoxModel<Bitmap> bitmapModel = new DefaultComboBoxModel<>(modelHandler.getModel().getTextures().toArray(new Bitmap[0]));
			bitmapModel.setSelectedItem(idObject.getTexture());
			textureChooser.setModel(bitmapModel);
		} finally {
			updatingTexture = false;
		}
	}

	private void changeTexture(ItemEvent e) {
		if (updatingTexture || e.getStateChange() != ItemEvent.SELECTED) {
			return;
		}
		int index = textureChooser.getSelectedIndex();
		if (index < 0) {
			return; // 贴图列表为空时不构造动作，避免 null 引发 NPE
		}
		Bitmap itemAt = textureChooser.getItemAt(index);
		ChangeParticleTextureAction action = new ChangeParticleTextureAction(idObject, itemAt, ModelStructureChangeListener.changeListener);
		modelHandler.getUndoManager().pushAction(action.redo());
	}
}
