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
** File: nxslext.cpp
**
**/

#include "nxcore.h"
#include <nxcore_ps.h>

/**
 * Externals
 */
void RegisterDCIFunctions(NXSL_Environment *pEnv);
int F_map(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm);
int F_mapList(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm);

int F_FindAlarmById(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm);
int F_FindAlarmByKey(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm);
int F_FindAlarmByKeyRegex(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm);

int F_GetSyslogRuleCheckCount(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm);
int F_GetSyslogRuleMatchCount(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm);

int F_GetServerQueueNames(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm);

/**
 * Get node's custom attribute
 * First argument is a node object, and second is an attribute name
 */
static int F_GetCustomAttribute(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	NetObj *netxmsObject = static_cast<shared_ptr<NetObj>*>(object->getData())->get();
	NXSL_Value *value = netxmsObject->getCustomAttributeForNXSL(vm, argv[1]->getValueAsCString());
	*ppResult = (value != nullptr) ? value : vm->createValue(); // Return nullptr if attribute not found
	return 0;
}

/**
 * Set node's custom attribute
 * First argument is a node object, second is an attribute name, third is new value
 * Returns previous value
 */
static int F_SetCustomAttribute(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isString() || !argv[2]->isString())
		return NXSL_ERR_NOT_STRING;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	NetObj *netxmsObject = static_cast<shared_ptr<NetObj>*>(object->getData())->get();
   NXSL_Value *value = netxmsObject->getCustomAttributeForNXSL(vm, argv[1]->getValueAsCString());
   *ppResult = (value != nullptr) ? value : vm->createValue(); // Return nullptr if attribute not found
	netxmsObject->setCustomAttribute(argv[1]->getValueAsCString(), argv[2]->getValueAsCString(), StateChange::IGNORE);
	return 0;
}

/**
 * Delete node's custom attribute
 * First argument is a node object, second is an attribute name
 */
static int F_DeleteCustomAttribute(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	NXSL_Object *object;

	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	NetObj *netxmsObject = static_cast<shared_ptr<NetObj>*>(object->getData())->get();
	netxmsObject->deleteCustomAttribute(argv[1]->getValueAsCString());
   *ppResult = vm->createValue();
	return 0;
}

/**
 * Get interface name by index
 * Parameters: node object and interface index
 */
static int F_GetInterfaceName(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isInteger())
		return NXSL_ERR_NOT_INTEGER;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	shared_ptr<Interface> iface = static_cast<shared_ptr<Node>*>(object->getData())->get()->findInterfaceByIndex(argv[1]->getValueAsUInt32());
	*result = (iface != nullptr) ? vm->createValue(iface->getName()) : vm->createValue();
	return 0;
}

/**
 * Get interface object by index
 * Parameters: node object and interface index
 */
static int F_GetInterfaceObject(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isInteger())
		return NXSL_ERR_NOT_INTEGER;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	shared_ptr<Interface> iface = static_cast<shared_ptr<Node>*>(object->getData())->get()->findInterfaceByIndex(argv[1]->getValueAsUInt32());
	*result = (iface != nullptr) ? iface->createNXSLObject(vm) : vm->createValue();
	return 0;
}

/**
 * Get ID of node representing NetXMS server.
 */
static int F_GetServerNodeId(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   *result = vm->createValue(g_dwMgmtNode);
   return 0;
}

/**
 * Get node object representing NetXMS server.
 * Optional first argument: current node object or null
 */
static int F_GetServerNode(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (argc > 1)
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

   shared_ptr<Node> currNode;
   if ((argc > 0) && !argv[0]->isNull())
   {
      if (!argv[0]->isObject())
         return NXSL_ERR_NOT_OBJECT;

      NXSL_Object *object = argv[0]->getValueAsObject();
      if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
         return NXSL_ERR_BAD_CLASS;

      currNode = *static_cast<shared_ptr<Node>*>(object->getData());
   }

   shared_ptr<Node> serverNode = static_pointer_cast<Node>(FindObjectById(g_dwMgmtNode, OBJECT_NODE));
   if (serverNode != nullptr)
   {
      if (g_flags & AF_CHECK_TRUSTED_NODES)
      {
         if ((currNode != nullptr) && (serverNode->isTrustedNode(currNode->getId())))
         {
            *result = serverNode->createNXSLObject(vm);
         }
         else
         {
            // No access, return null
            *result = vm->createValue();
            nxlog_debug(4, _T("NXSL::GetServerNode(%s [%d]): access denied for node %s [%d]"),
                      (currNode != nullptr) ? currNode->getName() : _T("null"), (currNode != nullptr) ? currNode->getId() : 0,
                      argv[1]->getValueAsCString(), serverNode->getName(), serverNode->getId());
         }
      }
      else
      {
         *result = serverNode->createNXSLObject(vm);
      }
   }
   else
   {
      *result = vm->createValue();
   }
   return 0;
}

/**
 * Find node object
 * First argument: current node object or null
 * Second argument: node id or name
 * Returns node object or null if requested node was not found or access to it was denied
 */
static int F_FindNodeObject(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	shared_ptr<Node> currNode, node;

	if (!argv[0]->isNull())
	{
		if (!argv[0]->isObject())
			return NXSL_ERR_NOT_OBJECT;

		NXSL_Object *object = argv[0]->getValueAsObject();
		if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
			return NXSL_ERR_BAD_CLASS;

		currNode = *static_cast<shared_ptr<Node>*>(object->getData());
	}

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	if (argv[1]->isInteger())
	{
		node = static_pointer_cast<Node>(FindObjectById(argv[1]->getValueAsUInt32(), OBJECT_NODE));
	}
	else
	{
		node = static_pointer_cast<Node>(FindObjectByName(argv[1]->getValueAsCString(), OBJECT_NODE));
	}

	if (node != nullptr)
	{
		if (g_flags & AF_CHECK_TRUSTED_NODES)
		{
			if ((currNode != nullptr) && (node->isTrustedNode(currNode->getId())))
			{
				*ppResult = node->createNXSLObject(vm);
			}
			else
			{
				// No access, return null
				*ppResult = vm->createValue();
				DbgPrintf(4, _T("NXSL::FindNodeObject(%s [%d], '%s'): access denied for node %s [%d]"),
				          (currNode != nullptr) ? currNode->getName() : _T("null"), (currNode != nullptr) ? currNode->getId() : 0,
							 argv[1]->getValueAsCString(), node->getName(), node->getId());
			}
		}
		else
		{
			*ppResult = node->createNXSLObject(vm);
		}
	}
	else
	{
		// Node not found, return null
		*ppResult = vm->createValue();
	}

	return 0;
}

/**
 * Generic "find object" functions implementation
 * First argument: search criteria
 * Second argument (optional): current node object or null
 * Returns generic object or null if requested object was not found or access to it was denied
 */
static int F_FindObjectImpl(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm, const TCHAR *fname, shared_ptr<NetObj> (*finder)(NXSL_Value *))
{
	if ((argc !=1) && (argc != 2))
		return NXSL_ERR_INVALID_ARGUMENT_COUNT;

	if (!argv[0]->isString())
		return NXSL_ERR_NOT_STRING;

	if (argc == 2 && (!argv[1]->isNull() && !argv[1]->isObject()))
		return NXSL_ERR_NOT_OBJECT;

   shared_ptr<NetObj> object = finder(argv[0]);
	if (object != nullptr)
	{
		if (g_flags & AF_CHECK_TRUSTED_NODES)
		{
			Node *currNode = nullptr;
			if ((argc == 2) && !argv[1]->isNull())
			{
				NXSL_Object *o = argv[1]->getValueAsObject();
				if (!o->getClass()->instanceOf(g_nxslNodeClass.getName()))
					return NXSL_ERR_BAD_CLASS;

				currNode = static_cast<shared_ptr<Node>*>(o->getData())->get();
			}
			if ((currNode != nullptr) && (object->isTrustedNode(currNode->getId())))
			{
				*result = object->createNXSLObject(vm);
			}
			else
			{
				// No access, return null
				*result = vm->createValue();
				nxlog_debug_tag(_T("nxsl.objects"), 4, _T("%s('%s', %s [%d]): object %s [%d] found but trusted node check failed"),
                   fname, argv[0]->getValueAsCString(),
                   (currNode != nullptr) ? currNode->getName() : _T("null"), (currNode != nullptr) ? currNode->getId() : 0,
                   object->getName(), object->getId());
			}
		}
		else
		{
			*result = object->createNXSLObject(vm);;
		}
	}
	else
	{
		// Object not found, return null
		*result = vm->createValue();
	}

	return 0;
}

/**
 * Finder for F_FindObjectImpl: find by object name or ID
 */
static shared_ptr<NetObj> FindObjectByNameOrId(NXSL_Value *v)
{
   return v->isInteger() ? FindObjectById(v->getValueAsUInt32()) : FindObjectByName(v->getValueAsCString(), -1);
}

/**
 * Find object by name or ID
 * First argument: object id or name
 * Second argument (optional): current node object or null
 * Returns generic object or null if requested object was not found or access to it was denied
 */
static int F_FindObject(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   return F_FindObjectImpl(argc, argv, result, vm, _T("FindObject"), FindObjectByNameOrId);
}

/**
 * Finder for F_FindObjectImpl: find object by GUID
 */
static shared_ptr<NetObj> FindObjectByGUID(NXSL_Value *v)
{
   uuid id = uuid::parse(v->getValueAsCString());
   return !id.isNull() ? FindObjectByGUID(id) : shared_ptr<NetObj>();
}

