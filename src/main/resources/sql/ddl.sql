CREATE TABLE user_identities (
                                 preference_user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 primary_user_id VARCHAR2(255) NOT NULL,
                                 secondary_user_id VARCHAR2(255),  -- Nullable
                                 identity_type VARCHAR2(10) CHECK (identity_type IN ('RETAIL', 'CORP')) NOT NULL,
                                 is_active NUMBER(1) DEFAULT 1 NOT NULL,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                 CONSTRAINT uk_primary_secondary UNIQUE (primary_user_id, secondary_user_id),
                                 CONSTRAINT chk_active CHECK (is_active IN (0, 1))
);

CREATE INDEX idx_user_primary ON user_identities(primary_user_id, is_active);
CREATE INDEX idx_user_lookup ON user_identities(primary_user_id, secondary_user_id, is_active);

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
                                  CONSTRAINT chk_value_type CHECK (value_type IN ('BOOLEAN', 'TEXT', 'INTEGER', 'JSON')),
                                  CONSTRAINT pk_pref PRIMARY KEY (preference_id, preference_user_id)

) PARTITION BY RANGE (preference_user_id) INTERVAL (100000) (
      PARTITION p_initial VALUES LESS THAN (100000)
  );;

CREATE INDEX idx_pref_user ON user_preferences(preference_user_id);
CREATE INDEX idx_pref_user_compat ON user_preferences(preference_user_id, compat_version);
CREATE INDEX idx_pref_user_resource ON user_preferences(preference_user_id, resource_type, compat_version);
CREATE INDEX idx_pref_version_user ON user_preferences(compat_version, preference_user_id);
CREATE INDEX idx_pref_domain ON user_preferences(domain_id, compat_version);

CREATE TABLE user_sortables (
                                sort_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                preference_user_id NUMBER NOT NULL,
                                sort_type VARCHAR2(20) NOT NULL,
                                domain_id VARCHAR2(255) NOT NULL,
                                sort_position NUMBER NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

                                CONSTRAINT fk_sort_user FOREIGN KEY (preference_user_id)
                                    REFERENCES user_identities(internal_user_id) ON DELETE CASCADE,

                                CONSTRAINT uk_sort_position UNIQUE (preference_user_id, sort_type, sort_position),
                                CONSTRAINT uk_sort_entity UNIQUE (preference_user_id, sort_type, domain_id),
                                CONSTRAINT chk_position CHECK (sort_position > 0)
);

CREATE INDEX idx_sort_user_type ON user_sortables(preference_user_id, sort_type, sort_position);

CREATE TABLE user_favorites (
                                favorite_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                preference_user_id NUMBER NOT NULL,
                                favorite_type VARCHAR2(20) NOT NULL,  -- 'ACCOUNT', 'PARTNER'
                                domain_id VARCHAR2(255) NOT NULL,  -- domain_id
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

                                CONSTRAINT fk_fav_user FOREIGN KEY (preference_user_id)
                                    REFERENCES user_identities(internal_user_id) ON DELETE CASCADE,

                                CONSTRAINT uk_favorite UNIQUE (preference_user_id, favorite_type, domain_id),
                                CONSTRAINT chk_favorite_type CHECK (favorite_type IN ('ACCOUNT', 'PARTNER'))
);

CREATE INDEX idx_fav_user_type ON user_favorites(preference_user_id, favorite_type);

