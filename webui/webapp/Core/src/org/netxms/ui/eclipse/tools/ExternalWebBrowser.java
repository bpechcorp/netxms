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
package org.netxms.ui.eclipse.tools;

import java.net.URL;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.rap.rwt.client.service.UrlLauncher;

/**
 * Helper class to interact with external web browser
 */
public class ExternalWebBrowser
{
   /**
    * Open given URL in external web browser
    *
    * @param url URL to open
    */
   public static void open(URL url)
   {
      open(url.toString());
   }

   /**
    * Open given URL in external web browser
    *
    * @param url URL to open
    */
   public static void open(String url)
   {
      UrlLauncher launcher = RWT.getClient().getService(UrlLauncher.class);
      launcher.openURL(url);
   }
}
