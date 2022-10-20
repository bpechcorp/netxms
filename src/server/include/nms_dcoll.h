/*
** NetXMS - Network Management System
** Copyright (C) 2003-2021 Victor Kirhenshtein
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
** File: nms_dcoll.h
**
**/

#ifndef _nms_dcoll_h_
#define _nms_dcoll_h_

/**
 * Max. length for prediction engine name
 */
#define MAX_NPE_NAME_LEN            16

/**
 * Interface for objects that can be searched
 */
class NXCORE_EXPORTABLE SearchAttributeProvider
{
public:
   virtual ~SearchAttributeProvider() = default;

   virtual SharedString getText() const = 0;
   virtual SharedString getAttribute(const TCHAR *attribute) const = 0;
};

/**
 * Instance discovery data
 */
class InstanceDiscoveryData
{
private:
   TCHAR *m_instanceName;
   uint32_t m_relatedObjectId;

public:
   InstanceDiscoveryData(const TCHAR *instanceName, uint32_t relatedObject)
   {
      m_instanceName = MemCopyString(instanceName);
      m_relatedObjectId = relatedObject;
   }
   ~InstanceDiscoveryData()
   {
      MemFree(m_instanceName);
   }

   const TCHAR *getInstanceName() const { return m_instanceName; }
   uint32_t getRelatedObject() const { return m_relatedObjectId; }
};

/**
 * Threshold check results
 */
enum class ThresholdCheckResult
{
   ACTIVATED = 0,
   DEACTIVATED = 1,
   ALREADY_ACTIVE = 2,
   ALREADY_INACTIVE = 3
};

/**
 * Statistic type
 */
enum class StatisticType
{
   CURRENT, MIN, MAX, AVERAGE
};

/**
 * DCI value
 */
class NXCORE_EXPORTABLE ItemValue
{
private:
   double m_double;
   int64_t m_int64;
   uint64_t m_uint64;
   time_t m_timestamp;
   TCHAR m_string[MAX_DB_STRING];

public:
   ItemValue();
   ItemValue(const TCHAR *value, time_t timestamp);
   ItemValue(const ItemValue *value);

   void setTimeStamp(time_t timestamp) { m_timestamp = timestamp; }
   time_t getTimeStamp() const { return m_timestamp; }

   int32_t getInt32() const { return static_cast<int32_t>(m_int64); }
   uint32_t getUInt32() const { return static_cast<uint32_t>(m_uint64); }
   int64_t getInt64() const { return m_int64; }
   uint64_t getUInt64() const { return m_uint64; }
   double getDouble() const { return m_double; }
   const TCHAR *getString() const { return m_string; }

   void set(const TCHAR *stringValue);
   void set(double value, const TCHAR *stringValue = nullptr);
   void set(int32_t value, const TCHAR *stringValue = nullptr);
   void set(int64_t value, const TCHAR *stringValue = nullptr);
   void set(uint32_t value, const TCHAR *stringValue = nullptr);
   void set(uint64_t value, const TCHAR *stringValue = nullptr);

   operator double() const { return m_double; }
   operator uint32_t() const { return static_cast<uint32_t>(m_uint64); }
   operator uint64_t() const { return m_uint64; }
   operator int32_t() const { return static_cast<int32_t>(m_int64); }
   operator int64_t() const { return m_int64; }
   operator const TCHAR*() const { return m_string; }

   const ItemValue& operator=(const ItemValue &src);
   const ItemValue& operator=(const TCHAR *value) { set(value); return *this; }
   const ItemValue& operator=(double value) { set(value); return *this; }
   const ItemValue& operator=(int32_t value) { set(value); return *this; }
   const ItemValue& operator=(int64_t value) { set(value); return *this; }
   const ItemValue& operator=(uint32_t value) { set(value); return *this; }
   const ItemValue& operator=(uint64_t value) { set(value); return *this; }
};

class DCItem;
class DataCollectionTarget;

/**
 * Threshold definition class
 */
class NXCORE_EXPORTABLE Threshold
{
private:
   uint32_t m_id;             // Unique threshold id
   uint32_t m_itemId;         // Parent item id
   uint32_t m_targetId;       // Parent data collection target ID
   uint32_t m_eventCode;      // Event code to be generated
   uint32_t m_rearmEventCode;
   ItemValue m_value;
   bool m_expandValue;
   ItemValue m_lastCheckValue;
   BYTE m_function;          // Function code
   BYTE m_operation;         // Comparision operation code
   BYTE m_dataType;          // Related item data type
	BYTE m_currentSeverity;   // Current everity (NORMAL if threshold is inactive)
   int m_sampleCount;        // Number of samples to calculate function on
   TCHAR *m_scriptSource;
   NXSL_Program *m_script;
   time_t m_lastScriptErrorReport;
   bool m_isReached;
   bool m_wasReachedBeforeMaint;
	int m_numMatches;			// Number of consecutive matches
	int m_repeatInterval;		// -1 = default, 0 = off, >0 = seconds between repeats
	time_t m_lastEventTimestamp;

