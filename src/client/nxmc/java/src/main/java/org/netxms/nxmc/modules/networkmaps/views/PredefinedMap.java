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
package org.netxms.nxmc.modules.networkmaps.views;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Shell;
import org.netxms.base.NXCommon;
import org.netxms.client.NXCObjectModificationData;
import org.netxms.client.NXCSession;
import org.netxms.client.constants.UserAccessRights;
import org.netxms.client.maps.MapLayoutAlgorithm;
import org.netxms.client.maps.NetworkMapLink;
import org.netxms.client.maps.NetworkMapPage;
import org.netxms.client.maps.elements.NetworkMapDCIContainer;
import org.netxms.client.maps.elements.NetworkMapDCIImage;
import org.netxms.client.maps.elements.NetworkMapDecoration;
import org.netxms.client.maps.elements.NetworkMapElement;
import org.netxms.client.maps.elements.NetworkMapObject;
import org.netxms.client.maps.elements.NetworkMapTextBox;
import org.netxms.client.objects.AbstractObject;
import org.netxms.client.objects.NetworkMap;
import org.netxms.nxmc.base.jobs.Job;
import org.netxms.nxmc.base.propertypages.PropertyDialog;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.imagelibrary.ImageProvider;
import org.netxms.nxmc.modules.imagelibrary.ImageUpdateListener;
import org.netxms.nxmc.modules.imagelibrary.dialogs.ImageSelectionDialog;
import org.netxms.nxmc.modules.networkmaps.dialogs.EditGroupBoxDialog;
import org.netxms.nxmc.modules.networkmaps.propertypages.DCIContainerDataSources;
import org.netxms.nxmc.modules.networkmaps.propertypages.DCIContainerGeneral;
import org.netxms.nxmc.modules.networkmaps.propertypages.DCIImageGeneral;
import org.netxms.nxmc.modules.networkmaps.propertypages.DCIImageRules;
import org.netxms.nxmc.modules.networkmaps.propertypages.LinkDataSources;
import org.netxms.nxmc.modules.networkmaps.propertypages.LinkGeneral;
import org.netxms.nxmc.modules.networkmaps.propertypages.TextBoxGeneral;
import org.netxms.nxmc.modules.networkmaps.views.helpers.LinkEditor;
import org.netxms.nxmc.modules.objects.ObjectPropertiesManager;
import org.netxms.nxmc.modules.objects.dialogs.ObjectSelectionDialog;
import org.netxms.nxmc.resources.ResourceManager;
import org.netxms.nxmc.resources.SharedIcons;
import org.netxms.nxmc.tools.ColorConverter;
import org.netxms.nxmc.tools.MessageDialogHelper;
import org.xnap.commons.i18n.I18n;

/**
 * View for predefined map
 */
public class PredefinedMap extends AbstractNetworkMapView implements ImageUpdateListener
{
   private static final I18n i18n = LocalizationHelper.getI18n(PredefinedMap.class);

	private Action actionAddObject;
	private Action actionAddDCIContainer;
	private Action actionLinkObjects;
	private Action actionAddGroupBox;
	private Action actionAddImage;
	private Action actionRemove;
	private Action actionDCIContainerProperties;
	private Action actionDCIImageProperties;
	private Action actionMapProperties;
	private Action actionLinkProperties;
	private Action actionAddDCIImage;
	private Action actionAddTextBox;
	private Action actionTextBoxProperties;
	private Action actionGroupBoxProperties;
   private Action actionImageProperties;
	private Color defaultLinkColor = null;
   private boolean disableGeolocationBackground = false;
   private boolean readOnly = true;
   private Map<Long, Boolean> readOnlyFlagsCache = new HashMap<>();

	/**
	 * Create predefined map view
	 */
	public PredefinedMap()
	{
      super(i18n.tr("Map"), ResourceManager.getImageDescriptor("icons/object-views/netmap.png"), "PredefinedMap");
	}

   /**
    * @see org.netxms.nxmc.modules.objects.views.ObjectView#isValidForContext(java.lang.Object)
    */
   @Override
   public boolean isValidForContext(Object context)
   {
      return (context != null) && (context instanceof NetworkMap);
   }

