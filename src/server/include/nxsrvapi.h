/*
** NetXMS - Network Management System
** Server Library
** Copyright (C) 2003-2022 Reden Solutions
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
** File: nxsrvapi.h
**
**/

#ifndef _nxsrvapi_h_
#define _nxsrvapi_h_

#ifdef LIBNXSRV_EXPORTS
#define LIBNXSRV_EXPORTABLE __EXPORT
#define LIBNXSRV_EXPORTABLE_VAR(v) __EXPORT_VAR(v)
#else
#define LIBNXSRV_EXPORTABLE __IMPORT
#define LIBNXSRV_EXPORTABLE_VAR(v) __IMPORT_VAR(v)
#endif

#include <nxcpapi.h>
#include <nms_util.h>
#include <nms_agent.h>
#include <nxsnmp.h>
#include <netxms_isc.h>
#include <nxcldefs.h>
#include <nxsl.h>
#include <interface_types.h>

/**
 * Default files
 */
#ifdef _WIN32

#define DEFAULT_LOG_FILE      _T("C:\\netxmsd.log")
#define DEFAULT_DUMP_DIR      _T("C:\\")

#define LDIR_NCD              _T("\\ncdrv")
#define LDIR_NDD              _T("\\ndd")
#define LDIR_PDSDRV           _T("\\pdsdrv")

#define DDIR_PACKAGES         _T("\\packages")
#define DDIR_BACKGROUNDS      _T("\\backgrounds")
#define DFILE_KEYS            _T("\\server_key")
#define DFILE_COMPILED_MIB    _T("\\netxms.mib")
#define DDIR_IMAGES           _T("\\images")
#define DDIR_FILES            _T("\\files")
#define DDIR_CRL              _T("\\crl")

#define SDIR_SCRIPTS          _T("\\scripts")
#define SDIR_TEMPLATES        _T("\\templates")
#define SFILE_RADDICT         _T("\\radius.dict")

#else    /* _WIN32 */

#ifndef DATADIR
#define DATADIR              _T("/usr/share/netxms")
#endif

#ifndef STATEDIR
#define STATEDIR             _T("/var/lib/netxms")
#endif

#ifndef LIBDIR
#define LIBDIR               _T("/usr/lib")
#endif

#ifndef PKGLIBDIR
#define PKGLIBDIR            _T("/usr/lib/netxms")
#endif

#define DEFAULT_LOG_FILE      _T("/var/log/netxmsd.log")
#define DEFAULT_DUMP_DIR      _T("/var/tmp")

#define LDIR_NCD              _T("/ncdrv")
#define LDIR_NDD              _T("/ndd")
#define LDIR_PDSDRV           _T("/pdsdrv")

#define DDIR_PACKAGES         _T("/packages")
#define DDIR_BACKGROUNDS      _T("/backgrounds")
#define DFILE_KEYS            _T("/.server_key")
#define DFILE_COMPILED_MIB    _T("/netxms.mib")
#define DDIR_IMAGES           _T("/images")
#define DDIR_FILES            _T("/files")
#define DDIR_CRL              _T("/crl")

#define SDIR_SCRIPTS          _T("/scripts")
#define SDIR_TEMPLATES        _T("/templates")
#define SFILE_RADDICT         _T("/radius.dict")

#endif   /* _WIN32 */

/**
 * Debug tags
 */
#define DEBUG_TAG_TOPO_ARP    _T("topology.arp")
#define DEBUG_TAG_TOPO_CDP    _T("topology.cdp")
#define DEBUG_TAG_TOPO_FDB    _T("topology.fdb")
#define DEBUG_TAG_TOPO_LLDP   _T("topology.lldp")
#define DEBUG_TAG_TOPO_NDP    _T("topology.ndp")

/**
 * Application flags
 */
#define AF_DAEMON                              _LL(0x0000000000000001)
#define AF_USE_SYSLOG                          _LL(0x0000000000000002)
#define AF_PASSIVE_NETWORK_DISCOVERY           _LL(0x0000000000000004)
#define AF_ACTIVE_NETWORK_DISCOVERY            _LL(0x0000000000000008)
#define AF_ENABLE_8021X_STATUS_POLL            _LL(0x0000000000000010)
#define AF_DELETE_EMPTY_SUBNETS                _LL(0x0000000000000020)
#define AF_ENABLE_SNMP_TRAPD                   _LL(0x0000000000000040)
#define AF_ENABLE_ZONING                       _LL(0x0000000000000080)
#define AF_SYNC_NODE_NAMES_WITH_DNS            _LL(0x0000000000000100)
#define AF_CHECK_TRUSTED_NODES                 _LL(0x0000000000000200)
#define AF_ENABLE_NXSL_CONTAINER_FUNCTIONS     _LL(0x0000000000000400)
#define AF_USE_FQDN_FOR_NODE_NAMES             _LL(0x0000000000000800)
#define AF_APPLY_TO_DISABLED_DCI_FROM_TEMPLATE _LL(0x0000000000001000)
#define AF_DEBUG_CONSOLE_DISABLED              _LL(0x0000000000002000)
#define AF_AUTOBIND_ON_CONF_POLL               _LL(0x0000000000004000)
#define AF_WRITE_FULL_DUMP                     _LL(0x0000000000080000)
#define AF_RESOLVE_NODE_NAMES                  _LL(0x0000000000100000)
#define AF_CATCH_EXCEPTIONS                    _LL(0x0000000000200000)
#define AF_HELPDESK_LINK_ACTIVE                _LL(0x0000000000400000)
#define AF_DB_LOCKED                           _LL(0x0000000001000000)
#define AF_DB_CONNECTION_LOST                  _LL(0x0000000004000000)
#define AF_NO_NETWORK_CONNECTIVITY             _LL(0x0000000008000000)
#define AF_EVENT_STORM_DETECTED                _LL(0x0000000010000000)
#define AF_SNMP_TRAP_DISCOVERY                 _LL(0x0000000020000000)
#define AF_TRAPS_FROM_UNMANAGED_NODES          _LL(0x0000000040000000)
#define AF_PERFDATA_STORAGE_DRIVER_LOADED      _LL(0x0000000100000000)
#define AF_BACKGROUND_LOG_WRITER               _LL(0x0000000200000000)
#define AF_CASE_INSENSITIVE_LOGINS             _LL(0x0000000400000000)
#define AF_TRAP_SOURCES_IN_ALL_ZONES           _LL(0x0000000800000000)
#define AF_SYSLOG_DISCOVERY                    _LL(0x0000001000000000)
// unused: #define AF_ENABLE_LOCAL_CONSOLE                _ULL(0x0000002000000000)
#define AF_CACHE_DB_ON_STARTUP                 _LL(0x0000004000000000)
#define AF_ENABLE_NXSL_FILE_IO_FUNCTIONS       _LL(0x0000008000000000)
// unused: #define AF_ENABLE_EMBEDDED_PYTHON              _LL(0x0000010000000000)
#define AF_DB_SUPPORTS_MERGE                   _LL(0x0000020000000000)
#define AF_PARALLEL_NETWORK_DISCOVERY          _LL(0x0000040000000000)
#define AF_SINGLE_TABLE_PERF_DATA              _LL(0x0000080000000000)
#define AF_MERGE_DUPLICATE_NODES               _LL(0x0000100000000000)
#define AF_SYSTEMD_DAEMON                      _LL(0x0000200000000000)
#define AF_USE_SYSTEMD_JOURNAL                 _LL(0x0000400000000000)
#define AF_COLLECT_ICMP_STATISTICS             _LL(0x0000800000000000)
#define AF_LOG_IN_JSON_FORMAT                  _LL(0x0001000000000000)
#define AF_LOG_TO_STDOUT                       _LL(0x0002000000000000)
#define AF_DBWRITER_HK_INTERLOCK               _LL(0x0004000000000000)
#define AF_LOG_ALL_SNMP_TRAPS                  _LL(0x0008000000000000)
#define AF_ALLOW_TRAP_VARBIND_CONVERSION       _LL(0x0010000000000000)
#define AF_TSDB_DROP_CHUNKS_V2                 _LL(0x0020000000000000)
#define AF_SERVER_INITIALIZED                  _LL(0x2000000000000000)
#define AF_SHUTDOWN                            _LL(0x4000000000000000)

