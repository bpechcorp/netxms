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
package org.netxms.nxmc.modules.networkmaps.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.draw2d.ManhattanConnectionRouter;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.gef4.zest.core.viewers.AbstractZoomableViewer;
import org.eclipse.gef4.zest.core.viewers.IZoomableWorkbenchPart;
import org.eclipse.gef4.zest.core.widgets.Graph;
import org.eclipse.gef4.zest.core.widgets.GraphConnection;
import org.eclipse.gef4.zest.core.widgets.GraphNode;
import org.eclipse.gef4.zest.layouts.LayoutAlgorithm;
import org.eclipse.gef4.zest.layouts.algorithms.CompositeLayoutAlgorithm;
import org.eclipse.gef4.zest.layouts.algorithms.GridLayoutAlgorithm;
import org.eclipse.gef4.zest.layouts.algorithms.RadialLayoutAlgorithm;
import org.eclipse.gef4.zest.layouts.algorithms.SpringLayoutAlgorithm;
import org.eclipse.gef4.zest.layouts.algorithms.TreeLayoutAlgorithm;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.ImageTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.netxms.client.NXCSession;
import org.netxms.client.SessionListener;
import org.netxms.client.SessionNotification;
import org.netxms.client.datacollection.ChartDciConfig;
import org.netxms.client.datacollection.DciValue;
import org.netxms.client.maps.MapLayoutAlgorithm;
import org.netxms.client.maps.MapObjectDisplayMode;
import org.netxms.client.maps.NetworkMapLink;
import org.netxms.client.maps.NetworkMapPage;
import org.netxms.client.maps.configs.DCIImageConfiguration;
import org.netxms.client.maps.configs.SingleDciConfig;
import org.netxms.client.maps.elements.NetworkMapDCIContainer;
import org.netxms.client.maps.elements.NetworkMapDCIImage;
import org.netxms.client.maps.elements.NetworkMapElement;
import org.netxms.client.maps.elements.NetworkMapObject;
import org.netxms.client.maps.elements.NetworkMapTextBox;
import org.netxms.client.objects.AbstractObject;
import org.netxms.client.objects.Dashboard;
import org.netxms.client.objects.NetworkMap;
import org.netxms.nxmc.PreferenceStore;
import org.netxms.nxmc.Registry;
import org.netxms.nxmc.base.jobs.Job;
import org.netxms.nxmc.base.views.Perspective;
import org.netxms.nxmc.base.windows.PopOutViewWindow;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.datacollection.views.HistoricalGraphView;
import org.netxms.nxmc.modules.networkmaps.ObjectDoubleClickHandlerRegistry;
import org.netxms.nxmc.modules.networkmaps.algorithms.ExpansionAlgorithm;
import org.netxms.nxmc.modules.networkmaps.algorithms.ManualLayout;
import org.netxms.nxmc.modules.networkmaps.views.helpers.BendpointEditor;
import org.netxms.nxmc.modules.networkmaps.widgets.helpers.ExtendedGraphViewer;
import org.netxms.nxmc.modules.networkmaps.widgets.helpers.LinkDciValueProvider;
import org.netxms.nxmc.modules.networkmaps.widgets.helpers.MapContentProvider;
import org.netxms.nxmc.modules.networkmaps.widgets.helpers.MapLabelProvider;
import org.netxms.nxmc.modules.objects.ObjectContextMenuManager;
import org.netxms.nxmc.modules.objects.views.ObjectView;
import org.netxms.nxmc.resources.ResourceManager;
import org.netxms.nxmc.resources.SharedIcons;
import org.netxms.nxmc.tools.PngTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

/**
 * Base class for network map views
 */
public abstract class AbstractNetworkMapView extends ObjectView implements ISelectionProvider, IZoomableWorkbenchPart
{
   private static final Logger logger = LoggerFactory.getLogger(AbstractNetworkMapView.class);
   private static final I18n i18n = LocalizationHelper.getI18n(AbstractNetworkMapView.class);

	protected static final int LAYOUT_SPRING = 0;
	protected static final int LAYOUT_RADIAL = 1;
	protected static final int LAYOUT_HTREE = 2;
	protected static final int LAYOUT_VTREE = 3;
	protected static final int LAYOUT_SPARSE_VTREE = 4;

	private static final String[] layoutAlgorithmNames = { 
         i18n.tr("Spring"), i18n.tr("Radial"), i18n.tr("Horizontal tree"), i18n.tr("Vertical tree"), i18n.tr("Sparse vertical tree") 
	};
   private static final String[] connectionRouterNames = { i18n.tr("Direct"), i18n.tr("Manhattan") };

	private static final int SELECTION_EMPTY = 0;
	private static final int SELECTION_MIXED = 1;
	private static final int SELECTION_OBJECTS = 2;
	private static final int SELECTION_ELEMENTS = 3;
	private static final int SELECTION_LINKS = 4;

   protected NXCSession session = Registry.getSession();
	protected NetworkMapPage mapPage;
	protected ExtendedGraphViewer viewer;
	protected MapLabelProvider labelProvider;
	protected MapLayoutAlgorithm layoutAlgorithm = MapLayoutAlgorithm.SPRING;
	protected int routingAlgorithm = NetworkMapLink.ROUTING_DIRECT;
	protected boolean allowManualLayout = false; // True if manual layout can be switched on
	protected boolean automaticLayoutEnabled = true; // Current layout mode - automatic or manual
	protected boolean alwaysFitLayout = false;

