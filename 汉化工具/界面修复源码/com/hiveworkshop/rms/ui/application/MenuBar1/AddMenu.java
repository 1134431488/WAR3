package com.hiveworkshop.rms.ui.application.MenuBar1;

import com.hiveworkshop.rms.editor.actions.UndoAction;
import com.hiveworkshop.rms.editor.actions.addactions.AddCameraAction;
import com.hiveworkshop.rms.editor.actions.model.AddFaceEffectAction;
import com.hiveworkshop.rms.editor.actions.model.material.AddMaterialAction;
import com.hiveworkshop.rms.editor.actions.model.material.SetMaterialShaderStringAction;
import com.hiveworkshop.rms.editor.actions.nodes.AddNodeAction;
import com.hiveworkshop.rms.editor.model.*;
import com.hiveworkshop.rms.ui.application.AddBirthDeathSequences;
import com.hiveworkshop.rms.ui.application.AddParticlePanel;
import com.hiveworkshop.rms.ui.application.AddSingleAnimationActions;
import com.hiveworkshop.rms.ui.application.ProgramGlobals;
import com.hiveworkshop.rms.ui.application.War3EffectImporter;
import com.hiveworkshop.rms.ui.application.edit.ModelStructureChangeListener;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.util.Vec3;

import javax.swing.*;
import java.awt.event.KeyEvent;

import static com.hiveworkshop.rms.ui.application.MenuCreationUtils.createMenu;
import static com.hiveworkshop.rms.ui.application.MenuCreationUtils.createMenuItem;

public class AddMenu extends JMenu {

	public AddMenu() {
		super("添加");
		setMnemonic(KeyEvent.VK_A);
		putClientProperty("rms.accelerator", KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.SHIFT_DOWN_MASK));
		getAccessibleContext().setAccessibleDescription("向模型中添加新的组件。快捷键：Shift+A");

		JMenu addParticleMenu = createMenu("粒子", KeyEvent.VK_P);
		add(addParticleMenu);

		AddParticlePanel.addParticleButtons(addParticleMenu);
		addParticleMenu.add(createMenuItem("空爆米花粒子", KeyEvent.VK_O, e -> AddParticlePanel.addEmptyPopcorn()));
		addParticleMenu.add(createMenuItem("空丝带粒子（Ribbon）", KeyEvent.VK_R, e -> AddParticlePanel.addEmptyRibbon()));
		addParticleMenu.addSeparator();
		addParticleMenu.add(createMenuItem("从 war3 本体导入特效…", KeyEvent.VK_W,
				e -> War3EffectImporter.show(AddMenu.this)));

		JMenu animationMenu = createMenu("动画", KeyEvent.VK_A);
		add(animationMenu);

		animationMenu.add(createMenuItem("空动画", KeyEvent.VK_F, e -> AddSingleAnimationActions.addEmptyAnimation()));
		animationMenu.add(createMenuItem("升起/落下 出生/死亡", KeyEvent.VK_R, e -> AddBirthDeathSequences.riseFallBirthActionRes()));

		JMenu singleAnimationMenu = createMenu("单个动画", KeyEvent.VK_S);
		animationMenu.add(singleAnimationMenu);

		singleAnimationMenu.add(createMenuItem("从文件", KeyEvent.VK_F, e -> AddSingleAnimationActions.addAnimationFromFile()));
		singleAnimationMenu.add(createMenuItem("从单位", KeyEvent.VK_U, e -> AddSingleAnimationActions.addAnimationFromUnit()));
		singleAnimationMenu.add(createMenuItem("从模型", KeyEvent.VK_M, e -> AddSingleAnimationActions.addAnimFromModel()));
		singleAnimationMenu.add(createMenuItem("从对象", KeyEvent.VK_O, e -> AddSingleAnimationActions.addAnimationFromObject()));

		add(createMenuItem("材质", KeyEvent.VK_M, e -> addNewMaterial()));
		add(createMenuItem("附着点", KeyEvent.VK_C, e -> addIdObject(new Attachment("新建附着点"))));
		add(createMenuItem("骨骼", KeyEvent.VK_C, e -> addIdObject(new Bone("新建骨骼"))));
		add(createMenuItem("相机", KeyEvent.VK_C, e -> addCamera()));
		add(createMenuItem("碰撞体", KeyEvent.VK_C, e -> addCollisionShape()));
		add(createMenuItem("事件对象", KeyEvent.VK_C, e -> addIdObject(new EventObject("新建事件对象"))));
		add(createMenuItem("表情FX", KeyEvent.VK_C, e -> addFaceFX()));
		add(createMenuItem("辅助点", KeyEvent.VK_C, e -> addIdObject(new Helper("新建辅助点"))));
		add(createMenuItem("灯光", KeyEvent.VK_C, e -> addIdObject(new Light("新建灯光"))));
	}

	private static void addIdObject(IdObject idObject) {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null) {
			EditableModel current = modelPanel.getModel();
			modelPanel.getModelHandler().getUndoManager().pushAction(new AddNodeAction(current, idObject, ModelStructureChangeListener.changeListener).redo());
		}
	}
	private static void addCollisionShape() {
		CollisionShape shape = new CollisionShape();
		shape.setName("新建碰撞体");
		addIdObject(shape);
	}

	private static void addCamera() {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null) {
			EditableModel current = modelPanel.getModel();
			Camera camera = new Camera("New Camera");
			camera.setPosition(new Vec3(100, 0, 90));
			camera.setTargetPosition(new Vec3(0, 0, 80));
			modelPanel.getModelHandler().getUndoManager().pushAction(new AddCameraAction(current, camera, ModelStructureChangeListener.changeListener).redo());
		}
	}
	private static void addFaceFX() {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null) {
			EditableModel current = modelPanel.getModel();
			FaceEffect faceEffect = new FaceEffect("Node", "");
			modelPanel.getModelHandler().getUndoManager().pushAction(new AddFaceEffectAction(faceEffect, current, ModelStructureChangeListener.changeListener).redo());
		}
	}

	public static void addNewMaterial() {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null) {
			EditableModel model = modelPanel.getModel();
			Bitmap texture;
			if (model.getTextures().isEmpty()) {
				String path = model.getFormatVersion() == 1000 ? "Textures\\White.dds" : "Textures\\White.blp";
				texture = new Bitmap(path);
			} else {
				texture = model.getTexture(0);
			}

			Material material = new Material(new Layer(texture));

			if (model.getFormatVersion() == 1000) {
				new SetMaterialShaderStringAction(model, material, "Shader_HD_DefaultUnit", null).redo();
			}
			UndoAction action = new AddMaterialAction(material, model, ModelStructureChangeListener.changeListener);
			modelPanel.getModelHandler().getUndoManager().pushAction(action.redo());
		}
	}
}
