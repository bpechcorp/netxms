package org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TreeNode {
	private String id;
	private String name;
	private String value;
	private List<TreeNode> children = new ArrayList<>();
	private TreeNode parent;
	private boolean isLeaf;

	public TreeNode(String inputName, String inputValue) {
		name = inputName;
		value = inputValue;
		isLeaf = true;
		id = UUID.randomUUID().toString();
	}

	protected TreeNode getParent() {
		return parent;
	}

	public TreeNode addChild(TreeNode child) {
		children.add(child);
		child.parent = this;
		isLeaf = false;
		return this;
	}

	public List<TreeNode> getChildren() {
		return children;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public boolean isLeaf() {
		return isLeaf;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}
