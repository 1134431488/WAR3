package com.hiveworkshop.rms.ui.application;

import com.hiveworkshop.rms.editor.actions.nodes.AddNodeAction;
import com.hiveworkshop.rms.editor.model.Bitmap;
import com.hiveworkshop.rms.editor.model.Bone;
import com.hiveworkshop.rms.editor.model.EditableModel;
import com.hiveworkshop.rms.editor.model.IdObject;
import com.hiveworkshop.rms.editor.model.Layer;
import com.hiveworkshop.rms.editor.model.Material;
import com.hiveworkshop.rms.editor.model.ParticleEmitter2;
import com.hiveworkshop.rms.editor.model.RibbonEmitter;
import com.hiveworkshop.rms.filesystem.GameDataFileSystem;
import com.hiveworkshop.rms.filesystem.sources.DataSourceDescriptor;
import com.hiveworkshop.rms.filesystem.sources.MpqDataSourceDescriptor;
import com.hiveworkshop.rms.parsers.mdlx.util.MdxUtils;
import com.hiveworkshop.rms.ui.application.edit.ModelStructureChangeListener;
import com.hiveworkshop.rms.ui.application.edit.mesh.activity.UndoManager;
import com.hiveworkshop.rms.ui.application.viewer.AnimationViewer;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.ui.preferences.SaveProfile;
import com.hiveworkshop.rms.util.Vec3;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * 从 war3 本体（MPQ / CASC 数据源）浏览原生技能特效，把其中的粒子 / 丝带发射器
 * 连同贴图、材质导入当前模型，导入后即可像普通节点一样在编辑器里修改。
 *
 * 关键点：
 * - 数据源沿用 GameDataFileSystem.getMergedListfile()，未配置游戏目录时提供
 *   「添加本机 war3 目录…」按钮扫描 War3*.mpq 并注册。
 * - 右侧用项目现成的 AnimationViewer 做实时预览（点击列表即切换），GL 组件只创建一次。
 * - 贴图 / 材质按路径去重后追加到目标模型，并分别重映射 ParticleEmitter2.textureId
 *   与 RibbonEmitter.materialId（丝带靠 materialId → Material → Layer → Bitmap 链取贴图，
 *   只搬发射器会丢材质，这就是此前「丝带导入没效果」的原因）。
 * - 用 AddNodeAction 加入节点：它会同时 model.add + nodesUpdated()，既让树立刻刷新、
 *   可撤销，也避免了此前「导入后什么都没发生」的问题。
 */
public final class War3EffectImporter {

	private final JDialog dialog;
	private final DefaultListModel<String> listModel = new DefaultListModel<>();
	private final JList<String> modelList = new JList<>(listModel);
	private final JTextField searchField = new JTextField();
	private final JLabel statusLabel = new JLabel(" ");
	private final JLabel hintLabel = new JLabel(" ");
	private final AnimationViewer previewViewer;
	private final Bone nullParent = new Bone("无父级（模型根）");
	private final JComboBox<IdObject> parentCombo = new JComboBox<>();
	private final JCheckBox snapPivot = new JCheckBox("特效落在骨骼位置", true);
	private List<String> allModels = new ArrayList<>();
	private volatile long previewGeneration = 0;

