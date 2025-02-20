/*
** NetXMS - Network Management System
** Client Library
** Copyright (C) 2003-2021 Victor Kirhenshtein
**
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU Lesser General Public License as published by
** the Free Software Foundation; either version 3 of the License, or
** (at your option) any later version.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public License
** along with this program; if not, write to the Free Software
** Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
**
** File: session.cpp
**
**/

#include "libnxclient.h"
#include <netxms-version.h>

/**
 * Max NXCP message size
 */
#define MAX_MSG_SIZE    4194304

/**
 * Default controller destructor
 */
Controller::~Controller()
{
}

/**
 * Default controller message handler
 */
bool Controller::handleMessage(NXCPMessage *msg)
{
   return false;
}

/**
 * Constructor
 */
NXCSession::NXCSession() : m_dataLock(MutexType::FAST), m_msgSendLock(MutexType::FAST)
{
   m_controllers = new StringObjectMap<Controller>(Ownership::True);
   m_msgId = 0;
   m_connected = false;
   m_disconnected = false;
   m_hSocket = INVALID_SOCKET;
   m_msgWaitQueue = nullptr;
   m_receiverThread = INVALID_THREAD_HANDLE;
   m_serverVersion[0] = 0;
   m_serverTimeZone[0] = 0;
   m_userId = 0;
   m_systemRights = 0;
   m_commandTimeout = 60000;  // 60 seconds
   m_protocolVersions = new IntegerArray<UINT32>(8, 8);
   m_passwordChangeNeeded = false;
	m_compressionEnabled = false;
	m_receiver = nullptr;
}

/**
 * Destructor
 */
NXCSession::~NXCSession()
{
   disconnect();
   delete m_controllers;
   delete m_protocolVersions;
}

/**
 * Get controller
 */
Controller *NXCSession::getController(const TCHAR *name)
{
   m_dataLock.lock();
   Controller *c = m_controllers->get(name);
   if (c == NULL)
   {
      if (!_tcsicmp(name, CONTROLLER_ALARMS))
         c = new AlarmController(this);
      else if (!_tcsicmp(name, CONTROLLER_DATA_COLLECTION))
         c = new DataCollectionController(this);
      else if (!_tcsicmp(name, CONTROLLER_EVENTS))
         c = new EventController(this);
      else if (!_tcsicmp(name, CONTROLLER_OBJECTS))
         c = new ObjectController(this);
      else if (!_tcsicmp(name, CONTROLLER_SERVER))
         c = new ServerController(this);

      if (c != NULL)
         m_controllers->set(name, c);
   }
   m_dataLock.unlock();
   return c;
}

/**
 * Disconnect
 */
void NXCSession::disconnect()
{
   if (!m_connected || m_disconnected)
      return;

   // Cancel receiver
   if (m_receiver != NULL)
      m_receiver->cancel();

   if (m_receiverThread != INVALID_THREAD_HANDLE)
      ThreadJoin(m_receiverThread);

   if (m_hSocket != INVALID_SOCKET)
   {
      shutdown(m_hSocket, SHUT_RDWR);
      closesocket(m_hSocket);
   }

   // Clear message wait queue
   delete m_msgWaitQueue;

   m_connected = false;
   m_disconnected = true;
}

/**
 * Connect to server
 */
