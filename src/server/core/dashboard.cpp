/*
** NetXMS - Network Management System
** Copyright (C) 2003-2022 Victor Kirhenshtein
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
** File: netmap.cpp
**
**/

#include "nxcore.h"

/**
 * Default constructor
 */
Dashboard::Dashboard() : super(), AutoBindTarget(this), Pollable(this, Pollable::AUTOBIND), m_elements(0, 16, Ownership::True)
{
	m_numColumns = 1;
   m_displayPriority = 0;
	m_status = STATUS_NORMAL;
}

/**
 * Constructor for creating new dashboard object
 */
Dashboard::Dashboard(const TCHAR *name) : super(name, 0), AutoBindTarget(this), Pollable(this, Pollable::AUTOBIND), m_elements(0, 16, Ownership::True)
{
	m_numColumns = 1;
	m_displayPriority = 0;
	m_status = STATUS_NORMAL;
}

/**
 * Redefined status calculation
 */
void Dashboard::calculateCompoundStatus(bool forcedRecalc)
{
   m_status = STATUS_NORMAL;
}

/**
 * Create object from database
 */
bool Dashboard::loadFromDatabase(DB_HANDLE hdb, UINT32 id)
{
	if (!super::loadFromDatabase(hdb, id))
		return false;

   if (!AutoBindTarget::loadFromDatabase(hdb, id))
      return false;

   if (!Pollable::loadFromDatabase(hdb, id))
      return false;

	m_status = STATUS_NORMAL;

	TCHAR query[256];
	_sntprintf(query, 256, _T("SELECT num_columns,display_priority FROM dashboards WHERE id=%u"), id);
	DB_RESULT hResult = DBSelect(hdb, query);
	if (hResult == nullptr)
		return false;
	if (DBGetNumRows(hResult) > 0)
	{
		m_numColumns = DBGetFieldLong(hResult, 0, 0);
      m_displayPriority = DBGetFieldLong(hResult, 0, 1);
	}
	DBFreeResult(hResult);

	_sntprintf(query, 256, _T("SELECT element_type,element_data,layout_data FROM dashboard_elements WHERE dashboard_id=%u ORDER BY element_id"), id);
	hResult = DBSelect(hdb, query);
	if (hResult == nullptr)
		return false;

	int count = DBGetNumRows(hResult);
	for(int i = 0; i < count; i++)
	{
		DashboardElement *e = new DashboardElement;
		e->m_type = (int)DBGetFieldLong(hResult, i, 0);
		e->m_data = DBGetField(hResult, i, 1, nullptr, 0);
		e->m_layout = DBGetField(hResult, i, 2, nullptr, 0);
		m_elements.add(e);
	}

	DBFreeResult(hResult);
	return true;
}

/**
 * Save object to database
 */
bool Dashboard::saveToDatabase(DB_HANDLE hdb)
{
   bool success = super::saveToDatabase(hdb);

   if (success && (m_modified & MODIFY_OTHER))
      success = AutoBindTarget::saveToDatabase(hdb);

   if (success && (m_modified & MODIFY_OTHER))
   {
      DB_STATEMENT hStmt;
      if (IsDatabaseRecordExist(hdb, _T("dashboards"), _T("id"), m_id))
         hStmt = DBPrepare(hdb, _T("UPDATE dashboards SET num_columns=?,display_priority=? WHERE id=?"));
      else
         hStmt = DBPrepare(hdb, _T("INSERT INTO dashboards (num_columns,display_priority,id) VALUES (?,?,?)"));
      if (hStmt != nullptr)
      {
         lockProperties();
         DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, m_numColumns);
         DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, m_displayPriority);
         DBBind(hStmt, 3, DB_SQLTYPE_INTEGER, m_id);
         unlockProperties();
         success = DBExecute(hStmt);
         DBFreeStatement(hStmt);
      }
      else
      {
         success = false;
      }

      if (success)
      {
         success = executeQueryOnObject(hdb, _T("DELETE FROM dashboard_elements WHERE dashboard_id=?"));
         lockProperties();
         if (success && !m_elements.isEmpty())
         {
            hStmt = DBPrepare(hdb, _T("INSERT INTO dashboard_elements (dashboard_id,element_id,element_type,element_data,layout_data) VALUES (?,?,?,?,?)"));
            if (hStmt != nullptr)
            {
               DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, m_id);
               for(int i = 0; success && (i < m_elements.size()); i++)
               {
                  DashboardElement *element = m_elements.get(i);
                  DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, i);
                  DBBind(hStmt, 3, DB_SQLTYPE_INTEGER, element->m_type);
                  DBBind(hStmt, 4, DB_SQLTYPE_TEXT, element->m_data, DB_BIND_STATIC);
                  DBBind(hStmt, 5, DB_SQLTYPE_TEXT, element->m_layout, DB_BIND_STATIC);
                  success = DBExecute(hStmt);
               }
               DBFreeStatement(hStmt);
            }
            else
            {
               success = false;
            }
         }
         unlockProperties();
      }
   }
   return success;
}