   /**
    * @see org.netxms.nxmc.modules.objects.views.ObjectView#onObjectChange(org.netxms.client.objects.AbstractObject)
    */
   @Override
   protected void onObjectChange(final AbstractObject object)
   {
      Boolean cachedFlag = readOnlyFlagsCache.get(object.getObjectId());
      if (cachedFlag != null)
      {
         readOnly = cachedFlag;
         reconfigureViewer();
         refresh();
      }
      else
      {
         readOnly = true;
         Job job = new Job("Get map effective rights", this) {
            @Override
            protected void run(IProgressMonitor monitor) throws Exception
            {
               final Boolean readOnly = ((object.getEffectiveRights() & UserAccessRights.OBJECT_ACCESS_MODIFY) == 0);
               runInUIThread(new Runnable() {
                  @Override
                  public void run()
                  {
                     readOnlyFlagsCache.put(object.getObjectId(), readOnly);
                     PredefinedMap.this.readOnly = readOnly;
                     reconfigureViewer();
                     updateToolBar();
                     updateMenu();
                     refresh();
                  }
               });
            }

            @Override
            protected String getErrorMessage()
            {
               return "Cannot get effective rights for map";
            }
         };
         job.setUser(false);
         job.start();
      }
   }

   /**
    * Reconfigure viewer
    */
   private void reconfigureViewer()
   {
      allowManualLayout = !readOnly;
      viewer.setDraggingEnabled(!readOnly);

      NetworkMap mapObject = getMapObject();
      if (mapObject == null)
         return;

      if (mapObject.getLayout() == MapLayoutAlgorithm.MANUAL)
      {
         automaticLayoutEnabled = false;
      }
      else
      {
         automaticLayoutEnabled = true;
         layoutAlgorithm = mapObject.getLayout();
      }

      if ((mapObject.getBackground() != null) && (mapObject.getBackground().compareTo(NXCommon.EMPTY_GUID) != 0))
      {
         if (mapObject.getBackground().equals(org.netxms.client.objects.NetworkMap.GEOMAP_BACKGROUND))
         {
            if (!disableGeolocationBackground)
               viewer.setBackgroundImage(ImageProvider.getInstance().getImage(mapObject.getBackground()), mapObject.isCenterBackgroundImage());
         }
         else
         {
            viewer.setBackgroundImage(ImageProvider.getInstance().getImage(mapObject.getBackground()), mapObject.isCenterBackgroundImage());
         }
      }

      setConnectionRouter(mapObject.getDefaultLinkRouting(), false);
      viewer.setBackgroundColor(ColorConverter.rgbFromInt(mapObject.getBackgroundColor()));

      if (mapObject.getDefaultLinkColor() >= 0)
      {
         defaultLinkColor = new Color(viewer.getControl().getDisplay(), ColorConverter.rgbFromInt(mapObject.getDefaultLinkColor()));
         labelProvider.setDefaultLinkColor(defaultLinkColor);
      }

      setObjectDisplayMode(mapObject.getObjectDisplayMode(), false);
      labelProvider.setShowStatusBackground(mapObject.isShowStatusBackground());
      labelProvider.setShowStatusFrame(mapObject.isShowStatusFrame());
      labelProvider.setShowStatusIcons(mapObject.isShowStatusIcon());
      labelProvider.setShowLinkDirection(mapObject.isShowLinkDirection());
      labelProvider.setTranslucentLabelBackground(mapObject.isTranslucentLabelBackground());

      actionShowStatusBackground.setChecked(labelProvider.isShowStatusBackground());
      actionShowStatusFrame.setChecked(labelProvider.isShowStatusFrame());
      actionShowStatusIcon.setChecked(labelProvider.isShowStatusIcons());
      actionShowLinkDirection.setChecked(labelProvider.isShowLinkDirection());
      actionTranslucentLabelBkgnd.setChecked(labelProvider.isTranslucentLabelBackground());
   }

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#setupMapControl()
    */
	@Override
	public void setupMapControl()
	{
      viewer.addSelectionChangedListener(new ISelectionChangedListener() {
         @Override
         public void selectionChanged(SelectionChangedEvent event)
         {
            actionLinkObjects.setEnabled(!readOnly && (((IStructuredSelection)event.getSelection()).size() == 2));
         }
      });
   
      addDropSupport();

		ImageProvider.getInstance().addUpdateListener(this);

      reconfigureViewer();
	}

