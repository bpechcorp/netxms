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

import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IViewPart;
import org.netxms.client.dashboards.DashboardElement;
import org.netxms.client.objects.AbstractObject;
import org.netxms.ui.eclipse.console.Activator;
import org.netxms.ui.eclipse.dashboard.widgets.internal.DashboardElementConfig;
import org.netxms.ui.eclipse.dashboard.widgets.internal.DashboardElementLayout;
import org.netxms.ui.eclipse.tools.ColorConverter;
import org.netxms.ui.eclipse.tools.FontTools;
import org.netxms.ui.eclipse.tools.IntermediateSelectionProvider;
import org.netxms.ui.eclipse.widgets.DashboardComposite;

/**
 * Base class for all dashboard elements
 */
public class ElementWidget extends DashboardComposite implements ControlListener
{
	protected DashboardElement element;
	protected IViewPart viewPart;

   private Label title;
   private Composite content;
   private Font titleFont;
	private DashboardControl dbc;
	private DashboardElementLayout layout;
	private boolean editMode = false;
	private EditPaneWidget editPane = null;
	
	/**
	 * @param parent
	 * @param style
	 */
	protected ElementWidget(DashboardControl parent, int style, DashboardElement element, IViewPart viewPart)
	{
		super(parent, style);
		dbc = parent;
		this.element = element;
		this.viewPart = viewPart;
      setupElement();
	}

	/**
	 * @param parent
	 * @param style
	 */
	protected ElementWidget(DashboardControl parent, DashboardElement element, IViewPart viewPart)
	{
		super(parent, SWT.BORDER);
		dbc = parent;
		this.element = element;
		this.viewPart = viewPart;
      setupElement();
	}

