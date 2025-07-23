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
    last_modified timestamp
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
            last_modified: toTimeStamp(now())
        },
        {
            component_id: 'sidebar_1',
            component_type: 'SIDEBAR',
            name: 'Navigation Sidebar',
            display_order: 2,
            visible: true,
            properties: '{"width": 250, "theme": "dark"}',
            last_modified: toTimeStamp(now())
        },
        {
            component_id: 'widget_1',
            component_type: 'WIDGET',
            name: 'Dashboard Widget',
            display_order: 3,
            visible: true,
            properties: '{"type": "chart", "data": "sales"}',
            last_modified: toTimeStamp(now())
        }
    ]
);