/**
 * Find object by GUID
 * First argument: object id or name
 * Second argument (optional): current node object or null
 * Returns generic object or null if requested object was not found or access to it was denied
 */
static int F_FindObjectByGUID(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   return F_FindObjectImpl(argc, argv, result, vm, _T("FindObjectByGUID"), FindObjectByGUID);
}

/**
 * Finder for F_FindObjectImpl: find node by IP address
 */
static shared_ptr<NetObj> FindNodeByIPAddress(NXSL_Value *v)
{
   InetAddress addr = InetAddress::parse(v->getValueAsCString());
   return addr.isValidUnicast() ? FindNodeByIP(0, true, addr) : shared_ptr<Node>();
}

/**
 * Find node by IP address
 * First argument: object id or name
 * Second argument (optional): current node object or null
 * Returns generic object or null if requested object was not found or access to it was denied
 */
static int F_FindNodeByIPAddress(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   return F_FindObjectImpl(argc, argv, result, vm, _T("FindNodeByIPAddress"), FindNodeByIPAddress);
}

/**
 * Finder for F_FindObjectImpl: find node by MAC address
 */
static shared_ptr<NetObj> FindNodeByMACAddress(NXSL_Value *v)
{
   MacAddress addr = MacAddress::parse(v->getValueAsCString());
   return addr.isValid() ? FindNodeByMAC(addr) : shared_ptr<Node>();
}

/**
 * Find node by MAC address
 * First argument: object id or name
 * Second argument (optional): current node object or null
 * Returns generic object or null if requested object was not found or access to it was denied
 */
static int F_FindNodeByMACAddress(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   return F_FindObjectImpl(argc, argv, result, vm, _T("FindNodeByMACAddress"), FindNodeByMACAddress);
}

/**
 * Finder for F_FindObjectImpl: find node by agent ID
 */
static shared_ptr<NetObj> FindNodeByAgentId(NXSL_Value *v)
{
   uuid id = uuid::parse(v->getValueAsCString());
   return !id.isNull() ? FindNodeByAgentId(id) : shared_ptr<Node>();
}

/**
 * Find node by agent ID
 * First argument: object id or name
 * Second argument (optional): current node object or null
 * Returns generic object or null if requested object was not found or access to it was denied
 */
static int F_FindNodeByAgentId(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   return F_FindObjectImpl(argc, argv, result, vm, _T("FindNodeByAgentId"), FindNodeByAgentId);
}

/**
 * Finder for F_FindObjectImpl: find node by SNMP sysName
 */
static shared_ptr<NetObj> FindNodeBySysName(NXSL_Value *v)
{
   return FindNodeBySysName(v->getValueAsCString());
}

/**
 * Find node by SNMP sysName
 * First argument: object id or name
 * Second argument (optional): current node object or null
 * Returns generic object or null if requested object was not found or access to it was denied
 */
static int F_FindNodeBySysName(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   return F_FindObjectImpl(argc, argv, result, vm, _T("FindNodeBySysName"), FindNodeBySysName);
}

/**
 * Get node object's parents
 * First argument: node object
 * Returns array of accessible parent objects
 */
static int F_GetNodeParents(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	Node *node = static_cast<shared_ptr<Node>*>(object->getData())->get();
	*result = node->getParentsForNXSL(vm);
	return 0;
}

/**
 * Get node object's templates
 * First argument: node object
 * Returns array of accessible template objects
 */
static int F_GetNodeTemplates(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	Node *node = static_cast<shared_ptr<Node>*>(object->getData())->get();
	*ppResult = vm->createValue(node->getTemplatesForNXSL(vm));
	return 0;
}

/**
 * Get object's parents
 * First argument: NetXMS object (NetObj, Node, or Interface)
 * Returns array of accessible parent objects
 */
static int F_GetObjectParents(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	NetObj *netobj = static_cast<shared_ptr<NetObj>*>(object->getData())->get();
	*result = netobj->getParentsForNXSL(vm);
	return 0;
}

/**
 * Get object's children
 * First argument: NetXMS object (NetObj, Node, or Interface)
 * Returns array of accessible child objects
 */
static int F_GetObjectChildren(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   NetObj *netobj = static_cast<shared_ptr<NetObj>*>(object->getData())->get();
	*result = netobj->getChildrenForNXSL(vm);
	return 0;
}

/**
 * Get node's interfaces
 * First argument: node object
 * Returns array of interface objects
 */
static int F_GetNodeInterfaces(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   Node *node = static_cast<shared_ptr<Node>*>(object->getData())->get();
	*result = node->getInterfacesForNXSL(vm);
	return 0;
}

/**
 * Get all nodes
 * Returns array of accessible node objects
 * (empty array if trusted nodes check is on and current node not provided)
 */
static int F_GetAllNodes(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (argc > 1)
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

   Node *node = nullptr;
   if (argc > 0)
   {
      if (!argv[0]->isObject())
         return NXSL_ERR_NOT_OBJECT;

      NXSL_Object *object = argv[0]->getValueAsObject();
      if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
         return NXSL_ERR_BAD_CLASS;

      node = static_cast<shared_ptr<Node>*>(object->getData())->get();
   }

   NXSL_Array *a = new NXSL_Array(vm);
   if (!(g_flags & AF_CHECK_TRUSTED_NODES) || (node != nullptr))
   {
      unique_ptr<SharedObjectArray<NetObj>> nodes = g_idxNodeById.getObjects();
      int index = 0;
      for(int i = 0; i < nodes->size(); i++)
      {
         Node *n = (Node *)nodes->get(i);
         if ((node == nullptr) || n->isTrustedNode(node->getId()))
         {
            a->set(index++, n->createNXSLObject(vm));
         }
      }
   }
   *result = vm->createValue(a);
   return 0;
}

/**
 * Get event's named parameter
 * First argument: event object
 * Second argument: parameter's name
 * Returns parameter's value or null
 */
static int F_GetEventParameter(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslEventClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	Event *e = (Event *)object->getData();
	const TCHAR *value = e->getNamedParameter(argv[1]->getValueAsCString());
	*ppResult = (value != nullptr) ? vm->createValue(value) : vm->createValue();
	return 0;
}

/**
 * Set event's named parameter
 * First argument: event object
 * Second argument: parameter's name
 * Third argument: new value
 */
static int F_SetEventParameter(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslEventClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	if (!argv[1]->isString() || !argv[2]->isString())
		return NXSL_ERR_NOT_STRING;

	Event *e = (Event *)object->getData();
	e->setNamedParameter(argv[1]->getValueAsCString(), argv[2]->getValueAsCString());
	*ppResult = vm->createValue();
	return 0;
}

/**
 * Post event
 * Syntax:
 *    PostEvent(node, event, tag, ...)
 * where:
 *     node - node object to send event on behalf of
 *     event - event code
 *     tag - user tag (optional)
 *     ... - optional parameters, will be passed as %1, %2, etc.
 */
static int F_PostEvent(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (argc < 2)
		return NXSL_ERR_INVALID_ARGUMENT_COUNT;

	// Validate first argument
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   Node *node = static_cast<shared_ptr<Node>*>(object->getData())->get();

	// Event code
	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	uint32_t eventCode = 0;
	if (argv[1]->isInteger())
	{
		eventCode = argv[1]->getValueAsUInt32();
	}
	else
	{
		eventCode = EventCodeFromName(argv[1]->getValueAsCString(), 0);
	}

	bool success;
	if (eventCode > 0)
	{
		// User tag
		const TCHAR *userTag = nullptr;
		if ((argc > 2) && !argv[2]->isNull())
		{
			if (!argv[2]->isString())
				return NXSL_ERR_NOT_STRING;
			userTag = argv[2]->getValueAsCString();
		}

		// Post event
		StringList parameters;
		for(int i = 3; i < argc; i++)
			parameters.add(argv[i]->getValueAsCString());
		success = PostEventWithTag(eventCode, EventOrigin::NXSL, 0, node->getId(), userTag, parameters);
	}
	else
	{
		success = false;
	}

	*result = vm->createValue(success);
	return 0;
}

/**
 * Post event (extended version)
 * Syntax:
 *    PostEventEx(node, event, parameters, tags, originTimestamp, origin)
 * where:
 *     node - node object to send event on behalf of
 *     event - event code or name
 *     parameters - event parameters (as array or hash map), will be passed as %1, %2, etc. (optional)
 *     tags - event tags (optional)
 *     originTimestamp - origin timestamp (optional, system timestamp will be used if omitted)
 *     origin - origin (optional, "SYSTEM" will be used if omitted)
 */
