# Single-Table Cassandra Design - Final Version with entry_id

## Updated Schema with entry_id
```cql
CREATE KEYSPACE IF NOT EXISTS prefs
WITH replication = {'class': 'NetworkTopologyStrategy', 'DC1': '3'}
AND durable_writes = true;

CREATE TABLE IF NOT EXISTS prefs.user_preferences (
  -- Partition Key
  user_id text,
  
  -- Clustering Keys (creates sorted, wide row)
  resource_type text,      -- 'META', 'TOGGLE', 'PREF', 'FAV_ACC', 'FAV_PAR', 'SORT_ACC', 'SORT_PAR'
  entry_id uuid,           -- Unique identifier for each preference entry
  compat_ver text,         -- 'v1', 'v2', etc.
  
  -- Identity Columns
  item_id text,            -- For TOGGLE/PREF: toggleableId/preferenceId
                           -- For FAV_*/SORT_*: domainId (from domain service)
  
  -- Data Columns (sparse - only populated when relevant)
  bool_value boolean,      -- For toggleables and favorites
  text_value text,         -- For preferences
  int_value int,           -- For sortables (order)
  json_value text,         -- For complex data or full snapshots
  
  -- Domain Service Reference
  domain_id text,          -- FK to domain service (domainId)
  entity_type text,        -- Entity type from domain service
  
  -- Metadata
  created_at timestamp,
  updated_at timestamp,
  version int,
  
  PRIMARY KEY (user_id, resource_type, entry_id, compat_ver)
) WITH CLUSTERING ORDER BY (resource_type ASC, entry_id ASC, compat_ver ASC)
  AND compaction = {'class': 'LeveledCompactionStrategy'}
  AND compression = {'class': 'LZ4Compressor'}
  AND caching = {'keys': 'ALL', 'rows_per_partition': 'ALL'};
```

---

## Clarified Data Model

### Column Definitions

| Column | Purpose | Example Values |
|--------|---------|----------------|
| **user_id** | Partition key - user identifier | `'user123'`, `'user456'` |
| **resource_type** | Type of preference | `'TOGGLE'`, `'PREF'`, `'FAV_ACC'`, `'SORT_ACC'` |
| **entry_id** | Unique ID for this preference entry (UUID) | `550e8400-e29b-41d4-a716-446655440000` |
| **compat_ver** | Compatibility version | `'v1'`, `'v2'`, `'v3'` |
| **item_id** | Business identifier (toggleableId, preferenceId, or domainId) | `'darkMode'`, `'language'`, `'domain1'` |
| **domain_id** | Reference to domain service (for FAV_*/SORT_*) | `'ACCOUNT'`, `'PARTNER'`, `'domain123'` |
| **entity_type** | Entity type from domain service | `'DASHBOARD'`, `'WIDGET'`, `'REPORT'` |

### Distinction: entry_id vs item_id vs domain_id

**entry_id (UUID):**
- Internal Cassandra identifier (primary key component)
- Unique across all preference entries
- Immutable once created
- Used for direct row lookups and updates

**item_id (text):**
- **For TOGGLE/PREF:** Business identifier (e.g., `'darkMode'`, `'language'`, `'timezone'`)
- **For FAV_*/SORT_*:** The domainId from domain service (e.g., `'domain1'`, `'domain2'`)
- Human-readable identifier
- Used in API responses and queries

**domain_id (text):**
- **Always** references the domain service ID
- For FAV_*/SORT_*: Same as item_id (denormalized for clarity)
- For TOGGLE/PREF: NULL or references which domain this preference applies to
- Used for joining with domain service data

---

## Data Layout Examples

