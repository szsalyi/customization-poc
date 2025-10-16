# Display Preferences Cassandra Design - Single Table with Random UUID

## Design Concept

Since the **domain service** controls domain ID generation and we cannot guarantee:
- Domain IDs are globally unique across entity types
- No ID collisions will occur
- Domain service schema won't change

We use **random UUIDs** as the primary unique identifier (`entry_id`) and store `domain_id` as reference columns. This provides **absolute safety** and **future-proofing**.

---

## Complete Schema
```cql
CREATE KEYSPACE IF NOT EXISTS prefs
WITH replication = {'class': 'NetworkTopologyStrategy', 'DC1': '3'}
AND durable_writes = true;

CREATE TABLE IF NOT EXISTS prefs.user_preferences (
  -- Partition Key
  user_id text,
  
  -- Clustering Keys
  resource_type text,      -- 'META', 'TOGGLE', 'PREF', 'FAV_ACC', 'FAV_PAR', 'SORT_ACC', 'SORT_PAR'
  entry_id uuid,           -- Random UUID - guarantees uniqueness
  compat_ver text,         -- 'v1', 'v2', etc.
  
  -- Reference Columns (from domain service)
  domain_id text,          -- Domain ID from domain service (can have duplicates)
  
  -- Value Columns (sparse)
  bool_value boolean,      -- For toggleables and favorites
  text_value text,         -- For preferences
  int_value int,           -- For sortables (order)
  json_value text,         -- For complex data or full snapshots
  
  -- Metadata
  created_at timestamp,
  updated_at timestamp,
  version int,
  
  PRIMARY KEY (user_id, resource_type, entry_id, compat_ver)
) WITH CLUSTERING ORDER BY (resource_type ASC, entry_id ASC, compat_ver ASC)
  AND compaction = {'class': 'LeveledCompactionStrategy', 'sstable_size_in_mb': 160}
  AND compression = {'class': 'LZ4Compressor'}
  AND gc_grace_seconds = 864000
  AND caching = {'keys': 'ALL', 'rows_per_partition': 'ALL'}
  AND comment = 'Single-table user preferences with random UUID for absolute uniqueness';
```

---

## IF NEEDED IN THE FUTURE —Materialized View for domain_id Lookups

Since `domain_id` is not in the primary key, we need a materialized view to enable efficient lookups by domain_id:
```cql
CREATE MATERIALIZED VIEW prefs.user_preferences_by_domain AS
  SELECT user_id, resource_type, domain_id, compat_ver, 
         entry_id, bool_value, text_value, int_value, version, updated_at
  FROM prefs.user_preferences
  WHERE user_id IS NOT NULL 
    AND resource_type IS NOT NULL 
    AND domain_id IS NOT NULL
    AND entry_id IS NOT NULL
    AND compat_ver IS NOT NULL
  PRIMARY KEY ((user_id, resource_type, domain_id), compat_ver, entry_id)
WITH CLUSTERING ORDER BY (compat_ver ASC, entry_id ASC);
```

**Purpose:** Fast lookups when you know `domain_id` but not `entry_id`

**Cost:** 2x write amplification (every write to main table also writes to view)

---

## Column Definitions

| Column | Purpose | Example Values |
|--------|---------|----------------|
| **user_id** | Partition key - user identifier | `'user123'`, `'user456'` |
| **resource_type** | Type of preference (includes scope) | `'TOGGLE'`, `'PREF'`, `'FAV_ACC'`, `'SORT_ACC'`, `'FAV_PAR'`, `'SORT_PAR'` |
| **entry_id** | Random UUID - guaranteed unique identifier | `550e8400-e29b-41d4-a716-446655440000` |
| **compat_ver** | Compatibility version | `'v1'`, `'v2'`, `'v3'` |
| **domain_id** | Domain ID from domain service | `'domain-123'`, `'dash-001'`, `'darkMode'`, `'language'` |
| **bool_value** | Boolean value (TOGGLE, FAV_*) | `true`, `false` |
| **text_value** | String value (PREF) | `'hu'`, `'Europe/Budapest'` |
| **int_value** | Integer value for ordering (SORT_*) | `1000`, `2000`, `3000` |
| **json_value** | Complex data (META/SNAPSHOT) | `'{"lastSync":"2025-10-15"}'` |

