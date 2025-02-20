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
package org.netxms.ui.eclipse.dashboard.widgets;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IViewPart;
import org.netxms.client.NXCSession;
import org.netxms.client.constants.HistoricalDataType;
import org.netxms.client.dashboards.DashboardElement;
import org.netxms.client.datacollection.ChartDciConfig;
import org.netxms.client.datacollection.DciData;
import org.netxms.client.datacollection.DciDataRow;
import org.netxms.client.datacollection.DciValue;
import org.netxms.client.datacollection.GraphItem;
import org.netxms.client.datacollection.MeasurementUnit;
import org.netxms.client.datacollection.Threshold;
import org.netxms.client.objects.AbstractObject;
import org.netxms.ui.eclipse.charts.widgets.Chart;
import org.netxms.ui.eclipse.dashboard.Activator;
import org.netxms.ui.eclipse.dashboard.Messages;
import org.netxms.ui.eclipse.jobs.ConsoleJob;
import org.netxms.ui.eclipse.shared.ConsoleSharedData;
import org.netxms.ui.eclipse.tools.ViewRefreshController;

/**
 * Base class for data comparison charts - like bar chart, pie chart, etc.
 */
public abstract class ComparisonChartElement extends ElementWidget
{
   protected Chart chart;
	protected NXCSession session;
	protected int refreshInterval = 30;
	protected boolean updateThresholds = false;
   protected List<ChartDciConfig> runtimeDciList = new ArrayList<>();

	private ViewRefreshController refreshController;
	private boolean updateInProgress = false;

   /**
    * @param parent parent composite
    * @param element dashboard element
    * @param viewPart owning view
    */
   public ComparisonChartElement(DashboardControl parent, DashboardElement element, IViewPart viewPart)
   {
      super(parent, element, viewPart);
      session = ConsoleSharedData.getSession();

		addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            if (refreshController != null)
               refreshController.dispose();
         }
      });
   }

   /**
    * Configure metrics on chart and start refresh timer on success
    */
   protected void configureMetrics()
   {
      ConsoleJob job = new ConsoleJob("Reading measurement unit information", viewPart, Activator.PLUGIN_ID) {
         @Override
         protected void runInternal(IProgressMonitor monitor) throws Exception
         {
            DciValue[] nodeDciList = null;
            for(ChartDciConfig dci : getDciList())
            {
               if ((dci.nodeId == 0) || (dci.nodeId == AbstractObject.CONTEXT))
               {
                  AbstractObject contextObject = getContext();
                  if (contextObject == null)
                     continue;

                  if (nodeDciList == null)
                     nodeDciList = session.getLastValues(contextObject.getObjectId());

                  Pattern namePattern = Pattern.compile(dci.dciName);
                  Pattern descriptionPattern = Pattern.compile(dci.dciDescription);
                  for(DciValue dciInfo : nodeDciList)
                  {
                     if ((!dci.dciName.isEmpty() && namePattern.matcher(dciInfo.getName()).find()) || (!dci.dciDescription.isEmpty() && descriptionPattern.matcher(dciInfo.getDescription()).find()))
                     {
                        ChartDciConfig instance = new ChartDciConfig(dci);
                        instance.nodeId = contextObject.getObjectId();
                        instance.dciId = dciInfo.getId();
                        runtimeDciList.add(instance);
                        if (!dci.multiMatch)
                           break;
                     }
                  }
               }
               else
               {
                  runtimeDciList.add(dci);
               }
            }

            final Map<Long, MeasurementUnit> measurementUnits = session.getDciMeasurementUnits(runtimeDciList);
            runInUIThread(new Runnable() {
               @Override
               public void run()
               {
                  if (chart.isDisposed())
                     return;

                  for(ChartDciConfig dci : runtimeDciList)
                  {
                     GraphItem item = new GraphItem(dci);
                     item.setMeasurementUnit(measurementUnits.get(dci.getDciId()));
                     chart.addParameter(item);
                  }

                  chart.rebuild();
                  layout(true, true);
                  startRefreshTimer();
               }
            });
         }

         @Override
         protected String getErrorMessage()
         {
            return "Cannot read measurement unit information";
         }
      };
      job.setUser(false);
      job.start();
   }

	/**
	 * Start refresh timer
	 */
	protected void startRefreshTimer()
	{
		refreshController = new ViewRefreshController(viewPart, refreshInterval, new Runnable() {
			@Override
			public void run()
			{
				if (ComparisonChartElement.this.isDisposed())
					return;
				
            refreshData();
			}
		});
      refreshData();
	}

	/**
	 * Refresh graph's data
	 */
   protected void refreshData()
	{
		if (updateInProgress)
			return;

		updateInProgress = true;

		ConsoleJob job = new ConsoleJob(Messages.get().ComparisonChartElement_JobTitle, viewPart, Activator.PLUGIN_ID) {
			@Override
			protected void runInternal(IProgressMonitor monitor) throws Exception
			{
            final DciData[] data = new DciData[runtimeDciList.size()];
            for(int i = 0; i < runtimeDciList.size(); i++)
				{
               ChartDciConfig dci = runtimeDciList.get(i);
               if (dci.type == ChartDciConfig.ITEM)
                  data[i] = session.getCollectedData(dci.nodeId, dci.dciId, null, null, 1, HistoricalDataType.PROCESSED);
					else
                  data[i] = session.getCollectedTableData(dci.nodeId, dci.dciId, dci.instance, dci.column, null, null, 1);
				}

            final Threshold[][] thresholds;
            if (updateThresholds)
            {
               thresholds = new Threshold[runtimeDciList.size()][];
               for(int i = 0; i < runtimeDciList.size(); i++)
               {
                  ChartDciConfig dci = runtimeDciList.get(i);
                  if (dci.type == ChartDciConfig.ITEM)
                     thresholds[i] = session.getThresholds(dci.nodeId, dci.dciId);
                  else
                     thresholds[i] = new Threshold[0];
               }
            }
            else
            {
               thresholds = null;
            }

				runInUIThread(new Runnable() {
					@Override
					public void run()
					{
                  updateInProgress = false;
                  if (chart.isDisposed())
                     return;

                  for(int i = 0; i < data.length; i++)
						{
                     DciDataRow lastValue = data[i].getLastValue();
                     chart.updateParameter(i, (lastValue != null) ? lastValue : new DciDataRow(new Date(), 0.0), data[i].getDataType(), false);
                     if (updateThresholds)
                        chart.updateParameterThresholds(i, thresholds[i]);
						}
                  chart.refresh();
                  chart.clearErrors();
					}
				});
			}

			@Override
			protected String getErrorMessage()
			{
				return Messages.get().ComparisonChartElement_JobError;
			}
	
			@Override
			protected void jobFailureHandler()
			{
				updateInProgress = false;
				super.jobFailureHandler();
			}

			@Override
			protected IStatus createFailureStatus(final Exception e)
			{
				runInUIThread(new Runnable() {
					@Override
					public void run()
					{
						chart.addError(getErrorMessage() + " (" + e.getLocalizedMessage() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				});
				return Status.OK_STATUS;
			}
		};
		job.setUser(false);
		job.start();
	}

   /**
    * @see org.netxms.ui.eclipse.dashboard.widgets.ElementWidget#adjustContentHeight(org.eclipse.swt.widgets.Composite,
    *      org.eclipse.swt.graphics.Point)
    */
   @Override
   protected int adjustContentHeight(Composite content, Point computedSize)
   {
      return Math.max(computedSize.y, 250);
   }

   /**
    * Get list of configured DCIs.
    *
    * @return list of configured DCIs
    */
	protected abstract ChartDciConfig[] getDciList();
}