### Example 1: Single User's Partition
```
user_id='user123'
│
├── resource_type='META', entry_id=uuid1, compat_ver='v2'
│   └── item_id='ALL', json_value='{"lastSync":"2025-10-15T10:00:00Z"}'
│
├── resource_type='TOGGLE', entry_id=uuid2, compat_ver='v2'
│   └── item_id='darkMode', bool_value=true
│
├── resource_type='TOGGLE', entry_id=uuid3, compat_ver='v2'
│   └── item_id='notifications', bool_value=false
│
├── resource_type='PREF', entry_id=uuid4, compat_ver='v2'
│   └── item_id='language', text_value='hu'
│
├── resource_type='PREF', entry_id=uuid5, compat_ver='v2'
│   └── item_id='timezone', text_value='Europe/Budapest'
│
├── resource_type='FAV_ACC', entry_id=uuid6, compat_ver='v2'
│   └── item_id='domain1', domain_id='domain1', bool_value=true, entity_type='DASHBOARD'
│
├── resource_type='FAV_ACC', entry_id=uuid7, compat_ver='v2'
│   └── item_id='domain2', domain_id='domain2', bool_value=true, entity_type='WIDGET'
│
├── resource_type='FAV_PAR', entry_id=uuid8, compat_ver='v2'
│   └── item_id='domain3', domain_id='domain3', bool_value=true, entity_type='PARTNER'
│
├── resource_type='SORT_ACC', entry_id=uuid9, compat_ver='v2'
│   └── item_id='domain1', domain_id='domain1', int_value=1000, entity_type='DASHBOARD'
│
├── resource_type='SORT_ACC', entry_id=uuid10, compat_ver='v2'
│   └── item_id='domain2', domain_id='domain2', int_value=2000, entity_type='WIDGET'
│
└── resource_type='SORT_PAR', entry_id=uuid11, compat_ver='v2'
    └── item_id='domain3', domain_id='domain3', int_value=1000, entity_type='PARTNER'
```

---

## Updated Query Patterns

### 1. GET /preferences/all (Read entire user partition)
```cql
SELECT resource_type, entry_id, item_id, domain_id, entity_type,
       bool_value, text_value, int_value, json_value, updated_at, version
FROM prefs.user_preferences
WHERE user_id = 'user123';
```

**Response assembly:**
```json
{
  "toggleables": {
    "darkMode": true,
    "notifications": false
  },
  "preferences": {
    "language": "hu",
    "timezone": "Europe/Budapest"
  },
  "favorites": {
    "ACCOUNT": [
      {
        "entryId": "uuid6",
        "domainId": "domain1",
        "entityType": "DASHBOARD"
      },
      {
        "entryId": "uuid7",
        "domainId": "domain2",
        "entityType": "WIDGET"
      }
    ],
    "PARTNER": [
      {
        "entryId": "uuid8",
        "domainId": "domain3",
        "entityType": "PARTNER"
      }
    ]
  },
  "sortables": {
    "ACCOUNT": [
      {
        "entryId": "uuid9",
        "domainId": "domain1",
        "order": 1000,
        "entityType": "DASHBOARD"
      },
      {
        "entryId": "uuid10",
        "domainId": "domain2",
        "order": 2000,
        "entityType": "WIDGET"
      }
    ],
    "PARTNER": [
      {
        "entryId": "uuid11",
        "domainId": "domain3",
        "order": 1000,
        "entityType": "PARTNER"
      }
    ]
  }
}
```

---

### 2. GET /toggleables
```cql
SELECT entry_id, item_id, bool_value, updated_at, version
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'TOGGLE';
```

**Response:**
```json
{
  "darkMode": true,
  "notifications": false
}
```

---

### 3. GET /domains/{domainId}/favorites
```cql
-- Account favorites
SELECT entry_id, item_id, domain_id, entity_type, bool_value
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_ACC';
```

**Response (list of domainIds):**
```json
[
  {
    "entryId": "uuid6",
    "domainId": "domain1",
    "entityType": "DASHBOARD"
  },
  {
    "entryId": "uuid7",
    "domainId": "domain2",
    "entityType": "WIDGET"
  }
]
```

---

### 4. GET /domains/{domainId}/sortables
```cql
-- Account sortables
SELECT entry_id, item_id, domain_id, int_value, entity_type
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'SORT_ACC'
  AND compat_ver = 'v2';
```

