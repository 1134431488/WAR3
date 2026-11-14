package com.hiveworkshop.rms.ui.application.model.nodepanels;

import com.hiveworkshop.rms.editor.model.Material;
import com.hiveworkshop.rms.editor.model.RibbonEmitter;
import com.hiveworkshop.rms.editor.model.animflag.FloatAnimFlag;
import com.hiveworkshop.rms.editor.model.animflag.Vec3AnimFlag;
import com.hiveworkshop.rms.parsers.mdlx.mdl.MdlUtils;
import com.hiveworkshop.rms.ui.application.model.editors.ColorValuePanel;
import com.hiveworkshop.rms.ui.application.model.editors.FloatValuePanel;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelHandler;
import com.hiveworkshop.rms.util.Vec3;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;

public class ComponentRibbonPanel extends ComponentIdObjectPanel<RibbonEmitter> {

	private final FloatValuePanel alphaPanel;
	private final FloatValuePanel heightAbovePanel;
	private final FloatValuePanel heightBelowPanel;
	private final FloatValuePanel lifeSpanPanel;
	private final FloatValuePanel emissionRatePanel;
	private final FloatValuePanel gravityPanel;
	private final FloatValuePanel textureSlotPanel;
	private final ColorValuePanel colorPanel;
	private final JSpinner rowsSpinner;
	private final JSpinner columnsSpinner;
	private final JComboBox<Material> materialBox;
	private boolean updatingMaterial;

