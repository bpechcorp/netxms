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
package org.netxms.nxmc.modules.objects.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.netxms.client.NXCSession;
import org.netxms.client.SessionListener;
import org.netxms.client.SessionNotification;
import org.netxms.client.objecttools.ObjectTool;
import org.netxms.client.objecttools.ObjectToolDetails;
import org.netxms.nxmc.Registry;
import org.netxms.nxmc.base.jobs.Job;
import org.netxms.nxmc.base.views.ConfigurationView;
import org.netxms.nxmc.base.widgets.SortableTableViewer;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.objects.dialogs.CreateNewToolDialog;
import org.netxms.nxmc.modules.objects.propertypages.ObjectToolsAccessControl;
import org.netxms.nxmc.modules.objects.propertypages.ObjectToolsColumns;
import org.netxms.nxmc.modules.objects.propertypages.ObjectToolsFilterPropertyPage;
import org.netxms.nxmc.modules.objects.propertypages.ObjectToolsGeneral;
import org.netxms.nxmc.modules.objects.propertypages.ObjectToolsInputFields;
import org.netxms.nxmc.modules.objects.views.helpers.ObjectToolsComparator;
import org.netxms.nxmc.modules.objects.views.helpers.ObjectToolsFilter;
import org.netxms.nxmc.modules.objects.views.helpers.ObjectToolsLabelProvider;
import org.netxms.nxmc.resources.ResourceManager;
import org.netxms.nxmc.resources.SharedIcons;
import org.netxms.nxmc.tools.MessageDialogHelper;
import org.netxms.nxmc.tools.WidgetHelper;
import org.xnap.commons.i18n.I18n;

/**
 * Editor for object tools
 */
public class ObjectToolsEditor extends ConfigurationView implements SessionListener
{
   private static final I18n i18n = LocalizationHelper.getI18n(ObjectToolsEditor.class);

   private static final String TABLE_CONFIG_PREFIX = "ObjectToolsEditor";

   public static final int COLUMN_ID = 0;
   public static final int COLUMN_NAME = 1;
   public static final int COLUMN_TYPE = 2;
   public static final int COLUMN_DESCRIPTION = 3;

   private Map<Long, ObjectTool> tools = new HashMap<Long, ObjectTool>();
   private SortableTableViewer viewer;
   private NXCSession session;
   private Action actionNew;
   private Action actionEdit;
   private Action actionClone;
   private Action actionDelete;
   private Action actionDisable;
   private Action actionEnable;

   /**
    * Create object tools editor view
    */
   public ObjectToolsEditor()
   {
      super(i18n.tr("Object Tools"), ResourceManager.getImageDescriptor("icons/config-views/tools.png"), "ObjectTools", true);
      session = Registry.getSession();
   }

