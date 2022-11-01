package org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

public class NodeFilter extends ViewerFilter {

	private static final long serialVersionUID = -6773528872128377483L;
	private String filterString = null;
	private List<TreeNode> sourceObjects = new ArrayList<>();
	private Map<String, TreeNode> filteredNodes;

	public void addSourceObject(TreeNode treeNode) {
		sourceObjects.add(treeNode);
	}

	public void search(String inputFilterString) {
		filterString = inputFilterString;
		if (filterString != null) {
			filteredNodes = new HashMap<>();
			for (TreeNode node : sourceObjects) {
				if (matchFilterString(node)) {
					filteredNodes.put(node.getId(), node);
					if(node.getParent() != null) {
						filteredNodes.put(node.getParent().getId(), node.getParent());
					}
				}
			}
		} else {
			filteredNodes = null;
		}
	}

	private boolean matchFilterString(TreeNode node) {
		if (filterString == null) {
			return true;
		}
		return (node.getName().toLowerCase().contains(filterString.toLowerCase())
				|| node.getValue().toLowerCase().contains(filterString.toLowerCase()));
	}

	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		if (filteredNodes == null) {
			return true;
		}
		if (element instanceof TreeNode) {
			TreeNode node = (TreeNode) element;
			return filteredNodes.containsKey(node.getId());
		}
		return true;
	}
}
