/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2021 Victor Kirhenshtein
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
package org.netxms.ui.eclipse.objecttools.views;

import java.io.IOException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.console.IOConsoleOutputStream;
import org.netxms.client.TextOutputListener;
import org.netxms.ui.eclipse.console.resources.SharedIcons;
import org.netxms.ui.eclipse.jobs.ConsoleJob;
import org.netxms.ui.eclipse.objecttools.Activator;
import org.netxms.ui.eclipse.objecttools.Messages;

/**
 * View for agent action execution results
 */
public class SSHCommandResults extends AbstractCommandResults implements TextOutputListener
{
   public static final String ID = "org.netxms.ui.eclipse.objecttools.views.SSHCommandResults"; //$NON-NLS-1$

   private IOConsoleOutputStream out;
   private Action actionRestart;
   private String executionString;

   /**
    * Create actions
    */
   protected void createActions()
   {
      super.createActions();

      actionRestart = new Action(Messages.get().LocalCommandResults_Restart, SharedIcons.RESTART) {
         @Override
         public void run()
         {
            executeSshCommand(executionString);
         }
      };
      actionRestart.setEnabled(false);
   }

   /**
    * Fill local pull-down menu
    * 
    * @param manager Menu manager for pull-down menu
    */
   protected void fillLocalPullDown(IMenuManager manager)
   {
      manager.add(actionRestart);
      manager.add(new Separator());
      super.fillLocalPullDown(manager);
   }

   /**
    * Fill local tool bar
    * 
    * @param manager Menu manager for local toolbar
    */
   protected void fillLocalToolBar(IToolBarManager manager)
   {
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
      manager.add(actionRestart);
      manager.add(new Separator());
      super.fillContextMenu(manager);
   }

   /**
    * @param action
    */
   public void executeSshCommand(final String executionString)
   {
      actionRestart.setEnabled(false);
      out = console.newOutputStream();
      this.executionString = executionString;
      ConsoleJob job = new ConsoleJob(String.format(Messages.get().ObjectToolsDynamicMenu_ExecuteOnNode, session.getObjectName(nodeId)), null, Activator.PLUGIN_ID, null) {
         @Override
         protected String getErrorMessage()
         {
            return String.format(Messages.get().ObjectToolsDynamicMenu_CannotExecuteOnNode, session.getObjectName(nodeId));
         }

         @Override
         protected void runInternal(IProgressMonitor monitor) throws Exception
         {
            try
            {
               session.executeSshCommand(nodeId, executionString, true, SSHCommandResults.this, null);
               out.write(Messages.get().LocalCommandResults_Terminated);
            }
            finally
            {
               out.close();
               out = null;
            }
         }

         @Override
         protected void jobFinalize()
         {
            runInUIThread(new Runnable() {
               @Override
               public void run()
               {
                  actionRestart.setEnabled(true);
               }
            });
         }
      };
      job.setUser(false);
      job.setSystem(true);
      job.start();
   }

   /**
    * @see org.netxms.client.ActionExecutionListener#messageReceived(java.lang.String)
    */
   @Override
   public void messageReceived(String text)
   {
      try
      {
         if (out != null)
            out.write(text);
      }
      catch(IOException e)
      {
      }
   }

   /**
    * @see org.eclipse.ui.part.WorkbenchPart#dispose()
    */
   @Override
   public void dispose()
   {
      if (out != null)
      {
         try
         {
            out.close();
         }
         catch(IOException e)
         {
         }
         out = null;
      }
      super.dispose();
   }

   /* (non-Javadoc)
    * @see org.netxms.client.TextOutputListener#setStreamId(long)
    */
   @Override
   public void setStreamId(long streamId)
   {
   }

   @Override
   public void onError()
   {
   }
}
