Display Preferences Service API Specification (Refined)
Key Issues Identified & Solutions
🔴 Critical Issue: Resource Modeling
The current design treats {domain} as a path parameter, but it's actually a filter/scope, not a resource hierarchy. According to REST principles and Google API design guidelines, resources should be nouns representing entities, not contexts or filters.

Refined API Design
New Resource Model
preferences (collection resource)
└── preference (singular resource)
Base URL: https://{host}/{version}/users/{userId}/preferences

Highlighted Refinements
✅ 1. GET User Preferences - REFINED
Before:
GET /display-preferences/{domain}
After (RECOMMENDED):
GET /users/{userId}/preferences?domain={domain}&sortBy=order&sortOrder=asc
Changes:

✨ Made userId explicit in path - clearer resource ownership
✨ Moved domain to query parameter - it's a filter, not a resource
✨ Simplified path structure - preferences is the resource

Alternative (if domains are truly separate resources):
GET /users/{userId}/domains/{domain}/preferences
Use this only if domains have independent lifecycle and management.

✅ 2. Create/Update Preferences - REFINED
Before:
PUT /display-preferences/{domain}
After (RECOMMENDED):
PATCH /users/{userId}/preferences:batchUpdate
Request Body:
json{
  "domain": "accounts",
  "preferences": [
    {
      "entityId": "account-456",
      "entityType": "account",
      "order": 1,
      "attributes": {}
    }
  ],
  "updateMask": "order,attributes",
  "metadata": {
    "syncSource": "ui-dashboard"
  }
}
Changes:

✨ Used custom method :batchUpdate - Google convention for bulk operations
✨ Changed from PUT to PATCH - partial updates are more common
✨ Added updateMask - explicit field-level update control (Google API standard)
✨ Domain moved to request body - it's an attribute, not a resource path


✅ 3. Update Single Preference - REFINED
Before:
PATCH /display-preferences/{domain}/entries/{entryId}
After (RECOMMENDED):
PATCH /users/{userId}/preferences/{preferenceId}?updateMask=order,attributes.isFavorite
Request Body:
json{
  "order": 5,
  "attributes": {
    "isFavorite": false,
    "customLabel": "My Custom Name"
  }
}
Changes:

✨ Renamed entryId → preferenceId - clearer resource naming
✨ Removed {domain} from path - stored as preference attribute
✨ Added updateMask query param - Google field mask standard
✨ Flattened resource hierarchy - simpler, more RESTful


✅ 4. Reorder Preferences - REFINED
Before:
PATCH /display-preferences/{domain}/reorder
After (RECOMMENDED):
POST /users/{userId}/preferences:reorder
Request Body:
json{
  "domain": "accounts",
  "reorderOperations": [
    {
      "preferenceId": "pref-001",
      "order": 3
    },
    {
      "preferenceId": "pref-002",
      "order": 1
    }
  ]
}
Changes:

✨ Used custom method :reorder - Google convention (: prefix for custom methods)
✨ Changed to POST - custom methods typically use POST
✨ Used preferenceId instead of entityId - operate on preference resources
✨ Renamed orders → reorderOperations - more descriptive


✅ 5. Create Single Preference - REFINED
Before:
POST /display-preferences/{domain}/entries
After (RECOMMENDED):
POST /users/{userId}/preferences
Request Body:
json{
  "domain": "accounts",
  "entityId": "new-account-999",
  "entityType": "account",
  "order": 10,
  "attributes": {
    "customColor": "#FF5733",
    "notes": "High priority account"
  }
}
Response (201):
json{
  "id": "pref-123",
  "name": "users/user-123/preferences/pref-123",
  "domain": "accounts",
  "entityId": "new-account-999",
  "entityType": "account",
  "order": 10,
  "attributes": {...},
  "createTime": "2024-01-15T10:30:00Z",
  "updateTime": "2024-01-15T10:30:00Z"
}
Changes:

✨ Domain in request body - it's an attribute of the preference
✨ Added resource name field - Google API standard for full resource path
✨ Renamed timestamps - createTime/updateTime (Google convention)


