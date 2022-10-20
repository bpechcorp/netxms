/*
** NetXMS - Network Management System
** Copyright (C) 2003-2022 Raden Solutions
**
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation; either version 2 of the License, or
** (at your option) any later version.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with this program; if not, write to the Free Software
** Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
**
** File: objtools.cpp
**
**/

#include "nxcore.h"
#include <netxms-regex.h>
#include <nxtools.h>

/**
 * Object tool acl entry
 */
struct OBJECT_TOOL_ACL
{
   uint32_t toolId;
   uint32_t userId;
};

/**
 * Tool startup info
 */
struct ToolStartupInfo
{
   uint32_t toolId;
   uint32_t requestId;
   uint32_t flags;
   shared_ptr<Node> node;
   ClientSession *session;
   TCHAR *toolData;

   ~ToolStartupInfo()
   {
      session->decRefCount();
      MemFree(toolData);
   }
};

/**
 * SNMP table enumerator arguments
 */
struct SNMP_ENUM_ARGS
{
   uint32_t dwNumCols;
   TCHAR **ppszOidList;
   int32_t *pnFormatList;
   uint32_t dwFlags;
   shared_ptr<Node> pNode;
	Table *table;
};

/**
 * Rollback all querys, release BD connection, free prepared statement if not nullptr and return RCC_DB_FAILURE code
 */
static uint32_t ReturnDBFailure(DB_HANDLE hdb, DB_STATEMENT hStmt)
{
   DBRollback(hdb);
   if (hStmt != nullptr)
      DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);
   return RCC_DB_FAILURE;
}

/**
 * Check if tool with given id exist and is a table tool
 */
bool IsTableTool(uint32_t toolId)
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT tool_type FROM object_tools WHERE tool_id=?"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return false;
   }

   bool bResult = false;

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult != nullptr)
   {
      if (DBGetNumRows(hResult) > 0)
      {
         int type = DBGetFieldLong(hResult, 0, 0);
         bResult = ((type == TOOL_TYPE_SNMP_TABLE) || (type == TOOL_TYPE_AGENT_TABLE) || (type == TOOL_TYPE_AGENT_LIST));
      }
      DBFreeResult(hResult);
   }
   DBConnectionPoolReleaseConnection(hdb);
   DBFreeStatement(hStmt);
   return bResult;
}

/**
 * Check if user has access to the tool
 */
bool CheckObjectToolAccess(uint32_t toolId, uint32_t userId)
{
   if (userId == 0)
      return true;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT user_id FROM object_tools_acl WHERE tool_id=?"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return false;
   }

   bool result = false;

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult != nullptr)
   {
		int count = DBGetNumRows(hResult);
      for(int i = 0; i < count; i++)
      {
			uint32_t id = DBGetFieldULong(hResult, i, 0);
			if ((id == userId) || (id == GROUP_EVERYONE))
			{
				result = true;
				break;
			}
			if (id & GROUP_FLAG)
			{
				if (CheckUserMembership(userId, id))
				{
					result = true;
					break;
				}
			}
      }
      DBFreeResult(hResult);
   }
   DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);
   return result;
}

/**
 * Agent table tool execution thread
 */
static void GetAgentTable(ToolStartupInfo *toolData)
{
   NXCPMessage msg(CMD_TABLE_DATA, toolData->requestId);

   TCHAR *tableName = _tcschr(toolData->toolData, _T('\x7F'));
   if (tableName != nullptr)
   {
      *tableName = 0;
      tableName++;
      shared_ptr<AgentConnectionEx> pConn = toolData->node->createAgentConnection();
      if (pConn != nullptr)
      {
         Table *table;
         UINT32 err = pConn->getTable(tableName, &table);
         if (err == ERR_SUCCESS)
         {
            // Convert data types returned by agent into table tool codes
            for(int i = 0; i < table->getNumColumns(); i++)
            {
               switch(table->getColumnDefinition(i)->getDataType())
               {
                  case DCI_DT_INT:
                  case DCI_DT_UINT:
                  case DCI_DT_INT64:
                  case DCI_DT_UINT64:
                  case DCI_DT_COUNTER32:
                  case DCI_DT_COUNTER64:
                     table->setColumnDataType(i, CFMT_INTEGER);
                     break;
                  case DCI_DT_FLOAT:
                     table->setColumnDataType(i, CFMT_FLOAT);
                     break;
                  default:
                     table->setColumnDataType(i, CFMT_STRING);
                     break;
               }
            }

            msg.setField(VID_RCC, RCC_SUCCESS);
            table->setTitle(toolData->toolData);
            table->fillMessage(&msg, 0, -1);
            delete table;
         }
         else
         {
            msg.setField(VID_RCC, AgentErrorToRCC(err));
         }
      }
      else
      {
         msg.setField(VID_RCC, RCC_COMM_FAILURE);
      }
   }
   else
   {
      msg.setField(VID_RCC, RCC_INVALID_ARGUMENT);
   }

   // Send response to client
   toolData->session->sendMessage(msg);
   delete toolData;
}

/**
 * Agent list tool execution thread
 */
