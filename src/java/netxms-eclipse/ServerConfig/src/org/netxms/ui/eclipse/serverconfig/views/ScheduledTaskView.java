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
package org.netxms.ui.eclipse.serverconfig.views;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;
import org.netxms.client.NXCSession;
import org.netxms.client.ScheduledTask;
import org.netxms.client.SessionListener;
import org.netxms.client.SessionNotification;
import org.netxms.ui.eclipse.actions.RefreshAction;
import org.netxms.ui.eclipse.console.resources.SharedIcons;
import org.netxms.ui.eclipse.jobs.ConsoleJob;
import org.netxms.ui.eclipse.serverconfig.Activator;
import org.netxms.ui.eclipse.serverconfig.dialogs.ScheduledTaskEditDialog;
import org.netxms.ui.eclipse.serverconfig.views.helpers.ScheduledTaskComparator;
import org.netxms.ui.eclipse.serverconfig.views.helpers.ScheduledTaskLabelProvider;
import org.netxms.ui.eclipse.serverconfig.views.helpers.ScheduledTaskFilter;
import org.netxms.ui.eclipse.shared.ConsoleSharedData;
import org.netxms.ui.eclipse.tools.MessageDialogHelper;
import org.netxms.ui.eclipse.tools.WidgetHelper;
import org.netxms.ui.eclipse.widgets.SortableTableViewer;

/**
 * Scheduled task manager
 */
public class ScheduledTaskView extends ViewPart
{
   public static final String ID = "org.netxms.ui.eclipse.serverconfig.views.ScheduledTaskView"; //$NON-NLS-1$

   public static final int SCHEDULE_ID = 0;
   public static final int CALLBACK_ID = 1;
   public static final int OBJECT = 2;
   public static final int PARAMETERS = 3;
   public static final int TIMER_KEY = 4;
   public static final int EXECUTION_TIME = 5;
   public static final int LAST_EXECUTION_TIME = 6;
   public static final int STATUS = 7;
   public static final int MANAGMENT_STATE = 8;
   public static final int OWNER = 9;
   public static final int COMMENTS = 10;
   
   private NXCSession session;
   private SessionListener listener;
   private SortableTableViewer viewer;
   private ScheduledTaskFilter filter;
   private Action actionRefresh;
   private Action actionShowCompletedTasks;
   private Action actionShowDisabledTasks;
   private Action actionShowSystemTasks;
   private Action actionCreate;
   private Action actionEdit;
   private Action actionDelete;
   private Action actionDisable;
   private Action actionEnable;
   private Action actionReschedule;
   
   /**
    * @see org.eclipse.ui.part.ViewPart#init(org.eclipse.ui.IViewSite)
    */
   @Override
   public void init(IViewSite site) throws PartInitException
   {
      super.init(site);
      session = ConsoleSharedData.getSession();
   }

