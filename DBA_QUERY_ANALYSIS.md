# DBA Query Analysis & Index Review

## Query 1: Dashboard Load - All Preferences by Primary User ID

### Business Case
User opens dashboard → System needs to fetch all preferences for that user in compact format (v1). This is a high-frequency query that runs on every dashboard load.

### Query - Version A (Recommended)
```sql
-- Load all preferences for a user (by external UUID)
-- Use case: Dashboard initialization
-- Execution frequency: Per dashboard load (high volume)

SELECT
    ui.preference_user_id,
    ui.primary_user_id,
    ui.identity_type,
    p.preference_key,
    p.preference_value,
    p.compat_version
FROM user_identities ui
INNER JOIN user_preferences p ON ui.preference_user_id = p.preference_user_id
WHERE ui.primary_user_id = :primary_user_uuid
    AND p.compat_version = 'v1'
    AND ui.is_active = 1
ORDER BY p.preference_key;
```

### Query - Version B (Alternative - Direct Lookup if internal ID cached)
```sql
-- If client already has internal preference_user_id (from cache/previous lookup)
-- Faster path, skips identity table

SELECT
    preference_key,
    preference_value,
    compat_version
FROM user_preferences
WHERE preference_user_id = :internal_user_id
    AND compat_version = 'v1'
ORDER BY preference_key;
```

### Query - Version C (Compact JSON Response)
```sql
-- Return as JSON for API response (Oracle 12.1+)
-- Single round-trip, minimal parsing at app layer

SELECT
    JSON_OBJECT(
        'user_id' VALUE ui.preference_user_id,
        'primary_uuid' VALUE ui.primary_user_id,
        'identity_type' VALUE ui.identity_type,
        'preferences' VALUE JSON_ARRAYAGG(
            JSON_OBJECT(
                'key' VALUE p.preference_key,
                'value' VALUE p.preference_value
                RETURNING CLOB
            ) ORDER BY p.preference_key
        )
        RETURNING CLOB
    ) AS dashboard_preferences
FROM user_identities ui
INNER JOIN user_preferences p ON ui.preference_user_id = p.preference_user_id
WHERE ui.primary_user_id = :primary_user_uuid
    AND p.compat_version = 'v1'
    AND ui.is_active = 1
GROUP BY ui.preference_user_id, ui.primary_user_id, ui.identity_type;
```

---

## Query 2: Fetch Single Preference by Key - backgroundColor Example

### Business Case
User UI component needs specific preference value (e.g., backgroundColor). App queries for single preference key to avoid fetching all 50+ preferences. High-frequency read, low-impact operation.

### Query - Version A (Recommended)
```sql
-- Fetch single preference by key
-- Use case: Theme loading, component initialization
-- Execution frequency: Per page render (very high)

SELECT
    preference_value,
    compat_version
FROM user_preferences
WHERE preference_user_id = :internal_user_id
    AND preference_key = 'backgroundColor'
    AND compat_version = 'v1';
```

### Query - Version B (With Default Fallback)
```sql
-- Fetch preference with default if not found
-- Use case: Safe component rendering with fallback
-- Note: Application can handle NULL result, use app default

SELECT
    preference_value,
    COALESCE(preference_value, '#FFFFFF') AS backgroundColor,
    compat_version
FROM user_preferences
WHERE preference_user_id = :internal_user_id
    AND preference_key = 'backgroundColor'
    AND compat_version = 'v1'
    AND ROWNUM <= 1;  -- Safety: ensure single row
```

### Query - Version C (Batch Fetch Multiple Keys)
```sql
-- Fetch multiple specific preferences in one query
-- Use case: Theme initialization (backgroundColor, textColor, borderColor)
-- Execution frequency: Dashboard load (reduces round-trips)

SELECT
    preference_key,
    preference_value,
    compat_version
FROM user_preferences
WHERE preference_user_id = :internal_user_id
    AND preference_key IN ('backgroundColor', 'textColor', 'borderColor', 'fontSize')
    AND compat_version = 'v1'
ORDER BY preference_key;
```

---

## Execution Plan Analysis - Single Key Lookup

### Query Plan for Version A (Single backgroundColor Lookup)

**Query**:
```sql
SELECT preference_value, compat_version
FROM user_preferences
WHERE preference_user_id = :internal_user_id
    AND preference_key = 'backgroundColor'
    AND compat_version = 'v1';
```

**Constraints**:
- Unique constraint: `uk_pref UNIQUE (preference_user_id, preference_key, compat_version)`
- This means result is always 0 or 1 row (guaranteed unique)

