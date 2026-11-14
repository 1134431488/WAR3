package com.hiveworkshop.rms.ui.application;

import com.hiveworkshop.rms.editor.model.Bitmap;
import com.hiveworkshop.rms.editor.model.EditableModel;
import com.hiveworkshop.rms.editor.model.ParticleEmitter2;
import com.hiveworkshop.rms.editor.model.RibbonEmitter;
import com.hiveworkshop.rms.filesystem.GameDataFileSystem;
import com.hiveworkshop.rms.filesystem.sources.DataSourceDescriptor;
import com.hiveworkshop.rms.filesystem.sources.MpqDataSourceDescriptor;
import com.hiveworkshop.rms.parsers.mdlx.util.MdxUtils;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.ui.preferences.SaveProfile;

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
 * 连同所需贴图导入当前模型，导入后即可像普通节点一样在编辑器里修改。
 *
 * 设计说明：
 * - 数据源沿用 GameDataFileSystem.getMergedListfile()，不自己解析 MPQ；
 *   未配置游戏目录时提供「添加本机 war3 目录…」按钮，扫描目录下的 War3*.mpq 并注册。
 * - 贴图按路径去重后追加到目标模型，并重映射 ParticleEmitter2.textureId，
 *   否则导入的粒子会引用目标模型里不存在的贴图索引。
 * - 只搬发射器与贴图，不搬源模型的动画序列；拷贝对象自带的 AnimFlag 可能仍引用
 *   源模型的 Sequence，界面上会提示用户在当前模型里检查/重建动画。
 */
public final class War3EffectImporter {

	private final JDialog dialog;
	private final DefaultListModel<String> listModel = new DefaultListModel<>();
	private final JList<String> modelList = new JList<>(listModel);
	private final JTextField searchField = new JTextField();
	private final JLabel statusLabel = new JLabel(" ");
	private final JLabel hintLabel = new JLabel(" ");
	private List<String> allModels = new ArrayList<>();

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

		modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		root.add(new JScrollPane(modelList), BorderLayout.CENTER);

		JPanel bottom = new JPanel(new BorderLayout(6, 4));
		bottom.add(statusLabel, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		JButton importBtn = new JButton("导入到当前模型");
		importBtn.addActionListener(e -> importSelected());
		JButton closeBtn = new JButton("关闭");
		closeBtn.addActionListener(e -> dialog.dispose());
		buttons.add(importBtn);
		buttons.add(closeBtn);
		bottom.add(buttons, BorderLayout.EAST);
		root.add(bottom, BorderLayout.SOUTH);

		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { applyFilter(); }
			public void removeUpdate(DocumentEvent e) { applyFilter(); }
			public void changedUpdate(DocumentEvent e) { applyFilter(); }
		});
		modelList.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2) importSelected();
			}
		});

		dialog.setContentPane(root);
		dialog.setSize(820, 560);
		dialog.setLocationRelativeTo(owner);
		reloadModels();
	}

	public static void show(Component parent) {
		Frame owner = JOptionPane.getFrameForComponent(parent);
		new War3EffectImporter(owner).dialog.setVisible(true);
	}

	private void status(String text) {
		statusLabel.setText(text);
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
			hintLabel.setText("数据源已就绪：可搜索雷电 / 霜冻 / 冲击波等技能特效模型，选中后点「导入到当前模型」。");
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
		try (InputStream stream = GameDataFileSystem.getDefault().getResourceAsStream(path)) {
			if (stream == null) {
				status("读取失败：" + path);
				return;
			}
			EditableModel source = MdxUtils.loadEditable(stream);

			// 贴图按路径去重，建立 源索引 -> 目标索引 映射
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

			int imported = 0;
			for (ParticleEmitter2 emitter : source.getParticleEmitter2s()) {
				ParticleEmitter2 copy = new ParticleEmitter2(emitter);
				copy.setTextureId(textureRemap.getOrDefault(emitter.getTextureId(), 0));
				target.add(copy);
				imported++;
			}
			for (RibbonEmitter emitter : source.getRibbonEmitters()) {
				RibbonEmitter copy = new RibbonEmitter(emitter);
				target.add(copy);
				imported++;
			}

			if (ProgramGlobals.getMainPanel() != null) {
				ProgramGlobals.getMainPanel().repaintSelfAndChildren();
			}
			modelPanel.repaintSelfAndRelatedChildren();
			status("已导入 " + imported + " 个发射器、重映射 " + textureRemap.size()
					+ " 张贴图。动画曲线可能引用源模型序列，请在当前模型里检查。");
		} catch (Throwable t) {
			status("导入失败：" + t);
			t.printStackTrace();
		}
	}
}
