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
package org.netxms.nxmc.modules.dashboards.propertypages;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.netxms.nxmc.base.widgets.LabeledSpinner;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.dashboards.config.DashboardElementConfig;
import org.netxms.nxmc.modules.dashboards.config.DashboardElementLayout;
import org.xnap.commons.i18n.I18n;

/**
 * "Layout" page for dashboard element
 */
public class Layout extends DashboardElementPropertyPage
{
   private static final I18n i18n = LocalizationHelper.getI18n(Layout.class);

	private Button checkGrabVerticalSpace;
   private LabeledSpinner spinnerHorizontalSpan;
   private LabeledSpinner spinnerVerticalSpan;
   private LabeledSpinner spinnerHeightHint;
	private DashboardElementLayout elementLayout;

   /**
    * Create page.
    *
    * @param elementConfig element configuration
    */
   public Layout(DashboardElementConfig elementConfig)
   {
      super(i18n.tr("Layout"), elementConfig);
   }

   /**
    * @see org.netxms.nxmc.modules.dashboards.propertypages.DashboardElementPropertyPage#getId()
    */
   @Override
   public String getId()
   {
      return "layout";
   }

   /**
    * @see org.eclipse.jface.preference.PreferencePage#createContents(org.eclipse.swt.widgets.Composite)
    */
	@Override
	protected Control createContents(Composite parent)
	{
		elementLayout = elementConfig.getLayout();

		Composite dialogArea = new Composite(parent, SWT.NONE);
		
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		dialogArea.setLayout(layout);
		
      spinnerHorizontalSpan = new LabeledSpinner(dialogArea, SWT.NONE);
      spinnerHorizontalSpan.setLabel(i18n.tr("Horizontal span"));
      spinnerHorizontalSpan.setRange(1, 128);
      spinnerHorizontalSpan.setSelection(elementLayout.horizontalSpan);
      spinnerHorizontalSpan.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

      spinnerHeightHint = new LabeledSpinner(dialogArea, SWT.NONE);
      spinnerHeightHint.setLabel(i18n.tr("Height hint"));
      spinnerHeightHint.setRange(-1, 8192);
      spinnerHeightHint.setSelection(elementLayout.heightHint);
      spinnerHeightHint.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

      spinnerVerticalSpan = new LabeledSpinner(dialogArea, SWT.NONE);
      spinnerVerticalSpan.setLabel(i18n.tr("Vertical span"));
      spinnerVerticalSpan.setRange(1, 128);
      spinnerVerticalSpan.setSelection(elementLayout.verticalSpan);
      spinnerVerticalSpan.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

      checkGrabVerticalSpace = new Button(dialogArea, SWT.CHECK);
      checkGrabVerticalSpace.setText(i18n.tr("Grab excessive vertical space"));
      checkGrabVerticalSpace.setSelection(elementLayout.grabVerticalSpace);
      checkGrabVerticalSpace.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));

		return dialogArea;
	}

   /**
    * @see org.netxms.nxmc.base.propertypages.PropertyPage#applyChanges(boolean)
    */
   @Override
   protected boolean applyChanges(boolean isApply)
   {
      elementLayout.grabVerticalSpace = checkGrabVerticalSpace.getSelection();
      elementLayout.horizontalSpan = spinnerHorizontalSpan.getSelection();
      elementLayout.verticalSpan = spinnerVerticalSpan.getSelection();
      elementLayout.heightHint = spinnerHeightHint.getSelection();
      return true;
   }
}
