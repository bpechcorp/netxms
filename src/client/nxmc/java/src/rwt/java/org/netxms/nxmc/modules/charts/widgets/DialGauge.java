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
package org.netxms.nxmc.modules.charts.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.netxms.client.datacollection.ChartConfiguration;
import org.netxms.client.datacollection.DataFormatter;
import org.netxms.client.datacollection.GraphItem;
import org.netxms.nxmc.modules.charts.api.DataSeries;
import org.netxms.nxmc.modules.charts.api.GaugeColorMode;
import org.netxms.nxmc.tools.WidgetHelper;

/**
 * Dial chart implementation
 */
public class DialGauge extends GenericGauge
{
	private static final int NEEDLE_PIN_RADIUS = 8;
	private static final int SCALE_OFFSET = 30;	// In percents
	private static final int SCALE_WIDTH = 10;	// In percents

	private Font[] scaleFonts = null;
	private Font[] valueFonts = null;

	/**
	 * @param parent
	 * @param style
	 */
   public DialGauge(Chart parent)
	{
      super(parent);
	}

   /**
    * @see org.netxms.ui.eclipse.charts.widgets.GenericGauge#createFonts()
    */
	@Override
	protected void createFonts()
	{
      String fontName = chart.getConfiguration().getFontName();

		scaleFonts = new Font[16];
		for(int i = 0; i < scaleFonts.length; i++)
			scaleFonts[i] = new Font(getDisplay(), fontName, i + 6, SWT.NORMAL); //$NON-NLS-1$

		valueFonts = new Font[16];
		for(int i = 0; i < valueFonts.length; i++)
			valueFonts[i] = new Font(getDisplay(), fontName, i + 6, SWT.BOLD); //$NON-NLS-1$
	}

   /**
    * @see org.netxms.ui.eclipse.charts.widgets.GenericGauge#disposeFonts()
    */
	@Override
	protected void disposeFonts()
	{
		if (scaleFonts != null)
		{
			for(int i = 0; i < scaleFonts.length; i++)
			{
				scaleFonts[i].dispose();
			}
		}
		if (valueFonts != null)
		{
			for(int i = 0; i < valueFonts.length; i++)
				valueFonts[i].dispose();
		}
	}

