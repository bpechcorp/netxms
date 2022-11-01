package org.netxms.nxmc.modules.agentmanagement.widgets;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer.NodeFilter;
import org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer.TreeContentProvider;
import org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer.TreeNode;
import org.netxms.nxmc.resources.SharedIcons;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xnap.commons.i18n.I18n;

public class AgentConfigXmlEditor extends Composite {

	private I18n i18n = LocalizationHelper.getI18n(AgentConfigXmlEditor.class);
	private TreeViewer treeViewer;

	TreeViewerColumn keyColumn;
	TreeViewerColumn valueColumn;

	private TreeNode treeNode;

	private Action actionRename;
	private Action actionChangeValue;
	private Action actionAddNode;
	private Action actionDeleteNode;

	private Text filterText;
	private NodeFilter filter;

	public AgentConfigXmlEditor(Composite parent, int style, int editorStyle) {
		super(parent, style);
		setLayout(new GridLayout());
		createFilterText();
		createTreeViewer(style);
		createActions();
		createContextMenu();
	}

	public void setContent(String content) {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new InputSource(new StringReader(content.replaceAll("\n|\r|\t", ""))));
			handleContentAsXml(doc);
			return;
		} catch (Exception e) {
		}
		handleContentAsKeyValue(content);
	}

	private void handleContentAsXml(Document doc) {
		treeNode = new TreeNode("root", "");
		buildTreeNode(doc.getDocumentElement(), treeNode);
		treeViewer.setInput(treeNode);
	}

	private void buildTreeNode(Node node, TreeNode treeNode) {
		NodeList childNodes = node.getChildNodes();
		int numberOfNodes = childNodes.getLength();
		for (int i = 0; i < numberOfNodes; i++) {
			Node currentNode = childNodes.item(i);
			if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
				TreeNode newNode = new TreeNode(currentNode.getNodeName(), currentNode.getTextContent());
				treeNode.addChild(newNode);
				filter.addSourceObject(newNode);
				buildTreeNode(currentNode, newNode);
			}
		}
	}

	private void handleContentAsKeyValue(String content) {
		treeNode = new TreeNode("root", "");
		List<String> lines = Arrays.asList(content.split(System.getProperty("line.separator")));
		TreeNode sectionNode = new TreeNode("Core", "");
		filter.addSourceObject(sectionNode);
		treeNode.addChild(sectionNode);
		for (String line : lines) {
			String str = StringUtils.trimToEmpty(line);
			if (StringUtils.isBlank(str) || line.matches("^#.*")) {
				continue;
			}
			if (str.matches("^\\[.+\\]$") && !"[Core]".equals(str)) {
				Matcher matcher = Pattern.compile("^\\[(.+)\\]$").matcher(str);
				if (matcher.find()) {
					sectionNode = new TreeNode(matcher.group(1), "");
					treeNode.addChild(sectionNode);
					filter.addSourceObject(sectionNode);
				}
				continue;
			}
			Matcher matcher = Pattern.compile("^([^=]+)=(.+)$").matcher(str);
			if (matcher.find()) {
				TreeNode newNode = new TreeNode(matcher.group(1), matcher.group(2));
				filter.addSourceObject(newNode);
				sectionNode.addChild(newNode);
			}
		}
		treeViewer.setInput(treeNode);
	}

	private void createContextMenu() {
		MenuManager menuMgr = new MenuManager();
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {

			private static final long serialVersionUID = -6868735989779702531L;

			public void menuAboutToShow(IMenuManager mgr) {
				fillContextMenu(mgr);
			}
		});

		Menu menu = menuMgr.createContextMenu(treeViewer.getControl());
		treeViewer.getControl().setMenu(menu);
	}

	private void createActions() {
		actionRename = new Action(i18n.tr("Rename"), SharedIcons.EDIT) {

			private static final long serialVersionUID = 8490478296254079716L;

			@Override
			public void run() {
			}
		};
		actionRename.setId("AgentConfigXmlEditor.rename");

		actionChangeValue = new Action(i18n.tr("Change value"), SharedIcons.SAVE) {

			private static final long serialVersionUID = -5491629062808525113L;

			@Override
			public void run() {
			}
		};
		actionChangeValue.setId("AgentConfigXmlEditor.changeValue");

		actionAddNode = new Action(i18n.tr("Add new node"), SharedIcons.ADD_OBJECT) {

			private static final long serialVersionUID = 8540983913564599102L;

			@Override
			public void run() {
			}
		};
		actionAddNode.setId("AgentConfigXmlEditor.addNode");

		actionDeleteNode = new Action(i18n.tr("Delete"), SharedIcons.DELETE_OBJECT) {

			private static final long serialVersionUID = -6513734773438689108L;

			@Override
			public void run() {
			}
		};
		actionDeleteNode.setId("AgentConfigXmlEditor.deleteNode");
	}

	private void fillContextMenu(IMenuManager manager) {
		IStructuredSelection selection = treeViewer.getStructuredSelection();
		if (selection.isEmpty())
			return;

		Object element = selection.getFirstElement();
		if (!(element instanceof TreeNode)) {
			return;
		}
		TreeNode treeNode = (TreeNode) element;
		if (treeNode.isLeaf()) {
			manager.add(actionRename);
			manager.add(actionChangeValue);
			manager.add(actionDeleteNode);
		} else {
			manager.add(actionAddNode);
			if (!"Core".equals(treeNode.getName())) {
				manager.add(actionRename);
			}
			manager.add(actionChangeValue);
			manager.add(actionDeleteNode);
		}
	}

	private void createTreeViewer(int style) {
		Composite treeViewerArea = new Composite(this, SWT.BORDER);

		GridLayout treeViewerLayout = new GridLayout();
		treeViewerLayout.numColumns = 1;
		treeViewerLayout.marginBottom = 0;
		treeViewerLayout.marginTop = 0;
		treeViewerLayout.marginLeft = 0;
		treeViewerLayout.marginRight = 0;
		treeViewerArea.setLayout(treeViewerLayout);
		treeViewerArea.setLayoutData(new GridData(GridData.FILL_BOTH));

		treeViewer = new TreeViewer(treeViewerArea, style);
		treeViewer.getTree().setLayoutData(new GridData(GridData.FILL_BOTH));
		treeViewer.setContentProvider(new TreeContentProvider());
		filter = new NodeFilter();
		treeViewer.addFilter(filter);

		TreeViewerColumn keyColumn = new TreeViewerColumn(treeViewer, SWT.NONE);
		keyColumn.getColumn().setText("Key");
		keyColumn.getColumn().setWidth(300);
		keyColumn.setLabelProvider(new CellLabelProvider() {

			private static final long serialVersionUID = -7007983541265880812L;

			@Override
			public void update(ViewerCell cell) {
				Object element = cell.getElement();
				if (element instanceof TreeNode) {
					TreeNode treeNode = (TreeNode) element;
					cell.setText(treeNode.getName());
				}
			}
		});

		TreeViewerColumn valueColumn = new TreeViewerColumn(treeViewer, SWT.NONE);
		valueColumn.getColumn().setText("Value");
		valueColumn.getColumn().setWidth(300);
		valueColumn.setLabelProvider(new CellLabelProvider() {

			private static final long serialVersionUID = -7007983541265880812L;

			@Override
			public void update(ViewerCell cell) {
				Object element = cell.getElement();
				if (element instanceof TreeNode) {
					TreeNode treeNode = (TreeNode) element;
					cell.setText(treeNode.getValue());
				}
			}
		});

		treeViewer.addTreeListener(new ITreeViewerListener() {

			@Override
			public void treeExpanded(TreeExpansionEvent event) {
				resize();
			}

			@Override
			public void treeCollapsed(TreeExpansionEvent event) {
				resize();
			}
		});
	}

	private void resize() {
		if (keyColumn != null && keyColumn.getColumn() != null) {
			keyColumn.getColumn().pack();
		}
		if (valueColumn != null && valueColumn.getColumn() != null) {
			valueColumn.getColumn().pack();
		}
	}

	private void createFilterText() {
		Composite textArea = new Composite(this, SWT.BORDER);

		GridLayout textLayout = new GridLayout();
		textLayout.numColumns = 1;
		textLayout.marginBottom = 0;
		textLayout.marginTop = 0;
		textLayout.marginLeft = 0;
		textLayout.marginRight = 0;
		textArea.setLayout(textLayout);

		GridData gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.verticalAlignment = SWT.CENTER;
		gd.grabExcessHorizontalSpace = true;
		textArea.setLayoutData(gd);

		filterText = new Text(textArea, SWT.SINGLE);
		filterText.setTextLimit(64);
		filterText.setMessage(i18n.tr("Filter is empty"));
		filterText.setLayoutData(gd);
		filterText.addModifyListener(new ModifyListener() {

			@Override
			public void modifyText(ModifyEvent e) {
				String filterString = filterText.getText();
				filter.search(filterString);
				treeViewer.refresh();
				treeViewer.expandAll();
			}
		});
	}
}