/**
 * Encryption usage policies
 */
#define ENCRYPTION_DISABLED   0
#define ENCRYPTION_ALLOWED    1
#define ENCRYPTION_PREFERRED  2
#define ENCRYPTION_REQUIRED   3

/**
 * Data collection errors
 */
enum DataCollectionError
{
   DCE_SUCCESS           = 0,
   DCE_COMM_ERROR        = 1,
   DCE_NOT_SUPPORTED     = 2,
   DCE_IGNORE            = 3,
   DCE_NO_SUCH_INSTANCE  = 4,
   DCE_COLLECTION_ERROR  = 5,
   DCE_ACCESS_DENIED     = 6
};

/**
 * Agent action output callback events
 */
enum ActionCallbackEvent
{
   ACE_CONNECTED = 0,
   ACE_DATA = 1,
   ACE_DISCONNECTED = 2
};

/**
 * Win32 service and syslog constants
 */
#ifdef _WIN32

#define CORE_SERVICE_NAME     _T("NetXMSCore")
#define CORE_EVENT_SOURCE     _T("NetXMSCore")
#define NETXMSD_SYSLOG_NAME   CORE_EVENT_SOURCE

#else

#define NETXMSD_SYSLOG_NAME   _T("netxmsd")

#endif   /* _WIN32 */

/**
 * Single ARP cache entry
 */
struct ArpEntry
{
   uint32_t ifIndex;       // Interface index, 0 if unknown
   InetAddress ipAddr;
   MacAddress macAddr;

   ArpEntry(const InetAddress& _ipAddr, const MacAddress& _macAddr, uint32_t _ifIndex) : ipAddr(_ipAddr), macAddr(_macAddr) { ifIndex = _ifIndex; }
};

#ifdef _WIN32
template class LIBNXSRV_EXPORTABLE ObjectArray<ArpEntry>;
template class LIBNXSRV_EXPORTABLE HashMap<InetAddress, ArpEntry>;
#endif

/**
 * ARP cache structure used by discovery functions and AgentConnection class
 */
class LIBNXSRV_EXPORTABLE ArpCache
{
private:
   ObjectArray<ArpEntry> m_entries;
   HashMap<InetAddress, ArpEntry> m_ipIndex;
   time_t m_timestamp;

public:
   ArpCache();

   void addEntry(ArpEntry *entry);
   void addEntry(const InetAddress& ipAddr, const MacAddress& macAddr, uint32_t ifIndex = 0) { addEntry(new ArpEntry(ipAddr, macAddr, ifIndex)); }

   int size() const { return m_entries.size(); }
   time_t timestamp() const { return m_timestamp; }
   const ArpEntry *get(int index) const { return m_entries.get(index); }
   const ArpEntry *findByIP(const InetAddress& addr);

   void dumpToLog() const;
};

#ifdef _WIN32
template class LIBNXSRV_EXPORTABLE shared_ptr<ArpCache>;
#endif

/**
 * Interface physical location
 */
struct LIBNXSRV_EXPORTABLE InterfacePhysicalLocation
{
   uint32_t chassis;
   uint32_t module;
   uint32_t pic;
   uint32_t port;

   InterfacePhysicalLocation()
   {
      chassis = 0;
      module = 0;
      pic = 0;
      port = 0;
   }

   InterfacePhysicalLocation(uint32_t _chassis, uint32_t _module, uint32_t _pic, uint32_t _port)
   {
      chassis = _chassis;
      module = _module;
      pic = _pic;
      port = _port;
   }

   bool equals(const InterfacePhysicalLocation& l)
   {
      return (port == l.port) && (pic == l.pic) && (module == l.module) && (chassis == l.chassis);
   }

   TCHAR *toString(TCHAR *buffer, size_t size)
   {
      _sntprintf(buffer, size, _T("%u/%u/%u/%u"), chassis, module, pic, port);
      return buffer;
   }
};

/**
 * Interface information structure used by discovery functions and AgentConnection class
 */