**Expected Execution Plan**:
```
ID | Operation Name                    | Rows | Bytes | Cost | Time
───┼──────────────────────────────────┼──────┼───────┼──────┼────────
  0 | SELECT STATEMENT                 |      |       |      |
  1 │ PARTITION RANGE ITERATOR         |      |       |      |
  2 │  TABLE ACCESS BY LOCAL INDEX     |  1   |   50  |   3  | 1ms
     │    ROWID                        |      |       |      |
     │   idx_pref_user_compat          |      |       |      |
     │   [user_id = :id, version=v1]   |      |       |      |
  3 │   FILTER (preference_key=X)      |  1   |   50  |      | <1ms
```

**Analysis**:

1. **Partition Range Iterator**
   ```
   Operation: Identifies which partition contains preference_user_id
   Logic: preference_user_id ranges → maps to partition number
   Partitions scanned: 1 (out of 10)
   ✅ Partition pruning working efficiently
   ```

2. **Index Range Scan (idx_pref_user_compat)**
   ```
   Index structure: (preference_user_id, compat_version)
   Filter applied: preference_user_id = :id AND compat_version = 'v1'

   ⚠️ Note: preference_key NOT in index
   Rows returned from index: ~50 (all v1 prefs for user)

   Then filter: preference_key = 'backgroundColor'
   Final rows: 1 (UNIQUE constraint guarantees 0-1)

   Cost: 3 logical I/Os
   Time: ~1ms
   ```

3. **Filter (preference_key)**
   ```
   Operation: Apply remaining filter not in index
   Rows filtered: 50 → 1
   CPU cost: Minimal (string comparison on 50 rows)
   Time: <1ms
   ```

**Performance**:
- **Logical I/Os**: 3-4
- **Physical I/Os**: 0 (cached)
- **Elapsed time**: <1ms ✅
- **CPU time**: <0.5ms

---

### Query Plan Analysis - Batch Multiple Keys

**Query**:
```sql
SELECT preference_key, preference_value, compat_version
FROM user_preferences
WHERE preference_user_id = :internal_user_id
    AND preference_key IN ('backgroundColor', 'textColor', 'borderColor', 'fontSize')
    AND compat_version = 'v1'
ORDER BY preference_key;
```

**Expected Execution Plan**:
```
ID | Operation Name                    | Rows | Bytes | Cost | Time
───┼──────────────────────────────────┼──────┼───────┼──────┼────────
  0 | SELECT STATEMENT                 |      |       |      |
  1 │ SORT ORDER BY                    |  4   |  200  |   4  | 1ms
  2 │  PARTITION RANGE ITERATOR        |      |       |      |
  3 │   TABLE ACCESS BY LOCAL INDEX    |  4   |  200  |   3  | 1ms
     │    ROWID                        |      |       |      |
     │   idx_pref_user_compat          |      |       |      |
     │   [user_id = :id, version=v1]   |      |       |      |
  4 │   FILTER (preference_key IN(...))| 50   |  2500 |      | <1ms
```

**Analysis**:

1. **Index Scan (all v1 preferences for user)**
   ```
   ✅ Index provides: (preference_user_id = :id, compat_version = 'v1')
   Returns: ~50 rows (all user's v1 preferences)

   Filter applied: preference_key IN (4 keys)
   Result rows: 4 (only matching keys)

   Cost: 3 logical I/Os
   Time: ~1ms
   ```

2. **Sort ORDER BY preference_key**
   ```
   Input rows: 4
   In-memory sort (small result)
   Cost: 1 logical I/O
   Time: <1ms (negligible for 4 rows)
   ```

**Performance**:
- **Logical I/Os**: 4-5
- **Physical I/Os**: 0 (cached)
- **Elapsed time**: 1-2ms ✅
- **CPU time**: <1ms

**Benefit vs Single Query × 4**:
- Single query: 1 round-trip, 2ms
- 4 separate queries: 4 round-trips, 4ms total execution
- **Network savings**: ~2ms (1 round-trip vs 4)

---

## Index Optimization for Key-Based Lookups

### Current Index Limitation
```sql
CREATE INDEX idx_pref_user_compat ON user_preferences(preference_user_id, compat_version);
```

**Problem**: To find `preference_key = 'backgroundColor'`, must scan all 50 rows with that (user_id, version)

**Could we add preference_key to index?**

```sql
-- Proposed alternative
CREATE INDEX idx_pref_key_lookup ON user_preferences(
    preference_user_id,
    preference_key,
    compat_version
);
```

**Analysis**:

```
Pros:
✅ Index covers WHERE completely
✅ Could do index-only scan (no table fetch)
✅ Faster for single-key lookups: ~1 row fetched vs 50

Cons:
❌ Larger index: 36 bytes × 50M = 1.8GB (vs 1.87GB current)
❌ Slower writes: 3 indexes to maintain (current + 2 others)
❌ Redundant with current index (column order matters)
   - Current: (user_id, version) → good for "get all v1 prefs"
   - Proposed: (user_id, key, version) → good for "get specific key"
   - Both have different access patterns

Decision: NOT recommended
Reasoning:
  • Single-key lookups are fast enough (<1ms) with current index
  • Benefit of adding index: 0.1-0.2ms improvement (negligible)
  • Cost: Larger storage, slower writes
  • Better to cache single preferences at app layer
```

