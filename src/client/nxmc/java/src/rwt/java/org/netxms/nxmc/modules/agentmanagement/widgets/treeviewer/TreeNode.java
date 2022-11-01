package org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer;

import java.util.ArrayList;
import java.util.List;

public class TreeNode {
	private String name;
	private String value;
	private List<TreeNode> children = new ArrayList<>();
	private TreeNode parent;

	public TreeNode(String inputName, String inputValue) {
		name = inputName;
		value = inputValue;
	}

	protected TreeNode getParent() {
		return parent;
	}

	public TreeNode addChild(TreeNode child) {
		children.add(child);
		child.parent = this;
		return this;
	}

	public List<TreeNode> getChildren() {
		return children;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String toString() {
		return name;
	}
}
