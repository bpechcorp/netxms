/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2022 Victor Kirhenshtein
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.netxms.ui.eclipse.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.preference.ColorSelector;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.rap.rwt.scripting.ClientListener;
import org.eclipse.rap.rwt.widgets.WidgetUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scrollable;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.dialogs.PropertyPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.netxms.ui.eclipse.console.Activator;
import org.netxms.ui.eclipse.console.Messages;
import org.netxms.ui.eclipse.console.resources.ThemeEngine;
import org.netxms.ui.eclipse.shared.ConsoleSharedData;
import org.netxms.ui.eclipse.widgets.LabeledText;
import org.netxms.ui.eclipse.widgets.SortableTableViewer;
import org.netxms.ui.eclipse.widgets.SortableTreeViewer;
import org.netxms.ui.eclipse.widgets.helpers.MsgProxyWidget;

/**
 * Utility class for simplified creation of widgets
 */
public class WidgetHelper
{
	public static final int INNER_SPACING = 2;
	public static final int OUTER_SPACING = 4;
	public static final int DIALOG_WIDTH_MARGIN = 10;
	public static final int DIALOG_HEIGHT_MARGIN = 10;
	public static final int DIALOG_SPACING = 5;
	public static final int BUTTON_WIDTH_HINT = 90;
	public static final int WIDE_BUTTON_WIDTH_HINT = 120;
	public static final String DEFAULT_LAYOUT_DATA = "WidgetHelper::default_layout_data"; //$NON-NLS-1$

	private static final Pattern patternOnlyCharNum = Pattern.compile("[a-zA-Z0-9]+");
	private static final Pattern patternAllDotsAtEnd = Pattern.compile("[.]*$");
	private static final Pattern patternCharsAndNumbersAtEnd = Pattern.compile("[a-zA-Z0-9]*$");
	private static final Pattern patternCharsAndNumbersAtStart = Pattern.compile("^[a-zA-Z0-9]*");

   /**
    * Get character(s) to represent new line in text.
    *
    * @return character(s) to represent new line in text
    */
   public static String getNewLineCharacters()
   {
      return "\r\n";
   }

	/**
    * Create pair of label and input field, with label above
	 * 
	 * @param parent Parent composite
	 * @param flags Flags for Text creation
	 * @param widthHint Width hint for text control
	 * @param labelText Label's text
	 * @param initialText Initial text for input field (may be null)
	 * @param layoutData Layout data for label/input pair. If null, default GridData will be assigned.
	 * @return Created Text object
	 */
   public static Text createLabeledText(final Composite parent, int flags, int widthHint, final String labelText, final String initialText, Object layoutData)
	{
		Composite group = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = INNER_SPACING;
		layout.horizontalSpacing = 0;
		layout.marginTop = 0;
		layout.marginBottom = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		group.setLayout(layout);

		if (layoutData != DEFAULT_LAYOUT_DATA)
		{
			group.setLayoutData(layoutData);
		}
		else
		{
			GridData gridData = new GridData();
			gridData.horizontalAlignment = GridData.FILL;
			gridData.grabExcessHorizontalSpace = true;
			group.setLayoutData(gridData);
		}
		
		Label label = new Label(group, SWT.NONE);
		label.setText(labelText);

		Text text = new Text(group, flags);
		if (initialText != null)
			text.setText(initialText);
		GridData gridData = new GridData();
		gridData.horizontalAlignment = GridData.FILL;
		gridData.grabExcessHorizontalSpace = true;
		gridData.verticalAlignment = GridData.FILL;
		gridData.grabExcessVerticalSpace = true;
		gridData.widthHint = widthHint;
		text.setLayoutData(gridData);		
		
		return text;
	}