### Recommendation: Application-Level Caching

Instead of adding indexes, implement caching strategy:

```java
// Application preferences cache
private static final Cache<String, String> preferenceCache =
    CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(10000)  // Cache 10K user preferences
        .build();

public String getPreference(long userId, String key) {
    String cacheKey = userId + ":" + key;
    return preferenceCache.get(cacheKey, () ->
        // Cache miss: query DB
        queryDatabase(userId, key)
    );
}
```

**Performance with caching**:
- **Cache hit**: <1ms (in-memory)
- **Cache miss**: 1-2ms (DB query)
- **Overall**: ~0.1-0.5ms (depends on hit ratio)

---

## Query 3: Account Sorting UI Fetch - user_sortables

### Business Case
User opens dashboard and needs to display accounts in their custom sort order. Fetch all sortables for ACCOUNT type. Medium-frequency read (once per dashboard load for account management UI).

### Query - Version A (Recommended - Simple Fetch)
```sql
-- Fetch all user's account sortables
-- Use case: Display accounts in custom order on UI
-- Execution frequency: Dashboard load (medium volume)

SELECT
    domain_id,
    sort_position,
    created_at,
    updated_at
FROM user_sortables
WHERE preference_user_id = :internal_user_id
    AND domain_type = 'ACCOUNT'
ORDER BY sort_position ASC;
```

### Query - Version B (With Account Names via Join)
```sql
-- Fetch sortables with account details from domain service
-- Note: Join external domain service in application layer (not recommended in SQL)
-- but shown here for completeness

SELECT
    s.domain_id,
    s.sort_position,
    s.created_at
FROM user_sortables s
WHERE s.preference_user_id = :internal_user_id
    AND s.domain_type = 'ACCOUNT'
ORDER BY s.sort_position ASC;

-- Application then enriches with:
-- FOR EACH domain_id:
--   account_details = domainService.getAccount(domain_id)
--   result.add({domain_id, account_details, sort_position})
```

### Query - Version C (JSON Response Format)
```sql
-- Return as JSON array for API response
-- Use case: Single-trip API response with sorting data

SELECT
    JSON_ARRAYAGG(
        JSON_OBJECT(
            'account_id' VALUE domain_id,
            'position' VALUE sort_position,
            'created_at' VALUE created_at
            ORDER BY sort_position ASC
        )
        RETURNING CLOB
    ) AS accounts_sorted
FROM user_sortables
WHERE preference_user_id = :internal_user_id
    AND domain_type = 'ACCOUNT';
```

### Query - Version D (With Account Count)
```sql
-- Fetch sortables with metadata
-- Use case: UI shows "3 sorted accounts"

SELECT
    domain_id,
    sort_position,
    created_at,
    ROW_NUMBER() OVER (ORDER BY sort_position) AS position_rank
FROM user_sortables
WHERE preference_user_id = :internal_user_id
    AND domain_type = 'ACCOUNT'
ORDER BY sort_position ASC;
```

---

## Execution Plan Analysis - Account Sortables Fetch

### Query Plan for Version A (Simple Fetch)

**Query**:
```sql
SELECT domain_id, sort_position, created_at, updated_at
FROM user_sortables
WHERE preference_user_id = :internal_user_id
    AND domain_type = 'ACCOUNT'
ORDER BY sort_position ASC;
```

**Expected Execution Plan**:
```
ID | Operation Name                 | Rows | Bytes | Cost | Time
───┼───────────────────────────────┼──────┼───────┼──────┼────────
  0 | SELECT STATEMENT              |      |       |      |
  1 │ TABLE ACCESS BY INDEX ROWID   | 200  | 5600  |  6   | 2ms
     │  INDEX RANGE SCAN             |      |       |      |
     │   idx_sort_user_type          |      |       |      |
     │   [user_id=:id, type='ACC']   |      |       |      |
```

**Analysis**:

1. **Index Range Scan (idx_sort_user_type)**
   ```
   Index structure: (preference_user_id, domain_type, sort_position)

   Filters applied from index:
   ✅ preference_user_id = :id (1st column)
   ✅ domain_type = 'ACCOUNT' (2nd column)

   Rows returned: ~200 (typical user has 200 accounts in sort order)
   Already sorted by sort_position (3rd column in index) ✅

   Cost: 5 logical I/Os
   Time: ~1ms
   ```

