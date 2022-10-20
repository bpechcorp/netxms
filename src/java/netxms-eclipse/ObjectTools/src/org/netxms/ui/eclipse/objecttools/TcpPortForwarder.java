/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2022 Raden Solutions
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
package org.netxms.ui.eclipse.objecttools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.console.IOConsoleOutputStream;
import org.netxms.client.NXCSession;
import org.netxms.client.TcpProxy;
import org.netxms.ui.eclipse.tools.MessageDialogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP port forwarder
 */
public class TcpPortForwarder
{
   private static final Logger logger = LoggerFactory.getLogger(TcpPortForwarder.class);

   private Display display = null;
   private Shell parentShell = null;
   private NXCSession session;
   private long nodeId;
   private int remotePort;
   private ServerSocket listener;
   private int sessionId = 0;
   private Map<Integer, Session> sessions = new HashMap<>();
   private IOConsoleOutputStream consoleOutputStream = null;

   /**
    * Create new port forwarder instance.
    *
    * @param session client session
    * @param nodeId target node ID
    * @param remotePort port number on target node
    * @param listenerTimeout listener timeout (0 for infinite)
    * @throws IOException if cannot setup local TCP port listener
    */
   public TcpPortForwarder(NXCSession session, long nodeId, int remotePort, int listenerTimeout) throws IOException
   {
      this.session = session;
      this.nodeId = nodeId;
      this.remotePort = remotePort;
      listener = new ServerSocket(0);
      listener.setSoTimeout(listenerTimeout);
   }

   /**
    * Run port forwarder (will start background thread accepting connection on local port).
    *
    * @throws Exception on any error
    */
   public void run() throws Exception
   {
      final Object mutex = new Object();
      Thread thread = new Thread(new Runnable() {
         @Override
         public void run()
         {
            logger.info("TCP port forwarder listening on port " + listener.getLocalPort());
            synchronized(mutex)
            {
               mutex.notifyAll();
            }
            try
            {
               while(true)
               {
                  final Socket socket = listener.accept();
                  try
                  {
                     final TcpProxy proxy = session.setupTcpProxy(nodeId, remotePort);
                     Session session = new Session(++sessionId, socket, proxy);
                     synchronized(sessions)
                     {
                        sessions.put(session.getId(), session);
                     }
                  }
                  catch(Exception e)
                  {
                     logger.error("TCP port forwarder session setup error", e);

                     String emsg = e.getLocalizedMessage();
                     String msg = String.format("TCP port forwarder session setup error (%s)", (emsg != null) && !emsg.isEmpty() ? emsg : e.getClass().getCanonicalName());

                     if (consoleOutputStream != null)
                     {
                        consoleOutputStream.write("\n*** " + msg + " ***\n");
                     }
                     else if (display != null)
                     {
                        display.asyncExec(() -> {
                           MessageDialogHelper.openError(parentShell, "TCP Port Forwarding Error", msg);
                        });
                     }

                     socket.close();
                  }
               }
            }
            catch(Exception e)
            {
               logger.error("TCP port forwarder listener loop error", e);
            }
            finally
            {
               try
               {
                  listener.close();
               }
               catch(IOException e)
               {
               }
            }
         }
      }, "TcpForwarder");
      thread.setDaemon(true);
      synchronized(mutex)
      {
         thread.start();
         mutex.wait(); // wait for listener thread start
         Thread.sleep(100); // Additional wait to ensure that accept() is called on listening socket
      }
   }

   /**
    * Close port forwarder. Will also close all underlying TCP proxy objects.
    */
   public void close()
   {
      logger.debug("Closing TCP forwarder instance on port " + listener.getLocalPort());
      try
      {
         listener.close();
      }
      catch(Exception e)
      {
         logger.debug("Error closing listening socket", e);
      }

      synchronized(sessions)
      {
         for(Session s : sessions.values())
            s.close();
         sessions.clear();
      }
   }