✅ 6. Delete Preference - REFINED
Before:
DELETE /display-preferences/{domain}/entries/{entryId}
After (RECOMMENDED):
DELETE /users/{userId}/preferences/{preferenceId}
Changes:

✨ Simplified path - removed unnecessary domain nesting
✨ Domain is stored in resource - no need in path


✅ 7. List Available Domains - REFINED
Before:
GET /display-preferences/domains
After (RECOMMENDED - Option A):
GET /users/{userId}/preferences:aggregateDomains
Response:
json{
  "domains": [
    {
      "domain": "accounts",
      "preferenceCount": 15,
      "lastModified": "2024-01-15T10:30:00Z"
    },
    {
      "domain": "partners",
      "preferenceCount": 8,
      "lastModified": "2024-01-14T15:20:00Z"
    }
  ]
}
Alternative (Option B - if domains are first-class resources):
GET /users/{userId}/domains
Changes:

✨ Used custom method - aggregation operation
✨ Made userId explicit - clearer ownership
✨ Renamed entryCount → preferenceCount - consistent terminology


✅ 8. Bulk Operations - REFINED
Before:
POST /users/bulk
After (RECOMMENDED):
POST /users/{userId}/preferences:batchModify
Request Body:
json{
  "operations": [
    {
      "operation": "CREATE",
      "preference": {
        "domain": "accounts",
        "entityId": "account-123",
        "entityType": "account",
        "order": 1
      }
    },
    {
      "operation": "DELETE",
      "preferenceId": "pref-456"
    },
    {
      "operation": "UPDATE",
      "preferenceId": "pref-789",
      "preference": {
        "order": 5
      },
      "updateMask": "order"
    }
  ]
}
Response:
json{
  "results": [
    {
      "operation": "CREATE",
      "preference": {...},
      "status": {"code": 0, "message": "OK"}
    },
    {
      "operation": "DELETE",
      "status": {"code": 0, "message": "OK"}
    }
  ],
  "summary": {
    "total": 2,
    "successful": 2,
    "failed": 0
  }
}
Changes:

✨ Renamed to :batchModify - Google bulk operation convention
✨ Added operation results - individual success/failure tracking
✨ Used enum for operations - CREATE/UPDATE/DELETE
✨ Added summary statistics - better observability


Updated Data Models
Preference Resource (Refined)
json{
  "id": "pref-001",
  "name": "users/user-123/preferences/pref-001",
  "domain": "accounts",
  "entityId": "account-456",
  "entityType": "account",
  "order": 1,
  "attributes": {},
  "createTime": "2024-01-15T10:30:00Z",
  "updateTime": "2024-01-15T10:30:00Z",
  "etag": "BwX9z3kKhYo="
}
Key Changes:

✨ Added name - full resource identifier
✨ Added etag - optimistic concurrency control
✨ Renamed timestamps to Google convention


Complete Refined Endpoint Summary
MethodEndpointPurposeGET/users/{userId}/preferencesList preferences (with domain filter)POST/users/{userId}/preferencesCreate preferenceGET/users/{userId}/preferences/{preferenceId}Get single preferencePATCH/users/{userId}/preferences/{preferenceId}Update preferenceDELETE/users/{userId}/preferences/{preferenceId}Delete preferencePOST/users/{userId}/preferences:reorderReorder multiple preferencesPATCH/users/{userId}/preferences:batchUpdateBatch update preferencesPOST/users/{userId}/preferences:batchModifyMixed batch operationsGET/users/{userId}/preferences:aggregateDomainsList domain summary

Additional Google API Best Practices Applied

✨ Resource-oriented design - preferences are resources, not contexts
✨ Standard methods - GET, POST, PATCH, DELETE
✨ Custom methods - :reorder, :batchUpdate, :batchModify, :aggregateDomains
✨ Field masks - updateMask for partial updates
✨ ETags - optimistic concurrency control
✨ Resource names - full paths like users/123/preferences/456
✨ Consistent naming - createTime, updateTime, not createdAt
✨ Pagination support - add pageSize, pageToken for large lists
✨ Filtering - domain as query parameter, not path

