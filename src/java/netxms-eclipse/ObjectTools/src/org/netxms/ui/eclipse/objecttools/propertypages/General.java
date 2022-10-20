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
package org.netxms.ui.eclipse.objecttools.propertypages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.netxms.client.objecttools.ObjectTool;
import org.netxms.client.objecttools.ObjectToolDetails;
import org.netxms.ui.eclipse.console.resources.SharedIcons;
import org.netxms.ui.eclipse.objecttools.Activator;
import org.netxms.ui.eclipse.objecttools.Messages;
import org.netxms.ui.eclipse.tools.MessageDialogHelper;
import org.netxms.ui.eclipse.tools.WidgetHelper;
import org.netxms.ui.eclipse.widgets.LabeledSpinner;
import org.netxms.ui.eclipse.widgets.LabeledText;

/**
 * "General" property page for object tool
 */
public class General extends PreferencePage
{
	private ObjectToolDetails objectTool;
	private LabeledText textName;
	private LabeledText textDescription;
	private LabeledText textData;
	private LabeledSpinner maxFileSize;
	private LabeledText textParameter;
	private LabeledText textRegexp;
	private Button checkShowOutput;
   private Button checkSuppressSuccessMessage;
	private Button checkConfirmation;
   private LabeledText textConfirmation;
	private Button checkDisable;
   private Button checkRunInContainerContext;
	private Button checkFollow;
	private Button checkCommand;
	private LabeledText textCommandName;
   private LabeledText textCommandShortName;
	private Button radioIndexOID;
	private Button radioIndexValue;
	private Label iconLabel;
	private Image icon;
	
	/**
	 * Constructor
	 * 
	 * @param toolDetails
	 */
	public General(ObjectToolDetails toolDetails)
	{
      super("General");
      noDefaultAndApplyButton();
	   objectTool = toolDetails;
	}