	protected Action actionShowStatusIcon;
	protected Action actionShowStatusBackground;
	protected Action actionShowStatusFrame;
	protected Action actionShowLinkDirection;
   protected Action actionTranslucentLabelBkgnd;
	protected Action actionZoomIn;
	protected Action actionZoomOut;
	protected Action actionZoomFit;
	protected Action[] actionZoomTo;
	protected Action[] actionSetAlgorithm;
	protected Action[] actionSetRouter;
	protected Action actionAlwaysFitLayout;
	protected Action actionEnableAutomaticLayout;
	protected Action actionSaveLayout;
	protected Action actionOpenDrillDownObject;
	protected Action actionFiguresIcons;
	protected Action actionFiguresSmallLabels;
	protected Action actionFiguresLargeLabels;
	protected Action actionFiguresStatusIcons;
	protected Action actionFiguresFloorPlan;
	protected Action actionShowGrid;
	protected Action actionAlignToGrid;
	protected Action actionSnapToGrid;
	protected Action actionShowObjectDetails;
	protected Action actionCopyImage;
   protected Action actionSaveImage;
   protected Action actionHideLinkLabels;
   protected Action actionHideLinks;
   protected Action actionSelectAllObjects;
   protected Action actionLockLink;

	private IStructuredSelection currentSelection = new StructuredSelection(new Object[0]);
	private Set<ISelectionChangedListener> selectionListeners = new HashSet<ISelectionChangedListener>();
	private BendpointEditor bendpointEditor = null;
	private SessionListener sessionListener;
	private ObjectDoubleClickHandlerRegistry doubleClickHandlers;
   private LinkDciValueProvider dciValueProvider = LinkDciValueProvider.getInstance();

   /**
    * Create new map view.
    *
    * @param name view name
    * @param image view icon
    * @param id view base ID
    */
   public AbstractNetworkMapView(String name, ImageDescriptor image, String id)
   {
      super(name, image, id, false);
   }

	/**
	 * Build map page containing data to display. Should be implemented in derived classes.
	 */
	protected abstract void buildMapPage();

   /**
    * @see org.netxms.nxmc.base.views.View#createContent(org.eclipse.swt.widgets.Composite)
    */
	@Override
   public final void createContent(Composite parent)
	{
		FillLayout layout = new FillLayout();
		parent.setLayout(layout);

      viewer = new ExtendedGraphViewer(parent, SWT.NONE, this);
      labelProvider = new MapLabelProvider(viewer);
		viewer.setContentProvider(new MapContentProvider(viewer, labelProvider));
		viewer.setLabelProvider(labelProvider);
      viewer.setBackgroundColor(parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND).getRGB());

      final PreferenceStore settings = PreferenceStore.getInstance();
      alwaysFitLayout = settings.getAsBoolean(getBaseId() + ".alwaysFitLayout", false);