Would you like me to elaborate on any specific aspect or provide implementation examples?RetrySSwe will have userId trom token please refine the responseEditDisplay Preferences Service API Specification (Refined)
Key Issues Identified & Solutions
🔴 Critical Issue: Resource Modeling
The current design treats {domain} as a path parameter, but it's actually a filter/scope, not a resource hierarchy. According to REST principles and Google API design guidelines, resources should be nouns representing entities, not contexts or filters.
🔴 Authentication Context
Since userId is extracted from the authentication token, it should be implicit in the API design, not part of the URL path.

Refined API Design
New Resource Model
preferences (collection resource - scoped to authenticated user)
└── preference (singular resource)
Base URL: https://{host}/{version}/preferences
Authentication: Bearer token (userId extracted automatically)

Highlighted Refinements
✅ 1. GET User Preferences - REFINED
Before:
GET /display-preferences/{domain}
After (RECOMMENDED):
GET /preferences?domain={domain}&sortBy=order&sortOrder=asc
Query Parameters:

domain (string, optional): Filter by domain context (e.g., "accounts", "partners")
sortBy (string, optional): Sort field (default: "order")
sortOrder (string, optional): "asc" or "desc" (default: "asc")
includeAttributes (boolean, optional): Include custom attributes (default: true)
pageSize (integer, optional): Number of results per page
pageToken (string, optional): Token for pagination

Response (200):
json{
  "preferences": [
    {
      "id": "pref-001",
      "name": "preferences/pref-001",
      "domain": "accounts",
      "entityId": "account-456",
      "entityType": "account",
      "order": 1,
      "attributes": {},
      "createTime": "2024-01-15T10:30:00Z",
      "updateTime": "2024-01-15T10:30:00Z",
      "etag": "BwX9z3kKhYo="
    }
  ],
  "nextPageToken": "CiAKGjBpNDd2Nmp2Zml2cXRwYjBpOXA",
  "totalSize": 10
}
Changes:

✨ Removed /users/{userId} from path - implicit from token
✨ Made domain a query parameter - it's a filter, not a resource
✨ Simplified base path to /preferences - cleaner, user context implicit
✨ Added pagination support - pageSize, pageToken, nextPageToken


✅ 2. Create/Update Preferences - REFINED
Before:
PUT /display-preferences/{domain}
After (RECOMMENDED):
PATCH /preferences:batchUpdate
Request Body:
json{
  "requests": [
    {
      "preference": {
        "domain": "accounts",
        "entityId": "account-456",
        "entityType": "account",
        "order": 1,
        "attributes": {}
      },
      "updateMask": "order,attributes"
    },
    {
      "preference": {
        "domain": "accounts",
        "entityId": "account-789",
        "entityType": "account",
        "order": 2,
        "attributes": {
          "isFavorite": true
        }
      },
      "updateMask": "order,attributes.isFavorite"
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
      "entityId": "account-456",
      "entityType": "account",
      "order": 1,
      "attributes": {},
      "createTime": "2024-01-15T10:30:00Z",
      "updateTime": "2024-01-15T10:35:00Z",
      "etag": "CwY8a2lLiZp="
    }
  ]
}
Changes:

✨ Removed userId from path - implicit from auth token
✨ Used custom method :batchUpdate - Google convention for bulk operations
✨ Changed from PUT to PATCH - partial updates are more common
✨ Added updateMask - explicit field-level update control (Google API standard)
✨ Domain moved to request body - it's an attribute, not a resource path


✅ 3. Update Single Preference - REFINED
Before:
PATCH /display-preferences/{domain}/entries/{entryId}
After (RECOMMENDED):
PATCH /preferences/{preferenceId}
Query Parameters:

updateMask (string, optional): Fields to update (e.g., "order,attributes.isFavorite")

Request Body:
json{
  "order": 5,
  "attributes": {
    "isFavorite": false,
    "customLabel": "My Custom Name"
  }
}
Response (200):
json{
  "id": "pref-001",
  "name": "preferences/pref-001",
  "domain": "accounts",
  "entityId": "account-456",
  "entityType": "account",
  "order": 5,
  "attributes": {
    "isFavorite": false,
    "customLabel": "My Custom Name"
  },
  "createTime": "2024-01-15T10:30:00Z",
  "updateTime": "2024-01-15T10:40:00Z",
  "etag": "DxZ1b4mNkRq="
}
Changes:

