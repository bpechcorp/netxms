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
package org.netxms.ui.eclipse.objecttools.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.IDialogSettings;
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
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.dialogs.PropertyDialogAction;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;
import org.netxms.client.AccessListElement;
import org.netxms.client.NXCSession;
import org.netxms.client.SessionListener;
import org.netxms.client.SessionNotification;
import org.netxms.client.objecttools.ObjectTool;
import org.netxms.client.objecttools.ObjectToolDetails;
import org.netxms.ui.eclipse.actions.RefreshAction;
import org.netxms.ui.eclipse.console.resources.SharedIcons;
import org.netxms.ui.eclipse.jobs.ConsoleJob;
import org.netxms.ui.eclipse.objecttools.Activator;
import org.netxms.ui.eclipse.objecttools.Messages;
import org.netxms.ui.eclipse.objecttools.dialogs.CreateNewToolDialog;
import org.netxms.ui.eclipse.objecttools.propertypages.AccessControl;
import org.netxms.ui.eclipse.objecttools.propertypages.Columns;
import org.netxms.ui.eclipse.objecttools.propertypages.Filter;
import org.netxms.ui.eclipse.objecttools.propertypages.General;
import org.netxms.ui.eclipse.objecttools.propertypages.InputFields;
import org.netxms.ui.eclipse.objecttools.views.helpers.ObjectToolsComparator;
import org.netxms.ui.eclipse.objecttools.views.helpers.ObjectToolsFilter;
import org.netxms.ui.eclipse.objecttools.views.helpers.ObjectToolsLabelProvider;
import org.netxms.ui.eclipse.shared.ConsoleSharedData;
import org.netxms.ui.eclipse.tools.MessageDialogHelper;
import org.netxms.ui.eclipse.tools.WidgetHelper;
import org.netxms.ui.eclipse.widgets.FilterText;
import org.netxms.ui.eclipse.widgets.SortableTableViewer;

/**
 * Editor for object tools
 */
public class ObjectToolsEditor extends ViewPart implements SessionListener
{
   public static final String ID = "org.netxms.ui.eclipse.objecttools.views.ObjectToolsEditor"; //$NON-NLS-1$

	private static final String TABLE_CONFIG_PREFIX = "ObjectToolsEditor"; //$NON-NLS-1$
	
	public static final int COLUMN_ID = 0;
	public static final int COLUMN_NAME = 1;
	public static final int COLUMN_TYPE = 2;
	public static final int COLUMN_DESCRIPTION = 3;
	
	private Map<Long, ObjectTool> tools = new HashMap<Long, ObjectTool>();
	private SortableTableViewer viewer;
	private NXCSession session;
	private Action actionRefresh;
	private Action actionNew;
	private Action actionEdit;
   private Action actionClone;
	private Action actionDelete;
	private Action actionDisable;
	private Action actionEnable;
	private Action actionShowFilter;
	
	private Composite content;
	private FilterText filterText;
	private ObjectToolsFilter filter;
	private boolean initShowFilter = true;

   /**
    * @see org.eclipse.ui.part.ViewPart#init(org.eclipse.ui.IViewSite)
    */
	@Override
   public void init(IViewSite site) throws PartInitException
   {
      super.init(site);
      initShowFilter = getBooleanSetting("ObjectTools.showFilter", initShowFilter);
      session = ConsoleSharedData.getSession();
   }

   /**
    * Get boolean value from settings.
    * 
    * @param name parameter name
    * @param defval default value
    * @return value from settings or default
    */
   private static boolean getBooleanSetting(String name, boolean defval)
   {
      IDialogSettings settings = Activator.getDefault().getDialogSettings();
      if (settings.get(name) == null)
         return defval;
      return settings.getBoolean(name);
   }