UINT32 NXCSession::connect(const TCHAR *host, const TCHAR *login, const TCHAR *password, UINT32 flags, 
                           const TCHAR *clientInfo, const UINT32 *cpvIndexList, size_t cpvIndexListSize)
{
   if (m_connected || m_disconnected)
      return RCC_OUT_OF_STATE_REQUEST;

   TCHAR hostname[128];
   _tcslcpy(hostname, host, 128);
   Trim(hostname);

   // Check if server given in form host:port
   // If IPv6 address is given, it must be enclosed in [] if port is also specified
   UINT16 port = SERVER_LISTEN_PORT_FOR_CLIENTS;
   TCHAR *p = _tcsrchr(hostname, _T(':'));
   if ((p != NULL) && (p != hostname) &&
       ((hostname[0] != _T('[')) || (NumChars(hostname, _T(':') == 1)) || (*(p - 1) == _T(']'))))
   {
      *p = 0;
      p++;
      TCHAR *eptr;
      int n = _tcstol(p, &eptr, 10);
      if ((*eptr != 0) || (n < 1) || (n > 65535))
         return RCC_INVALID_ARGUMENT;
      port = (UINT16)n;
   }
   DebugPrintf(_T("NXCSession::connect: host=\"%s\" port=%d"), hostname, (int)port);

   InetAddress addr = InetAddress::resolveHostName(hostname);
   if (!addr.isValid())
      return RCC_COMM_FAILURE;

   struct sockaddr *sa;
   int saLen;

   struct sockaddr_in sa4;
#ifdef WITH_IPV6
   struct sockaddr_in6 sa6;
#endif
   if (addr.getFamily() == AF_INET)
   {
      saLen = sizeof(sa4);
      memset(&sa4, 0, saLen);
      sa = (struct sockaddr *)&sa4;
      sa4.sin_family = AF_INET;
      sa4.sin_addr.s_addr = htonl(addr.getAddressV4());
      sa4.sin_port = htons(port);
   }
   else
   {
#ifdef WITH_IPV6
      saLen = sizeof(sa6);
      memset(&sa6, 0, saLen);
      sa = (struct sockaddr *)&sa6;
      sa6.sin6_family = AF_INET6;
      memcpy(&sa6.sin6_addr, addr.getAddressV6(), 16);
      sa6.sin6_port = ntohs(port);
#else
      return RCC_NOT_IMPLEMENTED;
#endif
   }

   m_hSocket = CreateSocket(addr.getFamily(), SOCK_STREAM, 0);
   if (m_hSocket == INVALID_SOCKET)
      return RCC_COMM_FAILURE;

   if (::connect(m_hSocket, sa, saLen) != 0)
   {
      closesocket(m_hSocket);
      m_hSocket = INVALID_SOCKET;
      return RCC_COMM_FAILURE;
   }

   m_connected = true;
   m_msgWaitQueue = new MsgWaitQueue();
   m_receiverThread = ThreadCreateEx(this, &NXCSession::receiverThread);
   
   uint32_t rcc = RCC_COMM_FAILURE;

   // Query server information
   NXCPMessage msg;
   msg.setId(createMessageId());
   msg.setCode(CMD_GET_SERVER_INFO);
   if (sendMessage(&msg))
   {
      NXCPMessage *response = waitForMessage(CMD_REQUEST_COMPLETED, msg.getId());
      if (response != nullptr)
      {
         rcc = response->getFieldAsUInt32(VID_RCC);
         if (rcc == RCC_SUCCESS)
         {
            response->getFieldAsBinary(VID_SERVER_ID, m_serverId, 8);
            response->getFieldAsString(VID_SERVER_VERSION, m_serverVersion, 64);
            response->getFieldAsString(VID_TIMEZONE, m_serverTimeZone, MAX_TZ_LEN);

            if (!(flags & NXCF_IGNORE_PROTOCOL_VERSION))
            {
               if (response->getFieldAsUInt32(VID_PROTOCOL_VERSION) != CLIENT_PROTOCOL_VERSION_BASE)
                  rcc = RCC_BAD_PROTOCOL;
            }
            if ((rcc == RCC_SUCCESS) && (flags & NXCF_EXACT_VERSION_MATCH))
            {
               if (_tcsncmp(m_serverVersion, NETXMS_VERSION_STRING, 64))
                  rcc = RCC_VERSION_MISMATCH;
            }
            if (rcc == RCC_SUCCESS)
            {
               response->getFieldAsInt32Array(VID_PROTOCOL_VERSION_EX, m_protocolVersions);
               if (cpvIndexList != nullptr)
               {
                  static UINT32 currentProtocolVersions[] = {
                     CLIENT_PROTOCOL_VERSION_BASE,
                     CLIENT_PROTOCOL_VERSION_ALARMS,
                     CLIENT_PROTOCOL_VERSION_PUSH,
                     CLIENT_PROTOCOL_VERSION_TRAP,
                     CLIENT_PROTOCOL_VERSION_MOBILE,
                     CLIENT_PROTOCOL_VERSION_FULL
                  };

                  for(int i = 0; i < (int)cpvIndexListSize; i++)
                  {
                     int idx = cpvIndexList[i];
                     if ((idx >= sizeof(currentProtocolVersions) / sizeof(UINT32)) || (m_protocolVersions->get(idx) != currentProtocolVersions[idx]))
                     {
                        rcc = RCC_BAD_PROTOCOL;
                        break;
                     }
                  }
               }
            }
         }
         delete response;
      }
      else
      {
         rcc = RCC_TIMEOUT;
      }
   }

   // Request encryption if needed
   if ((rcc == RCC_SUCCESS) && (flags & NXCF_ENCRYPT))
   {
      msg.deleteAllFields();
      msg.setId(createMessageId());
      msg.setCode(CMD_REQUEST_ENCRYPTION);
      if (sendMessage(&msg))
      {
         rcc = waitForRCC(msg.getId());
      }
      else
      {
         rcc = RCC_COMM_FAILURE;
      }
   }

   if (rcc == RCC_SUCCESS)
   {
      msg.deleteAllFields();
      msg.setId(createMessageId());
      msg.setCode(CMD_LOGIN);
      msg.setField(VID_LOGIN_NAME, login);
	   if (flags & NXCF_USE_CERTIFICATE)
	   {
         /* TODO: implement certificate auth */
			msg.setField(VID_AUTH_TYPE, (UINT16)NETXMS_AUTH_TYPE_CERTIFICATE);
	   }
	   else
	   {
		   msg.setField(VID_PASSWORD, password);
		   msg.setField(VID_AUTH_TYPE, (UINT16)NETXMS_AUTH_TYPE_PASSWORD);
	   }
      msg.setField(VID_CLIENT_INFO, (clientInfo != NULL) ? clientInfo : _T("Unnamed Client"));
      msg.setField(VID_LIBNXCL_VERSION, NETXMS_VERSION_STRING);
      msg.setField(VID_ENABLE_COMPRESSION, true);

      TCHAR buffer[64];
      GetOSVersionString(buffer, 64);
      msg.setField(VID_OS_INFO, buffer);

      if (sendMessage(&msg))
      {
         NXCPMessage *response = waitForMessage(CMD_REQUEST_COMPLETED, msg.getId());
         if (response != nullptr)
         {
            rcc = response->getFieldAsUInt32(VID_RCC);
            if (rcc == RCC_SUCCESS)
            {
               m_userId = response->getFieldAsUInt32(VID_USER_ID);
               m_systemRights = response->getFieldAsUInt64(VID_USER_SYS_RIGHTS);
               m_passwordChangeNeeded = response->getFieldAsBoolean(VID_CHANGE_PASSWD_FLAG);
               m_compressionEnabled = response->getFieldAsBoolean(VID_ENABLE_COMPRESSION);
            }
            delete response;
         }
         else
         {
            rcc = RCC_TIMEOUT;
         }
      }
      else
      {
         rcc = RCC_COMM_FAILURE;
      }
   }

   m_connected = true;
   if (rcc != RCC_SUCCESS)
      disconnect();

   return rcc;
}