---

## resource_type Encoding

| resource_type | Meaning |
|---------------|---------|
| `TOGGLE` | User toggleable settings | 
| `PREF` | User preferences (strings) |
| `FAV_ACC` | Favorites in ACCOUNT scope |
| `FAV_PAR` | Favorites in PARTNER scope |
| `SORT_ACC` | Sortables in ACCOUNT scope |
| `SORT_PAR` | Sortables in PARTNER scope |
| `META` | Metadata (snapshot, sync info) |

---

## Data Layout: Each Entry is a Separate Row

### Example: User's Partition
```
user_id='user123'
│
├── resource_type='TOGGLE', entry_id=uuid1, compat_ver='v2'
│   └── domain_id='darkMode', bool_value=true
│
├── resource_type='TOGGLE', entry_id=uuid2, compat_ver='v2'
│   └── domain_id='notifications', bool_value=false
│
├── resource_type='PREF', entry_id=uuid3, compat_ver='v2'
│   └── domain_id='language', text_value='hu'
│
├── resource_type='PREF', entry_id=uuid4, compat_ver='v2'
│   └── domain_id='timezone', text_value='Europe/Budapest'
│
├── resource_type='FAV_ACC', entry_id=uuid5, compat_ver='v2'
│   └── domain_id='domain-123', bool_value=true
│
├── resource_type='FAV_ACC', entry_id=uuid6, compat_ver='v2'
│   └── domain_id='domain-123', bool_value=true
│
├── resource_type='FAV_ACC', entry_id=uuid7, compat_ver='v2'
│   └── domain_id='domain-456', bool_value=true
│
├── resource_type='FAV_PAR', entry_id=uuid8, compat_ver='v2'
│   └── domain_id='partner-001', bool_value=true
│
├── resource_type='FAV_PAR', entry_id=uuid9, compat_ver='v2'
│   └── domain_id='partner-002', bool_value=true
│
├── resource_type='SORT_ACC', entry_id=uuid10, compat_ver='v2'
│   └── domain_id='domain-123', int_value=1000
│
├── resource_type='SORT_ACC', entry_id=uuid11, compat_ver='v2'
│   └── domain_id='domain-456', int_value=2000
│
└── resource_type='SORT_PAR', entry_id=uuid12, compat_ver='v2'
    └── domain_id='partner-001', int_value=1000
```

---

## Query Patterns

### 1. GET /preferences/all (Hot Path - Single Query)
```cql
SELECT resource_type, entry_id, domain_id,
       bool_value, text_value, int_value, json_value, updated_at, version
FROM prefs.user_preferences
WHERE user_id = 'user123';
```

**Response assembly:**
```json
{
  "toggleables": {
    "darkMode": true,
    "hideAccounts": false
  },
  "preferences": {
    "language": "hu",
    "timezone": "Europe/Budapest",
    "background": "green"
  },
  "favorites": {
    "ACCOUNT": [
      {
        "entryId": "550e8400-e29b-41d4-a716-446655440000",
        "domainId": "domain-123"
      },
      {
        "entryId": "660e9500-f39c-52e5-b827-557766551111",
        "domainId": "domain-123"
      },
      {
        "entryId": "770ea600-039d-63f6-c938-668877662222",
        "domainId": "domain-456"
      }
    ],
    "PARTNER": [
      {
        "entryId": "880eb700-149e-74g7-d049-779988773333",
        "domainId": "partner-001"
      },
      {
        "entryId": "990ec800-259f-85h8-e15a-88aa99884444",
        "domainId": "partner-002"
      }
    ]
  },
  "sortables": {
    "ACCOUNT": [
      {
        "entryId": "aa0ed900-36a0-96i9-f26b-99bb00995555",
        "domainId": "domain-123",
        "order": 1000
      },
      {
        "entryId": "bb0ee000-47b1-a7j0-037c-00cc11006666",
        "domainId": "domain-456",
        "order": 2000
      }
    ],
    "PARTNER": [
      {
        "entryId": "cc0ef100-58c2-b8k1-148d-11dd22117777",
        "domainId": "partner-001",
        "order": 1000
      }
    ]
  }
}
```