✨ Removed userId from path - implicit from auth token
✨ Renamed entryId → preferenceId - clearer resource naming
✨ Removed {domain} from path - stored as preference attribute
✨ Added updateMask query param - Google field mask standard
✨ Flattened resource hierarchy - simpler, more RESTful


✅ 4. Reorder Preferences - REFINED
Before:
PATCH /display-preferences/{domain}/reorder
After (RECOMMENDED):
POST /preferences:reorder
Request Body:
json{
  "domain": "accounts",
  "reorderOperations": [
    {
      "preferenceId": "pref-001",
      "order": 3
    },
    {
      "preferenceId": "pref-002",
      "order": 1
    },
    {
      "preferenceId": "pref-003",
      "order": 2
    }
  ]
}
Response (200):
json{
  "preferences": [
    {
      "id": "pref-002",
      "order": 1,
      "updateTime": "2024-01-15T10:35:00Z"
    },
    {
      "id": "pref-003",
      "order": 2,
      "updateTime": "2024-01-15T10:35:00Z"
    },
    {
      "id": "pref-001",
      "order": 3,
      "updateTime": "2024-01-15T10:35:00Z"
    }
  ],
  "domain": "accounts"
}
Changes:

✨ Removed userId from path - implicit from auth token
✨ Used custom method :reorder - Google convention (: prefix for custom methods)
✨ Changed to POST - custom methods typically use POST
✨ Used preferenceId instead of entityId - operate on preference resources
✨ Renamed orders → reorderOperations - more descriptive


✅ 5. Create Single Preference - REFINED
Before:
POST /display-preferences/{domain}/entries
After (RECOMMENDED):
POST /preferences
Request Body:
json{
  "domain": "accounts",
  "entityId": "new-account-999",
  "entityType": "account",
  "order": 10,
  "attributes": {
    "customColor": "#FF5733",
    "notes": "High priority account"
  }
}
Response (201):
json{
  "id": "pref-123",
  "name": "preferences/pref-123",
  "domain": "accounts",
  "entityId": "new-account-999",
  "entityType": "account",
  "order": 10,
  "attributes": {
    "customColor": "#FF5733",
    "notes": "High priority account"
  },
  "createTime": "2024-01-15T10:30:00Z",
  "updateTime": "2024-01-15T10:30:00Z",
  "etag": "AxY2c5nOpTu="
}
Changes:

✨ Removed userId from path - implicit from auth token
✨ Simplified to /preferences - clean resource creation
✨ Domain in request body - it's an attribute of the preference
✨ Added resource name field - Google API standard for full resource path
✨ Renamed timestamps - createTime/updateTime (Google convention)
✨ Added etag - for optimistic concurrency control


✅ 6. Get Single Preference - NEW
New Endpoint (RECOMMENDED):
GET /preferences/{preferenceId}
Response (200):
json{
  "id": "pref-001",
  "name": "preferences/pref-001",
  "domain": "accounts",
  "entityId": "account-456",
  "entityType": "account",
  "order": 1,
  "attributes": {},
  "createTime": "2024-01-15T10:30:00Z",
  "updateTime": "2024-01-15T10:30:00Z",
  "etag": "BwX9z3kKhYo="
}
Changes:

✨ Added missing GET single resource endpoint - standard REST pattern
✨ No userId in path - implicit from auth token


✅ 7. Delete Preference - REFINED
Before:
DELETE /display-preferences/{domain}/entries/{entryId}
After (RECOMMENDED):
DELETE /preferences/{preferenceId}
Optional Query Parameters:

etag (string, optional): For optimistic concurrency control

Response (204): No content
Changes:

✨ Removed userId from path - implicit from auth token
✨ Simplified path - removed unnecessary domain nesting
✨ Domain is stored in resource - no need in path
✨ Added optional etag - prevent accidental deletions