   const ItemValue& value() const { return m_value; }
   void calculateAverage(ItemValue *result, const ItemValue &lastValue, ItemValue **ppPrevValues);
   void calculateTotal(ItemValue *result, const ItemValue &lastValue, ItemValue **ppPrevValues);
   void calculateAbsoluteDeviation(ItemValue *result, const ItemValue &lastValue, ItemValue **ppPrevValues);
   void calculateMeanDeviation(ItemValue *result, const ItemValue &lastValue, ItemValue **ppPrevValues);
   void setScript(TCHAR *script);

public:
	Threshold();
   Threshold(DCItem *pRelatedItem);
   Threshold(Threshold *src, bool shadowCopy);
   Threshold(DB_RESULT hResult, int iRow, DCItem *pRelatedItem);
	Threshold(ConfigEntry *config, DCItem *parentItem);
   ~Threshold();

   void bindToItem(uint32_t itemId, uint32_t targetId) { m_itemId = itemId; m_targetId = targetId; }

   uint32_t getId() const { return m_id; }
   uint32_t getEventCode() const { return m_eventCode; }
   uint32_t getRearmEventCode() const { return m_rearmEventCode; }
	int getFunction() const { return m_function; }
	int getOperation() const { return m_operation; }
	int getSampleCount() const { return m_sampleCount; }
   const TCHAR *getStringValue() const { return m_value.getString(); }
   bool isReached() const { return m_isReached; }
   bool wasReachedBeforeMaintenance() const { return m_wasReachedBeforeMaint; }
   const ItemValue& getLastCheckValue() const { return m_lastCheckValue; }
	int getRepeatInterval() const { return m_repeatInterval; }
	time_t getLastEventTimestamp() const { return m_lastEventTimestamp; }
	int getCurrentSeverity() const { return m_currentSeverity; }
	bool needValueExpansion() const { return m_expandValue; }

	void markLastEvent(int severity);
   void updateBeforeMaintenanceState() { m_wasReachedBeforeMaint = m_isReached; }
   void setLastCheckedValue(const ItemValue &value) { m_lastCheckValue = value; }

   bool saveToDB(DB_HANDLE hdb, uint32_t index);
   ThresholdCheckResult check(ItemValue &value, ItemValue **ppPrevValues, ItemValue &fvalue, ItemValue &tvalue, shared_ptr<NetObj> target, DCItem *dci);
   ThresholdCheckResult checkError(UINT32 dwErrorCount);

   void fillMessage(NXCPMessage *msg, uint32_t baseId) const;
   void updateFromMessage(const NXCPMessage& msg, uint32_t baseId);

   void createId();
   uint32_t getRequiredCacheSize() { return ((m_function == F_LAST) || (m_function == F_ERROR)) ? 0 : m_sampleCount; }

   bool equals(const Threshold *t) const;

   void createExportRecord(StringBuffer &xml, int index) const;
   json_t *toJson() const;

	void associate(DCItem *pItem);
	void setDataType(BYTE type) { m_dataType = type; }

	void reconcile(const Threshold *src);

   bool isUsingEvent(uint32_t eventCode) const { return (eventCode == m_eventCode || eventCode == m_rearmEventCode); }
};

class DataCollectionOwner;
class DCObjectInfo;

/**
 * DCObject storage class
 */
enum class DCObjectStorageClass
{
   DEFAULT = 0,
   BELOW_7 = 1,
   BELOW_30 = 2,
   BELOW_90 = 3,
   BELOW_180 = 4,
   OTHER = 5
};

/**
 * Data collection object poll schedule types
 */
enum DCObjectPollingScheduleType
{
   DC_POLLING_SCHEDULE_DEFAULT = 0,
   DC_POLLING_SCHEDULE_CUSTOM = 1,
   DC_POLLING_SCHEDULE_ADVANCED = 2
};

/**
 * Data collection object retention types
 */
enum DCObjectRetentionType
{
   DC_RETENTION_DEFAULT = 0,
   DC_RETENTION_CUSTOM = 1,
   DC_RETENTION_NONE = 2
};

#ifdef _WIN32
template class NXCORE_EXPORTABLE weak_ptr<DataCollectionOwner>;
#endif

/**
 * Generic data collection object
 */