class InterfaceInfo
{
private:
   void init()
   {
      name[0] = 0;
      description[0] = 0;
      alias[0] = 0;
      type = IFTYPE_OTHER;
      mtu = 0;
      speed = 0;
      bridgePort = 0;
      memset(macAddr, 0, sizeof(macAddr));
      isPhysicalPort = false;
      isSystem = false;
      parentIndex = 0;
   }

public:
   uint32_t index;
   TCHAR name[MAX_DB_STRING];			// Interface display name
	TCHAR description[MAX_DB_STRING];	// Value of ifDescr MIB variable for SNMP agents
	TCHAR alias[MAX_DB_STRING];	// Value of ifAlias MIB variable for SNMP agents
	uint32_t type;
	uint32_t mtu;
   uint64_t speed;  // interface speed in bits/sec
   uint32_t bridgePort;
	InterfacePhysicalLocation location;
   InetAddressList ipAddrList;
   BYTE macAddr[MAC_ADDR_LENGTH];
	bool isPhysicalPort;
   bool isSystem;
   uint32_t ifTableSuffix[16];   // actual ifTable suffix
   int ifTableSuffixLength;
   uint32_t parentIndex;

   InterfaceInfo(uint32_t ifIndex)
   {
      index = ifIndex;
      ifTableSuffixLength = 0;
      init();
   }

   InterfaceInfo(uint32_t ifIndex, int suffixLen, const uint32_t *suffix)
   {
      index = ifIndex;
      ifTableSuffixLength = ((suffixLen >= 0) && (suffixLen < 16)) ? suffixLen : 0;
      memcpy(ifTableSuffix, suffix, ifTableSuffixLength * sizeof(uint32_t));
      init();
   }

   bool hasAddress(const InetAddress& addr) { return ipAddrList.hasAddress(addr); }
};

/**
 * Interface list used by discovery functions and AgentConnection class
 */
class LIBNXSRV_EXPORTABLE InterfaceList
{
private:
   ObjectArray<InterfaceInfo> *m_interfaces;
   void *m_data;                  // Can be used by custom enumeration handlers
   bool m_needPrefixWalk;

public:
	InterfaceList(int initialAlloc = 8);
	~InterfaceList();

   void add(InterfaceInfo *iface) { m_interfaces->add(iface); }
   void remove(int index) { m_interfaces->remove(index); }

	int size() const { return m_interfaces->size(); }
	InterfaceInfo *get(int index) const { return m_interfaces->get(index); }
	InterfaceInfo *findByIfIndex(uint32_t ifIndex) const;
   InterfaceInfo *findByPhysicalLocation(const InterfacePhysicalLocation &loc) const;
   InterfaceInfo *findByPhysicalLocation(int chassis, int module, int pic, int port) const { return findByPhysicalLocation(InterfacePhysicalLocation(chassis, module, pic, port)); }

	void setData(void *data) { m_data = data; }
	void *getData() const { return m_data; }

   bool isPrefixWalkNeeded() const { return m_needPrefixWalk; }
   void setPrefixWalkNeeded() { m_needPrefixWalk = true; }
};

#define VLAN_PRM_IFINDEX   0
#define VLAN_PRM_PHYLOC    1
#define VLAN_PRM_BPORT     2

/**
 * VLAN port reference
 */
struct VlanPortInfo
{
   uint32_t portId;    // device or driver specific port ID
   uint32_t objectId;
   uint32_t ifIndex;
   InterfacePhysicalLocation location;
};

/**
 * Vlan information
 */
class LIBNXSRV_EXPORTABLE VlanInfo
{
private:
	int m_vlanId;
	TCHAR *m_name;
	int m_portRefMode;	// Port reference mode - (by ifIndex, physical location, or bridge port number)
	int m_allocated;
	int m_numPorts;	// Number of ports in VLAN
	VlanPortInfo *m_ports;	// member ports (slot/port pairs or ifIndex)
	uint32_t m_nodeId;

public:
	VlanInfo(int vlanId, int prm);
	VlanInfo(const VlanInfo *src, uint32_t nodeId);
	~VlanInfo();

	int getVlanId() const { return m_vlanId; }
	int getPortReferenceMode() const { return m_portRefMode; }
	const TCHAR *getName() const { return CHECK_NULL_EX(m_name); }
	int getNumPorts() const { return m_numPorts; }
	VlanPortInfo *getPorts() { return m_ports; }
	UINT32 getNodeId() const { return m_nodeId; }

	void add(const InterfacePhysicalLocation& location);
	void add(uint32_t chassis, uint32_t module, uint32_t pic, uint32_t port) { add(InterfacePhysicalLocation(chassis, module, pic, port)); }
	void add(uint32_t portId);
	void setName(const TCHAR *name);

	void resolvePort(int index, const InterfacePhysicalLocation& location, uint32_t ifIndex, uint32_t id);
};

/**
 * Vlan list
 */
class LIBNXSRV_EXPORTABLE VlanList
{
private:
   int m_size;          // Number of valid entries
	int m_allocated;     // Number of allocated entries
   void *m_data;        // Can be used by custom enumeration handlers
   VlanInfo **m_vlans;  // VLAN entries

public:
	VlanList(int initialAlloc = 8);
	~VlanList();

	void add(VlanInfo *vlan);
	void addMemberPort(int vlanId, uint32_t portId);
   void addMemberPort(int vlanId, const InterfacePhysicalLocation& location);

	int size() { return m_size; }
	VlanInfo *get(int index) { return ((index >= 0) && (index < m_size)) ? m_vlans[index] : nullptr; }
	VlanInfo *findById(int id);
	VlanInfo *findByName(const TCHAR *name);

	void setData(void *data) { m_data = data; }
	void *getData() { return m_data; }

	void fillMessage(NXCPMessage *msg);
};

/**
 * Custom attribute flags
 */
#define CAF_INHERITABLE 0x01
#define CAF_REDEFINED   0x02
#define CAF_CONFLICT    0x04

/**
 * Custom attribute
 */
struct CustomAttribute
{
   SharedString value;
   uint32_t sourceObject; // source object ID for inherited attribute
   uint32_t flags;

   CustomAttribute(SharedString value, uint32_t flags, uint32_t sourceObject = 0)
   {
      this->value = value;
      this->flags = flags;
      this->sourceObject = sourceObject;
   }

