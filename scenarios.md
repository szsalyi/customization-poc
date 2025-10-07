Complete Scenario Analysis

Scenario 1: First-Time Preferences Setup (Empty State)
Context

User has never set preferences for "accounts" domain
User clicks "Customize Order" button
UI shows all accounts in default order (e.g., alphabetical or by creation date)
User drags to reorder and clicks "Save"


Flow Diagram
User clicks "Customize" 
    ↓
UI fetches existing preferences
    ↓
Empty result (no preferences exist)
    ↓
UI fetches all accounts from domain service
    ↓
Display all accounts in default order
    ↓
User reorders via drag-and-drop
    ↓
User clicks "Save"
    ↓
Create all preferences (PUT :batchReplace)
    ↓
Success → Show confirmation

Step-by-Step API Calls
Step 1: Check for Existing Preferences
httpGET /preferences?domain=accounts
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "preferences": [],
  "nextPageToken": null,
  "totalSize": 0
}
UI Logic:
javascriptconst { preferences, totalSize } = await fetchPreferences('accounts');

if (totalSize === 0) {
  // First-time setup path
  console.log('No existing preferences found');
}

Step 2: Fetch All Accounts from Domain Service
httpGET /accounts
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "accounts": [
    {
      "id": "acc-001",
      "name": "Enterprise Account A",
      "type": "enterprise",
      "createdAt": "2024-01-10T10:00:00Z"
    },
    {
      "id": "acc-002",
      "name": "SMB Account B",
      "type": "small_business",
      "createdAt": "2024-01-12T14:30:00Z"
    },
    {
      "id": "acc-003",
      "name": "Startup Account C",
      "type": "startup",
      "createdAt": "2024-01-15T09:15:00Z"
    },
    {
      "id": "acc-004",
      "name": "Corporate Account D",
      "type": "corporate",
      "createdAt": "2024-01-18T11:45:00Z"
    }
  ]
}

Step 3: Display in UI (Default Order)
javascript// UI shows all accounts in default order
function displayCustomizeDialog(accounts, existingPreferences) {
  if (existingPreferences.length === 0) {
    // First-time: Show all accounts in default order
    const sortedAccounts = accounts.sort((a, b) => 
      a.name.localeCompare(b.name)
    );
    
    renderOrderableList(sortedAccounts);
  }
}
UI Display:
┌─────────────────────────────────────┐
│  Customize Account Order            │
├─────────────────────────────────────┤
│  ☰ Corporate Account D              │
│  ☰ Enterprise Account A             │
│  ☰ SMB Account B                    │
│  ☰ Startup Account C                │
├─────────────────────────────────────┤
│  [Cancel]              [Save Order] │
└─────────────────────────────────────┘

Step 4: User Reorders (via drag-and-drop)
New Order After User Interaction:
1. Enterprise Account A
2. Startup Account C
3. Corporate Account D
4. SMB Account B

Step 5: Save New Order (PUT :batchReplace)
httpPUT /preferences:batchReplace
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "domain": "accounts",
  "preferences": [
    {
      "entityId": "acc-001",
      "entityType": "account",
      "order": 1,
      "attributes": {}
    },
    {
      "entityId": "acc-003",
      "entityType": "account",
      "order": 2,
      "attributes": {}
    },
    {
      "entityId": "acc-004",
      "entityType": "account",
      "order": 3,
      "attributes": {}
    },
    {
      "entityId": "acc-002",
      "entityType": "account",
      "order": 4,
      "attributes": {}
    }
  ]
}
Response (200):
json{
  "preferences": [
    {
      "id": "pref-001",
      "name": "preferences/pref-001",
      "domain": "accounts",
      "entityId": "acc-001",
      "entityType": "account",
      "order": 1,
      "attributes": {},
      "createTime": "2024-10-07T14:30:00Z",
      "updateTime": "2024-10-07T14:30:00Z",
      "etag": "AxY2c5nOpTu="
    },
    {
      "id": "pref-002",
      "name": "preferences/pref-002",
      "domain": "accounts",
      "entityId": "acc-003",
      "entityType": "account",
      "order": 2,
      "attributes": {},
      "createTime": "2024-10-07T14:30:00Z",
      "updateTime": "2024-10-07T14:30:00Z",
      "etag": "BwZ3d6oQpVv="
    },
    {
      "id": "pref-003",
      "name": "preferences/pref-003",
      "domain": "accounts",
      "entityId": "acc-004",
      "entityType": "account",
      "order": 3,
      "attributes": {},
      "createTime": "2024-10-07T14:30:00Z",
      "updateTime": "2024-10-07T14:30:00Z",
      "etag": "CxA4e7pRqWw="
    },
    {
      "id": "pref-004",
      "name": "preferences/pref-004",
      "domain": "accounts",
      "entityId": "acc-002",
      "entityType": "account",
      "order": 4,
      "attributes": {},
      "createTime": "2024-10-07T14:30:00Z",
      "updateTime": "2024-10-07T14:30:00Z",
      "etag": "DxB5f8qSrXx="
    }
  ],
  "summary": {
    "created": 4,
    "updated": 0,
    "deleted": 0,
    "total": 4
  }
}