✅ 8. List Available Domains - REFINED
Before:
GET /display-preferences/domains
After (RECOMMENDED):
GET /preferences:aggregateDomains
Response (200):
json{
  "domains": [
    {
      "domain": "accounts",
      "preferenceCount": 15,
      "lastModified": "2024-01-15T10:30:00Z"
    },
    {
      "domain": "partners",
      "preferenceCount": 8,
      "lastModified": "2024-01-14T15:20:00Z"
    }
  ]
}
Changes:

✨ Removed userId from path - implicit from auth token
✨ Used custom method :aggregateDomains - aggregation operation
✨ Renamed entryCount → preferenceCount - consistent terminology


✅ 9. Bulk Operations - REFINED
Before:
POST /users/bulk
After (RECOMMENDED):
POST /preferences:batchModify
Request Body:
json{
  "operations": [
    {
      "operation": "CREATE",
      "preference": {
        "domain": "accounts",
        "entityId": "account-123",
        "entityType": "account",
        "order": 1,
        "attributes": {}
      }
    },
    {
      "operation": "DELETE",
      "preferenceId": "pref-456"
    },
    {
      "operation": "UPDATE",
      "preferenceId": "pref-789",
      "preference": {
        "order": 5
      },
      "updateMask": "order"
    }
  ]
}
Response (200):
json{
  "results": [
    {
      "operation": "CREATE",
      "preference": {
        "id": "pref-890",
        "name": "preferences/pref-890",
        "domain": "accounts",
        "entityId": "account-123",
        "order": 1
      },
      "status": {
        "code": 0,
        "message": "OK"
      }
    },
    {
      "operation": "DELETE",
      "preferenceId": "pref-456",
      "status": {
        "code": 0,
        "message": "OK"
      }
    },
    {
      "operation": "UPDATE",
      "preference": {
        "id": "pref-789",
        "order": 5
      },
      "status": {
        "code": 0,
        "message": "OK"
      }
    }
  ],
  "summary": {
    "total": 3,
    "successful": 3,
    "failed": 0
  }
}
Changes:

✨ Removed userId from path - implicit from auth token
✨ Renamed to :batchModify - Google bulk operation convention
✨ Added operation results - individual success/failure tracking
✨ Used enum for operations - CREATE/UPDATE/DELETE
✨ Added summary statistics - better observability


Updated Data Models
Preference Resource (Refined)
json{
  "id": "pref-001",
  "name": "preferences/pref-001",
  "domain": "accounts",
  "entityId": "account-456",
  "entityType": "account",
  "order": 1,
  "attributes": {
    "isFavorite": true,
    "customLabel": "Primary Account",
    "tags": ["important"]
  },
  "createTime": "2024-01-15T10:30:00Z",
  "updateTime": "2024-01-15T10:30:00Z",
  "etag": "BwX9z3kKhYo="
}
Field Descriptions:

id (string): Unique identifier for the preference
name (string): Full resource name (Google API standard)
domain (string): Domain context (e.g., "accounts", "partners")
entityId (string): ID of the entity being ordered/customized
entityType (string): Type of entity (e.g., "account", "partner")
order (integer): Display order within the domain
attributes (object): Flexible JSON object for custom properties
createTime (string, ISO 8601): Creation timestamp
updateTime (string, ISO 8601): Last update timestamp
etag (string): Entity tag for optimistic concurrency control


Complete Refined Endpoint Summary
MethodEndpointPurposeAuth SourceGET/preferencesList preferences (with optional domain filter)TokenPOST/preferencesCreate single preferenceTokenGET/preferences/{preferenceId}Get single preferenceTokenPATCH/preferences/{preferenceId}Update single preferenceTokenDELETE/preferences/{preferenceId}Delete preferenceTokenPOST/preferences:reorderReorder multiple preferencesTokenPATCH/preferences:batchUpdateBatch update preferencesTokenPOST/preferences:batchModifyMixed batch operations (create/update/delete)TokenGET/preferences:aggregateDomainsGet domain summary statisticsToken

Error Responses (Updated)
Standard Error Format
json{
  "error": {
    "code": "PREFERENCE_NOT_FOUND",
    "message": "The requested preference does not exist or you don't have access to it",
    "status": "NOT_FOUND",
    "details": [
      {
        "@type": "type.googleapis.com/google.rpc.ResourceInfo",
        "resourceType": "Preference",
        "resourceName": "preferences/pref-001"
      }
    ]
  }
}
Common Error Codes