   bool isInheritable() const
   {
      return (flags & CAF_INHERITABLE) > 0;
   }

   bool isRedefined() const
   {
      return (flags & CAF_REDEFINED) > 0;
   }

   bool isConflict() const
   {
      return (flags & CAF_CONFLICT) > 0;
   }


   bool isInherited() const
   {
      return sourceObject != 0;
   }

   json_t *toJson(const TCHAR *name)const
   {
      json_t *root = json_object();
      json_object_set_new(root, "name", json_string_t(name));
      json_object_set_new(root, "value", json_string_t(value));
      json_object_set_new(root, "flags", json_integer(flags));
      json_object_set_new(root, "sourceObject", json_integer(sourceObject));
      return root;
   }
};

#ifdef _WIN32
class NObject;
template class LIBNXSRV_EXPORTABLE shared_ptr<NObject>;
template class LIBNXSRV_EXPORTABLE weak_ptr<NObject>;
template class LIBNXSRV_EXPORTABLE ObjectMemoryPool<shared_ptr<NObject>>;
template class LIBNXSRV_EXPORTABLE SharedObjectArray<NObject>;
template class LIBNXSRV_EXPORTABLE StringObjectMap<CustomAttribute>;
#endif

/**
 * Base class for all monitoring objects
 */
class LIBNXSRV_EXPORTABLE NObject : public enable_shared_from_this<NObject>
{
private:
   SharedObjectArray<NObject> m_childList;     // Array of pointers to child objects
   SharedObjectArray<NObject> m_parentList;    // Array of pointers to parent objects

   StringObjectMap<CustomAttribute> m_customAttributes;
   Mutex m_customAttributeLock;

   SharedString getCustomAttributeFromParent(const TCHAR *name, uint32_t id);
   std::pair<uint32_t, SharedString> getCustomAttributeFromParent(const TCHAR *name);
   bool setCustomAttributeFromMessage(const NXCPMessage& msg, uint32_t base);
   void setCustomAttribute(const TCHAR *name, SharedString value, uint32_t parent);
   void deletePopulatedCustomAttribute(const TCHAR *name);
   void populate(const TCHAR *name, SharedString value, uint32_t parentId);
   void populateRemove(const TCHAR *name);
   bool checkCustomAttributeInConflict(const TCHAR *name, uint32_t newParent);

protected:
   uint32_t m_id;
   uuid m_guid;
   TCHAR m_name[MAX_OBJECT_NAME];

   RWLock m_rwlockParentList; // Lock for parent list
   RWLock m_rwlockChildList;  // Lock for child list

   const SharedObjectArray<NObject> &getChildList() const { return m_childList; }
   const SharedObjectArray<NObject> &getParentList() const { return m_parentList; }

   void clearChildList();
   void clearParentList();

   bool isDirectChildInternal(uint32_t id) const
   {
      for(int i = 0; i < m_childList.size(); i++)
         if (m_childList.get(i)->getId() == id)
            return true;
      return false;
   }
   bool isDirectParentInternal(uint32_t id) const
   {
      for(int i = 0; i < m_parentList.size(); i++)
         if (m_parentList.get(i)->getId() == id)
            return true;
      return false;
   }

   void lockCustomAttributes() const { m_customAttributeLock.lock(); }
   void unlockCustomAttributes() const { m_customAttributeLock.unlock(); }

   void readLockParentList() const { m_rwlockParentList.readLock(); }
   void writeLockParentList() { m_rwlockParentList.writeLock(); }
   void unlockParentList() const { m_rwlockParentList.unlock(); }

   void readLockChildList() const { m_rwlockChildList.readLock(); }
   void writeLockChildList() { m_rwlockChildList.writeLock(); }
   void unlockChildList() const { m_rwlockChildList.unlock(); }

   virtual void onCustomAttributeChange();
   virtual bool getObjectAttribute(const TCHAR *name, TCHAR **value, bool *isAllocated) const;

public:
   NObject();
   NObject(const NObject& src) = delete;
   virtual ~NObject();

   shared_ptr<NObject> self() { return shared_from_this(); }
   shared_ptr<const NObject> self() const { return shared_from_this(); }

   uint32_t getId() const { return m_id; }
   const uuid& getGuid() const { return m_guid; }
   const TCHAR *getName() const { return m_name; }

   void addChild(const shared_ptr<NObject>& object);     // Add reference to child object
   void addParent(const shared_ptr<NObject>& object);    // Add reference to parent object

   void deleteChild(uint32_t objectId);  // Delete reference to child object
   void deleteParent(uint32_t objectId); // Delete reference to parent object

   bool isChild(uint32_t id) const;
   bool isDirectChild(uint32_t id) const;
   bool isParent(uint32_t id) const;
   bool isDirectParent(uint32_t id) const;

   int getChildCount() const { return m_childList.size(); }
   int getParentCount() const { return m_parentList.size(); }

   TCHAR *getCustomAttribute(const TCHAR *name, TCHAR *buffer, size_t size) const;
   SharedString getInheritableCustomAttribute(const TCHAR *name) const;
   uint32_t getInheritableCustomAttributeParent(const TCHAR *name) const;
   SharedString getCustomAttribute(const TCHAR *name) const;
   TCHAR *getCustomAttributeCopy(const TCHAR *name) const;
   int32_t getCustomAttributeAsInt32(const TCHAR *key, int32_t defaultValue) const;
   uint32_t getCustomAttributeAsUInt32(const TCHAR *key, uint32_t defaultValue) const;
   int64_t getCustomAttributeAsInt64(const TCHAR *key, int64_t defaultValue) const;
   uint64_t getCustomAttributeAsUInt64(const TCHAR *key, uint64_t defaultValue) const;
   double getCustomAttributeAsDouble(const TCHAR *key, double defaultValue) const;
   bool getCustomAttributeAsBoolean(const TCHAR *key, bool defaultValue) const;

   StringMap *getCustomAttributes(bool (*filter)(const TCHAR *, const CustomAttribute *, void *) = nullptr, void *context = nullptr) const;
   StringMap *getCustomAttributes(const TCHAR *regexp) const;