   /**
    * Setup this element
    */
   private void setupElement()
   {
      parseLayout(element.getLayout());
      addControlListener(this);

      GridLayout layout = new GridLayout();
      layout.marginHeight = 0;
      layout.marginWidth = 0;
      layout.verticalSpacing = 4;
      setLayout(layout);

      content = new Composite(this, SWT.NONE) {
         @Override
         public Point computeSize(int wHint, int hHint, boolean changed)
         {
            Point size = super.computeSize(wHint, hHint, changed);
            if (hHint == SWT.DEFAULT)
            {
               int h = adjustContentHeight(this, size);
               if (h > 0)
                  size.y = h;
            }
            return (size != null) ? size : super.computeSize(wHint, hHint, changed);
         }
      };
      content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      content.setLayout(new FillLayout());
      content.setBackground(getBackground());

      addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            if (titleFont != null)
               titleFont.dispose();
         }
      });
   }

   /**
    * Get content area for placing element's widgets.
    *
    * @return content area for placing element's widgets
    */
   protected Composite getContentArea()
   {
      return content;
   }

   /**
    * Adjust content height. Called by framework after size for content area is computed so subclasses can implement more complex
    * logic for preferred height calculation.
    *
    * @param content content area
    * @param computedSize computed content area size
    * @return adjusted content height or -1 to leave computed height
    */
   protected int adjustContentHeight(Composite content, Point computedSize)
   {
      return -1;
   }

   /**
    * Set element's title.
    *
    * @param text
    * @param backgroundColor
    * @param foregroundColor
    * @param textSizeAdjustment
    */
   protected void setTitle(String text, RGB backgroundColor, RGB foregroundColor, int textSizeAdjustment)
   {
      if (title != null)
         title.dispose();

      title = new Label(this, SWT.CENTER);
      title.setText(text.replace("&", "&&"));
      if (backgroundColor != null)
      {
         title.setBackground(colors.create(backgroundColor));
         setBackground(title.getBackground());
      }
      else
      {
         title.setBackground(getBackground());
      }
      if (foregroundColor != null)
         title.setForeground(colors.create(foregroundColor));
      if (titleFont != null)
         titleFont.dispose();
      titleFont = FontTools.createAdjustedFont(FontTools.getTitleFont(), textSizeAdjustment);
      title.setFont(titleFont);
      GridData gd = new GridData(SWT.CENTER, SWT.CENTER, true, false);
      gd.verticalIndent = 4;
      title.setLayoutData(gd);
      title.moveAbove(null);
      layout(true, true);
   }

   /**
    * Process settings common for all elements.
    *
    * @param config dashboard element configuration
    */
   protected void processCommonSettings(DashboardElementConfig config)
   {
      if (!config.getTitle().isEmpty())
         setTitle(config.getTitle(), ColorConverter.parseColorDefinition(config.getTitleBackground()), ColorConverter.parseColorDefinition(config.getTitleForeground()), config.getTitleFontSize());
   }

	/**
	 * @param xml
	 */
	private void parseLayout(String xml)
	{
		try
		{
			layout = DashboardElementLayout.createFromXml(xml);
		}
		catch(Exception e)
		{
         Activator.logError("Cannot parse dashboard element layout", e);
			layout = new DashboardElementLayout();
		}
	}

	/**
	 * @return the layout
	 */
	public DashboardElementLayout getElementLayout()
	{
		return layout;
	}

	/**
	 * @return the editMode
	 */
	public boolean isEditMode()
	{
		return editMode;
	}

	/**
	 * @param editMode the editMode to set
	 */
	public void setEditMode(boolean editMode)
	{
		this.editMode = editMode;
		if (editMode)
      {
			editPane = new EditPaneWidget(this, dbc, element);
			editPane.setLocation(0,  0);
			editPane.setSize(getSize());
			editPane.moveAbove(null);
		}
      else if (editPane != null)
		{
         editPane.dispose();
         editPane = null;
		}
	}

   /**
    * @see org.eclipse.swt.events.ControlListener#controlMoved(org.eclipse.swt.events.ControlEvent)
    */
	@Override
	public void controlMoved(ControlEvent e)
	{
	}

   /**
    * @see org.eclipse.swt.events.ControlListener#controlResized(org.eclipse.swt.events.ControlEvent)
    */
	@Override
	public void controlResized(ControlEvent e)
	{
		if (editPane != null)
		{
			editPane.setLocation(0,  0);
			editPane.setSize(getSize());
			editPane.moveAbove(null);
		}
	}

	/**
	 * Set delegate selection provider
	 * 
	 * @param delegate
	 */
	protected void setSelectionProviderDelegate(ISelectionProvider delegate)
	{
	   dbc.getSelectionProvider().setSelectionProviderDelegate(delegate);
	}

	/**
	 * Get intermediate selection provider
	 * 
	 * @return
	 */
	protected IntermediateSelectionProvider getSelectionProvider()
	{
	   return dbc.getSelectionProvider();
	}
	
	/**
	 * Request layout run for entire dashboard. Can be called by subclasses when complete dashboard layout re-run is needed. 
	 */
	protected void requestDashboardLayout()
	{
	   dbc.layout(true, true);
	}

   /**
    * Get ID of owning dashboard object.
    *
    * @return ID of owning dashboard object
    */
   protected long getDashboardObjectId()
   {
      return dbc.getDashboardObject().getObjectId();
   }

   /**
    * Get context for owning dashboard.
    *
    * @return context for owning dashboard (can be null)
    */
   protected AbstractObject getContext()
   {
      return dbc.getContext();
   }

   /**
    * Get ID of context object.
    *
    * @return ID of context object or 0 if context is not set
    */
   protected long getContextObjectId()
   {
      AbstractObject object = dbc.getContext();
      return (object != null) ? object.getObjectId() : 0;
   }

   /**
    * Get effective object ID - if suppliet object ID is a context placeholder, returns ID of current context object, otherwise
    * supplied ID itself.
    * 
    * @param objectId object ID to check
    * @return supplied object ID or context object ID if supplied ID is context placeholder
    */
   protected long getEffectiveObjectId(long objectId)
   {
      return (objectId == AbstractObject.CONTEXT) ? getContextObjectId() : objectId;
   }
}
