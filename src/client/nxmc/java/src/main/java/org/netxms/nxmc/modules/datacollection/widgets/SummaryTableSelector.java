/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2014 Victor Kirhenshtein
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
package org.netxms.nxmc.modules.datacollection.widgets;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Composite;
import org.netxms.client.datacollection.DciSummaryTableDescriptor;
import org.netxms.nxmc.base.widgets.AbstractSelector;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.datacollection.SummaryTablesCache;
import org.netxms.nxmc.modules.datacollection.dialogs.SelectSummaryTableDialog;
import org.xnap.commons.i18n.I18n;

/**
 * Summary table selector
 */
public class SummaryTableSelector extends AbstractSelector
{
   private static final I18n i18n = LocalizationHelper.getI18n(SummaryTableSelector.class);

   private DciSummaryTableDescriptor table = null;

   /**
    * @param parent
    * @param style
    * @param options
    */
   public SummaryTableSelector(Composite parent, int style, int options)
   {
      super(parent, style, options);
      setText(i18n.tr("<none>"));
   }

   /**
    * @see org.netxms.nxmc.base.widgets.AbstractSelector#selectionButtonHandler()
    */
   @Override
   protected void selectionButtonHandler()
   {
      SelectSummaryTableDialog dlg = new SelectSummaryTableDialog(getShell());
      if (dlg.open() == Window.OK)
      {
         table = dlg.getTable();
         if (table != null)
            setText(table.getTitle());
         else
            setText(i18n.tr("<none>"));
      }
   }

   /**
    * @see org.netxms.ui.eclipse.widgets.AbstractSelector#clearButtonHandler()
    */
   @Override
   protected void clearButtonHandler()
   {
      table = null;
      setText(i18n.tr("<none>"));
   }

   /**
    * Set table ID
    * 
    * @param id
    */
   public void setTableId(int id)
   {
      table = null;
      for(DciSummaryTableDescriptor t : SummaryTablesCache.getInstance().getTables())
      {
         if (t.getId() == id)
         {
            table = t;
            break;
         }
      }
      if (table != null)
         setText(table.getTitle());
      else
         setText(i18n.tr("<none>"));
   }

   /**
    * Get selected table ID
    * 
    * @return
    */
   public int getTableId()
   {
      return (table != null) ? table.getId() : 0;
   }
}
