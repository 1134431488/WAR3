package com.hiveworkshop.rms.ui.gui.modeledit.modelcomponenttree;

import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.util.ModelDependentView;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ModelComponentsView extends ModelDependentView {
	private final JSplitPane splitPane;
	private final JScrollPane componentBrowserTreePane;
	private ModelComponentBrowserTree modelComponentBrowserTree;
	private ModelPanel modelPanel;
	private final JPanel contentsTreeDummy;
	private final JPanel contentsDummy;
	private final JTextField searchField;

	public ModelComponentsView() {
		super("Model", null, new JPanel());

		contentsTreeDummy = new JPanel(new MigLayout());
		contentsTreeDummy.add(new JLabel("..."));
		contentsDummy = new JPanel(new MigLayout());
		contentsDummy.add(new JLabel("..."));

		componentBrowserTreePane = new JScrollPane(contentsTreeDummy);

		// 「模型」页的组件树此前没有任何查找入口。注意：这与「编辑」页的
		// 大纲视图 ModelViewManagingView 是两个不同的组件，别把搜索框加错地方。
		searchField = new JTextField();
		searchField.setToolTipText("搜索模型组件（按 CTRL+F 聚焦此处）");
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { applyFilter(); }
			public void removeUpdate(DocumentEvent e) { applyFilter(); }
			public void changedUpdate(DocumentEvent e) { applyFilter(); }
			private void applyFilter() {
				if (modelComponentBrowserTree != null) {
					modelComponentBrowserTree.setSearchFilter(searchField.getText().trim());
				}
			}
		});

		JPanel treeContainer = new JPanel(new BorderLayout());
		treeContainer.add(searchField, BorderLayout.NORTH);
		treeContainer.add(componentBrowserTreePane, BorderLayout.CENTER);

		// CTRL+F 聚焦搜索框。BlenderHooks 里原先占用 CTRL+F 的「创建面」已改键为
		// CTRL+SHIFT+F，否则全局 KeyEventDispatcher 会把本快捷键吞掉。
		KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
		treeContainer.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(ctrlF, "focusModelSearch");
		treeContainer.getActionMap().put("focusModelSearch", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				searchField.requestFocusInWindow();
			}
		});

		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeContainer, contentsDummy);
		this.setComponent(splitPane);
		splitPane.setDividerLocation(0.2);
	}

	@Override
	public ModelComponentsView setModelPanel(ModelPanel modelPanel){
		this.modelPanel = modelPanel;
		if(modelPanel == null){
			componentBrowserTreePane.setViewportView(contentsTreeDummy);
			modelComponentBrowserTree = null;
			splitPane.setRightComponent(contentsDummy);
		} else {
			modelComponentBrowserTree = new ModelComponentBrowserTree(modelPanel.getModelHandler());

			splitPane.setRightComponent(modelComponentBrowserTree.getComponentsPanel());
			componentBrowserTreePane.setViewportView(modelComponentBrowserTree);

		}
		splitPane.setDividerLocation(0.2);
		splitPane.repaint();
		return this;
	}

	@Override
	public ModelComponentsView reload() {
		if (modelComponentBrowserTree != null) {
			modelComponentBrowserTree.reloadFromModelView().repaint();
		}
		return this;
	}
}
