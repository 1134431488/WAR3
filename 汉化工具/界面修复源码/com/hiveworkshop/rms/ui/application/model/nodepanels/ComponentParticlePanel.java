package com.hiveworkshop.rms.ui.application.model.nodepanels;

import com.hiveworkshop.rms.editor.model.ParticleEmitter;
import com.hiveworkshop.rms.editor.model.animflag.FloatAnimFlag;
import com.hiveworkshop.rms.parsers.mdlx.mdl.MdlUtils;
import com.hiveworkshop.rms.ui.application.FileDialog;
import com.hiveworkshop.rms.ui.application.ImportFileActions;
import com.hiveworkshop.rms.ui.application.model.editors.ComponentEditorTextField;
import com.hiveworkshop.rms.ui.application.model.editors.FloatValuePanel;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelHandler;

import javax.swing.*;

public class ComponentParticlePanel extends ComponentIdObjectPanel<ParticleEmitter> {
	private final ComponentEditorTextField pathField;

	private final FloatValuePanel longitudePanel;
	private final FloatValuePanel latitudePanel;
	private final FloatValuePanel speedPanel;
	private final FloatValuePanel gravityPanel;
	private final FloatValuePanel emissionPanel;
	private final FloatValuePanel visibilityPanel;

	public ComponentParticlePanel(ModelHandler modelHandler) {
		super(modelHandler);

		pathField = new ComponentEditorTextField(24, this::texturePathField);
		topPanel.add(pathField, "");
		JButton exportButton = new JButton("Export");
		exportButton.addActionListener(e -> export());
		topPanel.add(exportButton, "wrap");

		longitudePanel = new FloatValuePanel(modelHandler, "经度");
		latitudePanel = new FloatValuePanel(modelHandler, "发射张角");
		speedPanel = new FloatValuePanel(modelHandler, "速度");
		gravityPanel = new FloatValuePanel(modelHandler, "重力");
		emissionPanel = new FloatValuePanel(modelHandler, "发射速率");
		visibilityPanel = new FloatValuePanel(modelHandler, "可见性");
		topPanel.add(longitudePanel, "spanx, growx, wrap");
		topPanel.add(latitudePanel, "spanx, growx, wrap");
		topPanel.add(speedPanel, "spanx, growx, wrap");
		topPanel.add(gravityPanel, "spanx, growx, wrap");
		topPanel.add(emissionPanel, "spanx, growx, wrap");
		topPanel.add(visibilityPanel, "spanx, growx, wrap");
	}

	@Override
	public void updatePanels() {
		pathField.reloadNewValue(idObject.getPath());
		longitudePanel.reloadNewValue((float) idObject.getLongitude(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_LONGITUDE), idObject, MdlUtils.TOKEN_LONGITUDE, this::setLongitude);
		latitudePanel.reloadNewValue((float) idObject.getLatitude(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_LATITUDE), idObject, MdlUtils.TOKEN_LATITUDE, this::setLatitude);
		speedPanel.reloadNewValue((float) idObject.getInitVelocity(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_SPEED), idObject, MdlUtils.TOKEN_SPEED, this::setInitVelocity);
		gravityPanel.reloadNewValue((float) idObject.getGravity(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_GRAVITY), idObject, MdlUtils.TOKEN_GRAVITY, this::setGravity);
		emissionPanel.reloadNewValue((float) idObject.getEmissionRate(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_EMISSION_RATE), idObject, MdlUtils.TOKEN_EMISSION_RATE, this::setEmissionRate);
		// 原实现把「可见性」绑到了 EMISSION_RATE，会与上面的发射速率控件
		// 争用同一条关键帧轨道并污染数据。这里改回 VISIBILITY。
		visibilityPanel.reloadNewValue(1f, (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_VISIBILITY), idObject, MdlUtils.TOKEN_VISIBILITY, null);
	}

	private void texturePathField(String newPath) {
		idObject.setPath(newPath);
	}

	private void setLongitude(float value){
		if(idObject.getLongitude() != value) {
//			undoManager.pushAction(new xx().redo);
			idObject.setLongitude(value);
		}
	}
	private void setLatitude(float value){
		if(idObject.getLatitude() != value) {
//			undoManager.pushAction(new xx().redo);
			idObject.setLatitude(value);
		}
	}
	private void setInitVelocity(float value){
		if(idObject.getInitVelocity() != value) {
//			undoManager.pushAction(new xx().redo);
			idObject.setInitVelocity(value);
		}
	}
	private void setGravity(float value){
		if(idObject.getGravity() != value) {
//			undoManager.pushAction(new xx().redo);
			idObject.setGravity(value);
		}
	}
	private void setEmissionRate(float value){
		if(idObject.getEmissionRate() != value) {
//			undoManager.pushAction(new xx().redo);
			idObject.setEmissionRate(value);
		}
	}

	private void export(){
		String particlePath = ImportFileActions.convertPathToMDX(idObject.getPath());
		if(!particlePath.isEmpty()){
			FileDialog fileDialog = new FileDialog();
			fileDialog.exportInternalFile(particlePath);
		}
	}
}