Edge Cases for Scenario 1
Edge CaseHandlingUser cancels without savingNo API call. Preferences remain emptyNetwork failure during savePUT is idempotent - safe to retry with same payloadUser adds/removes items before savingAdjust order numbers, then send final stateConcurrent users (same user, different tabs)Last write wins. Consider adding optimistic locking with ETags in future

Scenario 2: Updating Existing Preferences
Context

User already has preferences set for "accounts"
User wants to reorder existing items
User may also want to mark items as favorites


Flow Diagram
User clicks "Customize"
    ↓
UI fetches existing preferences
    ↓
Preferences exist (4 items ordered)
    ↓
UI fetches all accounts
    ↓
Merge: Show ordered items first, then unordered items
    ↓
User reorders
    ↓
User clicks "Save"
    ↓
Replace all preferences (PUT :batchReplace)
    ↓
Success → Update UI

Step-by-Step API Calls
Step 1: Fetch Existing Preferences
httpGET /preferences?domain=accounts&sortBy=order&sortOrder=asc
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "preferences": [
    {
      "id": "pref-001",
      "domain": "accounts",
      "entityId": "acc-001",
      "entityType": "account",
      "order": 1,
      "attributes": {},
      "updateTime": "2024-10-07T14:30:00Z"
    },
    {
      "id": "pref-002",
      "domain": "accounts",
      "entityId": "acc-003",
      "entityType": "account",
      "order": 2,
      "attributes": {"isFavorite": true},
      "updateTime": "2024-10-07T15:20:00Z"
    },
    {
      "id": "pref-003",
      "domain": "accounts",
      "entityId": "acc-004",
      "entityType": "account",
      "order": 3,
      "attributes": {},
      "updateTime": "2024-10-07T14:30:00Z"
    },
    {
      "id": "pref-004",
      "domain": "accounts",
      "entityId": "acc-002",
      "entityType": "account",
      "order": 4,
      "attributes": {},
      "updateTime": "2024-10-07T14:30:00Z"
    }
  ],
  "totalSize": 4
}

Step 2: Fetch All Accounts
httpGET /accounts
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "accounts": [
    {"id": "acc-001", "name": "Enterprise Account A"},
    {"id": "acc-002", "name": "SMB Account B"},
    {"id": "acc-003", "name": "Startup Account C"},
    {"id": "acc-004", "name": "Corporate Account D"},
    {"id": "acc-005", "name": "New Account E"}  // ← New account added!
  ]
}

Step 3: Merge Data in UI
javascriptfunction mergeAccountsWithPreferences(accounts, preferences) {
  // Create a map of entityId → preference
  const prefMap = new Map(
    preferences.map(p => [p.entityId, p])
  );
  
  // Ordered accounts (have preferences)
  const orderedAccounts = preferences
    .map(pref => {
      const account = accounts.find(a => a.id === pref.entityId);
      return account ? {
        ...account,
        preference: pref,
        isOrdered: true
      } : null;
    })
    .filter(Boolean);
  
  // Unordered accounts (no preferences yet)
  const unorderedAccounts = accounts
    .filter(account => !prefMap.has(account.id))
    .map(account => ({
      ...account,
      preference: null,
      isOrdered: false
    }));
  
  return {
    orderedAccounts,
    unorderedAccounts
  };
}
UI Display:
┌─────────────────────────────────────┐
│  Customize Account Order            │
├─────────────────────────────────────┤
│  ☰ Enterprise Account A             │
│  ☰ Startup Account C            ⭐  │ ← Favorite
│  ☰ Corporate Account D              │
│  ☰ SMB Account B                    │
│  ─────────────────────────────────  │
│  Not Ordered:                       │
│  ☰ New Account E                    │ ← No preference yet
├─────────────────────────────────────┤
│  [Cancel]              [Save Order] │
└─────────────────────────────────────┘

