/* 
** NetXMS - Network Management System
** NetXMS Foundation Library
** Copyright (C) 2003-2021 Victor Kirhenshtein
**
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU Lesser General Public License as published
** by the Free Software Foundation; either version 3 of the License, or
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
** File: strmap.cpp
**
**/

#include "libnetxms.h"
#include "strmap-internal.h"
#include <nxcpapi.h>

/**
 * Copy constructor
 */
StringMap::StringMap(const StringMap &src) : StringMapBase(Ownership::True)
{
	m_objectOwner = src.m_objectOwner;
   m_ignoreCase = src.m_ignoreCase;
   m_objectDestructor = src.m_objectDestructor;

   StringMapEntry *entry, *tmp;
   HASH_ITER(hh, src.m_data, entry, tmp)
   {
      setObject(MemCopyString(m_ignoreCase ? entry->originalKey : entry->key), MemCopyString((TCHAR *)entry->value), true);
   }
}

/**
 * Create string map from NXCP message
 */
StringMap::StringMap(const NXCPMessage& msg, uint32_t baseFieldId, uint32_t sizeFieldId) : StringMapBase(Ownership::True)
{
   loadMessage(msg, baseFieldId, sizeFieldId);
}

/**
 * Assignment
 */
StringMap& StringMap::operator =(const StringMap &src)
{
	clear();
	m_objectOwner = src.m_objectOwner;
   m_ignoreCase = src.m_ignoreCase;
   m_objectDestructor = src.m_objectDestructor;

   StringMapEntry *entry, *tmp;
   HASH_ITER(hh, src.m_data, entry, tmp)
   {
      setObject(MemCopyString(m_ignoreCase ? entry->originalKey : entry->key), MemCopyString((TCHAR *)entry->value), true);
   }
	return *this;
}

/**
 * Add all values from another string map
 */
void StringMap::addAll(const StringMap *src, bool (*filter)(const TCHAR *, const TCHAR *, void *), void *context)
{
   StringMapEntry *entry, *tmp;
   HASH_ITER(hh, src->m_data, entry, tmp)
   {
      const TCHAR *k = src->m_ignoreCase ? entry->originalKey : entry->key;
      if ((filter == NULL) || filter(k, static_cast<TCHAR*>(entry->value), context))
      {
         setObject(MemCopyString(k), MemCopyString(static_cast<TCHAR*>(entry->value)), true);
      }
   }
}

/**
 * Set value from INT32
 */
void StringMap::set(const TCHAR *key, INT32 value)
{
   TCHAR buffer[32];
   _sntprintf(buffer, 32, _T("%d"), (int)value);
   set(key, buffer);
}

/**
 * Set value from UINT32
 */
void StringMap::set(const TCHAR *key, UINT32 value)
{
	TCHAR buffer[32];
	_sntprintf(buffer, 32, _T("%u"), (unsigned int)value);
	set(key, buffer);
}

/**
 * Set value from INT64
 */
void StringMap::set(const TCHAR *key, INT64 value)
{
   TCHAR buffer[64];
   _sntprintf(buffer, 64, INT64_FMT, value);
   set(key, buffer);
}

/**
 * Set value from UINT64
 */
void StringMap::set(const TCHAR *key, UINT64 value)
{
   TCHAR buffer[64];
   _sntprintf(buffer, 64, UINT64_FMT, value);
   set(key, buffer);
}

/**
 * Get value by key as INT32
 */
INT32 StringMap::getInt32(const TCHAR *key, INT32 defaultValue) const
{
	const TCHAR *value = get(key);
	if (value == NULL)
		return defaultValue;
	return _tcstol(value, NULL, 0);
}

/**
 * Get value by key as UINT32
 */
UINT32 StringMap::getUInt32(const TCHAR *key, UINT32 defaultValue) const
{
   const TCHAR *value = get(key);
   if (value == NULL)
      return defaultValue;
   return _tcstoul(value, NULL, 0);
}

/**
 * Get value by key as INT64
 */
INT64 StringMap::getInt64(const TCHAR *key, INT64 defaultValue) const
{
   const TCHAR *value = get(key);
   if (value == NULL)
      return defaultValue;
   return _tcstoll(value, NULL, 0);
}

/**
 * Get value by key as UINT64
 */
UINT64 StringMap::getUInt64(const TCHAR *key, UINT64 defaultValue) const
{
   const TCHAR *value = get(key);
   if (value == NULL)
      return defaultValue;
   return _tcstoull(value, NULL, 0);
}

/**
 * Get value by key as double
 */
double StringMap::getDouble(const TCHAR *key, double defaultValue) const
{
   const TCHAR *value = get(key);
   if (value == nullptr)
      return defaultValue;
   return _tcstod(value, nullptr);
}

/**
 * Get value by key as boolean
 */
bool StringMap::getBoolean(const TCHAR *key, bool defaultValue) const
{
	const TCHAR *value = get(key);
	if (value == nullptr)
		return defaultValue;
	if (!_tcsicmp(value, _T("false")))
		return false;
	if (!_tcsicmp(value, _T("true")))
		return true;
	return _tcstoul(value, nullptr, 0) != 0;
}

/**
 * Fill NXCP message with map data
 */
void StringMap::fillMessage(NXCPMessage *msg, uint32_t sizeFieldId, uint32_t baseFieldId) const
{
   msg->setField(sizeFieldId, static_cast<uint32_t>(size()));
   uint32_t id = baseFieldId;
   StringMapEntry *entry, *tmp;
   HASH_ITER(hh, m_data, entry, tmp)
   {
      msg->setField(id++, m_ignoreCase ? entry->originalKey : entry->key);
      msg->setField(id++, static_cast<TCHAR*>(entry->value));
   }
}

/**
 * Load data from NXCP message
 */
void StringMap::loadMessage(const NXCPMessage& msg, uint32_t baseFieldId, uint32_t sizeFieldId)
{
   int count = msg.getFieldAsInt32(sizeFieldId);
   uint32_t id = baseFieldId;
   for(int i = 0; i < count; i++)
   {
      TCHAR *key = msg.getFieldAsString(id++);
      TCHAR *value = msg.getFieldAsString(id++);
      setPreallocated(key, value);
   }
}

/**
 * Serialize as JSON
 */
json_t *StringMap::toJson() const
{
   json_t *root = json_array();
   StringMapEntry *entry, *tmp;
   HASH_ITER(hh, m_data, entry, tmp)
   {
      json_t *e = json_array();
      json_array_append_new(e, json_string_t(m_ignoreCase ? entry->originalKey : entry->key));
      json_array_append_new(e, json_string_t((TCHAR *)entry->value));
      json_array_append_new(root, e);
   }
   return root;
}