	/**
    * Create pair of label and combo box, with label above
	 * 
	 * @param parent Parent composite
	 * @param flags Flags for Text creation
	 * @param labelText Label's text
	 * @param layoutData Layout data for label/input pair. If null, default GridData will be assigned.
	 * @return Created Combo object
	 */
	public static Combo createLabeledCombo(final Composite parent, int flags, final String labelText, Object layoutData)
	{
      return createLabeledCombo(parent, flags, labelText, layoutData, null, null);
	}

   /**
    * Create pair of label and combo box, with label above
    * 
    * @param parent Parent composite
    * @param flags Flags for Text creation
    * @param labelText Label's text
    * @param layoutData Layout data for label/input pair. If null, default GridData will be assigned.
    * @param toolkit form toolkit to be used for control creation. May be null.
    * @return Created Combo object
    */
   public static Combo createLabeledCombo(final Composite parent, int flags, final String labelText, Object layoutData, FormToolkit toolkit)
   {
      return createLabeledCombo(parent, flags, labelText, layoutData, toolkit, null);
   }

	/**
    * Create pair of label and combo box, with label above
    * 
    * @param parent Parent composite
    * @param flags Flags for Text creation
    * @param labelText Label's text
    * @param layoutData Layout data for label/input pair. If null, default GridData will be assigned.
    * @param toolkit form toolkit to be used for control creation. May be null.
    * @param backgroundColor background color for surrounding composite and label (null for default)
    * @return Created Combo object
    */
   public static Combo createLabeledCombo(final Composite parent, int flags, final String labelText, Object layoutData,
         FormToolkit toolkit, Color backgroundColor)
   {
      Composite group = (toolkit != null) ? toolkit.createComposite(parent) : new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = INNER_SPACING;
		layout.horizontalSpacing = 0;
		layout.marginTop = 0;
		layout.marginBottom = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		group.setLayout(layout);

      if (backgroundColor != null)
         group.setBackground(backgroundColor);

		if (layoutData != DEFAULT_LAYOUT_DATA)
		{
			group.setLayoutData(layoutData);
		}
		else
		{
			GridData gridData = new GridData();
			gridData.horizontalAlignment = GridData.FILL;
			gridData.grabExcessHorizontalSpace = true;
			group.setLayoutData(gridData);
		}
		
      Label label;
		if (toolkit != null)
		{
         label = toolkit.createLabel(group, labelText);
		}
		else
		{
         label = new Label(group, SWT.NONE);
			label.setText(labelText);
		}
      if (backgroundColor != null)
         label.setBackground(backgroundColor);

		Combo combo = new Combo(group, flags);
		GridData gridData = new GridData();
		gridData.horizontalAlignment = GridData.FILL;
		gridData.grabExcessHorizontalSpace = true;
		combo.setLayoutData(gridData);
		
		if (toolkit != null)
			toolkit.adapt(combo);
		
		return combo;
	}

	/**
    * Create pair of label and spinner, with label above
	 * 
	 * @param parent Parent composite
	 * @param flags Flags for Text creation
	 * @param labelText Label's text
	 * @param minVal minimal spinner value
	 * @param maxVal maximum spinner value
	 * @param layoutData Layout data for label/input pair. If null, default GridData will be assigned.
	 * @return Created Spinner object
	 */
	public static Spinner createLabeledSpinner(final Composite parent, int flags, final String labelText, int minVal, int maxVal, Object layoutData)
	{
		Composite group = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = INNER_SPACING;
		layout.horizontalSpacing = 0;
		layout.marginTop = 0;
		layout.marginBottom = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		group.setLayout(layout);

		if (layoutData != DEFAULT_LAYOUT_DATA)
		{
			group.setLayoutData(layoutData);
		}
		else
		{
			GridData gridData = new GridData();
			gridData.horizontalAlignment = GridData.FILL;
			gridData.grabExcessHorizontalSpace = true;
			group.setLayoutData(gridData);
		}
		
		Label label = new Label(group, SWT.NONE);
		label.setText(labelText);

		Spinner spinner = new Spinner(group, flags);
		GridData gridData = new GridData();
		gridData.horizontalAlignment = GridData.FILL;
		gridData.grabExcessHorizontalSpace = true;
		spinner.setLayoutData(gridData);
		
		spinner.setMinimum(minVal);
		spinner.setMaximum(maxVal);
		
		return spinner;
	}

