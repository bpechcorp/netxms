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
package org.netxms.nxmc.modules.objects.views.helpers;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.netxms.client.objecttools.ObjectTool;
import org.netxms.nxmc.base.widgets.SortableTableViewer;
import org.netxms.nxmc.modules.objects.views.ObjectToolsEditor;

/**
 * Comparator for object tool list
 */
public class ObjectToolsComparator extends ViewerComparator
{
   private String[] toolTypes = ObjectToolsLabelProvider.getToolTypeNames();

   /**
    * @see org.eclipse.jface.viewers.ViewerComparator#compare(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
    */
	@Override
	public int compare(Viewer viewer, Object e1, Object e2)
	{
		ObjectTool tool1 = (ObjectTool)e1;
		ObjectTool tool2 = (ObjectTool)e2;
		final int column = (Integer)((SortableTableViewer)viewer).getTable().getSortColumn().getData("ID"); //$NON-NLS-1$

		int result;
		switch(column)
		{
			case ObjectToolsEditor.COLUMN_ID:
				result = Long.signum(tool1.getId() - tool2.getId());
				break;
			case ObjectToolsEditor.COLUMN_NAME:
				result = tool1.getName().compareToIgnoreCase(tool2.getName());
				break;
			case ObjectToolsEditor.COLUMN_TYPE:
            result = getToolTypeName(tool1).compareTo(getToolTypeName(tool2));
				break;
			case ObjectToolsEditor.COLUMN_DESCRIPTION:
				result = tool1.getDescription().compareToIgnoreCase(tool2.getDescription());
				break;
			default:
				result = 0;
				break;
		}
		return (((SortableTableViewer)viewer).getTable().getSortDirection() == SWT.UP) ? result : -result;
	}

   /**
    * Get display name for object tool's type
    * 
    * @param tool object tool
    * @return tool type's name
    */
   private String getToolTypeName(ObjectTool tool)
   {
      try
      {
         return toolTypes[tool.getToolType()];
      }
      catch(ArrayIndexOutOfBoundsException e)
      {
         return "<unknown>";
      }
   }
}