   /**
    * @see org.netxms.ui.eclipse.charts.widgets.GenericGauge#renderElement(org.eclipse.swt.graphics.GC,
    *      org.netxms.client.datacollection.ChartConfiguration, org.netxms.client.datacollection.GraphItem,
    *      org.netxms.ui.eclipse.charts.api.DataSeries, int, int, int, int)
    */
	@Override
   protected void renderElement(GC gc, ChartConfiguration configuration, GraphItem dci, DataSeries data, int x, int y, int w, int h)
	{
		Rectangle rect = new Rectangle(x + INNER_MARGIN_WIDTH, y + INNER_MARGIN_HEIGHT, w - INNER_MARGIN_WIDTH * 2, h - INNER_MARGIN_HEIGHT * 2);

      if (configuration.areLabelsVisible() && !configuration.areLabelsInside())
		{
			rect.height -= gc.textExtent("MMM").y + 4; //$NON-NLS-1$
		}
		if (rect.height > rect.width)
		{
			rect.y += (rect.height - rect.width) / 2;
			rect.height = rect.width;
		}
		else
		{
			rect.x += (rect.width - rect.height) / 2;
			rect.width = rect.height;
		}

      double maxValue = configuration.getMaxYScaleValue();
      double minValue = configuration.getMinYScaleValue();

      double angleValue = (maxValue - minValue) / 270.0;
		int outerRadius = (rect.width + 1) / 2;
		int scaleOuterOffset = ((rect.width / 2) * SCALE_OFFSET / 100);
		int scaleInnerOffset = ((rect.width / 2) * (SCALE_OFFSET + SCALE_WIDTH) / 100);

		int cx = rect.x + rect.width / 2 + 1;
		int cy = rect.y + rect.height / 2 + 1;
      gc.setBackground(chart.getColorFromPreferences("Chart.Colors.PlotArea")); //$NON-NLS-1$
		gc.fillArc(rect.x, rect.y, rect.width, rect.height, 0, 360);

		// Draw zones
      switch(GaugeColorMode.getByValue(configuration.getGaugeColorMode()))
		{
		   case ZONE:
            double startAngle = 225;
            startAngle = drawZone(gc, rect, startAngle, minValue, configuration.getLeftRedZone(), angleValue, RED_ZONE_COLOR);
            startAngle = drawZone(gc, rect, startAngle, configuration.getLeftRedZone(), configuration.getLeftYellowZone(), angleValue, YELLOW_ZONE_COLOR);
            startAngle = drawZone(gc, rect, startAngle, configuration.getLeftYellowZone(), configuration.getRightYellowZone(), angleValue, GREEN_ZONE_COLOR);
            startAngle = drawZone(gc, rect, startAngle, configuration.getRightYellowZone(), configuration.getRightRedZone(), angleValue, YELLOW_ZONE_COLOR);
            startAngle = drawZone(gc, rect, startAngle, configuration.getRightRedZone(), maxValue, angleValue, RED_ZONE_COLOR);
		      break;
		   case CUSTOM:
            drawZone(gc, rect, 225, minValue, maxValue, angleValue, chart.getPaletteEntry(0).getRGBObject());
		      break;
		   default:
		      break;
		}

		// Draw center part and border
      gc.setBackground(chart.getColorFromPreferences("Chart.Colors.PlotArea")); //$NON-NLS-1$
      gc.setForeground(chart.getColorFromPreferences("Chart.Axis.Y.Color")); //$NON-NLS-1$
		gc.fillArc(rect.x + scaleInnerOffset, rect.y + scaleInnerOffset, rect.width - scaleInnerOffset * 2, rect.height - scaleInnerOffset * 2, 0, 360);
		gc.setLineWidth(2);
		gc.drawArc(rect.x, rect.y, rect.width, rect.height, 0, 360);
		gc.setLineWidth(1);

		// Draw scale
      Color scaleColor = chart.getColorFromPreferences("Chart.Colors.DialScale"); //$NON-NLS-1$
      Color scaleTextColor = chart.getColorFromPreferences("Chart.Colors.DialScaleText"); //$NON-NLS-1$
      gc.setForeground(scaleColor);
		int textOffset = ((rect.width / 2) * SCALE_OFFSET / 200);
		double arcLength = (outerRadius - scaleOuterOffset) * 4.7123889803846898576939650749193;	// r * (270 degrees angle in radians)
		int step = (arcLength >= 200) ? 27 : 54;
		double valueStep = Math.abs((maxValue - minValue) / ((arcLength >= 200) ? 10 : 20)); 
		int textWidth = (int)(Math.sqrt((outerRadius - scaleOuterOffset) * (outerRadius - scaleOuterOffset) / 2) * 0.7);
		final Font markFont = WidgetHelper.getBestFittingFont(gc, scaleFonts, "900MM", textWidth, outerRadius - scaleOuterOffset); //$NON-NLS-1$
		gc.setFont(markFont);
		for(int i = 225; i >= -45; i -= step)
		{
         if (configuration.isGridVisible())
			{
	         gc.setForeground(scaleColor);
				Point l1 = positionOnArc(cx, cy, outerRadius - scaleOuterOffset, i);
				Point l2 = positionOnArc(cx, cy, outerRadius - scaleInnerOffset, i);
				gc.drawLine(l1.x, l1.y, l2.x, l2.y);
			}
			
	      double angle = (225 - i) * angleValue + minValue;
			String value = DataFormatter.roundDecimalValue(angle, valueStep, 5);
			Point t = positionOnArc(cx, cy, outerRadius - textOffset, i);
         Point ext = gc.textExtent(value);
	      gc.setForeground(scaleTextColor);
			gc.drawText(value, t.x - ext.x / 2, t.y - ext.y / 2, SWT.DRAW_TRANSPARENT);
		}
      gc.setForeground(scaleColor);
		gc.drawArc(rect.x + scaleOuterOffset, rect.y + scaleOuterOffset, rect.width - scaleOuterOffset * 2, rect.height - scaleOuterOffset * 2, -45, 270);
		gc.drawArc(rect.x + scaleInnerOffset, rect.y + scaleInnerOffset, rect.width - scaleInnerOffset * 2, rect.height - scaleInnerOffset * 2, -45, 270);

		// Draw needle
      gc.setBackground(chart.getColorFromPreferences("Chart.Colors.DialNeedle")); //$NON-NLS-1$
      double dciValue = data.getCurrentValue();
		if (dciValue < minValue)
			dciValue = minValue;
		if (dciValue > maxValue)
			dciValue = maxValue;
		int angle = (int)(225 - (dciValue - minValue) / angleValue);
		Point needleEnd = positionOnArc(cx, cy, outerRadius - ((rect.width / 2) * (SCALE_WIDTH / 2) / 100), angle);
		Point np1 = positionOnArc(cx, cy, NEEDLE_PIN_RADIUS / 2, angle - 90);
		Point np2 = positionOnArc(cx, cy, NEEDLE_PIN_RADIUS / 2, angle + 90);
		gc.fillPolygon(new int[] { np1.x, np1.y, needleEnd.x, needleEnd.y, np2.x, np2.y });
		gc.fillArc(cx - NEEDLE_PIN_RADIUS, cy - NEEDLE_PIN_RADIUS, NEEDLE_PIN_RADIUS * 2 - 1, NEEDLE_PIN_RADIUS * 2 - 1, 0, 360);
      gc.setBackground(chart.getColorFromPreferences("Chart.Colors.DialNeedlePin")); //$NON-NLS-1$
		gc.fillArc(cx - NEEDLE_PIN_RADIUS / 2, cy - NEEDLE_PIN_RADIUS / 2, NEEDLE_PIN_RADIUS - 1, NEEDLE_PIN_RADIUS - 1, 0, 360);

		// Draw current value
      String value = getValueAsDisplayString(dci, data);
		gc.setFont(WidgetHelper.getMatchingSizeFont(valueFonts, markFont));
      Point ext = gc.textExtent(value);
		gc.setLineWidth(3);
      gc.setBackground(chart.getColorFromPreferences("Chart.Colors.DialValueBackground")); //$NON-NLS-1$
		int boxW = Math.max(outerRadius - scaleInnerOffset - 6, ext.x + 8);
		gc.fillRoundRectangle(cx - boxW / 2, cy + rect.height / 4, boxW, ext.y + 6, 3, 3);
      gc.setForeground(chart.getColorFromPreferences("Chart.Colors.DialValueText")); //$NON-NLS-1$
		gc.drawText(value, cx - ext.x / 2, cy + rect.height / 4 + 3, true);

      // Draw labels
      if (configuration.areLabelsVisible())
		{
         gc.setFont(configuration.areLabelsInside() ? markFont : null);
         ext = gc.textExtent(dci.getDescription());
         gc.setForeground(chart.getColorFromPreferences("Chart.Colors.Legend")); //$NON-NLS-1$
         if (configuration.areLabelsInside())
			{
            gc.drawText(dci.getDescription(), rect.x + ((rect.width - ext.x) / 2), rect.y + scaleInnerOffset / 2 + rect.height / 4, true);
			}
			else
			{
            gc.drawText(dci.getDescription(), rect.x + ((rect.width - ext.x) / 2), rect.y + rect.height + 4, true);
			}
		}
	}
	