INVALID_ARGUMENT (400): Invalid domain, entity type, or request parameters
UNAUTHENTICATED (401): Missing or invalid authentication token
PERMISSION_DENIED (403): User doesn't have access to the preference
NOT_FOUND (404): Preference doesn't exist
ALREADY_EXISTS (409): Duplicate preference for entity in domain
FAILED_PRECONDITION (412): ETag mismatch (concurrent modification)
INTERNAL (500): Internal server error


Additional Google API Best Practices Applied

✨ Implicit user context - userId extracted from auth token, not in URL
✨ Resource-oriented design - preferences are resources, not contexts
✨ Standard methods - GET, POST, PATCH, DELETE
✨ Custom methods - :reorder, :batchUpdate, :batchModify, :aggregateDomains
✨ Field masks - updateMask for partial updates
✨ ETags - optimistic concurrency control
✨ Resource names - full paths like preferences/pref-123
✨ Consistent naming - createTime, updateTime, not createdAt
✨ Pagination support - pageSize, pageToken, nextPageToken
✨ Filtering - domain as query parameter, not path
✨ Structured errors - Google error format with details
✨ Clean URLs - no redundant user context in paths


Example Usage Scenarios
Scenario 1: User Reorders Accounts on Dashboard
httpPOST /preferences:reorder
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "domain": "accounts",
  "reorderOperations": [
    {"preferenceId": "pref-001", "order": 2},
    {"preferenceId": "pref-002", "order": 1},
    {"preferenceId": "pref-003", "order": 3}
  ]
}
Scenario 2: User Marks Partner as Favorite
httpPATCH /preferences/pref-456?updateMask=attributes.isFavorite
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "attributes": {
    "isFavorite": true
  }
}
Scenario 3: Fetch All Account Preferences
httpGET /preferences?domain=accounts&sortBy=order&sortOrder=asc
Authorization: Bearer eyJhbGc...
This refined design is cleaner, more RESTful, and follows Google API design guidelines while keeping the user context implicit through authentication! 🎯RetrySSAnd what happens in case of scenario when a user don't have prefreences set so the UI will send the preferences order with the entry details for the first time?EditExcellent question! This is a critical initial state scenario. Let me design the optimal approach for this first-time setup.

First-Time Preference Setup Scenarios
Scenario: User Orders Accounts for the First Time
When a user clicks the customize button and orders their accounts (or partners) for the first time, they need to create multiple preferences in one atomic operation.

Recommended Approach: Use :batchUpdate with Upsert Semantics
Why This Approach?

✅ Atomic operation - All preferences created together
✅ Idempotent - Safe to retry if network fails
✅ Upsert semantics - Creates if not exists, updates if exists
✅ Single API call - Better performance and UX


API Call for First-Time Setup
httpPATCH /preferences:batchUpdate
Authorization: Bearer eyJhbGc...
Content-Type: application/json
Request Body:
json{
  "domain": "accounts",
  "preferences": [
    {
      "entityId": "account-123",
      "entityType": "account",
      "order": 1,
      "attributes": {}
    },
    {
      "entityId": "account-456",
      "entityType": "account",
      "order": 2,
      "attributes": {}
    },
    {
      "entityId": "account-789",
      "entityType": "account",
      "order": 3,
      "attributes": {
        "isFavorite": true
      }
    }
  ],
  "allowCreate": true
}
Response (200):
json{
  "preferences": [
    {
      "id": "pref-001",
      "name": "preferences/pref-001",
      "domain": "accounts",
      "entityId": "account-123",
      "entityType": "account",
      "order": 1,
      "attributes": {},
      "createTime": "2024-01-15T10:30:00Z",
      "updateTime": "2024-01-15T10:30:00Z",
      "etag": "AxY2c5nOpTu="
    },
    {
      "id": "pref-002",
      "name": "preferences/pref-002",
      "domain": "accounts",
      "entityId": "account-456",
      "entityType": "account",
      "order": 2,
      "attributes": {},
      "createTime": "2024-01-15T10:30:00Z",
      "updateTime": "2024-01-15T10:30:00Z",
      "etag": "BwZ3d6oQpVv="
    },
    {
      "id": "pref-003",
      "name": "preferences/pref-003",
      "domain": "accounts",
      "entityId": "account-789",
      "entityType": "account",
      "order": 3,
      "attributes": {
        "isFavorite": true
      },
      "createTime": "2024-01-15T10:30:00Z",
      "updateTime": "2024-01-15T10:30:00Z",
      "etag": "CxA4e7pRqWw="
    }
  ],
  "summary": {
    "created": 3,
    "updated": 0,
    "unchanged": 0
  }
}