   void setCustomAttribute(const TCHAR *name, SharedString value, StateChange inheritable);
   void setCustomAttribute(const TCHAR *key, int32_t value);
   void setCustomAttribute(const TCHAR *key, uint32_t value);
   void setCustomAttribute(const TCHAR *key, int64_t value);
   void setCustomAttribute(const TCHAR *key, uint64_t value);

   void setCustomAttributesFromMessage(const NXCPMessage& msg);
   void setCustomAttributesFromDatabase(DB_RESULT hResult);
   void deleteCustomAttribute(const TCHAR *name);
   void updateOrDeleteCustomAttributeOnParentRemove(const TCHAR *name);
   NXSL_Value *getCustomAttributeForNXSL(NXSL_VM *vm, const TCHAR *name) const;
   NXSL_Value *getCustomAttributesForNXSL(NXSL_VM *vm) const;
   int getCustomAttributeSize() const { return m_customAttributes.size(); }

   template <typename C>
   EnumerationCallbackResult forEachCustomAttribute(EnumerationCallbackResult (*cb)(const TCHAR *, const CustomAttribute *, C *), C *context) const
   {
      lockCustomAttributes();
      EnumerationCallbackResult result =  m_customAttributes.forEach(reinterpret_cast<EnumerationCallbackResult (*)(const TCHAR*, const void*, void*)>(cb), (void *)context);
      unlockCustomAttributes();
      return result;
   }

   StringBuffer dbgGetParentList() const;
   StringBuffer dbgGetChildList() const;

   void pruneCustomAttributes();
};

/**
 * Route information
 */
typedef struct
{
   uint32_t dwDestAddr;
   uint32_t dwDestMask;
   uint32_t dwNextHop;
   uint32_t dwIfIndex;
   uint32_t dwRouteType;
} ROUTE;

/**
 * Routing table
 */
typedef StructArray<ROUTE> RoutingTable;

/**
 * Information about policies installed on agent
 */
class LIBNXSRV_EXPORTABLE AgentPolicyInfo
{
private:
   bool m_newTypeFormat;
	int m_size;
	uint8_t *m_guidList;
	TCHAR **m_typeList;
   TCHAR **m_serverInfoList;
	uint64_t *m_serverIdList;
	uint8_t *m_hashList;
	int *m_version;

public:
	AgentPolicyInfo(NXCPMessage *msg);
	~AgentPolicyInfo();

	int size() { return m_size; }
	uuid getGuid(int index);
   const uint8_t *getHash(int index);
	const TCHAR *getType(int index) { return ((index >= 0) && (index < m_size)) ? m_typeList[index] : NULL; }
	const TCHAR *getServerInfo(int index) { return ((index >= 0) && (index < m_size)) ? m_serverInfoList[index] : NULL; }
	uint64_t getServerId(int index) { return ((index >= 0) && (index < m_size)) ? m_serverIdList[index] : 0; }
	int getVersion(int index) { return ((index >= 0) && (index < m_size)) ? m_version[index] : -1; }
	bool isNewTypeFormat() { return m_newTypeFormat; }
};

/**
 * Agent parameter definition
 */
class LIBNXSRV_EXPORTABLE AgentParameterDefinition
{
private:
   TCHAR *m_name;
   TCHAR *m_description;
   int m_dataType;

public:
   AgentParameterDefinition(const NXCPMessage *msg, uint32_t baseId);
   AgentParameterDefinition(const AgentParameterDefinition *src);
   AgentParameterDefinition(const TCHAR *name, const TCHAR *description, int dataType);
   ~AgentParameterDefinition();

   uint32_t fillMessage(NXCPMessage *msg, uint32_t baseId) const;

   const TCHAR *getName() const { return m_name; }
   const TCHAR *getDescription() const { return m_description; }
   int getDataType() const { return m_dataType; }
};

/**
 * Agent table column definition
 */
struct AgentTableColumnDefinition
{
   TCHAR m_name[MAX_COLUMN_NAME];
   int m_dataType;

   AgentTableColumnDefinition(const AgentTableColumnDefinition *src)
   {
      _tcslcpy(m_name, src->m_name, MAX_COLUMN_NAME);
      m_dataType = src->m_dataType;
   }
};

/**
 * Agent table definition
 */
class LIBNXSRV_EXPORTABLE AgentTableDefinition
{
private:
   TCHAR *m_name;
   TCHAR *m_description;
   StringList *m_instanceColumns;
   ObjectArray<AgentTableColumnDefinition> *m_columns;

public:
   AgentTableDefinition(const NXCPMessage *msg, uint32_t baseId);
   AgentTableDefinition(const AgentTableDefinition *src);
   ~AgentTableDefinition();

   uint32_t fillMessage(NXCPMessage *msg, uint32_t baseId) const;

   const TCHAR *getName() const { return m_name; }
   const TCHAR *getDescription() const { return m_description; }
};

/**
 * Remote file information
 */
class LIBNXSRV_EXPORTABLE RemoteFileInfo
{
private:
   TCHAR *m_name;
   uint64_t m_size;
   time_t m_mtime;
   BYTE m_hash[MD5_DIGEST_SIZE];
   uint32_t m_status;
   uint32_t m_permissions;
   TCHAR *m_ownerUser;
   TCHAR *m_ownerGroup;

public:
   RemoteFileInfo(NXCPMessage *msg, uint32_t baseId, const TCHAR *name);
   ~RemoteFileInfo();

   const TCHAR *name() const { return m_name; }
   uint32_t status() const { return m_status; }
   bool isValid() const { return m_status == ERR_SUCCESS; }
   uint64_t size() const { return m_size; }
   time_t modificationTime() const { return m_mtime; }
   const BYTE *hash() const { return m_hash; }
   const uint32_t permissions() const { return m_permissions; }
   const TCHAR *ownerUser() const { return m_ownerUser; }
   const TCHAR *ownerGroup() const { return m_ownerGroup; }
};

/**
 * Fingerprint of remote file
 */
struct RemoteFileFingerprint
{
   uint64_t size;
   uint32_t crc32;
   BYTE md5[MD5_DIGEST_SIZE];
   BYTE sha256[SHA256_DIGEST_SIZE];
   size_t dataLength;
   BYTE data[64];
};