Step 4: User Reorders
New Order:
1. Startup Account C (favorite)
2. New Account E (newly ordered)
3. Enterprise Account A
4. Corporate Account D
5. SMB Account B

Step 5: Save Updated Order (PUT :batchReplace)
httpPUT /preferences:batchReplace
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "domain": "accounts",
  "preferences": [
    {
      "entityId": "acc-003",
      "entityType": "account",
      "order": 1,
      "attributes": {"isFavorite": true}
    },
    {
      "entityId": "acc-005",
      "entityType": "account",
      "order": 2,
      "attributes": {}
    },
    {
      "entityId": "acc-001",
      "entityType": "account",
      "order": 3,
      "attributes": {}
    },
    {
      "entityId": "acc-004",
      "entityType": "account",
      "order": 4,
      "attributes": {}
    },
    {
      "entityId": "acc-002",
      "entityType": "account",
      "order": 5,
      "attributes": {}
    }
  ]
}
Response (200):
json{
  "preferences": [/* 5 preferences */],
  "summary": {
    "created": 1,    // acc-005 (New Account E)
    "updated": 4,    // Others reordered
    "deleted": 0,
    "total": 5
  }
}

Alternative: Partial Update (Mark Favorite Only)
If user only wants to mark an account as favorite without reordering:
httpPATCH /preferences/pref-002
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "attributes": {
    "isFavorite": false  // Toggle favorite off
  }
}
Response (200):
json{
  "id": "pref-002",
  "domain": "accounts",
  "entityId": "acc-003",
  "order": 2,
  "attributes": {
    "isFavorite": false
  },
  "updateTime": "2024-10-07T16:45:00Z",
  "etag": "ExC6g9rTsYy="
}

Edge Cases for Scenario 2
Edge CaseHandlingUser removes item from ordered listDon't include it in PUT payload - it will be deleted from preferencesUser adds new itemInclude in PUT payload with appropriate orderConcurrent modificationUse ETags in future: If-Match header to detect conflictsNetwork timeout during savePUT is idempotent - retry with same payload

Scenario 3: UI Loading Preferences for Display
Context

Application loads and needs to display accounts in user's preferred order
This happens on every page load or when navigating to accounts section


Flow Diagram
User navigates to Accounts page
    ↓
UI fetches preferences (parallel)
UI fetches accounts data (parallel)
    ↓
Merge data: ordered items first, then unordered
    ↓
Render UI with proper order

Step-by-Step API Calls
Parallel Fetching (Optimal Performance)
javascriptasync function loadAccountsView() {
  // Fetch both in parallel for better performance
  const [preferencesResponse, accountsResponse] = await Promise.all([
    fetch('/preferences?domain=accounts&sortBy=order&sortOrder=asc', {
      headers: { 'Authorization': `Bearer ${token}` }
    }),
    fetch('/accounts', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
  ]);
  
  const { preferences } = await preferencesResponse.json();
  const { accounts } = await accountsResponse.json();
  
  return mergeAndDisplay(accounts, preferences);
}

Request 1: Fetch Preferences
httpGET /preferences?domain=accounts&sortBy=order&sortOrder=asc
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "preferences": [
    {"id": "pref-001", "entityId": "acc-003", "order": 1},
    {"id": "pref-002", "entityId": "acc-005", "order": 2},
    {"id": "pref-003", "entityId": "acc-001", "order": 3},
    {"id": "pref-004", "entityId": "acc-004", "order": 4},
    {"id": "pref-005", "entityId": "acc-002", "order": 5}
  ],
  "totalSize": 5
}

