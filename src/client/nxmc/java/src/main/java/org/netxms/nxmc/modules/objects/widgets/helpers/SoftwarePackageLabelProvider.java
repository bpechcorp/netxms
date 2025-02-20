/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2013 Victor Kirhenshtein
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
package org.netxms.nxmc.modules.objects.widgets.helpers;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.netxms.client.SoftwarePackage;
import org.netxms.nxmc.localization.DateFormatFactory;
import org.netxms.nxmc.modules.objects.widgets.SoftwareInventory;
import org.netxms.nxmc.resources.ResourceManager;

/**
 * Label provider for software package list
 */
public class SoftwarePackageLabelProvider extends LabelProvider implements ITableLabelProvider
{
	private Image imgPackage;
	private boolean treeMode;
   private DecoratingObjectLabelProvider objectLabelProvider;

	/**
	 * @param treeMode
	 */
	public SoftwarePackageLabelProvider(boolean treeMode)
	{
		super();
		this.treeMode = treeMode;
	   objectLabelProvider = new DecoratingObjectLabelProvider();
	   imgPackage = ResourceManager.getImageDescriptor("icons/package.png").createImage();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
	 */
	@Override
	public Image getColumnImage(Object element, int columnIndex)
	{
		if (columnIndex == 0)
		{
			if (element instanceof SoftwareInventoryNode)
				return objectLabelProvider.getImage(((SoftwareInventoryNode)element).getNode());
			if (treeMode && (element instanceof SoftwarePackage))
				return imgPackage;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
	 */
	@Override
	public String getColumnText(Object element, int columnIndex)
	{
		if (element instanceof SoftwareInventoryNode)
		{
			if (columnIndex == 0)
				return ((SoftwareInventoryNode)element).getNode().getObjectName();
		}
		else
		{
			SoftwarePackage p = (SoftwarePackage)element;
			switch(columnIndex)
			{
				case SoftwareInventory.COLUMN_DATE:
					if ((p.getInstallDate() == null) || (p.getInstallDate().getTime() == 0))
						return null;
					return DateFormatFactory.getDateFormat().format(p.getInstallDate());
				case SoftwareInventory.COLUMN_DESCRIPTION:
					return p.getDescription();
				case SoftwareInventory.COLUMN_NAME:
					return p.getName();
				case SoftwareInventory.COLUMN_URL:
					return p.getSupportUrl();
				case SoftwareInventory.COLUMN_VENDOR:
					return p.getVendor();
				case SoftwareInventory.COLUMN_VERSION:
					return p.getVersion();
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.BaseLabelProvider#dispose()
	 */
	@Override
	public void dispose()
	{
	   objectLabelProvider.dispose();
		imgPackage.dispose();
		super.dispose();
	}
}