/**
 * Information about file part
 */
struct FilePartInfo
{
   TCHAR *m_name;
   uint64_t m_offset;
   uint32_t m_size;
   BYTE m_hash[MD5_DIGEST_SIZE];

   FilePartInfo()
   {
      m_name = nullptr;
      m_size = 0;
      m_offset = 0;
      memset(m_hash, 0, sizeof(m_hash));
   }

   ~FilePartInfo()
   {
      MemFree(m_name);
   }

   bool isFoundInRemoteFiles(ObjectArray<RemoteFileInfo> *remoteFiles)
   {
      if (remoteFiles != nullptr)
      {
         for (int i = 0; i < remoteFiles->size(); i++)
         {
            RemoteFileInfo* remoteFilePart = remoteFiles->get(i);
            if (!_tcscmp(m_name, remoteFilePart->name()))
            {
               if ((m_size == static_cast<uint32_t>(remoteFilePart->size())) && !memcmp(m_hash, remoteFilePart->hash(), MD5_DIGEST_SIZE))
               {
                  return true;
               }
            }
         }
      }
      return false;
   }
};

/**
 * Receiver for agent connection
 */
class AgentConnectionReceiver;

#ifdef _WIN32
class AgentConnection;
template class LIBNXSRV_EXPORTABLE weak_ptr<AgentConnection>;
template class LIBNXSRV_EXPORTABLE shared_ptr<AgentConnectionReceiver>;
#endif

/**
 * Agent connection
 */
class LIBNXSRV_EXPORTABLE AgentConnection : public enable_shared_from_this<AgentConnection>
{
   friend class AgentConnectionReceiver;

private:
   shared_ptr<AgentConnectionReceiver> m_receiver;
   uint32_t m_debugId;
   InetAddress m_addr;
   int m_nProtocolVersion;
   bool m_controlServer;
   bool m_masterServer;
   char m_secret[MAX_SECRET_LENGTH];
   time_t m_tLastCommandTime;
   shared_ptr<AbstractCommChannel> m_channel;
   VolatileCounter m_requestId;
   uint32_t m_commandTimeout;
   uint32_t m_connectionTimeout;
	uint32_t m_recvTimeout;
   MsgWaitQueue m_messageWaitQueue;
   bool m_isConnected;
   Mutex m_mutexDataLock;
   Mutex m_mutexSocketWrite;
   int m_encryptionPolicy;
   bool m_useProxy;
   InetAddress m_proxyAddr;
   uint16_t m_port;
   uint16_t m_proxyPort;
   char m_proxySecret[MAX_SECRET_LENGTH];
	int m_hCurrFile;
	TCHAR m_currentFileName[MAX_PATH];
	uint32_t m_downloadRequestId;
	time_t m_downloadActivityTimestamp;
	Condition m_condFileDownload;
	bool m_fileDownloadSucceeded;
	void (*m_downloadProgressCallback)(size_t, void*);
	void *m_downloadProgressCallbackArg;
	bool m_deleteFileOnDownloadFailure;
	void (*m_sendToClientMessageCallback)(NXCPMessage*, void*);
	bool m_fileUploadInProgress;
	bool m_fileUpdateConnection;
	bool m_allowCompression;
	VolatileCounter m_bulkDataProcessing;

   uint32_t setupEncryption(RSA *pServerKey);
   uint32_t authenticate(BOOL bProxyData);
   uint32_t setupProxyConnection();
   uint32_t prepareFileDownload(const TCHAR *fileName, uint32_t rqId, bool append,
         void (*downloadProgressCallback)(size_t, void*), void (*fileResendCallback)(NXCPMessage*, void*), void *cbArg);
   void processFileData(NXCPMessage *msg);
   void processFileTransferAbort(NXCPMessage *msg);

   void processCollectedDataCallback(NXCPMessage *msg);
   void onDataPushCallback(NXCPMessage *msg);
   void onSnmpTrapCallback(NXCPMessage *msg);
   void onTrapCallback(NXCPMessage *msg);
   void onSyslogMessageCallback(NXCPMessage *msg);
   void onWindowsEventCallback(NXCPMessage *msg);
   void onNotifyCallback(NXCPMessage *msg);
   void postMessageCallback(NXCPMessage *msg);
   void postRawMessageCallback(NXCP_MESSAGE *msg);
   void getSshKeysCallback(NXCPMessage *msg);

protected:
   virtual shared_ptr<AbstractCommChannel> createChannel();
   virtual void onTrap(NXCPMessage *pMsg);
   virtual void onSyslogMessage(const NXCPMessage& msg);
   virtual void onWindowsEvent(const NXCPMessage& msg);
   virtual void onDataPush(NXCPMessage *msg);
   virtual void onFileMonitoringData(NXCPMessage *msg);
   virtual void onSnmpTrap(NXCPMessage *pMsg);
   virtual void onNotify(NXCPMessage *msg);
   virtual void onFileDownload(bool success);
   virtual void onDisconnect();
   virtual uint32_t processCollectedData(NXCPMessage *msg);
   virtual uint32_t processBulkCollectedData(NXCPMessage *request, NXCPMessage *response);
   virtual bool processCustomMessage(NXCPMessage *pMsg);
   virtual void processTcpProxyData(uint32_t channelId, const void *data, size_t size, bool errorIndicator);
   virtual void getSshKeys(NXCPMessage *msg, NXCPMessage *response);

   const InetAddress& getIpAddr() const { return m_addr; }

	void debugPrintf(int level, const TCHAR *format, ...);

   void lock() { m_mutexDataLock.lock(); }
   void unlock() { m_mutexDataLock.unlock(); }
	shared_ptr<NXCPEncryptionContext> acquireEncryptionContext();
   shared_ptr<AbstractCommChannel> acquireChannel();

public:
   AgentConnection(const InetAddress& addr, uint16_t port = AGENT_LISTEN_PORT, const TCHAR *secret = nullptr, bool allowCompression = true);
   virtual ~AgentConnection();

   shared_ptr<AgentConnection> self() { return shared_from_this(); }
   shared_ptr<const AgentConnection> self() const { return shared_from_this(); }