   /**
    * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
    */
	@Override
	public void createPartControl(Composite parent)
	{
		// Initiate loading of required plugins if they was not loaded yet
		try
		{
			Platform.getAdapterManager().loadAdapter(new AccessListElement(0, 0), "org.eclipse.ui.model.IWorkbenchAdapter"); //$NON-NLS-1$
		}
		catch(Exception e)
		{
		}
		
      // Create content area
      content = new Composite(parent, SWT.NONE);
		FormLayout formLayout = new FormLayout();
		content.setLayout(formLayout);

		// Create filter
		filterText = new FilterText(content, SWT.NONE);
		filterText.addModifyListener(new ModifyListener() {
		   @Override
		   public void modifyText(ModifyEvent e)
		   {
		      onFilterModify();
		   }
		});
		filterText.setCloseAction(new Action() {
         @Override
         public void run()
         {
            enableFilter(false);
            actionShowFilter.setChecked(initShowFilter);
         }
      });
		
		final String[] columnNames = { Messages.get().ObjectToolsEditor_ColId, Messages.get().ObjectToolsEditor_ColName, Messages.get().ObjectToolsEditor_ColType, Messages.get().ObjectToolsEditor_ColDescr };
		final int[] columnWidths = { 90, 200, 100, 200 };
		viewer = new SortableTableViewer(content, columnNames, columnWidths, COLUMN_NAME, SWT.UP, SWT.FULL_SELECTION | SWT.MULTI);
		WidgetHelper.restoreTableViewerSettings(viewer, Activator.getDefault().getDialogSettings(), TABLE_CONFIG_PREFIX);
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new ObjectToolsLabelProvider());
		
