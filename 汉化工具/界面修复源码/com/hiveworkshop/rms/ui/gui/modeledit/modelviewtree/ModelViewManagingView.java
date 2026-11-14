package com.hiveworkshop.rms.ui.gui.modeledit.modelviewtree;

import com.hiveworkshop.rms.ui.gui.modeledit.ModelPanel;
import com.hiveworkshop.rms.util.ModelDependentView;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class ModelViewManagingView extends ModelDependentView {
	ComponentThingTree modelViewManagingTree2;
	JScrollPane modelEditingTreePane;
	ModelPanel modelPanel;
	JPanel jPanel;
	JTextField searchField;
	String name;

	public ModelViewManagingView() {
		super("Outliner", null, new JPanel());
		this.name = "Outliner";
		jPanel = new JPanel();
		jPanel.add(new JLabel("..."));
		modelEditingTreePane = new JScrollPane(jPanel);
		modelViewManagingTree2 = new ComponentThingTree();

		searchField = new JTextField();
		searchField.setToolTipText("搜索节点名称（按 CTRL+F 聚焦此处）");
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { applyFilter(); }
			public void removeUpdate(DocumentEvent e) { applyFilter(); }
			public void changedUpdate(DocumentEvent e) { applyFilter(); }
			private void applyFilter() {
				modelViewManagingTree2.setSearchFilter(searchField.getText().trim());
			}
		});

		JPanel container = new JPanel(new BorderLayout());
		container.add(searchField, BorderLayout.NORTH);
		container.add(modelEditingTreePane, BorderLayout.CENTER);

		setComponent(container);

		// CTRL+F 聚焦搜索框（全局 CTRL+F 空闲，无冲突）
		KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
		container.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ctrlF, "focusSearch");
		container.getActionMap().put("focusSearch", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				searchField.requestFocusInWindow();
			}
		});
	}

	@Override
	public ModelViewManagingView setModelPanel(ModelPanel modelPanel){
		this.modelPanel = modelPanel;
		if(modelPanel == null) {
			modelEditingTreePane.setViewportView(jPanel);
		} else {
			modelViewManagingTree2.setModel(modelPanel.getModelHandler());
			modelEditingTreePane.setViewportView(modelViewManagingTree2);
			modelViewManagingTree2.expandMeshNode();
		}
		System.out.println("name: " + name + ", panel: " + modelPanel);
		return this;
	}

	@Override
	public ModelViewManagingView reload() {
		if (modelPanel != null) {
			modelViewManagingTree2.reloadTree().repaint();
		}
		return this;
	}
}