2. **Table Access by Index ROWID**
   ```
   Operation: Fetch columns not in index
   Columns in index: (preference_user_id, domain_type, sort_position)
   Columns NOT in index: (created_at, updated_at, domain_id)

   Rows fetched: 200
   Cost: 1 logical I/O (assume ~100 rows per block, 2 blocks)
   Time: ~1ms
   ```

3. **Results Already Ordered**
   ```
   Index provides: sort_position ASC
   No additional SORT operation needed ✅
   Time: 0ms (already ordered)
   ```

**Total Performance**:
- **Logical I/Os**: 6-7
- **Physical I/Os**: 0 (cached)
- **Elapsed time**: 2-3ms ✅ (Very fast)
- **CPU time**: <1ms

**Why so fast?**
- ✅ Index covers all WHERE columns
- ✅ Index provides natural ORDER BY
- ✅ Result set is 200 rows (reasonable size)
- ✅ No sort operation needed

---

## Index Coverage Analysis - user_sortables

### Current Index
```sql
CREATE INDEX idx_sort_user_type ON user_sortables(preference_user_id, domain_type, sort_position);
```

**Index Design Quality**: ⭐⭐⭐⭐⭐ Excellent

**Coverage for common queries**:

1. **Fetch by user_id + domain_type**
   ```
   Query: WHERE preference_user_id = ? AND domain_type = 'ACCOUNT'
   ✅ Perfect match (first 2 columns)
   Selectivity: user_id is unique per user, domain_type = 50% (ACCOUNT vs PARTNER)
   Result: ~100-200 rows
   ```

2. **Ordered fetch (with sort_position)**
   ```
   Query: ... ORDER BY sort_position ASC
   ✅ Index is sorted by sort_position (3rd column)
   No SORT operation needed
   ```

3. **Range queries on position**
   ```
   Query: WHERE preference_user_id = ?
          AND domain_type = 'ACCOUNT'
          AND sort_position BETWEEN 1 AND 50
   ✅ Index can skip blocks efficiently
   ```

**Index Size Estimate** (50M sortables):
```
Per entry:
  preference_user_id: 8 bytes (NUMBER)
  domain_type: 20 bytes (VARCHAR2(20))
  sort_position: 8 bytes (NUMBER)
  ROWID: 6 bytes
  ─────────────────
  Per entry: ~42 bytes

Entries per block: 7300 / 42 ≈ 173 entries per block
Index entries: 50M
Blocks needed: 50,000,000 / 173 ≈ 289,000 blocks = ~2.25GB

Total index size: ~2.25GB
```

---

## Drag-and-Drop Reorder Performance

### Update Query After Reorder

**Business Case**: User drags Account B from position 100 → position 50

```sql
-- Update single account's sort position (gap-based)
UPDATE user_sortables
SET sort_position = 50,
    updated_at = CURRENT_TIMESTAMP
WHERE preference_user_id = :internal_user_id
    AND domain_id = 'ACC_B'
    AND domain_type = 'ACCOUNT';
```

**Unique Constraint on entity**:
```sql
CONSTRAINT uk_sort_entity UNIQUE (preference_user_id, domain_type, domain_id)
```

**Execution**:
1. Find row via UNIQUE constraint (unique key lookup)
2. Update sort_position to 50
3. Update updated_at to CURRENT_TIMESTAMP
4. Index update: idx_sort_user_type (one entry modified)

**Performance**:
- **Logical I/Os**: 2-3 (UNIQUE constraint lookup + update)
- **Physical I/Os**: 0 (row cached)
- **Elapsed time**: <1ms ✅
- **Locks acquired**: 1 row (no cascading)

**Why gap-based positioning is perfect here**:
```
Positions before: ..., 100, 110, 120, ...
User drags to: 50

UPDATE user_sortables SET sort_position = 50
WHERE domain_id = 'ACC_B';

New positions: ..., 50, 100, 110, 120, ...
               (gap between 50 and 100)

✅ Single row update
✅ No cascading updates needed
✅ No lock contention with other users
✅ Gap is transparent to ORDER BY
```

---

## Comparison: Single Key Query vs Batch Query vs Cache

### Scenario: UI needs theme preferences

**Option 1: 4 individual queries** (backgroundColor, textColor, fontSize, borderColor)
```sql
-- Query 1
SELECT preference_value FROM user_preferences
WHERE preference_user_id = 1 AND preference_key = 'backgroundColor' AND compat_version = 'v1';

-- Query 2
SELECT preference_value FROM user_preferences
WHERE preference_user_id = 1 AND preference_key = 'textColor' AND compat_version = 'v1';

-- ... etc (4 queries total)
```

**Performance**:
- Database round-trips: 4
- Execution time: 0.5ms × 4 = 2ms
- Network time: 50ms × 4 = 200ms
- **Total**: 202ms