**Performance:** 5-10ms (full partition scan, ~50-100 rows)

---

### 2. GET /toggleables
```cql
SELECT entry_id, domain_id, bool_value, updated_at, version
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

**Performance:** 2-5ms

---

### 3. GET /preferences
```cql
SELECT entry_id, domain_id, text_value, updated_at, version
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'PREF';
```

**Response:**
```json
{
  "language": "hu",
  "timezone": "Europe/Budapest"
}
```

**Performance:** 2-5ms

---

### 4. GET /domains/ACCOUNT/favorites
```cql
SELECT entry_id, domain_id
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_ACC';
```

**Response:**
```json
[
  {
    "entryId": "550e8400-e29b-41d4-a716-446655440000",
    "domainId": "domain-123",
    "entityType": "DASHBOARD"
  },
  {
    "entryId": "660e9500-f39c-52e5-b827-557766551111",
    "domainId": "domain-123",
    "entityType": "WIDGET"
  },
  {
    "entryId": "770ea600-039d-63f6-c938-668877662222",
    "domainId": "domain-456",
    "entityType": "REPORT"
  }
]
```

**Performance:** 2-5ms

**Note:** Returns multiple rows. Backend assembles into array. User can have multiple favorites with same domain_id but different resource_type.

---

### 5. GET /domains/PARTNER/favorites
```cql
SELECT entry_id, domain_id
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_PAR';
```

**Response:**
```json
[
  {
    "entryId": "880eb700-149e-74g7-d049-779988773333",
    "domainId": "partner-001",
    "entityType": "PARTNER"
  },
  {
    "entryId": "990ec800-259f-85h8-e15a-88aa99884444",
    "domainId": "partner-002",
    "entityType": "PARTNER"
  }
]
```

**Performance:** 2-5ms

---

### 6. GET /domains/ACCOUNT/sortables
```cql
SELECT entry_id, domain_id, int_value
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'SORT_ACC'
  AND compat_ver = 'v2';
```

**Response (sorted by order in app):**
```json
[
  {
    "entryId": "aa0ed900-36a0-96i9-f26b-99bb00995555",
    "domainId": "domain-123",
    "entityType": "DASHBOARD",
    "order": 1000
  },
  {
    "entryId": "bb0ee000-47b1-a7j0-037c-00cc11006666",
    "domainId": "domain-456",
    "entityType": "REPORT",
    "order": 2000
  }
]
```

**Performance:** 3-8ms

---

### 7. GET /domains/PARTNER/sortables
```cql
SELECT entry_id, domain_id, int_value
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'SORT_PAR'
  AND compat_ver = 'v2';
```

**Response:**
```json
[
  {
    "entryId": "cc0ef100-58c2-b8k1-148d-11dd22117777",
    "domainId": "partner-001",
    "entityType": "PARTNER",
    "order": 1000
  }
]
```

**Performance:** 3-8ms

---

## Write Patterns

### 1. POST /toggleables (Create Toggle)
```cql
INSERT INTO prefs.user_preferences 
  (user_id, resource_type, entry_id, compat_ver, 
   domain_id, bool_value, created_at, updated_at, version)
VALUES 
  ('user123', 'TOGGLE', uuid(), 'v2', 
   'darkMode', null, true, toTimestamp(now()), toTimestamp(now()), 1);