static void GetAgentList(ToolStartupInfo *toolData)
{
   NXCPMessage msg(CMD_TABLE_DATA, toolData->requestId);

   TCHAR *pszRegEx, buffer[256];
	Table table;

   // Parse tool data. For agent table, it should have the following format:
   // table_title<separator>enum<separator>matching_regexp
   // where <separator> is a character with code 0x7F
   TCHAR *pszEnum = _tcschr(toolData->toolData, _T('\x7F'));
   if (pszEnum != nullptr)
   {
      *pszEnum = 0;
      pszEnum++;
      pszRegEx = _tcschr(pszEnum, _T('\x7F'));
      if (pszRegEx != nullptr)
      {
         *pszRegEx = 0;
         pszRegEx++;
      }
   }
	table.setTitle(toolData->toolData);

   if ((pszEnum != nullptr) && (pszRegEx != nullptr))
   {
      // Load column information
      DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
      DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT col_name,col_format,col_substr FROM object_tools_table_columns WHERE tool_id=? ORDER BY col_number"));
      if (hStmt != nullptr)
      {
         DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolData->toolId);

         DB_RESULT hResult = DBSelectPrepared(hStmt);
         if (hResult != nullptr)
         {
            int numCols = DBGetNumRows(hResult);
            if (numCols > 0)
            {
               int *pnSubstrPos = MemAllocArray<int>(numCols);
               for(int i = 0; i < numCols; i++)
               {
                  DBGetField(hResult, i, 0, buffer, 256);
                  table.addColumn(buffer, DBGetFieldULong(hResult, i, 1));
                  pnSubstrPos[i] = DBGetFieldLong(hResult, i, 2);
               }

               const char *eptr;
               int eoffset;
               PCRE *preg = _pcre_compile_t(reinterpret_cast<const PCRE_TCHAR*>(pszRegEx), PCRE_COMMON_FLAGS | PCRE_CASELESS, &eptr, &eoffset, nullptr);
               if (preg != nullptr)
               {
                  shared_ptr<AgentConnectionEx> pConn = toolData->node->createAgentConnection();
                  if (pConn != nullptr)
                  {
                     StringList *values;
                     UINT32 dwResult = pConn->getList(pszEnum, &values);
                     if (dwResult == ERR_SUCCESS)
                     {
                        int *pMatchList = MemAllocArray<int>((numCols + 1) * 3);
                        for(int i = 0; i < values->size(); i++)
                        {
                           const TCHAR *line = values->get(i);
                           if (_pcre_exec_t(preg, nullptr, reinterpret_cast<const PCRE_TCHAR*>(line), static_cast<int>(_tcslen(line)), 0, 0, pMatchList, (numCols + 1) * 3) >= 0)
                           {
                              table.addRow();

                              // Write data for current row into message
                              for(int j = 0; j < numCols; j++)
                              {
                                 int pos = pnSubstrPos[j];
                                 if (pMatchList[pos * 2] != -1)
                                 {
                                    size_t len = pMatchList[pos * 2 + 1] - pMatchList[pos * 2];
                                    memcpy(buffer, &line[pMatchList[pos * 2]], len * sizeof(TCHAR));
                                    buffer[len] = 0;
                                 }
                                 else
                                 {
                                    buffer[0] = 0;
                                 }
                                 table.set(j, buffer);
                              }
                           }
                        }
                        MemFree(pMatchList);
                        delete values;

                        msg.setField(VID_RCC, RCC_SUCCESS);
                        table.fillMessage(&msg, 0, -1);
                     }
                     else
                     {
                        msg.setField(VID_RCC, AgentErrorToRCC(dwResult));
                     }
                  }
                  else
                  {
                     msg.setField(VID_RCC, RCC_COMM_FAILURE);
                  }
                  _pcre_free_t(preg);
               }
               else     // Regexp compilation failed
               {
                  msg.setField(VID_RCC, RCC_BAD_REGEXP);
               }
               MemFree(pnSubstrPos);
            }
            else  // No columns defined
            {
               msg.setField(VID_RCC, RCC_INTERNAL_ERROR);
            }
            DBFreeResult(hResult);
         }
         else     // Cannot load column info from DB
         {
            msg.setField(VID_RCC, RCC_DB_FAILURE);
         }
         DBFreeStatement(hStmt);
      }
      else
      {
         msg.setField(VID_RCC, RCC_DB_FAILURE);
      }
      DBConnectionPoolReleaseConnection(hdb);
  }
   else
   {
      msg.setField(VID_RCC, RCC_INVALID_ARGUMENT);
   }

   // Send response to client
   toolData->session->sendMessage(msg);
   delete toolData;
}

/**
 * Add SNMP variable value to results list
 */
static void AddSNMPResult(Table *table, int column, SNMP_Variable *pVar, LONG nFmt, const Node& pNode)
{
   TCHAR buffer[4096];
   if (pVar != nullptr)
   {
      switch(nFmt)
      {
         case CFMT_MAC_ADDR:
            pVar->getValueAsMACAddr().toString(buffer);
            break;
         case CFMT_IP_ADDR:
            pVar->getValueAsIPAddr(buffer);
            break;
         case CFMT_IFINDEX:   // Column is an interface index, convert to interface name
         {
            uint32_t dwIndex = pVar->getValueAsUInt();
            shared_ptr<Interface> pInterface = pNode.findInterfaceByIndex(dwIndex);
            if (pInterface != nullptr)
            {
               _tcslcpy(buffer, pInterface->getName(), 4096);
            }
            else
            {
               if (dwIndex == 0)    // Many devices uses index 0 for internal MAC, etc.
                  _tcscpy(buffer, _T("INTERNAL"));
               else
                  _sntprintf(buffer, 64, _T("%d"), dwIndex);
            }
            break;
         }
         default:
         {
				bool convert = true;
            pVar->getValueAsPrintableString(buffer, 4096, &convert);
            break;
         }
      }
   }
   else
   {
      buffer[0] = 0;
   }
	table->set(column, buffer);
}

/**
 * Handler for SNMP table enumeration
 */
static uint32_t TableHandler(SNMP_Variable *pVar, SNMP_Transport *pTransport, SNMP_ENUM_ARGS *args)
{
   TCHAR szOid[MAX_OID_LEN * 4], szSuffix[MAX_OID_LEN * 4];
   uint32_t result, varbindName[MAX_OID_LEN];
   size_t nameLen;

   // Create index (OID suffix) for columns
   if (args->dwFlags & TF_SNMP_INDEXED_BY_VALUE)
   {
      _sntprintf(szSuffix, MAX_OID_LEN * 4, _T(".%u"), pVar->getValueAsUInt());
   }
   else
   {
      nameLen = SNMPParseOID(args->ppszOidList[0], varbindName, MAX_OID_LEN);
      const SNMP_ObjectId& oid = pVar->getName();
      SNMPConvertOIDToText(oid.length() - nameLen, (UINT32 *)&(oid.value())[nameLen], szSuffix, MAX_OID_LEN * 4);
   }

   // Get values for other columns
   if (args->dwNumCols > 1)
   {
      SNMP_PDU request(SNMP_GET_REQUEST, SnmpNewRequestId(), pTransport->getSnmpVersion());
      for(uint32_t i = 1; i < args->dwNumCols; i++)
      {
         _tcscpy(szOid, args->ppszOidList[i]);
         _tcscat(szOid, szSuffix);
         nameLen = SNMPParseOID(szOid, varbindName, MAX_OID_LEN);
         if (nameLen != 0)
         {
            request.bindVariable(new SNMP_Variable(varbindName, nameLen));
         }
      }

      SNMP_PDU *response;
      result = pTransport->doRequest(&request, &response, SnmpGetDefaultTimeout(), 3);
      if (result == SNMP_ERR_SUCCESS)
      {
         if ((response->getNumVariables() > 0) && (response->getErrorCode() == SNMP_PDU_ERR_SUCCESS))
         {
            args->table->addRow();

            // Add first column to results
            AddSNMPResult(args->table, 0, pVar, args->pnFormatList[0], *args->pNode);

            for(uint32_t i = 1; i < args->dwNumCols; i++)
               AddSNMPResult(args->table, i, response->getVariable(i - 1), args->pnFormatList[i], *args->pNode);
         }
         delete response;
      }
   }
   else  // single column
   {
      args->table->addRow();

      // Add first column to results
      AddSNMPResult(args->table, 0, pVar, args->pnFormatList[0], *args->pNode);
      result = SNMP_ERR_SUCCESS;
   }
   return result;
}

/**
 * SNMP table tool execution thread
 */