class NXCORE_EXPORTABLE DCObject : public SearchAttributeProvider
{
   friend class DCObjectInfo;

protected:
   uint32_t m_id;
   uuid m_guid;
   weak_ptr<DataCollectionOwner> m_owner;  // Pointer to node or template object this item related to
   uint32_t m_ownerId;                     // Cached owner object ID
   SharedString m_name;
   SharedString m_description;
   SharedString m_systemTag;
   time_t m_lastPoll;           // Last poll time
   time_t m_lastValueTimestamp; // Timestamp of last obtained value
   int m_pollingInterval;       // Polling interval in seconds
   int m_retentionTime;         // Retention time in days
   TCHAR *m_pollingIntervalSrc;
   TCHAR *m_retentionTimeSrc;
   BYTE m_pollingScheduleType;   // Polling schedule type - default, custom fixed, advanced
   BYTE m_retentionType;         // Retention type - default, custom, do not store
   BYTE m_source;                // origin: SNMP, agent, etc.
   BYTE m_status;                // Item status: active, disabled or not supported
   BYTE m_busy;                  // 1 when item is queued for polling, 0 if not
	BYTE m_scheduledForDeletion;  // 1 when item is scheduled for deletion, 0 if not
   uint32_t m_flags;             // user flags
   uint32_t m_stateFlags;        // runtime flags
   uint32_t m_dwTemplateId;      // Related template's id
   uint32_t m_dwTemplateItemId;  // Related template item's id
   Mutex m_mutex;
   StringList *m_schedules;
   time_t m_tLastCheck;          // Last schedule checking time
   uint32_t m_errorCount;        // Consequtive collection error count
   uint32_t m_resourceId;	   	// Associated cluster resource ID
   uint32_t m_sourceNode;        // Source node ID or 0 to disable
	uint16_t m_snmpPort;          // Custom SNMP port or 0 for node default
	SNMP_Version m_snmpVersion;   // Custom SNMP version or SNMP_VERSION_DEFAULT for node default
	TCHAR *m_pszPerfTabSettings;
   TCHAR *m_transformationScriptSource;   // Transformation script (source code)
   NXSL_Program *m_transformationScript;  // Compiled transformation script
   time_t m_lastScriptErrorReport;
	TCHAR *m_comments;
	bool m_doForcePoll;                    // Force poll indicator
	ClientSession *m_pollingSession;       // Force poll requestor session
   uint16_t m_instanceDiscoveryMethod;
   SharedString m_instanceDiscoveryData;  // Instance discovery data (instance value for discovered DCIs and method specific data for prototype)
   TCHAR *m_instanceFilterSource;
   NXSL_Program *m_instanceFilter;
   SharedString m_instanceName;           // Instance display name (used only for display purposes)
   IntegerArray<uint32_t> m_accessList;
   time_t m_instanceGracePeriodStart;  // Start of grace period for missing instance
   int32_t m_instanceRetentionTime;      // Retention time if instance is not found
   time_t m_startTime;                 // Time to start data collection
   uint32_t m_relatedObject;

   void lock() const { m_mutex.lock(); }
   bool tryLock() const { return m_mutex.tryLock(); }
   void unlock() const { m_mutex.unlock(); }

   bool loadAccessList(DB_HANDLE hdb);
	bool loadCustomSchedules(DB_HANDLE hdb);
	String expandSchedule(const TCHAR *schedule);

   void updateTimeIntervalsInternal();

	StringBuffer expandMacros(const TCHAR *src, size_t dstLen);

   void setTransformationScript(TCHAR *source);

	virtual bool isCacheLoaded();
   virtual shared_ptr<DCObjectInfo> createDescriptorInternal() const;

	// --- constructors ---
   DCObject(const shared_ptr<DataCollectionOwner>& owner);
   DCObject(uint32_t id, const TCHAR *name, int source, BYTE scheduleType, const TCHAR *pollingInterval,
         BYTE retentionType, const TCHAR *retentionTime, const shared_ptr<DataCollectionOwner>& owner,
         const TCHAR *description = nullptr, const TCHAR *systemTag = nullptr);
	DCObject(ConfigEntry *config, const shared_ptr<DataCollectionOwner>& owner);
   DCObject(const DCObject *src, bool shadowCopy);

public:
	virtual ~DCObject();

   virtual DCObject *clone() const = 0;

	virtual int getType() const { return DCO_TYPE_GENERIC; }

   virtual void updateFromTemplate(DCObject *dcObject);
   virtual void updateFromImport(ConfigEntry *config);

   virtual bool saveToDatabase(DB_HANDLE hdb);
   virtual void deleteFromDatabase();
   virtual bool loadThresholdsFromDB(DB_HANDLE hdb);
   virtual void loadCache() = 0;

   void processNewError(bool noInstance);
   virtual void processNewError(bool noInstance, time_t now);
   virtual void updateThresholdsBeforeMaintenanceState();
   virtual void generateEventsBasedOnThrDiff();

   virtual void fillLastValueSummaryMessage(NXCPMessage *bsg, uint32_t baseId, const TCHAR *column = nullptr, const TCHAR *instance = nullptr) = 0;
   virtual void fillLastValueMessage(NXCPMessage *msg) = 0;

   shared_ptr<DCObjectInfo> createDescriptor() const;