		filter = new ObjectToolsFilter();
		viewer.addFilter(filter);
		
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
				WidgetHelper.saveTableViewerSettings(viewer, Activator.getDefault().getDialogSettings(), TABLE_CONFIG_PREFIX);
			}
		});
		
		// Setup layout
      FormData fd = new FormData();
      fd.left = new FormAttachment(0, 0);
      fd.top = new FormAttachment(filterText);
      fd.right = new FormAttachment(100, 0);
      fd.bottom = new FormAttachment(100, 0);
      viewer.getControl().setLayoutData(fd);

      fd = new FormData();
      fd.left = new FormAttachment(0, 0);
      fd.top = new FormAttachment(0, 0);
      fd.right = new FormAttachment(100, 0);
      filterText.setLayoutData(fd);
		
      getSite().setSelectionProvider(viewer);

      createActions();
      contributeToActionBars();
      createContextMenu();
      activateContext();
	
      session.addListener(this);
		
		// Set initial focus to filter input line
      if (initShowFilter)
         filterText.setFocus();
      else
         enableFilter(false); // Will hide filter area correctly
		
      refresh();
	}

	/**
    * Activate context
    */
   private void activateContext()
   {
      IContextService contextService = (IContextService)getSite().getService(IContextService.class);
      if (contextService != null)
      {
         contextService.activateContext("org.netxms.ui.eclipse.objecttools.context.ObjectTools"); //$NON-NLS-1$
      }
   }
	
	/**
    * Enable or disable filter
    * 
    * @param enable New filter state
    */
   public void enableFilter(boolean enable)
   {
      initShowFilter = enable;
      filterText.setVisible(initShowFilter);
      FormData fd = (FormData)viewer.getTable().getLayoutData();
      fd.top = enable ? new FormAttachment(filterText, 0, SWT.BOTTOM) : new FormAttachment(0, 0);
      content.layout();
      if (enable)
      {
         filterText.setFocus();
      }
      else
      {
         filterText.setText("");
         onFilterModify();
      }
   }

   /**
    * Handler for filter modification
    */
   public void onFilterModify()
   {
      final String text = filterText.getText();
      filter.setFilterString(text);
      viewer.refresh(false);
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
      final IHandlerService handlerService = (IHandlerService)getSite().getService(IHandlerService.class);

      actionShowFilter = new Action("Show filter", Action.AS_CHECK_BOX) {
         @Override
         public void run()
         {
            enableFilter(!initShowFilter);
            actionShowFilter.setChecked(initShowFilter);
         }
      };
      actionShowFilter.setImageDescriptor(SharedIcons.FILTER);
      actionShowFilter.setChecked(initShowFilter);
      actionShowFilter.setActionDefinitionId("org.netxms.ui.eclipse.objecttools.commands.showFilter"); //$NON-NLS-1$
      handlerService.activateHandler(actionShowFilter.getActionDefinitionId(), new ActionHandler(actionShowFilter));

      actionRefresh = new RefreshAction(this) {
			@Override
			public void run()
			{
            refresh();
			}
		};

		actionNew = new Action(Messages.get().ObjectToolsEditor_New) {
			@Override
			public void run()
			{
				createTool();
			}
		};
		actionNew.setImageDescriptor(SharedIcons.ADD_OBJECT);

		actionEdit = new PropertyDialogAction(getSite(), viewer) {
			@Override
			public void run()
			{
            IStructuredSelection selection = viewer.getStructuredSelection();
				if (selection.size() != 1)
					return;

				// Check if we have details loaded or can load before showing properties dialog
				// If there will be error, adapter factory will show error message to user
				if (Platform.getAdapterManager().getAdapter(selection.getFirstElement(), ObjectToolDetails.class) == null)
					return;

		      ObjectToolDetails objectToolDetails = (ObjectToolDetails)Platform.getAdapterManager().getAdapter(selection.getFirstElement(), ObjectToolDetails.class);   
		      if (showObjectToolPropertyPages(objectToolDetails))
		      {
		         saveObjectTool(objectToolDetails);
		      }
			}
		};
		actionEdit.setImageDescriptor(SharedIcons.EDIT);

      actionDelete = new Action(Messages.get().ObjectToolsEditor_Delete) {
         @Override
         public void run()
         {
            deleteTools();
         }
      };
      actionDelete.setImageDescriptor(SharedIcons.DELETE_OBJECT);

      actionDisable = new Action(Messages.get().ObjectToolsEditor_Disable) {
         @Override
         public void run()
         {
            enableTools(false);
         }
      };

      actionEnable = new Action(Messages.get().ObjectToolsEditor_Enable) {
         @Override
         public void run()
         {
            enableTools(true);
         }
      };

      actionClone = new Action(Messages.get().ObjectToolsEditor_Clone) {
         @Override
         public void run()
         {
            cloneTool();
         }
      };
   }

	/**
	 * Contribute actions to action bar
	 */
	private void contributeToActionBars()
	{
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	/**
	 * Fill local pull-down menu
	 * 
	 * @param manager
	 *           Menu manager for pull-down menu
	 */
	private void fillLocalPullDown(IMenuManager manager)
	{
		manager.add(actionNew);
		manager.add(new Separator());
		manager.add(actionShowFilter);
		manager.add(new Separator());
		manager.add(actionRefresh);
	}

	/**
	 * Fill local tool bar
	 * 
	 * @param manager
	 *           Menu manager for local toolbar
	 */
	private void fillLocalToolBar(IToolBarManager manager)
	{
		manager.add(actionNew);
		manager.add(new Separator());
		manager.add(actionShowFilter);
		manager.add(new Separator());
		manager.add(actionRefresh);
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
	 * Refresh tool list
	 */
	private void refresh()
	{
		new ConsoleJob(Messages.get().ObjectToolsEditor_JobGetConfig, this, Activator.PLUGIN_ID) {
			@Override
			protected void runInternal(IProgressMonitor monitor) throws Exception
			{
				final List<ObjectTool> tl = session.getObjectTools();
            runInUIThread(() -> {
               tools.clear();
               for(ObjectTool t : tl)
                  tools.put(t.getId(), t);
               viewer.setInput(tools.values().toArray());
				});
			}

			@Override
			protected String getErrorMessage()
			{
				return Messages.get().ObjectToolsEditor_JobGetConfigError;
			}
		}.start();
	}

	/**
	 * Create new tool
	 */
	private void createTool()
	{
		final CreateNewToolDialog dlg = new CreateNewToolDialog(getSite().getShell());
		if (dlg.open() == Window.OK)
		{
			new ConsoleJob(Messages.get().ObjectToolsEditor_JobNewId, this, Activator.PLUGIN_ID) {
				@Override
				protected void runInternal(IProgressMonitor monitor) throws Exception
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
					return Messages.get(getDisplay()).ObjectToolsEditor_JobNewIdError;
				}
			}.start();
		}
	}

	/**
	 * Delete selected tools
	 */
	private void deleteTools()
	{
      IStructuredSelection selection = viewer.getStructuredSelection();
		if (selection.isEmpty())
			return;
		
		if (!MessageDialogHelper.openConfirm(getSite().getShell(), Messages.get().ObjectToolsEditor_Confirmation, Messages.get().ObjectToolsEditor_DeleteConfirmation))
			return;
		
		final Object[] objects = selection.toArray();
		new ConsoleJob(Messages.get().ObjectToolsEditor_JobDelete, this, Activator.PLUGIN_ID) {
			@Override
			protected String getErrorMessage()
			{
				return Messages.get(getDisplay()).ObjectToolsEditor_JobDeleteError;
			}

			@Override
			protected void runInternal(IProgressMonitor monitor) throws Exception
			{
				for(int i = 0; i < objects.length; i++)
				{
					session.deleteObjectTool(((ObjectTool)objects[i]).getId());
				}
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
		new ConsoleJob(Messages.get().ObjectToolsEditor_JobSave, this, Activator.PLUGIN_ID) {
			@Override
			protected void runInternal(IProgressMonitor monitor) throws Exception
			{
				session.modifyObjectTool(details);
			}

			@Override
			protected String getErrorMessage()
			{
				return Messages.get(getDisplay()).ObjectToolsEditor_JobSaveError;
			}
		}.schedule();
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

      new ConsoleJob(enable ? Messages.get().ObjectToolsEditor_EnableObjTool : Messages.get().ObjectToolsEditor_DisableObjTool, this, Activator.PLUGIN_ID) {
         @Override
         protected void runInternal(IProgressMonitor monitor) throws Exception
         {
            for(long toolId : toolIdList)
               session.enableObjectTool(toolId, enable);
         }

         @Override
         protected String getErrorMessage()
         {
            return Messages.get().ObjectToolsEditor_ErrorDisablingObjectTools;
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

      final InputDialog dlg = new InputDialog(getSite().getShell(), "Clone Object Tool", "Enter name for cloned object tool", currentTool.getName() + "2", new IInputValidator() {
         @Override
         public String isValid(String newText)
         {
            return newText.isBlank() ? "Name should not be blank" : null;
         }
      });
      if (dlg.open() == Window.OK)
      {
         new ConsoleJob(Messages.get().ObjectToolsEditor_CloneObjectTool, this, Activator.PLUGIN_ID) {
            @Override
            protected void runInternal(IProgressMonitor monitor) throws Exception
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
               return Messages.get().ObjectToolsEditor_CloneError;
            }
         }.start();
      }
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
            getSite().getShell().getDisplay().asyncExec(() -> {
               refresh();
            });
            break;
         case SessionNotification.OBJECT_TOOL_DELETED:
            getSite().getShell().getDisplay().asyncExec(() -> {
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
		IDialogSettings settings = Activator.getDefault().getDialogSettings();
		settings.put("ObjectTools.showFilter", initShowFilter);
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
      pm.addToRoot(new PreferenceNode("general", new General(objectTool)));
      pm.addToRoot(new PreferenceNode("access_control", new AccessControl(objectTool)));
      pm.addToRoot(new PreferenceNode("filter", new Filter(objectTool)));
      pm.addToRoot(new PreferenceNode("input_fields", new InputFields(objectTool)));
      if (objectTool.getToolType() == ObjectTool.TYPE_AGENT_LIST || objectTool.getToolType() == ObjectTool.TYPE_SNMP_TABLE)
         pm.addToRoot(new PreferenceNode("columns", new Columns(objectTool)));

      PreferenceDialog dlg = new PreferenceDialog(getViewSite().getShell(), pm) {
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
}