Request 2: Fetch Accounts Data
httpGET /accounts
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "accounts": [
    {"id": "acc-001", "name": "Enterprise Account A", "revenue": 500000},
    {"id": "acc-002", "name": "SMB Account B", "revenue": 50000},
    {"id": "acc-003", "name": "Startup Account C", "revenue": 25000},
    {"id": "acc-004", "name": "Corporate Account D", "revenue": 750000},
    {"id": "acc-005", "name": "New Account E", "revenue": 100000},
    {"id": "acc-006", "name": "Another Account F", "revenue": 80000}
  ]
}

Step 3: Merge and Display
javascriptfunction mergeAndDisplay(accounts, preferences) {
  // Create lookup map
  const accountMap = new Map(accounts.map(a => [a.id, a]));
  const preferenceMap = new Map(preferences.map(p => [p.entityId, p]));
  
  // Ordered accounts (have preferences)
  const orderedAccounts = preferences
    .map(pref => accountMap.get(pref.entityId))
    .filter(Boolean)  // Filter out deleted accounts
    .map(account => ({
      ...account,
      preference: preferenceMap.get(account.id)
    }));
  
  // Unordered accounts (no preference set)
  const unorderedAccounts = accounts
    .filter(account => !preferenceMap.has(account.id))
    .sort((a, b) => a.name.localeCompare(b.name));  // Default sort
  
  // Display: ordered first, then unordered
  return [...orderedAccounts, ...unorderedAccounts];
}
Final UI Display Order:
1. Startup Account C        (order: 1)
2. New Account E            (order: 2)
3. Enterprise Account A     (order: 3)
4. Corporate Account D      (order: 4)
5. SMB Account B            (order: 5)
─────────────────────────────────────
6. Another Account F        (no preference - default sort)

Performance Optimization
javascript// Option 1: Cache preferences in session storage
function getCachedPreferences(domain) {
  const cached = sessionStorage.getItem(`prefs_${domain}`);
  if (cached) {
    const { data, timestamp } = JSON.parse(cached);
    // Cache valid for 5 minutes
    if (Date.now() - timestamp < 5 * 60 * 1000) {
      return data;
    }
  }
  return null;
}

function setCachedPreferences(domain, preferences) {
  sessionStorage.setItem(`prefs_${domain}`, JSON.stringify({
    data: preferences,
    timestamp: Date.now()
  }));
}

// Option 2: Use conditional requests with ETags
async function fetchPreferencesWithCache(domain) {
  const etag = localStorage.getItem(`prefs_etag_${domain}`);
  
  const response = await fetch(`/preferences?domain=${domain}`, {
    headers: {
      'Authorization': `Bearer ${token}`,
      'If-None-Match': etag || ''
    }
  });
  
  if (response.status === 304) {
    // Not modified - use cached data
    return JSON.parse(localStorage.getItem(`prefs_data_${domain}`));
  }
  
  const data = await response.json();
  localStorage.setItem(`prefs_etag_${domain}`, response.headers.get('ETag'));
  localStorage.setItem(`prefs_data_${domain}`, JSON.stringify(data));
  
  return data;
}

Edge Cases for Scenario 3
Edge CaseHandlingNo preferences existShow all accounts in default orderPreference points to deleted accountFilter out from ordered listNew account addedAppend to end in default sort orderAPI timeoutShow cached data with "stale" indicator, retry in backgroundEmpty accounts listShow empty state with "Add Account" CTA

Scenario 4: Domain Data Changes (Account Deleted/Added)
Context

Admin deletes an account from the system
System needs to clean up orphaned preferences
OR: New account is added and user hasn't ordered it yet


Sub-Scenario 4A: Account Deleted
Flow Diagram
Admin deletes account (acc-003)
    ↓
Account service publishes "account.deleted" event
    ↓
Preference service listens to event
    ↓
Find all preferences with entityId = "acc-003"
    ↓
Delete orphaned preferences
    ↓
Optionally: Reorder remaining preferences