/**
 * Send message
 */
bool NXCSession::sendMessage(NXCPMessage *msg)
{
   if (!m_connected)
      return false;

   TCHAR buffer[128];
   DebugPrintf(_T("NXCSession::sendMessage(\"%s\", id:%d)"), NXCPMessageCodeName(msg->getCode(), buffer), msg->getId());

   bool result;
   NXCP_MESSAGE *rawMsg = msg->serialize(m_compressionEnabled);
	m_msgSendLock.lock();
   if (m_encryptionContext != nullptr)
   {
      NXCP_ENCRYPTED_MESSAGE *emsg = m_encryptionContext->encryptMessage(rawMsg);
      if (emsg != nullptr)
      {
         result = (SendEx(m_hSocket, (char *)emsg, ntohl(emsg->size), 0, nullptr) == (int)ntohl(emsg->size));
         MemFree(emsg);
      }
      else
      {
         result = false;
      }
   }
   else
   {
      result = (SendEx(m_hSocket, (char *)rawMsg, ntohl(rawMsg->size), 0, nullptr) == (int)ntohl(rawMsg->size));
   }
	m_msgSendLock.unlock();
   MemFree(rawMsg);
   return result;
}

/**
 * Wait for message
 */
NXCPMessage *NXCSession::waitForMessage(uint16_t code, uint32_t id, uint32_t timeout)
{
   if (!m_connected)
      return nullptr;

   return m_msgWaitQueue->waitForMessage(code, id, (timeout == 0) ? m_commandTimeout : timeout);
}