      viewer.zoomTo(settings.getAsDouble(getBaseId() + ".zoom", 1.0));
		viewer.getGraphControl().addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            settings.set(getBaseId() + ".zoom", viewer.getZoom());
         }
      });

		ISelectionChangedListener listener = new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent e)
			{
				if (bendpointEditor != null)
				{
					bendpointEditor.stop();
					bendpointEditor = null;
				}

				currentSelection = transformSelection(e.getSelection());

				if (currentSelection.size() == 1)
				{
					int selectionType = analyzeSelection(currentSelection);
					if (selectionType == SELECTION_OBJECTS)
					{
						AbstractObject object = (AbstractObject)currentSelection.getFirstElement();
						actionOpenDrillDownObject.setEnabled(object.getDrillDownObjectId() != 0);
					}
					else
					{
						actionOpenDrillDownObject.setEnabled(false);
						if (selectionType == SELECTION_LINKS)
						{
							NetworkMapLink link = (NetworkMapLink)currentSelection.getFirstElement();
							actionLockLink.setChecked(link.isLocked());
							if (!link.isLocked() && link.getRouting() == NetworkMapLink.ROUTING_BENDPOINTS)
							{
								bendpointEditor = new BendpointEditor(link,
										(GraphConnection)viewer.getGraphControl().getSelection().get(0), viewer);
							}
						}
					}
				}
				else
				{
					actionOpenDrillDownObject.setEnabled(false);
				}

				if (selectionListeners.isEmpty())
					return;

				SelectionChangedEvent event = new SelectionChangedEvent(AbstractNetworkMapView.this, currentSelection);
				for(ISelectionChangedListener l : selectionListeners)
				{
					l.selectionChanged(event);
				}
			}
		};
		viewer.addPostSelectionChangedListener(listener);

		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event)
			{
				int selectionType = analyzeSelection(currentSelection);
				if (selectionType == SELECTION_EMPTY)
				   return;
				
				if (selectionType == SELECTION_OBJECTS)
				{
					doubleClickHandlers.handleDoubleClick((AbstractObject)currentSelection.getFirstElement());
				}
				else if (selectionType == SELECTION_LINKS && ((NetworkMapLink)currentSelection.getFirstElement()).isLocked())
				{
				   openLinkDci();
				}
			}
		});

		sessionListener = new SessionListener() {
			@Override
			public void notificationHandler(final SessionNotification n)
			{
				if (n.getCode() == SessionNotification.OBJECT_CHANGED)
				{
					viewer.getControl().getDisplay().asyncExec(new Runnable() {
						@Override
						public void run()
						{
                     processObjectUpdateNotification((AbstractObject)n.getObject());
						}
					});
				}
			}
		};
		session.addListener(sessionListener);

		createActions();
		createContextMenu();

		if (automaticLayoutEnabled)
		{
			setLayoutAlgorithm(layoutAlgorithm, true);
		}
		else
		{
			viewer.setLayoutAlgorithm(new ManualLayout());
		}

		doubleClickHandlers = new ObjectDoubleClickHandlerRegistry(this);
		setupMapControl();
	}

	/**
    * @see org.netxms.nxmc.base.views.ViewWithContext#postContentCreate()
    */
   @Override
   protected void postContentCreate()
   {
      super.postContentCreate();
      refresh();
   }

   /**
    * Called from createPartControl to allow subclasses to do additional map setup. Subclasses should override this method instead
    * of createPartControl. Default implementation do nothing.
    */
	protected void setupMapControl()
	{
	}

	/**
    * @see org.netxms.nxmc.base.views.View#refresh()
    */
   @Override
   public void refresh()
	{
      if (mapPage != null)
         dciValueProvider.removeDcis(mapPage);
		buildMapPage();
		viewer.setInput(mapPage);
		viewer.setSelection(StructuredSelection.EMPTY);
	}

	/**
	 * Replace current map page with new one
	 * 
	 * @param page new map page
	 */
	protected void replaceMapPage(final NetworkMapPage page, Display display)
	{
		display.asyncExec(new Runnable() {
			@Override
			public void run()
			{
				mapPage = page;
				addDciToRequestList();
				viewer.setInput(mapPage);
			}
		});
	}

	/**
	 * Set layout algorithm for map
	 * 
	 * @param alg Layout algorithm
	 * @param forceChange
	 */
	protected void setLayoutAlgorithm(MapLayoutAlgorithm alg, boolean forceChange)
	{
		if (alg == MapLayoutAlgorithm.MANUAL)
		{
			if (!automaticLayoutEnabled)
				return; // manual layout already

			automaticLayoutEnabled = false;
			// TODO: rewrite, enum value should not be used as index
			actionSetAlgorithm[layoutAlgorithm.getValue()].setChecked(false);
			actionEnableAutomaticLayout.setChecked(false);
			return;
		}

		if (automaticLayoutEnabled && (alg == layoutAlgorithm) && !forceChange)
			return; // nothing to change

		if (!automaticLayoutEnabled)
		{
			actionEnableAutomaticLayout.setChecked(true);
			automaticLayoutEnabled = true;
		}

		LayoutAlgorithm algorithm;

		switch(alg)
		{
			case SPRING:
				algorithm = new SpringLayoutAlgorithm();
				break;
			case RADIAL:
				algorithm = new RadialLayoutAlgorithm();
				break;
			case HTREE:
				algorithm = new TreeLayoutAlgorithm(TreeLayoutAlgorithm.LEFT_RIGHT);
				break;
			case VTREE:
				algorithm = new TreeLayoutAlgorithm(TreeLayoutAlgorithm.TOP_DOWN);
				break;
			case SPARSE_VTREE:
				algorithm = new TreeLayoutAlgorithm(TreeLayoutAlgorithm.TOP_DOWN);
				((TreeLayoutAlgorithm)algorithm).setNodeSpace(new Dimension(100, 100));
				break;
			default:
				algorithm = new GridLayoutAlgorithm();
				break;
		}

		viewer.setLayoutAlgorithm(alwaysFitLayout ? algorithm : new CompositeLayoutAlgorithm(new LayoutAlgorithm[] { algorithm, new ExpansionAlgorithm() }));

		actionSetAlgorithm[layoutAlgorithm.getValue()].setChecked(false);
		layoutAlgorithm = alg;
		actionSetAlgorithm[layoutAlgorithm.getValue()].setChecked(true);
	}

	/**
	 * Update stored object positions with actual positions read from graph control
	 */
	protected void updateObjectPositions()
	{
		Graph graph = viewer.getGraphControl();
		List<?> nodes = graph.getNodes();
		for(Object o : nodes)
		{
			if (o instanceof GraphNode)
			{
				Object data = ((GraphNode)o).getData();
				if (data instanceof NetworkMapElement)
				{
					Point loc = ((GraphNode)o).getLocation();
					Dimension size = ((GraphNode)o).getSize();
					((NetworkMapElement)data).setLocation(loc.x + (size.width + 1) / 2, loc.y + (size.height + 1) / 2);
				}
			}
		}
	}

	/**
	 * Set manual layout mode
	 */
	protected void setManualLayout()
	{
		updateObjectPositions();

		automaticLayoutEnabled = false;
		viewer.setLayoutAlgorithm(new ManualLayout(), true);

		for(int i = 0; i < actionSetAlgorithm.length; i++)
			actionSetAlgorithm[i].setEnabled(false);
		actionSaveLayout.setEnabled(true);
	}

	/**
	 * Set automatic layout mode
	 */
	protected void setAutomaticLayout()
	{
		automaticLayoutEnabled = true;
		setLayoutAlgorithm(layoutAlgorithm, true);

		for(int i = 0; i < actionSetAlgorithm.length; i++)
			actionSetAlgorithm[i].setEnabled(true);
		actionSaveLayout.setEnabled(false);
	}

	/**
	 * Create actions
	 */
	protected void createActions()
	{
      actionShowLinkDirection = new Action("Show link &direction", Action.AS_CHECK_BOX) {
         @Override
         public void run()
         {
            labelProvider.setShowLinkDirection(!labelProvider.isShowLinkDirection());
            setChecked(labelProvider.isShowLinkDirection());
            updateObjectPositions();
            saveLayout();
            viewer.refresh();
         }
      };
      actionShowLinkDirection.setChecked(labelProvider.isShowLinkDirection());
		
      actionShowStatusBackground = new Action(i18n.tr("Show status &background"), Action.AS_CHECK_BOX) {
			@Override
			public void run()
			{
				labelProvider.setShowStatusBackground(!labelProvider.isShowStatusBackground());
				setChecked(labelProvider.isShowStatusBackground());
				updateObjectPositions();
				saveLayout();
				viewer.refresh();
			}
		};
		actionShowStatusBackground.setChecked(labelProvider.isShowStatusBackground());
		actionShowStatusBackground.setEnabled(labelProvider.getObjectFigureType() == MapObjectDisplayMode.ICON);

      actionShowStatusIcon = new Action(i18n.tr("Show status &icon"), Action.AS_CHECK_BOX) {
			@Override
			public void run()
			{
				labelProvider.setShowStatusIcons(!labelProvider.isShowStatusIcons());
				setChecked(labelProvider.isShowStatusIcons());
				updateObjectPositions();
				saveLayout();
				viewer.refresh();
			}
		};
		actionShowStatusIcon.setChecked(labelProvider.isShowStatusIcons());
		actionShowStatusIcon.setEnabled(labelProvider.getObjectFigureType() == MapObjectDisplayMode.ICON);

      actionShowStatusFrame = new Action(i18n.tr("Show status &frame"), Action.AS_CHECK_BOX) {
			@Override
			public void run()
			{
				labelProvider.setShowStatusFrame(!labelProvider.isShowStatusFrame());
				setChecked(labelProvider.isShowStatusFrame());
				updateObjectPositions();
				saveLayout();
				viewer.refresh();
			}
		};
		actionShowStatusFrame.setChecked(labelProvider.isShowStatusFrame());
		actionShowStatusFrame.setEnabled(labelProvider.getObjectFigureType() == MapObjectDisplayMode.ICON);

      actionTranslucentLabelBkgnd = new Action(i18n.tr("Translucent label background"), Action.AS_CHECK_BOX) {
         @Override
         public void run()
         {
            labelProvider.setTranslucentLabelBackground(actionTranslucentLabelBkgnd.isChecked());
            updateObjectPositions();
            saveLayout();
            viewer.refresh();
         }
      };
      actionTranslucentLabelBkgnd.setChecked(labelProvider.isTranslucentLabelBackground());

      actionZoomIn = new Action(i18n.tr("Zoom &in"), SharedIcons.ZOOM_IN) {
			@Override
			public void run()
			{
				viewer.zoomIn();
			}
		};
      addKeyBinding("M1+=", actionZoomIn);

      actionZoomOut = new Action(i18n.tr("Zoom &out"), SharedIcons.ZOOM_OUT) {
			@Override
			public void run()
			{
				viewer.zoomOut();
			}
		};
      addKeyBinding("M1+-", actionZoomOut);

      actionZoomFit = new Action(i18n.tr("Zoom to &fit"), ResourceManager.getImageDescriptor("icons/netmap/fit.png")) {
         @Override
         public void run()
         {
            viewer.zoomFit();
         }
      };
      addKeyBinding("M1+F", actionZoomFit);

      actionZoomTo = viewer.createZoomActions();

		actionSetAlgorithm = new Action[layoutAlgorithmNames.length];
		for(int i = 0; i < layoutAlgorithmNames.length; i++)
		{
			final MapLayoutAlgorithm alg = MapLayoutAlgorithm.getByValue(i);
			actionSetAlgorithm[i] = new Action(layoutAlgorithmNames[i], Action.AS_RADIO_BUTTON) {
				@Override
				public void run()
				{
					setLayoutAlgorithm(alg, true);
					viewer.setInput(mapPage);
				}
			};
			actionSetAlgorithm[i].setChecked(layoutAlgorithm.getValue() == i);
			actionSetAlgorithm[i].setEnabled(automaticLayoutEnabled);
		}
		
      actionAlwaysFitLayout = new Action(i18n.tr("Always fit layout to screen"), Action.AS_CHECK_BOX) {
         @Override
         public void run()
         {
            alwaysFitLayout = actionAlwaysFitLayout.isChecked();
            setLayoutAlgorithm(layoutAlgorithm, true);
            PreferenceStore settings = PreferenceStore.getInstance();
            settings.set(getBaseId() + ".alwaysFitLayout", alwaysFitLayout);
         }
      };
      actionAlwaysFitLayout.setChecked(alwaysFitLayout);

		actionSetRouter = new Action[connectionRouterNames.length];
		for(int i = 0; i < connectionRouterNames.length; i++)
		{
			final int alg = i + 1;
			actionSetRouter[i] = new Action(connectionRouterNames[i], Action.AS_RADIO_BUTTON) {
				@Override
				public void run()
				{
					setConnectionRouter(alg, true);
				}
			};
			actionSetRouter[i].setChecked(routingAlgorithm == alg);
		}

      actionEnableAutomaticLayout = new Action(i18n.tr("Enable &automatic layout"), Action.AS_CHECK_BOX) {
			@Override
			public void run()
			{
				if (automaticLayoutEnabled)
				{
					setManualLayout();
				}
				else
				{
					setAutomaticLayout();
				}
				setChecked(automaticLayoutEnabled);
				saveLayout();
			}
		};
		actionEnableAutomaticLayout.setChecked(automaticLayoutEnabled);

      actionSaveLayout = new Action(i18n.tr("&Save layout")) {
			@Override
			public void run()
			{
				updateObjectPositions();
				saveLayout();
			}
		};
		actionSaveLayout.setImageDescriptor(SharedIcons.SAVE);
		actionSaveLayout.setEnabled(!automaticLayoutEnabled);

		actionOpenDrillDownObject = new Action("Open drill-down object") {
			@Override
			public void run()
			{
				openDrillDownObject();
			}
		};
		actionOpenDrillDownObject.setEnabled(false);

      actionFiguresIcons = new Action(i18n.tr("&Icons"), Action.AS_RADIO_BUTTON) {
			@Override
			public void run()
			{
			   setObjectDisplayMode(MapObjectDisplayMode.ICON, true);
			}
		};

      actionFiguresSmallLabels = new Action(i18n.tr("&Small labels"), Action.AS_RADIO_BUTTON) {
			@Override
			public void run()
			{
            setObjectDisplayMode(MapObjectDisplayMode.SMALL_LABEL, true);
			}
		};

      actionFiguresLargeLabels = new Action(i18n.tr("&Large labels"), Action.AS_RADIO_BUTTON) {
			@Override
			public void run()
			{
            setObjectDisplayMode(MapObjectDisplayMode.LARGE_LABEL, true);
			}
		};

      actionFiguresStatusIcons = new Action(i18n.tr("Status icons"), Action.AS_RADIO_BUTTON) {
         @Override
         public void run()
         {
            setObjectDisplayMode(MapObjectDisplayMode.STATUS, true);
         }
      };

      actionFiguresFloorPlan = new Action("Floor plan", Action.AS_RADIO_BUTTON) {
         @Override
         public void run()
         {
            setObjectDisplayMode(MapObjectDisplayMode.FLOOR_PLAN, true);
         }
      };

      actionShowGrid = new Action(i18n.tr("Show &grid"), Action.AS_CHECK_BOX) {
			@Override
			public void run()
			{
				viewer.showGrid(actionShowGrid.isChecked());
			}
		};
      actionShowGrid.setImageDescriptor(ResourceManager.getImageDescriptor("icons/netmap/grid.png"));
		actionShowGrid.setChecked(viewer.isGridVisible());
      addKeyBinding("M1+G", actionShowGrid);

      actionSnapToGrid = new Action(i18n.tr("S&nap to grid"), Action.AS_CHECK_BOX) {
			@Override
			public void run()
			{
				viewer.setSnapToGrid(actionSnapToGrid.isChecked());
			}
		};
      actionSnapToGrid.setImageDescriptor(ResourceManager.getImageDescriptor("icons/netmap/snap_to_grid.png"));
		actionSnapToGrid.setChecked(viewer.isSnapToGrid());

      actionAlignToGrid = new Action(i18n.tr("&Align to grid"), ResourceManager.getImageDescriptor("icons/netmap/align_to_grid.gif")) {
			@Override
			public void run()
			{
				viewer.alignToGrid(false);
				updateObjectPositions();
			}
		};
      addKeyBinding("M1+M3+G", actionAlignToGrid);

      actionShowObjectDetails = new Action(i18n.tr("Show object details")) {
			@Override
			public void run()
			{
				showObjectDetails();
			}
		};

      actionCopyImage = new Action(i18n.tr("&Copy map image to clipboard"), SharedIcons.COPY) {
         @Override
         public void run()
         {
            Image image = viewer.takeSnapshot();
            Transfer imageTransfer = SystemUtils.IS_OS_LINUX ? PngTransfer.getInstance() : ImageTransfer.getInstance();
            final Clipboard clipboard = new Clipboard(viewer.getControl().getDisplay());
            clipboard.setContents(new Object[] { image.getImageData() }, new Transfer[] { imageTransfer });
         }
		};

      actionSaveImage = new Action(i18n.tr("Save map image to file")) {
         @Override
         public void run()
         {
            saveMapImageToFile(null);
         }
      };

      actionHideLinkLabels = new Action(i18n.tr("Hide link labels"), Action.AS_CHECK_BOX) {
         @Override
         public void run()
         {         
            labelProvider.setConnectionLabelsVisible(!actionHideLinkLabels.isChecked());
            viewer.refresh(true);
         }
      };
      actionHideLinkLabels.setImageDescriptor(ResourceManager.getImageDescriptor("icons/netmap/hide_link_labels.png"));
      
      actionHideLinks = new Action(i18n.tr("Hide links"), Action.AS_CHECK_BOX) {
         @Override
         public void run()
         {
            labelProvider.setConnectionsVisible(!actionHideLinks.isChecked());
            viewer.refresh(true);
         }
      };
      actionHideLinks.setImageDescriptor(ResourceManager.getImageDescriptor("icons/netmap/hide_links.png"));

      actionSelectAllObjects = new Action(i18n.tr("Select &all objects")) {
         @Override
         public void run()
         {
            viewer.setSelection(new StructuredSelection(mapPage.getObjectElements()));
         }
      };
      addKeyBinding("M1+A", actionSelectAllObjects);

      actionLockLink = new Action("Locked") {
         @Override
         public void run()
         {
            changeLinkLock();
         }
      };
	}

   /**
	 * Create "Layout" submenu
	 * 
	 * @return
	 */
	protected IContributionItem createLayoutSubmenu()
	{
      MenuManager layout = new MenuManager(i18n.tr("&Layout"));
		if (allowManualLayout)
		{
			layout.add(actionEnableAutomaticLayout);
		}
      layout.add(actionAlwaysFitLayout);
		layout.add(new Separator());
		for(int i = 0; i < actionSetAlgorithm.length; i++)
			layout.add(actionSetAlgorithm[i]);
		if (allowManualLayout)
		{
			layout.add(new Separator());
			layout.add(actionSaveLayout);
		}
		return layout;
	}

	/**
	 * Create "Routing" submenu
	 * 
	 * @return
	 */
	protected IContributionItem createRoutingSubmenu()
	{
      MenuManager submenu = new MenuManager(i18n.tr("&Routing"));
		for(int i = 0; i < actionSetRouter.length; i++)
			submenu.add(actionSetRouter[i]);
		return submenu;
	}

   /**
    * @see org.netxms.nxmc.base.views.View#fillLocalMenu(IMenuManager)
    */
   @Override
   protected void fillLocalMenu(IMenuManager manager)
	{
      MenuManager zoom = new MenuManager(i18n.tr("&Zoom"));
		for(int i = 0; i < actionZoomTo.length; i++)
			zoom.add(actionZoomTo[i]);

      MenuManager figureType = new MenuManager(i18n.tr("&Display objects as"));
		figureType.add(actionFiguresIcons);
		figureType.add(actionFiguresSmallLabels);
		figureType.add(actionFiguresLargeLabels);
		figureType.add(actionFiguresStatusIcons);
		figureType.add(actionFiguresFloorPlan);

		manager.add(actionShowStatusBackground);
		manager.add(actionShowStatusIcon);
		manager.add(actionShowStatusFrame);
		manager.add(actionShowLinkDirection);
      manager.add(actionTranslucentLabelBkgnd);
		manager.add(new Separator());
		manager.add(createLayoutSubmenu());
		manager.add(createRoutingSubmenu());
      manager.add(figureType);
      manager.add(new Separator());
      manager.add(actionZoomIn);
      manager.add(actionZoomOut);
      manager.add(actionZoomFit);
		manager.add(zoom);
		manager.add(new Separator());
		manager.add(actionAlignToGrid);
		manager.add(actionSnapToGrid);
		manager.add(actionShowGrid);
      manager.add(new Separator()); 
      manager.add(actionHideLinkLabels); 
      manager.add(actionHideLinks);
      manager.add(new Separator());      
      manager.add(actionCopyImage);
      manager.add(actionSaveImage);
		manager.add(new Separator());
      manager.add(actionSelectAllObjects);
	}

   /**
    * @see org.netxms.nxmc.base.views.View#fillLocalToolBar(IToolBarManager)
    */
   @Override
   protected void fillLocalToolBar(IToolBarManager manager)
	{
		manager.add(actionZoomIn);
		manager.add(actionZoomOut);
      manager.add(actionZoomFit);
		manager.add(new Separator());
		manager.add(actionAlignToGrid);
		manager.add(actionSnapToGrid);
		manager.add(actionShowGrid);
      manager.add(new Separator()); 
      manager.add(actionHideLinkLabels);  
      manager.add(actionHideLinks);
		manager.add(new Separator());
		if (allowManualLayout)
		{
			manager.add(actionSaveLayout);
		}
      manager.add(actionCopyImage);
	}

	/**
	 * Create popup menu for map
	 */
	private void createContextMenu()
	{
		// Create menu manager.
      MenuManager menuMgr = new ObjectContextMenuManager(this, this) {
         @Override
         protected void fillContextMenu()
         {
            int selectionType = analyzeSelection(currentSelection);
            switch(selectionType)
            {
               case SELECTION_EMPTY:
                  fillMapContextMenu(this);
                  break;
               case SELECTION_OBJECTS:
                  fillObjectContextMenu(this);
                  add(new Separator());
                  super.fillContextMenu();
                  break;
               case SELECTION_ELEMENTS:
                  fillElementContextMenu(this);
                  break;
               case SELECTION_LINKS:
                  fillLinkContextMenu(this);
                  break;
            }
         }
      };

		// Create menu.
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
	}

   /**
    * Fill context menu for object.
    *
    * @param manager menu manager
    */
   protected void fillObjectContextMenu(IMenuManager manager)
   {
      manager.add(actionOpenDrillDownObject);
      if (currentSelection.size() == 1)
         manager.add(actionShowObjectDetails);
   }

	/**
	 * Fill context menu for map element
	 * 
	 * @param manager Menu manager
	 */
	protected void fillElementContextMenu(IMenuManager manager)
	{
	}

	/**
	 * Fill context menu for link between objects
	 * 
	 * @param manager Menu manager
	 */
	protected void fillLinkContextMenu(IMenuManager manager)
	{
	}

	/**
	 * Fill context menu for map view
	 * 
	 * @param manager Menu manager
	 */
	protected void fillMapContextMenu(IMenuManager manager)
	{
      MenuManager zoom = new MenuManager(i18n.tr("&Zoom"));
		for(int i = 0; i < actionZoomTo.length; i++)
			zoom.add(actionZoomTo[i]);

      MenuManager figureType = new MenuManager(i18n.tr("&Display objects as"));
		figureType.add(actionFiguresIcons);
		figureType.add(actionFiguresSmallLabels);
		figureType.add(actionFiguresLargeLabels);
      figureType.add(actionFiguresStatusIcons);
      figureType.add(actionFiguresFloorPlan);

		manager.add(actionShowStatusBackground);
		manager.add(actionShowStatusIcon);
		manager.add(actionShowStatusFrame);
		manager.add(actionShowLinkDirection);
      manager.add(actionTranslucentLabelBkgnd);
		manager.add(new Separator());
		manager.add(createLayoutSubmenu());
		manager.add(createRoutingSubmenu());
      manager.add(figureType);
      manager.add(new Separator());
		manager.add(actionZoomIn);
      manager.add(actionZoomOut);
      manager.add(actionZoomFit);
		manager.add(zoom);
		manager.add(new Separator());
		manager.add(actionAlignToGrid);
		manager.add(actionSnapToGrid);
		manager.add(actionShowGrid);
      manager.add(new Separator()); 
      manager.add(actionHideLinkLabels);
      manager.add(actionHideLinks);
		manager.add(new Separator());
      manager.add(actionSelectAllObjects);
	}

	/**
	 * Tests if given selection contains only NetXMS objects
	 * 
	 * @param selection
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private int analyzeSelection(IStructuredSelection selection)
	{
		if (selection.isEmpty())
			return SELECTION_EMPTY;

		Iterator it = selection.iterator();
		Object first = it.next();
		int type;
		Class firstClass;
		if (first instanceof AbstractObject)
		{
			type = SELECTION_OBJECTS;
			firstClass = AbstractObject.class;
		}
		else if (first instanceof NetworkMapElement)
		{
			type = SELECTION_ELEMENTS;
			firstClass = NetworkMapElement.class;
		}
		else if (first instanceof NetworkMapLink)
		{
			type = SELECTION_LINKS;
			firstClass = NetworkMapLink.class;
		}
		else
		{
			return SELECTION_MIXED;
		}

		while(it.hasNext())
		{
			final Object o = it.next();
			if (!firstClass.isInstance(o))
				return SELECTION_MIXED;
		}
		return type;
	}

	/**
	 * Called by session listener when NetXMS object was changed.
	 * 
	 * @param object changed NetXMS object
	 */
   protected void processObjectUpdateNotification(final AbstractObject object)
	{
		NetworkMapObject element = mapPage.findObjectElement(object.getObjectId());
		if (element != null)
			viewer.refresh(element, true);

		List<NetworkMapLink> links = mapPage.findLinksWithStatusObject(object.getObjectId());
		if (links != null)
		{
			for(NetworkMapLink l : links)
				viewer.refresh(l);
			addDciToRequestList();
		}
	}

	/**
	 * Called when map layout has to be saved. Object positions already updated when this method is called. Default implementation
	 * does nothing.
	 */
	protected void saveLayout()
	{
	}

   /**
    * @see org.netxms.nxmc.base.views.View#setFocus()
    */
	@Override
	public void setFocus()
	{
      viewer.getControl().setFocus();
	}

   /**
    * @see org.netxms.nxmc.base.views.View#dispose()
    */
	@Override
	public void dispose()
	{
		if (sessionListener != null)
			session.removeListener(sessionListener);

		if (labelProvider != null)
		{
         PreferenceStore settings = PreferenceStore.getInstance();
         settings.set(getBaseId() + ".objectFigureType", labelProvider.getObjectFigureType().ordinal());
		}

      if (mapPage != null)
         dciValueProvider.removeDcis(mapPage);

		super.dispose();
	}

   /**
    * @see org.eclipse.jface.viewers.ISelectionProvider#addSelectionChangedListener(org.eclipse.jface.viewers.ISelectionChangedListener)
    */
	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener)
	{
		selectionListeners.add(listener);
	}

	/**
	 * Transform viewer's selection to form usable by another plugins by extracting NetXMS objects from map elements.
	 * 
	 * @param viewerSelection viewer's selection
	 * @return selection containing only NetXMS objects
	 */
	@SuppressWarnings("rawtypes")
	private IStructuredSelection transformSelection(ISelection viewerSelection)
	{
		IStructuredSelection selection = (IStructuredSelection)viewerSelection;
		if (selection.isEmpty())
			return selection;

		List<Object> objects = new ArrayList<Object>();
		Iterator it = selection.iterator();
		while(it.hasNext())
		{
			Object element = it.next();
			if (element instanceof NetworkMapObject)
			{
				AbstractObject object = session.findObjectById(((NetworkMapObject)element).getObjectId());
				if (object != null)
				{
					objects.add(object);
				}
				else
				{
					// Fix for issue NX-24
					// If object not found, add map element to selection
					// This will allow removal of unknown objects from map
					objects.add(element);
				}
			}
			else if (isSelectableElement(element))
			{
				objects.add(element);
			}
		}

		return new StructuredSelection(objects.toArray());
	}

	/**
	 * Tests if given map element is selectable. Default implementation always returns false.
	 * 
	 * @param element element to test
	 * @return true if given element is selectable
	 */
	protected boolean isSelectableElement(Object element)
	{
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.viewers.ISelectionProvider#getSelection()
	 */
	@Override
	public ISelection getSelection()
	{
		return currentSelection;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.jface.viewers.ISelectionProvider#removeSelectionChangedListener(org.eclipse.jface.viewers.ISelectionChangedListener
	 * )
	 */
	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener)
	{
		selectionListeners.remove(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.viewers.ISelectionProvider#setSelection(org.eclipse.jface.viewers.ISelection)
	 */
	@Override
	public void setSelection(ISelection selection)
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.gef4.zest.core.viewers.IZoomableWorkbenchPart#getZoomableViewer()
	 */
	@Override
	public AbstractZoomableViewer getZoomableViewer()
	{
		return viewer;
	}

	/**
	 * Open drill-down object for currently selected object
	 */
	private void openDrillDownObject()
	{
      if ((currentSelection == null) || currentSelection.isEmpty())
			return;

      Object currentObject = currentSelection.getFirstElement();

		long drillDownObjectId = 0;
		if (currentObject instanceof AbstractObject)
			drillDownObjectId = (currentObject instanceof NetworkMap) ? ((AbstractObject)currentObject).getObjectId() : ((AbstractObject)currentObject).getDrillDownObjectId();
		else if (currentObject instanceof NetworkMapTextBox)
		   drillDownObjectId = ((NetworkMapTextBox)currentObject).getDrillDownObjectId();

		if (drillDownObjectId != 0)
      {
         Object drillDownObject = session.findObjectById(drillDownObjectId);
         if (drillDownObject instanceof NetworkMap)
         {
            /* FIXME: open drill-down map */
         }
         if (drillDownObject instanceof Dashboard)
         {
            /* FIXME: open drill-down dashboard */
         }
      }
	}

	/**
	 * Handler for opening network map dci on double click
	 */
	private void openLinkDci()
	{
	   final NetworkMapLink link = (NetworkMapLink)currentSelection.getFirstElement();	   
	   if (!link.hasDciData())
	      return;

      new Job(i18n.tr("Get DCI configuration"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            DciValue[] values = session.getLastValues(link.getDciAsList());

            final List<ChartDciConfig> items = new ArrayList<ChartDciConfig>(values.length);
            for(DciValue dci : values)
               items.add(new ChartDciConfig(dci));

            runInUIThread(new Runnable() {               
               @Override
               public void run()
               {
                  AbstractObject object = getObject();
                  Perspective p = getPerspective();
                  if (p != null)
                  {
                     p.addMainView(new HistoricalGraphView(object, items), true, false);
                  }
                  else
                  {
                     PopOutViewWindow window = new PopOutViewWindow(new HistoricalGraphView(object, items));
                     window.open();
                  }
               }
            });
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot get DCI configuration");
         }
      }.start();
	}

	/**
	 * Set map default connection routing algorithm
	 * 
	 * @param routingAlgorithm
	 */
	public void setConnectionRouter(int routingAlgorithm, boolean doSave)
	{
		switch(routingAlgorithm)
		{
			case NetworkMapLink.ROUTING_MANHATTAN:
				this.routingAlgorithm = NetworkMapLink.ROUTING_MANHATTAN;
				viewer.getGraphControl().setRouter(new ManhattanConnectionRouter());
				break;
			default:
				this.routingAlgorithm = NetworkMapLink.ROUTING_DIRECT;
				viewer.getGraphControl().setRouter(null);
				break;
		}
		for(int i = 0; i < actionSetRouter.length; i++)
			actionSetRouter[i].setChecked(routingAlgorithm == (i + 1));
		if (doSave)
		{
			updateObjectPositions();
			saveLayout();
		}
		viewer.refresh();
	}

	/**
	 * Show details for selected object
	 */
	private void showObjectDetails()
	{
		if ((currentSelection.size() != 1) || !(currentSelection.getFirstElement() instanceof AbstractObject))
			return;

		AbstractObject object = (AbstractObject)currentSelection.getFirstElement();
      /* FIXME: show object details */
	}

   /**
    * Goes thought all links and tries to add to request list required DCIs.
    */
   protected void addDciToRequestList()
   {
      Collection<NetworkMapLink> linkList = mapPage.getLinks();
      for(NetworkMapLink item : linkList)
      {
         if (item.hasDciData())
         {
            for(SingleDciConfig value : item.getDciAsList())
            {
               if (value.type == SingleDciConfig.ITEM)
               {
                  dciValueProvider.addDci(value.getNodeId(), value.dciId, mapPage);
               }
               else
               {
                  dciValueProvider.addDci(value.getNodeId(), value.dciId, value.column, value.instance, mapPage);
               }
            }
         }
      }
      Collection<NetworkMapElement> mapElements = mapPage.getElements();
      for(NetworkMapElement element : mapElements)
      {
         if (element instanceof NetworkMapDCIContainer)
         {
            NetworkMapDCIContainer item = (NetworkMapDCIContainer)element;
            if (item.hasDciData())
            {
               for(SingleDciConfig value : item.getObjectDCIArray())
               {
                  if (value.type == SingleDciConfig.ITEM)
                  {
                     dciValueProvider.addDci(value.getNodeId(), value.dciId, mapPage);
                  }
                  else
                  {
                     dciValueProvider.addDci(value.getNodeId(), value.dciId, value.column, value.instance, mapPage);
                  }
               }
            }
         }

         if (element instanceof NetworkMapDCIImage)
         {
            NetworkMapDCIImage item = (NetworkMapDCIImage)element;
            DCIImageConfiguration config = item.getImageOptions();
            SingleDciConfig value = config.getDci();
            if (value.type == SingleDciConfig.ITEM)
            {
               dciValueProvider.addDci(value.getNodeId(), value.dciId, mapPage);
            }
            else
            {
               dciValueProvider.addDci(value.getNodeId(), value.dciId, value.column, value.instance, mapPage);
            }
         }
      }
   }

   /**
    * @param mode
    * @param saveLayout
    */
   protected void setObjectDisplayMode(MapObjectDisplayMode mode, boolean saveLayout)
   {
      labelProvider.setObjectFigureType(mode);
      if (saveLayout)
      {
         updateObjectPositions();
         saveLayout();
      }
      viewer.refresh(true);
      actionShowStatusBackground.setEnabled(mode == MapObjectDisplayMode.ICON);
      actionShowStatusFrame.setEnabled(mode == MapObjectDisplayMode.ICON);
      actionShowStatusIcon.setEnabled(mode == MapObjectDisplayMode.ICON);
      actionFiguresIcons.setChecked(labelProvider.getObjectFigureType() == MapObjectDisplayMode.ICON);
      actionFiguresSmallLabels.setChecked(labelProvider.getObjectFigureType() == MapObjectDisplayMode.SMALL_LABEL);
      actionFiguresLargeLabels.setChecked(labelProvider.getObjectFigureType() == MapObjectDisplayMode.LARGE_LABEL);
      actionFiguresStatusIcons.setChecked(labelProvider.getObjectFigureType() == MapObjectDisplayMode.STATUS);
      actionFiguresFloorPlan.setChecked(labelProvider.getObjectFigureType() == MapObjectDisplayMode.FLOOR_PLAN);
   }
   
   /**
    * Set link locked or unlocked
    */
   protected void changeLinkLock()
   {
      if ((currentSelection.size() != 1) || !(currentSelection.getFirstElement() instanceof NetworkMapLink))
         return;
      
      NetworkMapLink link = (NetworkMapLink)currentSelection.getFirstElement();
      link.setLocked(actionLockLink.isChecked());
      
      if (link.isLocked() && bendpointEditor != null)
      {
         bendpointEditor.stop();
         bendpointEditor = null;
      }                    
      else if (link.getRouting() == NetworkMapLink.ROUTING_BENDPOINTS)
      {
         bendpointEditor = new BendpointEditor(link,
               (GraphConnection)viewer.getGraphControl().getSelection().get(0), viewer);
      }      
   }

   /**
    * Save map image to file
    */
   public boolean saveMapImageToFile(String fileName)
   {
      if (fileName == null)
      {
         FileDialog dlg = new FileDialog(getWindow().getShell(), SWT.SAVE);
         dlg.setFilterExtensions(new String[] { ".png" });
         dlg.setOverwrite(true);
         fileName = dlg.open();
         if (fileName == null)
            return false;
      }
      
      Image image = viewer.takeSnapshot();
      try
      {
         ImageLoader loader = new ImageLoader();
         loader.data = new ImageData[] { image.getImageData() };
         loader.save(fileName, SWT.IMAGE_PNG);
         return true;
      }
      catch(Exception e)
      {
         logger.error("Exception in saveMapImageToFile", e);
         return false;
      }
      finally
      {
         image.dispose();
      }
   }
}