```

**Returns:**
```json
{
  "entryId": "550e8400-e29b-41d4-a716-446655440000",
  "domainId": "darkMode",
  "value": true
}
```

**Performance:** 2-5ms

---

### 2. PUT /toggleables/{toggleableId} (Update Toggle)

**Challenge:** We need to find `entry_id` from `domain_id` first.

**Option A: Use Materialized View (Recommended)**
```cql
-- Step 1: Find entry_id using materialized view
SELECT entry_id, version
FROM prefs.user_preferences_by_domain
WHERE user_id = 'user123'
  AND resource_type = 'TOGGLE'
  AND domain_id = 'darkMode';
  
-- Returns: entry_id, version

-- Step 2: Update by entry_id
UPDATE prefs.user_preferences
SET bool_value = false, updated_at = toTimestamp(now()), version = version + 1
WHERE user_id = 'user123'
  AND resource_type = 'TOGGLE'
  AND entry_id = ?  -- from step 1
  AND compat_ver = 'v2'
IF version = ?;  -- Optimistic locking
```

**Performance:** 3-8ms (2 queries, but view lookup is fast)

---

**Option B: Upsert Pattern (Alternative)**

If you don't care about preserving the original `entry_id`, you can use deterministic UUIDs for TOGGLE/PREF:
```java
// For TOGGLE/PREF only, use deterministic UUID
UUID entryId = UUID.nameUUIDFromBytes(
    (userId + ":TOGGLE:" + domainId + ":" + compatVer).getBytes()
);
```
```cql
INSERT INTO prefs.user_preferences 
  (user_id, resource_type, entry_id, compat_ver, domain_id, bool_value, updated_at, version)
VALUES 
  ('user123', 'TOGGLE', <deterministic_uuid>, 'v2', 'darkMode', false, toTimestamp(now()), 1);
```

**Performance:** 2-5ms (single query, idempotent)

**Trade-off:** Uses deterministic UUID for TOGGLE/PREF but random UUID for FAV_*/SORT_*

---

### 3. PUT /domains/ACCOUNT/favorites (Add Favorite)
```cql
INSERT INTO prefs.user_preferences 
  (user_id, resource_type, entry_id, compat_ver, 
   domain_id, bool_value, created_at, updated_at, version)
VALUES 
  ('user123', 'FAV_ACC', uuid(), 'v2', 
   'domain-123', 'DASHBOARD', true, toTimestamp(now()), toTimestamp(now()), 1);
```

**Returns:**
```json
{
  "entryId": "550e8400-e29b-41d4-a716-446655440000",
  "domainId": "domain-123",
  "entityType": "DASHBOARD"
}
```

**Performance:** 2-5ms

**Note:** Each favorite is a separate row with unique `entry_id`. User can favorite multiple entities with the same `domain_id` if they have different `resource_type`.

---

### 4. DELETE /domains/ACCOUNT/favorites/{entryId} (Remove Favorite by entryId)

**API Call:**
```
DELETE /domains/ACCOUNT/favorites/550e8400-e29b-41d4-a716-446655440000
```

**CQL:**
```cql
DELETE FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_ACC'
  AND entry_id = '550e8400-e29b-41d4-a716-446655440000'
  AND compat_ver = 'v2';
```

**Performance:** 2-5ms

**Result:** Only this specific favorite row is deleted. Other favorites remain untouched.

---

### 5. DELETE /domains/ACCOUNT/favorites (Remove Favorite by domainId and entityType)

**API Call:**
```
DELETE /domains/ACCOUNT/favorites?domainId=domain-123&entityType=DASHBOARD
```

**CQL (using materialized view):**
```cql
-- Step 1: Find entry_id
SELECT entry_id
FROM prefs.user_preferences_by_domain
WHERE user_id = 'user123'
  AND resource_type = 'FAV_ACC'
  AND domain_id = 'domain-123';

-- Returns: entry_id = '550e8400-e29b-41d4-a716-446655440000'

-- Step 2: Delete by entry_id
DELETE FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_ACC'
  AND entry_id = '550e8400-e29b-41d4-a716-446655440000'
  AND compat_ver = 'v2';
