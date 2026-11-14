package com.hiveworkshop.rms.ui.application.model;

import com.hiveworkshop.rms.editor.model.EditableModel;
import com.hiveworkshop.rms.ui.application.edit.ModelStructureChangeListener;
import com.hiveworkshop.rms.ui.application.edit.mesh.activity.UndoManager;
import com.hiveworkshop.rms.ui.gui.modeledit.ModelHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public abstract class ComponentPanel<T> extends JPanel {
	protected final ModelHandler modelHandler;
	protected final EditableModel model;
	protected final UndoManager undoManager;
	protected final ModelStructureChangeListener changeListener;
	protected T selectedItem;

	public ComponentPanel(ModelHandler modelHandler) {
		this.modelHandler = modelHandler;
		this.model = modelHandler.getModel();
		this.undoManager = modelHandler.getUndoManager();
		this.changeListener = ModelStructureChangeListener.changeListener;
	}

	public abstract ComponentPanel<T> setSelectedItem(T itemToSelect);

	protected JButton getDeleteButton(ActionListener actionListener) {
		JButton deleteButton = new JButton("Delete");
		deleteButton.setBackground(Color.RED);
		// 移除白色前景：InfoNode 主题会忽略 setBackground(Color.RED)、但保留白色前景，
		// 导致白字配浅色按钮看不见。改为使用主题默认（深色）前景即可见。
		// deleteButton.setForeground(Color.WHITE);
		deleteButton.addActionListener(actionListener);
		return deleteButton;
	}

	protected JButton getXButton(ActionListener actionListener) {
		JButton deleteButton = new JButton("X");
		deleteButton.setBackground(Color.RED);
		// 移除白色前景：InfoNode 主题会忽略 setBackground(Color.RED)、但保留白色前景，
		// 导致白字配浅色按钮看不见。改为使用主题默认（深色）前景即可见。
		// deleteButton.setForeground(Color.WHITE);
		deleteButton.addActionListener(actionListener);
		return deleteButton;
	}

	protected JButton getButton(String text, ActionListener actionListener) {
		JButton button = new JButton(text);
		button.addActionListener(actionListener);
		return button;
	}
}