/**
 * Wait for CMD_REQUEST_COMPLETED message and return RCC
 */
uint32_t NXCSession::waitForRCC(uint32_t id, uint32_t timeout)
{
   NXCPMessage *response = waitForMessage(CMD_REQUEST_COMPLETED, id, timeout);
   if (response == nullptr)
      return RCC_TIMEOUT;

   uint32_t rcc = response->getFieldAsUInt32(VID_RCC);
   delete response;
   return rcc;
}

/**
 * Receiver thread
 */
void NXCSession::receiverThread()
{
   m_receiver = new SocketMessageReceiver(m_hSocket, 4096, MAX_MSG_SIZE);
   while(true)
   {
      MessageReceiverResult result;
      NXCPMessage *msg = m_receiver->readMessage(900000, &result);

      // Check for decryption error
      if (result == MSGRECV_DECRYPTION_FAILURE)
      {
         DebugPrintf(_T("NXCSession::receiverThread: Unable to decrypt received message"));
         continue;
      }

      // Receive error
      if (msg == NULL)
      {
         if (result != MSGRECV_CLOSED)
            DebugPrintf(_T("NXCSession::receiverThread: message receiving error (%s)"), AbstractMessageReceiver::resultToText(result));
         break;
      }

      TCHAR buffer[128];
      DebugPrintf(_T("NXCSession::receiveMessage(\"%s\", id:%d)"), NXCPMessageCodeName(msg->getCode(), buffer), msg->getId());

      switch(msg->getCode())
      {
         case CMD_REQUEST_SESSION_KEY:
            if (m_encryptionContext == nullptr)
            {
               NXCPMessage *response;
               NXCPEncryptionContext *encryptionContext = nullptr;
               m_dataLock.lock();
               SetupEncryptionContext(msg, &encryptionContext, &response, nullptr, NXCP_VERSION);
               m_encryptionContext = shared_ptr<NXCPEncryptionContext>(encryptionContext);
               m_receiver->setEncryptionContext(m_encryptionContext);
               m_dataLock.unlock();
               sendMessage(response);
               delete response;
            }
            break;
         case CMD_NOTIFY:
            onNotify(msg);
            break;
         default:
            if (!handleMessage(msg))
            {
               m_msgWaitQueue->put(msg);
               msg = nullptr;    // prevent destruction
            }
            break;
      }
      delete msg;
   }
   delete_and_null(m_receiver);
}

/**
 * Message handler callback
 */
static EnumerationCallbackResult HandleMessageCallback(const TCHAR *key, const void *value, void *data)
{
   return ((Controller *)value)->handleMessage((NXCPMessage *)data) ? _STOP : _CONTINUE;
}

/**
 * Handle incoming message on controller
 */
bool NXCSession::handleMessage(NXCPMessage *msg)
{
   m_dataLock.lock();
   EnumerationCallbackResult result = m_controllers->forEach(HandleMessageCallback, msg);
   m_dataLock.unlock();
   return result == _STOP;
}

/**
 * Notification message handler
 */
void NXCSession::onNotify(NXCPMessage *msg)
{
}