	/**
	 * Add drop support
	 */
	private void addDropSupport()
	{
		final Transfer[] transfers = new Transfer[] { LocalSelectionTransfer.getTransfer() };
		viewer.addDropSupport(DND.DROP_COPY | DND.DROP_MOVE, transfers, new ViewerDropAdapter(viewer) {
			private int x;
			private int y;

			@SuppressWarnings("rawtypes")
			@Override
			public boolean validateDrop(Object target, int operation, TransferData transferType)
			{
            if (readOnly || !LocalSelectionTransfer.getTransfer().isSupportedType(transferType))
					return false;

				IStructuredSelection selection = (IStructuredSelection)LocalSelectionTransfer.getTransfer().getSelection();
				Iterator it = selection.iterator();
				while(it.hasNext())
				{
					Object object = it.next();
					if (!((object instanceof AbstractObject) && ((AbstractObject)object).isAllowedOnMap())) 
						return false;
				}

				return true;
			}

			@SuppressWarnings("unchecked")
			@Override
			public boolean performDrop(Object data)
			{
				IStructuredSelection selection = (IStructuredSelection)LocalSelectionTransfer.getTransfer().getSelection();
				addObjectsFromList(selection.toList(), viewer.getControl().toControl(x, y));
				return true;
			}

			@Override
			public void dropAccept(DropTargetEvent event)
			{
				x = event.x;
				y = event.y;
				super.dropAccept(event);
			}
		});
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#buildMapPage()
    */
	@Override
	protected void buildMapPage()
	{
	   NetworkMap mapObject = getMapObject();
      mapPage = (mapObject != null) ? mapObject.createMapPage() : new NetworkMapPage("EMPTY");
      addDciToRequestList();
	}

	/**
	 * Synchronize objects, required when interface objects are placed on the map
	 */
	private void syncObjects()
	{
      mapPage = getMapObject().createMapPage();
	   final List<Long> mapObjectIds = mapPage.getObjectIds();	  
	   mapObjectIds.addAll(mapPage.getAllLinkStatusObjects());

      Job job = new Job(String.format(i18n.tr("Synchronize objects for network map %s"), getObjectName()), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            session.syncMissingObjects(mapObjectIds, true, NXCSession.OBJECT_SYNC_WAIT);
            runInUIThread(new Runnable() {
               @Override
               public void run()
               {
                  if (!viewer.getControl().isDisposed())
                     refresh();
               }
            });
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot synchronize objects");
         }
      };
      job.setUser(false);
      job.start();
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#createActions()
    */
	@Override
	protected void createActions()
	{
		super.createActions();

      actionAddObject = new Action(i18n.tr("&Add object..."), SharedIcons.ADD_OBJECT) {
			@Override
			public void run()
			{
				addObjectToMap();
			}
		};
      addKeyBinding("M1+M3+A", actionAddObject);

      actionAddDCIContainer = new Action(i18n.tr("Add DCI &container...")) {
         @Override
         public void run()
         {
            addDCIContainer();
         }
      };
      addKeyBinding("M1+M3+C", actionAddDCIContainer);
      
      actionAddDCIImage = new Action(i18n.tr("Add DCI &image...")) {
         @Override
         public void run()
         {
            addDCIImage();
         }
      };
		
      actionAddGroupBox = new Action(i18n.tr("&Group box...")) {
			@Override
			public void run()
			{
				addGroupBox();
			}
		};

      actionGroupBoxProperties = new Action(i18n.tr("&Properties")) {
         @Override
         public void run()
         {
            editGroupBox();
         }
      };

      actionAddImage = new Action(i18n.tr("&Image...")) {
			@Override
			public void run()
			{
				addImageDecoration();
			}
		};

      actionImageProperties = new Action(i18n.tr("&Properties")) {
         @Override
         public void run()
         {
            editImageDecoration();
         }
      };

      actionLinkObjects = new Action(i18n.tr("&Link selected objects"), ResourceManager.getImageDescriptor("icons/netmap/add_link.png")) {
			@Override
			public void run()
			{
				linkSelectedObjects();
			}
		};
      addKeyBinding("M1+L", actionLinkObjects);

      actionRemove = new Action(i18n.tr("&Remove from map"), SharedIcons.DELETE_OBJECT) {
			@Override
			public void run()
			{
				removeSelectedElements();
			}
		};
      addKeyBinding("M1+R", actionRemove);

      actionDCIContainerProperties = new Action(i18n.tr("&Properties")) {
         @Override
         public void run()
         {
            editDCIContainer();
         }
      };
      
      actionDCIImageProperties = new Action(i18n.tr("&Properties")) {
         @Override
         public void run()
         {
            editDCIImage();
         }
      };

      actionMapProperties = new Action(i18n.tr("&Properties")) {
			@Override
			public void run()
			{
				showMapProperties();
			}
		};

      actionLinkProperties = new Action(i18n.tr("&Properties")) {
			@Override
			public void run()
			{
				showLinkProperties();
			}
		};
		
      actionAddTextBox = new Action("Text box") {
         @Override
         public void run()
         {
            addTextBox();
         }
      };
      
      actionTextBoxProperties = new Action(i18n.tr("&Properties")) {
         @Override
         public void run()
         {
            editTextBox();
         }
      };
	}

	/**
	 * Create "Add decoration" submenu
	 * 
	 * @return menu manager for decoration submenu
	 */
	private IMenuManager createDecorationAdditionSubmenu()
	{
      MenuManager menu = new MenuManager(i18n.tr("Add &decoration"));
		menu.add(actionAddGroupBox);
		menu.add(actionAddImage);
		menu.add(actionAddTextBox);
		return menu;
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#fillMapContextMenu(org.eclipse.jface.action.IMenuManager)
    */
	@Override
	protected void fillMapContextMenu(IMenuManager manager)
	{
	   if (!readOnly)
	   {
   		manager.add(actionAddObject);
   		manager.add(actionAddDCIContainer);		
   		manager.add(actionAddDCIImage);  
   		manager.add(createDecorationAdditionSubmenu());
   		manager.add(new Separator());
	   }
		super.fillMapContextMenu(manager);
		manager.add(new Separator());
		manager.add(actionMapProperties);
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#fillObjectContextMenu(org.eclipse.jface.action.IMenuManager)
    */
	@Override
	protected void fillObjectContextMenu(IMenuManager manager)
	{
	   if (!readOnly)
	   {
   		int size = ((IStructuredSelection)viewer.getSelection()).size();
   		if (size == 2)
   			manager.add(actionLinkObjects);
   		manager.add(actionRemove);
   		manager.add(new Separator());
	   }
		super.fillObjectContextMenu(manager);
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#fillLinkContextMenu(org.eclipse.jface.action.IMenuManager)
    */
	@Override
	protected void fillLinkContextMenu(IMenuManager manager)
	{
	   if (readOnly)
	   {
	      super.fillLinkContextMenu(manager);
	      return;
	   }
	   
		int size = ((IStructuredSelection)viewer.getSelection()).size();
		manager.add(actionRemove);
		manager.add(new Separator());
      manager.add(actionLockLink);
      manager.add(new Separator());
		super.fillLinkContextMenu(manager);
		if (size == 1)
		{
			manager.add(new Separator());
			manager.add(actionLinkProperties);
		}
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#fillElementContextMenu(org.eclipse.jface.action.IMenuManager)
    */
	protected void fillElementContextMenu(IMenuManager manager)
	{
	   if (!readOnly)
	   {
   		manager.add(actionRemove);
   		Object o = ((IStructuredSelection)viewer.getSelection()).getFirstElement();
   		if (o instanceof NetworkMapDCIContainer)
   		{
   		   manager.add(actionDCIContainerProperties);
   		}
   		else if (o instanceof NetworkMapDCIImage)
   		{
            manager.add(actionDCIImageProperties);
   		}
   		else if (o instanceof NetworkMapTextBox)
   		{
            manager.add(actionTextBoxProperties);
   		}
   		else if (o instanceof NetworkMapDecoration)
   		{
            manager.add((((NetworkMapDecoration)o).getDecorationType() == NetworkMapDecoration.IMAGE) ? actionImageProperties : actionGroupBoxProperties);
   		}
   		manager.add(new Separator());
	   }
		super.fillElementContextMenu(manager);
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#fillLocalMenu(IMenuManager)
    */
	@Override
   protected void fillLocalMenu(IMenuManager manager)
	{
	   if (!readOnly)
	   {
   		manager.add(actionAddObject);
   		manager.add(actionLinkObjects);
   		manager.add(createDecorationAdditionSubmenu());
   		manager.add(new Separator());
	   }
      super.fillLocalMenu(manager);
		manager.add(new Separator());
		manager.add(actionMapProperties);
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#fillLocalToolBar(IToolBarManager)
    */
	@Override
   protected void fillLocalToolBar(IToolBarManager manager)
	{
	   if (!readOnly)
	   {
         manager.add(actionAddObject);
   		manager.add(actionLinkObjects);
   		manager.add(new Separator());
	   }
      super.fillLocalToolBar(manager);
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#saveLayout()
    */
	@Override
	protected void saveLayout()
	{
		saveMap();
	}

	/**
	 * Add object to map
	 */
	private void addObjectToMap()
	{
      ObjectSelectionDialog dlg = new ObjectSelectionDialog(getWindow().getShell());
		if (dlg.open() != Window.OK)
			return;

		addObjectsFromList(dlg.getSelectedObjects(), null);
	}

	/**
	 * Add objects from list to map
	 * 
	 * @param list
	 *           object list
	 */
	private void addObjectsFromList(List<AbstractObject> list, Point location)
	{
		int added = 0;
		for(AbstractObject object : list)
		{
			if (mapPage.findObjectElement(object.getObjectId()) == null)
			{
				final NetworkMapObject mapObject = new NetworkMapObject(mapPage.createElementId(), object.getObjectId());
				if (location != null)
					mapObject.setLocation(location.x, location.y);
				else
				   mapObject.setLocation(40, 40);
				mapPage.addElement(mapObject);
				added++;
			}
		}

		if (added > 0)
		{
			saveMap();
		}
	}

	/**
	 * Remove currently selected map elements
	 */
	private void removeSelectedElements()
	{
      IStructuredSelection selection = viewer.getStructuredSelection();

      if (!MessageDialogHelper.openQuestion(getWindow().getShell(), i18n.tr("Confirm Removal"),
            (selection.size() == 1) ? i18n.tr("Are you sure to remove selected element from map?") : i18n.tr("Are you sure to remove selected elements from map?")))
			return;

		Object[] objects = selection.toArray();
		for(Object element : objects)
		{
			if (element instanceof AbstractObject)
			{
				mapPage.removeObjectElement(((AbstractObject)element).getObjectId());
			}
			else if (element instanceof NetworkMapElement)
			{
				mapPage.removeElement(((NetworkMapElement)element).getId());
			}
			else if (element instanceof NetworkMapLink)
			{
				mapPage.removeLink((NetworkMapLink)element);
			}
		}
		saveMap();
		
		// for some reason graph viewer does not clear selection 
		// after all selected elements was removed, so we have to do it manually
		viewer.setSelection(StructuredSelection.EMPTY);
	}

	/**
	 * Save map on server
	 */
	private void saveMap()
	{
		updateObjectPositions();
		
      final NXCObjectModificationData md = new NXCObjectModificationData(getObjectId());
		md.setMapContent(mapPage.getElements(), mapPage.getLinks());
		md.setMapLayout(automaticLayoutEnabled ? layoutAlgorithm : MapLayoutAlgorithm.MANUAL);
		md.setConnectionRouting(routingAlgorithm);
		md.setMapObjectDisplayMode(labelProvider.getObjectFigureType());
		
      int flags = getMapObject().getFlags();
		if (labelProvider.isShowStatusIcons())
			flags |= NetworkMap.MF_SHOW_STATUS_ICON;
		else
			flags &= ~NetworkMap.MF_SHOW_STATUS_ICON;
		if (labelProvider.isShowStatusFrame())
			flags |= NetworkMap.MF_SHOW_STATUS_FRAME;
		else
			flags &= ~NetworkMap.MF_SHOW_STATUS_FRAME;
		if (labelProvider.isShowStatusBackground())
			flags |= NetworkMap.MF_SHOW_STATUS_BKGND;
		else
			flags &= ~NetworkMap.MF_SHOW_STATUS_BKGND;
      if (labelProvider.isShowLinkDirection())
         flags |= NetworkMap.MF_SHOW_LINK_DIRECTION;
      else
         flags &= ~NetworkMap.MF_SHOW_LINK_DIRECTION;
      if (labelProvider.isTranslucentLabelBackground())
         flags |= NetworkMap.MF_TRANSLUCENT_LABEL_BKGND;
      else
         flags &= ~NetworkMap.MF_TRANSLUCENT_LABEL_BKGND;
		md.setObjectFlags(flags);

      new Job(String.format(i18n.tr("Save network map %s"), getObjectName()), this) {
			@Override
         protected void run(IProgressMonitor monitor) throws Exception
			{
				session.modifyObject(md);
				runInUIThread(new Runnable() {
					@Override
					public void run()
					{
						viewer.setInput(mapPage);
					}
				});
			}

			@Override
			protected String getErrorMessage()
			{
            return i18n.tr("Cannot save map");
			}
		}.start();
      addDciToRequestList();
	}

	/**
	 * Show properties dialog for map object
	 */
	private void showMapProperties()
	{
		updateObjectPositions();
      ObjectPropertiesManager.openObjectPropertiesDialog(getMapObject(), getWindow().getShell());
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#isSelectableElement(java.lang.Object)
    */
	@Override
	protected boolean isSelectableElement(Object element)
	{
		return (element instanceof NetworkMapDecoration) || (element instanceof NetworkMapLink) ||
		       (element instanceof NetworkMapDCIContainer) || (element instanceof NetworkMapDCIImage) ||
		       (element instanceof NetworkMapTextBox);
	}

   /**
    * @see org.netxms.nxmc.modules.imagelibrary.ImageUpdateListener#imageUpdated(java.util.UUID)
    */
	@Override
	public void imageUpdated(final UUID guid)
	{
      getWindow().getShell().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run()
			{
            if (guid.equals(getMapObject().getBackground()))
               viewer.setBackgroundImage(ImageProvider.getInstance().getImage(guid), getMapObject().isCenterBackgroundImage());

				final String guidText = guid.toString();
				for(NetworkMapElement e : mapPage.getElements())
				{
					if ((e instanceof NetworkMapDecoration) && 
					    (((NetworkMapDecoration)e).getDecorationType() == NetworkMapDecoration.IMAGE) &&
					    ((NetworkMapDecoration)e).getTitle().equals(guidText))
					{
						viewer.updateDecorationFigure((NetworkMapDecoration)e);
						break;
					}
				}
			}
		});
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#dispose()
    */
	@Override
	public void dispose()
	{
		ImageProvider.getInstance().removeUpdateListener(this);
		if (defaultLinkColor != null)
			defaultLinkColor.dispose();
		super.dispose();
	}

   /**
    * @see org.netxms.nxmc.modules.networkmaps.views.AbstractNetworkMapView#processObjectUpdateNotification(org.netxms.client.objects.AbstractObject)
    */
	@Override
   protected void processObjectUpdateNotification(final AbstractObject object)
	{
      super.processObjectUpdateNotification(object);

      if (object.getObjectId() != getObjectId())
			return;

      NetworkMap mapObject = (NetworkMap)object;
		UUID oldBackground = mapObject.getBackground();
		if (!oldBackground.equals(mapObject.getBackground()) || mapObject.getBackground().equals(org.netxms.client.objects.NetworkMap.GEOMAP_BACKGROUND))
		{
			if (mapObject.getBackground().equals(NXCommon.EMPTY_GUID))
			{
            viewer.setBackgroundImage(null, false);
			}
			else if (mapObject.getBackground().equals(org.netxms.client.objects.NetworkMap.GEOMAP_BACKGROUND))
			{
			   if (!disableGeolocationBackground)
               viewer.setBackgroundImage(ImageProvider.getInstance().getImage(mapObject.getBackground()), mapObject.isCenterBackgroundImage());
			}
			else
			{
            viewer.setBackgroundImage(ImageProvider.getInstance().getImage(mapObject.getBackground()), mapObject.isCenterBackgroundImage());
			}
		}

		viewer.setBackgroundColor(ColorConverter.rgbFromInt(mapObject.getBackgroundColor()));

		setConnectionRouter(mapObject.getDefaultLinkRouting(), false);

		if (defaultLinkColor != null)
			defaultLinkColor.dispose();
		if (mapObject.getDefaultLinkColor() >= 0)
		{
			defaultLinkColor = new Color(viewer.getControl().getDisplay(), ColorConverter.rgbFromInt(mapObject.getDefaultLinkColor()));
		}
		else
		{
			defaultLinkColor = null;
		}
		labelProvider.setDefaultLinkColor(defaultLinkColor);

		if ((mapObject.getBackground() != null) && (mapObject.getBackground().compareTo(NXCommon.EMPTY_GUID) != 0))
		{
			if (mapObject.getBackground().equals(org.netxms.client.objects.NetworkMap.GEOMAP_BACKGROUND))
			{
			   if (!disableGeolocationBackground)
			      viewer.setBackgroundImage(mapObject.getBackgroundLocation(), mapObject.getBackgroundZoom());
			}
			else
			{
            viewer.setBackgroundImage(ImageProvider.getInstance().getImage(mapObject.getBackground()), mapObject.isCenterBackgroundImage());
			}
		}

		setLayoutAlgorithm(mapObject.getLayout(), false);
		setObjectDisplayMode(mapObject.getObjectDisplayMode(), false);
      labelProvider.setShowStatusBackground(mapObject.isShowStatusBackground());
      labelProvider.setShowStatusFrame(mapObject.isShowStatusFrame());
      labelProvider.setShowStatusIcons(mapObject.isShowStatusIcon());
      labelProvider.setShowLinkDirection(mapObject.isShowLinkDirection());
      labelProvider.setTranslucentLabelBackground(mapObject.isTranslucentLabelBackground());

      actionShowStatusBackground.setChecked(labelProvider.isShowStatusBackground());
      actionShowStatusFrame.setChecked(labelProvider.isShowStatusFrame());
      actionShowStatusIcon.setChecked(labelProvider.isShowStatusIcons());
      actionShowLinkDirection.setChecked(labelProvider.isShowLinkDirection());
      actionTranslucentLabelBkgnd.setChecked(labelProvider.isTranslucentLabelBackground());
		
      syncObjects();//refresh will be done after sync
	}

   /**
    * Add DCI container to map
    */
   private void addDCIContainer()
   {
      NetworkMapDCIContainer dciContainer = new NetworkMapDCIContainer(mapPage.createElementId());
      if (showDCIContainerProperties(dciContainer))
      {
         mapPage.addElement(dciContainer);
         saveMap();
         addDciToRequestList();
      }
   }

	/**
	 * Show DCI Container properties
	 */
   private void editDCIContainer()
   {
      updateObjectPositions();

      IStructuredSelection selection = viewer.getStructuredSelection();
      if ((selection.size() != 1) || !(selection.getFirstElement() instanceof NetworkMapDCIContainer))
         return;

      if (showDCIContainerProperties((NetworkMapDCIContainer)selection.getFirstElement()))
         saveMap();
   }

   /**
    * Show DCI container properties.
    *
    * @param container DCI container to edit
    * @return true if changes were made
    */
   private boolean showDCIContainerProperties(NetworkMapDCIContainer container)
   {
      PreferenceManager pm = new PreferenceManager();
      pm.addToRoot(new PreferenceNode("dciContainer.general", new DCIContainerGeneral(container)));
      pm.addToRoot(new PreferenceNode("dciContainer.dataSources", new DCIContainerDataSources(container)));
      PropertyDialog dlg = new PropertyDialog(getWindow().getShell(), pm, i18n.tr("DCI Container Properties"));
      return dlg.open() == Window.OK;
   }

   /**
    * Add DCI image to map
    */
   private void addDCIImage()
   {
      NetworkMapDCIImage dciImage = new NetworkMapDCIImage(mapPage.createElementId());
      if (showDCIImageProperties(dciImage))
      {
         mapPage.addElement(dciImage);
         saveMap();
         addDciToRequestList();
      }
   }

   /**
    * Show DCI Image properties
    */
   private void editDCIImage()
   {
      updateObjectPositions();

      IStructuredSelection selection = viewer.getStructuredSelection();
      if ((selection.size() != 1) || !(selection.getFirstElement() instanceof NetworkMapDCIImage))
         return;

      if (showDCIImageProperties((NetworkMapDCIImage)selection.getFirstElement()))
         saveMap();
   }

   /**
    * Show DCI image properties.
    *
    * @param dciImage DCI image to edit
    * @return true if changes were made
    */
   private boolean showDCIImageProperties(NetworkMapDCIImage dciImage)
   {
      PreferenceManager pm = new PreferenceManager();
      pm.addToRoot(new PreferenceNode("dciImage.general", new DCIImageGeneral(dciImage)));
      pm.addToRoot(new PreferenceNode("dciImage.rules", new DCIImageRules(dciImage)));
      PropertyDialog dlg = new PropertyDialog(getWindow().getShell(), pm, i18n.tr("DCI Image Properties"));
      return dlg.open() == Window.OK;
   }

   /**
    * Link currently selected objects
    */
   private void linkSelectedObjects()
   {
      IStructuredSelection selection = (IStructuredSelection)viewer.getSelection();
      if (selection.size() != 2)
         return;

      Object[] objects = selection.toArray();
      long id1 = ((NetworkMapObject)objects[0]).getId();
      long id2 = ((NetworkMapObject)objects[1]).getId();
      mapPage.addLink(new NetworkMapLink(mapPage.createLinkId(), NetworkMapLink.NORMAL, id1, id2));
      saveMap();
   }

	/**
	 * Show properties for currently selected link
	 */
	private void showLinkProperties()
	{
		updateObjectPositions();

      IStructuredSelection selection = viewer.getStructuredSelection();
		if ((selection.size() != 1) || !(selection.getFirstElement() instanceof NetworkMapLink))
			return;

		LinkEditor link = new LinkEditor((NetworkMapLink)selection.getFirstElement(), mapPage);

      PreferenceManager pm = new PreferenceManager();
      pm.addToRoot(new PreferenceNode("link.general", new LinkGeneral(link)));
      pm.addToRoot(new PreferenceNode("link.dataSources", new LinkDataSources(link)));

      PreferenceDialog dlg = new PreferenceDialog(getWindow().getShell(), pm) {
         @Override
         protected void configureShell(Shell newShell)
         {
            super.configureShell(newShell);
            newShell.setText(i18n.tr("Link Properties"));
         }
      };
      dlg.setBlockOnOpen(true);
      dlg.open();
      if (link.isModified())
         saveMap();
	}
	
   /**
    * Add text box element
    */
   private void addTextBox()
   {
      NetworkMapTextBox textBox = new NetworkMapTextBox(mapPage.createElementId());
      if (showTextBoxProperties(textBox))
      {
         mapPage.addElement(textBox);
         saveMap();
      }
   }

   /**
    * Edit selected text box
    */
   private void editTextBox()
   {
      updateObjectPositions();

      IStructuredSelection selection = viewer.getStructuredSelection();
      if ((selection.size() != 1) || !(selection.getFirstElement() instanceof NetworkMapTextBox))
         return;

      if (showTextBoxProperties((NetworkMapTextBox)selection.getFirstElement()))
         saveMap();
   }

	/**
    * Show text box properties
    * 
    * @return true if there were modifications
    */
   private boolean showTextBoxProperties(NetworkMapTextBox textBox)
	{
      PreferenceManager pm = new PreferenceManager();
      pm.addToRoot(new PreferenceNode("textbox.general", new TextBoxGeneral(textBox)));
      PropertyDialog dlg = new PropertyDialog(getWindow().getShell(), pm, i18n.tr("Text Box Properties"));
      return dlg.open() == Window.OK;
	}

   /**
    * Add image decoration
    */
   private void addImageDecoration()
   {
      ImageSelectionDialog dlg = new ImageSelectionDialog(getWindow().getShell());
      if (dlg.open() != Window.OK)
         return;

      UUID imageGuid = dlg.getImageGuid();
      Rectangle imageBounds = ImageProvider.getInstance().getImage(imageGuid).getBounds();

      NetworkMapDecoration element = new NetworkMapDecoration(mapPage.createElementId(), NetworkMapDecoration.IMAGE);
      element.setSize(imageBounds.width, imageBounds.height);
      element.setTitle(imageGuid.toString());
      mapPage.addElement(element);

      saveMap();
   }

	/**
	 * Edit image decoration
	 */
	private void editImageDecoration()
	{
      updateObjectPositions();
      
      IStructuredSelection selection = viewer.getStructuredSelection();
      if ((selection.size() != 1) || !(selection.getFirstElement() instanceof NetworkMapDecoration))
         return;
      
      ImageSelectionDialog dlg = new ImageSelectionDialog(getWindow().getShell());
      if (dlg.open() != Window.OK)
         return;
      
      UUID imageGuid = dlg.getImageGuid();
      Rectangle imageBounds = ImageProvider.getInstance().getImage(imageGuid).getBounds();

      NetworkMapDecoration element = (NetworkMapDecoration)selection.getFirstElement();
      element.setSize(imageBounds.width, imageBounds.height);
      element.setTitle(imageGuid.toString());
      mapPage.addElement(element);

      saveMap();
	}
	
   /**
    * Add group box decoration
    */
   private void addGroupBox()
   {
      NetworkMapDecoration element = new NetworkMapDecoration(mapPage.createElementId(), NetworkMapDecoration.GROUP_BOX);
      EditGroupBoxDialog dlg = new EditGroupBoxDialog(getWindow().getShell(), element);
      if (dlg.open() != Window.OK)
         return;

      mapPage.addElement(element);

      saveMap();
   }

	/**
	 * Edit group box
	 */
	private void editGroupBox()
	{
      updateObjectPositions();

      IStructuredSelection selection = viewer.getStructuredSelection();
      if ((selection.size() != 1) || !(selection.getFirstElement() instanceof NetworkMapDecoration))
         return;

      NetworkMapDecoration groupBox = (NetworkMapDecoration)selection.getFirstElement();
      EditGroupBoxDialog dlg = new EditGroupBoxDialog(getWindow().getShell(), groupBox);
      if (dlg.open() == Window.OK)
      {
         mapPage.addElement(groupBox);
         saveMap();
      }
	}

   /**
    * Get current object as NetworkMap object.
    *
    * @return current object as NetworkMap object
    */
   private NetworkMap getMapObject()
   {
      return (NetworkMap)getObject();
   }
}