   /**
    * Get local port number.
    *
    * @return local port number
    */
   public int getLocalPort()
   {
      return listener.getLocalPort();
   }

   /**
    * @return the consoleOutputStream
    */
   public IOConsoleOutputStream getConsoleOutputStream()
   {
      return consoleOutputStream;
   }

   /**
    * @param consoleOutputStream the consoleOutputStream to set
    */
   public void setConsoleOutputStream(IOConsoleOutputStream consoleOutputStream)
   {
      this.consoleOutputStream = consoleOutputStream;
   }

   /**
    * @return the display
    */
   public Display getDisplay()
   {
      return display;
   }

   /**
    * @param display the display to set
    */
   public void setDisplay(Display display)
   {
      this.display = display;
   }

   /**
    * @return the parentShell
    */
   public Shell getParentShell()
   {
      return parentShell;
   }

   /**
    * @param parentShell the parentShell to set
    */
   public void setParentShell(Shell parentShell)
   {
      this.parentShell = parentShell;
   }

   /**
    * Port forwarding session
    */
   private class Session
   {
      private int id;
      private Socket socket;
      private TcpProxy proxy;
      private Thread socketReaderThread;
      private Thread proxyReaderThread;

      /**
       * Create new session.
       *
       * @param id session ID
       * @param socket local socket
       * @param proxy TCP proxy session
       */
      public Session(int id, Socket socket, TcpProxy proxy)
      {
         this.id = id;
         this.socket = socket;
         this.proxy = proxy;

         socketReaderThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
               logger.info("Socket reader started");
               socketReader();
            }
         }, "Session-" + id + "-Socket");
         socketReaderThread.setDaemon(true);

         proxyReaderThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
               logger.info("Proxy reader started");
               proxyReader();
            }
         }, "Session-" + id + "-Proxy");
         proxyReaderThread.setDaemon(true);

         socketReaderThread.start();
         proxyReaderThread.start();
      }

      /**
       * Close session
       */
      public void close()
      {
         try
         {
            socket.shutdownInput();
         }
         catch(IOException e)
         {
         }

         try
         {
            socket.shutdownOutput();
         }
         catch(IOException e)
         {
         }
      }

      /**
       * @return the id
       */
      public int getId()
      {
         return id;
      }

      /**
       * Local socket reader thread
       */
      private void socketReader()
      {
         try
         {
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[32768];
            while(true)
            {
               int bytes = in.read(buffer);
               if (bytes <= 0)
               {
                  logger.info("Exit code " + bytes + " while reading socket input stream");
                  break;
               }
               proxy.getOutputStream().write(buffer, 0, bytes);
            }
         }
         catch(Exception e)
         {
            logger.error("Socket reader exception", e);
         }

         proxy.close();

         logger.info("Waiting for proxy reader to stop");
         try
         {
            proxyReaderThread.join();
         }
         catch(InterruptedException e)
         {
            logger.error("Thread join exception", e);
         }

         try
         {
            socket.close();
         }
         catch(IOException e)
         {
         }
         logger.info("Socket reader terminated");

         synchronized(sessions)
         {
            sessions.remove(id);
         }

         socket = null;
         proxy = null;
      }

      /**
       * Proxy reader thread
       */
      private void proxyReader()
      {
         try
         {
            InputStream in = proxy.getInputStream();
            OutputStream out = socket.getOutputStream();
            byte[] buffer = new byte[32768];
            while(true)
            {
               int bytes = in.read(buffer);
               if (bytes <= 0)
               {
                  logger.info("Exit code " + bytes + " while reading proxy input stream");
                  break;
               }
               out.write(buffer, 0, bytes);
            }
         }
         catch(Exception e)
         {
            logger.error("Proxy reader exception", e);
         }

         logger.info("Proxy reader requesting local socket closure");
         try
         {
            socket.close();
         }
         catch(IOException e)
         {
         }
         logger.info("Proxy reader terminated");
      }
   }
}