static void GetSNMPTable(ToolStartupInfo *toolData)
{
   TCHAR buffer[256];
   SNMP_ENUM_ARGS args;
	Table table;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   NXCPMessage msg(CMD_TABLE_DATA, toolData->requestId);

   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT col_name,col_oid,col_format FROM object_tools_table_columns WHERE tool_id=? ORDER BY col_number"));
   if (hStmt != nullptr)
   {
      DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolData->toolId);

      DB_RESULT hResult = DBSelectPrepared(hStmt);
      if (hResult != nullptr)
      {
         int numColumns = DBGetNumRows(hResult);
         if (numColumns > 0)
         {
            args.dwNumCols = numColumns;
            args.ppszOidList = MemAllocArrayNoInit<TCHAR*>(numColumns);
            args.pnFormatList = MemAllocArrayNoInit<int32_t>(numColumns);
            args.dwFlags = toolData->flags;
            args.pNode = toolData->node;
            args.table = &table;
            for(int i = 0; i < numColumns; i++)
            {
               DBGetField(hResult, i, 0, buffer, 256);
               args.ppszOidList[i] = DBGetField(hResult, i, 1, nullptr, 0);
               args.pnFormatList[i] = DBGetFieldLong(hResult, i, 2);
               table.addColumn(buffer, args.pnFormatList[i]);
            }

            // Enumerate
            if (toolData->node->callSnmpEnumerate(args.ppszOidList[0], TableHandler, &args) == SNMP_ERR_SUCCESS)
            {
               // Fill in message with results
               msg.setField(VID_RCC, RCC_SUCCESS);
               table.setTitle(toolData->toolData);
               table.fillMessage(&msg, 0, -1);
            }
            else
            {
               msg.setField(VID_RCC, RCC_SNMP_ERROR);
            }

            // Cleanup
            for(int i = 0; i < numColumns; i++)
               MemFree(args.ppszOidList[i]);
            MemFree(args.ppszOidList);
            MemFree(args.pnFormatList);
         }
         else
         {
            msg.setField(VID_RCC, RCC_INTERNAL_ERROR);
         }
         DBFreeResult(hResult);
      }
      else
      {
         msg.setField(VID_RCC, RCC_DB_FAILURE);
      }
      DBFreeStatement(hStmt);
   }
   else
   {
      msg.setField(VID_RCC, RCC_DB_FAILURE);
   }
   DBConnectionPoolReleaseConnection(hdb);

   // Send response to client
   toolData->session->sendMessage(msg);
   delete toolData;
}

/**
 * Execute table tool
 */
uint32_t ExecuteTableTool(uint32_t toolId, const shared_ptr<Node>& node, uint32_t requestId, ClientSession *session)
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT tool_type,tool_data,flags FROM object_tools WHERE tool_id=?"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return RCC_DB_FAILURE;
   }

   uint32_t rcc = RCC_SUCCESS;

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult != nullptr)
   {
      if (DBGetNumRows(hResult) > 0)
      {
         int32_t toolType = DBGetFieldLong(hResult, 0, 0);
         if ((toolType == TOOL_TYPE_SNMP_TABLE) || (toolType == TOOL_TYPE_AGENT_TABLE) || (toolType == TOOL_TYPE_AGENT_LIST))
         {
            session->incRefCount();
            auto startupInfo = new ToolStartupInfo();
            startupInfo->toolId = toolId;
            startupInfo->requestId = requestId;
            startupInfo->toolData = DBGetField(hResult, 0, 1, nullptr, 0);
            startupInfo->flags = DBGetFieldULong(hResult, 0, 2);
            startupInfo->node = node;
            startupInfo->session = session;
            ThreadPoolExecute(g_mainThreadPool, (toolType == TOOL_TYPE_SNMP_TABLE) ? GetSNMPTable : ((toolType == TOOL_TYPE_AGENT_LIST) ? GetAgentList : GetAgentTable), startupInfo);
         }
         else
         {
            rcc = RCC_INCOMPATIBLE_OPERATION;
         }
      }
      else
      {
         rcc = RCC_INVALID_TOOL_ID;
      }
      DBFreeResult(hResult);
   }
   else
   {
      rcc = RCC_DB_FAILURE;
   }

   DBConnectionPoolReleaseConnection(hdb);
   DBFreeStatement(hStmt);
   return rcc;
}


/**
 * Delete object tool from database
 */
UINT32 DeleteObjectToolFromDB(UINT32 toolId)
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

	if (!DBBegin(hdb))
	{
		DBConnectionPoolReleaseConnection(hdb);
		return RCC_DB_FAILURE;
	}

   DB_STATEMENT hStmt = DBPrepare(hdb, _T("DELETE FROM object_tools WHERE tool_id=?"));
   if (hStmt == nullptr)
      return ReturnDBFailure(hdb, hStmt);
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   if(!DBExecute(hStmt))
      return ReturnDBFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   hStmt = DBPrepare(hdb, _T("DELETE FROM object_tools_acl WHERE tool_id=?"));
   if (hStmt == nullptr)
      return ReturnDBFailure(hdb, hStmt);
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   if(!DBExecute(hStmt))
      return ReturnDBFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   hStmt = DBPrepare(hdb, _T("DELETE FROM object_tools_table_columns WHERE tool_id=?"));
   if (hStmt == nullptr)
      return ReturnDBFailure(hdb, hStmt);
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   if(!DBExecute(hStmt))
      return ReturnDBFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   hStmt = DBPrepare(hdb, _T("DELETE FROM input_fields WHERE category='T' AND owner_id=?"));
   if (hStmt == nullptr)
      return ReturnDBFailure(hdb, hStmt);
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   if(!DBExecute(hStmt))
      return ReturnDBFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   DBCommit(hdb);
	DBConnectionPoolReleaseConnection(hdb);
   NotifyClientSessions(NX_NOTIFY_OBJTOOL_DELETED, toolId);
   return RCC_SUCCESS;
}

/**
 * Change Object Tool Disable status to opposit
 */
UINT32 ChangeObjectToolStatus(UINT32 toolId, bool enabled)
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT flags FROM object_tools WHERE tool_id=?"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return RCC_DB_FAILURE;
   }

   UINT32 rcc = RCC_SUCCESS;
   UINT32 flags = 0;

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult != nullptr)
   {
      flags = DBGetFieldULong(hResult, 0, 0);
      DBFreeResult(hResult);
   }
   else
   {
      rcc = RCC_DB_FAILURE;
   }
   DBFreeStatement(hStmt);

   if (rcc == RCC_SUCCESS)
   {
      if (enabled)
      {
         flags &= ~TF_DISABLED;
      }
      else
      {
         flags |= TF_DISABLED;
      }
      rcc = RCC_DB_FAILURE;
      hStmt = DBPrepare(hdb, _T("UPDATE object_tools SET flags=? WHERE tool_id=?"));
      if (hStmt != nullptr)
      {
         DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, flags);
         DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, toolId);
         if (DBExecute(hStmt))
         {
            NotifyClientSessions(NX_NOTIFY_OBJTOOLS_CHANGED, toolId);
            rcc = RCC_SUCCESS;
         }
         DBFreeStatement(hStmt);
      }
   }

	DBConnectionPoolReleaseConnection(hdb);
   return rcc;
}

/**
 * Update/Insert object tool from NXCP message
 */