```

**Performance:** 3-8ms (2 queries)

---

### 6. DELETE /domains/PARTNER/favorites/{entryId} (Remove Partner Favorite)

**User removes "partner-002" from favorites by clicking X button in UI**

**API Call:**
```
DELETE /domains/PARTNER/favorites/990ec800-259f-85h8-e15a-88aa99884444
```

**CQL:**
```cql
DELETE FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_PAR'
  AND entry_id = '990ec800-259f-85h8-e15a-88aa99884444'
  AND compat_ver = 'v2';
```

**Performance:** 2-5ms

**Before deletion (2 favorites):**
```
Row 1: entry_id=uuid8, domain_id='partner-001'
Row 2: entry_id=uuid9, domain_id='partner-002'  ← This row deleted
```

**After deletion (1 favorite):**
```
Row 1: entry_id=uuid8, domain_id='partner-001'
```

---

### 7. PUT /domains/ACCOUNT/sortables (Bulk Replace)
```cql
-- Step 1: Delete all existing sortables for ACCOUNT scope
DELETE FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'SORT_ACC';

-- Step 2: Batch insert new sortables (each with random UUID)
BEGIN BATCH
  INSERT INTO prefs.user_preferences 
    (user_id, resource_type, entry_id, compat_ver, 
     domain_id, int_value, created_at, updated_at, version)
  VALUES 
    ('user123', 'SORT_ACC', uuid(), 'v2', 
     'domain-123', 'DASHBOARD', 1000, toTimestamp(now()), toTimestamp(now()), 1);
  
  INSERT INTO prefs.user_preferences 
    (user_id, resource_type, entry_id, compat_ver, 
     domain_id, int_value, created_at, updated_at, version)
  VALUES 
    ('user123', 'SORT_ACC', uuid(), 'v2', 
     'domain-456', 'REPORT', 2000, toTimestamp(now()), toTimestamp(now()), 1);
  
  INSERT INTO prefs.user_preferences 
    (user_id, resource_type, entry_id, compat_ver, 
     domain_id, int_value, created_at, updated_at, version)
  VALUES 
    ('user123', 'SORT_ACC', uuid(), 'v2', 
     'domain-789', 'CHART', 3000, toTimestamp(now()), toTimestamp(now()), 1);
APPLY BATCH;
```

**Performance:** 10-25ms

**Result:** Old sortables completely replaced with new list.

---

### 8. PATCH /domains/ACCOUNT/sortables/{entryId} (Update Single Sortable Order)

**User drags "domain-456 REPORT" to new position (order: 1500)**

**API Call:**
```
PATCH /domains/ACCOUNT/sortables/bb0ee000-47b1-a7j0-037c-00cc11006666
Body: { "order": 1500 }
```

**CQL:**
```cql
UPDATE prefs.user_preferences
SET int_value = 1500, updated_at = toTimestamp(now()), version = version + 1
WHERE user_id = 'user123'
  AND resource_type = 'SORT_ACC'
  AND entry_id = 'bb0ee000-47b1-a7j0-037c-00cc11006666'
  AND compat_ver = 'v2'
IF version = 3;  -- Optimistic locking
```

**Performance:** 5-15ms (LWT for concurrency safety)

**Result:** Only this sortable's order is updated. Other sortables untouched.

---

## Complete Example: Removing a Favorite

### Scenario: User removes "domain-123 WIDGET" from ACCOUNT favorites

**Initial state (3 favorites, 3 rows):**
```
Row 1: entry_id=uuid5, domain_id='domain-123', bool_value=true
Row 2: entry_id=uuid6, domain_id='domain-123', bool_value=true
Row 3: entry_id=uuid7, domain_id='domain-456', bool_value=true
```

**User clicks "Remove" on the WIDGET card in UI:**

**Frontend makes API call:**
```javascript
fetch('/domains/ACCOUNT/favorites/660e9500-f39c-52e5-b827-557766551111', { 
  method: 'DELETE' 
});
```

**Backend executes:**
```cql
DELETE FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_ACC'
  AND entry_id = '660e9500-f39c-52e5-b827-557766551111'  -- The WIDGET entry
  AND compat_ver = 'v2';