Event-Driven Approach (Recommended)
Event Published by Account Service:
json{
  "eventType": "account.deleted",
  "eventId": "evt-12345",
  "timestamp": "2024-10-07T17:00:00Z",
  "data": {
    "accountId": "acc-003",
    "accountName": "Startup Account C",
    "deletedBy": "admin-user-789"
  }
}
Preference Service Event Handler:
javascriptasync function handleAccountDeleted(event) {
  const { accountId } = event.data;
  
  // Find all preferences for this account across all users
  const orphanedPrefs = await db.preferences.find({
    entityId: accountId,
    entityType: 'account'
  });
  
  console.log(`Found ${orphanedPrefs.length} orphaned preferences`);
  
  // Delete orphaned preferences
  for (const pref of orphanedPrefs) {
    await deletePreference(pref.id);
    
    // Optional: Reorder remaining preferences for this user
    await reorderAfterDeletion(pref.userId, pref.domain, pref.order);
  }
  
  // Emit event for cache invalidation
  await publishEvent({
    eventType: 'preferences.cleaned',
    data: {
      entityId: accountId,
      affectedUsers: orphanedPrefs.map(p => p.userId)
    }
  });
}

async function reorderAfterDeletion(userId, domain, deletedOrder) {
  // Shift down all preferences with order > deletedOrder
  await db.preferences.updateMany(
    {
      userId,
      domain,
      order: { $gt: deletedOrder }
    },
    {
      $inc: { order: -1 }  // Decrement order by 1
    }
  );
}

API Endpoint for Cleanup (Admin/System)
POST /preferences:cleanOrphaned (System/Admin only)
httpPOST /preferences:cleanOrphaned
Authorization: Bearer system_token
Content-Type: application/json

{
  "entityId": "acc-003",
  "entityType": "account"
}
Response (200):
json{
  "deleted": 15,  // 15 users had this account in preferences
  "affectedUsers": [
    "user-123",
    "user-456",
    "user-789"
  ]
}

User Experience After Deletion
Before Deletion:
User's Account Order:
1. Startup Account C        ← Will be deleted
2. New Account E
3. Enterprise Account A
After Deletion (Auto-reordered):
User's Account Order:
1. New Account E            ← Moved up
2. Enterprise Account A     ← Moved up
Next time user fetches preferences:
httpGET /preferences?domain=accounts
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "preferences": [
    {"id": "pref-002", "entityId": "acc-005", "order": 1},  // Reordered
    {"id": "pref-003", "entityId": "acc-001", "order": 2}   // Reordered
  ],
  "totalSize": 2
}

Sub-Scenario 4B: New Account Added
Flow Diagram
Admin creates new account (acc-007)
    ↓
Account service publishes "account.created" event
    ↓
Preference service (optional): Do nothing
    ↓
User loads UI
    ↓
UI shows ordered items + new unordered item at end
    ↓
User can optionally add it to their order

Event Published:
json{
  "eventType": "account.created",
  "eventId": "evt-67890",
  "timestamp": "2024-10-07T18:00:00Z",
  "data": {
    "accountId": "acc-007",
    "accountName": "Global Account G",
    "createdBy": "admin-user-123"
  }
}
Preference Service Action:
✅ Do nothing - preferences are opt-in
❌ Don't auto-create preferences for all users

User Experience
User loads accounts page:
httpGET /preferences?domain=accounts
GET /accounts
Merged Display:
Ordered (from preferences):
1. New Account E
2. Enterprise Account A
3. Corporate Account D

Unordered (no preference yet):
4. Global Account G         ← New account appears here
5. Another Account F
User can drag "Global Account G" into their ordered list and save.

Sub-Scenario 4C: Bulk Account Deletion
Use Case: Company merger - delete 50 accounts at once
httpPOST /preferences:bulkCleanOrphaned
Authorization: Bearer system_token
Content-Type: application/json

{
  "entities": [
    {"entityId": "acc-010", "entityType": "account"},
    {"entityId": "acc-011", "entityType": "account"},
    {"entityId": "acc-012", "entityType": "account"}
    // ... 47 more
  ]
}
Response (200):
json{
  "summary": {
    "totalEntities": 50,
    "totalPreferencesDeleted": 340,
    "affectedUsers": 127,
    "errors": []
  },
  "details": [
    {
      "entityId": "acc-010",
      "preferencesDeleted": 8,
      "affectedUsers": 8
    }
    // ... more
  ]
}

API Specification for Cleanup Endpoints
Endpoint 1: Clean Single Orphaned Entity
POST /preferences:cleanOrphaned
Request Body:
json{
  "entityId": "string (required)",
  "entityType": "string (required)",
  "reorder": "boolean (optional, default: true)"
}
Response:
json{
  "deleted": 15,
  "affectedUsers": ["user-123", "user-456"],
  "reordered": true
}