uint32_t UpdateObjectToolFromMessage(const NXCPMessage& msg)
{
   TCHAR buffer[MAX_DB_STRING];
   UINT32 i, aclSize, *pdwAcl;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

	if (!DBBegin(hdb))
	{
		DBConnectionPoolReleaseConnection(hdb);
		return RCC_DB_FAILURE;
	}

   // Insert or update common properties
   int nType = msg.getFieldAsUInt16(VID_TOOL_TYPE);
   UINT32 toolId = msg.getFieldAsUInt32(VID_TOOL_ID);
   bool newTool = false;
   DB_STATEMENT hStmt;
   if (IsDatabaseRecordExist(hdb, _T("object_tools"), _T("tool_id"), toolId))
   {
      hStmt = DBPrepare(hdb, _T("UPDATE object_tools SET tool_name=?,tool_type=?,")
                             _T("tool_data=?,description=?,flags=?,")
                             _T("tool_filter=?,confirmation_text=?,command_name=?,")
                             _T("command_short_name=?,icon=? ")
                             _T("WHERE tool_id=?"));
   }
   else
   {
      hStmt = DBPrepare(hdb, _T("INSERT INTO object_tools (tool_name,tool_type,")
                             _T("tool_data,description,flags,tool_filter,")
                             _T("confirmation_text,command_name,command_short_name,")
                             _T("icon,tool_id,guid) VALUES ")
                             _T("(?,?,?,?,?,?,?,?,?,?,?,?)"));
      newTool = true;
   }
   if (hStmt == nullptr)
      return ReturnDBFailure(hdb, hStmt);

   DBBind(hStmt, 1, DB_SQLTYPE_VARCHAR, msg.getFieldAsString(VID_NAME), DB_BIND_DYNAMIC);
   DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, nType);
   DBBind(hStmt, 3, DB_SQLTYPE_TEXT, msg.getFieldAsString(VID_TOOL_DATA), DB_BIND_DYNAMIC);
   DBBind(hStmt, 4, DB_SQLTYPE_VARCHAR, msg.getFieldAsString(VID_DESCRIPTION), DB_BIND_DYNAMIC);
   DBBind(hStmt, 5, DB_SQLTYPE_INTEGER, msg.getFieldAsUInt32(VID_FLAGS));
   DBBind(hStmt, 6, DB_SQLTYPE_TEXT, msg.getFieldAsString(VID_TOOL_FILTER), DB_BIND_DYNAMIC);
   DBBind(hStmt, 7, DB_SQLTYPE_VARCHAR, msg.getFieldAsString(VID_CONFIRMATION_TEXT), DB_BIND_DYNAMIC);
   DBBind(hStmt, 8, DB_SQLTYPE_VARCHAR, msg.getFieldAsString(VID_COMMAND_NAME), DB_BIND_DYNAMIC);
   DBBind(hStmt, 9, DB_SQLTYPE_VARCHAR, msg.getFieldAsString(VID_COMMAND_SHORT_NAME), DB_BIND_DYNAMIC);

   size_t size;
   const BYTE *imageData = msg.getBinaryFieldPtr(VID_IMAGE_DATA, &size);
   if (size > 0)
   {
      TCHAR *imageHexData = MemAllocString(size * 2 + 1);
      BinToStr(imageData, size, imageHexData);
      DBBind(hStmt, 10, DB_SQLTYPE_TEXT, imageHexData, DB_BIND_DYNAMIC);
   }
   else
   {
      DBBind(hStmt, 10, DB_SQLTYPE_TEXT, _T(""), DB_BIND_STATIC);
   }

   DBBind(hStmt, 11, DB_SQLTYPE_INTEGER, toolId);
   if (newTool)
   {
      DBBind(hStmt, 12, DB_SQLTYPE_VARCHAR, uuid::generate());
   }

   if (!DBExecute(hStmt))
      return ReturnDBFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   // Update ACL
   hStmt = DBPrepare(hdb, _T("DELETE FROM object_tools_acl WHERE tool_id=?"));
   if (hStmt == nullptr)
      return ReturnDBFailure(hdb, hStmt);
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   if(!DBExecute(hStmt))
      return ReturnDBFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   aclSize = msg.getFieldAsUInt32(VID_ACL_SIZE);
   if (aclSize > 0)
   {
      pdwAcl = MemAllocArray<UINT32>(aclSize);
      msg.getFieldAsInt32Array(VID_ACL, aclSize, pdwAcl);
      hStmt = DBPrepare(hdb, _T("INSERT INTO object_tools_acl (tool_id,user_id) VALUES (?,?)"), aclSize > 1);
      if (hStmt == nullptr)
         return ReturnDBFailure(hdb, hStmt);
      DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
      for(i = 0; i < aclSize; i++)
      {
         DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, pdwAcl[i]);
         if(!DBExecute(hStmt))
            return ReturnDBFailure(hdb, hStmt);
      }
      DBFreeStatement(hStmt);
      MemFree(pdwAcl);
   }

   // Update columns configuration
   hStmt = DBPrepare(hdb, _T("DELETE FROM object_tools_table_columns WHERE tool_id=?"));
   if (hStmt == nullptr)
      return ReturnDBFailure(hdb, hStmt);
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);

   if (!DBExecute(hStmt))
      return ReturnDBFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   if ((nType == TOOL_TYPE_SNMP_TABLE) || (nType == TOOL_TYPE_AGENT_LIST))
   {
      uint32_t numColumns = msg.getFieldAsUInt16(VID_NUM_COLUMNS);
      if (numColumns > 0)
      {
         hStmt = DBPrepare(hdb, _T("INSERT INTO object_tools_table_columns (tool_id,")
                                _T("col_number,col_name,col_oid,col_format,col_substr) ")
                                _T("VALUES (?,?,?,?,?,?)"), numColumns > 1);
         if (hStmt == nullptr)
            return ReturnDBFailure(hdb, hStmt);

         uint32_t fieldId;
         for(i = 0, fieldId = VID_COLUMN_INFO_BASE; i < numColumns; i++)
         {
            msg.getFieldAsString(fieldId++, buffer, MAX_DB_STRING);

            DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
            DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, i);
            DBBind(hStmt, 3, DB_SQLTYPE_VARCHAR, buffer, DB_BIND_STATIC);
            DBBind(hStmt, 4, DB_SQLTYPE_VARCHAR, msg.getFieldAsString(fieldId++), DB_BIND_DYNAMIC);
            DBBind(hStmt, 5, DB_SQLTYPE_INTEGER, msg.getFieldAsUInt16(fieldId++));
            DBBind(hStmt, 6, DB_SQLTYPE_INTEGER, msg.getFieldAsUInt16(fieldId++));

            if (!DBExecute(hStmt))
               return ReturnDBFailure(hdb, hStmt);
         }
         DBFreeStatement(hStmt);
      }
   }

   hStmt = DBPrepare(hdb, _T("DELETE FROM input_fields WHERE category='T' AND owner_id=?"));
   if (hStmt == nullptr)
      return ReturnDBFailure(hdb, hStmt);
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);

   if (!DBExecute(hStmt))
      return ReturnDBFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   uint32_t numFields = msg.getFieldAsUInt16(VID_NUM_FIELDS);
   if (numFields > 0)
   {
      hStmt = DBPrepare(hdb, _T("INSERT INTO input_fields (category,owner_id,name,input_type,display_name,flags,sequence_num) VALUES ('T',?,?,?,?,?,?)"), numFields > 1);
      if (hStmt == nullptr)
         return ReturnDBFailure(hdb, hStmt);
      DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);

      uint32_t fieldId = VID_FIELD_LIST_BASE;
      for(i = 0; i < numFields; i++)
      {
         DBBind(hStmt, 2, DB_SQLTYPE_VARCHAR, msg.getFieldAsString(fieldId++), DB_BIND_DYNAMIC);
         DBBind(hStmt, 3, DB_SQLTYPE_INTEGER, msg.getFieldAsUInt16(fieldId++));
         DBBind(hStmt, 4, DB_SQLTYPE_VARCHAR, msg.getFieldAsString(fieldId++), DB_BIND_DYNAMIC);
         DBBind(hStmt, 5, DB_SQLTYPE_INTEGER, msg.getFieldAsUInt32(fieldId++));
         DBBind(hStmt, 6, DB_SQLTYPE_INTEGER, msg.getFieldAsInt16(fieldId++));
         fieldId += 5;

         if (!DBExecute(hStmt))
            return ReturnDBFailure(hdb, hStmt);
      }
      DBFreeStatement(hStmt);
   }

   DBCommit(hdb);
	DBConnectionPoolReleaseConnection(hdb);
   NotifyClientSessions(NX_NOTIFY_OBJTOOLS_CHANGED, toolId);
   return RCC_SUCCESS;
}

