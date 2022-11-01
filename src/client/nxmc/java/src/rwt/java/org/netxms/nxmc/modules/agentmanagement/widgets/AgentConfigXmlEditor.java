package org.netxms.nxmc.modules.agentmanagement.widgets;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer.TreeContentProvider;
import org.netxms.nxmc.modules.agentmanagement.widgets.treeviewer.TreeNode;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class AgentConfigXmlEditor extends Composite {
	TreeViewer treeViewer;
	TreeNode treeNode;
	Display display;

	public AgentConfigXmlEditor(Composite parent, int style, int editorStyle) {
		super(parent, style);
		display = parent.getDisplay();
		setLayout(new FillLayout());
		createTreeViewer(style);
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
				buildTreeNode(currentNode, newNode);
			}
		}
	}

	private void handleContentAsKeyValue(String content) {
		treeNode = new TreeNode("root", "");
		List<String> lines = Arrays.asList(content.split(System.getProperty("line.separator")));
		TreeNode sectionNode = new TreeNode("Core", "");
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
				}
				continue;
			}
			Matcher matcher = Pattern.compile("^([^=]+)=(.+)$").matcher(str);
			if (matcher.find()) {
				TreeNode newNode = new TreeNode(matcher.group(1), matcher.group(2));
				sectionNode.addChild(newNode);
			}
		}
		treeViewer.setInput(treeNode);
	}

	private void createTreeViewer(int style) {
		final Image image = display.getSystemImage(SWT.ICON_INFORMATION);
		treeViewer = new TreeViewer(this, style);

		Tree tree = treeViewer.getTree();
		Listener paintListener = new Listener() {
			public void handleEvent(Event event) {
				switch (event.type) {
				case SWT.MeasureItem: {
					Rectangle rect = image.getBounds();
					event.width += rect.width;
					event.height = Math.max(event.height, rect.height + 2);
					break;
				}
				case SWT.PaintItem: {
					int x = event.x + event.width;
					Rectangle rect = image.getBounds();
					int offset = Math.max(0, (event.height - rect.height) / 2);
					event.gc.drawImage(image, x, event.y + offset);
					break;
				}
				}
			}
		};
		tree.addListener(SWT.MeasureItem, paintListener);
		tree.addListener(SWT.PaintItem, paintListener);
		treeViewer.setContentProvider(new TreeContentProvider());

	}
}