static int F_PostEventEx(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (argc < 2)
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

   // Validate first argument
   if (!argv[0]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   Node *node = static_cast<shared_ptr<Node>*>(object->getData())->get();

   // Event code
   if (!argv[1]->isString())
      return NXSL_ERR_NOT_STRING;

   uint32_t eventCode = 0;
   if (argv[1]->isInteger())
   {
      eventCode = argv[1]->getValueAsUInt32();
   }
   else
   {
      eventCode = EventCodeFromName(argv[1]->getValueAsCString(), 0);
   }

   bool success;
   if (eventCode > 0)
   {
      success = true;

      // Parameters
      StringMap parameters;
      if ((argc > 2) && !argv[2]->isNull())
      {
         if (argv[2]->isArray())
         {
            NXSL_Array *value = argv[2]->getValueAsArray();
            for(int i = 0; i < value->size(); i++)
            {
               TCHAR name[64];
               _sntprintf(name, 64, _T("Parameter%d"), i + 1);
               parameters.set(name, value->getByPosition(i)->getValueAsCString());
            }
         }
         else if (argv[2]->isHashMap())
         {
            argv[2]->getValueAsHashMap()->toStringMap(&parameters);
         }
         else
         {
            success = false;
         }
      }

      // Tags
      StringSet tags;
      if ((argc > 3) && !argv[3]->isNull())
      {
         if (argv[3]->isArray())
         {
            argv[3]->getValueAsArray()->toStringSet(&tags);
         }
         else
         {
            success = false;
         }
      }

      // Origin timestamp
      time_t originTimestamp = 0;
      if ((argc > 4) && argv[4]->isInteger())
      {
         originTimestamp = static_cast<time_t>(argv[4]->getValueAsUInt64());
      }

      // Origin
      EventOrigin eventOrigin = EventOrigin::NXSL;
      if ((argc > 5) && argv[5]->isInteger())
      {
         int value = argv[5]->getValueAsInt32();
         if ((value >= 0) && (value <= 7))
            eventOrigin = static_cast<EventOrigin>(value);
      }

      if (success)
         success = PostEventWithTagsAndNames(eventCode, eventOrigin, originTimestamp, node->getId(), &tags, &parameters);
   }
   else
   {
      success = false;
   }

   *result = vm->createValue(success);
   return 0;
}

/**
 * Load event from database by ID
 */
static int F_LoadEvent(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isInteger())
      return NXSL_ERR_NOT_INTEGER;

   Event *e = FindEventInLoggerQueue(argv[0]->getValueAsUInt64());
   if (e == nullptr)
      e = LoadEventFromDatabase(argv[0]->getValueAsUInt64());
   *result = (e != nullptr) ? vm->createValue(vm->createObject(&g_nxslEventClass, e, false)) : vm->createValue();
   return 0;
}

/**
 * Create node object
 * Syntax:
 *    CreateNode(parent, name, primaryHostName, zoneUIN)
 * where:
 *     parent          - parent object
 *     name            - name for new node
 *     primaryHostName - primary host name for new node (optional)
 *     zoneUIN         - zone UIN (optional)
 * Return value:
 *     new node object or null on failure
 */