/**
 * Import failure exit
 */
static inline bool ImportFailure(DB_HANDLE hdb, DB_STATEMENT hStmt)
{
   if (hStmt != nullptr)
      DBFreeStatement(hStmt);
   DBRollback(hdb);
   DBConnectionPoolReleaseConnection(hdb);
   DbgPrintf(4, _T("ImportObjectTool: database failure"));
   return false;
}

/**
 * Import object tool
 */
bool ImportObjectTool(ConfigEntry *config, bool overwrite)
{
   const TCHAR *guid = config->getSubEntryValue(_T("guid"));
   if (guid == nullptr)
   {
      DbgPrintf(4, _T("ImportObjectTool: missing GUID"));
      return false;
   }

   uuid_t temp;
   if (_uuid_parse(guid, temp) == -1)
   {
      DbgPrintf(4, _T("ImportObjectTool: GUID (%s) is invalid"), guid);
      return false;
   }

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   // Step 1: find existing tool ID by GUID
   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT tool_id FROM object_tools WHERE guid=?"));
   if (hStmt == nullptr)
      return ImportFailure(hdb, nullptr);

   DBBind(hStmt, 1, DB_SQLTYPE_VARCHAR, guid, DB_BIND_STATIC);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult == nullptr)
      return ImportFailure(hdb, hStmt);

   uint32_t toolId;
   if (DBGetNumRows(hResult) > 0)
   {
      toolId = DBGetFieldULong(hResult, 0, 0);
   }
   else
   {
      toolId = 0;
   }
   DBFreeResult(hResult);
   DBFreeStatement(hStmt);

   if ((toolId != 0) && !overwrite)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return true;
   }

   // Step 2: create or update tool record
	if (!DBBegin(hdb))
      return ImportFailure(hdb, nullptr);

   if (toolId != 0)
   {
      hStmt = DBPrepare(hdb, _T("UPDATE object_tools SET tool_name=?,tool_type=?,")
                             _T("tool_data=?,description=?,flags=?,")
                             _T("tool_filter=?,confirmation_text=?,command_name=?,")
                             _T("command_short_name=?,icon=? ")
                             _T("WHERE tool_id=?"));
   }
   else
   {
      hStmt = DBPrepare(hdb, _T("INSERT INTO object_tools (tool_name,tool_type,")
                             _T("tool_data,description,flags,tool_filter,")
                             _T("confirmation_text,command_name,command_short_name,")
                             _T("icon,tool_id,guid) VALUES ")
                             _T("(?,?,?,?,?,?,?,?,?,?,?,?)"));
   }
   if (hStmt == nullptr)
      return ImportFailure(hdb, nullptr);

   DBBind(hStmt, 1, DB_SQLTYPE_VARCHAR, config->getSubEntryValue(_T("name")), DB_BIND_STATIC);
   DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, config->getSubEntryValueAsInt(_T("type")));
   DBBind(hStmt, 3, DB_SQLTYPE_TEXT, config->getSubEntryValue(_T("data")), DB_BIND_STATIC);
   DBBind(hStmt, 4, DB_SQLTYPE_VARCHAR, config->getSubEntryValue(_T("description")), DB_BIND_STATIC);
   DBBind(hStmt, 5, DB_SQLTYPE_INTEGER, config->getSubEntryValueAsUInt(_T("flags")));
   DBBind(hStmt, 6, DB_SQLTYPE_TEXT, config->getSubEntryValue(_T("filter")), DB_BIND_STATIC);
   DBBind(hStmt, 7, DB_SQLTYPE_VARCHAR, config->getSubEntryValue(_T("confirmation")), DB_BIND_STATIC);
   DBBind(hStmt, 8, DB_SQLTYPE_VARCHAR, config->getSubEntryValue(_T("commandName")), DB_BIND_STATIC);
   DBBind(hStmt, 9, DB_SQLTYPE_VARCHAR, config->getSubEntryValue(_T("commandShortName")), DB_BIND_STATIC);
   DBBind(hStmt, 10, DB_SQLTYPE_TEXT, config->getSubEntryValue(_T("image")), DB_BIND_STATIC);
   if (toolId == 0)
   {
      toolId = CreateUniqueId(IDG_OBJECT_TOOL);
      DBBind(hStmt, 11, DB_SQLTYPE_INTEGER, toolId);
      DBBind(hStmt, 12, DB_SQLTYPE_VARCHAR, guid, DB_BIND_STATIC);
   }
   else
   {
      DBBind(hStmt, 11, DB_SQLTYPE_INTEGER, toolId);
   }

   if (!DBExecute(hStmt))
      return ImportFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   // Update ACL
   if (!ExecuteQueryOnObject(hdb, toolId, _T("DELETE FROM object_tools_acl WHERE tool_id=?")))
      return ImportFailure(hdb, nullptr);

   // Default ACL for imported tools - accessible by everyone
   hStmt = DBPrepare(hdb, _T("INSERT INTO object_tools_acl (tool_id,user_id) VALUES (?,?)"));
   if (hStmt == nullptr)
      return ImportFailure(hdb, nullptr);
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, GROUP_EVERYONE);
   if (!DBExecute(hStmt))
      return ImportFailure(hdb, hStmt);
   DBFreeStatement(hStmt);

   // Update columns configuration
   if (!ExecuteQueryOnObject(hdb, toolId, _T("DELETE FROM object_tools_table_columns WHERE tool_id=?")))
      return ImportFailure(hdb, nullptr);

   int toolType = config->getSubEntryValueAsInt(_T("type"));
   if ((toolType == TOOL_TYPE_SNMP_TABLE) || (toolType == TOOL_TYPE_AGENT_LIST))
   {
   	ConfigEntry *root = config->findEntry(_T("columns"));
	   if (root != nullptr)
	   {
         unique_ptr<ObjectArray<ConfigEntry>> columns = root->getOrderedSubEntries(_T("column#*"));
         if (columns->size() > 0)
         {
            hStmt = DBPrepare(hdb, _T("INSERT INTO object_tools_table_columns (tool_id,")
                                   _T("col_number,col_name,col_oid,col_format,col_substr) ")
                                   _T("VALUES (?,?,?,?,?,?)"));
            if (hStmt == nullptr)
               return ImportFailure(hdb, hStmt);

            for(int i = 0; i < columns->size(); i++)
            {
               ConfigEntry *c = columns->get(i);
               DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
               DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, (INT32)i);
               DBBind(hStmt, 3, DB_SQLTYPE_VARCHAR, c->getSubEntryValue(_T("name")), DB_BIND_STATIC);
               DBBind(hStmt, 4, DB_SQLTYPE_VARCHAR, c->getSubEntryValue(_T("oid")), DB_BIND_STATIC);
               DBBind(hStmt, 5, DB_SQLTYPE_INTEGER, (INT32)c->getSubEntryValueAsInt(_T("format")));
               DBBind(hStmt, 6, DB_SQLTYPE_INTEGER, (INT32)c->getSubEntryValueAsInt(_T("captureGroup")));

               if (!DBExecute(hStmt))
                  return ImportFailure(hdb, hStmt);
            }
            DBFreeStatement(hStmt);
         }
      }
   }

   // Update input fields
   if (!ExecuteQueryOnObject(hdb, toolId, _T("DELETE FROM input_fields WHERE category='T' AND owner_id=?")))
      return ImportFailure(hdb, nullptr);

	ConfigEntry *inputFieldsRoot = config->findEntry(_T("inputFields"));
   if (inputFieldsRoot != nullptr)
   {
      unique_ptr<ObjectArray<ConfigEntry>> inputFields = inputFieldsRoot->getOrderedSubEntries(_T("inputField#*"));
      if (inputFields->size() > 0)
      {
         hStmt = DBPrepare(hdb, _T("INSERT INTO input_fields (category,owner_id,name,input_type,display_name,flags,sequence_num) VALUES ('T',?,?,?,?,?,?)"));
         if (hStmt == nullptr)
            return ImportFailure(hdb, nullptr);

         DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
         for(int i = 0; i < inputFields->size(); i++)
         {
            ConfigEntry *c = inputFields->get(i);
            DBBind(hStmt, 2, DB_SQLTYPE_VARCHAR, c->getSubEntryValue(_T("name")), DB_BIND_STATIC);
            DBBind(hStmt, 3, DB_SQLTYPE_INTEGER, c->getSubEntryValueAsInt(_T("type")));
            DBBind(hStmt, 4, DB_SQLTYPE_VARCHAR, c->getSubEntryValue(_T("displayName")), DB_BIND_STATIC);
            DBBind(hStmt, 5, DB_SQLTYPE_INTEGER, c->getSubEntryValueAsInt(_T("flags")));
            DBBind(hStmt, 6, DB_SQLTYPE_INTEGER, i + 1);

            if (!DBExecute(hStmt))
               return ImportFailure(hdb, hStmt);
         }
         DBFreeStatement(hStmt);
      }
   }

   DBCommit(hdb);
	DBConnectionPoolReleaseConnection(hdb);
   NotifyClientSessions(NX_NOTIFY_OBJTOOLS_CHANGED, toolId);
   return true;
}