Endpoint 2: Bulk Clean Orphaned Entities
POST /preferences:bulkCleanOrphaned
Request Body:
json{
  "entities": [
    {"entityId": "acc-001", "entityType": "account"},
    {"entityId": "acc-002", "entityType": "account"}
  ],
  "reorder": "boolean (optional, default: true)"
}

Endpoint 3: Detect Orphaned Preferences (Health Check)
GET /preferences:detectOrphaned?domain={domain}
Response:
json{
  "orphanedPreferences": [
    {
      "preferenceId": "pref-123",
      "userId": "user-456",
      "entityId": "acc-deleted",
      "entityType": "account",
      "reason": "Entity no longer exists"
    }
  ],
  "totalOrphaned": 3
}

Edge Cases for Scenario 4
Edge CaseHandlingAccount deleted but preference remainsCleanup job removes orphaned preferencesAccount temporarily disabledKeep preference, UI filters out disabled accountsAccount restored after deletionPreferences are gone - user must reorder if desiredMass deletionBatch cleanup endpoint, async job with progress trackingCascade failuresTransaction rollback OR compensating transaction

Complete Implementation Checklist
Frontend Responsibilities

 Fetch preferences on page load
 Fetch domain data in parallel
 Merge ordered + unordered items
 Handle empty states (no preferences)
 Implement drag-and-drop reordering
 Save with PUT :batchReplace
 Cache preferences with invalidation
 Show loading/error states
 Handle network retries

Backend Responsibilities

 Implement GET /preferences with filtering
 Implement PUT /preferences:batchReplace (idempotent)
 Implement PATCH /preferences:batchUpdate
 Implement POST /preferences:cleanOrphaned
 Listen to domain entity events (created/deleted)
 Auto-cleanup orphaned preferences
 Reorder remaining preferences after deletion
 Add ETags for cache invalidation
 Add rate limiting
 Add audit logging

Data Integrity

 Unique constraint: (userId, domain, entityId)
 Foreign key validation (optional - can be eventual consistency)
 Order conflict detection
 Cascade delete on user deletion
 Periodic orphan cleanup job


Complete Flow Summary
ScenarioPrimary MethodKey Considerations1. First-time setupPUT :batchReplaceCreates all preferences atomically2. Update existingPUT :batchReplaceReplaces all, handles reordering3. Load UIGET /preferencesParallel fetch, merge with domain data4A. Entity deletedPOST :cleanOrphanedEvent-driven cleanup, reorder remaining4B. Entity addedNo actionAppears in unordered section4C. Bulk deletionPOST :bulkCleanOrphanedAsync job with progress

**This comprehensive design handles all scenarios while maintaining data integrityRetrySSContinueEditContinuing: Advanced Scenarios & Production-Ready Considerations

Scenario 5: Concurrent Modifications (Race Conditions)
Context

User has the app open in two browser tabs
Tab 1: User reorders accounts
Tab 2: User simultaneously marks an account as favorite
Problem: Last write wins, one change gets lost


Flow Diagram
Tab 1: User reorders accounts
Tab 2: User marks account as favorite
    ↓
Both tabs send PUT/PATCH requests
    ↓
Without locking: Last write wins (data loss!)
With ETags: Conflict detected (412 Precondition Failed)
    ↓
User resolves conflict

Solution: Optimistic Concurrency Control with ETags
Step 1: Initial State
Both tabs load preferences:
httpGET /preferences?domain=accounts
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "preferences": [
    {
      "id": "pref-001",
      "entityId": "acc-001",
      "order": 1,
      "attributes": {},
      "etag": "v1",
      "updateTime": "2024-10-07T10:00:00Z"
    },
    {
      "id": "pref-002",
      "entityId": "acc-002",
      "order": 2,
      "attributes": {},
      "etag": "v1",
      "updateTime": "2024-10-07T10:00:00Z"
    }
  ],
  "etag": "collection-v1"  // Collection-level ETag
}

Step 2: Tab 1 - Reorder (Happens First)
httpPUT /preferences:batchReplace
Authorization: Bearer eyJhbGc...
If-Match: "collection-v1"
Content-Type: application/json

