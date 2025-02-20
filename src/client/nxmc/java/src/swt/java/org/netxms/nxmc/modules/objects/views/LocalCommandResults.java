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
package org.netxms.nxmc.modules.objects.views;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.netxms.client.objecttools.ObjectTool;
import org.netxms.nxmc.base.jobs.Job;
import org.netxms.nxmc.base.widgets.TextConsole.IOConsoleOutputStream;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.objects.ObjectContext;
import org.netxms.nxmc.modules.objects.TcpPortForwarder;
import org.netxms.nxmc.resources.SharedIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

/**
 * Results of local command execution
 */
public class LocalCommandResults extends AbstractCommandResultView
{
   private static final Logger logger = LoggerFactory.getLogger(LocalCommandResults.class);
   private static final I18n i18n = LocalizationHelper.getI18n(LocalCommandResults.class);

	private Process process;
   private TcpPortForwarder tcpPortForwarder = null;
	private boolean running = false;
	private Object mutex = new Object();
	private Action actionTerminate;
	private Action actionRestart;

   /**
    * Constructor
    * 
    * @param node
    * @param tool
    * @param inputValues
    * @param maskedFields
    */
   public LocalCommandResults(ObjectContext node, ObjectTool tool, final Map<String, String> inputValues, final List<String> maskedFields)
   {
      super(node, tool, inputValues, maskedFields);
   }

	/**
	 * Create actions
	 */
	protected void createActions()
	{
	   super.createActions();

		actionTerminate = new Action(i18n.tr("&Terminate"), SharedIcons.TERMINATE) {
			@Override
			public void run()
			{
				synchronized(mutex)
				{
					if (running)
					{
						process.destroy();
                  if (tcpPortForwarder != null)
                  {
                     tcpPortForwarder.close();
                     tcpPortForwarder = null;
                  }
					}
				}
			}
		};
		actionTerminate.setEnabled(false);
      actionTerminate.setActionDefinitionId("org.netxms.ui.eclipse.objecttools.commands.terminate_process"); //$NON-NLS-1$

		actionRestart = new Action(i18n.tr("&Restart"), SharedIcons.RESTART) {
			@Override
			public void run()
			{
				execute();
			}
		};
		actionRestart.setEnabled(false);
	}

   /**
    * @see org.netxms.nxmc.base.views.View#fillLocalMenu(org.eclipse.jface.action.MenuManager)
    */
   @Override
   protected void fillLocalMenu(IMenuManager manager)
	{
		manager.add(actionTerminate);
		manager.add(actionRestart);
		manager.add(new Separator());
		super.fillLocalMenu(manager);
	}

   /**
    * @see org.netxms.nxmc.base.views.View#fillLocalToolbar(org.eclipse.jface.action.ToolBarManager)
    */
   @Override
   protected void fillLocalToolBar(IToolBarManager manager)
	{
		manager.add(actionTerminate);
		manager.add(actionRestart);
		manager.add(new Separator());
		super.fillLocalToolBar(manager);
	}

	/**
	 * Fill context menu
	 * 
	 * @param mgr Menu manager
	 */
	protected void fillContextMenu(final IMenuManager manager)
	{
		manager.add(actionTerminate);
		manager.add(actionRestart);
		manager.add(new Separator());
		super.fillContextMenu(manager);
	}

   /**
    * @see org.netxms.nxmc.modules.objects.views.AbstractCommandResultView#execute()
    */
   @Override
	public void execute()
	{
		synchronized(mutex)
		{
			if (running)
			{
				process.destroy();
				try
				{
					mutex.wait();
				}
				catch(InterruptedException e)
				{
				}
            if (tcpPortForwarder != null)
            {
               tcpPortForwarder.close();
               tcpPortForwarder = null;
            }
			}
			running = true;
			actionTerminate.setEnabled(true);
			actionRestart.setEnabled(false);
		}

		final IOConsoleOutputStream out = console.newOutputStream();
		Job job = new Job(i18n.tr("Execute external command"), this) {
			@Override
			protected String getErrorMessage()
			{
				return i18n.tr("Cannot execute external command");
			}

			@Override
			protected void run(IProgressMonitor monitor) throws Exception
			{
            String commandLine;
            if (((tool.getFlags() & ObjectTool.SETUP_TCP_TUNNEL) != 0) && object.isNode())
            {
               tcpPortForwarder = new TcpPortForwarder(session, object.object.getObjectId(), tool.getRemotePort(), 0);
               tcpPortForwarder.setConsoleOutputStream(out);
               tcpPortForwarder.run();
               commandLine = executionString.replace("${local-port}", Integer.toString(tcpPortForwarder.getLocalPort()));
            }
            else
            {
               commandLine = executionString;
            }

            Process process;
            if (SystemUtils.IS_OS_WINDOWS)
            {
               commandLine = "CMD.EXE /C " + commandLine;
               process = Runtime.getRuntime().exec(commandLine);
            }
            else
            {
               process = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", commandLine });
            }

				InputStream in = process.getInputStream();
				try
				{
					byte[] data = new byte[16384];
					while(true)
					{
						int bytes = in.read(data);
						if (bytes == -1)
							break;
						String s = new String(Arrays.copyOf(data, bytes));

						// The following is a workaround for issue NX-65
						// Problem is that on Windows XP many system commands
						// (like ping, tracert, etc.) generates output with lines
						// ending in 0x0D 0x0D 0x0A
                  if (SystemUtils.IS_OS_WINDOWS)
							out.write(s.replace("\r\r\n", " \r\n")); //$NON-NLS-1$ //$NON-NLS-2$
						else
							out.write(s);
					}

					out.write(i18n.tr("\n\n*** TERMINATED ***\n\n\n"));
				}
				catch(IOException e)
				{
				   logger.error("Exception while running local command", e);
				}
				finally
				{
					in.close();
					out.close();
				}
			}

			@Override
			protected void jobFinalize()
			{
				synchronized(mutex)
				{
               if (tcpPortForwarder != null)
               {
                  tcpPortForwarder.close();
                  tcpPortForwarder = null;
               }
					running = false;
					process = null;
					mutex.notifyAll();
				}

				runInUIThread(new Runnable() {
					@Override
					public void run()
					{
						synchronized(mutex)
						{
							actionTerminate.setEnabled(running);
							actionRestart.setEnabled(!running);
						}
					}
				});
			}
		};
		job.setUser(false);
		job.setSystem(true);
		job.start();
	}

   /**
    * @see org.netxms.nxmc.base.views.View#dispose()
    */
	@Override
	public void dispose()
	{
		synchronized(mutex)
		{
			if (running)
			{
			   if (process != null)
			      process.destroy();
            if (tcpPortForwarder != null)
            {
               tcpPortForwarder.close();
               tcpPortForwarder = null;
            }
			}
		}
		super.dispose();
	}
}