	/**
    * Create pair of label and color selector, with label above
	 * 
	 * @param parent Parent composite
	 * @param labelText Label's text
	 * @param layoutData Layout data for label/input pair. If null, default GridData will be assigned.
	 * @return Created Text object
	 */
	public static ColorSelector createLabeledColorSelector(final Composite parent, final String labelText, Object layoutData)
	{
		Composite group = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = INNER_SPACING;
		layout.horizontalSpacing = 0;
		layout.marginTop = 0;
		layout.marginBottom = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		group.setLayout(layout);

		if (layoutData != DEFAULT_LAYOUT_DATA)
		{
			group.setLayoutData(layoutData);
		}
		else
		{
			GridData gridData = new GridData();
			gridData.horizontalAlignment = GridData.FILL;
			group.setLayoutData(gridData);
		}
		
		Label label = new Label(group, SWT.NONE);
		label.setText(labelText);

		ColorSelector cs = new ColorSelector(group);
		GridData gridData = new GridData();
		gridData.horizontalAlignment = GridData.FILL;
		gridData.grabExcessHorizontalSpace = true;
		cs.getButton().setLayoutData(gridData);		

		return cs;
	}

	/**
	 * Create labeled control using factory.
	 * 
	 * @param parent parent composite
	 * @param flags flags for control being created
	 * @param factory control factory
	 * @param labelText Label's text
	 * @param layoutData Layout data for label/input pair. If null, default GridData will be assigned.
	 * @return created control
	 */
	public static Control createLabeledControl(Composite parent, int flags, WidgetFactory factory, String labelText, Object layoutData)
	{
		Composite group = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = INNER_SPACING;
		layout.horizontalSpacing = 0;
		layout.marginTop = 0;
		layout.marginBottom = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		group.setLayout(layout);

		if (layoutData != DEFAULT_LAYOUT_DATA)
		{
			group.setLayoutData(layoutData);
		}
		else
		{
			GridData gridData = new GridData();
			gridData.horizontalAlignment = GridData.FILL;
			gridData.grabExcessHorizontalSpace = true;
			group.setLayoutData(gridData);
		}

		Label label = new Label(group, SWT.NONE);
		label.setText(labelText);

		final Control widget = factory.createControl(group, flags);
      widget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		return widget;
	}

