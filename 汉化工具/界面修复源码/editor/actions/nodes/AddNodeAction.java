package com.hiveworkshop.rms.editor.actions.nodes;

import com.hiveworkshop.rms.editor.actions.UndoAction;
import com.hiveworkshop.rms.editor.model.EditableModel;
import com.hiveworkshop.rms.editor.model.IdObject;
import com.hiveworkshop.rms.ui.application.edit.ModelStructureChangeListener;

public class AddNodeAction implements UndoAction {
	private final EditableModel model;
	private final IdObject node;
	// 构造时快照父级：EditableModel.add/remove 只维护模型节点列表，不联动 IdObject.setParent，
	// 而父子关系由 setParent() 维护（写入 parent.childrenNodes）。若不在此同步解除/恢复，
	// 撤销后节点仍残留于父级 childrenNodes（保存会损坏文件），重做时可能重复挂接。
	private final IdObject parent;
	private final ModelStructureChangeListener changeListener;

	public AddNodeAction(EditableModel model, IdObject node, ModelStructureChangeListener changeListener) {
		this.model = model;
		this.node = node;
		this.parent = node.getParent();
		this.changeListener = changeListener;
	}

	@Override
	public UndoAction undo() {
		model.remove(node);
		// 撤销时从父级 childrenNodes 解绑，避免节点成为孤儿（setParent(null) 幂等）
		if (parent != null && node.getParent() == parent) {
			node.setParent(null);
		}
		if (changeListener != null) {
			changeListener.nodesUpdated();
		}
		return this;
	}

	@Override
	public UndoAction redo() {
		model.add(node);
		// 重做时恢复父级绑定（setParent 幂等：重复调用不会产生重复条目）
		if (parent != null && node.getParent() != parent) {
			node.setParent(parent);
		}
		if (changeListener != null) {
			changeListener.nodesUpdated();
		}
		return this;
	}

	@Override
	public String actionName() {
		return "add " + node.getClass().getSimpleName();
	}
}
