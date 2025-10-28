CREATE TABLE user_identities (
                                 preferences_user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 primary_user_id VARCHAR2(255) NOT NULL,
                                 secondary_user_id VARCHAR2(255),  -- Nullable
                                 is_active NUMBER(1) DEFAULT 1 NOT NULL,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                 CONSTRAINT uk_primary_secondary UNIQUE (primary_user_id, secondary_user_id),
                                 CONSTRAINT chk_active CHECK (is_active IN (0, 1))
);

CREATE INDEX idx_user_primary ON user_identities(primary_user_id, is_active);
CREATE INDEX idx_user_secondary ON user_identities(secondary_user_id) WHERE secondary_user_id IS NOT NULL;

-- Main preferences storage (optimized for single-user queries)
CREATE TABLE user_preferences (
                                  preference_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                  preference_user_id NUMBER NOT NULL,
                                  resource_type VARCHAR2(50) NOT NULL,
                                  domain_id VARCHAR2(255) NOT NULL,
                                  compat_version VARCHAR2(20) DEFAULT 'v1' NOT NULL,

                                  preference_value VARCHAR2(4000) NOT NULL,
                                  value_type VARCHAR2(20) NOT NULL,

                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                  version_number NUMBER DEFAULT 1 NOT NULL,

                                  CONSTRAINT fk_pref_user FOREIGN KEY (preference_user_id)
                                      REFERENCES user_identities(preference_user_id) ON DELETE CASCADE,
                                  CONSTRAINT uk_pref UNIQUE (preference_user_id, resource_type, domain_id, compat_version),
                                  CONSTRAINT chk_value_type CHECK (value_type IN ('BOOLEAN', 'TEXT', 'INTEGER', 'JSON'))
);

CREATE INDEX idx_pref_user_compat ON user_preferences(preference_user_id, compat_version);
CREATE INDEX idx_pref_user_resource ON user_preferences(preference_user_id, resource_type, compat_version);