**Option 2: Single batch query**
```sql
SELECT preference_key, preference_value FROM user_preferences
WHERE preference_user_id = 1
  AND preference_key IN ('backgroundColor', 'textColor', 'fontSize', 'borderColor')
  AND compat_version = 'v1'
ORDER BY preference_key;
```

**Performance**:
- Database round-trips: 1
- Execution time: 1-2ms
- Network time: 50ms × 1 = 50ms
- **Total**: 51-52ms

**Savings**: 150ms (75% improvement)

**Option 3: Application cache**
```java
// After initial load, preferences cached in memory
String bgColor = cache.get("1:backgroundColor");  // <1ms
```

**Performance**:
- Execution time: <1ms (in-memory)
- Network time: 0ms
- **Total**: <1ms

**Savings**: 200ms+ (99% improvement)

**Recommendation**: Use batch query (Option 2) on first load, then cache in app (Option 3)

---

## Index Coverage Summary

### user_identities
```
✅ idx_user_lookup(primary_user_id, secondary_user_id)
   - Covers dashboard identity lookup (Version A)
   - Covers corporate user lookups

⚠️ Missing: idx_user_secondary
   - Should add for corporate (secondary_user_id) only lookups
```

### user_preferences
```
✅ idx_pref_user_compat(preference_user_id, compat_version)
   - Covers dashboard preference fetch
   - Partition pruning enabled
   - Sufficient for most queries

⚠️ Single-key lookups (backgroundColor)
   - Scans 50 rows then filters (acceptable)
   - Could optimize with app cache instead of index
```

### user_sortables
```
✅ idx_sort_user_type(preference_user_id, domain_type, sort_position)
   - EXCELLENT: Covers all columns in query
   - Covers WHERE clause
   - Covers ORDER BY
   - Index-only possible (all columns used)
   - Best index design in schema
```

### user_favorites
```
⚠️ idx_fav_user_type(preference_user_id, favorite_type)
   - Missing: created_at in index
   - Should be: (preference_user_id, favorite_type, created_at DESC)
   - Causes sort operation for ordered queries
   - Recommendation: Add created_at DESC to index
```

---

## Index Coverage Analysis

### Updated Index Strategy (No Partitioning)

**Change**: Removed PARTITION BY RANGE from user_preferences, added explicit preference_user_id indexes on all tables.

### Current Indexes (Refined)
```sql
-- user_identities
CREATE INDEX idx_user_lookup ON user_identities(primary_user_id, secondary_user_id);
CREATE INDEX idx_user_active ON user_identities(is_active, preference_user_id);
CREATE INDEX idx_user_secondary ON user_identities(secondary_user_id)
WHERE secondary_user_id IS NOT NULL;

-- user_preferences (NO PARTITIONING - simpler, still performs well)
CREATE INDEX idx_pref_user_id ON user_preferences(preference_user_id);
CREATE INDEX idx_pref_user_compat ON user_preferences(preference_user_id, compat_version);

-- user_sortables
CREATE INDEX idx_sort_user_id ON user_sortables(preference_user_id);
CREATE INDEX idx_sort_user_type ON user_sortables(preference_user_id, domain_type, sort_position);

-- user_favorites
CREATE INDEX idx_fav_user_id ON user_favorites(preference_user_id);
CREATE INDEX idx_fav_user_type ON user_favorites(preference_user_id, favorite_type, created_at DESC);
```

### Execution Plan Analysis - Version A

**Query**:
```sql
SELECT ui.preference_user_id, ui.primary_user_id, ui.identity_type,
       p.preference_key, p.preference_value, p.compat_version
FROM user_identities ui
INNER JOIN user_preferences p ON ui.preference_user_id = p.preference_user_id
WHERE ui.primary_user_id = :primary_user_uuid
    AND p.compat_version = 'v1'
    AND ui.is_active = 1
ORDER BY p.preference_key;
```

**Expected Execution Plan**:
```
ID | Operation Name                    | Rows | Bytes | Cost | Time
───┼──────────────────────────────────┼──────┼───────┼──────┼────────
  0 | SELECT STATEMENT                 |      |       |      |
  1 │ SORT ORDER BY                    |  50  | 2400  |  15  | 5ms
  2 │  NESTED LOOPS INNER              |  50  | 2400  |  12  | 4ms
  3 │   INDEX RANGE SCAN               |  1   |  36   |   2  | 1ms
     │  idx_user_lookup                 |      |       |      |
     │  [primary_user_id = :uuid]       |      |       |      |
  4 │  TABLE ACCESS BY INDEX ROWID     |  1   |  25   |   3  | 1ms
     │  user_identities                 |      |       |      |
     │  [is_active = 1 filter]          |      |       |      |
  5 │   PARTITION RANGE ITERATOR       |      |       |      |
  6 │    TABLE ACCESS BY GLOBAL INDEX  |  50  |  1500 |   8  | 2ms
     │     ROWID                        |      |       |      |
     │    user_preferences              |      |       |      |
     │    [compat_version = 'v1']       |      |       |      |
```

