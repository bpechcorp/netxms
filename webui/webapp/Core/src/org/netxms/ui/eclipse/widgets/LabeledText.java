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
package org.netxms.ui.eclipse.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;

/**
 * Composite widget - text with label
 */
public class LabeledText extends LabeledControl
{
	/**
	 * @param parent
	 * @param style
	 */
	public LabeledText(Composite parent, int style)
	{
		super(parent, style);
	}

	/**
	 * @param parent
	 * @param style
	 * @param textStyle
	 */
	public LabeledText(Composite parent, int style, int textStyle)
	{
		super(parent, style, textStyle);
	}
	
   /**
    * @param parent
    * @param style
    * @param textStyle
    * @param widthHint
    */
   public LabeledText(Composite parent, int style, int textStyle, int widthHint)
   {
      super(parent, style, textStyle, widthHint);
   }

	/**
	 * @param parent
	 * @param style
	 * @param textStyle
	 * @param toolkit
	 */
	public LabeledText(Composite parent, int style, int textStyle, FormToolkit toolkit)
	{
      super(parent, style, textStyle, SWT.DEFAULT, toolkit, null);
	}

   /**
    * @param parent
    * @param style
    * @param textStyle
    * @param widthHint
    * @param toolkit
    */
   public LabeledText(Composite parent, int style, int textStyle, int widthHint, FormToolkit toolkit)
   {
      super(parent, style, textStyle, widthHint, toolkit, null);
   }

   /**
    * @see org.netxms.ui.eclipse.widgets.LabeledControl#createControl(int, java.lang.Object)
    */
   @Override
   protected Control createControl(int controlStyle, Object parameters)
   {
      return (toolkit != null) ? toolkit.createText(this, "", controlStyle) : new Text(this, controlStyle); //$NON-NLS-1$;
   }

   /**
    * @see org.netxms.ui.eclipse.widgets.LabeledControl#getDefaultControlStyle()
    */
   @Override
   protected int getDefaultControlStyle()
   {
      return SWT.BORDER | SWT.SINGLE;
   }
	
   /**
    * @see org.netxms.ui.eclipse.widgets.LabeledControl#isExtraVerticalSpaceNeeded(int)
    */
   @Override
   protected boolean isExtraVerticalSpaceNeeded(int controlStyle)
   {
      return (controlStyle & SWT.MULTI) != 0;
   }

   /**
    * Sets the editable state.
    * 
    * @param editable the new editable state
    */
   public void setEditable(boolean editable)
   {
      ((Text)control).setEditable(editable);
   }
   
   /**
    * Returns the editable state.
    * 
    * @return whether or not the receiver is editable
    */
   public boolean isEditable()
   {
      return ((Text)control).getEditable();
   }
	
   /**
    * @see org.netxms.ui.eclipse.widgets.LabeledControl#setText(java.lang.String)
    */
   @Override
	public void setText(final String newText)
	{
      ((Text)control).setText((newText != null) ? newText : ""); //$NON-NLS-1$
	}

   /**
    * @see org.netxms.ui.eclipse.widgets.LabeledControl#getText()
    */
   @Override
	public String getText()
	{
		return ((Text)control).getText();
	}

	/**
	 * Get text control
	 * 
	 * @return text control
	 */
	public Text getTextControl()
	{
		return (Text)control;
	}
}
