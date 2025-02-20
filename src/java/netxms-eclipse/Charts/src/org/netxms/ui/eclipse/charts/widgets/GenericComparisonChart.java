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
package org.netxms.ui.eclipse.charts.widgets;

import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Canvas;
import org.netxms.client.datacollection.DataFormatter;
import org.netxms.client.datacollection.GraphItem;
import org.netxms.ui.eclipse.charts.Activator;
import org.netxms.ui.eclipse.charts.api.DataSeries;
import org.netxms.ui.eclipse.console.resources.RegionalSettings;

/**
 * Generic plot area widget for comparison chart
 */
public abstract class GenericComparisonChart extends Canvas implements PlotArea
{
   protected Chart chart;
   protected boolean fontsCreated = false;

   /**
    * Create chart plot area widget.
    *
    * @param parent parent chart widget
    */
   public GenericComparisonChart(Chart parent)
   {
      super(parent, SWT.DOUBLE_BUFFERED);

      chart = parent;

      addPaintListener(new PaintListener() {
         @Override
         public void paintControl(PaintEvent e)
         {
            prepareGCAndRender(e.gc);
         }
      });

      addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            disposeFonts();
         }
      });

      addMouseListener(new MouseListener() {
         @Override
         public void mouseUp(MouseEvent e)
         {
         }

         @Override
         public void mouseDown(MouseEvent e)
         {
         }

         @Override
         public void mouseDoubleClick(MouseEvent e)
         {
            chart.fireDoubleClickListeners();
         }
      });
   }

   /**
    * Create fonts
    */
   protected abstract void createFonts();

   /**
    * Dispose fonts
    */
   protected abstract void disposeFonts();

   /**
    * Create color object from preference string
    * 
    * @param name Preference name
    * @return Color object
    */
   protected Color getColorFromPreferences(final String name)
   {
      return chart.getColorCache().create(PreferenceConverter.getColor(Activator.getDefault().getPreferenceStore(), name));
   }

   /**
    * @see org.netxms.ui.eclipse.charts.widgets.PlotArea#refresh()
    */
   @Override
   public void refresh()
   {
      redraw();
   }

   /**
    * Prepare GC and render chart
    *
    * @param gc GC to use
    */
   private void prepareGCAndRender(GC gc)
   {
      if (!fontsCreated)
      {
         createFonts();
         fontsCreated = true;
      }

      gc.setAntialias(SWT.ON);
      gc.setTextAntialias(SWT.ON);

      render(gc);
   }

   /**
    * Render chart
    * 
    * @param gc graphics context
    */
   protected abstract void render(GC gc);

   /**
    * @param dci
    * @return
    */
   protected String getValueAsDisplayString(GraphItem dci, DataSeries data)
   {
      return new DataFormatter(dci.getDisplayFormat(), data.getDataType(), dci.getMeasurementUnit()).format(data.getCurrentValueAsString(), RegionalSettings.TIME_FORMATTER);
   }
}