   bool connect(RSA *serverKey = nullptr, uint32_t *error = nullptr, uint32_t *socketError = nullptr, uint64_t serverId = 0);
   void disconnect();
   bool isConnected() const { return m_isConnected; }
   bool isProxyMode() { return m_useProxy; }
	int getProtocolVersion() const { return m_nProtocolVersion; }
	bool isControlServer() const { return m_controlServer; }
	bool isMasterServer() const { return m_masterServer; }
	bool isCompressionAllowed() const { return m_allowCompression && (m_nProtocolVersion >= 4); }
	bool isFileUpdateConnection() const { return m_fileUpdateConnection; }

   bool sendMessage(NXCPMessage *msg);
   void postMessage(NXCPMessage *msg);
   bool sendRawMessage(NXCP_MESSAGE *msg);
   void postRawMessage(NXCP_MESSAGE *msg);
   NXCPMessage *waitForMessage(uint16_t code, uint32_t id, uint32_t timeout) { return m_messageWaitQueue.waitForMessage(code, id, timeout); }
   uint32_t waitForRCC(uint32_t requestId, uint32_t timeout);

   shared_ptr<ArpCache> getArpCache();
   InterfaceList *getInterfaceList();
   RoutingTable *getRoutingTable();
   uint32_t getParameter(const TCHAR *param, TCHAR *buffer, size_t size);
   uint32_t getList(const TCHAR *param, StringList **list);
   uint32_t getTable(const TCHAR *param, Table **table);
   uint32_t queryWebService(WebServiceRequestType requestType, const TCHAR *url, HttpRequestMethod httpRequestMethod, const TCHAR *requestData,
         uint32_t requestTimeout, uint32_t retentionTime, const TCHAR *login, const TCHAR *password, WebServiceAuthType authType,
         const StringMap& headers, const StringList& pathList, bool verifyCert, bool verifyHost, bool forcePlainTextParser, void *results);
   uint32_t queryWebServiceParameters(const TCHAR *url, HttpRequestMethod httpRequestMethod, const TCHAR *requestData, uint32_t requestTimeout,
         uint32_t retentionTime, const TCHAR *login, const TCHAR *password, WebServiceAuthType authType, const StringMap& headers,
         const StringList& parameters, bool verifyCert, bool verifyHost, bool forcePlainTextParser, StringMap *results);
   uint32_t queryWebServiceList(const TCHAR *url, HttpRequestMethod httpRequestMethod, const TCHAR *requestData, uint32_t requestTimeout,
         uint32_t retentionTime, const TCHAR *login, const TCHAR *password, WebServiceAuthType authType, const StringMap& headers,
         const TCHAR *path, bool verifyCert, bool verifyHost, bool forcePlainTextParser, StringList *results);
   uint32_t nop();
   uint32_t setServerCapabilities();
   uint32_t setServerId(uint64_t serverId);
   uint32_t executeCommand(const TCHAR *command, const StringList &args, bool withOutput = false,
         void (*outputCallback)(ActionCallbackEvent, const TCHAR*, void*) = nullptr, void *cbData = nullptr);
   uint32_t uploadFile(const TCHAR *localFile, const TCHAR *destinationFile = nullptr, bool allowPathExpansion = false,
            void (* progressCallback)(size_t, void *) = nullptr, void *cbArg = nullptr,
            NXCPStreamCompressionMethod compMethod = NXCP_STREAM_COMPRESSION_NONE);
   uint32_t downloadFile(const TCHAR *localFile, const TCHAR *destinationFile = nullptr, bool allowPathExpansion = false,
            void (* progressCallback)(size_t, void *) = nullptr, void *cbArg = nullptr,
            NXCPStreamCompressionMethod compMethod = NXCP_STREAM_COMPRESSION_NONE);
   uint32_t getFileFingerprint(const TCHAR *file, RemoteFileFingerprint *fp);
   uint32_t changeFileOwner(const TCHAR *file, const TCHAR *newOwner, const TCHAR *newGroup);
   uint32_t changeFilePermissions(const TCHAR *file, uint32_t permissions, const TCHAR *newOwner, const TCHAR *newGroup);
   uint32_t getFileSetInfo(const StringList &fileSet, bool allowPathExpansion, ObjectArray<RemoteFileInfo> **info);
   uint32_t startUpgrade(const TCHAR *pkgName);
   uint32_t installPackage(const TCHAR *pkgName, const TCHAR *pkgType, const TCHAR *command);
   uint32_t checkNetworkService(uint32_t *status, const InetAddress& addr, int serviceType, uint16_t port = 0, uint16_t proto = 0,
            const TCHAR *serviceRequest = nullptr, const TCHAR *serviceResponse = nullptr, uint32_t *responseTime = nullptr);
   uint32_t getSupportedParameters(ObjectArray<AgentParameterDefinition> **paramList, ObjectArray<AgentTableDefinition> **tableList);
   uint32_t readConfigFile(TCHAR **content, size_t *size);
   uint32_t writeConfigFile(const TCHAR *content);
   uint32_t enableTraps();
   uint32_t enableFileUpdates();
   uint32_t getPolicyInventory(AgentPolicyInfo **info);
   uint32_t uninstallPolicy(const uuid& guid);
   uint32_t takeScreenshot(const TCHAR *sessionName, BYTE **data, size_t *size);
   TCHAR *getHostByAddr(const InetAddress& ipAddr, TCHAR *buffer, size_t bufLen);
   uint32_t setupTcpProxy(const InetAddress& ipAddr, uint16_t port, uint32_t *channelId);
   uint32_t closeTcpProxy(uint32_t channelId);

   uint32_t generateRequestId() { return (uint32_t)InterlockedIncrement(&m_requestId); }
	NXCPMessage *customRequest(NXCPMessage *request, const TCHAR *recvFile = nullptr, bool append = false,
	         void (*downloadProgressCallback)(size_t, void*) = nullptr,
	         void (*fileResendCallback)(NXCPMessage*, void*) = nullptr, void *cbArg = nullptr);