Updated :batchUpdate Endpoint Specification
PATCH /preferences:batchUpdate
Purpose: Create or update multiple preferences atomically with upsert semantics.
Request Body Parameters:
json{
  "domain": "string (required)",
  "preferences": [
    {
      "entityId": "string (required)",
      "entityType": "string (required)",
      "order": "integer (required)",
      "attributes": "object (optional)"
    }
  ],
  "allowCreate": "boolean (optional, default: true)",
  "replaceAll": "boolean (optional, default: false)"
}
Field Descriptions:

✨ domain (required): The domain context (e.g., "accounts", "partners")
✨ preferences (required): Array of preference objects to create/update
✨ allowCreate (optional, default: true):

If true: Creates new preferences if they don't exist (UPSERT)
If false: Only updates existing preferences, returns error for non-existent ones


✨ replaceAll (optional, default: false):

If true: Deletes all existing preferences in the domain not included in the request
If false: Only creates/updates specified preferences, leaves others unchanged



Response (200):
json{
  "preferences": [/* array of created/updated preferences */],
  "summary": {
    "created": 3,
    "updated": 0,
    "unchanged": 0,
    "deleted": 0
  }
}

Decision Tree for UI Implementation
┌─────────────────────────────────────────┐
│ User clicks "Customize Order" button    │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│ UI: Fetch existing preferences          │
│ GET /preferences?domain=accounts        │
└──────────────┬──────────────────────────┘
               │
               ▼
       ┌───────┴───────┐
       │               │
   Empty list?     Has data?
       │               │
       ▼               ▼
┌──────────────┐  ┌──────────────┐
│ First Time   │  │ Existing     │
│ Setup        │  │ Preferences  │
└──────┬───────┘  └──────┬───────┘
       │                 │
       ▼                 ▼
┌──────────────────┐  ┌──────────────────┐
│ Show all items   │  │ Show ordered     │
│ in default order │  │ items first,     │
│                  │  │ then unordered   │
└──────┬───────────┘  └──────┬───────────┘
       │                     │
       ▼                     ▼
┌──────────────────────────────────────┐
│ User drags/reorders items in UI      │
└──────────────┬───────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│ User clicks "Save"                       │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│ PATCH /preferences:batchUpdate           │
│ {                                        │
│   "domain": "accounts",                  │
│   "preferences": [...],                  │
│   "allowCreate": true                    │
│ }                                        │
└──────────────────────────────────────────┘

Complete Flow Examples
Example 1: First-Time Setup (Empty State)
Step 1: Check for existing preferences
httpGET /preferences?domain=accounts
Authorization: Bearer eyJhbGc...
Response:
json{
  "preferences": [],
  "totalSize": 0
}
Step 2: User orders 5 accounts in the UI and clicks Save
httpPATCH /preferences:batchUpdate
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "domain": "accounts",
  "preferences": [
    {"entityId": "acc-1", "entityType": "account", "order": 1},
    {"entityId": "acc-2", "entityType": "account", "order": 2},
    {"entityId": "acc-3", "entityType": "account", "order": 3},
    {"entityId": "acc-4", "entityType": "account", "order": 4},
    {"entityId": "acc-5", "entityType": "account", "order": 5}
  ],
  "allowCreate": true
}
Response:
json{
  "preferences": [/* 5 newly created preferences */],
  "summary": {
    "created": 5,
    "updated": 0,
    "unchanged": 0,
    "deleted": 0
  }
}