**Response (sorted by order in app):**
```json
[
  {
    "entryId": "uuid9",
    "domainId": "domain1",
    "order": 1000,
    "entityType": "DASHBOARD"
  },
  {
    "entryId": "uuid10",
    "domainId": "domain2",
    "order": 2000,
    "entityType": "WIDGET"
  }
]
```

---

## Write Patterns with entry_id

### 1. POST /toggleables (Create new toggle)
```cql
INSERT INTO prefs.user_preferences 
  (user_id, resource_type, entry_id, compat_ver, 
   item_id, bool_value, created_at, updated_at, version)
VALUES 
  ('user123', 'TOGGLE', uuid(), 'v2', 
   'darkMode', true, toTimestamp(now()), toTimestamp(now()), 1);
```

**Returns:** The generated entry_id

---

### 2. PUT /toggleables/{toggleableId} (Update by item_id)

**Challenge:** We need to find entry_id from item_id first

**Option A: Read-then-write (2 queries)**
```cql
-- Step 1: Find entry_id
SELECT entry_id, version
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'TOGGLE'
  AND item_id = 'darkMode'  -- Requires ALLOW FILTERING
ALLOW FILTERING;

-- Step 2: Update by entry_id
UPDATE prefs.user_preferences
SET bool_value = true, updated_at = toTimestamp(now()), version = version + 1
WHERE user_id = 'user123'
  AND resource_type = 'TOGGLE'
  AND entry_id = ?  -- from step 1
  AND compat_ver = 'v2';
```

**Performance:** 5-10ms (2 queries)

---

**Option B: Upsert pattern (better for toggleables)**
```cql
-- Use deterministic UUID based on user_id + item_id
-- UUID v5 (SHA-1 hash of namespace + name)
entry_id = uuid5(namespace='toggleables', name=user_id + ':' + item_id)

INSERT INTO prefs.user_preferences 
  (user_id, resource_type, entry_id, compat_ver, 
   item_id, bool_value, updated_at, version)
VALUES 
  ('user123', 'TOGGLE', deterministic_uuid, 'v2', 
   'darkMode', true, toTimestamp(now()), 1);
```

**Performance:** 2-5ms (1 query, idempotent)

**Recommendation:** Use deterministic UUIDs for TOGGLE/PREF (predictable entry_id)

---

### 3. POST /domains/{domainId}/favorites (Add favorite)
```cql
INSERT INTO prefs.user_preferences 
  (user_id, resource_type, entry_id, compat_ver, 
   item_id, domain_id, entity_type, bool_value, created_at, updated_at, version)
VALUES 
  ('user123', 'FAV_ACC', uuid(), 'v2', 
   'domain1', 'domain1', 'DASHBOARD', true, toTimestamp(now()), toTimestamp(now()), 1);
```

**Returns:** 
```json
{
  "entryId": "550e8400-e29b-41d4-a716-446655440000",
  "domainId": "domain1",
  "entityType": "DASHBOARD"
}
```

---

### 4. DELETE /domains/{domainId}/favorites/{entryId} (Remove favorite)
```cql
DELETE FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_ACC'
  AND entry_id = '550e8400-e29b-41d4-a716-446655440000'
  AND compat_ver = 'v2';
```

**Performance:** 2-5ms

---

### 5. PUT /domains/{domainId}/sortables (Bulk replace)
```cql
-- Delete all existing sortables for scope
DELETE FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'SORT_ACC';

-- Batch insert new sortables
BEGIN BATCH
  INSERT INTO prefs.user_preferences 
    (user_id, resource_type, entry_id, compat_ver, 
     item_id, domain_id, entity_type, int_value, created_at, updated_at, version)
  VALUES 
    ('user123', 'SORT_ACC', uuid(), 'v2', 
     'domain1', 'domain1', 'DASHBOARD', 1000, toTimestamp(now()), toTimestamp(now()), 1);
  
  INSERT INTO prefs.user_preferences 
    (user_id, resource_type, entry_id, compat_ver, 
     item_id, domain_id, entity_type, int_value, created_at, updated_at, version)
  VALUES 
    ('user123', 'SORT_ACC', uuid(), 'v2', 
     'domain2', 'domain2', 'WIDGET', 2000, toTimestamp(now()), toTimestamp(now()), 1);
APPLY BATCH;
```