	public ComponentRibbonPanel(ModelHandler modelHandler) {
		super(modelHandler);

		JLabel guide = new JLabel("<html>丝带在游戏中沿节点运动轨迹形成拖尾。<br>"
				+ "“上方高度 / 下方高度”控制拖尾宽度。<br>"
				+ "节点需要位移动画才能形成明显轨迹。<br>"
				+ "编辑视口未显示拖尾时，请在游戏中查看。</html>");
		guide.setToolTipText("材质决定贴图和混合方式；贴图行数/列数对应贴图分格");
		topPanel.add(guide, "spanx, growx, wrap");

		JPanel materialPanel = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill]"));
		materialPanel.add(new JLabel("材质"));
		materialBox = new JComboBox<>();
		materialBox.setRenderer(new DefaultListCellRenderer() {
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
					int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof Material) {
					Material material = (Material) value;
					String name = material.getName();
					if (name == null || name.trim().isEmpty()) {
						name = "未命名材质";
					}
					int materialIndex = modelHandler.getModel().getMaterials().indexOf(material);
					setText((materialIndex >= 0 ? "材质 " + (materialIndex + 1) + "：" : "") + name);
				}
				return this;
			}
		});
		materialBox.addActionListener(e -> changeMaterial());
		materialPanel.add(materialBox, "growx");
		topPanel.add(materialPanel, "spanx, growx, wrap");

		JPanel textureGridPanel = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill][][grow,fill]"));
		textureGridPanel.add(new JLabel("贴图行数"));
		rowsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1024, 1));
		rowsSpinner.addChangeListener(e -> idObject.setRows((Integer) rowsSpinner.getValue()));
		textureGridPanel.add(rowsSpinner, "growx");
		textureGridPanel.add(new JLabel("贴图列数"));
		columnsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1024, 1));
		columnsSpinner.addChangeListener(e -> idObject.setColumns((Integer) columnsSpinner.getValue()));
		textureGridPanel.add(columnsSpinner, "growx");
		topPanel.add(textureGridPanel, "spanx, growx, wrap");

		heightAbovePanel = addFloatPanel("上方高度", "丝带上边缘到节点轨迹的距离");
		heightBelowPanel = addFloatPanel("下方高度", "丝带下边缘到节点轨迹的距离");
		lifeSpanPanel = addFloatPanel("生命周期", "每段丝带保留的时间");
		emissionRatePanel = addFloatPanel("发射速率", "每秒生成的丝带段数");
		gravityPanel = addFloatPanel("重力", "丝带段受到的重力");
		textureSlotPanel = addFloatPanel("贴图格索引", "多格贴图使用的格子编号，从 0 开始");
		alphaPanel = addFloatPanel("不透明度", "0 为完全透明，1 为不透明");

		colorPanel = new ColorValuePanel(modelHandler, "丝带颜色");
		colorPanel.setToolTipText("丝带颜色，会与材质贴图颜色相乘");
		topPanel.add(colorPanel, "spanx, growx, wrap");
	}

	private FloatValuePanel addFloatPanel(String title, String tooltip) {
		FloatValuePanel panel = new FloatValuePanel(modelHandler, title);
		panel.setToolTipText(tooltip);
		topPanel.add(panel, "spanx, growx, wrap");
		return panel;
	}

	private void changeMaterial() {
		if (!updatingMaterial && idObject != null) {
			Material material = (Material) materialBox.getSelectedItem();
			if (material != null) {
				idObject.setMaterial(material);
				idObject.setMaterialId(modelHandler.getModel().getMaterials().indexOf(material));
			}
		}
	}

	@Override
	public void updatePanels() {
		updatingMaterial = true;
		try {
			materialBox.removeAllItems();
			for (Material material : modelHandler.getModel().getMaterials()) {
				materialBox.addItem(material);
			}
			Material material = idObject.getMaterial();
			if (material == null && idObject.getMaterialId() >= 0
					&& idObject.getMaterialId() < modelHandler.getModel().getMaterials().size()) {
				material = modelHandler.getModel().getMaterials().get(idObject.getMaterialId());
				idObject.setMaterial(material);
			}
			materialBox.setSelectedItem(material);
		} finally {
			updatingMaterial = false;
		}

		rowsSpinner.setValue(idObject.getRows());
		columnsSpinner.setValue(idObject.getColumns());
		heightAbovePanel.reloadNewValue((float) idObject.getHeightAbove(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_HEIGHT_ABOVE), idObject, MdlUtils.TOKEN_HEIGHT_ABOVE, this::setHeightAbove);
		heightBelowPanel.reloadNewValue((float) idObject.getHeightBelow(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_HEIGHT_BELOW), idObject, MdlUtils.TOKEN_HEIGHT_BELOW, this::setHeightBelow);
		lifeSpanPanel.reloadNewValue((float) idObject.getLifeSpan(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_LIFE_SPAN), idObject, MdlUtils.TOKEN_LIFE_SPAN, this::setLifeSpan);
		emissionRatePanel.reloadNewValue((float) idObject.getEmissionRate(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_EMISSION_RATE), idObject, MdlUtils.TOKEN_EMISSION_RATE, this::setEmissionRate);
		gravityPanel.reloadNewValue((float) idObject.getGravity(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_GRAVITY), idObject, MdlUtils.TOKEN_GRAVITY, this::setGravity);
		textureSlotPanel.reloadNewValue((float) idObject.getTextureSlot(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_TEXTURE_SLOT), idObject, MdlUtils.TOKEN_TEXTURE_SLOT, this::setTextureSlot);
		alphaPanel.reloadNewValue((float) idObject.getAlpha(), (FloatAnimFlag) idObject.find(MdlUtils.TOKEN_ALPHA), idObject, MdlUtils.TOKEN_ALPHA, this::setAlpha);
		colorPanel.reloadNewValue(idObject.getStaticColor(), (Vec3AnimFlag) idObject.find(MdlUtils.TOKEN_COLOR), idObject, MdlUtils.TOKEN_COLOR, this::setColor);
		// KRIB 只有 KRHA/KRHB/KRAL/KRCO/KRTX/KRVS 六条动画通道；生命周期 / 发射速率 /
		// 重力是纯静态字段，禁用「转为动态」以免生成的 AnimFlag 存不进文件、静默丢失。
		lifeSpanPanel.setDynamicAllowed(false);
		emissionRatePanel.setDynamicAllowed(false);
		gravityPanel.setDynamicAllowed(false);
	}

	private void setHeightAbove(float value) {
		idObject.setHeightAbove(value);
	}

	private void setHeightBelow(float value) {
		idObject.setHeightBelow(value);
	}

	private void setLifeSpan(float value) {
		idObject.setLifeSpan(value);
	}

	private void setEmissionRate(float value) {
		idObject.setEmissionRate(Math.max(0, Math.round(value)));
	}

	private void setGravity(float value) {
		idObject.setGravity(value);
	}

	private void setTextureSlot(float value) {
		idObject.setTextureSlot(Math.max(0, value));
	}

	private void setAlpha(float value) {
		idObject.setAlpha(value);
	}

	private void setColor(Vec3 color) {
		idObject.setStaticColor(color);
	}
}
