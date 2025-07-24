-- Create keyspace
CREATE KEYSPACE IF NOT EXISTS poc_keyspace
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE poc_keyspace;

-- Create User Defined Type for UI Components
CREATE TYPE IF NOT EXISTS ui_component (
    component_id text,
    component_type text,
    name text,
    display_order int,
    visible boolean,
    properties text,
    last_modified timestamp,
    sub_components list<frozen<ui_component>>,
);

-- Create main table
CREATE TABLE IF NOT EXISTS ui_customizations (
    user_id text PRIMARY KEY,
    profile_name text,
    created_at timestamp,
    updated_at timestamp,
    version text,
    components list<frozen<ui_component>>
);

-- Insert sample data
INSERT INTO ui_customizations (user_id, profile_name, created_at, updated_at, version, components) VALUES (
    'demo_user',
    'Demo Profile',
    toTimeStamp(now()),
    toTimeStamp(now()),
    'v1',
    [
        {
            component_id: 'header_1',
            component_type: 'HEADER',
            name: 'Main Header',
            display_order: 1,
            visible: true,
            properties: '{"color": "#ffffff", "height": 60}',
            last_modified: toTimeStamp(now()),
            sub_components: [],
        },
        {
            component_id: 'carousel_1',
            component_type: 'CAROUSEL',
            name: 'Bank Accounts',
            display_order: 2,
            visible: true,
            properties: '{"type": "carousel"}',
            last_modified: toTimeStamp(now()),
            sub_components: [
                {
                    component_id: 'main_account',
                    component_type: 'ACCOUNT',
                    name: 'Main Account',
                    display_order: 1,
                    visible: true,
                    properties: '{"currency": "HUF"}',
                    last_modified: toTimeStamp(now()),
                    sub_components: [],
                },
                {
                    component_id: 'euro_account',
                    component_type: 'ACCOUNT',
                    name: 'Euro Account',
                    display_order: 2,
                    visible: true,
                    properties: '{"currency": "EUR"}',
                    last_modified: toTimeStamp(now()),
                    sub_components: [],
                },
                {
                    component_id: 'saving_account',
                    component_type: 'ACCOUNT',
                    name: 'Saving Account',
                    display_order: 3,
                    visible: true,
                    properties: '{"currency": "HUF"}',
                    last_modified: toTimeStamp(now()),
                    sub_components: [],
                }
            ],
        },
        {
            component_id: 'widget_1',
            component_type: 'WIDGET',
            name: 'Dashboard Widget',
            display_order: 3,
            visible: true,
            properties: '{"type": "chart", "data": "sales"}',
            last_modified: toTimeStamp(now()),
            sub_components: [],
        }
    ]
);