```

**Result state (2 favorites, 2 rows):**
```
Row 1: entry_id=uuid5, domain_id='domain-123', bool_value=true
Row 3: entry_id=uuid7, domain_id='domain-456', bool_value=true
```

**Next GET /domains/ACCOUNT/favorites:**
```cql
SELECT entry_id, domain_id
FROM prefs.user_preferences
WHERE user_id = 'user123'
  AND resource_type = 'FAV_ACC';
```

**Returns 2 rows:**
```json
[
  {
    "entryId": "550e8400-e29b-41d4-a716-446655440000",
    "domainId": "domain-123",
    "entityType": "DASHBOARD"
  },
  {
    "entryId": "770ea600-039d-63f6-c938-668877662222",
    "domainId": "domain-456",
    "entityType": "REPORT"
  }
]
```

✅ **WIDGET favorite removed, DASHBOARD favorite with same domain_id remains intact**

---

## API Design Recommendations

### Option 1: Frontend Tracks entry_id (Best Performance)

**GET Response includes entry_id:**
```json
{
  "favorites": {
    "ACCOUNT": [
      {
        "entryId": "550e8400-e29b-41d4-a716-446655440000",
        "domainId": "domain-123",
        "entityType": "DASHBOARD"
      },
      {
        "entryId": "660e9500-f39c-52e5-b827-557766551111",
        "domainId": "domain-123",
        "entityType": "WIDGET"
      }
    ]
  }
}
```

**DELETE Endpoint:**
```
DELETE /domains/ACCOUNT/favorites/{entryId}
```

**Backend:**
```cql
DELETE FROM prefs.user_preferences
WHERE user_id = ? AND resource_type = ? AND entry_id = ? AND compat_ver = ?;
```

✅ **1 query, 2-5ms latency**

---

### Option 2: Frontend Only Knows domain_id (More Flexible)

**GET Response (simpler for frontend):**
```json
{
  "favorites": {
    "ACCOUNT": [
      {
        "domainId": "domain-123"
      },
      {
        "domainId": "domain-123"
      }
    ]
  }
}
```

**DELETE Endpoint:**
```
DELETE /domains/ACCOUNT/favorites?domainId=domain-123&entityType=WIDGET
```

**Backend (with materialized view):**
```java
// Step 1: Find entry_id
String entryId = cassandra.selectOne(
  "SELECT entry_id FROM prefs.user_preferences_by_domain " +
  "WHERE user_id = ? AND resource_type = ? AND domain_id = ? ,
  userId, "FAV_ACC", "domain-123"
).getEntryId();

