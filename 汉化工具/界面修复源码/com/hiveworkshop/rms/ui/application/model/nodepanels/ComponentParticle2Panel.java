package com.hiveworkshop.rms.ui.application.model.nodepanels;

import com.hiveworkshop.rms.editor.actions.nodes.ChangeParticleTextureAction;
import com.hiveworkshop.rms.editor.model.Bitmap;
import com.hiveworkshop.rms.editor.model.ParticleEmitter2;
import com.hiveworkshop.rms.editor.model.animflag.FloatAnimFlag;
import com.hiveworkshop.rms.parsers.mdlx.mdl.MdlUtils;
import com.hiveworkshop.rms.ui.application.edit.ModelStructureChangeListener;
import com.hiveworkshop.rms.ui.application.model.editors.FloatValuePanel;
import com.hiveworkshop.rms.ui.application.tools.ParticleEditPanel;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelHandler;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelTextureThings;
import com.hiveworkshop.rms.util.FramePopup;

import javax.swing.*;
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
		topPanel.add(emissionPanel, "spanx, growx, wrap");
		topPanel.add(speedPanel, "spanx, growx, wrap");
		topPanel.add(gravityPanel, "spanx, growx, wrap");
		topPanel.add(variationPanel, "spanx, growx, wrap");
		topPanel.add(widthPanel, "spanx, growx, wrap");
		topPanel.add(lengthPanel, "spanx, growx, wrap");
		topPanel.add(latitudePanel, "spanx, growx, wrap");
		// 原来用 add(...) 加到 this，可见性面板被挤到父类变换面板之后；
		// 改成 topPanel.add 与其他参数排在一起。
		topPanel.add(visibilityPanel, "spanx, growx, wrap");
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
