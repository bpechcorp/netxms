/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2011 Victor Kirhenshtein
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
package org.netxms.nxmc.modules.objects.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.netxms.nxmc.base.widgets.LabeledText;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.objects.views.helpers.ObjectToolsLabelProvider;
import org.netxms.nxmc.tools.WidgetHelper;
import org.xnap.commons.i18n.I18n;

/**
 * "Create new tool" dialog
 */
public class CreateNewToolDialog extends Dialog
{
   private static I18n i18n = LocalizationHelper.getI18n(CreateNewToolDialog.class);
   
	private int type;
	private String name;
	
	private LabeledText textName;
	private Combo comboType;
	
	/**
	 * Default constructor.
	 * 
	 * @param parentShell
	 */
	public CreateNewToolDialog(Shell parentShell)
	{
		super(parentShell);
	}

   /**
    * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
    */
	@Override
	protected Control createDialogArea(Composite parent)
	{
		Composite dialogArea = (Composite)super.createDialogArea(parent);

		GridLayout layout = new GridLayout();
		layout.marginHeight = WidgetHelper.DIALOG_HEIGHT_MARGIN;
		layout.marginWidth = WidgetHelper.DIALOG_WIDTH_MARGIN;
		layout.verticalSpacing = WidgetHelper.DIALOG_SPACING;
		dialogArea.setLayout(layout);

		textName = new LabeledText(dialogArea, SWT.NONE);
		textName.setLabel(i18n.tr("Name"));
		GridData gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
		gd.widthHint = 300;
		textName.setLayoutData(gd);

		comboType = WidgetHelper.createLabeledCombo(dialogArea, SWT.READ_ONLY, i18n.tr("Tool type"), WidgetHelper.DEFAULT_LAYOUT_DATA);
      for(String s : ObjectToolsLabelProvider.getToolTypeNames())
			comboType.add(s);
		comboType.select(0);

		return dialogArea;
	}

   /**
    * @see org.eclipse.jface.dialogs.Dialog#okPressed()
    */
	@Override
	protected void okPressed()
	{
		name = textName.getText();
		type = comboType.getSelectionIndex();
		super.okPressed();
	}

	/**
	 * @return the type
	 */
	public int getType()
	{
		return type;
	}

	/**
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}
}