	private War3EffectImporter(Frame owner) {
		dialog = new JDialog(owner, "从 war3 本体导入特效", false);

		JPanel root = new JPanel(new BorderLayout(6, 6));
		root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel(new BorderLayout(4, 4));
		top.add(new JLabel("搜索："), BorderLayout.WEST);
		top.add(searchField, BorderLayout.CENTER);
		JButton addDir = new JButton("添加本机 war3 目录…");
		addDir.setToolTipText("选择含 War3.mpq / War3x.mpq / War3Patch.mpq 的游戏目录并注册为数据源");
		addDir.addActionListener(e -> addWar3Directory());
		top.add(addDir, BorderLayout.EAST);

		hintLabel.setForeground(new Color(150, 80, 0));
		JPanel north = new JPanel(new BorderLayout(4, 4));
		north.add(top, BorderLayout.CENTER);
		north.add(hintLabel, BorderLayout.SOUTH);
		root.add(north, BorderLayout.NORTH);

		// 右侧预览：GL 组件只创建一次，切换列表时仅 setModel，不反复重建 GL 上下文。
		AnimationViewer viewer = null;
		try {
			viewer = new AnimationViewer(ProgramGlobals.getPrefs(), false);
			viewer.setTitle("特效预览（点击左侧列表切换）");
		} catch (Throwable t) {
			System.out.println("[war3导入] 预览组件初始化失败: " + t);
		}
		previewViewer = viewer;
		JComponent right = viewer != null ? viewer
				: new JLabel("预览不可用（OpenGL 初始化失败）", SwingConstants.CENTER);

		modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				new JScrollPane(modelList), right);
		split.setResizeWeight(0.44);
		root.add(split, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		JButton importBtn = new JButton("导入到当前模型");
		importBtn.addActionListener(e -> importSelected());
		JButton closeBtn = new JButton("关闭");
		closeBtn.addActionListener(e -> dialog.dispose());
		buttons.add(importBtn);
		buttons.add(closeBtn);

		// 绑定父级：选一个当前模型的骨骼/节点，导入的发射器会挂到它下面。
		JPanel parentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		parentRow.add(new JLabel("绑定父级："));
		parentCombo.setPreferredSize(new Dimension(300, 24));
		parentCombo.setToolTipText("可直接输入名称搜索节点；导入时按此项建立父子关系");
		parentRow.add(parentCombo);
		parentRow.add(snapPivot);
		JButton refreshParents = new JButton("刷新节点");
		refreshParents.setToolTipText("切换到别的模型后点此重新读取节点列表");
		refreshParents.addActionListener(e -> reloadParentNodes());
		parentRow.add(refreshParents);

		JPanel bottomRow = new JPanel(new BorderLayout(6, 4));
		bottomRow.add(statusLabel, BorderLayout.CENTER);
		bottomRow.add(buttons, BorderLayout.EAST);
		JPanel south = new JPanel(new BorderLayout(0, 4));
		south.add(parentRow, BorderLayout.NORTH);
		south.add(bottomRow, BorderLayout.SOUTH);
		root.add(south, BorderLayout.SOUTH);

		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { applyFilter(); }
			public void removeUpdate(DocumentEvent e) { applyFilter(); }
			public void changedUpdate(DocumentEvent e) { applyFilter(); }
		});
		modelList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				showPreview(modelList.getSelectedValue());
			}
		});
		modelList.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2) importSelected();
			}
		});

		dialog.setContentPane(root);
		dialog.setSize(1080, 620);
		dialog.setLocationRelativeTo(owner);
		reloadModels();
		reloadParentNodes();
	}

	public static void show(Component parent) {
		Frame owner = JOptionPane.getFrameForComponent(parent);
		new War3EffectImporter(owner).dialog.setVisible(true);
	}

	private void status(String text) {
		statusLabel.setText(text);
	}

	/** 当前模型视图里唯一选中的节点（骨骼 / 辅助器 / 特效等）；无或多个选中时返回 null。 */
	private IdObject currentSelectedNode() {
		try {
			ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
			if (modelPanel == null || modelPanel.getModelView() == null) {
				return null;
			}
			Set<IdObject> selected = modelPanel.getModelView().getSelectedIdObjects();
			if (selected != null && selected.size() == 1) {
				return selected.iterator().next();
			}
		} catch (Throwable t) {
			System.out.println("[war3导入] 读取当前选中节点失败: " + t);
		}
		return null;
	}

	/** 从当前模型读取可作为父级的节点，复用 AddParticlePanel 的可搜索下拉逻辑。 */
	private void reloadParentNodes() {
		List<IdObject> nodes = new ArrayList<>();
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel != null && modelPanel.getModel() != null) {
			nodes.addAll(modelPanel.getModel().getIdObjects());
		}
		nodes.add(0, nullParent);
		parentCombo.setModel(new DefaultComboBoxModel<>(nodes.toArray(new IdObject[0])));
		// 第十轮：打开对话框时按当前模型选中的唯一节点预选父级。
		// 必须在 configureSearchable 之前设置——其 DocumentListener 触发的
		// 延迟 refilter / setModel 会把之后设置的选中项覆盖掉。
		IdObject preferred = currentSelectedNode();
		if (preferred != null && nodes.contains(preferred)) {
			parentCombo.setSelectedItem(preferred);
		}
		parentCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				String label;
				if (value == nullParent) {
					label = "无父级（模型根）";
				} else if (value instanceof IdObject) {
					IdObject node = (IdObject) value;
					label = node.getClass().getSimpleName() + "：" + node.getName();
				} else {
					label = String.valueOf(value);
				}
				return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
			}
		});
		parentCombo.setMaximumRowCount(14);
		try {
			// 同包 package-private：可编辑 + 输入过滤 + 回车选第一项
			AddParticlePanel.configureSearchable(parentCombo, nodes, nullParent);
		} catch (Throwable t) {
			System.out.println("[war3导入] 父级搜索初始化失败: " + t);
		}
	}

	private void reloadModels() {
		Set<String> files = GameDataFileSystem.getDefault().getMergedListfile();
		allModels = new ArrayList<>();
		if (files != null) {
			for (String path : files) {
				String lower = path.toLowerCase(Locale.US);
				if (lower.endsWith(".mdx") || lower.endsWith(".mdl")) {
					allModels.add(path);
				}
			}
		}
		Collections.sort(allModels, String.CASE_INSENSITIVE_ORDER);
		if (allModels.size() < 20) {
			hintLabel.setText("未检测到 war3 游戏数据源：点右上「添加本机 war3 目录…」选择含 War3.mpq 的目录，即可浏览全部原生技能特效。");
		} else {
			hintLabel.setText("数据源已就绪：点击左侧任意 .mdx 即在右侧实时预览，选中后点「导入到当前模型」。");
		}
		applyFilter();
	}

	private void applyFilter() {
		String query = searchField.getText().trim().toLowerCase(Locale.US);
		listModel.clear();
		for (String path : allModels) {
			if (query.isEmpty() || path.toLowerCase(Locale.US).contains(query)) {
				listModel.addElement(path);
			}
		}
		status("共 " + allModels.size() + " 个模型，当前显示 " + listModel.size() + " 个。");
	}

	/** 后台解析 .mdx，EDT 里只做 setModel；generation 防止快速连点导致乱序覆盖。 */
	private void showPreview(final String path) {
		if (previewViewer == null || path == null) {
			return;
		}
		final long generation = ++previewGeneration;
		status("正在加载预览…");
		new SwingWorker<EditableModel, Void>() {
			@Override
			protected EditableModel doInBackground() throws Exception {
				try (InputStream stream = GameDataFileSystem.getDefault().getResourceAsStream(path)) {
					return stream == null ? null : MdxUtils.loadEditable(stream);
				}
			}

			@Override
			protected void done() {
				if (generation != previewGeneration) {
					return;
				}
				try {
					EditableModel model = get();
					if (model == null) {
						status("找不到资源：" + path);
						return;
					}
					previewViewer.setModel(model);
					previewViewer.setTitle(path);
					int emitters = model.getParticleEmitter2s().size() + model.getRibbonEmitters().size();
					status("预览：" + path + "（发射器 " + emitters + " 个）");
				} catch (Exception ex) {
					status("预览失败：" + (ex.getCause() == null ? ex : ex.getCause()));
				}
			}
		}.execute();
	}

	private void addWar3Directory() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("选择魔兽争霸3安装目录（含 War3.mpq 等）");
		if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File dir = chooser.getSelectedFile();
		File[] archives = dir.listFiles((d, name) -> {
			String lower = name.toLowerCase(Locale.US);
			return lower.endsWith(".mpq") || lower.endsWith(".w3mod");
		});
		if (archives == null || archives.length == 0) {
			status("该目录下没有找到 .mpq / .w3mod 文件。");
			return;
		}
		List<DataSourceDescriptor> existing = SaveProfile.get().getDataSources();
		List<DataSourceDescriptor> descriptors = existing == null
				? new ArrayList<>() : new ArrayList<>(existing);
		int added = 0;
		for (File archive : archives) {
			MpqDataSourceDescriptor descriptor =
					new MpqDataSourceDescriptor(archive.getAbsolutePath());
			if (!descriptors.contains(descriptor)) {
				descriptors.add(descriptor);
				added++;
			}
		}
		SaveProfile.get().setDataSources(descriptors);
		SaveProfile.save();
		GameDataFileSystem.refresh(descriptors);
		reloadModels();
		status("已添加 " + added + " 个 MPQ 数据源，当前共 " + allModels.size() + " 个模型。");
	}

	private void importSelected() {
		String path = modelList.getSelectedValue();
		if (path == null) {
			status("请先选择一个特效模型。");
			return;
		}
		ModelPanel modelPanel = ProgramGlobals.getCurrentModelPanel();
		if (modelPanel == null || modelPanel.getModelHandler() == null) {
			status("请先打开一个模型，再导入特效。");
			return;
		}
		EditableModel target = modelPanel.getModelHandler().getModel();
		UndoManager undo = modelPanel.getModelHandler().getUndoManager();
		// 对话框是非模态的，期间用户可能切换了模型；父级必须校验仍属于当前 target，
		// 否则会把发射器挂到别的模型的对象上。
		Object chosenParent = parentCombo.getSelectedItem();
		IdObject parent = (chosenParent instanceof IdObject) ? (IdObject) chosenParent : null;
		if (parent == nullParent || (parent != null && !target.getIdObjects().contains(parent))) {
			parent = null;
		}
		final Vec3 parentPivot = parent == null ? null : parent.getPivotPoint();
		final boolean snapToParent = snapPivot.isSelected();
		try (InputStream stream = GameDataFileSystem.getDefault().getResourceAsStream(path)) {
			if (stream == null) {
				status("读取失败：" + path);
				return;
			}
			EditableModel source = MdxUtils.loadEditable(stream);

			// 1) 贴图按路径去重，建立 源索引 -> 目标索引 映射
			Map<Integer, Integer> textureRemap = new HashMap<>();
			List<Bitmap> targetTextures = target.getTextures();
			int sourceIndex = 0;
			for (Bitmap bitmap : source.getTextures()) {
				int newIndex = -1;
				for (int i = 0; i < targetTextures.size(); i++) {
					if (Objects.equals(targetTextures.get(i).getPath(), bitmap.getPath())) {
						newIndex = i;
						break;
					}
				}
				if (newIndex < 0) {
					Bitmap copy = new Bitmap(bitmap.getPath(), bitmap.getReplaceableId());
					copy.setWrapWidth(bitmap.isWrapWidth());
					copy.setWrapHeight(bitmap.isWrapHeight());
					target.add(copy);
					newIndex = targetTextures.size() - 1;
				}
				textureRemap.put(sourceIndex++, newIndex);
			}

			// 2) 材质深拷贝 + Layer.textureId 二次重映射（丝带靠这条链取贴图）
			Map<Integer, Integer> materialRemap = new HashMap<>();
			List<Material> targetMaterials = target.getMaterials();
			int sourceMaterialIndex = 0;
			for (Material sourceMaterial : source.getMaterials()) {
				Material materialCopy = sourceMaterial.deepCopy();
				for (Layer layer : materialCopy.getLayers()) {
					int newTextureId = textureRemap.getOrDefault(layer.getTextureId(), 0);
					layer.setTextureId(newTextureId);
					if (newTextureId >= 0 && newTextureId < targetTextures.size()) {
						layer.setTexture(targetTextures.get(newTextureId));
					}
				}
				target.add(materialCopy);
				materialRemap.put(sourceMaterialIndex++, targetMaterials.size() - 1);
			}

			int imported = 0;
			for (ParticleEmitter2 emitter : source.getParticleEmitter2s()) {
				ParticleEmitter2 copy = new ParticleEmitter2(emitter);
				// 断开源模型父级；若用户选了目标模型的节点则挂上去，并可选落在骨骼位置
				copy.setParent(parent);
				if (snapToParent && parentPivot != null) {
					copy.setPivotPoint(parentPivot);
				}
				copy.setTextureId(textureRemap.getOrDefault(emitter.getTextureId(), 0));
				undo.pushAction(new AddNodeAction(target, copy,
						ModelStructureChangeListener.changeListener).redo());
				imported++;
			}
			for (RibbonEmitter emitter : source.getRibbonEmitters()) {
				RibbonEmitter copy = new RibbonEmitter(emitter);
				copy.setParent(parent);
				if (snapToParent && parentPivot != null) {
					copy.setPivotPoint(parentPivot);
				}
				copy.setMaterialId(materialRemap.getOrDefault(emitter.getMaterialId(), 0));
				copy.updateMaterialRef(target.getMaterials());
				undo.pushAction(new AddNodeAction(target, copy,
						ModelStructureChangeListener.changeListener).redo());
				imported++;
			}

			// 3) 让渲染器重建发射器列表，否则新导入的粒子不会出现在视口里
			try {
				modelPanel.getModelHandler().getRenderModel().updateNodes(true);
			} catch (Throwable t) {
				System.out.println("[war3导入] 渲染器刷新失败: " + t);
			}
			if (ProgramGlobals.getMainPanel() != null) {
				ProgramGlobals.getMainPanel().repaintSelfAndChildren();
			}
			modelPanel.repaintSelfAndRelatedChildren();
			status("已导入 " + imported + " 个发射器（父级："
					+ (parent == null ? "模型根" : parent.getName()) + "，贴图 "
					+ textureRemap.size() + "、材质 " + materialRemap.size()
					+ " 已重映射），可在撤销栈里撤销。"
					+ "动画曲线可能仍引用源模型序列，请在当前模型里检查。");
		} catch (Throwable t) {
			status("导入失败：" + t);
			t.printStackTrace();
		}
	}
}
