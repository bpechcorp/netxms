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
package org.netxms.nxmc.base.views;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.netxms.nxmc.keyboard.KeyStroke;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.resources.SharedIcons;
import org.netxms.nxmc.tools.WidgetHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

/**
 * View folder (multiple views represented as tabs)
 */
public class ViewFolder extends ViewContainer
{
   private static final Logger logger = LoggerFactory.getLogger(ViewFolder.class);

   private final I18n i18n = LocalizationHelper.getI18n(ViewFolder.class);

   private CTabFolder tabFolder;
   private Composite topRightControl;
   private boolean allViewsAreCloseable = false;
   private boolean useGlobalViewId = false;
   private String preferredViewId = null;
   private String lastViewId = null;
   private View activeView = null;
   private boolean contextChange = false;
   private Map<String, View> views = new HashMap<>();
   private Map<String, CTabItem> tabs = new HashMap<>();
   private Set<ViewFolderSelectionListener> selectionListeners = new HashSet<>();

   /**
    * Create new view folder.
    *
    * @param window owning window
    * @param perspective owning perspective
    * @param parent parent composite
    * @param enableViewExtraction enable/disable view extraction into separate window
    * @param enableViewPinning nable/disable view extraction into "Pinboard" perspective
    */
   public ViewFolder(Window window, Perspective perspective, Composite parent, boolean enableViewExtraction, boolean enableViewPinning, boolean enableNavigationHistory)
   {
      super(window, perspective, parent, SWT.NONE);

      setLayout(new FillLayout());
      tabFolder = new CTabFolder(this, SWT.TOP | SWT.BORDER);
      tabFolder.setUnselectedCloseVisible(true);
      WidgetHelper.disableTabFolderSelectionBar(tabFolder);
      tabFolder.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            if (contextChange)
               return; // Do not handle selection change durint context change

            CTabItem tabItem = tabFolder.getSelection();
            View view = (tabItem != null) ? (View)tabItem.getData("view") : null;
            if (view != null)
               activateView(view, tabItem);
            fireSelectionListeners(view);
         }
      });
      tabFolder.addCTabFolder2Listener(new CTabFolder2Adapter() {
         @Override
         public void close(CTabFolderEvent event)
         {
            CTabItem tabItem = tabFolder.getSelection();
            View view = (tabItem != null) ? (View)tabItem.getData("view") : null;
            if (view != null)
            {
               event.doit = view.beforeClose();
            }
            if (event.doit)
            {
               if (activeView != null)
               {
                  activeView.deactivate();
                  activeView = null;
               }
               if (lastViewId != null)
               {
                  showView(lastViewId);
               }
            }
         }
      });

      topRightControl = new Composite(tabFolder, SWT.NONE);
      GridLayout layout = new GridLayout();
      layout.numColumns = 2;
      layout.marginHeight = 0;
      layout.marginWidth = 0;
      topRightControl.setLayout(layout);
      tabFolder.setTopRight(topRightControl);

      viewToolBarManager = new ToolBarManager(SWT.FLAT | SWT.WRAP | SWT.RIGHT);
      viewToolBar = viewToolBarManager.createControl(topRightControl);
      viewToolBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      viewControlBar = new ToolBar(topRightControl, SWT.FLAT | SWT.WRAP | SWT.RIGHT);
      viewControlBar.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, false, true));

      if (enableNavigationHistory)
      {
         navigationBack = new ToolItem(viewControlBar, SWT.PUSH);
         navigationBack.setImage(SharedIcons.IMG_NAV_BACKWARD);
         navigationBack.setToolTipText(i18n.tr("Back (Alt+Left)"));
         navigationBack.setEnabled(false);
         navigationBack.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
               navigateBack();
            }
         });
         keyBindingManager.addBinding(SWT.ALT, SWT.ARROW_LEFT, new Action() {
            @Override
            public void run()
            {
               navigateBack();
            }
         });

         navigationForward = new ToolItem(viewControlBar, SWT.PUSH);
         navigationForward.setImage(SharedIcons.IMG_NAV_FORWARD);
         navigationForward.setToolTipText(i18n.tr("Forward (Alt+Right)"));
         navigationForward.setEnabled(false);
         navigationForward.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
               navigateForward();
            }
         });
         keyBindingManager.addBinding(SWT.ALT, SWT.ARROW_RIGHT, new Action() {
            @Override
            public void run()
            {
               navigateForward();
            }
         });
      }

      ToolItem refreshView = new ToolItem(viewControlBar, SWT.PUSH);
      refreshView.setImage(SharedIcons.IMG_REFRESH);
      refreshView.setToolTipText(i18n.tr("Refresh (F5)"));
      refreshView.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            View view = getActiveView();
            if (view != null)
            {
               view.refresh();
            }
         }
      });

      if (enableViewPinning)
      {
         ToolItem pinView = new ToolItem(viewControlBar, SWT.PUSH);
         pinView.setImage(SharedIcons.IMG_PIN);
         pinView.setToolTipText(i18n.tr("Add view to pinboard (F7)"));
         pinView.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
               pinActiveView();
            }
         });
         keyBindingManager.addBinding(SWT.NONE, SWT.F7, new Action() {
            @Override
            public void run()
            {
               pinActiveView();
            }
         });
      }
      if (enableViewExtraction)
      {
         ToolItem popOutView = new ToolItem(viewControlBar, SWT.PUSH);
         popOutView.setImage(SharedIcons.IMG_POP_OUT);
         popOutView.setToolTipText(i18n.tr("Pop out view (F8)"));
         popOutView.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
               extractActiveView();
            }
         });
         keyBindingManager.addBinding(SWT.NONE, SWT.F8, new Action() {
            @Override
            public void run()
            {
               extractActiveView();
            }
         });
      }
   }

   /**
    * Add view to folder. View will be created immediately if it is context-insensitive or is allowed in current context, otherwise
    * it's creation will be delayed until valid context change.
    *
    * @param view view to add
    */
   public void addView(View view)
   {
      addView(view, false, false);
   }

   /**
    * Add view to folder. View will be created immediately if it is context-insensitive or is allowed in current context, otherwise
    * it's creation will be delayed until valid context change.
    *
    * @param view view to add
    * @param activate if set to true, view will be activated
    * @param ignoreContext set to true to ignore current context
    */
   public void addView(View view, boolean activate, boolean ignoreContext)
   {
      String viewId = getViewId(view);
      View currentView = views.get(viewId);
      if (currentView != null)
      {
         if (currentView == view)
            return; // View already added

         // Dispose current view with same ID and replace with provided one
         currentView.dispose();
         CTabItem tabItem = tabs.remove(viewId);
         if (tabItem != null)
            tabItem.dispose();
      }

      views.put(viewId, view);

      if (ignoreContext || !(view instanceof ViewWithContext) || ((ViewWithContext)view).isValidForContext(context))
      {
         view.create(this, tabFolder, onFilterCloseCallback);
         CTabItem tabItem = createViewTab(view, ignoreContext);
         if (activate)
         {
            tabFolder.setSelection(tabItem);
            activateView(view, tabItem);
         }
      }
   }

   /**
    * Find view with given ID.
    *
    * @param id view ID
    * @return view with given ID or null if not exist
    */
   public View findView(String id)
   {
      return views.get(id);
   }

   /**
    * Remove view from folder. View content will be destroyed.
    *
    * @param id ID of view to remove
    */
   public void removeView(String id)
   {
      View view = views.remove(id);
      if (view != null)
      {
         view.dispose();
         CTabItem tabItem = tabs.remove(getViewId(view));
         if (tabItem != null)
            tabItem.dispose();
      }
   }

   /**
    * Show view with given ID.
    *
    * @param viewId view ID
    * @return true if view was shown
    */
   public boolean showView(String viewId)
   {
      View view = views.get(viewId);
      if (view == null)
         return false;

      CTabItem tabItem = tabs.get(viewId);
      if (tabItem == null)
         return false;

      tabFolder.setSelection(tabItem);
      activateView(view, tabItem);
      return true;
   }

   /**
    * Activate view internal handling of view activation.
    *
    * @param view view to activate
    * @param tabItem tab item holding view
    */
   private void activateView(View view, CTabItem tabItem)
   {
      if ((view instanceof ViewWithContext) && !ignoreContextForView(tabItem))
      {
         if (((ViewWithContext)view).getContext() != context)
            ((ViewWithContext)view).setContext(context);
      }

      updateViewToolBar(view);
      updateViewMenu(view);

      if (view.hasFilter())
      {
         if (enableFilter == null)
         {
            enableFilter = new ToolItem(viewControlBar, SWT.CHECK, (navigationBack != null) ? 2 : 0);
            enableFilter.setImage(SharedIcons.IMG_FILTER);
            enableFilter.setToolTipText(String.format(i18n.tr("Show filter (%s)"), KeyStroke.normalizeDefinition("M1+F2")));
            enableFilter.addSelectionListener(new SelectionAdapter() {
               @Override
               public void widgetSelected(SelectionEvent e)
               {
                  View view = getActiveView();
                  if (view != null)
                  {
                     view.enableFilter(enableFilter.getSelection());
                  }
               }
            });
         }
         enableFilter.setSelection(view.isFilterEnabled());
      }
      else if (enableFilter != null)
      {
         enableFilter.dispose();
         enableFilter = null;
      }

      if ((navigationBack != null) && (navigationForward != null))
      {
         navigationHistory = (view instanceof NavigationView) ? ((NavigationView)view).getNavigationHistory() : null;
         navigationForward.setEnabled((navigationHistory != null) && navigationHistory.canGoForward());
         navigationBack.setEnabled((navigationHistory != null) && navigationHistory.canGoBackward());
      }

      if (activeView != null)
         activeView.deactivate();
      if (!contextChange)
         lastViewId = (activeView != null) ? getViewId(activeView) : null;
      activeView = view;
      if (!contextChange)
         preferredViewId = getViewId(activeView);

      view.activate();
   }

   /**
    * Update trim (title, actions on toolbar, etc.) for given view.
    *
    * @param view view to update
    * @return true if view trim was updated
    */
   public boolean updateViewTrim(View view)
   {
      boolean updated = false;
      CTabItem tab = tabs.get(getViewId(view));
      if (tab != null)
      {
         tab.setText(ignoreContextForView(tab) ? view.getFullName() : view.getName());
         tab.setImage(view.getImage());
         updated = true;
      }
      return updated;
   }

   /**
    * Create tab for view
    *
    * @param view view to add
    * @param ignoreContext set to true to ignore current context
    * @return created tab item
    */
   private CTabItem createViewTab(View view, boolean ignoreContext)
   {
      int index = 0;
      int priority = view.getPriority();
      for(CTabItem i : tabFolder.getItems())
      {
         if (((View)i.getData("view")).getPriority() > priority)
            break;
         index++;
      }

      CTabItem tabItem = new CTabItem(tabFolder, SWT.NONE, index);
      tabItem.setControl(view.getViewArea());
      tabItem.setText(ignoreContext ? view.getFullName() : view.getName());
      tabItem.setImage(view.getImage());
      tabItem.setData("view", view);
      tabItem.setData("ignoreContext", Boolean.valueOf(ignoreContext));
      tabItem.setShowClose(allViewsAreCloseable || view.isCloseable());
      tabItem.addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            View view = (View)e.widget.getData("view");
            String viewId = getViewId(view);
            if (e.widget.getData("keepView") == null)
            {
               views.remove(viewId);
               view.dispose();
            }
            tabs.remove(viewId);
         }
      });
      tabs.put(getViewId(view), tabItem);
      return tabItem;
   }

   /**
    * Set current context. Will update context in all context-sensitive views and hide/show views as necessary.
    *
    * @param context new context
    */
   public void setContext(Object context)
   {
      if (context == this.context)
         return;

      if (activeView != null)
         activeView.deactivate();

      this.context = context;
      contextChange = true;

      boolean invalidActiveView = false;
      for(View view : views.values())
      {
         if (!(view instanceof ViewWithContext))
            continue;

         if (((ViewWithContext)view).isValidForContext(context))
         {
            view.setVisible(true);
            if (!tabs.containsKey(getViewId(view)))
            {
               if (view.isCreated())
                  view.setVisible(true);
               else
                  view.create(this, tabFolder, onFilterCloseCallback);
               createViewTab(view, false);
            }
         }
         else
         {
            String viewId = getViewId(view);
            CTabItem tabItem = tabs.remove(viewId);
            if (tabItem != null)
            {
               logger.debug("View " + viewId + " is not valid for current context");
               tabItem.setData("keepView", Boolean.TRUE); // Prevent view dispose by tab's dispose listener
               tabItem.dispose();
               view.setVisible(false);
               if (view == activeView)
                  invalidActiveView = true;
            }
         }
      }

      CTabItem tabItem = tabFolder.getSelection();
      activeView = (tabItem != null) ? (View)tabItem.getData("view") : null;
      if (((preferredViewId != null) || (lastViewId != null)) && ((activeView == null) || !preferredViewId.equals(getViewId(activeView))))
      {
         if (showView(preferredViewId) || showView(lastViewId))
            invalidActiveView = false;
      }

      // Select first view if none were selected
      if (invalidActiveView || (tabFolder.getSelectionIndex() == -1))
      {
         tabFolder.setSelection(0);
         tabItem = tabFolder.getSelection();
         activeView = (tabItem != null) ? (View)tabItem.getData("view") : null;
      }

      contextChange = false;

      if (activeView != null)
      {
         if (activeView instanceof ViewWithContext)
            ((ViewWithContext)activeView).setContext(context);
         activeView.activate();
      }
   }

   /**
    * @see org.netxms.nxmc.base.views.ViewContainer#getActiveView()
    */
   @Override
   protected View getActiveView()
   {
      return tabFolder.isDisposed() ? null : activeView;
   }

   /**
    * Add selection listener.
    *
    * @param listener listener to add.
    */
   public void addSelectionListener(ViewFolderSelectionListener listener)
   {
      selectionListeners.add(listener);
   }

   /**
    * Remove selection listener.
    * 
    * @param listener listener to remove
    */
   public void removeSelectionListener(ViewFolderSelectionListener listener)
   {
      selectionListeners.remove(listener);
   }

   /**
    * Call registered selection listeners when view selection changed.
    *
    * @param view new selection
    */
   private void fireSelectionListeners(View view)
   {
      for(ViewFolderSelectionListener l : selectionListeners)
         l.viewSelected(view);
   }

   /**
    * Check if all views are closeable.
    *
    * @return true if all views are closeable
    */
   public boolean areAllViewsCloseable()
   {
      return allViewsAreCloseable;
   }

   /**
    * If set to true then all views will be marked as closeable and isCloseable() result will be ignored.
    *
    * @param allViewsAreCloseable if true all views will be marked as closeable
    */
   public void setAllViewsAsCloseable(boolean allViewsAreCloseable)
   {
      this.allViewsAreCloseable = allViewsAreCloseable;
   }

   /**
    * Check if context should be ignored for view in given tab.
    *
    * @param tabItem tab item
    * @return true if context should be ignored
    */
   private static boolean ignoreContextForView(CTabItem tabItem)
   {
      Object ignoreContext = tabItem.getData("ignoreContext");
      return (ignoreContext != null) && (ignoreContext instanceof Boolean) && (Boolean)ignoreContext;
   }

   /**
    * Get view identification mode.
    *
    * @return true if view folder uses global ID to check if view already present
    */
   public boolean isUseGlobalViewId()
   {
      return useGlobalViewId;
   }

   /**
    * Set view identification mode. If useGlobalViewId set to true, view folder will use global IDs to check if view already
    * present, otherwise base ID will be used.
    *
    * @param useGlobalViewId true to use global view ID to identify duplicate views
    */
   public void setUseGlobalViewId(boolean useGlobalViewId)
   {
      this.useGlobalViewId = useGlobalViewId;
   }

   /**
    * Get ID for given view. Depending on operation mode will return base or global view ID.
    * 
    * @param view view to get ID from
    * @return view ID according to current operation mode
    */
   private String getViewId(View view)
   {
      return useGlobalViewId ? view.getGlobalId() : view.getBaseId();
   }

   /**
    * @see org.eclipse.swt.widgets.Composite#setFocus()
    */
   @Override
   public boolean setFocus()
   {
      View view = getActiveView();
      if ((view != null) && !view.isClientAreaDisposed())
         view.setFocus();
      else
         super.setFocus();
      return true;
   }
}
