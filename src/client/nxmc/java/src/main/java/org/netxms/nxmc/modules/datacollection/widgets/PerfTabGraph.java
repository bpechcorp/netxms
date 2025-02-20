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
package org.netxms.nxmc.modules.datacollection.widgets;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.netxms.client.NXCSession;
import org.netxms.client.constants.HistoricalDataType;
import org.netxms.client.constants.TimeUnit;
import org.netxms.client.datacollection.ChartConfiguration;
import org.netxms.client.datacollection.ChartDciConfig;
import org.netxms.client.datacollection.DciData;
import org.netxms.client.datacollection.GraphItem;
import org.netxms.client.datacollection.PerfTabDci;
import org.netxms.client.datacollection.Threshold;
import org.netxms.client.objects.AbstractObject;
import org.netxms.nxmc.Registry;
import org.netxms.nxmc.base.actions.RefreshAction;
import org.netxms.nxmc.base.jobs.Job;
import org.netxms.nxmc.base.views.Perspective;
import org.netxms.nxmc.base.views.View;
import org.netxms.nxmc.base.widgets.DashboardComposite;
import org.netxms.nxmc.base.windows.PopOutViewWindow;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.charts.api.ChartType;
import org.netxms.nxmc.modules.charts.widgets.Chart;
import org.netxms.nxmc.modules.datacollection.views.HistoricalGraphView;
import org.netxms.nxmc.modules.datacollection.views.HistoricalGraphView.ChartActionType;
import org.netxms.nxmc.modules.datacollection.views.HistoricalGraphView.HistoricalChartOwner;
import org.netxms.nxmc.modules.datacollection.views.helpers.PerfTabGraphSettings;
import org.netxms.nxmc.modules.objects.views.ObjectView;
import org.netxms.nxmc.tools.ViewRefreshController;
import org.netxms.nxmc.tools.VisibilityValidator;
import org.xnap.commons.i18n.I18n;

/**
 * Performance tab graph
 */
public class PerfTabGraph extends DashboardComposite implements HistoricalChartOwner
{
   private static I18n i18n = LocalizationHelper.getI18n(PerfTabGraph.class);

	private long nodeId;
	private List<PerfTabDci> items = new ArrayList<PerfTabDci>(4);
   private Chart chart;
   private View view;
	private ViewRefreshController refreshController = null;
	private boolean updateInProgress = false;
	private NXCSession session;
	private VisibilityValidator validator;
   private Action actionRefresh;
   private Action actionAdjustX;
   private Action actionAdjustY;
   private Action actionAdjustBoth;
   private Action[] presetActions;
   private PerfTabGraphSettings settings;

