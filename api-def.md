# Display Preferences Service API Specification

## Overview
The Display Preferences Service manages user-specific UI data preferences for ordering and customizing how domain data is presented across different domains.

## Base URL
```
https://{host}/{version}/display-preferences
```

## Authentication
All endpoints require authentication via Bearer token in the Authorization header.
(Extracting user-specific id(s) from the token is assumed.)
---

## Data Models

### PreferenceEntry
```json
{
  "id": "string (UUID)",
  "userId": "string",
  "domain": "string",
  "entityId": "string",
  "entityType": "string",
  "order": "integer",
  "attributes": "object",
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

### PreferenceContext
```json
{
  "domain": "string",
  "userId": "string",
  "entries": "PreferenceEntry[]",
  "metadata": "object",
  "lastModified": "string (ISO 8601)"
}
```

---

## API Endpoints

### 1. Get User Preferences by Context

**GET** `/users/{userId}/{domain}`

Retrieve all preferences for a specific user and domain.

**Path Parameters:**
- `userId` (string, required): User identifier (May be extracted from auth token from more than one identifier)
- `domain` (string, required): Preference domain context (e.g., "accounts", "partners")

**Query Parameters:**
- `includeAttributes` (boolean, optional): Include custom attributes in response (default: true)
- `sortBy` (string, optional): Sort field (default: "order")
- `sortOrder` (string, optional): "asc" or "desc" (default: "asc")

**Response (200):**
```json
{
  "domain": "accounts",
  "userId": "user-123",
  "entries": [
    {
      "id": "pref-001",
      "userId": "user-123",
      "domain": "accounts",
      "entityId": "account-456",
      "entityType": "account",
      "order": 1,
      "attributes": {},
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z"
    }
  ],
  "metadata": {
    "totalCount": 10,
    "lastSync": "2024-01-15T10:30:00Z"
  },
  "lastModified": "2024-01-15T10:30:00Z"
}
```

### 2. Create or Update Preferences Context

**PUT** `/users/{userId}/{domain}`

Creates or completely replaces all preferences for a given domain.

**Path Parameters:**
- `userId` (string, required): User identifier
- `domain` (string, required): Preference domain context

**Request Body:**
```json
{
  "entries": [
    {
      "entityId": "account-456",
      "entityType": "account",
      "order": 1,
      "attributes": {}
    },
    {
      "entityId": "partner-789",
      "entityType": "partner",
      "order": 1,
      "attributes": {
        "isFavorite": true,
        "tags": ["important", "primary"]
      }
    }
  ],
  "metadata": {
    "syncSource": "ui-dashboard"
  }
}
```

**Response (200):** Returns updated PreferenceContext

### 3. Update Single Preference Entry

**PATCH** `/users/{userId}/{domain}/entries/{entryId}`

Updates a specific preference entry.

**Path Parameters:**
- `userId` (string, required): User identifier
- `domain` (string, required): Preference domain context
- `entryId` (string, required): Preference entry ID

**Request Body:**
```json
{
  "order": 5,
  "attributes": {
    "isFavorite": false,
    "customLabel": "My Custom Name"
  }
}
```

**Response (200):** Returns updated PreferenceEntry

### 4. Bulk Update Preferences Order

**PATCH** `/users/{userId}/{domain}/reorder`

Updates the order of multiple preference entries efficiently.

**Request Body:**
```json
{
  "orders": [
    {
      "entityId": "account-456",
      "order": 3
    },
    {
      "entityId": "account-789",
      "order": 1
    }
  ]
}
```

**Response (200):**
```json
{
  "updated": 2,
  "domain": "accounts",
  "lastModified": "2024-01-15T10:35:00Z"
}
```

### 5. Add New Preference Entry

**POST** `/users/{userId}/{domain}/entries`

Adds a new preference entry to an existing domain context.

**Request Body:**
```json
{
  "entityId": "new-account-999",
  "entityType": "account",
  "order": 10,
  "attributes": {
    "customColor": "#FF5733",
    "notes": "High priority account"
  }
}
```

**Response (201):** Returns created PreferenceEntry

### 6. Delete Preference Entry

**DELETE** `/users/{userId}/{domain}/entries/{entryId}`

Removes a specific preference entry.

**Response (204):** No content

### 7. Get Available Contexts

**GET** `/users/{userId}/domains`

Lists all preference domain contexts for a user.

**Response (200):**
```json
{
  "contexts": [
    {
      "name": "accounts",
      "entryCount": 15,
      "lastModified": "2024-01-15T10:30:00Z"
    },
    {
      "name": "partners",
      "entryCount": 8,
      "lastModified": "2024-01-14T15:20:00Z"
    }
  ]
}
```

### 8. Bulk Operations

**POST** `/users/{userId}/bulk`

Performs bulk operations across multiple contexts.

**Request Body:**
```json
{
  "operations": [
    {
      "action": "upsert",
      "domain": "accounts",
      "entityId": "account-123",
      "entityType": "account",
      "order": 1,
      "attributes": {}
    },
    {
      "action": "delete",
      "domain": "partners",
      "entityId": "partner-456"
    }
  ]
}
```

---

## Error Responses

### Standard Error Format
```json
{
  "error": {
    "code": "PREFERENCE_NOT_FOUND",
    "message": "Preference entry not found",
    "details": {
      "userId": "user-123",
      "domain": "accounts",
      "entryId": "pref-001"
    },
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### Common Error Codes
- `INVALID_DOMAIN` (400): Invalid or unsupported domain context
- `PREFERENCE_NOT_FOUND` (404): Preference entry doesn't exist
- `DUPLICATE_ORDER` (409): Order conflict within domain context
- `INVALID_ENTITY_TYPE` (400): Unsupported entity type for domain context
- `USER_NOT_FOUND` (404): User doesn't exist
- `VALIDATION_ERROR` (400): Request validation failed

---

## Design Principles

### Flexibility for Future Attributes
- **Open Attributes Object**: The `attributes` field is a flexible JSON object that can store any custom properties
- **Context-Aware Validation**: Different domain contexts can have different attribute schemas
- **Versioned API**: API versioning allows for schema evolution
- **Metadata Support**: Context-level metadata for storing additional information

### Performance Considerations
- **Bulk Operations**: Efficient endpoints for mass updates
- **Selective Loading**: Optional attribute inclusion to reduce payload size
- **Caching Headers**: Proper ETags and Last-Modified headers for caching

### Example Usage Scenarios

#### Account Preferences (Simple Order)
```json
{
  "domain": "accounts",
  "entries": [
    {
      "entityId": "account-1",
      "entityType": "account",
      "order": 1,
      "attributes": {}
    },
    {
      "entityId": "account-2", 
      "entityType": "account",
      "order": 2,
      "attributes": {}
    }
  ]
}
```

#### Partner Preferences (Order + Favorites)
```json
{
  "domain": "partners",
  "entries": [
    {
      "entityId": "partner-1",
      "entityType": "partner", 
      "order": 1,
      "attributes": {
        "isFavorite": true,
        "category": "strategic",
        "customLabel": "Primary Partner"
      }
    },
    {
      "entityId": "partner-2",
      "entityType": "partner",
      "order": 2,
      "attributes": {
        "isFavorite": false,
        "tags": ["regional", "backup"]
      }
    }
  ]
}
```