Example 2: Subsequent Update (Already Has Preferences)
Step 1: Fetch existing preferences
httpGET /preferences?domain=accounts
Authorization: Bearer eyJhbGc...
Response:
json{
  "preferences": [
    {"id": "pref-1", "entityId": "acc-1", "order": 1},
    {"id": "pref-2", "entityId": "acc-2", "order": 2},
    {"id": "pref-3", "entityId": "acc-3", "order": 3}
  ],
  "totalSize": 3
}
Step 2: User reorders and adds new account
httpPATCH /preferences:batchUpdate
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "domain": "accounts",
  "preferences": [
    {"entityId": "acc-2", "entityType": "account", "order": 1},
    {"entityId": "acc-1", "entityType": "account", "order": 2},
    {"entityId": "acc-3", "entityType": "account", "order": 3},
    {"entityId": "acc-4", "entityType": "account", "order": 4}
  ],
  "allowCreate": true
}
Response:
json{
  "preferences": [/* 3 updated + 1 created */],
  "summary": {
    "created": 1,
    "updated": 3,
    "unchanged": 0,
    "deleted": 0
  }
}

Example 3: Replace All Preferences in Domain
Use Case: User wants to completely reset their account order
httpPATCH /preferences:batchUpdate
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "domain": "accounts",
  "preferences": [
    {"entityId": "acc-5", "entityType": "account", "order": 1},
    {"entityId": "acc-6", "entityType": "account", "order": 2}
  ],
  "allowCreate": true,
  "replaceAll": true
}
Response:
json{
  "preferences": [/* 2 new preferences */],
  "summary": {
    "created": 2,
    "updated": 0,
    "unchanged": 0,
    "deleted": 3
  }
}

Alternative Approach: Lightweight Check Endpoint
If you want to optimize the initial check, add a lightweight endpoint:
GET /preferences:exists
Purpose: Quickly check if user has any preferences in a domain without fetching all data.
httpGET /preferences:exists?domain=accounts
Authorization: Bearer eyJhbGc...
Response (200):
json{
  "exists": false,
  "domain": "accounts",
  "count": 0
}
or
json{
  "exists": true,
  "domain": "accounts",
  "count": 15
}
UI Logic:
javascript// Step 1: Quick check
const { exists } = await checkPreferencesExist('accounts');

if (!exists) {
  // First time - show all accounts in default order
  displayAllAccountsForOrdering();
} else {
  // Has preferences - fetch and display ordered list
  const { preferences } = await getPreferences('accounts');
  displayOrderedAccounts(preferences);
}

// Step 2: User reorders and saves
await batchUpdatePreferences({
  domain: 'accounts',
  preferences: userOrderedList,
  allowCreate: true
});

Recommended Implementation Strategy
For UI/Frontend:
javascriptasync function handleCustomizeOrder(domain) {
  // 1. Fetch existing preferences
  const response = await fetch(`/preferences?domain=${domain}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  
  const { preferences, totalSize } = await response.json();
  
  if (totalSize === 0) {
    // First-time setup: Show all entities in default order
    const allEntities = await fetchAllEntities(domain); // e.g., all accounts
    showOrderingDialog(allEntities, []);
  } else {
    // Has preferences: Show ordered items
    const allEntities = await fetchAllEntities(domain);
    showOrderingDialog(allEntities, preferences);
  }
}

async function savePreferenceOrder(domain, orderedItems) {
  const preferences = orderedItems.map((item, index) => ({
    entityId: item.id,
    entityType: item.type,
    order: index + 1,
    attributes: item.attributes || {}
  }));
  
  const response = await fetch('/preferences:batchUpdate', {
    method: 'PATCH',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      domain,
      preferences,
      allowCreate: true
    })
  });
  
  return response.json();
}

Summary: Key Design Decisions
AspectDecisionRationaleFirst-time setupUse :batchUpdate with allowCreate: trueSingle API call, atomic, idempotentUpsert semanticsDefault behaviorSimplifies client logicInitial state checkGET /preferences?domain=XStandard list operationOptimizationOptional :exists endpointReduce payload for existence checkReplace allreplaceAll: false by defaultSafer default, opt-in for complete replacement