**Step-by-Step Analysis**:

1. **Line 3-4: Look up user identity**
   ```
   Operation: INDEX RANGE SCAN idx_user_lookup
   Filter: primary_user_id = :uuid
   Expected rows: 1 (UNIQUE constraint enforces it)
   ✅ Uses index idx_user_lookup (primary_user_id, secondary_user_id)
   Cost: 2 logical I/Os
   Time: ~1ms
   ```

2. **Line 4-5: Fetch user_identities row**
   ```
   Operation: TABLE ACCESS BY INDEX ROWID
   Filter: is_active = 1
   Expected rows: 1 (already filtered by UNIQUE constraint)
   ✅ Direct access via ROWID from index
   Cost: 3 logical I/Os
   Time: ~1ms
   ```

3. **Line 5-6: Join to preferences**
   ```
   Operation: PARTITION RANGE ITERATOR + TABLE ACCESS BY GLOBAL INDEX ROWID
   Filter: preference_user_id = :internal_id AND compat_version = 'v1'
   Expected rows: 50 (typical user has 20-100 preferences)
   ⚠️ Note: Partitioned table, range iterator narrows to relevant partition
   ✅ Uses index idx_pref_user_compat (preference_user_id, compat_version)
   Cost: 8 logical I/Os
   Time: ~2ms
   ```

4. **Line 1-2: Sort results**
   ```
   Operation: SORT ORDER BY preference_key
   Expected rows: 50
   ✅ In-memory sort (small result set)
   Cost: 5 logical I/Os
   Time: ~5ms (includes I/O for unsorted input)
   ```

**Total Cost**: 15 logical I/Os, ~5-10ms wall time

---

### Execution Plan Analysis - Version B (Cached Internal ID)

**Query**:
```sql
SELECT preference_key, preference_value, compat_version
FROM user_preferences
WHERE preference_user_id = :internal_user_id
    AND compat_version = 'v1'
ORDER BY preference_key;
```

**Expected Execution Plan**:
```
ID | Operation Name                    | Rows | Bytes | Cost | Time
───┼──────────────────────────────────┼──────┼───────┼──────┼────────
  0 | SELECT STATEMENT                 |      |       |      |
  1 │ SORT ORDER BY                    |  50  | 1500  |   9  | 4ms
  2 │  PARTITION RANGE ITERATOR        |      |       |      |
  3 │   TABLE ACCESS BY LOCAL INDEX    |  50  | 1500  |   7  | 2ms
     │    ROWID                        |      |       |      |
     │   idx_pref_user_compat          |      |       |      |
     │   [user_id = :id, version=v1]   |      |       |      |
```

**Analysis**:

1. **Line 3-4: Index scan with both filter columns**
   ```
   Operation: TABLE ACCESS BY LOCAL INDEX ROWID
   Filters applied: preference_user_id = :id AND compat_version = 'v1'
   ✅ Index idx_pref_user_compat covers BOTH predicates
   ✅ Index is on partitioned table (LOCAL index)
   ✅ Range iterator limits to specific partition
   Cost: 7 logical I/Os
   Time: ~2ms
   ```

2. **Line 1-2: Sort**
   ```
   Operation: SORT ORDER BY preference_key
   Expected rows: 50
   ✅ In-memory sort
   Cost: 2 logical I/Os
   Time: ~4ms
   ```

**Total Cost**: 9 logical I/Os, ~3-6ms wall time
**⏱️ 30% faster than Version A** (skips identity table join)

---

## Index Performance Metrics

### idx_user_lookup Analysis
```sql
CREATE INDEX idx_user_lookup ON user_identities(primary_user_id, secondary_user_id);
```

**Index Definition**:
- **Columns**: (primary_user_id, secondary_user_id)
- **Type**: B-tree (default)
- **Unique**: No (but constrained by UNIQUE constraint on columns)
- **Compression**: None

**Query Coverage**:
```
WHERE ui.primary_user_id = :uuid
      AND ui.secondary_user_id = NULL (optional)
```

**✅ Perfect coverage** - Both filter columns in index, query uses both

**Size Estimate** (1M users):
```
Leaf block entries:
  primary_user_id: 36 bytes (VARCHAR2(36))
  secondary_user_id: 36 bytes (nullable)
  ROWID: 6 bytes
  ─────────────────
  Per entry: ~78 bytes

Assuming 8KB blocks with 10% overhead = ~7300 bytes usable
Entries per block: 7300 / 78 = ~93 entries

Index blocks needed: 1,000,000 / 93 ≈ 10,752 blocks = ~85MB

Total index size: ~85MB
```