/**
 * Create export records for object tool columns
 */
static void CreateObjectToolColumnExportRecords(DB_HANDLE hdb, StringBuffer &xml, uint32_t id)
{
   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT col_number,col_name,col_oid,col_format,col_substr FROM object_tools_table_columns WHERE tool_id=?"));
   if (hStmt == nullptr)
      return;

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, id);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult != nullptr)
   {
      int count = DBGetNumRows(hResult);
      if (count > 0)
      {
         xml.append(_T("\t\t\t<columns>\n"));
         for(int i = 0; i < count; i++)
         {
            xml.append(_T("\t\t\t\t<column id=\""));
            xml.append(DBGetFieldLong(hResult, i, 0) + 1);
            xml.append(_T("\">\n\t\t\t\t\t<name>"));
            xml.appendPreallocated(DBGetFieldForXML(hResult, i, 1));
            xml.append(_T("</name>\n\t\t\t\t\t<oid>"));
            xml.appendPreallocated(DBGetFieldForXML(hResult, i, 2));
            xml.append(_T("</oid>\n\t\t\t\t\t<format>"));
            xml.append(DBGetFieldLong(hResult, i, 3));
            xml.append(_T("</format>\n\t\t\t\t\t<captureGroup>"));
            xml.append(DBGetFieldLong(hResult, i, 4));
            xml.append(_T("</captureGroup>\n\t\t\t\t</column>\n"));
         }
         xml.append(_T("\t\t\t</columns>\n"));
      }
      DBFreeResult(hResult);
   }

   DBFreeStatement(hStmt);
}

/**
 * Create export records for object tool input fields
 */
static void CreateObjectToolInputFieldExportRecords(DB_HANDLE hdb, StringBuffer &xml, uint32_t id)
{
   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT name,input_type,display_name,flags FROM input_fields WHERE category='T' AND owner_id=?"));
   if (hStmt == nullptr)
      return;

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, id);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult != nullptr)
   {
      int count = DBGetNumRows(hResult);
      if (count > 0)
      {
         xml.append(_T("\t\t\t<inputFields>\n"));
         for(int i = 0; i < count; i++)
         {
            xml.append(_T("\t\t\t\t<inputField id=\""));
            xml.append(i + 1);
            xml.append(_T("\">\n\t\t\t\t\t<name>"));
            xml.appendPreallocated(DBGetFieldForXML(hResult, i, 0));
            xml.append(_T("</name>\n\t\t\t\t\t<type>"));
            xml.append(DBGetFieldLong(hResult, i, 1));
            xml.append(_T("</type>\n\t\t\t\t\t<displayName>"));
            xml.appendPreallocated(DBGetFieldForXML(hResult, i, 2));
            xml.append(_T("</displayName>\n\t\t\t\t\t<flags>"));
            xml.append(DBGetFieldLong(hResult, i, 3));
            xml.append(_T("</flags>\n\t\t\t\t</inputField>\n"));
         }
         xml.append(_T("\t\t\t</inputFields>\n"));
      }
      DBFreeResult(hResult);
   }

   DBFreeStatement(hStmt);
}

/**
 * Create export record for given object tool
 */
void CreateObjectToolExportRecord(StringBuffer &xml, UINT32 id)
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT tool_name,guid,tool_type,tool_data,description,flags,tool_filter,confirmation_text,command_name,command_short_name,icon FROM object_tools WHERE tool_id=?"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return;
   }

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, id);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult != nullptr)
   {
      if (DBGetNumRows(hResult) > 0)
      {
         xml.append(_T("\t\t<objectTool id=\""));
         xml.append(id);
         xml.append(_T("\">\n\t\t\t<name>"));
         xml.appendPreallocated(DBGetFieldForXML(hResult, 0, 0));
         xml.append(_T("</name>\n\t\t\t<guid>"));
         xml.appendPreallocated(DBGetField(hResult, 0, 1, nullptr, 0));
         xml.append(_T("</guid>\n\t\t\t<type>"));
         xml.append(DBGetFieldLong(hResult, 0, 2));
         xml.append(_T("</type>\n\t\t\t<data>"));
         xml.appendPreallocated(DBGetFieldForXML(hResult, 0, 3));
         xml.append(_T("</data>\n\t\t\t<description>"));
         xml.appendPreallocated(DBGetFieldForXML(hResult, 0, 4));
         xml.append(_T("</description>\n\t\t\t<flags>"));
         xml.append(DBGetFieldLong(hResult, 0, 5));
         xml.append(_T("</flags>\n\t\t\t<filter>"));
         xml.appendPreallocated(DBGetFieldForXML(hResult, 0, 6));
         xml.append(_T("</filter>\n\t\t\t<confirmation>"));
         xml.appendPreallocated(DBGetFieldForXML(hResult, 0, 7));
         xml.append(_T("</confirmation>\n\t\t\t<commandName>"));
         xml.appendPreallocated(DBGetFieldForXML(hResult, 0, 8));
         xml.append(_T("</commandName>\n\t\t\t<commandShortName>"));
         xml.appendPreallocated(DBGetFieldForXML(hResult, 0, 9));
         xml.append(_T("</commandShortName>\n\t\t\t<image>"));
         xml.appendPreallocated(DBGetFieldForXML(hResult, 0, 10));
         xml.append(_T("</image>\n"));
         CreateObjectToolColumnExportRecords(hdb, xml, id);
         CreateObjectToolInputFieldExportRecords(hdb, xml, id);
         xml.append(_T("\t\t</objectTool>\n"));
      }
      DBFreeResult(hResult);
   }

   DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);
}