   uint32_t getId() const { return m_id; }
	const uuid& getGuid() const { return m_guid; }
   int getDataSource() const { return m_source; }
   const TCHAR *getDataProviderName() const { return getDataProviderName(m_source); }
   int getStatus() const { return m_status; }
   SharedString getName() const { return GetAttributeWithLock(m_name, m_mutex); }
   SharedString getDescription() const { return GetAttributeWithLock(m_description, m_mutex); }
	const TCHAR *getPerfTabSettings() const { return m_pszPerfTabSettings; }
   int getEffectivePollingInterval() const { return (m_pollingScheduleType == DC_POLLING_SCHEDULE_CUSTOM) ? std::max(m_pollingInterval, 1) : m_defaultPollingInterval; }
   shared_ptr<DataCollectionOwner> getOwner() const { return m_owner.lock(); }
   uint32_t getOwnerId() const { return m_ownerId; }
   const TCHAR *getOwnerName() const;
   uint32_t getTemplateId() const { return m_dwTemplateId; }
   uint32_t getTemplateItemId() const { return m_dwTemplateItemId; }
   uint32_t getResourceId() const { return m_resourceId; }
   uint32_t getSourceNode() const { return m_sourceNode; }
	time_t getLastPollTime() const { return m_lastPoll; }
   time_t getLastValueTimestamp() const { return m_lastValueTimestamp; }
   uint32_t getErrorCount() const { return m_errorCount; }
	uint16_t getSnmpPort() const { return m_snmpPort; }
   SNMP_Version getSnmpVersion() const { return m_snmpVersion; }
   bool isShowOnObjectTooltip() const { return (m_flags & DCF_SHOW_ON_OBJECT_TOOLTIP) ? true : false; }
   bool isShowInObjectOverview() const { return (m_flags & DCF_SHOW_IN_OBJECT_OVERVIEW) ? true : false; }
   bool isAggregateOnCluster() const { return (m_flags & DCF_AGGREGATE_ON_CLUSTER) ? true : false; }
	bool isStatusDCO() const { return (m_flags & DCF_CALCULATE_NODE_STATUS) ? true : false; }
   bool isAggregateWithErrors() const { return (m_flags & DCF_AGGREGATE_WITH_ERRORS) ? true : false; }
   bool isStoreChangesOnly() const { return (m_flags & DCF_STORE_CHANGES_ONLY) ? true : false; }
   bool isAdvancedSchedule() const { return m_pollingScheduleType == DC_POLLING_SCHEDULE_ADVANCED; }
   int getAggregationFunction() const { return DCF_GET_AGGREGATION_FUNCTION(m_flags); }
   DCObjectStorageClass getStorageClass() const { return (m_retentionType == DC_RETENTION_CUSTOM) ? storageClassFromRetentionTime(m_retentionTime) : DCObjectStorageClass::DEFAULT; }
   int getEffectiveRetentionTime() const { return (m_retentionType == DC_RETENTION_CUSTOM) ? std::max(m_retentionTime, 1) : m_defaultRetentionTime; }
   bool isDataStorageEnabled() const { return m_retentionType != DC_RETENTION_NONE; }
   int16_t getAgentCacheMode();
   bool hasValue();
   bool hasAccess(uint32_t userId);
   uint32_t getRelatedObject() const { return m_relatedObject; }
   bool isDisabledByUser() { return (m_stateFlags & DCO_STATE_DISABLED_BY_USER) ? true : false; }

	bool matchClusterResource();
   bool isReadyForPolling(time_t currTime);
	bool isScheduledForDeletion() const { return m_scheduledForDeletion ? true : false; }
   void setLastPollTime(time_t lastPoll) { m_lastPoll = lastPoll; }
   void setStatus(int status, bool generateEvent, bool userChange = false);
   void setBusyFlag() { m_busy = 1; }
   void clearBusyFlag() { m_busy = 0; }
   void setTemplateId(UINT32 dwTemplateId, UINT32 dwItemId) { m_dwTemplateId = dwTemplateId; m_dwTemplateItemId = dwItemId; }
   void updateTimeIntervals() { lock(); updateTimeIntervalsInternal(); unlock(); }
   void fillSchedulingDataMessage(NXCPMessage *msg, uint32_t base) const;

   virtual void createMessage(NXCPMessage *pMsg);
   virtual void updateFromMessage(const NXCPMessage& msg);

   virtual void changeBinding(UINT32 newId, shared_ptr<DataCollectionOwner> newOwner, bool doMacroExpansion);

	virtual bool deleteAllData();
	virtual bool deleteEntry(time_t timestamp);

   virtual void getEventList(HashSet<uint32_t> *eventList) const = 0;
   virtual bool isUsingEvent(uint32_t eventCode) const = 0;
   virtual void createExportRecord(StringBuffer &xml) const = 0;
   virtual void getScriptDependencies(StringSet *dependencies) const;
   virtual json_t *toJson();

   NXSL_Value *createNXSLObject(NXSL_VM *vm) const;

   ClientSession *processForcePoll();
   void requestForcePoll(ClientSession *session);
   bool isForcePollRequested() { return m_doForcePoll; }
	bool prepareForDeletion();

	uint16_t getInstanceDiscoveryMethod() const { return m_instanceDiscoveryMethod; }
	SharedString getInstanceDiscoveryData() const { return GetAttributeWithLock(m_instanceDiscoveryData, m_mutex); }
   int32_t getInstanceRetentionTime() const { return m_instanceRetentionTime; }
   StringObjectMap<InstanceDiscoveryData> *filterInstanceList(StringMap *instances);
   void setInstanceDiscoveryMethod(uint16_t method) { m_instanceDiscoveryMethod = method; }
   void setInstanceDiscoveryData(const TCHAR *data) { lock(); m_instanceDiscoveryData = data; unlock(); }
   void setInstanceFilter(const TCHAR *script);
   void setInstanceName(const TCHAR *instanceName) { lock(); m_instanceName = instanceName; unlock(); }
   SharedString getInstanceName() const { return GetAttributeWithLock(m_instanceName, m_mutex); }
   void expandInstance();
   time_t getInstanceGracePeriodStart() const { return m_instanceGracePeriodStart; }
   void setInstanceGracePeriodStart(time_t t) { m_instanceGracePeriodStart = t; }
   void setRelatedObject(uint32_t relatedObject) { m_relatedObject = relatedObject; }
   void setPollingIntervalType(BYTE pollingScheduleType);
   void setPollingInterval(const TCHAR *schedule);
   void setRetentionType(BYTE retentionType);
   void setRetention(const TCHAR *schedule);

