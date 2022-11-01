package org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

public class TreeContentProvider implements ITreeContentProvider {

	private static final long serialVersionUID = -1833824881073481323L;

	@Override
	public void dispose() {
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}

	@Override
	public Object[] getElements(Object inputElement) {
		if (inputElement instanceof TreeNode) {
			TreeNode treeNode = (TreeNode) inputElement;
			return treeNode.getChildren().toArray();
		}
		return null;
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof TreeNode) {
			TreeNode treeNode = (TreeNode) parentElement;
			return treeNode.getChildren().toArray();
		}
		return null;
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof TreeNode) {
			TreeNode treeNode = (TreeNode) element;
			return treeNode.getParent();
		}
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof TreeNode) {
			TreeNode treeNode = (TreeNode) element;
			return treeNode.getChildren().size() > 0;
		}
		return false;
	}

}