	/**
	 * @param parent
	 * @param style
	 */
	public PerfTabGraph(Composite parent, long nodeId, PerfTabDci dci, PerfTabGraphSettings settings, View view, VisibilityValidator validator)
	{
		super(parent, SWT.BORDER);
		this.nodeId = nodeId;
      this.view = view;
		this.validator = validator;
		this.settings = settings;
		items.add(dci);
      session = Registry.getSession();

		setLayout(new FillLayout());

      ChartConfiguration chartConfiguration = new ChartConfiguration();
      chartConfiguration.setZoomEnabled(false);
      chartConfiguration.setTitleVisible(true);
      chartConfiguration.setTitle(settings.getRuntimeTitle());
      chartConfiguration.setLegendVisible(settings.isShowLegendAlways());
      chartConfiguration.setExtendedLegend(settings.isExtendedLegend());
      chartConfiguration.setLogScale(settings.isLogScaleEnabled());
      chartConfiguration.setUseMultipliers(settings.isUseMultipliers());
      chartConfiguration.setStacked(settings.isStacked());
      chartConfiguration.setTranslucent(settings.isTranslucent());
      chartConfiguration.setAutoScale(settings.isAutoScale());
      chartConfiguration.setMinYScaleValue(settings.getMinYScaleValue());
      chartConfiguration.setMaxYScaleValue(settings.getMaxYScaleValue());

      chart = new Chart(this, SWT.NONE, ChartType.LINE, chartConfiguration);

      final Date from = new Date(System.currentTimeMillis() - settings.getTimeRangeMillis());
      final Date to = new Date(System.currentTimeMillis());
      chart.setTimeRange(from, to);

      GraphItem item = new GraphItem(nodeId, dci.getId(), null, settings.getRuntimeName(), "", settings.getType(), settings.isAutomaticColor() ? -1 : settings.getColorAsInt());
      item.setInverted(settings.isInvertedValues());
      item.setShowThresholds(settings.isShowThresholds());
      item.setMeasurementUnit(dci.getMeasurementUnit());
      chart.addParameter(item);

		addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            if (refreshController != null)
               refreshController.dispose();
         }
      });

      chart.addDoubleClickListener(new IDoubleClickListener() {
         @Override
         public void doubleClick(DoubleClickEvent event)
         {
            openHistoryGraph();
         }
      });

      createActions();
      createChartContextMenu();
   }

   /**
    * Create actions
    */
   private void createActions()
   {
      actionRefresh = new RefreshAction() {
         @Override
         public void run()
         {
            refreshData();
         }
      };

      actionAdjustX = HistoricalGraphView.createAction(ChartActionType.ADJUST_X, this);
      actionAdjustY = HistoricalGraphView.createAction(ChartActionType.ADJUST_Y, this);
      actionAdjustBoth = HistoricalGraphView.createAction(ChartActionType.ADJUST_BOTH, this);

      presetActions = HistoricalGraphView.createPresetActions(new HistoricalGraphView.PresetHandler() {
         @Override
         public void onPresetSelected(TimeUnit unit, int range)
         {
            settings.setTimeUnits(unit.getValue());
            settings.setTimeRange(range);
            refreshData();
         }
      });
   }

   /**
    * Create chart's context menu
    */
   private void createChartContextMenu()
   {
      final MenuManager manager = new MenuManager();
      manager.setRemoveAllWhenShown(true);
      manager.addMenuListener(new IMenuListener() {
         public void menuAboutToShow(IMenuManager mgr)
         {
            fillContextMenu(manager);
         }
      });
      chart.setMenuManager(manager);
   }

   /**
    * Fill context menu
    * 
    * @param manager
    */
   private void fillContextMenu(IMenuManager manager)
   {
      MenuManager presets = new MenuManager("&Presets");
      for(int i = 0; i < presetActions.length; i++)
         presets.add(presetActions[i]);

      manager.add(presets);
      manager.add(new Separator());
      manager.add(actionAdjustBoth);
      manager.add(actionAdjustX);
      manager.add(actionAdjustY);
      manager.add(new Separator());
      manager.add(actionRefresh);
   }
	
	/**
	 * Add another item to graph
	 * 
	 * @param dci
	 * @param settings
	 */
	public void addItem(PerfTabDci dci, PerfTabGraphSettings settings)
	{
      chart.getConfiguration().setLegendVisible(true);
		if (settings.isExtendedLegend())
         chart.getConfiguration().setExtendedLegend(true);
		synchronized(items)
		{
			items.add(dci);
         GraphItem item = new GraphItem(nodeId, dci.getId(), null, settings.getRuntimeName(), "", settings.getType(), settings.isAutomaticColor() ? -1 : settings.getColorAsInt());
         item.setInverted(settings.isInvertedValues());
         item.setShowThresholds(settings.isShowThresholds());
         item.setMeasurementUnit(dci.getMeasurementUnit());
         chart.addParameter(item);
		}
	}

	/**
	 * Start chart update
	 */
	public void start()
	{
      chart.rebuild();
      refreshController = new ViewRefreshController(view, 30, new Runnable() {
			@Override
			public void run()
			{
				if (PerfTabGraph.this.isDisposed())
					return;

				refreshData();
			}
		}, validator);
		if (validator.isVisible())
		   refreshData();
	}

	/**
	 * Refresh graph's data
	 */
	public void refreshData()
	{
		if (updateInProgress)
			return;

		updateInProgress = true;

      Job job = new Job(i18n.tr("Updating performance view"), view) {
			private PerfTabDci currentDci;

			@Override
         protected void run(IProgressMonitor monitor) throws Exception
			{
				final Date from = new Date(System.currentTimeMillis() - settings.getTimeRangeMillis());
				final Date to = new Date(System.currentTimeMillis());
				synchronized(items)
				{
					final DciData[] data = new DciData[items.size()];
               final Threshold[][] thresholds = new Threshold[items.size()][];
					for(int i = 0; i < data.length; i++)
					{
						currentDci = items.get(i);
						data[i] = session.getCollectedData(nodeId, currentDci.getId(), from, to, 0, HistoricalDataType.PROCESSED);
						thresholds[i] = session.getThresholds(nodeId, currentDci.getId());
					}
					runInUIThread(new Runnable() {
						@Override
						public void run()
						{
                     if (!chart.isDisposed())
							{
								chart.setTimeRange(from, to);
								for(int i = 0; i < data.length; i++)
                           chart.updateParameter(i, data[i], false);
                        chart.setThresholds(thresholds);
                        chart.refresh();
							}
							updateInProgress = false;
						}
					});
				}
			}

			@Override
			protected String getErrorMessage()
			{
            return String.format(i18n.tr("Cannot get value for DCI %d (%s)"), currentDci.getId(), currentDci.getDescription());
			}

			@Override
         protected void jobFailureHandler(Exception e)
			{
				updateInProgress = false;
			}
		};
		job.setUser(false);
		job.start();
	}

	/**
	 * Open history graph of dci
	 */
	private void openHistoryGraph()
	{
      List<ChartDciConfig> graphItems = new ArrayList<ChartDciConfig>(items.size());
      for(PerfTabDci dci : items)
      {
         ChartDciConfig cd = new ChartDciConfig();
         cd.nodeId = nodeId;
         cd.dciId = dci.getId();
         cd.name = dci.getDescription();
         cd.dciName = dci.getDescription();
         cd.dciDescription = dci.getDescription();
         graphItems.add(cd);
      }

      AbstractObject object = (view instanceof ObjectView) ? ((ObjectView)view).getObject() : session.findObjectById(nodeId);
      Perspective p = view.getPerspective();
      if (p != null)
      {
         p.addMainView(new HistoricalGraphView(object, graphItems), true, false);
      }
      else
      {
         PopOutViewWindow window = new PopOutViewWindow(new HistoricalGraphView(object, graphItems));
         window.open();
      }
	}

   /**
    * @see org.netxms.nxmc.modules.datacollection.views.HistoricalGraphView.HistoricalChartOwner#getChart()
    */
   @Override
   public Chart getChart()
   {
      return chart;
   }
}