	static int m_defaultRetentionTime;
	static int m_defaultPollingInterval;

   static DCObjectStorageClass storageClassFromRetentionTime(int retentionTime);
   static const TCHAR *getStorageClassName(DCObjectStorageClass storageClass);
   static const TCHAR *getDataProviderName(int dataProvider);

   virtual SharedString getText() const override;
   virtual SharedString getAttribute(const TCHAR *attribute) const override;
};

/**
 * Data collection item class
 */
class NXCORE_EXPORTABLE DCItem : public DCObject
{
   friend class DCObjectInfo;

protected:
   BYTE m_deltaCalculation;      // Delta calculation method
   BYTE m_dataType;
	int m_sampleCount;            // Number of samples required to calculate value
	ObjectArray<Threshold> *m_thresholds;
   uint32_t m_cacheSize;          // Number of items in cache
   uint32_t m_requiredCacheSize;
   ItemValue **m_ppValueCache;
   ItemValue m_prevRawValue;     // Previous raw value (used for delta calculation)
   time_t m_tPrevValueTimeStamp;
   bool m_bCacheLoaded;
	int m_multiplier;
	SharedString m_unitName;
	uint16_t m_snmpRawValueType;		// Actual SNMP raw value type for input transformation
	TCHAR m_predictionEngine[MAX_NPE_NAME_LEN];

   bool transform(ItemValue &value, time_t nElapsedTime);
   void checkThresholds(ItemValue &value);
   void updateCacheSizeInternal(bool allowLoad, uint32_t conditionId = 0);
   void clearCache();

   bool hasScriptThresholds() const;
   Threshold *getThresholdById(UINT32 id) const;

	virtual bool isCacheLoaded() override;
   virtual shared_ptr<DCObjectInfo> createDescriptorInternal() const override;

   using DCObject::updateFromMessage;

public:
   DCItem(const DCItem *src, bool shadowCopy);
   DCItem(DB_HANDLE hdb, DB_RESULT hResult, int row, const shared_ptr<DataCollectionOwner>& owner, bool useStartupDelay);
   DCItem(UINT32 id, const TCHAR *name, int source, int dataType, BYTE scheduleType, const TCHAR *pollingInterval,
         BYTE retentionType, const TCHAR *retentionTime, const shared_ptr<DataCollectionOwner>& owner,
         const TCHAR *description = nullptr, const TCHAR *systemTag = nullptr);
	DCItem(ConfigEntry *config, const shared_ptr<DataCollectionOwner>& owner);
   virtual ~DCItem();

   virtual DCObject *clone() const override;

	virtual int getType() const override { return DCO_TYPE_ITEM; }

   virtual void updateFromTemplate(DCObject *dcObject) override;
   virtual void updateFromImport(ConfigEntry *config) override;

   virtual bool saveToDatabase(DB_HANDLE hdb) override;
   virtual void deleteFromDatabase() override;
   virtual bool loadThresholdsFromDB(DB_HANDLE hdb) override;
   virtual void loadCache() override;

   void updateCacheSize(uint32_t conditionId = 0) { lock(); updateCacheSizeInternal(true, conditionId); unlock(); }
   void reloadCache(bool forceReload);

   int getDataType() const { return m_dataType; }
   int getNXSLDataType() const;
   int getDeltaCalculationMethod() const { return m_deltaCalculation; }
	bool isInterpretSnmpRawValue() const { return (m_flags & DCF_RAW_VALUE_OCTET_STRING) ? true : false; }
	WORD getSnmpRawValueType() const { return m_snmpRawValueType; }
	bool hasActiveThreshold() const;
   int getThresholdSeverity() const;
	int getSampleCount() const { return m_sampleCount; }
	const TCHAR *getPredictionEngine() const { return m_predictionEngine; }
	int getMultiplier() const { return m_multiplier; }
	SharedString getUnitName() const { return GetAttributeWithLock(m_unitName, m_mutex); }

	uint64_t getCacheMemoryUsage() const;

   bool processNewValue(time_t nTimeStamp, const TCHAR *value, bool *updateStatus);

   virtual void processNewError(bool noInstance, time_t now) override;
   virtual void updateThresholdsBeforeMaintenanceState() override;
   virtual void generateEventsBasedOnThrDiff() override;

   virtual void fillLastValueSummaryMessage(NXCPMessage *bsg, uint32_t baseId,const TCHAR *column = nullptr, const TCHAR *instance = nullptr) override;
   virtual void fillLastValueMessage(NXCPMessage *msg) override;
   NXSL_Value *getValueForNXSL(NXSL_VM *vm, int function, int sampleCount);
   NXSL_Value *getRawValueForNXSL(NXSL_VM *vm);
   const TCHAR *getLastValue();
   ItemValue *getInternalLastValue();
   TCHAR *getAggregateValue(AggregationFunction func, time_t periodStart, time_t periodEnd);