   /**
    * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
    */
   @Override
   public void createPartControl(Composite parent)
   {      
      final int[] widths = { 50, 100, 200, 400, 200, 150, 150, 100, 200, 250, 200 };
      final String[] names = { "Id", "Schedule Type", "Object", "Parameters", "Timer key", "Execution time", "Last execution time", "Execution status", "Administrative status", "Owner", "Comments" };
      viewer = new SortableTableViewer(parent, names, widths, SCHEDULE_ID, SWT.UP, SWT.FULL_SELECTION | SWT.MULTI);
      viewer.setContentProvider(new ArrayContentProvider());
      viewer.setLabelProvider(new ScheduledTaskLabelProvider(viewer));
      viewer.setComparator(new ScheduledTaskComparator());
      viewer.addDoubleClickListener(new IDoubleClickListener() {
         @Override
         public void doubleClick(DoubleClickEvent event)
         {
            actionEdit.run();
         }
      });
      
      filter = new ScheduledTaskFilter();
      viewer.addFilter(filter);

      IDialogSettings settings = Activator.getDefault().getDialogSettings();
      WidgetHelper.restoreTableViewerSettings(viewer, settings, "ScheduledTasks");
      filter.setShowCompletedTasks(getBooleanFromSettings(settings, "ScheduledTasks.showCompleted", true));
      filter.setShowDisabledTasks(getBooleanFromSettings(settings, "ScheduledTasks.showDisabled", true));
      filter.setShowSystemTasks(getBooleanFromSettings(settings, "ScheduledTasks.showSystem", false));
      
      viewer.getControl().addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            IDialogSettings settings = Activator.getDefault().getDialogSettings();
            WidgetHelper.saveTableViewerSettings(viewer, settings, "ScheduledTasks");
            settings.put("ScheduledTasks.showCompleted", filter.isShowCompletedTasks());
            settings.put("ScheduledTasks.showDisabled", filter.isShowDisabledTasks());
            settings.put("ScheduledTasks.showSystem", filter.isShowSystemTasks());
         }
      });

      activateContext();
      createActions();
      contributeToActionBars();
      createPopupMenu();
      refresh();

      final Display display = getSite().getShell().getDisplay();
      listener = new SessionListener() {
         @Override
         public void notificationHandler(SessionNotification n)
         {
            if (n.getCode() == SessionNotification.SCHEDULE_UPDATE)
            {
               display.asyncExec(new Runnable() {
                  @Override
                  public void run()
                  {
                     refresh();
                  }
               });
            }
         }
      };
      session.addListener(listener);
   }

   /**
    * @param settings
    * @param name
    * @param defaultValue
    * @return
    */
   private static boolean getBooleanFromSettings(IDialogSettings settings, String name, boolean defaultValue)
   {
      if (settings.get(name) == null)
         return defaultValue;
      return settings.getBoolean(name);
   }

   /**
    * Activate context
    */
   private void activateContext()
   {
      IContextService contextService = (IContextService)getSite().getService(IContextService.class);
      if (contextService != null)
      {
         contextService.activateContext("org.netxms.ui.eclipse.serverconfig.context.ScheduledTaskView"); //$NON-NLS-1$
      }
   }

   /**
    * Create actions
    */
   private void createActions()
   {
      final IHandlerService handlerService = (IHandlerService)getSite().getService(IHandlerService.class);
      
      actionRefresh = new RefreshAction(this) {
         @Override
         public void run()
         {
            refresh();
         }
      };

      actionShowCompletedTasks = new Action("Show &completed tasks", IAction.AS_CHECK_BOX) {
         @Override
         public void run()
         {
            filter.setShowCompletedTasks(actionShowCompletedTasks.isChecked());
            viewer.refresh();
         }
      };
      actionShowCompletedTasks.setChecked(filter.isShowCompletedTasks());
      
      actionShowDisabledTasks = new Action("Show &disabled tasks", IAction.AS_CHECK_BOX) {
         @Override
         public void run()
         {
            filter.setShowDisabledTasks(actionShowDisabledTasks.isChecked());
            viewer.refresh();
         }
      };
      actionShowDisabledTasks.setChecked(filter.isShowDisabledTasks());
      
      actionShowSystemTasks = new Action("Show &system tasks", IAction.AS_CHECK_BOX) {
         @Override
         public void run()
         {
            filter.setShowSystemTasks(actionShowSystemTasks.isChecked());
            viewer.refresh();
         }
      };
      actionShowSystemTasks.setChecked(filter.isShowSystemTasks());

      actionCreate = new Action("&New scheduled task...", Activator.getImageDescriptor("icons/add-task.png")) {
         @Override
         public void run()
         {
            createTask();
         }
      };
      actionCreate.setActionDefinitionId("org.netxms.ui.eclipse.serverconfig.commands.new_task"); //$NON-NLS-1$
      handlerService.activateHandler(actionCreate.getActionDefinitionId(), new ActionHandler(actionCreate));

      actionEdit = new Action("&Edit...", SharedIcons.EDIT) {
         @Override
         public void run()
         {
            editTask(false);
         }
      };
      actionEdit.setActionDefinitionId("org.netxms.ui.eclipse.serverconfig.commands.edit_task"); //$NON-NLS-1$
      handlerService.activateHandler(actionEdit.getActionDefinitionId(), new ActionHandler(actionEdit));

      actionDelete = new Action("&Delete", SharedIcons.DELETE_OBJECT) {
         @Override
         public void run()
         {
            deleteTask();
         }
      };

      actionDisable = new Action("D&isable", SharedIcons.DISABLE) {
         @Override
         public void run()
         {
            setScheduledTaskEnabled(false);
         }
      };
      actionDisable.setActionDefinitionId("org.netxms.ui.eclipse.serverconfig.commands.disable_task"); //$NON-NLS-1$
      handlerService.activateHandler(actionDisable.getActionDefinitionId(), new ActionHandler(actionDisable));

      actionEnable = new Action("En&able") {
         @Override
         public void run()
         {
            setScheduledTaskEnabled(true);
         }
      };
      actionEnable.setActionDefinitionId("org.netxms.ui.eclipse.serverconfig.commands.enable_task"); //$NON-NLS-1$
      handlerService.activateHandler(actionEnable.getActionDefinitionId(), new ActionHandler(actionEnable));

      actionReschedule = new Action("&Reschedule...", Activator.getImageDescriptor("icons/reschedule-task.png")) {
         @Override
         public void run()
         {
            editTask(true);
         }
      };
      actionReschedule.setActionDefinitionId("org.netxms.ui.eclipse.serverconfig.commands.reschedule_task"); //$NON-NLS-1$
      handlerService.activateHandler(actionReschedule.getActionDefinitionId(), new ActionHandler(actionReschedule));
   }

   /**
    * Delete task
    */
   protected void deleteTask()
   {
      final IStructuredSelection selection = viewer.getStructuredSelection();
      if (selection.isEmpty())
         return;
      
      if (!MessageDialogHelper.openQuestion(getSite().getShell(), "Confirm Task Delete", 
            (selection.size() == 1) ? "Selected task will be deleted. Are you sure?" : "Selected tasks will be deleted. Are you sure?"))
         return;

      new ConsoleJob("Delete scheduled task", null, Activator.PLUGIN_ID, null) {
         @Override
         protected void runInternal(IProgressMonitor monitor) throws Exception
         {
            for(Object o : selection.toList())
            {
               session.deleteScheduledTask(((ScheduledTask)o).getId());
            }
         }

         @Override
         protected String getErrorMessage()
         {
            return "Cannot delete scheduled tasks";
         }
      }.start();
   }

   /**
    * Edit selected task
    */
   protected void editTask(boolean reschedule)
   {
      final IStructuredSelection selection = viewer.getStructuredSelection();
      if (selection.size() != 1)
         return;
      
      final ScheduledTask originalTask = (ScheduledTask)selection.getFirstElement();
      if (reschedule)
         originalTask.setId(0);

      new ConsoleJob("Update scheduled task", this, Activator.PLUGIN_ID) {
         private ScheduledTask task = null;

         @Override
         protected void runInternal(IProgressMonitor monitor) throws Exception
         {
            final List<String> taskList = session.getScheduledTaskHandlers();
            getDisplay().syncExec(new Runnable() {
               @Override
               public void run()
               {
                  ScheduledTaskEditDialog dialog = new ScheduledTaskEditDialog(getSite().getShell(), originalTask, taskList);
                  if (dialog.open() == Window.OK)
                  {
                     task = dialog.getTask();
                  }
               }
            });
            if (task != null)
            {
               if(reschedule)
                  session.addScheduledTask(task);
               else
                  session.updateScheduledTask(task);                  
            }
         }

         @Override
         protected String getErrorMessage()
         {
            return "Cannot update scheduled tasks";
         }
      }.start();
   }

   /**
    * @param enabled
    */
   protected void setScheduledTaskEnabled(final boolean enabled)
   {
      final IStructuredSelection selection = viewer.getStructuredSelection();
      if (selection.isEmpty())
         return;
      
      new ConsoleJob("Update scheduled task", this, Activator.PLUGIN_ID) {
         @Override
         protected void runInternal(IProgressMonitor monitor) throws Exception
         {
            for(Object o : selection.toList())
            {  
               ScheduledTask task = (ScheduledTask)o;
               task.setEnabed(enabled);
               session.updateScheduledTask(task);
            }
         }

         @Override
         protected String getErrorMessage()
         {
            return "Cannot update scheduled tasks";
         }
      }.start();
   }

   /**
    * Create task
    */
   private void createTask()
   {     
      new ConsoleJob("Create scheduled task", this, Activator.PLUGIN_ID) {
         private ScheduledTask task = null;
         
         @Override
         protected void runInternal(IProgressMonitor monitor) throws Exception
         {
            final List<String> taskList = session.getScheduledTaskHandlers();
            getDisplay().syncExec(new Runnable() {
               @Override
               public void run()
               {
                  ScheduledTaskEditDialog dialog = new ScheduledTaskEditDialog(getSite().getShell(), null, taskList);
                  if (dialog.open() == Window.OK)
                  {
                     task = dialog.getTask();
                  }
               }
            });
            if (task != null)
               session.addScheduledTask(task);
         }

         @Override
         protected String getErrorMessage()
         {
            return "Cannot create scheduled tasks";
         }
      }.start();
   }

   /**
    * Fill action bars
    */
   private void contributeToActionBars()
   {
      IActionBars bars = getViewSite().getActionBars();
      fillLocalPullDown(bars.getMenuManager());
      fillLocalToolBar(bars.getToolBarManager());
   }

   /**
    * @param manager
    */
   private void fillLocalPullDown(IMenuManager manager)
   {
      manager.add(actionCreate);
      manager.add(new Separator());
      manager.add(actionShowCompletedTasks);
      manager.add(actionShowDisabledTasks);
      manager.add(actionShowSystemTasks);
      manager.add(new Separator());
      manager.add(actionRefresh);
   }

   /**
    * @param manager
    */
   private void fillLocalToolBar(IToolBarManager manager)
   {
      manager.add(actionCreate);
      manager.add(new Separator());
      manager.add(actionRefresh);
   }
   
   /**
    * Create pop-up menu for variable list
    */
   private void createPopupMenu()
   {
      // Create menu manager.
      MenuManager menuMgr = new MenuManager();
      menuMgr.setRemoveAllWhenShown(true);
      menuMgr.addMenuListener(new IMenuListener() {
         public void menuAboutToShow(IMenuManager mgr)
         {
            fillContextMenu(mgr);
         }
      });

      // Create menu.
      Menu menu = menuMgr.createContextMenu(viewer.getControl());
      viewer.getControl().setMenu(menu);
   }

   /**
    * Fill context menu
    * @param mgr Menu manager
    */
   protected void fillContextMenu(IMenuManager mgr)
   {
      final IStructuredSelection selection = viewer.getStructuredSelection();
      if (selection.size() == 1) 
      {
         ScheduledTask task = (ScheduledTask)selection.getFirstElement();
         if (task.getSchedule().isEmpty())
         {
            mgr.add(actionReschedule);
         }
         else
         {
            mgr.add(actionEdit);            
         }
      }

      boolean containDisabled = false;
      boolean containEnabled = false;
      for(Object o : selection.toList())
      {
         if (((ScheduledTask)o).isDisabled())
            containDisabled = true;
         else
            containEnabled = true;
         if (containDisabled && containEnabled)
            break;
      }

      if (containDisabled)
         mgr.add(actionEnable);
      if (containEnabled)
         mgr.add(actionDisable);

      if (!selection.isEmpty())
         mgr.add(actionDelete);

      mgr.add(new Separator());
      mgr.add(actionCreate);
   }

   /**
    * Refresh list
    */
   private void refresh()
   {
      new ConsoleJob("Reloading scheduled task list", this, Activator.PLUGIN_ID, null) {
         @Override
         protected void runInternal(IProgressMonitor monitor) throws Exception
         {
            final List<ScheduledTask> schedules = session.getScheduledTasks();
            runInUIThread(new Runnable() {
               @Override
               public void run()
               {
                  final IStructuredSelection selection = viewer.getStructuredSelection();
                  viewer.setInput(schedules.toArray());
                  if (!selection.isEmpty())
                  {
                     List<ScheduledTask> newSelection = new ArrayList<ScheduledTask>();
                     for(Object o : selection.toList())
                     {
                        long id = ((ScheduledTask)o).getId();
                        for(ScheduledTask s : schedules)
                        {
                           if (s.getId() == id)
                           {
                              newSelection.add(s);
                              break;
                           }
                        }
                     }
                     if (!newSelection.isEmpty())
                        viewer.setSelection(new StructuredSelection(newSelection));
                  }
               }
            });
         }

         @Override
         protected String getErrorMessage()
         {
            return "Cannot get list of scheduled tasks";
         }
      }.start();
   }

   /**
    * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
    */
   @Override
   public void setFocus()
   {
      viewer.getTable().setFocus();      
   }

   /**
    * @see org.eclipse.ui.part.WorkbenchPart#dispose()
    */
   @Override
   public void dispose()
   {
      if ((listener != null) && (session != null))
         session.removeListener(listener);
      super.dispose();
   }
}