static int F_CreateNode(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if ((argc < 2) || (argc > 4))
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *obj = argv[0]->getValueAsObject();
	if (!obj->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	shared_ptr<NetObj> parent = *static_cast<shared_ptr<NetObj>*>(obj->getData());
	if ((parent->getObjectClass() != OBJECT_CONTAINER) && (parent->getObjectClass() != OBJECT_SERVICEROOT))
		return NXSL_ERR_BAD_CLASS;

	if (!argv[1]->isString() || ((argc > 2) && !argv[2]->isString()))
		return NXSL_ERR_NOT_STRING;

	if ((argc > 3) && !argv[3]->isInteger())
	   return NXSL_ERR_NOT_INTEGER;

	const TCHAR *pname;
	if (argc > 2)
	{
	   pname = argv[2]->getValueAsCString();
	   if (*pname == 0)
	      pname = argv[1]->getValueAsCString();
	}
	else
	{
      pname = argv[1]->getValueAsCString();
	}
	NewNodeData newNodeData(InetAddress::resolveHostName(pname));
	_tcslcpy(newNodeData.name, argv[1]->getValueAsCString(), MAX_OBJECT_NAME);
	newNodeData.zoneUIN = (argc > 3) ? argv[3]->getValueAsUInt32() : 0;
	newNodeData.doConfPoll = true;

   shared_ptr<Node> node = PollNewNode(&newNodeData);
	if (node != nullptr)
	{
		node->setPrimaryHostName(pname);
		parent->addChild(node);
		node->addParent(parent);
		node->unhide();
		*result = node->createNXSLObject(vm);
	}
	else
	{
		*result = vm->createValue();
	}
	return 0;
}

/**
 * Create container object
 * Syntax:
 *    CreateContainer(parent, name)
 * where:
 *     parent - parent object
 *     name   - name for new container
 * Return value:
 *     new container object
 */
static int F_CreateContainer(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *obj = argv[0]->getValueAsObject();
	if (!obj->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	shared_ptr<NetObj> parent = *static_cast<shared_ptr<NetObj>*>(obj->getData());
	if ((parent->getObjectClass() != OBJECT_CONTAINER) && (parent->getObjectClass() != OBJECT_SERVICEROOT))
		return NXSL_ERR_BAD_CLASS;

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	const TCHAR *name = argv[1]->getValueAsCString();

	shared_ptr<Container> container = make_shared<Container>(name, 0);
	NetObjInsert(container, true, false);
	parent->addChild(container);
	container->addParent(parent);
	container->unhide();

	*ppResult = container->createNXSLObject(vm);
	return 0;
}

/**
 * Delete object
 * Syntax:
 *    DeleteObject(object)
 * where:
 *     object - object to remove, must be of class NetObj, Interface, or Node
 * Return value:
 *     null
 */
static int F_DeleteObject(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *obj = argv[0]->getValueAsObject();
   if (!obj->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   static_cast<shared_ptr<NetObj>*>(obj->getData())->get()->deleteObject();

	*ppResult = vm->createValue();
	return 0;
}

/**
 * Bind object to container
 * Syntax:
 *    BindObject(parent, child)
 * where:
 *     parent - container object
 *     child  - either node or container or subnet to be bound to parent
 * Return value:
 *     null
 */
static int F_BindObject(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
   if (!argv[0]->isObject() || !argv[1]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   NXSL_Object *nxslParent = argv[0]->getValueAsObject();
   if (!nxslParent->getClass()->instanceOf(g_nxslNetObjClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   shared_ptr<NetObj> parent = *static_cast<shared_ptr<NetObj>*>(nxslParent->getData());
   if ((parent->getObjectClass() != OBJECT_CONTAINER) && (parent->getObjectClass() != OBJECT_SERVICEROOT))
      return NXSL_ERR_BAD_CLASS;

   NXSL_Object *nxslChild = argv[1]->getValueAsObject();
   if (!nxslChild->getClass()->instanceOf(g_nxslNetObjClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   shared_ptr<NetObj> child = *static_cast<shared_ptr<NetObj>*>(nxslChild->getData());
   if (!IsValidParentClass(child->getObjectClass(), parent->getObjectClass()))
      return NXSL_ERR_BAD_CLASS;

   if (child->isChild(parent->getId())) // prevent loops
      return NXSL_ERR_INVALID_OBJECT_OPERATION;

   parent->addChild(child);
   child->addParent(parent);
   parent->calculateCompoundStatus();

   *ppResult = vm->createValue();
   return 0;
}

/**
 * Remove (unbind) object from container
 * Syntax:
 *    UnbindObject(parent, child)
 * where:
 *     parent - container object
 *     child  - either node or container or subnet to be removed from container
 * Return value:
 *     null
 */
static int F_UnbindObject(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
   if (!argv[0]->isObject() || !argv[1]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   NXSL_Object *nxslParent = argv[0]->getValueAsObject();
   if (!nxslParent->getClass()->instanceOf(g_nxslNetObjClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   NetObj *parent = static_cast<shared_ptr<NetObj>*>(nxslParent->getData())->get();
   if ((parent->getObjectClass() != OBJECT_CONTAINER) && (parent->getObjectClass() != OBJECT_SERVICEROOT))
      return NXSL_ERR_BAD_CLASS;

   NXSL_Object *nxslChild = argv[1]->getValueAsObject();
   if (!nxslChild->getClass()->instanceOf(g_nxslNetObjClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   NetObj *child = static_cast<shared_ptr<NetObj>*>(nxslChild->getData())->get();
   parent->deleteChild(*child);
   child->deleteParent(*parent);

   *ppResult = vm->createValue();
   return 0;
}

/**
 * Rename object
 * Syntax:
 *    RenameObject(object, name)
 * where:
 *     object - NetXMS object (Node, Interface, or NetObj)
 *     name   - new name for object
 * Return value:
 *     null
 */
static int F_RenameObject(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
   if (!argv[0]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   if (!argv[1]->isString())
      return NXSL_ERR_NOT_STRING;

   NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   static_cast<shared_ptr<NetObj>*>(object->getData())->get()->setName(argv[1]->getValueAsCString());

   *ppResult = vm->createValue();
   return 0;
}

/**
 * Manage object (set to managed state)
 * Syntax:
 *    ManageObject(object)
 * where:
 *     object - NetXMS object (Node, Interface, or NetObj)
 * Return value:
 *     null
 */
static int F_ManageObject(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   static_cast<shared_ptr<NetObj>*>(object->getData())->get()->setMgmtStatus(true);

	*ppResult = vm->createValue();
	return 0;
}

/**
 * Unmanage object (set to unmanaged state)
 * Syntax:
 *    UnmanageObject(object)
 * where:
 *     object - NetXMS object (Node, Interface, or NetObj)
 * Return value:
 *     null
 */
static int F_UnmanageObject(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   static_cast<shared_ptr<NetObj>*>(object->getData())->get()->setMgmtStatus(false);

	*ppResult = vm->createValue();
	return 0;
}

/**
 * EnterMaintenance object (set to unmanaged state)
 * Syntax:
 *    EnterMaintenance(object, comments)
 * where:
 *     object - NetXMS object (Node, Interface, or NetObj)
 * Return value:
 *     null
 */
static int F_EnterMaintenance(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
   if ((argc < 1) || (argc > 2))
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

   if ((argc > 1) && !argv[1]->isString())
      return NXSL_ERR_NOT_STRING;

	NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   static_cast<shared_ptr<NetObj>*>(object->getData())->get()->enterMaintenanceMode(0, (argc > 1) ? argv[1]->getValueAsCString() : nullptr);

	*ppResult = vm->createValue();
	return 0;
}

/**
 * LeaveMaintenance object (set to unmanaged state)
 * Syntax:
 *    LeaveMaintenance(object)
 * where:
 *     object - NetXMS object (Node, Interface, or NetObj)
 * Return value:
 *     null
 */
static int F_LeaveMaintenance(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   static_cast<shared_ptr<NetObj>*>(object->getData())->get()->leaveMaintenanceMode(0);

	*ppResult = vm->createValue();
	return 0;
}

/**
 * Set interface expected state
 * Syntax:
 *    SetInterfaceExpectedState(interface, state)
 * where:
 *     interface - Interface object
 *     state     - state ID or name. Possible values: 0 "UP", 1 "DOWN", 2 "IGNORE"
 * Return value:
 *     null
 */
static int F_SetInterfaceExpectedState(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslInterfaceClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	int state;
	if (argv[1]->isInteger())
	{
		state = argv[1]->getValueAsInt32();
	}
	else if (argv[1]->isString())
	{
		static const TCHAR *stateNames[] = { _T("UP"), _T("DOWN"), _T("IGNORE"), nullptr };
		const TCHAR *name = argv[1]->getValueAsCString();
		for(state = 0; stateNames[state] != nullptr; state++)
			if (!_tcsicmp(stateNames[state], name))
				break;
	}
	else
	{
		return NXSL_ERR_NOT_STRING;
	}

	if ((state >= 0) && (state <= 2))
	   static_cast<shared_ptr<Interface>*>(object->getData())->get()->setExpectedState(state);

	*ppResult = vm->createValue();
	return 0;
}

/**
 * Create new SNMP transport object
 * Syntax:
 *    CreateSNMPTransport(node, [port], [community], [context])
 * where:
 *     node    - node to create SNMP transport for
 *     port    - SNMP port (optional)
 *     context - SNMP context (optional)
 * Return value:
 *     new SNMP_Transport object
 */
static int F_CreateSNMPTransport(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
   if ((argc < 1) || (argc > 4))
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

   if ((argc > 1) && !argv[1]->isNull() && !argv[1]->isInteger())
      return NXSL_ERR_NOT_INTEGER;

   if ((argc > 2) && !argv[2]->isNull() && !argv[2]->isString())
      return NXSL_ERR_NOT_STRING;

   if ((argc > 3) && !argv[3]->isNull() && !argv[3]->isString())
      return NXSL_ERR_NOT_STRING;

   NXSL_Object *obj = argv[0]->getValueAsObject();
   if (!obj->getClass()->instanceOf(g_nxslNodeClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   Node *node = static_cast<shared_ptr<Node> *>(obj->getData())->get();
   if (node != nullptr)
   {
      uint16_t port = ((argc > 1) && argv[1]->isInteger()) ? static_cast<uint16_t>(argv[1]->getValueAsInt32()) : 0;
      const char *community = ((argc > 2) && argv[2]->isString()) ? argv[2]->getValueAsMBString() : nullptr;
      const char *context = ((argc > 3) && argv[3]->isString()) ? argv[3]->getValueAsMBString() : nullptr;
      SNMP_Transport *t = node->createSnmpTransport(port, SNMP_VERSION_DEFAULT, context, community);
      *ppResult = (t != nullptr) ? vm->createValue(vm->createObject(&g_nxslSnmpTransportClass, t)) : vm->createValue();
   }
   else
   {
      *ppResult = vm->createValue();
   }

   return 0;
}

/**
 * Do SNMP GET for the given object id
 * Syntax:
 *    SNMPGet(transport, oid)
 * where:
 *     transport - NXSL transport object
 *		 oid - SNMP object id
 * Return value:
 *     new SNMP_VarBind object
 */
static int F_SNMPGet(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   if (!argv[1]->isString())
      return NXSL_ERR_NOT_STRING;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslSnmpTransportClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   // Create PDU and send request
   uint32_t oid[MAX_OID_LEN];
   size_t nameLen = SNMPParseOID(argv[1]->getValueAsCString(), oid, MAX_OID_LEN);
   if (nameLen == 0)
   {
      *result = vm->createValue();
      return 0;
   }

   SNMP_Transport *transport = static_cast<SNMP_Transport*>(object->getData());

   SNMP_PDU request(SNMP_GET_REQUEST, SnmpNewRequestId(), transport->getSnmpVersion());
	request.bindVariable(new SNMP_Variable(oid, nameLen));

	SNMP_PDU *response;
   if (transport->doRequest(&request, &response, SnmpGetDefaultTimeout(), 3) == SNMP_ERR_SUCCESS)
   {
      if ((response->getNumVariables() > 0) && (response->getErrorCode() == SNMP_PDU_ERR_SUCCESS))
      {
         SNMP_Variable *pVar = response->getVariable(0);
		   *result = vm->createValue(vm->createObject(&g_nxslSnmpVarBindClass, pVar));
         response->unlinkVariables();
	   }
      else
      {
   		*result = vm->createValue();
      }
      delete response;
   }
	else
	{
		*result = vm->createValue();
	}
	return 0;
}

/**
 * Do SNMP GET for the given object id
 * Syntax:
 *    SNMPGetValue(transport, oid)
 * where:
 *     transport - NXSL transport object
 *		 oid - SNMP object id
 * Return value:
 *     value for the given oid
 */
static int F_SNMPGetValue(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslSnmpTransportClass.getName()))
		return NXSL_ERR_BAD_CLASS;

   SNMP_Transport *transport = static_cast<SNMP_Transport*>(object->getData());

   TCHAR buffer[4096];
	if (SnmpGetEx(transport, argv[1]->getValueAsCString(), nullptr, 0, buffer, sizeof(buffer), SG_STRING_RESULT, nullptr) == SNMP_ERR_SUCCESS)
	{
		*result = vm->createValue(buffer);
	}
	else
	{
		*result = vm->createValue();
	}

	return 0;
}

/**
 * Do SNMP SET for the given object id
 * Syntax:
 *    SNMPSet(transport, oid, value, [data_type])
 * where:
 *     transport - NXSL transport object
 *		 oid - SNMP object id
 *		 value - value to set
 *		 data_type (optional)
 * Return value:
 *     value for the given oid
 */
static int F_SNMPSet(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (argc < 3 || argc > 4)
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;
   if (!argv[0]->isObject())
      return NXSL_ERR_NOT_OBJECT;
   if (!argv[1]->isString() || (!argv[2]->isString() && !argv[2]->isObject(_T("ByteStream"))) || ((argc == 4) && !argv[3]->isString()))
      return NXSL_ERR_NOT_STRING;

   NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslSnmpTransportClass.getName()))
      return NXSL_ERR_BAD_CLASS;
   SNMP_Transport *transport = static_cast<SNMP_Transport*>(object->getData());

   SNMP_PDU request(SNMP_SET_REQUEST, SnmpNewRequestId(), transport->getSnmpVersion());
   SNMP_PDU *response = nullptr;
   bool success = false;
   if (SNMPIsCorrectOID(argv[1]->getValueAsCString()))
   {
      SNMP_Variable *var = new SNMP_Variable(argv[1]->getValueAsCString());
      if (argc == 3)
      {
         if (argv[2]->isObject())
            var->setValueFromByteStream(ASN_OCTET_STRING, *static_cast<ByteStream*>(argv[2]->getValueAsObject()->getData()));
         else
            var->setValueFromString(ASN_OCTET_STRING, argv[2]->getValueAsCString());
      }
      else
      {
         uint32_t dataType = SNMPResolveDataType(argv[3]->getValueAsCString());
         if (dataType == ASN_NULL)
         {
            nxlog_debug_tag(_T("snmp.nxsl"), 6, _T("SNMPSet: failed to resolve data type '%s', assume string"),
               argv[3]->getValueAsCString());
            dataType = ASN_OCTET_STRING;
         }
         if (argv[2]->isObject())
            var->setValueFromByteStream(dataType, *static_cast<ByteStream*>(argv[2]->getValueAsObject()->getData()));
         else
            var->setValueFromString(dataType, argv[2]->getValueAsCString());
      }
      request.bindVariable(var);
      success = true;
   }
   else
   {
      nxlog_debug_tag(_T("snmp.nxsl"), 6, _T("SNMPSet: Invalid OID: %s"), argv[1]->getValueAsCString());
      success = false;
   }

   // Send request and process response
   if (success)
   {
      success = false;
      uint32_t snmpResult = transport->doRequest(&request, &response, SnmpGetDefaultTimeout(), 3);
      if (snmpResult == SNMP_ERR_SUCCESS)
      {
         if (response->getErrorCode() == SNMP_PDU_ERR_SUCCESS)
         {
            nxlog_debug_tag(_T("snmp.nxsl"), 6, _T("SNMPSet: success"));
            success = true;
         }
         else
         {
            nxlog_debug_tag(_T("snmp.nxsl"), 6, _T("SNMPSet: operation failed (error code %d)"), response->getErrorCode());
         }
         delete response;
      }
      else
      {
         nxlog_debug_tag(_T("snmp.nxsl"), 6, _T("SNMPSet: %s"), SNMPGetErrorText(snmpResult));
      }
   }

   *result = vm->createValue(success);
   return 0;
}

/**
 * SNMP walk callback
 */
static uint32_t WalkCallback(SNMP_Variable *var, SNMP_Transport *transport, void *context)
{
   NXSL_VM *vm = static_cast<NXSL_VM*>(static_cast<NXSL_Array*>(context)->vm());
   static_cast<NXSL_Array*>(context)->append(vm->createValue(vm->createObject(&g_nxslSnmpVarBindClass, new SNMP_Variable(var))));
   return SNMP_ERR_SUCCESS;
}

/**
 * Do SNMP walk starting from the given oid
 * Syntax:
 *    SNMPWalk(transport, oid)
 * where:
 *     transport - NXSL transport object
 *		 oid - SNMP object id
 * Return value:
 *     an array of VarBind objects
 */
static int F_SNMPWalk(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;
	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

   NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslSnmpTransportClass.getName()))
      return NXSL_ERR_BAD_CLASS;
   SNMP_Transport *transport = static_cast<SNMP_Transport*>(object->getData());

   NXSL_Array *varbinds = new NXSL_Array(vm);
   if (SnmpWalk(transport, argv[1]->getValueAsCString(), WalkCallback, varbinds) == SNMP_ERR_SUCCESS)
   {
      *result = vm->createValue(varbinds);
   }
   else
   {
      *result = vm->createValue();
      delete varbinds;
   }
   return 0;
}

/**
 * Execute agent command
 * Syntax:
 *    AgentExecuteCommand(object, command, ...)
 * where:
 *     object    - NetXMS node object
 *     command   - command to be executed
 * Return value:
 *     true on success
 */
static int F_AgentExecuteCommand(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (argc < 2)
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

   if (!argv[0]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   for(int i = 1; i < argc; i++)
      if (!argv[i]->isString())
         return NXSL_ERR_NOT_STRING;

   NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   Node *node = static_cast<shared_ptr<Node>*>(object->getData())->get();
   shared_ptr<AgentConnectionEx> conn = node->createAgentConnection();
   if (conn != nullptr)
   {
      StringList list;
      for(int i = 2; (i < argc) && (i < 128); i++)
         list.add(argv[i]->getValueAsCString());
      uint32_t rcc = conn->executeCommand(argv[1]->getValueAsCString(), list);
      *result = vm->createValue(rcc == ERR_SUCCESS);
      nxlog_debug(5, _T("NXSL: F_AgentExecuteCommand: command \"%s\" on node %s [%u]: RCC=%u"), argv[1]->getValueAsCString(), node->getName(), node->getId(), rcc);
   }
   else
   {
      *result = vm->createValue(false);
   }
   return 0;
}

/**
 * Execute agent action
 * Syntax:
 *    AgentExecuteCommandWithOutput(object, command, ...)
 * where:
 *     object    - NetXMS node object
 *     command   - command to be executed
 * Return value:
 *     command output on success and null on failure
 */
static int F_AgentExecuteCommandWithOutput(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (argc < 2)
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

   if (!argv[0]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   for(int i = 1; i < argc; i++)
      if (!argv[i]->isString())
         return NXSL_ERR_NOT_STRING;

   NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   Node *node = static_cast<shared_ptr<Node>*>(object->getData())->get();
   shared_ptr<AgentConnectionEx> conn = node->createAgentConnection();
   if (conn != nullptr)
   {
      StringList list;
      for(int i = 2; (i < argc) && (i < 128); i++)
         list.add(argv[i]->getValueAsCString());
      StringBuffer output;
      uint32_t rcc = conn->executeCommand(argv[1]->getValueAsCString(), list, true, [](ActionCallbackEvent event, const TCHAR *text, void *context) {
         if (event == ACE_DATA)
            static_cast<StringBuffer*>(context)->append(text);
      }, &output);
      *result = (rcc == ERR_SUCCESS) ? vm->createValue(output) : vm->createValue();
      nxlog_debug(5, _T("NXSL: F_AgentExecuteCommandWithOutput: command \"%s\" on node %s [%u]: RCC=%u"), argv[1]->getValueAsCString(), node->getName(), node->getId(), rcc);
   }
   else
   {
      *result = vm->createValue();
   }
   return 0;
}

/**
 * Read parameter's value from agent
 * Syntax:
 *    AgentReadParameter(object, name)
 * where:
 *     object - NetXMS node object
 *     name   - name of the parameter
 * Return value:
 *     paramater's value on success and null on failure
 */
static int F_AgentReadParameter(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	TCHAR buffer[MAX_RESULT_LENGTH];
	UINT32 rcc = static_cast<shared_ptr<Node>*>(object->getData())->get()->getMetricFromAgent(argv[1]->getValueAsCString(), buffer, MAX_RESULT_LENGTH);
	*result = (rcc == DCE_SUCCESS) ? vm->createValue(buffer) : vm->createValue();
	return 0;
}

/**
 * Read table value from agent
 * Syntax:
 *    AgentReadTable(object, name)
 * where:
 *     object - NetXMS node object
 *     name   - name of the table
 * Return value:
 *     table value on success and null on failure
 */
static int F_AgentReadTable(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	shared_ptr<Table> table;
   uint32_t rcc = static_cast<shared_ptr<Node>*>(object->getData())->get()->getTableFromAgent(argv[1]->getValueAsCString(), &table);
   *result = (rcc == DCE_SUCCESS) ? vm->createValue(vm->createObject(&g_nxslTableClass, new shared_ptr<Table>(table))) : vm->createValue();
	return 0;
}

/**
 * Read list from agent
 * Syntax:
 *    AgentReadList(object, name)
 * where:
 *     object - NetXMS node object
 *     name   - name of the list
 * Return value:
 *     list values (as an array) on success and null on failure
 */
static int F_AgentReadList(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
	if (!argv[0]->isObject())
		return NXSL_ERR_NOT_OBJECT;

	if (!argv[1]->isString())
		return NXSL_ERR_NOT_STRING;

	NXSL_Object *object = argv[0]->getValueAsObject();
	if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
		return NXSL_ERR_BAD_CLASS;

	StringList *list;
	uint32_t rcc = static_cast<shared_ptr<Node>*>(object->getData())->get()->getListFromAgent(argv[1]->getValueAsCString(), &list);
   *result = (rcc == DCE_SUCCESS) ? vm->createValue(new NXSL_Array(vm, list)) : vm->createValue();
   delete list;
	return 0;
}

/**
 * Read parameter's value from network device driver
 * Syntax:
 *    DriverReadParameter(object, name)
 * where:
 *     object - NetXMS node object
 *     name   - name of the parameter
 * Return value:
 *     paramater's value on success and null on failure
 */
static int F_DriverReadParameter(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   if (!argv[1]->isString())
      return NXSL_ERR_NOT_STRING;

   NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNodeClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   TCHAR buffer[MAX_RESULT_LENGTH];
   uint32_t rcc = static_cast<shared_ptr<Node>*>(object->getData())->get()->getMetricFromDeviceDriver(argv[1]->getValueAsCString(), buffer, MAX_RESULT_LENGTH);
   *result = (rcc == DCE_SUCCESS) ? vm->createValue(buffer) : vm->createValue();
   return 0;
}

/**
 * Get server's configuration variable
 * First argument is a variable name
 * Optional second argumet is default value
 * Returns variable's value if found, default value if not found, and null if not found and no default value given
 */
static int F_GetConfigurationVariable(int argc, NXSL_Value **argv, NXSL_Value **ppResult, NXSL_VM *vm)
{
	if ((argc == 0) || (argc > 2))
		return NXSL_ERR_INVALID_ARGUMENT_COUNT;

	if (!argv[0]->isString())
		return NXSL_ERR_NOT_STRING;

	TCHAR buffer[MAX_CONFIG_VALUE];
	if (ConfigReadStr(argv[0]->getValueAsCString(), buffer, MAX_CONFIG_VALUE, _T("")))
	{
		*ppResult = vm->createValue(buffer);
	}
	else
	{
		*ppResult = (argc == 2) ? vm->createValue(argv[1]) : vm->createValue();
	}

	return 0;
}

/**
 * Get country alpha code from numeric code
 */
static int F_CountryAlphaCode(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   const TCHAR *code = CountryAlphaCode(argv[0]->getValueAsCString());
   *result = (code != nullptr) ? vm->createValue(code) : vm->createValue();
   return 0;
}

/**
 * Get country name from code
 */
static int F_CountryName(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   const TCHAR *name = CountryName(argv[0]->getValueAsCString());
   *result = (name != nullptr) ? vm->createValue(name) : vm->createValue();
   return 0;
}

/**
 * Get currency alpha code from numeric code
 */
static int F_CurrencyAlphaCode(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   const TCHAR *code = CurrencyAlphaCode(argv[0]->getValueAsCString());
   *result = (code != nullptr) ? vm->createValue(code) : vm->createValue();
   return 0;
}

/**
 * Get currency exponent
 */
static int F_CurrencyExponent(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   *result = vm->createValue(CurrencyExponent(argv[0]->getValueAsCString()));
   return 0;
}

/**
 * Get country name from code
 */
static int F_CurrencyName(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   const TCHAR *name = CurrencyName(argv[0]->getValueAsCString());
   *result = (name != nullptr) ? vm->createValue(name) : vm->createValue();
   return 0;
}

/**
 * Get event name from code
 */
static int F_EventNameFromCode(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isInteger())
      return NXSL_ERR_NOT_INTEGER;

   TCHAR eventName[MAX_EVENT_NAME];
   if (EventNameFromCode(argv[0]->getValueAsUInt32(), eventName))
      *result = vm->createValue(eventName);
   else
      *result = vm->createValue();
   return 0;
}

/**
 * Get event code from name
 */
static int F_EventCodeFromName(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   *result = vm->createValue(EventCodeFromName(argv[0]->getValueAsCString(), 0));
   return 0;
}

/**
 * Count scheduled tasks by key
 */
static int F_CountScheduledTasksByKey(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   *result = vm->createValue(CountScheduledTasksByKey(argv[0]->getValueAsCString()));
   return 0;
}

/**
 * Create user agent message
 * Syntax:
 *    CreateUserAgentMessage(object, message, startTime, endTime)
 * where:
 *     object        - NetXMS object
 *     message       - message to be sent
 *     startTime     - start time of message delivery
 *     endTime       - end time of message delivery
 *     showOnStartup - true to show message on startup
 * Return value:
 *     message id
 */
static int F_CreateUserAgentNotification(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isObject())
      return NXSL_ERR_NOT_OBJECT;

   if (!argv[1]->isString())
      return NXSL_ERR_NOT_STRING;

   if (!argv[2]->isNumeric() || !argv[3]->isNumeric())
      return NXSL_ERR_NOT_INTEGER;

   NXSL_Object *object = argv[0]->getValueAsObject();
   if (!object->getClass()->instanceOf(g_nxslNetObjClass.getName()))
      return NXSL_ERR_BAD_CLASS;

   uint32_t len = MAX_USER_AGENT_MESSAGE_SIZE;
   const TCHAR *message = argv[1]->getValueAsString(&len);
   IntegerArray<uint32_t> idList(16,16);
   idList.add(static_cast<shared_ptr<NetObj>*>(object->getData())->get()->getId());
   time_t startTime = (time_t)argv[2]->getValueAsUInt64();
   time_t endTime = (time_t)argv[3]->getValueAsUInt64();

   UserAgentNotificationItem *uan = CreateNewUserAgentNotification(message, idList, startTime, endTime, argv[3]->getValueAsBoolean(), 0);

   *result = vm->createValue(uan->getId());
   uan->decRefCount();
   return 0;
}

/**
 * Expand macros in string
 * Syntax:
 *    ExpandString(string, [object], [event])
 * Returnded value:
 *    expanded string
 */
static int F_ExpandString(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if ((argc < 1) || (argc > 3))
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   shared_ptr<NetObj> object;
   if (argc > 1)
   {
      if (!argv[1]->isObject(_T("NetObj")))
         return NXSL_ERR_NOT_OBJECT;
      object = *static_cast<shared_ptr<NetObj>*>(argv[1]->getValueAsObject()->getData());
   }
   else
   {
      object = FindObjectById(g_dwMgmtNode);
      if (object == nullptr)
         object = g_entireNetwork;
   }

   Event *event = nullptr;
   if (argc > 2)
   {
      if (!argv[2]->isObject(_T("Event")))
         return NXSL_ERR_NOT_OBJECT;
      event = static_cast<Event*>(argv[2]->getValueAsObject()->getData());
   }

   *result = vm->createValue(object->expandText(argv[0]->getValueAsCString(), nullptr, event, shared_ptr<DCObjectInfo>(), nullptr, nullptr, nullptr, nullptr, nullptr));
   return 0;
}

/**
 * Sends e-mail to specified recipients
 * Syntax:
 *    SendMail(recipients, subject, text, isHTML)
 *    isHTML is optional, if omitted assume false
 * Returned value:
 *    none
 */
static int F_SendMail(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if ((argc < 3) || (argc > 4))
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

	if (!argv[0]->isString() || !argv[1]->isString() || !argv[2]->isString())
		return NXSL_ERR_NOT_STRING;

	StringBuffer rcpts(argv[0]->getValueAsCString());
	rcpts.trim();
	const TCHAR *subj = argv[1]->getValueAsCString();
	const TCHAR *text = argv[2]->getValueAsCString();
	bool isHTML = (argc > 3) ? argv[3]->getValueAsBoolean() : false;

	if (!rcpts.isEmpty())
	{
		nxlog_debug_tag(_T("nxsl.sendmail"), 3, _T("Sending mail to %s: \"%s\""), rcpts.cstr(), text);
      TCHAR channelName[MAX_OBJECT_NAME];
      if (isHTML)
         ConfigReadStr(_T("DefaultNotificationChannel.SMTP.Html"), channelName, MAX_OBJECT_NAME, _T("SMTP-HTML"));
      else
         ConfigReadStr(_T("DefaultNotificationChannel.SMTP.Text"), channelName, MAX_OBJECT_NAME, _T("SMTP-Text"));

      SendNotification(channelName, rcpts.getBuffer(), subj, text);
	}
	else
	{
		nxlog_debug_tag(_T("nxsl.sendmail"), 3, _T("Empty recipients list - mail will not be sent"));
	}

   *result = vm->createValue();
   return 0;
}

/**
 * Sends notifications using provided channel to specified recipients
 * Syntax:
 *    SendNotification(channelName, recipients, subject, text)
 * Returned value:
 *    none
 */
static int F_SendNotification(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (argc != 4)
      return NXSL_ERR_INVALID_ARGUMENT_COUNT;

   if (!argv[0]->isString() || !argv[1]->isString() || !argv[2]->isString() || !argv[3]->isString())
      return NXSL_ERR_NOT_STRING;

   const TCHAR *channelName = argv[0]->getValueAsCString();
   StringBuffer rcpts(argv[1]->getValueAsCString());
   rcpts.trim();
   const TCHAR *subj = argv[2]->getValueAsCString();
   const TCHAR *text = argv[3]->getValueAsCString();

   if (!rcpts.isEmpty())
   {
      nxlog_debug_tag(_T("nxsl.sendntfy"), 3, _T("Sending notification using channel %s to %s: \"%s\""), channelName, rcpts.cstr(), text);
      SendNotification(channelName, rcpts.getBuffer(), subj, text);
   }
   else
   {
      nxlog_debug_tag(_T("nxsl.sendntfy"), 3, _T("Empty recipients list - notification will not be sent"));
   }

   *result = vm->createValue();
   return 0;
}

/**
 * Sends poller trace message to client (if poll initiated by client) and log it
 * Syntax:
 *    PollerTrace(text)
 * Returned value:
 *    none
 */
static int F_PollerTrace(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   auto context = static_cast<NetObj*>(vm->getUserData());
   if (context != nullptr)
   {
      const TCHAR *text = argv[0]->getValueAsCString();
      context->sendPollerMsg(_T("%s\r\n"), text);
      nxlog_debug_tag(_T("nxsl.pollertrace"), 4, _T("%s"), text);
   }
   *result = vm->createValue();
   return 0;
}

/**
 * Finds vendor name by MAC address
 * Syntax:
 *    FindVendorByMACAddress(text)
 * Returned value:
 *    String or null
 */
static int F_FindVendorByMACAddress(int argc, NXSL_Value **argv, NXSL_Value **result, NXSL_VM *vm)
{
   if (!argv[0]->isString())
      return NXSL_ERR_NOT_STRING;

   MacAddress addr = MacAddress::parse(argv[0]->getValueAsCString());
   if (addr.isValid())
   {
      *result = vm->createValue(FindVendorByMac(addr));
   }
   else
   {
      *result = vm->createValue();
   }
   return 0;
}

/**
 * Additional server functions to use within all scripts
 */
static NXSL_ExtFunction m_nxslServerFunctions[] =
{
	{ "map", F_map, -1 },
   { "mapList", F_mapList, -1 },
   { "AgentExecuteCommand", F_AgentExecuteCommand, -1 },
   { "AgentExecuteCommandWithOutput", F_AgentExecuteCommandWithOutput, -1 },
	{ "AgentReadList", F_AgentReadList, 2 },
	{ "AgentReadParameter", F_AgentReadParameter, 2 },
	{ "AgentReadTable", F_AgentReadTable, 2 },
	{ "CreateSNMPTransport", F_CreateSNMPTransport, -1 },
   { "CountryAlphaCode", F_CountryAlphaCode, 1 },
   { "CountryName", F_CountryName, 1 },
   { "CountScheduledTasksByKey", F_CountScheduledTasksByKey, 1 },
   { "CreateUserAgentNotification", F_CreateUserAgentNotification, 4},
   { "CurrencyAlphaCode", F_CurrencyAlphaCode, 1 },
   { "CurrencyExponent", F_CurrencyExponent, 1 },
   { "CurrencyName", F_CurrencyName, 1 },
	{ "DeleteCustomAttribute", F_DeleteCustomAttribute, 2 },
   { "DriverReadParameter", F_DriverReadParameter, 2 },
   { "EnterMaintenance", F_EnterMaintenance, -1 },
   { "EventNameFromCode", F_EventNameFromCode, 1 },
   { "EventCodeFromName", F_EventCodeFromName, 1 },
   { "ExpandString", F_ExpandString, -1 },
   { "GetAllNodes", F_GetAllNodes, -1 },
   { "GetConfigurationVariable", F_GetConfigurationVariable, -1 },
   { "GetCustomAttribute", F_GetCustomAttribute, 2 },
   { "GetEventParameter", F_GetEventParameter, 2 },
   { "GetInterfaceName", F_GetInterfaceName, 2 },
   { "GetInterfaceObject", F_GetInterfaceObject, 2 },
   { "GetNodeInterfaces", F_GetNodeInterfaces, 1 },
   { "GetNodeParents", F_GetNodeParents, 1 },
   { "GetNodeTemplates", F_GetNodeTemplates, 1 },
   { "GetObjectChildren", F_GetObjectChildren, 1 },
   { "GetObjectParents", F_GetObjectParents, 1 },
   { "GetServerNode", F_GetServerNode, -1 },
   { "GetServerNodeId", F_GetServerNodeId, 0 },
   { "GetServerQueueNames", F_GetServerQueueNames, 0 },
   { "GetSyslogRuleCheckCount", F_GetSyslogRuleCheckCount, -1 },
   { "GetSyslogRuleMatchCount", F_GetSyslogRuleMatchCount, -1 },
	{ "FindAlarmById", F_FindAlarmById, 1 },
	{ "FindAlarmByKey", F_FindAlarmByKey, 1 },
   { "FindAlarmByKeyRegex", F_FindAlarmByKeyRegex, 1 },
   { "FindNodeByAgentId", F_FindNodeByAgentId, -1 },
   { "FindNodeByIPAddress", F_FindNodeByIPAddress, -1 },
   { "FindNodeByMACAddress", F_FindNodeByMACAddress, -1 },
   { "FindNodeBySysName", F_FindNodeBySysName, -1 },
	{ "FindNodeObject", F_FindNodeObject, 2 },
	{ "FindObject", F_FindObject, -1 },
   { "FindObjectByGUID", F_FindObjectByGUID, -1 },
   { "FindVendorByMACAddress", F_FindVendorByMACAddress, 1 },
   { "LeaveMaintenance", F_LeaveMaintenance, 1 },
   { "LoadEvent", F_LoadEvent, 1 },
   { "ManageObject", F_ManageObject, 1 },
   { "PollerTrace", F_PollerTrace, 1 },
	{ "PostEvent", F_PostEvent, -1 },
   { "PostEventEx", F_PostEventEx, -1 },
	{ "RenameObject", F_RenameObject, 2 },
	{ "SendMail", F_SendMail, -1 },
	{ "SendNotification", F_SendNotification, 4 },
   { "SetCustomAttribute", F_SetCustomAttribute, 3 },
   { "SetEventParameter", F_SetEventParameter, 3 },
	{ "SetInterfaceExpectedState", F_SetInterfaceExpectedState, 2 },
	{ "SNMPGet", F_SNMPGet, 2 },
	{ "SNMPGetValue", F_SNMPGetValue, 2 },
	{ "SNMPSet", F_SNMPSet, -1 /* 3 or 4 */ },
	{ "SNMPWalk", F_SNMPWalk, 2 },
   { "UnmanageObject", F_UnmanageObject, 1 }
};

/**
 * Additional server functions to manage objects (disabled by default)
 */
static NXSL_ExtFunction m_nxslServerFunctionsForContainers[] =
{
	{ "BindObject", F_BindObject, 2 },
	{ "CreateContainer", F_CreateContainer, 2 },
	{ "CreateNode", F_CreateNode, -1 },
	{ "DeleteObject", F_DeleteObject, 1 },
	{ "UnbindObject", F_UnbindObject, 2 }
};

/*** NXSL_ServerEnv class implementation ***/

/**
 * Constructor for server default script environment
 */
NXSL_ServerEnv::NXSL_ServerEnv() : NXSL_Environment()
{
	m_console = nullptr;
	setLibrary(GetServerScriptLibrary());
	registerFunctionSet(sizeof(m_nxslServerFunctions) / sizeof(NXSL_ExtFunction), m_nxslServerFunctions);
	RegisterDCIFunctions(this);
	if (g_flags & AF_ENABLE_NXSL_CONTAINER_FUNCTIONS)
		registerFunctionSet(sizeof(m_nxslServerFunctionsForContainers) / sizeof(NXSL_ExtFunction), m_nxslServerFunctionsForContainers);
   if (g_flags & AF_ENABLE_NXSL_FILE_IO_FUNCTIONS)
      registerIOFunctions();
   CALL_ALL_MODULES(pfNXSLServerEnvConfig, (this));
}

/**
 * Script trace output
 */
void NXSL_ServerEnv::trace(int level, const TCHAR *text)
{
	if (level == 0)
	{
		nxlog_write_tag(NXLOG_INFO, _T("nxsl.trace"), _T("%s"), text);
	}
	else
	{
		nxlog_debug_tag(_T("nxsl.trace"), level, _T("%s"), text);
	}
}

/**
 * Print script output to console
 */
void NXSL_ServerEnv::print(const TCHAR *text)
{
	if (m_console != nullptr)
		ConsolePrintf(m_console, _T("%s"), CHECK_NULL(text));
}

/**
 * Additional VM configuration
 */
void NXSL_ServerEnv::configureVM(NXSL_VM *vm)
{
   NXSL_Environment::configureVM(vm);

   vm->setStorage(&g_nxslPstorage);

   CALL_ALL_MODULES(pfNXSLServerVMConfig, (vm));
}

/**
 * Get constant value defined by this environment
 */
NXSL_Value *NXSL_ServerEnv::getConstantValue(const NXSL_Identifier& name, NXSL_ValueManager *vm)
{
   if (name.value[0] == 'B')
   {
      // Business service states
      NXSL_ENV_CONSTANT("BusinessServiceState::OPERATIONAL", STATUS_NORMAL);
      NXSL_ENV_CONSTANT("BusinessServiceState::DEGRADED", STATUS_MINOR);
      NXSL_ENV_CONSTANT("BusinessServiceState::FAILED", STATUS_CRITICAL);

      // Business service types
      NXSL_ENV_CONSTANT("BusinessServiceType::DCI", static_cast<int32_t>(BusinessServiceCheckType::DCI));
      NXSL_ENV_CONSTANT("BusinessServiceType::NONE", static_cast<int32_t>(BusinessServiceCheckType::NONE));
      NXSL_ENV_CONSTANT("BusinessServiceType::OBJECT", static_cast<int32_t>(BusinessServiceCheckType::OBJECT));
      NXSL_ENV_CONSTANT("BusinessServiceType::SCRIPT", static_cast<int32_t>(BusinessServiceCheckType::SCRIPT));
   }

   if (name.value[0] == 'C')
   {
      // Change code
      NXSL_ENV_CONSTANT("ChangeCode::ADDED", static_cast<int32_t>(ChangeCode::CHANGE_ADDED));
      NXSL_ENV_CONSTANT("ChangeCode::NONE", static_cast<int32_t>(ChangeCode::CHANGE_NONE));
      NXSL_ENV_CONSTANT("ChangeCode::REMOVED", static_cast<int32_t>(ChangeCode::CHANGE_REMOVED));
      NXSL_ENV_CONSTANT("ChangeCode::UPDATED", static_cast<int32_t>(ChangeCode::CHANGE_UPDATED));

      // Cluster state flags
      NXSL_ENV_CONSTANT("ClusterState::Unreachable", DCSF_UNREACHABLE);
      NXSL_ENV_CONSTANT("ClusterState::NetworkPathProblem", DCSF_NETWORK_PATH_PROBLEM);
      NXSL_ENV_CONSTANT("ClusterState::Down", CLSF_DOWN);
   }

   if (name.value[0] == 'D')
   {
      // DCI data source (origin)
      NXSL_ENV_CONSTANT("DataSource::AGENT", DS_NATIVE_AGENT);
      NXSL_ENV_CONSTANT("DataSource::DEVICE_DRIVER", DS_DEVICE_DRIVER);
      NXSL_ENV_CONSTANT("DataSource::INTERNAL", DS_INTERNAL);
      NXSL_ENV_CONSTANT("DataSource::MQTT", DS_MQTT);
      NXSL_ENV_CONSTANT("DataSource::PUSH", DS_PUSH_AGENT);
      NXSL_ENV_CONSTANT("DataSource::SCRIPT", DS_SCRIPT);
      NXSL_ENV_CONSTANT("DataSource::SMCLP", DS_SMCLP);
      NXSL_ENV_CONSTANT("DataSource::SNMP", DS_SNMP_AGENT);
      NXSL_ENV_CONSTANT("DataSource::SSH", DS_SSH);
      NXSL_ENV_CONSTANT("DataSource::WEB_SERVICE", DS_WEB_SERVICE);
      NXSL_ENV_CONSTANT("DataSource::WINPERF", DS_WINPERF);

      // DCI data types
      NXSL_ENV_CONSTANT("DCI::INT32", DCI_DT_INT);
      NXSL_ENV_CONSTANT("DCI::UINT32", DCI_DT_UINT);
      NXSL_ENV_CONSTANT("DCI::COUNTER32", DCI_DT_COUNTER32);
      NXSL_ENV_CONSTANT("DCI::INT64", DCI_DT_INT64);
      NXSL_ENV_CONSTANT("DCI::UINT64", DCI_DT_UINT64);
      NXSL_ENV_CONSTANT("DCI::COUNTER64", DCI_DT_COUNTER64);
      NXSL_ENV_CONSTANT("DCI::FLOAT", DCI_DT_FLOAT);
      NXSL_ENV_CONSTANT("DCI::STRING", DCI_DT_STRING);
      NXSL_ENV_CONSTANT("DCI::NULL", DCI_DT_NULL);

      // DCI states
      NXSL_ENV_CONSTANT("DCI::ACTIVE", ITEM_STATUS_ACTIVE);
      NXSL_ENV_CONSTANT("DCI::DISABLED", ITEM_STATUS_DISABLED);
      NXSL_ENV_CONSTANT("DCI::UNSUPPORTED", ITEM_STATUS_NOT_SUPPORTED);
   }

   if (name.value[0] == 'E')
   {
      // Event origin
      NXSL_ENV_CONSTANT("EventOrigin::AGENT", static_cast<int32_t>(EventOrigin::AGENT));
      NXSL_ENV_CONSTANT("EventOrigin::CLIENT", static_cast<int32_t>(EventOrigin::CLIENT));
      NXSL_ENV_CONSTANT("EventOrigin::NXSL", static_cast<int32_t>(EventOrigin::NXSL));
      NXSL_ENV_CONSTANT("EventOrigin::REMOTE_SERVER", static_cast<int32_t>(EventOrigin::REMOTE_SERVER));
      NXSL_ENV_CONSTANT("EventOrigin::SNMP", static_cast<int32_t>(EventOrigin::SNMP));
      NXSL_ENV_CONSTANT("EventOrigin::SYSLOG", static_cast<int32_t>(EventOrigin::SYSLOG));
      NXSL_ENV_CONSTANT("EventOrigin::SYSTEM", static_cast<int32_t>(EventOrigin::SYSTEM));
      NXSL_ENV_CONSTANT("EventOrigin::WINDOWS_EVENT", static_cast<int32_t>(EventOrigin::WINDOWS_EVENT));
   }

   if (name.value[0] == 'H')
   {
      // Hardware component category
      NXSL_ENV_CONSTANT("HardwareComponentCategory::BASEBOARD", static_cast<int32_t>(HardwareComponentCategory::HWC_BASEBOARD));
      NXSL_ENV_CONSTANT("HardwareComponentCategory::BATTERY", static_cast<int32_t>(HardwareComponentCategory::HWC_BATTERY));
      NXSL_ENV_CONSTANT("HardwareComponentCategory::MEMORY", static_cast<int32_t>(HardwareComponentCategory::HWC_MEMORY));
      NXSL_ENV_CONSTANT("HardwareComponentCategory::NETWORK_ADAPTER", static_cast<int32_t>(HardwareComponentCategory::HWC_NETWORK_ADAPTER));
      NXSL_ENV_CONSTANT("HardwareComponentCategory::OTHER", static_cast<int32_t>(HardwareComponentCategory::HWC_OTHER));
      NXSL_ENV_CONSTANT("HardwareComponentCategory::PROCESSOR", static_cast<int32_t>(HardwareComponentCategory::HWC_PROCESSOR));
      NXSL_ENV_CONSTANT("HardwareComponentCategory::STORAGE", static_cast<int32_t>(HardwareComponentCategory::HWC_STORAGE));
   }

   if (name.value[0] == 'I')
   {
      // Interface expected states
      NXSL_ENV_CONSTANT("InterfaceExpectedState::DOWN", IF_EXPECTED_STATE_DOWN);
      NXSL_ENV_CONSTANT("InterfaceExpectedState::IGNORE", IF_EXPECTED_STATE_IGNORE);
      NXSL_ENV_CONSTANT("InterfaceExpectedState::UP", IF_EXPECTED_STATE_UP);
   }

   if (name.value[0] == 'N')
   {
      // Node state flags
      NXSL_ENV_CONSTANT("NodeState::Unreachable", DCSF_UNREACHABLE);
      NXSL_ENV_CONSTANT("NodeState::NetworkPathProblem", DCSF_NETWORK_PATH_PROBLEM);
      NXSL_ENV_CONSTANT("NodeState::AgentUnreachable", NSF_AGENT_UNREACHABLE);
      NXSL_ENV_CONSTANT("NodeState::EtherNetIPUnreachable", NSF_ETHERNET_IP_UNREACHABLE);
      NXSL_ENV_CONSTANT("NodeState::SNMPUnreachable", NSF_SNMP_UNREACHABLE);
      NXSL_ENV_CONSTANT("NodeState::CacheModeNotSupported", NSF_CACHE_MODE_NOT_SUPPORTED);
      NXSL_ENV_CONSTANT("NodeState::SNMPTrapFlood", NSF_SNMP_TRAP_FLOOD);
      NXSL_ENV_CONSTANT("NodeState::ICMPUnreachable", NSF_ICMP_UNREACHABLE);
      NXSL_ENV_CONSTANT("NodeState::SSHUnreachable", NSF_SSH_UNREACHABLE);
   }

   if (name.value[0] == 'S')
   {
      // Sensor state flags
      NXSL_ENV_CONSTANT("SensorState::Unreachable", DCSF_UNREACHABLE);
      NXSL_ENV_CONSTANT("SensorState::NetworkPathProblem", DCSF_NETWORK_PATH_PROBLEM);
      NXSL_ENV_CONSTANT("SensorState::Provisioned", SSF_PROVISIONED);
      NXSL_ENV_CONSTANT("SensorState::Registered", SSF_REGISTERED);
      NXSL_ENV_CONSTANT("SensorState::Active", SSF_ACTIVE);
      NXSL_ENV_CONSTANT("SensorState::PendingConfigUpdate", SSF_CONF_UPDATE_PENDING);

      // Severity codes
      NXSL_ENV_CONSTANT("Severity::NORMAL", SEVERITY_NORMAL);
      NXSL_ENV_CONSTANT("Severity::WARNING", SEVERITY_WARNING);
      NXSL_ENV_CONSTANT("Severity::MINOR", SEVERITY_MINOR);
      NXSL_ENV_CONSTANT("Severity::MAJOR", SEVERITY_MAJOR);
      NXSL_ENV_CONSTANT("Severity::CRITICAL", SEVERITY_CRITICAL);

      // Object status codes
      NXSL_ENV_CONSTANT("Status::NORMAL", STATUS_NORMAL);
      NXSL_ENV_CONSTANT("Status::WARNING", STATUS_WARNING);
      NXSL_ENV_CONSTANT("Status::MINOR", STATUS_MINOR);
      NXSL_ENV_CONSTANT("Status::MAJOR", STATUS_MAJOR);
      NXSL_ENV_CONSTANT("Status::CRITICAL", STATUS_CRITICAL);
      NXSL_ENV_CONSTANT("Status::UNKNOWN", STATUS_UNKNOWN);
      NXSL_ENV_CONSTANT("Status::UNMANAGED", STATUS_UNMANAGED);
      NXSL_ENV_CONSTANT("Status::DISABLED", STATUS_DISABLED);
      NXSL_ENV_CONSTANT("Status::TESTING", STATUS_TESTING);

      // Status colors
      NXSL_ENV_CONSTANT("StatusColor::NORMAL", _T("rgb(0, 192, 0)"));
      NXSL_ENV_CONSTANT("StatusColor::WARNING", _T("rgb(0, 255, 255)"));
      NXSL_ENV_CONSTANT("StatusColor::MINOR", _T("rgb(231, 226, 0)"));
      NXSL_ENV_CONSTANT("StatusColor::MAJOR", _T("rgb(255, 128, 0)"));
      NXSL_ENV_CONSTANT("StatusColor::CRITICAL", _T("rgb(192, 0, 0)"));
      NXSL_ENV_CONSTANT("StatusColor::UNKNOWN", _T("rgb(0, 0, 128)"));
      NXSL_ENV_CONSTANT("StatusColor::UNMANAGED", _T("rgb(192, 192, 192)"));
      NXSL_ENV_CONSTANT("StatusColor::DISABLED", _T("rgb(128, 64, 0)"));
      NXSL_ENV_CONSTANT("StatusColor::TESTING", _T("rgb(255, 128, 255)"));
   }

   return NXSL_Environment::getConstantValue(name, vm);
}

/**
 * Constructor for environment intended for passing script's output to client
 */
NXSL_ClientSessionEnv::NXSL_ClientSessionEnv(ClientSession *session, NXCPMessage *response) : NXSL_ServerEnv()
{
   m_session = session;
   m_response = response;
}

/**
 * Send script output to user
 */
void NXSL_ClientSessionEnv::print(const TCHAR *text)
{
	if (m_session != nullptr && m_response != nullptr)
	{
		m_response->setField(VID_MESSAGE, text);
		m_session->sendMessage(m_response);
	}
}

/**
 * Trace output
 */
void NXSL_ClientSessionEnv::trace(int level, const TCHAR *text)
{
	if ((m_session != nullptr) && (m_response != nullptr))
	{
      size_t len = _tcslen(text);
      TCHAR *t = MemAllocString(len + 2);
      memcpy(t, text, len * sizeof(TCHAR));
      t[len] = _T('\n');
      t[len + 1] = 0;
		m_response->setField(VID_MESSAGE, t);
		m_session->sendMessage(m_response);
      MemFree(t);
	}
   NXSL_ServerEnv::trace(level, text);
}

/**
 * Call hook script
 */
NXSL_VM *FindHookScript(const TCHAR *hookName, shared_ptr<NetObj> object)
{
   TCHAR scriptName[MAX_PATH] = _T("Hook::");
   _tcslcpy(&scriptName[6], hookName, MAX_PATH - 6);
   NXSL_VM *vm = CreateServerScriptVM(scriptName, object);
   if (vm == nullptr)
      DbgPrintf(7, _T("FindHookScript: hook script \"%s\" not found"), scriptName);
   return vm;
}