   virtual void createMessage(NXCPMessage *msg) override;
   void updateFromMessage(const NXCPMessage& msg, uint32_t *numMaps, uint32_t **mapIndex, uint32_t **mapId);
   void fillMessageWithThresholds(NXCPMessage *msg, bool activeOnly);

   virtual void changeBinding(UINT32 newId, shared_ptr<DataCollectionOwner> newOwner, bool doMacroExpansion) override;

	virtual bool deleteAllData() override;
   virtual bool deleteEntry(time_t timestamp) override;

   virtual void getEventList(HashSet<uint32_t> *eventList) const override;
   virtual bool isUsingEvent(uint32_t eventCode) const override;
   virtual void createExportRecord(StringBuffer &str) const override;
   virtual json_t *toJson() override;

	int getThresholdCount() const { return (m_thresholds != nullptr) ? m_thresholds->size() : 0; }

	void setUnitName(const SharedString &unitName) { SetAttributeWithLock(m_unitName, unitName, m_mutex); }
	void setDataType(int dataType) { m_dataType = dataType; }
	void setDeltaCalculationMethod(int method) { m_deltaCalculation = method; }
	void setAllThresholdsFlag(BOOL bFlag) { if (bFlag) m_flags |= DCF_ALL_THRESHOLDS; else m_flags &= ~DCF_ALL_THRESHOLDS; }
	void addThreshold(Threshold *pThreshold);
	void deleteAllThresholds();

	void prepareForRecalc();
	void recalculateValue(ItemValue &value);

   static bool testTransformation(DataCollectionTarget *object, const shared_ptr<DCObjectInfo>& dcObjectInfo,
            const TCHAR *script, const TCHAR *value, TCHAR *buffer, size_t bufSize);
};

struct TableThresholdCbData;

/**
 * Table column definition
 */
class NXCORE_EXPORTABLE DCTableColumn
{
private:
	TCHAR m_name[MAX_COLUMN_NAME];
   TCHAR *m_displayName;
	SNMP_ObjectId *m_snmpOid;
	uint16_t m_flags;

public:
	DCTableColumn(const DCTableColumn *src);
	DCTableColumn(const NXCPMessage& msg, uint32_t baseId);
	DCTableColumn(DB_RESULT hResult, int row);
   DCTableColumn(ConfigEntry *e);
	~DCTableColumn();

	const TCHAR *getName() const { return m_name; }
   const TCHAR *getDisplayName() const { return (m_displayName != NULL) ? m_displayName : m_name; }
   uint16_t getFlags() const { return m_flags; }
   int getDataType() const { return TCF_GET_DATA_TYPE(m_flags); }
   int getAggregationFunction() const { return TCF_GET_AGGREGATION_FUNCTION(m_flags); }
	const SNMP_ObjectId *getSnmpOid() const { return m_snmpOid; }
   bool isInstanceColumn() const { return (m_flags & TCF_INSTANCE_COLUMN) != 0; }
   bool isConvertSnmpStringToHex() const { return (m_flags & TCF_SNMP_HEX_STRING) != 0; }

   void fillMessage(NXCPMessage *msg, uint32_t baseId) const;
   void createExportRecord(StringBuffer &xml, int id) const;
   json_t *toJson() const;
};

/**
 * Table column ID hash entry
 */
struct TC_ID_MAP_ENTRY
{
	INT32 id;
	TCHAR name[MAX_COLUMN_NAME];
};

/**
 * Condition for table DCI threshold
 */
class NXCORE_EXPORTABLE DCTableCondition
{
private:
   TCHAR *m_column;
   int m_operation;
   ItemValue m_value;

public:
   DCTableCondition(const TCHAR *column, int operation, const TCHAR *value);
   DCTableCondition(DCTableCondition *src);
   ~DCTableCondition();

   bool check(Table *value, int row);

   const TCHAR *getColumn() const { return m_column; }
   int getOperation() const { return m_operation; }
   const TCHAR *getValue() const { return m_value.getString(); }

   json_t *toJson() const;

   bool equals(DCTableCondition *c) const;
};

/**
 * Condition group for table DCI threshold
 */
class NXCORE_EXPORTABLE DCTableConditionGroup
{
private:
   ObjectArray<DCTableCondition> *m_conditions;

public:
   DCTableConditionGroup();
   DCTableConditionGroup(const NXCPMessage& msg, uint32_t *baseId);
   DCTableConditionGroup(DCTableConditionGroup *src);
   DCTableConditionGroup(ConfigEntry *e);
   ~DCTableConditionGroup();

   void addCondition(DCTableCondition *c) { m_conditions->add(c); }

   const ObjectArray<DCTableCondition> *getConditions() const { return m_conditions; }

   uint32_t fillMessage(NXCPMessage *msg, uint32_t baseId) const;
   json_t *toJson() const;

   bool equals(DCTableConditionGroup *g) const;

   bool check(Table *value, int row);
};

/**
 * Threshold instance
 */
class NXCORE_EXPORTABLE DCTableThresholdInstance
{
private:
   TCHAR *m_name;
   int m_matchCount;
   bool m_active;
   int m_row;

public:
   DCTableThresholdInstance(const TCHAR *name, int matchCount, bool active, int row);
   DCTableThresholdInstance(const DCTableThresholdInstance *src);
   ~DCTableThresholdInstance();

