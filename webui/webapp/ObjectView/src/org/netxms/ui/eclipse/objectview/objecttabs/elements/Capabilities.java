/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2020 Victor Kirhenshtein
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
package org.netxms.ui.eclipse.objectview.objecttabs.elements;

import org.eclipse.swt.widgets.Composite;
import org.netxms.client.objects.AbstractNode;
import org.netxms.client.objects.AbstractObject;
import org.netxms.client.snmp.SnmpVersion;
import org.netxms.ui.eclipse.objectview.Messages;
import org.netxms.ui.eclipse.objectview.objecttabs.ObjectTab;

/**
 * "Capabilities" element for object overview page
 */
public class Capabilities extends TableElement
{
	/**
	 * @param parent
	 * @param anchor
	 * @param objectTab
	 */
	public Capabilities(Composite parent, OverviewPageElement anchor, ObjectTab objectTab)
	{
		super(parent, anchor, objectTab);
	}

   /**
    * @see org.netxms.ui.eclipse.objectview.objecttabs.elements.OverviewPageElement#getTitle()
    */
	@Override
	protected String getTitle()
	{
		return Messages.get().Capabilities_Title;
	}

   /**
    * @see org.netxms.ui.eclipse.objectview.objecttabs.elements.TableElement#fillTable()
    */
	@Override
	protected void fillTable()
	{
		if (!(getObject() instanceof AbstractNode))
			return;
		
		AbstractNode node = (AbstractNode)getObject();
      addFlag("802.1x", (node.getCapabilities() & AbstractNode.NC_IS_8021X) != 0);
		addFlag(Messages.get().Capabilities_FlagIsAgent, (node.getCapabilities() & AbstractNode.NC_IS_NATIVE_AGENT) != 0);
		addFlag(Messages.get().Capabilities_FlagIsBridge, (node.getCapabilities() & AbstractNode.NC_IS_BRIDGE) != 0);
      addFlag("CDP", (node.getCapabilities() & AbstractNode.NC_IS_CDP) != 0);
      addFlag(Messages.get().Capabilities_FlagHasEntityMIB, (node.getCapabilities() & AbstractNode.NC_HAS_ENTITY_MIB) != 0);
      addFlag("EtherNet/IP", (node.getCapabilities() & AbstractNode.NC_IS_ETHERNET_IP) != 0);
      addFlag("LLDP", (node.getCapabilities() & AbstractNode.NC_IS_LLDP) != 0);
      addFlag("NDP", (node.getCapabilities() & AbstractNode.NC_IS_NDP) != 0);
      addFlag("OSPF", (node.getCapabilities() & AbstractNode.NC_IS_OSPF) != 0);
		addFlag(Messages.get().Capabilities_FlagIsPrinter, (node.getCapabilities() & AbstractNode.NC_IS_PRINTER) != 0);
		addFlag(Messages.get().Capabilities_FlagIsRouter, (node.getCapabilities() & AbstractNode.NC_IS_ROUTER) != 0);
      addFlag("SMCLP", (node.getCapabilities() & AbstractNode.NC_IS_SMCLP) != 0);
      addFlag("SNMP", (node.getCapabilities() & AbstractNode.NC_IS_SNMP) != 0);
      if ((node.getCapabilities() & AbstractNode.NC_IS_SNMP) != 0)
      {
         addFlag(Messages.get().Capabilities_FlagHasIfXTable, (node.getCapabilities() & AbstractNode.NC_HAS_IFXTABLE) != 0);
         addPair(Messages.get().Capabilities_SNMPPort, Integer.toString(node.getSnmpPort()));
         addPair(Messages.get().Capabilities_SNMPVersion, getSnmpVersionName(node.getSnmpVersion()));
      }
      addFlag("SSH", (node.getCapabilities() & AbstractNode.NC_IS_SSH) != 0);
      addFlag("STP", (node.getCapabilities() & AbstractNode.NC_IS_STP) != 0);
      addFlag("User Agent", (node.getCapabilities() & AbstractNode.NC_HAS_USER_AGENT) != 0);
      addFlag("VRRP", (node.getCapabilities() & AbstractNode.NC_IS_VRRP) != 0);
	}
	
	/**
	 * Add flag to list
	 * 
	 * @param name
	 * @param value
	 */
	private void addFlag(String name, boolean value)
	{
		addPair(name, value ? Messages.get().Capabilities_Yes : Messages.get().Capabilities_No);
	}
	
	/**
	 * Get SNMP version name from internal number
	 * 
	 * @param version
	 * @return
	 */
   private String getSnmpVersionName(SnmpVersion version)
	{
		switch(version)
		{
         case V1:
				return "1"; //$NON-NLS-1$
         case V2C:
				return "2c"; //$NON-NLS-1$
         case V3:
				return "3"; //$NON-NLS-1$
			default:
				return "???"; //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see org.netxms.ui.eclipse.objectview.objecttabs.elements.OverviewPageElement#isApplicableForObject(org.netxms.client.objects.AbstractObject)
	 */
	@Override
	public boolean isApplicableForObject(AbstractObject object)
	{
		return object instanceof AbstractNode;
	}
}