{
  "domain": "accounts",
  "preferences": [
    {"entityId": "acc-002", "order": 1},
    {"entityId": "acc-001", "order": 2}
  ]
}
Response (200):
json{
  "preferences": [
    {
      "id": "pref-002",
      "entityId": "acc-002",
      "order": 1,
      "etag": "v2",
      "updateTime": "2024-10-07T10:05:00Z"
    },
    {
      "id": "pref-001",
      "entityId": "acc-001",
      "order": 2,
      "etag": "v2",
      "updateTime": "2024-10-07T10:05:00Z"
    }
  ],
  "etag": "collection-v2"  // Updated!
}

Step 3: Tab 2 - Mark Favorite (Happens After, Stale ETag)
httpPATCH /preferences/pref-001
Authorization: Bearer eyJhbGc...
If-Match: "v1"  // ← Stale ETag!
Content-Type: application/json

{
  "attributes": {
    "isFavorite": true
  }
}
Response (412 Precondition Failed):
json{
  "error": {
    "code": "PRECONDITION_FAILED",
    "message": "The resource has been modified since you last retrieved it",
    "status": "FAILED_PRECONDITION",
    "details": {
      "currentEtag": "v2",
      "providedEtag": "v1",
      "resourceName": "preferences/pref-001",
      "lastModifiedTime": "2024-10-07T10:05:00Z"
    }
  }
}

Step 4: Tab 2 - Resolve Conflict
Option A: Refetch and Retry
javascriptasync function updatePreferenceWithRetry(preferenceId, updates, maxRetries = 3) {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      // Fetch latest version
      const latest = await fetch(`/preferences/${preferenceId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      }).then(r => r.json());
      
      // Apply update with current ETag
      const response = await fetch(`/preferences/${preferenceId}`, {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
          'If-Match': latest.etag
        },
        body: JSON.stringify(updates)
      });
      
      if (response.ok) {
        return response.json();
      }
      
      if (response.status === 412 && attempt < maxRetries - 1) {
        console.log(`Conflict detected, retrying (${attempt + 1}/${maxRetries})`);
        continue;
      }
      
      throw new Error(`Update failed: ${response.status}`);
    } catch (error) {
      if (attempt === maxRetries - 1) throw error;
    }
  }
}
Option B: Force Overwrite (Dangerous!)
httpPATCH /preferences/pref-001?force=true
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "attributes": {
    "isFavorite": true
  }
}
⚠️ Not recommended - can cause data loss

Implementation: Backend ETag Generation
javascript// Generate ETag for single preference
function generatePreferenceETag(preference) {
  const content = JSON.stringify({
    id: preference.id,
    order: preference.order,
    attributes: preference.attributes,
    updateTime: preference.updateTime
  });
  
  return crypto
    .createHash('sha256')
    .update(content)
    .digest('base64')
    .substring(0, 16);
}

// Generate ETag for collection
function generateCollectionETag(preferences) {
  const content = preferences
    .sort((a, b) => a.id.localeCompare(b.id))
    .map(p => p.etag)
    .join('|');
  
  return crypto
    .createHash('sha256')
    .update(content)
    .digest('base64')
    .substring(0, 16);
}

// Middleware: Check If-Match header
function checkETag(req, res, next) {
  const ifMatch = req.headers['if-match'];
  const currentETag = req.resource.etag;
  
  if (ifMatch && ifMatch !== currentETag && ifMatch !== '*') {
    return res.status(412).json({
      error: {
        code: 'PRECONDITION_FAILED',
        message: 'Resource has been modified',
        details: {
          currentEtag: currentETag,
          providedEtag: ifMatch
        }
      }
    });
  }
  
  next();
}

Updated API Specification with ETags
All Read Endpoints Return ETags
httpGET /preferences?domain=accounts
Authorization: Bearer eyJhbGc...
Response Headers:
HTTP/1.1 200 OK
ETag: "collection-abc123"
Cache-Control: private, max-age=300

All Write Endpoints Accept If-Match
httpPUT /preferences:batchReplace
Authorization: Bearer eyJhbGc...
If-Match: "collection-abc123"
Content-Type: application/json
Success:
HTTP/1.1 200 OK
ETag: "collection-xyz789"
Conflict:
HTTP/1.1 412 Precondition Failed