/**
 * Load object tool's input field definitions
 */
static bool LoadInputFieldDefinitions(uint32_t toolId, DB_HANDLE hdb, NXCPMessage *msg, uint32_t countFieldId, uint32_t baseFieldId)
{
   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT name,input_type,display_name,flags,sequence_num FROM input_fields WHERE category='T' AND owner_id=? ORDER BY name"));
   if (hStmt == nullptr)
      return false;

   bool success = false;

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   if (hResult != nullptr)
   {
      int count = DBGetNumRows(hResult);
      msg->setField(countFieldId, (UINT16)count);
      UINT32 fieldId = baseFieldId;
      for(int i = 0; i < count; i++)
      {
         TCHAR buffer[128];

         DBGetField(hResult, i, 0, buffer, 128);
         msg->setField(fieldId++, buffer);

         msg->setField(fieldId++, static_cast<int16_t>(DBGetFieldLong(hResult, i, 1)));

         DBGetField(hResult, i, 2, buffer, 128);
         msg->setField(fieldId++, buffer);

         msg->setField(fieldId++, DBGetFieldLong(hResult, i, 3));

         int seq = DBGetFieldLong(hResult, i, 4);
         if (seq == -1)
            seq = i;
         msg->setField(fieldId++, static_cast<int16_t>(seq));

         fieldId += 5;
      }
      DBFreeResult(hResult);
      success = true;
   }

   DBFreeStatement(hStmt);
   return success;
}

/**
 * Get all object tools available for given user into NXCP message
 */
UINT32 GetObjectToolsIntoMessage(NXCPMessage *msg, UINT32 userId, bool fullAccess)
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   DB_RESULT hResult = DBSelect(hdb, _T("SELECT tool_id,user_id FROM object_tools_acl"));
   if (hResult == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return RCC_DB_FAILURE;
   }

   int aclSize = DBGetNumRows(hResult);
   OBJECT_TOOL_ACL *acl = (OBJECT_TOOL_ACL *)malloc(aclSize * sizeof(OBJECT_TOOL_ACL));
   for(int i = 0; i < aclSize; i++)
   {
      acl[i].toolId = DBGetFieldULong(hResult, i, 0);
      acl[i].userId = DBGetFieldULong(hResult, i, 1);
   }
   DBFreeResult(hResult);

   hResult = DBSelect(hdb, _T("SELECT tool_id,tool_name,tool_type,tool_data,flags,description,tool_filter,confirmation_text,command_name,command_short_name,icon FROM object_tools"));
   if (hResult == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      free(acl);
      return RCC_DB_FAILURE;
   }

   UINT32 recordCount = 0;
   UINT32 fieldId = VID_OBJECT_TOOLS_BASE;
   int count = DBGetNumRows(hResult);
   for(int i = 0; i < count; i++)
   {
      UINT32 toolId = DBGetFieldULong(hResult, i, 0);
      bool hasAccess = fullAccess;
      if (!fullAccess)
      {
         for(int j = 0; j < aclSize; j++)
         {
            if (acl[j].toolId == toolId)
            {
               if ((acl[j].userId == userId) ||
                   (acl[j].userId == GROUP_EVERYONE) ||
                   ((acl[j].userId & GROUP_FLAG) && CheckUserMembership(userId, acl[j].userId)))
               {
                  hasAccess = true;
                  break;
               }
            }
         }
      }

      if (hasAccess)
      {
         TCHAR buffer[MAX_DB_STRING];

         msg->setField(fieldId, toolId);

         // name
         DBGetField(hResult, i, 1, buffer, MAX_DB_STRING);
         msg->setField(fieldId + 1, buffer);

         msg->setField(fieldId + 2, (WORD)DBGetFieldLong(hResult, i, 2));

         // data
         TCHAR *data = DBGetField(hResult, i, 3, nullptr, 0);
         msg->setField(fieldId + 3, data);
         free(data);

         msg->setField(fieldId + 4, DBGetFieldULong(hResult, i, 4));

         // description
         DBGetField(hResult, i, 5, buffer, MAX_DB_STRING);
         msg->setField(fieldId + 5, buffer);

         // matching OID
         DBGetField(hResult, i, 6, buffer, MAX_DB_STRING);
         msg->setField(fieldId + 6, buffer);

         // confirmation text
         DBGetField(hResult, i, 7, buffer, MAX_DB_STRING);
         msg->setField(fieldId + 7, buffer);

         // command name
         DBGetField(hResult, i, 8, buffer, MAX_DB_STRING);
         msg->setField(fieldId + 8, buffer);

         // command short name
         DBGetField(hResult, i, 9, buffer, MAX_DB_STRING);
         msg->setField(fieldId + 9, buffer);

         // icon
         TCHAR *imageDataHex = DBGetField(hResult, i, 10, nullptr, 0);
         if (imageDataHex != nullptr)
         {
            size_t size = _tcslen(imageDataHex) / 2;
            BYTE *imageData = (BYTE *)malloc(size);
            size_t bytes = StrToBin(imageDataHex, imageData, size);
            msg->setField(fieldId + 10, imageData, (UINT32)bytes);
            free(imageData);
            free(imageDataHex);
         }
         else
         {
            msg->setField(fieldId + 10, (BYTE *)nullptr, 0);
         }

         LoadInputFieldDefinitions(toolId, hdb, msg, fieldId + 11, fieldId + 20);

         recordCount++;
         fieldId += 10000;
      }
   }
   msg->setField(VID_NUM_TOOLS, recordCount);

   DBFreeResult(hResult);
   free(acl);

   DBConnectionPoolReleaseConnection(hdb);
   return RCC_SUCCESS;
}

/**
 * Get object tool details into NXCP message
 */