---

### 6. PATCH /domains/{domainId}/sortables/{entryId} (Update single sortable)

**Fast path with entry_id:**
```cql
UPDATE prefs.user_preferences
SET int_value = 1500, updated_at = toTimestamp(now()), version = version + 1
WHERE user_id = 'user123'
  AND resource_type = 'SORT_ACC'
  AND entry_id = '550e8400-e29b-41d4-a716-446655440000'
  AND compat_ver = 'v2'
IF version = 3;  -- Optimistic locking
```

**Performance:** 5-15ms (LWT)

---

## Secondary Index for item_id Lookups (Optional)

If you frequently need to look up by item_id without knowing entry_id:
```cql
CREATE INDEX IF NOT EXISTS idx_user_resource_item
ON prefs.user_preferences (item_id);
```

**Query pattern:**
```cql
SELECT entry_id, version
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'TOGGLE'
  AND item_id = 'darkMode';
```

**⚠️ Warning:** Secondary indexes can be slow on large datasets. Better alternatives:

---

## Better Alternative: Materialized View for item_id Lookups
```cql
CREATE MATERIALIZED VIEW prefs.user_preferences_by_item AS
  SELECT user_id, resource_type, item_id, compat_ver, entry_id, version, bool_value, text_value, int_value
  FROM prefs.user_preferences
  WHERE user_id IS NOT NULL 
    AND resource_type IS NOT NULL 
    AND item_id IS NOT NULL
    AND entry_id IS NOT NULL
    AND compat_ver IS NOT NULL
  PRIMARY KEY ((user_id, resource_type, item_id), compat_ver, entry_id);
```

**Query:**
```cql
SELECT entry_id, version, bool_value
FROM prefs.user_preferences_by_item
WHERE user_id = 'user123'
  AND resource_type = 'TOGGLE'
  AND item_id = 'darkMode';
```

**Performance:** 1-3ms (partition key lookup)

**Cost:** 2x write amplification + extra storage

---

## Recommended Approach: Deterministic UUIDs

### Best Practice for Different Resource Types

**For TOGGLE/PREF (1:1 mapping, predictable):**
```java
// Generate deterministic UUID v5
UUID entryId = UUID.nameUUIDFromBytes(
    (userId + ":" + resourceType + ":" + itemId + ":" + compatVer).getBytes()
);
```

**Benefits:**
- No lookup needed (calculate entry_id from item_id)
- Idempotent inserts
- No secondary index required

**Example:**
```cql
-- Calculate entry_id deterministically
entry_id = uuid5('user123:TOGGLE:darkMode:v2')

-- Direct upsert
INSERT INTO prefs.user_preferences 
  (user_id, resource_type, entry_id, compat_ver, item_id, bool_value, updated_at)
VALUES 
  ('user123', 'TOGGLE', calculated_uuid, 'v2', 'darkMode', true, toTimestamp(now()));
```

---

**For FAV_*/SORT_* (1:many, user can add multiple times):**
```java
// Generate random UUID v4
UUID entryId = UUID.randomUUID();
```

**Benefits:**
- Supports multiple entries per domainId (user favorites same domain multiple times - rare but possible)
- True unique identifier per preference entry

---