	/**
	 * Save settings of table viewer columns
	 * 
	 * @param table Table control
	 * @param settings Dialog settings object
	 * @param prefix Prefix for properties
	 */
	public static void saveColumnSettings(Table table, IDialogSettings settings, String prefix)
	{
		TableColumn[] columns = table.getColumns();
		for(int i = 0; i < columns.length; i++)
		{
         Object id = columns[i].getData("ID");
         if ((id == null) || !(id instanceof Integer))
            id = Integer.valueOf(i);
			settings.put(prefix + "." + id + ".width", columns[i].getWidth()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 * Restore settings of table viewer columns previously saved by call to WidgetHelper.saveColumnSettings
	 * 
	 * @param table Table control
	 * @param settings Dialog settings object
	 * @param prefix Prefix for properties
	 */
	public static void restoreColumnSettings(Table table, IDialogSettings settings, String prefix)
	{
		TableColumn[] columns = table.getColumns();
		for(int i = 0; i < columns.length; i++)
		{
			try
			{
			   Object id = columns[i].getData("ID");
			   if ((id == null) || !(id instanceof Integer))
			      id = Integer.valueOf(i);
				int w = settings.getInt(prefix + "." + id + ".width"); //$NON-NLS-1$ //$NON-NLS-2$
				columns[i].setWidth((w > 0) ? w : 50);
			}
			catch(NumberFormatException e)
			{
			}
		}
	}

	/**
	 * Save settings of tree viewer columns
	 * 
	 * @param table Table control
	 * @param settings Dialog settings object
	 * @param prefix Prefix for properties
	 */
	public static void saveColumnSettings(Tree tree, IDialogSettings settings, String prefix)
	{
		TreeColumn[] columns = tree.getColumns();
		for(int i = 0; i < columns.length; i++)
		{
         Object id = columns[i].getData("ID");
         if ((id == null) || !(id instanceof Integer))
            id = Integer.valueOf(i);
			settings.put(prefix + "." + id + ".width", columns[i].getWidth()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	/**
	 * Restore settings of tree viewer columns previously saved by call to WidgetHelper.saveColumnSettings
	 * 
	 * @param table Table control
	 * @param settings Dialog settings object
	 * @param prefix Prefix for properties
	 */
	public static void restoreColumnSettings(Tree tree, IDialogSettings settings, String prefix)
	{
		TreeColumn[] columns = tree.getColumns();
		for(int i = 0; i < columns.length; i++)
		{
			try
			{
	         Object id = columns[i].getData("ID");
	         if ((id == null) || !(id instanceof Integer))
	            id = Integer.valueOf(i);
				int w = settings.getInt(prefix + "." + id + ".width"); //$NON-NLS-1$ //$NON-NLS-2$
				columns[i].setWidth(w);
			}
			catch(NumberFormatException e)
			{
			}
		}
	}
	
	/**
	 * Save settings for sortable table viewer
	 * @param viewer Viewer
	 * @param settings Dialog settings object
	 * @param prefix Prefix for properties
	 */
	public static void saveTableViewerSettings(SortableTableViewer viewer, IDialogSettings settings, String prefix)
	{
		final Table table = viewer.getTable();
		saveColumnSettings(table, settings, prefix);
		TableColumn column = table.getSortColumn();
		settings.put(prefix + ".sortColumn", (column != null) ? (Integer)column.getData("ID") : -1); //$NON-NLS-1$ //$NON-NLS-2$
		settings.put(prefix + ".sortDirection", table.getSortDirection()); //$NON-NLS-1$
	}
	
	/**
	 * Restore settings for sortable table viewer
	 * @param viewer Viewer
	 * @param settings Dialog settings object
	 * @param prefix Prefix for properties
	 */
	public static void restoreTableViewerSettings(SortableTableViewer viewer, IDialogSettings settings, String prefix)
	{
		final Table table = viewer.getTable();
		restoreColumnSettings(table, settings, prefix);
		try
		{
			table.setSortDirection(settings.getInt(prefix + ".sortDirection")); //$NON-NLS-1$
			int column = settings.getInt(prefix + ".sortColumn"); //$NON-NLS-1$
			if (column >= 0)
			{
				table.setSortColumn(viewer.getColumnById(column));
			}
		}
		catch(NumberFormatException e)
		{
		}
	}
	
	/**
	 * Save settings for sortable tree viewer
	 * @param viewer Viewer
	 * @param settings Dialog settings object
	 * @param prefix Prefix for properties
	 */
	public static void saveTreeViewerSettings(SortableTreeViewer viewer, IDialogSettings settings, String prefix)
	{
		final Tree tree = viewer.getTree();
		saveColumnSettings(tree, settings, prefix);
		TreeColumn column = tree.getSortColumn();
		settings.put(prefix + ".sortColumn", (column != null) ? (Integer)column.getData("ID") : -1); //$NON-NLS-1$ //$NON-NLS-2$
		settings.put(prefix + ".sortDirection", tree.getSortDirection()); //$NON-NLS-1$
	}
	
	/**
	 * Restore settings for sortable table viewer
	 * @param viewer Viewer
	 * @param settings Dialog settings object
	 * @param prefix Prefix for properties
	 */
	public static void restoreTreeViewerSettings(SortableTreeViewer viewer, IDialogSettings settings, String prefix)
	{
		final Tree tree = viewer.getTree();
		restoreColumnSettings(tree, settings, prefix);
		try
		{
			tree.setSortDirection(settings.getInt(prefix + ".sortDirection")); //$NON-NLS-1$
			int column = settings.getInt(prefix + ".sortColumn"); //$NON-NLS-1$
			if (column >= 0)
			{
				tree.setSortColumn(viewer.getColumnById(column));
			}
		}
		catch(NumberFormatException e)
		{
		}
	}

	/**
	 * Wrapper for saveTableViewerSettings/saveTreeViewerSettings
	 * 
	 * @param viewer
	 * @param settings
	 * @param prefix
	 */
	public static void saveColumnViewerSettings(ColumnViewer viewer, IDialogSettings settings, String prefix)
	{
		if (viewer instanceof SortableTableViewer)
		{
			saveTableViewerSettings((SortableTableViewer)viewer, settings, prefix);
		}
		else if (viewer instanceof SortableTreeViewer)
		{
			saveTreeViewerSettings((SortableTreeViewer)viewer, settings, prefix);
		}
	}
	
	/**
	 * Wrapper for restoreTableViewerSettings/restoreTreeViewerSettings
	 * 
	 * @param viewer table or tree viewer
	 * @param settings
	 * @param prefix
	 */
	public static void restoreColumnViewerSettings(ColumnViewer viewer, IDialogSettings settings, String prefix)
	{
		if (viewer instanceof SortableTableViewer)
		{
			restoreTableViewerSettings((SortableTableViewer)viewer, settings, prefix);
		}
		else if (viewer instanceof SortableTreeViewer)
		{
			restoreTreeViewerSettings((SortableTreeViewer)viewer, settings, prefix);
		}
	}

	/**
	 * Copy given text to clipboard
	 * 
	 * @param text 
	 */
	public static void copyToClipboard(final String text)
	{
		final Clipboard cb = new Clipboard(Display.getCurrent());
      Transfer transfer = TextTransfer.getInstance();
      cb.setContents(new Object[] { (text != null) ? text : "" }, new Transfer[] { transfer }); //$NON-NLS-1$
      cb.dispose();
   }

   /**
    * Get best fitting font from given font list for given string and bounding rectangle.
    * String should fit using multiline.
    * Fonts in the list must be ordered from smaller to larger.
    * 
    * @param gc GC
    * @param fonts list of available fonts
    * @param text text to fit
    * @param width width of bounding rectangle
    * @param height height of bounding rectangle
    * @param maxLineCount maximum line count that should be used
    * @return best font by position in array
    */
   public static int getBestFittingFontMultiline(GC gc, Font[] fonts, String text, int width, int height, int maxLineCount)
   {
      int first = 0;
      int last = fonts.length - 1;
      int curr = last / 2;
      int font = 0;
      while(last > first)
      {
         gc.setFont(fonts[curr]);
         if (fitStringToRect(gc, text, width, height, maxLineCount))
         {
            font = curr;
            first = curr + 1;
            curr = first + (last - first) / 2;
         }
         else
         {
            last = curr - 1;
            curr = first + (last - first) / 2;
         }
      }
      
      return font;
   }
   
   /**
    * Checks if string fits to given rectangle using font set already set in GC
    * 
    * @param gc GC
    * @param text text to fit
    * @param width width of bounding rectangle
    * @param height height of bounding rectangle
    * @param maxLineCount maximum line count that should be used
    * @return true if string was cut
    */
   public static boolean fitStringToRect(GC gc, String text, int width, int height, int maxLineCount)
   {
      Point ext = gc.textExtent(text);
      if (ext.y > height)
         return false;
      if (ext.x <= width)
         return true;

      FittedString newString = fitStringToArea(gc, text, width, maxLineCount > 0 ? Math.min(maxLineCount, (int)(height / ext.y)) : (int)(height / ext.y));
      return newString.isCutted(); 
   }

   /**
    * Calculate substring for string to fit into the given area defined by width in pixels and number of lines of text
    * 
    * @param gc gc object
    * @param text object name
    * @param maxLineCount number of lines that can be used to display text
    * @return formated string
    */
   public static FittedString fitStringToArea(GC gc, String text, int width, int maxLineCount)
   {
      StringBuilder name = new StringBuilder("");
      int start = 0;
      boolean fit = true;
      for(int i = 0; start < text.length(); i++)
      {
         if (i >= maxLineCount)
         {
            fit = false;
            break;
         }

         String substr = text.substring(start);
         int nameL = gc.textExtent(substr).x;
         int numOfCharToLeave = (int)((width - 6) / (nameL / substr.length())); // make best guess
         if (numOfCharToLeave >= substr.length())
            numOfCharToLeave = substr.length();
         String tmp = substr;

         while(gc.textExtent(tmp).x > width)
         {
            numOfCharToLeave--;
            tmp = substr.substring(0, numOfCharToLeave);
            Matcher matcher = patternOnlyCharNum.matcher(tmp);
            if (matcher.matches() || (i + 1 == maxLineCount && numOfCharToLeave != substr.length()))
            {
               Matcher matcherReplaceDot = patternAllDotsAtEnd.matcher(tmp);
               tmp = matcherReplaceDot.replaceAll("");
               tmp += "...";
               fit = false;
            }
            else
            {
               Matcher matcherRemoveCharsAfterSeparator = patternCharsAndNumbersAtEnd.matcher(tmp);
               tmp = matcherRemoveCharsAfterSeparator.replaceAll("");
               numOfCharToLeave = tmp.length();
            }
         }

         name.append(tmp);
         if (i + 1 < maxLineCount && numOfCharToLeave != substr.length())
         {
            name.append("\n");
         }

         Matcher matcherRemoveLineEnd = patternCharsAndNumbersAtStart.matcher(substr.substring(numOfCharToLeave - 1));
         numOfCharToLeave = substr.length() - matcherRemoveLineEnd.replaceAll("").length(); // remove if something left after last word
         start = start + numOfCharToLeave + 1;
      }

      return new FittedString(name.toString(), fit);
   }

	/**
	 * Get best fitting font from given font list for given string and bounding rectangle.
	 * Fonts in the list must be ordered from smaller to larger.
	 * 
	 * @param gc GC
	 * @param fonts list of available fonts
	 * @param text text to fit
	 * @param width width of bounding rectangle
	 * @param height height of bounding rectangle
	 * @return best font
	 */
	public static Font getBestFittingFont(GC gc, Font[] fonts, String text, int width, int height)
	{
		int first = 0;
		int last = fonts.length - 1;
		int curr = last / 2;
		Font font = null;
		while(last > first)
		{
			gc.setFont(fonts[curr]);
			Point ext = gc.textExtent(text);
			if ((ext.x <= width) && (ext.y <= height))
			{
				font = fonts[curr];
				first = curr + 1;
				curr = first + (last - first) / 2;
			}
			else
			{
				last = curr - 1;
				curr = first + (last - first) / 2;
			}
		}

		// Use smallest font if no one fit
		if (font == null)
			font = fonts[0];
		return font;
	}
	
	/**
	 * Find font with matching size in font array.
	 * 
	 * @param fonts fonts to select from
	 * @param sourceFont font to match
	 * @return matching font or null
	 */
	public static Font getMatchingSizeFont(Font[] fonts, Font sourceFont)
	{
		float h = sourceFont.getFontData()[0].getHeight();
		for(int i = 0; i < fonts.length; i++)
			if (fonts[i].getFontData()[0].getHeight() == h)
				return fonts[i];
		return null;
	}

	/**
	 * Validate text input
	 * 
	 * @param text text control
	 * @param validator validator
	 * @return true if text is valid
	 */
	private static boolean validateTextInputInternal(Control control, String text, String label, TextFieldValidator validator, PropertyPage page)
	{
		if (!control.isEnabled())
			return true;	// Ignore validation for disabled controls
		
		boolean ok = validator.validate(text);
      control.setBackground(ok ? null : ThemeEngine.getBackgroundColor("TextInput.Error"));
		if (ok)
		{
			if (page != null)
				page.setErrorMessage(null);
		}
		else
		{
			if (page != null)
				page.setErrorMessage(validator.getErrorMessage(text, label));
			else	
				MessageDialogHelper.openError(control.getShell(), Messages.get().WidgetHelper_InputValidationError, validator.getErrorMessage(text, label));
		}
		return ok;
	}

	/**
	 * Validate text input
	 * 
	 * @param text text control
	 * @param validator validator
	 * @return true if text is valid
	 */
	public static boolean validateTextInput(Text text, String label, TextFieldValidator validator, PropertyPage page)
	{
		return validateTextInputInternal(text, text.getText(), label, validator, page);
	}
	
	/**
	 * Validate text input
	 * 
	 * @param text text control
	 * @param validator validator
	 * @return true if text is valid
	 */
	public static boolean validateTextInput(LabeledText text, TextFieldValidator validator, PropertyPage page)
	{
		return validateTextInputInternal(text.getTextControl(), text.getText(), text.getLabel(), validator, page);
	}
	
	/**
	 * Convert font size in pixels to platform-dependent (DPI dependent actually) points
	 * @param device
	 * @param px
	 * @return
	 */
	public static int fontPixelsToPoints(Display device, int px)
	{
		return px;	// font height is measured in pixels in RAP
		//return (int)Math.round(px * 72.0 / device.getDPI().y);
	}

   /**
    * Scale text points relative to "basic" 96 DPI.
    * 
    * @param device
    * @param pt
    * @return
    */
   public static int scaleTextPoints(Display device, int pt)
   {
      return (int)Math.round(pt * (device.getDPI().y / 96.0));
   }
	
   /**
    * Get width of given text in pixels using settings from given control
    * 
    * @param control
    * @param text
    * @return
    */
   public static int getTextWidth(Control control, String text)
   {
      return getTextExtent(control, text).x;
   }

   /**
    * Get width and height of given text in pixels using settings from given control
    * 
    * @param control
    * @param text
    * @return
    */
   public static Point getTextExtent(Control control, String text)
   {
      GC gc = new GC(control);
      gc.setFont(control.getFont());
      Point e = gc.textExtent(text);
      gc.dispose();
      return e;
   }

   /**
    * Get width and height of given text in pixels using given control and font
    * 
    * @param control
    * @param font
    * @param text
    * @return
    */
   public static Point getTextExtent(Control control, Font font, String text)
   {
      GC gc = new GC(control);
      gc.setFont(font);
      Point e = gc.textExtent(text);
      gc.dispose();
      return e;
   }

   /**
    * Get column index by column ID
    * 
    * @param table table control
    * @param id the id index to be found by
    * @return index of the column
    */
   public static int getColumnIndexById(Table table, int id)
   {
      int index = -1;
      TableColumn[] columns = table.getColumns();
      for(int i = 0; i < columns.length; i++)
      {
         if (!columns[i].isDisposed() && ((Integer)columns[i].getData("ID") == id)) //$NON-NLS-1$
         {
            index = i;
            break;
         }
      }
      
      return index;
   }

   /**
    *  Get column index by column ID
    *  
    * @param tree tree control
    * @param id the id index to be found by
    * @return index of the column
    */
   public static int getColumnIndexById(Tree tree, int id)
   {
      int index = -1;
      TreeColumn[] columns = tree.getColumns();
      for(int i = 0; i < columns.length; i++)
      {
         if (!columns[i].isDisposed() && ((Integer)columns[i].getData("ID") == id)) //$NON-NLS-1$
         {
            index = i;
            break;
         }
      }
      
      return index;
   }

   /**
	 * Escape text for HTML
	 * 
	 * @param text text to escape
	 * @param convertNl if true will convert new line character to <br> tag
	 * @param jsString if true will escape ' and \ with \
	 * @return
	 */
	public static String escapeText(final String text, boolean convertNl, boolean jsString)
	{
		StringBuffer sb = new StringBuffer();
		int textLength = text.length();
		for(int i = 0; i < textLength; i++)
		{
			char ch = text.charAt(i);
			switch(ch)
			{
				case '&':
					sb.append("&amp;");
					break;
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '"':
					sb.append("&quot;");
					break;
				case '\r':
				   if (!convertNl)
	               sb.append(ch);
				   break;
				case '\n':
			      sb.append(convertNl ? "<br>" : ch);
				   break;
				case '\'':
               sb.append(jsString ? "\\'" : ch);
				   break;
            case '\\':
               sb.append(jsString ? "\\\\" : ch);
               break;
				default:
					sb.append(ch);
					break;
			}
		}
		return sb.toString();
	}

	/**
	 * Attach mouse track listener to composite. This listener is only suitable for hover detection.
	 * Only one listener can be attached.
	 * 
	 * @param control control to attach listener to
	 * @param listener mouse track listener
	 */
	public static void attachMouseTrackListener(Composite control, MouseTrackListener listener)
	{
	   WidgetUtil.registerDataKeys("msgProxyWidget");
	   
      MsgProxyWidget proxy = (MsgProxyWidget)control.getData("msgProxyWidgetInternal");
      if (proxy == null)
      {
         proxy = new MsgProxyWidget(control);
         control.setData("msgProxyWidgetInternal", proxy);
         control.setData("msgProxyWidget", proxy.getRemoteObjectId());
      }
      proxy.setMouseTrackListener(listener);
      
	   ClientListener clientListener = (ClientListener)ConsoleSharedData.getProperty("MouseHoverListener");
	   if (clientListener == null)
	   {
	      String script = loadResourceAsText("js/hover.js");
	      if (script == null)
	         return;
	      clientListener = new ClientListener(script);
	      ConsoleSharedData.setProperty("MouseHoverListener", clientListener);
	   }
      control.addListener(SWT.MouseEnter, clientListener);
      control.addListener(SWT.MouseMove, clientListener);
      control.addListener(SWT.MouseExit, clientListener);
	}
	
	/**
	 * Load resource file as text
	 * 
	 * @param resource
	 * @return
	 */
	private static String loadResourceAsText(String resource)
	{
	   InputStream input = WidgetHelper.class.getClassLoader().getResourceAsStream(resource);
	   if (input == null)
	   {
         Activator.logError("Resource " + resource + " not found");
	      return null;
	   }

	   BufferedReader reader = null;
	   try
	   {
   	   reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
   	   StringBuilder builder = new StringBuilder();
   	   String line = reader.readLine();
   	   while(line != null)
   	   {
   	      builder.append(line);
   	      builder.append('\n');
   	      line = reader.readLine();
   	   }
   	   return builder.toString();
	   }
	   catch(Exception e)
	   {
	      Activator.logError("Exception while loading resource " + resource, e);
	      return null;
	   }
	   finally
	   {
	      try
         {
	         if (reader != null)
	            reader.close();
	         else
	            input.close();
         }
         catch(IOException e)
         {
         }
      }
	}

   /**
    * Helper method to set scroll bar increment (compatibility layer for RAP).
    *
    * @param scrollable scrollable to configure scrollbar for
    * @param direction scrollbar direction (<code>SWT.HORIZONTAL</code> or <code>SWT.VERTICAL</code>)
    * @param increment increment value
    */
   public static void setScrollBarIncrement(Scrollable scrollable, int direction, int increment)
   {
   }
}