UINT32 GetObjectToolDetailsIntoMessage(UINT32 toolId, NXCPMessage *msg)
{
   UINT32 rcc = RCC_DB_FAILURE;
   DB_RESULT hResult = nullptr;
   TCHAR buffer[MAX_DB_STRING], *data;
   int aclSize, toolType;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb, _T("SELECT tool_name,tool_type,tool_data,description,flags,tool_filter,confirmation_text,command_name,command_short_name,icon FROM object_tools WHERE tool_id=?"));
   if (hStmt == nullptr)
      goto cleanup;

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);

   hResult = DBSelectPrepared(hStmt);
   if (hResult == nullptr)
      goto cleanup;

   if (DBGetNumRows(hResult) == 0)
   {
      rcc = RCC_INVALID_TOOL_ID;
      goto cleanup;
   }

	msg->setField(VID_TOOL_ID, toolId);
   msg->setField(VID_NAME, DBGetField(hResult, 0, 0, buffer, MAX_DB_STRING));

   toolType = DBGetFieldLong(hResult, 0, 1);
   msg->setField(VID_TOOL_TYPE, (INT16)toolType);

   data = DBGetField(hResult, 0, 2, nullptr, 0);
   msg->setField(VID_TOOL_DATA, data);
   MemFree(data);

   msg->setField(VID_DESCRIPTION, DBGetField(hResult, 0, 3, buffer, MAX_DB_STRING));
   msg->setField(VID_FLAGS, DBGetFieldULong(hResult, 0, 4));

   data = DBGetField(hResult, 0, 5, nullptr, 0);
   msg->setField(VID_TOOL_FILTER, data);
   MemFree(data);

   msg->setField(VID_CONFIRMATION_TEXT, DBGetField(hResult, 0, 6, buffer, MAX_DB_STRING));
   msg->setField(VID_COMMAND_NAME, DBGetField(hResult, 0, 7, buffer, MAX_DB_STRING));
   msg->setField(VID_COMMAND_SHORT_NAME, DBGetField(hResult, 0, 8, buffer, MAX_DB_STRING));

   // icon
   data = DBGetField(hResult, 0, 9, nullptr, 0);
   if (data != nullptr)
   {
      size_t size = _tcslen(data) / 2;
      BYTE *imageData = (BYTE *)malloc(size);
      size_t bytes = StrToBin(data, imageData, size);
      msg->setField(VID_IMAGE_DATA, imageData, (UINT32)bytes);
      free(imageData);
      free(data);
   }
   else
   {
      msg->setField(VID_IMAGE_DATA, (BYTE *)nullptr, 0);
   }

   DBFreeResult(hResult);
   hResult = nullptr;
   DBFreeStatement(hStmt);

   // Access list
   hStmt = DBPrepare(hdb, _T("SELECT user_id FROM object_tools_acl WHERE tool_id=?"));
   if (hStmt == nullptr)
      goto cleanup;
   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);

   hResult = DBSelectPrepared(hStmt);
   if (hResult == nullptr)
      goto cleanup;

   aclSize = DBGetNumRows(hResult);
   msg->setField(VID_ACL_SIZE, (UINT32)aclSize);
   if (aclSize > 0)
   {
      UINT32 *acl = (UINT32 *)malloc(sizeof(UINT32) * aclSize);
      for(int i = 0; i < aclSize; i++)
         acl[i] = DBGetFieldULong(hResult, i, 0);
      msg->setFieldFromInt32Array(VID_ACL, aclSize, acl);
      free(acl);
   }
   DBFreeResult(hResult);
   hResult = nullptr;

   // Column information for table tools
   if ((toolType == TOOL_TYPE_SNMP_TABLE) || (toolType == TOOL_TYPE_AGENT_LIST))
   {
      DBFreeStatement(hStmt);
      hStmt = DBPrepare(hdb, _T("SELECT col_name,col_oid,col_format,col_substr ")
                             _T("FROM object_tools_table_columns WHERE tool_id=? ")
                             _T("ORDER BY col_number"));
      if (hStmt == nullptr)
         goto cleanup;
      DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, toolId);

      hResult = DBSelectPrepared(hStmt);
      if (hResult == nullptr)
         goto cleanup;

      int count = DBGetNumRows(hResult);
      msg->setField(VID_NUM_COLUMNS, (INT16)count);
      UINT32 fieldId = VID_COLUMN_INFO_BASE;
      for(int i = 0; i < count; i++)
      {
         DBGetField(hResult, i, 0, buffer, MAX_DB_STRING);
         msg->setField(fieldId++, buffer);
         msg->setField(fieldId++, DBGetField(hResult, i, 1, buffer, MAX_DB_STRING));
         msg->setField(fieldId++, (UINT16)DBGetFieldLong(hResult, i, 2));
         msg->setField(fieldId++, (UINT16)DBGetFieldLong(hResult, i, 3));
      }
      DBFreeResult(hResult);
      hResult = nullptr;
   }

   if (!LoadInputFieldDefinitions(toolId, hdb, msg, VID_NUM_FIELDS, VID_FIELD_LIST_BASE))
      goto cleanup;

   rcc = RCC_SUCCESS;

cleanup:
   if (hResult != nullptr)
      DBFreeResult(hResult);
   if (hStmt != nullptr)
      DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);

   if (rcc != RCC_SUCCESS)
      msg->deleteAllFields();
   return rcc;
}

/**
 * Command execution data constructor
 */
ServerCommandExecutor::ServerCommandExecutor(const NXCPMessage& request, ClientSession *session) : ProcessExecutor(nullptr)
{
   shared_ptr<NetObj> object = FindObjectById(request.getFieldAsUInt32(VID_OBJECT_ID));
   if (object != nullptr)
   {
      StringMap *inputFields;
      int count = request.getFieldAsInt16(VID_NUM_FIELDS);
      if (count > 0)
      {
         inputFields = new StringMap();
         uint32_t fieldId = VID_FIELD_LIST_BASE;
         for(int i = 0; i < count; i++)
         {
            TCHAR *name = request.getFieldAsString(fieldId++);
            TCHAR *value = request.getFieldAsString(fieldId++);
            inputFields->setPreallocated(name, value);
         }
      }
      else
      {
         inputFields = nullptr;
      }

      TCHAR *cmd = request.getFieldAsString(VID_COMMAND);
      m_cmd = MemCopyString(object->expandText(cmd, nullptr, nullptr, shared_ptr<DCObjectInfo>(), session->getLoginName(), nullptr, nullptr, inputFields, nullptr));

      if (request.getFieldAsInt32(VID_NUM_MASKED_FIELDS) > 0)
      {
         StringList list(request, VID_MASKED_FIELD_LIST_BASE, VID_NUM_MASKED_FIELDS);
         for (int i = 0; i < list.size(); i++)
         {
            inputFields->set(list.get(i), _T("******"));
         }
         m_maskedCommand = object->expandText(cmd, nullptr, nullptr, shared_ptr<DCObjectInfo>(), session->getLoginName(), nullptr, nullptr, inputFields, nullptr);
      }
      else
      {
         m_maskedCommand = StringBuffer(m_cmd);
      }
      delete inputFields;
      MemFree(cmd);
   }

   m_sendOutput = request.getFieldAsBoolean(VID_RECEIVE_OUTPUT);
   if (m_sendOutput)
   {
      m_requestId = request.getId();
      m_session = session;
      m_session->incRefCount();
   }
   else
   {
      m_requestId = 0;
      m_session = nullptr;
   }
}

/**
 * Command execution data destructor
 */
ServerCommandExecutor::~ServerCommandExecutor()
{
   if (m_session != nullptr)
      m_session->decRefCount();
}

/**
 * Send output to console
 */
void ServerCommandExecutor::onOutput(const char *text, size_t length)
{
   NXCPMessage msg(CMD_COMMAND_OUTPUT, m_requestId);
#ifdef UNICODE
   TCHAR *buffer = WideStringFromMBStringSysLocale(text);
   msg.setField(VID_MESSAGE, buffer);
   m_session->sendMessage(&msg);
   MemFree(buffer);
#else
   msg.setField(VID_MESSAGE, text);
   m_session->sendMessage(&msg);
#endif
}

/**
 * Send message to make console stop listening to output
 */
void ServerCommandExecutor::endOfOutput()
{
   NXCPMessage msg(CMD_COMMAND_OUTPUT, m_requestId);
   msg.setEndOfSequence();
   m_session->sendMessage(&msg);
   m_session->unregisterServerCommand(getId());
}