	/**
	 * Draw colored zone.
	 * 
	 * @param gc
	 * @param rect
	 * @param startAngle
	 * @param maxValue2
	 * @param leftRedZone2
	 * @param angleValue
	 * @param color color
	 * @return
	 */
   private double drawZone(GC gc, Rectangle rect, double startAngle, double minValue, double maxValue, double angleValue, RGB color)
	{
		if (minValue >= maxValue)
			return startAngle;	// Ignore incorrect zone settings

      double angle = (maxValue - minValue) / angleValue;
		if (angle <= 0)
			return startAngle;

		int offset = ((rect.width / 2) * SCALE_OFFSET / 100);

      gc.setBackground(chart.getColorCache().create(color));
      gc.fillArc(rect.x + offset, rect.y + offset, rect.width - offset * 2, rect.height - offset * 2, (int)Math.ceil(startAngle), (int)-Math.ceil(angle));
      return startAngle - angle;
	}

	/**
	 * Find point coordinates on arc by given angle and radius. Angles are 
	 * interpreted such that 0 degrees is at the 3 o'clock position.
	 * 
	 * @param cx center point X coordinate
	 * @param cy center point Y coordinate
	 * @param radius radius
	 * @param angle angle
	 * @return point coordinates
	 */
	private Point positionOnArc(int cx, int cy, int radius, int angle)
	{
		return new Point((int)(radius * Math.cos(Math.toRadians(angle)) + cx), (int)(radius * -Math.sin(Math.toRadians(angle)) + cy));
	}

   /**
    * Get minimal element size
    * 
    * @return
    */
	@Override
   protected Point getMinElementSize()
   {
      return new Point(40, 40);
   }
}