   /**
    * @see org.netxms.nxmc.base.views.View#createContent(org.eclipse.swt.widgets.Composite)
    */
   @Override
   public void createContent(Composite parent)
   {
      final String[] columnNames = { i18n.tr("ID"), i18n.tr("Name"), i18n.tr("Type"), i18n.tr("Description") };
      final int[] columnWidths = { 90, 200, 100, 200 };
      viewer = new SortableTableViewer(parent, columnNames, columnWidths, COLUMN_NAME, SWT.UP, SWT.FULL_SELECTION | SWT.MULTI);
      WidgetHelper.restoreTableViewerSettings(viewer, TABLE_CONFIG_PREFIX);
      viewer.setContentProvider(new ArrayContentProvider());
      viewer.setLabelProvider(new ObjectToolsLabelProvider());
      ObjectToolsFilter filter = new ObjectToolsFilter();
      viewer.addFilter(filter);
      setFilterClient(viewer, filter);

      viewer.setComparator(new ObjectToolsComparator());
      viewer.addSelectionChangedListener(new ISelectionChangedListener() {
         @Override
         public void selectionChanged(SelectionChangedEvent event)
         {
            IStructuredSelection selection = (IStructuredSelection)event.getSelection();
            if (selection != null)
            {
               actionEdit.setEnabled(selection.size() == 1);
               actionClone.setEnabled(selection.size() == 1);
               actionDelete.setEnabled(selection.size() > 0);
               actionDisable.setEnabled(containsEnabled(selection));
               actionEnable.setEnabled(containsDisabled(selection));
            }
         }
      });
      viewer.addDoubleClickListener(new IDoubleClickListener() {
         @Override
         public void doubleClick(DoubleClickEvent event)
         {
            actionEdit.run();
         }
      });
      viewer.getTable().addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            WidgetHelper.saveTableViewerSettings(viewer, TABLE_CONFIG_PREFIX);
         }
      });

      createActions();
      createContextMenu();

      session.addListener(this);
   }

   /**
    * @see org.netxms.nxmc.base.views.View#postContentCreate()
    */
   @Override
   protected void postContentCreate()
   {
      refresh();
   }

   /**
    * Check if selection contains disabled object tools.
    *
    * @param selection selection to check
    * @return true if selection contains disabled object tools
    */
   private static boolean containsDisabled(IStructuredSelection selection)
   {
      for(Object o : selection.toList())
         if (!((ObjectTool)o).isEnabled())
            return true;
      return false;
   }

   /**
    * Check if selection contains enabled object tools.
    *
    * @param selection selection to check
    * @return true if selection contains enabled object tools
    */
   private static boolean containsEnabled(IStructuredSelection selection)
   {
      for(Object o : selection.toList())
         if (((ObjectTool)o).isEnabled())
            return true;
      return false;
   }

   /**
    * Create actions
    */
   private void createActions()
   {
      actionNew = new Action(i18n.tr("&New...")) {
         @Override
         public void run()
         {
            createTool();
         }
      };
      actionNew.setImageDescriptor(SharedIcons.ADD_OBJECT);
      addKeyBinding("M1+N", actionNew);

      actionEdit = new Action(i18n.tr("&Edit...")) {
         @Override
         public void run()
         {
            editTool();
         }
      };
      actionEdit.setImageDescriptor(SharedIcons.EDIT);
      addKeyBinding("M3+ENTER", actionEdit);

      actionDelete = new Action(i18n.tr("&Delete")) {
         @Override
         public void run()
         {
            deleteTools();
         }
      };
      actionDelete.setImageDescriptor(SharedIcons.DELETE_OBJECT);
      addKeyBinding("M1+D", actionDelete);

      actionDisable = new Action(i18n.tr("D&isable")) {
         @Override
         public void run()
         {
            enableTools(false);
         }
      };
      addKeyBinding("M1+I", actionDisable);

      actionEnable = new Action(i18n.tr("Ena&ble")) {
         @Override
         public void run()
         {
            enableTools(true);
         }
      };
      addKeyBinding("M1+E", actionEnable);

      actionClone = new Action(i18n.tr("Clone")) {
         @Override
         public void run()
         {
            cloneTool();
         }
      };
   }

   /**
    * @see org.netxms.nxmc.base.views.View#fillLocalToolBar(org.eclipse.jface.action.MenuManager)
    */
   @Override
   protected void fillLocalToolBar(IToolBarManager manager)
   {
      manager.add(actionNew);
   }

   /**
    * @see org.netxms.nxmc.base.views.View#fillLocalMenu(org.eclipse.jface.action.MenuManager)
    */
   @Override
   protected void fillLocalMenu(IMenuManager manager)
   {
      manager.add(actionNew);
   }

   /**
    * Create context menu for tool list
    */
   private void createContextMenu()
   {
      // Create menu manager
      MenuManager menuMgr = new MenuManager();
      menuMgr.setRemoveAllWhenShown(true);
      menuMgr.addMenuListener(new IMenuListener() {
         public void menuAboutToShow(IMenuManager mgr)
         {
            fillContextMenu(mgr);
         }
      });

      // Create menu
      Menu menu = menuMgr.createContextMenu(viewer.getControl());
      viewer.getControl().setMenu(menu);
   }

   /**
    * Fill context menu
    * 
    * @param mgr Menu manager
    */
   protected void fillContextMenu(final IMenuManager mgr)
   {
      mgr.add(actionNew);
      mgr.add(actionEdit);
      mgr.add(actionClone);
      IStructuredSelection selection = viewer.getStructuredSelection();
      if (containsEnabled(selection))
      {
         mgr.add(actionDisable);
      }
      if (containsDisabled(selection))
      {
         mgr.add(actionEnable);
      }
      mgr.add(actionDelete);
   }

   /**
    * @see org.netxms.nxmc.base.views.View#refresh()
    */
   @Override
   public void refresh()
   {
      new Job(i18n.tr("Get object tools configuration"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            final List<ObjectTool> tl = session.getObjectTools();
            runInUIThread(new Runnable() {
               @Override
               public void run()
               {
                  tools.clear();
                  for(ObjectTool t : tl)
                     tools.put(t.getId(), t);
                  viewer.setInput(tools.values().toArray());
               }
            });
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot get object tools configuration");
         }
      }.start();
   }

   /**
    * Edit existing object tool
    */
   private void editTool()
   {
      IStructuredSelection selection = viewer.getStructuredSelection();
      if (selection.size() != 1)
         return;

      final Long toolId = ((ObjectTool)selection.getFirstElement()).getId();
      new Job(i18n.tr("Get object tool details"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            ObjectToolDetails objectToolDetails = session.getObjectToolDetails(toolId);
            runInUIThread(() -> {
               if (showObjectToolPropertyPages(objectToolDetails))
               {
                  saveObjectTool(objectToolDetails);
               }
            });
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Failed to load object tool details");
         }
      }.start();
   }

   /**
    * Create new tool
    */
   private void createTool()
   {
      final CreateNewToolDialog dlg = new CreateNewToolDialog(getWindow().getShell());
      if (dlg.open() != Window.OK)
         return;

      new Job(i18n.tr("Generate new object tool ID"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            final long toolId = session.generateObjectToolId();
            final ObjectToolDetails details = new ObjectToolDetails(toolId, dlg.getType(), dlg.getName());
            session.modifyObjectTool(details);
            runInUIThread(() -> {
               if (showObjectToolPropertyPages(details))
               {
                  if (details.isModified())
                     saveObjectTool(details);
               }
            });
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot generate object tool ID");
         }
      }.start();
   }

   /**
    * Delete selected tools
    */
   private void deleteTools()
   {
      IStructuredSelection selection = viewer.getStructuredSelection();
      if (selection.isEmpty())
         return;

      if (!MessageDialogHelper.openConfirm(getWindow().getShell(), i18n.tr("Confirmation"), i18n.tr("Do you really want to delete selected object tools?")))
         return;

      final Object[] objects = selection.toArray();
      new Job(i18n.tr("Delete object tools"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            for(int i = 0; i < objects.length; i++)
            {
               session.deleteObjectTool(((ObjectTool)objects[i]).getId());
            }
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot delete object tool");
         }
      }.start();
   }

   /**
    * Save object tool configuration on server
    * 
    * @param details object tool details
    */
   private void saveObjectTool(final ObjectToolDetails details)
   {
      new Job(i18n.tr("Save object tool configuration"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            session.modifyObjectTool(details);
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot save object tool configuration");
         }
      }.start();
   }

   /**
    * Enable/disable selected object tools
    */
   private void enableTools(final boolean enable)
   {
      IStructuredSelection selection = viewer.getStructuredSelection();
      if (selection.isEmpty())
         return;

      final List<Long> toolIdList = new ArrayList<>(selection.size());
      for(Object o : selection.toList())
      {
         if (((ObjectTool)o).isEnabled() != enable)
            toolIdList.add(((ObjectTool)o).getId());
      }

      new Job(enable ? i18n.tr("Enable object tools") : i18n.tr("Disable object tools"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            for(long toolId : toolIdList)
               session.enableObjectTool(toolId, enable);
         }

         @Override
         protected String getErrorMessage()
         {
            return enable ? i18n.tr("Cannot enable object tool") : i18n.tr("Cannot disable object tool");
         }
      }.start();
   }

   /**
    * Clone object tool
    */
   private void cloneTool()
   {
      final IStructuredSelection selection = viewer.getStructuredSelection();
      if (selection.size() != 1)
         return;

      final ObjectTool currentTool = (ObjectTool)selection.getFirstElement();

      final InputDialog dlg = new InputDialog(getWindow().getShell(), "Clone Object Tool", "Enter name for cloned object tool", currentTool.getName() + "2", new IInputValidator() {
         @Override
         public String isValid(String newText)
         {
            return newText.isBlank() ? "Name should not be blank" : null;
         }
      });
      if (dlg.open() != Window.OK)
         return;

      new Job(i18n.tr("Clone object tool"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            final long toolId = session.generateObjectToolId();
            ObjectToolDetails details = session.getObjectToolDetails(currentTool.getId());
            details.setId(toolId);
            details.setName(dlg.getValue());
            session.modifyObjectTool(details);
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot clone object tool");
         }
      }.start();
   }

   /**
    * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
    */
   @Override
   public void setFocus()
   {
      viewer.getControl().setFocus();
   }

   /**
    * @see org.netxms.api.client.SessionListener#notificationHandler(org.netxms.api.client.SessionNotification)
    */
   @Override
   public void notificationHandler(final SessionNotification n)
   {
      switch(n.getCode())
      {
         case SessionNotification.OBJECT_TOOLS_CHANGED:
            getDisplay().asyncExec(() -> {
               refresh();
            });
            break;
         case SessionNotification.OBJECT_TOOL_DELETED:
            getDisplay().asyncExec(() -> {
               tools.remove(n.getSubCode());
               viewer.setInput(tools.values().toArray());
            });
            break;
      }
   }

   /**
    * @see org.eclipse.ui.part.WorkbenchPart#dispose()
    */
   @Override
   public void dispose()
   {
      session.removeListener(this);
      super.dispose();
   }

   /**
    * Show Object tools configuration dialog
    * 
    * @param trap Object tool details object
    * @return true if OK was pressed
    */
   private boolean showObjectToolPropertyPages(final ObjectToolDetails objectTool)
   {
      PreferenceManager pm = new PreferenceManager();
      pm.addToRoot(new PreferenceNode("general", new ObjectToolsGeneral(objectTool)));
      pm.addToRoot(new PreferenceNode("access_control", new ObjectToolsAccessControl(objectTool)));
      pm.addToRoot(new PreferenceNode("filter", new ObjectToolsFilterPropertyPage(objectTool)));
      pm.addToRoot(new PreferenceNode("input_fields", new ObjectToolsInputFields(objectTool)));
      if (objectTool.getToolType() == ObjectTool.TYPE_AGENT_LIST || objectTool.getToolType() == ObjectTool.TYPE_SNMP_TABLE)
         pm.addToRoot(new PreferenceNode("columns", new ObjectToolsColumns(objectTool)));

      PreferenceDialog dlg = new PreferenceDialog(getWindow().getShell(), pm) {
         @Override
         protected void configureShell(Shell newShell)
         {
            super.configureShell(newShell);
            newShell.setText("Properties for " + objectTool.getCommandDisplayName());
         }
      };
      dlg.setBlockOnOpen(true);
      return dlg.open() == Window.OK;
   }

   /**
    * @see org.netxms.nxmc.base.views.ConfigurationView#isModified()
    */
   @Override
   public boolean isModified()
   {
      return false;
   }

   /**
    * @see org.netxms.nxmc.base.views.ConfigurationView#save()
    */
   @Override
   public void save()
   {
   }
}