**Access pattern**:
- Read index root block (pinned in buffer cache)
- Read branch blocks (search for primary_user_id)
- Read leaf block containing primary_user_id
- Follow ROWID to table

**Performance**:
- **Selectivity**: Very high (equals on UNIQUE column)
- **Buffer cache**: Will stay cached (used frequently)
- **I/O**: 2-3 logical I/Os typically

---

### idx_pref_user_compat Analysis
```sql
CREATE INDEX idx_pref_user_compat ON user_preferences(preference_user_id, compat_version);
```

**Index Definition**:
- **Columns**: (preference_user_id, compat_version)
- **Type**: B-tree
- **Unique**: No
- **Compression**: Consider for partitioned index
- **Partitioned**: YES - LOCAL index (one index segment per table partition)

**Query Coverage**:
```
WHERE p.preference_user_id = :id
  AND p.compat_version = 'v1'
ORDER BY p.preference_key  -- NOT in index
```

**Coverage Analysis**:
```
✅ WHERE clause fully covered by index
  ├─ preference_user_id = :id (1st column, high selectivity)
  └─ compat_version = 'v1' (2nd column, medium selectivity)

⚠️ ORDER BY not covered
  └─ preference_key not in index
     Requires sort after table access
```

**Size Estimate** (50M preferences, 1M users):
```
Per index entry:
  preference_user_id: 8 bytes (NUMBER)
  compat_version: 20 bytes (VARCHAR2(20))
  ROWID: 6 bytes
  ─────────────────
  Per entry: ~34 bytes

Per partition (100K users × 50 prefs = 5M rows):
Entries per block: 7300 / 34 ≈ 214 entries
Blocks per partition: 5,000,000 / 214 ≈ 23,364 blocks = ~187MB

Total index size (10 partitions): ~1.87GB
```

**Access pattern**:
- Partition iterator selects partition based on preference_user_id
- Read LOCAL index segment for that partition only
- No need to scan other partitions ✅

**Performance**:
- **Selectivity**: Very high on preference_user_id (unique internally)
- **Cardinality**: compat_version='v1' matches ~99% (poor on 2nd column)
- **I/O**: 2-5 logical I/Os per partition access

**Optimization Opportunity**:
```
Consider if ORDER BY preference_key could be in index:
❌ NOT RECOMMENDED (keys are random/unbounded)

Current: ORDER BY preference_key (sort in memory - acceptable)
Cost: Small result set (50 items), sort = ~1ms
Benefit of adding to index: Minimal (1-2ms saved)
Cost: Larger index (+500MB), slower writes
Decision: Keep as-is
```

---

## Query Performance Baseline

### Dashboard Load Query (Version B - Cached ID)

**Scenario**: User opens dashboard
```
Precondition: Internal preference_user_id already cached from login
Query complexity: Single table, 2-column filter, small sort

Performance metrics:
├─ Logical I/Os: ~7-9
├─ Physical I/Os: 0-1 (mostly cached)
├─ Wait events: None (index cached, partitions cached)
├─ CPU time: 1-2ms
├─ Elapsed time: 2-5ms
└─ Throughput: ~1000 queries/second possible
```

**Expected response time**:
```
┌─ Index search (partition identification): 0.5ms
├─ Index range scan (50 rows): 1-2ms
├─ Table fetch via ROWID (50 rows): 1-2ms
├─ Sort ORDER BY: 1-2ms
└─ Total: 3-6ms ✅

SLA target: <20ms
Current: 3-6ms (80% below SLA)
```

### Dashboard Load Query (Version A - UUID Lookup)

**Scenario**: User login, no cached internal ID
```
Precondition: Have external UUID only
Query complexity: Join 2 tables, 3 filters, small sort

Performance metrics:
├─ Logical I/Os: ~12-15
├─ Physical I/Os: 0-2
├─ Wait events: None
├─ CPU time: 3-5ms
├─ Elapsed time: 5-10ms
└─ Throughput: ~500 queries/second possible
```

**Expected response time**:
```
├─ Identity index lookup: 1-2ms
├─ Identity table fetch: 1ms
├─ Preferences index scan (50 rows): 2-3ms
├─ Preferences table fetch (50 rows): 1-2ms
├─ Sort ORDER BY: 1-2ms
└─ Total: 5-10ms ✅

SLA target: <20ms
Current: 5-10ms (50-75% below SLA)
```

---

## DBA Recommendations

### Current Index Strategy Assessment