## Final Schema with Comments
```cql
CREATE TABLE IF NOT EXISTS prefs.user_preferences (
  -- Partition Key: All data for one user in single partition (wide row)
  user_id text,
  
  -- Clustering Keys: Creates sorted, ordered rows within partition
  resource_type text,      -- 'META', 'TOGGLE', 'PREF', 'FAV_ACC', 'FAV_PAR', 'SORT_ACC', 'SORT_PAR'
  entry_id uuid,           -- Unique ID per entry (deterministic for TOGGLE/PREF, random for FAV/SORT)
  compat_ver text,         -- 'v1', 'v2' - compatibility version
  
  -- Business Identifiers
  item_id text,            -- toggleableId/preferenceId OR domainId (business key)
  domain_id text,          -- Always references domain service ID (FK to domain service)
  entity_type text,        -- Entity type from domain service (DASHBOARD, WIDGET, etc.)
  
  -- Value Columns (sparse - only populated based on resource_type)
  bool_value boolean,      -- For TOGGLE (true/false) and FAV_* (favorite status)
  text_value text,         -- For PREF (string values like 'hu', 'Europe/Budapest')
  int_value int,           -- For SORT_* (display order: 1000, 2000, 3000...)
  json_value text,         -- For META/SNAPSHOT (complex data)
  
  -- Audit Columns
  created_at timestamp,    -- Row creation time
  updated_at timestamp,    -- Last modification time
  version int,             -- Optimistic locking version
  
  PRIMARY KEY (user_id, resource_type, entry_id, compat_ver)
) WITH CLUSTERING ORDER BY (resource_type ASC, entry_id ASC, compat_ver ASC)
  AND compaction = {'class': 'LeveledCompactionStrategy', 'sstable_size_in_mb': 160}
  AND compression = {'class': 'LZ4Compressor'}
  AND gc_grace_seconds = 864000  -- 10 days
  AND caching = {'keys': 'ALL', 'rows_per_partition': 'ALL'}
  AND comment = 'Single-table user preferences with entry_id for unique identification';
```

---

## Data Model Summary

| Column | TOGGLE | PREF | FAV_ACC/PAR | SORT_ACC/PAR | META |
|--------|--------|------|-------------|--------------|------|
| **entry_id** | Deterministic UUID | Deterministic UUID | Random UUID | Random UUID | Fixed UUID |
| **item_id** | toggleableId | preferenceId | domainId | domainId | 'ALL' |
| **domain_id** | NULL | NULL | domainId | domainId | NULL |
| **entity_type** | NULL | NULL | From domain svc | From domain svc | NULL |
| **bool_value** | ✓ Used | NULL | ✓ Used | NULL | NULL |
| **text_value** | NULL | ✓ Used | NULL | NULL | NULL |
| **int_value** | NULL | NULL | NULL | ✓ Used (order) | NULL |
| **json_value** | NULL | NULL | NULL | NULL | ✓ Used |

---

## Performance Characteristics

| Operation | Queries | Latency | Notes |
|-----------|---------|---------|-------|
| **GET /all** | 1 | 5-10ms | Full partition scan (~50-100 rows) |
| **GET /toggleables** | 1 | 2-5ms | Clustering filter on resource_type |
| **PUT /toggleables/{id}** | 1 | 2-5ms | Direct entry_id (deterministic UUID) |
| **GET /sortables** | 1 | 3-8ms | Clustering filter + app sort by int_value |
| **POST /favorites** | 1 | 2-5ms | Insert with random UUID |
| **DELETE /favorites/{entryId}** | 1 | 2-5ms | Direct delete by entry_id |
| **PATCH /sortables/{entryId}** | 1 | 5-15ms | Update with LWT (IF version = ?) |

---

## Conclusion: Why This Design Works

✅ **Single table** - No joins, no complexity  
✅ **Wide rows** - All user data in one partition (5-15 KB)  
✅ **entry_id** - Unique identifier for direct updates  
✅ **item_id** - Business key for API responses  
✅ **domain_id** - Clear FK to domain service  
✅ **Sparse columns** - No wasted storage  
✅ **Single query** - GET /all is one partition read  
✅ **LCS compaction** - Optimized for read-heavy workload  
✅ **Deterministic UUIDs** - No lookup needed for TOGGLE/PREF  

**This is production-ready Cassandra design following Google's wide-row philosophy.**
