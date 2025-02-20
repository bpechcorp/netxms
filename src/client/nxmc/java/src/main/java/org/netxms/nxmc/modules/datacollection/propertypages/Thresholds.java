/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2022 Raden Solutions
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
package org.netxms.nxmc.modules.datacollection.propertypages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.netxms.client.datacollection.DataCollectionItem;
import org.netxms.client.datacollection.Threshold;
import org.netxms.nxmc.base.widgets.LabeledText;
import org.netxms.nxmc.base.widgets.SortableTableViewer;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.datacollection.DataCollectionObjectEditor;
import org.netxms.nxmc.modules.datacollection.dialogs.EditThresholdDialog;
import org.netxms.nxmc.modules.datacollection.widgets.helpers.ThresholdLabelProvider;
import org.netxms.nxmc.tools.WidgetHelper;
import org.xnap.commons.i18n.I18n;

/**
 * "Thresholds" page for data collection item
 */
public class Thresholds extends AbstractDCIPropertyPage
{
   private static final I18n i18n = LocalizationHelper.getI18n(Thresholds.class);

   public static final int COLUMN_OPERATION = 0;
	public static final int COLUMN_EVENT = 1;
   public static final int COLUMN_DEACTIVATION_EVENT = 2;

	private DataCollectionItem dci;
	private List<Threshold> thresholds;
	private LabeledText instance;
	private Button checkAllThresholds;
   private SortableTableViewer thresholdList;
	private Button addButton;
	private Button modifyButton;
	private Button deleteButton;
	private Button upButton;
	private Button downButton;
   private Button duplicateButton;

   /**
    * Create property page.
    *
    * @param editor data collection editor
    */
   public Thresholds(DataCollectionObjectEditor editor)
   {
      super(i18n.tr("Thresholds"), editor);
   }