// Step 2: Delete
cassandra.execute(
  "DELETE FROM prefs.user_preferences " +
  "WHERE user_id = ? AND resource_type = ? AND entry_id = ? AND compat_ver = ?",
  userId, "FAV_ACC", entryId, compatVer
);
```

✅ **2 queries, 3-8ms total latency** (with materialized view)

---

## Hybrid UUID Strategy (Recommended Optimization)

To get the best of both worlds:

**For TOGGLE/PREF:** Use deterministic UUIDs (no lookup needed)
```java
UUID entryId = UUID.nameUUIDFromBytes(
    (userId + ":TOGGLE:" + domainId + ":" + compatVer).getBytes()
);
```

**For FAV_*/SORT_*:** Use random UUIDs (handles domain_id collisions)
```java
UUID entryId = UUID.randomUUID();
```

**Benefits:**
- ✅ TOGGLE/PREF operations are fast (no materialized view needed)
- ✅ FAV_*/SORT_* operations handle domain_id + resource_type collisions
- ✅ Materialized view only needed for FAV_*/SORT_* lookups
- ✅ Reduced write amplification (fewer writes to MV)

---

## Data Model Summary

| Column            | TOGGLE              | PREF                | FAV_ACC/PAR   | SORT_ACC/PAR |
|-------------------|---------------------|---------------------|---------------|--------------|
| **entry_id**      | Deterministic UUID* | Deterministic UUID* | Random UUID   | Random UUID |
| **domain_id**     | toggleableId        | preferenceId        | domainId      | domainId |
| **resource_type** | TOGGL               | PERF                | FAV_ACC/PAR   | SORT_ACC/PAR |
| **bool_value**    | ✓ Used              | -                   | ✓ Used        | - |
| **text_value**    | -                   | ✓ Used              | -             | - |
| **int_value**     | -                   | -                   | -             | ✓ Used (order) |
| **json_value**    | -                   | -                   | -             | - |
| **Rows per user** | ~10 toggles         | ~20 prefs           | ~50 favorites | ~100 sortables |

*Can use deterministic UUID for optimization

---

## Performance Characteristics

|HTTP| Queries | Latency | Notes |
|----------|---------|---------|-------|
| GET /all | 1 | 5-10ms | Full partition scan (~180 rows total) |
| GET /toggleables | 1 | 2-5ms | Clustering filter on resource_type (~10 rows) |
| GET /preferences | 1 | 2-5ms | Clustering filter on resource_type (~20 rows) |
| GET /favorites | 1 | 2-5ms | Clustering filter on resource_type (~50 rows) |
| GET /sortables | 1 | 3-8ms | Clustering filter + app sort (~100 rows) |
| PUT /toggleables/{id} | 1 (determ) or 2 (random) | 2-5ms or 3-8ms | Deterministic: direct, Random: lookup+update |
| PUT /preferences/{id} | 1 (determ) or 2 (random) | 2-5ms or 3-8ms | Deterministic: direct, Random: lookup+update |
| POST /favorites (add) | 1 | 2-5ms | Insert with random UUID |
| DELETE /favorites by entryId | 1 | 2-5ms | Direct delete if frontend knows entryId |
| DELETE /favorites by domainId | 2 | 3-8ms | MV lookup + delete |
| PUT /sortables (bulk) | 1 batch | 10-25ms | Delete all + batch insert |
| PATCH /sortables/{entryId} | 1 | 5-15ms | Direct update with LWT |

Partition Size Analysis
Single user partition (typical):
- Toggleables:     ~10 rows  × 140 bytes  = 1.4 KB
- Preferences:     ~20 rows  × 160 bytes  = 3.2 KB
- Favorites:       ~50 rows  × 130 bytes  = 6.5 KB
- Sortables:      ~100 rows  × 160 bytes  = 16.0 KB
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Total per user: ~27.1 KB (180 rows)
```

**Cassandra limits:**
- Soft limit: 100 MB per partition
- Hard limit: 2 GB per partition
- Our design: 27 KB (0.027% of soft limit)

**Safety margin:** 3,700x under soft limit ✅

**Heavy user (extreme case):**
```
- Toggleables:     ~20 rows  × 140 bytes  = 2.8 KB
- Preferences:     ~40 rows  × 160 bytes  = 6.4 KB
- Favorites:      ~200 rows  × 130 bytes  = 26.0 KB
- Sortables:      ~500 rows  × 160 bytes  = 80.0 KB
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Total: ~115 KB (760 rows) - still well within limits ✅

Consistency & Compaction
## Consistency & Compaction

Read/Write Consistency

```cql
CONSISTENCY LOCAL_QUORUM;
SERIAL CONSISTENCY LOCAL_SERIAL;
```

### Why LOCAL_QUORUM

Balance between consistency and latency
Survives single node failure (2 out of 3 replicas)

Read-your-writes consistency within datacenter

No cross-DC latency penalty

**When to use LWT (LOCAL_SERIAL):
PATCH operations with version checking
Concurrent updates to same row
Critical operations requiring strict ordering**