   const TCHAR *getName() const { return m_name; }
   int getMatchCount() const { return m_matchCount; }
   int getRow() const { return m_row; }
   bool isActive() const { return m_active; }
   void updateRow(int row) { m_row = row; }

   void incMatchCount() { m_matchCount++; }
   void setActive() { m_active = true; }
};

#ifdef _WIN32
template class NXCORE_EXPORTABLE ObjectArray<DCTableConditionGroup>;
template class NXCORE_EXPORTABLE StringObjectMap<DCTableThresholdInstance>;
#endif

/**
 * Threshold definition for tabe DCI
 */
class NXCORE_EXPORTABLE DCTableThreshold
{
private:
   uint32_t m_id;
   ObjectArray<DCTableConditionGroup> m_groups;
   uint32_t m_activationEvent;
   uint32_t m_deactivationEvent;
   int m_sampleCount;
   StringObjectMap<DCTableThresholdInstance> m_instances;
   StringObjectMap<DCTableThresholdInstance> m_instancesBeforeMaint;

   void loadConditions(DB_HANDLE hdb);
   void loadInstances(DB_HANDLE hdb);

   DCTableThresholdInstance *findInstance(const TCHAR *instance, bool originalList)
   {
      return originalList ? m_instances.get(instance) : m_instancesBeforeMaint.get(instance);
   }

   static EnumerationCallbackResult eventGenerationCallback(const TCHAR *key, const DCTableThresholdInstance *value, TableThresholdCbData *context);

public:
   DCTableThreshold();
   DCTableThreshold(DB_HANDLE hdb, DB_RESULT hResult, int row);
   DCTableThreshold(const NXCPMessage& msg, uint32_t *baseId);
   DCTableThreshold(const DCTableThreshold *src, bool shadowCopy);
   DCTableThreshold(ConfigEntry *e);

   void copyState(DCTableThreshold *src);

   ThresholdCheckResult check(Table *value, int row, const TCHAR *instance);
   void updateBeforeMaintenanceState();
   void generateEventsBasedOnThrDiff(TableThresholdCbData *data);

   bool saveToDatabase(DB_HANDLE hdb, uint32_t tableId, int seq) const;
   uint32_t fillMessage(NXCPMessage *msg, uint32_t baseId) const;

   void createExportRecord(StringBuffer &xml, int id) const;
   json_t *toJson() const;

   bool equals(const DCTableThreshold *t) const;

   uint32_t getId() const { return m_id; }
   uint32_t getActivationEvent() const { return m_activationEvent; }
   uint32_t getDeactivationEvent() const { return m_deactivationEvent; }
   int getSampleCount() const { return m_sampleCount; }
   StringBuffer getConditionAsText() const;

   bool isActive() const
   {
      return m_instances.findElement([] (const TCHAR *key, const void *value, void *context) { return static_cast<const DCTableThresholdInstance*>(value)->isActive(); }, nullptr);
   }

   bool isUsingEvent(uint32_t eventCode) const { return (eventCode == m_activationEvent || eventCode == m_deactivationEvent); }
};

/**
 * Tabular data collection object
 */
class NXCORE_EXPORTABLE DCTable : public DCObject
{
   friend class DCObjectInfo;

protected:
	ObjectArray<DCTableColumn> *m_columns;
   ObjectArray<DCTableThreshold> *m_thresholds;
	shared_ptr<Table> m_lastValue;

	static TC_ID_MAP_ENTRY *m_cache;
	static int m_cacheSize;
	static int m_cacheAllocated;
	static Mutex m_cacheMutex;

   bool transform(const shared_ptr<Table>& value);
   void checkThresholds(Table *value);

   bool loadThresholds(DB_HANDLE hdb);
   bool saveThresholds(DB_HANDLE hdb);

public:
   DCTable(const DCTable *src, bool shadowCopy);
   DCTable(UINT32 id, const TCHAR *name, int source, BYTE scheduleType, const TCHAR *pollingInterval,
         BYTE retentionType, const TCHAR *retentionTime, const shared_ptr<DataCollectionOwner>& owner,
         const TCHAR *description = nullptr, const TCHAR *systemTag = nullptr);
   DCTable(DB_HANDLE hdb, DB_RESULT hResult, int row, const shared_ptr<DataCollectionOwner>& owner, bool useStartupDelay);
   DCTable(ConfigEntry *config, const shared_ptr<DataCollectionOwner>& owner);
	virtual ~DCTable();

	virtual DCObject *clone() const override;

	virtual int getType() const override { return DCO_TYPE_TABLE; }

   virtual void updateFromTemplate(DCObject *dcObject) override;
   virtual void updateFromImport(ConfigEntry *config) override;

   virtual bool saveToDatabase(DB_HANDLE hdb) override;
   virtual void deleteFromDatabase() override;
   virtual void loadCache() override;

   virtual void processNewError(bool noInstance, time_t now) override;
   virtual void updateThresholdsBeforeMaintenanceState() override;
   virtual void generateEventsBasedOnThrDiff() override;