   /**
    * @see org.netxms.nxmc.modules.datacollection.propertypages.AbstractDCIPropertyPage#createContents(org.eclipse.swt.widgets.Composite)
    */
	@Override
	protected Control createContents(Composite parent)
	{
		Composite dialogArea = (Composite)super.createContents(parent);
		dci = editor.getObjectAsItem();

		thresholds = new ArrayList<Threshold>(dci.getThresholds().size());
		for(Threshold t : dci.getThresholds())
			thresholds.add(new Threshold(t));

		GridLayout layout = new GridLayout();
		layout.verticalSpacing = WidgetHelper.OUTER_SPACING;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		dialogArea.setLayout(layout);

		instance = new LabeledText(dialogArea, SWT.NONE);
      instance.setLabel(i18n.tr("Instance name"));
		instance.setText(dci.getInstanceName());
		GridData gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
		instance.setLayoutData(gd);
		if (dci.getTemplateId() == dci.getNodeId())	// DCI created by instance discovery
			instance.getTextControl().setEditable(false);

		checkAllThresholds = new Button(dialogArea, SWT.CHECK);
      checkAllThresholds.setText(i18n.tr("Process &all thresholds"));
		checkAllThresholds.setSelection(dci.isProcessAllThresholds());

		Composite thresholdArea = new Composite(dialogArea, SWT.NONE);
		gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
		gd.verticalAlignment = SWT.FILL;
		gd.grabExcessVerticalSpace = true;
		gd.horizontalSpan = 2;
		thresholdArea.setLayoutData(gd);
		layout = new GridLayout();
		layout.verticalSpacing = WidgetHelper.INNER_SPACING;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.numColumns = 2;
		thresholdArea.setLayout(layout);

      new Label(thresholdArea, SWT.NONE).setText(i18n.tr("Thresholds"));

      thresholdList = new SortableTableViewer(thresholdArea, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
		gd.verticalAlignment = SWT.FILL;
		gd.grabExcessVerticalSpace = true;
		gd.horizontalSpan = 2;
		thresholdList.getControl().setLayoutData(gd);
		setupThresholdList();
		thresholdList.setInput(thresholds.toArray());
      thresholdList.packColumns();

		Composite leftButtons = new Composite(thresholdArea, SWT.NONE);
		gd = new GridData();
		gd.horizontalAlignment = SWT.LEFT;
		leftButtons.setLayoutData(gd);
		RowLayout buttonsLayout = new RowLayout(SWT.HORIZONTAL);
		buttonsLayout.marginBottom = 0;
		buttonsLayout.marginLeft = 0;
		buttonsLayout.marginRight = 0;
		buttonsLayout.marginTop = 0;
		buttonsLayout.spacing = WidgetHelper.OUTER_SPACING;
		buttonsLayout.fill = true;
		buttonsLayout.pack = false;
		leftButtons.setLayout(buttonsLayout);

		upButton = new Button(leftButtons, SWT.PUSH);
      upButton.setText(i18n.tr("&Up"));
		upButton.setEnabled(false);
		upButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e)
			{
				widgetSelected(e);
			}

			@Override
			public void widgetSelected(SelectionEvent e)
			{
				moveUp();
			}
		});

		downButton = new Button(leftButtons, SWT.PUSH);
      downButton.setText(i18n.tr("&Down"));
		downButton.setEnabled(false);
		downButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e)
			{
				widgetSelected(e);
			}

			@Override
			public void widgetSelected(SelectionEvent e)
			{
				moveDown();
			}
		});

		Composite buttons = new Composite(thresholdArea, SWT.NONE);
		gd = new GridData();
		gd.horizontalAlignment = SWT.RIGHT;
		buttons.setLayoutData(gd);
		buttonsLayout = new RowLayout(SWT.HORIZONTAL);
		buttonsLayout.marginBottom = 0;
		buttonsLayout.marginLeft = 0;
		buttonsLayout.marginRight = 0;
		buttonsLayout.marginTop = 0;
		buttonsLayout.spacing = WidgetHelper.OUTER_SPACING;
		buttonsLayout.fill = true;
		buttonsLayout.pack = false;
		buttons.setLayout(buttonsLayout);

		addButton = new Button(buttons, SWT.PUSH);
      addButton.setText(i18n.tr("&Add..."));
		RowData rd = new RowData();
		rd.width = WidgetHelper.BUTTON_WIDTH_HINT;
		addButton.setLayoutData(rd);
		addButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e)
			{
				widgetSelected(e);
			}

			@Override
			public void widgetSelected(SelectionEvent e)
			{
				addThreshold();
			}
		});
		
		duplicateButton = new Button(buttons, SWT.PUSH);
      duplicateButton.setText(i18n.tr("Duplicate"));
      rd = new RowData();
      rd.width = WidgetHelper.BUTTON_WIDTH_HINT;
      duplicateButton.setLayoutData(rd);
      duplicateButton.setEnabled(false);
      duplicateButton.addSelectionListener(new SelectionListener() {
         @Override
         public void widgetDefaultSelected(SelectionEvent e)
         {
            widgetSelected(e);
         }

         @Override
         public void widgetSelected(SelectionEvent e)
         {
            duplicateThreshold();
         }
      });

		modifyButton = new Button(buttons, SWT.PUSH);
      modifyButton.setText(i18n.tr("&Edit..."));
		rd = new RowData();
		rd.width = WidgetHelper.BUTTON_WIDTH_HINT;
		modifyButton.setLayoutData(rd);
		modifyButton.setEnabled(false);
		modifyButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e)
			{
				widgetSelected(e);
			}

			@Override
			public void widgetSelected(SelectionEvent e)
			{
				editThreshold();
			}
		});

		deleteButton = new Button(buttons, SWT.PUSH);
      deleteButton.setText(i18n.tr("De&lete"));
		rd = new RowData();
		rd.width = WidgetHelper.BUTTON_WIDTH_HINT;
		deleteButton.setLayoutData(rd);
		deleteButton.setEnabled(false);
		deleteButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e)
			{
				widgetSelected(e);
			}

			@Override
			public void widgetSelected(SelectionEvent e)
			{
				deleteThresholds();
			}
		});

		/*** Selection change listener for thresholds list ***/
		thresholdList.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event)
			{
				IStructuredSelection selection = (IStructuredSelection)event.getSelection();
				int index = thresholds.indexOf(selection.getFirstElement());
				upButton.setEnabled((selection.size() == 1) && (index > 0));
				downButton.setEnabled((selection.size() == 1) && (index >= 0) && (index < thresholds.size() - 1));
				modifyButton.setEnabled(selection.size() == 1);
				deleteButton.setEnabled(selection.size() > 0);
				duplicateButton.setEnabled(selection.size() > 0);
			}
		});

		thresholdList.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event)
			{
				editThreshold();
			}
		});

		return dialogArea;
	}

	/**
	 * Delete selected thresholds
	 */
	private void deleteThresholds()
	{
		final IStructuredSelection selection = (IStructuredSelection)thresholdList.getSelection();
		if (!selection.isEmpty())
		{
			Iterator<?> it = selection.iterator();
			while(it.hasNext())
			{
				thresholds.remove(it.next());
			}
			thresholdList.setInput(thresholds.toArray());
         thresholdList.packColumns();
		}
	}

	/**
	 * Edit selected threshold
	 */
	private void editThreshold()
	{
		final IStructuredSelection selection = (IStructuredSelection)thresholdList.getSelection();
		if (selection.size() == 1)
		{
			final Threshold threshold = (Threshold)selection.getFirstElement();
			EditThresholdDialog dlg = new EditThresholdDialog(getShell(), threshold);
			if (dlg.open() == Window.OK)
			{
				thresholdList.update(threshold, null);
            thresholdList.packColumns();
			}
		}
	}

	/**
	 * Add new threshold
	 */
	private void addThreshold()
	{
		Threshold threshold = new Threshold();
		EditThresholdDialog dlg = new EditThresholdDialog(getShell(), threshold);
		if (dlg.open() == Window.OK)
		{
			thresholds.add(threshold);
			thresholdList.setInput(thresholds.toArray());
         thresholdList.packColumns();
			thresholdList.setSelection(new StructuredSelection(threshold));
		}
	}

	/**
	 * Duplicate selected threshold
	 */
	@SuppressWarnings("unchecked")
   private void duplicateThreshold()
	{
      final IStructuredSelection selection = (IStructuredSelection)thresholdList.getSelection();
      if (selection.size() > 0)
      {
         List<Threshold> list = selection.toList();
         for(Threshold t : list)
         {               
            thresholds.add(thresholds.indexOf(t) + 1, t.duplicate());
            thresholdList.setInput(thresholds.toArray());
         }
      }
	}

	/**
	 * Move currently selected threshold up
	 */
	private void moveUp()
	{
		final IStructuredSelection selection = (IStructuredSelection)thresholdList.getSelection();
		if (selection.size() == 1)
		{
			final Threshold threshold = (Threshold)selection.getFirstElement();

			int index = thresholds.indexOf(threshold);
			if (index > 0)
			{
				Collections.swap(thresholds, index - 1, index);
				thresholdList.setInput(thresholds.toArray());
				thresholdList.setSelection(new StructuredSelection(threshold));
			}
		}
	}

	/**
	 * Move currently selected threshold down
	 */
	private void moveDown()
	{
		final IStructuredSelection selection = (IStructuredSelection)thresholdList.getSelection();
		if (selection.size() == 1)
		{
			final Threshold threshold = (Threshold)selection.getFirstElement();

			int index = thresholds.indexOf(threshold);
			if ((index < thresholds.size() - 1) && (index >= 0))
			{
				Collections.swap(thresholds, index + 1, index);
				thresholdList.setInput(thresholds.toArray());
				thresholdList.setSelection(new StructuredSelection(threshold));
			}
		}
	}

	/**
	 * Setup threshold list control
	 */
	private void setupThresholdList()
	{
		Table table = thresholdList.getTable();

		TableColumn column = new TableColumn(table, SWT.LEFT);
      column.setText(i18n.tr("Expression"));

		column = new TableColumn(table, SWT.LEFT);
      column.setText(i18n.tr("Activation event"));
      
      column = new TableColumn(table, SWT.LEFT);
      column.setText(i18n.tr("Deactivation event"));

		thresholdList.setContentProvider(new ArrayContentProvider());
		thresholdList.setLabelProvider(new ThresholdLabelProvider());

      thresholdList.disableSorting();
	}

	/**
	 * Apply changes
	 * 
	 * @param isApply true if update operation caused by "Apply" button
	 */
	protected boolean applyChanges(final boolean isApply)
	{
		dci.setInstanceName(instance.getText());
		dci.setProcessAllThresholds(checkAllThresholds.getSelection());
		dci.getThresholds().clear();
		dci.getThresholds().addAll(thresholds);
		editor.modify();		
		return true;
	}
}