/**
 * Delete object from database
 */
bool Dashboard::deleteFromDatabase(DB_HANDLE hdb)
{
   bool success = super::deleteFromDatabase(hdb);
   if (success)
      success = executeQueryOnObject(hdb, _T("DELETE FROM dashboards WHERE id=?"));
   if (success)
      success = executeQueryOnObject(hdb, _T("DELETE FROM dashboard_elements WHERE dashboard_id=?"));
   if (success)
      success = AutoBindTarget::deleteFromDatabase(hdb);
   return success;
}

/**
 * Create NXCP message with object's data
 */
void Dashboard::fillMessageInternal(NXCPMessage *msg, UINT32 userId)
{
   super::fillMessageInternal(msg, userId);
   AutoBindTarget::fillMessage(msg);

	msg->setField(VID_NUM_COLUMNS, static_cast<uint16_t>(m_numColumns));
   msg->setField(VID_DISPLAY_PRIORITY, m_displayPriority);
	msg->setField(VID_NUM_ELEMENTS, static_cast<uint32_t>(m_elements.size()));

	uint32_t fieldId = VID_ELEMENT_LIST_BASE;
	for(int i = 0; i < m_elements.size(); i++)
	{
		DashboardElement *element = m_elements.get(i);
		msg->setField(fieldId++, static_cast<uint16_t>(element->m_type));
		msg->setField(fieldId++, CHECK_NULL_EX(element->m_data));
		msg->setField(fieldId++, CHECK_NULL_EX(element->m_layout));
		fieldId += 7;
	}
}

/**
 * Modify object from NXCP message
 */
uint32_t Dashboard::modifyFromMessageInternal(const NXCPMessage& msg)
{
   AutoBindTarget::modifyFromMessage(msg);

	if (msg.isFieldExist(VID_NUM_COLUMNS))
		m_numColumns = msg.getFieldAsInt16(VID_NUM_COLUMNS);

   if (msg.isFieldExist(VID_DISPLAY_PRIORITY))
      m_displayPriority = msg.getFieldAsInt32(VID_DISPLAY_PRIORITY);

	if (msg.isFieldExist(VID_NUM_ELEMENTS))
	{
		m_elements.clear();

		int count = (int)msg.getFieldAsUInt32(VID_NUM_ELEMENTS);
		uint32_t fieldId = VID_ELEMENT_LIST_BASE;
		for(int i = 0; i < count; i++)
		{
			DashboardElement *e = new DashboardElement;
			e->m_type = msg.getFieldAsInt16(fieldId++);
			e->m_data = msg.getFieldAsString(fieldId++);
			e->m_layout = msg.getFieldAsString(fieldId++);
			fieldId += 7;
			m_elements.add(e);
		}
	}

	return super::modifyFromMessageInternal(msg);
}

/**
 * Called by client session handler to check if threshold summary should be shown for this object.
 */
bool Dashboard::showThresholdSummary() const
{
	return false;
}

/**
 * Serialize object to JSON
 */
json_t *Dashboard::toJson()
{
   json_t *root = super::toJson();
   AutoBindTarget::toJson(root);

   lockProperties();
   json_object_set_new(root, "numColumns", json_integer(m_numColumns));
   json_object_set_new(root, "displayPriority", json_integer(m_displayPriority));
   json_object_set_new(root, "elements", json_object_array(m_elements));
   unlockProperties();

   return root;
}

/**
 * Lock dashboard for autobind poll
 */