   void setConnectionTimeout(uint32_t timeout) { m_connectionTimeout = MAX(timeout, 1000); }
	uint32_t getConnectionTimeout() const { return m_connectionTimeout; }
   void setCommandTimeout(uint32_t timeout) { m_commandTimeout = MAX(timeout, 500); }
   uint32_t getCommandTimeout() const { return m_commandTimeout; }
   void setRecvTimeout(uint32_t timeout) { m_recvTimeout = MAX(timeout, 1800000); }
   void setEncryptionPolicy(int policy) { m_encryptionPolicy = policy; }
   void setProxy(const InetAddress& addr, uint16_t port = AGENT_LISTEN_PORT, const TCHAR *secret = nullptr);
   void setPort(uint16_t port) { m_port = port; }
   void setSharedSecret(const TCHAR *secret);

   void setDeleteFileOnDownloadFailure(bool flag) { m_deleteFileOnDownloadFailure = flag; }
   uint32_t cancelFileDownload();
};

#ifdef _WIN32
template class LIBNXSRV_EXPORTABLE shared_ptr<AgentConnection>;
#endif

/**
 * Proxy SNMP transport
 */
class LIBNXSRV_EXPORTABLE SNMP_ProxyTransport : public SNMP_Transport
{
protected:
	shared_ptr<AgentConnection> m_agentConnection;
	NXCPMessage *m_response;
	InetAddress m_ipAddr;
	uint16_t m_port;
	bool m_waitForResponse;
	TCHAR m_debugId[64];

public:
	SNMP_ProxyTransport(const shared_ptr<AgentConnection>& conn, const InetAddress& ipAddr, uint16_t port);
	virtual ~SNMP_ProxyTransport();

   virtual int readMessage(SNMP_PDU **pdu, uint32_t timeout = INFINITE, struct sockaddr *sender = nullptr,
            socklen_t *addrSize = nullptr, SNMP_SecurityContext* (*contextFinder)(struct sockaddr *, socklen_t) = nullptr) override;
   virtual int sendMessage(SNMP_PDU *pdu, uint32_t timeout) override;
   virtual InetAddress getPeerIpAddress() override;
   virtual uint16_t getPort() override;
   virtual bool isProxyTransport() override;

   void setWaitForResponse(bool wait) { m_waitForResponse = wait; }
};

/**
 * ISC flags
 */
#define ISCF_IS_CONNECTED        ((UINT32)0x00000001)
#define ISCF_REQUIRE_ENCRYPTION  ((UINT32)0x00000002)

/**
 * Inter-server connection (ISC)
 */
class LIBNXSRV_EXPORTABLE ISC
{
private:
	uint32_t m_flags;
   InetAddress m_addr;
	uint16_t m_port;
   SOCKET m_socket;
   int m_protocolVersion;
   SocketMessageReceiver *m_messageReceiver;
	VolatileCounter m_requestId;
	uint32_t m_recvTimeout;
   MsgWaitQueue *m_msgWaitQueue;
   Mutex m_mutexDataLock;
   Mutex m_socketLock;
   THREAD m_hReceiverThread;
   shared_ptr<NXCPEncryptionContext> m_ctx;
   uint32_t m_commandTimeout;

   void receiverThread();
   static THREAD_RESULT THREAD_CALL receiverThreadStarter(void *);

protected:
   UINT32 setupEncryption(RSA *pServerKey);
	UINT32 connectToService(UINT32 service);

   void lock() { m_mutexDataLock.lock(); }
   void unlock() { m_mutexDataLock.unlock(); }

   virtual void printMessage(const TCHAR *format, ...);
   virtual bool onMessage(NXCPMessage *msg);

public:
   ISC();
   ISC(const InetAddress& addr, uint16_t port = NETXMS_ISC_PORT);
   virtual ~ISC();

   UINT32 connect(UINT32 service, RSA *serverKey = NULL, BOOL requireEncryption = FALSE);
	void disconnect();
   bool connected() { return m_flags & ISCF_IS_CONNECTED; };

   BOOL sendMessage(NXCPMessage *msg);
   NXCPMessage *waitForMessage(WORD code, UINT32 id, UINT32 timeOut) { return m_msgWaitQueue->waitForMessage(code, id, timeOut); }
   UINT32 waitForRCC(UINT32 rqId, UINT32 timeOut);
   UINT32 generateMessageId() { return (UINT32)InterlockedIncrement(&m_requestId); }

   UINT32 nop();
};

/**
 * Server command line tool definition
 */
struct ServerCommandLineTool
{
#ifdef _WIN32
   TCHAR **argv;
#else
   char **argv;
#endif
   int argc;
   const TCHAR *displayName;
   const TCHAR *mainHelpText;
   const char *additionalOptions;
   bool (*parseAdditionalOptionCb)(const char, const TCHAR*);
   bool (*isArgMissingCb)(int);
   int (*executeCommandCb)(AgentConnection*, int, TCHAR**, int, RSA*);
};

/**
 * Execute server command line tool
 */
int LIBNXSRV_EXPORTABLE ExecuteServerCommandLineTool(ServerCommandLineTool *tool);

void LIBNXSRV_EXPORTABLE SortRoutingTable(RoutingTable *routingTable);
const TCHAR LIBNXSRV_EXPORTABLE *AgentErrorCodeToText(uint32_t err);
uint32_t LIBNXSRV_EXPORTABLE AgentErrorToRCC(uint32_t err);

// for compatibility - new code should use nxlog_debug
#define DbgPrintf nxlog_debug

void LIBNXSRV_EXPORTABLE SetAgentDEP(int iPolicy);
void LIBNXSRV_EXPORTABLE DisableAgentConnections();

const TCHAR LIBNXSRV_EXPORTABLE *ISCErrorCodeToText(UINT32 code);

/**
 * Variables
 */
extern LIBNXSRV_EXPORTABLE_VAR(VolatileCounter64 g_flags);
extern LIBNXSRV_EXPORTABLE_VAR(ThreadPool *g_agentConnectionThreadPool);

/**
 * Helper finctions for checking server flags
 */
inline bool IsStandalone()
{
	return !(g_flags & AF_DAEMON) ? true : false;
}

inline bool IsZoningEnabled()
{
	return (g_flags & AF_ENABLE_ZONING) ? true : false;
}

#endif   /* _nxsrvapi_h_ */
