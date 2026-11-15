package com.hiveworkshop.rms.ui.application;

import com.hiveworkshop.rms.editor.model.*;
import com.hiveworkshop.rms.editor.model.animflag.FloatAnimFlag;
import com.hiveworkshop.rms.editor.model.util.FilterMode;
import com.hiveworkshop.rms.filesystem.GameDataFileSystem;
import com.hiveworkshop.rms.parsers.mdlx.mdl.MdlUtils;
import com.hiveworkshop.rms.parsers.mdlx.util.MdxUtils;
import com.hiveworkshop.rms.ui.application.edit.ModelStructureChangeListener;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.ui.icons.IconUtils;
import com.hiveworkshop.rms.util.Vec3;
import com.hiveworkshop.rms.util.Vec3SpinnerArray;
import net.miginfocom.swing.MigLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddParticlePanel {

	static String[][] sdParticleFilePairs = {
			{"DustEmitter.mdx", "DustEmitter.png"},
			{"FireSmallBurn.mdx", "FireSmallBurn.png"},
			{"InfernalMagicFire.mdx", "InfernalMagicFire.png"},
			{"LargeBrownSmokeEmitter.mdx", "LargeBrownSmokeEmitter.png"},
			{"LargeFireEmitter.mdx", "LargeFireEmitter.png"},
			{"PixieDustEmitter.mdx", "PixieDustEmitter.png"},
			{"ShockEnergyEmitter.mdx", "ShockEnergyEmitter.png"},
			{"TeamcolorWispShimmer.mdx", "TeamcolorWispShimmer.png"},
			{"WeaponMagicEmitter.mdx", "WeaponMagicEmitter.png"},
			{"WeaponMagicFlatEmitter.mdx", "WeaponMagicFlatEmitter.png"}
	};

	public static void addParticleButtons(JMenu addParticle) {
		addParticleButtons(addParticle, null);
	}

	/**
	 * 带默认父级：供视口 / 节点树右键调用，弹出的添加对话框会预选右键选中的骨骼，
	 * 并把坐标同步到该骨骼的 pivot。主菜单「添加 → 粒子」仍走上面的无参重载。
	 * 用参数透传而非静态字段：菜单项监听器是延迟执行的，静态值会被后续菜单构建覆盖。
	 */
	public static void addParticleButtons(JMenu addParticle, IdObject defaultParent) {
		List<ParticleInformation> particleInformationList = fetchIncludedParticles();
		for (ParticleInformation particleInformation : particleInformationList) {
			makeAndAddParticleButtons(addParticle, particleInformation, defaultParent);
		}
	}

	static String[] particleStockFiles = {
			"DustEmitter.mdx",
			"DustEmitter.png",
			"FireSmallBurn.mdx",
			"FireSmallBurn.png",
			"InfernalMagicFire.mdx",
			"InfernalMagicFire.png",
			"LargeBrownSmokeEmitter.mdx",
			"LargeBrownSmokeEmitter.png",
			"LargeFireEmitter.mdx",
			"LargeFireEmitter.png",
			"PixieDustEmitter.mdx",
			"PixieDustEmitter.png",
			"ShockEnergyEmitter.mdx",
			"ShockEnergyEmitter.png",
			"TeamcolorWispShimmer.mdx",
			"TeamcolorWispShimmer.png",
			"WeaponMagicEmitter.mdx",
			"WeaponMagicEmitter.png",
			"WeaponMagicFlatEmitter.mdx",
			"WeaponMagicFlatEmitter.png",
			"MagicFireBurn.png",
			"FireSmallOrange.png",
	};

	public static void addEmptyPopcorn() {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null && modelPanel.getModel() != null) {
			ParticleEmitterPopcorn newPopcornEmitter = new ParticleEmitterPopcorn("新建爆米花粒子");
			newPopcornEmitter.setPivotPoint(new Vec3(0, 0, 0));
			modelPanel.getModel().add(newPopcornEmitter);
			ModelStructureChangeListener.changeListener.nodesUpdated();
		}
	}

	/** 创建带默认白色材质和基础参数的丝带发射器。 */
	public static void addEmptyRibbon() {
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null && modelPanel.getModel() != null) {
			RibbonEmitter ribbon = createDefaultRibbon(modelPanel.getModel());
			ModelStructureChangeListener.changeListener.nodesUpdated();
			JOptionPane.showMessageDialog(ProgramGlobals.getMainPanel(),
					"<html><body style='width: 360px'>已创建“" + ribbon.getName() + "”，并配置白色双面材质。<br><br>"
							+ "在“模型”视图的节点列表中选中它，即可调整上方/下方高度、生命周期、发射速率、重力和颜色。"
							+ "丝带在游戏中沿节点的运动轨迹形成拖尾；请给节点添加位移动画或把它绑定到会运动的骨骼上。"
							+ "若编辑视口未显示拖尾，请在游戏中查看。</body></html>",
					"丝带已创建", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	static RibbonEmitter createDefaultRibbon(EditableModel model) {
		String whitePath = model.getFormatVersion() == 1000 ? "Textures\\White.dds" : "Textures\\White.blp";
		Bitmap texture = null;
		for (Bitmap candidate : model.getTextures()) {
			if (whitePath.equalsIgnoreCase(candidate.getPath())) {
				texture = candidate;
				break;
			}
		}
		if (texture == null) {
			texture = new Bitmap(whitePath);
			model.add(texture);
		}

		Layer layer = new Layer(FilterMode.BLEND, texture);
		layer.setTextureId(model.getTextureId(texture));
		layer.setTwoSided(true);
		layer.setUnshaded(true);
		Material material = new Material(layer);
		material.setTwoSided(true);
		model.add(material);

		RibbonEmitter ribbon = new RibbonEmitter("新建丝带");
		ribbon.setPivotPoint(new Vec3(0, 0, 0));
		ribbon.setHeightAbove(24);
		ribbon.setHeightBelow(24);
		ribbon.setAlpha(1.0);
		ribbon.setLifeSpan(1.0);
		ribbon.setEmissionRate(40);
		ribbon.setRows(1);
		ribbon.setColumns(1);
		ribbon.setGravity(0);
		ribbon.setTextureSlot(0);
		ribbon.setStaticColor(new Vec3(1, 1, 1));
		ribbon.setMaterial(material);
		ribbon.setMaterialId(model.getMaterials().indexOf(material));
		model.add(ribbon);
		return ribbon;
	}

	private static void makeAndAddParticleButtons(JMenu addParticle, ParticleInformation particleInformation,
			IdObject defaultParent) {
		final JMenuItem particleItem = new JMenuItem(particleInformation.getName(),
				new ImageIcon(particleInformation.getImage().getScaledInstance(28, 28, Image.SCALE_SMOOTH)));
		particleItem.addActionListener(e -> makeAddParticlePanel(particleInformation, defaultParent));
		addParticle.add(particleItem);
	}

	private static List<ParticleInformation> fetchIncludedParticles() {
		List<ParticleInformation> particleInformations = new ArrayList<>();
		for (String[] particleFiles : sdParticleFilePairs) {
			final String name = particleFiles[0].split("\\.")[0];
			Image image2 = loadImage("stock\\particles\\" + particleFiles[1]);
			String mdxPath = "stock\\particles\\" + particleFiles[0];
			particleInformations.add(new ParticleInformation(mdxPath, name, image2));
		}
		return particleInformations;
	}

	public static Image loadImage(final String path) {
		try (InputStream is = GameDataFileSystem.getDefault().getResourceAsStream(path)) {
			if (is == null) {
				System.err.println("[Particle] 缺少预设图标: " + path);
				return blankIcon();
			}
			Image img = ImageIO.read(is);
			return img != null ? img : blankIcon();
		} catch (IOException | IllegalArgumentException e) {
			System.err.println("[Particle] 预设图标读取失败: " + path);
			return blankIcon();
		}
	}

	private static Image blankIcon() {
		return new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
	}

	private static void makeAddParticlePanel(ParticleInformation particleInformation, IdObject defaultParent) {
		final ParticleEmitter2 particle;
		try (InputStream is = GameDataFileSystem.getDefault().getResourceAsStream(particleInformation.filePath)) {
			if (is == null) {
				System.err.println("[Particle] 缺少预设模型: " + particleInformation.filePath);
				return;
			}
			com.hiveworkshop.rms.editor.model.EditableModel preset = MdxUtils.loadEditable(is);
			if (preset.getParticleEmitter2s().isEmpty()) {
				System.err.println("[Particle] 预设里没有粒子发射器: " + particleInformation.filePath);
				return;
			}
			particle = preset.getParticleEmitter2s().get(0);
		} catch (IOException | RuntimeException e1) {
			e1.printStackTrace();
			return;
		}


		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null && modelPanel.getModel() != null) {
			final JPanel particlePanel = new JPanel(new MigLayout("ins 8, fillx", "[128!]8[grow,fill]"));

			final JLabel imageLabel = new JLabel(new ImageIcon(
					particleInformation.getImage().getScaledInstance(128, 128, Image.SCALE_SMOOTH)));
			particlePanel.add(imageLabel, "top");

			JPanel optionsPanel = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill]"));

			final JLabel titleLabel = new JLabel("添加 " + particleInformation.getName());
			titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
			optionsPanel.add(titleLabel, "growx, wrap");

			optionsPanel.add(new JLabel("粒子名称："), "split 2");
			JTextField nameField = new JTextField("我的" + particleInformation.getName() + "粒子");
			optionsPanel.add(nameField, "growx, pushx, wmin 260, wrap");

			optionsPanel.add(new JLabel("父级（可直接输入名称搜索）："), "spanx, wrap");

			List<IdObject> idObjects = new ArrayList<>(modelPanel.getModel().getIdObjects());
			Bone nullBone = new Bone("无父级");
			idObjects.add(0, nullBone);

			JComboBox<IdObject> parentBone = new JComboBox<>(idObjects.toArray(new IdObject[0]));
			parentBone.setRenderer(createParticleParentComboBox(nullBone));
			parentBone.setMaximumRowCount(14);
			parentBone.setToolTipText("骨骼/节点很多时，直接在这里输入名称即可搜索定位，按回车选择第一项");
			// 右键入口传入的默认父级。必须在 configureSearchable 之前预选：
			// 它会给 editor 装 DocumentListener 并延迟 refilter 重建 model，晚设置会丢失选中项。
			boolean hasDefaultParent = defaultParent != null && idObjects.contains(defaultParent);
			if (hasDefaultParent) {
				parentBone.setSelectedItem(defaultParent);
			}
			configureSearchable(parentBone, idObjects, nullBone);
			optionsPanel.add(parentBone, "growx, pushx, wmin 320, wrap");

			Vec3SpinnerArray spinnerArray = new Vec3SpinnerArray("X:", "Y:", "Z:")
					.setLabelWrap(false).setLabelConstrains("gapx 10");
			if (hasDefaultParent) {
				// 程序化 setSelectedItem 不一定触发 ActionListener，坐标要显式同步一次
				spinnerArray.setValues(defaultParent.getPivotPoint());
			}
			optionsPanel.add(spinnerArray.spinnerPanel(), "spanx, wrap");
			applyCoordinateTooltips(spinnerArray.spinnerPanel());

			final JLabel coordHint = new JLabel("坐标：X 前后 / Y 左右 / Z 上下（相对模型原点）");
			coordHint.setForeground(new Color(0x88, 0x88, 0x88));
			optionsPanel.add(coordHint, "spanx, wrap");

			final JLabel livePreview = new JLabel(" ");
			livePreview.setForeground(new Color(0x47, 0x72, 0xB3));
			optionsPanel.add(livePreview, "spanx, wrap");
			spinnerArray.setVec3Consumer(v -> livePreview.setText(String.format(
					"当前位置：X=%.2f  Y=%.2f  Z=%.2f", v.x, v.y, v.z)));

			parentBone.addActionListener(e14 -> {
				Object selected = parentBone.getSelectedItem();
				if (selected instanceof IdObject) {
					spinnerArray.setValues(((IdObject) selected).getPivotPoint());
				}
			});

			Map<Animation, Boolean> animVisStatus = new HashMap<>();
			modelPanel.getModel().getAnims().forEach(a -> animVisStatus.put(a, true));

			final JPanel animPanel = animVisPanel(animVisStatus);

			final JButton chooseAnimations = new JButton("选择显示时机");
			ensureReadableButton(chooseAnimations);
			chooseAnimations.addActionListener(e13 -> JOptionPane.showMessageDialog(particlePanel, animPanel));
			optionsPanel.add(chooseAnimations, "growx, align center, wrap");

			final JButton[] colorButtons = new JButton[3];
			final Color[] colors = new Color[colorButtons.length];
			makeColorButtons(particlePanel, particle, colorButtons, colors);
			JPanel colorPanel = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill][grow,fill][grow,fill]", "[]"));
			for (JButton button : colorButtons) {
				Object selectedParent = parentBone.getSelectedItem();
				IdObject parent = (selectedParent instanceof IdObject) ? (IdObject) selectedParent : null;
			}
			optionsPanel.add(colorPanel, "growx, wrap");

			particlePanel.add(optionsPanel, "growx, pushx, wmin 420");
			final int x = JOptionPane.showConfirmDialog(ProgramGlobals.getMainPanel(), particlePanel,
					"添加 " + particleInformation.getName(), JOptionPane.OK_CANCEL_OPTION);
			if (x == JOptionPane.OK_OPTION) {
				Object chosenParent = parentBone.getSelectedItem();
				IdObject parent = (chosenParent instanceof IdObject) ? (IdObject) chosenParent : null;
				if (parent == nullBone) {
					parent = null;
				}
				addParticleEmitter2(particle, parent, nameField.getText(),
						spinnerArray.getValue(), animVisStatus, colors);
			}
		}
	}

	/** 可搜索的父级下拉框：输入即过滤，回车选中第一项。 */
	static void configureSearchable(final JComboBox<IdObject> combo,
			final List<IdObject> allNodes, final Bone nullBone) {
		combo.setEditable(true);
		final boolean[] guard = {false};
		combo.setEditor(new BasicComboBoxEditor() {
			@Override
			public void setItem(Object anObject) {
				boolean wasGuarded = guard[0];
				guard[0] = true;
				try {
					editor.setText(displayName(anObject, nullBone));
				} finally {
					guard[0] = wasGuarded;
				}
			}

			@Override
			public Object getItem() {
				return combo.getSelectedItem();
			}
		});

		final JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
		editor.setToolTipText("输入骨骼/节点名称过滤，回车选择第一项");
		final boolean[] refilterQueued = {false};

		final Runnable refilter = () -> {
			refilterQueued[0] = false;
			if (guard[0]) return;
			final String text = editor.getText();
			if (text == null) return;
			final String query = text.trim().toLowerCase();
			final int caret = editor.getCaretPosition();
			List<IdObject> filtered = new ArrayList<>();
			for (IdObject object : allNodes) {
				if (query.isEmpty() || displayName(object, nullBone).toLowerCase().contains(query)) {
					filtered.add(object);
				}
			}

			guard[0] = true;
			try {
				combo.setModel(new DefaultComboBoxModel<>(filtered.toArray(new IdObject[0])));
				// setModel 会同步编辑器；恢复用户正在输入的文本和光标，而不是回写选中项。
				editor.setText(text);
				editor.setCaretPosition(Math.max(0, Math.min(caret, text.length())));
			} finally {
				guard[0] = false;
			}
			if (!query.isEmpty()) {
				combo.showPopup();
			} else {
				combo.hidePopup();
			}
		};

		final Runnable queueRefilter = () -> {
			if (!guard[0] && !refilterQueued[0]) {
				refilterQueued[0] = true;
				// DocumentListener 通知期间不能修改 Document/ComboBoxModel，延迟到通知结束后合并处理。
				SwingUtilities.invokeLater(refilter);
			}
		};

		editor.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				queueRefilter.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				queueRefilter.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				queueRefilter.run();
			}
		});

		editor.addActionListener(e -> {
			if (combo.getItemCount() > 0) {
				combo.setSelectedItem(combo.getItemAt(0));
				combo.hidePopup();
			}
		});
	}

	private static String displayName(Object value, Bone nullBone) {
		if (value == null) return "";
		if (value == nullBone) return "无父级（模型根）";
		if (value instanceof IdObject) {
			IdObject o = (IdObject) value;
			return typeName(o) + " \"" + o.getName() + "\"";
		}
		return String.valueOf(value);
	}

	private static String typeName(IdObject o) {
		if (o instanceof Bone) return "骨骼";
		if (o instanceof RibbonEmitter) return "丝带";
		if (o instanceof ParticleEmitterPopcorn) return "爆米花粒子";
		if (o instanceof ParticleEmitter2) return "粒子2";
		if (o instanceof ParticleEmitter) return "粒子";
		if (o instanceof Attachment) return "附着点";
		if (o instanceof Helper) return "辅助点";
		if (o instanceof Light) return "灯光";
		if (o instanceof CollisionShape) return "碰撞体";
		if (o instanceof EventObject) return "事件";
		return o.getClass().getSimpleName();
	}

	private static void applyCoordinateTooltips(Component root) {
		if (root instanceof JSpinner) {
			int axis = indexOfAxis(root);
			((JSpinner) root).setToolTipText(axisText(axis));
		}
		if (root instanceof Container) {
			for (Component child : ((Container) root).getComponents()) {
				applyCoordinateTooltips(child);
			}
		}
	}

	private static int indexOfAxis(Component c) {
		Container parent = c.getParent();
		if (parent == null) return -1;
		int index = 0;
		for (Component sibling : parent.getComponents()) {
			if (sibling == c) return index;
			index++;
		}
		return -1;
	}

	private static String axisText(int axis) {
		switch (axis) {
			case 0:
				return "X 轴：前后方向（模型朝向前方为正）";
			case 1:
				return "Y 轴：左右方向";
			case 2:
				return "Z 轴：上下方向（向上为正）";
			default:
				return "粒子的挂载坐标";
		}
	}

	private static void addParticleEmitter2(ParticleEmitter2 particle, IdObject parent, String name, Vec3 pivot,
			Map<Animation, Boolean> animVisMap, Color[] colors) {
		particle.setPivotPoint(pivot);
		for (int i = 0; i < colors.length; i++) {
			particle.setSegmentColor(i, new Vec3(
					colors[i].getRed() / 255.00,
					colors[i].getGreen() / 255.00,
					colors[i].getBlue() / 255.00));
		}

		particle.setParent(parent);

		FloatAnimFlag oldFlag = (FloatAnimFlag) particle.getVisibilityFlag();
		if (oldFlag == null) {
			oldFlag = new FloatAnimFlag(MdlUtils.TOKEN_VISIBILITY);
		}

		FloatAnimFlag visFlag = (FloatAnimFlag) oldFlag.getEmptyCopy();
		for (Animation anim : animVisMap.keySet()) {
			if (!animVisMap.get(anim)) {
				visFlag.addEntry(0, 0f, anim);
			}
		}
		// 总是写入：空表用来清掉预设模型自带、引用外部 Sequence 的可见性数据
		particle.setVisibilityFlag(visFlag);

		particle.setName(name);
		ProgramGlobals.getCurrentModelPanel().getModel().add(particle);
		ModelStructureChangeListener.changeListener.nodesUpdated();
	}

	private static JPanel animVisPanel(Map<Animation, Boolean> animVisStatus) {
		final JPanel animPanel = new JPanel(new MigLayout(""));

		for (Animation animation : animVisStatus.keySet()) {
			JCheckBox checkBox = new JCheckBox(animation.getName());
			checkBox.setSelected(animVisStatus.get(animation));
			checkBox.addActionListener(e -> animVisStatus.put(animation, checkBox.isSelected()));
			animPanel.add(checkBox, "wrap");
		}

		return animPanel;
	}

	static void ensureReadableButton(AbstractButton button) {
		FontMetrics metrics = button.getFontMetrics(button.getFont());
		Insets insets = button.getInsets();
		Icon icon = button.getIcon();
		int iconWidth = icon == null ? 0 : icon.getIconWidth() + button.getIconTextGap();
		int minWidth = metrics.stringWidth("中文") + iconWidth + insets.left + insets.right + 8;
		Dimension preferred = button.getPreferredSize();
		preferred.width = Math.max(preferred.width, minWidth);
		button.setPreferredSize(preferred);
		Dimension minimum = button.getMinimumSize();
		minimum.width = Math.max(minimum.width, minWidth);
		button.setMinimumSize(minimum);
	}

	private static void makeColorButtons(JPanel parentPanel, ParticleEmitter2 particle, JButton[] colorButtons,
			Color[] colors) {
		for (int i = 0; i < colorButtons.length; i++) {
			final Vec3 colorValues = particle.getSegmentColor(i);
			final Color color = new Color((int) (colorValues.x * 255), (int) (colorValues.y * 255),
					(int) (colorValues.z * 255));

			final JButton button = new JButton("颜色 " + (i + 1), new ImageIcon(IconUtils.createBlank(color, 32, 32)));
			ensureReadableButton(button);
			colors[i] = color;
			final int index = i;
			button.addActionListener(e12 -> {
				final Color colorChoice = JColorChooser.showDialog(parentPanel, "选择颜色", colors[index]);
				if (colorChoice != null) {
					colors[index] = colorChoice;
					button.setIcon(new ImageIcon(IconUtils.createBlank(colors[index], 32, 32)));
				}
			});
			colorButtons[i] = button;
		}
	}

	private static BasicComboBoxRenderer createParticleParentComboBox(final Bone nullBone) {
		return new BasicComboBoxRenderer() {
			@Override
			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				if (value == nullBone) {
					return super.getListCellRendererComponent(list, "无父级（模型根）", index, isSelected, cellHasFocus);
				}
				return super.getListCellRendererComponent(list, displayName(value, nullBone), index, isSelected,
						cellHasFocus);
			}
		};
	}

	private static class ParticleInformation {
		private String filePath;
		private final String uggName;
		private final Image image;

		private ParticleInformation(String filePath, String uggName, Image image) {
			this.filePath = filePath;
			this.uggName = uggName;
			this.image = image;
		}

		public String getName() {
			return uggName;
		}

		public String getFilePath() {
			return filePath;
		}

		public Image getImage() {
			return image;
		}
	}
}