   virtual void createMessage(NXCPMessage *msg) override;
   virtual void updateFromMessage(const NXCPMessage& msg) override;

	virtual bool deleteAllData() override;
   virtual bool deleteEntry(time_t timestamp) override;

   virtual void getEventList(HashSet<uint32_t> *eventList) const override;
   virtual bool isUsingEvent(uint32_t eventCode) const override;
   virtual void createExportRecord(StringBuffer &xml) const override;
   virtual json_t *toJson() override;

   bool processNewValue(time_t nTimeStamp, const shared_ptr<Table>& value, bool *updateStatus);

   virtual void fillLastValueSummaryMessage(NXCPMessage *bsg, uint32_t baseId,const TCHAR *column = nullptr, const TCHAR *instance = nullptr) override;
   virtual void fillLastValueMessage(NXCPMessage *msg) override;

   int getColumnDataType(const TCHAR *name) const;
   const ObjectArray<DCTableColumn>& getColumns() const { return *m_columns; }
   shared_ptr<Table> getLastValue();
   IntegerArray<UINT32> *getThresholdIdList();

   void mergeValues(Table *dest, Table *src, int count);

   void updateResultColumns(const shared_ptr<Table>& t);

	static INT32 columnIdFromName(const TCHAR *name);
};

/**
 * Callback data for after maintenance event generation
 */
struct TableThresholdCbData
{
   DCTableThreshold *threshold;
   DCTable *table;
   bool originalList;
};

/**
 * Data collection object information (for NXSL)
 */
class DCObjectInfo
{
   friend class DCObject;
   friend class DCItem;
   friend class DCTable;

private:
   uint32_t m_id;
   uint32_t m_ownerId;
   uint32_t m_templateId;
   uint32_t m_templateItemId;
   int m_type;
   TCHAR m_name[MAX_ITEM_NAME];
   TCHAR m_description[MAX_DB_STRING];
   TCHAR m_systemTag[MAX_DB_STRING];
   TCHAR m_instanceName[MAX_DB_STRING];
   TCHAR *m_instanceData;
   TCHAR *m_comments;
   int m_dataType;
   int m_origin;
   int m_status;
   uint32_t m_errorCount;
   int32_t m_pollingInterval;
   time_t m_lastPollTime;
   time_t m_lastCollectionTime;
   bool m_hasActiveThreshold;
   int m_thresholdSeverity;
   uint32_t m_relatedObject;

public:
   DCObjectInfo(const DCObject& object);
   DCObjectInfo(const NXCPMessage& msg, const DCObject *object);
   ~DCObjectInfo();

   uint32_t getId() const { return m_id; }
   uint32_t getTemplateId() const { return m_templateId; }
   uint32_t getTemplateItemId() const { return m_templateItemId; }
   int getType() const { return m_type; }
   const TCHAR *getName() const { return m_name; }
   const TCHAR *getDescription() const { return m_description; }
   const TCHAR *getSystemTag() const { return m_systemTag; }
   const TCHAR *getInstanceName() const { return m_instanceName; }
   const TCHAR *getInstanceData() const { return m_instanceData; }
   const TCHAR *getComments() const { return m_comments; }
   int getDataType() const { return m_dataType; }
   int getOrigin() const { return m_origin; }
   int getStatus() const { return m_status; }
   uint32_t getErrorCount() const { return m_errorCount; }
   int32_t getPollingInterval() const { return m_pollingInterval; }
   time_t getLastPollTime() const { return m_lastPollTime; }
   time_t getLastCollectionTime() const { return m_lastCollectionTime; }
   uint32_t getOwnerId() const { return m_ownerId; }
   bool hasActiveThreshold() const { return m_hasActiveThreshold; }
   int getThresholdSeverity() const { return m_thresholdSeverity; }
   uint32_t getRelatedObject() const { return m_relatedObject; }
};

/**
 * Functions
 */
void InitDataCollector();
void DeleteAllItemsForNode(UINT32 dwNodeId);
void WriteFullParamListToMessage(NXCPMessage *pMsg, int origin, WORD flags);
int GetDCObjectType(UINT32 nodeId, UINT32 dciId);

void CalculateItemValueDiff(ItemValue *result, int dataType, const ItemValue &value1, const ItemValue &value2);
void CalculateItemValueAverage(ItemValue *result, int dataType, const ItemValue * const *valueList, size_t sampleCount);
void CalculateItemValueMeanDeviation(ItemValue *result, int dataType, const ItemValue * const *valueList, size_t sampleCount);
void CalculateItemValueTotal(ItemValue *result, int dataType, const ItemValue *const *valueList, size_t sampleCount);
void CalculateItemValueMin(ItemValue *result, int dataType, const ItemValue *const *valueList, size_t sampleCount);
void CalculateItemValueMax(ItemValue *result, int dataType, const ItemValue *const *valueList, size_t sampleCount);

DataCollectionError GetQueueStatistic(const TCHAR *parameter, StatisticType type, TCHAR *value);

uint64_t GetDCICacheMemoryUsage();

/**
 * DCI cache loader queue
 */
extern SharedObjectQueue<DCObjectInfo> g_dciCacheLoaderQueue;

#endif   /* _nms_dcoll_h_ */