✅ **GOOD**:
- idx_user_lookup covers identity lookups perfectly
- idx_pref_user_compat covers dashboard preferences queries
- LOCAL index on partitioned table (efficient)
- Partition pruning narrows search space

⚠️ **CONSIDERATIONS**:
- ORDER BY preference_key requires sort (acceptable, small set)
- compat_version selectivity is poor (99% of rows = 'v1')
  - But filtering is still efficient due to column position

❌ **NOT DONE** (missing from current DDL):
- No index for secondary_user_id lookups (corporate users)
  - Should be added: `CREATE INDEX idx_user_secondary ON user_identities(secondary_user_id)`

- No index for update queries on user_preferences by preference_key
  - Scenario: "Update THEME preference" → Need WHERE preference_key = 'THEME'
  - Low priority (not frequent)

### Recommended Index Additions

**For corporate (secondary_user_id) lookups**:
```sql
CREATE INDEX idx_user_secondary ON user_identities(secondary_user_id)
WHERE secondary_user_id IS NOT NULL;  -- Filtered index (ignore NULLs)
```

**If you add preference_key updates**:
```sql
CREATE INDEX idx_pref_key ON user_preferences(preference_user_id, preference_key)
WHERE compat_version = 'v1';  -- Filtered index (only v1)
```

---

## Query Tuning Guidelines

### Parameter Binding (CRITICAL)
```sql
❌ DON'T:
SELECT * FROM user_preferences
WHERE preference_user_id = 1
  AND compat_version = 'v1';

✅ DO:
SELECT * FROM user_preferences
WHERE preference_user_id = :internal_user_id
  AND compat_version = :version;
```

**Why**: Allows query plan caching, prevents SQL injection

### Bind Variable Types
```sql
-- For primary_user_uuid
:primary_user_uuid VARCHAR2(36)

-- For internal ID
:internal_user_id NUMBER

-- For version
:version VARCHAR2(20) DEFAULT 'v1'
```

### Partition Pruning Tips
```sql
-- Query ALWAYS filters by preference_user_id for best performance
✅ GOOD: WHERE preference_user_id = :id AND compat_version = 'v1'
          Partition pruning works, narrows to 1 partition out of 10

❌ BAD: WHERE compat_version = 'v1' AND preference_key LIKE '%THEME%'
        No partition pruning, scans all partitions
```

---

## Monitoring Queries for DBA

### Monitor Index Usage
```sql
-- Check if indexes are being used
SELECT
    owner,
    index_name,
    table_name,
    blevel,               -- B-tree levels
    leaf_blocks,          -- Number of leaf blocks
    distinct_keys,        -- Cardinality
    avg_leaf_blocks_per_key,
    avg_data_blocks_per_key
FROM dba_indexes
WHERE table_name IN ('USER_IDENTITIES', 'USER_PREFERENCES')
ORDER BY owner, table_name, index_name;
```

### Monitor Index Size
```sql
-- Index size tracking
SELECT
    owner,
    segment_name,
    segment_type,
    round(bytes / 1024 / 1024) AS size_mb,
    extents,
    blocks
FROM dba_segments
WHERE owner = 'YOUR_SCHEMA'
  AND segment_type = 'INDEX'
ORDER BY bytes DESC;
```

### Monitor Query Performance
```sql
-- Slow query analysis
SELECT
    sql_id,
    child_number,
    executions,
    elapsed_time / executions / 1000000 AS avg_elapsed_sec,
    buffer_gets / executions AS avg_logical_ios,
    physical_reads / executions AS avg_physical_ios,
    sql_text
FROM v$sql
WHERE lower(sql_text) LIKE '%user_preferences%'
  AND executions > 0
ORDER BY elapsed_time DESC;
```

### Monitor Partition Distribution
```sql
-- Check partition sizes
SELECT
    table_name,
    partition_name,
    num_rows,
    round(bytes / 1024 / 1024) AS size_mb
FROM dba_tab_partitions
WHERE table_name = 'USER_PREFERENCES'
ORDER BY partition_name;
```

---

## Production Readiness Checklist

- [ ] Verify idx_user_lookup and idx_pref_user_compat created without errors
- [ ] Run DBMS_STATS.GATHER_TABLE_STATS for optimizer statistics
- [ ] Execute sample queries (Version A, B, C) and verify execution plans
- [ ] Benchmark query response times against SLA (<20ms)
- [ ] Add idx_user_secondary for corporate user lookups
- [ ] Monitor index fragmentation after 1 week of production use
- [ ] Confirm partition pruning working (use EXPLAIN PLAN)
- [ ] Set up monitoring queries for ongoing tuning

---

**Document Version**: 1.0
**Created**: 2025-10-30
**Query Type**: Dashboard preference loading
**Complexity**: Medium (join + partitioned table)
**Expected Performance**: 3-10ms (SLA: <20ms)