   /**
    * @see org.eclipse.jface.preference.PreferencePage#createContents(org.eclipse.swt.widgets.Composite)
    */
	@Override
	protected Control createContents(Composite parent)
	{			
		Composite dialogArea = new Composite(parent, SWT.NONE);
		
		GridLayout layout = new GridLayout();
      		layout.verticalSpacing = WidgetHelper.DIALOG_SPACING;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
      		layout.numColumns = 2;
		dialogArea.setLayout(layout);

		textName = new LabeledText(dialogArea, SWT.NONE);
		textName.setLabel(Messages.get().General_Name);
		GridData gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
		textName.setLayoutData(gd);
		textName.setText(objectTool.getName());

		createIcon();
		createIconSelector(dialogArea);

		textDescription = new LabeledText(dialogArea, SWT.NONE);
		textDescription.setLabel(Messages.get().General_Description);
		gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
      gd.horizontalSpan = 2;
		textDescription.setLayoutData(gd);
		textDescription.setText(objectTool.getDescription());

		textData = new LabeledText(dialogArea, SWT.NONE);
		gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
		gd.horizontalSpan = 2;
		textData.setLayoutData(gd);
		textData.setText(objectTool.getData());

		switch(objectTool.getToolType())
		{
			case ObjectTool.TYPE_INTERNAL:
				textData.setLabel(Messages.get().General_Operation);
				break;
			case ObjectTool.TYPE_LOCAL_COMMAND:
			case ObjectTool.TYPE_SERVER_COMMAND:
			case ObjectTool.TYPE_SSH_COMMAND:
				textData.setLabel(Messages.get().General_Command);
				createOutputGroup(dialogArea);
				break;
         case ObjectTool.TYPE_SERVER_SCRIPT:
            textData.setLabel(Messages.get().General_Script);
            createOutputGroup(dialogArea);
            break;
			case ObjectTool.TYPE_ACTION:
				textData.setLabel(Messages.get().General_AgentCommand);
				createOutputGroup(dialogArea);
				break;
			case ObjectTool.TYPE_URL:
				textData.setLabel(Messages.get().General_URL);
				break;
			case ObjectTool.TYPE_FILE_DOWNLOAD:
			   String[] parameters = objectTool.getData().split("\u007F"); //$NON-NLS-1$

				textData.setLabel(Messages.get().General_RemoteFileName);
				textData.setText((parameters.length > 0) ? parameters[0] : ""); //$NON-NLS-1$

				Group fileOptionsGoup = new Group(dialogArea, SWT.NONE);
				fileOptionsGoup.setText(Messages.get().General_FileOptions);
            gd = new GridData();
            gd.horizontalAlignment = SWT.FILL;
            gd.grabExcessHorizontalSpace = true;
            gd.horizontalSpan = 2;
            fileOptionsGoup.setLayoutData(gd);

            GridLayout fileGroupLayout = new GridLayout();
				fileGroupLayout.verticalSpacing = WidgetHelper.OUTER_SPACING;
				fileGroupLayout.numColumns = 1;
				fileOptionsGoup.setLayout(fileGroupLayout);

		      maxFileSize  = new LabeledSpinner(fileOptionsGoup, SWT.NONE);
		      gd = new GridData();
		      gd.horizontalAlignment = SWT.FILL;
		      gd.grabExcessHorizontalSpace = true;
		      maxFileSize.setLayoutData(gd);
				maxFileSize.setLabel(Messages.get().General_LimitDownloadFileSizeLable);
				maxFileSize.setRange(0, 0x7FFFFFFF);
				try
				{
				   maxFileSize.setSelection((parameters.length > 1) ? Integer.parseInt(parameters[1]) : 0);
				}
				catch(NumberFormatException e)
				{
				   maxFileSize.setSelection(0);
				}
							
				checkFollow = new Button(fileOptionsGoup, SWT.CHECK);
				checkFollow.setText(Messages.get().General_FollowFileChanges);
				if(parameters.length > 2) //$NON-NLS-1$
				{
				   checkFollow.setSelection( parameters[2].equals("true") ? true : false);  //$NON-NLS-1$
				}	
				break;
			case ObjectTool.TYPE_SNMP_TABLE:
				textData.setLabel(Messages.get().General_Title);
				
				Group snmpOptGroup = new Group(dialogArea, SWT.NONE);
				snmpOptGroup.setText(Messages.get().General_SNMPListOptions);
				gd = new GridData();
				gd.horizontalAlignment = SWT.FILL;
				gd.grabExcessHorizontalSpace = true;
				gd.horizontalSpan = 2;
				snmpOptGroup.setLayoutData(gd);
				layout = new GridLayout();
				snmpOptGroup.setLayout(layout);

				new Label(snmpOptGroup, SWT.NONE).setText(Messages.get().General_UseAsIndex);

				radioIndexOID = new Button(snmpOptGroup, SWT.RADIO);
				radioIndexOID.setText(Messages.get().General_OIDSuffix);
				radioIndexOID.setSelection((objectTool.getFlags() & ObjectTool.SNMP_INDEXED_BY_VALUE) == 0);
				
				radioIndexValue = new Button(snmpOptGroup, SWT.RADIO);
				radioIndexValue.setText(Messages.get().General_FirstColumnValue);
				radioIndexValue.setSelection(!radioIndexOID.getSelection());

				break;
			case ObjectTool.TYPE_AGENT_LIST:
				textData.setLabel(Messages.get().General_Title);
				
				String[] listParts = objectTool.getData().split("\u007F"); //$NON-NLS-1$
				textData.setText((listParts.length > 0) ? listParts[0] : ""); //$NON-NLS-1$

				textParameter = new LabeledText(dialogArea, SWT.NONE);
				textParameter.setLabel(Messages.get().General_Parameter);
				gd = new GridData();
				gd.horizontalAlignment = SWT.FILL;
				gd.grabExcessHorizontalSpace = true;
				gd.horizontalSpan = 2;
				textParameter.setLayoutData(gd);
				textParameter.setText((listParts.length > 1) ? listParts[1] : ""); //$NON-NLS-1$

				textRegexp = new LabeledText(dialogArea, SWT.NONE);
				textRegexp.setLabel(Messages.get().General_RegExp);
				gd = new GridData();
				gd.horizontalAlignment = SWT.FILL;
				gd.grabExcessHorizontalSpace = true;
            gd.horizontalSpan = 2;
				textRegexp.setLayoutData(gd);
				textRegexp.setText((listParts.length > 2) ? listParts[2] : ""); //$NON-NLS-1$
				break;
         case ObjectTool.TYPE_AGENT_TABLE:
            textData.setLabel(Messages.get().General_Title);
            
            String[] tableParts = objectTool.getData().split("\u007F"); //$NON-NLS-1$
            textData.setText((tableParts.length > 0) ? tableParts[0] : ""); //$NON-NLS-1$

            textParameter = new LabeledText(dialogArea, SWT.NONE);
            textParameter.setLabel(Messages.get().General_Parameter);
            gd = new GridData();
            gd.horizontalAlignment = SWT.FILL;
            gd.grabExcessHorizontalSpace = true;
            gd.horizontalSpan = 2;
            textParameter.setLayoutData(gd);
            textParameter.setText((tableParts.length > 1) ? tableParts[1] : ""); //$NON-NLS-1$
            break;
		}

		Group confirmationGroup = new Group(dialogArea, SWT.NONE);
		confirmationGroup.setText(Messages.get().General_Confirmation);
		gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
      gd.horizontalSpan = 2;
		confirmationGroup.setLayoutData(gd);
		layout = new GridLayout();
		confirmationGroup.setLayout(layout);

		checkConfirmation = new Button(confirmationGroup, SWT.CHECK);
		checkConfirmation.setText(Messages.get().General_RequiresConfirmation);
		checkConfirmation.setSelection((objectTool.getFlags() & ObjectTool.ASK_CONFIRMATION) != 0);
      checkConfirmation.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				textConfirmation.setEnabled(checkConfirmation.getSelection());
				if (checkConfirmation.getSelection())
					textConfirmation.setFocus();
			}
		});

		textConfirmation = new LabeledText(confirmationGroup, SWT.NONE);
		textConfirmation.setLabel(Messages.get().General_ConfirmationMessage);
		gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
		textConfirmation.setLayoutData(gd);
		textConfirmation.setText(objectTool.getConfirmationText());
		textConfirmation.setEnabled(checkConfirmation.getSelection());

      Group commandGroup = new Group(dialogArea, SWT.NONE);
      commandGroup.setText(Messages.get().General_ShowInCommands);
      gd = new GridData();
      gd.horizontalAlignment = SWT.FILL;
      gd.grabExcessHorizontalSpace = true;
      gd.horizontalSpan = 2;
      commandGroup.setLayoutData(gd);
      layout = new GridLayout();
      layout.numColumns = 2;
      layout.makeColumnsEqualWidth = true;
      commandGroup.setLayout(layout);

      checkCommand = new Button(commandGroup, SWT.CHECK);
      checkCommand.setText(Messages.get().General_ShowInCommandsTooltip);
      checkCommand.setSelection((objectTool.getFlags() & ObjectTool.SHOW_IN_COMMANDS) != 0);
      checkCommand.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            textCommandName.setEnabled(checkCommand.getSelection());
            textCommandShortName.setEnabled(checkCommand.getSelection());
            if (checkCommand.getSelection())
               textCommandName.setFocus();
         }
      });
      gd = new GridData();
      gd.horizontalAlignment = SWT.FILL;
      gd.grabExcessHorizontalSpace = true;
      gd.horizontalSpan = 2;
      checkCommand.setLayoutData(gd);

      textCommandName = new LabeledText(commandGroup, SWT.NONE);
      textCommandName.setLabel(Messages.get().General_CommandName);
      gd = new GridData();
      gd.horizontalAlignment = SWT.FILL;
      gd.grabExcessHorizontalSpace = true;
      textCommandName.setLayoutData(gd);
      textCommandName.setText(objectTool.getCommandName());
      textCommandName.setEnabled(checkCommand.getSelection());
		
      textCommandShortName = new LabeledText(commandGroup, SWT.NONE);
      textCommandShortName.setLabel(Messages.get().General_CommandShortName);
      gd = new GridData();
      gd.horizontalAlignment = SWT.FILL;
      gd.grabExcessHorizontalSpace = true;
      textCommandShortName.setLayoutData(gd);
      textCommandShortName.setText(objectTool.getCommandShortName());
      textCommandShortName.setEnabled(checkCommand.getSelection());

      // Disable option
      checkDisable = new Button(dialogArea, SWT.CHECK);
      gd = new GridData();
      gd.horizontalSpan = 2;
      checkDisable.setLayoutData(gd);
		checkDisable.setText(Messages.get().General_DisableObjectToll);
		checkDisable.setSelection((objectTool.getFlags() & ObjectTool.DISABLED) > 0);

      // Run in container context
      if ((objectTool.getToolType() == ObjectTool.TYPE_LOCAL_COMMAND) || (objectTool.getToolType() == ObjectTool.TYPE_SERVER_COMMAND) || (objectTool.getToolType() == ObjectTool.TYPE_SERVER_SCRIPT) ||
            (objectTool.getToolType() == ObjectTool.TYPE_URL))
      {
         checkRunInContainerContext = new Button(dialogArea, SWT.CHECK);
         gd = new GridData();
         gd.horizontalSpan = 2;
         checkDisable.setLayoutData(gd);
         checkRunInContainerContext.setText(Messages.get().General_RunContainerContext);
         checkRunInContainerContext.setSelection(objectTool.isRunInContainerContext());
      }

		return dialogArea;
	}
	
	/**
	 * @param parent
	 */
	private void createOutputGroup(Composite parent)
	{
		Group outputGroup = new Group(parent, SWT.NONE);
		outputGroup.setText(Messages.get().General_ExecOptions);
		GridData gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
      gd.horizontalSpan = 2;
		outputGroup.setLayoutData(gd);
		outputGroup.setLayout(new GridLayout());

		checkShowOutput = new Button(outputGroup, SWT.CHECK);
		checkShowOutput.setText(Messages.get().General_GeneratesOutput);
		checkShowOutput.setSelection((objectTool.getFlags() & ObjectTool.GENERATES_OUTPUT) != 0);

      checkSuppressSuccessMessage = new Button(outputGroup, SWT.CHECK);
      checkSuppressSuccessMessage.setText("&Suppress notification of successful execution");
      checkSuppressSuccessMessage.setSelection((objectTool.getFlags() & ObjectTool.SUPPRESS_SUCCESS_MESSAGE) != 0);
	}
	
	/**
	 * Create icon
	 */
	@SuppressWarnings("deprecation")
   private void createIcon()
	{
	   if (icon != null)
	   {
	      icon.dispose();
	      icon = null;
	   }

      byte[] imageBytes = objectTool.getImageData();
      if ((imageBytes == null) || (imageBytes.length == 0))
         return;

      ByteArrayInputStream input = new ByteArrayInputStream(imageBytes);
      try
      {
         ImageDescriptor d = ImageDescriptor.createFromImageData(new ImageData(input));
         icon = d.createImage();
      }
      catch(Exception e)
      {
         Activator.logError("Exception in General.createIcon()", e); //$NON-NLS-1$
      }
	}
	
	/**
	 * @param parent
	 */
	private void createIconSelector(Composite parent)
	{
	   Group group = new Group(parent, SWT.NONE);
	   group.setText(Messages.get().General_Icon);
	   group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
	   
	   GridLayout layout = new GridLayout();
	   layout.numColumns = 4;
	   group.setLayout(layout);
	   
	   iconLabel = new Label(group, SWT.NONE);
      iconLabel.setImage((icon != null) ? icon : SharedIcons.IMG_EMPTY);
      iconLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true));
      
      Label dummy = new Label(group, SWT.NONE);
      GridData gd = new GridData();
      gd.widthHint = 8;
      dummy.setLayoutData(gd);
	   
      Button link = new Button(group, SWT.PUSH);
      link.setImage(SharedIcons.IMG_FIND);
      link.setToolTipText(Messages.get().General_Select);
      link.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            selectIcon();
         }
      });

      link = new Button(group, SWT.PUSH);
      link.setImage(SharedIcons.IMG_CLEAR);
      link.setToolTipText(Messages.get().General_Clear);
      link.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            iconLabel.setImage(SharedIcons.IMG_EMPTY);
            if (icon != null)
            {
               icon.dispose();
               icon = null;
            }
         }
      });
      link.addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            if (icon != null)
               icon.dispose();
         }
      });
	}
	
	/**
	 * 
	 */
	private void selectIcon()
	{
	   FileDialog dlg = new FileDialog(getShell(), SWT.OPEN);
	   dlg.setFilterExtensions(new String[] { "*.gif;*.jpg;*.png", "*.*" }); //$NON-NLS-1$ //$NON-NLS-2$
      dlg.setFilterNames(new String[] { Messages.get().General_ImageFiles, Messages.get().General_AllFiles });
      String fileName = dlg.open();
      if (fileName == null)
         return;
      
      try
      {
         Image image = new Image(getShell().getDisplay(), new FileInputStream(new File(fileName)));
         if ((image.getImageData().width <= 16) && (image.getImageData().height <= 16))
         {
            if (icon != null)
               icon.dispose();
            icon = image;
            iconLabel.setImage(icon);
         }
         else
         {
            image.dispose();
            MessageDialogHelper.openError(getShell(), Messages.get().General_Error, Messages.get().General_ImageTooLarge);
         }
      }
      catch(Exception e)
      {
         MessageDialogHelper.openError(getShell(), Messages.get().General_Error, String.format(Messages.get().General_CannotLoadImage, e.getLocalizedMessage()));
      }
	}

	/**
	 * Apply changes
	 * 
	 * @param isApply true if update operation caused by "Apply" button
	 */
	protected void applyChanges(final boolean isApply)
	{
      if (!isControlCreated())
      {
         return;
      }
	   
		objectTool.setName(textName.getText());
		objectTool.setDescription(textDescription.getText());

		// Tool data
		switch(objectTool.getToolType())
		{
		   case ObjectTool.TYPE_AGENT_LIST:
	         objectTool.setData(textData.getText() + "\u007F" + textParameter.getText() + "\u007F" + textRegexp.getText()); //$NON-NLS-1$ //$NON-NLS-2$
	         break;
         case ObjectTool.TYPE_AGENT_TABLE:
            objectTool.setData(textData.getText() + "\u007F" + textParameter.getText()); //$NON-NLS-1$
            break;
		   case ObjectTool.TYPE_FILE_DOWNLOAD:
            objectTool.setData(textData.getText() + "\u007F" + maxFileSize.getSelection() + "\u007F" + checkFollow.getSelection()); //$NON-NLS-1$ //$NON-NLS-2$
		      break;
		   default:
            objectTool.setData(textData.getText());
            break;
		}
		
		if (checkConfirmation.getSelection())
		{
			objectTool.setFlags(objectTool.getFlags() | ObjectTool.ASK_CONFIRMATION);
		}
		else
		{
			objectTool.setFlags(objectTool.getFlags() & ~ObjectTool.ASK_CONFIRMATION);
		}
		objectTool.setConfirmationText(textConfirmation.getText());
		
      if (checkCommand.getSelection())
      {
         objectTool.setFlags(objectTool.getFlags() | ObjectTool.SHOW_IN_COMMANDS);
      }
      else
      {
         objectTool.setFlags(objectTool.getFlags() & ~ObjectTool.SHOW_IN_COMMANDS);
      }
      objectTool.setCommandName(textCommandName.getText());
      objectTool.setCommandShortName(textCommandShortName.getText());
      
      if (checkDisable.getSelection())
      {
         objectTool.setFlags(objectTool.getFlags() | ObjectTool.DISABLED);
      }
      else
      {
         objectTool.setFlags(objectTool.getFlags() & ~ObjectTool.DISABLED);
      }
      
      if ((objectTool.getToolType() == ObjectTool.TYPE_LOCAL_COMMAND) ||
            (objectTool.getToolType() == ObjectTool.TYPE_SERVER_COMMAND) ||
            (objectTool.getToolType() == ObjectTool.TYPE_SERVER_SCRIPT) ||
            (objectTool.getToolType() == ObjectTool.TYPE_URL))
      {
         if (checkRunInContainerContext.getSelection())
         {
            objectTool.setFlags(objectTool.getFlags() | ObjectTool.RUN_IN_CONTAINER_CONTEXT);
         }
         else
         {
            objectTool.setFlags(objectTool.getFlags() & ~ObjectTool.RUN_IN_CONTAINER_CONTEXT);
         } 
      }

		if (objectTool.getToolType() == ObjectTool.TYPE_SNMP_TABLE)
		{
			if (radioIndexValue.getSelection())
			{
				objectTool.setFlags(objectTool.getFlags() | ObjectTool.SNMP_INDEXED_BY_VALUE);
			}
			else
			{
				objectTool.setFlags(objectTool.getFlags() & ~ObjectTool.SNMP_INDEXED_BY_VALUE);
			}
		}
		
		if ((objectTool.getToolType() == ObjectTool.TYPE_LOCAL_COMMAND) ||
		    (objectTool.getToolType() == ObjectTool.TYPE_SERVER_COMMAND) ||
          (objectTool.getToolType() == ObjectTool.TYPE_SERVER_SCRIPT) ||
		    (objectTool.getToolType() == ObjectTool.TYPE_ACTION) ||
		    (objectTool.getToolType() == ObjectTool.TYPE_SSH_COMMAND))
		{
			if (checkShowOutput.getSelection())
			{
				objectTool.setFlags(objectTool.getFlags() | ObjectTool.GENERATES_OUTPUT);
			}
			else
			{
				objectTool.setFlags(objectTool.getFlags() & ~ObjectTool.GENERATES_OUTPUT);
			}
		}

      if (checkSuppressSuccessMessage.getSelection())
      {
         objectTool.setFlags(objectTool.getFlags() | ObjectTool.SUPPRESS_SUCCESS_MESSAGE);
      }
      else
      {
         objectTool.setFlags(objectTool.getFlags() & ~ObjectTool.SUPPRESS_SUCCESS_MESSAGE);
      }
		
		if (icon != null)
		{
		   ImageLoader loader = new ImageLoader();
		   loader.data = new ImageData[] { icon.getImageData() };
		   ByteArrayOutputStream stream = new ByteArrayOutputStream(1024);
		   loader.save(stream, SWT.IMAGE_PNG);
		   objectTool.setImageData(stream.toByteArray());
		}
		else
		{
		   objectTool.setImageData(null);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.PreferencePage#performApply()
	 */
	@Override
	protected void performApply()
	{
		applyChanges(true);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.PreferencePage#performOk()
	 */
	@Override
	public boolean performOk()
	{
		applyChanges(false);
		return true;
	}
}