bool Dashboard::lockForAutobindPoll()
{
   bool success = false;
   lockProperties();
   if (!m_isDeleted && !m_isDeleteInitiated && (m_status != STATUS_UNMANAGED) &&
       (static_cast<uint32_t>(time(nullptr) - m_autobindPollState.getLastCompleted()) > getCustomAttributeAsUInt32(_T("SysConfig:Objects.AutobindPollingInterval"), g_autobindPollingInterval)))
   {
      success = m_autobindPollState.schedule();
   }
   unlockProperties();
   return success;
}

/**
 * Perform automatic assignment to objects
 */
void Dashboard::autobindPoll(PollerInfo *poller, ClientSession *session, uint32_t rqId)
{
   poller->setStatus(_T("wait for lock"));
   pollerLock(autobind);

   if (IsShutdownInProgress())
   {
      pollerUnlock();
      return;
   }

   m_pollRequestor = session;
   m_pollRequestId = rqId;
   nxlog_debug_tag(DEBUG_TAG_AUTOBIND_POLL, 5, _T("Starting autobind poll of dashboard %s [%u]"), m_name, m_id);
   poller->setStatus(_T("checking objects"));

   if (!isAutoBindEnabled())
   {
      sendPollerMsg(_T("Automatic object binding is disabled\r\n"));
      nxlog_debug_tag(DEBUG_TAG_AUTOBIND_POLL, 5, _T("Finished autobind poll of dashboard %s [%u])"), m_name, m_id);
      pollerUnlock();
      return;
   }

   NXSL_VM *cachedFilterVM = nullptr;
   unique_ptr<SharedObjectArray<NetObj>> objects = g_idxObjectById.getObjects(
      [] (NetObj *object, void *context) -> bool
      {
         if (object->isDataCollectionTarget())
            return true;
         int objectClass = object->getObjectClass();
         return (objectClass == OBJECT_NETWORK) || (objectClass == OBJECT_SERVICEROOT) || (objectClass == OBJECT_SUBNET) || (objectClass == OBJECT_ZONE) || (objectClass == OBJECT_CONDITION) || (objectClass == OBJECT_CONTAINER) || (objectClass == OBJECT_RACK);
      }, nullptr);

   for (int i = 0; i < objects->size(); i++)
   {
      shared_ptr<NetObj> object = objects->getShared(i);

      AutoBindDecision decision = isApplicable(&cachedFilterVM, object, this);
      if ((decision == AutoBindDecision_Ignore) || ((decision == AutoBindDecision_Unbind) && !isAutoUnbindEnabled()))
         continue;   // Decision cannot affect checks

      if (decision == AutoBindDecision_Bind)
      {
         if (object->addDashboard(m_id))
         {
            StringBuffer cn(object->getObjectClassName());
            cn.toLowercase();
            sendPollerMsg(_T("   Adding to %s %s\r\n"), cn.cstr(), object->getName());
            nxlog_debug_tag(DEBUG_TAG_AUTOBIND_POLL, 4, _T("Dashboard::autobindPoll(): adding dashboard \"%s\" [%u] to %s \"%s\" [%u]"), m_name, m_id, cn.cstr(), object->getName(), object->getId());
         }
      }
      else if (decision == AutoBindDecision_Unbind)
      {
         if (object->removeDashboard(m_id))
         {
            StringBuffer cn(object->getObjectClassName());
            cn.toLowercase();
            nxlog_debug_tag(DEBUG_TAG_AUTOBIND_POLL, 4, _T("Dashboard::autobindPoll(): removing dashboard \"%s\" [%u] from %s \"%s\" [%u]"), m_name, m_id, cn.cstr(), object->getName(), object->getId());
            sendPollerMsg(_T("   Removing from %s %s\r\n"), cn.cstr(), object->getName());
         }
      }
   }
   delete cachedFilterVM;

   pollerUnlock();
   nxlog_debug_tag(DEBUG_TAG_AUTOBIND_POLL, 5, _T("Finished autobind poll of dashboard %s [%u])"), m_name, m_id);
}

/**
 * Redefined status calculation for dashboard group
 */
void DashboardGroup::calculateCompoundStatus(bool forcedRecalc)
{
   m_status = STATUS_NORMAL;
}

/**
 * Called by client session handler to check if threshold summary should be shown for this object.
 */
bool DashboardGroup::showThresholdSummary() const
{
   return false;
}
